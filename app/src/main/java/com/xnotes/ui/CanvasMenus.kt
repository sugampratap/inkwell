package com.xnotes.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.xnotes.R
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillMetrics
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/**
 * The screenshot tool's floating action, shown above the frozen capture rectangle: a single
 * "Copy as image" button that renders the region and puts it on the system clipboard.
 */
@Composable
fun ScreenshotMenu(editor: Editor) {
    val rect = editor.screenshotMenu ?: return
    val ink = LocalInk.current
    val density = LocalDensity.current

    val barHeightPx = with(density) { SCREENSHOT_BAR_H.toPx() }
    val barWidthPx = with(density) { 170.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val centerX = ((rect.left + rect.right) / 2.0).toFloat()
    val xPx = (centerX - barWidthPx / 2f).coerceAtLeast(with(density) { 8.dp.toPx() })
    val yPx = if (rect.top.toFloat() - barHeightPx - gap > 0f) {
        rect.top.toFloat() - barHeightPx - gap
    } else {
        rect.bottom.toFloat() + gap
    }
    Row(
        modifier = Modifier
            .offset(with(density) { xPx.toDp() }, with(density) { yPx.toDp() })
            .height(SCREENSHOT_BAR_H)
            .inkSurface(RoundedCornerShape(percent = 50), InkElevation.FLOAT)
            .clickable(role = Role.Button) { editor.copyScreenshotAsImage() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label says it; the icon is decoration (it used to repeat the label to TalkBack).
        Icon(Ph.copy, contentDescription = null, tint = ink.text, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.copy_as_image),
            style = InkType.buttonSmall,
            color = ink.text,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The screenshot button's height: the one the placement above has always reserved. */
private val SCREENSHOT_BAR_H = 44.dp

/** The PDF bars are the text format pill's icon pill (TX 68–74): 44 dp round actions, 10 dp ends, 2 dp gaps. */
private val PdfBarPadding = 10.dp
private val PdfBarGap = 2.dp

/** The width of a PDF bar holding [n] icon actions, to place it by. */
private fun pdfBarWidth(n: Int): Dp =
    InkPillMetrics.IconActionSize * n + PdfBarGap * (n - 1).coerceAtLeast(0) + PdfBarPadding * 2

/**
 * The PDF text selection's bar: Copy, the four marks (made in the active ink colour), and behind
 * the overflow the apps that act on selected text ("Translate", "Search" and the like). Never takes
 * focus; a canvas touch hides it until the selection, or the view under it, settles again.
 */
@Composable
fun PdfSelectionMenu(editor: Editor) {
    val rect = editor.pdfTextMenu ?: return
    val density = LocalDensity.current
    val actions = remember { editor.pdfTextActions() }
    var overflowOpen by remember { mutableStateOf(false) }

    val count = 1 + PDF_MARKS.size + if (actions.isEmpty()) 0 else 1
    val barHeightPx = with(density) { InkPillMetrics.Height.toPx() }
    val barWidthPx = with(density) { pdfBarWidth(count).toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    // When pushed below the selection, also clear the teardrop handles hanging there.
    val handleClearance = with(density) { (2 * com.xnotes.canvas.TextHandles.RADIUS_DP).dp.toPx() }
    val centerX = ((rect.left + rect.right) / 2.0).toFloat()
    val xPx = (centerX - barWidthPx / 2f).coerceAtLeast(margin)
    val above = rect.top.toFloat() - barHeightPx - gap
    val below = rect.bottom.toFloat() + handleClearance + gap
    // A selection taller than the view leaves no room either side: keep the bar on screen.
    val maxY = (editor.state.viewportH - barHeightPx - margin).coerceAtLeast(margin)
    val yPx = (if (above > 0f) above else below).coerceIn(margin, maxY)

    InkPill(
        Modifier.offset(with(density) { xPx.toDp() }, with(density) { yPx.toDp() }),
        padding = PdfBarPadding,
        gap = PdfBarGap,
    ) {
        InkPillAction(Ph.copy, null, { editor.copyPdfText() }, contentDescription = stringResource(R.string.copy))
        for ((type, icon, label) in PDF_MARKS) {
            InkPillAction(icon, null, { editor.markPdfSelection(type) }, contentDescription = stringResource(label))
        }
        if (actions.isNotEmpty()) {
            Box {
                InkPillAction(
                    Ph.dotsThree, null, { overflowOpen = true },
                    on = overflowOpen,
                    contentDescription = stringResource(R.string.more),
                )
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                    modifier = Modifier.widthIn(min = 248.dp),
                    properties = PopupProperties(focusable = false),
                ) {
                    for ((label, app) in actions) {
                        InkMenuRow(label, {
                            overflowOpen = false
                            editor.processPdfText(app)
                        })
                    }
                }
            }
        }
    }
}

/**
 * A markup's tap menu: its colour and kind (each a small list), its note, Copy (the text it marks),
 * Delete, and Open link when the tap was on a link. Placed like the selection bar, by the markup.
 */
@Composable
fun MarkupMenu(editor: Editor) {
    val menu = editor.markupMenu ?: return
    val density = LocalDensity.current
    var colorsOpen by remember { mutableStateOf(false) }
    var kindsOpen by remember { mutableStateOf(false) }
    val count = 5 + if (menu.openLink != null) 1 else 0
    val barHeightPx = with(density) { InkPillMetrics.Height.toPx() }
    val barWidthPx = with(density) { pdfBarWidth(count).toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val rect = menu.anchor
    val xPx = (((rect.left + rect.right) / 2.0).toFloat() - barWidthPx / 2f).coerceAtLeast(margin)
    val above = rect.top.toFloat() - barHeightPx - gap
    val maxY = (editor.state.viewportH - barHeightPx - margin).coerceAtLeast(margin)
    val yPx = (if (above > 0f) above else rect.bottom.toFloat() + gap).coerceIn(margin, maxY)
    val m = menu.markup

    InkPill(
        Modifier.offset(with(density) { xPx.toDp() }, with(density) { yPx.toDp() }),
        padding = PdfBarPadding,
        gap = PdfBarGap,
    ) {
        Box {
            InkSwatch(
                m.color.toComposeColor(),
                selected = false,
                size = 22.dp,
                cell = InkPillMetrics.IconActionSize,
                contentDescription = stringResource(R.string.markup_colour),
            ) { colorsOpen = true }
            DropdownMenu(expanded = colorsOpen, onDismissRequest = { colorsOpen = false }, properties = PopupProperties(focusable = false)) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val words = rememberExplorerWords()
                    editor.toolbarColors.take(editor.toolbarColorCount).forEach { c ->
                        // No name here before Part 5: the picker's swatch name (R4 #12, "Blue, #1E88E5").
                        InkSwatch(c.toComposeColor(), selected = c.r == m.color.r && c.g == m.color.g && c.b == m.color.b, size = 26.dp, cell = 36.dp, contentDescription = swatchName(words, c)) {
                            colorsOpen = false
                            editor.recolorMarkup(c)
                        }
                    }
                }
            }
        }
        Box {
            InkPillAction(
                markIcon(m.type), null, { kindsOpen = true },
                on = kindsOpen,
                contentDescription = stringResource(R.string.markup_kind),
            )
            DropdownMenu(
                expanded = kindsOpen,
                onDismissRequest = { kindsOpen = false },
                modifier = Modifier.widthIn(min = 248.dp),
                properties = PopupProperties(focusable = false),
            ) {
                for ((type, icon, label) in PDF_MARKS) {
                    InkMenuRow(stringResource(label), {
                        kindsOpen = false
                        if (type != m.type) editor.retypeMarkup(type)
                    }, icon = icon, checked = type == m.type)
                }
            }
        }
        InkPillAction(Ph.notePencil, null, { editor.editMarkupNote() }, contentDescription = stringResource(R.string.markup_note))
        InkPillAction(Ph.copy, null, { editor.copyMarkupText() }, contentDescription = stringResource(R.string.copy))
        InkPillAction(Ph.trash, null, { editor.deleteMarkup() }, contentDescription = stringResource(R.string.delete))
        if (menu.openLink != null) {
            InkPillAction(Ph.arrowSquareOut, null, { editor.openMarkupLink() }, contentDescription = stringResource(R.string.open_link))
        }
    }
}

/**
 * A markup's note, shown by a first tap on it: placed as its menu would be, then moving with the
 * page, one size at any zoom. It takes no focus and leaves the page working around it; on itself it
 * takes touches, so a long note scrolls. It goes once scrolled out of view.
 */
@Composable
fun MarkupNotePeek(editor: Editor) {
    val peek = editor.notePeek ?: return
    val note = peek.markup.note ?: return
    val ink = LocalInk.current
    val density = LocalDensity.current
    val scroll = remember(peek) { ScrollState(0) }
    val maxHeight = with(density) { (editor.state.viewportH * NOTE_PEEK_MAX_HEIGHT).toDp() }.coerceAtMost(240.dp)
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                editor.notePeekTick
                editor.notePeekLaidOut(p.width, p.height)
                val at = editor.notePeekRect(p.width, p.height)
                layout(p.width, p.height) {
                    if (at != null) p.place(at.left.roundToInt(), at.top.roundToInt())
                }
            }
            .widthIn(max = 300.dp)
            .heightIn(max = maxHeight)
            .inkSurface(MaterialTheme.shapes.medium, InkElevation.SOFT)
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(note, style = InkType.body, color = ink.text)
    }
}

/** The most of the view's height a markup's note window takes before it scrolls. */
private const val NOTE_PEEK_MAX_HEIGHT = 0.4f

/** A markup's note to read or write: plain text, kept on Save, dropped when emptied or deleted. */
@Composable
fun MarkupNoteDialog(editor: Editor) {
    val target = editor.markupNote ?: return
    var text by remember(target) { mutableStateOf(target.markup.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = { editor.dismissMarkupNote() },
        title = { Text(stringResource(R.string.markup_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.markup_note_hint)) },
                minLines = 3,
                maxLines = 10,
            )
        },
        confirmButton = { TextButton(onClick = { editor.saveMarkupNote(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (target.markup.note != null) {
                    TextButton(onClick = { editor.saveMarkupNote("") }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = { editor.dismissMarkupNote() }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun markIcon(type: com.xnotes.core.model.MarkupType): ImageVector = PDF_MARKS.first { it.first == type }.second

/** The selection bar's marks, in the order the markup tool's popup lists them. */
private val PDF_MARKS = listOf(
    Triple(com.xnotes.core.model.MarkupType.HIGHLIGHT, Ph.highlighter, R.string.markup_highlight),
    Triple(com.xnotes.core.model.MarkupType.UNDERLINE, Ph.textUnderline, R.string.underline),
    Triple(com.xnotes.core.model.MarkupType.STRIKEOUT, Ph.textStrikethrough, R.string.strikethrough),
    Triple(com.xnotes.core.model.MarkupType.SQUIGGLY, Ph.waveSine, R.string.markup_squiggly),
)
