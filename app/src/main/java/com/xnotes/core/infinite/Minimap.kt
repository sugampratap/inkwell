package com.xnotes.core.infinite

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect

/**
 * Where the minimap sits and what it maps.
 *
 * On an unbounded canvas a minimap cannot show "the document", because there is no edge to it. It
 * shows the extent of what has actually been drawn, unioned with where the viewport currently is,
 * so panning off into blank space still leaves the marker somewhere meaningful rather than pinning
 * it to the border.
 *
 * Pure geometry, so the mapping both ways is unit-testable and the renderer only draws.
 */
object Minimap {

    /** Panel size and inset from the corner, in device pixels. */
    const val WIDTH_PX = 168.0
    const val HEIGHT_PX = 120.0
    const val MARGIN_PX = 14.0

    /** Space left inside the panel around the mapped extent. */
    const val PAD_PX = 8.0

    /** The panel's own rectangle in device pixels: bottom-right of the viewport, clear of a floating bar. */
    fun panel(viewportW: Int, viewportH: Int, insetRight: Double = 0.0, insetBottom: Double = 0.0): Rect = Rect(
        viewportW - insetRight - WIDTH_PX - MARGIN_PX,
        viewportH - insetBottom - HEIGHT_PX - MARGIN_PX,
        WIDTH_PX,
        HEIGHT_PX,
    )

    /**
     * The content region the panel maps: everything drawn, plus wherever the view is now, so the
     * viewport marker is always inside the panel.
     */
    fun mappedExtent(contentBounds: Rect?, visible: Rect): Rect {
        val union = contentBounds?.union(visible) ?: visible
        // A degenerate extent (a single dot, an empty canvas) still needs an area to map into.
        val w = if (union.w > 1e-6) union.w else visible.w.coerceAtLeast(1.0)
        val h = if (union.h > 1e-6) union.h else visible.h.coerceAtLeast(1.0)
        return Rect(union.centerX - w / 2.0, union.centerY - h / 2.0, w, h)
    }

    /** Device pixels per content pixel inside the panel, fitting [extent] with [PAD_PX] to spare. */
    fun scaleFor(extent: Rect, panel: Rect): Double {
        if (extent.w <= 0.0 || extent.h <= 0.0) return 1.0
        val availW = (panel.w - 2 * PAD_PX).coerceAtLeast(1.0)
        val availH = (panel.h - 2 * PAD_PX).coerceAtLeast(1.0)
        return minOf(availW / extent.w, availH / extent.h)
    }

    /** A content point mapped into device pixels inside the panel. */
    fun toPanel(p: Pt, extent: Rect, panel: Rect): Pt {
        val scale = scaleFor(extent, panel)
        return Pt(
            panel.centerX + (p.x - extent.centerX) * scale,
            panel.centerY + (p.y - extent.centerY) * scale,
        )
    }

    /** A content rectangle mapped into device pixels inside the panel. */
    fun toPanel(r: Rect, extent: Rect, panel: Rect): Rect {
        val scale = scaleFor(extent, panel)
        val topLeft = toPanel(Pt(r.left, r.top), extent, panel)
        return Rect(topLeft.x, topLeft.y, r.w * scale, r.h * scale)
    }

    /** The content point a tap inside the panel means, so tapping the map jumps the view there. */
    fun toContent(p: Pt, extent: Rect, panel: Rect): Pt {
        val scale = scaleFor(extent, panel)
        if (scale <= 0.0) return Pt(extent.centerX, extent.centerY)
        return Pt(
            extent.centerX + (p.x - panel.centerX) / scale,
            extent.centerY + (p.y - panel.centerY) / scale,
        )
    }

    /**
     * [panel], the visible rect, [mappedExtent] and [scaleFor] for one frame, held as plain numbers
     * so the renderer can redo them every frame without allocating. Every value is worked out with
     * exactly the arithmetic the [Rect] forms use, so the two agree to the bit.
     */
    class Layout {
        var panelX = 0.0
            private set
        var panelY = 0.0
            private set
        var panelW = WIDTH_PX
            private set
        var panelH = HEIGHT_PX
            private set
        var extentX = 0.0
            private set
        var extentY = 0.0
            private set
        var extentW = 1.0
            private set
        var extentH = 1.0
            private set
        var scale = 1.0
            private set

        /** The viewport's own marker in device pixels: [toPanel] of the visible rect. */
        var viewX = 0.0
            private set
        var viewY = 0.0
            private set
        var viewW = 0.0
            private set
        var viewH = 0.0
            private set

        val panelCenterX: Double get() = panelX + panelW / 2.0
        val panelCenterY: Double get() = panelY + panelH / 2.0
        val extentCenterX: Double get() = extentX + extentW / 2.0
        val extentCenterY: Double get() = extentY + extentH / 2.0

        fun update(
            viewportW: Int,
            viewportH: Int,
            insetRight: Double,
            insetBottom: Double,
            contentBounds: Rect?,
            scrollX: Double,
            scrollY: Double,
            zoom: Double,
        ) {
            // [panel].
            panelX = viewportW - insetRight - WIDTH_PX - MARGIN_PX
            panelY = viewportH - insetBottom - HEIGHT_PX - MARGIN_PX
            panelW = WIDTH_PX
            panelH = HEIGHT_PX
            // The visible rect, Rect(scrollX, scrollY, viewportW / zoom, viewportH / zoom).
            val visW = viewportW / zoom
            val visH = viewportH / zoom
            // [mappedExtent]: the union with what has been drawn, kept non-empty, then centred.
            var ux = scrollX
            var uy = scrollY
            var uw = visW
            var uh = visH
            if (contentBounds != null) {
                val l = kotlin.math.min(contentBounds.left, scrollX)
                val t = kotlin.math.min(contentBounds.top, scrollY)
                val r = kotlin.math.max(contentBounds.right, scrollX + visW)
                val b = kotlin.math.max(contentBounds.bottom, scrollY + visH)
                ux = l
                uy = t
                uw = r - l
                uh = b - t
            }
            val w = if (uw > 1e-6) uw else visW.coerceAtLeast(1.0)
            val h = if (uh > 1e-6) uh else visH.coerceAtLeast(1.0)
            extentX = (ux + uw / 2.0) - w / 2.0
            extentY = (uy + uh / 2.0) - h / 2.0
            extentW = w
            extentH = h
            // [scaleFor].
            scale = if (extentW <= 0.0 || extentH <= 0.0) {
                1.0
            } else {
                val availW = (panelW - 2 * PAD_PX).coerceAtLeast(1.0)
                val availH = (panelH - 2 * PAD_PX).coerceAtLeast(1.0)
                kotlin.math.min(availW / extentW, availH / extentH)
            }
            // [toPanel] of the visible rect.
            viewX = panelCenterX + (scrollX - extentCenterX) * scale
            viewY = panelCenterY + (scrollY - extentCenterY) * scale
            viewW = visW * scale
            viewH = visH * scale
        }
    }

    /**
     * Every item's marker as one batch of triangles in clip space, so the map of a whole canvas
     * draws in one call rather than in a handful of calls per item every frame.
     *
     * Each marker is the two triangles its single quad was drawn as, at the very same clip
     * coordinates, in the order the items are added. One draw blends its triangles in submission
     * order, so overlapping markers composite exactly as they did one call at a time. The batch is
     * rebuilt only when something it was built from changes: the content, the mapping or the
     * viewport.
     */
    class Dots {
        /** x,y pairs in clip space; [vertexCount] of them are live. */
        var vertices = FloatArray(0)
            private set
        var vertexCount = 0
            private set

        /** Bumped by every rebuild, so whoever uploads the batch can tell it has changed. */
        var version = 0
            private set

        private var built = false
        private var builtRevision = 0
        private var builtW = 0
        private var builtH = 0
        private var panelCX = 0.0
        private var panelCY = 0.0
        private var extentCX = 0.0
        private var extentCY = 0.0
        private var builtScale = 0.0

        /** Whether the batch is already what [layout] would build for [revision] of the content. */
        fun isCurrent(revision: Int, layout: Layout, viewportW: Int, viewportH: Int): Boolean =
            built && revision == builtRevision && viewportW == builtW && viewportH == builtH &&
                layout.panelCenterX == panelCX && layout.panelCenterY == panelCY &&
                layout.extentCenterX == extentCX && layout.extentCenterY == extentCY &&
                layout.scale == builtScale

        /** Start a rebuild for [layout]; then [add] each item's bounds in draw order. */
        fun begin(revision: Int, layout: Layout, viewportW: Int, viewportH: Int) {
            built = true
            builtRevision = revision
            builtW = viewportW
            builtH = viewportH
            panelCX = layout.panelCenterX
            panelCY = layout.panelCenterY
            extentCX = layout.extentCenterX
            extentCY = layout.extentCenterY
            builtScale = layout.scale
            vertexCount = 0
            version++
        }

        /**
         * One item's marker: [toPanel] of its [bounds], grown to at least [minPx] across so a thin
         * stroke still shows, put into clip space the way a single filled rect is.
         */
        fun add(bounds: Rect, minPx: Double) {
            val x = panelCX + (bounds.left - extentCX) * builtScale
            val y = panelCY + (bounds.top - extentCY) * builtScale
            val w = (bounds.w * builtScale).coerceAtLeast(minPx)
            val h = (bounds.h * builtScale).coerceAtLeast(minPx)
            if (w <= 0.0 || h <= 0.0) return
            val x0 = (x / builtW * 2.0 - 1.0).toFloat()
            val y0 = (1.0 - y / builtH * 2.0).toFloat()
            val x1 = ((x + w) / builtW * 2.0 - 1.0).toFloat()
            val y1 = (1.0 - (y + h) / builtH * 2.0).toFloat()
            if (2 * (vertexCount + 6) > vertices.size) {
                vertices = vertices.copyOf(maxOf(2 * (vertexCount + 6), vertices.size * 2, 96))
            }
            // The strip (x0,y0) (x1,y0) (x0,y1) (x1,y1) the quad was drawn as, as its two
            // triangles: the same corners and the same shared diagonal.
            val v = vertices
            var i = 2 * vertexCount
            v[i++] = x0; v[i++] = y0
            v[i++] = x1; v[i++] = y0
            v[i++] = x0; v[i++] = y1
            v[i++] = x0; v[i++] = y1
            v[i++] = x1; v[i++] = y0
            v[i++] = x1; v[i] = y1
            vertexCount += 6
        }
    }
}
