package com.xnotes.core.pal

import com.xnotes.core.model.Rgba

/**
 * An off-screen ARGB pixel buffer (spec 01 §2): the per-page ink cache,
 * thumbnails and decoded images. A host MUST allow drawing
 * additional content into an existing surface (a finished stroke is *appended*
 * to its page cache rather than re-rendering the whole page).
 */
interface RasterSurface {
    val width: Int
    val height: Int

    /** Density tag so a surface rendered at higher pixel density blits at the
     *  correct logical size. */
    val devicePixelRatio: Double

    /** Clear to a solid colour, including fully transparent. */
    fun fill(color: Rgba)

    /** A [Renderer] that draws **into** this surface. */
    fun renderer(): Renderer

    /** Release backing memory. Optional; safe to call more than once. */
    fun recycle() {}
}

/** Allocates [RasterSurface]s — the host-provided factory used by caches/thumbnails. */
interface SurfaceFactory {
    fun create(widthPx: Int, heightPx: Int, devicePixelRatio: Double = 1.0): RasterSurface

    /**
     * A surface that only has to hold coverage: whatever is drawn into it keeps its alpha and may
     * lose its colour, and blitted it gives that alpha back in whatever colour the host likes. A
     * quarter of [create]'s memory where the host can do it (one byte a pixel rather than four);
     * the default is simply a full surface, which holds coverage too.
     */
    fun createMask(widthPx: Int, heightPx: Int, devicePixelRatio: Double = 1.0): RasterSurface =
        create(widthPx, heightPx, devicePixelRatio)
}
