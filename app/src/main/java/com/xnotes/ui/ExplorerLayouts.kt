package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.TileSize
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** A selected row's label: the bold weight is the non-colour cue beside the ink.sel fill. */
private val RowBold = InkType.row.copy(fontWeight = FontWeight.Bold)

/** Columns the grid shows at [size], from the screen's width so folding the sidebar never reflows it. */
internal fun gridColumns(screenWidthDp: Int, size: TileSize): Int {
    val tile = when (size) { TileSize.S -> 180f; TileSize.M -> 240f; TileSize.L -> 320f; TileSize.XL -> 420f }
    return Math.round(screenWidthDp / tile).coerceIn(1, 10)
}

internal fun timelineCard(size: TileSize): Dp = when (size) { TileSize.S -> 110.dp; TileSize.M -> 140.dp; TileSize.L -> 180.dp; TileSize.XL -> 230.dp }

/** Grid: the chip row, folders in a row of chips (or mixed in as cards), then file cards under their group headings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBody(
    b: ExplorerBody, state: LazyGridState, columns: Int, chips: @Composable (Dp) -> Unit, top: (LazyGridScope.() -> Unit)?,
    modifier: Modifier = Modifier, stickyTabs: (@Composable () -> Unit)? = null, topPadding: Dp = EXPLORER_HEADER, hPad: Dp = 0.dp,
) {
    val gap = if (hPad < 32.dp) 16.dp else 24.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        // The mockup's 56px foot (.grid), so the last row clears the screen's edge.
        contentPadding = PaddingValues(start = hPad, end = hPad, top = topPadding, bottom = 56.dp),
    ) {
        if (stickyTabs == null) item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") { chips(48.dp) }
        top?.invoke(this)
        if (stickyTabs != null) {
            item(key = "tabsGap", span = { GridItemSpan(maxLineSpan) }, contentType = "gap") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "tabs", contentType = "tabs") { stickyTabs() }
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") { chips(48.dp) }
        }
        item(key = "gridGap", span = { GridItemSpan(maxLineSpan) }, contentType = "gap") { Spacer(Modifier.height(20.dp)) }
        if (b.folders.isNotEmpty()) {
            items(b.folders, key = { it.documentUri }, contentType = { "folder" }) { Box(Modifier.padding(bottom = gap)) { FolderChipTile(b, it) } }
        }
        b.groups.forEach { g ->
            if (g.label.isNotEmpty()) {
                item(key = "group:${g.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    GroupHeader(g.label, g.items.size, g.color, Modifier.padding(bottom = 8.dp))
                }
            }
            items(g.items, key = { it.documentUri }, contentType = { if (it.isDir) "folderCard" else "file" }) { e ->
                Box(Modifier.padding(bottom = gap)) { if (e.isDir) FolderCardTile(b, e) else GridFileTile(b, e) }
            }
        }
    }
}

/** List: the chip row and a header of sortable column names, both scrolling away with one row per item. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListBody(
    b: ExplorerBody,
    state: LazyListState,
    wide: Boolean,
    onSort: ((ExplorerSortKey) -> Unit)?,
    chips: @Composable (Dp) -> Unit,
    top: (LazyListScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    stickyTabs: (@Composable () -> Unit)? = null,
    topPadding: Dp = EXPLORER_HEADER,
    hPad: Dp = 0.dp,
) {
    val inset = 8.dp + (if (b.view.compactRows) 28.dp else 40.dp) + 14.dp + (if (b.host.selecting()) LIST_CHECK_INSET else 0.dp)
    LazyColumn(state = state, modifier = modifier, contentPadding = PaddingValues(start = hPad, end = hPad, top = topPadding, bottom = 32.dp)) {
        if (stickyTabs != null) {
            top?.invoke(this)
            item(key = "tabsGap", contentType = "gap") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "tabs", contentType = "tabs") { stickyTabs() }
        }
        item(key = "chips", contentType = "chips") { chips(48.dp) }
        item(key = "columnHeads", contentType = "columnHeads") {
            Row(Modifier.fillMaxWidth().height(36.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ColumnHead(stringResource(R.string.sort_name), ExplorerSortKey.NAME, b, onSort, Modifier.weight(1f).padding(start = inset))
                if (wide) {
                    ColumnHead(stringResource(R.string.group_kind), null, b, null, Modifier.width(LIST_KIND_W))
                    ColumnHead(stringResource(R.string.pages), null, b, null, Modifier.width(LIST_PAGES_W), TextAlign.End)
                    ColumnHead(stringResource(R.string.sort_size), ExplorerSortKey.SIZE, b, onSort, Modifier.width(LIST_SIZE_W), TextAlign.End)
                    val byCreated = b.view.sortKey == ExplorerSortKey.CREATED
                    ColumnHead(if (byCreated) stringResource(R.string.created) else stringResource(R.string.modified), if (byCreated) ExplorerSortKey.CREATED else ExplorerSortKey.MODIFIED, b, onSort, Modifier.width(LIST_WHEN_W).padding(start = 28.dp))
                }
                Spacer(Modifier.width(40.dp))
            }
            Hairline()
        }
        if (stickyTabs == null) top?.invoke(this)
        items(b.folders, key = { it.documentUri }, contentType = { "row" }) { e ->
            ListRow(b, e, wide)
            Hairline()
        }
        b.groups.forEach { g ->
            if (g.label.isNotEmpty()) {
                item(key = "group:${g.key}", contentType = "header") {
                    GroupHeader(g.label, g.items.size, g.color, Modifier.padding(start = 8.dp, top = 10.dp))
                }
            }
            items(g.items, key = { it.documentUri }, contentType = { "row" }) { e ->
                ListRow(b, e, wide)
                Hairline()
            }
        }
    }
}

@Composable
private fun ColumnHead(
    label: String,
    key: ExplorerSortKey?,
    b: ExplorerBody,
    onSort: ((ExplorerSortKey) -> Unit)?,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
) {
    val ink = LocalInk.current
    val active = key != null && b.view.sortKey == key
    val tint = if (active) ink.text else ink.text2
    Row(
        modifier.then(if (key != null && onSort != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onSort(key) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start,
    ) {
        Text(label, style = InkType.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) {
            Spacer(Modifier.width(4.dp))
            Icon(if (b.view.descending) Ph.arrowDown else Ph.arrowUp, null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Columns: one column per folder along the path from the top folder, then a preview of the picked file.
 * [levels] is that path, (document id, name) from the top down; [arrange] sorts and filters a folder's listing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnsBody(
    b: ExplorerBody,
    root: String,
    levels: List<Pair<String, String>>,
    refreshKey: Int,
    arrange: (List<BrowseEntry>) -> List<BrowseEntry>,
    picked: BrowseEntry?,
    onOpenFolder: (level: Int, BrowseEntry) -> Unit,
    onPickFile: (level: Int, BrowseEntry) -> Unit,
    preview: @Composable (BrowseEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val scroll = rememberScrollState()
    // Keep the deepest column in view as the path grows; the new width lands a frame after the change.
    LaunchedEffect(levels.size, picked?.documentUri) { snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) } }
    Column(modifier) {
        Hairline()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columnW = 272.dp
            val previewMin = 360.dp
            val columnsW = (maxWidth - previewMin).coerceAtLeast(columnW)
            Row(Modifier.fillMaxSize()) {
                Row(Modifier.width(minOf(columnsW, columnW * levels.size)).fillMaxHeight().horizontalScroll(scroll)) {
                    levels.forEachIndexed { i, (docId, name) -> key(docId) {
                        val entries by produceState(b.editor.cachedChildren(root, docId), root, docId, refreshKey) {
                            value = withContext(Dispatchers.IO) { b.editor.browseChildren(root, docId) }
                        }
                        val shown = remember(entries, arrange) { entries?.let(arrange) }
                        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        val next = levels.getOrNull(i + 1)?.first
                        Column(
                            Modifier.width(columnW).fillMaxHeight().padding(horizontal = 6.dp),
                        ) {
                            Text(
                                if (shown == null) name else dotJoined(listOf(name, shown.size.toString())),
                                style = InkType.label, color = ink.text2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(36.dp).padding(start = 12.dp, top = 11.dp),
                            )
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                                items(shown.orEmpty(), key = { it.documentUri }) { e ->
                                    val onPath = e.isDir && next != null && b.editor.browseDocId(e.documentUri) == next
                                    val on = !e.isDir && picked?.documentUri == e.documentUri
                                    val sel = on || b.host.isSelected(e)
                                    val code = b.colorOf(e)?.let { codeTint(it, palette) }
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    sel -> ink.sel
                                                    onPath -> ink.hover
                                                    else -> Color.Transparent
                                                },
                                            )
                                            .combinedClickable(
                                                onClick = { if (e.isDir) onOpenFolder(i, e) else onPickFile(i, e) },
                                                onLongClick = { b.host.onLongClick(e) },
                                            )
                                            .padding(start = 12.dp, end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            kindIcon(b.kind(e)), null,
                                            tint = if (sel) ink.text else (code ?: ink.text2),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(b.label(e), style = if (sel) RowBold else InkType.row, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (e.isDir) Icon(if (rtl) Ph.caretLeft else Ph.caretRight, null, tint = ink.text2, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(ink.line2))
                    } }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (picked != null) preview(picked) else EmptyNote(stringResource(R.string.pick_note_hint), Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** When [e] sits on the Timeline: the day it was created, or last saved when the view asks or no creation time is known. */
internal fun ExplorerView.timelineTime(e: BrowseEntry): Long = if (timelineByCreated && e.created > 0) e.created else e.modified

/**
 * Timeline: every file under the folder, a row per day it was created (or last saved) on, for one month at a
 * time. A heat strip across the top counts the files each day; tapping a day scrolls to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineBody(
    b: ExplorerBody,
    items: List<BrowseEntry>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    listState: LazyListState,
    chips: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    stickyTabs: (@Composable () -> Unit)? = null,
    topPadding: Dp = EXPLORER_HEADER,
    hPad: Dp = 0.dp,
) {
    val ink = LocalInk.current
    val zone = b.zone
    val today = remember(b.now) { Instant.ofEpochMilli(b.now).atZone(zone).toLocalDate() }
    val view = b.view
    val byDay = remember(items, month, view.timelineByCreated) {
        items.filter { view.timelineTime(it) > 0 }
            .groupBy { Instant.ofEpochMilli(view.timelineTime(it)).atZone(zone).toLocalDate() }
            .filterKeys { YearMonth.from(it) == month }
            .toSortedMap(compareByDescending { it })
    }
    val scope = rememberCoroutineScope()
    val card = timelineCard(b.view.tileSize)
    // The tabs (when sticky), the chip row and the month strip come before the days; a tap on a day skips them.
    val lead = if (stickyTabs != null) 4 else 2
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(start = hPad, end = hPad, top = topPadding, bottom = 32.dp)) {
        if (stickyTabs != null) {
            item(key = "tabsGap", contentType = "gap") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "tabs", contentType = "tabs") { stickyTabs() }
        }
        item(key = "chips", contentType = "chips") { chips(48.dp) }
        item(key = "month", contentType = "month") {
            Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(164.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExplorerIcon(Ph.caretLeft, stringResource(R.string.previous_month), ink.text2) { onMonth(month.minusMonths(1)) }
                    Text(
                        if (month.year == today.year) b.words.month(month.month) else b.words.monthYear(month.month, month.year),
                        style = InkType.rowStrong, color = ink.text,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    ExplorerIcon(Ph.caretRight, stringResource(R.string.next_month), ink.text2, enabled = month < YearMonth.from(today)) { onMonth(month.plusMonths(1)) }
                }
                Spacer(Modifier.width(16.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val counts = remember(items, month, view.timelineByCreated) {
                        items.filter { view.timelineTime(it) > 0 }.groupingBy { Instant.ofEpochMilli(view.timelineTime(it)).atZone(zone).toLocalDate() }.eachCount()
                    }
                    for (d in 1..month.lengthOfMonth()) {
                        val date = month.atDay(d)
                        val ahead = date.isAfter(today)
                        val n = counts[date] ?: 0
                        val shape = RoundedCornerShape(3.dp)
                        val fill = when {
                            ahead -> Color.Transparent
                            n == 0 -> ink.surface
                            n <= 2 -> ink.solid.copy(alpha = 0.28f)
                            n == 3 -> ink.solid.copy(alpha = 0.55f)
                            else -> ink.solid
                        }
                        Box(
                            Modifier
                                .size(24.dp)
                                .then(if (date == today) Modifier.border(1.5.dp, ink.text, shape) else Modifier)
                                .clip(shape)
                                .background(fill)
                                .then(if (ahead) Modifier.border(1.dp, ink.line, shape) else Modifier)
                                .clickable(enabled = n > 0) {
                                    val index = byDay.keys.indexOf(date)
                                    if (index >= 0) scope.launch { listState.animateScrollToItem(lead + index) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$d", style = InkType.tiny.tnum(),
                                color = when {
                                    ahead -> ink.text2
                                    // The 3-item fill is the solid at 55%. Under dark chrome the solid is near-white,
                                    // so light body text washes out on it and onSolid reads.
                                    n >= 4 || (n == 3 && ink.isDark) -> ink.onSolid
                                    else -> ink.text
                                },
                            )
                        }
                    }
                }
            }
        }
        if (byDay.isEmpty()) {
            item(key = "none", contentType = "none") { EmptyNote(stringResource(if (view.timelineByCreated) R.string.nothing_created_in else R.string.nothing_saved_in, b.words.month(month.month))) }
        } else {
            byDay.forEach { (date, files) ->
                item(key = date.toString()) { TimelineDay(b, date, today, files, card) }
            }
        }
    }
}

@Composable
private fun TimelineDay(b: ExplorerBody, date: LocalDate, today: LocalDate, files: List<BrowseEntry>, card: Dp) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().height(card + 64.dp)) {
        Column(Modifier.width(84.dp)) {
            Text("${date.dayOfMonth}", style = InkType.display.tnum(), color = ink.text, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(b.words.shortWeekday(date.dayOfWeek), style = InkType.small, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rel = when (date) {
                today -> b.words.today
                today.minusDays(1) -> b.words.yesterday
                else -> null
            }
            if (rel != null) Text(rel, style = InkType.small, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            Box(Modifier.offset(x = 4.dp, y = 12.dp).width(1.dp).fillMaxHeight().background(ink.line2))
            Box(Modifier.offset(y = 8.dp).size(9.dp).clip(CircleShape).background(ink.solid))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
            items(files.sortedByDescending { b.view.timelineTime(it) }, key = { it.documentUri }) { TimelineCard(b, it, card) }
        }
    }
}
