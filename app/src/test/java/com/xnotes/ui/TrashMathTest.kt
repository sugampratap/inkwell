package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMathTest {
    private val day = 86_400_000L

    @Test fun daysLeftCountDownAndStopAtZero() {
        val now = 100 * day
        assertEquals(30, TrashMath.daysLeft(30, now, now))
        assertEquals(3, TrashMath.daysLeft(30, now - 27 * day, now))
        assertEquals(0, TrashMath.daysLeft(30, now - 40 * day, now))
    }

    @Test fun aTrashKeptUntilEmptiedHasNoCountdown() {
        assertNull(TrashMath.daysLeft(-1, 0L, day))
        assertNull(TrashMath.daysLeft(0, 0L, day))
    }

    @Test fun threeDaysOrFewerIsSoon() {
        assertTrue(TrashMath.soon(3))
        assertTrue(TrashMath.soon(0))
        assertFalse(TrashMath.soon(4))
        assertFalse(TrashMath.soon(null))
    }
}
