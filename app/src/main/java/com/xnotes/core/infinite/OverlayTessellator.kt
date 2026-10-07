package com.xnotes.core.infinite

import com.xnotes.core.geometry.Obb
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba

/**
 * The chrome drawn over the content: the selection box and its handles, the band a drag sweeps out,
 * the lasso loop.
 *
 * The selection is drawn as the mockup draws it (SC 46-49), and the paged canvas draws its own from
 * the same dp numbers here: a dashed frame, white-faced grips ringed in the page accent, each on a
 * flat drop shadow, and one filled rotate grip carrying the clockwise arrow on a stem above the top
 * edge. The shadow is one disc a dp down, not a blur, and the arrow is [RotateGlyph]'s triangles,
 * built once.
 *
 * All of it is built as ordinary triangles and pushed through the same transient buffer the wet
 * stroke uses, rather than through a shader of its own. That keeps one path for everything the
 * canvas draws, and the two are never needed at once: you cannot be inking and selecting.
 *
 * Widths arrive in device pixels and are divided by the zoom here, so the outline stays one
 * thickness however far in or out the canvas is. That means the overlay has to be rebuilt when the
 * zoom changes. Its discs are cut to an on-screen tolerance ([chromeTolerance]) rather than the
 * ink's, so a grip is a dozen or so segments instead of the 64 a stroke's tolerance asks for.
 */
object OverlayTessellator {

    /** Selection frame thickness, in dp (SC 46: 1.5). */
    const val FRAME_DP = 1.5

    /** A resize grip's white face, across, in dp (SC 47: 14). */
    const val GRIP_DP = 14.0

    /** The page-accent ring round a grip's face, in dp (SC 47: a 1.5 spread). */
    const val GRIP_RING_DP = 1.5

    /** The rotate grip's diameter, in dp (SC 49: 26). */
    const val ROTATE_GRIP_DP = 26.0

    /** The stem from the box's top edge up to the rotate grip's rim, in dp (SC 48: 28). */
    const val ROTATE_STEM_DP = 28.0

    /** The arrow on the rotate grip: its icon box across, in dp (SC 49: 14). */
    const val ROTATE_GLYPH_DP = 14.0

    /** How far the rotate grip's centre sits past the box's top edge, in dp: the stem and its radius. */
    const val ROTATE_ARM_DP = ROTATE_STEM_DP + ROTATE_GRIP_DP / 2.0

    /** How far the rotate grip's far rim reaches past the top edge, in dp: what the selection bar clears. */
    const val ROTATE_REACH_DP = ROTATE_STEM_DP + ROTATE_GRIP_DP

    /** A grip's shadow: one flat disc this far down, in dp (SC 47/49: 1px down), no blur. */
    const val SHADOW_DROP_DP = 1.0

    /** How much wider (radius, dp) the shadow disc is than the grip it is under, standing in for the blur. */
    const val SHADOW_GROW_DP = 0.5

    /** A resize grip's shadow (SC 47: black at 22 %). */
    val GRIP_SHADOW = Rgba(0, 0, 0, 56)

    /** The rotate grip's shadow (SC 49: black at 25 %). */
    val ROTATE_SHADOW = Rgba(0, 0, 0, 64)

    /** A grip's face on ordinary paper; the caller passes the accent's own on a dark page. */
    private val WHITE = Rgba(255, 255, 255, 255)

    /**
     * How far a chrome disc may stray from a true circle, in dp on screen. A quarter dp is under a
     * device pixel at any density and cuts a 14 dp grip into about a dozen segments; ink's own
     * tolerance (a device pixel at the deepest zoom) would cut every disc at its 64-segment cap.
     */
    const val CHROME_TOLERANCE_DP = 0.25

    /** [CHROME_TOLERANCE_DP] in content px at [zoom], for the chrome built here. */
    fun chromeTolerance(zoom: Double, devicePxPerDp: Double): Double =
        CHROME_TOLERANCE_DP * devicePxPerDp / maxOf(zoom, 1e-9)

    /**
     * How far the rotate grip's centre sits past the box's top edge, in content px at [zoom]. The
     * drawing and both canvases' hit tests read it here, so what is drawn is what is grabbed.
     */
    fun rotateArm(zoom: Double, devicePxPerDp: Double): Double =
        ROTATE_ARM_DP * devicePxPerDp / maxOf(zoom, 1e-9)

    /** Band and lasso outline thickness, in device pixels. */
    const val MARQUEE_PX = 1.4

    /**
     * Dash on/off runs for every marquee, in dp, so chrome reads the same on any screen. Both
     * canvases dash from these, so a band, a lasso and a selection box share one rhythm on either
     * canvas. Their widths differ: the frame is [FRAME_DP], the band and lasso [MARQUEE_PX] here
     * and 1.3 px on the paged canvas.
     */
    const val DASH_ON_DP = 6.0
    const val DASH_GAP_DP = 5.0

    /**
     * The builders [selection] fills, kept by a caller that rebuilds the chrome often (the canvas,
     * on every resize or rotate sample), so a rebuild reuses their arrays rather than growing five
     * fresh ones. Main thread only, like the chrome itself.
     */
    class Scratch {
        internal val outline = MeshBuilder(1024, 1536)
        internal val shadows = MeshBuilder(160, 384)
        internal val rotateShadow = MeshBuilder(32, 64)
        internal val marks = MeshBuilder(192, 448)
        internal val faces = MeshBuilder(256, 512)

        internal fun clear() {
            outline.clear()
            shadows.clear()
            rotateShadow.clear()
            marks.clear()
            faces.clear()
        }
    }

    /**
     * The selection box, its eight grips and the rotate grip on its stem, in draw order: the frame
     * and stem, the shadows over them, the accent discs (rings and the rotate grip), then the faces
     * and the arrow in [face]. The arrow stays upright as the box tilts, as an icon does.
     * [tolerance] is normally [chromeTolerance]; [scratch] lends the builders.
     */
    fun selection(
        box: Obb,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        devicePxPerDp: Double = 1.0,
        face: Rgba = WHITE,
        scratch: Scratch = Scratch(),
    ): List<MeshPart> {
        if (zoom <= 0.0) return emptyList()
        // Content px per dp: every size below is an on-screen one, so it comes back out of the zoom.
        val dp = devicePxPerDp / zoom
        scratch.clear()
        val outline = scratch.outline
        val half = FRAME_DP * dp / 2.0
        dashInto(outline, box.corners(), half, closed = true, zoom = zoom, devicePxPerDp = devicePxPerDp, tolerance = tolerance)

        // The stem out to the grip, so it reads as attached rather than floating. Solid: it is a
        // join, not a boundary, and a dashed one at this length would be two ticks and a gap.
        val top = com.xnotes.canvas.ResizeMath.obbTopMid(box)
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(box, rotateArm(zoom, devicePxPerDp))
        outline.polylineRibbon(listOf(top, grip), half, closed = false, tolerance = tolerance)

        val handles = com.xnotes.canvas.ResizeMath.obbHandles(box)
        val faceR = GRIP_DP * dp / 2.0
        val ringR = faceR + GRIP_RING_DP * dp
        val rotateR = ROTATE_GRIP_DP * dp / 2.0
        val drop = SHADOW_DROP_DP * dp
        val grow = SHADOW_GROW_DP * dp

        val shadows = scratch.shadows
        for (h in handles) shadows.circle(h.content.x, h.content.y + drop, ringR + grow, tolerance)
        val rotateShadow = scratch.rotateShadow
        rotateShadow.circle(grip.x, grip.y + drop, rotateR + grow, tolerance)

        val marks = scratch.marks
        for (h in handles) marks.circle(h.content.x, h.content.y, ringR, tolerance)
        marks.circle(grip.x, grip.y, rotateR, tolerance)

        val faces = scratch.faces
        for (h in handles) faces.circle(h.content.x, h.content.y, faceR, tolerance)
        glyphInto(faces, grip, ROTATE_GLYPH_DP * dp)

        val parts = ArrayList<MeshPart>(5)
        if (!outline.isEmpty) parts.add(MeshPart(outline.build(), accent, InkPass.OPAQUE))
        if (!shadows.isEmpty) parts.add(MeshPart(shadows.build(), GRIP_SHADOW, InkPass.OPAQUE))
        if (!rotateShadow.isEmpty) parts.add(MeshPart(rotateShadow.build(), ROTATE_SHADOW, InkPass.OPAQUE))
        if (!marks.isEmpty) parts.add(MeshPart(marks.build(), accent, InkPass.OPAQUE))
        if (!faces.isEmpty) parts.add(MeshPart(faces.build(), face, InkPass.OPAQUE))
        return parts
    }

    /**
     * A lone line's or arrow's chrome: one grip on each of its ends and nothing else, no frame and
     * no rotate grip, since the ends are how it is resized and turned. Each grip is the one the box
     * corners wear — a [GRIP_DP] face in [face], a [GRIP_RING_DP] accent ring, a flat drop shadow —
     * in the same draw order: the shadows, then the rings, then the faces.
     */
    fun endpoints(
        start: Pt,
        end: Pt,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        devicePxPerDp: Double = 1.0,
        face: Rgba = WHITE,
        scratch: Scratch = Scratch(),
    ): List<MeshPart> {
        if (zoom <= 0.0) return emptyList()
        val dp = devicePxPerDp / zoom
        scratch.clear()
        val faceR = GRIP_DP * dp / 2.0
        val ringR = faceR + GRIP_RING_DP * dp
        val drop = SHADOW_DROP_DP * dp
        val grow = SHADOW_GROW_DP * dp
        val shadows = scratch.shadows
        val marks = scratch.marks
        val faces = scratch.faces
        shadows.circle(start.x, start.y + drop, ringR + grow, tolerance)
        shadows.circle(end.x, end.y + drop, ringR + grow, tolerance)
        marks.circle(start.x, start.y, ringR, tolerance)
        marks.circle(end.x, end.y, ringR, tolerance)
        faces.circle(start.x, start.y, faceR, tolerance)
        faces.circle(end.x, end.y, faceR, tolerance)
        return listOf(
            MeshPart(shadows.build(), GRIP_SHADOW, InkPass.OPAQUE),
            MeshPart(marks.build(), accent, InkPass.OPAQUE),
            MeshPart(faces.build(), face, InkPass.OPAQUE),
        )
    }

    /**
     * [parts] moved by ([dx], [dy]): the chrome of a box that has only moved since it was built,
     * without tessellating it again. Only the positions are new; the offsets and triangles are the
     * built mesh's own, since nothing writes to a mesh once it is built.
     */
    fun translated(parts: List<MeshPart>, dx: Double, dy: Double): List<MeshPart> {
        if (dx == 0.0 && dy == 0.0) return parts
        val out = ArrayList<MeshPart>(parts.size)
        for (part in parts) {
            val mesh = part.mesh
            val src = mesh.positions
            val pos = DoubleArray(src.size)
            var i = 0
            while (i + 1 < src.size) {
                pos[i] = src[i] + dx
                pos[i + 1] = src[i + 1] + dy
                i += 2
            }
            out.add(MeshPart(MeshData(pos, mesh.offsets, mesh.indices, mesh.colors), part.color, part.pass, part.glow))
        }
        return out
    }

    /** [RotateGlyph]'s cached triangles into [b], centred on [at] and [size] content px across. */
    private fun glyphInto(b: MeshBuilder, at: Pt, size: Double) {
        val glyph = com.xnotes.canvas.RotateGlyph.mesh ?: return
        val pts = glyph.points
        if (pts.isEmpty()) return
        val base = b.vertex(at.x + pts[0].x * size, at.y + pts[0].y * size)
        for (i in 1 until pts.size) b.vertex(at.x + pts[i].x * size, at.y + pts[i].y * size)
        val idx = glyph.indices
        var i = 0
        while (i + 2 < idx.size) {
            b.triangle(base + idx[i], base + idx[i + 1], base + idx[i + 2])
            i += 3
        }
    }

    /** The rectangle a band-select drag has swept out so far, dashed like every other marquee. */
    fun band(
        rect: Rect,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        devicePxPerDp: Double = 1.0,
    ): List<MeshPart> {
        if (zoom <= 0.0 || rect.w <= 0.0 && rect.h <= 0.0) return emptyList()
        val b = MeshBuilder()
        val corners = listOf(
            Pt(rect.left, rect.top),
            Pt(rect.right, rect.top),
            Pt(rect.right, rect.bottom),
            Pt(rect.left, rect.bottom),
        )
        dashInto(b, corners, MARQUEE_PX / zoom / 2.0, closed = true, zoom = zoom, devicePxPerDp = devicePxPerDp, tolerance = tolerance)
        if (b.isEmpty) return emptyList()
        return listOf(MeshPart(b.build(), accent, InkPass.OPAQUE))
    }

    /**
     * The lasso as drawn so far: a dashed, open line that stays where the pen put it.
     *
     * Open because the loop is the pen's, not a shape: drawing a chord back to the start would
     * claim an edge the hand never made. What the lasso *encloses* is worked out at pen up, and is
     * not what this shows.
     */
    fun lasso(
        points: List<Pt>,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        devicePxPerDp: Double = 1.0,
    ): List<MeshPart> = lassoRun(points, 0, points.size, zoom, accent, tolerance, 0.0, devicePxPerDp)

    /**
     * A stretch of the lasso: [count] points from [from], dashed and open, picking the pattern up
     * [phase] content units in.
     *
     * A lasso only ever grows at its end, and every vertex carries a disc of its own, so building
     * it whole on each touch sample costs the whole line again. A settled stretch is uploaded once
     * and never rewritten, exactly as a settled run of wet ink is, and [phase] is the arc the runs
     * before it spent so the dashes land where an unbroken line would have put them.
     */
    fun lassoRun(
        points: List<Pt>,
        from: Int,
        count: Int,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        phase: Double,
        devicePxPerDp: Double = 1.0,
    ): List<MeshPart> {
        if (zoom <= 0.0 || count < 2 || from < 0 || from + count > points.size) return emptyList()
        val b = MeshBuilder()
        val half = MARQUEE_PX / zoom / 2.0
        val span = points.subList(from, from + count)
        dashInto(b, span, half, closed = false, zoom = zoom, devicePxPerDp = devicePxPerDp, tolerance = tolerance, phase = phase)
        if (b.isEmpty) return emptyList()
        return listOf(MeshPart(b.build(), accent, InkPass.OPAQUE))
    }

    /** The moving end of the lasso, from [from] to the last point it has. */
    fun lassoTail(
        points: List<Pt>,
        from: Int,
        zoom: Double,
        accent: Rgba,
        tolerance: Double,
        phase: Double,
        devicePxPerDp: Double = 1.0,
    ): List<MeshPart> {
        if (from < 0 || from >= points.size) return emptyList()
        return lassoRun(points, from, points.size - from, zoom, accent, tolerance, phase, devicePxPerDp)
    }

    /**
     * [points] as a dashed ribbon into [b]. The dash is an on-screen length, so it comes back out
     * of the zoom into content px; [phase] lets a line split into runs keep one rhythm.
     */
    private fun dashInto(
        b: MeshBuilder,
        points: List<Pt>,
        half: Double,
        closed: Boolean,
        zoom: Double,
        devicePxPerDp: Double,
        tolerance: Double,
        phase: Double = 0.0,
    ) {
        val on = DASH_ON_DP * devicePxPerDp / zoom
        val gap = DASH_GAP_DP * devicePxPerDp / zoom
        for (run in MeshBuilder.dashRuns(points, on, gap, closed = closed, phase = phase)) {
            b.polylineRibbon(run, half, closed = false, tolerance = tolerance)
        }
    }

    /**
     * [bounds] (viewport px, the selection's box) stretched up or down over the rotate grip centred
     * at [grip] (viewport px; null when there is none), for the selection bar's anchor, so the bar
     * floats clear of the grip instead of burying it. The grip hangs off the tilted box, so after a
     * turn it can reach well above the items' upright bounds, and past a half turn it hangs below
     * them; reading where it actually is covers both. Upright, its rim is [ROTATE_REACH_DP] above
     * the box. Across, the box is unchanged. Both canvases anchor their bar with this.
     *
     * The box's grips ([handles], viewport px, their centres) are cleared the same way, by their ringed rim: turned, a
     * corner of the box stands above the items' bounds and its grip half above that, so a bar 12 dp over the bounds
     * sat on the corner grip while it cleared the rotate grip.
     */
    fun clearRotateGrip(bounds: Rect, grip: Pt?, devicePxPerDp: Double, handles: List<Pt> = emptyList()): Rect {
        if (grip == null && handles.isEmpty()) return bounds
        var top = bounds.top
        var bottom = bounds.bottom
        if (grip != null) {
            val r = ROTATE_GRIP_DP / 2.0 * devicePxPerDp
            top = minOf(top, grip.y - r)
            bottom = maxOf(bottom, grip.y + r)
        }
        val h = (GRIP_DP / 2.0 + GRIP_RING_DP) * devicePxPerDp
        for (p in handles) {
            top = minOf(top, p.y - h)
            bottom = maxOf(bottom, p.y + h)
        }
        if (top == bounds.top && bottom == bounds.bottom) return bounds
        return Rect(bounds.left, top, bounds.w, bottom - top)
    }

    /** Content-space bounds of an oriented box grown by its grips, the rotate grip and their shadows. */
    fun selectionBounds(box: Obb, zoom: Double, devicePxPerDp: Double = 1.0): Rect {
        val pad = (ROTATE_REACH_DP + GRIP_DP + SHADOW_DROP_DP) * devicePxPerDp / maxOf(zoom, 1e-9)
        var acc = Rect.bounding(box.corners())
        acc = acc.outset(pad)
        return acc
    }
}
