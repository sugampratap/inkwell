package com.xnotes.core.model

/**
 * The sums behind moving pages: where a set of pages lands when dropped into a slot. A slot is
 * "before page N" in the order as it stands, 0 to `count` (the last slot is after the last page).
 */
object PageOrder {

    /**
     * The order after moving the pages at [indices] (any order, duplicates and strays ignored) to
     * slot [before], as old indices: `result[newPosition] = oldIndex`. They land together in their
     * own document order. Null when nothing would change, so a drop beside itself records nothing.
     */
    fun move(count: Int, indices: List<Int>, before: Int): IntArray? {
        if (count <= 0) return null
        val moving = BooleanArray(count)
        var picked = 0
        for (i in indices) if (i in 0 until count && !moving[i]) { moving[i] = true; picked++ }
        if (picked == 0 || picked == count) return null
        val slot = before.coerceIn(0, count)
        // The slot counted among the pages that stay: how many of them stand before it.
        var at = 0
        for (i in 0 until slot) if (!moving[i]) at++
        val out = IntArray(count)
        var n = 0
        var stayed = 0
        for (i in 0 until count) {
            if (moving[i]) continue
            if (stayed == at) for (m in 0 until count) if (moving[m]) out[n++] = m
            out[n++] = i
            stayed++
        }
        if (stayed == at) for (m in 0 until count) if (moving[m]) out[n++] = m
        for (i in 0 until count) if (out[i] != i) return out
        return null
    }

    /**
     * True when moving [sorted] (ascending, distinct, in range) to slot [before] would leave the
     * order as it is: only a run of neighbours dropped inside or right beside itself does. Allocates
     * nothing, so a drag can ask it every frame.
     */
    fun isNoOp(sorted: IntArray, before: Int): Boolean {
        if (sorted.isEmpty()) return true
        val first = sorted[0]
        val last = sorted[sorted.size - 1]
        if (last - first + 1 != sorted.size) return false // a gap: gathering them changes the order
        return before in first..last + 1
    }
}
