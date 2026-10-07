package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ink that reads on the paper it lands on.
 *
 * A colour is chosen once and written on many pages: white ink picked for a black OLED page is
 * invisible on a white one, and the default navy is nearly invisible on black. Samsung Notes swaps
 * such colours for their light or dark counterpart depending on the page; this does the same. A
 * colour that already stands out from the paper is left exactly as it was picked; one too close to
 * it keeps its hue and saturation and moves its lightness to the far side of the page's, so a navy
 * becomes a pale blue on black, and white becomes near-black on white.
 */
object InkContrast {

    /** Contrast ratio (WCAG) under which ink counts as lost on its paper. */
    const val MIN_CONTRAST = 1.8

    /** Relative luminance, 0 for black to 1 for white. */
    fun luminance(c: Rgba): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.r) + 0.7152 * ch(c.g) + 0.0722 * ch(c.b)
    }

    fun contrast(a: Rgba, b: Rgba): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Whether [paper] is a dark page: light ink is what reads on it. */
    fun isDark(paper: Rgba): Boolean = luminance(paper) < 0.18

    /**
     * [ink] as it should be written on [paper]: itself when it reads, its counterpart when not.
     * "Reads" is [minContrast]: ink's own [MIN_CONTRAST] unless the caller holds a thinner mark,
     * such as a hairline of chrome, to a stricter ratio.
     */
    fun forPaper(ink: Rgba, paper: Rgba, minContrast: Double = MIN_CONTRAST): Rgba {
        if (contrast(ink, paper) >= minContrast) return ink
        val (h, s, l) = hsl(ink)
        val dark = isDark(paper)
        // How far the colour is from grey at all (max channel less min, 0..1). Near white or black, HSL saturation
        // reads a sliver of tint as a full one: a cream #FFFDF7 has s = 1, and moved to the light page's 0.36 it
        // came out a strong ochre (#B78900) rather than near-black.
        val chroma = chroma(ink)
        val grey = s < 0.12 || chroma < GREY_CHROMA
        val target = when {
            // A grey has no hue to keep, so it goes all the way: white on white turns ink-black.
            grey -> if (dark) 0.94 else 0.10
            dark -> max(l, 0.72)
            else -> min(l, 0.36)
        }
        // A near-grey keeps its tint, not its HSL saturation: the same chroma at the new lightness.
        val span = 1.0 - kotlin.math.abs(2.0 * target - 1.0)
        val sat = if (grey && span > 0.0) min(s, chroma / span) else s
        return fromHsl(h, sat, target, ink.a)
    }

    /** Under this chroma (about 15 of 255 between the strongest and weakest channel) a colour counts as a grey. */
    private const val GREY_CHROMA = 0.06

    private fun chroma(c: Rgba): Double = (max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b))) / 255.0

    /** The ink a fresh pen starts with on [paper]: the classic navy, or near-white on a dark page. */
    fun defaultInk(paper: Rgba): Rgba = forPaper(InkPalette.INK, paper)

    private fun hsl(c: Rgba): Triple<Double, Double, Double> {
        val r = c.r / 255.0
        val g = c.g / 255.0
        val b = c.b / 255.0
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2.0
        if (hi == lo) return Triple(0.0, 0.0, l)
        val d = hi - lo
        val s = if (l > 0.5) d / (2.0 - hi - lo) else d / (hi + lo)
        val h = when (hi) {
            r -> ((g - b) / d + (if (g < b) 6.0 else 0.0))
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        } / 6.0
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Double, s: Double, l: Double, a: Int): Rgba {
        if (s == 0.0) {
            val v = (l * 255).toInt().coerceIn(0, 255)
            return Rgba(v, v, v, a)
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(t0: Double): Double {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        fun ch(v: Double) = (v * 255).toInt().coerceIn(0, 255)
        return Rgba(ch(hue(h + 1.0 / 3)), ch(hue(h)), ch(hue(h - 1.0 / 3)), a)
    }
}
