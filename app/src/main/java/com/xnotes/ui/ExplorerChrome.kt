package com.xnotes.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.settings.ExplorerLayout
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.FolderPlacement
import com.xnotes.settings.GroupBy
import com.xnotes.settings.ThumbShape
import com.xnotes.settings.TileSize
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkRowCheck
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.Palette
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor

/** A layout's icon; [on] is its Fill weight, for the chosen one. */
internal fun layoutIcon(l: ExplorerLayout, on: Boolean = false): ImageVector = when (l) {
    ExplorerLayout.GRID -> if (on) Ph.squaresFourFill else Ph.squaresFour
    ExplorerLayout.GALLERY -> if (on) Ph.cardsFill else Ph.cards
    ExplorerLayout.LIST -> if (on) Ph.rowsFill else Ph.rows
    ExplorerLayout.COLUMNS -> if (on) Ph.columnsFill else Ph.columns
    ExplorerLayout.TIMELINE -> if (on) Ph.calendarBlankFill else Ph.calendarBlank
}

@Composable
internal fun sortLabel(k: ExplorerSortKey): String = when (k) {
    ExplorerSortKey.NAME -> stringResource(R.string.sort_name)
    ExplorerSortKey.MODIFIED -> stringResource(R.string.sort_modified)
    ExplorerSortKey.CREATED -> stringResource(R.string.sort_created)
    ExplorerSortKey.SIZE -> stringResource(R.string.sort_size)
}

/** Which way a sort runs, in words that fit its field: A to Z, Newest first, Largest first. */
@Composable
internal fun directionLabel(k: ExplorerSortKey, descending: Boolean): String = when (k) {
    ExplorerSortKey.NAME -> if (descending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
    ExplorerSortKey.SIZE -> if (descending) stringResource(R.string.largest_first) else stringResource(R.string.smallest_first)
    else -> if (descending) stringResource(R.string.newest_first) else stringResource(R.string.oldest_first)
}

private val ChipOff = InkType.chip
private val ChipOn = InkType.chip.copy(fontWeight = FontWeight.ExtraBold)

/**
 * A chip in the explorer and View options (.chip): 36dp pill, 1dp --line; [on] is the chosen look, a 2dp near-black
 * ring and an extra-bold label (never colour alone). Without [labelled] only its icons show.
 */
@Composable
internal fun ExplorerChip(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: ImageVector? = null,
    labelled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(36.dp)
            .pressScale(src, 0.96f)
            .clip(CircleShape)
            .border(if (on) 2.dp else 1.dp, if (on) ink.solid else ink.line, CircleShape)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { selected = on }
            .padding(start = if (icon != null) 12.dp else 14.dp, end = if (trailing != null || !labelled) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, if (labelled) null else label, tint = ink.text, modifier = Modifier.size(16.dp))
        if (labelled) Text(label, style = if (on) ChipOn else ChipOff, color = ink.text, maxLines = 1, softWrap = false)
        if (trailing != null) Icon(trailing, null, tint = ink.text, modifier = Modifier.size(14.dp))
    }
}

/** Height of the library's title row, which stays over the files as they scroll under it. */
internal val EXPLORER_HEADER = 64.dp

/** Keeps a pointer from reaching whatever lies under this element, without consuming it. */
internal val BlockPointer = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }

/** A floating control's own surface, edge and shadow, faded in by [lift] (0 to 1) while files scroll under it. */
internal fun Modifier.floatingBacking(lift: () -> Float, shape: Shape, fill: Color, edge: Color, shadow: () -> Float = lift): Modifier = this
    .graphicsLayer {
        shadowElevation = 3.dp.toPx() * shadow()
        this.shape = shape
        clip = false
    }
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            val l = lift()
            if (l > 0f) {
                drawOutline(outline, fill.copy(alpha = fill.alpha * l))
                drawOutline(outline, edge.copy(alpha = edge.alpha * l), style = stroke)
            }
        }
    }
    .then(BlockPointer)

/** A plain icon button in the explorer's bars: the kit's 44dp round button. */
@Composable
internal fun ExplorerIcon(icon: ImageVector, desc: String, tint: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    InkIconButton(icon, desc, onClick, enabled = enabled, iconSize = 20.dp, tint = tint ?: LocalInk.current.text)
}

/** A heading over one group of items: its name (with the colour's dot when grouped by colour) and how many it holds. */
@Composable
internal fun GroupHeader(label: String, count: Int, color: Rgba? = null, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    Row(modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (color != null) Box(Modifier.size(10.dp).background(codeTint(color, palette), CircleShape))
        Text(label, style = InkType.label, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text("$count", style = InkType.meta.tnum(), color = ink.text2)
    }
}

/** A colour code as drawn on the explorer's chrome: as stored in the dark themes, deepened in the light one. */
internal fun codeTint(c: Rgba, palette: Palette): Color =
    (if (palette.isDark) c else com.xnotes.ui.theme.ColorMath.darkenForLight(c)).toComposeColor()

/**
 * The bar that takes the header's place while items are picked: ×, "N selected", Select all, then the actions,
 * on the page background. The count gives way first when the actions don't fit; then they scroll.
 */
@Composable
internal fun SelectionBar(count: Int, onClear: () -> Unit, onSelectAll: (() -> Unit)?, actions: @Composable () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxSize().background(ink.bg).then(BlockPointer).padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIconButton(Ph.x, stringResource(R.string.clear_selection), onClear)
        val scroll = rememberScrollState()
        SubcomposeLayout(Modifier.weight(1f).fillMaxHeight()) { c ->
            val loose = Constraints(maxHeight = c.maxHeight)
            val counted = subcompose("count") {
                Text(stringResource(R.string.n_selected, count), style = InkType.cardTitle.tnum(), color = ink.text, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 12.dp))
            }.first().measure(loose)
            val all = subcompose("all") {
                if (onSelectAll != null) InkGhostButton(stringResource(R.string.select_all), onSelectAll)
            }.firstOrNull()?.measure(loose)
            val tools = subcompose("tools") {
                Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { actions() }
            }.first()
            val room = (c.maxWidth - (all?.width ?: 0)).coerceAtLeast(0)
            val showCount = counted.width + tools.maxIntrinsicWidth(c.maxHeight) <= room
            val bar = tools.measure(Constraints(maxWidth = if (showCount) room - counted.width else room, maxHeight = c.maxHeight))
            layout(c.maxWidth, c.maxHeight) {
                var x = 0
                if (showCount) { counted.placeRelative(0, (c.maxHeight - counted.height) / 2); x = counted.width }
                all?.placeRelative(x, (c.maxHeight - all.height) / 2)
                bar.placeRelative(c.maxWidth - bar.width, (c.maxHeight - bar.height) / 2)
            }
        }
    }
}

/** A hairline in the selection bar, setting the destructive action apart. */
@Composable
internal fun SelectionDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(LocalInk.current.line2))
}

private val BadgeLabel = InkType.small.copy(fontWeight = FontWeight.Bold).tnum()

/** A small label on a thumbnail (.badge): its kind, or its page count. */
@Composable
internal fun TileBadge(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier
            .height(24.dp)
            .shadow(1.dp, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .background(ink.badge, CircleShape)
            .padding(start = 8.dp, end = if (text.isEmpty()) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = ink.badgeInk, modifier = Modifier.size(14.dp))
        if (text.isNotEmpty()) Text(text, style = BadgeLabel, color = ink.badgeInk, maxLines = 1)
    }
}

private val CardLabel = InkType.tiny
private val CardLabelOn = InkType.tiny.copy(fontWeight = FontWeight.Bold)

/** One layout as a card (.vopt): 62dp tall, r12, 1dp --line; the chosen one has a 2dp near-black ring, the Fill icon and a bold label. */
@Composable
internal fun ViewOptionCard(layout: ExplorerLayout, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(62.dp)
            .pressScale(src, 0.96f)
            .clip(shape)
            .border(if (on) 2.dp else 1.dp, if (on) ink.solid else ink.line, shape)
            .selectable(on, src, LocalIndication.current, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(layoutIcon(layout, on), null, tint = ink.text, modifier = Modifier.size(22.dp))
        Text(stringResource(layout.labelRes), style = if (on) CardLabelOn else CardLabel, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The View options popover's contents: every setting of [view] the current [layout] uses, applied as it's tapped.
 * [everyFolder] is whether one view serves every folder; off, each folder keeps its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ViewOptionsContent(
    view: ExplorerView,
    layouts: List<ExplorerLayout>,
    everyFolder: Boolean,
    onChange: (ExplorerView) -> Unit,
    onReset: () -> Unit,
    onEveryFolder: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val ink = LocalInk.current
    val layout = view.layout
    Column(Modifier.widthIn(max = 372.dp).padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.toolbar_view), style = InkType.cardTitle, color = ink.text, modifier = Modifier.weight(1f))
            InkGhostButton(stringResource(R.string.reset), onReset)
            InkIconButton(Ph.x, stringResource(R.string.close_view_options), onClose, size = 36.dp, iconSize = 18.dp)
        }
        OptionBlock(stringResource(R.string.opt_layout)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                layouts.forEach { l -> ViewOptionCard(l, l == layout, Modifier.width(62.dp)) { onChange(view.copy(layout = l)) } }
            }
        }
        if (layout == ExplorerLayout.LIST) {
            OptionBlock(stringResource(R.string.opt_rows)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerChip(stringResource(R.string.rows_comfortable), !view.compactRows) { onChange(view.copy(compactRows = false)) }
                    ExplorerChip(stringResource(R.string.rows_compact), view.compactRows) { onChange(view.copy(compactRows = true)) }
                }
            }
        } else if (layout != ExplorerLayout.COLUMNS) {
            OptionBlock(stringResource(R.string.opt_tile_size)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileSize.entries.forEach { t -> ExplorerChip(t.label, view.tileSize == t, Modifier.widthIn(min = 48.dp)) { onChange(view.copy(tileSize = t)) } }
                }
                if (layout == ExplorerLayout.GRID) {
                    Text(stringResource(R.string.pinch_hint), style = InkType.meta, color = ink.text2)
                }
            }
        }
        if (layout == ExplorerLayout.TIMELINE) {
            OptionBlock(stringResource(R.string.opt_place_by)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerChip(stringResource(R.string.sort_created), view.timelineByCreated) { onChange(view.copy(timelineByCreated = true)) }
                    ExplorerChip(stringResource(R.string.sort_modified), !view.timelineByCreated) { onChange(view.copy(timelineByCreated = false)) }
                }
            }
        }
        if (layout == ExplorerLayout.GRID) {
            OptionBlock(stringResource(R.string.opt_thumbnail)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerChip(stringResource(R.string.thumb_top), view.thumb == ThumbShape.TOP) { onChange(view.copy(thumb = ThumbShape.TOP)) }
                    ExplorerChip(stringResource(R.string.thumb_page), view.thumb == ThumbShape.PAGE) { onChange(view.copy(thumb = ThumbShape.PAGE)) }
                }
            }
        }
        if (layout == ExplorerLayout.GRID || layout == ExplorerLayout.GALLERY || layout == ExplorerLayout.TIMELINE) {
            OptionBlock(stringResource(R.string.opt_show_on_tiles)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun toggle(label: String, on: Boolean, flip: () -> ExplorerView) =
                        ExplorerChip(label, on, icon = if (on) Ph.check else Ph.plus) { onChange(flip()) }
                    toggle(stringResource(R.string.group_kind), view.showKind) { view.copy(showKind = !view.showKind) }
                    if (layout != ExplorerLayout.TIMELINE) {
                        toggle(stringResource(R.string.show_page_count), view.showPages) { view.copy(showPages = !view.showPages) }
                        toggle(stringResource(R.string.show_time), view.showTime) { view.copy(showTime = !view.showTime) }
                        toggle(stringResource(R.string.sort_size), view.showSize) { view.copy(showSize = !view.showSize) }
                    }
                    toggle(stringResource(R.string.colour_code), view.showColour) { view.copy(showColour = !view.showColour) }
                }
            }
        }
        // Covers stand in one run, so only the denser layouts group under headings.
        if (layout != ExplorerLayout.COLUMNS && layout != ExplorerLayout.TIMELINE && layout != ExplorerLayout.GALLERY) {
            OptionBlock(stringResource(R.string.opt_group_by)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupBy.entries.forEach { g -> ExplorerChip(stringResource(g.labelRes), view.groupBy == g) { onChange(view.copy(groupBy = g)) } }
                }
            }
        }
        if (layout != ExplorerLayout.TIMELINE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sort_by), style = InkType.label, color = ink.text, modifier = Modifier.weight(1f))
                    Text(directionLabel(view.sortKey, view.descending), style = InkType.meta, color = ink.text2)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ExplorerSortKey.NAME to stringResource(R.string.sort_name), ExplorerSortKey.MODIFIED to stringResource(R.string.modified), ExplorerSortKey.CREATED to stringResource(R.string.created), ExplorerSortKey.SIZE to stringResource(R.string.sort_size)).forEach { (k, label) ->
                        val active = view.sortKey == k
                        ExplorerChip(label, active, trailing = if (active) (if (view.descending) Ph.arrowDown else Ph.arrowUp) else null) {
                            onChange(if (active) view.copy(descending = !view.descending) else view.copy(sortKey = k, descending = k != ExplorerSortKey.NAME))
                        }
                    }
                }
            }
            OptionBlock(stringResource(R.string.kind_folders)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderPlacement.entries.forEach { f -> ExplorerChip(stringResource(f.labelRes), view.folders == f) { onChange(view.copy(folders = f)) } }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line2))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEveryFolder(!everyFolder) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            InkRowCheck(everyFolder, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.view_every_folder), style = InkType.row, color = ink.text)
                Text(stringResource(R.string.view_every_folder_hint), style = InkType.meta, color = ink.text2)
            }
        }
    }
}

@Composable
private fun OptionBlock(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = InkType.label, color = LocalInk.current.text)
        content()
    }
}

/** A thin rule across the explorer, like the one under the Columns header. */
@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.line2))
}

/** One quiet, centred line in an empty part of the explorer (Frame 5's "text only" states). */
@Composable
internal fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 160.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = InkType.body.copy(fontWeight = FontWeight.Bold), color = LocalInk.current.text, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}
