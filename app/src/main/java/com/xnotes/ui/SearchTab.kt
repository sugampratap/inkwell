package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.search.SearchHit
import com.xnotes.core.search.SearchResults
import com.xnotes.core.search.SearchSnippet
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkPanelHint
import com.xnotes.ui.kit.InkSearchField
import com.xnotes.ui.kit.InkToggleChip
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum

/** What the count line says (see [searchPosition]). */
internal sealed interface SearchPosition {
    data object None : SearchPosition
    data object Searching : SearchPosition
    /** [current] counts from 1, null before a match is picked ("–"); [capped] adds "+" once a scan hit its cap. */
    data class At(val current: Int?, val total: Int, val capped: Boolean) : SearchPosition
}

/** The count line for a [query] with [count] matches so far (null: no results yet), [done] when the scan finished, [currentIndex] the shown match. */
internal fun searchPosition(query: String, count: Int?, done: Boolean, capped: Boolean, currentIndex: Int?): SearchPosition = when {
    count == null || query.isBlank() -> SearchPosition.None
    count == 0 -> if (done) SearchPosition.None else SearchPosition.Searching
    else -> SearchPosition.At(currentIndex?.takeIf { it >= 0 }?.plus(1), count, capped)
}

/**
 * The side panel's Search tab (r2_panel_share_empty Frame 2): the query, Match case and Whole words,
 * where the current match stands with previous and next, the scan line, then every match grouped by
 * page with the match in bold. The search session lives exactly as long as the tab is on screen.
 */
@Composable
internal fun SearchTab(editor: Editor) {
    DisposableEffect(editor) {
        editor.openSearchSession()
        onDispose { editor.closeSearchSession() }
    }
    val focusManager = LocalFocusManager.current
    val results = editor.searchResults
    val position = searchPosition(
        editor.searchQuery, results?.count, results?.done ?: true, results?.capped ?: false,
        editor.searchCurrent?.let { results?.indexOf(it) },
    )
    Column(Modifier.fillMaxSize()) {
        QueryField(editor)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkToggleChip(stringResource(R.string.match_case), editor.searchMatchCase, { editor.toggleSearchMatchCase() })
            InkToggleChip(stringResource(R.string.whole_words), editor.searchWholeWords, { editor.toggleSearchWholeWords() })
        }
        PositionRow(editor, position)
        ScanLine(results)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                editor.searchQuery.isBlank() -> InkPanelHint(Ph.magnifyingGlass, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
                results == null -> Unit
                results.count == 0 -> if (results.done) InkPanelHint(Ph.magnifyingGlass, stringResource(R.string.no_matches), stringResource(R.string.search_none_body))
                else -> MatchList(editor, results) { focusManager.clearFocus() }
            }
        }
    }
}

@Composable
private fun QueryField(editor: Editor) {
    val focus = remember { FocusRequester() }
    // The last query comes back selected, so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(editor.searchQuery, TextRange(0, editor.searchQuery.length))) }
    LaunchedEffect(editor.searchFocusTick) {
        runCatching { focus.requestFocus() }
        field = field.copy(selection = TextRange(0, field.text.length))
    }
    InkSearchField(
        value = field,
        onValueChange = {
            field = it
            editor.searchFor(it.text)
        },
        placeholder = stringResource(R.string.find_in_note),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        focusRequester = focus,
        onSearch = { editor.searchStep(forward = true) },
        // A keyboard's Enter steps on, Shift+Enter back.
        onKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                editor.searchStep(forward = !e.isShiftPressed)
                true
            } else false
        },
    )
}

/** The count line (.ps-spos): "6 / 23 matches" and the two arrows, big enough for a finger. */
@Composable
private fun PositionRow(editor: Editor, position: SearchPosition) {
    val ink = LocalInk.current
    val any = position is SearchPosition.At
    Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val text = when (position) {
            SearchPosition.None -> ""
            SearchPosition.Searching -> stringResource(R.string.searching)
            is SearchPosition.At -> stringResource(
                R.string.search_position,
                position.current?.toString() ?: "–",
                if (position.capped) "${position.total}+" else "${position.total}",
            )
        }
        Text(text, style = InkType.rowStrong.copy(fontWeight = FontWeight.ExtraBold).tnum(), color = ink.text, maxLines = 1)
        if (any) Text(stringResource(R.string.search_matches_label), style = InkType.meta, color = ink.text2)
        Spacer(Modifier.weight(1f))
        InkIconButton(Ph.caretUp, stringResource(R.string.previous_match), { editor.searchStep(forward = false) }, enabled = any, size = 36.dp, iconSize = 18.dp)
        InkIconButton(Ph.caretDown, stringResource(R.string.next_match), { editor.searchStep(forward = true) }, enabled = any, size = 36.dp, iconSize = 18.dp)
    }
}

/** How far a scan has read the PDF (.ps-prog): a 2dp ink line on --line2; the room stays when there is nothing to show, so the list never jumps. */
@Composable
private fun ScanLine(results: SearchResults?) {
    val ink = LocalInk.current
    val reading = results != null && !results.done && results.toRead > 0
    val fraction = if (reading) results!!.read.toFloat() / results.toRead else 0f
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp)) {
        if (!reading) return@Canvas
        val r = CornerRadius(size.height / 2)
        drawRoundRect(ink.line2, cornerRadius = r)
        drawRoundRect(ink.text, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

@Composable
private fun MatchList(editor: Editor, results: SearchResults, onPick: () -> Unit) {
    val ink = LocalInk.current
    val current = editor.searchCurrent
    // Page headings and matches in one list: an Int is a page's heading, a SearchHit one of its matches.
    val rows = remember(results) {
        ArrayList<Any>(results.count + results.pagesWithHits.size).apply {
            for (p in results.pagesWithHits) {
                add(p)
                addAll(results.hitsOn(p))
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        val i = rows.indexOfFirst { it === current }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.scrollToItem((i - 1).coerceAtLeast(0))
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 28.dp)) {
        items(rows.size, key = { k -> rowKey(rows[k]) }) { k ->
            when (val row = rows[k]) {
                is SearchHit -> MatchRow(row, row === current) {
                    onPick()
                    editor.showSearchHit(row)
                }
                else -> {
                    val page = row as Int
                    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp)) {
                        val style = InkType.hint.copy(lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.page_n, page + 1), style = style, color = ink.text2, modifier = Modifier.weight(1f))
                        Text("${results.hitsOn(page).size}", style = style.tnum(), color = ink.text2)
                    }
                }
            }
        }
    }
}

private fun rowKey(row: Any): Any = if (row is SearchHit) "h${row.page}:${row.target}:${row.start}" else "p$row"

/** One match (.ps-hit): two lines of context with the match extra-bold; the current one on the selected fill. */
@Composable
private fun MatchRow(hit: SearchHit, current: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    val text = remember(hit) {
        val s = SearchSnippet.of(hit)
        buildAnnotatedString {
            append(s.text)
            addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), s.matchStart, s.matchEnd)
        }
    }
    Text(
        text,
        style = InkType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        color = ink.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (current) ink.sel else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
