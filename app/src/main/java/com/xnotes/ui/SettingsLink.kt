package com.xnotes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A setting another screen asks Settings to show (Trash's "Change" → Keep deleted items). The
 * Settings pane takes it when it composes, opens its category, scrolls to the row and flashes it,
 * as a search result does. A request Settings does not take within [TTL_MS] has gone stale (it
 * never opened), so a later visit to Settings is not sent to that row.
 */
internal object SettingsLink {
    /** How long a request waits for Settings to take it. */
    const val TTL_MS = 5_000L

    var pending by mutableStateOf<SettingId?>(null)
        private set
    private var requestedAt = 0L

    fun request(id: SettingId, now: Long = clock()) {
        pending = id
        requestedAt = now
    }

    /** The pending setting, once (the next call returns null), or null when it has gone stale. */
    fun take(now: Long = clock()): SettingId? {
        val id = pending
        pending = null
        return if (id != null && now - requestedAt <= TTL_MS) id else null
    }

    /** A monotonic millisecond clock that also runs in JVM tests. */
    private fun clock(): Long = System.nanoTime() / 1_000_000L
}

/** Trash's countdown, out of Compose. */
internal object TrashMath {
    /** Whole days an item has left in a [days]-day trash, deleted at [deletedAt]; null when the trash keeps it until emptied (or is off). */
    fun daysLeft(days: Int, deletedAt: Long, now: Long): Int? =
        if (days > 0) (days - ((now - deletedAt) / 86_400_000L).toInt()).coerceAtLeast(0) else null

    /** Three days or fewer: the one place Trash uses red. */
    fun soon(left: Int?): Boolean = left != null && left <= 3
}
