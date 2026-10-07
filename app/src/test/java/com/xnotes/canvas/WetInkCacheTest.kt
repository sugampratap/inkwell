package com.xnotes.canvas

import com.xnotes.core.FakeRenderer
import com.xnotes.core.geometry.Rect
import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.pal.BlendMode
import com.xnotes.core.pal.RasterSurface
import com.xnotes.core.pal.Renderer
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * What the raster half of the wet cache has to get right: it must stop redrawing ink it has
 * already baked, it must never leave a gap where the baked run meets the live one, and it must
 * hand back the strokes it cannot paint in two pieces so they keep the plain redraw.
 */
class WetInkCacheTest {

    private val cap = 8_000_000L

    private val factory = FakeSurfaceFactory()
    private val cache = WetInkCache(factory)

    private fun sampleAt(i: Int): Sample {
        val u = i * 0.09
        return Sample(
            60.0 + u * 24.0 + sin(u * 2.9) * 8.0,
            80.0 + cos(u * 1.6) * 30.0,
            0.4 + 0.4 * (0.5 + 0.5 * sin(u * 3.1)),
            i * 6.0,
        )
    }

    private fun liveStroke(tool: Tool, count: Int, neon: Boolean = false, inkRev: Int? = null): Stroke {
        var config = ToolDefaults.configFor(tool)
        if (neon) config = config.copy(neon = true)
        if (inkRev != null) config = config.copy(inkRev = inkRev)
        val s = Stroke(tool, config)
        s.finished = false
        for (i in 0 until count) s.addSample(sampleAt(i))
        return s
    }

    /** Draw one frame and hand back what the screen renderer saw. */
    private fun frame(stroke: Stroke, res: Double = 1.0): Pair<Boolean, FakeRenderer> {
        val r = FakeRenderer()
        return cache.paint(r, stroke, res, cap) to r
    }

    /**
     * Ribbon runs painted into the tiles, each once however many tiles it landed on, in the order
     * they were baked.
     */
    private fun bakedRuns(): List<Pair<Int, Int>> = runsIn(factory.created.flatMap { it.painter.ribbonRuns })

    /** [runs] as distinct runs in stroke order; a run is drawn into every tile it reaches. */
    private fun runsIn(runs: List<Pair<Int, Int>>): List<Pair<Int, Int>> = runs.distinct().sortedBy { it.first }

    @Test fun aShortStrokeIsNotWorthASurface() {
        val (took, _) = frame(liveStroke(Tool.PEN, 12))
        assertFalse("a 12-point stroke should just redraw", took)
        assertTrue("it allocated a surface anyway", factory.created.isEmpty())
    }

    @Test fun aLongStrokeRedrawsOnlyItsMovingTail() {
        val stroke = liveStroke(Tool.PEN, 400)
        val (took, r) = frame(stroke)
        assertTrue("the cache turned a 400-point stroke down", took)
        assertTrue("it should blit its tiles", r.rasterDests.isNotEmpty())
        assertEquals("each tile once", r.rasterDests.size, r.rasterDests.distinct().size)
        assertEquals(factory.created.size, r.rasterDests.size)
        assertEquals(1, r.ribbonRuns.size)
        val (from, count) = r.ribbonRuns[0]
        assertTrue("the live run started at the head", from > 0)
        assertTrue("the live run is not a tail: $count points", count <= 8)
        assertEquals("the live run must reach the nib", stroke.wetRibbon!!.pointCount, from + count)
    }

    @Test fun theTwoRunsOverlapByAPointSoNoGapCanOpen() {
        val stroke = liveStroke(Tool.PEN, 300)
        val (_, screen) = frame(stroke)
        val liveFrom = screen.ribbonRuns[0].first
        // Whatever the baked run ended at, the live one starts one point behind it, so the segment
        // between them is drawn and they share a whole brush disc.
        val last = bakedRuns().last()
        assertEquals(last.first + last.second - 1, liveFrom)
    }

    @Test fun eachPointIsBakedOnceHoweverManyFramesItSurvives() {
        val stroke = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN))
        stroke.finished = false
        for (i in 0 until 400) {
            stroke.addSample(sampleAt(i))
            repeat(3) { frame(stroke) } // several frames per sample, as a slow hand produces
        }
        // Across every tile, each run once however many tiles it landed on.
        val runs = bakedRuns()
        val settled = stroke.wetRibbon!!.settledCount
        // Every settled point is painted once, plus the one point each run repeats as its overlap.
        assertEquals(settled + runs.size - 1, runs.sumOf { it.second })
        assertEquals("the bakes must cover the whole settled run", settled, runs.last().let { it.first + it.second })
        assertTrue("almost nothing settled", settled > 300)
    }

    @Test fun theDashPatternCarriesAcrossTheSeam() {
        val stroke = liveStroke(Tool.DASHED, 300)
        val (took, screen) = frame(stroke)
        assertTrue(took)
        val live = screen.dashRuns.single()
        val ribbon = stroke.wetRibbon!!
        // The live run must start the pattern exactly where the baked run's arc left it.
        var arc = 0.0
        for (k in 1..live.first) arc += hypot(ribbon.cx(k) - ribbon.cx(k - 1), ribbon.cy(k) - ribbon.cy(k - 1))
        assertEquals(arc, live.third, 1e-9)
    }

    @Test fun neonKeepsThePlainRedraw() {
        assertFalse("neon must not be baked in pieces", frame(liveStroke(Tool.PEN, 300, neon = true)).first)
        assertTrue("it should not have taken a surface", factory.created.isEmpty())
    }

    /** A renderer that also records the layers opened, the clips, and the colours ribbons were filled in. */
    private class LayerRecorder(val inner: FakeRenderer = FakeRenderer()) : Renderer by inner {
        val layers = mutableListOf<Pair<Double, BlendMode>>()
        val layerBounds = mutableListOf<Rect>()
        val blended = mutableListOf<Pair<Double, BlendMode>>()
        val ribbonColors = mutableListOf<Rgba>()
        val order = mutableListOf<String>()
        val clips = mutableListOf<Rect>()

        override fun saveLayerBlended(bounds: Rect, alpha: Double, blend: BlendMode) {
            layers += alpha to blend
            layerBounds += bounds
            order += "layer"
        }

        override fun save() {
            order += "save"
        }

        override fun restore() {
            order += "restore"
        }

        override fun clipRect(rect: Rect) {
            clips += rect
            order += "clip"
        }

        override fun clipOutRect(rect: Rect): Boolean {
            clips += rect
            order += "clipOut"
            return true
        }

        override fun drawRaster(raster: RasterSurface, dest: Rect, src: Rect?) {
            order += "raster"
            inner.drawRaster(raster, dest, src)
        }

        override fun drawRasterBlended(raster: RasterSurface, dest: Rect, alpha: Double, blend: BlendMode, src: Rect?) {
            blended += alpha to blend
            order += "blended"
            inner.drawRaster(raster, dest, src)
        }

        override fun fillDiskRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
            ribbonColors += color
            order += "ribbon"
            inner.fillDiskRibbon(centers, radii, from, count, color)
        }
    }

    @Test fun theHighlighterIsBakedSolidAndCompositedOnceInItsLayer() {
        for (inverse in listOf(false, true)) {
            val config = ToolDefaults.configFor(Tool.HIGHLIGHTER).copy(highlighterInverse = inverse)
            val stroke = Stroke(Tool.HIGHLIGHTER, config)
            stroke.finished = false
            for (i in 0 until 400) stroke.addSample(sampleAt(i))
            val r = LayerRecorder()
            assertTrue("the cache turned a 400-point highlight down", cache.paint(r, stroke, 1.0, cap))
            // Away from the tail, the baked tiles at the ink's alpha and blend; over the tail's box,
            // one layer at that alpha and blend holding the baked tiles there and the live tail:
            // together exactly the composite [Stroke.paint] makes of the whole stroke.
            val o = r.order
            assertEquals(listOf("save", "clipOut"), o.take(2))
            val back = o.indexOf("restore")
            assertTrue(o.subList(2, back).isNotEmpty() && o.subList(2, back).all { it == "blended" })
            assertEquals(
                listOf("save", "clip", "layer", "ribbon", "restore", "restore"),
                o.subList(back + 1, o.size).filter { it != "raster" },
            )
            assertTrue("the baked tiles under the tail", o.subList(back + 1, o.size).contains("raster"))
            // The box cut out is the box clipped to is the layer's, and it holds the tail.
            assertEquals(r.clips[0], r.clips[1])
            assertEquals(r.clips[0], r.layerBounds.single())
            val color = stroke.renderColor
            assertTrue("the highlighter is translucent", color.a < 255)
            assertEquals(color.a / 255.0, r.layers.single().first, 0.0)
            assertEquals(stroke.blendMode, r.layers.single().second)
            for ((a, blend) in r.blended) {
                assertEquals(color.a / 255.0, a, 0.0)
                assertEquals(stroke.blendMode, blend)
            }
            assertEquals(if (inverse) BlendMode.SCREEN else BlendMode.MULTIPLY, stroke.blendMode)
            // Solid inside the layer, both the tail and what was baked, so runs union, not darken.
            assertEquals(color.withAlpha(255), r.ribbonColors.single())
            val (from, count) = r.inner.ribbonRuns.single()
            assertTrue("the live run is not a tail: $count points", count <= 8)
            assertEquals(stroke.wetRibbon!!.pointCount, from + count)
            val ribbon = stroke.wetRibbon!!
            val box = r.layerBounds.single()
            for (i in from until from + count) {
                val h = ribbon.hw(i)
                assertTrue("the tail runs out of its box", ribbon.cx(i) - h >= box.left && ribbon.cx(i) + h <= box.right)
                assertTrue("the tail runs out of its box", ribbon.cy(i) - h >= box.top && ribbon.cy(i) + h <= box.bottom)
            }
            val baked = bakedRuns()
            assertTrue(baked.isNotEmpty())
            assertEquals("the bake must reach where the tail picks up", from + 1, baked.last().let { it.first + it.second })
            cache.clear()
        }
    }

    @Test fun translucentPenInkIsBakedUnderALayerAndTheDashedPenIsNot() {
        val faded = ToolDefaults.configFor(Tool.PEN).copy(rgba = Rgba(20, 30, 40, 128))
        val pen = Stroke(Tool.PEN, faded)
        pen.finished = false
        for (i in 0 until 300) pen.addSample(sampleAt(i))
        val r = LayerRecorder()
        assertTrue(cache.paint(r, pen, 1.0, cap))
        assertEquals(BlendMode.SRC_OVER, r.layers.single().second)

        val dashedConfig = ToolDefaults.configFor(Tool.DASHED).copy(rgba = Rgba(20, 30, 40, 128))
        val dashed = Stroke(Tool.DASHED, dashedConfig)
        dashed.finished = false
        for (i in 0 until 300) dashed.addSample(sampleAt(i))
        assertFalse("a translucent dashed pen is not layered by Stroke.paint", frame(dashed).first)
    }

    @Test fun theOldTaperPenKeepsThePlainRedraw() {
        // Revision 1 reshapes the whole stroke with every sample, so nothing of it ever settles.
        assertFalse(frame(liveStroke(Tool.TAPER, 300, inkRev = 1)).first)
        assertTrue(factory.created.isEmpty())
    }

    @Test fun theBrushBakesLikeAnyPen() {
        // Its head eases over a fixed travel and its tail waits for the lift, so it settles.
        assertTrue(frame(liveStroke(Tool.TAPER, 300)).first)
    }

    /** How many runs each tile has had painted into it so far. */
    private fun runCounts(): List<Int> = factory.created.map { it.painter.ribbonRuns.size }

    /** Runs painted into the tiles since [counts] was taken. */
    private fun runsSince(counts: List<Int>): List<Pair<Int, Int>> = runsIn(
        factory.created.flatMapIndexed { k, s -> s.painter.ribbonRuns.drop(counts.getOrElse(k) { 0 }) },
    )

    @Test fun aNewStrokeStartsOverRatherThanInheritingTheLastOnesInk() {
        frame(liveStroke(Tool.PEN, 300))
        val buffers = factory.created.size
        val before = runCounts()
        val fills = factory.created.map { it.fills }
        val (took, _) = frame(liveStroke(Tool.PEN, 300))
        assertTrue(took)
        val runs = runsSince(before)
        assertTrue("the second stroke baked nothing", runs.isNotEmpty())
        assertEquals("the second stroke must bake from its own head", 0, runs.first().first)
        assertEquals("a second stroke of the same size should not need new tiles", buffers, factory.created.size)
        // Its tiles are the first stroke's, rinsed rather than inheriting that ink.
        for (k in fills.indices) assertTrue("tile $k was not rinsed", factory.created[k].fills > fills[k])
    }

    @Test fun aZoomChangeStartsOver() {
        val stroke = liveStroke(Tool.PEN, 300)
        frame(stroke, res = 1.0)
        val before = runCounts()
        frame(stroke, res = 2.0)
        assertEquals("the rebuild must start from the head", 0, runsSince(before).first().first)
    }

    @Test fun aSurfaceLargerThanTheCapIsRefused() {
        val r = FakeRenderer()
        assertFalse(cache.paint(r, liveStroke(Tool.PEN, 300), 1.0, maxPixels = 16L))
    }

    @Test fun tilesSitOnAFixedPixelGridAndMeetEdgeToEdge() {
        // A tile off the grid would be resampled by its blit, and the ink would go soft.
        val res = 1.7
        val stroke = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN))
        stroke.finished = false
        var dests: List<Rect> = emptyList()
        for (i in 0 until 900) {
            stroke.addSample(Sample(20.0 + i * 2.0, 200.0 + sin(i * 0.05) * 40.0, 0.6, i * 5.0))
            dests = frame(stroke, res).second.rasterDests
        }
        assertTrue("a long stroke on a handful of tiles", dests.size > 4)
        val side = WetInkCache.TILE / res
        for (d in dests) {
            val gx = d.left * res / WetInkCache.TILE
            val gy = d.top * res / WetInkCache.TILE
            assertEquals("off the grid in x", Math.round(gx).toDouble(), gx, 1e-9)
            assertEquals("off the grid in y", Math.round(gy).toDouble(), gy, 1e-9)
            assertEquals(side, d.w, 1e-9)
            assertEquals(side, d.h, 1e-9)
        }
        assertEquals("a tile blitted twice", dests.size, dests.distinct().size)
    }

    @Test fun aLongStrokeOnlyEverTouchesTheTilesUnderTheNib() {
        // The tiles are the textures the GPU holds; one changed is one uploaded. A frame may only
        // change the few its newly settled run lands on, however long the stroke has got.
        fun worstChanged(tool: Tool, count: Int): Int {
            val f = FakeSurfaceFactory()
            val c = WetInkCache(f)
            val stroke = Stroke(tool, ToolDefaults.configFor(tool))
            stroke.finished = false
            var before = emptyList<Int>()
            var worst = 0
            for (i in 0 until count) {
                stroke.addSample(Sample(20.0 + i * 1.5, 300.0 + sin(i * 0.02) * 200.0, 0.6, i * 5.0))
                c.paint(LayerRecorder(), stroke, 1.0, cap)
                val now = f.created.map { it.painter.ops.size + it.painter.ribbonRuns.size + it.fills }
                var changed = 0
                for (k in now.indices) if (k >= before.size || now[k] != before[k]) changed++
                before = now
                if (i > 60) worst = maxOf(worst, changed)
            }
            assertTrue("$tool never covered many tiles", f.created.size > 8)
            // Nothing is ever copied from one surface to another.
            assertTrue(f.created.none { it.painter.ops.contains("drawRaster") })
            return worst
        }
        for (tool in listOf(Tool.PEN, Tool.HIGHLIGHTER)) {
            val short = worstChanged(tool, 300)
            val long = worstChanged(tool, 1500)
            assertTrue("$tool: $long tiles changed in a frame of 1500 vs $short of 300", long <= maxOf(short, 4))
        }
    }
}
