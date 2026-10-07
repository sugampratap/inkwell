package com.xnotes.gl

import com.xnotes.core.infinite.InkPass
import com.xnotes.core.infinite.ItemMesher
import com.xnotes.core.infinite.MeshData
import com.xnotes.core.infinite.MeshPart
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.max
import kotlin.math.sin

/**
 * The live pencil on the front buffer: that the stencilled passes [GlWetPadInk] lays reach exactly
 * what the committed stroke shows, however the runs overlap and in whatever order they come, and
 * that the pad takes the pencil only where it can.
 *
 * The pad is modelled per sample, the way GL runs it: each fragment is tested against the stencil
 * with the very func, ref and mask [WetPadGraphite] hands `glStencilFunc`, writes what its op
 * writes, and blends its premultiplied colour source-over. The committed render is modelled as the
 * canvas makes it: each stroke's outer filled once at `o·g`, its core once over it at `p·g`, and
 * stroke after stroke composited over the last.
 */
class WetPadGraphiteTest {

    /** The paper at a sample: one texel of the real grain tile per sample index. */
    private fun grain(px: Int): Double = (Graphite.tile[px % Graphite.tile.size].toInt() and 0xFF) / 255.0

    /** One piece the pad draws: a pen's opaque triangles, or a pencil's outer or core. */
    private class Piece(
        val stroke: Int,
        val role: Int,
        /** Samples the triangles reach, once per triangle that does: repeats are overlaps. */
        val samples: IntArray,
        val rgb: DoubleArray,
        val alpha: Double = 1.0,
        val under: Double = 0.0,
    )

    /** The pad's scratch, per sample: premultiplied colour, alpha and the stencil byte. */
    private class Scratch(n: Int) {
        val c = Array(n) { DoubleArray(3) }
        val a = DoubleArray(n)
        val stencil = IntArray(n)

        fun blend(px: Int, rgb: DoubleArray, alpha: Double) {
            for (k in 0..2) c[px][k] = rgb[k] * alpha + c[px][k] * (1 - alpha)
            a[px] = alpha + a[px] * (1 - alpha)
        }

        fun blendMax(px: Int, rgb: DoubleArray, alpha: Double) {
            for (k in 0..2) c[px][k] = max(c[px][k], rgb[k] * alpha)
            a[px] = max(a[px], alpha)
        }
    }

    /** One present as [GlWetPadInk] draws it, the stencil ids handed out stroke by stroke. */
    private fun present(n: Int, pieces: List<Piece>, stencilled: Boolean = true): Scratch {
        val pad = Scratch(n)
        var id = 0
        var last = Int.MIN_VALUE
        fun draw(p: Piece, func: Int, ref: Int, mask: Int, op: Int, a: Double, b: Double) {
            for (px in p.samples) {
                if (!WetPadGraphite.passes(func, ref, mask, pad.stencil[px])) continue
                pad.blend(px, p.rgb, WetPadGraphite.alpha(a, b, grain(px)))
                pad.stencil[px] = WetPadGraphite.written(op, ref, pad.stencil[px])
            }
        }
        for (p in pieces) {
            if (p.role == WetPadGraphite.NONE) {
                for (px in p.samples) pad.blend(px, p.rgb, 1.0)
                continue
            }
            if (p.stroke != last) {
                last = p.stroke
                if (WetPadGraphite.clearsBefore(id)) pad.stencil.fill(0)
                id = WetPadGraphite.nextId(id)
            }
            if (!stencilled) {
                val (a, b) = if (p.role == WetPadGraphite.OUTER) p.alpha to 0.0 else p.under to p.alpha
                for (px in p.samples) pad.blendMax(px, p.rgb, WetPadGraphite.alpha(a, b, grain(px)))
                continue
            }
            if (p.role == WetPadGraphite.OUTER) {
                draw(
                    p, WetPadGraphite.OUTER_FUNC, WetPadGraphite.outerRef(id), WetPadGraphite.OUTER_MASK,
                    WetPadGraphite.OUTER_OP, p.alpha, 0.0,
                )
            } else {
                draw(
                    p, WetPadGraphite.CORE_OVER_FUNC, WetPadGraphite.coreOverRef(id), WetPadGraphite.CORE_OVER_MASK,
                    WetPadGraphite.CORE_OVER_OP, p.alpha, 0.0,
                )
                draw(
                    p, WetPadGraphite.CORE_FRESH_FUNC, WetPadGraphite.coreFreshRef(id), WetPadGraphite.CORE_FRESH_MASK,
                    WetPadGraphite.CORE_FRESH_OP, p.under, p.alpha,
                )
            }
        }
        return pad
    }

    /** The committed render of the same strokes onto a page of colour [bg]: a premultiplied result. */
    private fun committed(n: Int, pieces: List<Piece>, bg: DoubleArray? = null): Scratch {
        val page = Scratch(n)
        if (bg != null) for (px in 0 until n) page.blend(px, bg, 1.0)
        for (stroke in pieces.map { it.stroke }.distinct()) {
            val mine = pieces.filter { it.stroke == stroke }
            val outer = BooleanArray(n)
            val core = BooleanArray(n)
            val pen = BooleanArray(n)
            for (p in mine) for (px in p.samples) when (p.role) {
                WetPadGraphite.OUTER -> outer[px] = true
                WetPadGraphite.CORE -> core[px] = true
                else -> pen[px] = true
            }
            val first = mine.first()
            val o = mine.firstOrNull { it.role == WetPadGraphite.OUTER }?.alpha ?: first.under
            val p = mine.firstOrNull { it.role == WetPadGraphite.CORE }?.alpha ?: 0.0
            for (px in 0 until n) {
                val a = if (first.role == WetPadGraphite.NONE) {
                    if (pen[px]) 1.0 else 0.0
                } else {
                    WetPadGraphite.committed(o, p, grain(px), outer[px], core[px])
                }
                if (a > 0) page.blend(px, first.rgb, a)
            }
        }
        return page
    }

    private fun assertSame(want: Scratch, got: Scratch, what: String) {
        for (px in want.a.indices) {
            assertEquals("$what: alpha at $px", want.a[px], got.a[px], 1e-12)
            for (k in 0..2) assertEquals("$what: colour $k at $px", want.c[px][k], got.c[px][k], 1e-12)
        }
    }

    /**
     * A pencil stroke as the pad is handed it: [runs] runs, each an outer and a core whose samples
     * overlap the run before and after it and themselves, the core inside the outer.
     */
    private fun pencilStroke(stroke: Int, at: Int, runs: Int, rnd: Random, rgb: DoubleArray, o: Double, p: Double): List<Piece> {
        val out = ArrayList<Piece>()
        for (r in 0 until runs) {
            val start = at + r * 6
            // The core is the same centreline at a narrower width, so every sample it reaches its
            // own run's outer reaches too.
            val core = IntArray(18) { start + 3 + rnd.nextInt(8) }
            val outer = (core.toList() + List(30) { start + rnd.nextInt(14) }).shuffled(rnd).toIntArray()
            out += Piece(stroke, WetPadGraphite.OUTER, outer, rgb, o)
            out += Piece(stroke, WetPadGraphite.CORE, core, rgb, p, under = o)
        }
        return out
    }

    private val graphite = doubleArrayOf(0.20, 0.22, 0.25)
    private val o = 115 / 255.0
    private val p = 166 / 255.0

    @Test fun oneStrokeLaysExactlyTheCommittedPencilWhateverTheOrderOfItsPieces() {
        val rnd = Random(3)
        val pieces = pencilStroke(1, 0, 12, rnd, graphite, o, p)
        val want = committed(120, pieces)
        assertSame(want, present(120, pieces), "in order")
        repeat(20) {
            val shuffled = pieces.shuffled(rnd)
            assertSame(want, present(120, shuffled), "shuffled")
        }
        // Drawn twice over, as the tail and a guess overlapping it are: still laid once.
        assertSame(want, present(120, pieces + pieces.shuffled(rnd)), "doubled")
        assertTrue("nothing inked", want.a.any { it > 0.5 })
    }

    @Test fun aStrokeNeverDarkensWhereItsRunsOverlapItself() {
        val rnd = Random(9)
        val pieces = pencilStroke(1, 0, 8, rnd, graphite, o, p)
        val pad = present(80, pieces)
        for (px in 0 until 80) {
            val g = grain(px)
            // Nothing above the core-over-outer value anywhere, however many runs reached the spot.
            assertTrue(pad.a[px] <= WetPadGraphite.alpha(o, p, g) + 1e-12)
        }
    }

    @Test fun pencilOverPencilBuildsUpAsItDoesOnThePage() {
        val rnd = Random(5)
        // Two strokes of one colour crossing the same samples: the second composites over the first.
        val first = pencilStroke(1, 0, 6, rnd, graphite, o, p)
        val second = pencilStroke(2, 10, 6, rnd, graphite, o, p)
        val pieces = first + second
        val want = committed(80, pieces)
        val got = present(80, pieces)
        assertSame(want, got, "two strokes")
        // And it really is a build-up: somewhere both reach is darker than either alone.
        val alone = present(80, first)
        assertTrue((10 until 36).any { got.a[it] > alone.a[it] + 0.05 && alone.a[it] > 0.1 })
    }

    @Test fun pensAndPencilsOfAnyColourMixOnOneRunOfThePad() {
        val rnd = Random(11)
        val red = doubleArrayOf(0.9, 0.1, 0.1)
        val blue = doubleArrayOf(0.1, 0.2, 0.9)
        val pieces = ArrayList<Piece>()
        pieces += Piece(1, WetPadGraphite.NONE, IntArray(40) { rnd.nextInt(60) }, doubleArrayOf(0.0, 0.0, 0.0))
        pieces += pencilStroke(2, 5, 7, rnd, red, o, p)
        pieces += Piece(3, WetPadGraphite.NONE, IntArray(20) { 20 + rnd.nextInt(30) }, doubleArrayOf(0.0, 0.5, 0.0))
        pieces += pencilStroke(4, 0, 9, rnd, blue, 0.3, 0.5)
        pieces += pencilStroke(5, 2, 9, rnd, red, o, p)
        val n = 80
        assertSame(committed(n, pieces), present(n, pieces), "mixed")

        // Over the page: the pad is a premultiplied layer, and source-over is associative, so the
        // pad composited onto the canvas is the committed strokes drawn onto it.
        val bg = doubleArrayOf(0.97, 0.95, 0.90)
        val pad = present(n, pieces)
        val onPage = Scratch(n)
        for (px in 0 until n) {
            onPage.blend(px, bg, 1.0)
            for (k in 0..2) onPage.c[px][k] = pad.c[px][k] + onPage.c[px][k] * (1 - pad.a[px])
            onPage.a[px] = pad.a[px] + onPage.a[px] * (1 - pad.a[px])
        }
        assertSame(committed(n, pieces, bg), onPage, "over the page")
    }

    @Test fun idsRunOutAndStartAgainWithoutConfusingOneStrokeForAnother() {
        val rnd = Random(17)
        val pieces = ArrayList<Piece>()
        // Far more strokes than a byte of stencil can name, all over the same few samples.
        for (s in 1..300) pieces += pencilStroke(s, rnd.nextInt(10), 2, rnd, graphite, 0.05, 0.08)
        assertSame(committed(40, pieces), present(40, pieces), "300 strokes")
    }

    @Test fun theCombinedValueIsTheCoreCompositedOverTheOuter() {
        for (gi in 0..20) {
            val g = gi / 20.0
            for ((oo, pp) in listOf(o to p, 0.2 to 0.4, 1.0 to 1.0, 0.0 to 0.65)) {
                val over = pp * g + oo * g * (1 - pp * g)
                assertEquals(over, WetPadGraphite.alpha(oo, pp, g), 1e-15)
                assertEquals(over, WetPadGraphite.committed(oo, pp, g, outer = true, core = true), 1e-15)
                // The core over its outer, as the pad's second draw lays it, reaches the same.
                val outerOnly = WetPadGraphite.alpha(oo, 0.0, g)
                val coreOver = WetPadGraphite.alpha(pp, 0.0, g)
                assertEquals(over, coreOver + outerOnly * (1 - coreOver), 1e-15)
                assertEquals(oo * g, WetPadGraphite.committed(oo, pp, g, outer = true, core = false), 1e-15)
            }
        }
    }

    @Test fun theMaxBlendFallbackIsIdempotentAndExactWithinOneStroke() {
        // max(C·a1, C·a2) = C·max(a1, a2) for C >= 0: laying a fragment again changes nothing.
        val rnd = Random(23)
        repeat(1000) {
            val c = rnd.nextDouble()
            val a1 = rnd.nextDouble()
            val a2 = rnd.nextDouble()
            assertEquals(c * max(a1, a2), max(c * a1, c * a2), 1e-15)
        }
        // So one stroke on the transparent scratch lands on the committed look in any order, the
        // combined core being above the outer wherever both reach.
        val pieces = pencilStroke(1, 0, 10, rnd, graphite, o, p)
        val want = committed(100, pieces)
        assertSame(want, present(100, pieces, stencilled = false), "max")
        assertSame(want, present(100, pieces.shuffled(rnd) + pieces, stencilled = false), "max, shuffled and doubled")
    }

    // --- what the mesher hands the pad, and which ink the pad takes ---

    private fun pencil(): Stroke {
        val s = Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL))
        s.finished = false
        for (i in 0 until 120) s.addSample(Sample(50.0 + i * 2.0, 90.0 + sin(i * 0.1) * 20.0, 0.85, i * 5.0))
        return s
    }

    @Test fun aPencilRunIsAnOuterAndACoreThatKnowsItsOuter() {
        val stroke = pencil()
        val ribbon = stroke.wetRibbon!!
        val run = ItemMesher.meshGraphiteRun(stroke, ribbon, 0, ribbon.pointCount)
        assertEquals(2, run.size)
        val (outer, core) = run
        assertEquals(WetPadGraphite.OUTER, WetPadGraphite.roleOf(outer.pass, outer.under))
        assertEquals(WetPadGraphite.CORE, WetPadGraphite.roleOf(core.pass, core.under))
        assertEquals(outer.color.a / 255.0, core.under, 0.0)
        val color = stroke.renderColor
        assertEquals(color.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0, outer.color.a / 255.0, 0.0)
        assertEquals(color.scaleAlpha(Graphite.CORE_ALPHA).a / 255.0, core.color.a / 255.0, 0.0)
        // The committed mesh's core knows its outer the same way, for the pad and the scene alike.
        val whole = ItemMesher.mesh(stroke)!!.parts
        assertEquals(whole[0].color.a / 255.0, whole[1].under, 0.0)
        // A pen is never a pencil's half.
        assertEquals(WetPadGraphite.NONE, WetPadGraphite.roleOf(InkPass.OPAQUE, -1.0))
    }

    @Test fun thePadTakesPensAndThePencilButNothingThatBlendsWithThePage() {
        assertTrue(WetPadRoute.takes(InkPass.OPAQUE, graphite = false))
        assertTrue(WetPadRoute.takes(InkPass.GRAPHITE, graphite = true))
        assertFalse("no grain shader, no pencil", WetPadRoute.takes(InkPass.GRAPHITE, graphite = false))
        for (pass in listOf(InkPass.TRANSLUCENT, InkPass.MULTIPLY, InkPass.SCREEN, InkPass.GLOW, InkPass.EVEN_ODD)) {
            assertFalse(pass.name, WetPadRoute.takes(pass, graphite = true))
        }
        val part = { pass: InkPass -> MeshPart(MeshData(DoubleArray(0), DoubleArray(0), IntArray(0)), Rgba(0, 0, 0, 255), pass) }
        assertTrue(WetPadRoute.takesAll(listOf(part(InkPass.GRAPHITE), part(InkPass.GRAPHITE)), graphite = true))
        assertFalse(WetPadRoute.takesAll(listOf(part(InkPass.GRAPHITE), part(InkPass.MULTIPLY)), graphite = true))
        assertFalse("nothing to draw", WetPadRoute.takesAll(emptyList(), graphite = true))
    }

    @Test fun thePagedPencilTakesThePadOnlyOnAnUprightView() {
        val pass = ItemMesher.passFor(pencil())
        assertEquals(InkPass.GRAPHITE, pass)
        assertTrue(WetPadRoute.paged(pass, rotationDeg = 0, graphite = true))
        for (turn in listOf(90, 180, 270)) assertFalse("rotated $turn", WetPadRoute.paged(pass, turn, graphite = true))
        // The highlighter multiplies onto the page, so it never does.
        val hl = Stroke(Tool.HIGHLIGHTER, ToolDefaults.configFor(Tool.HIGHLIGHTER))
        assertFalse(WetPadRoute.paged(ItemMesher.passFor(hl), 0, graphite = true))
        // The pencil is fed in runs even though it is not wet-cacheable ink.
        val stroke = pencil()
        assertFalse(stroke.wetCacheable)
        assertTrue(WetPadRoute.inRuns(stroke.wetCacheable, stroke.config.grain))
        assertFalse(WetPadRoute.inRuns(wetCacheable = false, grain = false))
    }

    @Test fun aJoiningStrokeIsFiledApartFromTheOneItJoins() {
        val ink = GlWetPadInk()
        assertTrue(ink.begin(0.0, 0.0, 1.0, 800, 600))
        assertEquals(0, ink.strokeInPlay)
        ink.end()
        assertTrue(ink.extend(0.0, 0.0, 1.0, 800, 600))
        // The last stroke's tail goes down as a run first, still its own; then the joiner's.
        assertEquals(0, ink.strokeInPlay)
        ink.nextStroke()
        assertEquals(1, ink.strokeInPlay)
        ink.end()
        // A fresh session starts counting again.
        assertTrue(ink.begin(0.0, 0.0, 1.0, 800, 600))
        assertEquals(0, ink.strokeInPlay)
    }
}
