package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the count line says. */
class SearchPositionTest {

    @Test fun nothingTypedOrNoResultsYetSaysNothing() {
        assertEquals(SearchPosition.None, searchPosition("", 10, true, false, 2))
        assertEquals(SearchPosition.None, searchPosition("heat", null, false, false, null))
    }

    @Test fun aScanStillReadingWithNoMatchSaysSearching() {
        assertEquals(SearchPosition.Searching, searchPosition("heat", 0, false, false, null))
        assertEquals(SearchPosition.None, searchPosition("heat", 0, true, false, null))
    }

    @Test fun theCurrentMatchCountsFromOne() {
        assertEquals(SearchPosition.At(6, 23, false), searchPosition("elimination", 23, false, false, 5))
        // Before a match is picked: "– / 23".
        assertEquals(SearchPosition.At(null, 23, false), searchPosition("elimination", 23, true, false, -1))
        // A scan that hit its cap: "10000+".
        assertEquals(SearchPosition.At(1, 10000, true), searchPosition("e", 10000, false, true, 0))
    }
}
