package com.xnotes.ui.kit

import androidx.compose.ui.unit.IntRect
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The eyedropper's catcher asks OpenCards whether a tap landed on a card rather than on the page (Part 5). */
class OpenCardsTest {

    private val a = Any()
    private val b = Any()

    @After fun forget() {
        OpenCards.remove(a)
        OpenCards.remove(b)
    }

    @Test fun aTapInsideAnOpenCardIsOnACard() {
        OpenCards.put(a, IntRect(100, 100, 440, 673))
        assertTrue(OpenCards.contains(100, 100))
        assertTrue(OpenCards.contains(439, 672))
        assertFalse(OpenCards.contains(440, 300))
        assertFalse(OpenCards.contains(99, 300))
    }

    @Test fun aClosedCardNoLongerCounts() {
        OpenCards.put(b, IntRect(0, 0, 10, 10))
        OpenCards.remove(b)
        assertFalse(OpenCards.contains(5, 5))
    }

    @Test fun aCardThatMovesCountsWhereItIsNow() {
        OpenCards.put(a, IntRect(0, 0, 10, 10))
        OpenCards.put(a, IntRect(100, 0, 110, 10))
        assertFalse(OpenCards.contains(5, 5))
        assertTrue(OpenCards.contains(105, 5))
    }
}
