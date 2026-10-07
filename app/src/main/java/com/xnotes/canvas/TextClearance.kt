package com.xnotes.canvas

/**
 * The lowest viewport y a caret or a handle drag may use: above the toolbar's cover and above the floating format
 * pill's clearance, whichever reaches higher. Pure, so it is tested without a canvas.
 */
internal fun clearBottom(viewportH: Double, insetBottom: Double, clearance: Double): Double = viewportH - maxOf(insetBottom, clearance)

/** How much further than the toolbar's cover the format pill's [clearance] reaches up from the bottom; 0 when it doesn't. */
internal fun pillReachPx(insetBottom: Double, clearance: Double): Double = maxOf(0.0, clearance - insetBottom)

/**
 * The most a page that fits the clear area may lift while the format pill is up: enough to bring its bottom, at
 * [restBottom] (viewport px, where centring puts it), up to the pill's top. 0 while the pill reaches no higher than
 * the cover, or the page already ends above the pill.
 */
internal fun fitLiftMaxPx(restBottom: Double, viewportH: Double, insetBottom: Double, clearance: Double): Double =
    if (pillReachPx(insetBottom, clearance) <= 0.0) 0.0 else maxOf(0.0, restBottom - clearBottom(viewportH, insetBottom, clearance))
