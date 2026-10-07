package com.xnotes.gl

/**
 * Which of a frame's queued wet edits still have to be applied, given the ones queued after them.
 *
 * A pen sending its samples unbatched can queue several edits for the stroke under it between two
 * frames, and each one used to be put into a buffer and uploaded in turn before the frame drew
 * once. Most of them are already dead by then. A [WetKind.WHOLE] replaces everything the wet
 * buffers hold, so every wet edit before it is wasted work; a [WetKind.TAIL] replaces the tail, so
 * every tail before it is too. A [WetKind.SETTLED] run is never superseded by a later tail, since
 * a tail never touches the settled runs, only by a later whole. Edits that are not wet edits are
 * always kept, in their order; none of them reads the wet buffers.
 *
 * What is left draws exactly what applying the lot would have: the final contents of both buffers
 * and the final wet bounds both come from the edits kept, the last wet edit always among them.
 *
 * Pure, so the rule is unit-tested without a GL context.
 */
internal object WetEditCoalescing {

    /**
     * Fill the first [count] slots of [keep]: true for each edit to apply. [kindAt] gives the
     * edit's [WetKind], or null for anything that is not a wet edit.
     */
    inline fun mark(count: Int, keep: BooleanArray, kindAt: (Int) -> WetKind?) {
        var lastWhole = -1
        var lastTail = -1
        for (i in 0 until count) {
            when (kindAt(i)) {
                WetKind.WHOLE -> lastWhole = i
                WetKind.TAIL -> lastTail = i
                else -> Unit
            }
        }
        for (i in 0 until count) {
            keep[i] = when (kindAt(i)) {
                null -> true
                WetKind.WHOLE -> i == lastWhole
                WetKind.SETTLED -> i > lastWhole
                WetKind.TAIL -> i > lastWhole && i == lastTail
            }
        }
    }
}
