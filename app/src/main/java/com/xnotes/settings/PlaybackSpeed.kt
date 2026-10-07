package com.xnotes.settings

import kotlin.math.abs

/**
 * The audio player's speeds (r3_audio AU 389; the user-approved new feature): kept process-wide in
 * [Preferences.playbackSpeed], written only when not 1×. Pure, so loading settings needs no UI.
 */
object PlaybackSpeed {
    /** The menu's speeds, slowest first. All are exact in binary floating point. */
    val ALL: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    const val NORMAL = 1f

    /** The JSON key in the settings file. */
    const val KEY = "playback_speed"

    /** NaN, an infinity or ≤ 0 → [NORMAL]; anything else → the nearest listed speed (1.3 → 1.25, 5 → 2). */
    fun coerce(v: Float): Float {
        if (v.isNaN() || v.isInfinite() || v <= 0f) return NORMAL
        return ALL.minBy { abs(it - v) }
    }

    fun coerce(v: Double): Float = if (v.isNaN() || v.isInfinite()) NORMAL else coerce(v.toFloat())

    fun isNormal(v: Float): Boolean = v == NORMAL
}

/**
 * The preferences to keep when Settings applies [incoming]: Settings edits a draft opened earlier, and the player may
 * have changed the speed since, so [live]'s speed wins (round-3 defaults, Part 7 row 9). Only [resetAll] ("Reset all
 * settings" itself, not a draft that happens to equal the factory preferences) takes [incoming]'s speed too.
 */
fun keepLiveSpeed(incoming: Preferences, live: Preferences, resetAll: Boolean = false): Preferences =
    if (resetAll) incoming else incoming.copy(playbackSpeed = live.playbackSpeed)
