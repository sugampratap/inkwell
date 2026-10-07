package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.tools.ToolbarSection
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.delay

/** What the customiser's live bar shows, from a layout (r2_settings Frame 4). */
internal object ToolbarPreview {
    private val PENS = setOf(
        ToolbarItem.PEN, ToolbarItem.BALLPOINT, ToolbarItem.DASHED, ToolbarItem.CALLIGRAPHY, ToolbarItem.SPEED, ToolbarItem.TAPER, ToolbarItem.PENCIL, ToolbarItem.HIGHLIGHTER,
    )

    /** Every shown tool, section by section, empty sections dropped. PDF text markup appears only on notes with a PDF, so the preview leaves it out. */
    fun visibleSections(layout: ToolbarLayout): List<List<ToolbarItem>> =
        layout.sections.map { s -> s.entries.filter { it.visible && it.item != ToolbarItem.MARKUP }.map { it.item } }.filter { it.isNotEmpty() }

    /** The tool drawn active on the preview: the first pen on the bar. */
    fun activeItem(sections: List<List<ToolbarItem>>): ToolbarItem? = sections.flatten().firstOrNull { it in PENS }

    /** "11 of 19 shown". */
    fun shown(section: ToolbarSection): Int = section.entries.count { it.visible }
}

/**
 * Customise toolbar (r2_settings Frame 4), a sheet from General › Customise toolbar: the notebook's
 * and the canvas's bars a tab apart (Reset to default resets only the open one), the live bar at the
 * top, and the sections as cards. Tap a tool to show or hide it; hold and drag it within a section or
 * into another (a caret marks where it lands); drag a section by its handle; delete a section and its
 * tools join the one before (the next, for the first; never the last section); Add section adds an
 * empty card. The dragged chip or card's copy is drawn at the sheet's root so the scroll never clips
 * it, and the list eases along while it is held near the top or bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolbarCustomizerSheet(editor: Editor, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val scrollState = rememberScrollState()
    var canvasTab by remember { mutableStateOf(false) }
    val layout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    var dragItem by remember { mutableStateOf<ToolbarItem?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val chipBounds = remember { mutableMapOf<ToolbarItem, Rect>() }
    val sectionBounds = remember { mutableMapOf<Int, Rect>() }
    val sectionGrabBounds = remember { mutableMapOf<Int, Rect>() }
    var dragSection by remember { mutableStateOf<Int?>(null) }
    var sectionDropTarget by remember { mutableStateOf<Int?>(null) }
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    fun editedLayout(): ToolbarLayout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    fun applyEdited(next: ToolbarLayout) {
        if (canvasTab) editor.applyCanvasToolbarLayout(next) else editor.applyToolbarLayout(next)
    }
    fun retarget() { dropTarget = toolbarDropTarget(editedLayout(), dragFinger, chipBounds, sectionBounds) }
    fun retargetSection() { sectionDropTarget = toolbarSectionDropTarget(editedLayout(), dragFinger, sectionBounds) }
    fun showTab(canvas: Boolean) {
        if (canvasTab == canvas) return
        canvasTab = canvas
        // The bounds maps are keyed by item and the two bars share most items, so stale entries
        // from the other tab would aim a drop at a chip that is no longer there.
        chipBounds.clear(); sectionBounds.clear(); sectionGrabBounds.clear()
        dragItem = null; dragSection = null; dropTarget = null; sectionDropTarget = null
    }

    InkSheet(
        title = stringResource(R.string.settings_customise_toolbar),
        onDismiss = onDismiss,
        subtitle = stringResource(R.string.customise_sub),
        width = 1056.dp,
        height = 736.dp,
        bodyScrolls = false,
        headerActions = {
            InkBoxSegmented(
                listOf(false, true), canvasTab,
                label = { stringResource(if (it) R.string.settings_toolbar_canvas else R.string.settings_toolbar_notebook) },
                onSelect = { showTab(it) },
            )
        },
        footer = {
            InkGhostButton(stringResource(R.string.customise_reset), { applyEdited(if (canvasTab) ToolbarLayout.CANVAS_DEFAULT else ToolbarLayout.DEFAULT) }, icon = Ph.arrowCounterClockwise)
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.done), onDismiss)
        },
    ) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { viewport = it.boundsInRoot() }
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
            ) {
                ToolbarPreviewBar(layout, editor.toolbarColors, editor.toolbarColorCount)
                Text(
                    stringResource(if (canvasTab) R.string.customise_preview_canvas else R.string.customise_preview_note),
                    style = InkType.hint, color = ink.text2,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.customise_sections), style = InkType.label, color = ink.text)
                        Text(stringResource(R.string.customise_help), style = InkType.hint, color = ink.text2)
                    }
                    InkSecondaryButton(stringResource(R.string.add_section), { applyEdited(editedLayout().addSection()) }, Modifier.height(40.dp), icon = Ph.plus)
                }
                ToolbarCustomizerBody(
                    layout = layout,
                    dragItem = dragItem,
                    dropTarget = dropTarget,
                    dragSection = dragSection,
                    sectionDropTarget = sectionDropTarget,
                    chipBounds = chipBounds,
                    sectionBounds = sectionBounds,
                    sectionGrabBounds = sectionGrabBounds,
                    onToggle = { s, i -> applyEdited(editedLayout().toggleVisible(s, i)) },
                    onDelete = { s -> applyEdited(editedLayout().removeSection(s)) },
                    onDragStart = { item, local ->
                        dragItem = item
                        dragGrab = local
                        dragFinger = (chipBounds[item]?.topLeft ?: Offset.Zero) + local
                        retarget()
                    },
                    onDrag = { delta -> dragFinger += delta; retarget() },
                    onDrop = {
                        val target = dropTarget
                        val moving = dragItem
                        if (target != null && moving != null) {
                            val cur = editedLayout()
                            val fSec = cur.sections.indexOfFirst { s -> s.entries.any { it.item == moving } }
                            val fIdx = if (fSec >= 0) cur.sections[fSec].entries.indexOfFirst { it.item == moving } else -1
                            if (fSec >= 0 && fIdx >= 0) applyEdited(cur.moveItem(fSec, fIdx, target.first, target.second))
                        }
                        dragItem = null
                        dropTarget = null
                    },
                    onCancel = { dragItem = null; dropTarget = null },
                    onSectionDragStart = { sec, local ->
                        dragSection = sec
                        val grab = sectionGrabBounds[sec]?.topLeft ?: sectionBounds[sec]?.topLeft ?: Offset.Zero
                        val card = sectionBounds[sec]?.topLeft ?: Offset.Zero
                        dragFinger = grab + local
                        dragGrab = dragFinger - card
                        retargetSection()
                    },
                    onSectionDrag = { delta -> dragFinger += delta; retargetSection() },
                    onSectionDrop = {
                        val from = dragSection
                        val insertAt = sectionDropTarget
                        if (from != null && insertAt != null) {
                            val cur = editedLayout()
                            val to = (if (insertAt > from) insertAt - 1 else insertAt).coerceIn(0, cur.sections.lastIndex)
                            if (to != from) applyEdited(cur.moveSection(from, to))
                        }
                        dragSection = null
                        sectionDropTarget = null
                    },
                    onSectionCancel = { dragSection = null; sectionDropTarget = null },
                )
            }
            // The dragged chip or card's copy, at the sheet's root so the scroll never clips it. The
            // finger is read only while placing it, so a drag moves the copy and recomposes nothing.
            val ghostAt = Modifier.offset {
                IntOffset((dragFinger.x - dragGrab.x - rootTopLeft.x).toInt(), (dragFinger.y - dragGrab.y - rootTopLeft.y).toInt())
            }
            val ghostChip = dragItem
            val ghostSection = dragSection
            if (ghostChip != null) {
                Box(ghostAt) { ChipFace(ghostChip, visible = true, Modifier.shadow(8.dp, MaterialTheme.shapes.small, ambientColor = ink.shadow, spotColor = ink.shadow)) }
            } else if (ghostSection != null) {
                val section = editedLayout().sections.getOrNull(ghostSection)
                val width = sectionBounds[ghostSection]?.width
                if (section != null && width != null) Box(ghostAt) { SectionCardGhost(section, ghostSection, width) }
            }
        }
    }

    // While dragging near the top or bottom of the scroll, ease the list along so far items can be reached.
    LaunchedEffect(dragItem != null || dragSection != null) {
        while (dragItem != null || dragSection != null) {
            val band = 90f
            val y = dragFinger.y
            val delta = when {
                y < viewport.top + band -> -((viewport.top + band - y) / band) * 14f
                y > viewport.bottom - band -> ((y - (viewport.bottom - band)) / band) * 14f
                else -> 0f
            }
            if (delta != 0f) {
                scrollState.scrollBy(delta)
                if (dragItem != null) retarget() else retargetSection()
            }
            delay(16L)
        }
    }
}

/**
 * The live bar (.st-prev): the B2 floating toolbar built from [layout] — 44dp buttons, the first pen
 * active on the near-black glider with its duotone icon and ink dot, hairlines between sections, and
 * the first [count] toolbar colours — on the canvas colour, scaled down (transform only) to fit.
 */
@Composable
private fun ToolbarPreviewBar(layout: ToolbarLayout, colours: List<Rgba>, count: Int) {
    val ink = LocalInk.current
    val sections = ToolbarPreview.visibleSections(layout)
    val active = ToolbarPreview.activeItem(sections)
    val shape = MaterialTheme.shapes.medium
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(100.dp).clip(shape).background(ink.canvas).border(1.dp, ink.line2, shape),
        contentAlignment = Alignment.Center,
    ) {
        val available = with(LocalDensity.current) { (maxWidth - 32.dp).toPx() }
        var natural by remember { mutableIntStateOf(0) }
        if (sections.isEmpty()) {
            Text(stringResource(R.string.customise_preview_empty), style = InkType.body.copy(fontWeight = FontWeight.SemiBold), color = ink.text2)
        } else Row(
            Modifier
                .wrapContentWidth(unbounded = true)
                .onSizeChanged { natural = it.width }
                .graphicsLayer {
                    val k = if (natural > available && natural > 0) available / natural else 1f
                    scaleX = k
                    scaleY = k
                }
                .shadow(10.dp, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
                .background(ink.raised, CircleShape)
                .border(1.dp, ink.line2, CircleShape)
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { i, sec ->
                if (i > 0) Box(Modifier.padding(horizontal = 6.dp).size(1.dp, 24.dp).background(ink.line))
                sec.forEach { item ->
                    if (item == ToolbarItem.COLORS) {
                        colours.take(count).forEachIndexed { j, c ->
                            Box(Modifier.size(34.dp, 44.dp).drawBehind {
                                val r = 13.dp.toPx()
                                if (j == 0) {
                                    drawCircle(ink.solid, r + 4.dp.toPx())
                                    drawCircle(ink.raised, r + 2.dp.toPx())
                                }
                                drawCircle(c.toComposeColor(), r)
                            })
                        }
                    } else {
                        val on = item == active
                        Box(Modifier.padding(horizontal = 1.dp).size(44.dp).background(if (on) ink.solid else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                            ItemIcon(item, if (on) ink.onSolid else ink.text, 22.dp, active = on)
                            if (on && colours.isNotEmpty()) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(10.dp)
                                        .drawBehind {
                                            drawCircle(ink.onSolid, size.minDimension / 2 + 2.dp.toPx())
                                            drawCircle(colours.first().toComposeColor(), size.minDimension / 2)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The section cards (.st-secs): they wrap and share each line, a caret marking where a dragged section lands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolbarCustomizerBody(
    layout: ToolbarLayout,
    dragItem: ToolbarItem?,
    dropTarget: Pair<Int, Int>?,
    dragSection: Int?,
    sectionDropTarget: Int?,
    chipBounds: MutableMap<ToolbarItem, Rect>,
    sectionBounds: MutableMap<Int, Rect>,
    sectionGrabBounds: MutableMap<Int, Rect>,
    onToggle: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDragStart: (ToolbarItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onSectionDragStart: (Int, Offset) -> Unit,
    onSectionDrag: (Offset) -> Unit,
    onSectionDrop: () -> Unit,
    onSectionCancel: () -> Unit,
) {
    val ink = LocalInk.current
    val dragging = dragItem != null
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        layout.sections.forEachIndexed { sec, section ->
            if (dragSection != null && sectionDropTarget == sec) SectionCaret()
            Column(
                Modifier
                    .weight(section.entries.size.coerceAtLeast(1).toFloat())
                    .widthIn(min = 236.dp)
                    .alpha(if (dragSection == sec) 0.3f else 1f)
                    .onGloballyPositioned { sectionBounds[sec] = it.boundsInRoot() }
                    .clip(MaterialTheme.shapes.medium)
                    .background(ink.raised)
                    .border(if (dragging && dropTarget?.first == sec) 2.dp else 1.dp, if (dragging && dropTarget?.first == sec) ink.solid else ink.line, MaterialTheme.shapes.medium)
                    .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionGrip(
                        sec, section,
                        Modifier.weight(1f).height(40.dp),
                        onBounds = { sectionGrabBounds[sec] = it },
                        onDragStart = { onSectionDragStart(sec, it) },
                        onDrag = onSectionDrag, onDrop = onSectionDrop, onCancel = onSectionCancel,
                    )
                    InkIconButton(Ph.x, stringResource(R.string.delete_section), { onDelete(sec) }, enabled = layout.sections.size > 1, size = 36.dp, iconSize = 18.dp, tint = ink.text2)
                }
                FlowRow(Modifier.heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (section.entries.isEmpty()) {
                        Text(stringResource(R.string.drop_here), style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = ink.text3, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    section.entries.forEachIndexed { idx, entry ->
                        if (dragging && dropTarget == sec to idx) InsertionCaret()
                        ChipCell(
                            item = entry.item,
                            visible = entry.visible,
                            dragging = dragItem == entry.item,
                            onTap = { onToggle(sec, idx) },
                            onDragStart = { local -> onDragStart(entry.item, local) },
                            onDrag = onDrag,
                            onDrop = onDrop,
                            onCancel = onCancel,
                            onBounds = { chipBounds[entry.item] = it },
                        )
                    }
                    if (dragging && dropTarget == sec to section.entries.size) InsertionCaret()
                }
            }
        }
        if (dragSection != null && sectionDropTarget == layout.sections.size) SectionCaret()
    }
}

/** A card's handle (.st-sech): grip, "Section N", "x of y shown"; long-press and drag it to move the whole section. */
@Composable
private fun SectionGrip(
    index: Int,
    section: ToolbarSection,
    modifier: Modifier,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val ink = LocalInk.current
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDrop)
    val dCancel by rememberUpdatedState(onCancel)
    Row(
        modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dStart(it) },
                    onDrag = { _, delta -> dMove(delta) },
                    onDragEnd = { dEnd() },
                    onDragCancel = { dCancel() },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Ph.dotsSixVertical, null, tint = ink.text3, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.customise_section_n, index + 1), style = InkType.label, color = ink.text, maxLines = 1)
        Text(
            if (section.entries.isEmpty()) stringResource(R.string.customise_section_empty)
            else stringResource(R.string.customise_section_shown, ToolbarPreview.shown(section), section.entries.size),
            style = InkType.hint, color = ink.text2, maxLines = 1,
        )
    }
}

@Composable
private fun ChipCell(
    item: ToolbarItem,
    visible: Boolean,
    dragging: Boolean,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    // The pointerInput blocks key on the stable item, so they capture the first-composition lambdas;
    // rememberUpdatedState keeps the gestures calling the latest callbacks instead.
    val tap by rememberUpdatedState(onTap)
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDrop)
    val dCancel by rememberUpdatedState(onCancel)
    val state = stringResource(if (visible) R.string.customise_shown_tap else R.string.customise_hidden_tap)
    ChipFace(
        item = item,
        visible = visible,
        modifier = Modifier
            .alpha(if (dragging) 0.25f else 1f)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .semantics { contentDescription = state }
            .pointerInput(item) { detectTapGestures(onTap = { tap() }) }
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dStart(it) },
                    onDrag = { _, delta -> dMove(delta) },
                    onDragEnd = { dEnd() },
                    onDragCancel = { dCancel() },
                )
            },
    )
}

/**
 * A tool chip (.st-tc): 38dp, the theme's small corners, its glyph and name. A hidden tool keeps its
 * place, dashed on --line3 with no fill, its glyph faded and a crossed eye at the end.
 */
@Composable
private fun ChipFace(item: ToolbarItem, visible: Boolean, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.small
    val radius = cornerOf(12.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .then(if (visible) Modifier.background(ink.raised).border(1.dp, ink.line, shape) else Modifier)
            .drawBehind {
                if (!visible) {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        ink.line3, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), CornerRadius(radius.toPx()),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            }
            .padding(start = 9.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.alpha(if (visible) 1f else 0.45f)) { ItemIcon(item, ink.text, 20.dp) }
        Text(stringResource(item.labelRes), style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = if (visible) ink.text else ink.text2, maxLines = 1)
        if (!visible) Icon(Ph.eyeSlash, null, tint = ink.text3, modifier = Modifier.size(14.dp))
    }
}

/** A lifted section's copy, pinned to the dragged card's width ([widthPx]) so it wraps exactly like it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCardGhost(section: ToolbarSection, index: Int, widthPx: Float) {
    val ink = LocalInk.current
    val width = with(LocalDensity.current) { widthPx.toDp() }
    Column(
        Modifier
            .width(width)
            .shadow(8.dp, MaterialTheme.shapes.medium, ambientColor = ink.shadow, spotColor = ink.shadow)
            .background(ink.raised, MaterialTheme.shapes.medium)
            .border(2.dp, ink.solid, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ph.dotsSixVertical, null, tint = ink.text3, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.customise_section_n, index + 1), style = InkType.label, color = ink.text)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            section.entries.forEach { e -> ChipFace(e.item, e.visible) }
        }
    }
}

/** Where a dragged tool lands (.st-caret): 2 × 30dp, near-black. */
@Composable
private fun InsertionCaret() {
    Box(Modifier.size(2.dp, 30.dp).background(LocalInk.current.solid, RoundedCornerShape(1.dp)))
}

/** Where a dragged section lands (.st-scaret): 3dp wide, near-black. */
@Composable
private fun SectionCaret() {
    Box(Modifier.size(3.dp, 56.dp).background(LocalInk.current.solid, RoundedCornerShape(2.dp)))
}

/**
 * Resolve the drop slot (sectionIndex, entryIndex) for a finger position in root coordinates. The
 * section is the card the finger is inside, else the nearest card. Within it, the first chip the
 * finger sits "before" (an earlier row, or left of its centre on the same row) wins; an empty card
 * takes slot 0. Returns null only when no card has been measured yet.
 */
internal fun toolbarDropTarget(
    layout: ToolbarLayout,
    finger: Offset,
    chipBounds: Map<ToolbarItem, Rect>,
    sectionBounds: Map<Int, Rect>,
): Pair<Int, Int>? {
    var sec = layout.sections.indices.firstOrNull { sectionBounds[it]?.contains(finger) == true } ?: -1
    if (sec < 0) {
        var best = Float.MAX_VALUE
        for (i in layout.sections.indices) {
            val r = sectionBounds[i] ?: continue
            val dx = maxOf(r.left - finger.x, 0f, finger.x - r.right)
            val dy = maxOf(r.top - finger.y, 0f, finger.y - r.bottom)
            val d = dx * dx + dy * dy
            if (d < best) { best = d; sec = i }
        }
    }
    if (sec < 0) return null
    val entries = layout.sections[sec].entries
    if (entries.isEmpty()) return sec to 0
    fun before(r: Rect): Boolean = finger.y < r.top || (finger.y <= r.bottom && finger.x < r.center.x)
    for (idx in entries.indices) {
        val r = chipBounds[entries[idx].item] ?: continue
        if (before(r)) return sec to idx
    }
    return sec to entries.size
}

/** Resolve the section insertion index (0..size) for a finger position in root coordinates. */
internal fun toolbarSectionDropTarget(
    layout: ToolbarLayout,
    finger: Offset,
    sectionBounds: Map<Int, Rect>,
): Int {
    fun before(r: Rect): Boolean = finger.y < r.top || (finger.y <= r.bottom && finger.x < r.center.x)
    for (sec in layout.sections.indices) {
        val r = sectionBounds[sec] ?: continue
        if (before(r)) return sec
    }
    return layout.sections.size
}
