package com.xnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.theme.toComposeColor
import java.util.WeakHashMap

/**
 * One button for every pen, as Samsung Notes and Starnote have it: the bar shows the pen in hand,
 * a tap picks it up, and a second tap opens the pen card, whose top row swaps the pen type.
 *
 * A pen type the user has put on the bar as a button of its own keeps that button and is not one
 * of the family's: two buttons lighting for one tool would leave the hand unsure which it holds.
 */

/** Whether [layout] shows [tool] on a button of its own. */
private fun ToolbarLayout.showsOwnButton(tool: Tool): Boolean =
    sections.any { s -> s.entries.any { it.visible && it.item.id == tool.id } }

/** Whether the pen button stands for [tool] on a bar laid out as [layout]. */
internal fun ToolbarLayout.penButtonCovers(tool: Tool): Boolean =
    tool.isPen && (tool == Tool.PEN || !showsOwnButton(tool))

/** The key the bar's glider follows: a pen the pen button covers glides to that button. */
internal fun glideKeyFor(tool: Tool, layout: ToolbarLayout): Tool =
    if (layout.penButtonCovers(tool)) Tool.PEN else tool

/** The last pen each host had in hand, so the button picks up the pen that was put down. */
private val lastPen = WeakHashMap<ToolPopupHost, Tool>()

/**
 * The pen button: the glyph of the pen in hand (or the last one held), the ink dot in the colour it
 * writes on this paper ([inkOnPaper] of [inkOf]), the glider under it while a pen it covers is held.
 * - Not holding one of its pens: a tap picks up the shown pen.
 * - Holding one: a tap opens that pen's card, hanging from this button.
 * - With a pen card up: a tap closes it.
 *
 * The card is keyed by the pen it was opened for, so picking another type inside it keeps it open here.
 */
@Composable
internal fun PenFamilyButton(
    host: ToolPopupHost,
    layout: ToolbarLayout,
    cards: ToolCardState,
    surface: ToolSurface,
    inkOnPaper: (Rgba) -> Rgba,
) {
    val armed = host.hostTool
    val holding = layout.penButtonCovers(armed)
    if (holding) lastPen[host] = armed
    val shown = if (holding) armed else lastPen[host]?.takeIf { layout.penButtonCovers(it) } ?: Tool.PEN
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarButton(
            stringResource(shown.labelRes),
            onClick = {
                val openPen = (cards.open as? CardKey.OfTool)?.tool?.takeIf { layout.penButtonCovers(it) }
                if (holding && openPen != null) cards.close() else cards.onToolTap(host, shown, surface)
            },
            active = holding,
            glideKey = Tool.PEN,
            anchor = anchor,
            inkDot = inkOnPaper(host.inkOf(shown)).toComposeColor(),
        ) { tint, size, on -> ToolGlyph(shown, tint, size, on) }
        ToolCardsAt(host, cards, surface, anchor) { layout.penButtonCovers(it) }
    }
}
