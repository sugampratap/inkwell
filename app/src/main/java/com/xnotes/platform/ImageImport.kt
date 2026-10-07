package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.xnotes.core.model.ImageData
import com.xnotes.core.util.ExifTransform
import com.xnotes.core.util.ImageBuckets
import com.xnotes.core.util.ImportSizing
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/**
 * Brings a picture into a note: copies it into the editor's image directory, upright and at a
 * sensible size, entirely off the UI thread.
 *
 * Two things a camera hands over need fixing on the way in. Phones store a portrait photo sideways
 * with an EXIF tag saying so, which the decoders here do not read, so it used to land on its side;
 * the turn (and any mirror) is now baked into the stored pixels once. And a modern sensor's frame
 * is 50 megapixels or more, which nothing on a tablet screen needs and every redraw paid for; the
 * stored copy is now capped at [ImportSizing.MAX_STORED_EDGE] on its long edge (re-encoded as a
 * JPEG at quality 90, or a PNG when it has transparency). A picture that needs neither keeps its
 * own bytes untouched.
 */
object ImageImport {

    /** A stored, upright image file and its pixel size, ready to become an [ImageData]. */
    class Prepared(val file: File, val width: Int, val height: Int) {
        fun toImageData(): ImageData = ImageData(file, width, height)
    }

    // One import at a time, in the order asked: a batch of pictures lands in the order picked.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "xnotes-image-import").apply { isDaemon = true }
    }

    /** Run [work] on the import thread. */
    fun execute(work: () -> Unit) = worker.execute(work)

    /** [prepare] from bytes already in memory (a clipboard paste, a camera result). */
    fun prepare(bytes: ByteArray, dir: File, prefetchEdge: Int = 0): Prepared? {
        val raw = runCatching { File.createTempFile("img", null, dir).apply { writeBytes(bytes) } }.getOrNull()
            ?: return null
        return prepareFile(raw, prefetchEdge)
    }

    /** [prepare] from a stream (a picked document), copied to disk without ever holding it whole. */
    fun prepare(open: () -> InputStream?, dir: File, prefetchEdge: Int = 0): Prepared? {
        val raw = runCatching {
            val f = File.createTempFile("img", null, dir)
            val ok = open()?.use { input -> f.outputStream().use { input.copyTo(it, 64 * 1024) }; true } ?: false
            if (!ok) {
                f.delete()
                null
            } else {
                f
            }
        }.getOrNull() ?: return null
        return prepareFile(raw, prefetchEdge)
    }

    /**
     * Normalise the file at [raw] in place (or replace it with a normalised copy), returning its
     * final file and size, or null — with the file deleted — when it is not a readable image.
     * [prefetchEdge], when positive, also decodes the size step the first paint will want, so the
     * picture appears with its pixels rather than a placeholder.
     */
    fun prepareFile(raw: File, prefetchEdge: Int = 0): Prepared? {
        val path = raw.path
        if (ImageDecoder.isVector(path)) {
            val size = ImageDecoder.probeFile(path) ?: return raw.deleteAndNull()
            return Prepared(raw, size.width, size.height)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return raw.deleteAndNull()
        val exif = readExif(path)
        val uprightW = if (exif.swapsAxes) h else w
        val uprightH = if (exif.swapsAxes) w else h
        val (sw, sh) = ImportSizing.storedSize(uprightW, uprightH)
        val prepared = if (exif.isIdentity && sw == uprightW && sh == uprightH) {
            Prepared(raw, w, h)
        } else {
            rewrite(raw, w, h, exif, maxOf(sw, sh), bounds.outMimeType) ?: Prepared(raw, w, h)
        }
        if (prefetchEdge > 0) {
            val data = prepared.toImageData()
            runCatching { ImageBitmapCache.obtain(data, prefetchEdge.coerceAtMost(ImageBuckets.MAX_EDGE)) }
        }
        return prepared
    }

    private fun File.deleteAndNull(): Prepared? {
        delete()
        return null
    }

    /** The orientation tag of the file at [path], or upright when it has none or cannot say. */
    private fun readExif(path: String): ExifTransform = runCatching {
        val tag = android.media.ExifInterface(path).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        )
        ExifTransform.of(tag)
    }.getOrDefault(ExifTransform.NONE)

    /**
     * Decode [raw] at a long edge of [targetLong] (subsampled and scaled inside the decode), bake
     * [exif]'s turn and mirror into the pixels, and write the result next to it, replacing it.
     */
    private fun rewrite(raw: File, w: Int, h: Int, exif: ExifTransform, targetLong: Int, mime: String?): Prepared? {
        val nativeLong = maxOf(w, h)
        val sample = ImageBuckets.sampleSize(nativeLong, targetLong)
        val sampledLong = nativeLong / sample
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (sampledLong > targetLong) {
                inScaled = true
                inDensity = sampledLong
                inTargetDensity = targetLong
            }
        }
        val decoded = try {
            BitmapFactory.decodeFile(raw.path, opts)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        val upright = if (exif.isIdentity) {
            decoded
        } else {
            val m = Matrix()
            if (exif.mirror) m.postScale(-1f, 1f)
            if (exif.degrees != 0) m.postRotate(exif.degrees.toFloat())
            try {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } catch (_: OutOfMemoryError) {
                decoded.recycle()
                return null
            }
        }
        val opaque = !upright.hasAlpha() || mime == "image/jpeg" || mime == "image/heif" || mime == "image/heic"
        val out = runCatching { File.createTempFile("img", null, raw.parentFile) }.getOrNull()
        if (out == null) {
            upright.recycle()
            return null
        }
        val written = runCatching {
            out.outputStream().buffered().use { stream ->
                if (opaque) upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                else upright.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrDefault(false)
        val result = Prepared(out, upright.width, upright.height)
        upright.recycle()
        if (!written) {
            out.delete()
            return null
        }
        raw.delete()
        return result
    }

    private const val JPEG_QUALITY = 90
}
