package com.xnotes.ui

import com.xnotes.core.model.PageEdge
import com.xnotes.core.model.PageMargins
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.model.Rgba
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Extra paper on each edge, as a fraction of the page's width (left, right) or height (top, bottom). */
internal data class EdgeFractions(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    val any: Boolean get() = left > 0.0 || top > 0.0 || right > 0.0 || bottom > 0.0
}

/** The sums Page setup and New notebook draw with. No Compose, so they are tested on the JVM. */
internal object PaperMaths {

    /** Page setup's Edge control, in the mockup's order. */
    val EDGE_ORDER = listOf(PageEdge.LEFT, PageEdge.TOP, PageEdge.RIGHT, PageEdge.BOTTOM)

    /**
     * What one edge shows at a scope: on All pages the note's own (unset is none); on This page the
     * page's own, else the note's it inherits, else none.
     */
    fun edgeValue(allPages: Boolean, doc: PageMargins, page: PageMargins, edge: PageEdge): Double =
        (if (allPages) doc.edge(edge) else page.edge(edge) ?: doc.edge(edge)) ?: 0.0

    /** Every edge as [edgeValue] reads it. */
    fun effectiveMargins(allPages: Boolean, doc: PageMargins, page: PageMargins) = EdgeFractions(
        left = edgeValue(allPages, doc, page, PageEdge.LEFT),
        top = edgeValue(allPages, doc, page, PageEdge.TOP),
        right = edgeValue(allPages, doc, page, PageEdge.RIGHT),
        bottom = edgeValue(allPages, doc, page, PageEdge.BOTTOM),
    )

    /** The page in mm once [m] adds its paper. The margin is ordinary page, so the template spans it. */
    fun withMargins(pageMm: Pair<Double, Double>, m: EdgeFractions): Pair<Double, Double> =
        pageMm.first * (1 + m.left + m.right) to pageMm.second * (1 + m.top + m.bottom)

    /** [pageMm] fitted in a [boxW] x [boxH] box at its own proportions: as wide as it can be, unless that is too tall. */
    fun fit(pageMm: Pair<Double, Double>, boxW: Float, boxH: Float): Pair<Float, Float> {
        val ratio = (pageMm.second / pageMm.first).toFloat()
        var w = boxW
        var h = w * ratio
        if (h > boxH) {
            h = boxH
            w = h / ratio
        }
        return w to h
    }

    /** Where [edge]'s dashed guide sits on the enlarged page: a fraction across it (left, right) or down it (top, bottom). */
    fun guideFraction(edge: PageEdge, m: EdgeFractions): Double {
        val across = 1 + m.left + m.right
        val down = 1 + m.top + m.bottom
        return when (edge) {
            PageEdge.LEFT -> m.left / across
            PageEdge.RIGHT -> (m.left + 1) / across
            PageEdge.TOP -> m.top / down
            PageEdge.BOTTOM -> (m.top + 1) / down
        }
    }

    fun isVertical(edge: PageEdge): Boolean = edge == PageEdge.LEFT || edge == PageEdge.RIGHT

    /** Dark paper takes a light guide: relative luminance under .45, the mockup's test. */
    fun guideIsLight(paper: Rgba): Boolean = (0.2126 * paper.r + 0.7152 * paper.g + 0.0722 * paper.b) / 255.0 < 0.45

    /** A slider length snapped to [step], or to 0.1 mm over a short range (5 mm or less) and 0.5 mm over a long one. */
    fun snapLength(mm: Double, step: Double?, span: Double): Double {
        val s = step ?: if (span <= 5.0) 0.1 else 0.5
        return Math.round(mm / s) * s
    }

    /** "7", "7.5": millimetres to one decimal, without a trailing ".0". */
    fun formatMm(v: Double): String {
        val r = (v * 10).roundToLong() / 10.0
        return if (r == floor(r)) r.toLong().toString() else "%.1f".format(r)
    }

    /** A whole percentage of a 0..1 fraction. */
    fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

    /** [top] laid over [base], opaque: how a translucent ruling colour reads on the paper. */
    fun over(top: Rgba, base: Rgba): Rgba {
        val t = top.a / 255.0
        fun mix(a: Int, b: Int) = (a * t + b * (1 - t)).roundToInt().coerceIn(0, 255)
        return Rgba(mix(top.r, base.r), mix(top.g, base.g), mix(top.b, base.b))
    }

    /** Opacity raised along a square root: on a small preview a faint ruling stays fainter but never vanishes. */
    fun lifted(c: Rgba): Rgba = c.copy(a = (255 * sqrt(c.a / 255.0)).roundToInt().coerceIn(0, 255))

    /** Presets match on hue alone, since a level may carry its own opacity. */
    fun sameRgb(a: Rgba, b: Rgba): Boolean = a.r == b.r && a.g == b.g && a.b == b.b

    /**
     * The template row after Blank: [base] in order, with [shown] moved to the front (dropping the
     * last) when it is none of them, so the selection is always on the row. [shown] null is Blank.
     */
    fun <K> quickRow(base: List<K>, shown: K?): List<K> =
        if (shown == null || shown in base) base else listOf(shown) + base.dropLast(1)

    /**
     * [style] resolved over [below] into one style, as a page shows it: each field its own or the one
     * below, and the parameters merged when both levels use the same (or compatible) template. A
     * null [below] leaves [style] as it is.
     */
    fun resolve(style: PageStyle, below: PageStyle?): PageStyle {
        if (below == null) return style
        val key = style.template ?: below.template
        val lower = below.takeIf { b -> key == null || b.template.let { it == null || PageTemplates.compatible(it, key) } }
        fun <V> merge(a: Map<String, V>?, b: Map<String, V>?): Map<String, V>? =
            if (a.isNullOrEmpty()) b?.takeIf { it.isNotEmpty() } else if (b.isNullOrEmpty()) a else a + b
        return PageStyle(
            pageColor = style.pageColor ?: below.pageColor,
            template = key,
            patternColor = style.patternColor ?: below.patternColor,
            spacing = style.spacing ?: lower?.spacing,
            accentColor = style.accentColor ?: below.accentColor,
            params = merge(lower?.params, style.params),
            colors = merge(lower?.colors, style.colors),
        )
    }
}
