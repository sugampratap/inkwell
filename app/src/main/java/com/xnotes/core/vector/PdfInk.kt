package com.xnotes.core.vector

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * A stroke of ink as PDF path operators, written to be small without looking any different.
 *
 * The ribbon arrives the way the screen draws it: a centre and a radius per sample, a fraction of a
 * millimetre apart. Written that way a dense page of handwriting is half a megabyte of numbers.
 * The leading note apps keep the same page to a few dozen kilobytes, still vector and sharp at any
 * zoom, and this does it the same way:
 *
 *  - The **centreline** is written, not the outline: one curve the viewer widens itself, with round
 *    caps and joins, so the two rails, every corner and both ends come from a single path. The width
 *    is held over runs that vary less than [WIDTH_STEP_PT] (a fiftieth of a millimetre either side),
 *    each run one stroke, consecutive runs meeting under round caps of all but equal width.
 *  - It is written as fitted cubic Béziers ([BezierFit]), not a line per sample, never straying more
 *    than [FIT_TOLERANCE_PT] from the ink.
 *  - Coordinates are whole tenths of a point (0.035 mm) under one `cm`, which is the precision a
 *    print resolves and a digit shorter than a decimal.
 *
 * Runs overlap where they meet, which only an ink that paints the same colour wherever it lands can
 * afford. A translucent or blended ink (the highlighter) is written as one stroke when its width is
 * even, since a single stroke is painted once however it crosses itself, and otherwise as filled
 * outlines ([RibbonOutline]) with fitted rails, all in one fill.
 *
 * Pure (no PDF library), so its size and fidelity are measured on the plain JVM.
 */
object PdfInk {

    /** Largest distance (points) a fitted curve may stray from the samples it replaces: 0.02 mm. */
    const val FIT_TOLERANCE_PT = 0.06

    /** How far (points) a run's width may spread before a new run starts: ±0.018 mm per edge. */
    const val WIDTH_STEP_PT = 0.1

    /** Coordinates are written in these units per point: tenths, 0.035 mm. */
    const val UNITS_PER_PT = 10

    /** Samples closer than this (points) whose width moves less than [FOLD_WIDTH_PT] are folded
     *  before fitting; the curve would pass between them anyway. */
    const val FOLD_STEP_PT = 0.3
    const val FOLD_WIDTH_PT = 0.04

    /**
     * Append the operators painting the ribbon [centers]/[radii] (content px, [from] for [count]) to
     * [out], mapped into user space by `(ox + x·sx, oy + y·sy)`. The colour (fill and stroke) must
     * already be set. [overlapSafe] says painting a spot twice looks the same as once (full opacity,
     * normal blend). Self-contained: it saves and restores the graphics state around itself.
     * Returns whether anything was written.
     */
    fun write(
        out: StringBuilder,
        centers: FloatArray,
        radii: FloatArray,
        from: Int,
        count: Int,
        ox: Double,
        oy: Double,
        sx: Double,
        sy: Double,
        overlapSafe: Boolean,
    ): Boolean {
        if (count <= 0) return false
        val k = ((abs(sx) + abs(sy)) / 2.0).coerceAtLeast(1e-9)
        var lo = Double.MAX_VALUE
        var hi = 0.0
        for (i in from until from + count) {
            val w = 2.0 * radii[i] * k
            lo = min(lo, w); hi = max(hi, w)
        }
        return if (overlapSafe || hi - lo <= WIDTH_STEP_PT) {
            centreline(out, centers, radii, from, count, ox, oy, sx, sy)
        } else {
            outline(out, centers, radii, from, count, ox, oy, sx, sy)
        }
    }

    /**
     * The centreline encoding (see the class notes). Runs overlap at their joins, so this is for
     * an [write] that is overlap-safe, or a stroke whose width is even enough to be one run.
     */
    fun centreline(
        out: StringBuilder,
        centers: FloatArray,
        radii: FloatArray,
        from: Int,
        count: Int,
        ox: Double,
        oy: Double,
        sx: Double,
        sy: Double,
        tolerance: Double = FIT_TOLERANCE_PT,
        widthStep: Double = WIDTH_STEP_PT,
    ): Boolean {
        if (count <= 0) return false
        val k = ((abs(sx) + abs(sy)) / 2.0).coerceAtLeast(1e-9)
        val xs = DoubleArray(count)
        val ys = DoubleArray(count)
        val ws = DoubleArray(count)
        var n = 0
        for (i in from until from + count) {
            val x = ox + centers[2 * i] * sx
            val y = oy + centers[2 * i + 1] * sy
            val w = 2.0 * radii[i].coerceAtLeast(0f) * k
            val last = i == from + count - 1
            // Only a sample on top of the last is dropped: the fit weighs every other one, so none
            // strays further than the tolerance however tightly the pen turned.
            if (n > 0 && !last && hypot(x - xs[n - 1], y - ys[n - 1]) < 1e-3 && abs(w - ws[n - 1]) < FOLD_WIDTH_PT) continue
            xs[n] = x; ys[n] = y; ws[n] = w; n++
        }
        val wr = Writer(out)
        wr.begin()
        out.append("1 J\n1 j\n")
        var lastWidth = -1L
        var a = 0
        while (true) {
            // Grow the run while its widths stay within the step. It always takes the next point,
            // so an abrupt change between two samples still leaves no gap between them.
            var lo = ws[a]
            var hi = ws[a]
            var b = a
            while (b + 1 < n) {
                val w = ws[b + 1]
                if (b > a && max(hi, w) - min(lo, w) > widthStep) break
                lo = min(lo, w); hi = max(hi, w); b++
            }
            val width = wr.units((lo + hi) / 2.0)
            if (width > 0L) {
                if (width != lastWidth) {
                    out.append(width).append(" w\n")
                    lastWidth = width
                }
                wr.point(xs[a], ys[a]); out.append("m\n")
                // A lone point is a dot: a zero-length subpath under round caps paints a disc of the
                // line width (ISO 32000-1 §8.5.3.2).
                if (b == a) wr.lineTo(xs[a], ys[a]) else BezierFit.fit(xs, ys, a, b, tolerance, wr)
                out.append("S\n")
            }
            if (b >= n - 1) break
            a = b // the next run starts where this one ended, so their round caps overlap there
        }
        wr.end()
        return true
    }

    /**
     * The outline encoding: the ribbon as [RibbonOutline]'s positively wound outlines, each rail a
     * fitted curve, and its discs as four-curve circles, all in one nonzero fill, so every spot is
     * painted exactly once whatever the blend.
     */
    fun outline(
        out: StringBuilder,
        centers: FloatArray,
        radii: FloatArray,
        from: Int,
        count: Int,
        ox: Double,
        oy: Double,
        sx: Double,
        sy: Double,
        tolerance: Double = FIT_TOLERANCE_PT,
    ): Boolean {
        if (count <= 0) return false
        val k = ((abs(sx) + abs(sy)) / 2.0).coerceAtLeast(1e-9)
        val o = RibbonOutline.build(centers, radii, from, count, minStep = FOLD_STEP_PT / k, minWidthStep = FOLD_WIDTH_PT / k)
        if (o.polygons.isEmpty() && o.discs.isEmpty()) return false
        val wr = Writer(out)
        wr.begin()
        for (poly in o.polygons) {
            val n = poly.size / 2
            val xs = DoubleArray(n) { ox + poly[2 * it] * sx }
            val ys = DoubleArray(n) { oy + poly[2 * it + 1] * sy }
            wr.point(xs[0], ys[0]); out.append("m\n")
            // Every outline is two rails of equal length, out along one and back along the other.
            val half = n / 2
            if (n >= 6 && n % 2 == 0) {
                BezierFit.fit(xs, ys, 0, half - 1, tolerance, wr)
                wr.lineTo(xs[half], ys[half])
                BezierFit.fit(xs, ys, half, n - 1, tolerance, wr)
            } else {
                for (i in 1 until n) wr.lineTo(xs[i], ys[i])
            }
            out.append("h\n")
        }
        val d = o.discs
        for (i in 0 until d.size / 3) wr.circle(ox + d[3 * i] * sx, oy + d[3 * i + 1] * sy, d[3 * i + 2] * k, sx < 0, sy < 0)
        out.append("f\n")
        wr.end()
        return true
    }

    /**
     * Discs of one [radius] (content px) at [centers], as one stroke of zero-length round-capped
     * subpaths: a dot grid's thousands of four-curve circles in a few bytes a dot. One stroke paints
     * each spot once, so this holds under any opacity. The stroke colour must already be set.
     */
    fun dots(
        out: StringBuilder,
        centers: List<com.xnotes.core.geometry.Pt>,
        radius: Double,
        ox: Double,
        oy: Double,
        sx: Double,
        sy: Double,
    ): Boolean {
        if (centers.isEmpty() || radius <= 0.0) return false
        val k = (abs(sx) + abs(sy)) / 2.0
        val wr = Writer(out)
        val width = max(1L, wr.units(2.0 * radius * k))
        wr.begin()
        out.append("1 J\n").append(width).append(" w\n")
        for (c in centers) {
            val x = ox + c.x * sx
            val y = oy + c.y * sy
            wr.point(x, y); out.append("m ")
            wr.point(x, y); out.append("l\n")
        }
        out.append("S\n")
        wr.end()
        return true
    }

    /** Writes points as whole [UNITS_PER_PT] units under a scaling `cm`, and fitted spans as operators. */
    private class Writer(val sb: StringBuilder) : BezierFit.Sink {

        fun begin() {
            sb.append("q\n").append(1.0 / UNITS_PER_PT).append(" 0 0 ").append(1.0 / UNITS_PER_PT).append(" 0 0 cm\n")
        }

        fun end() {
            sb.append("Q\n")
        }

        fun units(v: Double): Long = (v * UNITS_PER_PT).roundToLong()

        fun point(x: Double, y: Double) {
            sb.append(units(x)).append(' ').append(units(y)).append(' ')
        }

        override fun lineTo(x: Double, y: Double) {
            point(x, y)
            sb.append("l\n")
        }

        override fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
            point(x1, y1)
            point(x2, y2)
            point(x3, y3)
            sb.append("c\n")
        }

        /**
         * A disc as four Béziers, wound as [RibbonOutline]'s polygons are once mapped (a flipped
         * axis, [flipX] or [flipY], turns them over), so a nonzero fill unions it with them instead
         * of cutting a hole where they overlap.
         */
        fun circle(cx: Double, cy: Double, r: Double, flipX: Boolean, flipY: Boolean) {
            val q = r * 0.5522847498307936
            val ex = if (flipX) -1.0 else 1.0
            val ey = if (flipY) -1.0 else 1.0
            fun x(dx: Double) = cx + dx * ex
            fun y(dy: Double) = cy + dy * ey
            point(x(r), y(0.0)); sb.append("m\n")
            curveTo(x(r), y(q), x(q), y(r), x(0.0), y(r))
            curveTo(x(-q), y(r), x(-r), y(q), x(-r), y(0.0))
            curveTo(x(-r), y(-q), x(-q), y(-r), x(0.0), y(-r))
            curveTo(x(q), y(-r), x(r), y(-q), x(r), y(0.0))
            sb.append("h\n")
        }
    }
}
