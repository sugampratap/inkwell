package com.xnotes.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard that keeps androidx's Kalman predictor from spinning: it must let a normal pen stream
 * predict, refuse a stroke whose first samples came bunched into the same millisecond (the
 * report rate the library would divide by is then 0), and turn prediction off for the stroke after
 * a slow or huge call.
 */
class PredictionGuardTest {

    /** A down at [t0] and [moves] samples [gapMs] apart, each at a new position. */
    private fun stroke(g: PredictionGuard, t0: Long, gapMs: Long, moves: Int) {
        g.down(t0, 100f, 100f)
        for (i in 1..moves) g.sample(t0 + i * gapMs, 100f + i, 100f + i)
    }

    @Test
    fun steadyPenStreamPredicts() {
        val g = PredictionGuard()
        stroke(g, 10_000, 8, 30)
        assertTrue(g.allowPredict())
        g.afterPredict(200_000, 5)
        assertTrue(g.allowPredict())
    }

    @Test
    fun fastDigitiserStillPredicts() {
        // A 1 kHz stream is the fastest the library's whole-millisecond clock can tell apart.
        val g = PredictionGuard()
        stroke(g, 10_000, 1, 30)
        assertTrue(g.allowPredict())
    }

    @Test
    fun downAloneDoesNotPredict() {
        val g = PredictionGuard()
        g.down(10_000, 5f, 5f)
        assertFalse(g.allowPredict())
    }

    @Test
    fun bunchedStartIsRefused() {
        // A stalled emulator flushes its queue at once: every sample in the same millisecond.
        val g = PredictionGuard()
        stroke(g, 10_000, 0, 30)
        assertFalse(g.allowPredict())
    }

    @Test
    fun rateIsFrozenAfterTwentyGapsLikeTheLibrary() {
        // Nineteen bunched gaps and one of 8 ms: the library's rate is 0.4 ms and stays so for the
        // whole stroke, however normal the samples that follow, so the guard must stay off too.
        val g = PredictionGuard()
        g.down(10_000, 0.5f, 0.5f)
        for (i in 1..19) g.sample(10_000, 0.5f + i, 0.5f)
        g.sample(10_008, 50f, 50f)
        assertFalse(g.learning)
        for (i in 1..100) g.sample(10_008 + i * 8L, 50f + i, 50f)
        assertFalse(g.allowPredict())
    }

    @Test
    fun recoveredStartPredicts() {
        // A few bunched samples, then a steady stream: the mean of the first 20 gaps is over 1 ms.
        val g = PredictionGuard()
        g.down(10_000, 1f, 1f)
        for (i in 1..3) g.sample(10_000, 1f + i, 1f)
        assertFalse(g.allowPredict())
        for (i in 1..17) g.sample(10_000 + i * 8L, 10f + i, 1f)
        assertTrue(g.allowPredict())
    }

    @Test
    fun repeatedPositionWithinTwentyMsIsSkippedAsTheLibraryDoes() {
        val g = PredictionGuard()
        g.down(10_000, 3f, 3f)
        g.sample(10_005, 3f, 3f)
        g.sample(10_020, 3f, 3f)
        // None of those counted a gap: the library would not have either.
        assertFalse(g.allowPredict())
        // Past 20 ms the same position is a real sample again.
        g.sample(10_021, 3f, 3f)
        assertTrue(g.allowPredict())
    }

    @Test
    fun liftOrSecondPointerStopsUntilNextDown() {
        val g = PredictionGuard()
        stroke(g, 10_000, 8, 10)
        g.stop()
        assertFalse(g.allowPredict())
        g.sample(10_200, 9f, 9f)
        assertFalse(g.allowPredict())
        stroke(g, 20_000, 8, 10)
        assertTrue(g.allowPredict())
    }

    @Test
    fun slowCallTurnsPredictionOffForTheStroke() {
        val g = PredictionGuard()
        stroke(g, 10_000, 8, 10)
        g.afterPredict(PredictionGuard.MAX_PREDICT_NANOS + 1, 3)
        assertFalse(g.allowPredict())
        g.sample(10_500, 400f, 400f)
        assertFalse(g.allowPredict())
        stroke(g, 20_000, 8, 10)
        assertTrue(g.allowPredict())
    }

    @Test
    fun hugeCallTurnsPredictionOffForTheStroke() {
        val g = PredictionGuard()
        stroke(g, 10_000, 8, 10)
        g.afterPredict(100_000, PredictionGuard.MAX_PREDICTED_SAMPLES)
        assertTrue(g.allowPredict())
        g.afterPredict(100_000, PredictionGuard.MAX_PREDICTED_SAMPLES + 1)
        assertFalse(g.allowPredict())
    }

    @Test
    fun allowedRateBoundsTheLibrarysSampleCount() {
        // The library emits ceil(target / rate) samples for a target of at most 32 ms, plus a
        // catch-up to the previous prediction's end (at most as many again). At the lowest rate
        // the guard allows, that stays well inside the backstop.
        val worst = 2 * Math.ceil(32.0 / PredictionGuard.MIN_MEAN_GAP_MS).toInt() + 1
        assertTrue(worst <= PredictionGuard.MAX_PREDICTED_SAMPLES + 1)
        assertEquals(20, PredictionGuard.RATE_GAPS)
    }
}
