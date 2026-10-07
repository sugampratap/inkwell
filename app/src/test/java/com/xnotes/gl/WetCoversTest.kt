package com.xnotes.gl

import com.xnotes.core.infinite.InkPass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which covers a wet stroke's runs share: one for most ink, the outer and the core for the pencil. */
class WetCoversTest {

    /** A part: its pass and a key standing for its cover colour and alpha. */
    private class P(val pass: InkPass, val cover: Int)

    private fun group(parts: List<P>): Pair<Int, IntArray> {
        val heads = IntArray(WetCovers.MAX)
        val n = WetCovers.group(
            parts.size,
            heads,
            passAt = { parts[it].pass },
            sameCover = { a, b -> parts[a].pass == parts[b].pass && parts[a].cover == parts[b].cover },
        )
        return n to heads.copyOf(n)
    }

    @Test fun aHighlighterInRunsSharesOneCover() {
        val (n, heads) = group(List(9) { P(InkPass.MULTIPLY, 1) })
        assertEquals(1, n)
        assertArrayEquals(intArrayOf(0), heads)
    }

    @Test fun aPencilSharesTwoCoversTheOuterFirst() {
        // Settled outers, settled cores, then the tail's outer and core, as the scene lists them.
        val outer = P(InkPass.GRAPHITE, 45)
        val core = P(InkPass.GRAPHITE, 65)
        val (n, heads) = group(listOf(outer, outer, outer, core, core, outer, core))
        assertEquals(2, n)
        assertArrayEquals(intArrayOf(0, 3), heads)
    }

    @Test fun aLightPencilIsOneCover() {
        assertEquals(1, group(List(5) { P(InkPass.GRAPHITE, 45) }).first)
    }

    @Test fun twoCoversOfAnyOtherInkDoNotShare() {
        assertEquals(0, group(listOf(P(InkPass.MULTIPLY, 1), P(InkPass.MULTIPLY, 2))).first)
        assertEquals(0, group(listOf(P(InkPass.TRANSLUCENT, 1), P(InkPass.GRAPHITE, 2))).first)
        assertEquals(0, group(listOf(P(InkPass.GRAPHITE, 1), P(InkPass.MULTIPLY, 2))).first)
    }

    @Test fun aThirdCoverOrAnUnstencilledPartDoesNotShare() {
        assertEquals(0, group(listOf(P(InkPass.GRAPHITE, 1), P(InkPass.GRAPHITE, 2), P(InkPass.GRAPHITE, 3))).first)
        assertEquals(0, group(listOf(P(InkPass.OPAQUE, 1))).first)
        assertEquals(0, group(listOf(P(InkPass.GRAPHITE, 1), P(InkPass.GLOW, 1))).first)
    }

    @Test fun nothingIsNoCover() {
        assertEquals(0, group(emptyList()).first)
    }
}
