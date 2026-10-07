package com.xnotes.canvas

import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.pal.RasterSurface
import com.xnotes.core.pal.Renderer
import com.xnotes.core.pal.SurfaceFactory
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.WetRibbon
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The raster half of the wet cache: the part of the stroke under the pen that has stopped moving,
 * painted into offscreen surfaces once and blitted every frame after.
 *
 * [WetRibbon] makes the *geometry* of a long stroke cost what its moving tail costs. This does the
 * same for the *pixels*, which is the larger of the two: filling the ribbon means a path of two
 * shapes per point, rebuilt and scan-converted every frame, so a stroke that has been going for a
 * few seconds is redrawing thousands of discs to show the one under the nib. Here the settled run
 * is drawn into the surfaces as it settles — each point exactly once, ever — and the frame draws
 * bitmaps plus the handful of points still in play.
 *
 * ### Tiles
 *
 * The settled ink lives in a sparse grid of small square tiles in page space, not in one surface
 * the size of the stroke. A surface the canvas draws is a texture on the GPU, and a bitmap changed
 * since the last frame is uploaded again *whole*: one surface the size of the stroke meant every
 * frame uploaded the stroke's entire box to show the few points that had settled, which is what
 * made a long stroke stutter more the longer it got. A tile is only touched when a run lands on
 * it, so a frame uploads the one or two tiles under the nib and the rest stay on the GPU as they
 * were. Each tile has an apron a couple of pixels wide, overlapping its neighbours' and drawn into
 * like the rest of it, so a filtered blit at a fractional offset reads its neighbour's real pixels
 * across the seam instead of clamping. The grid is fixed in page pixels, so nothing is ever copied
 * from one surface into another as the stroke grows.
 *
 * ### The seam with the live tail
 *
 * The baked run and the live one deliberately overlap by a point, so they share a whole brush disc
 * and no seam can open between them. That only works because the ink is opaque: the same colour
 * laid down twice is still that colour. Neon's blooms would compound, so it keeps the plain redraw.
 *
 * Translucent ink — the highlighter above all — would darken along the join if its runs were
 * composited one by one, so it is baked solid, as the layer [Stroke.paint] accumulates it in holds
 * it, and the baked tiles and the live tail are put into one such layer and composited once. That
 * layer is only needed where the tail is: everywhere else the settled ink is all there is, and a
 * baked tile blitted at the ink's alpha and blend is exactly what the layer would have composited
 * there. So the layer is opened over the tail's box alone, with that box cut out of everything
 * else ([Renderer.clipOutRect]), and what a frame fills stops growing with the stroke's area.
 *
 * The pencil ([Graphite]) is two such passes, an outer and a pressed core, each through the paper's
 * grain at its own alpha. Each is baked as bare coverage into masks of its own (one byte a pixel
 * where the host has it), and a third grid holds what those masks composite to, ink and grain and
 * both passes, recomposited only where a run lands. Away from the tail a frame blits that; over the
 * tail's box it puts each mask and its pass's tail into a layer, lays the ink through the grain
 * over that coverage ([Renderer.maskGrain]) and composites the layer at the pass's alpha: per pixel
 * exactly what [Renderer.fillGrainRibbon] lays for the whole pass, since the grain is anchored to
 * the page either way and the coverage of the union is the same.
 *
 * Everything here is in **page space**, like the page and highlighter caches, so the tiles hold
 * still while the ink grows over them and only a zoom can invalidate them. A zoom cannot arrive
 * mid-stroke anyway: a second finger aborts the stroke before it becomes a pinch.
 */
class WetInkCache(private val surfaceFactory: SurfaceFactory) {

    /** One tile: a surface and a painter into it, at a place in the grid. */
    private class Tile(val tx: Int, val ty: Int, val surface: RasterSurface, val into: Renderer)

    /** A sparse grid of tiles of one kind: ink in colour, or coverage masks. */
    private inner class Grid(val mask: Boolean) {
        private val map = HashMap<Long, Tile>()

        /** In the order made, which is the order a frame blits them in. */
        val tiles = ArrayList<Tile>()

        /** Tiles an earlier stroke let go of, kept so the next one does not allocate. */
        private val spare = ArrayList<Tile>()

        val size: Int get() = tiles.size

        operator fun get(tx: Int, ty: Int): Tile? = map[key(tx, ty)]

        fun obtain(tx: Int, ty: Int): Tile {
            map[key(tx, ty)]?.let { return it }
            val reused = spare.removeLastOrNull()
            val tile = if (reused != null) {
                Tile(tx, ty, reused.surface, reused.into)
            } else {
                val s = if (mask) surfaceFactory.createMask(SIDE, SIDE, 1.0) else surfaceFactory.create(SIDE, SIDE, 1.0)
                Tile(tx, ty, s, s.renderer())
            }
            tile.surface.fill(TRANSPARENT)
            map[key(tx, ty)] = tile
            tiles += tile
            return tile
        }

        /** Let every tile go to the spares, for a stroke starting over. */
        fun rinse() {
            for (t in tiles) if (spare.size < MAX_SPARE) spare += t else t.surface.recycle()
            tiles.clear()
            map.clear()
        }

        fun recycle() {
            for (t in tiles) t.surface.recycle()
            for (t in spare) t.surface.recycle()
            tiles.clear()
            spare.clear()
            map.clear()
        }
    }

    /** The settled ink: in its colour, or for the pencil what both its passes composite to. */
    private val ink = Grid(mask = false)

    /** The pencil's outer and pressed core, as coverage. Both hold nothing for any other ink. */
    private val outer = Grid(mask = true)
    private val core = Grid(mask = true)

    /** Whether any point baked so far has a core, so the core's layer is worth opening. */
    private var coreBaked = false

    /** The live pencil's core radii by ribbon point; only the points about to be laid are worked out. */
    private var coreRadii = FloatArray(0)

    /** Device px per page px the tiles were rendered at. */
    private var res = 0.0

    /** The stroke the tiles hold, by identity; a different one starts over. */
    private var owner: Stroke? = null

    /** Ribbon points already in the tiles. */
    private var baked = 0

    /** Centreline arc those points spent, which is where the dashed pen's rhythm has got to. */
    private var bakedArc = 0.0

    /** Tiles a bake is drawing into, reused from one bake to the next. */
    private val touched = ArrayList<Tile>()

    /**
     * Draw [stroke]'s live ink into [r], baking whatever has settled since the last frame. Returns
     * false when this stroke is not one the cache can hold, or when it is not yet long enough to be
     * worth a surface, and the caller should paint it whole.
     *
     * [res] is device px per page px and [maxPixels] caps the tiles' pixels all told, since a stroke
     * sweeping a zoomed-in page could otherwise ask for far more than the screen showing it.
     */
    fun paint(r: Renderer, stroke: Stroke, res: Double, maxPixels: Long): Boolean {
        val ribbon = stroke.wetRibbon ?: return false
        val layered = !stroke.wetCacheable && bakesUnderLayer(stroke)
        if (!stroke.wetCacheable && !layered) return false
        // The pencil's grain goes on at the frame, so only a renderer that can lay it there may.
        val graphite = stroke.config.grain
        if (graphite && !r.masksGrain) return false
        if (owner !== stroke || abs(this.res - res) > 1e-9) restart(stroke, res)
        val settled = ribbon.settledCount
        if (settled < MIN_BAKE_POINTS) return false
        if (!bake(stroke, ribbon, settled, maxPixels)) return false
        // One point back, so the live run and the baked one share a disc and cannot leave a gap,
        // and it starts exactly where the baked run's dash pattern got to.
        val from = max(baked - 1, 0)
        when {
            graphite -> paintGraphite(r, stroke, ribbon, from)
            layered -> paintLayered(r, stroke, ribbon, from)
            else -> {
                for (t in ink.tiles) blit(r, t)
                stroke.paintRun(r, ribbon, from, ribbon.pointCount - from, bakedArc)
            }
        }
        return true
    }

    /** Let the last stroke's tiles go to the spares and start [stroke] from its head at [res]. */
    private fun restart(stroke: Stroke, res: Double) {
        owner = stroke
        this.res = res
        baked = 0
        bakedArc = 0.0
        coreBaked = false
        ink.rinse()
        outer.rinse()
        core.rinse()
    }

    /**
     * What [Stroke.paint] does with translucent ink, the same layer composited the same way once,
     * but only over the tail's box: away from it the baked tiles are blitted at the ink's alpha and
     * blend, which is what the layer would have composited there. Inside the layer both runs are
     * solid, so where they meet they union instead of darkening, and the alpha and the blend come
     * in once.
     */
    private fun paintLayered(r: Renderer, stroke: Stroke, ribbon: WetRibbon, from: Int) {
        val alpha = stroke.renderColor.a / 255.0
        val n = ribbon.pointCount
        val tail = tailBox(stroke, ribbon, ribbon.halfWidthArray(), from, n)
        if (tail == null || !cutOut(r, tail)) {
            // A renderer that cannot cut the tail's box out: the whole stroke in one layer.
            r.saveLayerBlended(stroke.bounds().outset(2.0), alpha, stroke.blendMode)
            for (t in ink.tiles) blit(r, t)
            r.fillDiskRibbon(ribbon.centerlineArray(), ribbon.halfWidthArray(), from, n - from, solid(stroke))
            r.restore()
            return
        }
        for (t in ink.tiles) r.drawRasterBlended(t.surface, innerRect(t), alpha, stroke.blendMode, INNER)
        r.restore()
        r.save()
        r.clipRect(tail)
        r.saveLayerBlended(tail, alpha, stroke.blendMode)
        for (t in ink.tiles) if (reaches(t, tail)) blit(r, t)
        r.fillDiskRibbon(ribbon.centerlineArray(), ribbon.halfWidthArray(), from, n - from, solid(stroke))
        r.restore()
        r.restore()
    }

    /**
     * The pencil's frame. Away from the tail, the composited tiles. Over the tail's box, for each
     * pass, its baked coverage and its tail's into one layer, the ink laid through the grain over
     * that coverage, and the layer composited at the pass's alpha, the core over the outer. Inside a
     * layer the baked run and the tail are both bare coverage, so where they meet they union, and
     * the grain and the alpha come in once, as when [Stroke.paint] fills each pass whole. What a
     * frame fills is the tiles and the tail's box, however long the stroke.
     */
    private fun paintGraphite(r: Renderer, stroke: Stroke, ribbon: WetRibbon, from: Int) {
        val color = stroke.renderColor
        val n = ribbon.pointCount
        val centers = ribbon.centerlineArray()
        val widths = ribbon.halfWidthArray()
        val radii = coreRoom(n)
        val c = stroke.config
        val tailCore = Graphite.coreRadii(widths, from, n - from, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, radii, 0)
        val tail = tailBox(stroke, ribbon, widths, from, n)
        val split = tail != null && cutOut(r, tail)
        val box = if (split) tail!! else stroke.bounds().outset(2.0)
        if (split) {
            for (t in ink.tiles) blit(r, t)
            r.restore()
            r.save()
            r.clipRect(box)
        }
        r.saveLayerAlpha(box, color.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0)
        for (t in outer.tiles) if (!split || reaches(t, box)) blit(r, t)
        r.fillDiskRibbon(centers, widths, from, n - from, MASK)
        r.maskGrain(color)
        r.restore()

        // A stroke drawn lightly throughout has no core, and its whole stroke skips the pass too.
        if (coreBaked || tailCore) {
            r.saveLayerAlpha(box, color.scaleAlpha(Graphite.CORE_ALPHA).a / 255.0)
            if (coreBaked) for (t in core.tiles) if (!split || reaches(t, box)) blit(r, t)
            if (tailCore) r.fillDiskRibbon(centers, radii, from, n - from, MASK)
            r.maskGrain(color)
            r.restore()
        }
        if (split) r.restore()
    }

    /**
     * Open a save with [box] cut out of the clip, for everything drawn away from the tail; the
     * caller restores it. False, with nothing left open, where the renderer cannot cut.
     */
    private fun cutOut(r: Renderer, box: Rect): Boolean {
        r.save()
        if (r.clipOutRect(box)) return true
        r.restore()
        return false
    }

    /**
     * The page-space box the tail from [from] reaches, grown by the antialiased edge so nothing it
     * lays is cut, or null for none.
     */
    private fun tailBox(stroke: Stroke, ribbon: WetRibbon, widths: FloatArray, from: Int, n: Int): Rect? {
        if (n <= from) return null
        return grown(stroke, runBox(ribbon, widths, from, n) ?: return null)
    }

    /** [box] grown by what the ink lays beyond its discs: the antialiased edge, and a dash's caps. */
    private fun grown(stroke: Stroke, box: Rect): Rect {
        var pad = EDGE_PX / res
        if (stroke.tool == com.xnotes.core.tools.Tool.DASHED) pad += stroke.config.baseWidth / 2.0
        return Rect(box.left - pad, box.top - pad, box.w + 2 * pad, box.h + 2 * pad)
    }

    /** The box the discs of points [from] until [until] cover, in page space, or null for none. */
    private fun runBox(ribbon: WetRibbon, widths: FloatArray, from: Int, until: Int): Rect? {
        var lo0 = Double.MAX_VALUE
        var lo1 = Double.MAX_VALUE
        var hi0 = -Double.MAX_VALUE
        var hi1 = -Double.MAX_VALUE
        val centers = ribbon.centerlineArray()
        for (i in from until until) {
            val x = centers[2 * i].toDouble()
            val y = centers[2 * i + 1].toDouble()
            val h = max(widths[i].toDouble(), 0.0)
            if (x - h < lo0) lo0 = x - h
            if (x + h > hi0) hi0 = x + h
            if (y - h < lo1) lo1 = y - h
            if (y + h > hi1) hi1 = y + h
        }
        if (lo0 > hi0 || lo1 > hi1) return null
        return Rect(lo0, lo1, hi0 - lo0, hi1 - lo1)
    }

    // --- tiles ---

    /** The page-space rect the inner part of [t] covers, which is what a blit lays it over. */
    private fun innerRect(t: Tile): Rect {
        val step = TILE / res
        return Rect(t.tx * step, t.ty * step, step, step)
    }

    /** The page-space rect [t]'s whole surface covers, apron included, which is what it is drawn in. */
    private fun surfaceRect(t: Tile): Rect {
        val step = TILE / res
        val apron = APRON / res
        return Rect(t.tx * step - apron, t.ty * step - apron, SIDE / res, SIDE / res)
    }

    private fun reaches(t: Tile, box: Rect): Boolean = intersects(innerRect(t), box)

    private fun blit(r: Renderer, t: Tile) = r.drawRaster(t.surface, innerRect(t), INNER)

    /**
     * The tiles of [grid] whose surfaces, aprons included, reach [box], into [into], made where they
     * do not exist yet. False, with nothing made, when that would take the tiles past [maxPixels].
     */
    private fun tilesFor(grid: Grid, box: Rect, maxPixels: Long, into: ArrayList<Tile>): Boolean {
        into.clear()
        val x0 = floor((box.left * res - APRON) / TILE).toInt()
        val x1 = floor((box.right * res + APRON) / TILE).toInt()
        val y0 = floor((box.top * res - APRON) / TILE).toInt()
        val y1 = floor((box.bottom * res + APRON) / TILE).toInt()
        var fresh = 0L
        for (ty in y0..y1) for (tx in x0..x1) if (grid[tx, ty] == null) fresh++
        val total = ink.size + outer.size + core.size + fresh
        if (total * SIDE * SIDE > maxPixels) return false
        for (ty in y0..y1) for (tx in x0..x1) into += grid.obtain(tx, ty)
        return true
    }

    /** Draw into [t] in page space: the tile's own pixels are page × [res], less its origin. */
    private inline fun inPage(t: Tile, block: (Renderer) -> Unit) {
        val r = t.into
        val origin = surfaceRect(t)
        r.save()
        r.scale(res, res)
        r.translate(-origin.left, -origin.top)
        block(r)
        r.restore()
    }

    /**
     * Paint the run that settled since the last frame into the tiles it lands on, at page scale,
     * and carry the dash phase over it. False when the tiles it needs would pass the cap.
     *
     * [bakedArc] is the arc the baked run has spent, always measured through the last point in it,
     * so it is both the phase this run starts at and — once this run's own length is added — the
     * phase the live tail starts at. Neither is ever measured from the head of the stroke.
     */
    private fun bake(stroke: Stroke, ribbon: WetRibbon, settled: Int, maxPixels: Long): Boolean {
        if (settled <= baked) return true
        // From one point back, so the segment bridging the last baked point to the next one is
        // drawn; its disc is simply laid down again, which opaque ink does not notice.
        val from = max(baked - 1, 0)
        val count = settled - from
        val centers = ribbon.centerlineArray()
        val widths = ribbon.halfWidthArray()
        val box = grown(stroke, runBox(ribbon, widths, from, settled) ?: return true)
        if (stroke.config.grain) {
            if (!tilesFor(outer, box, maxPixels, touched)) return false
            // Bare coverage: the grain and the colour come in when the tiles are composited.
            for (t in touched) inPage(t) { it.fillDiskRibbon(centers, widths, from, count, MASK) }
            val radii = coreRoom(settled)
            val c = stroke.config
            if (Graphite.coreRadii(widths, from, count, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, radii, 0)) {
                val coreBox = runBox(ribbon, radii, from, settled)
                if (coreBox != null) {
                    if (!tilesFor(core, grown(stroke, coreBox), maxPixels, touched)) return false
                    for (t in touched) inPage(t) { it.fillDiskRibbon(centers, radii, from, count, MASK) }
                    coreBaked = true
                }
            }
            if (!composite(stroke, box, maxPixels)) return false
        } else {
            if (!tilesFor(ink, box, maxPixels, touched)) return false
            val layered = !stroke.wetCacheable
            for (t in touched) inPage(t) {
                if (layered) {
                    // Solid, as the layer it is composited in would have held it.
                    it.fillDiskRibbon(centers, widths, from, count, solid(stroke))
                } else {
                    stroke.paintRun(it, ribbon, from, count, bakedArc)
                }
            }
        }
        for (k in from + 1 until settled) {
            bakedArc += hypot(ribbon.cx(k) - ribbon.cx(k - 1), ribbon.cy(k) - ribbon.cy(k - 1))
        }
        baked = settled
        return true
    }

    /**
     * Recomposite the pencil's ink tiles over [box] from its masks: the outer through the grain at
     * its alpha, the core over it at its own, exactly as a frame composites them over the tail.
     * Only [box] is touched, which is where the run just baked landed.
     */
    private fun composite(stroke: Stroke, box: Rect, maxPixels: Long): Boolean {
        if (!tilesFor(ink, box, maxPixels, touched)) return false
        val color = stroke.renderColor
        val outerAlpha = color.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0
        val coreAlpha = color.scaleAlpha(Graphite.CORE_ALPHA).a / 255.0
        for (t in touched) {
            val whole = surfaceRect(t)
            val area = intersection(whole, box) ?: continue
            val o = outer[t.tx, t.ty]
            val k = core[t.tx, t.ty]
            inPage(t) { r ->
                r.clipRect(area)
                r.clear()
                if (o != null) {
                    r.saveLayerAlpha(area, outerAlpha)
                    r.drawRaster(o.surface, whole)
                    r.maskGrain(color)
                    r.restore()
                }
                if (k != null) {
                    r.saveLayerAlpha(area, coreAlpha)
                    r.drawRaster(k.surface, whole)
                    r.maskGrain(color)
                    r.restore()
                }
            }
        }
        return true
    }

    /** [coreRadii], with room for [n] points. Nothing in it outlives the frame that fills it. */
    private fun coreRoom(n: Int): FloatArray {
        if (coreRadii.size < n) coreRadii = FloatArray(maxOf(n, coreRadii.size * 2, 256))
        return coreRadii
    }

    /**
     * Whether [stroke] is translucent ink [Stroke.paint] accumulates solid in a layer and
     * composites once: the highlighter, and any other translucent ink but neon and the dashed pen.
     *
     * The cache can hold that too, as long as what it bakes is the solid ink the layer would have
     * held. Overlapping runs of one solid colour union to that colour, exactly as for the pen, and
     * the frame then composites the baked runs and the live tail together in that one layer, at
     * the ink's alpha and blend, so nothing is blended twice.
     *
     * The pencil as well, which bakes the coverage of its two passes and has its grain laid over
     * them (see [paintGraphite]).
     */
    private fun bakesUnderLayer(stroke: Stroke): Boolean {
        if (stroke.config.grain) return true
        if (stroke.tool == com.xnotes.core.tools.Tool.DASHED) return false
        if (stroke.config.neon && stroke.tool != com.xnotes.core.tools.Tool.HIGHLIGHTER) return false
        return stroke.renderColor.a < 255
    }

    /** The colour layered ink is accumulated in: its own at full alpha, kept rather than rebuilt per frame. */
    private fun solid(stroke: Stroke): Rgba {
        val c = stroke.renderColor
        val held = solidColor
        if (held != null && held.r == c.r && held.g == c.g && held.b == c.b) return held
        return c.withAlpha(255).also { solidColor = it }
    }

    private var solidColor: Rgba? = null

    /** Let the surfaces go: a document closed, a memory trim, or the caches dropped wholesale. */
    fun clear() {
        ink.recycle()
        outer.recycle()
        core.recycle()
        owner = null
        baked = 0
        bakedArc = 0.0
        coreBaked = false
        res = 0.0
    }

    private fun intersects(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun intersection(a: Rect, b: Rect): Rect? {
        val l = max(a.left, b.left)
        val t = max(a.top, b.top)
        val rr = min(a.right, b.right)
        val bb = min(a.bottom, b.bottom)
        if (rr <= l || bb <= t) return null
        return Rect(l, t, rr - l, bb - t)
    }

    companion object {
        private val TRANSPARENT = Rgba(0, 0, 0, 0)

        /** What a coverage mask is filled with: any opaque colour holds the coverage, and only that is read. */
        private val MASK = Rgba(0, 0, 0, 255)

        /** Below this the stroke redraws whole: a short one costs nothing, and a surface for it is
         *  memory and a blit spent to save a few dozen discs. */
        const val MIN_BAKE_POINTS = 48

        /**
         * Pixels along a tile's inner side. Small enough that the tile a run lands on is a cheap
         * upload, large enough that a stroke across the screen is a few dozen blits.
         */
        const val TILE = 128

        /** Pixels of apron each tile carries past its inner square on every side: enough to filter across. */
        const val APRON = 2

        /** A tile surface's side, apron included. */
        const val SIDE = TILE + 2 * APRON

        /** The inner square of a tile surface, which is all a frame blits of it. */
        private val INNER = Rect(APRON.toDouble(), APRON.toDouble(), TILE.toDouble(), TILE.toDouble())

        /** Device pixels a box is grown by for the antialiased edge of what it bounds. */
        private const val EDGE_PX = 2.0

        /** Tiles kept between strokes per grid, so ordinary writing never allocates. */
        private const val MAX_SPARE = 96

        private fun key(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xffffffffL)
    }
}
