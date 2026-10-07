package com.xnotes.canvas

import com.xnotes.core.infinite.InkPass
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the paged front buffer is handed per move ([PadFeed]): a settled run now and then, and the
 * tail. For the pencil that is two parts each, and what a move meshes has to stay the size of a run
 * and a tail however long the stroke has grown, which is what keeps a long pencil stroke as quick
 * under the nib as a short one.
 */
class PadFeedTest {

    /** Points per run on the front buffer, as [FrontInk] uses. */
    private val runPoints = 8

    private fun sampleAt(i: Int): Sample {
        val u = i * 0.07
        return Sample(
            60.0 + u * 30.0 + sin(u * 2.3) * 14.0,
            300.0 + cos(u * 1.3) * 120.0,
            0.5 + 0.45 * sin(i * 0.031),
            i * 5.0,
        )
    }

    private class Moves(val worstVertices: Int, val lateWorstVertices: Int, val runCounts: List<Int>, val settled: Int)

    /** Draw [count] samples of [tool], handing the feed one move per sample as the canvas does. */
    private fun feed(tool: Tool, count: Int): Moves {
        val stroke = Stroke(tool, ToolDefaults.configFor(tool))
        stroke.finished = false
        val feed = PadFeed()
        var worst = 0
        var lateWorst = 0
        val runCounts = ArrayList<Int>()
        var last = 0
        for (i in 0 until count) {
            stroke.addSample(sampleAt(i))
            val ribbon = stroke.wetRibbon!!
            val run = feed.settledRun(stroke, ribbon, runPoints)
            if (run.isNotEmpty() || feed.meshed != last) {
                runCounts += feed.meshed - (last - 1).coerceAtLeast(0)
                last = feed.meshed
            }
            val tail = feed.tail(stroke, ribbon)
            if (tool == Tool.PENCIL) {
                assertTrue("a pencil move is graphite", (run + tail).all { it.pass == InkPass.GRAPHITE })
            }
            val vertices = (run + tail).sumOf { it.mesh.vertexCount }
            worst = maxOf(worst, vertices)
            if (i >= count - 200) lateWorst = maxOf(lateWorst, vertices)
        }
        return Moves(worst, lateWorst, runCounts, feed.meshed)
    }

    @Test fun aPencilMoveMeshesTheSameFewPointsAt300And1500() {
        val short = feed(Tool.PENCIL, 300)
        val long = feed(Tool.PENCIL, 1500)
        // The moves at the end of the long stroke cost what the short one's moves did.
        assertTrue("late moves of 1500: ${long.lateWorstVertices} vs 300: ${short.worstVertices}",
            long.lateWorstVertices <= short.worstVertices * 1.25 + 16)
        assertTrue("a move meshed ${long.worstVertices} vertices", long.worstVertices < 2000)
    }

    @Test fun everySettledPointIsHandedOverOnce() {
        for (tool in listOf(Tool.PENCIL, Tool.PEN)) {
            val moves = feed(tool, 900)
            // Each run repeats the point before it as its overlap, and nothing else is laid twice.
            assertEquals(tool.name, moves.settled + moves.runCounts.size - 1, moves.runCounts.sum())
            assertTrue(tool.name, moves.runCounts.all { it <= runPoints * 3 })
        }
    }
}
