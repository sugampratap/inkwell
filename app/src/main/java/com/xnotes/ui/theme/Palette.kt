package com.xnotes.ui.theme

import androidx.compose.runtime.Immutable
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkContrast
import com.xnotes.settings.Preferences
import kotlin.math.abs

/** The design tokens for one appearance (spec 11 §1), built from a Material 3 scheme. */
@Immutable
data class Palette(
    val bg: Rgba,
    val panel: Rgba,
    val paper: Rgba,
    val paperBorder: Rgba,
    val accent: Rgba,
    val border: Rgba,
    val text: Rgba,
    val textDim: Rgba,
    val surface: Rgba,
    val surfaceHi: Rgba,
    val menuBg: Rgba,
    val isDark: Boolean,
    val materialColors: MaterialColors,
    /**
     * What on-page chrome (selection marquee and grips, text selection and handles, table cell
     * outlines, search hits) is drawn in. The page is paper, not chrome: under dark chrome the
     * chrome's own accent would vanish on it.
     */
    val pageAccent: Rgba = accent,
    /** The desk the pages lie on in the paged editor. */
    val desk: Rgba = bg,
    /**
     * A designed look's B2 tokens; null derives them from the fields above ([InkTokens.derive]).
     * Defaults are taken at construction, so a copy() that changes bg or accent must pass
     * desk / pageAccent too, and a copy() of a designed look must pass its own inkTokens.
     */
    val inkTokens: InkTokens? = null,
) {
    /** The B2 kit's colours for this palette: [inkTokens] when a designed look sets them, else derived from this palette's own roles. */
    val ink: InkTokens by lazy { inkTokens ?: InkTokens.derive(this) }

    fun accentAlpha(alpha: Int): Rgba = accent.withAlpha(alpha)

    /**
     * [pageAccent] as it reads on [paper]: itself on normal paper, its light counterpart on a dark
     * page. Held to [CHROME_MIN_CONTRAST] rather than ink's ratio, since a 1.3px dashed frame
     * vanishes on a mid-dark page that a pen stroke still shows on.
     *
     * The counterpart keeps the accent's hue and picks a lightness, not a contrast, so on a
     * mid-tone page it can read worse than the accent it replaced. Where it still falls short, the
     * chrome takes whichever of the accent, the counterpart, near-black and white stands out most.
     */
    fun pageAccentOn(paper: Rgba): Rgba {
        val swapped = InkContrast.forPaper(pageAccent, paper, CHROME_MIN_CONTRAST)
        if (InkContrast.contrast(swapped, paper) >= CHROME_MIN_CONTRAST) return swapped
        var best = swapped
        var bestRatio = InkContrast.contrast(swapped, paper)
        for (c in arrayOf(pageAccent, NEAR_BLACK, WHITE)) {
            val ratio = InkContrast.contrast(c, paper)
            if (ratio > bestRatio) {
                best = c
                bestRatio = ratio
            }
        }
        return best
    }

    val onAccent: Rgba get() = materialColors.onPrimary
    val selectionBackground: Rgba get() = materialColors.primaryContainer
    val selectionForeground: Rgba get() = materialColors.onPrimaryContainer

    /** Material's disabled content: onSurface at 38%. */
    val disabled: Rgba get() = materialColors.onSurface.withAlpha(97)

    /** The destructive tint, for delete actions sat among ordinary ones. */
    val danger: Rgba get() = materialColors.error

    /**
     * Whether the default page is dark, which is not the same as the chrome being dark: the Paper
     * look keeps its pages paper-coloured under dark chrome, as a notebook stays white in a dim
     * room. What is drawn *on* the page follows this; what is drawn around it follows [isDark].
     */
    val paperIsDark: Boolean
        get() = (0.299 * paper.r + 0.587 * paper.g + 0.114 * paper.b) / 255.0 < 0.5

    companion object {
        /** Stands in wherever no theme is provided yet (and in tests). */
        val DEFAULT: Palette by lazy { materialDark(MaterialColors.seeded(Preferences.DEFAULT_MATERIAL_DUAL, dark = true)) }

        /** The ratio on-page chrome is held to against its paper: WCAG's for non-text marks. */
        const val CHROME_MIN_CONTRAST = 3.0

        /** B2's white: a resize grip's face, and what reads on a dark accent fill. */
        val WHITE = Rgba(0xFF, 0xFF, 0xFF, 255)

        /** B2's near-black (#222): the Inkwell page accent, and what reads on a light accent fill. */
        val NEAR_BLACK = Rgba(0x22, 0x22, 0x22, 255)

        /**
         * What is drawn on a [fill] of the page accent (a grip's face, the + on an insert button):
         * white, or near-black where the accent has turned light for a dark page, whichever stands
         * out more. Either way at least 3.9:1.
         */
        fun onFill(fill: Rgba): Rgba =
            if (InkContrast.contrast(WHITE, fill) >= InkContrast.contrast(NEAR_BLACK, fill)) WHITE else NEAR_BLACK

        /** Build the full chrome from a Material 3 scheme's tonal surfaces. */
        fun material(appearance: String, m: MaterialColors): Palette = when (appearance) {
            "light" -> materialLight(m)
            "oled" -> materialOled(m)
            else -> materialDark(m)
        }

        /** Light sits a few tone steps below white so panels and the backstage keep visible
         *  depth against white paper instead of washing out near the top of the ladder. */
        fun materialLight(m: MaterialColors): Palette = Palette(
            bg = m.surfaceContainerHighest,
            panel = m.surfaceContainerHigh,
            paper = m.surfaceContainerLowest,
            paperBorder = m.outline,
            accent = m.primary,
            // Raw outlineVariant reads too hard against the chrome; sink it a third of the way in.
            border = ColorMath.mix(m.outlineVariant, m.surfaceContainer, 0.35),
            text = m.onSurface,
            textDim = m.onSurfaceVariant,
            surface = m.surfaceDim,
            surfaceHi = ColorMath.darken(m.surfaceDim, 0.06),
            menuBg = m.surfaceContainer,
            isDark = false,
            materialColors = m,
        )

        fun materialDark(m: MaterialColors): Palette = Palette(
            bg = m.surfaceContainerLowest,
            panel = m.surface,
            paper = m.surfaceContainerLow,
            paperBorder = m.outlineVariant,
            accent = m.primary,
            // Raw outlineVariant reads too hard against the chrome; sink it a third of the way in.
            border = ColorMath.mix(m.outlineVariant, m.surfaceContainerLowest, 0.35),
            text = m.onSurface,
            textDim = m.onSurfaceVariant,
            surface = m.surfaceContainerHigh,
            surfaceHi = m.surfaceContainerHighest,
            menuBg = m.surfaceContainerLow,
            isDark = true,
            materialColors = m,
        )

        /** Material OLED: the big surfaces drop to pure black, small lifts step down one slot. */
        fun materialOled(m: MaterialColors): Palette {
            val black = hex(0x000000)
            return materialDark(m).copy(
                bg = black,
                panel = black,
                paper = black,
                desk = black,
                menuBg = black,
                surface = m.surfaceContainerLow,
                surfaceHi = m.surfaceContainer,
            )
        }

        private fun hex(rgb: Int): Rgba =
            Rgba((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, 255)
    }
}

/** Small pure colour math for accent derivations (spec 11 §1). */
object ColorMath {

    /**
     * Cap an accent's perceived luminance for the light theme. Colours brighter
     * than [maxLuminance] are scaled down in place (hue and saturation kept),
     * so a bright yellow deepens to gold; colours already dark enough pass through.
     */
    fun darkenForLight(c: Rgba, maxLuminance: Double = 0.35): Rgba {
        val lum = (0.299 * c.r + 0.587 * c.g + 0.114 * c.b) / 255.0
        if (lum <= maxLuminance) return c
        val k = maxLuminance / lum
        fun ch(v: Int) = (v * k).toInt().coerceIn(0, 255)
        return Rgba(ch(c.r), ch(c.g), ch(c.b), c.a)
    }


    /** Darken toward black by [amount] (0..1). */
    fun darken(c: Rgba, amount: Double): Rgba {
        val a = amount.coerceIn(0.0, 1.0)
        fun mix(v: Int) = (v * (1 - a)).toInt().coerceIn(0, 255)
        return Rgba(mix(c.r), mix(c.g), mix(c.b), c.a)
    }

    /** Linear mix of [a] toward [b] by [t] (0..1). */
    fun mix(a: Rgba, b: Rgba, t: Double): Rgba {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(x: Int, y: Int) = (x + (y - x) * k).toInt().coerceIn(0, 255)
        return Rgba(ch(a.r, b.r), ch(a.g, b.g), ch(a.b, b.b), ch(a.a, b.a))
    }

    fun rgbToHsv(c: Rgba): DoubleArray {
        val r = c.r / 255.0
        val g = c.g / 255.0
        val b = c.b / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        var h = when {
            delta < 1e-9 -> 0.0
            max == r -> 60.0 * (((g - b) / delta) % 6.0)
            max == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        if (h < 0) h += 360.0
        val s = if (max < 1e-9) 0.0 else delta / max
        return doubleArrayOf(h, s, max)
    }

    fun hsvToRgb(h: Double, s: Double, v: Double, alpha: Int = 255): Rgba {
        val c = v * s
        val x = c * (1 - abs((h / 60.0) % 2 - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun ch(v0: Double) = ((v0 + m) * 255).toInt().coerceIn(0, 255)
        return Rgba(ch(r1), ch(g1), ch(b1), alpha)
    }
}
