package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.util.DocumentKind
import com.xnotes.settings.Preferences
import com.xnotes.settings.ThumbShape
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkConfirmSheet
import com.xnotes.ui.kit.InkEmptyState
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkSearchField
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.inkRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private val WAS_IN_W = 160.dp
private val DELETED_W = 150.dp
private val LEFT_W = 112.dp
private val ACTIONS_W = 158.dp

/** How Trash's header shares its width, out of Compose. */
internal object TrashLayout {
    /** Below this, the search leaves the header row for a line of its own. */
    val STACK_BELOW = 560.dp

    /** Roughly what the title, count, Empty trash, gaps and padding take beside the search. */
    private val BESIDE = 360.dp

    /** The search's width in a header [width] wide, or null when it goes on its own line. */
    fun searchWidth(width: Dp): Dp? = if (width < STACK_BELOW) null else (width - BESIDE).coerceIn(140.dp, 260.dp)
}

/**
 * Trash (r2_panel_share_empty Frame 4): what was deleted, where it came from, when, and how long it
 * has left. Items go back to their folder on Restore; Empty trash and Delete permanently remove them
 * for good, each after asking. The rule is stated up top, and Change opens the setting that sets it.
 * Expired items are purged as the pane opens. Below 720dp the columns fold into one line per row.
 */
@Composable
internal fun TrashPane(editor: Editor, sidebarOpen: Boolean, onShowSidebar: () -> Unit, onOpenPreferences: () -> Unit) {
    val ink = LocalInk.current
    val context = LocalContext.current
    val root = editor.browseRoot
    val prefs = remember(editor.prefsVersion) { editor.preferences }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    val items by produceState<List<TrashItem>?>(null, root, refresh) {
        value = if (root == null) emptyList() else withContext(Dispatchers.IO) {
            editor.purgeExpiredTrash(root, prefs.trashDays)
            editor.listTrash(root)
        }
    }
    val rootName by produceState(root?.let { editor.cachedRootName(it) }, root) {
        value = root?.let { r -> withContext(Dispatchers.IO) { editor.browseRootName(r) } }
    }
    val now = remember(items) { System.currentTimeMillis() }
    val clock24 = android.text.format.DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()
    val q = query.text.trim()
    val shown = items.orEmpty().filter { q.isEmpty() || it.entry.name.contains(q, ignoreCase = true) }
    val count = items?.size ?: 0

    Column(Modifier.fillMaxSize()) {
        // The header (.mh): title, count, search, Empty trash; a hairline under it. The search takes
        // the room the rest leaves (260dp at most) and, below [TrashLayout.STACK_BELOW], a line of its
        // own; the title gives way first, so Empty trash is always on screen.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ink.line2, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
        ) {
            val searchW = TrashLayout.searchWidth(maxWidth)
            val sidePad = if (sidebarOpen) 32.dp else 12.dp
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(start = sidePad, end = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!sidebarOpen) InkIconButton(Ph.sidebarSimple, stringResource(R.string.show_sidebar), onShowSidebar)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.trash), style = InkType.display, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (items != null) Text(pluralStringResource(R.plurals.trash_items, count, count), style = InkType.meta, color = ink.text2, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (searchW != null) InkSearchField(query, { query = it }, stringResource(R.string.trash_search), Modifier.width(searchW))
                    InkSecondaryButton(stringResource(R.string.empty_trash), { confirmEmpty = true }, icon = Ph.trash, danger = true, enabled = !busy && !items.isNullOrEmpty())
                }
                if (searchW == null) {
                    InkSearchField(query, { query = it }, stringResource(R.string.trash_search), Modifier.fillMaxWidth().padding(start = sidePad, end = 32.dp, bottom = 16.dp))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp)) {
            RetentionBanner(prefs.trashDays) {
                SettingsLink.request(SettingId.KEEP_DELETED)
                onOpenPreferences()
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 720.dp
                val list = items
                when {
                    root == null -> QuietLine(stringResource(R.string.trash_no_root))
                    list == null -> QuietLine(stringResource(R.string.loading))
                    list.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
                        InkEmptyState(
                            stringResource(R.string.trash_empty_title),
                            art = { InkLineArt(LineArt.TRASH) },
                            body = when (prefs.trashDays) {
                                0 -> stringResource(R.string.trash_empty_body_off)
                                Preferences.TRASH_FOREVER -> stringResource(R.string.trash_empty_body_forever)
                                else -> pluralStringResource(R.plurals.trash_empty_body, prefs.trashDays, prefs.trashDays)
                            },
                        )
                    }
                    shown.isEmpty() -> QuietLine(stringResource(R.string.trash_no_match, q))
                    else -> Column(Modifier.fillMaxSize()) {
                        TableHead(wide)
                        // The keyboard slides over the rows; its height joins the end padding so the last ones scroll clear.
                        val endPad = WindowInsets.ime.exclude(WindowInsets.systemBars).add(WindowInsets(bottom = 40.dp)).asPaddingValues()
                        LazyColumn(Modifier.weight(1f), contentPadding = endPad) {
                            items(shown, key = { it.wrapperDocId }) { item ->
                                TrashRow(
                                    editor, item, wide, rootName ?: stringResource(R.string.the_top_folder), now, clock24, zone, prefs.trashDays, busy,
                                    onRestore = restore@{
                                        // One restore at a time: a second tap would find the item already gone.
                                        if (busy) return@restore
                                        busy = true
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { editor.restoreTrash(root, item) }
                                            busy = false
                                            refresh++
                                            editor.say(if (ok) context.getString(R.string.restored_item, label(item)) else context.getString(R.string.err_restore_item, label(item)))
                                        }
                                    },
                                    onDelete = { confirmDelete = item },
                                )
                            }
                            item {
                                Text(
                                    stringResource(R.string.restore_hint, rootName ?: stringResource(R.string.the_top_folder)),
                                    style = InkType.meta.copy(lineHeight = 19.sp), color = ink.text2,
                                    modifier = Modifier.padding(start = 2.dp, top = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty && root != null) {
        InkConfirmSheet(
            title = stringResource(R.string.empty_trash_confirm),
            message = stringResource(R.string.empty_trash_body),
            confirmLabel = stringResource(R.string.empty_trash),
            danger = true,
            confirmIcon = Ph.trash,
            onConfirm = {
                confirmEmpty = false
                busy = true
                scope.launch {
                    withContext(Dispatchers.IO) { editor.emptyTrash(root) }
                    busy = false
                    refresh++
                }
            },
            onDismiss = { confirmEmpty = false },
        )
    }
    confirmDelete?.let { item ->
        if (root == null) return@let
        InkConfirmSheet(
            title = stringResource(R.string.delete_permanently_confirm),
            message = stringResource(R.string.delete_permanently_body, label(item)),
            confirmLabel = stringResource(R.string.delete_permanently),
            danger = true,
            confirmIcon = Ph.trash,
            onConfirm = {
                confirmDelete = null
                busy = true
                scope.launch {
                    withContext(Dispatchers.IO) { editor.deleteTrash(root, item) }
                    busy = false
                    refresh++
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

private fun label(item: TrashItem): String = if (item.entry.isDir) item.entry.name else DocumentKind.stripSuffix(item.entry.name)

/** A quiet one-line state (no folder, loading, no match). */
@Composable
private fun QuietLine(text: String) {
    Text(text, style = InkType.body, color = LocalInk.current.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp))
}

/** The rule (.ps-ban): how long deleted items wait, with the days bold, and Change. */
@Composable
private fun RetentionBanner(days: Int, onChange: () -> Unit) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.medium
    val text = when (days) {
        0 -> stringResource(R.string.trash_off_hint)
        Preferences.TRASH_FOREVER -> stringResource(R.string.trash_forever_hint)
        else -> pluralStringResource(R.plurals.trash_deleted_after, days, days)
    }
    val bold = if (days > 0) pluralStringResource(R.plurals.days_count, days, days) else null
    Row(
        Modifier
            .padding(top = 20.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ink.surface)
            .border(1.dp, ink.line2, shape)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ph.clockCountdown, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Text(
            buildAnnotatedString {
                val at = if (bold != null) text.indexOf(bold) else -1
                if (at < 0) append(text) else {
                    append(text.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                    append(text.substring(at + bold!!.length))
                }
            },
            style = InkType.body, color = ink.text, modifier = Modifier.weight(1f),
        )
        InkGhostButton(stringResource(R.string.change), onChange, Modifier.height(36.dp))
    }
}

@Composable
private fun TableHead(wide: Boolean) {
    val ink = LocalInk.current
    val style = InkType.hint.copy(fontWeight = FontWeight.Bold)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawBehind { drawLine(ink.line2, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.sort_name), style = style, color = ink.text2, modifier = Modifier.weight(1f).padding(start = 58.dp))
        if (wide) {
            Text(stringResource(R.string.trash_was_in), style = style, color = ink.text2, modifier = Modifier.width(WAS_IN_W))
            // Sorted by when it went: the one heading in ink, with its arrow.
            Row(Modifier.width(DELETED_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.trash_deleted), style = style, color = ink.text)
                Icon(Ph.arrowDown, null, tint = ink.text, modifier = Modifier.size(13.dp))
            }
            Text(stringResource(R.string.trash_removed_in), style = style, color = ink.text2, modifier = Modifier.width(LEFT_W))
        }
        Spacer(Modifier.width(ACTIONS_W))
    }
}

private fun kindGlyph(k: EntryKind): ImageVector = when (k) {
    EntryKind.FOLDER -> Ph.folderSimple
    EntryKind.NOTE -> Ph.notebook
    EntryKind.PDF -> Ph.filePdf
    EntryKind.CANVAS -> Ph.infinity
}

/** One deleted item (.ps-trow): thumbnail, name and kind; where it was; when it went; days left (red when three or fewer); Restore and ⋮. */
@Composable
private fun TrashRow(
    editor: Editor,
    item: TrashItem,
    wide: Boolean,
    rootName: String,
    now: Long,
    clock24: Boolean,
    zone: ZoneId,
    days: Int,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val words = rememberExplorerWords()
    val e = item.entry
    val kind = entryKind(e, editor.cachedMeta(e))
    val wasIn = item.path.lastOrNull() ?: rootName
    val deleted = formatWhen(words, item.deleted, now, "day", true, clock24, zone)
    val left = TrashMath.daysLeft(days, item.deleted, now)
    val cell = InkType.body.copy(fontSize = 13.5.sp)
    val thumbShape = inkRounded(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .drawBehind { drawLine(ink.line2, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (e.isDir) {
                Box(Modifier.size(44.dp).background(ink.iconBadge, thumbShape), contentAlignment = Alignment.Center) {
                    Icon(Ph.folderSimple, null, tint = e.color?.let { codeTint(it, palette) } ?: ink.text, modifier = Modifier.size(22.dp))
                }
            } else {
                EntryThumb(editor, e, ThumbShape.TOP, Modifier.size(44.dp).clip(thumbShape).border(1.dp, Color.Black.copy(alpha = 0.09f), thumbShape))
            }
            Column(Modifier.weight(1f)) {
                Text(label(item), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(kindGlyph(kind), null, tint = ink.text2, modifier = Modifier.size(14.dp))
                    val sub = if (wide) AnnotatedString(kindLabel(kind)) else {
                        // Folded, the countdown joins the line, red when three days or fewer are left.
                        val base = stringResource(R.string.trash_row_sub, kindLabel(kind), wasIn, deleted)
                        val countdown = when (left) {
                            null -> null
                            0 -> stringResource(R.string.trash_row_left_today)
                            else -> pluralStringResource(R.plurals.trash_row_left, left, left)
                        }
                        buildAnnotatedString {
                            append(base)
                            if (countdown != null) {
                                append(" · ")
                                if (TrashMath.soon(left)) withStyle(SpanStyle(color = ink.danger, fontWeight = FontWeight.SemiBold)) { append(countdown) } else append(countdown)
                            }
                        }
                    }
                    Text(sub, style = InkType.caption, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (wide) {
            Row(Modifier.width(WAS_IN_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Ph.folderSimple, null, tint = ink.text2, modifier = Modifier.size(16.dp))
                Text(wasIn, style = cell, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(deleted, style = cell, color = ink.text2, maxLines = 1, modifier = Modifier.width(DELETED_W))
            Text(
                when (left) { null -> stringResource(R.string.never); 0 -> stringResource(R.string.today); else -> pluralStringResource(R.plurals.days_count, left, left) },
                style = cell.copy(fontWeight = FontWeight.SemiBold),
                color = if (TrashMath.soon(left)) ink.danger else ink.text,
                maxLines = 1,
                modifier = Modifier.width(LEFT_W),
            )
        }
        Row(Modifier.width(ACTIONS_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)) {
            InkSecondaryButton(stringResource(R.string.restore), onRestore, Modifier.height(38.dp), icon = Ph.arrowCounterClockwise, enabled = !busy)
            var menu by remember { mutableStateOf(false) }
            Box {
                InkIconButton(Ph.dotsThreeVertical, stringResource(R.string.more_for, label(item)), { menu = true }, size = 40.dp, iconSize = 20.dp, tint = ink.text2, on = menu)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.width(252.dp)) {
                    InkMenuRow(stringResource(R.string.restore), { menu = false; onRestore() }, icon = Ph.arrowCounterClockwise)
                    InkMenuRow(stringResource(R.string.delete_permanently), { menu = false; onDelete() }, icon = Ph.trash, danger = true)
                }
            }
        }
    }
}
