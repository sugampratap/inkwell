package com.xnotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xnotes.core.tools.Tool
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalInCardFrame
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.POPOVER_EXIT_MS
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.toPx
import com.xnotes.ui.theme.inkRounded
import kotlinx.coroutines.delay

/**
 * What card is up on a bar: a tool's, a colour swatch's picker, or a named chrome menu ("view", "zoom", "jump",
 * "image", "stickers", "fit", "styles", "waypoints").
 *
 * A tool card's key is the one it was opened with (`OfTool(tool)` at the tap), never re-derived from `hostTool`:
 * picking another pen type inside the pen card arms that pen, and the card stays open.
 */
internal sealed interface CardKey {
    data class OfTool(val tool: Tool) : CardKey
    data class OfSwatch(val index: Int) : CardKey
    data class Named(val name: String) : CardKey
}

/**
 * One open card per bar, and the one still closing (so it can animate out). A card is in composition while it is
 * [open] or [shown]; the two are never the same key.
 */
@Stable
internal class ToolCardState {
    /** The card that is open, or null. */
    var open by mutableStateOf<CardKey?>(null)
        private set

    /** The card still animating out: closed, or replaced by another; null once its exit is over ([closed]). */
    var shown by mutableStateOf<CardKey?>(null)
        private set

    /**
     * Open [key]. The card open before it moves to [shown], so it animates out while [key] opens; opening the card
     * that is still animating out takes it back.
     */
    fun open(key: CardKey) {
        val before = open
        if (before != null && before != key) shown = before else if (shown == key) shown = null
        open = key
    }

    /** Close the open card; it is [shown] until its exit has had time to play. */
    fun close() {
        val before = open ?: return
        shown = before
        open = null
    }

    fun toggle(key: CardKey) {
        if (open == key) close() else open(key)
    }

    /** [key]'s exit is over: it leaves composition. Nothing happens unless [key] is the card [shown]. */
    fun closed(key: CardKey) {
        if (shown == key) shown = null
    }
}

@Composable
internal fun rememberToolCardState(): ToolCardState = remember { ToolCardState() }

/** Inside a card's slot: whether it is open (false while it animates out). */
internal val LocalCardExpanded = staticCompositionLocalOf { true }

/** The anchor of the button whose card this is. */
internal val LocalCardAnchor = staticCompositionLocalOf<PopoverAnchor?> { null }

/**
 * Hosts [key]'s card at [anchor] while open or closing: provides LocalCardExpanded / LocalCardAnchor and composes
 * [card] with an onDismiss that closes it. After it closes the card stays composed with LocalCardExpanded = false for
 * [POPOVER_EXIT_MS], so whatever hosts it (ToolCardFrame, a ChromeCard, a bare InkPopover) can play its exit; then
 * the slot drops it. Compose it inside the anchor button's Box, so a card still on the DropdownMenu wrapper anchors
 * where it does today and the popup follows the button when the bar moves.
 */
@Composable
internal fun CardSlot(state: ToolCardState, key: CardKey, anchor: PopoverAnchor, card: @Composable (onDismiss: () -> Unit) -> Unit) {
    val expanded = state.open == key
    if (!expanded && state.shown != key) return
    val dismiss = remember(state, key) { { if (state.open == key) state.close() } }
    LaunchedEffect(expanded) {
        // Re-opening within the wait restarts this effect, and open() has already taken the key out of shown.
        if (!expanded) {
            delay(POPOVER_EXIT_MS)
            state.closed(key)
        }
    }
    CompositionLocalProvider(LocalCardExpanded provides expanded, LocalCardAnchor provides anchor) {
        card(dismiss)
    }
}

/**
 * The outer frame every tool card calls instead of `DropdownMenu(expanded = true, …)`: an InkPopover at
 * LocalCardAnchor with LocalCardExpanded, the content in a raised r24 surface with the line2 ring and
 * InkElevation.MENU. That is the card's one surface: it provides LocalInCardFrame, so an InkCard inside draws none
 * (and leaves the scrolling to this frame). With no LocalCardAnchor (a caller outside the bars) it falls back to the
 * B2 DropdownMenu wrapper, so nothing breaks. Under a header button (no LocalPopoverEdge) the default [spec] becomes
 * the header's (8 dp before the button, 8 below it), as ChromeCard's.
 */
@Composable
internal fun ToolCardFrame(onDismiss: () -> Unit, spec: PopoverSpecDp = PopoverSpecs.ToolCard, content: @Composable ColumnScope.() -> Unit) {
    val anchor = LocalCardAnchor.current
    // The canvas header's Waypoints and Styles cards hang as the paged header's View card does, not by the pill's spacing.
    val header = LocalPopoverEdge.current == null
    val hang = if (spec == PopoverSpecs.ToolCard && header) PopoverSpecs.HeaderMenu else spec
    // A header card (View, Waypoints, Styles) that would meet the pane's top bar drops under it instead of covering
    // the bar's end, as a tool card hangs from it.
    val paneBar = LocalPaneBar.current.takeIf { header }
    val density = LocalDensity.current
    val placer = remember(paneBar, hang, density) {
        paneBar?.let { bar ->
            val px = hang.toPx(density)
            val barGap = with(density) { PopoverSpecs.ToolCard.gap.roundToPx() }
            PopoverPlacer { a, e, window, content ->
                val p = placePopover(a, e, window, content, px, PopoverSide.BELOW)
                clearOfTopBar(p, content, bar.bounds, bar.prefer == PopoverSide.BELOW, window, barGap, px.margin)
            }
        }
    }
    if (anchor == null) {
        // The dropdown is the one surface and scroller here, so an InkCard inside draws neither again.
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            CompositionLocalProvider(LocalInCardFrame provides true) { content() }
        }
    } else {
        InkPopover(expanded = LocalCardExpanded.current, onDismiss = onDismiss, anchor = anchor, spec = hang, placer = placer) {
            // A card opened from inside this one is not this card: it must not hang off this card's anchor.
            CompositionLocalProvider(
                LocalCardAnchor provides null,
                LocalCardExpanded provides true,
                LocalInCardFrame provides true,
            ) {
                Column(
                    Modifier
                        .inkSurface(inkRounded(24.dp), InkElevation.MENU)
                        // As wide as the widest row, as Material's menu sizes its content, and scrolling within the
                        // popover's max height. No 8 dp padding: an InkCard body starts at the card's edge.
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

/**
 * The card a tool opens, the same on both bars (the paged `ToolSettingsPopup` and the canvas inline popups, merged).
 * TEXT needs the paged [Editor]; MARKUP shows only with a PDF.
 */
@Composable
internal fun ToolCardFor(host: ToolPopupHost, tool: Tool, surface: ToolSurface, onDismiss: () -> Unit) {
    if (!tool.hasCard(surface)) return
    when (tool) {
        Tool.SHAPE -> ShapeConfigPopup(host, onDismiss)
        Tool.ERASER -> EraserConfigPopup(host, onDismiss)
        Tool.LASSO -> LassoConfigPopup(host, onDismiss)
        Tool.TAPE -> TapePopover(host, onDismiss)
        // The text card edits the paged note's flow text, so only the paged editor has one.
        Tool.TEXT -> (host as? Editor)?.let { TextToolConfigPopup(it, onDismiss) }
        // Marks a PDF's text: a note without a PDF has nothing for it to mark.
        Tool.MARKUP -> if (host.hostHasPdf) MarkupToolPopup(host, onDismiss)
        else -> if (tool.isStroke) PenPopover(host, tool, onDismiss)
    }
}
