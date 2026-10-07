package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import com.xnotes.core.stroke.Graphite
import java.util.concurrent.ConcurrentHashMap

/**
 * The paper's grain ([Graphite.tile]) as the Android canvas samples it: one bitmap and one repeating
 * shader over it, made the first time a pencil stroke is drawn and shared by every renderer after,
 * on every thread (neither is mutated once made).
 *
 * The bitmap carries the grain in its alpha alone, with black colour, and the ink's colour comes in
 * through a [PorterDuff.Mode.SRC_IN] colour filter: the filter's colour, kept where the grain is.
 * The shader's coordinates are the canvas's local ones, so the grain is anchored to the page (or the
 * canvas's world) and moves and scales with it, like the paper.
 */
internal object GraphiteGrain {

    val bitmap: Bitmap by lazy {
        val n = Graphite.TILE
        val alpha = Graphite.tile
        val pixels = IntArray(n * n) { (alpha[it].toInt() and 0xFF) shl 24 }
        Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).apply {
            // Zoomed far out a page is drawn small; mipmaps let the grain average into a tone there
            // instead of aliasing into a shimmer.
            setHasMipMap(true)
        }
    }

    val shader: BitmapShader by lazy { BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    /** One filter per ink colour, made once: a page in three inks needs three. Capped so a sweep
     *  through the colour picker cannot grow it without end. */
    private val filters = ConcurrentHashMap<Int, PorterDuffColorFilter>()

    fun tint(opaqueArgb: Int): PorterDuffColorFilter {
        filters[opaqueArgb]?.let { return it }
        if (filters.size >= MAX_FILTERS) filters.clear()
        return PorterDuffColorFilter(opaqueArgb, PorterDuff.Mode.SRC_IN).also { filters[opaqueArgb] = it }
    }

    private const val MAX_FILTERS = 64
}
