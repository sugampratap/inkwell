package com.xnotes.canvas

import com.xnotes.core.FakeRenderer
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.pal.RasterSurface
import com.xnotes.core.pal.Renderer
import com.xnotes.core.pal.SurfaceFactory
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The paged canvas's wet cache holding a live pencil: each pass's settled coverage baked once into
 * mask tiles, what the masks composite to kept in tiles of its own, and per frame those blitted
 * away from the tail while, over the tail's box, each mask and its pass's tail go into a layer with
 * the grain laid over the coverage and the layer composited at the pass's alpha.
 *
 * What the screen ends up with is modelled exactly here: every ribbon drawn is kept as geometry, a
 * mask is the ribbons drawn into it, a composited tile is the passes laid into it over the area it
 * was cleared in, a clip is a predicate, and a spot's ink is worked out by compositing everything
 * drawn there the way the canvas does. That is set against the stroke painting itself whole, so the
 * test says the tiled bake lays the same ink on every spot as the full-path render, not roughly.
 */
class PencilWetInkCacheTest {

    private val cap = 8_000_000L

    /** A wandering line with the pressure swinging from a light touch to a firm one and back. */
    private fun sampleAt(i: Int): Sample {
        val u = i * 0.09
        return Sample(
            60.0 + u * 24.0 + sin(u * 2.9) * 8.0,
            80.0 + cos(u * 1.6) * 30.0,
            0.5 + 0.45 * sin(i * 0.037),
            i * 6.0,
        )
    }

    // --- an exact model of what reaches the screen ---

    /** One ribbon as drawn: its discs and the quads bridging them, copied out at draw time. */
    private class Ribbon(centers: FloatArray, radii: FloatArray, from: Int, val count: Int) {
        val x = FloatArray(count) { centers[2 * (from + it)] }
        val y = FloatArray(count) { centers[2 * (from + it) + 1] }
        val r = FloatArray(count) { radii[from + it] }

        /** Whether the swept disc covers ([px], [py]): a disc, or the trapezoid bridging two. */
        fun covers(px: Double, py: Double): Boolean {
            for (i in 0 until count) {
                val ri = r[i]
                if (ri > 0f && hypot(px - x[i], py - y[i]) <= ri) return true
            }
            for (i in 0 until count - 1) {
                // A bridge between two discs of no width has no area, as the canvas fills it.
                if (r[i] <= 0f && r[i + 1] <= 0f) continue
                val dx = (x[i + 1] - x[i]).toDouble()
                val dy = (y[i + 1] - y[i]).toDouble()
                val len = hypot(dx, dy)
                if (len < 1e-9) continue
                val t = ((px - x[i]) * dx + (py - y[i]) * dy) / len
                if (t < 0 || t > len) continue
                val s = abs(((px - x[i]) * -dy + (py - y[i]) * dx) / len)
                if (s <= r[i] + (r[i + 1] - r[i]) * t / len) return true
            }
            return false
        }
    }

    /** Coverage limited to the rect it was blitted over, or anywhere its geometry reaches (null). */
    private class Cover(val ribbons: List<Ribbon>, val within: Rect?)

    /** One pass: its alpha, what it covers, whether through the grain, and where it was clipped. */
    private class Pass(val alpha: Double, val parts: List<Cover>, val grained: Boolean, val clip: (Double, Double) -> Boolean)

    /** An area a composited tile was cleared over, and the passes then laid there. */
    private class Patch(val area: Rect, val passes: List<Pass>)

    /** A composited tile blitted to the page over [dest], under [clip]. */
    private class Blit(val surface: ModelSurface, val dest: Rect, val clip: (Double, Double) -> Boolean)

    /** A mask, or a tile of composited ink, as everything that has been drawn into it. */
    private class ModelSurface(override val width: Int, override val height: Int, val mask: Boolean) : RasterSurface {
        override val devicePixelRatio: Double = 1.0
        val ribbons = ArrayList<Ribbon>()
        val patches = ArrayList<Patch>()
        var recycled = false

        /** Bumped by anything that changes the pixels, which is what makes the GPU upload it again. */
        var changes = 0

        /** Ribbon runs drawn straight into this surface, as `from to count`. */
        val drawn = ArrayList<Pair<Int, Int>>()

        override fun fill(color: Rgba) {
            ribbons.clear()
            patches.clear()
            changes++
        }

        /** The composited ink at ([x], [y]), as the last patch over that spot laid it. */
        fun alphaAt(x: Double, y: Double): Double {
            val patch = patches.lastOrNull { has(it.area, x, y) } ?: return 0.0
            var a = 0.0
            for (p in patch.passes) {
                val s = passAlpha(p, x, y)
                a = s + a * (1 - s)
            }
            return a
        }

        override fun renderer(): Renderer = object : Renderer by FakeRenderer() {
            private var clip: Rect? = null
            private var cleared: Rect? = null
            private var layer: ArrayList<Cover>? = null
            private var layerAlpha = 0.0
            private var grained = false
            private val passes = ArrayList<Pass>()

            override fun save() {}

            override fun restore() {
                val l = layer
                if (l != null) {
                    passes += Pass(layerAlpha, l, grained) { _, _ -> true }
                    layer = null
                    return
                }
                // The end of a bake into this surface.
                cleared?.let { patches += Patch(it, passes.toList()) }
                cleared = null
                clip = null
                passes.clear()
            }

            override fun clipRect(rect: Rect) {
                clip = rect
            }

            override fun clear() {
                cleared = checkNotNull(clip) { "a tile cleared whole" }
                changes++
            }

            override fun saveLayerAlpha(bounds: Rect, alpha: Double) {
                check(!mask) { "a layer in a mask" }
                check(layer == null)
                layer = ArrayList()
                layerAlpha = alpha
                grained = false
            }

            override fun maskGrain(color: Rgba) {
                checkNotNull(layer)
                grained = true
            }

            override fun fillDiskRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
                check(mask || patches.isEmpty()) { "ribbons drawn straight into a composited tile" }
                ribbons += Ribbon(centers, radii, from, count)
                drawn += from to count
                changes++
            }

            override fun drawRaster(raster: RasterSurface, dest: Rect, src: Rect?) {
                val l = checkNotNull(layer) { "a mask blitted into a tile outside a layer" }
                val m = raster as ModelSurface
                check(m.mask)
                l += Cover(m.ribbons.toList(), dest)
                changes++
            }

            override fun withSave(block: () -> Unit) = block()
        }

        override fun recycle() {
            recycled = true
        }
    }

    private class ModelFactory : SurfaceFactory {
        val created = ArrayList<ModelSurface>()

        override fun create(widthPx: Int, heightPx: Int, devicePixelRatio: Double): RasterSurface =
            ModelSurface(widthPx, heightPx, mask = false).also { created += it }

        override fun createMask(widthPx: Int, heightPx: Int, devicePixelRatio: Double): RasterSurface =
            ModelSurface(widthPx, heightPx, mask = true).also { created += it }
    }

    /** The page: a renderer that composites the way the Android canvas does. */
    private class Screen(private val grain: Boolean = true, private val cuts: Boolean = true) : Renderer by FakeRenderer() {
        override val masksGrain: Boolean get() = grain

        /** Everything composited onto the page, in order: blits and passes. */
        val drawn = ArrayList<Any>()
        val order = ArrayList<String>()

        /** Points of each ribbon drawn into a layer straight, which is the live tail. */
        val tails = ArrayList<Int>()

        /** Every layer's bounds, which is what a frame fills. */
        val layerBounds = ArrayList<Rect>()

        private var clips = ArrayList<(Double, Double) -> Boolean>()
        private val saved = ArrayDeque<Pair<Boolean, List<(Double, Double) -> Boolean>>>()
        private var layer: ArrayList<Cover>? = null
        private var layerAlpha = 0.0
        private var layerClip: (Double, Double) -> Boolean = { _, _ -> true }
        private var grained = false

        private fun clipNow(): (Double, Double) -> Boolean {
            val list = clips.toList()
            return { x, y -> list.all { it(x, y) } }
        }

        override fun save() {
            saved.addLast(false to clips.toList())
            order += "save"
        }

        override fun clipRect(rect: Rect) {
            clips += { x, y -> has(rect, x, y) }
            order += "clip"
        }

        override fun clipOutRect(rect: Rect): Boolean {
            if (!cuts) return false
            clips += { x, y -> !has(rect, x, y) }
            order += "clipOut"
            return true
        }

        override fun saveLayerAlpha(bounds: Rect, alpha: Double) {
            check(layer == null) { "layers nested" }
            saved.addLast(true to clips.toList())
            layer = ArrayList()
            layerAlpha = alpha
            layerClip = clipNow()
            grained = false
            layerBounds += bounds
            order += "layer"
        }

        override fun drawRaster(raster: RasterSurface, dest: Rect, src: Rect?) {
            val s = raster as ModelSurface
            val l = layer
            if (l == null) {
                check(!s.mask) { "a mask blitted straight to the page" }
                drawn += Blit(s, dest, clipNow())
                order += "tile"
                return
            }
            check(!grained) { "drawn after the grain" }
            check(s.mask) { "composited ink blitted into a layer" }
            l += Cover(s.ribbons.toList(), dest)
            order += "raster"
        }

        override fun fillDiskRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
            val l = checkNotNull(layer) { "bare coverage drawn straight to the page" }
            check(!grained) { "drawn after the grain" }
            l += Cover(listOf(Ribbon(centers, radii, from, count)), null)
            tails += count
            order += "ribbon"
        }

        override fun maskGrain(color: Rgba) {
            checkNotNull(layer)
            check(!grained) { "the grain laid twice" }
            grained = true
            order += "grain"
        }

        override fun restore() {
            val (wasLayer, back) = saved.removeLast()
            if (wasLayer) {
                drawn += Pass(layerAlpha, checkNotNull(layer), grained, layerClip)
                layer = null
            }
            clips = ArrayList(back)
            order += "restore"
        }

        override fun fillGrainRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
            check(layer == null)
            val cover = Cover(listOf(Ribbon(centers, radii, from, count)), null)
            drawn += Pass(color.a / 255.0, listOf(cover), true) { _, _ -> true }
            order += "grainRibbon"
        }

        /** The ink's opacity at ([x], [y]): everything composited over the last, source-over. */
        fun alphaAt(x: Double, y: Double): Double {
            var a = 0.0
            for (d in drawn) {
                val s = when (d) {
                    is Blit -> if (d.clip(x, y) && has(d.dest, x, y)) d.surface.alphaAt(x, y) else 0.0
                    is Pass -> passAlpha(d, x, y)
                    else -> error("unknown")
                }
                a = s + a * (1 - s)
            }
            return a
        }

        val passes: List<Pass> get() = drawn.filterIsInstance<Pass>()
    }

    // --- the stroke ---

    private fun pencil(): Stroke = Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL)).also { it.finished = false }

    /** Draw [count] samples of a live pencil, a frame through [cache] after each, as a hand does. */
    private fun draw(
        cache: WetInkCache,
        count: Int,
        sample: (Int) -> Sample = ::sampleAt,
        screen: () -> Screen = { Screen() },
        onFrame: (Screen, Boolean) -> Unit = { _, _ -> },
    ): Pair<Stroke, Screen> {
        val stroke = pencil()
        var last = screen()
        for (i in 0 until count) {
            stroke.addSample(sample(i))
            last = screen()
            val took = cache.paint(last, stroke, 1.0, cap)
            onFrame(last, took)
        }
        return stroke to last
    }

    /** Check [screen] against the stroke painting itself whole, along the line and all over its box. */
    private fun assertSameInk(stroke: Stroke, screen: Screen, seed: Long, what: String) {
        val whole = Screen()
        stroke.paint(whole)
        assertEquals(listOf("grainRibbon", "grainRibbon"), whole.order)
        val ribbon = stroke.wetRibbon!!
        val rnd = java.util.Random(seed)
        var inked = 0
        var checked = 0
        fun check(x: Double, y: Double) {
            val want = whole.alphaAt(x, y)
            val got = screen.alphaAt(x, y)
            assertEquals("ink at ($x, $y) of $what", want, got, 1e-12)
            if (want > 0) inked++
            checked++
        }
        for (i in 0 until ribbon.pointCount) {
            repeat(10) {
                val reach = ribbon.hw(i) * 1.3
                check(ribbon.cx(i) + (rnd.nextDouble() * 2 - 1) * reach, ribbon.cy(i) + (rnd.nextDouble() * 2 - 1) * reach)
            }
        }
        val b = stroke.bounds()
        repeat(3000) { check(b.left + rnd.nextDouble() * b.w, b.top + rnd.nextDouble() * b.h) }
        assertTrue("the samples missed the ink: $inked of $checked", inked * 5 > checked)
    }

    @Test fun theTiledBakeLaysTheSameInkAsTheWholeStroke() {
        val cache = WetInkCache(ModelFactory())
        for (count in listOf(120, 300, 700)) {
            val (stroke, screen) = draw(cache, count)
            assertEquals("two passes over the tail, each composited once", 2, screen.passes.size)
            assertTrue("no tiles blitted away from the tail", screen.order.contains("tile"))
            val whole = Screen()
            stroke.paint(whole)
            for (k in 0..1) assertEquals("pass $k's alpha", whole.passes[k].alpha, screen.passes[k].alpha, 0.0)
            assertSameInk(stroke, screen, 7L + count, "a $count-point pencil")
            cache.clear()
        }
    }

    @Test fun aStrokeLoopingBackOverItselfStillLaysItsInkOnce() {
        // Round and round the same few tiles, so new runs land on tiles composited long before.
        val loop = { i: Int ->
            val u = i * 0.11
            Sample(300.0 + cos(u) * 90.0 + i * 0.05, 300.0 + sin(u) * 70.0, 0.55 + 0.4 * sin(i * 0.05), i * 6.0)
        }
        val (stroke, screen) = draw(WetInkCache(ModelFactory()), 500, loop)
        assertSameInk(stroke, screen, 99L, "a looping pencil")
    }

    @Test fun aRendererThatCannotCutStillLaysTheSameInk() {
        val (stroke, screen) = draw(WetInkCache(ModelFactory()), 300, screen = { Screen(cuts = false) })
        assertFalse("tiles blitted with nothing to keep them off the tail", screen.order.contains("tile"))
        assertSameInk(stroke, screen, 3L, "an uncut pencil")
    }

    @Test fun theTailIsLaidInItsOwnBoxAndTheTilesEverywhereElse() {
        // Ending on a firm stretch, so the tail has a core of its own to lay.
        val (stroke, screen) = draw(WetInkCache(ModelFactory()), 380)
        val o = screen.order
        val cut = o.indexOf("clipOut")
        val firstLayer = o.indexOf("layer")
        assertTrue("the tiles go down with the tail cut out", cut in 0 until firstLayer)
        assertTrue(o.subList(cut, firstLayer).contains("tile"))
        val overTail = o.subList(o.indexOf("clip"), o.size)
        assertEquals(
            listOf("clip", "layer", "ribbon", "grain", "restore", "layer", "ribbon", "grain", "restore", "restore"),
            overTail.filter { it != "save" && it != "raster" },
        )
        assertTrue("the baked coverage under the tail", overTail.indexOf("raster") in 0 until overTail.indexOf("ribbon"))
        val color = stroke.renderColor
        assertEquals(color.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0, screen.passes[0].alpha, 0.0)
        assertEquals(color.scaleAlpha(Graphite.CORE_ALPHA).a / 255.0, screen.passes[1].alpha, 0.0)
        // Both layers are the tail's box, which holds every disc the tail lays.
        val ribbon = stroke.wetRibbon!!
        val box = screen.layerBounds.first()
        assertEquals(box, screen.layerBounds.last())
        val n = ribbon.pointCount
        for (i in n - screen.tails.first() until n) {
            val h = ribbon.hw(i)
            assertTrue(ribbon.cx(i) - h >= box.left && ribbon.cx(i) + h <= box.right)
            assertTrue(ribbon.cy(i) - h >= box.top && ribbon.cy(i) + h <= box.bottom)
        }
    }

    @Test fun aFrameDrawsTheTailHoweverLongTheStroke() {
        fun worstTail(count: Int): Int {
            var worst = 0
            var taken = 0
            draw(WetInkCache(ModelFactory()), count) { screen, took ->
                if (took) {
                    taken++
                    worst = maxOf(worst, screen.tails.maxOrNull() ?: 0)
                }
            }
            assertTrue("the cache never took a $count-point pencil", taken > count / 2)
            return worst
        }
        val short = worstTail(300)
        val long = worstTail(1500)
        // A frame lays the points still moving and nothing behind them: the same few for a long
        // stroke as for a short one, and nothing like the stroke itself.
        assertTrue("tail $long vs $short", long <= short + 4)
        assertTrue("the tail is not a tail: $long points", long <= 16)
    }

    /**
     * What a frame changes and fills as the stroke grows from 300 points to 1500: the surfaces whose
     * pixels it changes (each one an upload to the GPU) and the area its layers cover. Neither may
     * grow with the stroke; with one surface and one layer the size of the stroke, both did.
     */
    @Test fun aFrameUploadsAndFillsTheSameAt300And1500Points() {
        class Cost(val worstChanged: Int, val worstLayerArea: Double, val surfaces: Int)

        fun cost(count: Int): Cost {
            val factory = ModelFactory()
            var before = IntArray(0)
            var worstChanged = 0
            var worstArea = 0.0
            // A long sweep across the page, so the stroke's box keeps growing.
            val sweep = { i: Int -> Sample(40.0 + i * 1.6, 300.0 + sin(i * 0.02) * 200.0, 0.5 + 0.45 * sin(i * 0.037), i * 6.0) }
            draw(WetInkCache(factory), count, sweep) { screen, took ->
                val now = IntArray(factory.created.size) { factory.created[it].changes }
                var changed = 0
                for (k in now.indices) if (k >= before.size || now[k] != before[k]) changed++
                before = now
                if (took) {
                    worstChanged = maxOf(worstChanged, changed)
                    for (b in screen.layerBounds) worstArea = maxOf(worstArea, b.w * b.h)
                }
            }
            return Cost(worstChanged, worstArea, factory.created.size)
        }
        val short = cost(300)
        val long = cost(1500)
        assertTrue("the long stroke covers more tiles: ${long.surfaces} vs ${short.surfaces}", long.surfaces > short.surfaces * 2)
        assertTrue(
            "surfaces changed in a frame: ${long.worstChanged} vs ${short.worstChanged}",
            long.worstChanged <= maxOf(short.worstChanged, 12),
        )
        assertTrue(
            "layer area in a frame: ${long.worstLayerArea} vs ${short.worstLayerArea}",
            long.worstLayerArea <= short.worstLayerArea * 1.5 + 400,
        )
    }

    @Test fun eachSettledPointIsBakedOnceIntoEachMask() {
        val factory = ModelFactory()
        val (stroke, _) = draw(WetInkCache(factory), 600)
        val masks = factory.created.filter { it.mask }
        assertTrue("the pencil keeps its passes as coverage masks", masks.isNotEmpty())
        // A run lands on every tile it reaches, but it is one run: the same points, once.
        val runs = masks.flatMap { it.drawn }.distinct().sortedBy { it.first }
        assertEquals("one count per run", runs.size, runs.map { it.first }.distinct().size)
        val settled = stroke.wetRibbon!!.settledCount
        assertEquals(settled + runs.size - 1, runs.sumOf { it.second })
        for (k in 1 until runs.size) {
            assertEquals("run $k starts a point back", runs[k - 1].first + runs[k - 1].second - 1, runs[k].first)
        }
    }

    @Test fun aRendererWithoutTheGrainKeepsTheWholeStrokePaint() {
        val factory = ModelFactory()
        val cache = WetInkCache(factory)
        val stroke = pencil()
        for (i in 0 until 300) stroke.addSample(sampleAt(i))
        assertFalse(cache.paint(Screen(grain = false), stroke, 1.0, cap))
        assertTrue("it took a surface anyway", factory.created.isEmpty())
    }

    @Test fun thePenAfterThePencilBakesColourAndLeavesTheMasksAlone() {
        val factory = ModelFactory()
        val cache = WetInkCache(factory)
        draw(cache, 300)
        val pen = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN))
        pen.finished = false
        for (i in 0 until 300) pen.addSample(sampleAt(i))
        val masksBefore = factory.created.filter { it.mask }.associateWith { it.changes }
        val r = FakeRenderer()
        assertTrue(cache.paint(r, pen, 1.0, cap))
        assertTrue("the pen blitted nothing", r.rasterDests.isNotEmpty())
        for ((m, changes) in masksBefore) assertEquals("the pen drew into a mask", changes, m.changes)
    }

    @Test fun aLightPencilOpensNoCoreLayer() {
        val cache = WetInkCache(ModelFactory())
        val stroke = pencil()
        for (i in 0 until 300) stroke.addSample(sampleAt(i).let { Sample(it.x, it.y, 0.05, it.t) })
        val screen = Screen()
        assertTrue(cache.paint(screen, stroke, 1.0, cap))
        assertEquals(1, screen.order.count { it == "layer" })
    }

    companion object {
        /** The paper's grain at a page spot, as the shader samples it: one texel per page pixel, tiled. */
        fun grainAt(x: Double, y: Double): Double {
            val n = Graphite.TILE
            val i = Math.floorMod(floor(x).toInt(), n)
            val j = Math.floorMod(floor(y).toInt(), n)
            return (Graphite.tile[j * n + i].toInt() and 0xFF) / 255.0
        }

        /** Half-open containment, so tiles laid edge to edge and a clip in and out never share a spot. */
        private fun has(r: Rect, x: Double, y: Double) = x >= r.left && x < r.right && y >= r.top && y < r.bottom

        private fun covered(parts: List<Cover>, x: Double, y: Double): Boolean =
            parts.any { c -> (c.within == null || has(c.within, x, y)) && c.ribbons.any { it.covers(x, y) } }

        private fun passAlpha(p: Pass, x: Double, y: Double): Double {
            if (!p.clip(x, y) || !covered(p.parts, x, y)) return 0.0
            return p.alpha * (if (p.grained) grainAt(x, y) else 1.0)
        }
    }
}
