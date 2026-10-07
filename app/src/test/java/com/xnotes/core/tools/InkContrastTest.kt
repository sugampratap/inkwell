package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkContrastTest {
    private val white = Rgba(255, 255, 255)
    private val black = Rgba(0, 0, 0)
    private val navy = InkPalette.INK

    @Test fun inkThatReadsIsLeftAlone() {
        assertEquals(navy, InkContrast.forPaper(navy, white))
        assertEquals(white, InkContrast.forPaper(white, black))
        assertEquals(InkPalette.PEN_RED, InkContrast.forPaper(InkPalette.PEN_RED, white))
    }

    @Test fun whiteOnWhiteWritesDark() {
        val got = InkContrast.forPaper(white, white)
        assertTrue(InkContrast.contrast(got, white) >= InkContrast.MIN_CONTRAST)
        assertTrue(InkContrast.luminance(got) < 0.05)
    }

    @Test fun navyOnBlackBecomesALightBlueThatReads() {
        val got = InkContrast.forPaper(navy, black)
        assertTrue(InkContrast.contrast(got, black) >= InkContrast.MIN_CONTRAST)
        assertTrue("keeps its blue", got.b > got.r)
    }

    @Test fun everyBarInkReadsOnEveryPaperItIsMadeFor() {
        val papers = listOf(white, black, Rgba(22, 22, 22), Rgba(250, 246, 236), Rgba(30, 41, 59))
        val inks = InkPalette.presets + listOf(InkPalette.MARKER_YELLOW, InkPalette.NEAR_WHITE, InkPalette.BLACK)
        for (p in papers) for (i in inks) {
            val got = InkContrast.forPaper(i, p)
            assertTrue("$i on $p gave $got", InkContrast.contrast(got, p) >= 1.5)
        }
    }

    @Test fun defaultInkFollowsThePage() {
        assertEquals(navy, InkContrast.defaultInk(white))
        assertTrue(InkContrast.luminance(InkContrast.defaultInk(black)) > 0.3)
    }

    /** A thin line asks more of its paper than ink does, but only a caller that says so gets the stricter test. */
    @Test fun aStricterThresholdIsTheCallersChoice() {
        val nearBlack = Rgba(0x22, 0x22, 0x22)
        val slate = Rgba(0x45, 0x5A, 0x64)
        // About 2.3:1: enough for ink, so ink is left exactly as it was picked.
        assertEquals(nearBlack, InkContrast.forPaper(nearBlack, slate))
        val strict = InkContrast.forPaper(nearBlack, slate, minContrast = 3.0)
        assertTrue("$strict on slate", InkContrast.contrast(strict, slate) >= 3.0)
        assertTrue("turns light", InkContrast.luminance(strict) > InkContrast.luminance(slate))
    }

    /** Gate: a cream picked on cream paper showed (and wrote) ochre #B78900, from HSL's full saturation near white. */
    @Test fun aNearPaperCreamWritesNearBlackNotOchre() {
        val cream = Rgba(0xFF, 0xFD, 0xF7)
        val got = InkContrast.forPaper(cream, cream)
        assertTrue("$got reads", InkContrast.contrast(got, cream) >= InkContrast.MIN_CONTRAST)
        assertTrue("$got is near-black", InkContrast.luminance(got) < 0.05)
        val chroma = maxOf(got.r, got.g, got.b) - minOf(got.r, got.g, got.b)
        assertTrue("$got keeps no more than a tint", chroma <= 12)
    }

    @Test fun aRealPastelStillKeepsItsHue() {
        val pink = Rgba(0xFF, 0xC0, 0xCB)
        val got = InkContrast.forPaper(pink, Rgba(255, 255, 255))
        assertTrue("$got is still red-ish", got.r > got.g + 40 && got.r > got.b + 20)
    }
}
