package com.xnotes.core.vector

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fits a polyline with a chain of cubic Béziers, every input point within a tolerance of the curve:
 * Schneider's algorithm ("An Algorithm for Automatically Fitting Digitized Curves", Graphics Gems,
 * 1990) with recursive splitting at the worst point.
 *
 * Ink is drawn from samples a fraction of a millimetre apart, and a file that writes every one of
 * them pays for detail no print or screen shows. A gently curving stretch of handwriting is one
 * cubic per letter stroke, not one line per sample, which is most of how the leading note apps keep
 * a page of ink to a few kilobytes.
 *
 * Works on primitive arrays and reports through [Sink], so a page of strokes allocates no points.
 */
object BezierFit {

    /** Receives the fitted path, after the caller has moved to the first point. */
    interface Sink {
        fun lineTo(x: Double, y: Double)
        fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double)
    }

    /** Newton-Raphson reparameterization passes tried before a span is split. */
    private const val REPARAM_ITERS = 4

    /** A span this many times over tolerance is split at once rather than reparameterized. */
    private const val REPARAM_SLACK = 4.0

    /**
     * Fit points [from]..[to] (inclusive) of [xs], [ys]. The path starts at point [from] (not
     * emitted) and ends exactly at point [to]; no input point lies further than [tol] from it.
     */
    fun fit(xs: DoubleArray, ys: DoubleArray, from: Int, to: Int, tol: Double, sink: Sink) {
        if (to <= from) return
        if (to - from == 1) {
            sink.lineTo(xs[to], ys[to])
            return
        }
        val (t1x, t1y) = endTangent(xs, ys, from, to, +1)
        val (t2x, t2y) = endTangent(xs, ys, to, from, -1)
        Fitter(xs, ys, tol, sink).fitSpan(from, to, t1x, t1y, t2x, t2y)
    }

    /** The unit direction leaving point [at] toward [toward] (step [dir]), skipping points on top of it. */
    private fun endTangent(xs: DoubleArray, ys: DoubleArray, at: Int, toward: Int, dir: Int): Pair<Double, Double> {
        var i = at + dir
        while (i != toward && hypot(xs[i] - xs[at], ys[i] - ys[at]) < 1e-9) i += dir
        val dx = xs[i] - xs[at]
        val dy = ys[i] - ys[at]
        val len = hypot(dx, dy)
        return if (len < 1e-12) 0.0 to 0.0 else (dx / len) to (dy / len)
    }

    private class Fitter(val xs: DoubleArray, val ys: DoubleArray, val tol: Double, val sink: Sink) {
        private var u = DoubleArray(16)
        private val bez = DoubleArray(8)
        private var splitAt = 0

        fun fitSpan(first: Int, last: Int, t1x: Double, t1y: Double, t2x: Double, t2y: Double) {
            val n = last - first + 1
            if (n == 2) {
                sink.lineTo(xs[last], ys[last])
                return
            }
            if (u.size < n) u = DoubleArray(max(n, u.size * 2))
            chordParams(first, last)
            generate(first, last, t1x, t1y, t2x, t2y)
            var err = maxError(first, last)
            if (err <= tol) return emit()
            if (err <= tol * REPARAM_SLACK) {
                repeat(REPARAM_ITERS) {
                    reparameterize(first, last)
                    generate(first, last, t1x, t1y, t2x, t2y)
                    err = maxError(first, last)
                    if (err <= tol) return emit()
                }
            }
            // Split at the worst point, the two halves meeting there along one shared tangent so the
            // joint stays smooth.
            val s = splitAt.coerceIn(first + 1, last - 1)
            var cx = xs[s - 1] - xs[s + 1]
            var cy = ys[s - 1] - ys[s + 1]
            var cl = hypot(cx, cy)
            if (cl < 1e-12) {
                cx = xs[s - 1] - xs[s]
                cy = ys[s - 1] - ys[s]
                cl = hypot(cx, cy)
            }
            if (cl < 1e-12) {
                fitSpan(first, s, t1x, t1y, 0.0, 0.0)
                fitSpan(s, last, 0.0, 0.0, t2x, t2y)
                return
            }
            cx /= cl
            cy /= cl
            fitSpan(first, s, t1x, t1y, cx, cy)
            fitSpan(s, last, -cx, -cy, t2x, t2y)
        }

        private fun emit() {
            sink.curveTo(bez[2], bez[3], bez[4], bez[5], bez[6], bez[7])
        }

        private fun chordParams(first: Int, last: Int) {
            u[0] = 0.0
            for (i in first + 1..last) u[i - first] = u[i - first - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            val total = u[last - first]
            if (total <= 0.0) {
                for (i in 0..last - first) u[i] = i.toDouble() / (last - first)
            } else {
                for (i in 1..last - first) u[i] /= total
            }
        }

        /** Least-squares control points for fixed end tangents (zero tangent: the chord's third). */
        private fun generate(first: Int, last: Int, t1x: Double, t1y: Double, t2x: Double, t2y: Double) {
            val p0x = xs[first]
            val p0y = ys[first]
            val p3x = xs[last]
            val p3y = ys[last]
            var c00 = 0.0
            var c01 = 0.0
            var c11 = 0.0
            var x0 = 0.0
            var x1 = 0.0
            for (i in 0..last - first) {
                val t = u[i]
                val mt = 1 - t
                val b0 = mt * mt * mt
                val b1 = 3 * mt * mt * t
                val b2 = 3 * mt * t * t
                val b3 = t * t * t
                val a1x = t1x * b1
                val a1y = t1y * b1
                val a2x = t2x * b2
                val a2y = t2y * b2
                c00 += a1x * a1x + a1y * a1y
                c01 += a1x * a2x + a1y * a2y
                c11 += a2x * a2x + a2y * a2y
                val tx = xs[first + i] - (p0x * (b0 + b1) + p3x * (b2 + b3))
                val ty = ys[first + i] - (p0y * (b0 + b1) + p3y * (b2 + b3))
                x0 += a1x * tx + a1y * ty
                x1 += a2x * tx + a2y * ty
            }
            val det = c00 * c11 - c01 * c01
            val segLen = hypot(p3x - p0x, p3y - p0y)
            var alpha1 = if (abs(det) > 1e-12) (x0 * c11 - x1 * c01) / det else 0.0
            var alpha2 = if (abs(det) > 1e-12) (c00 * x1 - c01 * x0) / det else 0.0
            val eps = 1e-6 * segLen
            if (alpha1 < eps || alpha2 < eps || alpha1 > segLen * 3 || alpha2 > segLen * 3) {
                alpha1 = segLen / 3
                alpha2 = segLen / 3
            }
            bez[0] = p0x; bez[1] = p0y
            bez[2] = p0x + t1x * alpha1; bez[3] = p0y + t1y * alpha1
            bez[4] = p3x + t2x * alpha2; bez[5] = p3y + t2y * alpha2
            bez[6] = p3x; bez[7] = p3y
        }

        private fun maxError(first: Int, last: Int): Double {
            var worst = 0.0
            splitAt = (first + last) / 2
            for (i in first + 1 until last) {
                val t = u[i - first]
                val mt = 1 - t
                val b0 = mt * mt * mt
                val b1 = 3 * mt * mt * t
                val b2 = 3 * mt * t * t
                val b3 = t * t * t
                val x = bez[0] * b0 + bez[2] * b1 + bez[4] * b2 + bez[6] * b3
                val y = bez[1] * b0 + bez[3] * b1 + bez[5] * b2 + bez[7] * b3
                val d = hypot(x - xs[i], y - ys[i])
                if (d > worst) {
                    worst = d
                    splitAt = i
                }
            }
            return worst
        }

        private fun reparameterize(first: Int, last: Int) {
            for (i in 1 until last - first) {
                val t = u[i]
                val px = xs[first + i]
                val py = ys[first + i]
                val mt = 1 - t
                // Q(t), Q'(t), Q''(t)
                val qx = bez[0] * mt * mt * mt + 3 * bez[2] * mt * mt * t + 3 * bez[4] * mt * t * t + bez[6] * t * t * t
                val qy = bez[1] * mt * mt * mt + 3 * bez[3] * mt * mt * t + 3 * bez[5] * mt * t * t + bez[7] * t * t * t
                val d1x = 3 * ((bez[2] - bez[0]) * mt * mt + 2 * (bez[4] - bez[2]) * mt * t + (bez[6] - bez[4]) * t * t)
                val d1y = 3 * ((bez[3] - bez[1]) * mt * mt + 2 * (bez[5] - bez[3]) * mt * t + (bez[7] - bez[5]) * t * t)
                val d2x = 6 * ((bez[4] - 2 * bez[2] + bez[0]) * mt + (bez[6] - 2 * bez[4] + bez[2]) * t)
                val d2y = 6 * ((bez[5] - 2 * bez[3] + bez[1]) * mt + (bez[7] - 2 * bez[5] + bez[3]) * t)
                val num = (qx - px) * d1x + (qy - py) * d1y
                val den = d1x * d1x + d1y * d1y + (qx - px) * d2x + (qy - py) * d2y
                if (abs(den) > 1e-12) u[i] = (t - num / den).coerceIn(0.0, 1.0)
            }
        }
    }

    /** Distance from ([px], [py]) to the cubic ([b] packed p0..p3), by dense sampling; for tests. */
    fun distanceToCubic(b: DoubleArray, px: Double, py: Double, steps: Int = 200): Double {
        var best = Double.MAX_VALUE
        for (k in 0..steps) {
            val t = k.toDouble() / steps
            val mt = 1 - t
            val x = b[0] * mt * mt * mt + 3 * b[2] * mt * mt * t + 3 * b[4] * mt * t * t + b[6] * t * t * t
            val y = b[1] * mt * mt * mt + 3 * b[3] * mt * mt * t + 3 * b[5] * mt * t * t + b[7] * t * t * t
            best = minOf(best, sqrt((x - px) * (x - px) + (y - py) * (y - py)))
        }
        return best
    }
}
