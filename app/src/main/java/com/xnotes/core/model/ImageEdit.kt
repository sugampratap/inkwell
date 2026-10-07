package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import kotlin.math.max
import kotlin.math.min

/**
 * The part of an image's stored pixels that is shown, as fractions of the source: ([l], [t]) to
 * ([r], [b]), each in 0..1, measured on the file's own pixels before any flip or turn.
 *
 * Kept normalised rather than in pixels so that a replaced or re-encoded source (a smaller copy
 * written on import, say) keeps cropping the same region, and so that the renderer can apply it to
 * whichever size bucket it happens to have decoded.
 */
data class ImageCrop(val l: Double, val t: Double, val r: Double, val b: Double) {
    val w: Double get() = r - l
    val h: Double get() = b - t

    /** True when nothing is cut away, which is stored as no crop at all. */
    val isFull: Boolean get() = l <= EPS && t <= EPS && r >= 1.0 - EPS && b >= 1.0 - EPS

    companion object {
        val FULL = ImageCrop(0.0, 0.0, 1.0, 1.0)

        /** Below this a crop edge counts as the image's own edge. */
        const val EPS = 1e-6

        /** The thinnest sliver a crop may keep, as a fraction of the source. */
        const val MIN_FRACTION = 0.01

        /**
         * A crop from any two corners, ordered, clamped into the source and kept at least
         * [MIN_FRACTION] across, so a file from a careless writer still draws something sane.
         */
        fun of(l: Double, t: Double, r: Double, b: Double): ImageCrop {
            var x0 = min(l, r).coerceIn(0.0, 1.0)
            var y0 = min(t, b).coerceIn(0.0, 1.0)
            var x1 = max(l, r).coerceIn(0.0, 1.0)
            var y1 = max(t, b).coerceIn(0.0, 1.0)
            if (x1 - x0 < MIN_FRACTION) {
                x1 = (x0 + MIN_FRACTION).coerceAtMost(1.0)
                x0 = x1 - MIN_FRACTION
            }
            if (y1 - y0 < MIN_FRACTION) {
                y1 = (y0 + MIN_FRACTION).coerceAtMost(1.0)
                y0 = y1 - MIN_FRACTION
            }
            return ImageCrop(x0, y0, x1, y1)
        }
    }
}

/**
 * Everything non-destructive done to an image's pixels short of placing it: the [crop], then a
 * mirror of what is left along its own axes. Turning is not here; it already has its own fields on
 * [ImageItem] (`orientation`, `angle`) and is applied after these.
 *
 * The order is the contract every backend draws by:
 *  1. take the [crop] of the stored pixels (normalised, file orientation),
 *  2. mirror that in its own frame: [flipX] left↔right, [flipY] top↔bottom,
 *  3. turn by the item's quarter-turn orientation, then by its free angle, about the box centre,
 *  4. fill the item's rect, which is the box of the *cropped, turned* image.
 */
data class ImageEdit(
    val crop: ImageCrop? = null,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
) {
    /** The crop worth applying: none when it would keep the whole source. */
    val effectiveCrop: ImageCrop? get() = crop?.takeUnless { it.isFull }

    val isIdentity: Boolean get() = effectiveCrop == null && !flipX && !flipY

    companion object {
        val NONE = ImageEdit()
    }
}

/**
 * The arithmetic between an image's source pixels and what is seen on the page. Pure, so the crop
 * tool, the renderers and the tests all agree on it.
 *
 * "Display" coordinates are normalised over the turned, mirrored image as it appears in its box
 * before any free angle; "source" coordinates are normalised over the stored file.
 */
object ImageGeometry {

    /** A quarter turn in 0, 90, 180 or 270, whatever was stored. */
    fun normOrientation(orientation: Int): Int = (((orientation / 90) % 4 + 4) % 4) * 90

    /** True when a quarter turn swaps the image's width and height. */
    fun swapsAxes(orientation: Int): Boolean = normOrientation(orientation).let { it == 90 || it == 270 }

    /** A source point after the mirror, then the clockwise quarter turn. */
    fun sourceToDisplay(u: Double, v: Double, orientation: Int, flipX: Boolean, flipY: Boolean): Pt {
        val fu = if (flipX) 1.0 - u else u
        val fv = if (flipY) 1.0 - v else v
        return when (normOrientation(orientation)) {
            90 -> Pt(1.0 - fv, fu)
            180 -> Pt(1.0 - fu, 1.0 - fv)
            270 -> Pt(fv, 1.0 - fu)
            else -> Pt(fu, fv)
        }
    }

    /** The inverse of [sourceToDisplay]. */
    fun displayToSource(x: Double, y: Double, orientation: Int, flipX: Boolean, flipY: Boolean): Pt {
        val (fu, fv) = when (normOrientation(orientation)) {
            90 -> y to 1.0 - x
            180 -> 1.0 - x to 1.0 - y
            270 -> 1.0 - y to x
            else -> x to y
        }
        return Pt(if (flipX) 1.0 - fu else fu, if (flipY) 1.0 - fv else fv)
    }

    /** Where a source crop shows up in the displayed image, as a normalised box. */
    fun sourceRectToDisplay(c: ImageCrop, orientation: Int, flipX: Boolean, flipY: Boolean): ImageCrop {
        val a = sourceToDisplay(c.l, c.t, orientation, flipX, flipY)
        val b = sourceToDisplay(c.r, c.b, orientation, flipX, flipY)
        return ImageCrop(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    /** The source crop a normalised box of the displayed image corresponds to. */
    fun displayRectToSource(d: ImageCrop, orientation: Int, flipX: Boolean, flipY: Boolean): ImageCrop {
        val a = displayToSource(d.l, d.t, orientation, flipX, flipY)
        val b = displayToSource(d.r, d.b, orientation, flipX, flipY)
        return ImageCrop(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    /**
     * The shown image's width and height in source pixels: the crop of the source, its axes
     * swapped by a sideways turn. The item's rect keeps this aspect unless it was stretched.
     */
    fun displaySize(srcW: Int, srcH: Int, crop: ImageCrop?, orientation: Int): Pair<Double, Double> {
        val c = crop ?: ImageCrop.FULL
        val w = srcW * c.w
        val h = srcH * c.h
        return if (swapsAxes(orientation)) h to w else w to h
    }

    /**
     * Mirror the *shown* image left↔right ([horizontal]) or top↔bottom, returning the new source
     * flips. The flips act before the quarter turn, so on a sideways image the on-screen
     * horizontal axis is the source's vertical one.
     */
    fun toggleDisplayFlip(orientation: Int, flipX: Boolean, flipY: Boolean, horizontal: Boolean): Pair<Boolean, Boolean> {
        val sourceX = horizontal != swapsAxes(orientation)
        return if (sourceX) !flipX to flipY else flipX to !flipY
    }

    /**
     * The centred crop that makes a [srcW]×[srcH] picture fill a [frameW]×[frameH] frame without
     * stretching ("cover"), or null when the shapes already agree. How a replaced picture takes
     * over its predecessor's frame exactly.
     */
    fun coverCrop(srcW: Int, srcH: Int, frameW: Double, frameH: Double): ImageCrop? {
        if (srcW <= 0 || srcH <= 0 || frameW <= 0.0 || frameH <= 0.0) return null
        val src = srcW.toDouble() / srcH
        val frame = frameW / frameH
        if (kotlin.math.abs(src - frame) / frame < 1e-3) return null
        return if (src > frame) {
            val keep = frame / src
            ImageCrop((1.0 - keep) / 2.0, 0.0, (1.0 + keep) / 2.0, 1.0)
        } else {
            val keep = src / frame
            ImageCrop(0.0, (1.0 - keep) / 2.0, 1.0, (1.0 + keep) / 2.0)
        }
    }
}
