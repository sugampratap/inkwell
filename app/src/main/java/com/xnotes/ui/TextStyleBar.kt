package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.StickyColors
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillDivider
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverEdge
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/**
 * How far a merely selected box's pill stacks over the box: the selection bar (64 dp, Part 5) plus a 10 dp gap
 * (TX 1308-1312). Part 5 changes this one value if its bar is a different height.
 */
private const val SELECTION_STACK_DP = 74f

/** The font list beside the box: 12 dp off its side, its top 8 dp under the 64 dp pill (TX 1266-1268). */
private val BoxSideSpec = PopoverSpecDp(lead = (-72).dp, gap = 12.dp, margin = 8.dp)

/** The sticky colour strip: under the pill, left-aligned, 10 dp below (TX 1313-1320). */
private val StripSpec = PopoverSpecDp(lead = 0.dp, gap = 10.dp, margin = 8.dp)

private enum class StyleMenu { COLOUR, FONT }

/**
 * The text box's style pill (r3_text Frame 7), for the box being edited or a lone selected one: on a sticky note its
 * colour first, then the font and size wells, and Done while editing. Colour comes from the toolbar's inks, so the
 * pill carries none for a plain box. It sits over the box (stacked over the selection bar when the box is merely
 * selected), never under the toolbar.
 */
@Composable
fun TextStyleBar(editor: Editor) {
    val bar = editor.textBar ?: return
    val density = LocalDensity.current
    val cover = LocalToolbarCover.current
    var barW by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<StyleMenu?>(null) }
    val pillAnchor = remember { PopoverAnchor() }
    // The box's window rect, for the font list beside it; set from the pill's own window spot on every layout.
    val boxEdge = remember { PopoverEdge().apply { prefer = PopoverSide.START } }

    val r = bar.rect
    val spot = with(density) {
        placeTextBar(
            left = r.left.roundToInt(),
            top = r.top.roundToInt(),
            right = r.right.roundToInt(),
            bottom = r.bottom.roundToInt(),
            barW = if (barW > 0) barW else (if (bar.editing) 400.dp else 330.dp).roundToPx(),
            barH = FORMAT_PILL_H_DP.dp.roundToPx(),
            viewW = editor.viewportSize().x.roundToInt(),
            gapAbove = 10.dp.roundToPx(),
            gapBelow = 10.dp.roundToPx(),
            stack = if (bar.editing) 0 else SELECTION_STACK_DP.dp.roundToPx(),
            minTop = (cover.calculateTopPadding() + 8.dp).roundToPx(),
            margin = 8.dp.roundToPx(),
        )
    }

    InkPill(
        Modifier
            .offset { IntOffset(spot.x, spot.y) }
            .onSizeChanged { barW = it.width }
            .popoverAnchor(pillAnchor)
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                val dx = p.x.roundToInt() - spot.x
                val dy = p.y.roundToInt() - spot.y
                boxEdge.bounds = IntRect(
                    dx + r.left.roundToInt(),
                    dy + r.top.roundToInt(),
                    dx + r.right.roundToInt(),
                    dy + r.bottom.roundToInt(),
                )
            },
        padding = 10.dp,
        gap = 2.dp,
        scroll = rememberScrollState(),
    ) {
        // A sticky note leads with its card colour.
        bar.fill?.let { fill ->
            Box {
                FormatButton(stringResource(R.string.stickynote_color), onClick = { menu = if (menu == StyleMenu.COLOUR) null else StyleMenu.COLOUR }, on = menu == StyleMenu.COLOUR) {
                    NoteDot(fill)
                }
                CompositionLocalProvider(LocalPopoverEdge provides null) {
                    InkPopover(
                        expanded = menu == StyleMenu.COLOUR,
                        onDismiss = { menu = null },
                        anchor = pillAnchor,
                        spec = StripSpec,
                        prefer = PopoverSide.BELOW,
                        focusable = false,
                    ) {
                        Row(
                            Modifier
                                .inkSurface(inkRounded(16.dp), InkElevation.MENU)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StickyColors.ALL.forEach { c ->
                                InkSwatch(c.toComposeColor(), selected = c == fill, size = 30.dp) { editor.setStickyColor(c) }
                            }
                            // .tx-cstrip .padd (TX 310): the 30 dp + after the six, filled once a custom colour is set.
                            var picking by remember { mutableStateOf(false) }
                            val custom = fill !in StickyColors.ALL
                            Box {
                                InkAddSwatch(
                                    colour = fill.takeIf { custom },
                                    lit = picking,
                                    contentDescription = stringResource(R.string.material_custom_colour),
                                    size = 30.dp,
                                    cell = 38.dp,
                                ) { picking = true }
                                if (picking) {
                                    StickyCustomColourPicker(
                                        initial = fill,
                                        recents = editor.stickyRecentColors,
                                        onDismiss = { picking = false },
                                        onPick = { editor.setStickyColor(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            InkPillDivider(margin = 7.dp)
        }
        Box {
            FontWell(bar.face, open = menu == StyleMenu.FONT, anchor = null, onClick = { menu = StyleMenu.FONT })
            CompositionLocalProvider(LocalPopoverEdge provides boxEdge) {
                InkPopover(
                    expanded = menu == StyleMenu.FONT,
                    onDismiss = { menu = null },
                    anchor = pillAnchor,
                    spec = BoxSideSpec,
                    prefer = PopoverSide.START,
                ) {
                    FontListMenu(current = bar.face, maxHeight = 470.dp, onPick = { picked ->
                        if (picked != null) editor.setTextFace(picked)
                        menu = null
                    })
                }
            }
        }
        SizeWell(
            bar.pointSize,
            onMinus = { editor.setTextPointSize(bar.pointSize - 1.0) },
            onPlus = { editor.setTextPointSize(bar.pointSize + 1.0) },
            modifier = Modifier.padding(start = 4.dp),
        )
        // Done, so an edit can be finished without tapping off the box.
        if (bar.editing) {
            InkPillDivider(margin = 7.dp)
            InkPillAction(icon = Ph.check, label = stringResource(R.string.done), onClick = { editor.commitText() }, solid = true)
        }
    }
}

/** The note colour (.tx-ndot, TX 311): a 24 dp dot with the swatch ring. */
@Composable
private fun NoteDot(fill: Rgba) {
    val dark = LocalInk.current.isDark
    val colour = fill.toComposeColor()
    Spacer(
        Modifier
            .size(24.dp)
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(colour, radius)
                drawSwatchRing(dark, radius)
            },
    )
}
