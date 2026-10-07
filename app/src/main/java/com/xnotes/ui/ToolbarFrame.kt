package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverEdge
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverEdge
import com.xnotes.ui.theme.LocalInk
import kotlin.math.roundToInt

/** The toolbar look from Preferences, provided once above both panes. */
val LocalToolbarLook = staticCompositionLocalOf { ToolbarLook() }

/**
 * The pane's toolbar as an edge (its bounds and side), provided around the header and the bar both, so a header card
 * can keep clear of the bar ([clearOfTopBar]). [ToolbarFrame] records into it; empty while no bar is up.
 */
internal val LocalPaneBar = staticCompositionLocalOf<PopoverEdge?> { null }

/**
 * What every toolbar widget sizes itself by, and which way the bar runs (B2 §2): [button] is a tool's
 * round target and the glider's diameter, [icon] its glyph, [swatch] a quick-colour dot and [swatchHit]
 * the width of that dot's hit box (44 dp tall, no gaps between swatches).
 */
internal data class BarMetrics(
    val button: Dp,
    val icon: Dp,
    val swatch: Dp,
    val swatchHit: Dp,
    val vertical: Boolean = false,
) {
    /** Across the bar: a button with 8 dp either side, the 60 dp pill at Regular. */
    val thickness: Dp get() = button + 16.dp

    /** A separator's length, 24 dp at Regular and in step with the button at the other sizes. */
    val rule: Dp get() = (button.value * 24f / 44f).roundToInt().dp
}

/** Regular is the mockup's bar (44 / 22 / 26 / 36, a 60 dp pill); Compact and Comfortable scale around it. */
internal fun barMetrics(size: ToolbarSize): BarMetrics = when (size) {
    ToolbarSize.COMPACT -> BarMetrics(button = 36.dp, icon = 20.dp, swatch = 22.dp, swatchHit = 30.dp)
    ToolbarSize.REGULAR -> BarMetrics(button = 44.dp, icon = 22.dp, swatch = 26.dp, swatchHit = 36.dp)
    ToolbarSize.COMFORTABLE -> BarMetrics(button = 52.dp, icon = 26.dp, swatch = 30.dp, swatchHit = 42.dp)
}

internal val LocalBar = staticCompositionLocalOf { barMetrics(ToolbarSize.REGULAR) }

/**
 * Where a Material menu opens from its anchor. The bar no longer offsets it: anchored cards are placed by
 * `InkPopover` from [LocalPopoverEdge], so this stays [DpOffset.Zero] (the `DropdownMenu` wrapper still reads it).
 */
internal val LocalMenuOffset = compositionLocalOf { DpOffset.Zero }

/** How far a floating bar sits in from its pane's edge. */
private val FLOAT_MARGIN = 8.dp

/** How far a floating top bar sits under the header (W 368: top 96 = header bottom 82 + 14). */
private val TOP_GAP = 14.dp

/** What a floating bar covers of the canvas, for overlays that must stay out from under it. */
internal val LocalToolbarCover = compositionLocalOf { PaddingValues(0.dp) }

/** How much of its edge a floating bar takes: its thickness plus its gap from that edge (14 dp under the header, else 8). */
internal fun floatingCover(look: ToolbarLook): Dp =
    barMetrics(look.size).thickness + if (look.position == ToolbarPosition.TOP) TOP_GAP else FLOAT_MARGIN

/** The side a card opens on from a bar along [position]: away from that edge, into the canvas. */
internal fun cardSideFor(position: ToolbarPosition): PopoverSide = when (position) {
    ToolbarPosition.TOP -> PopoverSide.BELOW
    ToolbarPosition.BOTTOM -> PopoverSide.ABOVE
    ToolbarPosition.LEFT -> PopoverSide.END
    ToolbarPosition.RIGHT -> PopoverSide.START
}

/** The floating bar's padding inside its pane: [TOP_GAP] under the header for a top bar, [FLOAT_MARGIN] all round otherwise. */
private fun floatPadding(position: ToolbarPosition): PaddingValues =
    if (position == ToolbarPosition.TOP) {
        PaddingValues(start = FLOAT_MARGIN, top = TOP_GAP, end = FLOAT_MARGIN, bottom = FLOAT_MARGIN)
    } else {
        PaddingValues(FLOAT_MARGIN)
    }

/**
 * Lays a pane out with its [bar] along the edge Preferences chose and [content] in the rest. A
 * floating bar is handed to [content] to lay over its canvas instead, and [onCover] learns how many
 * px of each edge (left, top, right, bottom) it covers so the canvas keeps its pages clear of it.
 * The pane's header sits directly above this, so a floating top bar's [TOP_GAP] is measured from it.
 */
@Composable
internal fun ColumnScope.ToolbarAround(
    bar: @Composable () -> Unit,
    onCover: (Double, Double, Double, Double) -> Unit,
    content: @Composable (floatingBar: @Composable BoxScope.() -> Unit) -> Unit,
) {
    val look = LocalToolbarLook.current
    val rest = Modifier.weight(1f).fillMaxWidth()
    if (!look.floating) {
        SideEffect { onCover(0.0, 0.0, 0.0, 0.0) }
        val docked: @Composable BoxScope.() -> Unit = {}
        // Left and right are the screen's, as the canvas insets are, whatever the language.
        val across = Arrangement.Absolute.Left
        when (look.position) {
            ToolbarPosition.TOP -> { bar(); Box(rest) { content(docked) } }
            ToolbarPosition.BOTTOM -> { Box(rest) { content(docked) }; bar() }
            ToolbarPosition.LEFT -> Row(rest, across) { bar(); Box(Modifier.weight(1f).fillMaxHeight()) { content(docked) } }
            ToolbarPosition.RIGHT -> Row(rest, across) { Box(Modifier.weight(1f).fillMaxHeight()) { content(docked) }; bar() }
        }
        return
    }
    val position = look.position
    val depth = floatingCover(look)
    val px = with(LocalDensity.current) { depth.toPx().toDouble() }
    SideEffect {
        onCover(
            if (position == ToolbarPosition.LEFT) px else 0.0,
            if (position == ToolbarPosition.TOP) px else 0.0,
            if (position == ToolbarPosition.RIGHT) px else 0.0,
            if (position == ToolbarPosition.BOTTOM) px else 0.0,
        )
    }
    val (cover, edge) = when (position) {
        ToolbarPosition.TOP -> PaddingValues(top = depth) to Alignment.TopCenter
        ToolbarPosition.BOTTOM -> PaddingValues(bottom = depth) to Alignment.BottomCenter
        ToolbarPosition.LEFT -> PaddingValues.Absolute(left = depth) to AbsoluteAlignment.CenterLeft
        ToolbarPosition.RIGHT -> PaddingValues.Absolute(right = depth) to AbsoluteAlignment.CenterRight
    }
    CompositionLocalProvider(LocalToolbarCover provides cover) {
        Box(rest) { content { Box(Modifier.align(edge).padding(floatPadding(position))) { bar() } } }
    }
}

/** A docked bar's one hairline (--line), on the side that faces the canvas. Left and right are the screen's. */
private fun DrawScope.dockedHairline(position: ToolbarPosition, color: Color) {
    val w = 1.dp.toPx()
    when (position) {
        ToolbarPosition.TOP -> drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
        ToolbarPosition.BOTTOM -> drawRect(color, Offset.Zero, Size(size.width, w))
        ToolbarPosition.LEFT -> drawRect(color, Offset(size.width - w, 0f), Size(w, size.height))
        ToolbarPosition.RIGHT -> drawRect(color, Offset.Zero, Size(w, size.height))
    }
}

/**
 * The strip both toolbars are laid out in (B2 §2.1, §2.8):
 * - **Floating:** a 60 dp pill (Regular) on `raised` with the one FLOAT shadow and the line2 ring
 *   (`inkSurface`), 8 dp padding along its length.
 * - **Docked:** a square strip across the whole edge on `raised`, its content centred, with one `line`
 *   hairline on the canvas side and no shadow (defaults row 8).
 * - **Down a side:** the same, as a column.
 *
 * [content] runs along it and scrolls when it runs long; [trailing] stays pinned at the end; [armed] is
 * the glide key the glider sits under. The bar is the edge its cards hang from ([LocalPopoverEdge]): they
 * open away from the bar's edge of the pane ([cardSideFor]).
 */
@Composable
internal fun ToolbarFrame(
    armed: Any?,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val look = LocalToolbarLook.current
    val ink = LocalInk.current
    val bar = barMetrics(look.size).copy(vertical = look.position.vertical)
    val glide = remember { ToolGlide() }
    // The pane's own edge when it provides one, so its header cards know where the bar is too.
    val pane = LocalPaneBar.current
    val edge = pane ?: remember { PopoverEdge() }
    edge.prefer = cardSideFor(look.position)
    if (pane != null) DisposableEffect(pane) { onDispose { pane.bounds = IntRect.Zero } }
    val surface = if (look.floating) {
        Modifier.inkSurface(CircleShape, InkElevation.FLOAT)
    } else {
        Modifier.background(ink.raised).drawBehind { dockedHairline(look.position, ink.line) }
    }
    val stretch = when {
        look.floating -> Modifier
        bar.vertical -> Modifier.fillMaxHeight()
        else -> Modifier.fillMaxWidth()
    }
    CompositionLocalProvider(
        LocalToolGlide provides glide,
        LocalBar provides bar,
        LocalMenuOffset provides DpOffset.Zero,
        LocalPopoverEdge provides edge,
    ) {
        if (bar.vertical) {
            Column(
                stretch.width(bar.thickness).popoverEdge(edge).then(surface),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                        .toolGlide(glide, armed),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
                trailing?.invoke()
            }
        } else {
            Row(
                stretch.height(bar.thickness).popoverEdge(edge).then(surface),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                        .toolGlide(glide, armed),
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
                trailing?.invoke()
            }
        }
    }
}
