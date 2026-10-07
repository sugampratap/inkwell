package com.xnotes.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * B2's chrome roles beyond [Palette]'s, from docs/mockups/b2/b2-base.css ("Design tokens"). The
 * Inkwell look sets them by hand ([LIGHT], [DARK], [OLED]); a Material look derives them from its
 * palette ([derive]), so the kit draws the same way whichever look is chosen.
 *
 * Marigold ([brand] and friends) is for hearts, page progress and favourite (New and search went near-black, 2026-10-07)
 * marks only. Everything that is selected or primary inside the chrome uses [solid].
 */
@Immutable
data class InkTokens(
    val bg: Color,
    val sidebar: Color,
    val chrome: Color,
    /** Wells and quiet fills (--surface). */
    val surface: Color,
    /** Popovers, menus, sheets and the toolbar pill (--raised). */
    val raised: Color,
    /** A selected row (--sel). */
    val sel: Color,
    /** A pressed row or button (--press). */
    val press: Color,
    /** Hover and soft-press fill (--hov2). */
    val hover: Color,
    val text: Color,
    val text2: Color,
    /** Placeholders and disabled glyphs only; not for reading text. */
    val text3: Color,
    /** Control outlines (--line). */
    val line: Color,
    /** Hairline separators and the ring round raised surfaces (--line2). */
    val line2: Color,
    /** Dashed and stepper outlines (--line3). */
    val line3: Color,
    /** Behind the pages (--canvas). */
    val canvas: Color,
    /** Near-black selection, strong buttons, the toolbar glider (--solid). */
    val solid: Color,
    val onSolid: Color,
    val badge: Color,
    val badgeInk: Color,
    /** A slider or switch track that is off. */
    val track: Color,
    val toast: Color,
    val toastInk: Color,
    /** The neutral badge an Insert tile's icon sits on (--ibx). */
    val iconBadge: Color,
    val iconBadgePressed: Color,
    val segTrack: Color,
    val segThumb: Color,
    val brand: Color,
    /** Marigold dark enough to read as text. */
    val brandInk: Color,
    val brandSoft: Color,
    val brandStart: Color,
    val brandEnd: Color,
    /** The 3D New button's base. */
    val brandEdge: Color,
    /** Ink-dark text on marigold (5.8:1 at the deep end), like the icon's black pen on orange. */
    val onBrand: Color,
    val danger: Color,
    val scrim: Color,
    /**
     * Colour of the one soft shadow an element may have. Android multiplies its alpha by the platform's
     * own spot (0.19) and ambient (0.04) shadow alphas: 60% matches the mockup's --sh-* on white, and the
     * dark themes are opaque, the deepest the platform allows (about half the mockup's depth on #161616).
     */
    val shadow: Color,
    val isDark: Boolean,
) {
    companion object {
        val LIGHT = InkTokens(
            bg = Color(0xFFFFFFFF), sidebar = Color(0xFFFFFFFF), chrome = Color(0xFFFFFFFF),
            surface = Color(0xFFF7F7F7), raised = Color(0xFFFFFFFF),
            sel = Color(0xFFF2F2F2), press = Color(0xFFEBEBEB), hover = Color(0xFFF7F7F7),
            text = Color(0xFF222222), text2 = Color(0xFF6A6A6A), text3 = Color(0xFF9A9A9A),
            line = Color(0xFFDDDDDD), line2 = Color(0xFFEBEBEB), line3 = Color(0xFFB0B0B0),
            canvas = Color(0xFFF3F0EA), solid = Color(0xFF222222), onSolid = Color(0xFFFFFFFF),
            badge = Color(0xF5FFFFFF), badgeInk = Color(0xFF222222), track = Color(0xFFDDDDDD),
            toast = Color(0xFF222222), toastInk = Color(0xFFFFFFFF),
            iconBadge = Color(0xFFF2F2F2), iconBadgePressed = Color(0xFFE8E8E8),
            segTrack = Color(0xFFEFEFEF), segThumb = Color(0xFFFFFFFF),
            brand = Color(0xFFE8821C), brandInk = Color(0xFFB05309), brandSoft = Color(0xFFFFF1DF),
            brandStart = Color(0xFFFFB43C), brandEnd = Color(0xFFE8821C), brandEdge = Color(0xFFC2610C),
            onBrand = Color(0xFF1F1408), danger = Color(0xFFC2261C),
            scrim = Color(0x52000000), shadow = Color(0x99000000), isDark = false,
        )

        /** Soft charcoal. */
        val DARK = InkTokens(
            bg = Color(0xFF161616), sidebar = Color(0xFF1C1C1C), chrome = Color(0xFF1C1C1C),
            surface = Color(0xFF1C1C1C), raised = Color(0xFF242424),
            sel = Color(0xFF2E2E2E), press = Color(0xFF363636), hover = Color(0xFF2C2C2C),
            text = Color(0xFFF2F2F2), text2 = Color(0xFFA8A8A8), text3 = Color(0xFF808080),
            line = Color(0xFF363636), line2 = Color(0xFF2E2E2E), line3 = Color(0xFF666666),
            canvas = Color(0xFF111111), solid = Color(0xFFF2F2F2), onSolid = Color(0xFF161616),
            badge = Color(0xF01C1C1C), badgeInk = Color(0xFFF2F2F2), track = Color(0xFF444444),
            toast = Color(0xFFF2F2F2), toastInk = Color(0xFF161616),
            iconBadge = Color(0xFF333333), iconBadgePressed = Color(0xFF3C3C3C),
            segTrack = Color(0xFF333333), segThumb = Color(0xFF5C5C5C),
            brand = Color(0xFFFFA238), brandInk = Color(0xFFFFB45C), brandSoft = Color(0xFF2E1F0D),
            brandStart = Color(0xFFFFB43C), brandEnd = Color(0xFFF08A24), brandEdge = Color(0xFFA04E0A),
            onBrand = Color(0xFF1F1408), danger = Color(0xFFFF7B72),
            scrim = Color(0x9E000000), shadow = Color.Black, isDark = true,
        )

        /** True black for OLED screens; the small lifts keep a step so they still read. */
        val OLED = InkTokens(
            bg = Color(0xFF000000), sidebar = Color(0xFF121212), chrome = Color(0xFF121212),
            surface = Color(0xFF121212), raised = Color(0xFF1E1E1E),
            sel = Color(0xFF1E1E1E), press = Color(0xFF2A2A2A), hover = Color(0xFF262626),
            text = Color(0xFFF2F2F2), text2 = Color(0xFFA0A0A0), text3 = Color(0xFF7C7C7C),
            line = Color(0xFF2A2A2A), line2 = Color(0xFF262626), line3 = Color(0xFF5C5C5C),
            canvas = Color(0xFF000000), solid = Color(0xFFF2F2F2), onSolid = Color(0xFF000000),
            badge = Color(0xF0121212), badgeInk = Color(0xFFF2F2F2), track = Color(0xFF3A3A3A),
            toast = Color(0xFFF2F2F2), toastInk = Color(0xFF000000),
            iconBadge = Color(0xFF2A2A2A), iconBadgePressed = Color(0xFF333333),
            segTrack = Color(0xFF2C2C2C), segThumb = Color(0xFF5A5A5A),
            brand = Color(0xFFFFA238), brandInk = Color(0xFFFFB45C), brandSoft = Color(0xFF2E1F0D),
            brandStart = Color(0xFFFFB43C), brandEnd = Color(0xFFF08A24), brandEdge = Color(0xFFA04E0A),
            onBrand = Color(0xFF1F1408), danger = Color(0xFFFF7B72),
            scrim = Color(0x9E000000), shadow = Color.Black, isDark = true,
        )

        /** Tokens for a Material look: the palette's own roles, with its primary as both solid and brand. */
        fun derive(p: Palette): InkTokens {
            val m = p.materialColors
            val accent = p.accent.toComposeColor()
            return InkTokens(
                bg = p.bg.toComposeColor(), sidebar = p.panel.toComposeColor(), chrome = p.panel.toComposeColor(),
                surface = p.surface.toComposeColor(), raised = p.menuBg.toComposeColor(),
                sel = p.selectionBackground.toComposeColor(), press = p.surfaceHi.toComposeColor(),
                hover = p.surface.toComposeColor(),
                text = p.text.toComposeColor(), text2 = p.textDim.toComposeColor(),
                text3 = ColorMath.mix(p.textDim, p.bg, 0.35).toComposeColor(),
                line = p.border.toComposeColor(), line2 = ColorMath.mix(p.border, p.menuBg, 0.5).toComposeColor(),
                line3 = m.outline.toComposeColor(),
                canvas = p.desk.toComposeColor(), solid = accent, onSolid = p.onAccent.toComposeColor(),
                badge = p.menuBg.toComposeColor().copy(alpha = 0.95f), badgeInk = p.text.toComposeColor(),
                track = p.border.toComposeColor(),
                toast = m.inverseSurface.toComposeColor(), toastInk = m.inverseOnSurface.toComposeColor(),
                iconBadge = p.surface.toComposeColor(), iconBadgePressed = p.surfaceHi.toComposeColor(),
                segTrack = p.surface.toComposeColor(),
                segThumb = if (p.isDark) m.surfaceContainerHighest.toComposeColor() else p.menuBg.toComposeColor(),
                brand = accent, brandInk = accent, brandSoft = p.selectionBackground.toComposeColor(),
                brandStart = accent, brandEnd = accent, brandEdge = ColorMath.darken(p.accent, 0.25).toComposeColor(),
                onBrand = p.onAccent.toComposeColor(), danger = p.danger.toComposeColor(),
                scrim = Color.Black.copy(alpha = if (p.isDark) 0.62f else 0.32f),
                shadow = if (p.isDark) Color.Black else Color(0x99000000),
                isDark = p.isDark,
            )
        }
    }
}
