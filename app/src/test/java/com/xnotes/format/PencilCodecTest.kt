package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.infinite.InfiniteDocument
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The pencil in both file formats: it reloads as graphite, and nothing older changes. */
class PencilCodecTest {

    private val notes = DocumentCodec(FakeImageCodec(), FakeTextMeasurer())
    private val canvases = CanvasCodec(FakeImageCodec())

    private val samples = listOf(Sample(1.0, 2.0, 0.3), Sample(6.0, 4.0, 0.8), Sample(12.0, 9.0, 1.0))
    private val ink = Rgba(31, 42, 68, 255)

    private fun pencil() = Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL).copy(rgba = ink), samples)
    private fun quill() = Stroke(Tool.SPEED, ToolDefaults.configFor(Tool.SPEED).copy(rgba = ink), samples.mapIndexed { i, s -> s.copy(t = i * 8.0) })
    private fun pen() = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN).copy(rgba = ink), samples)

    private fun noteOf(vararg strokes: Stroke): Document {
        val doc = Document(dpi = 150)
        val page = Page(400.0, 400.0)
        page.items.addAll(strokes)
        doc.pages.add(page)
        return doc
    }

    private fun noteBytes(doc: Document) = ByteArrayOutputStream().also { notes.write(doc, it) }.toByteArray()
    private fun noteRoundTrip(doc: Document) = notes.read(ByteArrayInputStream(noteBytes(doc))).pages[0].items.map { it as Stroke }

    private fun canvasOf(vararg strokes: Stroke) = InfiniteDocument(dpi = 150).also { d -> strokes.forEach { d.add(it) } }
    private fun canvasBytes(doc: InfiniteDocument) = ByteArrayOutputStream().also { canvases.write(doc, it) }.toByteArray()
    private fun canvasRoundTrip(doc: InfiniteDocument) = canvases.read(ByteArrayInputStream(canvasBytes(doc))).items.map { it as Stroke }

    private fun manifest(bundle: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return zis.readBytes().decodeToString()
                e = zis.nextEntry
            }
        }
        return ""
    }

    private fun bundle(manifest: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("manifest.json"))
            it.write(manifest.toByteArray())
            it.closeEntry()
        }
        return out.toByteArray()
    }

    private fun assertSameStroke(want: Stroke, got: Stroke) {
        assertEquals(want.tool, got.tool)
        assertEquals(want.config.grain, got.config.grain)
        assertEquals(want.config.baseWidth, got.config.baseWidth, 1e-9)
        assertEquals(want.config.pressureMinFactor, got.config.pressureMinFactor, 1e-9)
        assertEquals(want.config.speedStrength, got.config.speedStrength, 1e-9)
        assertEquals(want.config.rgba, got.config.rgba)
        assertEquals(want.sampleCount, got.sampleCount)
        for (i in 0 until want.sampleCount) {
            assertEquals(want.xAt(i), got.xAt(i), 0.01)
            assertEquals(want.pAt(i), got.pAt(i), 0.001)
        }
    }

    @Test fun aPencilStrokeReloadsAsGraphiteInANote() {
        val back = noteRoundTrip(noteOf(pencil(), quill(), pen()))
        assertSameStroke(pencil(), back[0])
        assertTrue(back[0].config.grain)
        assertSameStroke(quill(), back[1])
        assertSameStroke(pen(), back[2])
    }

    @Test fun aPencilStrokeReloadsAsGraphiteOnACanvas() {
        val back = canvasRoundTrip(canvasOf(pencil(), quill(), pen()))
        assertSameStroke(pencil(), back[0])
        assertTrue(back[0].config.grain)
        assertSameStroke(quill(), back[1])
        assertSameStroke(pen(), back[2])
    }

    @Test fun thePencilRecordsItsToolAndItsGrainAndNothingElseDoes() {
        for (text in listOf(manifest(noteBytes(noteOf(pencil()))), manifest(canvasBytes(canvasOf(pencil()))))) {
            assertTrue(text, text.contains("\"tool\":\"pencil\""))
            assertTrue(text, text.contains("\"grain\":true"))
        }
        for (text in listOf(manifest(noteBytes(noteOf(pen(), quill()))), manifest(canvasBytes(canvasOf(pen(), quill()))))) {
            assertFalse(text, text.contains("grain"))
            assertTrue(text, text.contains("\"tool\":\"speed\""))
        }
    }

    @Test fun anOlderNoteLoadsExactlyAsItDid() {
        val legacy = "{\"format\":\"xnote\",\"pages\":[{\"width\":100,\"height\":100,\"items\":[" +
            "{\"kind\":\"stroke\",\"tool\":\"speed\",\"config\":{\"base_width\":4.0,\"speed_strength\":0.8}," +
            "\"samples\":[[1,2,1.0,0],[5,6,0.5,8]],\"speed_scale\":1.5}," +
            "{\"kind\":\"stroke\",\"tool\":\"pen\",\"samples\":[[1,2,1.0]]}]}]}"
        val items = notes.read(ByteArrayInputStream(bundle(legacy))).pages[0].items.map { it as Stroke }
        assertEquals(Tool.SPEED, items[0].tool)
        assertEquals(0.8, items[0].config.speedStrength, 1e-9)
        assertEquals(8.0, items[0].tAt(1), 1e-9)
        assertFalse(items[0].config.grain)
        assertEquals(Tool.PEN, items[1].tool)
        assertFalse(items[1].config.grain)
    }

    @Test fun aPencilStrokeMissingTheFlagIsStillGraphiteAndAnUnknownToolStaysInk() {
        val written = "{\"format\":\"xnote\",\"pages\":[{\"width\":100,\"height\":100,\"items\":[" +
            "{\"kind\":\"stroke\",\"tool\":\"pencil\",\"config\":{\"base_width\":2.0},\"samples\":[[1,2,1.0]]}," +
            "{\"kind\":\"stroke\",\"tool\":\"crayon\",\"config\":{\"base_width\":2.0},\"samples\":[[1,2,1.0]]}]}]}"
        val items = notes.read(ByteArrayInputStream(bundle(written))).pages[0].items.map { it as Stroke }
        assertEquals(Tool.PENCIL, items[0].tool)
        assertTrue(items[0].config.grain)
        assertEquals(Tool.PEN, items[1].tool)
        assertFalse(items[1].config.grain)
    }

    @Test fun aCopiedOrSplitPencilStrokeStaysGraphite() {
        val copy = Stroke(pencil())
        assertTrue(copy.config.grain && copy.tool == Tool.PENCIL)
        assertEquals(ToolConfig().grain, false)
    }
}
