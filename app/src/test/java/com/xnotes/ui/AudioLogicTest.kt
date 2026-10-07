package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AudioLogicTest {

    // --- the recorder's level history ---

    @Test fun anEmptyHistoryIsSilent() {
        val h = LevelHistory()
        for (i in 0 until TRACE_BARS) assertEquals(0f, h[i])
    }

    @Test fun theNewestLevelIsTheLastBar() {
        val h = LevelHistory()
        for (k in 1..5) h.push(k / 10f)
        assertEquals(0.5f, h[19], 1e-6f)
        assertEquals(0.1f, h[15], 1e-6f)
        assertEquals(0f, h[14])
    }

    @Test fun theOldestLevelsRollOff() {
        val h = LevelHistory()
        for (k in 1..(TRACE_BARS + 7)) h.push(k / 100f)
        assertEquals((TRACE_BARS + 7) / 100f, h[19], 1e-6f)
        assertEquals(8 / 100f, h[0], 1e-6f)
    }

    @Test fun levelsAreClamped() {
        val h = LevelHistory()
        h.push(-1f)
        h.push(2f)
        h.push(Float.NaN)
        assertEquals(0f, h[17])
        assertEquals(1f, h[18])
        assertEquals(0f, h[19])
    }

    @Test fun clearEmptiesTheHistory() {
        val h = LevelHistory()
        h.push(0.7f)
        h.clear()
        assertEquals(0f, h[19])
    }

    @Test fun olderBarsAreFainter() {
        assertEquals(0.28f, traceAlpha(0), 1e-6f)
        assertEquals(1f, traceAlpha(19), 1e-6f)
    }

    @Test fun barsNeverDropBelowTheirFloorAndFlattenWhilePaused() {
        assertEquals(0.12f, traceScale(0f, paused = false), 0f)
        assertEquals(0.6f, traceScale(0.6f, paused = false), 0f)
        assertEquals(0.12f, traceScale(0.9f, paused = true), 0f)
    }

    // --- the tap-to-seek ring ---

    private val linear: (Float) -> Float = { it }

    @Test fun theRingHoldsFullThenFades() {
        assertEquals(RingFrame(1f, 1f), seekRingFrame(-5, linear))
        assertEquals(RingFrame(1f, 1f), seekRingFrame(0, linear))
        assertEquals(RingFrame(1f, 1f), seekRingFrame(RING_HOLD_MS, linear))
        val mid = seekRingFrame(RING_HOLD_MS + RING_FADE_MS / 2, linear)!!
        assertEquals(0.5f, mid.alpha, 1e-3f)
        assertEquals(1.125f, mid.scale, 1e-3f)
        val end = seekRingFrame(RING_TOTAL_MS, linear)!!
        assertEquals(0f, end.alpha, 1e-6f)
        assertEquals(1.25f, end.scale, 1e-6f)
        assertNull(seekRingFrame(RING_TOTAL_MS + 1, linear))
    }

    @Test fun theFadeFollowsTheEasingItIsGiven() {
        val f = seekRingFrame(RING_HOLD_MS + RING_FADE_MS / 2) { 0.8f }!!
        assertEquals(0.2f, f.alpha, 1e-6f)
    }

    @Test fun aSeekStartsAlittleBeforeTheNote() {
        assertEquals(0L, seekTargetMs(400L))
        assertEquals(4_400L, seekTargetMs(5_000L))
    }

    // --- clocks ---

    @Test fun theRecorderClockShowsWholeSecondsPaddedToFive() {
        assertEquals("00:00", recorderClock(0L))
        assertEquals("00:05", recorderClock(5_400L))
        assertEquals("00:59", recorderClock(59_999L))
        assertEquals("12:34", recorderClock(754_000L))
        assertEquals("1:02:03", recorderClock(3_723_000L))
    }

    @Test fun theScrubShowsTheDragWhileHeldElseThePlayHead() {
        assertEquals(30_000L, scrubShownMs(dragging = true, fraction = 0.5f, positionMs = 1_000L, durationMs = 60_000L))
        assertEquals(1_000L, scrubShownMs(dragging = false, fraction = 0.5f, positionMs = 1_000L, durationMs = 60_000L))
        assertEquals(0L, scrubShownMs(dragging = true, fraction = 0.5f, positionMs = 0L, durationMs = 0L))
    }

    // --- speed ---

    @Test fun speedLabelsDropTrailingZerosAndUseADotEverywhere() {
        val was = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"),
                com.xnotes.settings.PlaybackSpeed.ALL.map { speedLabel(it) },
            )
        } finally {
            Locale.setDefault(was)
        }
    }

    @Test fun theSpeedGoesOnOnlyAPreparedPlayingPlayer() {
        assertFalse(shouldApplySpeed(prepared = false, playing = true, applied = 1f, wanted = 1.5f, justStarted = true))
        assertFalse(shouldApplySpeed(prepared = true, playing = false, applied = 1f, wanted = 1.5f, justStarted = false))
        assertFalse(shouldApplySpeed(prepared = true, playing = true, applied = 1.5f, wanted = 1.5f, justStarted = false))
        assertTrue(shouldApplySpeed(prepared = true, playing = true, applied = 1f, wanted = 1.5f, justStarted = false))
        assertTrue(shouldApplySpeed(prepared = true, playing = true, applied = 1.5f, wanted = 1f, justStarted = false))
    }

    @Test fun aStartReappliesAnyNonNormalSpeedButNeverTouchesOneTimes() {
        assertTrue(shouldApplySpeed(prepared = true, playing = true, applied = 1.5f, wanted = 1.5f, justStarted = true))
        assertFalse(shouldApplySpeed(prepared = true, playing = true, applied = 1f, wanted = 1f, justStarted = true))
    }

    // --- the recorder's compact mode ---

    @Test fun theHeaderSpends70DpBeforeTheRecorder() {
        assertEquals(70f, RECORDER_HEADER_LEAD_DP)
    }

    @Test fun theRecorderIsCompactInAPaneUnder1000Dp() {
        // Offered the pane's width less the header's lead: a 640 dp split pane, a pane at 999 dp.
        assertTrue(recorderCompact(640f - RECORDER_HEADER_LEAD_DP))
        assertTrue(recorderCompact(999f - RECORDER_HEADER_LEAD_DP))
    }

    @Test fun theRecorderIsWideInAPaneOf1000DpOrMore() {
        assertFalse(recorderCompact(1000f - RECORDER_HEADER_LEAD_DP))
        assertFalse(recorderCompact(1280f - RECORDER_HEADER_LEAD_DP))
    }
}
