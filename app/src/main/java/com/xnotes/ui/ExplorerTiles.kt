package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.util.DocumentKind
import com.xnotes.platform.DocMetaStore
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.ThumbShape
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkRowCheck
import com.xnotes.ui.kit.SelectCheck
import com.xnotes.ui.kit.inkSelectionRing
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import java.time.ZoneId

/** What tiles do when they're used: every callback an explorer tile reaches for, built once per folder tree. */
@Stable
internal class TileHost(
    val isSelected: (BrowseEntry) -> Boolean,
    val selecting: () -> Boolean,
    val isCut: (BrowseEntry) -> Boolean,
    val isPinned: (BrowseEntry) -> Boolean,
    val isDropTarget: (BrowseEntry) -> Boolean,
    val isPulsing: (BrowseEntry) -> Boolean,
    val onPulseDone: (BrowseEntry) -> Unit,
    val onClick: (BrowseEntry) -> Unit,
    val onLongClick: (BrowseEntry) -> Unit,
    val onPlaced: (BrowseEntry, LayoutCoordinates?) -> Unit,
    val menu: EntryActions,
    /** How far select mode's marks have faded in (0 to 1), one value for every tile; read only while drawing. */
    val selectShown: () -> Float = { 1f },
)

/** The overflow menu's actions for one entry. */
@Stable
internal class EntryActions(
    val rename: (BrowseEntry) -> Unit,
    val copy: (BrowseEntry) -> Unit,
    val cut: (BrowseEntry) -> Unit,
    val moveTo: (BrowseEntry) -> Unit,
    val delete: (BrowseEntry) -> Unit,
    val color: (BrowseEntry, Rgba?) -> Unit,
    val nameColor: (Rgba) -> Unit,
    val togglePin: (BrowseEntry) -> Unit,
    val share: (BrowseEntry) -> Unit,
    val saveCopy: (BrowseEntry) -> Unit,
    val exportPdf: (BrowseEntry) -> Unit,
    val preview: (BrowseEntry) -> Unit,
)

/** Everything an explorer layout draws from: the view, what's in it, and how to describe each item. */
@Stable
internal class ExplorerBody(
    val editor: Editor,
    val view: ExplorerView,
    /** Folders drawn in their own row above the files; empty when they're mixed in or hidden. */
    val folders: List<BrowseEntry>,
    val groups: List<EntryGroup>,
    val metas: Map<String, DocMetaStore.Meta?>,
    /** Item counts for folders, by uri; empty when folder counts are off. */
    val counts: Map<String, Int>,
    val now: Long,
    val clock24: Boolean,
    val dateStyle: String,
    val showExtensions: Boolean,
    /** What the menus call deleting: "Move to trash" while Trash is on. */
    val deleteLabel: String,
    val host: TileHost,
    val words: ExplorerWords,
    /** A line to show under an item in place of its date, as Recent does with when it was opened. */
    val metaOverride: ((BrowseEntry) -> String?)? = null,
    /** The folder each item sits in, shown where items come from many folders. */
    val whereOf: ((BrowseEntry) -> String?)? = null,
    /** Favourites and cover colours, for the library's covers and menus; null leaves both out. */
    val marks: LibraryMarks? = null,
    /** The shelf's cross-fade (Task 14's tab change, 0..1), read by every tile in its layer; 1 when nothing fades. */
    val fade: () -> Float = { 1f },
) {
    val zone: ZoneId = ZoneId.systemDefault()

    fun label(e: BrowseEntry): String = if (e.isDir || showExtensions) e.name else DocumentKind.stripSuffix(e.name)

    fun meta(e: BrowseEntry): DocMetaStore.Meta? = metas[e.documentUri]

    fun kind(e: BrowseEntry): EntryKind = entryKind(e, meta(e))

    fun pages(e: BrowseEntry): Int = meta(e)?.pages ?: 0

    fun whenText(e: BrowseEntry, withTime: Boolean = view.showTime): String =
        formatWhen(words, if (view.sortKey == ExplorerSortKey.CREATED) e.created else e.modified, now, dateStyle, withTime, clock24, zone)

    /** The line under a tile's name: its date, and its size when tiles show sizes. */
    fun metaText(e: BrowseEntry): String {
        metaOverride?.invoke(e)?.let { return it }
        if (e.isDir) return counts[e.documentUri]?.let { itemsLabel(words, it) } ?: whenText(e)
        return listOfNotNull(whenText(e).ifEmpty { null }, formatSize(e.size).takeIf { view.showSize }).joinToString(" · ")
    }

    fun colorOf(e: BrowseEntry): Rgba? = e.color.takeIf { view.showColour }

    /** Each file's reuse type on the cover shelf ([coverContentType]), worked out once per body: the grid asks on every pass. Main thread. */
    private val coverTypes = HashMap<String, Any>()

    fun coverType(e: BrowseEntry): Any = coverTypes.getOrPut(e.documentUri) { coverContentType(e.name, kind(e), marks?.coverIndex(e.documentUri)) }
}

internal fun entryKind(e: BrowseEntry, meta: DocMetaStore.Meta?): EntryKind = when {
    e.isDir -> EntryKind.FOLDER
    DocumentKind.ofName(e.name) == DocumentKind.CANVAS -> EntryKind.CANVAS
    meta?.pdf == true -> EntryKind.PDF
    else -> EntryKind.NOTE
}

internal fun kindIcon(k: EntryKind): ImageVector = when (k) {
    EntryKind.FOLDER -> Ph.folderSimple
    EntryKind.NOTE -> Ph.notebook
    EntryKind.PDF -> Ph.filePdf
    EntryKind.CANVAS -> Ph.infinity
}

@Composable
internal fun kindLabel(k: EntryKind): String = when (k) {
    EntryKind.FOLDER -> stringResource(R.string.folder)
    EntryKind.NOTE -> stringResource(R.string.kind_note)
    EntryKind.PDF -> stringResource(R.string.kind_pdf_note)
    EntryKind.CANVAS -> stringResource(R.string.kind_canvas)
}

/** A document's first page: the top cropped to fill [shape]'s square, or the whole page fitted in. */
@Composable
internal fun EntryThumb(editor: Editor, entry: BrowseEntry, shape: ThumbShape, modifier: Modifier = Modifier) {
    val whole = shape == ThumbShape.PAGE
    ThumbImage(rememberThumb(editor, entry, whole), whole, modifier)
}

/** The top of [entry]'s first page as a square, or with [whole] the page entire; seeded from memory, null until loaded. */
@Composable
internal fun rememberThumb(editor: Editor, entry: BrowseEntry, whole: Boolean): ImageBitmap? = key(editor, editor.thumbnailVersion) {
    val thumb by produceState<ImageBitmap?>(
        if (whole) editor.cachedPageThumb(entry.documentUri) else editor.cachedNoteTile(entry.documentUri),
        entry.documentUri, entry.modified, whole,
    ) {
        value = if (whole) editor.pageThumbnail(entry.documentUri, entry.name) else editor.tileThumbnail(entry.documentUri, entry.name)
    }
    thumb
}

/** Width over height of an A4 page upright, and of the landscape frame a canvas is drawn in. */
internal const val A4_RATIO = 100f / 141f
internal const val CANVAS_RATIO = 141f / 100f

/** A page's width over height from its rendered thumbnail, or [fallback] until that has loaded. */
internal fun pageRatio(img: ImageBitmap?, fallback: Float): Float = img?.let { it.width.toFloat() / it.height } ?: fallback

/** The largest size with a page's shape ([ratio], width over height) that fits within [maxW] by [maxH]. */
internal fun pageFit(ratio: Float, maxW: Dp, maxH: Dp): DpSize =
    if (maxW / maxH > ratio) DpSize(maxH * ratio, maxH) else DpSize(maxW, maxW / ratio)

/** A thumbnail on the page colour: cropped to its top, or with [whole] fitted in entire; a file icon until it loads. */
@Composable
internal fun ThumbImage(img: ImageBitmap?, whole: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier.background(palette.paper.toComposeColor())) {
        if (img != null) {
            Image(
                img, null,
                contentScale = if (whole) ContentScale.Fit else ContentScale.Crop,
                alignment = if (whole) Alignment.Center else Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Ph.fileText, null, tint = LocalInk.current.text3, modifier = Modifier.size(28.dp).align(Alignment.Center))
        }
    }
}

/**
 * The kind and page-count badges a thumbnail carries, as far as the view shows them. Both ride the top edge, so
 * the select check owns the bottom-right corner.
 */
@Composable
internal fun ThumbBadges(b: ExplorerBody, e: BrowseEntry, inset: Dp = 10.dp) {
    Box(Modifier.fillMaxSize().padding(inset)) {
        val kind = b.kind(e)
        if (b.view.showKind && (kind == EntryKind.PDF || kind == EntryKind.CANVAS)) {
            TileBadge(kindIcon(kind), if (kind == EntryKind.PDF) stringResource(R.string.library_pdf_short) else stringResource(R.string.kind_canvas), Modifier.align(Alignment.TopStart))
        }
        val pages = b.pages(e)
        if (b.view.showPages && pages > 1) TileBadge(Ph.files, "$pages", Modifier.align(Alignment.TopEnd))
    }
}

/** An entry's ⋮: opens its menu, named for the entry it is for. */
@Composable
internal fun EntryMenuButton(b: ExplorerBody, e: BrowseEntry, tint: Color, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        InkIconButton(Ph.dotsThreeVertical, stringResource(R.string.more_for, b.label(e)), { open = true }, size = size, iconSize = 20.dp, tint = tint)
        EntryMenuFor(b, e, open) { open = false }
    }
}

/** An entry's overflow menu, with the file-only block for notes and canvases and the pin toggle for folders. */
@Composable
internal fun EntryMenuFor(b: ExplorerBody, e: BrowseEntry, expanded: Boolean, onDismiss: () -> Unit) {
    val m = b.host.menu
    val marks = b.marks
    EntryMenu(
        expanded, onDismiss,
        onRename = { m.rename(e) }, onCopy = { m.copy(e) }, onCut = { m.cut(e) }, onDelete = { m.delete(e) },
        onShare = if (e.isDir) null else ({ m.share(e) }),
        onSaveCopy = { m.saveCopy(e) },
        onExportPdf = { m.exportPdf(e) },
        onColor = { c -> m.color(e, c) },
        pinned = e.isDir && b.host.isPinned(e),
        onTogglePin = if (e.isDir) ({ m.togglePin(e) }) else null,
        onNameColor = e.color?.let { c -> { m.nameColor(c) } },
        onMoveTo = { m.moveTo(e) },
        onPreview = if (e.isDir) null else ({ m.preview(e) }),
        deleteLabel = b.deleteLabel,
        favourite = marks != null && !e.isDir && marks.isFavourite(e.documentUri),
        onToggleFavourite = if (marks == null || e.isDir) null else ({ marks.toggleFavourite(e.documentUri) }),
        cover = if (marks == null || b.kind(e) != EntryKind.NOTE) null else CoverPick(marks.coverIndex(e.documentUri), CoverPalette.autoIndex(e.name)) { marks.setCover(e.documentUri, it) },
    )
}

/** Keeps a tile's coordinates registered for hit-testing a drag, and drops them when it leaves. */
@Composable
internal fun Modifier.registered(b: ExplorerBody, e: BrowseEntry): Modifier {
    DisposableEffect(e.documentUri) { onDispose { b.host.onPlaced(e, null) } }
    return this.onPlaced { b.host.onPlaced(e, it) }
}

private val TileShape = RoundedCornerShape(14.dp)
private val RowBold = InkType.row.copy(fontWeight = FontWeight.Bold)

/** The meta and small roles with tabular figures, made once rather than on every tile's composition. */
internal val MetaNum = InkType.meta.tnum()
internal val SmallNum = InkType.small.tnum()

/** A file in the grid: the thumbnail (r14, a --line2 or colour-code edge) with its badges, then the name, date and ⋮. */
@Composable
internal fun GridFileTile(b: ExplorerBody, e: BrowseEntry) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val selected = b.host.isSelected(e)
    val cut = b.host.isCut(e)
    val code = b.colorOf(e)?.let { codeTint(it, palette) }
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .graphicsLayer { alpha = (if (cut) 0.4f else 1f) * b.fade() }
            .clickable(src, indication = null, role = Role.Button) { b.host.onClick(e) }
            .then(if (selecting) Modifier.semantics { this.selected = selected } else Modifier)
            .registered(b, e),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (b.view.thumb == ThumbShape.PAGE) A4_RATIO else 1f)
                .pressScale(src, 0.98f)
                .inkSelectionRing(selected, TileShape)
                .clip(TileShape)
                .border(1.dp, code ?: ink.line2, TileShape),
        ) {
            EntryThumb(b.editor, e, b.view.thumb, Modifier.fillMaxSize())
            ThumbBadges(b, e)
            if (selecting) SelectCheck(selected, Modifier.align(Alignment.BottomEnd).padding(8.dp), b.host.selectShown)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.label(e), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = b.metaText(e)
                if (meta.isNotEmpty()) Text(meta, style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selecting) EntryMenuButton(b, e, ink.text2, modifier = Modifier.offset(x = 8.dp))
        }
    }
}

/**
 * A folder as a chip (56–60dp, r12, 1dp --line or its colour code over a hatch). Selected, or under a dragged
 * selection: a 2dp near-black ring, a bold name and the Fill icon. A dropped move pulses it, in its layer only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderChipTile(b: ExplorerBody, e: BrowseEntry, height: Dp = 60.dp) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val active = b.host.isSelected(e) || b.host.isDropTarget(e)
    val cut = b.host.isCut(e)
    val code = b.colorOf(e)?.let { codeTint(it, palette) }
    val pulse = remember { Animatable(1f) }
    val pulsing = b.host.isPulsing(e)
    LaunchedEffect(pulsing) {
        if (pulsing) {
            pulse.animateTo(1.08f, tween(110))
            pulse.animateTo(1f, tween(160))
            b.host.onPulseDone(e)
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value; alpha = (if (cut) 0.4f else 1f) * b.fade() }
            .registered(b, e)
            .clip(shape)
            .then(if (!active && code != null) Modifier.colorHatch(code) else Modifier)
            .border(if (active) 2.dp else 1.dp, if (active) ink.solid else (code ?: ink.line), shape)
            .combinedClickable(onClick = { b.host.onClick(e) }, onLongClick = { b.host.onLongClick(e) }, role = Role.Button)
            .semantics { selected = b.host.isSelected(e) }
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (active) Ph.folderSimpleFill else Ph.folderSimple, null, tint = code ?: ink.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.label(e), style = if (active) RowBold else InkType.row, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = b.metaOverride?.invoke(e) ?: b.counts[e.documentUri]?.let { itemsLabel(b.words, it) }
            if (meta != null) Text(meta, style = MetaNum, color = ink.text2, maxLines = 1)
        }
        if (b.host.isPinned(e)) Icon(Ph.pushPin, stringResource(R.string.pinned_to_sidebar), tint = ink.text2, modifier = Modifier.size(14.dp))
        // Kept in select mode so the chip never changes width; there it only toggles the pick.
        if (selecting) InkIconButton(Ph.dotsThreeVertical, b.label(e), { b.host.onClick(e) }, size = 32.dp, iconSize = 20.dp, tint = ink.text2)
        else EntryMenuButton(b, e, ink.text2)
    }
}

/** A folder sorted in among file cards: a quiet card with the folder's icon where a page would be. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCardTile(b: ExplorerBody, e: BrowseEntry) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val active = b.host.isSelected(e) || b.host.isDropTarget(e)
    val code = b.colorOf(e)?.let { codeTint(it, palette) }
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .graphicsLayer { alpha = (if (b.host.isCut(e)) 0.4f else 1f) * b.fade() }
            .registered(b, e)
            .combinedClickable(src, indication = null, role = Role.Button, onLongClick = { b.host.onLongClick(e) }, onClick = { b.host.onClick(e) }),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (b.view.thumb == ThumbShape.PAGE) A4_RATIO else 1f)
                .pressScale(src, 0.98f)
                .inkSelectionRing(active, TileShape)
                .clip(TileShape).background(ink.surface).border(1.dp, code ?: ink.line2, TileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (active) Ph.folderSimpleFill else Ph.folderSimple, null, tint = code ?: ink.text2, modifier = Modifier.size(44.dp))
            if (selecting) SelectCheck(b.host.isSelected(e), Modifier.align(Alignment.BottomEnd).padding(8.dp), b.host.selectShown)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.label(e), style = InkType.rowStrong, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.metaText(e), style = MetaNum, color = ink.text2, maxLines = 1)
            }
            if (!selecting) EntryMenuButton(b, e, ink.text2, modifier = Modifier.offset(x = 8.dp))
        }
    }
}

/** One row of the List layout. [wide] shows the Kind, Pages and Size columns. A picked row: --sel, a bold name and its check. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListRow(b: ExplorerBody, e: BrowseEntry, wide: Boolean) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val selected = b.host.isSelected(e) || b.host.isDropTarget(e)
    val compact = b.view.compactRows
    val code = b.colorOf(e)?.let { codeTint(it, palette) }
    val kind = b.kind(e)
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 40.dp else 56.dp)
            .graphicsLayer { alpha = (if (b.host.isCut(e)) 0.4f else 1f) * b.fade() }
            // Rounded as the mockup's rows are (.row, r10), so the selected fill and the press stay inside one shape.
            .clip(ListRowShape)
            .background(if (selected) ink.sel else Color.Transparent)
            .registered(b, e)
            .combinedClickable(onClick = { b.host.onClick(e) }, onLongClick = if (e.isDir) ({ b.host.onLongClick(e) }) else null, role = Role.Button)
            .then(if (selecting) Modifier.semantics { this.selected = b.host.isSelected(e) } else Modifier)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            InkRowCheck(b.host.isSelected(e))
            Spacer(Modifier.width(LIST_CHECK_INSET - ROW_CHECK))
        }
        val box = if (compact) 28.dp else 40.dp
        val shape = RoundedCornerShape(10.dp)
        if (e.isDir || compact) {
            Box(Modifier.size(box).background(ink.surface, shape), contentAlignment = Alignment.Center) {
                Icon(kindIcon(kind), null, tint = code ?: ink.text2, modifier = Modifier.size(if (compact) 17.dp else 22.dp))
            }
        } else {
            EntryThumb(b.editor, e, ThumbShape.TOP, Modifier.size(box).clip(shape).border(1.dp, ink.line2, shape))
        }
        Row(Modifier.weight(1f).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(b.label(e), style = if (selected) RowBold else InkType.row, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = b.whereOf?.invoke(e)
                if (!wide && !compact) Text(listOfNotNull(where, b.metaText(e).ifEmpty { null }).joinToString(" · "), style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else if (where != null && !compact) Text(where, style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (code != null && !e.isDir) Box(Modifier.size(8.dp).background(code, CircleShape))
            if (b.host.isPinned(e)) Icon(Ph.pushPin, stringResource(R.string.pinned_to_sidebar), tint = ink.text2, modifier = Modifier.size(14.dp))
        }
        if (wide) {
            Row(Modifier.width(LIST_KIND_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(kindIcon(kind), null, tint = ink.text2, modifier = Modifier.size(16.dp))
                Text(kindLabel(kind), style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (e.isDir || kind == EntryKind.CANVAS) "–" else b.pages(e).takeIf { it > 0 }?.toString() ?: "", style = MetaNum, color = ink.text, textAlign = TextAlign.End, modifier = Modifier.width(LIST_PAGES_W))
            Text(
                if (e.isDir) b.counts[e.documentUri]?.let { itemsLabel(b.words, it) } ?: "" else formatSize(e.size),
                style = MetaNum, color = if (e.isDir) ink.text2 else ink.text, textAlign = TextAlign.End, maxLines = 1,
                modifier = Modifier.width(LIST_SIZE_W),
            )
            Text(b.metaOverride?.invoke(e) ?: b.whenText(e, withTime = true), style = MetaNum, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(LIST_WHEN_W).padding(start = 28.dp))
        }
        if (!selecting) EntryMenuButton(b, e, ink.text2, size = 40.dp) else Spacer(Modifier.width(40.dp))
    }
}

internal val LIST_KIND_W = 120.dp

private val ListRowShape = RoundedCornerShape(10.dp)

/** InkRowCheck's size. */
private val ROW_CHECK = 20.dp

/** What select mode adds before a list row's thumbnail: the 20dp check and its 14dp gap; the column heads move by as much. */
internal val LIST_CHECK_INSET = 34.dp
internal val LIST_PAGES_W = 70.dp
internal val LIST_SIZE_W = 90.dp
internal val LIST_WHEN_W = 170.dp

/** A file on the timeline: a square card, then its name, the time that placed it there and the folder it's in. */
@Composable
internal fun TimelineCard(b: ExplorerBody, e: BrowseEntry, side: Dp) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val selecting = b.host.selecting()
    val selected = b.host.isSelected(e)
    val code = b.colorOf(e)?.let { codeTint(it, palette) }
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(side)
            .graphicsLayer { alpha = (if (b.host.isCut(e)) 0.4f else 1f) * b.fade() }
            .clickable(src, indication = null, role = Role.Button) { b.host.onClick(e) }
            .then(if (selecting) Modifier.semantics { this.selected = selected } else Modifier)
            .registered(b, e),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(side).pressScale(src, 0.98f).inkSelectionRing(selected, TileShape).clip(TileShape).border(1.dp, code ?: ink.line2, TileShape)) {
            EntryThumb(b.editor, e, ThumbShape.TOP, Modifier.fillMaxSize())
            if (b.view.showKind) {
                val kind = b.kind(e)
                if (kind == EntryKind.PDF || kind == EntryKind.CANVAS) {
                    TileBadge(kindIcon(kind), if (kind == EntryKind.PDF) stringResource(R.string.library_pdf_short) else stringResource(R.string.kind_canvas), Modifier.align(Alignment.TopStart).padding(8.dp))
                }
            }
            if (selecting) SelectCheck(selected, Modifier.align(Alignment.BottomEnd).padding(6.dp), b.host.selectShown)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.label(e), style = InkType.body, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(formatClock(b.words, b.view.timelineTime(e), b.clock24, b.zone), style = SmallNum, color = ink.text2, maxLines = 1)
                val where = b.whereOf?.invoke(e)
                if (where != null) {
                    Icon(Ph.folderSimple, null, tint = ink.text2, modifier = Modifier.size(12.dp))
                    Text(where, style = InkType.small, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun formatClock(w: ExplorerWords, time: Long, clock24: Boolean, zone: ZoneId): String {
    if (time <= 0) return ""
    val t = java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
    return clockText(w, t, clock24)
}
