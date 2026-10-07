package com.xnotes.core.stroke

import kotlin.math.hypot

/**
 * Ink drawn ahead of the pen: a few provisional points from the live ribbon's last point toward
 * where the motion predictor says the nib will be by the time this present reaches the glass.
 *
 * Even a front buffer cannot close the gap the digitizer and the input pipeline open between the
 * nib and the newest sample, and on a fast hand that gap is a visible trail. Drawing the predicted
 * path closes it. Nothing here ever reaches the stroke: the tail is meshed into the wet layer only
 * and rebuilt on every present, so a wrong guess lasts one refresh and then is simply gone.
 *
 * The width is held at the ribbon's last half-width, so the tail continues the line rather than
 * tapering into a false pen-up, and its arc is capped so a jerky prediction cannot fling ink.
 *
 * One instance is reused for the whole stroke, so a present allocates nothing here.
 */
class PredictedTail : RibbonPoints {

    private val xs = DoubleArray(CAPACITY)
    private val ys = DoubleArray(CAPACITY)
    private val lx = DoubleArray(CAPACITY)
    private val ly = DoubleArray(CAPACITY)
    private val rx = DoubleArray(CAPACITY)
    private val ry = DoubleArray(CAPACITY)
    private var half = 0.0

    override var pointCount = 0
        private set

    override val hasRails: Boolean get() = pointCount >= 2

    override fun cx(i: Int): Double = xs[i]
    override fun cy(i: Int): Double = ys[i]
    override fun hw(i: Int): Double = half
    override fun leftX(i: Int): Double = lx[i]
    override fun leftY(i: Int): Double = ly[i]
    override fun rightX(i: Int): Double = rx[i]
    override fun rightY(i: Int): Double = ry[i]

    fun clear() {
        pointCount = 0
    }

    /**
     * Rebuild the tail from the end of [ribbon] through the first [n] points of [px], [py], in the
     * ribbon's own coordinates, keeping at most [maxLength] of arc. Returns whether there is
     * anything to draw; a prediction that goes nowhere, or a ribbon with no width, leaves it empty.
     */
    fun build(ribbon: RibbonPoints, px: DoubleArray, py: DoubleArray, n: Int, maxLength: Double): Boolean {
        pointCount = 0
        val last = ribbon.pointCount - 1
        if (last < 0 || n <= 0 || !(maxLength > 0.0)) return false
        val w = ribbon.hw(last)
        if (!(w > 0.0) || !w.isFinite()) return false
        half = w
        var x = ribbon.cx(last)
        var y = ribbon.cy(last)
        if (!x.isFinite() || !y.isFinite()) return false
        xs[0] = x
        ys[0] = y
        pointCount = 1
        var budget = maxLength
        val count = minOf(n, px.size, py.size, CAPACITY - 1)
        for (k in 0 until count) {
            val dx = px[k] - x
            val dy = py[k] - y
            val d = hypot(dx, dy)
            // Also false for NaN, so a broken prediction is skipped rather than drawn.
            if (!(d > MIN_STEP)) continue
            if (d >= budget) {
                val f = budget / d
                x += dx * f
                y += dy * f
                append(x, y)
                break
            }
            x = px[k]
            y = py[k]
            append(x, y)
            budget -= d
        }
        if (pointCount < 2) {
            pointCount = 0
            return false
        }
        buildRails()
        return true
    }

    private fun append(x: Double, y: Double) {
        xs[pointCount] = x
        ys[pointCount] = y
        pointCount++
    }

    /** Both edges, offset along each point's normal, in the same orientation the stroke engine uses. */
    private fun buildRails() {
        val n = pointCount
        for (i in 0 until n) {
            val a = if (i == 0) 0 else i - 1
            val b = if (i == n - 1) n - 1 else i + 1
            var tx = xs[b] - xs[a]
            var ty = ys[b] - ys[a]
            val len = hypot(tx, ty)
            if (len > 0.0) {
                tx /= len
                ty /= len
            } else {
                tx = 1.0
                ty = 0.0
            }
            val nx = -ty
            val ny = tx
            lx[i] = xs[i] - nx * half
            ly[i] = ys[i] - ny * half
            rx[i] = xs[i] + nx * half
            ry[i] = ys[i] + ny * half
        }
    }

    companion object {
        /** The anchor plus up to this many predicted points less one. */
        const val CAPACITY = 8

        /** Steps shorter than this (content px) add nothing but a degenerate quad. */
        const val MIN_STEP = 1e-3

        /**
         * How far ahead the tail may reach, in screen pixels. About one refresh of a brisk hand on a
         * tablet; beyond it a wrong guess starts to read as a flick rather than as the line.
         */
        const val MAX_SCREEN_PX = 28.0
    }
}
