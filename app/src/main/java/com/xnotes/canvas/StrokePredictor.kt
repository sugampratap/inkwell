package com.xnotes.canvas

import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor

/**
 * Where the pen is about to be, so wet ink can be drawn up to the nib rather than up to the last
 * sample the input pipeline delivered.
 *
 * An interface so the interaction code, which the unit tests drive, does not depend on the
 * platform predictor; editors install [MotionStrokePredictor] on the device.
 */
interface StrokePredictor {
    /** Feed every event the canvas receives, so the predictor sees the whole stream in order. */
    fun record(e: MotionEvent)

    /**
     * Predicted viewport positions of [pointerId], oldest first, written to [outX] and [outY].
     * Returns how many were written, 0 when there is no prediction.
     */
    fun predict(pointerId: Int, outX: DoubleArray, outY: DoubleArray): Int
}

/**
 * Decides, per stroke, whether androidx's predictor may be asked at all. Pure, so the unit tests
 * drive it with plain numbers.
 *
 * Why it exists: androidx motionprediction 1.0.0's Kalman fallback (what runs when the platform
 * has no model of its own, the emulator among them) is never told the report rate. Each stroke's
 * `SinglePointerPredictor` takes it as the mean of the stroke's first 20 gaps between samples, in
 * whole milliseconds, then emits about `32 ms / rate` samples per `predict()`, each `rate` ms
 * further on, in a loop that also runs until it passes the previous prediction's end. When the
 * first samples of a stroke arrive bunched into the same millisecond (a stalled device flushing
 * its input queue at once), the rate is 0: the sample count is infinite and the step never
 * advances, so `predict()` spins on the main thread in `MotionEvent.addBatch` until memory runs
 * out (the 2026-10-06 ANR). A rate just above 0 is not infinite but still thousands of samples.
 *
 * So the guard mirrors that estimate the way the library forms it (the same samples, the same
 * skip of a repeated position, the same 20-gap cap) and lets prediction run only while the mean
 * gap is at least [MIN_MEAN_GAP_MS], which bounds one call to about 2 x 32 samples. As a
 * backstop, a call that still takes longer than [MAX_PREDICT_NANOS] or returns more than
 * [MAX_PREDICTED_SAMPLES] samples turns prediction off for the rest of the stroke. The ink never
 * depends on this: without a prediction it is exact, only a refresh behind the nib.
 *
 * Cost on the hot path: a few comparisons per sample for the first 20 gaps of a stroke, nothing
 * after; no allocation.
 */
class PredictionGuard {
    /** A single-pointer stroke is down and prediction has not been turned off for it. */
    var active = false
        private set

    private var lastTimeMs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var gapSumMs = 0L
    private var gaps = 0

    /** Gaps are still being averaged, so samples are still worth feeding to [sample]. */
    val learning: Boolean get() = active && gaps < RATE_GAPS

    /** A new stroke starts with its down sample, as the library makes a fresh predictor per down. */
    fun down(timeMs: Long, x: Float, y: Float) {
        active = true
        lastTimeMs = 0L
        lastX = 0f
        lastY = 0f
        gapSumMs = 0L
        gaps = 0
        sample(timeMs, x, y)
    }

    /** One input sample of the stroke's pointer, historical ones first, as the library reads them. */
    fun sample(timeMs: Long, x: Float, y: Float) {
        if (!active) return
        // The library ignores a sample at the last position unless more than 20 ms went by.
        if (x == lastX && y == lastY && timeMs <= lastTimeMs + SAME_POSITION_MS) return
        if (gaps < RATE_GAPS && lastTimeMs > 0L) {
            gapSumMs += timeMs - lastTimeMs
            gaps++
        }
        lastX = x
        lastY = y
        lastTimeMs = timeMs
    }

    /** Lift, cancel, or a second pointer: no prediction until the next down. */
    fun stop() {
        active = false
    }

    /** Whether the library may be asked now without risking an unbounded `predict()`. */
    fun allowPredict(): Boolean = active && gaps > 0 && gapSumMs >= MIN_MEAN_GAP_MS * gaps

    /** After a call: one that was slow or huge despite the rate check ends prediction for the stroke. */
    fun afterPredict(elapsedNanos: Long, samples: Int) {
        if (elapsedNanos > MAX_PREDICT_NANOS || samples > MAX_PREDICTED_SAMPLES) active = false
    }

    companion object {
        /** The library averages this many gaps and then keeps the rate for the stroke. */
        const val RATE_GAPS = 20

        /** The library skips a repeated position within this many milliseconds. */
        const val SAME_POSITION_MS = 20L

        /** Below this mean gap the library's per-call sample count runs away (infinite at 0). */
        const val MIN_MEAN_GAP_MS = 1L

        /** A predict() call longer than this ends prediction for the stroke. */
        const val MAX_PREDICT_NANOS = 4_000_000L

        /** More samples than this from one call ends prediction for the stroke (normal is a few). */
        const val MAX_PREDICTED_SAMPLES = 64
    }
}

/**
 * [StrokePredictor] over androidx's `MotionEventPredictor`, which uses the platform's own
 * per-device model on Android 14 and later and a Kalman filter before that.
 */
class MotionStrokePredictor(private val view: View) : StrokePredictor {

    /** Built on the first event, when the view is on a display; null until then or if it failed. */
    private var predictor: MotionEventPredictor? = null
    private var failed = false

    /** Keeps the library's Kalman fallback away from report rates that make it spin; see there. */
    private val guard = PredictionGuard()

    /**
     * The predictor reads the refresh rate from its view's context, and a view built from the
     * application context has no display to read it from. So it is made on the first event, when
     * the view is attached, against a context for the display the view is actually on. Anything
     * that goes wrong leaves prediction off: the ink is still exact, only a refresh later.
     */
    private fun predictor(): MotionEventPredictor? {
        predictor?.let { return it }
        if (failed) return null
        return try {
            val display = view.display
            val anchor = if (display != null && view.context !is android.app.Activity) {
                View(view.context.createDisplayContext(display))
            } else {
                view
            }
            MotionEventPredictor.newInstance(anchor).also { predictor = it }
        } catch (_: RuntimeException) {
            failed = true
            null
        }
    }

    /** Feeds [guard] the same samples the library's per-stroke rate estimate sees. */
    private fun observe(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> guard.down(e.eventTime, e.getX(0), e.getY(0))
            MotionEvent.ACTION_MOVE -> {
                if (!guard.active) return
                if (e.pointerCount != 1) {
                    guard.stop()
                    return
                }
                if (!guard.learning) return
                for (h in 0 until e.historySize) {
                    guard.sample(e.getHistoricalEventTime(h), e.getHistoricalX(0, h), e.getHistoricalY(0, h))
                }
                guard.sample(e.eventTime, e.getX(0), e.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> guard.stop()
        }
    }

    override fun record(e: MotionEvent) {
        observe(e)
        try {
            predictor()?.record(e)
        } catch (_: RuntimeException) {
            // A predictor that cannot follow the stream predicts nothing; the ink is still exact.
        }
    }

    override fun predict(pointerId: Int, outX: DoubleArray, outY: DoubleArray): Int {
        if (!guard.allowPredict()) return 0
        val start = System.nanoTime()
        val p = try {
            predictor()?.predict()
        } catch (_: RuntimeException) {
            null
        }
        if (p == null) {
            guard.afterPredict(System.nanoTime() - start, 0)
            return 0
        }
        try {
            guard.afterPredict(System.nanoTime() - start, p.historySize + 1)
            if (!guard.active || p.pointerCount == 0) return 0
            var idx = p.findPointerIndex(pointerId)
            if (idx < 0) idx = 0
            val cap = minOf(outX.size, outY.size)
            var n = 0
            for (h in 0 until p.historySize) {
                if (n >= cap) break
                outX[n] = p.getHistoricalX(idx, h).toDouble()
                outY[n] = p.getHistoricalY(idx, h).toDouble()
                n++
            }
            if (n < cap) {
                outX[n] = p.getX(idx).toDouble()
                outY[n] = p.getY(idx).toDouble()
                n++
            }
            return n
        } finally {
            p.recycle()
        }
    }
}
