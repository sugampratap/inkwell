package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import com.xnotes.settings.MaterialStyle

/**
 * The Inkwell look (stored as the "paper" colour mode): B2's near-black-on-white chrome with a
 * marigold brand, tuned by hand from docs/mockups/b2/b2-base.css rather than generated. Light is
 * the default; Dark is soft charcoal and OLED true black. The page stays cream paper in all three,
 * so what is drawn on it ([Palette.pageAccent]) stays near-black.
 *
 * Material components still need every role, so the roles this look doesn't decide come from a
 * neutral scheme, and the surface and accent roles are then overwritten with the hand-picked values.
 */
object PaperPalette {

    private val PAPER = hex(0xFFFDF7)
    private val PAGE_INK = Palette.NEAR_BLACK

    fun forAppearance(appearance: String): Palette = when (appearance) {
        "dark" -> dark()
        "oled" -> oled()
        else -> light()
    }

    fun light(): Palette = build(
        Spec(
            bg = 0xFFFFFF, panel = 0xFFFFFF, raised = 0xFFFFFF, surface = 0xF7F7F7, sel = 0xF2F2F2,
            press = 0xEBEBEB, bright = 0xFFFFFF, high = 0xF7F7F7, text = 0x222222, text2 = 0x6A6A6A,
            line = 0xDDDDDD, line2 = 0xEBEBEB, line3 = 0xB0B0B0, solid = 0x222222, onSolid = 0xFFFFFF,
            desk = 0xF3F0EA, paperBorder = 0xE2DFDA, danger = 0xC2261C, dark = false,
        ),
        InkTokens.LIGHT,
    )

    fun dark(): Palette = build(
        Spec(
            bg = 0x161616, panel = 0x1C1C1C, raised = 0x242424, surface = 0x1C1C1C, sel = 0x2E2E2E,
            press = 0x363636, bright = 0x2C2C2C, high = 0x2E2E2E, text = 0xF2F2F2, text2 = 0xA8A8A8,
            line = 0x363636, line2 = 0x2E2E2E, line3 = 0x666666, solid = 0xF2F2F2, onSolid = 0x161616,
            desk = 0x111111, paperBorder = 0x1F1F1F, danger = 0xFF7B72, dark = true,
        ),
        InkTokens.DARK,
    )

    fun oled(): Palette = build(
        Spec(
            bg = 0x000000, panel = 0x121212, raised = 0x1E1E1E, surface = 0x121212, sel = 0x1E1E1E,
            press = 0x2A2A2A, bright = 0x262626, high = 0x262626, text = 0xF2F2F2, text2 = 0xA0A0A0,
            line = 0x2A2A2A, line2 = 0x262626, line3 = 0x5C5C5C, solid = 0xF2F2F2, onSolid = 0x000000,
            desk = 0x000000, paperBorder = 0x0D0D0D, danger = 0xFF7B72, dark = true,
        ),
        InkTokens.OLED,
    )

    /** One appearance's hand-picked values (RGB ints), named after the b2-base.css tokens. */
    private class Spec(
        val bg: Int, val panel: Int, val raised: Int, val surface: Int, val sel: Int, val press: Int,
        val bright: Int, val high: Int, val text: Int, val text2: Int, val line: Int, val line2: Int,
        val line3: Int, val solid: Int, val onSolid: Int, val desk: Int, val paperBorder: Int,
        val danger: Int, val dark: Boolean,
    )

    private fun build(s: Spec, ink: InkTokens): Palette {
        val m = MaterialColors.seeded(hex(s.solid), dark = s.dark, style = MaterialStyle.NEUTRAL).copy(
            primary = hex(s.solid),
            onPrimary = hex(s.onSolid),
            primaryContainer = hex(s.sel),
            onPrimaryContainer = hex(s.text),
            // B2 surfaces are flat: tint with the surface's own colour so tonal elevation adds nothing.
            surfaceTint = hex(s.raised),
            background = hex(s.bg),
            onBackground = hex(s.text),
            surface = hex(s.raised),
            onSurface = hex(s.text),
            surfaceVariant = hex(s.surface),
            onSurfaceVariant = hex(s.text2),
            outline = hex(s.line3),
            outlineVariant = hex(s.line2),
            surfaceBright = hex(s.bright),
            surfaceDim = if (s.dark) hex(s.bg) else hex(s.surface),
            surfaceContainerLowest = hex(s.bg),
            surfaceContainerLow = hex(s.panel),
            surfaceContainer = hex(s.raised),
            surfaceContainerHigh = hex(s.high),
            surfaceContainerHighest = hex(s.press),
            secondaryContainer = hex(s.sel),
            onSecondaryContainer = hex(s.text),
            inverseSurface = hex(s.solid),
            inverseOnSurface = hex(s.onSolid),
            error = hex(s.danger),
            onError = hex(s.onSolid),
        )
        return Palette(
            bg = hex(s.bg),
            panel = hex(s.panel),
            paper = PAPER,
            paperBorder = hex(s.paperBorder),
            accent = hex(s.solid),
            border = hex(s.line),
            text = hex(s.text),
            textDim = hex(s.text2),
            surface = hex(s.surface),
            surfaceHi = hex(s.press),
            menuBg = hex(s.raised),
            isDark = s.dark,
            materialColors = m,
            pageAccent = PAGE_INK,
            desk = hex(s.desk),
            inkTokens = ink,
        )
    }

    private fun hex(rgb: Int): Rgba = Rgba((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, 255)
}

/** The Inkwell look for [appearance] ("light", "dark" or "oled"). */
fun Palette.Companion.paper(appearance: String): Palette = PaperPalette.forAppearance(appearance)
