package com.xnotes.core.stroke

import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * What the pen leaves is what the writer saw. At pen-up a stroke is reduced ([StrokeSimplify]) and
 * rebuilt finished; anything that rebuild changes shows as the ink adjusting itself after it was
 * written. This holds the committed ribbon against the live one, point for point along the path.
 */
class PenUpStabilityTest {

    /** Handwriting-like loops at about a stylus's spacing (~1 px), pressure swelling and easing off
     *  at the end the way a pen leaves the glass. */
    private fun handwriting(count: Int = 420): List<Sample> {
        val out = ArrayList<Sample>(count)
        for (i in 0 until count) {
            val u = i * 0.03
            val x = 40.0 + u * 22.0 + sin(u * 4.1) * 9.0
            val y = 80.0 + cos(u * 4.1) * 16.0 + sin(u * 1.3) * 5.0
            val lift = ((count - 1 - i) / 25.0).coerceAtMost(1.0) // the last 25 samples lighten
            val land = (i / 12.0).coerceAtMost(1.0)
            val p = (0.35 + 0.35 * (0.5 + 0.5 * sin(u * 2.7))) * lift * land
            out.add(Sample(x, y, p.coerceAtLeast(0.02), i * 4.0))
        }
        return out
    }

    private fun geometry(tool: Tool, samples: List<Sample>, finished: Boolean, rev: Int? = null): StrokeGeometry {
        val c = ToolDefaults.configFor(tool).let { if (rev == null) it else it.copy(inkRev = rev) }
        return StrokeEngine.build(
            samples, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, c.directionStrength,
            c.speedStrength, c.taperEnabled, c.taperMinFactor, 1.0,
            smooth = true,
            holdEnds = tool == Tool.PEN || tool == Tool.BALLPOINT || tool == Tool.HIGHLIGHTER,
            finished = finished, smoothScale = 1.0, inkRev = c.inkRev,
            taperLen = if (c.taperEnabled) StrokeEngine.BRUSH_TAPER_LEN else 0.0,
        )
    }

    /** The largest outline difference (centre offset plus half-width change) between the ribbon
     *  under the pen and the one committed, reading the committed one at each live point's nearest
     *  place on it. */
    private fun maxDeviation(live: StrokeGeometry, done: StrokeGeometry): Double {
        var worst = 0.0
        for (i in 0 until live.pointCount) {
            val px = live.cx(i)
            val py = live.cy(i)
            var best = Double.MAX_VALUE
            var bestHw = 0.0
            for (j in 0 until done.pointCount - 1) {
                val ax = done.cx(j)
                val ay = done.cy(j)
                val dx = done.cx(j + 1) - ax
                val dy = done.cy(j + 1) - ay
                val l2 = dx * dx + dy * dy
                val t = if (l2 < 1e-12) 0.0 else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0.0, 1.0)
                val d = hypot(px - (ax + t * dx), py - (ay + t * dy))
                if (d < best) {
                    best = d
                    bestHw = done.hw(j) + (done.hw(j + 1) - done.hw(j)) * t
                }
            }
            worst = max(worst, best + abs(live.hw(i) - bestHw))
        }
        return worst
    }

    private fun penUpChange(tool: Tool, rev: Int? = null): Double {
        val samples = handwriting()
        val live = geometry(tool, samples, finished = false, rev = rev)
        val finished = geometry(tool, samples, finished = true, rev = rev)
        val kept = StrokeSimplify.simplify(
            samples, finished.halfWidths, 0.2, 1.0, ToolDefaults.configFor(tool).directionStrength,
        )
        return maxDeviation(live, geometry(tool, kept, finished = true, rev = rev))
    }

    @Test fun theBrushLeavesWhatItDrew() {
        val brush = penUpChange(Tool.TAPER)
        val fountain = penUpChange(Tool.PEN)
        println("pen-up change (px): brush=$brush brushRev2=${penUpChange(Tool.TAPER, StrokeEngine.INK_REV_LIVE_TIP)} fountain=$fountain ballpoint=${penUpChange(Tool.BALLPOINT)}")
        assertTrue("the brush moved $brush px at pen-up", brush <= max(fountain, 0.25))
    }

    @Test fun aFinishedBrushStrokeIsTheLiveOne() {
        val samples = handwriting()
        val live = geometry(Tool.TAPER, samples, finished = false)
        val done = geometry(Tool.TAPER, samples, finished = true)
        for (i in 0 until live.pointCount) assertEquals("hw[$i]", live.hw(i), done.hw(i), 0.0)
    }

    /** A straight run at a steady pressure, [msPerSample] apart: the speed decides the tail alone. */
    private fun steady(msPerSample: Double, count: Int = 160): List<Sample> =
        List(count) { Sample(20.0 + it * 1.5, 50.0, 0.5, it * msPerSample) }

    @Test fun theBrushEndTapersWhileWriting() {
        // Under the pen, not at the lift: the newest point is already the tip.
        val live = geometry(Tool.TAPER, steady(4.0), finished = false)
        val body = live.hw(live.pointCount / 2)
        assertTrue("the tip is ${live.hw(live.pointCount - 1)} of a $body body", live.hw(live.pointCount - 1) < body * 0.35)
    }

    @Test fun aFlickLeavesALongerPointThanASlowFinish() {
        fun tailLength(g: StrokeGeometry): Double {
            val body = g.hw(g.pointCount / 2)
            var i = g.pointCount - 1
            while (i > 0 && g.hw(i) < body * 0.98) i--
            return g.cx(g.pointCount - 1) - g.cx(i)
        }
        val fast = tailLength(geometry(Tool.TAPER, steady(1.0), finished = true))
        val slow = tailLength(geometry(Tool.TAPER, steady(30.0), finished = true))
        println("brush tail: fast $fast px, slow $slow px")
        assertTrue("fast $fast vs slow $slow", fast > slow * 2.0)
    }

    @Test fun revisionThreeBrushInkHasNoTail() {
        // A brush stroke saved by 1.3.1 still ends the way it did there.
        val g = geometry(Tool.TAPER, steady(4.0), finished = true, rev = StrokeEngine.INK_REV_LIVE_END)
        assertEquals(g.hw(g.pointCount / 2), g.hw(g.pointCount - 1), 1e-6)
    }

    @Test fun revisionTwoBrushInkKeepsItsTail() {
        // A brush stroke saved by 1.3.0 still ends the way it did there.
        val samples = handwriting()
        val live = geometry(Tool.TAPER, samples, finished = false, rev = StrokeEngine.INK_REV_LIVE_TIP)
        val done = geometry(Tool.TAPER, samples, finished = true, rev = StrokeEngine.INK_REV_LIVE_TIP)
        val last = live.pointCount - 1
        assertTrue(done.hw(last) < live.hw(last) * 0.5)
    }
}
