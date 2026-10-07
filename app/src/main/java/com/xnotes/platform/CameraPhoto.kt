package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns a photo from the camera (or a scanner page) into bytes the image insert can take as they
 * are. The image renderer draws pixels the way they are stored and ignores the EXIF orientation
 * tag, while a camera app usually stores the sensor's landscape frame plus a tag saying "turn me";
 * a portrait shot would land on its side. So a tagged photo is decoded, turned upright and
 * re-encoded once here (as is one past [MAX_EDGE]), and an untagged one passes through untouched.
 * Run off the main thread.
 */
object CameraPhoto {

    /** Long-edge cap for a re-encoded photo: sharp on a page at any zoom the editor allows, without
     *  carrying a 50-megapixel frame around in the note. */
    private const val MAX_EDGE = 4096

    /** [file]'s bytes, upright. Null when it is not a readable image. */
    fun uprightBytes(file: File): ByteArray? {
        if (!file.isFile || file.length() == 0L) return null
        val rotation = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
        }.getOrDefault(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (rotation == 0 && longEdge <= MAX_EDGE) return runCatching { file.readBytes() }.getOrNull()
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val turned = if (rotation == 0) {
            decoded
        } else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        return try {
            ByteArrayOutputStream(turned.byteCount / 6).use { out ->
                turned.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
        } finally {
            turned.recycle()
        }
    }
}
