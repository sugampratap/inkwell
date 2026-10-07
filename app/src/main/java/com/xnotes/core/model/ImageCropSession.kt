package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One visit to the crop tool for one image: the whole picture laid out where it would sit
 * uncropped, the box being dragged over it, and the arithmetic that turns the result back into a
 * normalised [ImageCrop] and a new rect. Pure, so the handles' behaviour is unit-tested rather than
 * judged by eye; the editors only draw it and feed it pointer positions.
 *
 * Everything here is measured in the session's *frame*: the item's own space with the image's free
 * angle taken out about the centre the image had when the session began. In that frame the picture
 * is upright and axis-aligned whatever its turn, so the crop box is a plain rectangle and its
 * handles are plain rectangle maths. [toItem] puts a frame point back where it is drawn.
 *
 * While the session is open the image shows in full ([applyFull]); [applyResult] then writes the
 * crop and the rect the kept part occupies, and [restore] puts back what it was if cancelled.
 */
class ImageCropSession(val item: ImageItem) {

    /** The item as it was when the session began, for cancel and for the undo step. */
    val before: GeometrySnapshot = item.snapshotGeometry()

    private val startRect = item.rect
    private val orientation = item.orientation
    private val flipX = item.flipX
    private val flipY = item.flipY
    private val angle = item.angle

    /** The fixed point the frame turns about: the image's centre when the session began. */
    private val pivot: Pt = startRect.center

    /** The whole picture, upright, in the frame. */
    val full: Rect

    /** The kept part, in the frame; always inside [full]. */
    var box: Rect
        private set

    /** Width over height the box is held to, or null for a free crop. */
    var aspect: Double? = null
        private set

    init {
        val d = ImageGeometry.sourceRectToDisplay(item.crop ?: ImageCrop.FULL, orientation, flipX, flipY)
        val fw = startRect.w / d.w.coerceAtLeast(ImageCrop.MIN_FRACTION)
        val fh = startRect.h / d.h.coerceAtLeast(ImageCrop.MIN_FRACTION)
        full = Rect(startRect.left - d.l * fw, startRect.top - d.t * fh, fw, fh)
        box = startRect
    }

    /** A frame point where it is drawn, in the item's space. */
    fun toItem(p: Pt): Pt = turn(p, angle)

    /** An item-space point in the frame. */
    fun toFrame(p: Pt): Pt = turn(p, -angle)

    private fun turn(p: Pt, a: Double): Pt {
        if (a == 0.0) return p
        val c = cos(a)
        val s = sin(a)
        val dx = p.x - pivot.x
        val dy = p.y - pivot.y
        return Pt(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
    }

    /** The upright rect, about the right centre, that draws frame rect [r] once the angle is applied. */
    private fun placed(r: Rect): Rect {
        val c = toItem(r.center)
        return Rect(c.x - r.w / 2.0, c.y - r.h / 2.0, r.w, r.h)
    }

    /** Show the whole picture, so the crop can be widened back out over what was cut. */
    fun applyFull() {
        item.restoreGeometry(before)
        item.crop = null
        item.rect = placed(full)
    }

    /** Put the item back exactly as it was. */
    fun restore() = item.restoreGeometry(before)

    /** Write the cropped result: the crop the box is of the picture, and the rect it fills. */
    fun applyResult() {
        item.restoreGeometry(before)
        item.crop = resultCrop()
        item.rect = placed(box)
    }

    /** The source crop the box stands for, or null when it keeps everything. */
    fun resultCrop(): ImageCrop? {
        val d = ImageCrop(
            (box.left - full.left) / full.w,
            (box.top - full.top) / full.h,
            (box.right - full.left) / full.w,
            (box.bottom - full.top) / full.h,
        )
        val src = ImageGeometry.displayRectToSource(d, orientation, flipX, flipY)
        val c = ImageCrop.of(src.l, src.t, src.r, src.b)
        return c.takeUnless { it.isFull }
    }

    /** True when the box differs from what the image showed when the session began. */
    val changed: Boolean get() = !near(box, startRect)

    /** True when the box keeps the whole picture. */
    val isWhole: Boolean get() = near(box, full)

    /** Back to the whole picture, free aspect. */
    fun reset() {
        aspect = null
        box = full
    }

    /**
     * Hold the box to [ratio] (width over height), or free it with null. The new box is the largest
     * of that shape centred on the old one that still fits the picture, so choosing 1:1 on a wide
     * crop keeps the middle of what was chosen.
     */
    fun setAspect(ratio: Double?) {
        aspect = ratio
        if (ratio == null || ratio <= 0.0) return
        val c = box.center
        val maxW = 2.0 * min(c.x - full.left, full.right - c.x)
        val maxH = 2.0 * min(c.y - full.top, full.bottom - c.y)
        var w = max(box.w, box.h * ratio)
        var h = w / ratio
        if (w > maxW) { w = maxW; h = w / ratio }
        if (h > maxH) { h = maxH; w = h * ratio }
        box = Rect(c.x - w / 2.0, c.y - h / 2.0, w, h)
    }

    /** Which handle a frame point lands on within [tolerance] frame units, MOVE inside the box, or null. */
    fun hit(p: Pt, tolerance: Double): CropHandle? {
        val b = box
        val nearL = abs(p.x - b.left) <= tolerance
        val nearR = abs(p.x - b.right) <= tolerance
        val nearT = abs(p.y - b.top) <= tolerance
        val nearB = abs(p.y - b.bottom) <= tolerance
        val inX = p.x >= b.left - tolerance && p.x <= b.right + tolerance
        val inY = p.y >= b.top - tolerance && p.y <= b.bottom + tolerance
        return when {
            nearL && nearT -> CropHandle.TL
            nearR && nearT -> CropHandle.TR
            nearL && nearB -> CropHandle.BL
            nearR && nearB -> CropHandle.BR
            nearL && inY -> CropHandle.L
            nearR && inY -> CropHandle.R
            nearT && inX -> CropHandle.T
            nearB && inX -> CropHandle.B
            b.contains(p) -> CropHandle.MOVE
            else -> null
        }
    }

    /**
     * Drag [handle] of the box as it was at the gesture's start ([from]) to put that handle at
     * [p]. The box never leaves the picture and never gets thinner than [minSide]; with an aspect
     * set, a corner keeps the shape and an edge grows the other side about its middle.
     */
    fun drag(handle: CropHandle, from: Rect, grab: Pt, p: Pt, minSide: Double) {
        val f = full
        val m = minSide.coerceAtMost(min(f.w, f.h))
        val dx = p.x - grab.x
        val dy = p.y - grab.y
        val a = aspect
        box = when {
            handle == CropHandle.MOVE -> {
                val x = (from.left + dx).coerceIn(f.left, f.right - from.w)
                val y = (from.top + dy).coerceIn(f.top, f.bottom - from.h)
                Rect(x, y, from.w, from.h)
            }
            a == null -> freeDrag(handle, from, dx, dy, m)
            handle.isCorner -> cornerDrag(handle, from, dx, dy, m, a)
            else -> edgeDrag(handle, from, dx, dy, m, a)
        }
    }

    private fun freeDrag(h: CropHandle, from: Rect, dx: Double, dy: Double, m: Double): Rect {
        val f = full
        var l = from.left
        var t = from.top
        var r = from.right
        var b = from.bottom
        if (h.movesLeft) l = (l + dx).coerceIn(f.left, r - m)
        if (h.movesRight) r = (r + dx).coerceIn(l + m, f.right)
        if (h.movesTop) t = (t + dy).coerceIn(f.top, b - m)
        if (h.movesBottom) b = (b + dy).coerceIn(t + m, f.bottom)
        return Rect.ltrb(l, t, r, b)
    }

    private fun cornerDrag(h: CropHandle, from: Rect, dx: Double, dy: Double, m: Double, a: Double): Rect {
        val f = full
        // The opposite corner stays put; the dragged one follows the pointer projected onto the
        // box's diagonal, so the drag grows and shrinks it smoothly in either direction.
        val ax = if (h.movesLeft) from.right else from.left
        val ay = if (h.movesTop) from.bottom else from.top
        val gx = (if (h.movesLeft) from.left else from.right) - ax
        val gy = (if (h.movesTop) from.top else from.bottom) - ay
        val px = gx + dx
        val py = gy + dy
        val g2 = gx * gx + gy * gy
        val scale = if (g2 < 1e-12) 1.0 else ((px * gx + py * gy) / g2).coerceAtLeast(0.0)
        var w = from.w * scale
        var hh = w / a
        val roomX = if (h.movesLeft) ax - f.left else f.right - ax
        val roomY = if (h.movesTop) ay - f.top else f.bottom - ay
        if (w > roomX) { w = roomX; hh = w / a }
        if (hh > roomY) { hh = roomY; w = hh * a }
        val minW = max(m, m * a)
        if (w < minW) { w = minW.coerceAtMost(roomX); hh = w / a }
        val l = if (h.movesLeft) ax - w else ax
        val t = if (h.movesTop) ay - hh else ay
        return Rect(l, t, w, hh)
    }

    private fun edgeDrag(h: CropHandle, from: Rect, dx: Double, dy: Double, m: Double, a: Double): Rect {
        val f = full
        return if (h == CropHandle.L || h == CropHandle.R) {
            val ax = if (h == CropHandle.L) from.right else from.left
            val cy = from.centerY
            val room = if (h == CropHandle.L) ax - f.left else f.right - ax
            val roomH = 2.0 * min(cy - f.top, f.bottom - cy)
            var w = (if (h == CropHandle.L) ax - (from.left + dx) else (from.right + dx) - ax)
            w = w.coerceIn(min(m, room), room).coerceAtMost(roomH * a)
            val hh = w / a
            Rect(if (h == CropHandle.L) ax - w else ax, cy - hh / 2.0, w, hh)
        } else {
            val ay = if (h == CropHandle.T) from.bottom else from.top
            val cx = from.centerX
            val room = if (h == CropHandle.T) ay - f.top else f.bottom - ay
            val roomW = 2.0 * min(cx - f.left, f.right - cx)
            var hh = (if (h == CropHandle.T) ay - (from.top + dy) else (from.bottom + dy) - ay)
            hh = hh.coerceIn(min(m, room), room).coerceAtMost(roomW / a)
            val w = hh * a
            Rect(cx - w / 2.0, if (h == CropHandle.T) ay - hh else ay, w, hh)
        }
    }

    private fun near(a: Rect, b: Rect): Boolean {
        val eps = 1e-6 * max(1.0, max(full.w, full.h))
        return abs(a.left - b.left) < eps && abs(a.top - b.top) < eps &&
            abs(a.right - b.right) < eps && abs(a.bottom - b.bottom) < eps
    }
}

/** The crop box's grips: its corners, its edge midpoints, and its inside (to slide it). */
enum class CropHandle(
    val movesLeft: Boolean,
    val movesTop: Boolean,
    val movesRight: Boolean,
    val movesBottom: Boolean,
) {
    TL(true, true, false, false),
    T(false, true, false, false),
    TR(false, true, true, false),
    R(false, false, true, false),
    BR(false, false, true, true),
    B(false, false, false, true),
    BL(true, false, false, true),
    L(true, false, false, false),
    MOVE(false, false, false, false);

    val isCorner: Boolean get() = this == TL || this == TR || this == BL || this == BR
}
