package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import kotlin.math.roundToInt

/** The library header's height (.mh), and on phones. */
internal val LIBRARY_HEADER = 84.dp
internal val LIBRARY_HEADER_COMPACT = 64.dp

/** Below this much header width the pill folds into the round search button (P2-2). */
private val PILL_HEADER_MIN = 640.dp

private val SegLabel = InkType.small.copy(fontWeight = FontWeight.Bold)
private val SegValue = InkType.body.copy(lineHeight = 19.sp)

/**
 * The header's row: [lead] (sidebar and back buttons, breadcrumbs and title), the search pill centred over the
 * header when it fits (else beside the title), and [new]. Narrow headers swap the pill for the round search button,
 * which opens the pill across the row. While a search is typed (or the pill holds the focus) the pill keeps the
 * place the title before the search gave it, and the title ("Results for …") gives way, ellipsised, rather than push it.
 */
@Composable
internal fun LibraryHeaderLayout(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier,
    lead: @Composable () -> Unit,
    new: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth < PILL_HEADER_MIN) {
            var open by remember { mutableStateOf(false) }
            BackHandler(enabled = open) { open = false }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (open) {
                    SearchPill(query, onQuery, Modifier.weight(1f), autoFocus = true)
                    Spacer(Modifier.width(8.dp))
                    InkIconButton(Ph.x, stringResource(R.string.kit_close), { open = false })
                } else {
                    Box(Modifier.weight(1f)) { lead() }
                    Spacer(Modifier.width(8.dp))
                    InkIconButton(Ph.magnifyingGlass, stringResource(R.string.search_notes), { open = true })
                    Spacer(Modifier.width(8.dp))
                    new()
                }
            }
        } else {
            var pillFocused by remember { mutableStateOf(false) }
            // The title's width before the search began, kept outside composition; -1 until measured.
            val restingLead = remember { IntArray(1) { -1 } }
            val holding = query.isNotEmpty() || pillFocused
            Layout(
                contents = listOf<@Composable () -> Unit>(lead, { SearchPill(query, onQuery, Modifier, onFocus = { pillFocused = it }) }, new),
                modifier = Modifier.fillMaxSize(),
            ) { (leadM, pillM, newM), c ->
                val w = c.maxWidth
                val h = c.maxHeight
                val gap = 16.dp.roundToPx()
                val minPill = 240.dp.roundToPx()
                val maxPill = 600.dp.toPx()
                val newP = newM.first().measure(Constraints(maxWidth = w, maxHeight = h))
                val newStart = (w - newP.width).toFloat()
                // The title gives way before the pill drops under its 240dp minimum.
                val leadRoom = (w - newP.width - 2 * gap - minPill).coerceAtLeast(0)
                val held = restingLead[0].takeIf { holding && it >= 0 }
                val leadP: Placeable
                val slot: SearchPillSlot?
                if (held != null) {
                    // Searching: the pill stays where the resting title put it, and the title fits in front of it.
                    slot = pillSlot(w.toFloat(), held.toFloat(), newStart, maxPill, minPill.toFloat(), gap.toFloat())
                    val room = slot?.let { (it.x.roundToInt() - gap).coerceIn(0, leadRoom) } ?: leadRoom
                    leadP = leadM.first().measure(Constraints(maxWidth = room, maxHeight = h))
                } else {
                    leadP = leadM.first().measure(Constraints(maxWidth = leadRoom, maxHeight = h))
                    restingLead[0] = leadP.width
                    slot = pillSlot(w.toFloat(), leadP.width.toFloat(), newStart, maxPill, minPill.toFloat(), gap.toFloat())
                }
                val pillP = slot?.let { pillM.first().measure(Constraints.fixed(it.width.roundToInt(), 60.dp.roundToPx().coerceAtMost(h))) }
                layout(w, h) {
                    leadP.placeRelative(0, (h - leadP.height) / 2)
                    if (slot != null && pillP != null) pillP.placeRelative(slot.x.roundToInt(), (h - pillP.height) / 2)
                    newP.placeRelative(w - newP.width, (h - newP.height) / 2)
                }
            }
        }
    }
}

/**
 * The search pill (.spill): one segment, "Search notes" over the hint or what is typed (D4: the "In" and "Tagged"
 * segments are not built), a clear button once something is typed, and a plain magnifier at its start (the user's
 * choice over the marigold button, 2026-10-07). Searching
 * runs as you type (debounced by the explorer). The button focuses the field, or, while typing, puts the keyboard
 * away. A keyboard that goes away ends the typing but keeps the search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchPill(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier, autoFocus: Boolean = false, onFocus: (Boolean) -> Unit = {}) {
    val ink = LocalInk.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    val imeUp = WindowInsets.isImeVisible
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeUp, focused) {
        when {
            !focused -> imeSeen = false
            imeUp -> imeSeen = true
            imeSeen -> focusManager.clearFocus()
        }
    }
    val label = stringResource(R.string.search_notes)
    Row(
        modifier
            .height(60.dp)
            .shadow(InkElevation.SOFT.shadow, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .background(ink.raised, CircleShape)
            .border(1.dp, ink.line, CircleShape)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIconButton(
            Ph.magnifyingGlass, label,
            { if (focused) focusManager.clearFocus() else runCatching { focus.requestFocus() } },
            size = 44.dp, iconSize = 22.dp, tint = ink.text2,
        )
        Column(
            Modifier
                .weight(1f)
                .height(58.dp)
                .clip(CircleShape)
                .clickable(enabled = !focused, role = Role.Button) { runCatching { focus.requestFocus() } }
                .padding(start = 4.dp, end = 22.dp), // after the magnifier; 22 out (.seg.s1)
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = SegLabel, color = ink.text, maxLines = 1)
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.library_search_hint), style = SegValue, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = SegValue.copy(color = ink.text),
                    cursorBrush = SolidColor(ink.solid),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }.semantics { contentDescription = label },
                )
            }
        }
        if (query.isNotEmpty()) {
            InkIconButton(Ph.x, stringResource(R.string.clear_search), { onQuery(""); focusManager.clearFocus() }, size = 36.dp, iconSize = 18.dp, tint = ink.text2)
        }
    }
}

/** Where the library stands, large (.mh h1); [compact] for phones. */
@Composable
internal fun LibraryTitle(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Text(
        text,
        style = if (compact) InkType.sheetTitle else InkType.display,
        color = LocalInk.current.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

/** The trail above a folder's title: All notes, then each folder above this one; [onPick] gets the step's index. */
@Composable
internal fun LibraryCrumbs(names: List<String>, onPick: (Int) -> Unit) {
    val ink = LocalInk.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, n ->
            if (i > 0) Icon(if (rtl) Ph.caretLeft else Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(12.dp))
            Text(
                n, style = InkType.meta, color = ink.text2, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onPick(i) }.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
