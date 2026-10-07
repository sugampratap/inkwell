package com.xnotes.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.followAppSystemBars
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.inkSelectionRing
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import com.xnotes.ui.theme.tnum

/** What a preview can do with the file it shows; [openBeside] is null when there's no other note to pair it with. */
internal class PreviewActions(
    val open: () -> Unit,
    val openBeside: (() -> Unit)?,
    val share: () -> Unit,
    val exportPdf: () -> Unit,
    val color: (Rgba?) -> Unit,
)

/**
 * A file up close without opening it: its first page large, what it is, when it was made and changed, where
 * it lives, and a strip of its first pages. The Columns layout shows it beside the columns; with "Tapping a
 * file shows a preview" on, a tap shows it in a dialog.
 */
@Composable
internal fun FilePreview(b: ExplorerBody, e: BrowseEntry, where: String?, actions: PreviewActions, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val kind = b.kind(e)
    val pages = b.pages(e)
    val landscape = kind == EntryKind.CANVAS
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 440.dp
            // The first page in its own shape, within an upright or landscape frame to match.
            val img = rememberThumb(b.editor, e, whole = true)
            val ratio = pageRatio(img, if (landscape) CANVAS_RATIO else A4_RATIO)
            val (pageW, pageH) = pageFit(ratio, if (ratio > 1f) 293.dp else 208.dp, if (ratio > 1f) 208.dp else 293.dp)
            val details: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(b.label(e), style = InkType.sheetTitle, color = ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(kindIcon(kind), null, tint = ink.text2, modifier = Modifier.size(15.dp))
                            val count = if (pages > 0 && !landscape) pluralStringResource(R.plurals.pages_count, pages, pages) else null
                            Text(dotJoined(listOfNotNull(kindLabel(kind), count)), style = InkType.meta, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow(stringResource(R.string.modified), formatFull(b.words, e.modified, b.clock24, b.zone))
                        DetailRow(stringResource(R.string.created), formatFull(b.words, e.created, b.clock24, b.zone))
                        DetailRow(stringResource(R.string.sort_size), formatSize(e.size))
                        if (where != null) DetailRow(stringResource(R.string.where_label), where)
                    }
                    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkStrongButton(stringResource(R.string.open), actions.open, Modifier.fillMaxWidth(), icon = Ph.notePencil)
                        InkSecondaryButton(stringResource(R.string.open_side_by_side), { actions.openBeside?.invoke() }, Modifier.fillMaxWidth(), icon = Ph.columns, enabled = actions.openBeside != null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExplorerIcon(Ph.shareNetwork, stringResource(R.string.share), onClick = actions.share)
                        ExplorerIcon(Ph.filePdf, stringResource(R.string.export_pdf), onClick = actions.exportPdf)
                        var colors by remember { mutableStateOf(false) }
                        Box {
                            ExplorerIcon(Ph.palette, stringResource(R.string.colour_code)) { colors = true }
                            DropdownMenu(expanded = colors, onDismissRequest = { colors = false }) {
                                ColorCodeMenuContent { c -> colors = false; actions.color(c) }
                            }
                        }
                    }
                }
            }
            val page: @Composable () -> Unit = {
                val shape = RoundedCornerShape(6.dp)
                ThumbImage(img, whole = true, Modifier.size(pageW, pageH).clip(shape).border(1.dp, ink.line2, shape))
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { page(); details() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { page(); Box(Modifier.weight(1f)) { details() } }
            }
        }
        if (!landscape && pages > 1) {
            val stripPx = with(LocalDensity.current) { 87.dp.roundToPx() }
            val strip by key(b.editor.thumbnailVersion) {
                produceState<List<ImageBitmap>?>(null, e.documentUri, e.modified) {
                    value = b.editor.pageStrip(e.documentUri, e.name, PREVIEW_PAGES, stripPx)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pages), style = InkType.label, color = ink.text2)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val shown = strip
                    if (shown == null) {
                        items(minOf(pages, PREVIEW_PAGES)) { i -> StripPage(null, i) }
                    } else {
                        itemsIndexed(shown) { i, img -> StripPage(img, i) }
                    }
                }
            }
        }
    }
}

private const val PREVIEW_PAGES = 12

private val StripNumber = InkType.small.tnum()
private val StripFirst = StripNumber.copy(fontWeight = FontWeight.Bold)

@Composable
private fun StripPage(img: ImageBitmap?, index: Int) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val first = index == 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val shape = RoundedCornerShape(4.dp)
        // The first page's ring sits outside the sheet, against the background rather than the paper.
        Box(
            Modifier.height(87.dp).widthIn(min = 40.dp, max = 124.dp).inkSelectionRing(first, shape).clip(shape)
                .background(palette.paper.toComposeColor())
                .border(1.dp, ink.line2, shape),
        ) {
            if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.height(87.dp))
            else Spacer(Modifier.size(62.dp, 87.dp))
        }
        Text("${index + 1}", style = if (first) StripFirst else StripNumber, color = if (first) ink.text else ink.text2)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val ink = LocalInk.current
    Row {
        Text(label, style = InkType.meta, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(76.dp))
        Text(value, style = InkType.meta, color = ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** [FilePreview] in a dialog, for "Tapping a file shows a preview". */
@Composable
internal fun FilePreviewDialog(b: ExplorerBody, e: BrowseEntry, where: String?, actions: PreviewActions, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The app's system bars, as every dialog keeps them (hidden in full screen).
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.DisposableEffect(window) {
            window?.followAppSystemBars()
            onDispose {}
        }
        // The 24dp padding leaves the pop shadow room to fall.
        Box(
            Modifier
                .padding(24.dp)
                .widthIn(max = 720.dp)
                .heightIn(max = 760.dp)
                .inkSurface(RoundedCornerShape(28.dp), InkElevation.POP),
        ) {
            FilePreview(b, e, where, actions)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                InkIconButton(Ph.x, stringResource(R.string.close_preview), onDismiss)
            }
        }
    }
}
