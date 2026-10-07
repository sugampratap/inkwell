package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Bookmark
import com.xnotes.core.model.Page
import com.xnotes.platform.PdfOutlineEntry
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPanelHint
import com.xnotes.ui.kit.InkSmallChip
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.kit.SelectCheck
import com.xnotes.ui.kit.inkSelectionRing
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkPressOver
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The side panel's tabs. */
enum class SidePanelTab { PAGES, CONTENTS, BOOKMARKS, SEARCH }

private val PANEL_W = 300.dp
private val THUMB_W = 116.dp
private const val COLUMNS = 2

private val THUMB_SHAPE = RoundedCornerShape(3.dp)

/** The grid's gaps, shared by the layout and the drag's slot sums. */
private val GAP_X = 22.dp
private val GAP_Y = 18.dp

/** A lifted page grows this much; the cells it was lifted from fade to [CARRIED_ALPHA]. */
private const val LIFT_SCALE = 1.04f
private const val CARRIED_ALPHA = 0.35f

/** Holding a dragged page within this of the grid's top or bottom scrolls it, up to [SCROLL_SPEED] a second. */
private val SCROLL_EDGE = 56.dp
private val SCROLL_SPEED = 900.dp

/** Pure sums for the panel, tested on the JVM. */
internal object PanelMath {
    fun rows(items: Int, columns: Int): Int = (items + columns - 1) / columns

    /** The scrollbar thumb's length for a [viewport] showing part of [content], on a [track]-long track, never under [minThumb]. */
    fun thumbLength(viewport: Float, content: Float, track: Float, minThumb: Float): Float =
        (viewport / content * track).coerceIn(minThumb, track)

    /** Where a scrollbar at [fraction] (0..1) puts the grid: the first item of the row there, and the pixel offset into that row. */
    fun gridTarget(fraction: Float, content: Float, viewport: Float, rowPx: Float, items: Int, columns: Int): Pair<Int, Int> {
        val targetPx = fraction.coerceIn(0f, 1f) * (content - viewport).coerceAtLeast(0f)
        val row = (targetPx / rowPx).toInt().coerceIn(0, (rows(items, columns) - 1).coerceAtLeast(0))
        val offset = (targetPx - row * rowPx).toInt().coerceAtLeast(0)
        return (row * columns).coerceAtMost((items - 1).coerceAtLeast(0)) to offset
    }

    /** The contents entry to mark on [page]: the last one that lands there, or -1. */
    fun currentEntry(destPages: List<Int>, page: Int): Int = destPages.indexOfLast { it == page }

    /**
     * The note page each contents entry opens on, as [Editor.goToTocEntry] finds it: the page carrying
     * the entry's PDF page [destPages] wherever it has been moved ([pdfPages] is each note page's
     * PDF page), else the last page carrying an earlier one; -1 for an entry that goes nowhere.
     */
    fun tocTargets(destPages: List<Int>, pdfPages: List<Int?>): IntArray {
        val first = HashMap<Int, Int>(pdfPages.size * 2)
        pdfPages.forEachIndexed { i, p -> if (p != null && p !in first) first[p] = i }
        return IntArray(destPages.size) { e ->
            val dest = destPages[e]
            if (dest < 0) -1 else first[dest] ?: pdfPages.indexOfLast { (it ?: -1) in 0..dest }
        }
    }
}

/**
 * The side panel (300dp): Pages, Contents, Bookmarks and Search. Pages shows thumbnails two to a row
 * with a ⋮ menu each (add, copy, cut, paste, erase, delete, share and save as an image or a PDF), and
 * multi-select (long-press, or the Select chip) with a labelled action bar. Share and Save need the
 * activity's launchers, so they are passed in; everything else acts on [editor].
 */
@Composable
fun SidePanel(
    editor: Editor,
    onSharePages: (indices: List<Int>, asPdf: Boolean) -> Unit = { _, _ -> },
    onSavePagesAsPdf: (indices: List<Int>) -> Unit = {},
    onSavePagesAsImages: (indices: List<Int>) -> Unit = {},
) {
    val ink = LocalInk.current
    val tab = editor.sidePanelTab
    Column(
        Modifier
            .width(PANEL_W)
            .fillMaxHeight()
            .background(ink.chrome)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(ink.line2, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            },
    ) {
        PanelTabs(tab) { editor.sidePanelTab = it }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                SidePanelTab.PAGES -> PagesTab(editor, onSharePages, onSavePagesAsPdf, onSavePagesAsImages)
                SidePanelTab.CONTENTS -> ContentsTab(editor)
                SidePanelTab.BOOKMARKS -> BookmarksTab(editor)
                SidePanelTab.SEARCH -> SearchTab(editor)
            }
        }
    }
}

private class PanelTabSpec(val tab: SidePanelTab, val icon: ImageVector, val filled: ImageVector, val label: Int)

private val TABS = listOf(
    PanelTabSpec(SidePanelTab.PAGES, Ph.files, Ph.filesFill, R.string.pages),
    PanelTabSpec(SidePanelTab.CONTENTS, Ph.listBullets, Ph.listBulletsFill, R.string.contents),
    PanelTabSpec(SidePanelTab.BOOKMARKS, Ph.bookmarkSimple, Ph.bookmarkSimpleFill, R.string.bookmarks),
    PanelTabSpec(SidePanelTab.SEARCH, Ph.magnifyingGlass, Ph.magnifyingGlassFill, R.string.search),
)

/**
 * The tab row (.ps-tabs): 66dp, four equal tabs, the chosen one opaque with a Fill icon and an
 * extra-bold label, the others at 60%. A 2dp underline in --text slides under the chosen label and
 * takes its width (glide spring); both are read only in the draw pass.
 */
@Composable
private fun PanelTabs(selected: SidePanelTab, onSelect: (SidePanelTab) -> Unit) {
    val ink = LocalInk.current
    val index = TABS.indexOfFirst { it.tab == selected }.coerceAtLeast(0)
    val labelWidths = remember { mutableStateListOf(0f, 0f, 0f, 0f) }
    val pos = animateFloatAsState(index.toFloat(), InkMotion.glide(), label = "tabUnderline")
    val width = animateFloatAsState(labelWidths[index], InkMotion.glide(), label = "tabUnderlineWidth")
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .drawWithContent {
                drawContent()
                val hair = 1.dp.toPx()
                drawLine(ink.line2, Offset(0f, size.height - hair / 2), Offset(size.width, size.height - hair / 2), hair)
                val pad = 6.dp.toPx()
                val tabW = (size.width - 2 * pad) / TABS.size
                val w = width.value
                if (w > 0f) {
                    val x = pad + tabW * (pos.value + 0.5f) - w / 2
                    drawRect(ink.text, Offset(x, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()))
                }
            }
            .padding(horizontal = 6.dp),
    ) {
        TABS.forEachIndexed { i, t ->
            val on = t.tab == selected
            val alpha = animateFloatAsState(if (on) 1f else 0.6f, InkMotion.press(), label = "tabAlpha")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { this.alpha = alpha.value }
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Tab) { onSelect(t.tab) }
                    .semantics { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Icon(if (on) t.filled else t.icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(t.label),
                    style = InkType.small.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = ink.text,
                    maxLines = 1,
                    modifier = Modifier.onSizeChanged { labelWidths[i] = it.width.toFloat() },
                )
            }
        }
    }
}

@Composable
private fun PagesTab(
    editor: Editor,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
) {
    val ink = LocalInk.current
    val gridState = rememberLazyGridState()
    val gapPx = with(LocalDensity.current) { GAP_X.toPx() }
    val drag = remember(gapPx) { PageDragState(gapPx) }
    // Open on the current page, centred (re-runs each open: a hidden panel leaves composition).
    LaunchedEffect(Unit) {
        gridState.scrollToItem(editor.pageIndex)
        val info = gridState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == editor.pageIndex }
        if (item != null) gridState.scrollBy(-((info.viewportSize.height - item.size.height) / 2f - 10f))
    }
    // Keyed by the page's stable id so insert/delete animate and keep each page's cached thumbnail.
    val pages = remember(editor.contentVersion) { editor.pagesSnapshot() }
    // The Select chip enters select mode with nothing picked yet; a long-press enters it with one.
    var selectMode by rememberSaveable { mutableStateOf(false) }
    val selecting = selectMode || editor.inPageSelectionMode
    // Back leaves select mode, dropping its picks, before it closes the note (a drag's own Back, composed later, wins while one is held).
    BackHandler(enabled = selecting) {
        editor.clearPageSelection()
        selectMode = false
    }
    // A scrub renders nothing until it settles, then the resting window renders once (see the
    // scrollbar: it turns a small finger move into a big jump). drop(1) lets the first open paint at once.
    val settled by produceState(initialValue = true, gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .drop(1)
            .collectLatest {
                value = false
                delay(150)
                value = true
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = editor.pageSelectionCount
            Text(
                if (selecting) pluralStringResource(R.plurals.panel_selected, n, n) else pluralStringResource(R.plurals.panel_pages, pages.size, pages.size),
                style = InkType.meta.copy(fontWeight = if (selecting) FontWeight.ExtraBold else FontWeight.SemiBold),
                color = if (selecting) ink.text else ink.text2,
                modifier = Modifier.weight(1f),
            )
            InkChip(
                stringResource(if (selecting) R.string.done else R.string.panel_select),
                selected = false,
                onClick = {
                    if (selecting) {
                        editor.clearPageSelection()
                        selectMode = false
                    } else selectMode = true
                },
                modifier = Modifier.height(34.dp),
                role = Role.Button,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onPlaced { drag.panelCoords = it }
                .pageDragInput(drag, gridState) { lift, slot ->
                    // Only if the note did not change under the drag (the indices are the ones it lifted).
                    if (editor.pageAt(lift.index) === lift.page) editor.movePages(lift.moving, slot)
                },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(GAP_X),
                verticalArrangement = Arrangement.spacedBy(GAP_Y),
            ) {
                itemsIndexed(pages, key = { _, page -> page.uid }) { index, page ->
                    PageCell(
                        editor, index, pages.size, page, selecting, settled, drag, Modifier.animateItem(),
                        onSharePages, onSavePagesAsPdf, onSavePagesAsImages,
                        onLongPress = {
                            selectMode = true
                            editor.togglePageSelection(index)
                        },
                    )
                }
            }
            GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd).padding(top = 4.dp, bottom = 10.dp))
            DragLayer(drag, gridState)
        }
        if (selecting) PageSelectionBar(editor, onSharePages, onSavePagesAsPdf, onSavePagesAsImages)
    }
}

/**
 * Watches every pointer on its way down (the Initial pass, before the grid scrolls or a cell sees
 * it), so a page a long-press [PageDragState.arm]ed lifts as soon as the finger moves past the slop,
 * and the drag then owns the gesture: its moves are consumed, so the grid neither scrolls nor taps.
 * Held here, on the panel rather than the cell, the drag outlives its cell scrolling out of view.
 */
private fun Modifier.pageDragInput(drag: PageDragState, gridState: LazyGridState, onDrop: (PageLift, Int) -> Unit): Modifier =
    pointerInput(drag, gridState) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            drag.pointerDown = true
            drag.rawX = down.position.x
            drag.rawY = down.position.y
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (drag.lifted != null || drag.cancelled) change.consume()
                        drag.onUp()?.let { (lift, slot) -> onDrop(lift, slot) }
                        break
                    }
                    if (drag.onMove(change.position.x, change.position.y, slop, gridState.layoutInfo)) change.consume()
                }
            } finally {
                drag.gestureEnded()
            }
        }
    }

/**
 * Over the grid while a page is dragged: the 3dp ink insertion bar at the drop slot, the lifted page
 * following the finger, the edge auto-scroll, and Back to put the page down where it was. The bar and
 * the page read the finger only while drawing and placing, so a drag recomposes nothing per frame.
 */
@Composable
private fun BoxScope.DragLayer(drag: PageDragState, gridState: LazyGridState) {
    val ink = LocalInk.current
    Box(
        Modifier.matchParentSize().drawBehind {
            if (!drag.barShown.value) return@drawBehind
            val w = 3.dp.toPx()
            val x = drag.barX.floatValue.coerceIn(w / 2, size.width - w / 2)
            val top = drag.barTop.floatValue
            drawRoundRect(ink.solid, Offset(x - w / 2, top), Size(w, drag.barBottom.floatValue - top), CornerRadius(w / 2))
        },
    )
    val lift = drag.lifted ?: return
    BackHandler { drag.cancel() }
    val density = LocalDensity.current
    LaunchedEffect(lift) {
        val edge = with(density) { SCROLL_EDGE.toPx() }
        val speed = with(density) { SCROLL_SPEED.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val viewport = gridState.layoutInfo.viewportSize.height.toFloat()
            val step = PageDragMath.autoScrollStep(drag.fingerY.floatValue, viewport, edge, speed * dt)
            if (step != 0f) gridState.dispatchRawDelta(step)
            drag.updateSlot(gridState.layoutInfo) // the cells moved under a still finger
        }
    }
    DragGhost(drag, lift)
}

/** The lifted page: its thumbnail grown to [LIFT_SCALE] on the kit's float shadow, and how many go with it. */
@Composable
private fun DragGhost(drag: PageDragState, lift: PageLift) {
    val ink = LocalInk.current
    val density = LocalDensity.current
    val rise = remember(lift) { Animatable(0f) }
    LaunchedEffect(lift) { rise.animateTo(1f, InkMotion.press()) }
    val shadowPx = with(density) { InkElevation.FLOAT.shadow.toPx() }
    val w = with(density) { lift.thumbW.toDp() }
    val h = with(density) { lift.thumbH.toDp() }
    Box(
        Modifier
            .offset { IntOffset((drag.fingerX.floatValue - drag.grabX).roundToInt(), (drag.fingerY.floatValue - drag.grabY).roundToInt()) }
            .size(w, h)
            .graphicsLayer {
                val r = rise.value
                val scale = 1f + (LIFT_SCALE - 1f) * r
                scaleX = scale
                scaleY = scale
                shadowElevation = shadowPx * r
                shape = THUMB_SHAPE
                clip = true
                ambientShadowColor = ink.shadow
                spotShadowColor = ink.shadow
            }
            .background(lift.paper),
    ) {
        lift.bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.matchParentSize().border(1.dp, ink.line, THUMB_SHAPE))
        if (lift.group && lift.moving.size > 1) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).heightIn(min = 26.dp).widthIn(min = 26.dp).background(ink.solid, CircleShape).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${lift.moving.size}", style = InkType.small.copy(fontWeight = FontWeight.Bold).tnum(), color = ink.onSolid)
            }
        }
    }
}

/** Where a cell and its thumbnail were last placed, for a long-press to lift it from. */
private class CellPlace {
    var cell: LayoutCoordinates? = null
    var thumb: LayoutCoordinates? = null
}

/**
 * One page (.ps-cell): its 116dp thumbnail (rendered once scrolling settles), the ⋮ badge, and its
 * number. The current page wears a 2dp ink ring outside a 2dp gap and an inverted number; a picked
 * page wears the ring, a 7% shade and the kit's check. Tap goes to the page (or picks it while selecting);
 * long-press starts selecting, or, if the finger then moves, lifts the page (with the other picked
 * pages, when it is one) to drag it to a new place. TalkBack moves it a step with its custom actions.
 * The thumbnail shrinks to .97 under the finger and the press tint is drawn over the picture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCell(
    editor: Editor,
    index: Int,
    count: Int,
    page: Page,
    selecting: Boolean,
    settled: Boolean,
    drag: PageDragState,
    modifier: Modifier,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
    onLongPress: () -> Unit,
) {
    val ink = LocalInk.current
    val current = index == editor.pageIndex
    val picked = editor.isPageSelected(index)
    var menuOpen by remember { mutableStateOf(false) }
    val bitmap by key(editor.thumbnailVersion) {
        produceState<ImageBitmap?>(editor.cachedPageThumbnail(page), page, editor.contentVersion, editor.pdfThumbTick, settled) {
            val cached = editor.cachedPageThumbnail(page)
            if (cached != null) value = cached else if (settled) value = editor.pageThumbnail(page, 300)
        }
    }
    val aspect = editor.pageAspectRatio(page)
    // Until its picture lands, the thumbnail shows the page's own paper (as the canvas resolves it).
    val paper = (editor.state.effectivePageColor(page) ?: editor.state.palette.paper).toComposeColor()
    val src = remember { MutableInteractionSource() }
    val ringed = picked || (current && !selecting)
    val label = stringResource(R.string.page_n, index + 1)
    val earlier = stringResource(R.string.page_move_earlier)
    val later = stringResource(R.string.page_move_later)
    val place = remember { CellPlace() }
    val longPress = {
        val panel = drag.panelCoords
        val cell = place.cell
        val thumb = place.thumb
        val lift = if (panel != null && cell != null && thumb != null && panel.isAttached && cell.isAttached && thumb.isAttached) {
            val c = panel.localPositionOf(cell, Offset.Zero)
            val t = panel.localPositionOf(thumb, Offset.Zero)
            val group = selecting && picked
            PageLift(
                page, index, if (group) editor.selectedPageIndices() else listOf(index), group,
                c.x, c.y, t.x, t.y, thumb.size.width.toFloat(), thumb.size.height.toFloat(), bitmap, paper,
            )
        } else null
        if (lift == null || !drag.arm(lift, onLongPress)) onLongPress()
    }
    Column(
        modifier
            .fillMaxWidth()
            .onPlaced { place.cell = it }
            .graphicsLayer { alpha = if (drag.carries(page, picked)) CARRIED_ALPHA else 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(THUMB_W, THUMB_W * aspect).onPlaced { place.thumb = it }) {
            Box(
                Modifier
                    .matchParentSize()
                    .pressScale(src, 0.97f)
                    // The kit's 2dp ink ring, 2dp outside the page so it reads against the panel, not the paper.
                    .inkSelectionRing(ringed, THUMB_SHAPE)
                    .clip(THUMB_SHAPE)
                    .background(paper)
                    .combinedClickable(
                        interactionSource = src,
                        indication = InkPressOver(3.dp),
                        role = Role.Button,
                        onLongClick = longPress,
                        onClick = { if (selecting) editor.togglePageSelection(index) else editor.goToPage(index) },
                    )
                    .semantics {
                        contentDescription = label
                        if (selecting) selected = picked
                        customActions = listOfNotNull(
                            if (index > 0) CustomAccessibilityAction(earlier) { editor.movePages(listOf(index), index - 1) } else null,
                            if (index < count - 1) CustomAccessibilityAction(later) { editor.movePages(listOf(index), index + 2) } else null,
                        )
                    },
            ) {
                bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                if (picked) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.07f)))
                if (!ringed) Box(Modifier.matchParentSize().border(1.dp, ink.line, THUMB_SHAPE))
                // The kit's check (its 30dp box holds a 2dp halo), so the disc sits 6dp in, as .ps-chk.
                if (selecting) SelectCheck(picked, Modifier.align(Alignment.TopStart).padding(4.dp))
            }
            if (!selecting) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                    PageDots(menuOpen, stringResource(R.string.page_options_n, index + 1)) { menuOpen = true }
                    PageMenu(editor, index, menuOpen, { menuOpen = false }, onSharePages, onSavePagesAsPdf, onSavePagesAsImages)
                }
            }
        }
        PageNumber(index + 1, current)
    }
}

/** The page's ⋮ badge (.ps-dots): 28dp on the badge colour, near-black while its menu is open. */
@Composable
private fun PageDots(on: Boolean, label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(28.dp)
            .pressScale(src, 0.9f)
            .shadow(1.dp, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .background(if (on) ink.solid else ink.badge, CircleShape)
            .clip(CircleShape)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ph.dotsThreeVertical, null, tint = if (on) ink.onSolid else ink.badgeInk, modifier = Modifier.size(17.dp))
    }
}

/** The page number (.ps-num), inverted for the current page. */
@Composable
private fun PageNumber(n: Int, current: Boolean) {
    val ink = LocalInk.current
    Box(
        Modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 26.dp)
            .background(if (current) ink.solid else Color.Transparent, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = InkType.small.copy(fontWeight = FontWeight.Bold).tnum(), color = if (current) ink.onSolid else ink.text2)
    }
}

/** A menu's quiet caption (.ps-pmenu .m-h): "Page 7". */
@Composable
private fun MenuCaption(text: String) {
    Text(text, style = InkType.hint.copy(fontWeight = FontWeight.Bold), color = LocalInk.current.text2, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp))
}

/**
 * A page's ⋮ menu (.ps-pmenu): Add page before, Add page after, Copy, Cut, Paste after (once pages are copied or cut);
 * Erase page, Delete page (the one red row; undoable from the toolbar, so it does not ask); then
 * Share as and Save as, each with Image and PDF on the row itself.
 */
@Composable
private fun PageMenu(
    editor: Editor,
    index: Int,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
) {
    val one = listOf(index)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.width(296.dp)) {
        MenuCaption(stringResource(R.string.page_n, index + 1))
        InkMenuRow(stringResource(R.string.page_menu_add_before), { editor.insertPageBefore(index); onDismiss() }, icon = Ph.plus)
        InkMenuRow(stringResource(R.string.page_menu_add_after), { editor.insertPageAfter(index); onDismiss() }, icon = Ph.plus)
        InkMenuRow(stringResource(R.string.copy), { editor.copyPages(one); onDismiss() }, icon = Ph.copy)
        InkMenuRow(stringResource(R.string.cut), { editor.cutPages(one); onDismiss() }, icon = Ph.scissors)
        if (editor.canPastePages) InkMenuRow(stringResource(R.string.page_menu_paste_after), { editor.pastePagesAfter(index); onDismiss() }, icon = Ph.clipboardText)
        InkMenuDivider()
        InkMenuRow(stringResource(R.string.erase_page), { editor.erasePage(index); onDismiss() }, icon = Ph.eraser)
        InkMenuRow(stringResource(R.string.page_menu_delete), { editor.deletePages(one); onDismiss() }, icon = Ph.trash, danger = true)
        InkMenuDivider()
        MenuChoiceRow(Ph.shareNetwork, stringResource(R.string.page_menu_share_as), { onSharePages(one, false); onDismiss() }, { onSharePages(one, true); onDismiss() })
        MenuChoiceRow(Ph.downloadSimple, stringResource(R.string.page_menu_save_as), { onSavePagesAsImages(one); onDismiss() }, { onSavePagesAsPdf(one); onDismiss() })
    }
}

/** A menu row that is not itself a button (.ps-mf): its icon and label, then Image and PDF chips. */
@Composable
private fun MenuChoiceRow(icon: ImageVector, label: String, onImage: () -> Unit, onPdf: () -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Text(label, style = InkType.row, color = ink.text, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InkSmallChip(stringResource(R.string.page_menu_image), onImage)
            InkSmallChip(stringResource(R.string.share_pdf), onPdf)
        }
    }
}

/** The action bar while selecting (.ps-selbar): five labelled actions; dimmed until a page is picked. */
@Composable
private fun PageSelectionBar(
    editor: Editor,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
) {
    val ink = LocalInk.current
    val n = editor.pageSelectionCount
    val any = n > 0
    Row(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(ink.chrome)
            .drawBehind { drawLine(ink.line2, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BarAction(Ph.copy, stringResource(R.string.copy), any) { editor.copyPages(editor.selectedPageIndices()) }
        BarAction(Ph.scissors, stringResource(R.string.cut), any) { editor.cutPages(editor.selectedPageIndices()) }
        BarAction(Ph.trash, stringResource(R.string.delete), any, danger = true) { editor.deletePages(editor.selectedPageIndices()) }
        FormatAction(
            Ph.downloadSimple, stringResource(R.string.panel_save_as), pluralStringResource(R.plurals.panel_save_n_as, n, n), any,
            onImage = { onSavePagesAsImages(editor.selectedPageIndices()) }, onPdf = { onSavePagesAsPdf(editor.selectedPageIndices()) },
        )
        FormatAction(
            Ph.shareNetwork, stringResource(R.string.share), pluralStringResource(R.plurals.panel_share_n_as, n, n), any,
            onImage = { onSharePages(editor.selectedPageIndices(), false) }, onPdf = { onSharePages(editor.selectedPageIndices(), true) },
        )
    }
}

/** One labelled action (.ps-sa): 54 × 60, the icon over its word; red for Delete. */
@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, danger: Boolean = false, on: Boolean = false, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val fg = if (danger) ink.danger else ink.text
    Column(
        Modifier
            .size(54.dp, 60.dp)
            .pressScale(src, 0.94f)
            .alpha(if (enabled) 1f else 0.32f)
            .clip(MaterialTheme.shapes.small)
            .background(if (on) ink.sel else Color.Transparent)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, style = InkType.tiny.copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = fg, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Save as / Share on the bar: opens "Share 3 pages as" with Image (PNG) and PDF (.ps-fmenu). */
@Composable
private fun FormatAction(icon: ImageVector, label: String, header: String, enabled: Boolean, onImage: () -> Unit, onPdf: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarAction(icon, label, enabled, on = open) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(220.dp)) {
            MenuCaption(header)
            InkMenuRow(stringResource(R.string.image_png), { open = false; onImage() }, icon = Ph.image)
            InkMenuRow(stringResource(R.string.share_pdf), { open = false; onPdf() }, icon = Ph.filePdf)
        }
    }
}

private class GridScrollMetrics(val viewport: Float, val content: Float, val fraction: Float, val rowPx: Float, val items: Int)

/**
 * A draggable scrollbar for the page grid (.ps-sbar): a 4dp bar in --text3 at 55%, full --text while
 * dragged, shown only while the grid overflows. Row extents come from the visible rows (thumbnails
 * are near-uniform). A drag jumps straight to its row with scrollToItem, which composes only the
 * landing window; scrolling by the drag instead composed every page passed on the way.
 */
@Composable
private fun GridScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gapPx = with(density) { 18.dp.toPx() }
    val minThumbPx = with(density) { 28.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    val metrics = remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            val viewport = info.viewportSize.height.toFloat()
            if (total == 0 || visible.isEmpty() || viewport <= 0f) return@derivedStateOf null
            val rowPx = visible.maxOf { it.size.height }.toFloat() + gapPx
            val content = rowPx * PanelMath.rows(total, COLUMNS)
            if (content <= viewport) return@derivedStateOf null
            val scrolled = rowPx * (state.firstVisibleItemIndex / COLUMNS) + state.firstVisibleItemScrollOffset
            GridScrollMetrics(viewport, content, (scrolled / (content - viewport)).coerceIn(0f, 1f), rowPx, total)
        }
    }
    Box(
        modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = metrics.value ?: return@awaitEachGesture
                    down.consume()
                    dragging = true
                    var lastY = down.position.y
                    var frac = start.fraction
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        val m = metrics.value
                        if (dy != 0f && m != null) {
                            val track = size.height.toFloat()
                            val travel = track - PanelMath.thumbLength(m.viewport, m.content, track, minThumbPx)
                            if (travel > 0f) {
                                frac = (frac + dy / travel).coerceIn(0f, 1f)
                                val (item, offset) = PanelMath.gridTarget(frac, m.content, m.viewport, m.rowPx, m.items, COLUMNS)
                                scope.launch { state.scrollToItem(item, offset) }
                            }
                            change.consume()
                        }
                    }
                    dragging = false
                }
            }
            .drawBehind {
                val m = metrics.value ?: return@drawBehind
                val thumb = PanelMath.thumbLength(m.viewport, m.content, size.height, minThumbPx)
                val w = 4.dp.toPx()
                drawRoundRect(
                    if (dragging) ink.text else ink.text3.copy(alpha = 0.55f),
                    Offset(size.width - w - 4.dp.toPx(), (size.height - thumb) * m.fraction),
                    Size(w, thumb),
                    CornerRadius(w / 2),
                )
            },
    )
}

@Composable
private fun ContentsTab(editor: Editor) {
    // tocVersion bumps when the off-thread outline parse lands (and to empty on a document swap).
    val version = editor.tocVersion
    val entries = remember(version) { editor.tableOfContents }
    if (entries.isEmpty()) {
        InkPanelHint(Ph.listBullets, stringResource(R.string.panel_no_contents_title), stringResource(R.string.panel_no_contents_body))
        return
    }
    // Each entry's note page, found through its PDF page so it follows pages that were moved.
    val targets = remember(entries, editor.contentVersion) {
        PanelMath.tocTargets(entries.map { it.destPage }, editor.pagesSnapshot().map { it.pdfPage })
    }
    val marked = PanelMath.currentEntry(targets.asList(), editor.pageIndex)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 24.dp)) {
        itemsIndexed(entries) { i, e -> ContentsRow(e, targets[i], i == marked) { editor.goToTocEntry(e) } }
    }
}

/** An outline entry (.ps-ti): indented 16dp a level, the top level bold, its note [page]'s number at the end; greyed when it goes nowhere. */
@Composable
private fun ContentsRow(e: PdfOutlineEntry, page: Int, on: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    val target = page >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (on) ink.sel else Color.Transparent)
            .then(if (target) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = (12 + 16 * e.level.coerceIn(0, 6)).dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            e.title,
            style = InkType.body.copy(fontWeight = when { on -> FontWeight.ExtraBold; e.level == 0 -> FontWeight.Bold; else -> FontWeight.Medium }),
            color = if (target) ink.text else ink.text3,
            modifier = Modifier.weight(1f),
        )
        if (target) Text("${page + 1}", style = InkType.hint.copy(fontWeight = FontWeight.SemiBold).tnum(), color = ink.text3)
    }
}

@Composable
private fun BookmarksTab(editor: Editor) {
    val ink = LocalInk.current
    // Read the version so the list recomposes on add/remove.
    val version = editor.bookmarkVersion
    val bookmarks = remember(version) { editor.bookmarks }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.panel_bookmarks, bookmarks.size, bookmarks.size),
                style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = ink.text2, modifier = Modifier.weight(1f),
            )
            InkChip(stringResource(R.string.panel_add), selected = false, onClick = { adding = true }, modifier = Modifier.height(34.dp), icon = Ph.plus, role = Role.Button)
        }
        if (adding) {
            AddBookmarkCard(
                editor.pageIndex,
                onAdd = { label -> editor.addBookmark(label); adding = false },
                onCancel = { adding = false },
            )
        }
        if (bookmarks.isEmpty()) {
            InkPanelHint(Ph.bookmarkSimple, stringResource(R.string.panel_no_bookmarks_title), stringResource(R.string.panel_no_bookmarks_body))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
                itemsIndexed(bookmarks, key = { i, b -> "$i:${b.page}:${b.label}" }) { i, bm ->
                    BookmarkRow(bm, bm.page == editor.pageIndex, Modifier.animateItem(), onGo = { editor.goToPage(bm.page) }, onRemove = { editor.removeBookmark(i) })
                }
            }
        }
    }
}

/**
 * Naming a new bookmark (.ps-addc), inline instead of a dialog: the name ("Page N", selected so
 * typing replaces it), the page it marks, Cancel and Add. Enter adds, Esc cancels; a blank name
 * takes "Page N".
 */
@Composable
private fun AddBookmarkCard(pageIndex: Int, onAdd: (String) -> Unit, onCancel: () -> Unit) {
    val ink = LocalInk.current
    val default = stringResource(R.string.page_n, pageIndex + 1)
    var field by remember { mutableStateOf(TextFieldValue(default, TextRange(0, default.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val add = { onAdd(field.text.trim().ifBlank { default }) }
    val nameLabel = stringResource(R.string.bookmark_name)
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ink.surface)
            .border(1.dp, ink.line2, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        InkTextField(
            field, { field = it },
            modifier = Modifier.semantics { contentDescription = nameLabel },
            height = 44.dp,
            focusRequester = focus,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
            onKeyEvent = { e ->
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> { add(); true }
                    e.key == Key.Escape -> { onCancel(); true }
                    else -> false
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(default, style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = ink.text2, modifier = Modifier.padding(start = 4.dp).weight(1f))
            InkGhostButton(stringResource(R.string.cancel), onCancel, Modifier.height(40.dp))
            InkStrongButton(stringResource(R.string.panel_add), { add() }, Modifier.height(40.dp))
        }
    }
}

/** A bookmark (.ps-bm): its icon (Fill on the current page), name and page; × removes it. */
@Composable
private fun BookmarkRow(bm: Bookmark, on: Boolean, modifier: Modifier, onGo: () -> Unit, onRemove: () -> Unit) {
    val ink = LocalInk.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp).clip(MaterialTheme.shapes.small).background(if (on) ink.sel else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onGo).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(if (on) Ph.bookmarkSimpleFill else Ph.bookmarkSimple, null, tint = ink.text, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(bm.label, style = InkType.body.copy(fontSize = 14.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold), color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.page_n, bm.page + 1), style = InkType.caption, color = ink.text2)
            }
        }
        InkIconButton(Ph.x, stringResource(R.string.remove_bookmark), onRemove, Modifier.padding(end = 4.dp), size = 36.dp, iconSize = 18.dp, tint = ink.text2)
    }
}
