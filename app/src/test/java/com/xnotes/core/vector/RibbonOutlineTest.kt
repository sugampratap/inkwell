package com.xnotes.core.vector

import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.StrokeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * [RibbonOutline] is what a PDF export writes a stroke as, so it has to cover exactly the ink the
 * screen draws while costing a fraction of it. Coverage is checked by sampling points: everything
 * the swept discs cover must be inside some subpath, and nothing far outside the ribbon may be.
 */
class RibbonOutlineTest {

    private fun handwriting(n: Int): List<Sample> = (0 until n).map { i ->
        val u = i * 0.012
        Sample(40.0 + u * 30.0 + cos(u * 9.0) * 12.0, 80.0 + sin(u * 9.0) * 18.0, 0.4 + 0.3 * sin(u * 3.0), i * 4.0)
    }

    private fun geometry(samples: List<Sample>) =
        StrokeEngine.build(samples, 4.0, true, 0.35, 0.0, holdEnds = true)

    private fun insidePolygon(p: DoubleArray, x: Double, y: Double): Int {
        // Winding number, nonzero rule.
        var w = 0
        val n = p.size / 2
        for (i in 0 until n) {
            val x0 = p[2 * i]; val y0 = p[2 * i + 1]
            val x1 = p[2 * ((i + 1) % n)]; val y1 = p[2 * ((i + 1) % n) + 1]
            val cross = (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0)
            if (y0 <= y) { if (y1 > y && cross > 0) w++ } else if (y1 <= y && cross < 0) w--
        }
        return w
    }

    private fun covered(o: RibbonOutline.Outline, x: Double, y: Double): Boolean {
        var w = 0
        for (p in o.polygons) w += insidePolygon(p, x, y)
        val d = o.discs
        for (i in 0 until d.size / 3) if (hypot(x - d[3 * i], y - d[3 * i + 1]) <= d[3 * i + 2]) w++
        return w != 0
    }

    @Test fun everySubpathWindsTheSameWay() {
        val g = geometry(handwriting(1200))
        val o = RibbonOutline.build(g.centerline, g.halfWidths, 0, g.pointCount, 0.5, 0.05)
        for (p in o.polygons) assertTrue(RibbonOutline.signedArea(p) > 0.0)
    }

    @Test fun coversTheInkTheScreenDrawsAndLittleElse() {
        val g = geometry(handwriting(1200))
        val o = RibbonOutline.build(g.centerline, g.halfWidths, 0, g.pointCount, 0.5, 0.05)
        var misses = 0
        var checked = 0
        for (i in 0 until g.pointCount) {
            val cx = g.centerline[2 * i].toDouble()
            val cy = g.centerline[2 * i + 1].toDouble()
            val r = g.halfWidths[i].toDouble()
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                // Well inside the swept disc: must be ink.
                checked++
                if (!covered(o, cx + cos(a) * r * 0.8, cy + sin(a) * r * 0.8)) misses++
            }
        }
        assertEquals("points of ink left uncovered out of $checked", 0, misses)
        // Far outside every disc: must not be ink.
        var stray = 0
        for (gx in 0 until 260) for (gy in 0 until 80) {
            val x = gx * 2.0; val y = 40.0 + gy
            var near = Double.MAX_VALUE
            for (i in 0 until g.pointCount) {
                near = minOf(near, hypot(x - g.centerline[2 * i], y - g.centerline[2 * i + 1]) - g.halfWidths[i])
            }
            if (near > 1.0 && covered(o, x, y)) stray++
        }
        assertEquals(0, stray)
    }

    @Test fun isFarSmallerThanADiscAndQuadPerSample() {
        val g = geometry(handwriting(2000))
        val o = RibbonOutline.build(g.centerline, g.halfWidths, 0, g.pointCount, 0.5, 0.05)
        var points = 0
        for (p in o.polygons) points += p.size / 2
        points += (o.discs.size / 3) * 13
        // The screen's form: a 4-point quad and a 13-point circle per sample.
        assertTrue("outline used $points points for ${g.pointCount} samples", points < g.pointCount * 17 / 5)
    }

    @Test fun aTapIsADot() {
        val g = geometry(listOf(Sample(10.0, 10.0, 0.5)))
        val o = RibbonOutline.build(g.centerline, g.halfWidths, 0, g.pointCount, 0.5, 0.05)
        assertEquals(0, o.polygons.size)
        assertEquals(3, o.discs.size)
    }
}
