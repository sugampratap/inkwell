package com.xnotes.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import com.xnotes.core.model.ImageData
import com.xnotes.platform.ImageBitmapCache
import com.xnotes.platform.ImageDecoder
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * GL textures for placed images, decoded on demand at the size the current zoom needs.
 *
 * Images keep their encoded bytes on disk and are decoded only when drawn, which is what keeps a
 * canvas full of photographs off the heap. That property has to survive the move to GL, so nothing
 * here holds a full-resolution bitmap: a decode produces a texture at the size the screen can
 * actually show, the bitmap is recycled immediately, and the texture is re-made only when the zoom
 * has moved far enough that the one in hand is visibly coarse.
 *
 * Decoding runs off the render thread, because it reads a file and can take tens of milliseconds.
 * The upload has to happen with the context current, so a finished decode is queued and picked up
 * at the start of the next frame.
 *
 * A picture seen for the first time is asked for twice: a small [PREVIEW_EDGE] decode first, which
 * lands quickly and shows a soft version straight away, then the size the zoom wants. Until even
 * the preview lands, [placeholder] is a quiet grey that holds the space. Every texture carries a
 * full mipmap chain, so a page of photos zoomed far out samples a pre-shrunk level instead of
 * shimmering as the view moves.
 */
class TextureCache(private val budgetBytes: Long = DEFAULT_BUDGET_BYTES) {

    private class Entry(
        val texture: Int,
        val width: Int,
        val height: Int,
        /** Longest edge, in pixels, this texture was decoded for. */
        val decodedFor: Int,
        var lastUsedFrame: Long,
    ) {
        // The mipmap chain adds a third to the base level.
        val bytes: Long get() = width.toLong() * height * 4 * 4 / 3
    }

    private class Pending(val image: ImageData, val bitmap: Bitmap, val decodedFor: Int, val owned: Boolean)

    private val entries = IdentityHashMap<ImageData, Entry>()
    private val ready = ConcurrentLinkedQueue<Pending>()
    private val decoding = IdentityHashMap<ImageData, Int>()

    private var contextGen = -1
    private var frame = 0L

    /** The 1x1 grey shown where a picture has not decoded yet; 0 until first asked for. */
    private var placeholderTexture = 0

    /** Bytes the live textures occupy, for the debug readout. */
    var residentBytes: Long = 0
        private set

    /** Images whose decode has been asked for and not yet arrived. */
    val pendingCount: Int get() = decoding.size

    val textureCount: Int get() = entries.size

    /** A new context: every texture name is gone, so drop the lot rather than delete it. */
    fun onContextCreated(gen: Int) {
        contextGen = gen
        entries.clear()
        decoding.clear()
        while (true) (ready.poll() ?: break).let { if (it.owned) it.bitmap.recycle() }
        residentBytes = 0
        placeholderTexture = 0
    }

    /** Take in whatever finished decoding since the last frame. Call with the context current. */
    fun uploadPending() {
        while (true) {
            val next = ready.poll() ?: return
            // The preview landing must not forget that the full size is still on its way.
            val awaited = decoding[next.image]
            if (awaited != null && awaited <= next.decodedFor) decoding.remove(next.image)
            if (next.bitmap.isRecycled) continue
            val held = entries[next.image]
            if (held != null && held.decodedFor >= next.decodedFor) {
                // A late preview behind the real thing: nothing to gain from it.
                if (next.owned) next.bitmap.recycle()
                continue
            }
            val existing = entries.remove(next.image)
            if (existing != null) {
                residentBytes -= existing.bytes
                GLES30.glDeleteTextures(1, intArrayOf(existing.texture), 0)
            }
            val name = IntArray(1)
            GLES30.glGenTextures(1, name, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, next.bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            val entry = Entry(name[0], next.bitmap.width, next.bitmap.height, next.decodedFor, frame)
            entries[next.image] = entry
            residentBytes += entry.bytes
            if (next.owned) next.bitmap.recycle()
        }
    }

    /**
     * The texture for [image] at a size that suits [wantedLongEdge] device pixels, or 0 when one is
     * not ready yet. Asks for a decode when there is nothing, or when what is held is coarse enough
     * that the difference would show.
     */
    fun textureFor(image: ImageData, wantedLongEdge: Int, decodeOn: (Runnable) -> Unit): Int {
        val target = targetSize(wantedLongEdge)
        val entry = entries[image]
        if (entry != null) {
            entry.lastUsedFrame = frame
            // Re-decode only on a real step up: re-uploading for every small zoom change would
            // spend more time decoding than drawing.
            if (target > entry.decodedFor && entry.decodedFor < maxEdge(image)) request(image, target, decodeOn)
            return entry.texture
        }
        // First sight: a quick soft preview, then the real size behind it on the same thread.
        if (target > PREVIEW_EDGE && maxEdge(image) > PREVIEW_EDGE && decoding[image] == null) {
            request(image, PREVIEW_EDGE, decodeOn)
        }
        request(image, target, decodeOn)
        return 0
    }

    /** A 1x1 quiet grey for a picture still decoding. Call with the context current. */
    fun placeholder(): Int {
        if (placeholderTexture != 0) return placeholderTexture
        val name = IntArray(1)
        GLES30.glGenTextures(1, name, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        val px = java.nio.ByteBuffer.allocateDirect(4)
        px.put(PLACEHOLDER_RGBA)
        px.flip()
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
        placeholderTexture = name[0]
        return placeholderTexture
    }

    /** Mark the start of a frame and evict whatever the budget no longer covers. */
    fun beginFrame() {
        frame++
        if (residentBytes <= budgetBytes) return
        // Least recently drawn first: what is off screen goes before what is on it.
        val ordered = entries.entries.sortedBy { it.value.lastUsedFrame }
        for ((image, entry) in ordered) {
            if (residentBytes <= budgetBytes) break
            if (entry.lastUsedFrame == frame) continue // in this very frame, so still needed
            GLES30.glDeleteTextures(1, intArrayOf(entry.texture), 0)
            residentBytes -= entry.bytes
            entries.remove(image)
        }
    }

    /** Drop everything, deleting the textures. Call with the context current. */
    fun release() {
        for (entry in entries.values) GLES30.glDeleteTextures(1, intArrayOf(entry.texture), 0)
        entries.clear()
        residentBytes = 0
        if (placeholderTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(placeholderTexture), 0)
        placeholderTexture = 0
    }

    private fun request(image: ImageData, target: Int, decodeOn: (Runnable) -> Unit) {
        val already = decoding[image]
        if (already != null && already >= target) return
        decoding[image] = target
        val gen = contextGen
        decodeOn(
            Runnable {
                val edge = target.coerceAtMost(maxEdge(image))
                // A vector's raster comes from the decoder's shared cache and must not be recycled;
                // a raster decode is this cache's own, exact-size, and is recycled after upload.
                val vector = ImageDecoder.isVector(image.file.path)
                val bitmap = runCatching { ImageBitmapCache.decode(image.file.path, edge) }.getOrNull()
                if (bitmap == null || gen != contextGen) {
                    if (!vector) bitmap?.recycle()
                    return@Runnable
                }
                ready.add(Pending(image, bitmap, target, owned = !vector))
            },
        )
    }

    /**
     * The decode size for a wanted edge, rounded up to a power of two. Rounding is what stops a
     * slow pinch from re-decoding on every frame: the target only changes at doublings.
     */
    private fun targetSize(wantedLongEdge: Int): Int {
        var size = MIN_EDGE
        while (size < wantedLongEdge && size < MAX_EDGE) size *= 2
        return size
    }

    private fun maxEdge(image: ImageData): Int = maxOf(image.width, image.height).coerceAtLeast(1)

    companion object {
        /** Texture memory the cache aims to stay under. */
        const val DEFAULT_BUDGET_BYTES = 96L * 1024 * 1024

        /** Smallest and largest decode edge, in pixels. */
        const val MIN_EDGE = 128
        const val MAX_EDGE = 4096

        /** The quick first decode of a picture not seen before. */
        const val PREVIEW_EDGE = 128

        /** Mid grey at a low alpha: reads as "something is coming" on any paper. */
        private val PLACEHOLDER_RGBA = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 46)
    }
}
