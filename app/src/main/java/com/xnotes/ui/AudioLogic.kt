package com.xnotes.ui

import com.xnotes.core.model.AudioItem
import com.xnotes.core.model.Rgba
import com.xnotes.settings.PlaybackSpeed
import java.math.BigDecimal
import kotlin.math.max

/** Bars in the recorder's rolling trace: one per 120 ms tick, so about the last 2.4 s (AU 31-33; defaults #6). */
internal const val TRACE_BARS = 20

/** A seek tap starts a little before the tapped note, so its first word is heard (NoteAudio.act). */
internal const val SEEK_LEAD_MS = 600L

/** The seek ring holds full this long (AU 560)… */
internal const val RING_HOLD_MS = 650L

/** …then fades and grows over this long. */
internal const val RING_FADE_MS = 420L

internal const val RING_TOTAL_MS = RING_HOLD_MS + RING_FADE_MS

/**
 * The last [size] microphone levels, oldest first, for the trace. One push per recorder tick (main thread) and read
 * while drawing; slots not filled yet read 0. Levels are clamped to 0..1 and NaN reads 0.
 */
internal class LevelHistory(val size: Int = TRACE_BARS) {
    private val levels = FloatArray(size)
    private var next = 0
    private var count = 0

    fun push(level: Float) {
        levels[next] = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        next = (next + 1) % size
        if (count < size) count++
    }

    /** Bar [i]: 0 is the oldest, size - 1 the newest. */
    operator fun get(i: Int): Float {
        val back = size - 1 - i
        if (back < 0 || back >= count) return 0f
        return levels[((next - 1 - back) % size + size) % size]
    }

    fun clear() {
        levels.fill(0f)
        next = 0
        count = 0
    }
}

/** Bar [i] of [n]'s opacity: the oldest at .28, the newest full (AU 374). */
internal fun traceAlpha(i: Int, n: Int = TRACE_BARS): Float = 0.28f + 0.72f * i / (n - 1)

/** A bar's height as a share of its 24 dp: the level, floored at .12; all at .12 while paused (AU 33, 476). */
internal fun traceScale(level: Float, paused: Boolean): Float = if (paused) 0.12f else max(0.12f, level)

/** Under this pane width, in dp, the recorder is compact: no trace and no "Recording" line (round-3 defaults, Part 7 row 5). */
internal const val RECORDER_COMPACT_BELOW_DP = 1000f

/**
 * What NoteHeader's row spends before the recorder, in dp: its 8 dp start and 12 dp end padding, and Back (44 dp) with
 * the 6 dp gap after it. The row offers the recorder the rest as its max width (the weighted title is measured after
 * it), so that width is the pane's less this.
 */
internal const val RECORDER_HEADER_LEAD_DP = 8f + 12f + 44f + 6f

/** Whether the recorder is compact when the header offers it [offeredDp] of width: in a pane under [RECORDER_COMPACT_BELOW_DP]. */
internal fun recorderCompact(offeredDp: Float): Boolean = offeredDp + RECORDER_HEADER_LEAD_DP < RECORDER_COMPACT_BELOW_DP

/** One frame of the seek ring: its opacity and its scale. */
internal data class RingFrame(val alpha: Float, val scale: Float)

/**
 * The ring [elapsedMs] after its tap (AU 560-562): full for [RING_HOLD_MS], then [ease]d over [RING_FADE_MS] to
 * alpha 0 and scale 1.25; null once gone.
 */
internal fun seekRingFrame(elapsedMs: Long, ease: (Float) -> Float): RingFrame? {
    if (elapsedMs > RING_TOTAL_MS) return null
    if (elapsedMs <= RING_HOLD_MS) return RingFrame(1f, 1f)
    val t = ease(((elapsedMs - RING_HOLD_MS).toFloat() / RING_FADE_MS).coerceIn(0f, 1f))
    return RingFrame(1f - t, 1f + 0.25f * t)
}

/** Where a seek tap on a note stamped at [stampMs] plays from. */
internal fun seekTargetMs(stampMs: Long): Long = max(0L, stampMs - SEEK_LEAD_MS)

/**
 * A seek tap for the ring: where it landed, in the pane's (= the canvas view's) px, which tap it was, and the ring's
 * [ink], the accent of the page it landed on (#222 on cream, light on a dark paper; CanvasState.pageAccentAt).
 */
internal data class SeekTap(val x: Float, val y: Float, val id: Long, val ink: Rgba)

/** The recorder's clock (AU 343): whole seconds, "00:05", "12:34", "1:02:03". */
internal fun recorderClock(ms: Long): String =
    AudioItem.formatDuration(ms.coerceAtLeast(0L) / 1000L * 1000L).padStart(5, '0')

/** The player's elapsed time: where the knob is while it is held, else the play head. */
internal fun scrubShownMs(dragging: Boolean, fraction: Float, positionMs: Long, durationMs: Long): Long =
    if (dragging) (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong() else positionMs

/** "0.5×" … "2×": trailing zeros dropped, a "." in every language (AU 389-390; defaults #7). */
internal fun speedLabel(v: Float): String =
    BigDecimal(v.toString()).stripTrailingZeros().toPlainString() + "×"

/**
 * Whether to set [wanted] on the player now. Only a prepared, playing player: on a paused or unprepared one
 * setPlaybackParams with a non-zero speed would start it. Then when it differs from what was [applied], or right after
 * a start ([justStarted]) for any speed but 1×, since a restart after completion may drop the parameters.
 */
internal fun shouldApplySpeed(prepared: Boolean, playing: Boolean, applied: Float, wanted: Float, justStarted: Boolean): Boolean =
    prepared && playing && (applied != wanted || (justStarted && !PlaybackSpeed.isNormal(wanted)))
