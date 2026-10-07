package com.xnotes.ui.kit

import kotlin.math.floor

/**
 * One value, two controls (TO 424–431): a − / + tap lands on the step grid, `round((v + step·d) / step) · step`,
 * clamped to the range, so an off-grid value (one the slider left) snaps onto the grid as it moves. − is off at the
 * bottom of the range and + at the top. The caller works in the unit it shows (mm, pt) and converts around it.
 */
internal object InkStepGrid {

    /** How close to an end counts as at it: the mockup's 1e-9, widened for Float. */
    const val EPSILON = 1e-4f

    /**
     * [value] moved [steps] grid steps of [step] (negative is down; 0 only snaps), clamped to [range]. Halves
     * round up, like the mockup's `Math.round`. Worked in Double, so a 0.1 grid lands on the nearest Float.
     */
    fun step(value: Float, steps: Int, step: Float, range: ClosedFloatingPointRange<Float>): Float {
        require(step > 0f) { "step must be positive, was $step" }
        val s = step.toDouble()
        val snapped = floor((value.toDouble() + s * steps) / s + 0.5) * s
        return snapped.toFloat().coerceIn(range.start, range.endInclusive)
    }

    /** Whether − is on: [value] is above the bottom of [range]. */
    fun canStepDown(value: Float, range: ClosedFloatingPointRange<Float>): Boolean = value > range.start + EPSILON

    /** Whether + is on: [value] is below the top of [range]. */
    fun canStepUp(value: Float, range: ClosedFloatingPointRange<Float>): Boolean = value < range.endInclusive - EPSILON
}
