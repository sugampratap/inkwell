package com.xnotes.core.util

/**
 * A least-recently-used map bounded by the total *size* of its values rather than their count, so
 * one huge photo and forty thumbnails are weighed for what they actually cost. Not thread-safe;
 * the owner synchronizes. Pure Kotlin so its eviction order is unit-tested.
 *
 * [onEvict] hears about every entry that leaves through the budget (not through [remove]), which is
 * how an owner keeps a side index in step.
 */
class ByteLru<K : Any, V : Any>(
    budget: Long,
    private val sizeOf: (V) -> Long,
    private val onEvict: (K, V) -> Unit = { _, _ -> },
) {
    /** The ceiling; lowering it evicts at once. */
    var budget: Long = budget
        set(value) {
            field = value
            trim()
        }

    // Access order, so iteration starts at the least recently used entry.
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    /** The summed size of everything held. */
    var size: Long = 0
        private set

    val count: Int get() = map.size

    operator fun get(key: K): V? = map[key]

    /** Look without counting as a use, so a probe for alternatives does not reorder the list. */
    fun peek(key: K): V? = if (map.containsKey(key)) map.entries.first { it.key == key }.value else null

    fun containsKey(key: K): Boolean = map.containsKey(key)

    /**
     * Hold [value] under [key] as the most recent entry, evicting the least recent until the total
     * fits. A single value bigger than the whole budget is not kept at all (it would only evict
     * everything else and then itself); it is reported evicted straight away.
     */
    fun put(key: K, value: V) {
        val cost = sizeOf(value)
        map.remove(key)?.let { size -= sizeOf(it) }
        if (cost > budget) {
            onEvict(key, value)
            return
        }
        map[key] = value
        size += cost
        trim()
    }

    fun remove(key: K): V? = map.remove(key)?.also { size -= sizeOf(it) }

    fun clear() {
        map.clear()
        size = 0
    }

    /** Keys from least to most recently used. */
    fun keys(): List<K> = map.keys.toList()

    private fun trim() {
        val it = map.entries.iterator()
        while (size > budget && it.hasNext()) {
            val e = it.next()
            it.remove()
            size -= sizeOf(e.value)
            onEvict(e.key, e.value)
        }
    }
}

/**
 * The size steps decoded images are cached at. A draw asks for whatever long edge its destination
 * needs; snapping that up to a power of two means a pinch, a drag or a resize keeps hitting the same
 * few bitmaps instead of decoding afresh for every pixel of change. Never past the source's own
 * size, which is the last step of every ladder.
 */
object ImageBuckets {

    /** The smallest step; anything smaller is not worth a separate decode. */
    const val MIN_EDGE = 64

    /** The largest edge ever decoded for the screen. */
    const val MAX_EDGE = 4096

    /** The long edge to decode for a draw that wants [wanted] pixels of a [native]-pixel source. */
    fun edgeFor(wanted: Int, native: Int): Int {
        val n = native.coerceAtLeast(1)
        val w = wanted.coerceIn(1, MAX_EDGE)
        var p = MIN_EDGE
        while (p < w) p = p shl 1
        return if (p >= n) n.coerceAtMost(MAX_EDGE) else p
    }

    /**
     * Of the edges already held, the one to show while [wanted] is being decoded: the smallest that
     * is at least as big (scaling down looks right), else the biggest smaller one (soft beats blank),
     * or null when none is held.
     */
    fun bestStandIn(held: Collection<Int>, wanted: Int): Int? {
        var above: Int? = null
        var below: Int? = null
        for (e in held) {
            if (e >= wanted) {
                if (above == null || e < above) above = e
            } else if (below == null || e > below) {
                below = e
            }
        }
        return above ?: below
    }

    /**
     * The power-of-two subsample that leaves the long edge at least [target] — the cheap part of a
     * decode, done by the codec itself; the rest of the way is a scale during the same decode.
     */
    fun sampleSize(nativeLong: Int, target: Int): Int {
        var s = 1
        while (nativeLong / (s * 2) >= target) s *= 2
        return s
    }
}

/**
 * The turn and mirror an EXIF orientation tag asks for, as the value an upright copy of the image
 * needs: rotate clockwise by [degrees] after mirroring left↔right when [mirror]. Tag values follow
 * the TIFF spec (1 = as stored … 8 = turned 90° counter-clockwise); anything else reads as upright.
 */
data class ExifTransform(val degrees: Int, val mirror: Boolean) {
    val isIdentity: Boolean get() = degrees == 0 && !mirror

    /** True when the upright image is the stored one with width and height swapped. */
    val swapsAxes: Boolean get() = degrees == 90 || degrees == 270

    companion object {
        val NONE = ExifTransform(0, false)

        fun of(tag: Int): ExifTransform = when (tag) {
            2 -> ExifTransform(0, true) // FLIP_HORIZONTAL
            3 -> ExifTransform(180, false) // ROTATE_180
            4 -> ExifTransform(180, true) // FLIP_VERTICAL = mirror, then a half turn
            5 -> ExifTransform(270, true) // TRANSPOSE: mirror, then a quarter turn back
            6 -> ExifTransform(90, false) // ROTATE_90
            7 -> ExifTransform(90, true) // TRANSVERSE: mirror, then a quarter turn on
            8 -> ExifTransform(270, false) // ROTATE_270
            else -> NONE
        }
    }
}

/**
 * How big a picture is stored once imported: the long edge is brought down to [MAX_STORED_EDGE]
 * when it is over, and left alone otherwise, so a camera's 50-megapixel frame does not cost
 * 200 MB to decode every time it is drawn, and an ordinary photo keeps its own bytes.
 */
object ImportSizing {
    const val MAX_STORED_EDGE = 4096

    /** The stored size for a [w]×[h] source (already upright), keeping its aspect. */
    fun storedSize(w: Int, h: Int, maxEdge: Int = MAX_STORED_EDGE): Pair<Int, Int> {
        val long = maxOf(w, h)
        if (long <= maxEdge || long <= 0) return w to h
        val s = maxEdge.toDouble() / long
        return maxOf(1, Math.round(w * s).toInt()) to maxOf(1, Math.round(h * s).toInt())
    }
}
