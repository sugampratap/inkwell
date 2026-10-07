package com.xnotes.format

import com.xnotes.core.FakeImageCodec
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.history.AddItem
import com.xnotes.core.history.AddItems
import com.xnotes.core.history.Command
import com.xnotes.core.history.CompositeCommand
import com.xnotes.core.history.History
import com.xnotes.core.history.InsertPdfPages
import com.xnotes.core.model.AudioData
import com.xnotes.core.model.AudioItem
import com.xnotes.core.model.AudioStamp
import com.xnotes.core.model.Document
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.model.TextItem
import com.xnotes.core.model.deepCopy
import com.xnotes.core.model.snapshot
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Audio chips and voice-sync stamps in the `.xnote` bundle, and the model plumbing around them. */
class AudioCodecTest {

    private val codec = DocumentCodec(FakeImageCodec(), FakeTextMeasurer())

    private fun tempFile(bytes: ByteArray, suffix: String? = null): File =
        File.createTempFile("asset", suffix).apply { writeBytes(bytes); deleteOnExit() }

    private fun write(doc: Document): ByteArray = ByteArrayOutputStream().also { codec.write(doc, it) }.toByteArray()

    private fun read(bundle: ByteArray): Document =
        codec.read(ByteArrayInputStream(bundle), imageDir = Files.createTempDirectory("xnotes-assets").toFile())

    private fun entryNames(bundle: ByteArray): List<String> {
        val out = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                out += e.name
                e = zis.nextEntry
            }
        }
        return out
    }

    private fun stroke(x: Double) = Stroke(
        Tool.PEN,
        ToolConfig(3.0, true, 0.4, 0.0, Rgba(0, 0, 0, 255)),
        mutableListOf(Sample(x, 10.0, 0.5, 0.0), Sample(x + 20.0, 30.0, 0.5, 400.0)),
    )

    @Test fun anAudioChipRoundTripsWithItsBytes() {
        val audioBytes = ByteArray(300) { (it * 7).toByte() }
        val doc = Document()
        val page = Page(1240.0, 1754.0)
        val chip = AudioItem(AudioData(tempFile(audioBytes, ".m4a")), Pt(40.0, 50.0), "Voice 1", 83_500L, voice = true, recording = "abc123")
        chip.locked = true
        page.items.add(chip)
        doc.pages.add(page)

        val bundle = write(doc)
        assertTrue(entryNames(bundle).contains("assets/audio-000.m4a"))
        val back = read(bundle).pages[0].items.single() as AudioItem
        assertEquals(Pt(40.0, 50.0), back.pos)
        assertEquals("Voice 1", back.title)
        assertEquals(83_500L, back.durationMs)
        assertTrue(back.voice)
        assertEquals("abc123", back.recording)
        assertTrue(back.locked)
        assertEquals("m4a", back.audio.ext)
        assertArrayEquals(audioBytes, back.audio.file.readBytes())
    }

    @Test fun audioFilesKeepTheirExtensionAndOrder() {
        val doc = Document()
        val page = Page(1000.0, 1000.0)
        page.items.add(AudioItem(AudioData(tempFile(byteArrayOf(1), ".mp3")), Pt.ZERO, "Song", 1000L, voice = false))
        page.items.add(AudioItem(AudioData(tempFile(byteArrayOf(2), ".m4a")), Pt(0.0, 100.0), "Voice 1", 1000L, voice = true))
        doc.pages.add(page)
        val bundle = write(doc)
        val names = entryNames(bundle)
        assertTrue(names.contains("assets/audio-000.mp3"))
        assertTrue(names.contains("assets/audio-001.m4a"))
        val items = read(bundle).pages[0].items
        assertEquals("Song", (items[0] as AudioItem).title)
        assertFalse((items[0] as AudioItem).voice)
        assertArrayEquals(byteArrayOf(2), (items[1] as AudioItem).audio.file.readBytes())
    }

    @Test fun stampsRoundTripOntoTheSameItemsAroundAssetItems() {
        val doc = Document()
        val page = Page(1240.0, 1754.0)
        val a = stroke(10.0)
        val img = ImageItem(ImageData(tempFile(byteArrayOf(9, 9)), 10, 10), Rect(0.0, 0.0, 10.0, 10.0))
        val b = stroke(200.0)
        val unstamped = stroke(400.0)
        val chip = AudioItem(AudioData(tempFile(byteArrayOf(5), ".m4a")), Pt.ZERO, "Voice 1", 9000L, voice = true, recording = "rec1")
        val text = TextItem(Pt(5.0, 5.0), width = 100.0, text = "hi", rgba = Rgba(0, 0, 0, 255), pointSize = 12.0, measurer = FakeTextMeasurer())
        page.items.addAll(listOf(a, img, b, unstamped, chip, text))
        doc.pages.add(page)
        doc.audioStamps[a] = AudioStamp("rec1", 1200L)
        doc.audioStamps[img] = AudioStamp("rec1", 2500L)
        doc.audioStamps[b] = AudioStamp("rec1", 4000L)
        doc.audioStamps[text] = AudioStamp("rec1", 8000L)

        val back = read(write(doc))
        val items = back.pages[0].items
        assertEquals(6, items.size)
        assertEquals(AudioStamp("rec1", 1200L), back.audioStamps[items[0]])
        assertTrue(items[1] is ImageItem)
        assertEquals(AudioStamp("rec1", 2500L), back.audioStamps[items[1]])
        assertEquals(AudioStamp("rec1", 4000L), back.audioStamps[items[2]])
        assertNull(back.audioStamps[items[3]])
        assertTrue(items[4] is AudioItem)
        assertNull(back.audioStamps[items[4]])
        assertEquals(AudioStamp("rec1", 8000L), back.audioStamps[items[5]])
    }

    @Test fun aNoteWithoutStampsWritesNoSyncField() {
        val doc = Document()
        doc.pages.add(Page(100.0, 100.0).apply { items.add(stroke(1.0)) })
        val bundle = write(doc)
        ZipInputStream(ByteArrayInputStream(bundle)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") assertFalse(zis.readBytes().decodeToString().contains("audio_sync"))
                e = zis.nextEntry
            }
        }
    }

    @Test fun snapshotAndDeepCopyCarryTheStamps() {
        val doc = Document()
        val s = stroke(1.0)
        doc.pages.add(Page(100.0, 100.0).apply { items.add(s) })
        doc.audioStamps[s] = AudioStamp("r", 10L)

        val snap = doc.snapshot()
        assertSame(s, snap.pages[0].items[0])
        assertEquals(AudioStamp("r", 10L), snap.audioStamps[s])

        val copy = doc.deepCopy(FakeTextMeasurer())
        val c = copy.pages[0].items[0]
        assertFalse(c === s)
        assertEquals(AudioStamp("r", 10L), copy.audioStamps[c])
    }

    @Test fun aCopiedChipSharesItsAudioAndRecording() {
        val chip = AudioItem(AudioData(tempFile(byteArrayOf(1), ".m4a")), Pt(1.0, 2.0), "Voice 3", 5L, voice = true)
        val copy = chip.deepCopy(FakeTextMeasurer()) as AudioItem
        assertFalse(copy === chip)
        assertSame(chip.audio, copy.audio)
        assertEquals(chip.recording, copy.recording)
        assertEquals(chip.pos, copy.pos)
    }

    @Test fun historyTellsItsListenerOfEveryPush() {
        val history = History()
        val seen = ArrayList<Command>()
        history.onPush = { seen.add(it) }
        val page = Page(10.0, 10.0)
        val one = AddItem(page, stroke(1.0))
        val two = CompositeCommand(listOf(AddItems(page, listOf(stroke(2.0)))))
        history.push(one)
        history.push(two)
        assertEquals(listOf<Command>(one, two), seen)
        assertEquals(1, two.parts.size)
    }

    @Test fun insertPdfPagesSwapsTheSourceAndUndoesWhole() {
        val oldPdf = File("old.pdf")
        val newPdf = File("new.pdf")
        val doc = Document(pdfFile = oldPdf)
        val first = Page(10.0, 10.0, pdfPage = 0)
        val last = Page(10.0, 10.0, pdfPage = 1)
        doc.pages.addAll(listOf(first, last))
        val added = listOf(Page(20.0, 20.0, pdfPage = 2), Page(20.0, 20.0, pdfPage = 3))
        var swaps = 0
        val cmd = InsertPdfPages(doc, oldPdf, newPdf, added, 1) { swaps++ }

        cmd.redo()
        assertSame(newPdf, doc.pdfFile)
        assertEquals(listOf(first, added[0], added[1], last), doc.pages)
        cmd.undo()
        assertSame(oldPdf, doc.pdfFile)
        assertEquals(listOf(first, last), doc.pages)
        cmd.redo()
        assertEquals(4, doc.pages.size)
        assertEquals(3, swaps)
    }

    @Test fun durationsFormatLikeAPlayer() {
        assertEquals("0:00", AudioItem.formatDuration(0L))
        assertEquals("1:05", AudioItem.formatDuration(65_000L))
        assertEquals("1:00:01", AudioItem.formatDuration(3_601_000L))
    }
}
