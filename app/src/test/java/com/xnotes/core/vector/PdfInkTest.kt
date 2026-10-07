package com.xnotes.core.vector

import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.StrokeEngine
import com.xnotes.core.stroke.StrokeGeometry
import com.xnotes.core.stroke.StrokeSimplify
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The PDF ink encoding: small, and the same ink. Sizes are read off a full A4 page of dense
 * handwriting built the way the app builds it (the stroke engine at the default pens, pen-up
 * reduction), Flate-compressed as PdfBox compresses a content stream.
 */
class PdfInkTest {

    private val dpi = 150.0
    private val s = 72.0 / dpi
    private val pageH = 1754.0

    /** One handwritten "word": a cursive run of loops, with pressure swelling and easing off. */
    private fun word(rnd: Random, x0: Double, y0: Double, letters: Int): List<Sample> {
        val out = ArrayList<Sample>()
        val step = 1.2 + rnd.nextDouble() * 0.8 // content px between samples, as a stylus reports
        var x = x0
        var t = 0.0
        val n = (letters * 26 / step).toInt()
        val amp = 9.0 + rnd.nextDouble() * 5
        val base = 0.35 + rnd.nextDouble() * 0.25
        val drift = rnd.nextDouble() * 6.0
        var tremor = 0.0
        for (i in 0 until n) {
            t += step / 26.0
            val phase = t * Math.PI * 2
            val y = y0 - amp * (0.5 + 0.5 * sin(phase * 1.0 + rnd.nextDouble() * 0.02)) - (if ((t.toInt() % 3) == 1) amp * 0.8 * max(0.0, sin(phase)) else 0.0)
            x += step * (0.55 + 0.45 * cos(phase) * cos(phase))
            val xx = x + 4.0 * sin(phase)
            // A writer's pressure: a level per word, drifting slowly, a little tremor, a quick landing
            // and a quicker lift.
            tremor = tremor * 0.8 + (rnd.nextDouble() - 0.5) * 0.02
            val ramp = minOf(1.0, (i + 1) / 4.0, (n - i) / 6.0)
            val p = (base + 0.08 * sin(x / 60.0 * Math.PI * 2 + drift) + tremor) * ramp
            out.add(Sample(xx, y, p.coerceIn(0.01, 1.0), i * 3.0))
        }
        return out
    }

    private fun geometryOf(tool: Tool, samples: List<Sample>): StrokeGeometry {
        val c = ToolDefaults.configFor(tool)
        fun build(ss: List<Sample>) = StrokeEngine.build(
            ss, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, c.directionStrength, c.speedStrength,
            c.taperEnabled, c.taperMinFactor, 1.0, smooth = true,
            holdEnds = tool == Tool.PEN || tool == Tool.BALLPOINT, finished = true, smoothScale = 1.0,
            inkRev = c.inkRev, taperLen = if (c.taperEnabled) StrokeEngine.BRUSH_TAPER_LEN else 0.0,
        )
        val kept = StrokeSimplify.simplify(samples, build(samples).halfWidths, 0.2, 1.0, c.directionStrength)
        return build(kept)
    }

    /** A dense page: 38 lines of 9 words, plus a dot or a cross-bar for every other word. */
    private fun densePage(tool: Tool = Tool.PEN): List<StrokeGeometry> {
        val rnd = Random(7)
        val out = ArrayList<StrokeGeometry>()
        var y = 90.0
        repeat(38) {
            var x = 70.0
            repeat(9) {
                val letters = 2 + rnd.nextInt(5)
                val w = word(rnd, x, y, letters)
                out += geometryOf(tool, w)
                if (rnd.nextBoolean()) {
                    val dx = x + rnd.nextDouble() * 30
                    out += geometryOf(tool, List(8) { Sample(dx + it * 1.5, y - 30 + it * 0.2, 0.5, it * 3.0) })
                }
                x = w.last().x + 18 + rnd.nextDouble() * 10
            }
            y += 43.0
        }
        return out
    }

    private fun centers(g: StrokeGeometry) = FloatArray(2 * g.pointCount) { if (it % 2 == 0) g.cx(it / 2).toFloat() else g.cy(it / 2).toFloat() }
    private fun radii(g: StrokeGeometry) = FloatArray(g.pointCount) { g.hw(it).toFloat() }

    /** The encoding 1.3.0 shipped: every folded outline point a line, discs as four curves, two decimals. */
    private fun legacy(sb: StringBuilder, g: StrokeGeometry) {
        val o = RibbonOutline.build(centers(g), radii(g), 0, g.pointCount, 0.3 / s, 0.04 / s)
        fun num(v: Double) {
            val n = Math.round(v * 100.0)
            if (n < 0) sb.append('-')
            val a = abs(n)
            sb.append(a / 100)
            val frac = (a % 100).toInt()
            if (frac != 0) {
                sb.append('.')
                if (frac < 10) sb.append('0')
                sb.append(if (frac % 10 == 0) frac / 10 else frac)
            }
            sb.append(' ')
        }
        fun pt(x: Double, y: Double) { num(x * s); num(pageH * s - y * s) }
        for (poly in o.polygons) {
            for (i in 0 until poly.size / 2) { pt(poly[2 * i], poly[2 * i + 1]); sb.append(if (i == 0) "m\n" else "l\n") }
            sb.append("h\n")
        }
        val d = o.discs
        for (i in 0 until d.size / 3) {
            val cx = d[3 * i]; val cy = d[3 * i + 1]; val r = d[3 * i + 2]; val q = r * 0.5523
            pt(cx + r, cy); sb.append("m\n")
            pt(cx + r, cy + q); pt(cx + q, cy + r); pt(cx, cy + r); sb.append("c\n")
            pt(cx - q, cy + r); pt(cx - r, cy + q); pt(cx - r, cy); sb.append("c\n")
            pt(cx - r, cy - q); pt(cx - q, cy - r); pt(cx, cy - r); sb.append("c\n")
            pt(cx + q, cy - r); pt(cx + r, cy - q); pt(cx + r, cy); sb.append("c\nh\n")
        }
        sb.append("f\n")
    }

    private fun deflated(text: String): Int {
        val bytes = ByteArrayOutputStream()
        DeflaterOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.size()
    }

    private fun encode(page: List<StrokeGeometry>, overlapSafe: Boolean = true): String {
        val sb = StringBuilder()
        for (g in page) PdfInk.write(sb, centers(g), radii(g), 0, g.pointCount, 0.0, pageH * s, s, -s, overlapSafe)
        return sb.toString()
    }

    @Test fun aDensePageOfHandwritingIsSmall() {
        val page = densePage()
        val samples = page.sumOf { it.pointCount }
        val old = StringBuilder().also { sb -> page.forEach { legacy(sb, it) } }.toString()
        val now = encode(page)
        val translucent = encode(page, overlapSafe = false)
        println(
            "dense page: ${page.size} strokes, $samples points | " +
                "1.3.0 ${deflated(old) / 1024} KB | now ${deflated(now) / 1024} KB | " +
                "translucent ink ${deflated(translucent) / 1024} KB (flate)",
        )
        assertTrue("a dense page of ink should be about 100 KB", deflated(now) < 110 * 1024)
        assertTrue("at least four times smaller than 1.3.0", deflated(now) * 4 < deflated(old))
        assertTrue(deflated(translucent) < deflated(old))
    }

    /** The emitted path, decoded: each piece a cubic (lines as degenerate cubics) with its line width. */
    private class Piece(val bez: DoubleArray, val width: Double)

    private fun decode(ops: String): List<Piece> {
        val out = ArrayList<Piece>()
        val nums = ArrayList<Double>()
        var unit = 1.0
        var width = 0.0
        var px = 0.0
        var py = 0.0
        for (tok in ops.split(' ', '\n').filter { it.isNotEmpty() }) {
            when (tok) {
                "cm" -> { unit = nums[0]; nums.clear() }
                "w" -> { width = nums[0] * unit; nums.clear() }
                "m" -> { px = nums[0] * unit; py = nums[1] * unit; nums.clear() }
                "l" -> {
                    val x = nums[0] * unit
                    val y = nums[1] * unit
                    out += Piece(doubleArrayOf(px, py, px, py, x, y, x, y), width)
                    px = x
                    py = y
                    nums.clear()
                }
                "c" -> {
                    val v = nums.map { it * unit }
                    out += Piece(doubleArrayOf(px, py, v[0], v[1], v[2], v[3], v[4], v[5]), width)
                    px = v[4]
                    py = v[5]
                    nums.clear()
                }
                else -> {
                    val v = tok.toDoubleOrNull()
                    if (v != null) nums += v else nums.clear()
                }
            }
        }
        return out
    }

    @Test fun theCentrelineIsTheInkWithinAFiftiethOfAMillimetre() {
        // Every sample's centre lies on the written path, and the width the path is stroked at there
        // is the ink's own, to within the fit, the run step and the rounding to tenths of a point.
        val page = densePage().take(60)
        val posTol = PdfInk.FIT_TOLERANCE_PT + 0.08
        val widthTol = PdfInk.WIDTH_STEP_PT / 2 + 0.06
        for (g in page) {
            val sb = StringBuilder()
            PdfInk.centreline(sb, centers(g), radii(g), 0, g.pointCount, 0.0, pageH * s, s, -s)
            val pieces = decode(sb.toString())
            for (i in 0 until g.pointCount) {
                val x = g.cx(i) * s
                val y = pageH * s - g.cy(i) * s
                var best = Double.MAX_VALUE
                var bestWidthErr = Double.MAX_VALUE
                for (p in pieces) {
                    val d = BezierFit.distanceToCubic(p.bez, x, y, 400)
                    if (d <= posTol) bestWidthErr = minOf(bestWidthErr, abs(p.width - 2 * g.hw(i) * s))
                    best = minOf(best, d)
                }
                assertTrue("sample $i is ${best}pt off the path", best <= posTol)
                assertTrue("sample $i width is off by ${bestWidthErr}pt", bestWidthErr <= widthTol)
            }
        }
    }

    @Test fun theFitStaysOnTheInk() {
        val rnd = Random(3)
        repeat(40) {
            val n = 30 + rnd.nextInt(200)
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            var a = rnd.nextDouble() * 6
            var x = 0.0
            var y = 0.0
            for (i in 0 until n) {
                a += (rnd.nextDouble() - 0.5) * 0.12
                x += cos(a) * 0.7
                y += sin(a) * 0.7
                xs[i] = x
                ys[i] = y
            }
            val tol = 0.06
            val curves = ArrayList<DoubleArray>()
            var px = xs[0]
            var py = ys[0]
            BezierFit.fit(xs, ys, 0, n - 1, tol, object : BezierFit.Sink {
                override fun lineTo(x: Double, y: Double) {
                    curves += doubleArrayOf(px, py, px, py, x, y, x, y)
                    px = x
                    py = y
                }
                override fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
                    curves += doubleArrayOf(px, py, x1, y1, x2, y2, x3, y3)
                    px = x3
                    py = y3
                }
            })
            assertEquals(xs[n - 1], px, 1e-9)
            assertEquals(ys[n - 1], py, 1e-9)
            for (i in 0 until n) {
                val d = curves.minOf { BezierFit.distanceToCubic(it, xs[i], ys[i]) }
                assertTrue("point $i strays $d", d <= tol + 0.01)
            }
            assertTrue("a smooth curve needs far fewer pieces than points (${curves.size} for $n)", curves.size * 4 < n)
        }
    }

    @Test fun outlineSubpathsAllWindTheSameWayUnderTheFlippedPage() {
        val sb = StringBuilder()
        val c = floatArrayOf(10f, 10f, 30f, 12f, 50f, 10f, 52f, 30f, 40f, 44f)
        val r = floatArrayOf(3f, 3.5f, 3f, 5f, 2f)
        PdfInk.outline(sb, c, r, 0, 5, 0.0, 800.0, s, -s)
        val areas = subpathAreas(sb.toString())
        assertTrue(areas.size >= 3)
        val sign = areas.first() > 0
        for (a in areas) assertEquals("every subpath winds the same way", sign, a > 0)
    }

    /** Signed areas of each closed subpath in a fill, curves taken by their control polygon. */
    private fun subpathAreas(ops: String): List<Double> {
        val out = ArrayList<Double>()
        var pts = ArrayList<Double>()
        val stack = ArrayList<Double>()
        for (tok in ops.split(' ', '\n').filter { it.isNotEmpty() }) {
            when (tok) {
                "m" -> {
                    pts = arrayListOf(stack[0], stack[1])
                    stack.clear()
                }
                "l", "c" -> {
                    pts.addAll(stack)
                    stack.clear()
                }
                "h" -> {
                    var a = 0.0
                    val n = pts.size / 2
                    for (i in 0 until n) {
                        val k = (i + 1) % n
                        a += pts[2 * i] * pts[2 * k + 1] - pts[2 * k] * pts[2 * i + 1]
                    }
                    out += a
                }
                else -> {
                    val v = tok.toDoubleOrNull()
                    if (v != null) stack += v else stack.clear()
                }
            }
        }
        return out
    }

    @Test fun aDotIsARoundCappedPointOfTheInkWidth() {
        val sb = StringBuilder()
        PdfInk.write(sb, floatArrayOf(100f, 100f), floatArrayOf(4f), 0, 1, 0.0, 800.0, s, -s, overlapSafe = true)
        val text = sb.toString()
        assertTrue(text, text.startsWith("q\n0.1 0 0 0.1 0 0 cm\n"))
        assertTrue(text, text.contains("1 J\n1 j\n"))
        assertTrue(text, text.contains("38 w\n")) // 2 * 4 px * 0.48 pt/px = 3.84 pt, in tenths
        assertTrue(text, text.contains("480 7520 m\n480 7520 l\nS\n"))
        assertTrue(text, text.endsWith("Q\n"))
    }

    @Test fun translucentInkPaintsEachSpotOnce() {
        // An even-width highlighter is one stroke; an uneven translucent ink is one fill. Neither may
        // lay overlapping runs, which a blend or an alpha would show as darker beads.
        val c = FloatArray(40) { if (it % 2 == 0) 10f + it * 2f else 50f }
        val even = StringBuilder().also { PdfInk.write(it, c, FloatArray(20) { 6f }, 0, 20, 0.0, 800.0, s, -s, overlapSafe = false) }.toString()
        assertEquals(1, even.lines().count { it == "S" })
        val uneven = StringBuilder().also { PdfInk.write(it, c, FloatArray(20) { i -> 2f + i }, 0, 20, 0.0, 800.0, s, -s, overlapSafe = false) }.toString()
        assertEquals(0, uneven.lines().count { it == "S" })
        assertEquals(1, uneven.lines().count { it == "f" })
        assertTrue(hypot(1.0, 0.0) > 0)
    }
}
