package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Rounded rectangles and the sticky note's card, drawn from nothing but filled and stroked
 * polygons.
 *
 * The renderer has no rounded-rect or shadow primitive, and adding one would mean teaching every
 * backend (the Skia page cache, the PDF writer, the test fakes) a new trick and trusting them to
 * agree. A polygon they already all draw the same way, so a card built from polygons is the same
 * card in the page cache, the lifted overlay, a thumbnail and an exported PDF by construction.
 */
object CardPaint {

    /** Segments per quarter-circle corner: smooth at any zoom a page is read at, cheap to fill. */
    private const val CORNER_SEGMENTS = 8

    /**
     * The outline of [r] with each corner rounded by its own radius ([tl], [tr], [br], [bl], in
     * the rect's units), clockwise from the top-left. Radii are clamped to half the shorter side so
     * a thin box never folds over itself; a zero radius is a sharp corner (one point).
     */
    fun roundRect(r: Rect, tl: Double, tr: Double = tl, br: Double = tl, bl: Double = tl): List<Pt> {
        val cap = min(r.w, r.h) / 2.0
        val out = ArrayList<Pt>(4 * (CORNER_SEGMENTS + 1))
        fun corner(cx: Double, cy: Double, radius: Double, startDeg: Double, sharp: Pt) {
            val rad = radius.coerceIn(0.0, cap.coerceAtLeast(0.0))
            if (rad <= 0.0) {
                out.add(sharp)
                return
            }
            for (i in 0..CORNER_SEGMENTS) {
                val a = (startDeg + 90.0 * i / CORNER_SEGMENTS) * PI / 180.0
                out.add(Pt(cx + rad * cos(a), cy + rad * sin(a)))
            }
        }
        val rTl = tl.coerceIn(0.0, cap.coerceAtLeast(0.0))
        val rTr = tr.coerceIn(0.0, cap.coerceAtLeast(0.0))
        val rBr = br.coerceIn(0.0, cap.coerceAtLeast(0.0))
        val rBl = bl.coerceIn(0.0, cap.coerceAtLeast(0.0))
        // Angles are y-down, so 180..270 sweeps the top-left corner from its left edge to its top.
        corner(r.left + rTl, r.top + rTl, rTl, 180.0, Pt(r.left, r.top))
        corner(r.right - rTr, r.top + rTr, rTr, 270.0, Pt(r.right, r.top))
        corner(r.right - rBr, r.bottom - rBr, rBr, 0.0, Pt(r.right, r.bottom))
        corner(r.left + rBl, r.bottom - rBl, rBl, 90.0, Pt(r.left, r.bottom))
        return out
    }

    // --- the sticky note's card ---

    /** Layers in the soft shadow; each is a slightly larger, very faint copy of the card. */
    private const val SHADOW_LAYERS = 5

    /** How far each shadow layer reaches past the last, as a fraction of the font size. */
    private const val SHADOW_STEP = 0.22

    /** How far the shadow drops below the card, as a fraction of the font size. */
    private const val SHADOW_DROP = 0.35

    /** Alpha of one shadow layer; they stack, so the shadow is darkest right under the card. */
    private const val SHADOW_ALPHA = 9

    /** Corner radius as a fraction of the font size, so a scaled note keeps its proportions. */
    private const val CARD_RADIUS = 0.85

    fun cardRadius(pointSize: Double): Double = pointSize * CARD_RADIUS

    /** How far the card's shadow paints past its bounds on the sides and top ([shadowBottom] below). */
    fun shadowReach(pointSize: Double): Double = pointSize * SHADOW_STEP * SHADOW_LAYERS

    fun shadowBottom(pointSize: Double): Double = shadowReach(pointSize) + pointSize * SHADOW_DROP

    /** The card's full paint extent: its [bounds] grown by the shadow. */
    fun cardPaintBounds(bounds: Rect, pointSize: Double): Rect {
        val s = shadowReach(pointSize)
        return Rect(bounds.x - s, bounds.y - s, bounds.w + 2 * s, bounds.h + s + shadowBottom(pointSize))
    }

    /**
     * Paint a sticky note's card over [bounds]: a soft drop shadow (stacked faint layers rather than
     * a blur, which the PDF writer could not reproduce), the [fill] itself, and a hairline a shade
     * darker than the fill so a pale card still has an edge on white paper.
     */
    fun paintCard(r: Renderer, bounds: Rect, fill: Rgba, pointSize: Double) {
        if (bounds.w <= 0.0 || bounds.h <= 0.0) return
        val radius = cardRadius(pointSize)
        val step = pointSize * SHADOW_STEP
        val drop = pointSize * SHADOW_DROP
        val shade = Rgba(0, 0, 0, SHADOW_ALPHA)
        for (i in SHADOW_LAYERS downTo 1) {
            val grow = step * i
            val layer = Rect(bounds.x - grow, bounds.y - grow + drop, bounds.w + 2 * grow, bounds.h + 2 * grow)
            r.fillPolygon(roundRect(layer, radius + grow), shade)
        }
        val outline = roundRect(bounds, radius)
        r.fillPolygon(outline, fill)
        r.strokePolygon(outline, Pen(darken(fill, 0.82), width = pointSize * 0.06, cosmetic = false))
    }

    /** [c] with each channel scaled by [k] (k < 1 darkens), alpha kept. */
    fun darken(c: Rgba, k: Double): Rgba =
        Rgba((c.r * k).toInt().coerceIn(0, 255), (c.g * k).toInt().coerceIn(0, 255), (c.b * k).toInt().coerceIn(0, 255), c.a)
}

/**
 * The sticky note's colours: the six pastels Samsung Notes and paper notes alike use, with the
 * dark ink the note's text is set in on any of them. Yellow is the default, as on paper.
 */
object StickyColors {
    val YELLOW = Rgba(255, 238, 153)
    val PINK = Rgba(255, 205, 216)
    val GREEN = Rgba(204, 238, 194)
    val BLUE = Rgba(196, 223, 255)
    val PURPLE = Rgba(224, 210, 255)
    val ORANGE = Rgba(255, 214, 170)

    val ALL = listOf(YELLOW, PINK, GREEN, BLUE, PURPLE, ORANGE)

    /** Text on a pastel card: near-black, never the toolbar ink, which may be pale on dark paper. */
    val TEXT = Rgba(38, 38, 38)
}
