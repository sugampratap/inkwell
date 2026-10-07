package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On-page chrome is drawn in [Palette.pageAccentOn] the paper under it: the page accent itself on
 * ordinary paper, and its light counterpart where the page is too dark for a thin line of it to
 * show, so a selection never vanishes into the page it is on.
 */
class PageAccentTest {

    private val cream = Rgba(0xFF, 0xFD, 0xF7, 255)
    private val white = Rgba(0xFF, 0xFF, 0xFF, 255)
    private val darkPage = Rgba(0x12, 0x12, 0x12, 255)
    private val slate = Rgba(0x45, 0x5A, 0x64, 255)
    private val nearBlack = Rgba(0x22, 0x22, 0x22, 255)

    private val inkwell = listOf("light" to PaperPalette.light(), "dark" to PaperPalette.dark(), "oled" to PaperPalette.oled())
    private val materialLight = Palette.materialLight(MaterialColors.seeded(Rgba(0, 230, 118), dark = false))
    private val materialDark = Palette.materialDark(MaterialColors.seeded(Rgba(0, 230, 118), dark = true))

    private fun assertLightOn(paper: Rgba, got: Rgba, what: String) {
        assertTrue("$what: $got on $paper", InkContrast.contrast(got, paper) >= Palette.CHROME_MIN_CONTRAST)
        assertTrue("$what reads as light", InkContrast.luminance(got) > InkContrast.luminance(paper))
    }

    @Test
    fun onOrdinaryPaperItIsThePageAccentItself() {
        for ((name, p) in inkwell) {
            assertEquals("$name on cream", nearBlack, p.pageAccentOn(cream))
            assertEquals("$name on white", nearBlack, p.pageAccentOn(white))
            assertEquals("$name on its own paper", p.pageAccent, p.pageAccentOn(p.paper))
        }
    }

    @Test
    fun onADarkPageItTurnsLightEnoughToRead() {
        for ((name, p) in inkwell) assertLightOn(darkPage, p.pageAccentOn(darkPage), name)
    }

    /** #222 makes 2.3:1 on a slate page, which ink gets away with but a 1.3px dashed frame does not. */
    @Test
    fun aMidDarkPageIsHeldToTheNonTextThreshold() {
        for ((name, p) in inkwell) assertLightOn(slate, p.pageAccentOn(slate), name)
    }

    @Test
    fun aMaterialAccentThatAlreadyReadsIsKept() {
        assertEquals(materialLight.accent, materialLight.pageAccent)
        assertEquals(materialLight.accent, materialLight.pageAccentOn(cream))
        assertEquals(materialDark.accent, materialDark.pageAccentOn(materialDark.paper))
    }

    @Test
    fun aMaterialLightAccentFlipsOnADarkPage() {
        assertLightOn(darkPage, materialLight.pageAccentOn(darkPage), "material light")
    }

    /**
     * The counterpart [InkContrast.forPaper] swaps to is picked by lightness, not by contrast, so on
     * a mid-tone page it can read worse than the accent it replaced. Chrome is then held to its
     * ratio by the best of the accent, the counterpart, near-black and white.
     */
    private fun assertChromeReadsOn(p: Palette, paper: Rgba, what: String) {
        val got = p.pageAccentOn(paper)
        val counterpart = InkContrast.forPaper(p.pageAccent, paper, Palette.CHROME_MIN_CONTRAST)
        val best = listOf(p.pageAccent, counterpart, Palette.NEAR_BLACK, Palette.WHITE).maxOf { InkContrast.contrast(it, paper) }
        val ratio = InkContrast.contrast(got, paper)
        assertTrue("$what: $got on $paper is $ratio", ratio >= minOf(Palette.CHROME_MIN_CONTRAST, best) - 1e-9)
    }

    @Test
    fun aSwapThatReadsWorseIsHeldToTheChromeRatio() {
        val midBlue = Rgba(0x44, 0x77, 0xBB, 255)
        assertChromeReadsOn(materialDark, midBlue, "material dark accent ${materialDark.accent}")
        assertChromeReadsOn(materialDark.copy(pageAccent = Rgba(0x7A, 0xDB, 0x8F, 255)), midBlue, "light green")
        assertChromeReadsOn(materialLight.copy(pageAccent = Rgba(0x67, 0x50, 0xA4, 255)), Rgba(0x70, 0x70, 0x70, 255), "M3 purple on grey")
        for ((name, p) in inkwell) {
            for (paper in listOf(cream, white, darkPage, slate, Rgba(0x44, 0x77, 0xBB, 255), Rgba(0x70, 0x70, 0x70, 255))) {
                assertChromeReadsOn(p, paper, "$name on $paper")
            }
        }
    }

    /** The near-black page accent is unchanged by that rule wherever the plain swap already reads. */
    @Test
    fun theInkwellAccentIsWhatItWas() {
        val light = Rgba(0xEF, 0xEF, 0xEF, 255)
        for ((name, p) in inkwell) {
            assertEquals("$name on cream", nearBlack, p.pageAccentOn(cream))
            assertEquals("$name on white", nearBlack, p.pageAccentOn(white))
            assertEquals("$name on #121212", light, p.pageAccentOn(darkPage))
            assertEquals("$name on #455A64", light, p.pageAccentOn(slate))
        }
    }

    /** What sits on an accent fill (a grip's face, the + on an insert button): white or #222, whichever stands out. */
    @Test
    fun whatReadsOnAnAccentFill() {
        assertEquals(Palette.WHITE, Palette.onFill(nearBlack))
        assertEquals(Palette.NEAR_BLACK, Palette.onFill(PaperPalette.dark().pageAccentOn(darkPage)))
        assertEquals(Palette.NEAR_BLACK, Palette.onFill(white))
        assertEquals(Palette.WHITE, Palette.onFill(materialLight.accent))
        assertEquals(Palette.NEAR_BLACK, Palette.onFill(materialDark.accent))
        for (fill in listOf(nearBlack, Rgba(0xEF, 0xEF, 0xEF), materialLight.accent, materialDark.accent, slate, Rgba(0x80, 0x80, 0x80))) {
            assertTrue("on $fill", InkContrast.contrast(Palette.onFill(fill), fill) >= Palette.CHROME_MIN_CONTRAST)
        }
    }
}
