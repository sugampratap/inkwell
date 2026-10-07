package com.xnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.IconBadge
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkGroup
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Move to folder (r2_page_notebook Frame 5): picks a folder under [root] to move things into, starting
 * at [start] (a path of (document id, name) below the top folder). Breadcrumbs go back up; folders are
 * grouped rows with their colour dot; [blocked] folders, the ones being moved, are dimmed, labelled
 * "Being moved", and can't be opened or picked. The footer says where things will land. [subtitle]
 * names what is being moved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderPickerDialog(
    editor: Editor,
    root: String,
    rootName: String,
    title: String,
    confirmLabel: String,
    start: List<Pair<String, String>>,
    blocked: (docId: String) -> Boolean,
    onPick: (docId: String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val ink = LocalInk.current
    val rootId = remember(root) { editor.browseRootDocId(root) }
    val path = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(start) } }
    val here = path.lastOrNull()?.first ?: rootId
    val hereName = path.lastOrNull()?.second ?: rootName
    // Keyed on the folder, so the one being left never shows (or takes taps) while the next loads.
    val folders by key(root, here) {
        produceState(editor.cachedChildren(root, here)?.filter { it.isDir }) {
            value = withContext(Dispatchers.IO) { editor.browseChildren(root, here).filter { it.isDir } }
        }
    }
    InkSheet(
        title = title,
        onDismiss = onDismiss,
        subtitle = subtitle,
        width = 580.dp,
        height = 500.dp,
        showClose = false,
        footer = {
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.move_moves_into))
                    withStyle(SpanStyle(color = ink.text, fontWeight = FontWeight.Bold)) { append(hereName) }
                },
                style = InkType.meta, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            InkGhostButton(stringResource(R.string.cancel), onDismiss)
            InkStrongButton(confirmLabel, { onPick(here) }, enabled = !blocked(here))
        },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
            FlowRow(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                CrumbButton(rootName, current = path.isEmpty()) { path.clear() }
                path.forEachIndexed { i, (_, name) ->
                    Icon(Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(14.dp))
                    CrumbButton(name, current = i == path.lastIndex) { while (path.size > i + 1) path.removeAt(path.lastIndex) }
                }
            }
            val list = folders
            when {
                list == null -> Text(stringResource(R.string.loading), style = InkType.body, color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp))
                list.isEmpty() -> InkGroup {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconBadge(Ph.folderSimple, Modifier.padding(bottom = 4.dp))
                        Text(stringResource(R.string.no_folders_here), style = InkType.rowStrong.copy(fontWeight = FontWeight.Bold), color = ink.text)
                        Text(stringResource(R.string.move_empty_body, hereName), style = InkType.meta.copy(lineHeight = 19.sp), color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
                    }
                }
                else -> InkGroup {
                    list.forEachIndexed { i, f ->
                        val id = editor.browseDocId(f.documentUri)
                        FolderRow(f.name, f.color?.let { codeTint(it, LocalPalette.current) }, off = blocked(id), first = i == 0) { path.add(id to f.name) }
                    }
                }
            }
        }
    }
}

/** A breadcrumb (.pn-crumbs button): the current folder bold and still; the others tap back up. */
@Composable
private fun CrumbButton(text: String, current: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Text(
        text,
        style = InkType.body.copy(fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold),
        color = if (current) ink.text else ink.text2,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(inkRounded(10.dp))
            .then(if (current) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A folder (.pn-flist .gr): ink folder icon, name, its colour dot, chevron; dimmed and "Being moved" when [off]. */
@Composable
private fun FolderRow(name: String, dot: androidx.compose.ui.graphics.Color?, off: Boolean, first: Boolean, onOpen: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (off) 0.42f else 1f)
            .drawBehind { if (!first) drawRect(ink.line2, Offset(18.dp.toPx(), 0f), Size(size.width - 18.dp.toPx(), 1.dp.toPx())) }
            .then(if (off) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen))
            .padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Ph.folderSimple, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (off) Text(stringResource(R.string.move_being_moved), style = InkType.meta, color = ink.text2)
        }
        if (dot != null) Box(Modifier.padding(end = 2.dp).size(10.dp).background(dot, CircleShape))
        Icon(Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(16.dp))
    }
}
