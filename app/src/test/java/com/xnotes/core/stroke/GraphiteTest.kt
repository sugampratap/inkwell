package com.xnotes.core.stroke

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GraphiteTest {

    private fun u(b: Byte) = b.toInt() and 0xFF

    @Test fun theGrainIsTheSameEveryTimeItIsMade() {
        val a = Graphite.tile(Graphite.TILE, Graphite.SEED)
        val b = Graphite.tile(Graphite.TILE, Graphite.SEED)
        assertArrayEquals(a, b)
        assertArrayEquals(a, Graphite.tile)
        assertEquals(Graphite.TILE * Graphite.TILE, a.size)
    }

    @Test fun anotherSeedIsAnotherPaper() {
        val a = Graphite.tile(64, 1)
        val b = Graphite.tile(64, 2)
        var differ = 0
        for (i in a.indices) if (a[i] != b[i]) differ++
        assertTrue("only $differ of ${a.size} texels differ", differ > a.size / 2)
    }

    @Test fun theGrainSpansTheToothFromAValleyToAPeak() {
        val t = Graphite.tile
        var min = 255
        var max = 0
        for (b in t) {
            min = minOf(min, u(b))
            max = maxOf(max, u(b))
        }
        // A valley still takes the floor, so a thin line thins rather than breaking; a peak takes it all.
        assertEquals((Graphite.FLOOR * 255 + 0.5).toInt(), min)
        assertEquals(255, max)
        assertTrue("mean ${Graphite.meanGrain}", Graphite.meanGrain in 0.55..0.8)
    }

    /**
     * Tileable: laid edge to edge, the step across the seam (last column to first, last row to
     * first) is no bigger than an ordinary step between neighbours inside the tile. A tile that did
     * not wrap would show a line there.
     */
    @Test fun tilesMeetWithoutASeam() {
        val n = Graphite.TILE
        val t = Graphite.tile
        fun at(x: Int, y: Int) = u(t[y * n + x]).toDouble()
        var inside = 0.0
        var seamX = 0.0
        var seamY = 0.0
        for (y in 0 until n) {
            for (x in 0 until n - 1) inside += abs(at(x + 1, y) - at(x, y))
            seamX += abs(at(0, y) - at(n - 1, y))
        }
        for (x in 0 until n) seamY += abs(at(x, 0) - at(x, n - 1))
        val meanInside = inside / (n * (n - 1))
        assertTrue("seam x ${seamX / n} vs inside $meanInside", seamX / n < meanInside * 1.25)
        assertTrue("seam y ${seamY / n} vs inside $meanInside", seamY / n < meanInside * 1.25)
    }

    @Test fun theLatticeHashIsFixedAndInRange() {
        assertEquals(Graphite.hash01(3, 5, 9), Graphite.hash01(3, 5, 9), 0.0)
        for (i in 0 until 1000) assertTrue(Graphite.hash01(i, i * 7, 11) in 0.0..1.0)
        assertTrue(Graphite.hash01(3, 5, 9) != Graphite.hash01(5, 3, 9))
    }

    @Test fun pressureIsReadBackFromTheWidthTheEngineGaveIt() {
        val base = 2.0
        val m = 0.45
        for (p in listOf(0.0, 0.25, 0.5, 0.8, 1.0)) {
            val hw = (base * (m + (1 - m) * p) / 2.0).toFloat()
            assertEquals(p, Graphite.pressureAt(hw, base, true, m), 1e-5)
        }
        // Wider than full (a resized or direction-swollen point) or thinner than the floor clamps.
        assertEquals(1.0, Graphite.pressureAt(5f, base, true, m), 0.0)
        assertEquals(0.0, Graphite.pressureAt(0.1f, base, true, m), 0.0)
        // No pressure to read: a firm, ordinary hand.
        assertEquals(Graphite.PRESSURE_OFF, Graphite.pressureAt(1f, base, false, m), 0.0)
        assertEquals(Graphite.PRESSURE_OFF, Graphite.pressureAt(1f, base, true, 1.0), 0.0)
    }

    @Test fun pressingHarderGrowsTheDarkCore() {
        assertEquals(0.0, Graphite.coreFraction(0.0), 0.0)
        assertEquals(0.0, Graphite.coreFraction(Graphite.CORE_FROM), 0.0)
        assertEquals(Graphite.CORE_MAX, Graphite.coreFraction(Graphite.CORE_TO), 1e-12)
        assertEquals(Graphite.CORE_MAX, Graphite.coreFraction(1.0), 1e-12)
        var last = -1.0
        for (i in 0..100) {
            val c = Graphite.coreFraction(i / 100.0)
            assertTrue(c >= last)
            last = c
        }
        // The core never reaches the edge, so the pale rim of the outer pass always shows.
        assertTrue(Graphite.CORE_MAX < 1.0)
    }

    @Test fun darknessRisesWithPressure() {
        // Covered by both passes at grain g: outer a1·g, then the core composited over it.
        fun tone(p: Double): Double {
            val inCore = Graphite.coreFraction(p) > 0.0
            val a1 = Graphite.OUTER_ALPHA * Graphite.meanGrain
            if (!inCore) return a1
            val a2 = Graphite.CORE_ALPHA * Graphite.meanGrain
            return a1 + a2 * (1 - a1)
        }
        assertTrue(tone(1.0) > tone(0.1))
        // Translucent even at its darkest, so crossing strokes still build up.
        assertTrue(tone(1.0) < 0.9)
    }

    @Test fun coreRadiiFillTheBufferGivenAndSayWhetherThereIsACore() {
        val base = 2.0
        val m = 0.45
        val light = floatArrayOf(0.45f, 0.46f, 0.47f)
        val into = FloatArray(8) { -1f }
        assertFalse(Graphite.coreRadii(light, 3, base, true, m, into))
        assertEquals(0f, into[0], 0f)
        assertEquals(-1f, into[3], 0f) // past count is left alone
        val heavy = floatArrayOf(0.5f, 1.0f, 0f)
        assertTrue(Graphite.coreRadii(heavy, 3, base, true, m, into))
        assertEquals((1.0 * Graphite.CORE_MAX).toFloat(), into[1], 1e-6f)
        assertEquals(0f, into[2], 0f)
    }

    @Test fun aScaledRibbonKeepsTheCentrelineAndDrawsTheRailsIn() {
        val g = StrokeGeometry(
            floatArrayOf(0f, 0f, 10f, 0f),
            floatArrayOf(2f, 2f),
            floatArrayOf(0f, 2f, 10f, 2f),
            floatArrayOf(0f, -2f, 10f, -2f),
        )
        val s = ScaledRibbon(g, floatArrayOf(1f, 0.5f))
        assertEquals(2, s.pointCount)
        assertTrue(s.hasRails)
        assertEquals(10.0, s.cx(1), 0.0)
        assertEquals(1.0, s.hw(0), 0.0)
        assertEquals(1.0, s.leftY(0), 1e-9)
        assertEquals(-1.0, s.rightY(0), 1e-9)
        assertEquals(0.5, s.leftY(1), 1e-9)
        assertEquals(10.0, s.leftX(1), 1e-9)
    }
}
