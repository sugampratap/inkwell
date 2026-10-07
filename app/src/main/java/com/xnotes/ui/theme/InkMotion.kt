package com.xnotes.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * B2's motion (mockup Sheet 5). Only transform and opacity animate, nothing animates while the pen
 * is down, and interruptible motions retarget from their current value, so a reversal never jumps.
 */
object InkMotion {
    /** Press scale ([press], `pressScale`): the shrink under the finger and the return on release. */
    const val FAST = 120

    /** Popovers, glides, thumbs, underlines, switches, and the press tint ([InkPress]) fading out on release. */
    const val BASE = 180

    /** Screen transitions (library → editor). */
    const val SLOW = 280

    /** The lasso's dashes march once when it is armed. */
    const val ANTS = 420

    /** Opacity and press: cubic-bezier(.2, 0, 0, 1). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Transforms that travel (the CSS mockups' cubic-bezier(.2, .9, .3, 1)): quick out, soft landing. */
    val Glide: Easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

    /** Panels that slide in from an edge, and screen transitions ([screen]). */
    val Emphasized: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    fun <T> press(): FiniteAnimationSpec<T> = tween(FAST, easing = Standard)
    fun <T> fade(): FiniteAnimationSpec<T> = tween(BASE, easing = Standard)
    fun <T> screen(): FiniteAnimationSpec<T> = tween(SLOW, easing = Emphasized)

    /** The toolbar glider, segmented thumbs, underlines: negligible overshoot (ζ 0.85, under 1%). */
    fun <T> glide(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 600f)

    /** A popover's scale and rise (its alpha uses [fade]). */
    fun <T> popover(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 500f)

    /** A heart settling back after its beat; tiny glyph, overshoot allowed. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 800f)

    /** The "Saved" check growing in. */
    fun <T> check(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 700f)
}
