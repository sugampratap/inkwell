package com.xnotes.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.pal.Renderer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Turning a selection (or one picture) into an image file that leaves the app: copied to the
 * system clipboard, shared, or saved where the user picks. Shared by both editors; every function
 * here does file or pixel work and is meant for a background thread.
 */
object SelectionImageExport {

    /** Blank space kept round the selection, in content units, so ink does not touch the edge. */
    private const val PAD = 16.0

    /** Longest side of a rendered selection, in pixels. */
    private const val MAX_SIDE = 4096.0

    /**
     * Paint a selection that covers [bounds] (content space) at twice its size, capped, over the
     * paper it sits on: what is seen, so light ink on a dark note stays legible wherever it is
     * pasted. [paint] draws the items in content space.
     */
    fun render(bounds: Rect, paper: Rgba?, paint: (Renderer) -> Unit): Bitmap? {
        val area = bounds.outset(PAD)
        if (area.w <= 0.0 || area.h <= 0.0) return null
        val res = minOf(2.0, MAX_SIDE / area.w, MAX_SIDE / area.h).coerceAtLeast(0.05)
        val w = ceil(area.w * res).toInt().coerceIn(1, MAX_SIDE.toInt())
        val h = ceil(area.h * res).toInt().coerceIn(1, MAX_SIDE.toInt())
        return runCatching {
            val surface = AndroidRasterSurface.create(w, h)
            if (paper != null) surface.fill(paper)
            val r = surface.renderer()
            r.scale(res, res)
            r.translate(-area.left, -area.top)
            paint(r)
            surface.bitmap
        }.getOrNull()
    }

    fun png(bmp: Bitmap): ByteArray = java.io.ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    /** Write [png] to the FileProvider-shared clipboard dir and make it the primary clip. */
    fun toClipboard(context: Context, png: ByteArray): Boolean = runCatching {
        val dir = File(context.cacheDir, "clipboard").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "selection-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { it.write(png) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // The clipboard service reads it on the main thread's behalf; setting it from here is fine.
        cm.setPrimaryClip(ClipData.newUri(context.contentResolver, "Inkwell selection", uri))
        true
    }.getOrDefault(false)

    /** A share-sheet intent for [png], written to the FileProvider-shared share dir. */
    fun shareIntent(context: Context, png: ByteArray, name: String, title: String): Intent? = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, safeName(name) + ".png")
        FileOutputStream(file).use { it.write(png) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    /** Write [bytes] to a document the user picked. */
    fun write(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
    }.getOrDefault(false)

    /** Copy [file] to a document the user picked, without holding it whole. */
    fun copy(context: Context, file: File, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    }.getOrDefault(false)

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "selection" }.take(80)

    /** What "Save image" writes for [item]: its own file when untouched, else the picture as shown. */
    class ImageSave(val mime: String, val fileName: String, val write: (Context, Uri) -> Boolean)

    /**
     * Plan saving one picture. An image nobody has cropped, turned or mirrored is saved as the
     * file it is (same bytes, its own type). An edited one is saved as it is shown, upright: the
     * crop, mirror and quarter turn baked in (the free angle is left out, since a tilted rectangle
     * of pixels would only add blank corners), as a JPEG at quality 92 when opaque, else a PNG.
     */
    fun planImageSave(item: ImageItem, stem: String): ImageSave {
        val image = item.image
        val path = image.file.path
        val vector = ImageDecoder.isVector(path)
        val edited = !item.edit.isIdentity || com.xnotes.core.model.ImageGeometry.normOrientation(item.orientation) != 0
        if (!edited || vector) {
            val (mime, ext) = when {
                vector -> "image/svg+xml" to "svg"
                else -> sniff(image.file)
            }
            return ImageSave(mime, "$stem.$ext") { ctx, uri -> copy(ctx, image.file, uri) }
        }
        val opaque = sniff(image.file).first == "image/jpeg"
        val mime = if (opaque) "image/jpeg" else "image/png"
        val orientation = item.orientation
        val edit = item.edit
        return ImageSave(mime, "$stem.${if (opaque) "jpg" else "png"}") { ctx, uri ->
            val bmp = renderShown(image, orientation, edit) ?: return@ImageSave false
            val bytes = java.io.ByteArrayOutputStream().use { out ->
                if (opaque) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            bmp.recycle()
            write(ctx, uri, bytes)
        }
    }

    /** The picture as shown (cropped, mirrored, quarter-turned), at up to [ImageBuckets.MAX_EDGE]. */
    private fun renderShown(image: com.xnotes.core.model.ImageData, orientation: Int, edit: com.xnotes.core.model.ImageEdit): Bitmap? {
        val crop = edit.effectiveCrop
        val (dw, dh) = com.xnotes.core.model.ImageGeometry.displaySize(image.width, image.height, crop, orientation)
        val cap = com.xnotes.core.util.ImageBuckets.MAX_EDGE.toDouble()
        val s = minOf(1.0, cap / maxOf(dw, dh))
        val w = (dw * s).toInt().coerceAtLeast(1)
        val h = (dh * s).toInt().coerceAtLeast(1)
        return runCatching {
            val surface = AndroidRasterSurface.create(w, h)
            val r = surface.renderer()
            r.drawImage(image, Rect(0.0, 0.0, w.toDouble(), h.toDouble()), orientation, 0.0, edit)
            surface.bitmap
        }.getOrNull()
    }

    /** A raster file's type and extension, from its magic number. */
    private fun sniff(file: File): Pair<String, String> {
        val head = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        fun at(i: Int) = if (i < n) head[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg" to "jpg"
            at(0) == 0x89 && at(1) == 0x50 -> "image/png" to "png"
            at(0) == 0x47 && at(1) == 0x49 -> "image/gif" to "gif"
            at(0) == 0x52 && at(1) == 0x49 && at(8) == 0x57 -> "image/webp" to "webp"
            at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70 -> "image/heic" to "heic"
            at(0) == 0x42 && at(1) == 0x4D -> "image/bmp" to "bmp"
            else -> "image/png" to "png"
        }
    }
}
