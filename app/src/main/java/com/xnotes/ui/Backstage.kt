package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.platform.PdfOpenError
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.ExplorerLayout
import com.xnotes.settings.FolderPlacement
import com.xnotes.settings.GroupBy
import com.xnotes.settings.TileSize
import com.xnotes.settings.ThumbShape
import com.xnotes.core.util.DocumentKind
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.PinnedFolder
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkEmptyState
import com.xnotes.ui.kit.InkBrandButton
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/** Which pane the backstage shows on the right. */
enum class BackstageView { HOME, PREFERENCES, ABOUT, TRASH }

/** Whether the Home explorer is awaiting a new file/folder name; a root folder lands at the top of the library, wherever it stands. */
private enum class CreateMode { NONE, FILE, CANVAS, FOLDER, ROOT_FOLDER }

/** Entries copied or cut in the explorer; each remembers the folder it was listed in. */
private data class ClipItem(val entries: List<BrowseEntry>, val isCut: Boolean)

/** The stem (no extension) offered for a fresh note in [entries], from the filename template. */
private fun nextUntitled(editor: Editor, entries: List<BrowseEntry>?): String =
    editor.newNoteStem(entries.orEmpty().filter { !it.isDir }.map { it.name.lowercase() }.toSet())

/**
 * The full-screen "File" area (the home screen): an in-app file explorer rooted at a folder the user
 * granted, beside a navigation sidebar. On wide screens the sidebar collapses into an icon rail; on
 * phones it is a slide-over drawer. Creating things lives in the explorer's create button.
 */
@Composable
fun Backstage(
    editor: Editor,
    view: BackstageView,
    onSelectView: (BackstageView) -> Unit,
    onImportPdf: () -> Unit,
    onOpenFile: (String) -> Unit,
    onPickRoot: () -> Unit,
    onShareFile: (String) -> Unit,
    onSaveCopyFile: (String) -> Unit,
    onExportFilePdf: (String) -> Unit,
    /** Home is the app's root: back from here leaves the app rather than dropping into the editor. */
    onExitApp: () -> Unit,
    /** Preferences asked to import a Helix code theme. */
    onImportCodeTheme: () -> Unit = {},
    /** Preferences asked to import a font file. */
    onImportFont: () -> Unit = {},
    /** Two picked files are to be opened together, one per pane of a split view. */
    onOpenSplit: (String, String) -> Unit = { _, _ -> },
    /** Several files are to be shared at once, as they are. */
    onShareFiles: (List<String>) -> Unit = {},
    /** Opens a file beside the note last open; null when there's no note to pair it with. */
    onOpenBeside: (String) -> (() -> Unit)? = { null },
) {
    // Below this width the sidebar becomes a slide-over drawer instead of a persistent pane.
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
    // A folder is required to import into; without one, send the user to pick a folder first.
    val calls = ExplorerCalls(
        openFile = onOpenFile,
        pickRoot = onPickRoot,
        importPdf = { if (editor.browseRoot != null) onImportPdf() else onPickRoot() },
        shareFile = onShareFile,
        shareFiles = onShareFiles,
        saveCopyFile = onSaveCopyFile,
        exportFilePdf = onExportFilePdf,
        openSplit = onOpenSplit,
        openBeside = onOpenBeside,
    )
    // The backstage is the root of the stack — ordinary base content, not a dialog. The activity
    // window already runs edge-to-edge with the system bars hidden (MainActivity.applyFullscreen).
    BackstageContent(editor, compact, view, onSelectView, calls, onExitApp, onImportCodeTheme, onImportFont)
}

/** Width at or above which the sidebar is a persistent pane rather than a drawer. */
private const val COMPACT_WIDTH_DP = 600

/**
 * The home-first layout: the library (or Preferences) fills the screen beside the sidebar. Wide
 * screens show either the full sidebar or its rail, remembered across launches; phones slide the
 * sidebar over the library from a hamburger.
 */
@Composable
private fun BackstageContent(
    editor: Editor,
    compact: Boolean,
    view: BackstageView,
    onSelectView: (BackstageView) -> Unit,
    calls: ExplorerCalls,
    onExitApp: () -> Unit,
    onImportCodeTheme: () -> Unit,
    onImportFont: () -> Unit,
) {
    var createMode by remember { mutableStateOf(CreateMode.NONE) }
    var drawerOpen by remember { mutableStateOf(false) }
    val railed = editor.backstageRail
    val prefs = remember(editor.prefsVersion) { editor.preferences }
    // Close animates only on a true dismiss (scrim, back); a command swaps the pane already composed
    // underneath, so it closes instantly.
    var animateClose by remember { mutableStateOf(true) }
    val dismissDrawer = { animateClose = true; drawerOpen = false }
    val setRailed: (Boolean) -> Unit = { editor.showRail(it) }
    val marks = rememberLibraryMarks()
    val root = editor.browseRoot
    // The whole library at once, for All notes and the sidebar's counts. Walked again whenever it may have
    // changed: a note closed, the tree touched elsewhere, or the explorer reporting a change of its own.
    val feed = remember(root) { LibraryFeed() }
    LaunchedEffect(feed, editor.treeVersion, editor.noteOpen, feed.tick) {
        if (root == null || editor.noteOpen) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { walkLibrary(editor, root) }.getOrNull() }?.let { feed.index = it }
    }

    val selectView: (BackstageView) -> Unit = { v ->
        if (v == BackstageView.HOME) createMode = CreateMode.NONE
        onSelectView(v)
        animateClose = false
        drawerOpen = false
    }
    // A sidebar pick the explorer has yet to act on, and the folder or filter it is showing.
    var explorerNav by remember { mutableStateOf<ExplorerNav?>(null) }
    var explorerFolder by remember { mutableStateOf<String?>(null) }
    var explorerColor by remember { mutableStateOf<Rgba?>(null) }
    var explorerRecent by remember { mutableStateOf(false) }
    var explorerFavourites by remember { mutableStateOf(false) }
    val link = remember(explorerNav) {
        ExplorerLink(explorerNav, { explorerNav = null }) { folder, color, recent, favourites ->
            explorerFolder = folder; explorerColor = color; explorerRecent = recent; explorerFavourites = favourites
        }
    }
    val scope = rememberCoroutineScope()
    var renamingColor by remember { mutableStateOf<Rgba?>(null) }
    // Colour names live in the notes folder, so pick up another device's edits whenever Home comes back.
    LaunchedEffect(editor.browseRoot, editor.noteOpen) {
        // Not while a note opens over Home: it is read again as Home comes back, and nothing shows them meanwhile.
        if (editor.noteOpen && editor.colorNames.isNotEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) { editor.loadColorNames() }
    }
    val colors = editor.colorNames.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
    val onHome = view == BackstageView.HOME
    val activeColor = if (onHome) explorerColor else null
    val recentActive = onHome && explorerRecent
    val favouritesActive = onHome && explorerFavourites
    val filtered = activeColor != null || recentActive || favouritesActive
    val index = feed.index
    val pins = if (prefs.sidebarPinned) editor.sidebarPins else emptyList()
    // A pinned folder at the top already stands among the folders; only deeper ones get rows of their own.
    val extraPins = remember(pins, index) {
        val tops = index?.folders.orEmpty().map { it.docId }.toSet()
        pins.filter { pin -> runCatching { editor.browseDocId(pin.uri) }.getOrNull() !in tops }
    }
    val activePin = if (!onHome || explorerFolder == null || filtered) -1
    else extraPins.indexOfFirst { runCatching { editor.browseDocId(it.uri) }.getOrNull() == explorerFolder }
    // A folder deep in the tree lights up the top-level folder it sits in.
    val activeFolder = if (!onHome || filtered || activePin >= 0) null else index?.topFolderOf(explorerFolder)
    val shownColors = if (prefs.sidebarColours) colors else emptyList()
    // With Trash off it leaves the sidebar, once whatever was already in it is restored or emptied.
    val trashCount = if (prefs.sidebarTrash && root != null && (prefs.trashDays != 0 || editor.trashCount > 0)) editor.trashCount else -1
    LaunchedEffect(editor.browseRoot) { editor.browseRoot?.let { r -> withContext(Dispatchers.IO) { editor.purgeExpiredTrash(r, prefs.trashDays) } } }
    val showRecent = prefs.sidebarRecent && root != null
    val favouriteCount = remember(index, marks.favourites) { index?.files?.count { marks.isFavourite(it.documentUri) } ?: 0 }
    // Kept while its inputs hold, so folding the sidebar never recomposes the rail or the sidebar.
    val nav = remember(
        view, root, index, prefs, shownColors, activeColor, extraPins, activePin, activeFolder, trashCount, showRecent,
        recentActive, favouritesActive, favouriteCount, explorerFolder,
    ) {
        SidebarNav(
            view = view,
            library = root != null,
            allSelected = onHome && explorerFolder == null && !filtered,
            allCount = index?.files?.size,
            onAll = { selectView(BackstageView.HOME); explorerNav = ExplorerNav(null) },
            favouritesSelected = favouritesActive,
            favouriteCount = favouriteCount,
            onFavourites = { selectView(BackstageView.HOME); explorerNav = ExplorerNav(null, favourites = true) },
            recent = if (!showRecent) null else recentActive,
            onRecent = { selectView(BackstageView.HOME); explorerNav = ExplorerNav(null, recent = true) },
            folders = index?.folders.orEmpty(),
            activeFolder = activeFolder,
            onOpenFolder = { uri -> selectView(BackstageView.HOME); explorerNav = ExplorerNav(uri) },
            onNewFolder = { selectView(BackstageView.HOME); createMode = CreateMode.ROOT_FOLDER },
            tags = prefs.sidebarColours,
            colors = shownColors,
            colorCounts = index?.files.orEmpty().mapNotNull { it.color }.groupingBy { it }.eachCount(),
            activeColor = activeColor,
            pins = extraPins,
            activePin = activePin,
            trashCount = trashCount,
            onSelectView = selectView,
            onOpenColor = { selectView(BackstageView.HOME); explorerNav = ExplorerNav(null, it) },
            onRenameColor = { renamingColor = it },
            onForgetColor = { c -> scope.launch { withContext(Dispatchers.IO) { editor.setColorName(c, null) } } },
            onOpenPin = { selectView(BackstageView.HOME); explorerNav = ExplorerNav(it.uri) },
            onUnpin = { editor.unpinFolder(it.uri) },
        )
    }
    renamingColor?.let { c -> ColorNameDialog(editor, c) { renamingColor = null } }

    // Home is the app's root, so it owns every back press while it's up (the editor sits
    // underneath in the same activity — letting the dialog dismiss would just bounce back to
    // it, and the editor's own handler would re-open Home: an endless loop). Back peels off
    // one layer at a time — drawer, Preferences, an in-progress create — and once at the bare
    // Home screen it leaves the app instead. A deeper explorer folder is popped first by the
    // explorer's own (more-nested) handler before this one ever sees the press.
    BackHandler {
        when {
            compact && drawerOpen -> dismissDrawer()
            // Preferences, About and Trash are sub-pages of Home: back lands on Home rather than leaving the app.
            view != BackstageView.HOME -> selectView(BackstageView.HOME)
            createMode != CreateMode.NONE -> createMode = CreateMode.NONE
            else -> onExitApp()
        }
    }

    val ink = LocalInk.current
    // An open note covers Home whole (its panes are opaque and fill the same box), so Home is not drawn beneath it: the
    // shelf's covers and shadows would otherwise be drawn again under every frame the editor's chrome redraws. Only the
    // layer's alpha is read, while drawing, so opening or closing a note recomposes nothing here.
    val underNote = remember(editor) { Modifier.graphicsLayer { alpha = if (editor.noteOpen || editor.secondary?.noteOpen == true) 0f else 1f } }
    if (compact) {
        Box(Modifier.fillMaxSize().then(underNote).background(ink.bg)) {
            BackstageMain(
                Modifier.fillMaxSize(), editor, view, compact, drawerOpen, { animateClose = true; drawerOpen = true }, { selectView(BackstageView.HOME) },
                calls, createMode, { createMode = it }, onImportCodeTheme, onImportFont, link, { selectView(BackstageView.PREFERENCES) },
                feed, marks,
            )
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(InkMotion.fade()),
                exit = if (animateClose) fadeOut(InkMotion.fade()) else ExitTransition.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize().background(ink.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismissDrawer() })
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(InkMotion.screen()) { -it },
                exit = if (animateClose) slideOutHorizontally(InkMotion.screen()) { -it } else ExitTransition.None,
            ) {
                LibrarySidebar(Modifier.width(DRAWER_WIDTH), nav, onCollapse = dismissDrawer)
            }
        }
    } else {
        // The pane takes its final room at once in both directions, so the shelf lays out once per fold. Only the
        // sidebar's own width animates (280 ms, Emphasized), and it measures its content at full size, so nothing inside relayouts.
        val width = animateDpAsState(if (railed) LIBRARY_RAIL_WIDTH else SIDEBAR_WIDTH, InkMotion.screen(), label = "sidebarWidth")
        Box(Modifier.fillMaxSize().then(underNote).background(ink.bg)) {
            BackstageMain(
                Modifier.fillMaxSize().padding(start = if (railed) LIBRARY_RAIL_WIDTH else SIDEBAR_WIDTH),
                editor, view, compact, true, { setRailed(false) }, { selectView(BackstageView.HOME) },
                calls, createMode, { createMode = it }, onImportCodeTheme, onImportFont, link, { selectView(BackstageView.PREFERENCES) },
                feed, marks,
            )
            // Both stay composed so a fold never pays to build one; only the current one is measured and placed.
            Layout(
                content = {
                    LibraryRail(Modifier.width(LIBRARY_RAIL_WIDTH), nav) { setRailed(false) }
                    LibrarySidebar(Modifier.width(SIDEBAR_WIDTH), nav) { setRailed(true) }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val w = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    }
                    .background(ink.sidebar)
                    .clipToBounds(),
            ) { measurables, constraints ->
                val shown = measurables[if (railed) 0 else 1].measure(Constraints(maxHeight = constraints.maxHeight, minHeight = constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { shown.place(0, 0) }
            }
        }
    }
}

/** A sidebar pick for the explorer: a folder, a colour to filter the whole tree by, Recent, Favourites, or none of them for All notes. */
private class ExplorerNav(val folderUri: String?, val color: Rgba? = null, val recent: Boolean = false, val favourites: Boolean = false)

/** The sidebar's line into the explorer: a pick to act on, and where the explorer reports what it shows. */
private class ExplorerLink(
    val nav: ExplorerNav?,
    val onNavHandled: () -> Unit,
    val onPlace: (folderDocId: String?, color: Rgba?, recent: Boolean, favourites: Boolean) -> Unit,
)

/** What the sidebar and its rail show and do, shared so the two never drift apart. */
internal class SidebarNav(
    val view: BackstageView,
    /** Whether there is a library folder at all; without one only the fixed destinations show. */
    val library: Boolean,
    val allSelected: Boolean,
    /** How many notes the whole library holds, or null until it has been counted. */
    val allCount: Int?,
    val onAll: () -> Unit,
    val favouritesSelected: Boolean,
    val favouriteCount: Int,
    val onFavourites: () -> Unit,
    /** Whether Recent is showing; null leaves Recent out of the sidebar. */
    val recent: Boolean?,
    val onRecent: () -> Unit,
    /** The library's top-level folders, by name. */
    val folders: List<LibraryFolder>,
    /** Document id of the top-level folder the explorer is in, or null. */
    val activeFolder: String?,
    val onOpenFolder: (String) -> Unit,
    val onNewFolder: () -> Unit,
    /** Whether the Tags block shows at all. */
    val tags: Boolean,
    /** Named colours only, by name; unnamed ones never reach the sidebar. */
    val colors: List<Pair<Rgba, String>>,
    /** How many notes carry each colour, anywhere in the library. */
    val colorCounts: Map<Rgba, Int>,
    val activeColor: Rgba?,
    /** Pinned folders below the top level (those at the top stand among [folders]). */
    val pins: List<PinnedFolder>,
    /** Index into [pins] of the folder the explorer is showing, or -1. */
    val activePin: Int,
    /** How many items wait in Trash, or -1 to leave Trash out of the sidebar. */
    val trashCount: Int,
    val onSelectView: (BackstageView) -> Unit,
    val onOpenColor: (Rgba) -> Unit,
    val onRenameColor: (Rgba) -> Unit,
    val onForgetColor: (Rgba) -> Unit,
    val onOpenPin: (PinnedFolder) -> Unit,
    val onUnpin: (PinnedFolder) -> Unit,
)

/** The main pane (the library, or Preferences); shows a hamburger when the sidebar is hidden. */
@Composable
private fun BackstageMain(
    modifier: Modifier,
    editor: Editor,
    view: BackstageView,
    compact: Boolean,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onBackToHome: () -> Unit,
    calls: ExplorerCalls,
    createMode: CreateMode,
    onCreateMode: (CreateMode) -> Unit,
    onImportCodeTheme: () -> Unit,
    onImportFont: () -> Unit,
    link: ExplorerLink,
    onOpenPreferences: () -> Unit,
    feed: LibraryFeed,
    marks: LibraryMarks,
) {
    Column(modifier) {
        // About's slim top bar (constant height so toggling the sidebar never shifts it) holds the
        // same leading control as Home/Preferences: a Back arrow to Home on compact, else a hamburger.
        if (view == BackstageView.ABOUT) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 6.dp, end = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (compact) {
                    InkIconButton(Ph.caretLeft, stringResource(R.string.back_to_home), onBackToHome)
                } else if (!sidebarOpen) {
                    InkIconButton(Ph.sidebarSimple, stringResource(R.string.show_sidebar), onShowSidebar)
                }
            }
        }
        // The library pads itself (its header, tabs and shelf); the other panes keep their margins.
        val pad = if (view == BackstageView.HOME) Modifier else Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
        Box(Modifier.weight(1f).fillMaxWidth().then(pad)) {
            when (view) {
                BackstageView.HOME -> HomePane(editor, calls, createMode, onCreateMode, sidebarOpen, onShowSidebar, link, feed, marks)
                BackstageView.PREFERENCES -> PreferencesPane(editor, compact, sidebarOpen, onShowSidebar, onBackToHome, onImportCodeTheme, onImportFont, calls.pickRoot)
                BackstageView.TRASH -> TrashPane(editor, sidebarOpen, onShowSidebar, onOpenPreferences)
                BackstageView.ABOUT -> AboutPane()
            }
        }
    }
}

/** Names a colour for every folder; a blank name forgets it, which also drops it from the sidebar. */
@Composable
internal fun ColorNameDialog(editor: Editor, color: Rgba, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    NameDialog(
        title = stringResource(R.string.name_this_colour),
        initial = editor.colorNames[color].orEmpty(),
        confirmLabel = stringResource(R.string.save),
        placeholder = stringResource(R.string.colour_name_placeholder),
        allowEmpty = true,
        onConfirm = { name -> scope.launch { withContext(Dispatchers.IO) { editor.setColorName(color, name) }; onDone() } },
        onDismiss = onDone,
        leadingColor = codeTint(color, LocalPalette.current),
        note = stringResource(R.string.colour_name_note),
        onRemove = if (editor.colorNames[color] == null) null else {
            { scope.launch { withContext(Dispatchers.IO) { editor.setColorName(color, null) }; onDone() } }
        },
    )
}

// --- home pane: the folder explorer ---

/** The things the library can add to the folder it stands in: the New button's menu. */
@Composable
private fun NewItemMenuItems(onClose: () -> Unit, onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit) {
    InkMenuRow(stringResource(R.string.new_note_menu), { onClose(); onCreateMode(CreateMode.FILE) }, icon = Ph.notebook)
    InkMenuRow(stringResource(R.string.new_canvas_menu), { onClose(); onCreateMode(CreateMode.CANVAS) }, icon = Ph.infinity)
    InkMenuRow(stringResource(R.string.import_pdf), { onClose(); onImportPdf() }, icon = Ph.filePdf)
    InkMenuRow(stringResource(R.string.new_folder_menu), { onClose(); onCreateMode(CreateMode.FOLDER) }, icon = Ph.folderSimplePlus)
}

/** What the explorer hands back to the activity: opening, sharing and exporting files it can't do itself. */
private class ExplorerCalls(
    val openFile: (String) -> Unit,
    val pickRoot: () -> Unit,
    val importPdf: () -> Unit,
    val shareFile: (String) -> Unit,
    val shareFiles: (List<String>) -> Unit,
    val saveCopyFile: (String) -> Unit,
    val exportFilePdf: (String) -> Unit,
    val openSplit: (String, String) -> Unit,
    /** Opens a file beside the note last open, or null when there is none to pair it with. */
    val openBeside: (String) -> (() -> Unit)?,
)

@Composable
private fun HomePane(
    editor: Editor,
    calls: ExplorerCalls,
    createMode: CreateMode,
    onCreateMode: (CreateMode) -> Unit,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    link: ExplorerLink,
    feed: LibraryFeed,
    marks: LibraryMarks,
) {
    val focusManager = LocalFocusManager.current
    // A tap on empty space anywhere in the pane drops focus from the search field, dismissing it
    // (children like tiles and buttons consume their own taps, so this only fires "outside").
    // Making things lives in the title row's New button, so the pane carries no floating one.
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        ExplorerSection(editor, calls, createMode, onCreateMode, sidebarOpen, onShowSidebar, link, feed, marks)
    }
}

// --- explorer section ---

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ExplorerSection(
    editor: Editor,
    calls: ExplorerCalls,
    createMode: CreateMode,
    onCreateMode: (CreateMode) -> Unit,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    link: ExplorerLink,
    feed: LibraryFeed,
    marks: LibraryMarks,
) {
    val palette = LocalPalette.current
    val root = editor.browseRoot
    val nav = link.nav
    if (root == null) {
        if (nav != null) LaunchedEffect(nav) { link.onNavHandled() }
        val ink = LocalInk.current
        Column(Modifier.fillMaxSize()) {
            if (!sidebarOpen) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkIconButton(Ph.sidebarSimple, stringResource(R.string.show_sidebar), onShowSidebar)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 46.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    EmptyIllustration(EmptyArt.PAGES, Modifier.padding(bottom = 6.dp).size(300.dp, 225.dp))
                    Text(stringResource(R.string.first_run_title), style = InkType.display, color = ink.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 540.dp))
                    Text(stringResource(R.string.first_run_body), style = FirstRunBody, color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 470.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InkStrongButton(stringResource(R.string.choose_folder), calls.pickRoot, icon = Ph.folderSimple)
                        InkSecondaryButton(stringResource(R.string.use_app_storage), { editor.useInternalStorage() }, Modifier.height(48.dp), icon = Ph.database)
                    }
                    // P2-15: the footnote is text2, not the placeholder-only text3.
                    Text(stringResource(R.string.first_run_later), style = InkType.meta, color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val words = rememberExplorerWords()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactScreen = screenWidthDp < COMPACT_WIDTH_DP
    val prefs = remember(editor.prefsVersion) { editor.preferences }
    val rootDocId = remember(root) { editor.browseRootDocId(root) }
    val stack = remember(root) { mutableStateListOf<Pair<String, String>>() }
    val currentDocId = if (stack.isEmpty()) rootDocId else stack.last().first
    var refreshKey by remember(root) { mutableIntStateOf(0) }
    var fieldError by remember(root) { mutableStateOf<String?>(null) }
    var renaming by remember(root) { mutableStateOf<BrowseEntry?>(null) }
    val selection = remember(root) { mutableStateListOf<BrowseEntry>() }
    var clipboard by remember(root) { mutableStateOf<ClipItem?>(null) }
    var pendingDelete by remember(root) { mutableStateOf<List<BrowseEntry>?>(null) }
    var moving by remember(root) { mutableStateOf<List<BrowseEntry>?>(null) }
    var previewing by remember(root) { mutableStateOf<BrowseEntry?>(null) }
    var opError by remember(root) { mutableStateOf<String?>(null) }
    // Set from the sidebar: the whole tree's items carrying this colour, shown in place of the folder.
    var colorFilter by remember(root) { mutableStateOf<Rgba?>(null) }
    var kindFilter by remember(root) { mutableStateOf<EntryKind?>(null) }
    // The tab moves at once; the shelf fades out over 120 ms, swaps, and fades back in (Frame 1's grid opacity).
    var pickedKind by remember(root) { mutableStateOf<EntryKind?>(null) }
    val kindFade = remember { Animatable(1f) }
    LaunchedEffect(pickedKind) {
        if (pickedKind != kindFilter) {
            kindFade.animateTo(0f, tween(InkMotion.FAST, easing = InkMotion.Standard))
            kindFilter = pickedKind
        }
        // Also when a pick returns to the shown kind mid-fade: the shelf comes back rather than staying dimmed.
        kindFade.animateTo(1f, tween(InkMotion.FAST, easing = InkMotion.Standard))
    }
    val fadeNow: () -> Float = remember(kindFade) { { kindFade.value } }
    // Set from the sidebar: what was opened lately, from every folder, in place of the folder.
    var showRecent by remember(root) { mutableStateOf(false) }
    // Set from the sidebar: every starred note, from every folder, in place of the folder.
    var showFavourites by remember(root) { mutableStateOf(false) }
    // Picking things without a first long-press: the title row's Select button, until the selection ends.
    var selectMode by remember(root) { mutableStateOf(false) }
    var includeSubfolders by remember(root) { mutableStateOf(true) }
    var timelineMonth by remember(root) { mutableStateOf<YearMonth?>(null) }
    var columnsPick by remember(root) { mutableStateOf<BrowseEntry?>(null) }
    var namingColor by remember(root) { mutableStateOf<Rgba?>(null) }
    var metaTick by remember(root) { mutableIntStateOf(0) }
    fun clearUp() { selection.clear(); selectMode = false; opError = null; columnsPick = null }
    // A sidebar pick either filters by a colour or replaces the path with a folder's chain from the root.
    LaunchedEffect(nav) {
        val target = nav ?: return@LaunchedEffect
        val color = target.color
        if (target.recent) {
            showRecent = true
            showFavourites = false
            colorFilter = null
        } else if (target.favourites) {
            showFavourites = true
            showRecent = false
            colorFilter = null
        } else if (color != null) {
            colorFilter = color
            showRecent = false
            showFavourites = false
        } else {
            val folderUri = target.folderUri
            val chain = if (folderUri == null) emptyList() else withContext(Dispatchers.IO) { editor.folderChain(root, folderUri) }
            if (chain == null) {
                opError = context.getString(R.string.err_open_folder)
            } else {
                stack.clear()
                stack.addAll(chain)
                opError = null
            }
            colorFilter = null
            showRecent = false
            showFavourites = false
        }
        selection.clear()
        selectMode = false
        columnsPick = null
        feed.query = ""
        link.onNavHandled()
    }
    LaunchedEffect(currentDocId, colorFilter, showRecent, showFavourites) {
        link.onPlace(if (stack.isEmpty()) null else currentDocId, colorFilter, showRecent, showFavourites)
    }
    LaunchedEffect(feed.query) { if (feed.query.isNotBlank()) { colorFilter = null; showRecent = false; showFavourites = false } }
    // Home opens on the folder last shown when set to; until that's settled, nothing is saved over it.
    var settled by remember(root) { mutableStateOf(false) }
    LaunchedEffect(root) {
        val last = editor.lastFolder
        if (prefs.homeOpensTo == "last" && nav == null && last != null && stack.isEmpty()) {
            withContext(Dispatchers.IO) { editor.folderChain(root, last) }?.let { chain -> if (stack.isEmpty()) stack.addAll(chain) }
        }
        settled = true
    }
    LaunchedEffect(currentDocId, settled) {
        if (settled) editor.setLastFolder(if (stack.isEmpty()) null else android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(root), currentDocId).toString())
    }
    // Changing folders drops a stale query so it can't carry into a folder the user just opened. Only a
    // change: the sidebar's search may have brought the library up with a query already typed.
    var queriedIn by remember(root) { mutableStateOf(currentDocId) }
    LaunchedEffect(currentDocId) { if (queriedIn != currentDocId) { queriedIn = currentDocId; feed.query = "" } }
    // Whatever the explorer changes, the library's index (All notes, the sidebar's counts) walks again.
    LaunchedEffect(refreshKey) { if (refreshKey > 0) feed.changed() }
    // Inside a subfolder, back climbs one level out (this sits below the Backstage's root
    // handler, so it's consulted first and only fires while there's a folder to leave).
    BackHandler(enabled = stack.isNotEmpty() && colorFilter == null) {
        stack.removeAt(stack.lastIndex)
        clearUp()
    }
    BackHandler(enabled = colorFilter != null) {
        colorFilter = null
        selection.clear()
    }
    BackHandler(enabled = showRecent) {
        showRecent = false
        selection.clear()
    }
    BackHandler(enabled = showFavourites) {
        showFavourites = false
        selection.clear()
    }
    BackHandler(enabled = feed.query.isNotBlank()) { feed.query = "" }
    BackHandler(enabled = selection.isNotEmpty() || selectMode) { selection.clear(); selectMode = false }
    // A selection emptied by what was done with it ends select mode too.
    var hadSelection by remember(root) { mutableStateOf(false) }
    LaunchedEffect(selection.isEmpty()) {
        if (selection.isNotEmpty()) hadSelection = true
        else if (hadSelection) { hadSelection = false; selectMode = false }
    }

    val folderKey = editor.folderKey(root, currentDocId)
    val view = editor.viewFor(folderKey)
    val setView: (ExplorerView) -> Unit = { editor.setView(folderKey, it) }
    val setViewNow = rememberUpdatedState(setView)
    val searching = feed.query.isNotBlank()
    val flat = searching || colorFilter != null || showRecent || showFavourites
    val layout = when {
        flat && (view.layout == ExplorerLayout.COLUMNS || view.layout == ExplorerLayout.TIMELINE) -> ExplorerLayout.GALLERY
        compactScreen && view.layout == ExplorerLayout.COLUMNS -> ExplorerLayout.LIST
        else -> view.layout
    }
    // At the top, the shelf layouts list every note in the library ("All notes") rather than the top folder's
    // own items; the folders themselves stand in the sidebar.
    val allNotes = !flat && stack.isEmpty() &&
        (layout == ExplorerLayout.GALLERY || layout == ExplorerLayout.GRID || layout == ExplorerLayout.LIST)
    val switcherLayouts = prefs.switcherLayouts.filter { !(compactScreen && it == ExplorerLayout.COLUMNS) }

    // Listing. Re-keyed on noteOpen so returning from the editor re-queries the folder, picking up
    // the just-closed note's new mtime (its tile refreshes) and any newly created/discovered items.
    // Opening a note leaves what is listed as it is (only a first listing is read under it): the shelf lies hidden under
    // the editor, and re-reading it there only competes with the note.
    val entries by produceState(editor.cachedChildren(root, currentDocId), root, currentDocId, refreshKey, editor.noteOpen, editor.treeVersion) {
        if (editor.noteOpen && value != null) return@produceState
        value = withContext(Dispatchers.IO) { editor.browseChildren(root, currentDocId) }
    }
    val trimmed = feed.query.trim()
    // When searching, walk the whole library (debounced) and show only the matching notes — no folders,
    // since a deep hit doesn't belong to the folder on screen.
    val results by produceState<List<BrowseEntry>?>(emptyList(), root, trimmed, refreshKey, editor.noteOpen, editor.treeVersion) {
        if (editor.noteOpen && value != null) return@produceState
        if (trimmed.isEmpty()) { value = emptyList(); return@produceState }
        value = null
        delay(250) // debounce keystrokes before walking the tree
        value = withContext(Dispatchers.IO) { editor.searchNotes(root, rootDocId, trimmed) }
    }
    val colorResults by produceState<List<BrowseEntry>?>(null, root, colorFilter, refreshKey, editor.noteOpen, editor.treeVersion) {
        if (editor.noteOpen && value != null) return@produceState
        val c = colorFilter ?: run { value = emptyList(); return@produceState }
        value = null
        value = withContext(Dispatchers.IO) { editor.findByColor(root, c) }
    }
    val timeline by produceState<TreeFiles?>(null, root, currentDocId, includeSubfolders, refreshKey, editor.noteOpen, editor.treeVersion, layout == ExplorerLayout.TIMELINE) {
        if (layout != ExplorerLayout.TIMELINE || (editor.noteOpen && value != null)) return@produceState
        value = withContext(Dispatchers.IO) { editor.filesUnder(root, currentDocId, includeSubfolders) }
    }
    // All notes opens with the notes written in last, a tap from carrying on.
    val continueShown = allNotes && kindFilter == null
    // Opening a note puts it first in Recent; Continue writing takes that up as Home comes back, not under the editor.
    val recents by produceState<List<RecentEntry>?>(null, root, showRecent || continueShown, editor.recentDocs, refreshKey, editor.noteOpen, editor.treeVersion) {
        if (editor.noteOpen && value != null) return@produceState
        if (showRecent || continueShown) value = withContext(Dispatchers.IO) { editor.recentEntries(root) }
    }
    val recentByUri = remember(recents) { recents.orEmpty().associateBy { it.entry.documentUri } }
    val index = feed.index
    val favouriteFiles = remember(index, marks.favourites) { index?.files?.filter { marks.isFavourite(it.documentUri) } }
    // Until the library has been walked, All notes makes do with the top folder's own notes.
    val topFiles = remember(entries) { entries?.filter { !it.isDir } }
    val source: List<BrowseEntry>? = when {
        showRecent -> recents?.map { it.entry }
        showFavourites -> favouriteFiles
        colorFilter != null -> colorResults
        searching -> results
        layout == ExplorerLayout.TIMELINE -> timeline?.files
        allNotes -> index?.files ?: topFiles
        else -> entries
    }
    // Page counts, PDF flags and the created times files record fill in behind the listing, a few files at a time.
    LaunchedEffect(source) {
        val todo = source?.filter { !it.isDir && editor.cachedMeta(it) == null && DocumentKind.isDocument(it.name) }
        if (todo.isNullOrEmpty()) return@LaunchedEffect
        for (chunk in todo.chunked(6)) {
            withContext(Dispatchers.IO) { chunk.forEach { editor.docMetaFor(it) } }
            metaTick++
        }
    }
    val kindOf: (BrowseEntry) -> EntryKind = { entryKind(it, editor.cachedMeta(it)) }
    val withCreated: (BrowseEntry) -> BrowseEntry = { e -> editor.createdOf(e).let { if (it == e.created) e else e.copy(created = it) } }
    val arrange: (List<BrowseEntry>) -> List<BrowseEntry> = remember(view.sortKey, view.descending, view.folders, kindFilter, metaTick) {
        { list ->
            list.filter { e -> if (e.isDir) view.folders != FolderPlacement.HIDDEN && kindFilter == null else kindFilter == null || kindOf(e) == kindFilter }
                .map(withCreated)
                .sortedWith(explorerComparator(view.sortKey, view.descending, foldersFirst = view.folders != FolderPlacement.MIXED) { it.created })
        }
    }
    // Recent keeps the order things were opened in.
    val arranged = remember(source, arrange, showRecent) {
        if (showRecent) source?.filter { kindFilter == null || kindOf(it) == kindFilter } else source?.let(arrange)
    }
    // Columns are how one gets around in that layout, so their folders stay whatever the placement says.
    val columnsArrange: (List<BrowseEntry>) -> List<BrowseEntry> = remember(view.sortKey, view.descending, kindFilter, metaTick) {
        { list ->
            list.filter { e -> e.isDir || kindFilter == null || kindOf(e) == kindFilter }
                .map(withCreated)
                .sortedWith(explorerComparator(view.sortKey, view.descending) { it.created })
        }
    }
    val now = remember(arranged) { System.currentTimeMillis() }
    val folderRow = remember(arranged, view.folders, layout) {
        if (view.folders == FolderPlacement.TOP && layout != ExplorerLayout.TIMELINE) arranged.orEmpty().filter { it.isDir } else emptyList()
    }
    val pool = remember(arranged, view.folders) { if (view.folders == FolderPlacement.MIXED) arranged.orEmpty() else arranged.orEmpty().filterNot { it.isDir } }
    val colorNames = editor.colorNames
    // The covers stand in one run, as books on a shelf do; headings are for the denser layouts.
    val groups = remember(pool, view.groupBy, view.sortKey, view.descending, colorNames, metaTick, showRecent, layout) {
        groupEntries(
            words,
            pool, if (showRecent || layout == ExplorerLayout.GALLERY) GroupBy.NONE else view.groupBy, view.sortKey, view.descending, kindOf, { colorNames[it] }, now, ZoneId.systemDefault(),
            WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek,
        )
    }
    val metas = remember(arranged, metaTick) { arranged.orEmpty().filter { !it.isDir }.associate { it.documentUri to editor.cachedMeta(it) } }
    val counts by produceState(emptyMap<String, Int>(), root, arranged, prefs.showFolderCounts) {
        val folders = arranged.orEmpty().filter { it.isDir }
        value = if (!prefs.showFolderCounts || folders.isEmpty()) emptyMap()
        else withContext(Dispatchers.IO) { folders.associate { it.documentUri to editor.folderItemCount(root, it) } }
    }

    // Drag-to-move state. While a selection is being dragged onto a folder, [dragPos] is the finger
    // position in window coords, [folderSpots] maps each visible folder to its coordinates for
    // hit-testing, [dropTargetUri] is the folder under the finger, and [pulseUri] flashes the folder a
    // dropped move just landed in. [boxCoords] anchors the floating preview into the body's own space.
    // Coordinates, not rects: tiles move every frame of a scroll or resize, and only a drag needs bounds.
    val folderSpots = remember(root) { HashMap<String, LayoutCoordinates>() }
    val fileSpots = remember(root) { HashMap<String, LayoutCoordinates>() }
    var dragItems by remember(root) { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var dragPos by remember(root) { mutableStateOf<Offset?>(null) }
    var dropTargetUri by remember(root) { mutableStateOf<String?>(null) }
    var pulseUri by remember(root) { mutableStateOf<String?>(null) }
    var boxCoords by remember(root) { mutableStateOf<LayoutCoordinates?>(null) }
    var dragCardSize by remember(root) { mutableStateOf<IntSize?>(null) }
    // What composition reads of a drag: whether one is on. The finger's place is read only in layout and effects,
    // so a drag recomposes nothing per frame; tiles see the drop target change only when it moves to another folder.
    val dragging by remember(root) { derivedStateOf { dragPos != null } }
    // The tab bar's place, so the long press below leaves a press on it alone (a cover may lie scrolled beneath).
    val tabsSpot = remember(root) { arrayOfNulls<LayoutCoordinates>(1) }
    // Select mode's marks fade in once, together, from here; a tile scrolled into view later shows its mark at once.
    val selectFade = remember(root) { Animatable(0f) }
    val gridState = rememberLazyGridState()
    val galleryState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val timelineState = rememberLazyListState()
    val scrollNow = rememberUpdatedState<ScrollableState>(
        when (layout) { ExplorerLayout.LIST -> listState; ExplorerLayout.GALLERY -> galleryState; ExplorerLayout.TIMELINE -> timelineState; else -> gridState },
    )
    fun toggleSelect(e: BrowseEntry) {
        val i = selection.indexOfFirst { it.documentUri == e.documentUri }
        if (i >= 0) selection.removeAt(i) else selection.add(e)
    }
    // The folder under the finger, ignoring any folder that's itself part of the dragged selection.
    fun updateDropTarget(pos: Offset) {
        dropTargetUri = folderSpots.entries
            .firstOrNull { (uri, c) -> c.isAttached && c.boundsInWindow().contains(pos) && dragItems.none { it.documentUri == uri } }?.key
    }
    // While dragging a selection near the top/bottom edge of the body, keep it scrolling so a folder
    // that's currently off-screen can still be reached, the same way the toolbar drag does.
    val autoScrollBand = with(density) { 72.dp.toPx() }
    val headerBand = 0f // the title row no longer lies over the files
    LaunchedEffect(dragging) {
        while (dragPos != null) {
            val pos = dragPos
            val bc = boxCoords
            if (pos != null && bc != null) {
                val b = bc.boundsInWindow()
                val top = b.top + headerBand
                val delta = when {
                    pos.y < top + autoScrollBand -> -((top + autoScrollBand - pos.y) / autoScrollBand).coerceIn(0f, 1f) * autoScrollBand
                    pos.y > b.bottom - autoScrollBand -> ((pos.y - (b.bottom - autoScrollBand)) / autoScrollBand).coerceIn(0f, 1f) * autoScrollBand
                    else -> 0f
                }
                if (delta != 0f) {
                    scrollNow.value.scrollBy(delta)
                    updateDropTarget(pos)
                }
            }
            delay(16L)
        }
    }

    val callsNow = rememberUpdatedState(calls)
    val prefsNow = rememberUpdatedState(prefs)
    val rootName by produceState(editor.cachedRootName(root), root) { value = withContext(Dispatchers.IO) { editor.browseRootName(root) } }
    fun openChain(folderUri: String) {
        scope.launch {
            val chain = withContext(Dispatchers.IO) { editor.folderChain(root, folderUri) }
            if (chain == null) opError = context.getString(R.string.err_open_folder) else {
                stack.clear()
                stack.addAll(chain)
                colorFilter = null
                showRecent = false
                showFavourites = false
                feed.query = ""
                clearUp()
            }
        }
    }
    fun openFolder(e: BrowseEntry) {
        opError = null
        // Read afresh: the tile callbacks outlive the composition this was built in.
        if (feed.query.isNotBlank() || colorFilter != null || showRecent || showFavourites) openChain(e.documentUri) else {
            stack.add(editor.browseDocId(e.documentUri) to e.name)
            columnsPick = null
        }
    }
    // Into Trash with an Undo, or through the delete-for-good dialog when Trash is off or can't take an item.
    fun remove(items: List<BrowseEntry>) {
        if (items.isEmpty()) return
        if (prefsNow.value.trashDays == 0) { pendingDelete = items; return }
        selection.clear(); opError = null
        scope.launch {
            val (trashed, failed) = withContext(Dispatchers.IO) { editor.trashEntries(root, items) }
            refreshKey++
            if (trashed.isNotEmpty()) {
                val what = if (trashed.size == 1) context.getString(R.string.moved_one_to_trash, entryLabel(trashed.first().entry)) else context.resources.getQuantityString(R.plurals.moved_items_to_trash, trashed.size, trashed.size)
                editor.say(what, context.getString(R.string.undo) to { editor.undoTrash(root, trashed) })
            }
            if (failed.isNotEmpty()) {
                opError = context.resources.getQuantityString(R.plurals.err_move_items_to_trash, failed.size, failed.size)
                pendingDelete = failed
            }
        }
    }
    // Favourites and cover colours follow what was moved or renamed, found again by name where it now lies.
    fun followMarks(items: List<BrowseEntry>, targetDocId: String, newName: String? = null) {
        if (items.isEmpty()) return
        scope.launch {
            val pairs = withContext(Dispatchers.IO) {
                val listing = editor.browseChildren(root, targetDocId)
                items.mapNotNull { e ->
                    listing.firstOrNull { it.isDir == e.isDir && it.name == (newName ?: e.name) }?.let { e.documentUri to it.documentUri }
                }
            }
            pairs.forEach { (from, to) -> marks.moved(from, to) }
        }
    }
    fun recolor(items: List<BrowseEntry>, c: Rgba?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                items.groupBy { it.parentDocId }.forEach { (parent, list) -> editor.setItemColors(root, parent, list.associate { it.name to c }) }
            }
            refreshKey++
        }
    }
    val layoutNow = rememberUpdatedState(layout)
    val layoutDir = LocalLayoutDirection.current
    val viewNow = rememberUpdatedState(view)
    // Opening from the shelf (B2 motion e): the tile's visual part grows into the page while the note loads.
    fun openFromShelf(e: BrowseEntry, from: LayoutCoordinates?, coverTop: Dp? = null, shown: ImageBitmap? = null) {
        val kind = kindOf(e)
        val card = coverTop != null
        // A Continue card grows from the page it shows (the one the note was left on); a tile from the first page.
        val page = (if (card) shown else null) ?: editor.cachedPageThumb(e.documentUri)
        val ratio = page?.let { it.width.toFloat() / it.height } ?: if (kind == EntryKind.CANVAS) CANVAS_RATIO else A4_RATIO
        val layoutUsed = layoutNow.value
        val asCover = !card && layoutUsed == ExplorerLayout.GALLERY
        // Its place and size, not boundsInWindow: that is clipped, and a tile half under the tabs or past the
        // shelf's edge must still grow from the whole of itself.
        val rect = from?.takeIf { it.isAttached }?.let { c ->
            val at = c.positionInWindow()
            val w = c.size.width.toFloat()
            val full = c.size.height.toFloat()
            when {
                card -> Rect(at.x, at.y, at.x + w, at.y + minOf(full, with(density) { coverTop!!.toPx() })) // just its image
                asCover -> Rect(at.x, at.y, at.x + w, at.y + minOf(full, w / COVER_RATIO)) // a cover: not its title lines
                layoutUsed == ExplorerLayout.GRID -> Rect(at.x, at.y, at.x + w, at.y + minOf(full, w / if (viewNow.value.thumb == ThumbShape.PAGE) A4_RATIO else 1f))
                layoutUsed == ExplorerLayout.TIMELINE -> Rect(at.x, at.y, at.x + w, at.y + minOf(full, w)) // its square thumbnail
                layoutUsed == ExplorerLayout.LIST -> with(density) {
                    // The row's thumbnail at its start, not the whole width of the row.
                    val side = (if (viewNow.value.compactRows) 28.dp else 40.dp).toPx()
                    val inset = 8.dp.toPx()
                    val x = if (layoutDir == LayoutDirection.Rtl) at.x + w - inset - side else at.x + inset
                    val y = at.y + (full - side) / 2f
                    Rect(x, y, x + side, y + side)
                }
                else -> Rect(at.x, at.y, at.x + w, at.y + full)
            }
        }
        // The cover fades into the page; a tile or card that already showed this page grows with it from the start.
        val art: (@Composable () -> Unit)? = when {
            asCover -> ({ CoverArt(editor, e, kind, marks.coverIndex(e.documentUri), entryLabel(e), Modifier.fillMaxSize()) })
            page == null -> ({ ThumbImage(editor.cachedNoteTile(e.documentUri), whole = true, Modifier.fillMaxSize()) })
            else -> null
        }
        LibraryOpen.begin(rect, ratio, page, art)
        callsNow.value.openFile(e.documentUri)
    }
    val host = remember(root) {
        TileHost(
            isSelected = { e -> selection.any { it.documentUri == e.documentUri } },
            selecting = { selection.isNotEmpty() || selectMode },
            isCut = { e -> clipboard?.let { c -> c.isCut && c.entries.any { it.documentUri == e.documentUri } } == true },
            isPinned = { e -> e.isDir && editor.isPinned(e.documentUri) },
            isDropTarget = { e -> dragging && dropTargetUri == e.documentUri },
            isPulsing = { e -> pulseUri == e.documentUri },
            onPulseDone = { e -> if (pulseUri == e.documentUri) pulseUri = null },
            onClick = { e ->
                opError = null
                when {
                    selection.isNotEmpty() || selectMode -> toggleSelect(e)
                    e.isDir -> openFolder(e)
                    prefsNow.value.tapPreviews -> previewing = e
                    else -> openFromShelf(e, fileSpots[e.documentUri])
                }
            },
            onLongClick = { e ->
                renaming = null; opError = null
                if (selection.none { it.documentUri == e.documentUri }) selection.add(e)
            },
            onPlaced = { e, c ->
                val spots = if (e.isDir) folderSpots else fileSpots
                if (c == null) spots.remove(e.documentUri) else spots[e.documentUri] = c
            },
            menu = EntryActions(
                rename = { renaming = it },
                copy = { clipboard = ClipItem(listOf(it), false) },
                cut = { clipboard = ClipItem(listOf(it), true) },
                moveTo = { moving = listOf(it) },
                delete = { remove(listOf(it)) },
                color = { e, c -> recolor(listOf(e), c) },
                nameColor = { namingColor = it },
                togglePin = { e -> if (editor.isPinned(e.documentUri)) editor.unpinFolder(e.documentUri) else editor.pinFolder(e.documentUri, e.name) },
                share = { callsNow.value.shareFile(it.documentUri) },
                saveCopy = { callsNow.value.saveCopyFile(it.documentUri) },
                exportPdf = { callsNow.value.exportFilePdf(it.documentUri) },
                preview = { previewing = it },
            ),
            selectShown = { selectFade.value },
        )
    }
    val folderNames = timeline?.folderNames
    val clock24 = android.text.format.DateFormat.is24HourFormat(context)
    // Kept while what it describes holds, so a recomposition here (a drag starting or ending) leaves the tiles be.
    val body = remember(view, folderRow, groups, metas, counts, now, clock24, prefs, host, showRecent, recentByUri, folderNames, layout, words, marks, allNotes, showFavourites) { ExplorerBody(
        editor = editor,
        view = view,
        folders = folderRow,
        groups = groups,
        metas = metas,
        counts = counts,
        now = now,
        clock24 = clock24,
        dateStyle = prefs.dateStyle,
        showExtensions = prefs.showExtensions,
        deleteLabel = if (prefs.trashDays == 0) context.getString(R.string.delete) else context.getString(R.string.move_to_trash),
        host = host,
        words = words,
        whereOf = when {
            showRecent -> ({ e -> recentByUri[e.documentUri]?.where })
            layout == ExplorerLayout.TIMELINE && folderNames != null -> ({ e -> folderNames[e.parentDocId] })
            // Notes gathered from every folder say which one they sit in, where the layout has room.
            (allNotes || showFavourites) && layout == ExplorerLayout.LIST -> ({ e -> feed.index?.folderNames?.get(e.parentDocId) })
            else -> null
        },
        metaOverride = if (showRecent) ({ e -> recentByUri[e.documentUri]?.let { openedLabel(words, it.opened, now) } }) else null,
        marks = marks,
        fade = fadeNow,
    ) }
    val pathText = (listOf(rootName ?: stringResource(R.string.folder)) + stack.map { it.second }).joinToString(" / ")
    // The Timeline opens on the month of the newest note, until a month is picked.
    val newestMonth = remember(arranged, view.timelineByCreated) {
        arranged.orEmpty().maxOfOrNull { view.timelineTime(it) }?.takeIf { it > 0 }
            ?.let { YearMonth.from(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: YearMonth.now()
    }
    val shownMonth = timelineMonth ?: newestMonth
    fun previewActions(e: BrowseEntry) = PreviewActions(
        open = { previewing = null; calls.openFile(e.documentUri) },
        openBeside = calls.openBeside(e.documentUri)?.let { go -> { previewing = null; go() } },
        share = { calls.shareFile(e.documentUri) },
        exportPdf = { calls.exportFilePdf(e.documentUri) },
        color = { c -> recolor(listOf(e), c) },
    )

    fun paste(clip: ClipItem) {
        opError = null
        scope.launch {
            val allOk = withContext(Dispatchers.IO) {
                val done = if (clip.isCut) editor.moveEntriesInto(root, clip.entries, currentDocId)
                else editor.copyEntriesInto(root, clip.entries, currentDocId)
                done == clip.entries.size
            }
            refreshKey++
            if (clip.isCut) followMarks(clip.entries, currentDocId)
            if (allOk) clipboard = null else opError = context.getString(R.string.err_paste_some)
        }
    }

    // A failed operation shows in the app's snackbar, which stays in view however far the files are scrolled.
    LaunchedEffect(opError) { opError?.let { editor.say(it); opError = null } }
    val filterNow = colorFilter
    val empty = when {
        filterNow != null && colorResults == null -> EmptyState(stringResource(R.string.finding))
        filterNow != null && colorResults!!.isEmpty() -> EmptyState(
            stringResource(R.string.empty_tag_title), EmptyArt.SEARCH,
            body = stringResource(R.string.empty_tag_body, editor.colorNames[filterNow] ?: hueName(words, filterNow)),
        )
        searching && results == null -> EmptyState(stringResource(R.string.searching))
        searching && results!!.isEmpty() -> EmptyState(stringResource(R.string.empty_search_title, trimmed), EmptyArt.SEARCH, body = stringResource(R.string.empty_search_body))
        showRecent && recents == null -> EmptyState(stringResource(R.string.loading))
        showRecent && recents!!.isEmpty() -> EmptyState(stringResource(R.string.empty_recent_title), EmptyArt.RECENT, body = stringResource(R.string.empty_recent_body))
        showFavourites && favouriteFiles == null -> EmptyState(stringResource(R.string.loading))
        showFavourites && favouriteFiles!!.isEmpty() -> EmptyState(stringResource(R.string.empty_favourites_title), EmptyArt.PAGES, body = stringResource(R.string.empty_favourites_body))
        source == null -> EmptyState(stringResource(R.string.loading))
        layout == ExplorerLayout.COLUMNS -> null
        source.isEmpty() -> EmptyState(
            stringResource(if (layout == ExplorerLayout.TIMELINE) R.string.empty_timeline_title else R.string.empty_folder_title),
            EmptyArt.PAGES, offersCreate = true,
        )
        arranged.isNullOrEmpty() -> if (kindFilter == null) EmptyState(stringResource(R.string.nothing_to_show)) else EmptyState(
            stringResource(
                when (kindFilter) {
                    EntryKind.PDF -> R.string.empty_kind_pdfs
                    EntryKind.FOLDER -> R.string.empty_kind_folders
                    EntryKind.NOTE -> R.string.empty_kind_notes
                    else -> R.string.empty_kind_canvases
                },
            ),
            EmptyArt.SEARCH, body = stringResource(R.string.empty_kind_body),
        )
        else -> null
    }
    val selecting = selection.isNotEmpty() || selectMode
    LaunchedEffect(selecting) {
        if (selecting) selectFade.animateTo(1f, tween(InkMotion.FAST, easing = InkMotion.Standard)) else selectFade.snapTo(0f)
    }
    val hPad = if (compactScreen) 16.dp else 32.dp
    val ink = LocalInk.current
    val countLabel = when {
        selecting -> stringResource(R.string.n_selected, selection.size)
        layout == ExplorerLayout.TIMELINE -> {
            val m = shownMonth
            val n = arranged.orEmpty().count { !it.isDir && view.timelineTime(it) > 0 && YearMonth.from(java.time.Instant.ofEpochMilli(view.timelineTime(it)).atZone(ZoneId.systemDefault())) == m }
            pluralStringResource(if (view.timelineByCreated) R.plurals.notes_created_in else R.plurals.notes_saved_in, n, n, words.month(m.month))
        }
        else -> {
            val files = arranged.orEmpty().count { !it.isDir }
            val folders = arranged.orEmpty().count { it.isDir }
            val size = if (layout == ExplorerLayout.LIST) arranged.orEmpty().filterNot { it.isDir }.sumOf { it.size }.takeIf { it > 0 }?.let { formatSize(it) } else null
            dotJoined(listOfNotNull(countsLabel(words, folders, files).ifEmpty { null }, size))
        }
    }
    // The category tabs (.cats) with the count, Sort & view and Select at their end; sticky under the header.
    val tabsBar: @Composable () -> Unit = {
        CategoryTabs(
            kind = pickedKind, onKind = { pickedKind = it }, count = countLabel, hPad = hPad, compact = compactScreen,
            sortView = {
                SortViewButton(
                    compact = compactScreen,
                    view = view, layout = layout,
                    showSort = !showRecent && layout != ExplorerLayout.TIMELINE,
                    layouts = ExplorerLayout.entries.filter { it in switcherLayouts || it == layout },
                    onView = setView,
                    onLayout = { l -> setView(view.copy(layout = l)); columnsPick = null },
                    locationName = rootName ?: stringResource(R.string.folder),
                    onChangeFolder = calls.pickRoot,
                    onForgetFolder = { editor.clearBrowseRoot() },
                    // Phones have no room for the count in the bar; the menu carries it.
                    count = if (compactScreen && !selecting) countLabel else null,
                ) { close ->
                    ViewOptionsContent(
                        view = view.copy(layout = layout),
                        layouts = ExplorerLayout.entries.filter { !(compactScreen && it == ExplorerLayout.COLUMNS) },
                        everyFolder = !prefs.perFolderViews,
                        // Results shown in covers for now leave the folder's own layout alone unless another is picked.
                        onChange = { v -> setView(if (v.layout == layout) v.copy(layout = view.layout) else v); if (v.layout != layout) columnsPick = null },
                        onReset = { editor.setView(folderKey, null) },
                        onEveryFolder = { every -> editor.applyHomePreferences(editor.preferences.copy(perFolderViews = !every)) },
                        onClose = close,
                    )
                }
            },
            select = { SelectButton(selecting, compactScreen) { if (selecting) { selection.clear(); selectMode = false } else selectMode = true } },
            modifier = Modifier.onPlaced { tabsSpot[0] = it },
        )
    }
    // Timeline's "placed by" and its subfolders toggle, and a pending paste: the shelf's contextual chips, under the tabs.
    val contextChips: @Composable (Dp) -> Unit = { _ ->
        val clip = clipboard
        if (layout == ExplorerLayout.TIMELINE || clip != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (layout == ExplorerLayout.TIMELINE) {
                    var byOpen by remember { mutableStateOf(false) }
                    Box {
                        ExplorerChip(stringResource(if (view.timelineByCreated) R.string.sort_created else R.string.sort_modified), true, icon = Ph.sortAscending, trailing = Ph.caretDown) { byOpen = true }
                        DropdownMenu(expanded = byOpen, onDismissRequest = { byOpen = false }, modifier = Modifier.widthIn(min = 248.dp)) {
                            listOf(true to R.string.sort_created, false to R.string.sort_modified).forEach { (created, label) ->
                                InkMenuRow(stringResource(label), { byOpen = false; setView(view.copy(timelineByCreated = created)) }, checked = created == view.timelineByCreated)
                            }
                        }
                    }
                    ExplorerChip(stringResource(R.string.include_subfolders), includeSubfolders, icon = Ph.folderSimple) { includeSubfolders = !includeSubfolders }
                }
                if (clip != null) {
                    ExplorerChip(pluralStringResource(R.plurals.paste_items, clip.entries.size, clip.entries.size), false, icon = Ph.clipboard) { paste(clip) }
                    InkIconButton(Ph.x, stringResource(R.string.clear_clipboard), { clipboard = null }, size = 36.dp, iconSize = 18.dp, tint = ink.text2)
                }
            }
        }
    }
    val pinchable = layout == ExplorerLayout.GRID || layout == ExplorerLayout.GALLERY || layout == ExplorerLayout.TIMELINE
    val filesNow = rememberUpdatedState(arranged.orEmpty())
    Column(Modifier.fillMaxSize()) {
        // The title row (.mh): where the library stands, the search pill and New, with a hairline under it. While
        // items are picked the selection bar takes its place, so nothing on the shelf moves; the hairline is drawn
        // over both, and the title row it hides leaves the accessibility tree.
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compactScreen) LIBRARY_HEADER_COMPACT else LIBRARY_HEADER)
                .background(ink.bg)
                .drawWithContent {
                    drawContent()
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(ink.line2, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                },
        ) {
            val inFolder = stack.isNotEmpty() && !flat
            var newOpen by remember { mutableStateOf(false) }
            LibraryHeaderLayout(
                query = feed.query,
                onQuery = { feed.query = it },
                modifier = Modifier.fillMaxSize().padding(horizontal = hPad)
                    .then(if (selecting) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier),
                lead = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!sidebarOpen) {
                            InkIconButton(Ph.sidebarSimple, stringResource(R.string.show_sidebar), onShowSidebar)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (inFolder) {
                            InkIconButton(Ph.caretLeft, stringResource(R.string.library_back), { stack.removeAt(stack.lastIndex); clearUp() })
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.Center) {
                            if (inFolder) {
                                LibraryCrumbs(listOf(stringResource(R.string.library_all_notes)) + stack.dropLast(1).map { it.second }) { i ->
                                    if (i == 0) stack.clear() else while (stack.size > i) stack.removeAt(stack.lastIndex)
                                    clearUp()
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val filter = colorFilter
                                when {
                                    searching -> LibraryTitle(stringResource(R.string.library_results_for, trimmed), Modifier.weight(1f, fill = false), compactScreen)
                                    showRecent -> {
                                        LibraryTitle(stringResource(R.string.recent), compact = compactScreen)
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.opened_on_device), style = InkType.body, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (!recents.isNullOrEmpty()) InkGhostButton(stringResource(R.string.clear), { editor.clearRecents() })
                                    }
                                    showFavourites -> LibraryTitle(stringResource(R.string.library_favourites), compact = compactScreen)
                                    filter != null -> {
                                        Box(Modifier.size(12.dp).background(codeTint(filter, palette), CircleShape))
                                        Spacer(Modifier.width(10.dp))
                                        LibraryTitle(editor.colorNames[filter] ?: hueName(words, filter), Modifier.weight(1f, fill = false), compactScreen)
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.in_every_folder), style = InkType.body, color = ink.text2, maxLines = 1)
                                        InkIconButton(Ph.x, stringResource(R.string.clear_colour_filter), { colorFilter = null; selection.clear() }, size = 36.dp, iconSize = 18.dp, tint = ink.text2)
                                    }
                                    inFolder -> LibraryTitle(stack.last().second, Modifier.weight(1f, fill = false), compactScreen)
                                    else -> LibraryTitle(stringResource(R.string.library_all_notes), compact = compactScreen)
                                }
                            }
                        }
                    }
                },
                new = {
                    Box {
                        InkBrandButton(stringResource(R.string.create_new), { newOpen = true }, icon = Ph.plus)
                        DropdownMenu(expanded = newOpen, onDismissRequest = { newOpen = false }, modifier = Modifier.widthIn(min = 248.dp)) {
                            NewItemMenuItems({ newOpen = false }, onCreateMode, calls.importPdf)
                        }
                    }
                },
            )
            if (selecting) {
                SelectionBar(selection.size, onClear = { selection.clear(); selectMode = false }, onSelectAll = { selection.clear(); selection.addAll(arranged.orEmpty()) }) {
                    val files = selection.filterNot { it.isDir }
                    // Star or unstar every picked note at once: unstar only when all of them already are.
                    if (files.isNotEmpty()) {
                        val uris = files.map { it.documentUri }
                        val starred = uris.all { marks.isFavourite(it) }
                        ExplorerIcon(
                            if (starred) Ph.heartFill else Ph.heart,
                            stringResource(if (starred) R.string.library_remove_favourite else R.string.library_add_favourite),
                            if (starred) ink.brand else ink.text,
                        ) { marks.setFavourite(uris, !starred); selection.clear() }
                    }
                    if (selection.size == 1) ExplorerIcon(Ph.pencilSimple, stringResource(R.string.rename)) { renaming = selection.first(); selection.clear() }
                    val pair = files.map { it.documentUri }.distinct().takeIf { it.size == 2 && files.size == selection.size }
                    if (pair != null) ExplorerIcon(Ph.columns, stringResource(R.string.open_side_by_side)) { selection.clear(); calls.openSplit(pair[0], pair[1]) }
                    ExplorerIcon(Ph.folderSimple, stringResource(R.string.move_to_folder)) { moving = selection.toList() }
                    ExplorerIcon(Ph.copy, stringResource(R.string.copy)) { clipboard = ClipItem(selection.toList(), false); selection.clear() }
                    ExplorerIcon(Ph.scissors, stringResource(R.string.cut)) { clipboard = ClipItem(selection.toList(), true); selection.clear() }
                    var colorsOpen by remember { mutableStateOf(false) }
                    Box {
                        ExplorerIcon(Ph.palette, stringResource(R.string.colour_code)) { colorsOpen = true }
                        DropdownMenu(expanded = colorsOpen, onDismissRequest = { colorsOpen = false }) {
                            ColorCodeMenuContent { c -> colorsOpen = false; recolor(selection.toList(), c); selection.clear() }
                        }
                    }
                    ExplorerIcon(Ph.shareNetwork, stringResource(R.string.share), enabled = files.size == selection.size) {
                        val uris = files.map { it.documentUri }
                        selection.clear()
                        if (uris.size == 1) calls.shareFile(uris[0]) else calls.shareFiles(uris)
                    }
                    SelectionDivider()
                    ExplorerIcon(Ph.trash, if (prefs.trashDays == 0) stringResource(R.string.delete) else stringResource(R.string.move_to_trash), ink.danger) { remove(selection.toList()) }
                }
            }
        }
        Box(
            // The keyboard slides over the files; ending the body at its edge lets the last ones scroll clear of it.
            Modifier.fillMaxWidth().weight(1f).imePadding()
                .onPlaced { boxCoords = it }
                // Pinching the tiles steps their size, a step each time the fingers spread or close far enough.
                .then(if (!pinchable) Modifier else Modifier.pointerInput(layout) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoom = 1f
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.changes.count { it.pressed } >= 2) {
                                zoom *= e.calculateZoom()
                                e.changes.forEach { if (it.positionChanged()) it.consume() }
                                val step = when { zoom > 1.3f -> 1; zoom < 0.77f -> -1; else -> 0 }
                                if (step != 0) {
                                    val v = viewNow.value
                                    val size = TileSize.entries[(v.tileSize.ordinal + step).coerceIn(0, TileSize.entries.lastIndex)]
                                    if (size != v.tileSize) setViewNow.value(v.copy(tileSize = size))
                                    zoom = 1f
                                }
                            }
                        } while (e.changes.any { it.pressed })
                    }
                })
                // Drag-to-move lives on the container (not the tiles) so the gesture keeps running while
                // the body auto-scrolls and the picked-up tile scrolls out of view. We can't reuse the
                // tiles' clickable for the long-press because a child consuming it (consumeUntilUp) would
                // starve this ancestor, so this is a hand-rolled long-press that hit-tests the file tiles
                // and consumes in the Initial pass (ahead of the tiles) to claim the gesture cleanly.
                .pointerInput(root) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Long-press gate: only a held, near-stationary single finger qualifies. A quick
                        // lift (tap), an early move (scroll) or a second finger (pinch) returns from the
                        // timeout normally so the body keeps those; holding past it throws, which is the signal.
                        val longPress = try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == down.id }
                                    if (c == null || !c.pressed || c.isConsumed) return@withTimeout false
                                    if (e.changes.any { it.id != down.id && it.pressed }) return@withTimeout false
                                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeout false
                                }
                                @Suppress("UNREACHABLE_CODE") false
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            true
                        }
                        if (!longPress) return@awaitEachGesture
                        // Long-press fired. Find the file tile under the finger; ignore folders/empty.
                        val winDown = boxCoords?.localToWindow(down.position) ?: return@awaitEachGesture
                        // A press on the sticky tab bar is the bar's, whatever cover lies scrolled beneath it.
                        if (tabsSpot[0]?.takeIf { it.isAttached }?.boundsInWindow()?.contains(winDown) == true) return@awaitEachGesture
                        val hitUri = fileSpots.entries.firstOrNull { it.value.isAttached && it.value.boundsInWindow().contains(winDown) }?.key
                        if (hitUri == null) return@awaitEachGesture
                        if (selection.none { it.documentUri == hitUri }) {
                            // First long-press selects. Consume to the up (Initial pass, ahead of the
                            // tile) so its click can't toggle the selection straight back off.
                            (filesNow.value.firstOrNull { it.documentUri == hitUri } ?: timeline?.files?.firstOrNull { it.documentUri == hitUri })?.let {
                                renaming = null; opError = null; selection.add(it)
                            }
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.id == down.id && it.pressed })
                            return@awaitEachGesture
                        }
                        // Second long-press on a selected tile: pick the whole selection up and drag it.
                        // Dragging moves files only; if any folder is selected, claim the gesture but don't drag.
                        if (selection.any { it.isDir }) {
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.id == down.id && it.pressed })
                            return@awaitEachGesture
                        }
                        val rect = fileSpots[hitUri]?.takeIf { it.isAttached }?.boundsInWindow()
                        dragItems = selection.toList()
                        dragCardSize = rect?.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) }
                        var pos = winDown
                        dragPos = pos
                        updateDropTarget(pos)
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            // Read the delta before consuming: positionChange() reports zero once consumed.
                            val delta = c.positionChange()
                            c.consume()
                            if (!c.pressed) break
                            pos += delta
                            dragPos = pos
                            updateDropTarget(pos)
                        }
                        // Drop: move the carried notes into the highlighted folder, then pulse it.
                        val target = dropTargetUri
                        val items = dragItems
                        dragPos = null; dropTargetUri = null; dragItems = emptyList()
                        if (target != null) {
                            val targetDocId = editor.browseDocId(target)
                            pulseUri = target; opError = null
                            val carried = items.filter { it.documentUri != target }
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { editor.moveEntriesInto(root, carried, targetDocId) == carried.size }
                                selection.clear(); refreshKey++
                                followMarks(carried, targetDocId)
                                if (!ok) opError = context.getString(R.string.err_move_some)
                            }
                        }
                    }
                }
                .then(
                    // In select mode, tapping empty space (not a tile) clears the selection.
                    if (selecting) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selection.clear(); selectMode = false } else Modifier,
                ),
        ) {
            // Continue writing: straight back into what was open last, from All notes. On All notes its item is always
            // there, a 1dp sliver until the recents are read: a lazy list keeps its first visible item where it is, so a
            // row inserted above it once they arrived would open the shelf scrolled past it. Not zero high: a lazy list
            // doesn't count an empty item as visible, so it would anchor on the first cover all the same.
            val hasContinue = continueShown && !recents.isNullOrEmpty()
            val continueBlock: (@Composable () -> Unit)? = if (!continueShown) null else ({
                val shownRecents = recents
                if (!shownRecents.isNullOrEmpty()) {
                    ContinueWriting(body, shownRecents, onOpen = { e, img -> if (prefsNow.value.tapPreviews) previewing = e else openFromShelf(e, LibraryOpen.cards[e.documentUri], coverTop = 128.dp, shown = img) })
                } else {
                    Spacer(Modifier.fillMaxWidth().height(1.dp))
                }
            })
            val gridTop: (LazyGridScope.() -> Unit)? = continueBlock?.let { c -> { item(key = "continue", span = { GridItemSpan(maxLineSpan) }, contentType = "continue") { c() } } }
            val covers = layout == ExplorerLayout.GALLERY
            // The shelf leads with a New note tile wherever a note can be made: a canvas when only canvases show.
            val onNewTile: (() -> Unit)? = if (flat || kindFilter == EntryKind.PDF) null else ({
                onCreateMode(if (kindFilter == EntryKind.CANVAS) CreateMode.CANVAS else CreateMode.FILE)
            })
            val shelfColumns = coverColumns(screenWidthDp, compactScreen, view.tileSize)
            when {
                // A folder or All notes keeps its shelf even when bare, since the New note tile is the way to fill it.
                covers && !flat && source != null -> CoversBody(
                    body, galleryState, shelfColumns, contextChips, gridTop, onNewTile,
                    // Until the library has been walked, a bare top folder says nothing rather than "empty".
                    hint = if (allNotes && index == null) null else empty?.let { if (it.offersCreate) stringResource(R.string.library_empty_hint) else it.text },
                    modifier = Modifier.fillMaxSize(),
                    stickyTabs = tabsBar, topPadding = 0.dp, hPad = hPad,
                )
                empty != null && !hasContinue -> Column(Modifier.fillMaxSize().padding(horizontal = hPad)) {
                    tabsBar()
                    contextChips(0.dp)
                    EmptyPane(empty.text, empty.art, if (!empty.offersCreate) null else { {
                        InkStrongButton(stringResource(R.string.new_note_menu), { onCreateMode(CreateMode.FILE) }, icon = Ph.notebook)
                        InkSecondaryButton(stringResource(R.string.new_canvas_menu), { onCreateMode(CreateMode.CANVAS) }, icon = Ph.infinity)
                        InkSecondaryButton(stringResource(R.string.import_pdf), calls.importPdf, icon = Ph.filePdf)
                    } }, body = empty.body)
                }
                covers -> CoversBody(body, galleryState, shelfColumns, contextChips, gridTop, null, null, Modifier.fillMaxSize(), stickyTabs = tabsBar, topPadding = 0.dp, hPad = hPad)
                layout == ExplorerLayout.GRID -> GridBody(body, gridState, gridColumns(screenWidthDp, view.tileSize), contextChips, gridTop, Modifier.fillMaxSize(), stickyTabs = tabsBar, topPadding = 0.dp, hPad = hPad)
                layout == ExplorerLayout.LIST -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    ListBody(
                        body, listState, wide = maxWidth >= 720.dp,
                        onSort = if (showRecent) null else ({ k -> setView(if (view.sortKey == k) view.copy(descending = !view.descending) else view.copy(sortKey = k, descending = k != ExplorerSortKey.NAME)) }),
                        chips = contextChips,
                        top = continueBlock?.let { c -> { item(key = "continue", contentType = "continue") { c() } } }, modifier = Modifier.fillMaxSize(),
                        stickyTabs = tabsBar, topPadding = 0.dp, hPad = hPad,
                    )
                }
                layout == ExplorerLayout.TIMELINE -> TimelineBody(body, arranged.orEmpty().filterNot { it.isDir }, shownMonth, { timelineMonth = it }, timelineState, contextChips, Modifier.fillMaxSize(), stickyTabs = tabsBar, topPadding = 0.dp, hPad = hPad)
                // Each column scrolls on its own, so here the tabs and chips stay put under the header.
                else -> Column(Modifier.fillMaxSize().padding(horizontal = hPad)) {
                    tabsBar()
                    contextChips(0.dp)
                    ColumnsBody(
                        body, root,
                        levels = listOf(rootDocId to (rootName ?: stringResource(R.string.folder))) + stack,
                        refreshKey = refreshKey,
                        arrange = columnsArrange,
                        picked = columnsPick,
                        onOpenFolder = { level, e ->
                            if (selection.isNotEmpty() || selectMode) { toggleSelect(e); return@ColumnsBody }
                            while (stack.size > level) stack.removeAt(stack.lastIndex)
                            stack.add(editor.browseDocId(e.documentUri) to e.name)
                            columnsPick = null
                        },
                        onPickFile = { level, e ->
                            if (selection.isNotEmpty() || selectMode) { toggleSelect(e); return@ColumnsBody }
                            while (stack.size > level) stack.removeAt(stack.lastIndex)
                            columnsPick = e
                        },
                        preview = { e -> FilePreview(body, e, pathText, previewActions(e), Modifier.fillMaxSize()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Floating stack of the dragged notes, the primary card centred on the finger and lifted. The finger is
            // read in the offset's layout block only, so following it never recomposes.
            val bc = boxCoords
            val sz = dragCardSize
            if (dragging && bc != null && sz != null && sz.width > 0) {
                val lift = with(density) { 24.dp.toPx() }
                DragPreview(editor, dragItems, sz) {
                    val pos = dragPos
                    if (pos == null || !bc.isAttached) IntOffset.Zero else {
                        val origin = bc.positionInWindow()
                        IntOffset((pos.x - origin.x - sz.width / 2f).roundToInt(), (pos.y - origin.y - sz.height / 2f - lift).roundToInt())
                    }
                }
            }
        }
    }

    // A multi-file pick gets no name prompt: it imports straight into the folder on screen, one file
    // at a time, taking each note's name from its source. This runs here because the explorer is
    // where the target folder is known. Cancelling keeps whatever already landed.
    val pendingImports = editor.pendingImports
    LaunchedEffect(pendingImports) {
        if (pendingImports.isNotEmpty()) {
            val n = editor.commitImportsAsync(root, currentDocId)
            refreshKey++
            if (n > 0) editor.message = context.resources.getQuantityString(R.plurals.imported_pdfs, n, n)
        }
    }

    val pendingImport = editor.pendingImport
    // Clear any stale error when a fresh name dialog opens for a new operation.
    LaunchedEffect(createMode, pendingImport) { fieldError = null }
    // A new notebook has its paper chosen before it is made: the New notebook sheet, not a bare name.
    val newNotebook = createMode == CreateMode.FILE && pendingImport == null
    if (newNotebook && !editor.importing) {
        NewNotebookSheet(
            editor,
            initialName = nextUntitled(editor, editor.cachedChildren(root, currentDocId)),
            folderName = stack.lastOrNull()?.second ?: rootName,
            onCreate = { n -> withContext(Dispatchers.IO) { editor.createBlankNoteFile(root, currentDocId, n) } },
            // Straight into the new notebook, as Starnote and GoodNotes do: it was made to be written in.
            onCreated = { uri -> onCreateMode(CreateMode.NONE); refreshKey++; calls.openFile(uri) },
            onDismiss = { onCreateMode(CreateMode.NONE) },
        )
    }
    // Name entry for a new canvas, new folder, or a pending PDF import. Hidden while an import is
    // actually being written, so only the "Importing…" sheet shows.
    if ((createMode != CreateMode.NONE || pendingImport != null) && !newNotebook && !editor.importing) {
        val isFolder = pendingImport == null && (createMode == CreateMode.FOLDER || createMode == CreateMode.ROOT_FOLDER)
        // The sidebar's + makes a folder at the top of the library, wherever the explorer stands.
        val folderParent = if (createMode == CreateMode.ROOT_FOLDER) rootDocId else currentDocId
        val default = when {
            pendingImport != null -> pendingImport.defaultName // import names default to the source file
            createMode == CreateMode.FILE || createMode == CreateMode.CANVAS ->
                nextUntitled(editor, editor.cachedChildren(root, currentDocId))
            else -> "" // new folder
        }
        val confirm: (String) -> Unit = { n ->
            when {
                pendingImport != null -> scope.launch {
                    // Land the import in the current folder; it opens only when the user taps it.
                    // commitImportAsync drives the "Importing…" sheet and runs the copy off-thread.
                    val uri = editor.commitImportAsync(root, currentDocId, n)
                    when {
                        uri != null -> refreshKey++
                        editor.pendingImport != null -> fieldError = context.getString( // genuine failure; keep the prompt
                            if (editor.lastImportError == PdfOpenError.PASSWORD) R.string.err_pdf_password else R.string.err_save_that_note,
                        )
                        // else: cancelled — the prompt already dismissed (pendingImport cleared)
                    }
                }
                isFolder -> scope.launch {
                    val ok = withContext(Dispatchers.IO) { editor.createFolder(root, folderParent, n) }
                    if (ok) { onCreateMode(CreateMode.NONE); refreshKey++ } else fieldError = context.getString(R.string.err_create_folder)
                }
                createMode == CreateMode.CANVAS -> scope.launch {
                    val uri = withContext(Dispatchers.IO) { editor.createBlankCanvasFile(root, currentDocId, n) }
                    if (uri != null) { onCreateMode(CreateMode.NONE); refreshKey++ } else fieldError = context.getString(R.string.err_create_canvas)
                }
                else -> scope.launch {
                    // Just create the note in the explorer — it opens only when the user taps it.
                    val uri = withContext(Dispatchers.IO) { editor.createBlankNoteFile(root, currentDocId, n) }
                    if (uri != null) { onCreateMode(CreateMode.NONE); refreshKey++ } else fieldError = context.getString(R.string.err_create_note)
                }
            }
        }
        val dismiss = { fieldError = null; if (pendingImport != null) editor.cancelImport() else onCreateMode(CreateMode.NONE) }
        val createKind = when {
            pendingImport != null -> CreateKind.IMPORT
            createMode == CreateMode.CANVAS -> CreateKind.CANVAS
            else -> null
        }
        if (createKind != null) {
            CreateNameSheet(
                kind = createKind,
                initial = default,
                folderName = stack.lastOrNull()?.second ?: rootName,
                error = fieldError,
                locked = createKind == CreateKind.IMPORT && fieldError != null && editor.lastImportError == PdfOpenError.PASSWORD,
                onConfirm = confirm,
                onDismiss = dismiss,
            )
        } else {
            NameDialog(
                title = if (isFolder) stringResource(R.string.new_folder) else stringResource(R.string.new_note),
                initial = default,
                confirmLabel = stringResource(R.string.create),
                placeholder = if (isFolder) stringResource(R.string.folder_name) else null,
                allowEmpty = !isFolder, // a folder needs a name; a blank note name becomes "untitled_N"
                error = fieldError,
                onConfirm = confirm,
                onDismiss = dismiss,
            )
        }
    }

    namingColor?.let { c -> ColorNameDialog(editor, c) { namingColor = null } }

    renaming?.let { entry ->
        NameDialog(
            title = if (entry.isDir) stringResource(R.string.rename_folder) else stringResource(R.string.rename_note),
            initial = entryLabel(entry),
            confirmLabel = stringResource(R.string.rename),
            allowEmpty = false,
            onConfirm = { raw ->
                val kind = DocumentKind.ofName(entry.name)
                val newName = if (entry.isDir || kind == null) raw else DocumentKind.withSuffix(DocumentKind.stripSuffix(raw), kind)
                // Renames touch the open-note binding (Compose state) so run on the main thread.
                val ok = editor.renameDocument(entry.documentUri, newName)
                renaming = null
                if (ok) {
                    followMarks(listOf(entry), entry.parentDocId, newName)
                    // Carry the colour code to the new name (sidecar is keyed by name), then re-list.
                    if (entry.color != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) { editor.moveItemColor(root, entry.parentDocId, entry.name, newName) }
                            refreshKey++
                        }
                    } else {
                        refreshKey++
                    }
                }
            },
            onDismiss = { renaming = null },
        )
    }

    moving?.let { items ->
        val movingFolders = remember(items) { items.filter { it.isDir }.map { editor.browseDocId(it.documentUri) } }
        FolderPickerDialog(
            editor, root, rootName ?: stringResource(R.string.folder),
            title = if (items.size == 1) stringResource(R.string.move_one_to, entryLabel(items.first())) else pluralStringResource(R.plurals.move_items_to, items.size, items.size),
            confirmLabel = stringResource(R.string.move_here),
            start = stack.toList(),
            blocked = { id -> movingFolders.any { com.xnotes.core.util.DocKeys.within(id, it) } },
            onPick = { target ->
                moving = null
                scope.launch {
                    val moved = withContext(Dispatchers.IO) { editor.moveEntriesInto(root, items, target) }
                    selection.clear(); refreshKey++
                    followMarks(items, target)
                    opError = if (moved < items.size) context.getString(R.string.err_move_some) else null
                }
            },
            onDismiss = { moving = null },
            subtitle = items.joinToString(", ") { entryLabel(it) },
        )
    }

    previewing?.let { e -> FilePreviewDialog(body, e, pathText.takeIf { !flat }, previewActions(e)) { previewing = null } }

    pendingDelete?.let { targets ->
        com.xnotes.ui.kit.InkConfirmSheet(
            title = stringResource(R.string.delete_confirm_title),
            message = if (targets.size == 1) stringResource(R.string.delete_one_confirm, entryLabel(targets.first()))
            else pluralStringResource(R.plurals.delete_items_confirm, targets.size, targets.size),
            confirmLabel = stringResource(R.string.delete),
            // Always for good: this sheet shows only when Trash is off or couldn't take the items.
            danger = true,
            confirmIcon = Ph.trash,
            onConfirm = {
                val items = targets.toList()
                pendingDelete = null; selection.clear(); opError = null
                scope.launch {
                    val allOk = withContext(Dispatchers.IO) {
                        var ok = true
                        items.forEach { e ->
                            if (editor.deleteDocument(e.documentUri)) {
                                // Drop its colour entry from the parent sidecar (a deleted folder's
                                // own sidecar goes with it).
                                if (e.color != null) editor.setItemColor(root, e.parentDocId, e.name, null)
                            } else {
                                ok = false
                            }
                        }
                        ok
                    }
                    refreshKey++
                    if (!allOk) opError = context.getString(R.string.err_delete_some)
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun entryLabel(entry: BrowseEntry): String =
    if (entry.isDir) entry.name else com.xnotes.core.util.DocumentKind.stripSuffix(entry.name)

/** A file's last-edited date for the line beneath its tile (relative, e.g. "2 days ago"). */
private fun entryDate(entry: BrowseEntry): String =
    if (entry.modified > 0)
        android.text.format.DateUtils.getRelativeTimeSpanString(
            entry.modified, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS,
        ).toString()
    else ""

/** The per-entry overflow menu. Files get the extra Share/Save-a-copy/Export block (pass [onShare]); folders don't. */
@Composable
internal fun EntryMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onCut: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onShare: (() -> Unit)? = null,
    onSaveCopy: (() -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    onColor: ((Rgba?) -> Unit)? = null,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onNameColor: (() -> Unit)? = null,
    onMoveTo: (() -> Unit)? = null,
    onPreview: (() -> Unit)? = null,
    deleteLabel: String = stringResource(R.string.delete),
    favourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    cover: CoverPick? = null,
) {
    val ink = LocalInk.current
    // "Colour code" and "Cover colour" swap the menu's contents for their picker until a choice is made;
    // closing the menu resets them so it always reopens on the main list.
    var showColors by remember { mutableStateOf(false) }
    var showCovers by remember { mutableStateOf(false) }
    // Only once a picker is up and the menu has closed: an effect on every tile's menu would start a coroutine per tile.
    if (!expanded && (showColors || showCovers)) LaunchedEffect(Unit) { showColors = false; showCovers = false }
    val more: @Composable () -> Unit = { Icon(Ph.caretRight, null, tint = ink.text2, modifier = Modifier.size(16.dp)) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.widthIn(min = 248.dp)) {
        if (showColors) {
            ColorCodeMenuContent { c -> onDismiss(); onColor?.invoke(c) }
        } else if (showCovers && cover != null) {
            CoverColorMenuContent(cover.current, cover.auto) { i -> onDismiss(); cover.onPick(i) }
        } else {
            if (onToggleFavourite != null) {
                InkMenuRow(stringResource(if (favourite) R.string.library_remove_favourite else R.string.library_add_favourite), { onDismiss(); onToggleFavourite() }, icon = if (favourite) Ph.heartFill else Ph.heart)
            }
            if (onPreview != null) InkMenuRow(stringResource(R.string.preview), { onDismiss(); onPreview() }, icon = Ph.eye)
            InkMenuRow(stringResource(R.string.rename), { onDismiss(); onRename?.invoke() }, icon = Ph.pencilSimple)
            if (onMoveTo != null) InkMenuRow(stringResource(R.string.move_to_folder_ellipsis), { onDismiss(); onMoveTo() }, icon = Ph.folderSimple)
            InkMenuRow(stringResource(R.string.copy), { onDismiss(); onCopy?.invoke() }, icon = Ph.copy)
            InkMenuRow(stringResource(R.string.cut), { onDismiss(); onCut?.invoke() }, icon = Ph.scissors)
            if (cover != null) InkMenuRow(stringResource(R.string.library_cover_colour), { showCovers = true }, icon = Ph.paintBucket, trailing = more)
            if (onColor != null) InkMenuRow(stringResource(R.string.colour_code), { showColors = true }, icon = Ph.palette, trailing = more)
            if (onNameColor != null) InkMenuRow(stringResource(R.string.name_colour_ellipsis), { onDismiss(); onNameColor() }, icon = Ph.textbox)
            if (onTogglePin != null) {
                InkMenuRow(stringResource(if (pinned) R.string.unpin_from_sidebar else R.string.pin_to_sidebar), { onDismiss(); onTogglePin() }, icon = if (pinned) Ph.pushPinSlash else Ph.pushPin)
            }
            InkMenuRow(deleteLabel, { onDismiss(); onDelete?.invoke() }, icon = Ph.trash, danger = true)
            if (onShare != null) {
                InkMenuDivider()
                InkMenuRow(stringResource(R.string.share), { onDismiss(); onShare() }, icon = Ph.shareNetwork)
                InkMenuRow(stringResource(R.string.save_copy_ellipsis), { onDismiss(); onSaveCopy?.invoke() }, icon = Ph.downloadSimple)
                InkMenuRow(stringResource(R.string.export_pdf), { onDismiss(); onExportPdf?.invoke() }, icon = Ph.filePdf)
            }
        }
    }
}

/** Slanted parallel lines that shade a colour-coded card without hiding what's on it. */
internal fun Modifier.colorHatch(color: Color): Modifier = drawBehind {
    val step = 10.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val faint = color.copy(alpha = 0.28f)
    var x = -size.height
    while (x < size.width) {
        drawLine(faint, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
        x += step
    }
}

/** Step each deeper card down-and-right by this much so the stack reads as a tidy pile. */
private val DRAG_STACK_STEP = 8.dp

/**
 * A neat stack of the dragged notes drawn as full-size tile cards (same size as the grid tiles, taken
 * from the picked-up tile), trailing the finger while they're moved onto a folder.
 */
@Composable
private fun DragPreview(editor: Editor, items: List<BrowseEntry>, sizePx: IntSize, at: () -> IntOffset) {
    val density = LocalDensity.current
    val cardW = with(density) { sizePx.width.toDp() }
    val cardH = with(density) { sizePx.height.toDp() }
    // At most three cards; the primary note sits on top of the pile, the rest peek out behind it.
    val shown = items.take(3)
    val spread = DRAG_STACK_STEP * shown.lastIndex.coerceAtLeast(0)
    Box(Modifier.offset { at() }.size(cardW + spread, cardH + spread)) {
        for (i in shown.indices.reversed()) {
            val shift = DRAG_STACK_STEP * i
            StackedNoteCard(
                editor, shown[i],
                Modifier.offset(shift, shift).size(cardW, cardH).alpha(if (i == 0) 1f else 0.97f),
            )
        }
    }
}

/** One raised card mirroring a file tile (thumbnail on paper, name, date), for the dragged stack. */
@Composable
private fun StackedNoteCard(editor: Editor, entry: BrowseEntry, modifier: Modifier) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val thumb = editor.cachedNoteTile(entry.documentUri)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.inkSurface(shape, InkElevation.FLOAT)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(palette.paper.toComposeColor())) {
            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.matchParentSize())
            else Icon(Ph.fileText, null, tint = ink.text3, modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(entryLabel(entry), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = entryDate(entry)
            if (date.isNotEmpty()) Text(date, style = InkType.meta, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- shared bits ---

/**
 * Asks for a single name, used for new folders, renames and Name this colour, as the B2 name sheet
 * ([InkNameSheet]). Pre-fills [initial] (fully selected so typing replaces it), confirms on the
 * keyboard's Done action or hardware Enter, and dismisses on Cancel, the scrim, or Esc. When
 * [allowEmpty] is false the confirm button stays disabled until something is typed; a non-null
 * [error] shows under the field and keeps the sheet open after a failed operation. A non-null
 * [onRemove] adds a red "Remove name" at the left; [leadingColor] puts a colour dot in the field and
 * [note] a line under it.
 */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    placeholder: String? = null,
    allowEmpty: Boolean,
    error: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    leadingColor: Color? = null,
    note: String? = null,
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val confirm = {
        val n = text.text.trim()
        if (allowEmpty || n.isNotEmpty()) onConfirm(n)
    }
    InkNameSheet(
        title, text, { text = it }, confirmLabel, placeholder,
        confirmEnabled = allowEmpty || text.text.isNotBlank(),
        error = error, focus = focus, onConfirm = confirm, onDismiss = onDismiss, onRemove = onRemove,
        leadingColor = leadingColor, note = note,
    )
}

/** What an empty explorer body says: its title, an optional line of help, the picture, and whether it offers to make something. */
private class EmptyState(val text: String, val art: EmptyArt? = null, val offersCreate: Boolean = false, val body: String? = null)

private val EmptyQuiet = InkType.body.copy(fontWeight = FontWeight.Bold, lineHeight = 19.sp)
private val FirstRunBody = InkType.body.copy(fontSize = 16.sp, lineHeight = 24.sp)

/**
 * An empty body (.empty): the kit's [InkEmptyState] (picture, bold title, optional line of [body]) and the ways to
 * fill it, centred; it scrolls when the window is short. The actions wrap below the 380dp column rather than squeeze
 * into it. Small states (no picture, no actions, no body, such as "Loading…") stay one quiet line, as Frame 5 asks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyPane(text: String, art: EmptyArt? = null, actions: (@Composable FlowRowScope.() -> Unit)? = null, body: String? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (art == null && actions == null && body == null) {
                Text(text, style = EmptyQuiet, color = LocalInk.current.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
            } else {
                InkEmptyState(text, art = art?.let { picture -> { EmptyIllustration(picture) } }, body = body)
            }
            if (actions != null) {
                FlowRow(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions,
                )
            }
        }
    }
}
