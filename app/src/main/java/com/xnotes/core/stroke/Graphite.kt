package com.xnotes.core.stroke

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The pencil's look, as numbers every renderer shares, so graphite reads the same on a page, on the
 * infinite canvas, under the pen and in an export.
 *
 * ### The grain
 *
 * Paper has a tooth: graphite catches on its peaks and skips its valleys. That is [tile], a square
 * of [TILE] texels of tileable noise made once from a fixed [SEED], so every run of the app and
 * every renderer sees the same paper. A texel is one content pixel and the tile repeats across the
 * page (or the world, on the canvas), anchored there rather than to the screen, so the grain holds
 * still under a scroll or a zoom like the paper it stands for. Each value is how much graphite that
 * spot takes, from [FLOOR] (a valley, almost bare) to 1 (a peak).
 *
 * ### The two passes
 *
 * A stroke is laid in two passes of the same grain, each a whole swept-disc ribbon filled once, so
 * a stroke never darkens where it overlaps itself:
 *  - the **outer** pass is the stroke at its full width, at [OUTER_ALPHA];
 *  - the **core** pass runs down the middle at a width that grows with pressure ([coreFraction]),
 *    at [CORE_ALPHA], composited over the first.
 *
 * So a light touch is a pale grey line, and pressing harder widens it a little and fills its middle
 * in, which is how pressure darkens graphite. Neither pass is opaque, so separate strokes build up
 * where they cross rather than going solid at once. Pressure is read back from the ribbon's own
 * width, which the engine already derives from it, so the live stroke, the committed one and the
 * GL mesh all agree without carrying pressure anywhere new.
 */
object Graphite {

    /** Texels along each side of the grain tile; one texel per content pixel. */
    const val TILE = 256

    /** The paper. Fixed for good: changing it changes the look of every pencil stroke ever drawn. */
    const val SEED = 0x51E5

    /** The full-width pass's opacity. */
    const val OUTER_ALPHA = 0.45

    /** The pressed core's opacity, composited over the outer pass. */
    const val CORE_ALPHA = 0.65

    /** Widest the core gets, as a fraction of the stroke's half-width, at full pressure. */
    const val CORE_MAX = 0.75

    /** Eased pressure at which the core starts to show, and at which it reaches [CORE_MAX]. */
    const val CORE_FROM = 0.30
    const val CORE_TO = 0.90

    /** The pressure assumed when the pen's pressure is off: a firm, ordinary hand. */
    const val PRESSURE_OFF = 0.6

    /** What a valley of the paper still takes, so a thin line thins out rather than breaking up. */
    const val FLOOR = 0.22

    // The noise: value noise on a lattice that wraps at the tile, at four cell sizes, fine weighted
    // heaviest, so the tooth reads as a speckle rather than as blotches along the line.
    private val CELLS = intArrayOf(8, 4, 2, 1)
    private val WEIGHTS = doubleArrayOf(0.12, 0.23, 0.30, 0.35)

    /** The normalised noise band mapped onto [FLOOR]..1, in standard deviations from the mean. */
    private const val BAND_LO = -1.6
    private const val BAND_HI = 1.2

    /** The paper, row-major, [TILE] × [TILE] coverage values (0..255 unsigned). Built once. */
    val tile: ByteArray by lazy { tile(TILE, SEED) }

    /** The paper's mean coverage, 0..1: what a renderer with no texture lays the ink at instead. */
    val meanGrain: Double by lazy {
        val t = tile
        var sum = 0L
        for (b in t) sum += b.toInt() and 0xFF
        sum / (255.0 * t.size)
    }

    /**
     * A [size] × [size] grain tile from [seed], row-major, one unsigned byte per texel. Exactly
     * periodic: the lattice of every octave wraps at the tile's edge, so tiles laid side by side
     * meet without a seam. Deterministic: the same arguments give the same bytes, always.
     */
    fun tile(size: Int, seed: Int): ByteArray {
        require(size > 0 && size % CELLS[0] == 0) { "tile size $size must be a multiple of ${CELLS[0]}" }
        val n = size * size
        val v = DoubleArray(n)
        for (k in CELLS.indices) addOctave(v, size, CELLS[k], WEIGHTS[k], seed + k * 977)
        var mean = 0.0
        for (x in v) mean += x
        mean /= n
        var sq = 0.0
        for (x in v) sq += (x - mean) * (x - mean)
        val sd = sqrt(sq / n).coerceAtLeast(1e-9)
        val out = ByteArray(n)
        for (i in 0 until n) {
            val z = (v[i] - mean) / sd
            val t = ((z - BAND_LO) / (BAND_HI - BAND_LO)).coerceIn(0.0, 1.0)
            val s = t * t * (3 - 2 * t)
            val g = FLOOR + (1 - FLOOR) * s
            out[i] = (g * 255.0 + 0.5).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    /** One octave of wrapped value noise with lattice [cell] texels apart, added in at [weight]. */
    private fun addOctave(into: DoubleArray, size: Int, cell: Int, weight: Double, seed: Int) {
        val m = size / cell
        val lattice = DoubleArray(m * m)
        for (j in 0 until m) for (i in 0 until m) lattice[j * m + i] = hash01(i, j, seed)
        for (y in 0 until size) {
            val fy = (y + 0.5) / cell - 0.5
            val y0 = floor(fy).toInt()
            val ty = fy - y0
            val sy = ty * ty * (3 - 2 * ty)
            val r0 = Math.floorMod(y0, m) * m
            val r1 = Math.floorMod(y0 + 1, m) * m
            for (x in 0 until size) {
                val fx = (x + 0.5) / cell - 0.5
                val x0 = floor(fx).toInt()
                val tx = fx - x0
                val sx = tx * tx * (3 - 2 * tx)
                val c0 = Math.floorMod(x0, m)
                val c1 = Math.floorMod(x0 + 1, m)
                val top = lattice[r0 + c0] + (lattice[r0 + c1] - lattice[r0 + c0]) * sx
                val bottom = lattice[r1 + c0] + (lattice[r1 + c1] - lattice[r1 + c0]) * sx
                into[y * size + x] += weight * (top + (bottom - top) * sy)
            }
        }
    }

    /** A well-mixed integer hash of a lattice point, as a value in [0, 1). */
    internal fun hash01(x: Int, y: Int, seed: Int): Double {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216.0
    }

    /**
     * The eased pressure a ribbon point of [halfWidth] was drawn at, read back from the width the
     * engine gave it ([StrokeEngine.halfWidth]): `baseWidth · (m + (1 − m) · p) / 2`. A pen with
     * pressure off has no pressure to read, and lays [PRESSURE_OFF].
     */
    fun pressureAt(halfWidth: Float, baseWidth: Double, pressureEnabled: Boolean, minFactor: Double): Double {
        if (!pressureEnabled || minFactor >= 0.999 || baseWidth <= 0.0) return PRESSURE_OFF
        val f = 2.0 * halfWidth / baseWidth
        return ((f - minFactor) / (1.0 - minFactor)).coerceIn(0.0, 1.0)
    }

    /**
     * The half-width a ribbon was typically drawn at: the median of the first [count] of
     * [halfWidths] that are drawn at all (> 0), the lower middle of an even count, or 0 when none
     * is. A shape snapped from a pencil stroke takes this as its width and [pressureAt] of it as its
     * one pressure, so the line keeps the stroke's shade. Since [pressureAt] only grows with the
     * width, that is the median pressure too. The median, not the mean, so the light lead-in and
     * lift-off and a firm press at the hold do not drag the tone along with them.
     */
    fun typicalHalfWidth(halfWidths: FloatArray, count: Int): Double {
        val n = minOf(count, halfWidths.size)
        var k = 0
        val drawn = FloatArray(n)
        for (i in 0 until n) if (halfWidths[i] > 0f) drawn[k++] = halfWidths[i]
        if (k == 0) return 0.0
        drawn.sort(0, k)
        return drawn[(k - 1) / 2].toDouble()
    }

    /** How much of the half-width the dark core fills at eased [pressure]: 0 for a light touch. */
    fun coreFraction(pressure: Double): Double {
        val t = ((pressure - CORE_FROM) / (CORE_TO - CORE_FROM)).coerceIn(0.0, 1.0)
        return CORE_MAX * t * t * (3 - 2 * t)
    }

    /**
     * The core pass's radii for the first [count] of [halfWidths], written into [into] (which must
     * hold [count]) and returned. Nothing is allocated, so a live stroke can redo it every frame.
     * Returns whether any point has a core at all; a stroke drawn lightly throughout has none and
     * skips the pass.
     */
    fun coreRadii(
        halfWidths: FloatArray,
        count: Int,
        baseWidth: Double,
        pressureEnabled: Boolean,
        minFactor: Double,
        into: FloatArray,
    ): Boolean = coreRadii(halfWidths, 0, count, baseWidth, pressureEnabled, minFactor, into, 0)

    /**
     * [coreRadii] over just the [count] points from [from], point `i` written at `into[i - shift]`
     * (so [into] must hold `from + count - shift`). A live pencil works its core out only for the
     * points it is about to lay — a settled run once, the moving tail per move — so what that costs
     * is the length of the run, never the length of the stroke. Returns whether any of them has a
     * core.
     */
    fun coreRadii(
        halfWidths: FloatArray,
        from: Int,
        count: Int,
        baseWidth: Double,
        pressureEnabled: Boolean,
        minFactor: Double,
        into: FloatArray,
        shift: Int,
    ): Boolean {
        var any = false
        for (i in from until from + count) {
            val h = halfWidths[i]
            val r = if (h > 0f) (h * coreFraction(pressureAt(h, baseWidth, pressureEnabled, minFactor))).toFloat() else 0f
            into[i - shift] = r
            if (r > 0f) any = true
        }
        return any
    }
}

/**
 * A ribbon's core, for the tessellator: the same centreline as [base], each point's half-width
 * swapped for [radii], and the two rails drawn in toward the centre in proportion, so the core
 * keeps the ribbon's own joins. A view: nothing is copied.
 *
 * [radii] begins at point [offset] of [base], so one run of a live ribbon can carry the core radii
 * of its own points alone; only points from [offset] on may be read.
 */
class ScaledRibbon(
    private val base: RibbonPoints,
    private val radii: FloatArray,
    private val offset: Int = 0,
) : RibbonPoints {
    override val pointCount: Int get() = minOf(base.pointCount, offset + radii.size)
    override fun cx(i: Int): Double = base.cx(i)
    override fun cy(i: Int): Double = base.cy(i)
    override fun hw(i: Int): Double = radii[i - offset].toDouble()
    override val hasRails: Boolean get() = base.hasRails

    private fun k(i: Int): Double {
        val h = base.hw(i)
        return if (h > 0.0) radii[i - offset] / h else 0.0
    }

    override fun leftX(i: Int): Double = base.cx(i) + (base.leftX(i) - base.cx(i)) * k(i)
    override fun leftY(i: Int): Double = base.cy(i) + (base.leftY(i) - base.cy(i)) * k(i)
    override fun rightX(i: Int): Double = base.cx(i) + (base.rightX(i) - base.cx(i)) * k(i)
    override fun rightY(i: Int): Double = base.cy(i) + (base.rightY(i) - base.cy(i)) * k(i)
}
