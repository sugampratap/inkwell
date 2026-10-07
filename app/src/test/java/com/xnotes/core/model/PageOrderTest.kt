package com.xnotes.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving pages: the new order as old indices, and the slots that would leave the note as it is. */
class PageOrderTest {

    @Test fun onePageMovesEarlier() {
        // Page 4 (index 3) to before page 2 (index 1).
        assertArrayEquals(intArrayOf(0, 3, 1, 2, 4), PageOrder.move(5, listOf(3), 1))
    }

    @Test fun onePageMovesLater() {
        // Index 1 to before index 4: it lands after index 3.
        assertArrayEquals(intArrayOf(0, 2, 3, 1, 4), PageOrder.move(5, listOf(1), 4))
    }

    @Test fun onePageMovesToTheEnd() {
        assertArrayEquals(intArrayOf(1, 2, 3, 0), PageOrder.move(4, listOf(0), 4))
    }

    @Test fun onePageMovesToTheStart() {
        assertArrayEquals(intArrayOf(3, 0, 1, 2), PageOrder.move(4, listOf(3), 0))
    }

    @Test fun droppingAPageBesideItselfChangesNothing() {
        assertNull(PageOrder.move(5, listOf(2), 2))
        assertNull(PageOrder.move(5, listOf(2), 3))
    }

    @Test fun severalPagesMoveTogetherInTheirOwnOrder() {
        // 0 and 3, given out of order, to before 5: they land together, 0 then 3.
        assertArrayEquals(intArrayOf(1, 2, 4, 0, 3, 5), PageOrder.move(6, listOf(3, 0), 5))
    }

    @Test fun severalPagesGatherWhereTheFirstOfThemWas() {
        // 1 and 4 to before 2: the slot sits after 1, which is moving, so they gather there.
        assertArrayEquals(intArrayOf(0, 1, 4, 2, 3, 5), PageOrder.move(6, listOf(1, 4), 2))
    }

    @Test fun aRunDroppedInsideItselfChangesNothing() {
        assertNull(PageOrder.move(6, listOf(2, 3, 4), 2))
        assertNull(PageOrder.move(6, listOf(2, 3, 4), 4))
        assertNull(PageOrder.move(6, listOf(2, 3, 4), 5))
    }

    @Test fun badIndicesAreIgnoredAndTheSlotIsClamped() {
        assertArrayEquals(intArrayOf(1, 2, 0), PageOrder.move(3, listOf(0, 7, -1, 0), 99))
        assertNull(PageOrder.move(3, listOf(9), 0))
        assertNull(PageOrder.move(3, emptyList(), 0))
    }

    @Test fun movingEveryPageChangesNothing() {
        assertNull(PageOrder.move(3, listOf(0, 1, 2), 0))
    }

    @Test fun theStepActionsSwapWithANeighbour() {
        // "Move page earlier" on index 2 is before 1; "later" is before 4.
        assertArrayEquals(intArrayOf(0, 2, 1, 3), PageOrder.move(4, listOf(2), 1))
        assertArrayEquals(intArrayOf(0, 1, 3, 2), PageOrder.move(4, listOf(2), 4))
    }

    @Test fun theNoOpCheckAgreesWithMove() {
        val sets = listOf(intArrayOf(2), intArrayOf(2, 3, 4), intArrayOf(1, 4), intArrayOf(0), intArrayOf(5))
        for (set in sets) for (before in 0..6) {
            val expected = PageOrder.move(6, set.toList(), before) == null
            if (expected) assertTrue("${set.toList()} before $before", PageOrder.isNoOp(set, before))
            else assertFalse("${set.toList()} before $before", PageOrder.isNoOp(set, before))
        }
    }
}
