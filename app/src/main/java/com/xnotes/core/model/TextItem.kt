package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.Renderer
import com.xnotes.core.pal.TextFlags
import com.xnotes.core.pal.TextMeasurer

/**
 * A wrapped plain-text box (spec 02 §5.3). [width] is the wrap width; the box
 * grows to fit its text, so [height] is a *reserved minimum* — the rendered box
 * is `max(height, content height)` tall and never clips. A box created by a tap
 * has `height == 0` (pure auto-height, as before); one dragged out reserves the
 * dragged height. The [measurer] lays out text identically for bounds and paint.
 *
 * A box with a [fill] is a **sticky note**: the same text box set on a coloured card with rounded
 * corners, a soft shadow and an inner padding (see [CardPaint]). Everything a text box can do, a
 * note does through the same paths (typing, resizing, copy/paste, search, undo), which is why it
 * is this class and not a second one; the card only changes where the text sits inside the box and
 * what is painted under it. A note is kept when emptied, being a visible object in its own right.
 * The padding and corner radius follow the font size, so scaling a note keeps its proportions.
 */
class TextItem(
    var pos: Pt,
    var width: Double = DEFAULT_WIDTH,
    var height: Double = 0.0,
    var text: String = "",
    var rgba: Rgba = DEFAULT_COLOR,
    var pointSize: Double = DEFAULT_POINT_SIZE,
    var face: FontFace = DEFAULT_FACE,
    private val measurer: TextMeasurer,
    /** The sticky note's card colour, or null for a plain text box (absent from older files). */
    var fill: Rgba? = null,
) : CanvasItem, Resizable {

    override val kind = KIND
    override val resizable = true
    override var locked = false

    val font get() = FontSpec(pointSize, face)

    /** True for a sticky note (a box on a coloured card). */
    val isSticky: Boolean get() = fill != null

    /** Inset of the text from the card's edge; zero for a plain box, so its layout is unchanged. */
    fun padding(): Double = if (fill != null) stickyPadding(pointSize) else 0.0

    /** The wrap width of the text: the box's width less the card's padding either side. */
    fun textWidth(): Double = (width - 2 * padding()).coerceAtLeast(1.0)

    /** Height the current text needs at the current width (empty ⇒ one line), padding included. */
    fun contentHeight(): Double = measurer.measure(text.ifEmpty { " " }, font, textWidth(), FLAGS).h + 2 * padding()

    override fun bounds(): Rect = Rect(pos.x, pos.y, width, maxOf(height, contentHeight()))

    /** Where the text itself is laid out: the bounds inset by the padding (the bounds for a plain box). */
    fun textRect(): Rect {
        val b = bounds()
        val p = padding()
        return Rect(b.x + p, b.y + p, textWidth(), (b.h - 2 * p).coerceAtLeast(0.0))
    }

    /** A note's shadow paints past its bounds; the page cache must repair that much around it. */
    override fun paintBounds(): Rect = fill?.let { CardPaint.cardPaintBounds(bounds(), pointSize) } ?: bounds()

    override fun paint(r: Renderer) {
        paintCard(r)
        if (text.isEmpty()) return
        r.drawText(text, textRect(), font, rgba, FLAGS)
    }

    /** Just the sticky note's card, without its text: what sits under the live editor field. */
    fun paintCard(r: Renderer) {
        val f = fill ?: return
        CardPaint.paintCard(r, bounds(), f, pointSize)
    }

    override fun translate(dx: Double, dy: Double) {
        pos = Pt(pos.x + dx, pos.y + dy)
    }

    override fun contains(p: Pt): Boolean = bounds().contains(p)

    override fun centroid(): Pt = bounds().center

    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean =
        bounds().distanceTo(Pt(cx, cy)) <= radius

    override fun geometry(): GeoHandle = TextHandle(pos, width, height)

    override fun setGeometry(handle: GeoHandle) {
        if (handle is TextHandle) {
            pos = handle.pos
            width = handle.width
            height = handle.height
        }
    }

    override fun snapshotGeometry(): GeometrySnapshot = TextSnapshot(pos, width, height, pointSize)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap !is TextSnapshot) return
        pos = snap.pos
        width = snap.width
        height = snap.height
        pointSize = snap.pointSize
    }

    /** Text never rotates. A uniform (corner) scale grows the wrap width, reserved height, and font
     *  size together; a single-axis (edge) scale changes only the wrap width or the reserved height
     *  and leaves the font size alone. The vertical scale reserves at least the current content
     *  height first, so dragging the bottom edge of an auto-height box actually grows it. */
    override fun applyTransform(t: Affine) {
        pos = t.apply(pos)
        width *= t.scaleX
        if (t.scaleY != 1.0) height = maxOf(height, contentHeight()) * t.scaleY
        if (t.isUniformScale) pointSize *= t.scaleX
    }

    companion object {
        const val KIND = "text"

        /** A sticky note's text inset, from its font size (content px per point). */
        fun stickyPadding(pointSize: Double): Double = pointSize * 1.3

        /** A new sticky note: a square card, sans text a little larger than a text box's. */
        const val STICKY_SIZE = 330.0
        const val STICKY_POINT_SIZE = 14.0
        val STICKY_FACE = FontFace.SANS
        const val DEFAULT_WIDTH = 300.0
        const val DEFAULT_POINT_SIZE = 13.0

        /** A new text box's face: the app's Sans, as a new note's body text has (Part 6's system Sans). */
        val DEFAULT_FACE = FontFace.SANS

        /**
         * The face a saved text box with no `font_face` has: Mono, xnotes' first default, which the file never wrote
         * out ([FontFace.fromId]). Only a box in this face is written without one, so older notes keep their Mono.
         */
        val STORED_DEFAULT_FACE = FontFace.MONO
        val DEFAULT_COLOR = Rgba(236, 236, 236, 255)
        val FLAGS = TextFlags(wordWrap = true, alignLeft = true, alignTop = true)
    }
}

/** A snapshot of a text box's restylable properties (colour, size, face, a note's card) for undo. */
data class TextStyle(val rgba: Rgba, val pointSize: Double, val face: FontFace, val fill: Rgba? = null) {
    fun applyTo(t: TextItem) {
        t.rgba = rgba
        t.pointSize = pointSize
        t.face = face
        t.fill = fill
    }

    companion object {
        fun of(t: TextItem) = TextStyle(t.rgba, t.pointSize, t.face, t.fill)
    }
}

/** Snapshot of a text box's transformable geometry (position, wrap width, reserved height, size). */
private data class TextSnapshot(
    val pos: Pt,
    val width: Double,
    val height: Double,
    val pointSize: Double,
) : GeometrySnapshot
