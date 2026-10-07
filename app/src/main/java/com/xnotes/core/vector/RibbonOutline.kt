package com.xnotes.core.vector

import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Pt
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * A swept-disc ink ribbon as a handful of filled outlines, for a file format that pays per byte.
 *
 * On screen a ribbon is a disc at every sample and a quad between each pair, which is cheap to draw
 * and can never hole. Written into a PDF that way it is a filled path *per sample*, two of them, so a
 * page of handwriting came to tens of megabytes. Here the same ink is a few subpaths per stroke:
 *
 *  - **runs**: stretches where both rails keep moving forward and the line turns gently, written as
 *    one polygon, left rail out and right rail back. Two points a sample instead of a quad and a
 *    four-curve circle.
 *  - **quads**: the segments that would not make a clean run (a turn sharper than [SHARP_TURN_COS],
 *    a rail running backwards on a curve tighter than the pen is wide, a width jump bigger than the
 *    step), kept as the screen's own [Geometry.ribbonQuad].
 *  - **discs**: the round caps at both ends and at every join a run or a quad meets something
 *    else, which is all the swept discs in between were ever needed for.
 *
 * Every subpath is wound the same way (positive area), so a nonzero fill unions them however they
 * overlap: the property the screen's quads and discs have, kept, because a single outline wound
 * both ways at a cusp cancels itself into a hole there.
 *
 * Samples closer than [minStep] that change the width by less than [minWidthStep] are folded into
 * their neighbours first; ink drawn at the digitiser's rate is far denser than a page needs.
 */
object RibbonOutline {

    /** Below this cosine between neighbouring segments a join is a corner, and gets a disc. */
    const val SHARP_TURN_COS = 0.7

    class Outline(
        /** Closed polygons, packed x,y, each wound with positive area. */
        val polygons: List<DoubleArray>,
        /** Discs, packed cx,cy,r. */
        val discs: DoubleArray,
    )

    fun build(
        centers: FloatArray,
        radii: FloatArray,
        from: Int,
        count: Int,
        minStep: Double,
        minWidthStep: Double,
    ): Outline {
        if (count <= 0) return Outline(emptyList(), DoubleArray(0))
        // 1. Fold samples too close to matter into their neighbours, keeping both ends.
        val keep = IntArray(count)
        var m = 0
        keep[m++] = from
        for (i in from + 1 until from + count - 1) {
            val k = keep[m - 1]
            val step = hypot(cx(centers, i) - cx(centers, k), cy(centers, i) - cy(centers, k))
            if (step >= minStep || abs(radii[i] - radii[k]) >= minWidthStep) keep[m++] = i
        }
        if (count > 1) keep[m++] = from + count - 1
        val x = DoubleArray(m) { cx(centers, keep[it]) }
        val y = DoubleArray(m) { cy(centers, keep[it]) }
        val r = DoubleArray(m) { radii[keep[it]].toDouble().coerceAtLeast(0.0) }

        val discs = DiscList()
        if (m == 1 || allCoincident(x, y)) {
            var big = 0.0
            for (v in r) big = max(big, v)
            discs.add(x[0], y[0], big)
            return Outline(emptyList(), discs.toArray())
        }

        // 2. Unit tangents by central difference, carried across coincident points.
        val tx = DoubleArray(m)
        val ty = DoubleArray(m)
        var lx = 1.0
        var ly = 0.0
        for (j in 0 until m) {
            val a = if (j == 0) 0 else j - 1
            val b = if (j == m - 1) m - 1 else j + 1
            val dx = x[b] - x[a]
            val dy = y[b] - y[a]
            val len = hypot(dx, dy)
            if (len > 1e-9) { lx = dx / len; ly = dy / len }
            tx[j] = lx
            ty[j] = ly
        }
        val leftX = DoubleArray(m) { x[it] - ty[it] * r[it] }
        val leftY = DoubleArray(m) { y[it] + tx[it] * r[it] }
        val rightX = DoubleArray(m) { x[it] + ty[it] * r[it] }
        val rightY = DoubleArray(m) { y[it] - tx[it] * r[it] }

        // 3. Which segments can join a run.
        val clean = BooleanArray(m - 1) { j ->
            val dx = x[j + 1] - x[j]
            val dy = y[j + 1] - y[j]
            val len = hypot(dx, dy)
            len > 1e-9 &&
                len > abs(r[j + 1] - r[j]) &&
                tx[j] * tx[j + 1] + ty[j] * ty[j + 1] >= SHARP_TURN_COS &&
                (leftX[j + 1] - leftX[j]) * dx + (leftY[j + 1] - leftY[j]) * dy > 0.0 &&
                (rightX[j + 1] - rightX[j]) * dx + (rightY[j + 1] - rightY[j]) * dy > 0.0
        }

        val polygons = ArrayList<DoubleArray>()
        discs.add(x[0], y[0], r[0])
        discs.add(x[m - 1], y[m - 1], r[m - 1])
        var j = 0
        while (j < m - 1) {
            if (!clean[j]) {
                val q = Geometry.ribbonQuad(Pt(x[j], y[j]), r[j], Pt(x[j + 1], y[j + 1]), r[j + 1])
                if (q.size == 4) polygons.add(DoubleArray(8) { if (it % 2 == 0) q[it / 2].x else q[it / 2].y })
                discs.add(x[j], y[j], r[j])
                discs.add(x[j + 1], y[j + 1], r[j + 1])
                j++
                continue
            }
            val a = j
            while (j < m - 1 && clean[j]) j++
            val b = j // the run spans points a..b
            val poly = DoubleArray(4 * (b - a + 1))
            var k = 0
            for (p in a..b) { poly[k++] = leftX[p]; poly[k++] = leftY[p] }
            for (p in b downTo a) { poly[k++] = rightX[p]; poly[k++] = rightY[p] }
            polygons.add(if (signedArea(poly) < 0.0) reversed(poly) else poly)
            // The run's flat ends meet whatever comes next, or are the stroke's own caps.
            discs.add(x[a], y[a], r[a])
            discs.add(x[b], y[b], r[b])
        }
        return Outline(polygons, discs.toArray())
    }

    /** Twice the signed area of a packed polygon (positive = the winding every subpath shares). */
    fun signedArea(p: DoubleArray): Double {
        val n = p.size / 2
        var s = 0.0
        for (i in 0 until n) {
            val k = (i + 1) % n
            s += p[2 * i] * p[2 * k + 1] - p[2 * k] * p[2 * i + 1]
        }
        return s
    }

    private fun reversed(p: DoubleArray): DoubleArray {
        val n = p.size / 2
        return DoubleArray(p.size) { i -> val v = n - 1 - i / 2; if (i % 2 == 0) p[2 * v] else p[2 * v + 1] }
    }

    private fun allCoincident(x: DoubleArray, y: DoubleArray): Boolean {
        for (i in 1 until x.size) if (hypot(x[i] - x[0], y[i] - y[0]) > 1e-9) return false
        return true
    }

    private fun cx(c: FloatArray, i: Int) = c[2 * i].toDouble()
    private fun cy(c: FloatArray, i: Int) = c[2 * i + 1].toDouble()

    /** Discs, de-duplicated against the last few added: a run's end and the next quad's start meet. */
    private class DiscList {
        private var a = DoubleArray(24)
        private var n = 0
        fun add(x: Double, y: Double, r: Double) {
            if (r <= 0.0) return
            var k = n - 3
            var seen = 0
            while (k >= 0 && seen < 4) {
                if (a[k] == x && a[k + 1] == y && a[k + 2] >= r) return
                k -= 3
                seen++
            }
            if (n + 3 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x; a[n++] = y; a[n++] = r
        }
        fun toArray(): DoubleArray = a.copyOf(n)
    }
}
