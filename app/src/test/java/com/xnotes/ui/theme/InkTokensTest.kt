package com.xnotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * B2's tokens are copied by hand from docs/mockups/b2/b2-base.css. These hold them to the mockup
 * and to WCAG, so a later tweak can't quietly make a label unreadable.
 */
class InkTokensTest {

    private fun luminance(c: Color): Double {
        val x = Rgba.fromArgb(c.toArgb())
        fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(x.r) + 0.7152 * ch(x.g) + 0.0722 * ch(x.b)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val looks = listOf("light" to InkTokens.LIGHT, "dark" to InkTokens.DARK, "oled" to InkTokens.OLED)

    @Test
    fun lightIsTheMockup() {
        val t = InkTokens.LIGHT
        assertEquals(Color(0xFFFFFFFF), t.bg)
        assertEquals(Color(0xFF222222), t.text)
        assertEquals(Color(0xFF6A6A6A), t.text2)
        assertEquals(Color(0xFFDDDDDD), t.line)
        assertEquals(Color(0xFFEBEBEB), t.line2)
        assertEquals(Color(0xFF222222), t.solid)
        assertEquals(Color(0xFFF3F0EA), t.canvas)
        assertEquals(Color(0xFFE8821C), t.brand)
        assertEquals(Color(0xFFFFB43C), t.brandStart)
        assertEquals(Color(0xFF1F1408), t.onBrand)
        assertFalse(t.isDark)
    }

    @Test
    fun darkIsCharcoalAndOledIsBlack() {
        assertEquals(Color(0xFF161616), InkTokens.DARK.bg)
        assertEquals(Color(0xFF242424), InkTokens.DARK.raised)
        assertEquals(Color(0xFF000000), InkTokens.OLED.bg)
        assertEquals(Color(0xFF1E1E1E), InkTokens.OLED.raised)
        assertTrue(InkTokens.DARK.isDark && InkTokens.OLED.isDark)
    }

    @Test
    fun textReadsOnEverySurface() {
        for ((name, t) in looks) {
            for (s in listOf(t.bg, t.sidebar, t.chrome, t.surface, t.raised, t.sel, t.press)) {
                assertTrue("$name text on $s", contrast(t.text, s) >= 7.0)
                assertTrue("$name text2 on $s", contrast(t.text2, s) >= 4.5)
            }
        }
    }

    @Test
    fun labelsReadOnTheirFills() {
        for ((name, t) in looks) {
            assertTrue("$name onSolid", contrast(t.onSolid, t.solid) >= 7.0)
            assertTrue("$name toast", contrast(t.toastInk, t.toast) >= 7.0)
            assertTrue("$name onBrand at the light end", contrast(t.onBrand, t.brandStart) >= 4.5)
            assertTrue("$name onBrand at the deep end", contrast(t.onBrand, t.brandEnd) >= 4.5)
            assertTrue("$name brand as text", contrast(t.brandInk, t.bg) >= 4.5)
            assertTrue("$name segment label", contrast(t.text, t.segThumb) >= 4.5)
            assertTrue("$name segment off label", contrast(t.text2, t.segTrack) >= 4.5)
            assertTrue("$name icon badge", contrast(t.text, t.iconBadge) >= 7.0)
            assertTrue("$name danger", contrast(t.danger, t.raised) >= 4.5)
        }
    }

    @Test
    fun aMaterialPaletteDerivesItsTokens() {
        val light = Palette.materialLight(MaterialColors.seeded(Rgba(0x1C, 0x6A, 0x85), dark = false))
        assertEquals(light.text.toComposeColor(), light.ink.text)
        assertEquals(light.accent.toComposeColor(), light.ink.solid)
        assertEquals(light.accent.toComposeColor(), light.ink.brand)
        assertFalse(light.ink.isDark)
        val oled = Palette.materialOled(MaterialColors.seeded(Rgba(0x1C, 0x6A, 0x85), dark = true))
        assertEquals(Color(0xFF000000), oled.ink.bg)
        assertEquals(Color(0xFF000000), oled.ink.canvas)
        assertTrue(oled.ink.isDark)
    }

    @Test
    fun aMaterialPaletteLeavesTheDesignedLookHooksAtTheirDefaults() {
        val seed = Rgba(0x1C, 0x6A, 0x85)
        val lightM = MaterialColors.seeded(seed, dark = false)
        val darkM = MaterialColors.seeded(seed, dark = true)
        for ((appearance, m) in listOf("light" to lightM, "dark" to darkM)) {
            val p = Palette.material(appearance, m)
            assertEquals("$appearance desk", p.bg, p.desk)
            assertEquals("$appearance pageAccent", p.accent, p.pageAccent)
            assertEquals("$appearance inkTokens", null, p.inkTokens)
        }
        val oled = Palette.material("oled", darkM)
        assertEquals("oled desk", oled.bg, oled.desk)
        assertEquals("oled inkTokens", null, oled.inkTokens)
    }
}
