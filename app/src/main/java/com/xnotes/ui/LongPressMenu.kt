package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.Fl
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.animateUnlessPenDown
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.kit.rememberActOnce
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** A table size picker waiting for a choice; the table goes at [at], or the visible page centre when null. */
data class TablePickerRequest(val at: Pt?)

/**
 * What the long-press menu needs from whichever editor is open. Same reasoning as
 * [SelectionMenuHost]: one menu, taken through an interface, rather than two that resemble each
 * other and drift.
 *
 * The first members every editor has. The rest default to "not here", and the menu hides an entry
 * its host does not offer, so the infinite canvas (which has no text boxes, notes or tables, and no
 * page to share) shows only what works there.
 */
interface LongPressMenuHost {
    /** Where the press landed, or null when no menu is open. */
    val contextMenu: ContextMenuTarget?

    val hasClipboardItems: Boolean
    val clipboardHasImage: Boolean

    fun pasteItemsAt(content: Pt)
    fun pasteClipboardImageAt(content: Pt)
    fun dismissContextMenu()

    /** Release [item], so it can be selected again. */
    fun unlockItem(item: CanvasItem)

    /** Sticky note, text box and table can be placed here. */
    val canInsertObjects: Boolean get() = false
    fun insertStickyNoteAt(content: Pt) {}
    fun insertTextBoxAt(content: Pt) {}
    fun insertTableAt(content: Pt?, rows: Int, cols: Int) {}

    /** The table size picker waiting for a choice, or null; TableChrome shows it. */
    val tablePickerRequest: TablePickerRequest? get() = null
    fun openTablePicker(atContent: Pt?) {}
    fun closeTablePicker() {}

    /** "Select all" (everything on the pressed page). */
    val canSelectAll: Boolean get() = false
    fun selectAllAt(content: Pt) {}

    /** The pressed page can be shared, or copied to the clipboard, as a picture. */
    val canShareAsImage: Boolean get() = false
    fun sharePageImageAt(content: Pt) {}
    fun copyPageImageAt(content: Pt) {}

    /** The press ring's ink (SC 87): what reads on the paper under the press, so near-black on the cream page and light
     *  on a dark one. Both editors override it with the paper they show. */
    val pressRingInk: Rgba get() = Rgba(0x22, 0x22, 0x22)
}

/** "Add here" tiles (SC 83): 12.5/15 SemiBold, centred. */
private val TileLabel = InkType.tileSmall.copy(fontSize = 12.5.sp, lineHeight = 15.sp)

/**
 * The card a press held still on the page opens (SC Frame 3; a finger in any tool, or the S Pen held still): text
 * rows for the clipboard and the page, then "Add here" with what can be put down right there, as Insert's tiles. It
 * rises 28 dp over the press (below it near the top) on Part 3's popover motion, and a faint ring marks the press
 * point. Only what can happen here appears ([longPressLayout]); a press on a locked item offers only Unlock, since
 * there is no other way back. Any action closes the card first; a tap outside or Back closes it.
 */
@Composable
fun LongPressMenu(host: LongPressMenuHost, onInsertImageAt: (Pt) -> Unit) {
    val target = host.contextMenu ?: return
    val density = LocalDensity.current.density
    // A new press is a new card: its ring fades in again and its rows act again.
    key(target) {
        val at = target.content
        val x = target.viewportX.roundToInt()
        val y = target.viewportY.roundToInt()
        val anchor = remember { PopoverAnchor() }
        val once = rememberActOnce(true)
        val act: (() -> Unit) -> () -> Unit = { block ->
            {
                once.run {
                    host.dismissContextMenu()
                    block()
                }
            }
        }
        val placer = remember(density) {
            PopoverPlacer { a, _, window, content -> longPressSpot(IntOffset(a.left, a.top), content, window, density) }
        }
        val layout = longPressLayout(
            locked = target.locked != null,
            clipItems = host.hasClipboardItems,
            clipImage = host.clipboardHasImage,
            selectAll = host.canSelectAll,
            sharePage = host.canShareAsImage,
            insertObjects = host.canInsertObjects,
        )
        PressRing(x, y, host.pressRingInk.toComposeColor())
        // A 0-size anchor at the press, with the card's Popup inside it, so the card hangs from the press point.
        Box(Modifier.offset { IntOffset(x, y) }.size(0.dp).popoverAnchor(anchor)) {
            CompositionLocalProvider(LocalPopoverEdge provides null) {
                InkPopover(expanded = true, onDismiss = { host.dismissContextMenu() }, anchor = anchor, prefer = PopoverSide.ABOVE, placer = placer) {
                    LongPressCard(layout, target, at, host, act, onInsertImageAt)
                }
            }
        }
    }
}

/** .sc-lp (SC 79-86): the rows, then "Add here" and its four columns of tiles. */
@Composable
private fun LongPressCard(
    layout: LpLayout,
    target: ContextMenuTarget,
    at: Pt,
    host: LongPressMenuHost,
    act: (() -> Unit) -> () -> Unit,
    onInsertImageAt: (Pt) -> Unit,
) {
    val ink = LocalInk.current
    Column(
        Modifier
            .width(if (layout.unlockOnly) 220.dp else 320.dp)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            // It scrolls rather than clips when the popover's max height is short of it (a press near a short window's edge).
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
    ) {
        val locked = target.locked
        if (layout.unlockOnly && locked != null) {
            SelRow(Fl.lockOpen, stringResource(R.string.lp_unlock), act { host.unlockItem(locked) })
            return@Column
        }
        for (row in layout.rows) {
            if (row == LpRow.SELECT_ALL && layout.ruleBeforeSelectAll) SelRule()
            when (row) {
                LpRow.PASTE -> SelRow(Fl.clipboardPaste, stringResource(R.string.lp_paste), act { host.pasteItemsAt(at) })
                LpRow.PASTE_IMAGE -> SelRow(Fl.clipboardImage, stringResource(R.string.lp_paste_image), act { host.pasteClipboardImageAt(at) })
                LpRow.SELECT_ALL -> SelRow(Fl.selectAllOn, stringResource(R.string.lp_select_all), act { host.selectAllAt(at) })
                LpRow.COPY_PAGE -> SelRow(Fl.imageCopy, stringResource(R.string.lp_copy_page), act { host.copyPageImageAt(at) })
                LpRow.SHARE_PAGE -> SelRow(Fl.shareAndroid, stringResource(R.string.lp_share_page), act { host.sharePageImageAt(at) })
            }
        }
        if (layout.ruleBeforeTiles) SelRule()
        if (layout.tiles.isNotEmpty()) {
            Text(
                stringResource(R.string.lp_add_here),
                style = InkType.label,
                color = ink.text,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp).semantics { heading() },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 4) {
                    Box(Modifier.weight(1f)) {
                        when (layout.tiles.getOrNull(i)) {
                            LpTile.STICKY_NOTE -> AddHereTile(Fl.note, stringResource(R.string.lp_sticky_note), act { host.insertStickyNoteAt(at) })
                            LpTile.TEXT_BOX -> AddHereTile(Fl.textbox, stringResource(R.string.lp_text_box), act { host.insertTextBoxAt(at) })
                            LpTile.IMAGE -> AddHereTile(Fl.image, stringResource(R.string.lp_image), act { onInsertImageAt(at) })
                            LpTile.TABLE -> AddHereTile(Fl.table, stringResource(R.string.lp_table), act { host.openTablePicker(at) })
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** An Add here tile: 82 dp, r12; a 24 dp Fluent icon over the label, as on the selection bar; .95 under the finger. */
@Composable
private fun AddHereTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .height(82.dp)
            .pressScale(src, 0.95f)
            .clip(inkRounded(12.dp))
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(24.dp))
        Text(label, style = TileLabel, color = ink.text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * .sc-press (SC 87-88): a 46 dp ring at the press point, [ink] at 6 % inside, a 1.5 dp ring at 28 % and a 6 dp dot at
 * 45 %, so you can see where the note, box, picture or table will land. It fades in over 180 ms (in the layer only),
 * and snaps while the pen is down.
 */
@Composable
private fun PressRing(x: Int, y: Int, ink: Color) {
    val fade = remember { Animatable(0f) }
    val penDown = LocalPenDown.current
    LaunchedEffect(Unit) {
        animateUnlessPenDown(penDown, settle = { fade.snapTo(1f) }) { fade.animateTo(1f, InkMotion.fade()) }
    }
    val half = with(LocalDensity.current) { 23.dp.roundToPx() }
    Spacer(
        Modifier
            .offset { IntOffset(x - half, y - half) }
            .size(46.dp)
            .graphicsLayer { alpha = fade.value }
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(ink.copy(alpha = 0.06f), r)
                drawCircle(ink.copy(alpha = 0.28f), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                drawCircle(ink.copy(alpha = 0.45f), 3.dp.toPx())
            },
    )
}
