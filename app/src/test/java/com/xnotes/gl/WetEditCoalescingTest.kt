package com.xnotes.gl

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scene skips a queued wet edit only when a later one in the same frame replaces it, and the
 * buffers then end up holding exactly what applying every edit in turn would have left.
 */
class WetEditCoalescingTest {

    /** What [WetEditCoalescing] keeps of [kinds], as the indices kept. */
    private fun kept(vararg kinds: WetKind?): List<Int> {
        val keep = BooleanArray(kinds.size)
        WetEditCoalescing.mark(kinds.size, keep) { kinds[it] }
        return kinds.indices.filter { keep[it] }
    }

    /**
     * A model of the two wet buffers: applying [edits] (null = not a wet edit, left alone) in order
     * gives the settled runs and the tail they end with, as edit indices.
     */
    private fun apply(kinds: List<WetKind?>, indices: List<Int>): Pair<List<Int>, List<Int>> {
        var settled = listOf<Int>()
        var tail = listOf<Int>()
        for (i in indices) {
            when (kinds[i]) {
                WetKind.WHOLE -> {
                    settled = emptyList()
                    tail = listOf(i)
                }
                WetKind.SETTLED -> settled = settled + i
                WetKind.TAIL -> tail = listOf(i)
                null -> Unit
            }
        }
        return settled to tail
    }

    @Test fun onlyTheNewestTailOfAFrameIsApplied() {
        assertEquals(listOf(3), kept(WetKind.TAIL, WetKind.TAIL, WetKind.TAIL, WetKind.TAIL))
    }

    @Test fun aSettledRunSurvivesTheTailsAroundIt() {
        assertEquals(listOf(1, 3), kept(WetKind.TAIL, WetKind.SETTLED, WetKind.TAIL, WetKind.TAIL))
    }

    @Test fun aWholeReplacementDropsEveryWetEditBeforeIt() {
        assertEquals(
            listOf(1, 4, 5),
            kept(WetKind.SETTLED, null, WetKind.TAIL, WetKind.WHOLE, WetKind.WHOLE, WetKind.TAIL),
        )
    }

    @Test fun aTailNeverDropsTheWholeItFollows() {
        // The whole also emptied the settled runs, which the tail alone would not have.
        assertEquals(listOf(1, 2), kept(WetKind.SETTLED, WetKind.WHOLE, WetKind.TAIL))
    }

    @Test fun editsThatAreNotWetAreAllKeptInOrder() {
        assertEquals(listOf(0, 1, 3, 4), kept(null, null, WetKind.TAIL, null, WetKind.TAIL))
    }

    @Test fun nothingQueuedKeepsNothing() {
        assertEquals(emptyList<Int>(), kept())
    }

    @Test fun whatIsKeptLeavesTheBuffersAsApplyingEverythingWould() {
        val random = java.util.Random(7)
        val choices = arrayOf(WetKind.WHOLE, WetKind.SETTLED, WetKind.TAIL, null)
        repeat(2000) {
            val n = random.nextInt(12)
            val kinds = List(n) { choices[random.nextInt(choices.size)] }
            val keep = BooleanArray(n)
            WetEditCoalescing.mark(n, keep) { kinds[it] }
            val all = apply(kinds, kinds.indices.toList())
            val coalesced = apply(kinds, kinds.indices.filter { keep[it] })
            assertEquals("for $kinds", all, coalesced)
            // The last wet edit is what sets the final bounds, so it is never dropped.
            val lastWet = kinds.indices.lastOrNull { kinds[it] != null }
            if (lastWet != null) assertEquals(true, keep[lastWet])
            // And nothing that is not a wet edit ever is.
            for (i in kinds.indices) if (kinds[i] == null) assertEquals(true, keep[i])
        }
    }
}
