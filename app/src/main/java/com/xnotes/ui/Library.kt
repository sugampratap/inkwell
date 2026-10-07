package com.xnotes.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.util.DocKeys
import com.xnotes.settings.LiveSettings
import com.xnotes.settings.Settings
import com.xnotes.settings.SettingsRepository
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.HeartToggle
import com.xnotes.ui.kit.InkMenuHeader
import com.xnotes.ui.kit.ProgressLine
import com.xnotes.ui.kit.SelectCheck
import com.xnotes.ui.kit.inkSelectionRing
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

// --- what the library knows beyond the folder it is showing ---

/**
 * The cloth colours a notebook cover can take. A notebook the user never coloured takes one by the hash of its name,
 * so it keeps its colour from launch to launch and on every device; picking one stores its index here.
 */
internal object CoverPalette {
    /** Every cover colour there has ever been. Append only: stored picks are indices into this list. */
    val colors: List<Color> = listOf(
        0xFF2F4858, 0xFFC9A66B, 0xFF7A9E7E, 0xFF8E5A6B, 0xFF3D5A80,
        0xFFD8CFC0, 0xFF5C6B73, 0xFFB5654A, 0xFF4E6E5D, 0xFF7D6B91,
        // B2, Frame 1 and the motion sheet: navy, teal, sage, mustard, terracotta, blush.
        0xFF2E3F5C, 0xFF2F6F82, 0xFF7FA37A, 0xFFD9A441, 0xFFC8664A, 0xFFE3A79A,
    ).map { Color(it) }

    /**
     * What the pickers offer and Automatic chooses from, as indices into [colors], in the pickers' two rows of five.
     * B2's warm set, with no purple or mauve; Plum (3) and Heather (9) stay drawable for notebooks already wearing them.
     */
    val offered: List<Int> = listOf(10, 11, 12, 8, 13, 1, 14, 15, 5, 6)

    /** The colour a notebook named [name] wears until one is picked: stable, since String.hashCode is specified. */
    fun autoIndex(name: String): Int =
        offered[Math.floorMod(com.xnotes.core.util.DocumentKind.stripSuffix(name).lowercase().hashCode(), offered.size)]

    fun colorFor(name: String, picked: Int?): Color = colors[picked?.takeIf { it in colors.indices } ?: autoIndex(name)]
}

/** The key a document is starred or coloured under: its provider and id, however its uri was reached. */
internal fun libraryKey(uri: String): String = runCatching {
    val u = android.net.Uri.parse(uri)
    "${u.authority}|${android.provider.DocumentsContract.getDocumentId(u)}"
}.getOrDefault(uri)

/**
 * Favourites and picked cover colours, kept in [Settings] beside everything else the device remembers.
 * Written through [LiveSettings] so the editors' own writes carry them along rather than over them.
 * One per process, like the settings it mirrors. Main thread.
 */
@Stable
internal class LibraryMarks private constructor(private val repo: SettingsRepository) {

    var favourites: Set<String> by mutableStateOf(LiveSettings.get(repo).favourites.toSet())
        private set
    var covers: Map<String, Int> by mutableStateOf(LiveSettings.get(repo).coverColors)
        private set

    /** Uri to key, since tiles ask on every frame they draw and parsing a uri is not free. */
    private val keys = HashMap<String, String>()

    private fun keyOf(uri: String): String = keys.getOrPut(uri) { libraryKey(uri) }

    fun isFavourite(uri: String): Boolean = keyOf(uri) in favourites

    fun toggleFavourite(uri: String) = setFavourite(listOf(uri), !isFavourite(uri))

    /** Stars [uris] (or unstars them with [on] false), in one write. */
    fun setFavourite(uris: List<String>, on: Boolean) {
        val ks = uris.map { keyOf(it) }
        val next = if (on) favourites + ks else favourites - ks.toSet()
        if (next == favourites) return
        favourites = next
        save { it.copy(favourites = next.toList()) }
    }

    /** The cover colour picked for [uri], as an index into [CoverPalette], or null for the automatic one. */
    fun coverIndex(uri: String): Int? = covers[keyOf(uri)]?.takeIf { it in CoverPalette.colors.indices }

    fun setCover(uri: String, index: Int?) {
        val k = keyOf(uri)
        val next = if (index == null) covers - k else covers + (k to index)
        if (next == covers) return
        covers = next
        save { it.copy(coverColors = next) }
    }

    /** Carries the marks on [fromUri], and on everything under it, to where it now lies at [toUri]. */
    fun moved(fromUri: String, toUri: String) {
        val from = libraryKey(fromUri)
        val to = libraryKey(toUri)
        if (from == to) return
        val favs = favourites.map { DocKeys.moved(it, from, to) ?: it }.toSet()
        val cov = covers.entries.associate { (k, v) -> (DocKeys.moved(k, from, to) ?: k) to v }
        if (favs == favourites && cov == covers) return
        favourites = favs
        covers = cov
        keys.clear()
        save { it.copy(favourites = favs.toList(), coverColors = cov) }
    }

    /** Small and rare, so written at once, as pinning a folder is. */
    private fun save(change: (Settings) -> Settings) {
        val s = change(LiveSettings.get(repo))
        LiveSettings.set(s)
        repo.save(s)
    }

    companion object {
        @Volatile private var shared: LibraryMarks? = null

        fun of(context: Context): LibraryMarks =
            shared ?: synchronized(this) { shared ?: LibraryMarks(SettingsRepository(context)).also { shared = it } }
    }
}

@Composable
internal fun rememberLibraryMarks(): LibraryMarks {
    val context = LocalContext.current
    return remember { LibraryMarks.of(context) }
}

/** A top-level folder for the sidebar: the folder, its document id, and how many notes lie anywhere under it. */
internal class LibraryFolder(val entry: BrowseEntry, val docId: String, val count: Int)

/** Every note in the library at once, as "All notes" lists it, with what the sidebar counts from it. */
internal class LibraryIndex(
    val files: List<BrowseEntry>,
    /** Names of the folders the notes sit in, by document id; the top folder itself is left out. */
    val folderNames: Map<String, String>,
    val folders: List<LibraryFolder>,
    private val topOf: Map<String, String>,
) {
    /** The top-level folder [docId] sits in (itself, when it is one), or null at the top. */
    fun topFolderOf(docId: String?): String? = docId?.let { topOf[it] }
}

/** Walks the whole library from [root], one listing per folder. IO. */
internal fun walkLibrary(editor: Editor, root: String): LibraryIndex {
    val rootId = editor.browseRootDocId(root)
    val files = ArrayList<BrowseEntry>()
    val names = HashMap<String, String>()
    val topOf = HashMap<String, String>()
    val counts = HashMap<String, Int>()
    val tops = ArrayList<Pair<BrowseEntry, String>>()
    val seen = HashSet<String>()
    val queue = ArrayDeque<String>().apply { addLast(rootId) }
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!seen.add(id)) continue // guard against any cyclic SAF links
        val top = topOf[id]
        for (e in editor.browseChildren(root, id)) {
            if (e.isDir) {
                val child = editor.browseDocId(e.documentUri)
                names[child] = e.name
                topOf[child] = top ?: child
                if (top == null) tops.add(e to child)
                queue.addLast(child)
            } else {
                files.add(e)
                if (top != null) counts[top] = (counts[top] ?: 0) + 1
            }
        }
    }
    val folders = tops.sortedBy { it.first.name.lowercase() }.map { (e, id) -> LibraryFolder(e, id, counts[id] ?: 0) }
    return LibraryIndex(files, names, folders, topOf)
}

/**
 * What the sidebar and the explorer share: the library's index, a tick to have it walked again after a
 * change, and the search the sidebar's field types into.
 */
@Stable
internal class LibraryFeed {
    var index: LibraryIndex? by mutableStateOf<LibraryIndex?>(null)
    var tick by mutableIntStateOf(0)
        private set
    var query by mutableStateOf("")

    fun changed() { tick++ }
}

/**
 * A note on the shelf (.tile): its cover with the tag badge (top-left, past the spine) and, when it is a favourite,
 * the heart; under it the title (15/600) with ⋮, then what it is and when. Select mode hides the heart and shows
 * the corner check. The press shrinks only the cover's layer (.98).
 */
@Composable
internal fun CoverTile(b: ExplorerBody, e: BrowseEntry) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val selected = b.host.isSelected(e)
    val kind = b.kind(e)
    val marks = b.marks
    val cut = b.host.isCut(e)
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .graphicsLayer { alpha = (if (cut) 0.4f else 1f) * b.fade() }
            .clickable(src, indication = null, role = Role.Button) { b.host.onClick(e) }
            .then(if (selecting) Modifier.semantics { this.selected = selected } else Modifier)
            .registered(b, e),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(COVER_RATIO).pressScale(src, 0.98f).inkSelectionRing(selected, coverShape(kind))) {
            CoverArt(b.editor, e, kind, marks?.coverIndex(e.documentUri), b.label(e), Modifier.fillMaxSize(), cached = true)
            b.colorOf(e)?.let { c ->
                TagBadge(
                    codeTint(c, palette), b.editor.colorNames[c],
                    Modifier.align(Alignment.TopStart).padding(start = if (kind == EntryKind.NOTE) 18.dp else 10.dp, top = 10.dp).widthIn(max = 140.dp),
                )
            }
            if (!selecting && marks != null && marks.isFavourite(e.documentUri)) {
                HeartToggle(true, { marks.toggleFavourite(e.documentUri) }, stringResource(R.string.library_remove_favourite), Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
            }
            if (selecting) SelectCheck(selected, Modifier.align(Alignment.BottomEnd).padding(8.dp), b.host.selectShown)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.label(e), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!selecting) EntryMenuButton(b, e, ink.text2, size = 32.dp, modifier = Modifier.offset(x = 8.dp))
        }
        CoverMeta(b, e, kind)
    }
}

/** The line under a cover (.tm): a canvas or PDF mark, then "12 pages · Today", "Canvas · Today"; Recent's opened time instead. */
@Composable
private fun CoverMeta(b: ExplorerBody, e: BrowseEntry, kind: EntryKind) {
    val ink = LocalInk.current
    Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        when (kind) {
            EntryKind.CANVAS -> Icon(Ph.infinity, null, tint = ink.text2, modifier = Modifier.size(15.dp))
            EntryKind.PDF -> Icon(Ph.filePdf, null, tint = ink.text2, modifier = Modifier.size(15.dp))
            else -> {}
        }
        val pages = b.pages(e)
        val what = when {
            kind == EntryKind.CANVAS -> stringResource(R.string.kind_canvas)
            pages > 0 -> pluralStringResource(R.plurals.pages_count, pages, pages)
            else -> null
        }
        val text = b.metaOverride?.invoke(e) ?: dotJoined(listOfNotNull(what, b.whenText(e, withTime = false).ifEmpty { null }))
        Text(text, style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** [parts] joined by the library's separator ("12 pages · Today"), through a string resource so a language can order it. */
@Composable
internal fun dotJoined(parts: List<String>): String = dotJoin(stringResource(R.string.library_dot_join), parts)

/** The shelf's first tile (.newcv): a 1.5dp dashed --line3 outline (--text2 while held), a 52dp plus disc and "New note" in bold. */
@Composable
internal fun NewNoteTile(label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val held by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_RATIO)
            .pressScale(src, 0.98f)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val w = 1.5.dp.toPx()
                val dashed = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 3 * w)))
                onDrawBehind { drawOutline(outline, if (held) ink.text2 else ink.line3, style = dashed) }
            }
            .clip(shape)
            .clickable(src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).background(ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ph.plus, null, tint = ink.text, modifier = Modifier.size(24.dp))
            }
            Text(label, style = InkType.button, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The cover shelf (.grid). Whatever [top] puts first (Continue writing), then (with [stickyTabs]) a 16dp gap and the
 * category tabs, which stick under the header; then the contextual [chips], 20dp, the folder row, the New note tile
 * and the covers. 24dp gaps (16 on phones); each tile carries its own row gap, so full-width rows keep the mockup's
 * spacing. [hint] says why the shelf is bare.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CoversBody(
    b: ExplorerBody,
    state: LazyGridState,
    columns: Int,
    chips: @Composable (Dp) -> Unit,
    top: (LazyGridScope.() -> Unit)?,
    onNew: (() -> Unit)?,
    hint: String?,
    modifier: Modifier = Modifier,
    stickyTabs: (@Composable () -> Unit)? = null,
    topPadding: Dp = EXPLORER_HEADER,
    hPad: Dp = 0.dp,
) {
    val ink = LocalInk.current
    val newLabel = stringResource(R.string.library_new_note)
    val gap = if (hPad < 32.dp) 16.dp else 24.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        // The mockup's 56px foot (.grid), so the last row of covers clears the screen's edge.
        contentPadding = PaddingValues(start = hPad, end = hPad, top = topPadding, bottom = 56.dp),
    ) {
        top?.invoke(this)
        if (stickyTabs != null) {
            item(key = "tabsGap", span = { GridItemSpan(maxLineSpan) }, contentType = "gap") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "tabs", contentType = "tabs") { stickyTabs() }
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") { chips(52.dp) }
        item(key = "shelfGap", span = { GridItemSpan(maxLineSpan) }, contentType = "gap") { Spacer(Modifier.height(20.dp)) }
        if (b.folders.isNotEmpty()) {
            items(b.folders, key = { it.documentUri }, contentType = { "folder" }) { Box(Modifier.padding(bottom = gap)) { FolderChipTile(b, it, height = 56.dp) } }
        }
        if (onNew != null) item(key = "new", contentType = "new") { Box(Modifier.padding(bottom = gap)) { NewNoteTile(newLabel, onNew) } }
        b.groups.forEach { g ->
            items(g.items, key = { it.documentUri }, contentType = { if (it.isDir) "folderCard" else b.coverType(it) }) { e ->
                Box(Modifier.padding(bottom = gap)) { if (e.isDir) FolderCardTile(b, e) else CoverTile(b, e) }
            }
        }
        if (hint != null) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }, contentType = "hint") {
                Text(hint, style = InkType.body, color = ink.text2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

// --- Continue writing ---

/** "Continue writing" (.h-sec) and up to three listing cards (.cw): a tap goes straight back in. */
@Composable
internal fun ContinueWriting(b: ExplorerBody, recents: List<RecentEntry>, onOpen: (BrowseEntry, ImageBitmap?) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.library_continue), style = InkType.title, color = ink.text, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp).semantics { heading() })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 560.dp
            val slots = if (narrow) 2 else 3
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 24.dp)) {
                val shown = recents.take(slots)
                shown.forEach { r -> key(r.entry.documentUri) { ContinueCard(b, r, Modifier.weight(1f)) { img -> onOpen(r.entry, img) } } }
                // Empty slots keep each card the width it would have in a full row.
                repeat(slots - shown.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One card: the page the note was left on (r16, 128dp, a 7% inset edge), its tag badge and heart; the title (15/600);
 * "Page 7 of 12 · 4 min ago" (PDFs lead with "PDF", canvases say "Canvas"); and a 4dp progress line for a paged note
 * whose page is known. The progress doesn't animate, so ProgressLine's Float is enough.
 */
@Composable
private fun ContinueCard(b: ExplorerBody, r: RecentEntry, modifier: Modifier, onClick: (ImageBitmap?) -> Unit) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val e = r.entry
    val meta = b.meta(e) ?: b.editor.cachedMeta(e)
    val kind = entryKind(e, meta)
    val pages = meta?.pages ?: 0
    // Read afresh whenever a note opens or closes: closing one is when the page it was left on is saved.
    val lastPage = remember(e.documentUri, e.modified, b.editor.noteOpen) { b.editor.lastPageOf(e.documentUri) }
    val page = if (kind == EntryKind.CANVAS) null else continuePage(lastPage, pages)
    val img = rememberContinueImage(b.editor, e, page ?: 0)
    val marks = b.marks
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    DisposableEffect(e.documentUri) { onDispose { LibraryOpen.cards.remove(e.documentUri) } }
    Column(
        modifier
            .pressScale(src, 0.98f)
            // The opening grows from the page this card shows.
            .clickable(src, indication = null, role = Role.Button) { onClick(img) }
            .onPlaced { LibraryOpen.cards[e.documentUri] = it },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(shape)
                .background(
                    when (kind) {
                        EntryKind.CANVAS -> CANVAS_PAPER
                        EntryKind.PDF -> Color.White
                        else -> palette.paper.toComposeColor()
                    },
                )
                .drawWithContent {
                    drawContent()
                    val w = 1.dp.toPx()
                    drawRoundRect(Color.Black.copy(alpha = 0.07f), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(16.dp.toPx() - w / 2), style = Stroke(w))
                },
        ) {
            if (img != null) {
                Image(img, null, contentScale = ContentScale.Crop, alignment = if (kind == EntryKind.CANVAS) Alignment.Center else Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
            b.colorOf(e)?.let { c -> TagBadge(codeTint(c, palette), b.editor.colorNames[c], Modifier.align(Alignment.TopStart).padding(10.dp).widthIn(max = 160.dp)) }
            if (marks != null) {
                val fav = marks.isFavourite(e.documentUri)
                HeartToggle(
                    fav, { marks.toggleFavourite(e.documentUri) },
                    stringResource(if (fav) R.string.library_remove_favourite else R.string.library_add_favourite),
                    Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp),
                )
            }
        }
        Text(b.label(e), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        val ago = formatWhen(b.words, r.opened, b.now, "relative", false, b.clock24, b.zone)
        val canvasWord = stringResource(R.string.kind_canvas)
        val pdfWord = stringResource(R.string.library_pdf_short)
        val pageWords = if (page != null) stringResource(R.string.library_page_of, page + 1, pages) else null
        val pagesWords = if (page == null && kind != EntryKind.CANVAS && pages > 0) pluralStringResource(R.plurals.pages_count, pages, pages) else null
        val parts = listOfNotNull(
            when (kind) {
                EntryKind.CANVAS -> canvasWord
                EntryKind.PDF -> pdfWord
                else -> null
            },
            pageWords ?: pagesWords,
            ago.ifEmpty { null },
        )
        Text(dotJoined(parts), style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Static, as the mockup draws it: an animated fraction here would recompose the card every frame.
        continueProgress(page, pages)?.let { ProgressLine(it, Modifier.padding(top = 8.dp)) }
    }
}

/** [page] of [e] for its card, seeded from memory and rendered off the main thread when missing. */
@Composable
private fun rememberContinueImage(editor: Editor, e: BrowseEntry, page: Int): ImageBitmap? = key(editor.thumbnailVersion) {
    val seed = if (page > 0) editor.cachedContinueThumb(e.documentUri, page, e.modified) else editor.cachedPageThumb(e.documentUri)
    val img by produceState(seed, e.documentUri, e.modified, page) {
        value = editor.continueThumbnail(e.documentUri, e.name, page, e.modified) ?: value
    }
    img
}

/** A notebook's cover colour as its menu offers it: the one picked (null for automatic), the automatic one, and how to pick. */
internal class CoverPick(val current: Int?, val auto: Int, val onPick: (Int?) -> Unit)

/**
 * The cover colour picker inside a notebook's menu: Automatic (with the colour its name gives it), then the offered
 * cloths as kit swatches (2dp gap ring when chosen). A notebook already wearing a colour no longer offered sees it first.
 */
@Composable
internal fun CoverColorMenuContent(current: Int?, autoIndex: Int, onPick: (Int?) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.widthIn(min = 248.dp).padding(bottom = 10.dp)) {
        InkMenuHeader(stringResource(R.string.library_cover_colour))
        Row(
            // A full-bleed menu row (.m-row): clipped to its own bounds, so its press stays in the row.
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RectangleShape).selectable(current == null, role = Role.RadioButton) { onPick(null) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The row is the one target: its swatch only shows the colour.
            InkSwatch(CoverPalette.colors[autoIndex], current == null, 22.dp, onClick = null)
            Text(stringResource(R.string.library_cover_auto), style = if (current == null) InkType.row.copy(fontWeight = FontWeight.Bold) else InkType.row, color = ink.text)
        }
        val shown = listOfNotNull(current?.takeIf { it !in CoverPalette.offered && it in CoverPalette.colors.indices }) + CoverPalette.offered
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { i -> InkSwatch(CoverPalette.colors[i], current == i, 30.dp) { onPick(i) } }
                }
            }
        }
    }
}
