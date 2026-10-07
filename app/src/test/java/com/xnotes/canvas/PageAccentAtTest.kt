package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.model.Document
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkContrast
import com.xnotes.ui.theme.MaterialColors
import com.xnotes.ui.theme.Palette
import com.xnotes.ui.theme.PaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CanvasState.pageAccentAt] is the page accent on the paper a page actually shows, remembered per
 * paper: the memo must never hand back a colour worked out for another palette or another paper.
 */
class PageAccentAtTest {

    private val nearBlack = Rgba(0x22, 0x22, 0x22, 255)
    private val darkPage = Rgba(0x12, 0x12, 0x12, 255)

    private fun state(palette: Palette = PaperPalette.light()) =
        CanvasState(Document.blank(3), FakeSurfaceFactory(), palette)

    private fun assertReadsOn(paper: Rgba, got: Rgba) {
        assertTrue("$got on $paper", InkContrast.contrast(got, paper) >= Palette.CHROME_MIN_CONTRAST)
        assertTrue("$got is light", InkContrast.luminance(got) > InkContrast.luminance(paper))
    }

    @Test
    fun followsAPaletteSwap() {
        val st = state()
        assertEquals(nearBlack, st.pageAccentAt(0))
        val material = Palette.materialDark(MaterialColors.seeded(Rgba(0, 230, 118), dark = true))
        st.palette = material
        assertEquals(material.pageAccentOn(material.paper), st.pageAccentAt(0))
        st.palette = PaperPalette.light()
        assertEquals(nearBlack, st.pageAccentAt(0))
    }

    @Test
    fun followsThePageColour() {
        val st = state()
        val page = st.document.pages[1]
        assertEquals(nearBlack, st.pageAccentAt(1))
        page.style = page.style.copy(pageColor = darkPage)
        assertReadsOn(darkPage, st.pageAccentAt(1))
        assertEquals("its neighbour keeps the plain accent", nearBlack, st.pageAccentAt(0))
        page.style = page.style.copy(pageColor = null)
        assertEquals(nearBlack, st.pageAccentAt(1))
    }

    /** A PDF page shows the PDF's own white paper through the View menu's filter, whatever its page colour. */
    @Test
    fun aPdfPageFollowsTheColourFilterNotThePageColour() {
        val st = state()
        val page = st.document.pages[2]
        page.pdfPage = 0
        page.style = page.style.copy(pageColor = darkPage)
        assertEquals("white PDF paper", nearBlack, st.pageAccentAt(2))

        st.pdfFilter = PdfPageFilter.of(contrast = 100, invert = 100, brightness = 100, sepia = 0, keepImages = false)
        assertEquals(Rgba(0, 0, 0, 255), st.pdfPaper)
        assertReadsOn(st.pdfPaper, st.pageAccentAt(2))
        assertEquals("a plain page is not filtered", nearBlack, st.pageAccentAt(0))

        st.pdfFilter = PdfPageFilter.NONE
        assertEquals(Rgba(255, 255, 255, 255), st.pdfPaper)
        assertEquals(nearBlack, st.pageAccentAt(2))
    }

    /** Every filter that tints or darkens the PDF's paper moves its chrome with it. */
    @Test
    fun aTintedOrDarkenedPdfPageKeepsItsChromeReadable() {
        val st = state()
        st.document.pages[2].pdfPage = 0
        val filters = mapOf(
            "sepia 100" to PdfPageFilter.of(contrast = 100, invert = 0, brightness = 100, sepia = 100, keepImages = false),
            "brightness 30" to PdfPageFilter.of(contrast = 100, invert = 0, brightness = 30, sepia = 0, keepImages = false),
            "multiply slate" to PdfPageFilter.of(
                contrast = 100, invert = 0, brightness = 100, sepia = 0, multiply = Rgba(0x45, 0x5A, 0x64), keepImages = false,
            ),
            "multiply navy, dimmed" to PdfPageFilter.of(
                contrast = 100, invert = 0, brightness = 60, sepia = 0, multiply = Rgba(30, 41, 89), keepImages = false,
            ),
        )
        for ((name, filter) in filters) {
            st.pdfFilter = filter
            assertTrue("$name: the PDF paper is tinted (${st.pdfPaper})", st.pdfPaper != Rgba(255, 255, 255, 255))
            val got = st.pageAccentAt(2)
            assertTrue("$name: $got on ${st.pdfPaper}", InkContrast.contrast(got, st.pdfPaper) >= Palette.CHROME_MIN_CONTRAST)
        }
        assertTrue("sepia leaves the paper light", InkContrast.luminance(PdfColorFilter.apply(
            PdfColorFilter.matrix(contrast = 100, invert = 0, brightness = 100, sepia = 100), Rgba(255, 255, 255, 255),
        )) > 0.5)
    }

    /** A sticky note's card shares the same memo, keyed by its colour and dropped with the palette. */
    @Test
    fun aCardIsRememberedByItsColour() {
        val st = state()
        val card = Rgba(0x2E, 0x3B, 0x4E, 255)
        assertReadsOn(card, st.accentOnPaper(card))
        assertEquals(nearBlack, st.accentOnPaper(Rgba(0xFF, 0xF1, 0xA8, 255)))
        val material = Palette.materialLight(MaterialColors.seeded(Rgba(0, 230, 118), dark = false))
        st.palette = material
        assertEquals(material.pageAccentOn(card), st.accentOnPaper(card))
    }

    @Test
    fun noSuchPageIsThePlainAccent() {
        val st = state()
        assertEquals(nearBlack, st.pageAccentAt(-1))
        assertEquals(nearBlack, st.pageAccentAt(3))
    }
}
