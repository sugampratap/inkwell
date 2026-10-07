package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.StickyColors
import com.xnotes.core.model.TableGrid
import com.xnotes.core.model.TableItem
import com.xnotes.core.model.TextItem
import com.xnotes.core.pal.FontFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The sticky note's card colour and the placed table round-trip through the note file, and both
 * are additive: a plain text box writes exactly what it always did, and a table a reader cannot
 * make sense of is skipped rather than breaking the page.
 */
class StickyTableCodecTest {

    private val m = FakeTextMeasurer()
    private val codec = DocumentCodec(FakeImageCodec(), m)

    private fun bundle(doc: Document): ByteArray = ByteArrayOutputStream().also { codec.write(doc, it) }.toByteArray()

    private fun read(bytes: ByteArray): Document =
        codec.read(ByteArrayInputStream(bytes), imageDir = Files.createTempDirectory("xnotes-img").toFile())

    private fun manifestOf(bundle: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return zis.readBytes().decodeToString()
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        return ""
    }

    private fun docWith(vararg items: com.xnotes.core.model.CanvasItem): Document {
        val doc = Document(dpi = 150)
        val page = Page(1240.0, 1754.0)
        page.items.addAll(items)
        doc.pages.add(page)
        return doc
    }

    @Test fun aStickyNoteKeepsItsCardColour() {
        val note = TextItem(Pt(10.0, 20.0), 330.0, 330.0, "buy milk", StickyColors.TEXT, 14.0, FontFace.SANS, m, fill = StickyColors.GREEN)
        val back = read(bundle(docWith(note))).pages[0].items.single() as TextItem
        assertEquals(StickyColors.GREEN, back.fill)
        assertTrue(back.isSticky)
        assertEquals("buy milk", back.text)
        assertEquals(330.0, back.height, 1e-9)
        assertEquals(FontFace.SANS, back.face)
    }

    @Test fun aPlainTextBoxWritesNoFill() {
        val box = TextItem(Pt(10.0, 20.0), width = 250.0, text = "plain", measurer = m)
        val bytes = bundle(docWith(box))
        assertFalse(manifestOf(bytes).contains("fill_rgba"))
        assertNull((read(bytes).pages[0].items.single() as TextItem).fill)
    }

    @Test fun aTableRoundTripsWholeGrid() {
        val g = TableGrid.empty(2, 3, 300.0, 11.0)
            .withCellText(0, 0, "Name")
            .withCellText(1, 2, "line one\nline two")
            .withCellFill(1, 1, Rgba(255, 205, 216, 120))
            .withColumnWidth(2, 140.0)
            .copy(rowHeights = listOf(0.0, 90.0), textColor = Rgba(236, 236, 236), borderColor = Rgba(236, 236, 236, 120), header = true, headerFill = Rgba(236, 236, 236, 24))
        val t = TableItem(Pt(40.0, 60.0), g, m).apply { locked = true }
        val back = read(bundle(docWith(t))).pages[0].items.single() as TableItem
        assertEquals(Pt(40.0, 60.0), back.pos)
        assertEquals(g, back.grid)
        assertTrue(back.locked)
    }

    @Test fun aTableWithoutHeaderOrShadingStaysThatWay() {
        val g = TableGrid.empty(1, 1, 100.0).copy(header = false, headerFill = null)
        val back = read(bundle(docWith(TableItem(Pt.ZERO, g, m)))).pages[0].items.single() as TableItem
        assertFalse(back.grid.header)
        assertNull(back.grid.headerFill)
        assertEquals(g, back.grid)
    }

    /** A hand-written manifest: a ragged table is squared up, a table with no columns is skipped. */
    @Test fun raggedTablesAreSquaredAndEmptyOnesSkipped() {
        val manifest = """
            {"format":"xnote","version":1,"dpi":150,"has_pdf":false,"bookmarks":[],
             "pages":[{"width":500,"height":500,"pdf_page":null,"items":[
               {"kind":"table","pos":[1,2],"table":{"cols":[100,0],"rows":[
                  {"cells":[{"text":"a"},{"text":"b"},{"text":"extra"}]},
                  {"height":40,"cells":[{"text":"c"}]}
               ],"header":false}},
               {"kind":"table","pos":[1,2],"table":{"cols":[],"rows":[{"cells":[]}]}},
               {"kind":"table","pos":[1,2]},
               {"kind":"text","pos":[3,4],"width":50,"text":"kept","rgba":[1,2,3,255],"point_size":10}
             ]}]}
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray())
            zos.closeEntry()
        }
        val items = read(out.toByteArray()).pages[0].items
        assertEquals(2, items.size)
        val t = items[0] as TableItem
        assertEquals(2, t.grid.cols)
        assertEquals(2, t.grid.rows)
        assertEquals(listOf("a", "b"), t.grid.cells[0].map { it.text })
        assertEquals(listOf("c", ""), t.grid.cells[1].map { it.text })
        assertEquals(TableItem.DEFAULT_COL_WIDTH, t.grid.colWidths[1], 1e-9)
        assertEquals(listOf(0.0, 40.0), t.grid.rowHeights)
        assertFalse(t.grid.header)
        assertEquals("kept", (items[1] as TextItem).text)
    }
}
