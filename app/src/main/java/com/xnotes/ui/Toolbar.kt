package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.platform.ImageDecoder
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.kit.rememberActOnce
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkTokens
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Toolbar(
    editor: Editor,
    onToggleFullscreen: () -> Unit,
    onOpenBackstage: () -> Unit,
    onInsertImage: () -> Unit,
    onAddStickers: () -> Unit,
    onClosePane: (() -> Unit)? = null,
    onImportTemplate: () -> Unit = {},
) {
    // One card up on the bar at a time: a tool's, a swatch's picker, or one of the bar's menus.
    val cards = rememberToolCardState()
    var renaming by remember { mutableStateOf(false) }
    val layout = editor.toolbarLayout
    val more = moreItems(layout, ToolSurface.NOTE, editor.hasPdf)
    val lit = moreLit(more, editor.tool.barItem()) { it == ToolbarItem.RULER && editor.rulerVisible }
    // Pinned outside the scrolling strip so closing a split pane is always one tap away.
    ToolbarFrame(armed = barGlideKey(editor.tool, layout, lit), trailing = onClosePane?.let { { ClosePaneButton(it) } }) {
        // The bar is driven by the user-customisable layout; separators sit between non-empty
        // sections, and each item dispatches to its renderer (see ToolbarItemView).
        layout.visibleSections.forEachIndexed { si, section ->
            if (si > 0) Separator()
            section.visibleEntries.forEach { entry ->
                key(entry.item) {
                    ToolbarItemView(
                        editor = editor,
                        item = entry.item,
                        cards = cards,
                        more = more,
                        lit = lit,
                        onRename = { renaming = true },
                        onOpenBackstage = onOpenBackstage,
                        onInsertImage = onInsertImage,
                        onAddStickers = onAddStickers,
                        onToggleFullscreen = onToggleFullscreen,
                        onImportTemplate = onImportTemplate,
                    )
                }
            }
        }
    }

    if (renaming) {
        val renameFailed = stringResource(R.string.err_rename_note)
        RenameDialog(
            initial = editor.title,
            onConfirm = { name ->
                renaming = false
                if (!editor.renameCurrentDocument(name)) editor.message = renameFailed
            },
            onDismiss = { renaming = false },
        )
    }
}

/** Renders one toolbar item by id. Every card or menu it opens goes through the bar's one [cards]. */
@Composable
private fun ToolbarItemView(
    editor: Editor,
    item: ToolbarItem,
    cards: ToolCardState,
    more: List<MoreItem>,
    lit: MoreLit,
    onRename: () -> Unit,
    onOpenBackstage: () -> Unit,
    onInsertImage: () -> Unit,
    onAddStickers: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onImportTemplate: () -> Unit,
) {
    when (item) {
        // Canvas-only items, and the retired tools: no paged layout can hold one (ToolbarLayout.NOTE_ITEMS).
        ToolbarItem.WAYPOINTS, ToolbarItem.MINIMAP, ToolbarItem.WAND, ToolbarItem.SELECT, ToolbarItem.SCREENSHOT -> Unit

        ToolbarItem.HOME -> ToolbarItemButton(ToolbarItem.HOME, stringResource(R.string.toolbar_home), onClick = onOpenBackstage)
        // A name has no room down a side rail.
        ToolbarItem.TITLE -> if (!LocalBar.current.vertical) Label(
            if (editor.state.document.displayName == null && editor.state.document.path == null) stringResource(R.string.untitled) else editor.title,
            modifier = Modifier
                .widthIn(max = 160.dp)
                .clip(RoundedCornerShape(cornerOf(8.dp)))
                .clickable(role = Role.Button, onClick = onRename),
        )
        ToolbarItem.SIDEBAR ->
            ToolbarItemButton(ToolbarItem.SIDEBAR, stringResource(R.string.side_panel), active = editor.sidebarVisible, toggle = true) { editor.toggleSidebar() }

        // The pen button stands for every pen type: it shows the one in hand and opens on them all.
        ToolbarItem.PEN -> PenFamilyButton(editor, editor.toolbarLayout, cards, ToolSurface.NOTE, editor::inkOnPaper)

        ToolbarItem.BALLPOINT, ToolbarItem.DASHED, ToolbarItem.CALLIGRAPHY, ToolbarItem.SPEED,
        ToolbarItem.TAPER, ToolbarItem.PENCIL, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER, ToolbarItem.PAN,
        ToolbarItem.LASSO, ToolbarItem.SHAPE, ToolbarItem.TEXT, ToolbarItem.TEXT_BOX,
        ToolbarItem.LASER, ToolbarItem.TAPE -> {
            val tool = Tool.fromId(item.id)
            if (tool != null) ToolButton(editor, tool, cards, ToolSurface.NOTE)
        }

        // Marks a PDF's text, so a note without a PDF has no use for it.
        ToolbarItem.MARKUP -> if (editor.hasPdf) ToolButton(editor, Tool.MARKUP, cards, ToolSurface.NOTE)

        ToolbarItem.RULER ->
            ToolbarItemButton(ToolbarItem.RULER, stringResource(R.string.tool_ruler), active = editor.rulerVisible, toggle = true) { editor.toggleRuler() }

        ToolbarItem.IMAGE -> ImageMenu(editor, cards, onInsertImage, onAddStickers)

        ToolbarItem.UNDO -> ToolbarItemButton(ToolbarItem.UNDO, stringResource(R.string.undo), enabled = editor.canUndo) { editor.undo() }
        ToolbarItem.REDO -> ToolbarItemButton(ToolbarItem.REDO, stringResource(R.string.redo), enabled = editor.canRedo) { editor.redo() }

        ToolbarItem.PAGE_NAV -> PageNav(editor, cards)
        ToolbarItem.STYLES -> StylesButton(editor, cards, onImportTemplate)
        ToolbarItem.MARGINS -> MarginsButton(editor, cards, onImportTemplate)
        ToolbarItem.VIEW -> ViewButton(editor, cards)
        ToolbarItem.ZOOM -> ZoomControls(editor, cards)
        ToolbarItem.FIT -> FitMenu(editor, cards)
        ToolbarItem.ZOOM_LOCK -> ZoomLockButton(editor.zoomLocked) { editor.toggleZoomLock() }
        ToolbarItem.FULLSCREEN -> ToolbarItemButton(ToolbarItem.FULLSCREEN, stringResource(R.string.toolbar_fullscreen), onClick = onToggleFullscreen)

        ToolbarItem.MORE -> MoreForNote(editor, cards, more, lit, onInsertImage)

        ToolbarItem.COLORS -> QuickColours(editor, cards, editor::inkOnPaper)
    }
}

/** The bar's named cards and menus, one [ToolCardState] key each. */
internal object BarCards {
    val IMAGE = CardKey.Named("image")
    val STICKERS = CardKey.Named("stickers")
    val FIT = CardKey.Named("fit")
    val JUMP = CardKey.Named("jump")
    val ZOOM = CardKey.Named("zoom")
    val VIEW = CardKey.Named("view")
    val STYLES = CardKey.Named("styles")
    val MARGINS = CardKey.Named("margins")
    val WAYPOINTS = CardKey.Named("waypoints")
    val MORE = CardKey.Named("more")
}

/**
 * A tap on [tool]'s button, or on its ⋯ row ([toolTap]): another tool is picked up through
 * [ToolPopupHost.hostArmTool] (the paged `selectTool`, the canvas `armTool`), closing any card; the armed
 * tool opens its card, or closes it when it is already up.
 */
internal fun ToolCardState.onToolTap(host: ToolPopupHost, tool: Tool, surface: ToolSurface) {
    when (toolTap(host.hostTool, tool, (open as? CardKey.OfTool)?.tool, surface)) {
        ToolTap.ARM -> {
            host.hostArmTool(tool)
            close()
        }
        ToolTap.OPEN_CARD -> open(CardKey.OfTool(tool))
        ToolTap.CLOSE_CARD -> close()
    }
}

/** The tools whose cards are up or still closing, by the tool each was opened for (never the tool in hand). */
internal fun ToolCardState.toolKeys(): List<Tool> =
    listOfNotNull(open, shown).distinct().mapNotNull { (it as? CardKey.OfTool)?.tool }

/** Closes [key] with no exit of the host's own: for a modal sheet, which runs its own. */
internal fun ToolCardState.dismissNow(key: CardKey) {
    if (open == key) close()
    closed(key)
}

/** Hosts, at [anchor], the card of each tool that is up or closing on [cards] and that this button [covers]. */
@Composable
internal fun ToolCardsAt(host: ToolPopupHost, cards: ToolCardState, surface: ToolSurface, anchor: PopoverAnchor, covers: (Tool) -> Boolean) {
    for (t in cards.toolKeys()) {
        if (covers(t)) key(t) {
            CardSlot(cards, CardKey.OfTool(t), anchor) { dismiss -> ToolCardFor(host, t, surface, dismiss) }
        }
    }
}

/** A tool's button, the same on both bars: arms the tool; tapping it again opens or closes its card, which hangs from it. */
@Composable
internal fun ToolButton(host: ToolPopupHost, tool: Tool, cards: ToolCardState, surface: ToolSurface) {
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarButton(
            stringResource(tool.labelRes),
            onClick = { cards.onToolTap(host, tool, surface) },
            active = host.hostTool == tool,
            glideKey = tool,
            anchor = anchor,
        ) { tint, size, on -> ToolGlyph(tool, tint, size, on) }
        ToolCardsAt(host, cards, surface, anchor) { it == tool }
    }
}

/**
 * The quick colours, the same on both bars: the swatches the bar shows, each drawn as it inks on the
 * paper ([inkOnPaper]). A tap lets go of a colour the pen box pinned and arms the swatch; tapping the
 * armed swatch again opens its colour picker, which hangs from it.
 */
@Composable
internal fun QuickColours(host: ToolPopupHost, cards: ToolCardState, inkOnPaper: (Rgba) -> Rgba) {
    val colours = host.hostToolbarColors.take(host.hostSwatchCount)
    colours.forEachIndexed { i, color ->
        key(i) {
            val anchor = remember { PopoverAnchor() }
            val swatch = CardKey.OfSwatch(i)
            val shown = inkOnPaper(color)
            Box {
                // Named by the colour it shows on the paper, and its place on the bar (TalkBack, review I5).
                Swatch(shown.toComposeColor(), active = i == host.hostActiveColorIndex, label = inkColourLabel(shown, i, colours.size), anchor = anchor) {
                    host.releasePenBoxInk()
                    if (i == host.hostActiveColorIndex) cards.open(swatch) else host.hostPickSwatch(i)
                }
                // The picker is an InkPopover now: it plays its exit and stops taking touches the moment it closes.
                CardSlot(cards, swatch, anchor) { dismiss -> ColorSwitcherPopup(host, i, dismiss) }
            }
        }
    }
}

/**
 * The bar's small menus (image, stickers, fit, ⋯), hosted straight in `InkPopover`, which draws no surface:
 * a raised r16 card with the line2 ring and the menu shadow (.menu, W 160), padding 8 / 0 / 6. It scrolls
 * inside the popover's maximum height.
 */
@Composable
internal fun BarMenu(width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(width)
            .inkSurface(RoundedCornerShape(cornerOf(16.dp)), InkElevation.MENU)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 6.dp),
        content = content,
    )
}

/** Previous page, the "3 / 12" counter (tap: jump to a page), next page. */
@Composable
private fun PageNav(editor: Editor, cards: ToolCardState) {
    ToolbarIcon(Ph.caretLeft, stringResource(R.string.previous_page)) { editor.prevPage() }
    val anchor = remember { PopoverAnchor() }
    Box {
        PageCounter(
            editor.pageIndex + 1,
            editor.pageCount,
            Modifier
                .popoverAnchor(anchor)
                .clip(RoundedCornerShape(cornerOf(8.dp)))
                .clickable(role = Role.Button) { cards.toggle(BarCards.JUMP) },
        )
        CardSlot(cards, BarCards.JUMP, anchor) { dismiss -> PageJumpPopup(editor, dismiss) }
    }
    ToolbarIcon(Ph.caretRight, stringResource(R.string.next_page)) { editor.nextPage() }
}

/** Zoom out, the zoom level (tap: the zoom limits), zoom in; the buttons rest while zoom is locked. */
@Composable
private fun ZoomControls(editor: Editor, cards: ToolCardState) {
    ToolbarIcon(Ph.minus, stringResource(R.string.zoom_out), enabled = !editor.zoomLocked) { editor.zoomOut() }
    val anchor = remember { PopoverAnchor() }
    Box {
        Label(
            "${editor.zoomPercent}%",
            modifier = Modifier
                .popoverAnchor(anchor)
                .clip(RoundedCornerShape(cornerOf(8.dp)))
                .clickable(role = Role.Button) { cards.toggle(BarCards.ZOOM) },
        )
        CardSlot(cards, BarCards.ZOOM, anchor) { dismiss -> ZoomMenuPopup(editor, dismiss) }
    }
    ToolbarIcon(Ph.plus, stringResource(R.string.zoom_in), enabled = !editor.zoomLocked) { editor.zoomIn() }
}

/** Zoom lock, the same on both bars: a toggle, lit while [locked], its lock turning to Fill. */
@Composable
internal fun ZoomLockButton(locked: Boolean, onToggle: () -> Unit) {
    ToolbarButton(stringResource(R.string.toolbar_zoom_lock), onToggle, active = locked, toggle = true) { tint, size, _ ->
        // Open padlock while unlocked, filled when locked: the state reads at a glance.
        Icon(if (locked) Ph.lockSimpleFill else Ph.lockSimpleOpen, null, tint = tint, modifier = Modifier.size(size))
    }
}

/** The paged bar's ⋯: a row per hidden tool, the Ruler switch and Insert image…, from [more]; lit as [lit] says. */
@Composable
private fun MoreForNote(editor: Editor, cards: ToolCardState, more: List<MoreItem>, lit: MoreLit, onInsertImage: () -> Unit) {
    val rows = ArrayList<MoreRow>(more.size)
    for (m in more) {
        when (m.kind) {
            MoreKind.TOOL -> {
                val t = Tool.fromId(m.item.id)
                if (t != null) rows += MoreRow(m, stringResource(t.labelRes), editor.tool == t) { cards.onToolTap(editor, t, ToolSurface.NOTE) }
            }
            MoreKind.SWITCH ->
                if (m.item == ToolbarItem.RULER) rows += MoreRow(m, stringResource(R.string.tool_ruler), editor.rulerVisible) { editor.toggleRuler() }
            MoreKind.ACTION ->
                if (m.item == ToolbarItem.IMAGE) rows += MoreRow(m, stringResource(R.string.insert_image_ellipsis), on = false) { onInsertImage() }
        }
    }
    MoreToolsButton(rows, lit, editor, cards, ToolSurface.NOTE)
}

/** Opens Page setup: paper, ruling and margins, for the note or the current page. Lit while it is open. */
@Composable
private fun StylesButton(editor: Editor, cards: ToolCardState, onImportTemplate: () -> Unit) {
    val open = cards.open == BarCards.STYLES
    PrewarmTemplateThumbs(editor, open)
    ToolbarItemButton(ToolbarItem.STYLES, stringResource(R.string.toolbar_styles), active = open) { cards.toggle(BarCards.STYLES) }
    if (open) PageSetupSheet(editor, onImportTemplate) { cards.dismissNow(BarCards.STYLES) }
}

/** Opens Page setup scrolled to Page margins (extra paper on any edge, for the note or the current page). */
@Composable
private fun MarginsButton(editor: Editor, cards: ToolCardState, onImportTemplate: () -> Unit) {
    val open = cards.open == BarCards.MARGINS
    ToolbarItemButton(ToolbarItem.MARGINS, stringResource(R.string.toolbar_margins), active = open) { cards.toggle(BarCards.MARGINS) }
    if (open) PageSetupSheet(editor, onImportTemplate, startAt = PageSetupStart.MARGINS) { cards.dismissNow(BarCards.MARGINS) }
}

/** Opens the view menu (viewing mode, scroll direction, PDF filters, rotation, scrollbar), hanging from the button. */
@Composable
private fun ViewButton(editor: Editor, cards: ToolCardState) {
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarItemButton(ToolbarItem.VIEW, stringResource(R.string.toolbar_view), active = cards.open == BarCards.VIEW, anchor = anchor) {
            cards.toggle(BarCards.VIEW)
        }
        CardSlot(cards, BarCards.VIEW, anchor) { dismiss -> ViewMenuPopup(editor, dismiss) }
    }
}

/** Where each glide-keyed button of one bar sits, so the bar can draw one glider under the armed one. */
internal class ToolGlide {
    /** Button centres in the strip's own coordinates, by glide key. Written on layout; an unchanged centre does not invalidate. */
    val centers = mutableStateMapOf<Any, Offset>()
    var row: LayoutCoordinates? = null
}

internal val LocalToolGlide = staticCompositionLocalOf<ToolGlide?> { null }

/** The 2 dp between neighbouring tools (`.tools{gap:2px}`, W 372), as 1 dp either side of each. */
private val TOOL_GAP = 2.dp

/** A disabled button's glyph (Undo with nothing to undo), as the kit's icon buttons have it. */
private const val DISABLED_ALPHA = 0.3f

/**
 * The bar's glider (B2 §2.4, W 377/1354): one `ink.solid` circle, [BarMetrics.button] across, under the
 * button keyed [armed], drawn behind the strip. A new key slides it there on [InkMotion.glide]. It snaps
 * instead on the first layout, on a relayout that only moved the armed button (W 1424), and whenever the
 * pen is down; a glide under way when the pen lands jumps to its end. With no button for [armed] (a tool
 * that is on no button) it is not drawn.
 *
 * Its position is read only while drawing, so a glide redraws the strip and recomposes nothing. It is an
 * [Animatable] moved from an effect rather than `animateFloatAsState`, because whether to snap depends on
 * [LocalPenDown], which composition must not read.
 */
@Composable
internal fun Modifier.toolGlide(glide: ToolGlide, armed: Any?): Modifier {
    val target = armed?.let { glide.centers[it] }
    val bar = LocalBar.current
    val ink = LocalInk.current
    val penDown = LocalPenDown.current
    val along = remember { Animatable(0f) }
    val across = remember { mutableFloatStateOf(0f) }
    val shown = remember { mutableStateOf(false) }
    // The key the glider last settled under: the same key at a new place is a relayout, which snaps.
    val last = remember { arrayOfNulls<Any>(1) }
    LaunchedEffect(armed, target, bar.vertical) {
        if (target == null) {
            shown.value = false
            return@LaunchedEffect
        }
        val to = if (bar.vertical) target.y else target.x
        across.floatValue = if (bar.vertical) target.x else target.y
        val relayout = last[0] == armed
        last[0] = armed
        if (!shown.value || relayout || penDown()) {
            along.snapTo(to)
            shown.value = true
        } else {
            along.animateTo(to, InkMotion.glide())
        }
    }
    LaunchedEffect(along) {
        snapshotFlow { penDown() }.collect { down -> if (down && along.isRunning) along.snapTo(along.targetValue) }
    }
    return onPlaced { glide.row = it }.drawBehind {
        if (!shown.value) return@drawBehind
        val c = if (bar.vertical) Offset(across.floatValue, along.value) else Offset(along.value, across.floatValue)
        drawCircle(ink.solid, bar.button.toPx() / 2f, c)
    }
}

/**
 * A toolbar button (.tbtn, W 373-376): a [BarMetrics.button] round target with no ripple, shrinking to
 * .92 under the finger (`pressScale`, in the layer).
 * - With a [glideKey] it is one of the bar's tools: its place goes into the bar's [ToolGlide], and while
 *   [active] the glider sits under it, so [glyph] is asked for its active form (duotone, or a custom
 *   glyph's active strokes) in `ink.onSolid`. The swap is instant (W 1002).
 * - Without one, [active] means a toggle that is on (Ruler, Sidebar, Zoom lock) or a button whose card is
 *   up: an `ink.selOnRaised` circle behind the glyph. [lit] asks for that circle whatever [active] says (the
 *   ⋯ button's "a hidden switch is on" state, TO 901). With [toggle] the button is a switch for TalkBack,
 *   announced on and off alike, rather than a button that reads "selected" only while on.
 *
 * Nothing here animates in composition: the press scale lives in the layer and the fill is drawn.
 * [spaced] adds the 2 dp tool gap (on by default for glide-keyed buttons; the paged Image menu, which
 * sits among the tools, asks for it too). [anchor] records the button's bounds for the card it opens.
 * [inkDot] draws the pen button's ink dot (§2.5).
 */
@Composable
internal fun ToolbarButton(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    glideKey: Any? = null,
    lit: Boolean = false,
    spaced: Boolean = glideKey != null,
    anchor: PopoverAnchor? = null,
    inkDot: Color? = null,
    toggle: Boolean = false,
    glyph: @Composable (tint: Color, size: Dp, active: Boolean) -> Unit,
) {
    val ink = LocalInk.current
    val bar = LocalBar.current
    val glide = if (glideKey != null) LocalToolGlide.current else null
    val onGlider = active && glide != null
    val filled = lit || (active && glide == null)
    val fill = ink.selOnRaised
    if (glide != null) DisposableEffect(glide, glideKey) { onDispose { glide.centers.remove(glideKey) } }
    val src = remember { MutableInteractionSource() }
    val half = if (spaced) TOOL_GAP / 2 else 0.dp
    Box(
        Modifier
            .padding(if (bar.vertical) PaddingValues(vertical = half) else PaddingValues(horizontal = half))
            .size(bar.button)
            .then(if (anchor != null) Modifier.popoverAnchor(anchor) else Modifier)
            .then(
                if (glide == null || glideKey == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { c ->
                        val row = glide.row
                        if (row != null && row.isAttached && c.isAttached) {
                            glide.centers[glideKey] = row.localPositionOf(c, Offset(c.size.width / 2f, c.size.height / 2f))
                        }
                    }
                },
            )
            .then(
                if (toggle) {
                    Modifier.toggleable(active, src, indication = null, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
                } else {
                    Modifier.clickable(src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                },
            )
            .semantics {
                contentDescription = label
                if (!toggle && (active || lit)) selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .pressScale(src, 0.92f)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .drawBehind { if (filled) drawCircle(fill) },
            contentAlignment = Alignment.Center,
        ) {
            glyph(if (onGlider) ink.onSolid else ink.text, bar.icon, onGlider)
            if (inkDot != null) Box(Modifier.matchParentSize().drawBehind { inkDot(inkDot, onGlider, ink) })
        }
    }
}

/** A [ToolbarButton] showing [icon] (the same icon when active): page arrows, zoom −/+, the zoom lock, close pane. */
@Composable
internal fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    enabled: Boolean = true,
    glideKey: Any? = null,
    anchor: PopoverAnchor? = null,
    onClick: () -> Unit,
) {
    ToolbarButton(contentDescription, onClick, active = active, enabled = enabled, glideKey = glideKey, anchor = anchor) { tint, size, _ ->
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * A [ToolbarButton] showing [item]'s B2 glyph ([ItemIcon]: Phosphor, or the lasso, laser and tape drawings). A
 * [toggle] that is on shows its Fill where the glyph has one (B2 ground rule 4: Sidebar, Minimap).
 */
@Composable
internal fun ToolbarItemButton(
    item: ToolbarItem,
    contentDescription: String,
    active: Boolean = false,
    enabled: Boolean = true,
    glideKey: Any? = null,
    lit: Boolean = false,
    spaced: Boolean = glideKey != null,
    anchor: PopoverAnchor? = null,
    toggle: Boolean = false,
    onClick: () -> Unit,
) {
    ToolbarButton(
        contentDescription,
        onClick,
        active = active,
        enabled = enabled,
        glideKey = glideKey,
        lit = lit,
        spaced = spaced,
        anchor = anchor,
        toggle = toggle,
    ) { tint, size, on -> ItemIcon(item, tint, size, on, filled = toggle && active) }
}

/**
 * The pen button's ink dot (§2.5): 10 dp at the button's lower right (right 6, bottom 6), in a 2 dp halo
 * of the pill's colour, or of `onSolid` while the glider is under it. Dark themes add a faint outer ring
 * (white 55% idle, black 25% on the glider) so the halo still parts the dot from the pill.
 */
private fun DrawScope.inkDot(color: Color, onGlider: Boolean, ink: InkTokens) {
    val r = 5.dp.toPx()
    val c = Offset(size.width - 11.dp.toPx(), size.height - 11.dp.toPx())
    if (ink.isDark) {
        drawCircle(if (onGlider) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.55f), r + 3.5.dp.toPx(), c)
    }
    drawCircle(if (onGlider) ink.onSolid else ink.raised, r + 2.dp.toPx(), c)
    drawCircle(color, r, c)
}

/** Closes this pane of a split, leaving the other one to fill the window. Shown on both toolbars. */
@Composable
internal fun ClosePaneButton(onClose: () -> Unit) {
    Separator()
    ToolbarIcon(Ph.x, stringResource(R.string.close_pane), onClick = onClose)
}

/**
 * A quick colour (.qsw, §2.7): a [BarMetrics.swatchHit] × [BarMetrics.button] hit box (turned for a side
 * rail) around [InkSwatch]'s dot, which brings the 1 dp sw-ring and, when [active], the 2 dp `raised` gap
 * and 2 dp `solid` ring. The dot shrinks to .9 under the finger. [anchor] is where its colour picker opens.
 * TalkBack reads [label] and the selected state; on the [active] swatch a tap's action is "Change colour",
 * since that tap opens the picker.
 */
@Composable
internal fun Swatch(color: Color, active: Boolean, label: String, anchor: PopoverAnchor? = null, onClick: () -> Unit) {
    val bar = LocalBar.current
    val src = remember { MutableInteractionSource() }
    val change = stringResource(R.string.ink_colour_change)
    Box(
        Modifier
            .then(if (bar.vertical) Modifier.size(bar.button, bar.swatchHit) else Modifier.size(bar.swatchHit, bar.button))
            .then(if (anchor != null) Modifier.popoverAnchor(anchor) else Modifier)
            // selectable's semantics, plus a click label: a re-tap of the chosen colour opens its picker.
            .clickable(src, indication = null, onClickLabel = if (active) change else null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.pressScale(src, 0.9f)) { InkSwatch(color, active, bar.swatch, onClick = null) }
    }
}

/** A line of text on the bar (the title, "125%", the canvas status notes): 13 sp Medium in text2. */
@Composable
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = InkType.meta,
        color = LocalInk.current.text2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Between sections (.tsep, W 382): 1 × 24 dp in `line` with 6 dp either side (turned on a side rail). */
@Composable
internal fun Separator() {
    Box(Modifier.padding(if (LocalBar.current.vertical) PaddingValues(vertical = 6.dp) else PaddingValues(horizontal = 6.dp))) { Rule() }
}

/** A hairline across the bar, in `line`. */
@Composable
private fun Rule() {
    val bar = LocalBar.current
    Box(
        Modifier
            .then(if (bar.vertical) Modifier.height(1.dp).width(bar.rule) else Modifier.width(1.dp).height(bar.rule))
            .background(LocalInk.current.line),
    )
}

/**
 * "3 / 12" along the bar in the counter role (13 sp SemiBold, tabular figures, text2), at least a button
 * square so it is a fair target; down a side rail, where that is too wide, the two numbers stack.
 */
@Composable
private fun PageCounter(current: Int, count: Int, modifier: Modifier = Modifier) {
    val bar = LocalBar.current
    val ink = LocalInk.current
    val style = InkType.counter.tnum()
    Box(modifier.defaultMinSize(minWidth = bar.button, minHeight = bar.button).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        if (!bar.vertical) {
            Text("$current / $count", style = style, color = ink.text2, maxLines = 1)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$current", style = style, color = ink.text2, maxLines = 1)
                Rule()
                Text("$count", style = style, color = ink.text2, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ImageMenu(editor: Editor, cards: ToolCardState, onInsertImage: () -> Unit, onAddStickers: () -> Unit) {
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarItemButton(
            ToolbarItem.IMAGE,
            stringResource(R.string.tool_image),
            active = cards.open == BarCards.IMAGE || cards.open == BarCards.STICKERS,
            spaced = true,
            anchor = anchor,
        ) { cards.toggle(BarCards.IMAGE) }
        CardSlot(cards, BarCards.IMAGE, anchor) { dismiss ->
            val once = rememberActOnce(LocalCardExpanded.current)
            InkPopover(expanded = LocalCardExpanded.current, onDismiss = dismiss, anchor = anchor, spec = PopoverSpecs.MoreMenu) {
                BarMenu(IMAGE_MENU_W) {
                    InkMenuRow(stringResource(R.string.paste_image), onClick = { once.run { editor.pasteImage(); dismiss() } }, icon = Ph.clipboard)
                    InkMenuRow(stringResource(R.string.insert_image_ellipsis), onClick = { once.run { onInsertImage(); dismiss() } }, icon = Ph.image)
                    // Swaps this menu for the sticker library, hanging from the same button.
                    InkMenuRow(stringResource(R.string.stickers), onClick = { cards.open(BarCards.STICKERS) }, icon = Ph.smiley)
                }
            }
        }
        CardSlot(cards, BarCards.STICKERS, anchor) { dismiss -> StickersMenu(editor, anchor, onAddStickers, dismiss) }
    }
}

private val IMAGE_MENU_W = 240.dp
private val STICKER = 72.dp
private val STICKER_GAP = 6.dp

/**
 * The sticker library: a grid of saved images that insert with one tap, so a recurring image never
 * needs the gallery round trip. Stickers live on disk (see [Editor.stickers]); each tile decodes its own
 * small preview off the main thread.
 */
@Composable
private fun StickersMenu(editor: Editor, anchor: PopoverAnchor, onAddStickers: () -> Unit, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    // A sticker goes in once per open: a double tap must not insert two.
    val once = rememberActOnce(LocalCardExpanded.current)
    InkPopover(expanded = LocalCardExpanded.current, onDismiss = onDismiss, anchor = anchor, spec = PopoverSpecs.MoreMenu) {
        BarMenu(STICKER * 3 + STICKER_GAP * 2 + 36.dp) {
            InkMenuRow(stringResource(R.string.add_stickers), onClick = onAddStickers, icon = Ph.plus)
            if (editor.stickers.isEmpty()) {
                Text(
                    stringResource(R.string.no_stickers),
                    style = InkType.meta,
                    color = ink.text2,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            } else {
                // Both dimensions fixed: a lazy grid needs bounded constraints, and a fixed size spares the
                // menu from asking it for intrinsics it cannot answer.
                val rows = ((editor.stickers.size + 2) / 3).coerceAtMost(3)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(STICKER_GAP),
                    verticalArrangement = Arrangement.spacedBy(STICKER_GAP),
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .width(STICKER * 3 + STICKER_GAP * 2)
                        .height(STICKER * rows + STICKER_GAP * (rows - 1)),
                ) {
                    items(editor.stickers, key = { it.name }) { file ->
                        StickerTile(
                            file = file,
                            onInsert = { once.run { editor.insertSticker(file); onDismiss() } },
                            onRemove = { editor.removeSticker(file) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.stickers_hint),
                    style = InkType.hint,
                    color = ink.text2,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(file: java.io.File, onInsert: () -> Unit, onRemove: () -> Unit) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(cornerOf(12.dp))
    val thumbPx = with(LocalDensity.current) { STICKER.roundToPx() }
    val thumb by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            ImageDecoder.decodeSampledFile(file.path, thumbPx, thumbPx)?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .size(STICKER)
            .clip(shape)
            .border(1.dp, ink.line, shape)
            .combinedClickable(onClick = onInsert, onLongClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.sticker),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
    }
}

/** Fit the page, its width or its height (Fit height is only here). */
@Composable
private fun FitMenu(editor: Editor, cards: ToolCardState) {
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarItemButton(ToolbarItem.FIT, stringResource(R.string.toolbar_fit), active = cards.open == BarCards.FIT, anchor = anchor) {
            cards.toggle(BarCards.FIT)
        }
        CardSlot(cards, BarCards.FIT, anchor) { dismiss ->
            val once = rememberActOnce(LocalCardExpanded.current)
            InkPopover(expanded = LocalCardExpanded.current, onDismiss = dismiss, anchor = anchor, spec = PopoverSpecs.MoreMenu) {
                BarMenu(IMAGE_MENU_W) {
                    InkMenuRow(stringResource(R.string.fit_page), onClick = { once.run { editor.fitPage(); dismiss() } })
                    InkMenuRow(stringResource(R.string.fit_width), onClick = { once.run { editor.fitWidth(); dismiss() } })
                    InkMenuRow(stringResource(R.string.fit_height), onClick = { once.run { editor.fitHeight(); dismiss() } })
                }
            }
        }
    }
}
