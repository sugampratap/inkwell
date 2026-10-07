package com.xnotes.platform

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * A placed picture as the smallest PDF image that still looks like it.
 *
 * Android decodes most PNGs, screenshots included, with an alpha channel whether or not a single
 * pixel is see-through, and an image with alpha used to go in lossless: a full-page screenshot was
 * ten megabytes or more. Here the pixels decide:
 *  - fully opaque (nearly every photo and screenshot): JPEG, or lossless when that is smaller, as it
 *    is for a flat diagram of a few colours, where JPEG would also smear the edges;
 *  - truly see-through (a cut-out): the colour as JPEG with the alpha as a compressed soft mask
 *    beside it, not the whole thing lossless.
 */
internal object PdfImages {

    /** Fewer distinct colours than this in the sample and lossless gets a try: a diagram or a slide, whose anti-aliased text still adds a few thousand shades, where a photo has tens of thousands. */
    private const val FLAT_COLOURS = 3000

    /** Pixels sampled to judge flatness. */
    private const val FLAT_SAMPLE = 40_000

    /**
     * [bmp] as an image XObject. For a flat picture [native], when given, supplies the same picture at
     * its own, larger pixels, which is tried lossless too: whichever comes out smallest is used.
     */
    fun xObject(doc: PDDocument, bmp: Bitmap, quality: Float, native: (() -> Bitmap?)? = null): PDImageXObject {
        val alpha = alphaOf(bmp)
        val colour = if (bmp.hasAlpha()) opaqueColour(bmp) else bmp
        try {
            var img = JPEGFactory.createFromImage(doc, colour, quality)
            if (alpha == null && isFlat(colour)) {
                // Only the one the page references is written; the others are dropped at save.
                val lossless = LosslessFactory.createFromImage(doc, colour)
                if (lossless.cosObject.length < img.cosObject.length) img = lossless
                native?.invoke()?.let { big ->
                    try {
                        if (alphaOf(big) == null) {
                            val opaque = if (big.hasAlpha()) opaqueColour(big) else big
                            val crisp = LosslessFactory.createFromImage(doc, opaque)
                            if (opaque !== big) opaque.recycle()
                            if (crisp.cosObject.length < img.cosObject.length) img = crisp
                        }
                    } finally {
                        big.recycle()
                    }
                }
            }
            if (alpha != null) img.cosObject.setItem(COSName.SMASK, softMask(doc, alpha, bmp.width, bmp.height))
            return img
        } finally {
            if (colour !== bmp) colour.recycle()
        }
    }

    /** The alpha channel, one byte a pixel, or null when every pixel is opaque. */
    internal fun alphaOf(bmp: Bitmap): ByteArray? {
        if (!bmp.hasAlpha()) return null
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var out: ByteArray? = null
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val a = row[x] ushr 24
                if (a != 255 && out == null) {
                    // The first see-through pixel: everything before it was opaque.
                    out = ByteArray(w * h)
                    java.util.Arrays.fill(out, 0, y * w + x, 255.toByte())
                }
                if (out != null) out[y * w + x] = a.toByte()
            }
        }
        return out
    }

    /** The colour without its alpha, un-premultiplied, as an opaque bitmap a JPEG can be made of. */
    private fun opaqueColour(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1) // un-premultiplied
            for (x in 0 until w) row[x] = row[x] or (0xFF shl 24)
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        out.setHasAlpha(false)
        return out
    }

    /** Whether a sample of the pixels holds few distinct colours: a diagram or a slide, not a photo. */
    internal fun isFlat(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val total = w.toLong() * h
        val step = maxOf(1L, total / FLAT_SAMPLE)
        val seen = HashSet<Int>()
        var i = 0L
        while (i < total) {
            val c = bmp.getPixel((i % w).toInt(), (i / w).toInt()) and 0xFFFFFF
            if (seen.add(c) && seen.size >= FLAT_COLOURS) return false
            i += step
        }
        return true
    }

    private fun softMask(doc: PDDocument, alpha: ByteArray, w: Int, h: Int): PDImageXObject {
        val bytes = ByteArrayOutputStream(alpha.size / 8)
        DeflaterOutputStream(bytes, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(alpha) }
        return PDImageXObject(doc, ByteArrayInputStream(bytes.toByteArray()), COSName.FLATE_DECODE, w, h, 8, PDDeviceGray.INSTANCE)
    }
}
