package com.xnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.popoverAnchor

/**
 * [InkPopover] for Part 7's cards and menus. They hang off the page or a floating bar, never off the toolbar, so the
 * toolbar's [LocalPopoverEdge] (should an ancestor provide one) is cleared and the anchor itself is the edge.
 */
@Composable
internal fun MediaPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: PopoverAnchor,
    spec: PopoverSpecDp,
    prefer: PopoverSide? = null,
    focusable: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPopoverEdge provides null) {
        InkPopover(
            expanded = expanded,
            onDismiss = onDismiss,
            anchor = anchor,
            spec = spec,
            prefer = prefer,
            focusable = focusable,
            content = content,
        )
    }
}

/** For [AnchorAt]: the card's top-left is the anchor's own, with no lead and no gap. */
internal val PlacedSpec = PopoverSpecDp(lead = 0.dp, gap = 0.dp, margin = 8.dp)

/**
 * A 0-high anchor laid out at pane px ([x], [y]) and centred on pane px [originX]. A card hosted in [content] with
 * [PlacedSpec] and BELOW lands with its top-left there and grows from [originX] (ChromeLogic.placePopover takes the
 * anchor's centre as the origin). The card's Popup is composed inside this anchor, so it follows the anchor when the
 * page scrolls (Part 3 note 10). Must sit in a box whose top-left is the pane's.
 */
@Composable
internal fun AnchorAt(x: Int, y: Int, originX: Int, content: @Composable (PopoverAnchor) -> Unit) {
    val anchor = remember { PopoverAnchor() }
    val width = with(LocalDensity.current) { (2 * (originX - x)).coerceAtLeast(1).toDp() }
    Box(Modifier.offset { IntOffset(x, y) }.size(width, 0.dp).popoverAnchor(anchor)) { content(anchor) }
}

/** The snackbar's visuals with the B2 toast's icon (TI 1233, AU 693-702). */
internal class ToastVisuals(
    override val message: String,
    override val actionLabel: String?,
    val icon: ImageVector?,
    override val duration: SnackbarDuration,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
}

/**
 * How far above the window's bottom a toast sits for the open [panes] (BottomStack): over whichever note pane's
 * player or format pill is up. Reads the panes' player and pill state, so a player opening under a toast lifts it.
 *
 * Each pane's format pill counts as shown while its `formatPillReserve` is above 0 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun toastBottomFor(panes: List<Editor>): Dp {
    val imeUp = WindowInsets.isImeVisible
    // What a floating bottom bar covers, from the toolbar look (snapshot-backed; one look for both panes), as
    // ToolbarAround works it out: so the bar moving or coming up moves the toast.
    val cover = bottomCoverDp(LocalToolbarLook.current)
    var player: Int? = null
    var pill: Int? = null
    for (e in panes) {
        // Read for every pane before the skip, so the composable calls keep one order.
        val pillUp = formatPillReserve(e) > 0.dp
        if (!e.noteOpen || e.canvasOpen) continue
        if (e.media.playerItem != null && !e.media.recordingActive) {
            player = maxOf(player ?: 0, playerBottomDp(pillUp, imeUp, cover))
        }
        if (pillUp) pill = maxOf(pill ?: 0, formatPillBottomDp(imeUp, cover))
    }
    return toastBottomDp(player, pill).dp
}
