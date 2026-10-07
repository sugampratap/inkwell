package com.xnotes.core.stroke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * [PredictedTail] is ink nobody wrote, so what matters is that it is bounded: it starts exactly on
 * the stroke, holds the stroke's width, never reaches past its cap, and draws nothing at all for a
 * prediction it cannot trust.
 */
class PredictedTailTest {

    /** A straight two-point ribbon ending at (10, 0) with half-width [hw]. */
    private fun ribbon(hw: Double = 2.0): RibbonPoints = object : RibbonPoints {
        override val pointCount = 2
        override fun cx(i: Int) = i * 10.0
        override fun cy(i: Int) = 0.0
        override fun hw(i: Int) = hw
        override val hasRails = true
        override fun leftX(i: Int) = cx(i)
        override fun leftY(i: Int) = -hw
        override fun rightX(i: Int) = cx(i)
        override fun rightY(i: Int) = hw
    }

    private fun arc(t: PredictedTail): Double {
        var s = 0.0
        for (i in 1 until t.pointCount) s += hypot(t.cx(i) - t.cx(i - 1), t.cy(i) - t.cy(i - 1))
        return s
    }

    @Test
    fun startsOnTheStrokeAndKeepsItsWidth() {
        val t = PredictedTail()
        assertTrue(t.build(ribbon(), doubleArrayOf(14.0), doubleArrayOf(0.0), 1, 100.0))
        assertEquals(2, t.pointCount)
        assertEquals(10.0, t.cx(0), 0.0)
        assertEquals(0.0, t.cy(0), 0.0)
        assertEquals(14.0, t.cx(1), 1e-9)
        for (i in 0 until t.pointCount) assertEquals(2.0, t.hw(i), 0.0)
    }

    @Test
    fun railsSitAHalfWidthEitherSide() {
        val t = PredictedTail()
        t.build(ribbon(3.0), doubleArrayOf(20.0), doubleArrayOf(0.0), 1, 100.0)
        for (i in 0 until t.pointCount) {
            assertEquals(3.0, hypot(t.leftX(i) - t.cx(i), t.leftY(i) - t.cy(i)), 1e-9)
            assertEquals(3.0, hypot(t.rightX(i) - t.cx(i), t.rightY(i) - t.cy(i)), 1e-9)
            // Matches the stroke engine's orientation: left is the -normal side.
            assertEquals(-3.0, t.leftY(i), 1e-9)
            assertEquals(3.0, t.rightY(i), 1e-9)
        }
    }

    @Test
    fun arcIsCappedAtTheBudget() {
        val t = PredictedTail()
        t.build(ribbon(), doubleArrayOf(30.0, 60.0), doubleArrayOf(0.0, 0.0), 2, 12.5)
        assertEquals(12.5, arc(t), 1e-9)
        assertEquals(22.5, t.cx(t.pointCount - 1), 1e-9)
    }

    @Test
    fun cappedAcrossSeveralPoints() {
        val t = PredictedTail()
        t.build(
            ribbon(),
            doubleArrayOf(13.0, 13.0, 30.0),
            doubleArrayOf(0.0, 4.0, 4.0),
            3,
            10.0,
        )
        assertEquals(10.0, arc(t), 1e-9)
        assertEquals(4, t.pointCount)
    }

    @Test
    fun nothingForAPredictionThatStaysPut() {
        val t = PredictedTail()
        assertFalse(t.build(ribbon(), doubleArrayOf(10.0), doubleArrayOf(0.0), 1, 100.0))
        assertEquals(0, t.pointCount)
        assertFalse(t.hasRails)
    }

    @Test
    fun nothingForNaNOrNoBudget() {
        val t = PredictedTail()
        assertFalse(t.build(ribbon(), doubleArrayOf(Double.NaN), doubleArrayOf(1.0), 1, 100.0))
        assertFalse(t.build(ribbon(), doubleArrayOf(20.0), doubleArrayOf(0.0), 1, 0.0))
        assertFalse(t.build(ribbon(), doubleArrayOf(20.0), doubleArrayOf(0.0), 0, 100.0))
        assertFalse(t.build(ribbon(hw = 0.0), doubleArrayOf(20.0), doubleArrayOf(0.0), 1, 100.0))
    }

    @Test
    fun nothingForAnEmptyRibbon() {
        val empty = object : RibbonPoints {
            override val pointCount = 0
            override fun cx(i: Int) = 0.0
            override fun cy(i: Int) = 0.0
            override fun hw(i: Int) = 0.0
            override val hasRails = false
            override fun leftX(i: Int) = 0.0
            override fun leftY(i: Int) = 0.0
            override fun rightX(i: Int) = 0.0
            override fun rightY(i: Int) = 0.0
        }
        assertFalse(PredictedTail().build(empty, doubleArrayOf(5.0), doubleArrayOf(5.0), 1, 100.0))
    }

    @Test
    fun neverHoldsMoreThanItsCapacity() {
        val t = PredictedTail()
        val n = 40
        val px = DoubleArray(n) { 10.0 + (it + 1) * 1.0 }
        val py = DoubleArray(n)
        t.build(ribbon(), px, py, n, 1_000.0)
        assertEquals(PredictedTail.CAPACITY, t.pointCount)
    }

    @Test
    fun rebuildReplacesThePreviousTail() {
        val t = PredictedTail()
        t.build(ribbon(), doubleArrayOf(20.0, 30.0), doubleArrayOf(0.0, 0.0), 2, 100.0)
        assertEquals(3, t.pointCount)
        t.build(ribbon(), doubleArrayOf(12.0), doubleArrayOf(0.0), 1, 100.0)
        assertEquals(2, t.pointCount)
        t.clear()
        assertEquals(0, t.pointCount)
    }
}
