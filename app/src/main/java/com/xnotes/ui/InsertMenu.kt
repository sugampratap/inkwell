package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.IconBadge
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.kit.rememberActOnce
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded
import kotlinx.coroutines.launch

/** What the Insert menu can put into a note, in the menu's order. */
enum class InsertKind { PDF, VOICE, IMAGE, CAMERA, SCAN, AUDIO_FILE }

/** The kinds the paged notebook offers: all of them. */
val NOTE_INSERT_KINDS: Set<InsertKind> = InsertKind.entries.toSet()

/**
 * The kinds the infinite canvas offers. It has no source PDF to put pages into, and an audio chip
 * would need text and icon rendering in its GL scene, so it takes pictures only; entries it cannot
 * honour are left out rather than shown dead.
 */
val CANVAS_INSERT_KINDS: Set<InsertKind> = setOf(InsertKind.IMAGE, InsertKind.CAMERA, InsertKind.SCAN)

/** The Insert card's width (mockup §6, defaults row 4: 348, not the 340 standard). */
private val InsertCardWidth = 348.dp

/** The [InsertKind] a tile picks; null for the two tiles that are callbacks (sticky note, table). */
internal fun insertKindOf(tile: InsertTile): InsertKind? = when (tile) {
    InsertTile.PDF -> InsertKind.PDF
    InsertTile.VOICE -> InsertKind.VOICE
    InsertTile.IMAGE -> InsertKind.IMAGE
    InsertTile.CAMERA -> InsertKind.CAMERA
    InsertTile.SCAN -> InsertKind.SCAN
    InsertTile.AUDIO_FILE -> InsertKind.AUDIO_FILE
    InsertTile.STICKY_NOTE, InsertTile.TABLE -> null
}

/** A tile's label: the menu's wording, and "Stop voice recording" on the Voice tile while [recording]. */
internal fun insertTileLabel(tile: InsertTile, recording: Boolean): Int = when (tile) {
    InsertTile.PDF -> R.string.insert_menu_pdf
    InsertTile.VOICE -> if (recording) R.string.insert_menu_stop_recording else R.string.insert_menu_voice_recording
    InsertTile.IMAGE -> R.string.insert_menu_image_item
    InsertTile.CAMERA -> R.string.insert_menu_camera
    InsertTile.SCAN -> R.string.insert_menu_document_scan
    InsertTile.AUDIO_FILE -> R.string.insert_menu_audio_file
    InsertTile.STICKY_NOTE -> R.string.insert_menu_sticky_note
    InsertTile.TABLE -> R.string.insert_menu_table
}

/** A tile's badge icon (Phosphor Regular); the Voice tile shows stop while [recording]. */
private fun insertTileIcon(tile: InsertTile, recording: Boolean): ImageVector = when (tile) {
    InsertTile.PDF -> Ph.filePdf
    InsertTile.VOICE -> if (recording) Ph.stop else Ph.microphone
    InsertTile.IMAGE -> Ph.image
    InsertTile.CAMERA -> Ph.camera
    InsertTile.SCAN -> Ph.scan
    InsertTile.AUDIO_FILE -> Ph.musicNote
    InsertTile.STICKY_NOTE -> Ph.note
    InsertTile.TABLE -> Ph.table
}

/**
 * The Insert card (mockup §6), laid out as Samsung Notes lays out its own: PDF and voice recording as two wide
 * tiles, a rule, then the things placed on the page as a 3-column grid. [kinds] filters it per surface (the canvas
 * gets one row: Image, Camera, Scan); a null [onInsertStickyNote] or [onInsertTable] leaves that tile out. While
 * [recording] the Voice tile reads "Stop voice recording" in a near-black ring. A tap closes the card, then acts;
 * only the first tap of an open acts.
 *
 * [anchor] is the paperclip: the card hangs right-aligned 8 dp under it on the popover host. It defaults to
 * [LocalCardAnchor], which the header provides; with neither, the card falls back to the B2 dropdown.
 */
@Composable
internal fun InsertMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    kinds: Set<InsertKind>,
    recording: Boolean,
    onPick: (InsertKind) -> Unit,
    onInsertStickyNote: (() -> Unit)? = null,
    onInsertTable: (() -> Unit)? = null,
    anchor: PopoverAnchor? = LocalCardAnchor.current,
) {
    val layout = insertLayout(kinds, sticky = onInsertStickyNote != null, table = onInsertTable != null)
    // One tile acts per open: a double tap must not open two pickers.
    val once = rememberActOnce(expanded)
    val pick: (InsertTile) -> Unit = { tile ->
        once.run {
            onDismiss()
            when (tile) {
                InsertTile.STICKY_NOTE -> onInsertStickyNote?.invoke()
                InsertTile.TABLE -> onInsertTable?.invoke()
                else -> insertKindOf(tile)?.let(onPick)
            }
        }
    }
    if (anchor != null) {
        InkPopover(expanded = expanded, onDismiss = onDismiss, anchor = anchor, spec = PopoverSpecs.Insert) {
            Column(
                Modifier
                    .width(InsertCardWidth)
                    .inkSurface(RoundedCornerShape(24.dp), InkElevation.MENU)
                    // Scrolls within the popover's cap, so a short window still reaches the last row.
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                InsertCardBody(layout, recording, pick)
            }
        }
    } else {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            Column(Modifier.width(InsertCardWidth).padding(horizontal = 14.dp, vertical = 6.dp)) {
                InsertCardBody(layout, recording, pick)
            }
        }
    }
}

/** The card's contents: header, the wide row and its rule (when there is one), then the grid in rows of three. */
@Composable
private fun InsertCardBody(layout: InsertLayout, recording: Boolean, onTile: (InsertTile) -> Unit) {
    val ink = LocalInk.current
    Box(Modifier.padding(start = 6.dp, end = 2.dp, bottom = 10.dp).height(32.dp), contentAlignment = Alignment.CenterStart) {
        Text(stringResource(R.string.insert_menu), style = InkType.cardTitle, color = ink.text, maxLines = 1)
    }
    if (layout.wide.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            layout.wide.forEach { tile ->
                InsertTileButton(tile, recording, wide = true, modifier = Modifier.weight(1f)) { onTile(tile) }
            }
            repeat(2 - layout.wide.size) { Spacer(Modifier.weight(1f)) }
        }
        if (layout.grid.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 4.dp, vertical = 12.dp).fillMaxWidth().height(1.dp).background(ink.line2))
        }
    }
    layout.grid.chunked(3).forEachIndexed { i, row ->
        if (i > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { tile ->
                InsertTileButton(tile, recording, wide = false, modifier = Modifier.weight(1f)) { onTile(tile) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * One tile (.ins-t): r16 with a 1 dp line ring drawn inside, or the 2 dp near-black ring on the Stop tile. While
 * held it shrinks to .96 and fades in the surface grey over 120 ms; its badge turns to the pressed grey. The press
 * lives in an Animatable read only while drawing, so a press recomposes nothing. [wide] lays badge and label side
 * by side (64 dp tall), else stacked (86 dp).
 */
@Composable
private fun InsertTileButton(tile: InsertTile, recording: Boolean, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val stop = tile == InsertTile.VOICE && recording
    val label = stringResource(insertTileLabel(tile, recording))
    val icon = insertTileIcon(tile, recording)
    val src = remember { MutableInteractionSource() }
    val press = remember { Animatable(0f) }
    LaunchedEffect(src) {
        src.interactions.collect { i ->
            val target = when (i) {
                is PressInteraction.Press -> 1f
                is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                else -> return@collect
            }
            launch { press.animateTo(target, InkMotion.press()) }
        }
    }
    val radius = cornerOf(16.dp)
    val ring = if (stop) 2.dp else 1.dp
    val frame = modifier
        .height(if (wide) 64.dp else 86.dp)
        .pressScale(src, 0.96f)
        .drawBehind {
            val r = radius.toPx()
            drawRoundRect(ink.surface, alpha = press.value, cornerRadius = CornerRadius(r))
            val w = ring.toPx()
            drawRoundRect(
                color = if (stop) ink.solid else ink.line,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(r - w / 2f),
                style = Stroke(w),
            )
        }
        .clip(inkRounded(16.dp))
        .clickable(src, null, role = Role.Button, onClick = onClick)
    if (wide) {
        Row(
            frame.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(icon, pressed = { press.value > 0.5f })
            Text(label, style = InkType.tileWide, color = ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(
            frame.padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            IconBadge(icon, pressed = { press.value > 0.5f })
            Text(label, style = InkType.tileSmall, color = ink.text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * An indeterminate busy sheet for an insert that works off the main thread (a big PDF merge); not
 * cancellable, but after a while [onBackground] ("Keep in background", or Back) hides it while the
 * insert runs on and lands as usual.
 */
@Composable
internal fun InsertBusyDialog(title: String, onBackground: () -> Unit) {
    com.xnotes.ui.kit.InkProgressSheet(title, stringResource(R.string.may_take_moment), fraction = null, onCancel = null, onBackground = onBackground)
}
