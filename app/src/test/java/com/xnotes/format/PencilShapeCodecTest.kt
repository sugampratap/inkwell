package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.infinite.InfiniteDocument
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.tools.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A shape snapped from the pencil saves its graphite in two additive keys, written on it alone:
 * every other shape saves byte for byte as it did, an older file loads as it always has, and an
 * older build, which skips keys it does not know, reads a pencil shape as the plain shape it is.
 */
class PencilShapeCodecTest {

    private val notes = DocumentCodec(FakeImageCodec(), FakeTextMeasurer())
    private val canvases = CanvasCodec(FakeImageCodec())

    private val ink = Rgba(40, 44, 52, 255)

    private fun shapes(): List<ShapeItem> = listOf(
        ShapeItem(ShapeKind.LINE, Pt(1.0, 2.0), Pt(90.0, 40.0), ink, 1.4, grain = true, grainPressure = 0.625),
        ShapeItem.poly(
            ShapeKind.POLYGON, listOf(Pt(0.0, 0.0), Pt(30.0, 0.0), Pt(15.0, 20.0)), ink, 2.0,
            grain = true, grainPressure = 0.5,
        ),
        ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(50.0, 30.0), ink, 3.0, Rgba(1, 2, 3, 40)),
    )

    private fun noteOf(items: List<ShapeItem>) = Document(dpi = 150).apply {
        pages.add(Page(200.0, 200.0).apply { this.items.addAll(items) })
    }

    private fun canvasOf(items: List<ShapeItem>) = InfiniteDocument().apply { addAll(items) }

    private fun bytes(write: (ByteArrayOutputStream) -> Unit) = ByteArrayOutputStream().also(write).toByteArray()

    private fun manifestOf(bundle: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return zis.readBytes().decodeToString()
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        throw AssertionError("no manifest")
    }

    private fun bundleOf(manifest: String): ByteArray = bytes { out ->
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    /** What an older build sees: the same manifest with the keys it does not know taken out. */
    private fun withoutGrain(manifest: String) = manifest.replace(Regex(",\"grain\":true,\"grain_pressure\":[0-9.]+"), "")

    private fun assertPencil(items: List<*>) {
        val line = items[0] as ShapeItem
        assertTrue(line.grain && line.graphite)
        assertEquals(0.625, line.grainPressure, 1e-9)
        assertEquals(1.4, line.strokeWidth, 1e-9)
        val poly = items[1] as ShapeItem
        assertEquals(ShapeKind.POLYGON, poly.shape)
        assertTrue(poly.grain)
        assertEquals(0.5, poly.grainPressure, 1e-9)
        val rect = items[2] as ShapeItem
        assertFalse(rect.grain)
        assertEquals(Graphite.PRESSURE_OFF, rect.grainPressure, 0.0)
    }

    private fun assertPlain(items: List<*>) {
        assertEquals(3, items.size)
        for (i in items) assertFalse((i as ShapeItem).grain)
        assertEquals(ShapeKind.POLYGON, (items[1] as ShapeItem).shape)
    }

    @Test fun aNoteRoundTripsItsPencilShapes() {
        val written = bytes { notes.write(noteOf(shapes()), it) }
        assertPencil(notes.read(ByteArrayInputStream(written)).pages[0].items)
    }

    @Test fun aCanvasRoundTripsItsPencilShapes() {
        val written = bytes { canvases.write(canvasOf(shapes()), it) }
        assertPencil(canvases.read(ByteArrayInputStream(written)).items)
    }

    @Test fun onlyAPencilShapeWritesTheNewKeys() {
        for (manifest in listOf(
            manifestOf(bytes { notes.write(noteOf(shapes()), it) }),
            manifestOf(bytes { canvases.write(canvasOf(shapes()), it) }),
        )) {
            assertEquals(2, Regex("\"grain\":true").findAll(manifest).count())
            assertTrue(manifest.contains(",\"grain\":true,\"grain_pressure\":0.625"))
            // A plain shape writes exactly what it wrote before.
            assertTrue(
                manifest.contains(
                    "{\"kind\":\"shape\",\"shape\":\"rectangle\",\"start\":[0,0],\"end\":[50,30]," +
                        "\"stroke_rgba\":[40,44,52,255],\"stroke_width\":3,\"fill_rgba\":[1,2,3,40]}",
                ),
            )
        }
    }

    @Test fun anOlderFileLoadsItsShapesPlain() {
        // Written by a build that had no graphite shapes: no keys, so plain shapes, as ever.
        val note = manifestOf(bytes { notes.write(noteOf(shapes()), it) })
        assertPlain(notes.read(ByteArrayInputStream(bundleOf(withoutGrain(note)))).pages[0].items)
        val canvas = manifestOf(bytes { canvases.write(canvasOf(shapes()), it) })
        assertPlain(canvases.read(ByteArrayInputStream(bundleOf(withoutGrain(canvas)))).items)
    }

    @Test fun aPencilShapeWithoutItsPressureStillReads() {
        // Forgiving like every other key: a bare grain flag lays the pencil's ordinary pressure,
        // and a pressure out of range is ignored rather than trusted.
        val note = manifestOf(bytes { notes.write(noteOf(shapes()), it) })
            .replace(",\"grain_pressure\":0.625", "")
            .replace("\"grain_pressure\":0.5", "\"grain_pressure\":7")
        val items = notes.read(ByteArrayInputStream(bundleOf(note))).pages[0].items
        assertTrue((items[0] as ShapeItem).grain)
        assertEquals(Graphite.PRESSURE_OFF, (items[0] as ShapeItem).grainPressure, 0.0)
        assertEquals(Graphite.PRESSURE_OFF, (items[1] as ShapeItem).grainPressure, 0.0)
    }
}
