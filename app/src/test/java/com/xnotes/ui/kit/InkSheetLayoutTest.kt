package com.xnotes.ui.kit

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A sheet on a narrow card, and a sheet over a sheet. */
class InkSheetLayoutTest {
    @Test fun aNarrowCardPutsItsHeaderActionsUnderTheTitle() {
        assertTrue(sheetHeaderStacks(420.dp))
        assertTrue(sheetHeaderStacks(599.dp))
        assertFalse(sheetHeaderStacks(600.dp))
        assertFalse(sheetHeaderStacks(1120.dp))
    }

    @Test fun theFirstModalDimsInFull() {
        assertEquals(0.32f, scrimDim(0.32f, othersOpen = 0), 0.0001f)
    }

    @Test fun aModalOverAnotherDimsLessSoTheTwoAreNotTwiceAsDark() {
        val first = 0.32f
        val second = scrimDim(first, othersOpen = 1)
        assertTrue(second < first)
        // Each window dims what is behind it: together they are darker than one, well short of two.
        val together = 1f - (1f - first) * (1f - second)
        assertTrue(together > first)
        assertTrue(together < 1f - (1f - first) * (1f - first))
    }
}
