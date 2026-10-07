package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.infinite.InfiniteDocument
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TapePattern
import com.xnotes.core.tools.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Tape in both bundle formats: it round-trips with its look and its revealed state, and is additive. */
class TapeCodecTest {

    private val noteCodec = DocumentCodec(FakeImageCodec(), FakeTextMeasurer())
    private val canvasCodec = CanvasCodec(FakeImageCodec())

    private fun covered() = TapeItem(Pt(10.0, 20.0), Pt(210.0, 20.0), 36.0, Rgba(240, 160, 182), TapePattern.DOTS, seed = 1234)

    private fun peeled() = TapeItem(Pt(50.0, 300.0), Pt(120.0, 420.0), 24.0, Rgba(150, 214, 180), TapePattern.GRID, revealed = true, seed = 99)
        .also { it.locked = true }

    private fun assertSameTape(want: TapeItem, got: TapeItem) {
        assertEquals(want.start, got.start)
        assertEquals(want.end, got.end)
        assertEquals(want.width, got.width, 1e-9)
        assertEquals(want.color, got.color)
        assertEquals(want.pattern, got.pattern)
        assertEquals(want.revealed, got.revealed)
        assertEquals(want.seed, got.seed)
        assertEquals(want.locked, got.locked)
    }

    private fun manifestOf(bundle: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return zis.readBytes().toString(Charsets.UTF_8)
                e = zis.nextEntry
            }
        }
        throw AssertionError("no manifest")
    }

    @Test fun tapeRoundTripsInANote() {
        val doc = Document()
        val page = Page(1240.0, 1754.0)
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(10.0, 10.0), Rgba(0, 0, 0), 2.0)
        page.items.addAll(listOf(covered(), shape, peeled()))
        doc.pages.add(page)
        val bytes = ByteArrayOutputStream().also { noteCodec.write(doc, it) }.toByteArray()
        val back = noteCodec.read(ByteArrayInputStream(bytes), imageDir = Files.createTempDirectory("tape").toFile())
        val items = back.pages.single().items
        assertEquals(3, items.size)
        assertSameTape(covered(), items[0] as TapeItem)
        assertTrue(items[1] is ShapeItem) // order kept
        assertSameTape(peeled(), items[2] as TapeItem)
    }

    @Test fun tapeRoundTripsOnACanvas() {
        val doc = InfiniteDocument(dpi = 150)
        doc.addAll(listOf(covered(), peeled()))
        val bytes = ByteArrayOutputStream().also { canvasCodec.write(doc, it) }.toByteArray()
        val back = canvasCodec.read(ByteArrayInputStream(bytes), imageDir = Files.createTempDirectory("tape").toFile())
        assertEquals(2, back.items.size)
        assertSameTape(covered(), back.items[0] as TapeItem)
        assertSameTape(peeled(), back.items[1] as TapeItem)
    }

    @Test fun onlyAPeeledStripWritesItsState() {
        val doc = InfiniteDocument(dpi = 150)
        doc.addAll(listOf(covered()))
        val text = manifestOf(ByteArrayOutputStream().also { canvasCodec.write(doc, it) }.toByteArray())
        assertTrue(text.contains("\"kind\":\"tape\""))
        assertFalse(text.contains("revealed"))
        assertFalse(text.contains("locked"))
    }

    /** A strip from a file with only the essentials still loads, with the roll's defaults filled in. */
    @Test fun aSparseStripLoadsWithDefaults() {
        val manifest = """{"format":"xcanvas","version":1,"items":[{"kind":"tape","start":[0,0],"end":[100,0]},""" +
            """{"kind":"tape","end":[1,1]}]}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json").apply { method = ZipEntry.DEFLATED })
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val back = canvasCodec.read(ByteArrayInputStream(out.toByteArray()))
        // The one without a start is dropped; the other gets the default width, print and state.
        assertEquals(1, back.items.size)
        val t = back.items.single() as TapeItem
        assertEquals(TapeItem.DEFAULT_WIDTH, t.width, 0.0)
        assertEquals(TapePattern.STRIPES, t.pattern)
        assertFalse(t.revealed)
    }
}
