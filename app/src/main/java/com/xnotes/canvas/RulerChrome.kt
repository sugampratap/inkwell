package com.xnotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.SparseArray
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.LineMetrics
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.platform.AndroidRasterSurface
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.Palette
import kotlin.math.ceil

/** The ruler's glyphs (TO 381): the two locks, off and on, and the turn handles' arrows. */
internal enum class RulerGlyph { LOCK_POS, LOCK_POS_ON, LOCK_ANGLE, LOCK_ANGLE_ON, TURN }

private val CLEAR = Rgba(0, 0, 0, 0)

/** The locks' glyphs are 18 dp, the handles' 20 dp (TO 97, 100). */
private const val LOCK_GLYPH_DP = 18.0
private const val TURN_GLYPH_DP = 20.0

private fun Color.rgba(): Rgba = Rgba.fromArgb(toArgb())

/**
 * Everything the ruler draws with that is not geometry (TO 91–102): its colours, pens and fonts, the three Phosphor
 * glyphs filled once into bitmaps, its label strings with their widths, and its pill outlines. [update] rebuilds them
 * only when the palette or the density changes, so a frame of the ruler, drawn on every frame of a live stroke, makes
 * no bitmap, path, pen, font or label string.
 */
internal class RulerChrome(private val measurer: TextMeasurer) {
    private var palette: Palette? = null
    private var density = 0.0

    var band = CLEAR
        private set
    var tickLabel = CLEAR
        private set
    var shadow = CLEAR
        private set
    var raised = CLEAR
        private set
    var raisedDim = CLEAR
        private set
    var line2 = CLEAR
        private set
    var solid = CLEAR
        private set
    var text = CLEAR
        private set
    private var text2 = CLEAR
    private var onSolid = CLEAR

    var edgePen = Pen(CLEAR)
        private set
    var tickPen = Pen(CLEAR)
        private set
    var lockRingPen = Pen(CLEAR)
        private set
    var handleRingPen = Pen(CLEAR)
        private set
    var handleRingPenDim = Pen(CLEAR)
        private set

    var tickFont = FontSpec(1.0)
        private set
    var readoutFont = FontSpec(1.0)
        private set
    var tickMetrics = LineMetrics(0.0, 0.0)
        private set
    var readoutMetrics = LineMetrics(0.0, 0.0)
        private set

    private val glyphs = arrayOfNulls<AndroidRasterSurface>(RulerGlyph.entries.size)
    private val numberLabels = LabelCache { it.toString() }
    private val degreeLabels = LabelCache { RulerMath.degreeLabel(it) }
    private var lengthTenths = Int.MIN_VALUE
    private var lengthLabel = ""
    private var lengthLabelWidth = 0.0
    private val bodyPills = SparseArray<List<Pt>>()
    private val ringPills = SparseArray<List<Pt>>()

    /** Rebuild for [pal] at [density] (viewport px per dp); does nothing when neither changed. */
    fun update(pal: Palette, density: Double) {
        if (pal === palette && density == this.density) return
        palette = pal
        this.density = density
        val dark = pal.isDark
        val ink = pal.ink
        // --to-band, --to-edge, --to-tick, --to-tlab (TO 92–93).
        band = if (dark) Rgba(28, 28, 28, 107) else Rgba(255, 255, 255, 128)
        val edge = if (dark) Rgba(0x9A, 0x9A, 0x9A) else Rgba(0x6A, 0x6A, 0x6A)
        val tick = if (dark) Rgba(0xF2, 0xF2, 0xF2) else Rgba(0x22, 0x22, 0x22)
        tickLabel = if (dark) Rgba(0xC8, 0xC8, 0xC8) else Rgba(0x55, 0x55, 0x55)
        // The one soft shadow (--sh-soft) as a flat offset fill: no blur.
        shadow = Rgba(0, 0, 0, if (dark) 90 else 30)
        raised = ink.raised.rgba()
        raisedDim = raised.scaleAlpha(RulerMath.LOCKED_HANDLE_ALPHA)
        line2 = ink.line2.rgba()
        solid = ink.solid.rgba()
        text = ink.text.rgba()
        text2 = ink.text2.rgba()
        onSolid = ink.onSolid.rgba()
        // Cosmetic pens are in device px: the mockup's px are dp.
        edgePen = Pen(edge, 1.2 * density, cosmetic = true)
        tickPen = Pen(tick, 1.0 * density, cosmetic = true)
        lockRingPen = Pen(ink.line3.rgba(), 1.0 * density, cosmetic = true)
        handleRingPen = Pen(text, 1.5 * density, cosmetic = true)
        handleRingPenDim = Pen(text.scaleAlpha(RulerMath.LOCKED_HANDLE_ALPHA), 1.5 * density, cosmetic = true)
        tickFont = FontSpec(RulerMath.pointsForDp(10.5, density), FontFace.SANS, bold = true)
        readoutFont = FontSpec(RulerMath.pointsForDp(13.0, density), FontFace.SANS, bold = true)
        tickMetrics = measurer.metrics(tickFont)
        readoutMetrics = measurer.metrics(readoutFont)
        // Old bitmaps are left to the collector rather than recycled: a frame in flight may still draw them.
        glyphs.fill(null)
        numberLabels.reset(tickFont)
        degreeLabels.reset(readoutFont)
        lengthTenths = Int.MIN_VALUE
        bodyPills.clear()
        ringPills.clear()
    }

    /** [g] as a bitmap at its size and colour, made on first use after [update]. */
    fun glyph(g: RulerGlyph): AndroidRasterSurface {
        glyphs[g.ordinal]?.let { return it }
        val made = when (g) {
            RulerGlyph.LOCK_POS -> raster(Ph.crosshairSimple, LOCK_GLYPH_DP, text2)
            RulerGlyph.LOCK_POS_ON -> raster(Ph.crosshairSimple, LOCK_GLYPH_DP, onSolid)
            RulerGlyph.LOCK_ANGLE -> raster(Ph.angle, LOCK_GLYPH_DP, text2)
            RulerGlyph.LOCK_ANGLE_ON -> raster(Ph.angle, LOCK_GLYPH_DP, onSolid)
            RulerGlyph.TURN -> raster(Ph.arrowsClockwise, TURN_GLYPH_DP, text)
        }
        glyphs[g.ordinal] = made
        return made
    }

    fun number(n: Int): String = numberLabels.text(n)
    fun numberWidth(n: Int): Double = numberLabels.width(n)
    fun degreeText(deg: Int): String = degreeLabels.text(deg)
    fun degreeWidth(deg: Int): Double = degreeLabels.width(deg)

    fun lengthText(tenths: Int): String {
        ensureLength(tenths)
        return lengthLabel
    }

    fun lengthWidth(tenths: Int): Double {
        ensureLength(tenths)
        return lengthLabelWidth
    }

    /** A readout pill's outline [widthPx] wide, from its top-left corner. */
    fun bodyPill(widthPx: Double): List<Pt> = pill(bodyPills, widthPx, RulerMath.PILL_HEIGHT_DP * density)

    /** The pill's 1 dp ring: the same pill 1 dp bigger all round, drawn under it. */
    fun ringPill(widthPx: Double): List<Pt> = pill(ringPills, widthPx + 2.0 * density, (RulerMath.PILL_HEIGHT_DP + 2.0) * density)

    private fun pill(cache: SparseArray<List<Pt>>, w: Double, h: Double): List<Pt> {
        val key = Math.round(w).toInt()
        cache.get(key)?.let { return it }
        return RulerMath.pillPoints(key.toDouble(), h).also { cache.put(key, it) }
    }

    private fun ensureLength(tenths: Int) {
        if (tenths == lengthTenths) return
        lengthTenths = tenths
        lengthLabel = RulerMath.lengthLabel(tenths)
        lengthLabelWidth = measurer.advances(lengthLabel, readoutFont).sum()
    }

    /** [icon]'s Phosphor paths filled once into a [sizeDp] square bitmap in [tint]. */
    private fun raster(icon: ImageVector, sizeDp: Double, tint: Rgba): AndroidRasterSurface {
        val px = ceil(sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(px / icon.viewportWidth, px / icon.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tint.toArgb()
        }
        for (node in icon.root) if (node is VectorPath) canvas.drawPath(node.pathData.toPath().asAndroidPath(), paint)
        return AndroidRasterSurface(bitmap)
    }

    /** Label strings by number, with their widths in [font], grown on demand and kept until [reset]. */
    private inner class LabelCache(private val make: (Int) -> String) {
        private var font = FontSpec(1.0)
        private var strings = arrayOfNulls<String>(0)
        private var widths = DoubleArray(0)

        fun reset(f: FontSpec) {
            font = f
            strings = arrayOfNulls(0)
            widths = DoubleArray(0)
        }

        fun text(n: Int): String {
            grow(n)
            return strings[n] ?: make(n).also { strings[n] = it }
        }

        fun width(n: Int): Double {
            grow(n)
            val w = widths[n]
            if (!w.isNaN()) return w
            return measurer.advances(text(n), font).sum().also { widths[n] = it }
        }

        private fun grow(n: Int) {
            if (n < strings.size) return
            val size = maxOf(n + 1, strings.size * 2, 64)
            val old = widths.size
            strings = strings.copyOf(size)
            widths = widths.copyOf(size).also { it.fill(Double.NaN, old, size) }
        }
    }
}
