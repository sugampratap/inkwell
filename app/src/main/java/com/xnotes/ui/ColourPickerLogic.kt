package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkContrast
import com.xnotes.ui.theme.ColorMath
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The shared colour picker's pure rules (r2_selection_colour Frames 4-5, r3_text's wide picker): the live colour's
 * HSV, the eyedropper's sample maths, and where the card opens. Window px in and out; [dp] is px per dp, so a test
 * passes 1f and reads the mockup's numbers straight.
 */

/** The picker's live colour as hue (0..360), saturation and value (0..1). */
internal data class PickerHsv(val h: Double, val s: Double, val v: Double)

/**
 * [c] as the picker shows it after [prev] (SC 430): its own hue, saturation and value, except that a grey (no
 * saturation) keeps [prev]'s hue and black (no value) keeps [prev]'s hue and saturation, so the thumbs never jump
 * home when a drag reaches the pad's foot or a grey.
 */
internal fun keepHueThroughGreys(prev: PickerHsv, c: Rgba): PickerHsv {
    val hsv = ColorMath.rgbToHsv(c)
    val h = if (hsv[1] > 1e-4 && hsv[2] > 1e-4) hsv[0] else prev.h
    val s = if (hsv[2] > 1e-4) hsv[1] else prev.s
    return PickerHsv(h, s, hsv[2])
}

/**
 * A sampled pixel ([argb], unpremultiplied, as Bitmap.getPixel gives it) as the opaque colour the picker deals in
 * (SC 327): whatever alpha it has is composited over [under], and the result is always fully opaque.
 */
internal fun opaqueSample(argb: Int, under: Rgba): Rgba {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    if (a == 255) return Rgba(r, g, b)
    fun over(top: Int, bottom: Int): Int = ((top * a + bottom * (255 - a)) / 255.0).roundToInt().coerceIn(0, 255)
    return Rgba(over(r, under.r), over(g, under.g), over(b, under.b))
}

/**
 * The view-local pixel under screen px ([screenX], [screenY]) for a view whose top-left is at ([left], [top]) on the
 * screen and which is [width] × [height]; null when the point is off the view (its right and bottom edges excluded).
 */
internal fun viewPixelAt(screenX: Int, screenY: Int, left: Int, top: Int, width: Int, height: Int): IntOffset? {
    val x = screenX - left
    val y = screenY - top
    return if (x in 0 until width && y in 0 until height) IntOffset(x, y) else null
}

/** The page-space square one viewport pixel across, centred on [p], at [zoom]: what the eyedropper renders on a note. */
internal fun eyedropperProbe(p: Pt, zoom: Double): Rect {
    val half = 0.5 / zoom.coerceAtLeast(1e-6)
    return Rect(p.x - half, p.y - half, 2 * half, 2 * half)
}

/**
 * Frame 4 (SC 476-477): under a toolbar swatch ([anchor]), starting 40 dp before its centre, 10 dp past the bar's
 * edge ([edge]). It flips and turns with the bar ([prefer]): above a bottom bar, beside a side rail.
 */
internal fun pickerUnderSwatch(anchor: IntRect, edge: IntRect, window: IntSize, content: IntSize, prefer: PopoverSide, dp: Float): PopoverPlacement {
    val across = if (prefer == PopoverSide.BELOW || prefer == PopoverSide.ABOVE) anchor.width else anchor.height
    val spec = PopoverSpec(lead = px(40, dp) - across / 2, gap = px(10, dp), margin = px(8, dp))
    return placePopover(anchor, edge, window, content, spec, prefer)
}

/**
 * With no card to stand beside and no bar (Settings, a sheet): under the dot, 8 dp before it and 8 below, as a header
 * menu hangs; over it (8 dp above) when only that fits. When neither fits (a dot halfway down a sheet), beside the dot
 * as [pickerBesideCard] stands beside a card, so it is never clamped back over its own dot.
 */
internal fun pickerUnderAnchor(anchor: IntRect, window: IntSize, content: IntSize, dp: Float): PopoverPlacement {
    val m = px(8, dp)
    val gap = px(8, dp)
    val fitsBelow = anchor.bottom + gap + content.height <= window.height - m
    val fitsAbove = anchor.top - gap - content.height >= m
    if (!fitsBelow && !fitsAbove) return pickerBesideCard(anchor, anchor, window, content, dp)
    return placePopover(anchor, anchor, window, content, PopoverSpec(px(8, dp), gap, m), PopoverSide.BELOW)
}

/**
 * Frame 5 (SC 478, TO 592): beside the [card] it was opened from, never over it: 12 dp off the card's end with its top
 * on the card's top; else 12 dp before the card's start; else clamped inside the window (TX 1008). Kept 8 dp inside
 * the window vertically; it grows from the dot ([anchor]) on the side it opened.
 */
internal fun pickerBesideCard(anchor: IntRect, card: IntRect, window: IntSize, content: IntSize, dp: Float): PopoverPlacement {
    val w = content.width
    val h = content.height
    val m = px(8, dp)
    val end = card.right + px(12, dp)
    val start = card.left - px(12, dp) - w
    val (side, x) = when {
        end + w <= window.width - m -> PopoverSide.END to end
        start >= m -> PopoverSide.START to start
        else -> PopoverSide.END to inside(end, w, window.width, m)
    }
    val y = inside(card.top, h, window.height, m)
    val originY = fraction((anchor.top + anchor.bottom) / 2f - y, h)
    return PopoverPlacement(x, y, side, if (side == PopoverSide.END) 0f else 1f, originY)
}

/**
 * The wide picker over the text format pill (TX 892-895): 10 dp above the pill ([pill]), centred on its button. When
 * that would cover the selection ([avoid]; within 4 dp across, or less than 24 dp above its foot), it goes 16 dp after
 * the selection, else 8 dp before it; when every spot covers, centred. It grows from the button, held 24 dp inside.
 */
internal fun pickerAbovePill(button: IntRect, pill: IntRect, avoid: IntRect?, window: IntSize, content: IntSize, dp: Float): PopoverPlacement {
    val w = content.width
    val h = content.height
    val m = px(8, dp)
    val y = max(m, pill.top - px(10, dp) - h)
    val cx = (button.left + button.right) / 2
    val spots = mutableListOf(inside(cx - w / 2, w, window.width, m))
    if (avoid != null) {
        spots += inside(avoid.right + px(16, dp), w, window.width, m)
        spots += inside(avoid.left - px(8, dp) - w, w, window.width, m)
    }
    fun covers(x: Int): Boolean =
        avoid != null && x < avoid.right + px(4, dp) && x + w > avoid.left - px(4, dp) && y < avoid.bottom + px(24, dp)
    val x = spots.firstOrNull { !covers(it) } ?: spots[0]
    val inset = px(24, dp)
    val ox = (cx - x).coerceIn(inset, max(inset, w - inset))
    return PopoverPlacement(x, y, PopoverSide.ABOVE, fraction(ox.toFloat(), w), 1f)
}

/**
 * Change style's + (SC 606-608): 12 dp off the selection bar's end ([bar]), its top at [top] (the toolbar line), kept
 * inside the window. It grows from the + ([anchor]), held 20 to 560 dp down the card. When there is no room past the
 * bar's end (a narrow or right-hand pane), or that spot would still lie over [hostCard] (the Change style card, which
 * can reach past a short bar's end), it opens beside that card, as [pickerBesideCard] places it, rather than over the
 * card and its lit +.
 */
internal fun pickerBesideBar(
    anchor: IntRect,
    bar: IntRect,
    top: Int,
    window: IntSize,
    content: IntSize,
    dp: Float,
    hostCard: IntRect? = null,
): PopoverPlacement {
    val m = px(8, dp)
    val end = bar.right + px(12, dp)
    if (hostCard != null && end + content.width > window.width - m) return pickerBesideCard(anchor, hostCard, window, content, dp)
    val x = inside(end, content.width, window.width, m)
    val y = inside(top, content.height, window.height, m)
    if (hostCard != null && x < hostCard.right && x + content.width > hostCard.left && y < hostCard.bottom && y + content.height > hostCard.top) {
        return pickerBesideCard(anchor, hostCard, window, content, dp)
    }
    val lo = px(20, dp)
    val hi = max(lo, min(px(560, dp), content.height))
    val oy = ((anchor.top + anchor.bottom) / 2 - y).coerceIn(lo, hi)
    return PopoverPlacement(x, y, PopoverSide.END, 0f, fraction(oy.toFloat(), content.height))
}

/**
 * Whether a favourite or recent dot of [colour] needs a light ring on the card: in a dark look ([dark]) when it is
 * under 3:1 against the card (relative luminance [cardLuminance]), the WCAG bar for a control's edge, as navy and black
 * are on the dark and OLED cards. A light look's dark ring is enough as it is.
 */
internal fun needsLightRing(colour: Rgba, cardLuminance: Float, dark: Boolean): Boolean {
    if (!dark) return false
    val l = InkContrast.luminance(colour)
    val hi = max(l, cardLuminance.toDouble())
    val lo = min(l, cardLuminance.toDouble())
    return (hi + 0.05) / (lo + 0.05) < 3.0
}

private fun px(v: Int, dp: Float): Int = (v * dp).roundToInt()

/** [start] moved so a [size] span stays [margin] inside [0, extent]; a span too big for that starts at [margin]. */
private fun inside(start: Int, size: Int, extent: Int, margin: Int): Int = max(margin, min(start, extent - margin - size))

/** [p] along a [size] span as a fraction, kept on the span. */
private fun fraction(p: Float, size: Int): Float = if (size <= 0) 0.5f else (p / size).coerceIn(0f, 1f)
