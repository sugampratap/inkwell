package com.xnotes.core.model

import com.xnotes.core.FakeRenderer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.FillRule
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The chip on the page (r3_audio AU 68-76): white, a near-black disc, Phosphor-weight glyphs; never red or blue. */
class AudioChipPaintTest {

    /** Records the colours and text runs the chip paints; everything else goes to the shared fake. */
    private class Paints(private val base: FakeRenderer = FakeRenderer()) : Renderer by base {
        val fills = mutableListOf<Rgba>()
        val circles = mutableListOf<Rgba>()
        val rects = mutableListOf<Pair<Rect, Rgba>>()
        val strokes = mutableListOf<Rgba>()
        val runs = mutableListOf<Triple<String, Double, Double>>()

        override fun fillPolygon(points: List<Pt>, color: Rgba, rule: FillRule) { fills += color }
        override fun fillCircle(center: Pt, radius: Double, color: Rgba) { circles += color }
        override fun fillRect(rect: Rect, color: Rgba) { rects += rect to color }
        override fun strokePolygon(points: List<Pt>, pen: Pen) { strokes += pen.color }
        override fun strokePolyline(points: List<Pt>, pen: Pen) { strokes += pen.color }
        override fun strokeEllipse(center: Pt, rx: Double, ry: Double, pen: Pen) { strokes += pen.color }
        override fun drawTextRun(text: String, x: Double, baseline: Double, font: FontSpec, color: Rgba) {
            runs += Triple(text, x, baseline)
        }

        val all: List<Rgba> get() = fills + circles + rects.map { it.second } + strokes
    }

    private val white = Rgba(255, 255, 255)
    private val ink = Rgba(0x22, 0x22, 0x22)
    private val red = Rgba(240, 84, 72)
    private val blue = Rgba(76, 138, 250)

    private fun chip(voice: Boolean) = AudioItem(AudioData(File("a.m4a")), Pt(100.0, 200.0), "Voice 1", 37_000L, voice = voice)

    @Test fun theChipIsWhiteWithANearBlackDisc() {
        for (voice in listOf(true, false)) {
            val p = Paints()
            chip(voice).paint(p)
            assertEquals(white, p.fills.first())
            assertTrue(ink in p.circles)
            assertFalse(red in p.all)
            assertFalse(blue in p.all)
        }
    }

    @Test fun titleAndDurationSitInTheMockupsColumn() {
        val p = Paints()
        chip(voice = true).paint(p)
        assertEquals("Voice 1", p.runs[0].first)
        assertEquals(100.0 + 79.7, p.runs[0].second, 1e-9)
        assertEquals("0:37", p.runs[1].first)
        assertEquals(100.0 + 79.7, p.runs[1].second, 1e-9)
    }

    @Test fun progressIsNearBlackOnAWarmTrack() {
        val p = Paints()
        val item = chip(voice = true)
        item.playing = true
        item.progress = 0.5
        item.paint(p)
        // The pause bars are white rects; the track and its fill come last.
        val (track, trackColour) = p.rects[p.rects.size - 2]
        val (fill, fillColour) = p.rects.last()
        assertEquals(Rgba(0xE3, 0xE0, 0xD8), trackColour)
        assertEquals(ink, fillColour)
        assertEquals(100.0 + 141.7, track.x, 1e-9)
        assertEquals(200.0 + 52.2, track.y, 1e-9)
        assertEquals(track.w / 2.0, fill.w, 1e-9)
    }

    @Test fun aChipAtRestHasNoProgress() {
        val p = Paints()
        chip(voice = false).paint(p)
        assertTrue(p.rects.isEmpty())
    }
}
