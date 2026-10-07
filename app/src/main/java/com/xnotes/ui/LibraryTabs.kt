package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.settings.ExplorerLayout
import com.xnotes.settings.ExplorerView
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuHeader
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private class KindTab(val kind: EntryKind?, val icon: ImageVector, val iconOn: ImageVector, val label: Int)

private val KindTabs by lazy {
    listOf(
        KindTab(null, Ph.squaresFour, Ph.squaresFourFill, R.string.library_kind_all),
        KindTab(EntryKind.NOTE, Ph.notebook, Ph.notebookFill, R.string.library_kind_notebooks),
        KindTab(EntryKind.CANVAS, Ph.infinity, Ph.infinityFill, R.string.kind_canvases),
        KindTab(EntryKind.PDF, Ph.filePdf, Ph.filePdfFill, R.string.library_kind_pdfs),
    )
}

private val TabOff = InkType.small
private val TabOn = InkType.small.copy(fontWeight = FontWeight.Bold)
private val SortOff = InkType.body
private val SortOn = InkType.body.copy(fontWeight = FontWeight.Bold)

/**
 * Where each tab's label sits in the tab strip's own content, kept outside composition: neither a scroll of the
 * shelf (which re-places the bar every frame) nor a scroll of the strip on phones moves a label within it.
 */
private class TabSpots(n: Int) {
    var strip: LayoutCoordinates? = null
    val lefts = FloatArray(n)
    val widths = FloatArray(n)
}

/**
 * The category bar (.cats), stuck under the header as the shelf scrolls. The bar's fill and hairline reach [hPad]
 * past it, over the shelf's margins, so covers slide out of sight beneath it. The underline's place and width live in
 * Animatables read only while drawing, so a tab change animates without recomposing. Label spots are compared before
 * anything is invalidated, so a scroll recomposes nothing here. The bar takes every touch that lands on it, so a tap
 * on its empty stretch never reaches the cover scrolled beneath; its tabs and buttons still get theirs first, and a
 * drag on it still scrolls the shelf. [modifier] is where the explorer learns the bar's place, to keep its long press off it.
 */
@Composable
internal fun CategoryTabs(
    kind: EntryKind?,
    onKind: (EntryKind?) -> Unit,
    count: String,
    hPad: Dp,
    compact: Boolean,
    sortView: @Composable () -> Unit,
    select: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val selected = KindTabs.indexOfFirst { it.kind == kind }.coerceAtLeast(0)
    val spots = remember { TabSpots(KindTabs.size) }
    var moved by remember { mutableIntStateOf(0) }
    val ulX = remember { Animatable(0f) }
    val ulW = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, moved) {
        val w = spots.widths[selected]
        if (w <= 0f) return@LaunchedEffect
        if (!placed) {
            ulX.snapTo(spots.lefts[selected]); ulW.snapTo(w); placed = true
        } else coroutineScope {
            launch { ulX.animateTo(spots.lefts[selected], InkMotion.glide()) }
            launch { ulW.animateTo(w, InkMotion.glide()) }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(BlockPointer)
            .drawBehind {
                val pad = hPad.toPx()
                val line = 1.dp.toPx()
                drawRect(ink.bg, Offset(-pad, 0f), Size(size.width + 2 * pad, size.height))
                drawRect(ink.line2, Offset(-pad, size.height - line), Size(size.width + 2 * pad, line))
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .weight(1f)
                .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                // Inside the scroll, so the strip's content (and the underline drawn with it) moves as one.
                .onPlaced { spots.strip = it }
                .drawBehind {
                    val h = 2.dp.toPx()
                    if (ulW.value > 0f) drawRect(ink.text, Offset(ulX.value, size.height - h), Size(ulW.value, h))
                }
                .selectableGroup(),
            verticalAlignment = Alignment.Bottom,
        ) {
            KindTabs.forEachIndexed { i, t ->
                val on = i == selected
                val alpha by animateFloatAsState(if (on) 1f else 0.6f, tween(InkMotion.FAST, easing = InkMotion.Standard), label = "tab")
                Column(
                    Modifier
                        .padding(end = if (compact) 16.dp else 24.dp)
                        .widthIn(min = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(on, role = Role.Tab) { onKind(t.kind) }
                        .graphicsLayer { this.alpha = alpha }
                        .padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (on) t.iconOn else t.icon, null, tint = ink.text, modifier = Modifier.size(24.dp))
                    val label = stringResource(t.label)
                    Box(
                        Modifier.onPlaced { c ->
                            val strip = spots.strip ?: return@onPlaced
                            val x = strip.localPositionOf(c, Offset.Zero).x
                            val w = c.size.width.toFloat()
                            if (x != spots.lefts[i] || w != spots.widths[i]) { spots.lefts[i] = x; spots.widths[i] = w; moved++ }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        // The bold label, unseen, holds the width so a tab never shifts when it becomes the bold one (.cat span::after).
                        Text(label, style = TabOn, color = Color.Transparent, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                        Text(label, style = if (on) TabOn else TabOff, color = ink.text, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.padding(start = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) Text(count, style = InkType.meta.tnum(), color = ink.text2, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
            sortView()
            select()
        }
    }
}

/** Select (.btn-sec with check-square), "Done" with the Fill icon and the chosen ring while picking; an icon on phones. */
@Composable
internal fun SelectButton(selecting: Boolean, compact: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (selecting) R.string.done else R.string.library_select)
    val icon = if (selecting) Ph.checkSquareFill else Ph.checkSquare
    if (compact) InkIconButton(icon, label, onClick, on = selecting)
    else InkSecondaryButton(label, onClick, icon = icon, on = selecting)
}

/**
 * "Sort & view" and its menu (.svm). Sorting is left out where it means nothing (Recent keeps its order, Timeline
 * places by date). Picks apply at once and the menu stays open, as the mockup does; the rows that lead elsewhere close it.
 * [moreOptions] is the View options popover's content, opened from here. On phones the bar has no room for the count,
 * so [count] heads the menu instead.
 */
@Composable
internal fun SortViewButton(
    compact: Boolean,
    view: ExplorerView,
    layout: ExplorerLayout,
    showSort: Boolean,
    layouts: List<ExplorerLayout>,
    onView: (ExplorerView) -> Unit,
    onLayout: (ExplorerLayout) -> Unit,
    locationName: String,
    onChangeFolder: () -> Unit,
    onForgetFolder: () -> Unit,
    count: String? = null,
    moreOptions: @Composable (onClose: () -> Unit) -> Unit,
) {
    val ink = LocalInk.current
    var open by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val label = stringResource(R.string.library_sort_and_view)
    val menuW = minOf(376, LocalConfiguration.current.screenWidthDp - 16).dp
    Box {
        if (compact) InkIconButton(Ph.sortAscending, label, { open = true }, on = open)
        else InkSecondaryButton(label, { open = true }, icon = Ph.sortAscending, on = open)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(menuW)) {
            if (!count.isNullOrEmpty()) {
                Text(count, style = InkType.meta.tnum(), color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 4.dp))
            }
            if (showSort) {
                InkMenuHeader(stringResource(R.string.sort_by))
                SORT_MENU_KEYS.forEach { k -> SortRow(sortLabel(k), k == view.sortKey) { onView(withSortKey(view, k)) } }
                // Which way the sort runs (.svseg); its labels follow the field: Newest/Oldest first, A to Z…
                InkBoxSegmented(
                    options = directionOptions(view.sortKey),
                    selected = view.descending,
                    label = { d -> directionLabel(view.sortKey, d) },
                    onSelect = { d -> onView(view.copy(descending = d)) },
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    height = 34.dp,
                    fill = true,
                    gap = 4.dp,
                )
                InkMenuDivider()
            }
            InkMenuHeader(stringResource(R.string.toolbar_view))
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                layouts.forEach { l -> ViewOptionCard(l, l == layout, Modifier.weight(1f)) { onLayout(l) } }
            }
            InkMenuRow(stringResource(R.string.library_more_view_options), { open = false; options = true }, icon = Ph.slidersHorizontal)
            InkMenuDivider()
            InkMenuHeader(dotJoined(listOf(stringResource(R.string.library_location), locationName)))
            InkMenuRow(stringResource(R.string.change_folder), { open = false; onChangeFolder() }, icon = Ph.folderSimple)
            InkMenuRow(stringResource(R.string.forget_folder), { open = false; onForgetFolder() }, icon = Ph.x)
        }
        DropdownMenu(expanded = options, onDismissRequest = { options = false }) { moreOptions { options = false } }
    }
}

/** A sort field (.svm .m-row): 42dp, 14sp, bold with the check when it is the one in use. */
@Composable
private fun SortRow(label: String, on: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        // A full-bleed menu row (.m-row): clipped to its own bounds, so its press stays in the row.
        Modifier.fillMaxWidth().height(42.dp).clip(RectangleShape).selectable(on, role = Role.RadioButton, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = if (on) SortOn else SortOff, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(18.dp)) { if (on) Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(18.dp)) }
    }
}
