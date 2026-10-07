package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The immutable source of an [AudioItem]: the encoded audio [file] on disk (an AAC `.m4a` the
 * recorder wrote, or a copy of a picked file) and the extension it is stored under in the bundle.
 * Like [ImageData] it is shared freely between copies, because nothing ever writes to the file once
 * it exists; it lives in the editor's note-asset temp dir, which is purged on launch.
 */
class AudioData(val file: File, val ext: String = extOf(file)) {
    companion object {
        /** A file's own extension, lower-cased and kept to something a zip entry name can carry. */
        fun extOf(file: File): String =
            file.name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
                ?: "m4a"
    }
}

/**
 * Where an item sits in a voice recording: [recording] is the [AudioItem.recording] it was made
 * during, [ms] how far into that recording it was put down. Written while a recording runs, read
 * back by synced playback, which shows the note being written again in time with the voice.
 */
data class AudioStamp(val recording: String, val ms: Long)

/**
 * A voice recording or audio file placed on a page: a compact rounded chip with a round play
 * button, a title, the duration and a small mic or music glyph. Tapping it plays it; it moves,
 * copies, locks and deletes like any other item, but never resizes or turns, since it is a control
 * rather than a picture.
 *
 * The chip is drawn with the plain [Renderer] primitives every backend has (polygons, circles, a
 * line and two text runs), so the screen, the thumbnails and the PDF export all draw the same chip.
 * The export draws it as it rests: the audio itself never goes into a PDF.
 */
class AudioItem(
    var audio: AudioData,
    /** Top-left corner, page-local. The size is fixed: [WIDTH] x [HEIGHT]. */
    var pos: Pt,
    var title: String,
    var durationMs: Long,
    /** True for something recorded in the note (mic glyph), false for an inserted audio file. */
    val voice: Boolean,
    /** This recording's id, which [AudioStamp]s name; shared by a copy, which plays the same audio. */
    val recording: String = newRecordingId(),
) : CanvasItem {

    override val kind = KIND
    override val resizable = false
    override var locked = false

    /** Transient play state, drawn on the chip (pause bars and a progress line); never saved. */
    @Volatile var playing: Boolean = false

    /** Transient: how far playback has got, 0..1, while [playing] or paused mid-way. */
    @Volatile var progress: Double = 0.0

    val rect: Rect get() = Rect(pos.x, pos.y, WIDTH, HEIGHT)

    override fun bounds(): Rect = rect

    override fun paint(r: Renderer) {
        val box = rect
        r.fillPolygon(pill(box), CHIP)
        r.strokePolygon(pill(box), Pen(CHIP_EDGE, EDGE_W, cosmetic = false))
        // The button: a near-black disc with play, or pause while it plays (AU 70).
        val c = Pt(box.left + HEIGHT / 2.0, box.top + HEIGHT / 2.0)
        val disc = DISC_R
        r.fillCircle(c, disc, DISC)
        if (playing) {
            val bw = disc * 0.22
            val bh = disc * 0.86
            r.fillRect(Rect(c.x - bw * 1.6, c.y - bh / 2.0, bw, bh), GLYPH)
            r.fillRect(Rect(c.x + bw * 0.6, c.y - bh / 2.0, bw, bh), GLYPH)
        } else {
            val s = disc * 0.48
            r.fillPolygon(listOf(Pt(c.x - s * 0.7, c.y - s), Pt(c.x + s * 1.05, c.y), Pt(c.x - s * 0.7, c.y + s)), GLYPH)
        }
        // Title and duration, in two runs. A model item has no text measurer, so the title is cut
        // by a character budget that fits the chip at this size (a little short on wide glyphs).
        val textX = box.left + TEXT_X
        r.drawTextRun(ellipsize(title, TITLE_CHARS), textX, box.top + 34.0, TITLE_FONT, TEXT)
        r.drawTextRun(formatDuration(durationMs), textX, box.top + 60.0, TIME_FONT, TEXT_DIM)
        // Kind glyph at the right end.
        val g = Pt(box.right - GLYPH_FROM_RIGHT, box.centerY)
        if (voice) paintMic(r, g) else paintNote(r, g)
        if (playing || progress > 0.0) {
            val x0 = box.left + BAR_LEFT
            val x1 = box.right - BAR_RIGHT
            val y = box.top + BAR_TOP
            r.fillRect(Rect(x0, y, x1 - x0, BAR_H), TRACK)
            r.fillRect(Rect(x0, y, (x1 - x0) * progress.coerceIn(0.0, 1.0), BAR_H), FILL)
        }
    }

    /** Phosphor's microphone on its 256 grid at [GLYPH_PX]: the capsule (88..168 × 24..152), the cradle under 128, the stand. */
    private fun paintMic(r: Renderer, c: Pt) {
        val pen = Pen(TEXT_DIM, GLYPH_STROKE, cosmetic = false)
        val k = GLYPH_PX / 256.0
        fun p(x: Double, y: Double) = Pt(c.x + (x - 128.0) * k, c.y + (y - 128.0) * k)
        val capsule = ArrayList<Pt>()
        for (i in 0..8) {
            val a = PI + PI * i / 8.0
            capsule.add(p(128.0 + 40.0 * cos(a), 64.0 + 40.0 * sin(a)))
        }
        for (i in 0..8) {
            val a = PI * i / 8.0
            capsule.add(p(128.0 + 40.0 * cos(a), 112.0 + 40.0 * sin(a)))
        }
        r.strokePolygon(capsule, pen)
        val cradle = ArrayList<Pt>()
        for (i in 0..12) {
            val a = PI * i / 12.0
            cradle.add(p(128.0 + 72.0 * cos(a), 128.0 + 72.0 * sin(a)))
        }
        r.strokePolyline(cradle, pen)
        r.strokePolyline(listOf(p(128.0, 200.0), p(128.0, 232.0)), pen)
    }

    /** Phosphor's music-note on its 256 grid at [GLYPH_PX]: the head (a ring round 88,184), the stem up x 128, the flag to 208. */
    private fun paintNote(r: Renderer, c: Pt) {
        val pen = Pen(TEXT_DIM, GLYPH_STROKE, cosmetic = false)
        val k = GLYPH_PX / 256.0
        fun p(x: Double, y: Double) = Pt(c.x + (x - 128.0) * k, c.y + (y - 128.0) * k)
        r.strokeEllipse(p(88.0, 184.0), 32.0 * k, 32.0 * k, pen)
        r.strokePolyline(listOf(p(128.0, 184.0), p(128.0, 44.0), p(208.0, 68.0), p(208.0, 108.0), p(128.0, 84.0)), pen)
    }

    override fun translate(dx: Double, dy: Double) {
        pos = Pt(pos.x + dx, pos.y + dy)
    }

    override fun contains(p: Pt): Boolean = rect.contains(p)

    override fun centroid(): Pt = rect.center

    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean =
        rect.distanceTo(Pt(cx, cy)) <= radius

    override fun snapshotGeometry(): GeometrySnapshot = AudioSnapshot(pos)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap is AudioSnapshot) pos = snap.pos
    }

    /** A chip only ever moves: a group scale or turn carries its centre along and leaves its size. */
    override fun applyTransform(t: Affine) {
        val c = t.apply(rect.center)
        pos = Pt(c.x - WIDTH / 2.0, c.y - HEIGHT / 2.0)
    }

    companion object {
        const val KIND = "audio"

        /** Chip size in page px (150 dpi): about 6.4 x 1.3 cm, a finger's width tall. */
        const val WIDTH = 380.0
        const val HEIGHT = 76.0

        private const val TITLE_CHARS = 20

        // The chip is page content (r3_audio AU 68-76): the same in every theme and in exports. Mockup px x 1.771
        // = page px; a font's mockup px x 1.771 x 72 / 150 = pt.
        private val TITLE_FONT = FontSpec(10.6, FontFace.SANS, bold = true) // 12.5 px Bold
        private val TIME_FONT = FontSpec(9.35, FontFace.SANS) // 11 px

        private val CHIP = Rgba(255, 255, 255)
        private val CHIP_EDGE = Rgba(0xDA, 0xD6, 0xCC)
        private val DISC = Rgba(0x22, 0x22, 0x22)
        private val GLYPH = Rgba(255, 255, 255)
        private val TEXT = Rgba(0x22, 0x22, 0x22)
        private val TEXT_DIM = Rgba(0x6A, 0x6A, 0x6A)
        private val TRACK = Rgba(0xE3, 0xE0, 0xD8)
        private val FILL = Rgba(0x22, 0x22, 0x22)

        private const val EDGE_W = 1.771 // the 1 px inset ring
        private const val DISC_R = 28.3 // 32 px disc
        private const val TEXT_X = 79.7 // left 45
        private const val GLYPH_PX = 31.9 // 18 px
        private const val GLYPH_FROM_RIGHT = 35.4 // right 11 + half the glyph
        private const val GLYPH_STROKE = 2.0 // Phosphor Regular's 16 on 256, at 18 px
        private const val BAR_LEFT = 141.7 // left 80
        private const val BAR_RIGHT = 63.8 // right 36
        private const val BAR_TOP = 52.2 // top 29.5
        private const val BAR_H = 3.5 // 2 px

        /** A short random id, unique enough within one note. */
        fun newRecordingId(): String =
            java.util.UUID.randomUUID().toString().replace("-", "").take(12)

        /** `m:ss`, or `h:mm:ss` past the hour. */
        fun formatDuration(ms: Long): String {
            val total = (ms.coerceAtLeast(0L) + 500L) / 1000L
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        internal fun ellipsize(text: String, max: Int): String =
            if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

        /** The chip outline: a rounded rect whose ends are full half circles. */
        private fun pill(box: Rect): List<Pt> {
            val rad = box.h / 2.0
            val out = ArrayList<Pt>(40)
            val steps = 16
            // Right end, top to bottom.
            for (i in 0..steps) {
                val a = -PI / 2.0 + PI * i / steps
                out.add(Pt(box.right - rad + rad * cos(a), box.centerY + rad * sin(a)))
            }
            // Left end, bottom to top.
            for (i in 0..steps) {
                val a = PI / 2.0 + PI * i / steps
                out.add(Pt(box.left + rad + rad * cos(a), box.centerY + rad * sin(a)))
            }
            return out
        }
    }
}

private data class AudioSnapshot(val pos: Pt) : GeometrySnapshot
