package com.xnotes.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteTest {

    /** Hue in degrees and HSV saturation, enough to tell purple from the rest. */
    private fun hue(c: Color): Pair<Float, Float> {
        val r = c.red; val g = c.green; val b = c.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        if (d == 0f) return 0f to 0f
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return ((h + 360f) % 360f) to d / max
    }

    private fun purple(c: Color) = hue(c).let { (h, s) -> s > 0.12f && h in 255f..345f }

    @Test fun theListIsAppendOnly() {
        assertEquals(16, CoverPalette.colors.size)
        assertEquals(Color(0xFF2F4858), CoverPalette.colors[0])
        assertEquals(Color(0xFF8E5A6B), CoverPalette.colors[3])
        assertEquals(Color(0xFF7D6B91), CoverPalette.colors[9])
    }

    @Test fun offersTenDistinctColoursFromTheList() {
        assertEquals(10, CoverPalette.offered.size)
        assertEquals(10, CoverPalette.offered.toSet().size)
        assertTrue(CoverPalette.offered.all { it in CoverPalette.colors.indices })
    }

    @Test fun theOldPurplesReadAsPurple() {
        assertTrue(purple(CoverPalette.colors[3]))
        assertTrue(purple(CoverPalette.colors[9]))
    }

    @Test fun offersNoPurpleOrMauve() {
        for (i in CoverPalette.offered) assertFalse("index $i", purple(CoverPalette.colors[i]))
    }

    @Test fun automaticColoursComeFromTheOfferAndNeverChange() {
        for (n in listOf("a", "Lecture 4 – Thermodynamics", "Weekly planning.xnote")) {
            assertTrue(CoverPalette.autoIndex(n) in CoverPalette.offered)
            assertEquals(CoverPalette.autoIndex(n), CoverPalette.autoIndex(n))
        }
        assertEquals(CoverPalette.autoIndex("Weekly"), CoverPalette.autoIndex("weekly.xnote"))
    }

    @Test fun aPickedColourIsKeptEvenWhenNoLongerOffered() {
        assertEquals(Color(0xFF8E5A6B), CoverPalette.colorFor("x", 3))
        assertEquals(Color(0xFF7D6B91), CoverPalette.colorFor("x", 9))
    }

    @Test fun aPickOutsideTheListFallsBackToAutomatic() {
        assertEquals(CoverPalette.colors[CoverPalette.autoIndex("x")], CoverPalette.colorFor("x", 99))
    }
}
