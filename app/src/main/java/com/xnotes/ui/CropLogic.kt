package com.xnotes.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** The crop bar's ratio chips, in the bar's order (TI 1142). [ratio] is fixed for the presets; Free and Original have none of their own. */
internal enum class CropChip(val ratio: Double?) {
    FREE(null),
    ORIGINAL(null),
    SQUARE(1.0),
    R4_3(4.0 / 3.0),
    R3_4(3.0 / 4.0),
    R16_9(16.0 / 9.0),
}

/** The aspect this chip sets on a picture whose own displayed aspect is [source]: none for Free. */
internal fun CropChip.aspectFor(source: Double): Double? = when (this) {
    CropChip.FREE -> null
    CropChip.ORIGINAL -> source
    else -> ratio
}

/** Two aspects this close are the same chip (TI 1159). */
internal const val ASPECT_MATCH = 1e-3

private val LIT_ORDER = listOf(CropChip.ORIGINAL, CropChip.SQUARE, CropChip.R4_3, CropChip.R3_4, CropChip.R16_9)

/**
 * The one chip that lights for the box's [current] aspect (TI 1158-1160): Free when there is none, else the first
 * of Original, 1:1, 4:3, 3:4 and 16:9 within [ASPECT_MATCH], so a picture whose own ratio is a preset lights
 * Original only. Null when nothing matches.
 */
internal fun litAspect(current: Double?, source: Double): CropChip? {
    if (current == null) return CropChip.FREE
    return LIT_ORDER.firstOrNull { chip -> chip.aspectFor(source)?.let { abs(it - current) < ASPECT_MATCH } == true }
}

/** A ratio chip's drawn rect in a 16 dp square (TI 1143): 16 wide and round(16 / q) high for q ≥ 1, else round(16·q) wide and 16 high. */
internal fun ratioGlyph(q: Double): Pair<Int, Int> =
    if (q >= 1.0) 16 to (16 / q).roundToInt() else (16 * q).roundToInt() to 16
