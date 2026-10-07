package com.xnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.tnum

internal val SIDEBAR_WIDTH = 256.dp
internal val LIBRARY_RAIL_WIDTH = 80.dp
internal val DRAWER_WIDTH = 256.dp

private val RowShape = RoundedCornerShape(10.dp)
private val SidebarName = InkType.cardTitle.copy(fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp)
private val RowText = InkType.row
private val RowTextOn = InkType.row.copy(fontWeight = FontWeight.Bold)
private val CountText = InkType.meta.tnum()
private val RailText = InkType.tiny
private val RailTextOn = InkType.tiny.copy(fontWeight = FontWeight.Bold)

/** The app's mark: the finalised launcher icon, 32dp at r9 with one small shadow (.mark). */
@Composable
internal fun LibraryMark(size: Dp = 32.dp) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(9.dp)
    Image(
        painterResource(R.drawable.inkwell_icon_tile), null,
        modifier = Modifier.size(size).shadow(1.dp, shape, ambientColor = ink.shadow, spotColor = ink.shadow).clip(shape),
    )
}

/**
 * The full sidebar (.sb): a pane on wide screens, a slide-over drawer on phones. The mark and the collapse button;
 * All notes, Favourites, Recent; Folders (with pinned deeper folders) and Tags, each under a header with its +;
 * and at its foot, under a hairline, Trash, Settings and About.
 */
@Composable
internal fun LibrarySidebar(modifier: Modifier, nav: SidebarNav, onCollapse: () -> Unit) {
    val ink = LocalInk.current
    Column(
        modifier
            .fillMaxHeight()
            .background(ink.sidebar)
            .drawBehind {
                // The hairline on the edge facing the shelf: the right in left-to-right layouts, the left in right-to-left.
                val half = 0.5.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) half else size.width - half
                drawLine(ink.line2, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            },
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryMark()
            Text(stringResource(R.string.app_name), style = SidebarName, color = ink.text, maxLines = 1, modifier = Modifier.weight(1f))
            InkIconButton(Ph.sidebarSimple, stringResource(R.string.collapse_sidebar), onCollapse)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            SidebarRow(Ph.books, Ph.booksFill, stringResource(R.string.library_all_notes), nav.allSelected, nav.allCount?.toString(), onClick = nav.onAll)
            if (nav.library) {
                SidebarRow(Ph.heart, Ph.heartFill, stringResource(R.string.library_favourites), nav.favouritesSelected, nav.favouriteCount.takeIf { it > 0 }?.toString(), onClick = nav.onFavourites)
            }
            nav.recent?.let { on -> SidebarRow(Ph.clock, Ph.clockFill, stringResource(R.string.recent), on, null, onClick = nav.onRecent) }
            if (nav.library) {
                SidebarHeader(stringResource(R.string.library_folders), stringResource(R.string.library_new_folder), nav.onNewFolder)
                nav.folders.forEach { f ->
                    key(f.entry.documentUri) {
                        SidebarRow(Ph.folderSimple, Ph.folderSimpleFill, f.entry.name, f.docId == nav.activeFolder, f.count.toString()) { nav.onOpenFolder(f.entry.documentUri) }
                    }
                }
                nav.pins.forEachIndexed { i, pin ->
                    key(pin.uri) { PinnedRow(pin.name, i == nav.activePin, onClick = { nav.onOpenPin(pin) }, onUnpin = { nav.onUnpin(pin) }) }
                }
                if (nav.tags) {
                    var tagMenu by remember { mutableStateOf(false) }
                    Box {
                        SidebarHeader(stringResource(R.string.sidebar_tags), stringResource(R.string.library_new_tag)) { tagMenu = true }
                        // A tag is a colour code with a name: pick the colour, then name it.
                        DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                            ColorCodeMenuContent { c -> tagMenu = false; if (c != null) nav.onRenameColor(c) }
                        }
                    }
                    nav.colors.forEach { (color, name) ->
                        key(color) {
                            TagRow(
                                color, name, nav.colorCounts[color], color == nav.activeColor,
                                onClick = { nav.onOpenColor(color) }, onRename = { nav.onRenameColor(color) }, onForget = { nav.onForgetColor(color) },
                            )
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ink.line2, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 18.dp),
        ) {
            if (nav.trashCount >= 0) {
                SidebarRow(Ph.trash, Ph.trashFill, stringResource(R.string.trash), nav.view == BackstageView.TRASH, nav.trashCount.takeIf { it > 0 }?.toString()) {
                    nav.onSelectView(BackstageView.TRASH)
                }
            }
            SidebarRow(Ph.gearSix, Ph.gearSixFill, stringResource(R.string.library_settings), nav.view == BackstageView.PREFERENCES, null) { nav.onSelectView(BackstageView.PREFERENCES) }
            SidebarRow(Ph.info, Ph.infoFill, stringResource(R.string.about), nav.view == BackstageView.ABOUT, null) { nav.onSelectView(BackstageView.ABOUT) }
        }
    }
}

/** A sidebar section's header (.sb-sec): bold sentence case, with its + (a 36dp quiet button). */
@Composable
private fun SidebarHeader(label: String, addLabel: String, onAdd: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).height(36.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = InkType.label, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
        InkIconButton(Ph.plus, addLabel, onAdd, size = 36.dp, iconSize = 18.dp, tint = ink.text2)
    }
}

/** A row's frame (.row): 44dp, r10; selected wears the --sel pill (the label goes bold and the icon Fill, so it never relies on the fill). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarRowFrame(selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RowShape)
            .then(if (selected) Modifier.background(ink.sel) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowScope.RowLabel(label: String, selected: Boolean, count: String?, trailing: (@Composable () -> Unit)? = null) {
    val ink = LocalInk.current
    Text(label, style = if (selected) RowTextOn else RowText, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    trailing?.invoke()
    if (count != null) Text(count, style = CountText, color = ink.text2)
}

/** One destination: its icon (Fill when selected), its name and, at the end, how many it holds. */
@Composable
private fun SidebarRow(
    icon: ImageVector,
    iconOn: ImageVector,
    label: String,
    selected: Boolean,
    count: String?,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SidebarRowFrame(selected, onClick, onLongClick) {
        Icon(if (selected) iconOn else icon, null, tint = LocalInk.current.text, modifier = Modifier.size(22.dp))
        RowLabel(label, selected, count, trailing)
    }
}

/** A folder pinned from deeper in the tree: its pin instead of a count; long-press offers to unpin it. */
@Composable
private fun PinnedRow(label: String, selected: Boolean, onClick: () -> Unit, onUnpin: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        SidebarRow(
            Ph.folderSimple, Ph.folderSimpleFill, label, selected, null,
            onLongClick = { menu = true },
            trailing = { Icon(Ph.pushPin, stringResource(R.string.pinned_to_sidebar), tint = LocalInk.current.text2, modifier = Modifier.size(14.dp)) },
            onClick = onClick,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.widthIn(min = 248.dp)) {
            InkMenuRow(stringResource(R.string.unpin_from_sidebar), { menu = false; onUnpin() }, icon = Ph.pushPinSlash)
        }
    }
}

/** A named colour (.row with .tagdot): everything carrying it; long-press to rename or forget the name. */
@Composable
private fun TagRow(color: Rgba, name: String, count: Int?, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit, onForget: () -> Unit) {
    val palette = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Box {
        SidebarRowFrame(selected, onClick, onLongClick = { menu = true }) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).background(codeTint(color, palette), CircleShape))
            }
            RowLabel(name, selected, count?.toString())
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.widthIn(min = 248.dp)) {
            InkMenuRow(stringResource(R.string.rename), { menu = false; onRename() }, icon = Ph.pencilSimple)
            InkMenuRow(stringResource(R.string.remove_name), { menu = false; onForget() }, icon = Ph.x)
        }
    }
}

/** The sidebar folded to an 80dp rail on wide screens: the same destinations as icons in pills, their names beneath. */
@Composable
internal fun LibraryRail(modifier: Modifier, nav: SidebarNav, onExpand: () -> Unit) {
    val ink = LocalInk.current
    Column(
        modifier
            .fillMaxHeight()
            .background(ink.sidebar)
            .drawBehind {
                // The hairline on the edge facing the shelf: the right in left-to-right layouts, the left in right-to-left.
                val half = 0.5.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) half else size.width - half
                drawLine(ink.line2, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkIconButton(Ph.sidebarSimple, stringResource(R.string.expand_sidebar), onExpand, size = 48.dp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            RailItem(Ph.books, Ph.booksFill, stringResource(R.string.library_all_notes), nav.allSelected) { nav.onAll() }
            if (nav.library) RailItem(Ph.heart, Ph.heartFill, stringResource(R.string.library_favourites), nav.favouritesSelected) { nav.onFavourites() }
            nav.recent?.let { on -> RailItem(Ph.clock, Ph.clockFill, stringResource(R.string.recent), on) { nav.onRecent() } }
            if (nav.folders.isNotEmpty() || nav.pins.isNotEmpty()) Spacer(Modifier.height(8.dp))
            nav.folders.forEach { f ->
                key(f.entry.documentUri) {
                    RailItem(Ph.folderSimple, Ph.folderSimpleFill, f.entry.name, f.docId == nav.activeFolder) { nav.onOpenFolder(f.entry.documentUri) }
                }
            }
            nav.pins.forEachIndexed { i, pin ->
                key(pin.uri) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        RailItem(Ph.folderSimple, Ph.folderSimpleFill, pin.name, i == nav.activePin, onLongClick = { menuOpen = true }) { nav.onOpenPin(pin) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.widthIn(min = 248.dp)) {
                            InkMenuRow(stringResource(R.string.unpin_from_sidebar), { menuOpen = false; nav.onUnpin(pin) }, icon = Ph.pushPinSlash)
                        }
                    }
                }
            }
        }
        if (nav.trashCount >= 0) RailItem(Ph.trash, Ph.trashFill, stringResource(R.string.trash), nav.view == BackstageView.TRASH) { nav.onSelectView(BackstageView.TRASH) }
        RailItem(Ph.gearSix, Ph.gearSixFill, stringResource(R.string.library_settings), nav.view == BackstageView.PREFERENCES) { nav.onSelectView(BackstageView.PREFERENCES) }
        RailItem(Ph.info, Ph.infoFill, stringResource(R.string.about), nav.view == BackstageView.ABOUT) { nav.onSelectView(BackstageView.ABOUT) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(icon: ImageVector, iconOn: ImageVector, label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier
            .width(LIBRARY_RAIL_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics { this.selected = selected }
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(16.dp)).then(if (selected) Modifier.background(ink.sel) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) iconOn else icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = if (selected) RailTextOn else RailText, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
