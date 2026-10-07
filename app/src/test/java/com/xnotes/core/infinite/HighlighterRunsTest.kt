package com.xnotes.core.infinite

import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The infinite canvas publishes a wet highlighter the way it publishes the pen: settled runs once
 * each, and only the tail per move. The scene stencils every run and covers them once, so what
 * reaches the screen is the union of the runs' triangles under one multiply. That union has to be
 * the mask the stroke meshed whole would have made, or the highlight would change as it is drawn.
 */
class HighlighterRunsTest {

    /** A hard zig-zag, the stroke that lagged: a disc at nearly every tip. */
    private fun zigZag(i: Int): Sample {
        val x = 40.0 + i * 3.0
        val phase = (i / 9) % 2
        val within = (i % 9) / 9.0
        val y = 200.0 + (if (phase == 0) within else 1.0 - within) * 160.0 + sin(i * 0.7) * 2.0
        return Sample(x, y, 0.6, i * 4.0)
    }

    private fun live(count: Int, tool: Tool = Tool.HIGHLIGHTER, inverse: Boolean = false): Stroke {
        var config = ToolDefaults.configFor(tool)
        if (inverse) config = config.copy(highlighterInverse = true)
        val s = Stroke(tool, config)
        s.finished = false
        for (i in 0 until count) s.addSample(zigZag(i))
        return s
    }

    /** What the editor's run path publishes for [stroke] after every sample: the runs, then the tail. */
    private class Published(val runs: List<MeshPart>, val tail: MeshPart?, val worstTailVertices: Int)

    private fun publishAsTheEditorDoes(count: Int, inverse: Boolean = false): Pair<Stroke, Published> {
        var config = ToolDefaults.configFor(Tool.HIGHLIGHTER)
        if (inverse) config = config.copy(highlighterInverse = true)
        val stroke = Stroke(Tool.HIGHLIGHTER, config)
        stroke.finished = false
        val runs = ArrayList<MeshPart>()
        var meshed = 0
        var arc = 0.0
        var tail: MeshPart? = null
        var worstTail = 0
        for (i in 0 until count) {
            stroke.addSample(zigZag(i))
            val ribbon = stroke.wetRibbon!!
            val settled = ribbon.settledCount
            if (settled - meshed >= RUN_POINTS) {
                val from = (meshed - 1).coerceAtLeast(0)
                ItemMesher.meshRun(stroke, ribbon, from, settled - from, arc)?.let { runs += it }
                for (k in from + 1 until settled) arc += hypot(ribbon.cx(k) - ribbon.cx(k - 1), ribbon.cy(k) - ribbon.cy(k - 1))
                meshed = settled
            }
            val tailFrom = (meshed - 1).coerceAtLeast(0)
            tail = ItemMesher.meshRun(stroke, ribbon, tailFrom, ribbon.pointCount - tailFrom, arc)
            worstTail = maxOf(worstTail, tail?.mesh?.vertexCount ?: 0)
        }
        return stroke to Published(runs, tail, worstTail)
    }

    /** Every triangle of [mesh] as its three corners, order-free, so two meshes can be compared. */
    private fun triangles(mesh: MeshData): List<List<Double>> {
        val out = ArrayList<List<Double>>(mesh.triangleCount)
        val p = mesh.positions
        val idx = mesh.indices
        var t = 0
        while (t + 2 < idx.size) {
            val corners = (0 until 3).map { listOf(p[2 * idx[t + it]], p[2 * idx[t + it] + 1]) }
                .sortedWith(compareBy({ it[0] }, { it[1] }))
            out += corners.flatten()
            t += 3
        }
        return out
    }

    @Test fun theRunsAndTheTailHoldEveryTriangleOfTheWholeStroke() {
        val (stroke, published) = publishAsTheEditorDoes(600)
        val whole = ItemMesher.mesh(stroke)!!.parts.single()
        val union = HashSet<List<Double>>()
        for (run in published.runs) union += triangles(run.mesh)
        published.tail?.let { union += triangles(it.mesh) }
        val missing = triangles(whole.mesh).filterNot { it in union }
        assertTrue("${missing.size} triangles of the whole stroke are in no run", missing.isEmpty())
        assertTrue("a 600-point stroke should have settled into several runs", published.runs.size >= 4)
    }

    @Test fun whatTheRunsAddIsOnlyDiscsAlreadyInsideTheStroke() {
        val (stroke, published) = publishAsTheEditorDoes(600)
        val whole = HashSet(triangles(ItemMesher.mesh(stroke)!!.parts.single().mesh))
        val ribbon = stroke.wetRibbon!!
        val extra = ArrayList<List<Double>>()
        for (run in published.runs) extra += triangles(run.mesh).filterNot { it in whole }
        published.tail?.let { t -> extra += triangles(t.mesh).filterNot { it in whole } }
        // Each extra triangle is a slice of the brush disc at one ribbon point: every corner sits
        // within that point's half-width of it. The swept disc is the stroke's own silhouette, so
        // the extra cover changes nothing about the shape the stencil masks.
        for (tri in extra) {
            val inside = (0 until ribbon.pointCount).any { i ->
                val h = ribbon.hw(i) + 1e-9
                (0 until 3).all { c -> hypot(tri[2 * c] - ribbon.cx(i), tri[2 * c + 1] - ribbon.cy(i)) <= h }
            }
            assertTrue("a triangle the runs added is outside every brush disc: $tri", inside)
        }
    }

    @Test fun everyRunIsTheWholeStrokesPassAndColour() {
        for (inverse in listOf(false, true)) {
            val (stroke, published) = publishAsTheEditorDoes(400, inverse)
            val whole = ItemMesher.mesh(stroke)!!.parts.single()
            assertEquals(if (inverse) InkPass.SCREEN else InkPass.MULTIPLY, whole.pass)
            for (run in published.runs + listOfNotNull(published.tail)) {
                assertEquals(whole.pass, run.pass)
                assertEquals(whole.color, run.color)
            }
        }
    }

    @Test fun aMoveCostsTheTailNotTheStroke() {
        val (_, short) = publishAsTheEditorDoes(300)
        val (stroke, long) = publishAsTheEditorDoes(1500)
        val wholeVertices = ItemMesher.mesh(stroke)!!.parts.single().mesh.vertexCount
        // The tail never holds more than a run's worth of points plus the few still moving, so its
        // worst case is the same for a short stroke and a long one, and a fraction of the whole.
        assertTrue("tail ${long.worstTailVertices} vs ${short.worstTailVertices}", long.worstTailVertices <= short.worstTailVertices * 3 / 2 + 64)
        assertTrue("tail ${long.worstTailVertices} vs whole $wholeVertices", long.worstTailVertices * 5 < wholeVertices)
    }

    @Test fun theStrokeStillHasARibbonToRunOn() {
        // The run path needs the live ribbon; a highlighter that lost it would fall back to the
        // whole-stroke mesh every move.
        for (inverse in listOf(false, true)) {
            val s = live(200, inverse = inverse)
            val ribbon = s.wetRibbon
            assertTrue("the highlighter has no live ribbon", ribbon != null)
            assertTrue("nothing of a 200-point highlight settled", ribbon!!.settledCount > 100)
        }
    }

    companion object {
        /** The editor's WET_RUN_POINTS. */
        const val RUN_POINTS = 96
    }
}
