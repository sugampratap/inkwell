package com.xnotes.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.canvas.TextHandles
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverEdge
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.popoverEdge
import com.xnotes.ui.theme.InkMotion
import kotlin.math.roundToInt

/** Four 58 dp actions in a pill with 6 dp ends: the width to place by until the bar has been measured. */
private val EST_W = (4 * 58 + 12).dp

/** The paste menu: 8 dp over the bar, centred on the 58 dp Paste button (x = btn.x + 29 − 240/2, TX 1034). */
private val PasteMenuSpec = PopoverSpecDp(lead = 91.dp, gap = 8.dp, margin = 8.dp)

/**
 * The flow-editing bar (r3_text Frame 2; long-press with the Text tool, after the word selection lands): Cut, Copy,
 * Paste and Delete, labelled, over the selection so its handles stay visible. Not a popup: it never takes focus (the
 * keyboard stays up) and any canvas touch retires it. Paste opens the explicit paste modes in a menu that does not
 * take focus either. It steps aside while a format-pill menu is up.
 */
@Composable
fun FlowEditMenu(editor: Editor) {
    val rect = editor.flowContextMenu ?: return
    val busy = editor.textChrome.busy

    // Fade out while a pill menu is up, then leave: a pill swallows taps between its actions, so a hidden one must go.
    val fade = remember { Animatable(1f) }
    var gone by remember { mutableStateOf(false) }
    val penDown = LocalPenDown.current
    LaunchedEffect(busy) {
        val snap = penDown() || !ValueAnimator.areAnimatorsEnabled()
        if (busy) {
            if (snap) fade.snapTo(0f) else fade.animateTo(0f, InkMotion.press())
            gone = true
        } else {
            gone = false
            if (snap) fade.snapTo(1f) else fade.animateTo(1f, InkMotion.press())
        }
    }
    if (gone) return

    val density = LocalDensity.current
    val cover = LocalToolbarCover.current
    val hasClip = editor.clipboardHasText()
    val hasSelection = editor.flowHasSelection
    var pasteOpen by remember { mutableStateOf(false) }
    var barW by remember { mutableIntStateOf(0) }
    val edge = remember { PopoverEdge().apply { prefer = PopoverSide.ABOVE } }
    val pasteAnchor = remember { PopoverAnchor() }

    val spot = with(density) {
        placeTextBar(
            left = rect.left.roundToInt(),
            top = rect.top.roundToInt(),
            right = rect.right.roundToInt(),
            bottom = rect.bottom.roundToInt(),
            barW = if (barW > 0) barW else EST_W.roundToPx(),
            barH = FORMAT_PILL_H_DP.dp.roundToPx(),
            viewW = editor.viewportSize().x.roundToInt(),
            gapAbove = 12.dp.roundToPx(),
            // Below: clear of the teardrops hanging there (2 × radius), plus 2, plus the 12 dp gap (TX 1048).
            gapBelow = (2 * TextHandles.RADIUS_DP + 2 + 12).dp.roundToPx(),
            stack = 0,
            minTop = (cover.calculateTopPadding() + 8.dp).roundToPx(),
            margin = 8.dp.roundToPx(),
        )
    }
    val dismiss = { editor.dismissFlowContextMenu() }

    CompositionLocalProvider(LocalPopoverEdge provides edge) {
        InkPill(
            Modifier
                .offset { IntOffset(spot.x, spot.y) }
                .onSizeChanged { barW = it.width }
                .popoverEdge(edge)
                .graphicsLayer { alpha = fade.value },
        ) {
            InkPillAction(
                icon = Ph.scissors,
                label = stringResource(R.string.cut),
                onClick = { editor.flowCut(); dismiss() },
                enabled = hasSelection && !busy,
            )
            InkPillAction(
                icon = Ph.copy,
                label = stringResource(R.string.copy),
                onClick = { editor.flowCopy(); dismiss() },
                enabled = hasSelection && !busy,
            )
            InkPillAction(
                icon = Ph.clipboardText,
                label = stringResource(R.string.paste),
                onClick = { pasteOpen = true },
                on = pasteOpen,
                enabled = hasClip && !busy,
                modifier = Modifier.popoverAnchor(pasteAnchor),
            )
            InkPopover(
                expanded = pasteOpen,
                onDismiss = { pasteOpen = false },
                anchor = pasteAnchor,
                spec = PasteMenuSpec,
                focusable = false,
            ) {
                TextMenuSurface(minWidth = 240.dp) {
                    TextMenuRow(Ph.clipboardText, stringResource(R.string.paste), onClick = {
                        pasteOpen = false
                        editor.pastePlainAtCaret()
                        dismiss()
                    })
                    TextMenuRow(Ph.markdownLogo, stringResource(R.string.paste_markdown), onClick = {
                        pasteOpen = false
                        editor.pasteMarkdownAtCaret()
                        dismiss()
                    })
                    TextMenuRow(Ph.code, stringResource(R.string.paste_code), onClick = {
                        pasteOpen = false
                        editor.pasteAsCodeAtCaret()
                        dismiss()
                    })
                }
            }
            InkPillAction(
                icon = Ph.trash,
                label = stringResource(R.string.delete),
                onClick = { editor.flowDeleteSelection(); dismiss() },
                enabled = hasSelection && !busy,
            )
        }
    }
}
