package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.xnotes.core.model.ImageData
import com.xnotes.core.util.ByteLru
import com.xnotes.core.util.ImageBuckets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Decoded raster images, shared by everything that paints them: the page caches, the lifted
 * (selected) overlay, the sharp viewport, thumbnails and exports.
 *
 * Before this, every one of those decoded the file afresh on every paint, and a selected photo was
 * re-decoded on the UI thread on every frame of a drag, which is what made a note with pictures
 * crawl. Now a decode happens once per size step ([ImageBuckets]) and is kept, bounded by bytes
 * (an eighth of the heap, so a note full of photos cannot crowd out the page caches).
 *
 * The UI thread never decodes. A paint there takes what is already held: the right size, or the
 * nearest size held as a stand-in, or nothing (the caller draws a placeholder), and the missing size
 * is decoded on a background thread. When it lands, [listeners] hear the file's path on the main
 * thread and repaint whatever showed the stand-in. Paints on any other thread (page-cache builds,
 * exports) decode inline, since they are already off the UI thread and want the real pixels.
 *
 * SVGs are not held here: [ImageDecoder] keeps its own rasterization cache for them, at sizes a
 * vector needs. Bitmaps handed out are never recycled; eviction leaves them to the collector, so a
 * paint still holding one is safe.
 */
object ImageBitmapCache {

    private data class Key(val path: String, val edge: Int)

    private val lock = Any()

    /** Edges held per file, so a miss can find a stand-in without scanning every entry. */
    private val heldEdges = HashMap<String, MutableSet<Int>>()

    private val lru = ByteLru<Key, Bitmap>(
        budget = (Runtime.getRuntime().maxMemory() / 8).coerceIn(24L shl 20, 160L shl 20),
        sizeOf = { it.allocationByteCount.toLong() },
        onEvict = { key, _ -> forget(key) },
    )

    /** Decodes queued for the UI thread's misses; one each, however often the frame asks. */
    private val inFlight = HashSet<Key>()

    /** Decodes that failed (an unreadable file), so a frame loop does not retry them forever. */
    private val failed = HashSet<Key>()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Two threads: one slow camera JPEG should not hold up every other picture on the page.
    private val decoder = Executors.newFixedThreadPool(2) { r ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "xnotes-image-decode").apply { isDaemon = true }
    }

    /** Told the path of a file whose decode just landed, on the main thread. */
    val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    /** Bytes held, for the debug readout. */
    val residentBytes: Long get() = synchronized(lock) { lru.size }

    private fun forget(key: Key) {
        heldEdges[key.path]?.let { set ->
            set.remove(key.edge)
            if (set.isEmpty()) heldEdges.remove(key.path)
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() === Looper.getMainLooper()

    /**
     * A decode of [image] whose long edge serves a draw [wantedLongEdge] device pixels across (for the
     * *whole* source, so a crop asks for more), or null when nothing is held yet and this is the UI
     * thread. Off the UI thread a miss decodes now and is kept.
     */
    fun obtain(image: ImageData, wantedLongEdge: Int): Bitmap? {
        val path = image.file.path
        val native = maxOf(image.width, image.height)
        val edge = ImageBuckets.edgeFor(wantedLongEdge, native)
        val key = Key(path, edge)
        synchronized(lock) { lru[key] }?.let { return it }
        if (!isMainThread()) return decodeAndKeep(key)
        val standIn = synchronized(lock) {
            val held = heldEdges[path]
            val best = held?.let { ImageBuckets.bestStandIn(it, edge) }
            best?.let { lru[Key(path, it)] }
        }
        request(key)
        return standIn
    }

    /** Whatever size of [image] is held, the largest, without decoding or queueing anything. */
    fun peekAny(image: ImageData): Bitmap? = synchronized(lock) {
        val path = image.file.path
        heldEdges[path]?.maxOrNull()?.let { lru.peek(Key(path, it)) }
    }

    /** Decode [image] for [wantedLongEdge] in the background, so the first paint of it already hits. */
    fun prefetch(image: ImageData, wantedLongEdge: Int) {
        val edge = ImageBuckets.edgeFor(wantedLongEdge, maxOf(image.width, image.height))
        request(Key(image.file.path, edge))
    }

    /** Drop every size of the file at [path], after it was replaced on disk. */
    fun evict(path: String) = synchronized(lock) {
        heldEdges.remove(path)?.forEach { lru.remove(Key(path, it)) }
        failed.removeAll { it.path == path }
    }

    private fun request(key: Key) {
        synchronized(lock) {
            if (lru.containsKey(key) || key in failed || !inFlight.add(key)) return
        }
        decoder.execute {
            val ok = runCatching { decodeAndKeep(key) }.getOrNull() != null
            synchronized(lock) {
                inFlight.remove(key)
                if (!ok && failed.size < MAX_FAILED) failed.add(key)
            }
            if (ok) mainHandler.post { for (l in listeners) l(key.path) }
        }
    }

    private fun decodeAndKeep(key: Key): Bitmap? {
        val bmp = decode(key.path, key.edge) ?: return null
        synchronized(lock) {
            // Another thread may have got there first; keep one copy, hand out the kept one.
            lru[key]?.let { return it }
            lru.put(key, bmp)
            if (lru.containsKey(key)) heldEdges.getOrPut(key.path) { HashSet() }.add(key.edge)
        }
        return bmp
    }

    /**
     * Decode the raster at [path] to a long edge of [edge]: the codec's power-of-two subsample for
     * the bulk of the reduction, then a density scale inside the same decode for the rest, so the
     * result is exactly the size wanted with no second full-size bitmap made to scale it.
     */
    fun decode(path: String, edge: Int): Bitmap? {
        if (ImageDecoder.isVector(path)) return ImageDecoder.decodeSampledFile(path, edge, edge)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val nativeLong = maxOf(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = ImageBuckets.sampleSize(nativeLong, edge)
        val sampledLong = nativeLong / sample
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (sampledLong > edge) {
                inScaled = true
                inDensity = sampledLong
                inTargetDensity = edge
            }
        }
        return try {
            BitmapFactory.decodeFile(path, opts)
        } catch (_: OutOfMemoryError) {
            synchronized(lock) { lru.budget = lru.budget / 2 }
            null
        }
    }

    private const val MAX_FAILED = 256
}
