package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Document
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.StrokeEngine
import com.xnotes.core.stroke.StrokeSimplify
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Writes a realistic note for measuring exports on a device: a dense handwritten page, a dotted
 * page, a page with a screenshot (an opaque PNG with an alpha channel) and highlights, and a dark
 * page. Only runs when INKWELL_SAMPLE_OUT names the file to write.
 */
class SampleNoteWriter {

    private fun word(rnd: Random, x0: Double, y0: Double, letters: Int): List<Sample> {
        val out = ArrayList<Sample>()
        val step = 1.2 + rnd.nextDouble() * 0.8
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
            val y = y0 - amp * (0.5 + 0.5 * sin(phase)) - (if ((t.toInt() % 3) == 1) amp * 0.8 * max(0.0, sin(phase)) else 0.0)
            x += step * (0.55 + 0.45 * cos(phase) * cos(phase))
            tremor = tremor * 0.8 + (rnd.nextDouble() - 0.5) * 0.02
            val ramp = minOf(1.0, (i + 1) / 4.0, (n - i) / 6.0)
            val p = (base + 0.08 * sin(x / 60.0 * Math.PI * 2 + drift) + tremor) * ramp
            out.add(Sample(x + 4.0 * sin(phase), y, p.coerceIn(0.01, 1.0), i * 3.0))
        }
        return out
    }

    private fun stroke(tool: Tool, samples: List<Sample>, color: Rgba? = null): Stroke {
        var c = ToolDefaults.configFor(tool)
        if (color != null) c = c.copy(rgba = color, colorOverride = color)
        val s = Stroke(tool, c, samples)
        val kept = StrokeSimplify.simplify(s.samples, s.geometry().halfWidths, 0.2, 1.0, c.directionStrength)
        return Stroke(tool, c, kept)
    }

    private fun handwriting(page: Page, rnd: Random, lines: Int, color: Rgba? = null, top: Double = 90.0) {
        var y = top
        repeat(lines) {
            var x = 70.0
            repeat(9) {
                val w = word(rnd, x, y, 2 + rnd.nextInt(5))
                page.items += stroke(Tool.PEN, w, color)
                if (rnd.nextBoolean()) {
                    val dx = x + rnd.nextDouble() * 30
                    page.items += stroke(Tool.PEN, List(8) { Sample(dx + it * 1.5, y - 30 + it * 0.2, 0.5, it * 3.0) }, color)
                }
                x = w.last().x + 18 + rnd.nextDouble() * 10
            }
            y += 43.0
        }
    }

    @Test fun writeSampleNote() {
        val outPath = System.getenv("INKWELL_SAMPLE_OUT")
        assumeTrue(outPath != null)
        val rnd = Random(11)
        val doc = Document()
        val (w, h) = 1240.0 to 1754.0
        doc.pages += Page(w, h).also { handwriting(it, rnd, 38) }
        doc.pages += Page(w, h, style = PageStyle(template = "dots")).also { handwriting(it, rnd, 14, top = 300.0) }
        doc.pages += Page(w, h).also { p ->
            // INKWELL_SAMPLE_PNG / _JPG: "path|width|height" of a screenshot and a camera photo.
            val (shot, sw, sh) = System.getenv("INKWELL_SAMPLE_PNG").split('|')
            p.items += ImageItem(ImageData(File(shot), sw.toInt(), sh.toInt()), Rect(70.0, 120.0, 1100.0, 687.5))
            System.getenv("INKWELL_SAMPLE_CUTOUT")?.split('|')?.let { (cut, cw, ch) ->
                p.items += ImageItem(ImageData(File(cut), cw.toInt(), ch.toInt()), Rect(900.0, 1350.0, 300.0, 300.0))
            }
            handwriting(p, rnd, 12, top = 900.0)
            repeat(6) { i ->
                val y = 905.0 + i * 86
                p.items += stroke(Tool.HIGHLIGHTER, List(60) { Sample(80.0 + it * 6, y + sin(it * 0.2), 0.6, it * 8.0) })
            }
        }
        doc.pages += Page(w, h).also { p ->
            val (photo, pw, ph) = System.getenv("INKWELL_SAMPLE_JPG").split('|')
            p.items += ImageItem(ImageData(File(photo), pw.toInt(), ph.toInt()), Rect(120.0, 150.0, 1000.0, 750.0))
            handwriting(p, rnd, 10, top = 1000.0)
        }
        doc.pages += Page(w, h, style = PageStyle(pageColor = Rgba(18, 18, 18, 255))).also { handwriting(it, rnd, 20, Rgba(235, 235, 235, 255)) }
        File(outPath).outputStream().use { DocumentCodec(FakeImageCodec(), FakeTextMeasurer()).write(doc, it) }
        println("sample note: ${File(outPath).length() / 1024} KB, inkRev ${StrokeEngine.INK_REV_CURRENT}")
    }
}
