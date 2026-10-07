package com.xnotes.ui

import androidx.compose.ui.geometry.Rect
import com.xnotes.core.util.DocumentKind
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.TileSize

// Pure decisions behind the library screen, kept apart from Compose so they can be tested on the JVM.

// --- the cover shelf ---

/** The full sidebar (256dp) and the shelf's 32dp margins: folding the sidebar only widens the covers, never reflows them. */
private const val SHELF_CHROME_DP = 256 + 32 * 2

/** A phone's two 16dp margins. */
private const val SHELF_CHROME_COMPACT_DP = 16 * 2

/** Columns on the shelf at [size]: B2's 172dp cover at M with 24dp gaps (16dp on phones), so five stand beside the sidebar on the S8. */
internal fun coverColumns(screenWidthDp: Int, compact: Boolean, size: TileSize): Int {
    val room = screenWidthDp - if (compact) SHELF_CHROME_COMPACT_DP else SHELF_CHROME_DP
    val gap = if (compact) 16f else 24f
    val tile = when (size) {
        TileSize.S -> 128f
        TileSize.M -> 172f
        TileSize.L -> 220f
        TileSize.XL -> 290f
    }
    return ((room + gap) / (tile + gap)).toInt().coerceIn(2, 10)
}

// --- Sort & view ---

/** The Sort & view menu's fields, in the mockup's order. */
internal val SORT_MENU_KEYS = listOf(ExplorerSortKey.MODIFIED, ExplorerSortKey.CREATED, ExplorerSortKey.NAME, ExplorerSortKey.SIZE)

/**
 * The two directions the menu's segmented control offers for [key], the natural one first, as `descending` values:
 * "Newest first" and "Largest first" are descending, "A to Z" is not.
 */
internal fun directionOptions(key: ExplorerSortKey): List<Boolean> =
    if (key == ExplorerSortKey.NAME) listOf(false, true) else listOf(true, false)

/** [view] sorted by [key]. The field in use keeps its direction (the control flips it); a new one starts in its natural direction. */
internal fun withSortKey(view: ExplorerView, key: ExplorerSortKey): ExplorerView =
    if (key == view.sortKey) view else view.copy(sortKey = key, descending = key != ExplorerSortKey.NAME)

// --- Continue writing (D6) ---

/** The page (0-based) Continue writing names for a note left on [page] of [pages], or null to give only its length. */
internal fun continuePage(page: Int?, pages: Int): Int? = page?.takeIf { pages >= 2 && it in 0 until pages }

/** How far through the note its reader is, for the progress line: page 7 of 12 is 7/12. Not animated, so a plain Float is fine. */
internal fun continueProgress(page: Int?, pages: Int): Float? = continuePage(page, pages)?.let { (it + 1f) / pages }

// --- cover designs (D8) ---

/** How a notebook's cloth is made up on the shelf. Picked from its name, so it never changes and needs no setting. */
internal enum class CoverDesign {
    /** Linen, a paper plate with the title written on it, and a ribbon. */
    PLATE,

    /** Leather with a stitched border and the title debossed. Light cloth only: debossing is dark. */
    STITCH,

    /** Leather with a strap and the title in gold foil. Dark cloth only: foil is light. */
    STRAP,
}

/** The design for a notebook named [name] whose cloth has [luminance] (0..1). */
internal fun coverDesign(name: String, luminance: Float): CoverDesign {
    val h = DocumentKind.stripSuffix(name).lowercase().hashCode()
    // A higher bit than CoverPalette.autoIndex leans on, so colour and design vary independently.
    if (Math.floorMod(h ushr 7, 2) == 0) return CoverDesign.PLATE
    return if (luminance > 0.35f) CoverDesign.STITCH else CoverDesign.STRAP
}

// --- the header's search pill ---

/** Where the header's search pill goes: [x] from the header's start, and its [width]. */
internal data class SearchPillSlot(val x: Float, val width: Float)

/**
 * The pill's place in a header [headerW] wide, between the title (ending at [leadEnd]) and New (starting at
 * [newStart]), with [gap] either side: centred and [maxW] wide when it fits (Frame 1), else as wide as the room
 * allows and pushed clear of the title, or null below [minW].
 */
internal fun pillSlot(headerW: Float, leadEnd: Float, newStart: Float, maxW: Float, minW: Float, gap: Float): SearchPillSlot? {
    val left = leadEnd + gap
    val right = newStart - gap
    val room = right - left
    if (room < minW) return null
    val w = minOf(maxW, room)
    return SearchPillSlot(((headerW - w) / 2f).coerceIn(left, right - w), w)
}

/** A cover's title set in two lines (.deboss, .foil): its first word, then the rest, if any. */
internal data class CoverTitle(val head: String, val rest: String?)

private val Spaces = Regex("\\s+")

internal fun coverTitle(title: String): CoverTitle {
    val t = title.trim()
    val i = t.indexOfFirst { it.isWhitespace() }
    return if (i < 0) CoverTitle(t, null) else CoverTitle(t.substring(0, i), t.substring(i).trim().replace(Spaces, " "))
}

/** Smoothstep, the ease the leather grain's value noise blends its lattice with, so no cell edge shows as a crease. */
internal fun valueNoiseEase(t: Float): Float = t * t * (3f - 2f * t)

/**
 * The leather grain (.leather): three octaves of tileable value noise, [n] px square, the SVG's fractal noise at its
 * .55 base frequency, as signed strengths in -1..1 (the darkest pixel -1, the lightest 1). Wraps, so the tile repeats seamlessly.
 */
internal fun leatherGrain(n: Int, seed: Long): FloatArray {
    val rnd = java.util.Random(seed)
    fun grid(cells: Int) = Array(cells + 1) { FloatArray(cells + 1) { rnd.nextFloat() } }.also { g ->
        for (i in 0..cells) { g[i][cells] = g[i][0]; g[cells][i] = g[0][i] }
    }
    val octaves = listOf(grid(80) to 0.5f, grid(40) to 0.3f, grid(20) to 0.2f)
    val v = FloatArray(n * n)
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (y in 0 until n) for (x in 0 until n) {
        var s = 0f
        for ((g, weight) in octaves) {
            val cells = g.size - 1
            val fx = x * cells / n.toFloat(); val fy = y * cells / n.toFloat()
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val tx = valueNoiseEase(fx - x0); val ty = valueNoiseEase(fy - y0)
            val a = g[y0][x0] + (g[y0][x0 + 1] - g[y0][x0]) * tx
            val b = g[y0 + 1][x0] + (g[y0 + 1][x0 + 1] - g[y0 + 1][x0]) * tx
            s += (a + (b - a) * ty) * weight
        }
        v[y * n + x] = s
        if (s < lo) lo = s
        if (s > hi) hi = s
    }
    // Averaged octaves bunch up round the middle; stretched to the full range, the grain has the SVG's contrast.
    val mid = (lo + hi) / 2f
    val half = ((hi - lo) / 2f).takeIf { it > 0f } ?: 1f
    for (i in v.indices) v[i] = ((v[i] - mid) / half).coerceIn(-1f, 1f)
    return v
}

// --- joined meta lines ---

/** [parts] joined through [pattern], a two-part format such as "%1$s · %2$s" (library_dot_join), folded left. */
internal fun dotJoin(pattern: String, parts: List<String>): String =
    if (parts.isEmpty()) "" else parts.reduce { a, b -> String.format(pattern, a, b) }

// --- Continue writing images ---

/** A Continue writing image's key: keyed by the file's modified time, so an edit, or a new page, renders afresh. */
internal fun continueKey(uri: String, page: Int, modified: Long) = "$uri#p$page@$modified"

/** Whether [key] is a Continue writing image of [uri] from before it was last saved at [modified]. */
internal fun isStaleContinueKey(key: String, uri: String, modified: Long): Boolean {
    if (!key.startsWith("$uri#p")) return false
    val at = key.lastIndexOf('@')
    if (at < 0) return false
    val page = key.substring(uri.length + 2, at)
    if (page.isEmpty() || !page.all { it.isDigit() }) return false
    return key.substring(at + 1) != modified.toString()
}

// --- library -> editor (motion e) ---

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The opening's motion (Motion sheet e): a layer laid out at the page's landing [to], scaled about its top-left
 * corner and shifted so it starts on [from] (the cover or card, window px) and comes to rest on the landing.
 * One scale both ways, so the page never squeezes: it starts as wide as the source, top edges together, with
 * only the source's height of it showing ([reveal], layer px from its top). Plain floats, so a frame allocates nothing.
 */
internal class PageOpen(from: Rect, to: Rect) {
    private val startScale = from.width / to.width
    private val startDx = from.left - to.left
    private val startDy = from.top - to.top
    private val startReveal = minOf(to.height, from.height / startScale)
    private val pageH = to.height

    /** The scale [t] of the way through, 0 to 1. */
    fun scale(t: Float) = mix(startScale, 1f, t)
    fun dx(t: Float) = mix(startDx, 0f, t)
    fun dy(t: Float) = mix(startDy, 0f, t)

    /** How far down the layer shows at [t]: the source's own height at the start, the whole page at the end. */
    fun reveal(t: Float) = mix(startReveal, pageH, t)
}

internal fun pageOpen(from: Rect, to: Rect): PageOpen = PageOpen(from, to)

/** Where the opening note's page lands in [area]: its shape ([ratio], width over height) fitted whole under [top], centred across. */
internal fun pageLanding(area: Rect, ratio: Float, top: Float, margin: Float): Rect {
    val w = area.width - 2 * margin
    val h = area.height - top - margin
    val (pw, ph) = if (w / h > ratio) h * ratio to h else w to w / ratio
    val left = area.left + (area.width - pw) / 2f
    return Rect(left, area.top + top, left + pw, area.top + top + ph)
}
