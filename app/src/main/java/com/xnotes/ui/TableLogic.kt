package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.model.TableItem
import com.xnotes.core.text.TableDefaults
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Where Part 7's table chrome goes, and the picker's and the style card's small rules (r3_table_image). Pure, in pane
 * px: the composables convert dp once and pass px in.
 */

/**
 * (rows, cols) under a touch at grid px ([x], [y]) (TI 827-828): one [stepPx] is a cell plus a gap, and a touch
 * outside the grid is kept on its nearest edge. [n] is the grid's side (TableItem.MAX_SIZE).
 */
internal fun gridPick(x: Float, y: Float, stepPx: Float, n: Int = TableItem.MAX_SIZE): Pair<Int, Int> {
    fun at(v: Float): Int = floor(v / stepPx).toInt().coerceIn(0, n - 1) + 1
    return at(y) to at(x)
}

/**
 * posOver (TI 591-593): a [bar] centred over [target], [gap] above it; below it when above would cross [clearTop]
 * (the toolbar's bottom + 12). Kept [margin] inside the [pane] on both axes.
 */
internal fun barOver(target: IntRect, bar: IntSize, pane: IntSize, clearTop: Int, gap: Int, margin: Int): IntOffset {
    val x = inside((target.left + target.right) / 2 - bar.width / 2, bar.width, pane.width, margin)
    val above = target.top - gap - bar.height
    val y = if (above >= clearTop) above else target.bottom + gap
    return IntOffset(x, inside(y, bar.height, pane.height, margin))
}

/** Where a card goes in pane px: its top-left ([x], [y]) and the x it grows from ([originX]). */
internal data class CardSpot(val x: Int, val y: Int, val originX: Int)

/**
 * posBeside (TI 595-597): a [menuWidth] menu off a bar button, [gap] under the bar's bottom, centred on the button.
 * When that would cover the selection [sel] (widened by [clear] each side), it goes [side] to the selection's left, or
 * to its right when the left has no room. Kept [margin] inside the pane; it grows from the button, held [originInset]
 * inside its own span.
 */
internal fun menuBeside(
    menuWidth: Int,
    buttonCentreX: Int,
    barBottom: Int,
    sel: IntRect,
    pane: IntSize,
    gap: Int,
    clear: Int,
    side: Int,
    margin: Int,
    originInset: Int,
): CardSpot {
    val y = barBottom + gap
    var x = buttonCentreX - menuWidth / 2
    if (y < sel.bottom && x < sel.right + clear && x + menuWidth > sel.left - clear) {
        val left = sel.left - side - menuWidth
        x = if (left >= margin) left else sel.right + side
    }
    x = inside(x, menuWidth, pane.width, margin)
    val origin = (buttonCentreX - x).coerceIn(originInset, max(originInset, menuWidth - originInset))
    return CardSpot(x, y, x + origin)
}

/**
 * The style card beside a typed-text table (G9; TI 1055): [gap] after the table's end when it fits, else [gap]
 * before its start, else against the pane's end ([endInset], as the mockup draws it). Its top is [top] (under the
 * toolbar); it grows from [originX] (the Style button).
 */
internal fun styleCardBeside(
    table: IntRect,
    cardWidth: Int,
    pane: IntSize,
    top: Int,
    gap: Int,
    endInset: Int,
    margin: Int,
    originX: Int,
): CardSpot {
    val x = when {
        table.right + gap + cardWidth <= pane.width - margin -> table.right + gap
        table.left - gap - cardWidth >= margin -> table.left - gap - cardWidth
        else -> pane.width - endInset - cardWidth
    }
    return CardSpot(inside(x, cardWidth, pane.width, margin), top, originX)
}

/** How long after a menu's dismissal a tap on its own action still counts as the tap that dismissed it. */
internal const val TOGGLE_GRACE_MS = 300L

/**
 * The typing-bar menu open after a tap on [tapped] (TI 884): a tap toggles its own menu. A non-focusable popup is
 * dismissed by the tap's touch-down, before the action's click, so a tap on the action whose menu ([justClosed])
 * closed less than [TOGGLE_GRACE_MS] ago keeps it closed.
 */
internal fun <T> menuAfterTap(open: T?, tapped: T, justClosed: T?, closedAt: Long, now: Long): T? = when {
    open == tapped -> null
    justClosed == tapped && now - closedAt < TOGGLE_GRACE_MS -> null
    else -> tapped
}

/** "Default for new tables" (TI 1006-1016): once anything differs from the saved defaults the row shows, for the session. */
internal fun defaultRowShown(shownBefore: Boolean, current: TableDefaults, saved: TableDefaults): Boolean =
    shownBefore || current != saved

/** …but it is hidden while the style is the factory one. */
internal fun defaultRowVisible(shown: Boolean, current: TableDefaults): Boolean = shown && !current.isFactory

/** On when the style is the saved defaults and those are not the factory ones. */
internal fun defaultRowOn(current: TableDefaults, saved: TableDefaults): Boolean = !saved.isFactory && current == saved

/**
 * The style card's and sheet's .ti-2 row of two steppers (TI 165): two equal columns [gap] apart in [width], as the
 * grid lays them out, except that the first runs on into the gap, down to [leastGap], when its label and stepper
 * need [firstNeeds] (Padding's in the 440 dp card, which the grid's 188 dp column is 10 short of). The second column
 * stays where the grid puts it. Returns the first column's width and the second's start and width.
 */
internal data class TwoColumns(val firstWidth: Int, val secondStart: Int, val secondWidth: Int)

internal fun twoColumns(width: Int, gap: Int, leastGap: Int, firstNeeds: Int): TwoColumns {
    val col = max(0, (width - gap) / 2)
    val first = firstNeeds.coerceIn(col, max(col, col + gap - leastGap))
    return TwoColumns(first, width - col, col)
}

/** [start] moved so a [size] span stays [margin] inside [0, extent]; a span too big for that starts at [margin]. */
private fun inside(start: Int, size: Int, extent: Int, margin: Int): Int = max(margin, min(start, extent - margin - size))
