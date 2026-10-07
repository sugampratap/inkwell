package com.xnotes.ui

import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import kotlin.math.roundToInt

/*
 * The bottom edge's floating pills, in dp (round-3 defaults, Shared row 5). Part 6's text format pill keeps the edge
 * (TX 801: 20 up, 10 over a keyboard, clear of a bottom toolbar). The audio player sits 16 up (AU 44), or 10 over the
 * format pill while it is shown. A toast rests 30 up (TI 233) and rises 16 over the highest pill (AU 109: 96 over the
 * player alone). Pure, so the toast host and the player read one rule.
 */

/** Every bottom pill is 64 dp tall (.ti-bar, .au-player, .tx-fbar). */
internal const val BOTTOM_PILL_DP = 64

/**
 * What a toolbar of [look] covers of its pane's bottom edge, in dp, worked out as ToolbarAround does (its cover and the
 * canvas's insetBottom): a floating bottom bar's [floatingCover]; 0 for a docked bar or a bar on another edge.
 */
internal fun bottomCoverDp(look: ToolbarLook): Int =
    if (look.floating && look.position == ToolbarPosition.BOTTOM) floatingCover(look).value.roundToInt() else 0

/**
 * The format pill's bottom above the pane's bottom: 20, or 10 over a keyboard, and 10 clear of a bottom toolbar
 * ([coverBottomDp]). The rule itself is [formatPillGap]'s; this is it in whole dp.
 */
internal fun formatPillBottomDp(imeUp: Boolean, coverBottomDp: Int): Int = formatPillGap(imeUp, coverBottomDp.toFloat()).roundToInt()

/** The player's bottom above the pane's bottom: 16 over what a bottom toolbar covers, or 10 over the format pill while it is up. */
internal fun playerBottomDp(formatPillShown: Boolean, imeUp: Boolean, coverBottomDp: Int): Int =
    if (formatPillShown) formatPillBottomDp(imeUp, coverBottomDp) + BOTTOM_PILL_DP + 10 else coverBottomDp + 16

/** The toast's bottom: 30 at rest, else 16 over the highest pill that is up (either may be null: not shown). */
internal fun toastBottomDp(playerBottomDp: Int?, formatPillBottomDp: Int?): Int {
    val highest = listOfNotNull(playerBottomDp, formatPillBottomDp).maxOrNull() ?: return 30
    return highest + BOTTOM_PILL_DP + 16
}
