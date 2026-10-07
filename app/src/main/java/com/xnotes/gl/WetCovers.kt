package com.xnotes.gl

import com.xnotes.core.infinite.InkPass

/**
 * Which covers the runs of the stroke under the pen share, so each is stencilled from every run and
 * covered once, the way the stroke meshed whole is.
 *
 * Parts that cover alike (one pass, colour and alpha) share a cover, and the covers go down in the
 * order their first part was laid. Only stencilled passes can share one. Most ink has a single
 * cover; the pencil ([InkPass.GRAPHITE]) has two, its outer and then its pressed core, which is what
 * [MAX] allows, and only for it. Anything else in more than one is not the runs of one stroke, and
 * keeps drawing part by part.
 *
 * Pure, so the rule is unit-tested without a GL context.
 */
internal object WetCovers {

    /** The most covers one wet stroke shares: the pencil's outer and core. */
    const val MAX = 2

    /**
     * Group [count] parts, writing the index of each cover's first part into [heads] in the order
     * the covers go down. Returns how many covers there are, or 0 when the parts cannot share them.
     */
    inline fun group(
        count: Int,
        heads: IntArray,
        passAt: (Int) -> InkPass,
        sameCover: (Int, Int) -> Boolean,
    ): Int {
        var n = 0
        for (i in 0 until count) {
            val pass = passAt(i)
            if (pass != InkPass.MULTIPLY && pass != InkPass.SCREEN && pass != InkPass.TRANSLUCENT &&
                pass != InkPass.GRAPHITE
            ) {
                return 0
            }
            var g = 0
            while (g < n && !sameCover(heads[g], i)) g++
            if (g < n) continue
            // A second cover is the pencil's core over its outer, and nothing else's.
            if (n > 0 && (pass != InkPass.GRAPHITE || passAt(heads[0]) != InkPass.GRAPHITE)) return 0
            if (n >= heads.size || n >= MAX) return 0
            heads[n++] = i
        }
        return n
    }
}
