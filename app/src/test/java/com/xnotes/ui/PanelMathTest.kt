package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The panel's sums: a two-column grid scrubbed by its scrollbar, and the contents entry to mark. */
class PanelMathTest {

    @Test fun rowsRoundUp() {
        assertEquals(6, PanelMath.rows(12, 2))
        assertEquals(7, PanelMath.rows(13, 2))
        assertEquals(0, PanelMath.rows(0, 2))
    }

    @Test fun theThumbShowsHowMuchIsInViewButNeverVanishes() {
        assertEquals(100f, PanelMath.thumbLength(500f, 2000f, 400f, 28f), 0.01f)
        assertEquals(28f, PanelMath.thumbLength(500f, 100_000f, 400f, 28f), 0.01f)
    }

    @Test fun aScrubLandsOnTheFirstPageOfARow() {
        // 12 pages, 6 rows of 200px, a 500px window: 700px of travel.
        assertEquals(0 to 0, PanelMath.gridTarget(0f, 1200f, 500f, 200f, 12, 2))
        // Half way: 350px in = row 1 (pages 3–4), 150px into it.
        assertEquals(2 to 150, PanelMath.gridTarget(0.5f, 1200f, 500f, 200f, 12, 2))
        // The end: 700px = row 3, 100px into it.
        assertEquals(6 to 100, PanelMath.gridTarget(1f, 1200f, 500f, 200f, 12, 2))
    }

    @Test fun theContentsMarkTheLastEntryOnThisPage() {
        assertEquals(2, PanelMath.currentEntry(listOf(0, 3, 3, 9), 3))
        assertEquals(-1, PanelMath.currentEntry(listOf(0, 3, 9), 5))
    }

    @Test fun aContentsEntryFollowsItsPdfPageWhereverItMoved() {
        // Note pages carry PDF pages 2, 0, 1 (moved), then a blank page.
        val pdfPages = listOf(2, 0, 1, null)
        assertEquals(listOf(1, 2, 0), PanelMath.tocTargets(listOf(0, 1, 2), pdfPages).toList())
    }

    @Test fun aContentsEntryWithNoPageOfItsOwnFallsBackToTheLastEarlierOne() {
        // PDF page 3 was deleted: the entry lands on the last page carrying a PDF page up to 3.
        assertEquals(listOf(1, -1), PanelMath.tocTargets(listOf(3, -1), listOf(0, 2, null)).toList())
        assertEquals(listOf(-1), PanelMath.tocTargets(listOf(1), listOf(null, 4)).toList())
    }
}
