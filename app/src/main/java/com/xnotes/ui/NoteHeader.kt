package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Which header buttons wear the lit fill; the toggles among them (pages, find, bookmark, insert) also take their Fill icon. */
internal data class HeaderLit(
    val pages: Boolean,
    val find: Boolean,
    val bookmark: Boolean,
    val setup: Boolean,
    val view: Boolean,
    val insert: Boolean,
)

/**
 * The capsule's lit states (mockup §1.4): Pages while the side panel is open, on any tab; Find while it is open on
 * the Search tab; Bookmark while this page is marked; Page setup and View while their sheet or card is up (defaults
 * row 2: no lasting on-state); Insert while its card is up or a voice recording runs.
 */
internal fun headerLit(
    panelOpen: Boolean,
    searchTab: Boolean,
    bookmarked: Boolean,
    setupOpen: Boolean,
    viewOpen: Boolean,
    insertOpen: Boolean,
    recording: Boolean,
): HeaderLit = HeaderLit(
    pages = panelOpen,
    find = panelOpen && searchTab,
    bookmark = bookmarked,
    setup = setupOpen,
    view = viewOpen,
    insert = insertOpen || recording,
)

private val ViewCard = CardKey.Named("view")
private val JumpCard = CardKey.Named("jump")
private val WaypointsCard = CardKey.Named("waypoints")
private val StylesCard = CardKey.Named("styles")

/** "7 / 12": the counter role with tabular figures, so the slash stays put as the page changes. */
private val CounterStyle = InkType.counter.tnum()

/**
 * The note's own row above the page (B2 Frame 2): the way back, what the note is called and whether it is saved,
 * then the page counter and one capsule of the things done to the note rather than with the pen, in four groups:
 * Navigate (pages, find, bookmark) | Page (page setup, view) | Add (insert, share) | More. The pen's tools live in
 * the floating bar under it, so the two never compete for the same strip. [recorder] is Part 7's recorder
 * capsule, placed after the title block; nothing passes it yet.
 */
@Composable
internal fun NoteHeader(
    editor: Editor,
    onOpenBackstage: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onImportTemplate: () -> Unit,
    onShare: () -> Unit,
    onClosePane: (() -> Unit)?,
    onInsert: (InsertKind) -> Unit = {},
    onInsertStickyNote: (() -> Unit)? = null,
    onInsertTable: (() -> Unit)? = null,
    recorder: (@Composable () -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    val cards = rememberToolCardState()
    val viewAnchor = remember { PopoverAnchor() }
    val jumpAnchor = remember { PopoverAnchor() }
    // The page index is read only by the counter and the bookmark button, each in its own scope: it changes as the
    // page scrolls past, and read here it recomposed the whole header on every page crossed.
    val recording = editor.media.recordingActive
    // The size picker opened from Insert hangs off the paperclip, so the paperclip stays lit under it (TI 834).
    val pickerUnderClip = editor.tablePickerRequest.let { it != null && it.at == null }
    val lit = headerLit(
        panelOpen = editor.sidebarVisible,
        searchTab = editor.sidePanelTab == SidePanelTab.SEARCH,
        bookmarked = false, // the bookmark button lights itself (BookmarkButton)
        setupOpen = setupOpen,
        viewOpen = cards.open == ViewCard,
        insertOpen = insertOpen || pickerUnderClip,
        recording = recording,
    )
    HeaderBar {
        BackButton(onOpenBackstage)
        val untitled = editor.state.document.displayName == null && editor.state.document.path == null
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderTitle(
                if (untitled) stringResource(R.string.untitled) else editor.title,
                Modifier.weight(1f, fill = false).alignByBaseline(),
            ) { renaming = true }
            SaveStatus(editor, Modifier.alignByBaseline())
        }
        recorder?.invoke()
        HeaderTools(HEADER_TITLE_MIN_NOTE_DP.dp, closePane = onClosePane != null, more = { NoteMoreMenu(editor, onToggleFullscreen, onImportTemplate) }) {
            Box {
                PageCounter(editor, jumpAnchor) { cards.open(JumpCard) }
                CardSlot(cards, JumpCard, jumpAnchor) { dismiss -> PageJumpPopup(editor, dismiss) }
            }
            Capsule {
                CapsuleButton(if (lit.pages) Ph.sidebarSimpleFill else Ph.sidebarSimple, stringResource(R.string.header_pages), lit.pages, toggle = true) {
                    editor.toggleSidebar()
                }
                CapsuleButton(if (lit.find) Ph.magnifyingGlassFill else Ph.magnifyingGlass, stringResource(R.string.header_search_note), lit.find) {
                    editor.sidePanelTab = SidePanelTab.SEARCH
                    if (!editor.sidebarVisible) editor.toggleSidebar()
                }
                BookmarkButton(editor)
                CapsuleSeparator()
                // Drawn ahead of time, so the sheet opens with every template tile already there.
                PrewarmTemplateThumbs(editor, setupOpen)
                Box {
                    CapsuleButton(Ph.fileText, stringResource(R.string.header_page_setup), lit.setup) { setupOpen = true }
                    if (setupOpen) PageSetup(editor, onImportTemplate) { setupOpen = false }
                }
                Box {
                    CapsuleButton(Ph.eye, stringResource(R.string.toolbar_view), lit.view, Modifier.popoverAnchor(viewAnchor)) { cards.open(ViewCard) }
                    CardSlot(cards, ViewCard, viewAnchor) { dismiss -> ViewMenuPopup(editor, dismiss) }
                }
                CapsuleSeparator()
                InsertButton(
                    open = insertOpen,
                    onOpenChange = { insertOpen = it },
                    lit = lit.insert,
                    kinds = NOTE_INSERT_KINDS,
                    recording = recording,
                    onInsert = onInsert,
                    onInsertStickyNote = onInsertStickyNote,
                    onInsertTable = onInsertTable,
                    sharedAnchor = editor.insertAnchor,
                )
                CapsuleButton(Ph.shareNetwork, stringResource(R.string.share), on = false, onClick = onShare)
            }
        }
        if (onClosePane != null) HeaderCloseButton(onClosePane)
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

/** The page setup the header opens. */
@Composable
private fun PageSetup(editor: Editor, onImportTemplate: () -> Unit, onDismiss: () -> Unit) {
    PageSetupSheet(editor, onImportTemplate, onDismiss = onDismiss)
}

/**
 * The note's More menu: the rarer view controls, one tap further away (B2 `.menu`, 8 dp under ⋯). Margins opens
 * Page setup at its margins; Ruler lives in the toolbar's ⋯ only (defaults row 3).
 */
@Composable
private fun NoteMoreMenu(editor: Editor, onToggleFullscreen: () -> Unit, onImportTemplate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var marginsOpen by remember { mutableStateOf(false) }
    val anchor = remember { PopoverAnchor() }
    // Once per open: a double tap on Zoom lock or Full screen must not toggle it back.
    fun pick(action: () -> Unit) {
        if (!open) return
        open = false
        action()
    }
    Box {
        CapsuleButton(Ph.dotsThree, stringResource(R.string.header_more), open, Modifier.popoverAnchor(anchor)) { open = true }
        InkPopover(expanded = open, onDismiss = { open = false }, anchor = anchor, spec = PopoverSpecs.HeaderMenu) {
            HeaderMenuCard {
                InkMenuRow(stringResource(R.string.toolbar_margins), { pick { marginsOpen = true } }, icon = Ph.frameCorners)
                InkMenuRow(stringResource(R.string.fit_page), { pick { editor.fitPage() } }, icon = Ph.rectangle)
                InkMenuRow(stringResource(R.string.fit_width), { pick { editor.fitWidth() } }, icon = Ph.arrowsOutLineHorizontal)
                InkMenuRow(
                    stringResource(R.string.toolbar_zoom_lock),
                    { pick { editor.toggleZoomLock() } },
                    icon = if (editor.zoomLocked) Ph.lockSimple else Ph.lockSimpleOpen,
                    checked = editor.zoomLocked,
                    toggle = true,
                )
                InkMenuRow(stringResource(R.string.toolbar_fullscreen), { pick(onToggleFullscreen) }, icon = Ph.arrowsOut)
            }
        }
        if (marginsOpen) PageSetupSheet(editor, onImportTemplate, startAt = PageSetupStart.MARGINS) { marginsOpen = false }
    }
}

/**
 * The canvas's row above the board, on the same plan as [NoteHeader] (defaults row 15): back, the title (not
 * renamable), then one capsule of Waypoints, Minimap | Styles | Insert (pictures only), Share | More.
 */
@Composable
internal fun CanvasHeader(
    canvas: InfiniteEditor,
    onOpenBackstage: () -> Unit,
    onShare: () -> Unit,
    onClosePane: (() -> Unit)?,
    onInsert: (InsertKind) -> Unit = {},
) {
    var insertOpen by remember { mutableStateOf(false) }
    val cards = rememberToolCardState()
    val waypointsAnchor = remember { PopoverAnchor() }
    val stylesAnchor = remember { PopoverAnchor() }
    HeaderBar {
        BackButton(onOpenBackstage)
        val untitled = canvas.document.displayName == null && canvas.document.path == null
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            HeaderTitle(if (untitled) stringResource(R.string.untitled) else canvas.title, Modifier.weight(1f, fill = false), onClick = null)
        }
        HeaderCapsule(HEADER_TITLE_MIN_CANVAS_DP.dp, closePane = onClosePane != null, more = { CanvasMoreMenu(canvas) }) {
            Box {
                CapsuleButton(Ph.mapPin, stringResource(R.string.toolbar_waypoints), cards.open == WaypointsCard, Modifier.popoverAnchor(waypointsAnchor)) {
                    cards.open(WaypointsCard)
                }
                CardSlot(cards, WaypointsCard, waypointsAnchor) { dismiss -> CanvasWaypointsPopup(canvas, dismiss) }
            }
            CapsuleButton(if (canvas.minimapVisible) Ph.mapTrifoldFill else Ph.mapTrifold, stringResource(R.string.toolbar_minimap), canvas.minimapVisible, toggle = true) {
                canvas.toggleMinimap()
            }
            CapsuleSeparator()
            Box {
                CapsuleButton(Ph.fileText, stringResource(R.string.toolbar_styles), cards.open == StylesCard, Modifier.popoverAnchor(stylesAnchor)) {
                    cards.open(StylesCard)
                }
                CardSlot(cards, StylesCard, stylesAnchor) { dismiss -> CanvasStylesPopup(canvas, dismiss) }
            }
            CapsuleSeparator()
            InsertButton(
                open = insertOpen,
                onOpenChange = { insertOpen = it },
                lit = insertOpen,
                kinds = CANVAS_INSERT_KINDS,
                recording = false,
                onInsert = onInsert,
            )
            CapsuleButton(Ph.shareNetwork, stringResource(R.string.share), on = false, onClick = onShare)
        }
        if (onClosePane != null) HeaderCloseButton(onClosePane)
    }
}

/** The canvas's More menu: Fit all and Zoom lock. */
@Composable
private fun CanvasMoreMenu(canvas: InfiniteEditor) {
    var open by remember { mutableStateOf(false) }
    val anchor = remember { PopoverAnchor() }
    Box {
        CapsuleButton(Ph.dotsThree, stringResource(R.string.header_more), open, Modifier.popoverAnchor(anchor)) { open = true }
        InkPopover(expanded = open, onDismiss = { open = false }, anchor = anchor, spec = PopoverSpecs.HeaderMenu) {
            HeaderMenuCard {
                // Once per open, as the note's menu: `open` is read at the tap.
                InkMenuRow(stringResource(R.string.fit_all), { if (open) { open = false; canvas.zoomToFit() } }, icon = Ph.frameCorners)
                InkMenuRow(
                    stringResource(R.string.toolbar_zoom_lock),
                    { if (open) { open = false; canvas.toggleZoomLock() } },
                    icon = if (canvas.zoomLocked) Ph.lockSimple else Ph.lockSimpleOpen,
                    checked = canvas.zoomLocked,
                    toggle = true,
                )
            }
        }
    }
}

/**
 * The header's Insert button (a paperclip, as Samsung Notes has it) and its card. The card hangs off the
 * paperclip through [LocalCardAnchor], which `InsertMenu`'s `anchor` parameter reads by default (Task 14).
 */
@Composable
private fun InsertButton(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    lit: Boolean,
    kinds: Set<InsertKind>,
    recording: Boolean,
    onInsert: (InsertKind) -> Unit,
    onInsertStickyNote: (() -> Unit)? = null,
    onInsertTable: (() -> Unit)? = null,
    sharedAnchor: PopoverAnchor? = null,
) {
    // The note's paperclip records into its editor's anchor, so the table size picker (TableChrome) opens under it too.
    val ownAnchor = remember { PopoverAnchor() }
    val anchor = sharedAnchor ?: ownAnchor
    Box {
        CapsuleButton(if (lit) Ph.paperclipFill else Ph.paperclip, stringResource(R.string.insert_menu), lit, Modifier.popoverAnchor(anchor)) {
            onOpenChange(true)
        }
        CompositionLocalProvider(LocalCardAnchor provides anchor) {
            InsertMenu(
                expanded = open,
                onDismiss = { onOpenChange(false) },
                kinds = kinds,
                recording = recording,
                onPick = onInsert,
                onInsertStickyNote = onInsertStickyNote,
                onInsertTable = onInsertTable,
            )
        }
    }
}

/** The bar (mockup §1.1): 56 dp on chrome, including its 1 dp line2 hairline; 8 dp in at the start, 12 at the end, items 6 apart. */
@Composable
private fun HeaderBar(content: @Composable RowScope.() -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(ink.chrome)
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(ink.line2, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .padding(start = 8.dp, end = 12.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Back to the library (.ib): 44 dp, caret-left 22, shrinks to .94 while held. */
@Composable
private fun BackButton(onClick: () -> Unit) {
    InkIconButton(Ph.caretLeft, stringResource(R.string.toolbar_home), onClick)
}

/** The split view's Close pane (not in the mockup): a plain 44 dp button after the capsule. */
@Composable
private fun HeaderCloseButton(onClick: () -> Unit) {
    InkIconButton(Ph.x, stringResource(R.string.close_pane), onClick, iconSize = 21.dp)
}

/** The note's name: 17/22 Bold, one line, ellipsised past 360 dp; a 44 dp tap target that renames when [onClick] is set. */
@Composable
private fun HeaderTitle(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val ink = LocalInk.current
    val renameLabel = stringResource(R.string.rename_note)
    Box(
        modifier
            .widthIn(max = 360.dp)
            .heightIn(min = 44.dp)
            .clip(inkRounded(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = renameLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = InkType.headerTitle, color = ink.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Saving… / Edited / ✓ Saved (mockup §1.2, motion sheet d; defaults row 9). The three labels are stacked and
 * composed once, so the box never changes size and a save recomposes nothing: a change cross-fades their alphas
 * over 180 ms and, on the way into Saved, springs the 13 dp tick from .4 to 1, all read in graphicsLayer. The
 * save state and the pen are watched only inside snapshotFlow; while the pen is down the shown label holds
 * (a fade under way snaps to its end) and the change plays at pen-up.
 */
@Composable
private fun SaveStatus(editor: Editor, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val penDown = LocalPenDown.current
    // Read without subscribing: from here on only the effect below follows the editor.
    val first = remember(editor) { Snapshot.withoutReadObservation { saveLabel(editor.savingNote, editor.dirty) } }
    val fades = remember(editor) { SaveLabel.entries.associateWith { Animatable(if (it == first) 1f else 0f) } }
    val tick = remember(editor) { Animatable(1f) }
    // What a screen reader hears; it changes only when the shown label does, never while the pen is down.
    var spoken by remember(editor) { mutableStateOf(first) }
    LaunchedEffect(editor) {
        var shown = first
        var running: Job? = null
        snapshotFlow { saveLabel(editor.savingNote, editor.dirty) to penDown() }.collect { (latest, down) ->
            if (down) {
                if (running?.isActive == true) {
                    running?.cancel()
                    SaveLabel.entries.forEach { fades.getValue(it).snapTo(if (it == shown) 1f else 0f) }
                    tick.snapTo(1f)
                }
                return@collect
            }
            val next = nextShownLabel(shown, latest, penDown = false)
            if (next == shown) return@collect
            val from = shown
            shown = next
            spoken = next
            running?.cancel()
            running = launch {
                SaveLabel.entries.forEach { l ->
                    launch { fades.getValue(l).animateTo(if (l == next) 1f else 0f, tween(InkMotion.BASE, easing = InkMotion.Standard)) }
                }
                if (playsSavedCheck(from, next)) {
                    launch {
                        tick.snapTo(0.4f)
                        tick.animateTo(1f, InkMotion.check())
                    }
                }
            }
        }
    }
    val saving = stringResource(R.string.header_saving)
    val edited = stringResource(R.string.header_edited)
    val saved = stringResource(R.string.header_saved)
    val said = when (spoken) {
        SaveLabel.SAVING -> saving
        SaveLabel.EDITED -> edited
        SaveLabel.SAVED -> saved
    }
    Box(modifier.clearAndSetSemantics { contentDescription = said }) {
        Text(
            saving,
            style = InkType.meta,
            color = ink.text2,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { alpha = fades.getValue(SaveLabel.SAVING).value },
        )
        Text(
            edited,
            style = InkType.meta,
            color = ink.text2,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { alpha = fades.getValue(SaveLabel.EDITED).value },
        )
        Row(
            Modifier.graphicsLayer { alpha = fades.getValue(SaveLabel.SAVED).value },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Ph.check,
                contentDescription = null,
                tint = ink.text2,
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer {
                        scaleX = tick.value
                        scaleY = tick.value
                    },
            )
            Text(saved, style = InkType.meta, color = ink.text2, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The capsule's Bookmark: lit while this page is marked (headerLit's bookmark state), a tap marks or unmarks it. It reads
 * the page index and the bookmarks itself, so a page crossed while scrolling recomposes this button, not the header.
 */
@Composable
private fun BookmarkButton(editor: Editor) {
    // Read so the icon follows the list as it changes; the list itself is not snapshot state.
    @Suppress("UNUSED_VARIABLE") val version = editor.bookmarkVersion
    val page = editor.pageIndex
    val marked = editor.bookmarks.indexOfFirst { it.page == page }
    val lit = marked >= 0
    val pageLabel = stringResource(R.string.page_n, page + 1)
    CapsuleButton(
        if (lit) Ph.bookmarkSimpleFill else Ph.bookmarkSimple,
        stringResource(if (lit) R.string.header_remove_bookmark else R.string.header_bookmark_page),
        lit,
        toggle = true,
    ) {
        if (marked >= 0) editor.removeBookmark(marked) else editor.addBookmark(pageLabel)
    }
}

/** "7 / 12" (13 SemiBold, tabular, text2) in a 44 dp target; a tap opens Go to page under it. */
@Composable
private fun PageCounter(editor: Editor, anchor: PopoverAnchor, onClick: () -> Unit) {
    val ink = LocalInk.current
    // Read here, not in NoteHeader: a page crossed while scrolling recomposes this counter alone.
    val index = editor.pageIndex
    val count = editor.pageCount
    Box(
        Modifier
            .popoverAnchor(anchor)
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(inkRounded(8.dp))
            .clickable(onClickLabel = stringResource(R.string.title_go_to_page), role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.header_page_of, index + 1, count), style = CounterStyle, color = ink.text2, maxLines = 1, softWrap = false)
    }
}

/**
 * Where the counter and the capsule sit in the bar (review I3): as wide as they are, but never wider than
 * [headerToolsRoom] leaves once the title has its least ([titleMin]) and, with [closePane], Close pane its 44 dp. The
 * Row lays its unweighted items out in order, so without this cap a wide capsule took Close pane's room in a narrow
 * split pane. When the cap bites, [content] (the counter, 14 dp, then the capsule's tools) scrolls sideways under the
 * capsule's end, which stays put with the separator and [more] (⋯): every tool is a swipe away, ⋯ never moves. The
 * tools fade out where they run under it ([scrollEdgeFade]), so a half-shown icon never reads as a broken sliver.
 */
@Composable
private fun HeaderTools(titleMin: Dp, closePane: Boolean, more: @Composable () -> Unit, content: @Composable RowScope.() -> Unit) {
    val ink = LocalInk.current
    val scroll = rememberScrollState()
    Row(
        Modifier.layout { measurable, constraints ->
            val max = if (!constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                val close = if (closePane) HEADER_BUTTON_DP.dp.roundToPx() else null
                headerToolsRoom(
                    constraints.maxWidth, titleMin.roundToPx(), HEADER_GAP_DP.dp.roundToPx(), close, HEADER_CAPSULE_END_DP.dp.roundToPx(),
                )
            }
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = max))
            layout(constraints.constrainWidth(placeable.width), placeable.height) { placeable.place(0, 0) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false).scrollEdgeFade(scroll, ink.chrome).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        CapsuleEnd(more)
    }
}

/** How far in from a scrolling edge the header's tools fade to the capsule's fill. */
private val HEADER_EDGE_FADE = 28.dp

/**
 * Where [scroll]'s content runs on past the viewport's end (or has scrolled off its start), it fades into [fill] over
 * [HEADER_EDGE_FADE], solid for the last quarter, instead of stopping at a hard clip mid-icon. The fade is [fill] drawn
 * over the content, held 1 dp in from the top and bottom so the capsule's outline runs on unbroken. Only the scroll
 * position is read here, in the draw phase, so scrolling redraws and never recomposes.
 */
private fun Modifier.scrollEdgeFade(scroll: ScrollState, fill: Color): Modifier = drawWithContent {
    drawContent()
    val fade = HEADER_EDGE_FADE.toPx().coerceAtMost(size.width / 2f)
    val inset = 1.dp.toPx()
    val h = size.height - 2 * inset
    if (fade <= 0f || h <= 0f) return@drawWithContent
    val ltr = layoutDirection == LayoutDirection.Ltr
    fun edge(right: Boolean) {
        val left = if (right) size.width - fade else 0f
        val stops = if (right) {
            arrayOf(0f to Color.Transparent, 0.75f to fill, 1f to fill)
        } else {
            arrayOf(0f to fill, 0.25f to fill, 1f to Color.Transparent)
        }
        drawRect(
            Brush.horizontalGradient(*stops, startX = left, endX = left + fade),
            topLeft = Offset(left, inset),
            size = Size(fade, h),
        )
    }
    // The end the tools run on towards is the right in LTR, the left in RTL.
    if (scroll.canScrollForward) edge(right = ltr)
    if (scroll.canScrollBackward) edge(right = !ltr)
}

/** [HeaderTools] for a header with no counter: the capsule's tools, then its pinned end with [more]. */
@Composable
private fun HeaderCapsule(titleMin: Dp, closePane: Boolean, more: @Composable () -> Unit, content: @Composable RowScope.() -> Unit) {
    HeaderTools(titleMin, closePane, more) { Capsule(content) }
}

/**
 * The capsule (mockup §1.3): a 48 dp pill on chrome with a 1 dp line border and 2 dp of padding at each end, drawn
 * in two halves so its tools can scroll. This is the start, holding the tools; [CapsuleEnd] closes it.
 */
@Composable
private fun Capsule(content: @Composable RowScope.() -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .height(48.dp)
            .capsuleHalf(ink.chrome, ink.line, start = true)
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The capsule's end, which never scrolls: the separator before More, [more], and the 2 dp end padding. */
@Composable
private fun CapsuleEnd(more: @Composable () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .height(48.dp)
            .capsuleHalf(ink.chrome, ink.line, start = false)
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleSeparator()
        more()
    }
}

/**
 * One half of the capsule's pill: filled with [fill] and outlined 1 dp in [line], rounded at its [start] or its end
 * and open at the other, where the two halves meet (or the start half runs on under the end while it scrolls).
 */
private fun Modifier.capsuleHalf(fill: Color, line: Color, start: Boolean): Modifier = drawWithCache {
    val w = 1.dp.toPx()
    val h = size.height
    val r = h / 2f
    // The start half rounds its left side in LTR, its right side in RTL.
    val roundLeft = start == (layoutDirection == LayoutDirection.Ltr)
    val corner = CornerRadius(r)
    val body = Path().apply {
        addRoundRect(
            if (roundLeft) {
                RoundRect(0f, 0f, size.width, h, topLeftCornerRadius = corner, bottomLeftCornerRadius = corner)
            } else {
                RoundRect(0f, 0f, size.width, h, topRightCornerRadius = corner, bottomRightCornerRadius = corner)
            },
        )
    }
    val top = w / 2f
    val bottom = h - w / 2f
    val outline = Path().apply {
        if (roundLeft) {
            moveTo(size.width, top)
            lineTo(r, top)
            arcTo(Rect(top, top, h - top, bottom), 270f, -180f, false)
            lineTo(size.width, bottom)
        } else {
            moveTo(0f, top)
            lineTo(size.width - r, top)
            arcTo(Rect(size.width - h + top, top, size.width - top, bottom), 270f, 180f, false)
            lineTo(0f, bottom)
        }
    }
    val stroke = Stroke(w)
    onDrawBehind {
        drawPath(body, fill)
        drawPath(outline, line, style = stroke)
    }
}

/**
 * A capsule button (.cb): 44 dp, 21 dp icon, .94 while held; [on] wears the lit grey (`ink.sel`) and reads as selected,
 * or with [toggle] (Pages, Bookmark, Minimap) as a switch that is on or off.
 */
@Composable
private fun CapsuleButton(icon: ImageVector, description: String, on: Boolean, modifier: Modifier = Modifier, toggle: Boolean = false, onClick: () -> Unit) {
    InkIconButton(icon, description, onClick, modifier, on = on, iconSize = 21.dp, toggle = toggle)
}

/** The rule between two capsule groups (.csep): 1 × 20 dp in line, 4 dp each side. */
@Composable
private fun CapsuleSeparator() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 20.dp).background(LocalInk.current.line))
}

/** The header menus' card (B2 `.menu`): 260 dp, raised, r16, the line2 ring and the MENU shadow. */
@Composable
private fun HeaderMenuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(260.dp)
            .inkSurface(RoundedCornerShape(16.dp), InkElevation.MENU)
            .padding(top = 8.dp, bottom = 6.dp),
        content = content,
    )
}

/**
 * Renames the open note: the B2 dialog with the kit's text field, opened with the name selected; the ".xnote"
 * suffix is implicit. A blank name cancels. Also used by the toolbar's TITLE item.
 */
@Composable
internal fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = stringResource(R.string.rename_note)
    fun confirm() {
        if (value.text.isBlank()) onDismiss() else onConfirm(value.text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            InkTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.widthIn(max = 400.dp).semantics { contentDescription = title },
                focusRequester = focus,
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = { InkStrongButton(stringResource(R.string.rename), onClick = { confirm() }) },
        dismissButton = { InkSecondaryButton(stringResource(R.string.cancel), onClick = onDismiss) },
    )
}
