package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Paper look is chosen by hand, so nothing generates the contrast for it: these hold every
 * appearance to WCAG's ratios, so a later tweak to a warm grey cannot quietly make labels unreadable.
 */
class PaperPaletteTest {

    private fun luminance(c: Rgba): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.r) + 0.7152 * ch(c.g) + 0.0722 * ch(c.b)
    }

    private fun contrast(a: Rgba, b: Rgba): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val all = listOf("light" to PaperPalette.light(), "dark" to PaperPalette.dark(), "oled" to PaperPalette.oled())

    @Test
    fun bodyTextIsReadableOnEverySurface() {
        for ((name, p) in all) {
            for (surface in listOf(p.bg, p.panel, p.menuBg, p.surface)) {
                assertTrue("$name text on $surface", contrast(p.text, surface) >= 7.0)
            }
        }
    }

    /**
     * The page stays paper under every appearance, so the pen box's inks read on it whatever the
     * chrome is doing: dark ink on a dark page is the failure this guards against.
     */
    @Test
    fun theDefaultInksReadOnThePageInEveryAppearance() {
        val inks = listOf(
            com.xnotes.core.tools.InkPalette.INK,
            com.xnotes.core.tools.InkPalette.PEN_BLUE,
            com.xnotes.core.tools.InkPalette.PEN_RED,
            com.xnotes.core.tools.InkPalette.PEN_GREEN,
            com.xnotes.core.tools.InkPalette.PEN_VIOLET,
        )
        for ((name, p) in all) {
            assertFalse("$name paper", p.paperIsDark)
            // Ink is a graphic, held to WCAG's 3:1 for non-text marks; the everyday ink to more.
            for (ink in inks) assertTrue("$name ink $ink on paper", contrast(ink, p.paper) >= 3.0)
            assertTrue("$name everyday ink", contrast(com.xnotes.core.tools.InkPalette.INK, p.paper) >= 7.0)
        }
    }

    @Test
    fun secondaryTextMeetsAa() {
        for ((name, p) in all) {
            for (surface in listOf(p.bg, p.panel, p.menuBg)) {
                assertTrue("$name textDim on $surface", contrast(p.textDim, surface) >= 4.5)
            }
        }
    }

    @Test
    fun accentReadsAsTextAndCarriesItsLabel() {
        for ((name, p) in all) {
            assertTrue("$name accent on menu", contrast(p.accent, p.menuBg) >= 4.5)
            assertTrue("$name accent on panel", contrast(p.accent, p.panel) >= 4.5)
            assertTrue("$name onAccent", contrast(p.onAccent, p.accent) >= 4.5)
            assertTrue(
                "$name selection",
                contrast(p.selectionForeground, p.selectionBackground) >= 4.5,
            )
        }
    }

    @Test
    fun appearancesAreWhatTheySay() {
        assertFalse(PaperPalette.light().isDark)
        assertTrue(PaperPalette.dark().isDark)
        assertTrue(PaperPalette.oled().isDark)
        assertEquals(Rgba(0, 0, 0, 255), PaperPalette.oled().bg)
        assertTrue(PaperPalette.dark().isDark && !PaperPalette.dark().paperIsDark)
        assertEquals(PaperPalette.light(), Palette.paper("light"))
        assertEquals(PaperPalette.dark(), Palette.paper("dark"))
        assertEquals(PaperPalette.oled(), Palette.paper("oled"))
        // Anything unrecognised is the default look.
        assertEquals(PaperPalette.light(), Palette.paper("system"))
    }

    @Test
    fun theInkwellLookIsTheB2Mockup() {
        assertEquals(Rgba(0xFF, 0xFF, 0xFF, 255), PaperPalette.light().bg)
        assertEquals(Rgba(0x22, 0x22, 0x22, 255), PaperPalette.light().accent)
        assertEquals(Rgba(0x16, 0x16, 0x16, 255), PaperPalette.dark().bg)
        assertEquals(Rgba(0xF3, 0xF0, 0xEA, 255), PaperPalette.light().desk)
        assertEquals(Rgba(0x11, 0x11, 0x11, 255), PaperPalette.dark().desk)
        assertEquals(Rgba(0, 0, 0, 255), PaperPalette.oled().desk)
        for ((name, p) in all) {
            assertEquals("$name paper", Rgba(0xFF, 0xFD, 0xF7, 255), p.paper)
            assertEquals("$name page ink", Rgba(0x22, 0x22, 0x22, 255), p.pageAccent)
            assertTrue("$name page ink on paper", contrast(p.pageAccent, p.paper) >= 7.0)
        }
        assertSame(InkTokens.LIGHT, PaperPalette.light().ink)
        assertSame(InkTokens.DARK, PaperPalette.dark().ink)
        assertSame(InkTokens.OLED, PaperPalette.oled().ink)
    }

    /**
     * [PaperPalette]'s values and [InkTokens]' are two hand-typed copies of b2-base.css, so this
     * holds each Palette role to its token twin: a number fixed in one place and not the other fails here.
     */
    @Test
    fun thePaletteRolesMatchTheirInkTokens() {
        for ((name, p) in all) {
            val t = p.ink
            assertEquals("$name accent", t.solid, p.accent.toComposeColor())
            assertEquals("$name onAccent", t.onSolid, p.onAccent.toComposeColor())
            assertEquals("$name desk", t.canvas, p.desk.toComposeColor())
            assertEquals("$name bg", t.bg, p.bg.toComposeColor())
            assertEquals("$name panel", t.sidebar, p.panel.toComposeColor())
            assertEquals("$name menuBg", t.raised, p.menuBg.toComposeColor())
            assertEquals("$name surface", t.surface, p.surface.toComposeColor())
            assertEquals("$name selection", t.sel, p.selectionBackground.toComposeColor())
            assertEquals("$name surfaceHi", t.press, p.surfaceHi.toComposeColor())
            assertEquals("$name text", t.text, p.text.toComposeColor())
            assertEquals("$name textDim", t.text2, p.textDim.toComposeColor())
            assertEquals("$name border", t.line, p.border.toComposeColor())
            assertEquals("$name danger", t.danger, p.danger.toComposeColor())
        }
    }
}
