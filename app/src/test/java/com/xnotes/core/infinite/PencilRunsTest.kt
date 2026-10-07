package com.xnotes.core.infinite

import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The infinite canvas publishes a wet pencil the way it publishes the highlighter: settled runs
 * once each, only the tail per move. Each run is two parts, an outer and a pressed core, and the
 * scene stencils every outer and covers them once through the grain, then every core. What reaches
 * the screen is each pass's union of run triangles, so that union has to be the mask the stroke
 * meshed whole makes for that pass, or the pencil would change as it is drawn.
 */
class PencilRunsTest {

    /** A hard zig-zag with the pressure swinging from a light touch to a firm one and back. */
    private fun zigZag(i: Int): Sample {
        val x = 40.0 + i * 3.0
        val phase = (i / 9) % 2
        val within = (i % 9) / 9.0
        val y = 200.0 + (if (phase == 0) within else 1.0 - within) * 160.0 + sin(i * 0.7) * 2.0
        return Sample(x, y, 0.5 + 0.45 * sin(i * 0.012), i * 4.0)
    }

    /** What the editor's run path publishes after every sample: the runs, then the tail. */
    private class Published(val runs: List<List<MeshPart>>, val tail: List<MeshPart>, val worstTailVertices: Int)

    private fun publishAsTheEditorDoes(count: Int): Pair<Stroke, Published> {
        val stroke = Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL))
        stroke.finished = false
        val runs = ArrayList<List<MeshPart>>()
        var meshed = 0
        var tail: List<MeshPart> = emptyList()
        var worstTail = 0
        for (i in 0 until count) {
            stroke.addSample(zigZag(i))
            val ribbon = stroke.wetRibbon!!
            val settled = ribbon.settledCount
            if (settled - meshed >= RUN_POINTS) {
                val from = (meshed - 1).coerceAtLeast(0)
                val run = ItemMesher.meshGraphiteRun(stroke, ribbon, from, settled - from)
                if (run.isNotEmpty()) runs += run
                meshed = settled
            }
            val tailFrom = (meshed - 1).coerceAtLeast(0)
            tail = ItemMesher.meshGraphiteRun(stroke, ribbon, tailFrom, ribbon.pointCount - tailFrom)
            worstTail = maxOf(worstTail, tail.sumOf { it.mesh.vertexCount })
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

    private val outerAlpha = (255 * Graphite.OUTER_ALPHA).toInt()
    private val coreAlpha = (255 * Graphite.CORE_ALPHA).toInt()

    /** Every published part of one pass, runs then tail. */
    private fun pass(published: Published, alpha: Int): List<MeshPart> =
        (published.runs.flatten() + published.tail).filter { it.color.a == alpha }

    @Test fun eachPassesRunsHoldEveryTriangleOfThatPassMeshedWhole() {
        val (stroke, published) = publishAsTheEditorDoes(600)
        val whole = ItemMesher.mesh(stroke)!!.parts
        assertEquals("a pressed pencil meshes an outer and a core", 2, whole.size)
        for (part in whole) {
            val union = HashSet<List<Double>>()
            for (run in pass(published, part.color.a)) union += triangles(run.mesh)
            val missing = triangles(part.mesh).filterNot { it in union }
            assertTrue("${missing.size} triangles of the whole ${part.color.a} pass are in no run", missing.isEmpty())
        }
        assertTrue("a 600-point stroke should have settled into several runs", published.runs.size >= 4)
    }

    @Test fun whatTheRunsAddIsOnlyDiscsAlreadyInsideTheirPass() {
        val (stroke, published) = publishAsTheEditorDoes(600)
        val ribbon = stroke.wetRibbon!!
        val n = ribbon.pointCount
        val widths = FloatArray(n) { ribbon.hw(it).toFloat() }
        val core = FloatArray(n)
        val c = stroke.config
        Graphite.coreRadii(widths, n, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, core)
        val passes = listOf(
            outerAlpha to { i: Int -> ribbon.hw(i) },
            coreAlpha to { i: Int -> core[i].toDouble() },
        )
        for ((alpha, radius) in passes) {
            val whole = HashSet(triangles(ItemMesher.mesh(stroke)!!.parts.single { it.color.a == alpha }.mesh))
            for (run in pass(published, alpha)) {
                for (tri in triangles(run.mesh).filterNot { it in whole }) {
                    // A slice of the brush disc at one point of this pass: every corner sits within
                    // that point's radius of it, so the stencil masks the pass's own silhouette.
                    val inside = (0 until n).any { i ->
                        val h = radius(i) + 1e-6
                        (0 until 3).all { k -> hypot(tri[2 * k] - ribbon.cx(i), tri[2 * k + 1] - ribbon.cy(i)) <= h }
                    }
                    assertTrue("a triangle the runs added is outside every disc of its pass: $tri", inside)
                }
            }
        }
    }

    @Test fun everyRunIsAnOuterThenACoreThroughTheGrain() {
        val (stroke, published) = publishAsTheEditorDoes(600)
        val whole = ItemMesher.mesh(stroke)!!.parts
        for (run in published.runs + listOf(published.tail)) {
            assertTrue(run.size in 1..2)
            assertTrue(run.all { it.pass == InkPass.GRAPHITE })
            // The outer always comes first, so the scene's covers go down outer, then core.
            assertEquals(whole[0].color, run[0].color)
            if (run.size == 2) assertEquals(whole[1].color, run[1].color)
        }
        // The light stretches of the zig-zag have no core, and their runs say so.
        assertTrue("every run had a core", published.runs.any { it.size == 1 })
        assertTrue("no run had a core", published.runs.any { it.size == 2 })
    }

    @Test fun aMoveCostsTheTailNotTheStroke() {
        val (_, short) = publishAsTheEditorDoes(300)
        val (stroke, long) = publishAsTheEditorDoes(1500)
        val wholeVertices = ItemMesher.mesh(stroke)!!.parts.sumOf { it.mesh.vertexCount }
        // The tail never holds more than a run's worth of points plus the few still moving, so its
        // worst case is the same for a short stroke and a long one, and a fraction of the whole.
        assertTrue("tail ${long.worstTailVertices} vs ${short.worstTailVertices}", long.worstTailVertices <= short.worstTailVertices * 3 / 2 + 64)
        assertTrue("tail ${long.worstTailVertices} vs whole $wholeVertices", long.worstTailVertices * 5 < wholeVertices)
    }

    @Test fun aRunDeepInAStrokeMeshesWhatTheWholeDoesThere() {
        // The core radii come off the run's own points alone, so a run deep in a long stroke lays
        // the same body triangles the whole stroke lays over those points; only its end discs are
        // its own.
        val (stroke, _) = publishAsTheEditorDoes(900)
        val ribbon = stroke.wetRibbon!!
        val from = 500
        val last = from + 79
        val run = ItemMesher.meshGraphiteRun(stroke, ribbon, from, 80)
        val whole = ItemMesher.meshGraphiteRun(stroke, ribbon, 0, ribbon.pointCount)
        assertEquals(whole.size, run.size)
        val n = ribbon.pointCount
        val widths = FloatArray(n) { ribbon.hw(it).toFloat() }
        val core = FloatArray(n)
        val c = stroke.config
        Graphite.coreRadii(widths, n, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, core)
        for (k in run.indices) {
            val all = HashSet(triangles(whole[k].mesh))
            val radius = { i: Int -> if (k == 0) ribbon.hw(i) else core[i].toDouble() }
            for (tri in triangles(run[k].mesh).filterNot { it in all }) {
                val inEndDisc = listOf(from, last).any { i ->
                    (0 until 3).all { j -> hypot(tri[2 * j] - ribbon.cx(i), tri[2 * j + 1] - ribbon.cy(i)) <= radius(i) + 1e-6 }
                }
                assertTrue("pass $k of the run laid a triangle the whole does not, outside its end discs: $tri", inEndDisc)
            }
        }
    }

    companion object {
        /** The editor's WET_RUN_POINTS. */
        const val RUN_POINTS = 96
    }
}
