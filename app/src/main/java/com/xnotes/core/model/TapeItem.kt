package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import com.xnotes.core.vector.PolygonClip
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** The print on a strip of tape. [id] is the file format's name for it (never rename one). */
enum class TapePattern(val id: String) {
    SOLID("solid"),
    STRIPES("stripes"),
    DOTS("dots"),
    GRID("grid");

    companion object {
        fun fromId(id: String?): TapePattern = entries.firstOrNull { it.id == id } ?: STRIPES
    }
}

/**
 * A strip of washi tape stuck over the page: the study tool GoodNotes and Starnote call Tape. It is
 * laid in one straight pull from [start] to [end], [width] across, and hides whatever is under it
 * until it is tapped, when it peels back to a faint ghost of itself ([revealed]) so the answer
 * shows through and it is still obvious there is tape there to cover it again.
 *
 * Revealing is how the tape is read, not an edit of the note: it is saved with the note, so a page
 * half-studied reopens the way it was left, but it is not an undo step, exactly as in GoodNotes.
 *
 * Everything it draws is plain polygons ([shape]), so the paged renderer, the thumbnails, the PDF
 * export and the GL canvas's tessellator ([com.xnotes.core.infinite.TapeTessellator]) all lay the
 * same strip: no free rotation, no textures, no blur, which keeps every backend honest and a strip
 * cheap enough to redraw under the pen on every frame while it is being pulled out.
 */
class TapeItem(
    var start: Pt,
    var end: Pt,
    var width: Double,
    var color: Rgba,
    var pattern: TapePattern = TapePattern.STRIPES,
    /** Peeled back: drawn as a faint outline so what it covers shows. Saved with the note. */
    var revealed: Boolean = false,
    /** Seeds the torn ends, so a strip keeps its own tear through moves, copies and reloads. */
    val seed: Int = newSeed(),
) : CanvasItem {

    override val kind = KIND
    override val resizable = true
    override var locked = false

    // --- the strip's own frame: u along it from [start], v across it ---

    private val length: Double get() = start.distanceTo(end)

    /** Unit vector from [start] to [end]; a strip with no length yet lies along +x. */
    private fun dir(): Pt {
        val l = length
        return if (l < 1e-9) Pt(1.0, 0.0) else Pt((end.x - start.x) / l, (end.y - start.y) / l)
    }

    private fun toLocal(p: Pt): Pt {
        val d = dir()
        val rx = p.x - start.x
        val ry = p.y - start.y
        return Pt(rx * d.x + ry * d.y, -rx * d.y + ry * d.x)
    }

    /** The strip's four corners, page-local: start-top, end-top, end-bottom, start-bottom. */
    fun corners(): List<Pt> {
        val d = dir()
        val n = Pt(-d.y, d.x) * (width / 2.0)
        return listOf(start - n, end - n, end + n, start + n)
    }

    // --- drawing ---

    private var cache: TapeShape? = null
    private var cacheKey: CacheKey? = null

    private data class CacheKey(val start: Pt, val end: Pt, val width: Double, val pattern: TapePattern)

    /** The strip's pieces in page space, rebuilt only when its geometry or print changes. */
    fun shape(): TapeShape {
        val key = CacheKey(start, end, width, pattern)
        val hit = cache
        if (hit != null && key == cacheKey) return hit
        return buildShape(start, end, width, pattern, seed).also {
            cache = it
            cacheKey = key
        }
    }

    override fun paint(r: Renderer) {
        val s = shape()
        if (revealed) {
            r.fillPolygon(s.body, ghostFill(color))
            val ghost = ghostPattern(color)
            for (poly in s.pattern) r.fillPolygon(poly, ghost)
            for (dot in s.dots) r.fillCircle(dot.center, dot.radius, ghost)
            r.strokePolygon(s.body, Pen(ghostEdge(color), outlineWidth(width), cosmetic = false))
            return
        }
        r.fillPolygon(s.shadowFar, SHADOW_FAR)
        r.fillPolygon(s.shadowNear, SHADOW_NEAR)
        r.fillPolygon(s.body, color.withAlpha(255))
        val print = patternColor(color)
        for (poly in s.pattern) r.fillPolygon(poly, print)
        for (dot in s.dots) r.fillCircle(dot.center, dot.radius, print)
        r.strokePolygon(s.body, Pen(edgeColor(color), edgeWidth(width), cosmetic = false))
    }

    // --- hit testing ---

    override fun bounds(): Rect = Rect.bounding(corners())

    /** The shadow falls a little below and right of the strip. */
    override fun paintBounds(): Rect {
        val b = bounds()
        val drop = shadowDrop(width)
        return Rect(b.x - 1.0, b.y - 1.0, b.w + drop + 2.0, b.h + drop * 2.0 + 2.0)
    }

    override fun translate(dx: Double, dy: Double) {
        start = Pt(start.x + dx, start.y + dy)
        end = Pt(end.x + dx, end.y + dy)
    }

    /** Anywhere on the strip, torn ends included. */
    override fun contains(p: Pt): Boolean {
        val l = toLocal(p)
        return l.x >= -HIT_SLOP && l.x <= length + HIT_SLOP && abs(l.y) <= width / 2.0 + HIT_SLOP
    }

    override fun centroid(): Pt = Pt((start.x + end.x) / 2.0, (start.y + end.y) / 2.0)

    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean {
        val l = toLocal(Pt(cx, cy))
        val du = max(0.0, max(-l.x, l.x - length))
        val dv = max(0.0, abs(l.y) - width / 2.0)
        return hypot(du, dv) <= radius
    }

    override fun snapshotGeometry(): GeometrySnapshot = TapeSnapshot(start, end, width)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap !is TapeSnapshot) return
        start = snap.start
        end = snap.end
        width = snap.width
    }

    /**
     * Both ends follow the map, so a strip turns and stretches with a selection. Its width becomes
     * the mapped strip's own thickness (area over length), which is right for a turn, a uniform
     * scale and a stretch along either axis alike.
     */
    override fun applyTransform(t: Affine) {
        val oldLen = length
        val d = dir()
        start = t.apply(start)
        end = t.apply(end)
        val alongX = t.a * d.x + t.c * d.y
        val alongY = t.b * d.x + t.d * d.y
        val stretch = hypot(alongX, alongY)
        width = if (oldLen > 1e-6 && stretch > 1e-9) width * abs(t.determinant) / stretch else width * t.linearScale
        width = width.coerceIn(MIN_WIDTH, MAX_WIDTH * 4.0)
    }

    companion object {
        const val KIND = "tape"

        /** Width range the tool offers, page px at 150 dpi (about 2 mm to 16 mm). */
        const val MIN_WIDTH = 12.0
        const val MAX_WIDTH = 96.0
        const val DEFAULT_WIDTH = 32.0

        /** A pull within this many degrees of level or plumb lies exactly level or plumb. */
        const val AXIS_SNAP_DEG = 6.0

        /** Extra reach a tap gets around the strip, page px. */
        private const val HIT_SLOP = 2.0

        private val SHADOW_FAR = Rgba(0, 0, 0, 16)
        private val SHADOW_NEAR = Rgba(0, 0, 0, 30)
        private val WHITE = Rgba(255, 255, 255)
        private val BLACK = Rgba(0, 0, 0)

        fun newSeed(): Int = (Math.random() * Int.MAX_VALUE).toInt()

        /**
         * The far end of a pull from [anchor] to [p], laid exactly level or plumb when the pull is
         * within [AXIS_SNAP_DEG] of one, so a strip across a line of text sits square on it.
         */
        fun snapAxis(anchor: Pt, p: Pt, snapDeg: Double = AXIS_SNAP_DEG): Pt {
            val dx = p.x - anchor.x
            val dy = p.y - anchor.y
            if (dx == 0.0 && dy == 0.0) return p
            val snap = Math.toRadians(snapDeg)
            val fromHoriz = atan2(abs(dy), abs(dx))
            return when {
                fromHoriz <= snap -> Pt(p.x, anchor.y)
                fromHoriz >= Math.PI / 2.0 - snap -> Pt(anchor.x, p.y)
                else -> p
            }
        }

        // --- the look ---

        /** [a] moved [t] of the way to [b], opaque. */
        fun mix(a: Rgba, b: Rgba, t: Double): Rgba = Rgba(
            (a.r + (b.r - a.r) * t).roundToInt().coerceIn(0, 255),
            (a.g + (b.g - a.g) * t).roundToInt().coerceIn(0, 255),
            (a.b + (b.b - a.b) * t).roundToInt().coerceIn(0, 255),
            255,
        )

        /** The print: the tape's colour washed halfway to white, which reads as printed on it. */
        fun patternColor(c: Rgba): Rgba = mix(c, WHITE, 0.5)

        /** A hairline a shade darker than the tape, which is what makes it read as a cut strip. */
        fun edgeColor(c: Rgba): Rgba = mix(c, BLACK, 0.16)

        fun ghostFill(c: Rgba): Rgba = c.withAlpha(22)

        /** The print at about 15 percent, so a peeled strip still shows what it was. */
        fun ghostPattern(c: Rgba): Rgba = mix(c, BLACK, 0.2).withAlpha(38)

        fun ghostEdge(c: Rgba): Rgba = mix(c, BLACK, 0.2).withAlpha(120)

        fun edgeWidth(width: Double): Double = (width * 0.025).coerceIn(0.6, 1.4)

        fun outlineWidth(width: Double): Double = (width * 0.04).coerceIn(1.0, 2.0)

        fun shadowDrop(width: Double): Double = (width * 0.05).coerceIn(1.0, 3.0)

        /**
         * Lay out a strip. Pure, so tests and every renderer share it.
         *
         * The ends are torn rather than cut: a run of shallow teeth across each end, each a little
         * deeper or shallower than the next (from [seed]), which is what tearing tape by hand
         * leaves. The print lives inside the teeth, on the straight middle of the strip, so the tear
         * stays clean. The shadow is the strip itself twice, dropped a hair down and right at two
         * strengths, which reads as soft without a blur any backend would have to emulate.
         */
        fun buildShape(start: Pt, end: Pt, width: Double, pattern: TapePattern, seed: Int): TapeShape {
            val len = start.distanceTo(end)
            val d = if (len < 1e-9) Pt(1.0, 0.0) else Pt((end.x - start.x) / len, (end.y - start.y) / len)
            val n = Pt(-d.y, d.x)
            val h = width / 2.0
            fun page(u: Double, v: Double) = Pt(start.x + d.x * u + n.x * v, start.y + d.y * u + n.y * v)

            val teeth = max(3, (width / 8.0).roundToInt())
            val depth = (width * 0.09).coerceIn(1.5, 6.0).coerceAtMost(len / 4.0 + 0.5)
            val rnd = java.util.Random(seed.toLong())
            fun tooth() = depth * (0.55 + 0.45 * rnd.nextDouble())

            // Outline, local coords: along the top edge, down the far torn end, back along the
            // bottom edge and up the near torn end.
            val local = ArrayList<Pt>(4 * teeth + 8)
            local.add(Pt(0.0, -h))
            local.add(Pt(len, -h))
            for (i in 1 until 2 * teeth) {
                val v = -h + width * i / (2.0 * teeth)
                local.add(Pt(if (i % 2 == 1) len - tooth() else len, v))
            }
            local.add(Pt(len, h))
            local.add(Pt(0.0, h))
            for (i in 2 * teeth - 1 downTo 1) {
                val v = -h + width * i / (2.0 * teeth)
                local.add(Pt(if (i % 2 == 1) tooth() else 0.0, v))
            }
            val body = local.map { page(it.x, it.y) }

            val drop = shadowDrop(width)
            val near = body.map { Pt(it.x + drop * 0.4, it.y + drop * 0.8) }
            val far = body.map { Pt(it.x + drop * 0.7, it.y + drop * 1.6) }

            // The print, clipped to the strip's straight middle.
            val u0 = depth
            val u1 = len - depth
            val polys = ArrayList<List<Pt>>()
            val dots = ArrayList<TapeDot>()
            if (u1 - u0 > 1.0) {
                val core = PolygonClip.wound(listOf(Pt(u0, -h), Pt(u1, -h), Pt(u1, h), Pt(u0, h)))
                fun clipped(ring: List<Pt>) {
                    val c = PolygonClip.polygon(ring, core)
                    if (c.size >= 3) polys.add(c.map { page(it.x, it.y) })
                }
                when (pattern) {
                    TapePattern.SOLID -> Unit
                    TapePattern.STRIPES -> {
                        // Diagonal bands at 45 degrees across the strip.
                        val period = max(width * 0.42, 6.0)
                        val band = period * 0.42
                        var s = u0 - width - period
                        while (s < u1) {
                            clipped(listOf(Pt(s, h), Pt(s + band, h), Pt(s + band + width, -h), Pt(s + width, -h)))
                            s += period
                        }
                    }
                    TapePattern.DOTS -> {
                        // Staggered polka dots, whole dots only.
                        val gap = max(width * 0.3, 5.0)
                        val radius = gap * 0.2
                        val rows = max(1, floor(width / gap).toInt())
                        val firstV = -(rows - 1) * gap / 2.0
                        for (row in 0 until rows) {
                            val v = firstV + row * gap
                            var u = u0 + gap / 2.0 + if (row % 2 == 1) gap / 2.0 else 0.0
                            while (u + radius <= u1) {
                                if (abs(v) + radius <= h - 0.5) dots.add(TapeDot(page(u, v), radius))
                                u += gap
                            }
                        }
                    }
                    TapePattern.GRID -> {
                        // Gingham: bands along and across. Covered, the two are one opaque colour;
                        // peeled back, the crossings come out a touch stronger, as gingham's do.
                        val gap = max(width * 0.34, 6.0)
                        val band = gap * 0.28
                        val rows = max(1, floor(width / gap).toInt())
                        val firstV = -(rows - 1) * gap / 2.0
                        for (row in 0 until rows) {
                            val v = firstV + row * gap
                            clipped(listOf(Pt(u0, v - band / 2), Pt(u1, v - band / 2), Pt(u1, v + band / 2), Pt(u0, v + band / 2)))
                        }
                        var u = u0 + gap / 2.0
                        while (u < u1) {
                            clipped(listOf(Pt(u - band / 2, -h), Pt(u + band / 2, -h), Pt(u + band / 2, h), Pt(u - band / 2, h)))
                            u += gap
                        }
                    }
                }
            }
            return TapeShape(body, near, far, polys, dots)
        }
    }
}

/** A dot of a [TapePattern.DOTS] print, page-local. */
class TapeDot(val center: Pt, val radius: Double)

/** A laid-out strip in page space: what every renderer draws, back to front. */
class TapeShape(
    /** The strip's outline, torn ends and all. */
    val body: List<Pt>,
    /** The two shadow layers, nearer (stronger) and farther (fainter). */
    val shadowNear: List<Pt>,
    val shadowFar: List<Pt>,
    /** The print, as filled polygons inside the strip. */
    val pattern: List<List<Pt>>,
    /** Or as dots. */
    val dots: List<TapeDot>,
)

private data class TapeSnapshot(val start: Pt, val end: Pt, val width: Double) : GeometrySnapshot
