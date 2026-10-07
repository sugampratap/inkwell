package com.xnotes.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.IconBadge
import com.xnotes.ui.kit.InkGroup
import com.xnotes.ui.kit.InkGroupHeader
import com.xnotes.ui.kit.InkGroupRow
import com.xnotes.ui.kit.InkPillSegmented
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/** What a note is shared as. */
enum class ShareFormat {
    /** Ink burned into the pages: the smallest file, the same in every app, nothing left to change. */
    PDF,

    /** Ink as annotations other PDF apps can still edit, with the note inside for Inkwell to reopen. */
    EDITABLE_PDF,

    /** The note file itself, for another copy of Inkwell. */
    NOTE,

    /** Each page as a picture. */
    IMAGES,
}

/** Which pages a share covers, when it is shared from inside the note. */
enum class ShareRange { ALL, CURRENT }

/**
 * Share & export (r2_panel_share_empty Frame 3): choose, then share. Inside a note ([canChooseRange])
 * the Pages control cuts it down to page [currentPage] of [pageCount]; a canvas ([isCanvas]) offers
 * PDF and the canvas file only. Each format card says what you give up. Bookmarks from headings
 * ([headingBookmarks], the same switch as Settings) shows when [onHeadingBookmarks] is given and
 * applies to the PDFs. The footer says what goes, offers [onSave] where it can (a PDF, or images
 * inside a note), and Share sends it ([onShare]). The callers close the sheet.
 */
@Composable
fun ShareSheet(
    name: String,
    isCanvas: Boolean,
    canChooseRange: Boolean,
    onDismiss: () -> Unit,
    pageCount: Int = 0,
    currentPage: Int = 0,
    headingBookmarks: Boolean = true,
    onHeadingBookmarks: ((Boolean) -> Unit)? = null,
    onSave: ((ShareFormat, ShareRange) -> Unit)? = null,
    onShare: (ShareFormat, ShareRange) -> Unit,
) {
    val ink = LocalInk.current
    var range by remember { mutableStateOf(ShareRange.ALL) }
    var format by remember { mutableStateOf(ShareFormat.PDF) }
    val inside = canChooseRange && !isCanvas
    val single = inside && ShareModel.single(range, pageCount)
    val what = when {
        isCanvas -> stringResource(R.string.share_summary_canvas)
        !inside -> null
        range == ShareRange.CURRENT -> stringResource(R.string.page_n, currentPage + 1)
        else -> pluralStringResource(R.plurals.share_n_pages, pageCount, pageCount)
    }
    val formatName = stringResource(ShareModel.formatTitle(format, isCanvas, single))
    // From the library the page count is not known, so images are not counted there.
    val label = ShareModel.shareLabel(format, isCanvas, single, counted = inside)
    val shareText = if (label.plural) pluralStringResource(label.res, pageCount, pageCount) else stringResource(label.res)
    InkSheet(
        title = stringResource(R.string.share),
        onDismiss = onDismiss,
        subtitle = if (inside) "$name · ${pluralStringResource(R.plurals.share_n_pages, pageCount, pageCount)}" else name,
        width = 620.dp,
        footer = {
            Text(
                if (what != null) stringResource(R.string.share_summary, what, formatName) else formatName,
                style = InkType.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = ink.text2,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (onSave != null && ShareModel.canSave(format, isCanvas, inside)) {
                InkSecondaryButton(
                    stringResource(if (format == ShareFormat.PDF) R.string.share_save_pdf else if (single) R.string.share_save_image else R.string.share_save_images),
                    { onSave(format, range) },
                    icon = Ph.downloadSimple,
                )
            }
            InkStrongButton(shareText, { onShare(format, range) }, icon = Ph.shareNetwork)
        },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp)) {
            if (inside) {
                Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_pages_label), style = InkType.label, color = ink.text, modifier = Modifier.weight(1f))
                    InkPillSegmented(
                        listOf(ShareRange.ALL, ShareRange.CURRENT),
                        range,
                        label = { if (it == ShareRange.ALL) pluralStringResource(R.plurals.share_all_n_pages, pageCount, pageCount) else stringResource(R.string.share_page_only, currentPage + 1) },
                        onSelect = { range = it; format = ShareModel.coerce(format, it) },
                        segmentWidth = 120.dp,
                    )
                }
            }
            Text(stringResource(R.string.share_format_label), style = InkType.label, color = ink.text, modifier = Modifier.padding(bottom = 10.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (pair in ShareModel.formats(isCanvas).chunked(2)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (f in pair) {
                            FormatCard(
                                icon = formatIcon(f, isCanvas),
                                title = stringResource(ShareModel.formatTitle(f, isCanvas, single)),
                                hint = stringResource(ShareModel.formatHint(f, isCanvas, single, range)),
                                selected = f == format,
                                enabled = !ShareModel.disabled(f, range),
                                modifier = Modifier.weight(1f),
                            ) { format = f }
                        }
                    }
                }
            }
            if (!isCanvas && onHeadingBookmarks != null) {
                InkGroupHeader(stringResource(R.string.share_pdf_options))
                InkGroup {
                    val on = ShareModel.headingsApply(format)
                    InkGroupRow(
                        title = stringResource(R.string.settings_pdf_bookmarks),
                        subtitle = stringResource(R.string.share_headings_desc),
                        icon = Ph.bookmarksSimple,
                        first = true,
                        enabled = on,
                        onClick = { onHeadingBookmarks(!headingBookmarks) },
                    ) { InkSwitch(headingBookmarks, null, enabled = on) }
                }
            }
        }
    }
}

private fun formatIcon(f: ShareFormat, isCanvas: Boolean): ImageVector = when (f) {
    ShareFormat.PDF -> Ph.filePdf
    ShareFormat.EDITABLE_PDF -> Ph.notePencil
    ShareFormat.NOTE -> if (isCanvas) Ph.infinity else Ph.notebook
    ShareFormat.IMAGES -> Ph.images
}

/** A format card (.ps-fmt): badge and name, then what it gives up; chosen = 2dp ink ring and a check; disabled fades to .42. */
@Composable
private fun FormatCard(icon: ImageVector, title: String, hint: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .heightIn(min = 120.dp)
            .pressScale(src, 0.98f)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ink.solid else ink.line, shape)
            .selectable(selected, src, LocalIndication.current, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(end = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon)
                Text(title, style = InkType.rowStrong.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold), color = ink.text)
            }
            Text(hint, style = InkType.meta, color = ink.text2)
        }
        if (selected) Icon(Ph.checkCircleFill, null, tint = ink.text, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(22.dp))
    }
}
