package com.xnotes.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedTest {

    @Test fun theMenuOffersSixSpeedsSlowestFirst() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), PlaybackSpeed.ALL)
    }

    @Test fun aStoredSpeedSnapsToTheNearestListedOne() {
        assertEquals(1.25f, PlaybackSpeed.coerce(1.3f))
        assertEquals(0.5f, PlaybackSpeed.coerce(0.6f))
        assertEquals(0.75f, PlaybackSpeed.coerce(0.63f))
        assertEquals(2f, PlaybackSpeed.coerce(3f))
        assertEquals(1.25f, PlaybackSpeed.coerce(1.3))
    }

    @Test fun nonsenseIsNormal() {
        for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f)) {
            assertEquals("for $v", 1f, PlaybackSpeed.coerce(v))
        }
        assertEquals(1f, PlaybackSpeed.coerce(Double.NaN))
        assertEquals(1f, PlaybackSpeed.coerce(Double.POSITIVE_INFINITY))
    }

    @Test fun everyListedSpeedIsAFixedPoint() {
        for (v in PlaybackSpeed.ALL) assertEquals(v, PlaybackSpeed.coerce(v))
    }

    @Test fun onlyOneTimesIsNormal() {
        assertTrue(PlaybackSpeed.isNormal(1f))
        for (v in PlaybackSpeed.ALL - 1f) assertFalse("for $v", PlaybackSpeed.isNormal(v))
    }
}
