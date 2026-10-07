package com.xnotes.ui

import com.xnotes.core.model.PageSize
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TapeItem
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConversions
import com.xnotes.ui.kit.InkStepGrid
import java.util.Locale
import kotlin.math.max

/*
 * The tool cards' rules (B2 Part 4), pure so they are tested on the JVM: millimetre steppers on any grid, the eraser's
 * size as a diameter, the tape's whole-pixel widths, which shape rows show, the ink's name, the colour rows' eight
 * columns, the laser preview's glow and the tape count line.
 */

private const val TO_MM_PER_INCH = 25.4f

/** [px] page pixels (150 dpi) in millimetres. */
internal fun pageMm(px: Float): Float = px * TO_MM_PER_INCH / PageSize.DEFAULT_DPI

/** [mm] millimetres in page pixels (150 dpi). */
internal fun pagePx(mm: Float): Float = mm * PageSize.DEFAULT_DPI / TO_MM_PER_INCH

/**
 * A − / + step on a [stepMm] millimetre grid (TO 427, through R0's [InkStepGrid.step]): read [px] as mm, land on the
 * grid [steps] steps away, back to px, clamped to [rangePx]. It always moves at least half a step before the clamp,
 * so it never stalls. The pen card's 0.1 mm [stepWidthPx] is unchanged; the eraser and the tape step 0.5 mm.
 */
internal fun stepMmPx(px: Float, steps: Int, stepMm: Float, rangePx: ClosedFloatingPointRange<Float>): Float {
    val mmRange = pageMm(rangePx.start)..pageMm(rangePx.endInclusive)
    return pagePx(InkStepGrid.step(pageMm(px), steps, stepMm, mmRange)).coerceIn(rangePx.start, rangePx.endInclusive)
}

// --- the eraser ---

/** The eraser's size steps 0.5 mm of its diameter (TO 632). */
internal const val ERASER_STEP_MM = 0.5f

/** The stored eraser size: its radius in page px (`ToolConversions.widthRange(ERASER)`, 1–80). */
internal val ERASER_RADIUS_PX: ClosedFloatingPointRange<Float> =
    ToolConversions.widthRange(Tool.ERASER).let { it.start.toFloat()..it.endInclusive.toFloat() }

/** "8.1 mm" for the default 24 px radius: the card shows the eraser's diameter. */
internal fun eraserSizeLabel(radiusPx: Float): String = widthLabelMm(2f * radiusPx)

/** The radius after [steps] 0.5 mm steps of the diameter, kept in 1–80 px. */
internal fun stepEraserRadius(radiusPx: Float, steps: Int): Float {
    val r = ERASER_RADIUS_PX
    return stepMmPx(2f * radiusPx, steps, ERASER_STEP_MM, 2f * r.start..2f * r.endInclusive) / 2f
}

// --- the tape ---

/** The tape's width steps 0.5 mm (TO 827). */
internal const val TAPE_STEP_MM = 0.5f

/** The tape tool's width range in page px (`TapeItem.MIN_WIDTH`..`MAX_WIDTH`). */
internal val TAPE_WIDTH_PX: ClosedFloatingPointRange<Float> = TapeItem.MIN_WIDTH.toFloat()..TapeItem.MAX_WIDTH.toFloat()

/** The width after [steps] 0.5 mm steps, rounded to whole px as the slider stores it (0.5 mm = 2.95 px, so it moves). */
internal fun stepTapeWidth(widthPx: Double, steps: Int): Double =
    kotlin.math.round(stepMmPx(widthPx.toFloat(), steps, TAPE_STEP_MM, TAPE_WIDTH_PX)).toDouble()

// --- the laser ---

/** The laser's thickness range in page px (the card's literal 1–12, not `widthRange`). */
internal val LASER_WIDTH_PX: ClosedFloatingPointRange<Float> = 1f..12f

/** The beam's width in the preview (TO 752): mm × 3.2 dp, at least 1.2 dp. */
internal fun laserPreviewWidth(px: Float): Float = max(1.2f, pageMm(px) * 3.2f)

/** One stroke of the laser preview: its share of the ink's alpha and its width in preview dp. */
internal data class GlowLayer(val alpha: Float, val width: Float)

/** TO 729–732: two halos that widen and brighten with [glow] (0–100), then the core. No blur. */
internal fun glowLayers(width: Float, glow: Float): List<GlowLayer> {
    val g = (glow / 100f).coerceIn(0f, 1f)
    return listOf(
        GlowLayer(0.18f * g, width * (2.2f + 2.6f * g)),
        GlowLayer(0.32f * g, width * (1.4f + 1.2f * g)),
        GlowLayer(1f, width),
    )
}

// --- the highlighter ---

/** The marker's width in the preview (TO 576): mm × 4.4 dp. */
internal fun highlighterPreviewWidth(px: Float): Float = pageMm(px) * 4.4f

// --- shapes ---

/** The Fill opacity row shows while Fill is on and the kind has an inside (TO 714). */
internal fun fillOpacityShown(fill: Boolean, kind: ShapeKind): Boolean = fill && kind.isClosed

/** Fill is dimmed, with its reason, for a kind with no inside. The stored `fill` is never rewritten (TO 712). */
internal fun fillDimmed(kind: ShapeKind): Boolean = !kind.isClosed

/** The shape preview's dp per mm (TO 703). */
internal const val SHAPE_PREVIEW_DP_PER_MM = 3.4f

/** The preview's line width: mm × 3.4 dp, never under 1 dp. */
internal fun shapePreviewWidth(strokePx: Float): Float = max(1f, pageMm(strokePx) * SHAPE_PREVIEW_DP_PER_MM)

/** Dash and gap in preview dp; the gap takes the line's [width] again, for the round caps (TO 705). */
internal fun shapePreviewDash(dashPx: Float, gapPx: Float, width: Float): FloatArray =
    floatArrayOf(pageMm(dashPx) * SHAPE_PREVIEW_DP_PER_MM, pageMm(gapPx) * SHAPE_PREVIEW_DP_PER_MM + width)

/** The five quick colours (D7) by name; any other ink is named by its hex (TO 701). */
internal enum class InkName { NAVY, BLUE, RED, GREEN, AMBER }

/** [c]'s name if it is one of the five quick colours, alpha ignored; else null. */
internal fun inkName(c: Rgba): InkName? = when (Rgba(c.r, c.g, c.b)) {
    Rgba(31, 42, 68) -> InkName.NAVY
    Rgba(37, 99, 235) -> InkName.BLUE
    Rgba(220, 38, 38) -> InkName.RED
    Rgba(22, 163, 74) -> InkName.GREEN
    Rgba(217, 119, 6) -> InkName.AMBER
    else -> null
}

/** "#1F2A44": an unnamed ink, upper-case, without its alpha. */
internal fun inkHex(c: Rgba): String = String.format(Locale.US, "#%02X%02X%02X", c.r, c.g, c.b)

// --- PDF markup ---

/** Only a highlight has a depth to set (TO 667). */
internal fun markupIntensityShown(mode: MarkupMode): Boolean = mode == MarkupMode.HIGHLIGHT

// --- grids ---

/** The colour rows' fixed columns (.to-cols, TO 46): a short row keeps the left columns. */
internal const val COLOUR_COLUMNS = 8

/** One row of a fixed-column grid: its first item, how many items, how many empty columns after them. */
internal data class GridRow(val start: Int, val size: Int, val empty: Int)

/** [count] items in rows of [columns]. */
internal fun gridRows(count: Int, columns: Int): List<GridRow> {
    require(columns > 0) { "columns must be positive" }
    if (count <= 0) return emptyList()
    return (0 until count step columns).map { s ->
        val n = minOf(columns, count - s)
        GridRow(s, n, columns - n)
    }
}

// --- the tape count line ---

/** "In this note": no tape yet, or how many strips and how many of them are peeled back (TO 816–818). */
internal sealed interface TapeCountLine {
    data object None : TapeCountLine
    data class Counts(val strips: Int, val peeled: Int) : TapeCountLine
}

/** [counts] = `hostTapeCounts()` (strips, peeled). */
internal fun tapeCountLine(counts: Pair<Int, Int>): TapeCountLine =
    if (counts.first <= 0) TapeCountLine.None else TapeCountLine.Counts(counts.first, counts.second.coerceIn(0, counts.first))
