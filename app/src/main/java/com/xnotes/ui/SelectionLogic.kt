package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The selection bar's and the long-press card's pure rules (r2_selection_colour Frames 2-3; r3_table_image 867). Pane
 * or window px in and out; [dp] is px per dp. The composables in SelectionMenu.kt and LongPressMenu.kt read these.
 */

/**
 * The selection bar's groups in their fixed order (the Fluent bar, 2026-10-07). The bar and More both list their
 * actions in this order, a hairline (the bar) or a rule (More) between neighbouring groups, so grouping follows from
 * which actions the user keeps on the bar (Settings › General › Selection bar) and needs no setting of its own.
 */
internal enum class SelGroup { LEAD, TABLE, CLIPBOARD, PICTURE, TURN, ARRANGE, EXPORT, LOCK }

/** A labelled action on the bar (64 dp, the mockup's .btn); a longer label gets a wider button. */
internal const val SEL_ACTION_DP = 64

/**
 * One action of the selection bar or its More menu, in the fixed order ([SelGroup], then the order here). [id] is
 * what Settings saves. [priority] decides which stay on the bar when the pane is narrow (SC 229; today's PillSlot):
 * the highest number goes to More first and 0 never goes; a table's own tools go before everything else. [widthDp]
 * is the button's width on the bar.
 */
internal enum class SelAction(val id: String, val group: SelGroup, val priority: Int, val widthDp: Int = SEL_ACTION_DP) {
    EDIT("edit", SelGroup.LEAD, 0),
    NOTE_COLOUR("note_colour", SelGroup.LEAD, 0),
    STYLE("style", SelGroup.LEAD, 2),
    CROP("crop", SelGroup.LEAD, 1),
    ADD_ROW("add_row", SelGroup.TABLE, 30),
    ADD_COLUMN("add_column", SelGroup.TABLE, 31, 80),
    HEADER("header", SelGroup.TABLE, 32),
    CUT("cut", SelGroup.CLIPBOARD, 8),
    COPY("copy", SelGroup.CLIPBOARD, 3),
    PASTE("paste", SelGroup.CLIPBOARD, 10),
    DELETE("delete", SelGroup.CLIPBOARD, 4),
    DUPLICATE("duplicate", SelGroup.CLIPBOARD, 9, 72),
    SAVE_IMAGE("save_image", SelGroup.PICTURE, 18, 80),
    RESET_IMAGE("reset_image", SelGroup.PICTURE, 19, 84),
    ROTATE("rotate", SelGroup.TURN, 5),
    FLIP_H("flip_horizontal", SelGroup.TURN, 6, 96),
    FLIP_V("flip_vertical", SelGroup.TURN, 7, 84),
    ROTATE_LEFT("rotate_left", SelGroup.TURN, 17, 80),
    REPLACE("replace", SelGroup.TURN, 15),
    SELECT_ALL("select_all", SelGroup.TURN, 16, 72),
    TO_FRONT("bring_to_front", SelGroup.ARRANGE, 11, 88),
    FORWARD("bring_forward", SelGroup.ARRANGE, 12, 92),
    BACKWARD("send_backward", SelGroup.ARRANGE, 13, 96),
    TO_BACK("send_to_back", SelGroup.ARRANGE, 14, 84),
    COPY_AS_IMAGE("copy_as_image", SelGroup.EXPORT, 20, 96),
    SHARE_AS_IMAGE("share_as_image", SelGroup.EXPORT, 21, 100),
    SAVE_AS_IMAGE("save_as_image", SelGroup.EXPORT, 22, 96),
    LOCK("lock", SelGroup.LOCK, 23),
    ;

    companion object {
        fun fromId(id: String): SelAction? = entries.firstOrNull { it.id == id }
    }
}

/** What the selection is, as the bar needs it (SelectionMenuHost, read once per selection). */
internal data class SelFacts(
    val image: Boolean = false,
    val canStyle: Boolean = false,
    val canRotate: Boolean = false,
    val canFlip: Boolean = false,
    val canPaste: Boolean = false,
    val editable: Boolean = false,
    val sticky: Boolean = false,
    val table: Boolean = false,
    /** The one picture has been cropped, mirrored or turned, so Reset image has something to undo. */
    val imageEdited: Boolean = false,
)

/**
 * Whether this action can act on the selection [f]; one that cannot is on neither the bar nor More. Style only for
 * strokes and shapes; Edit for a note, text box or table, Colour for a note, the row, column and header tools for a
 * table; a picture's own tools for one picture (Reset only after an edit); Paste only while something is copied;
 * Rotate and Flip only when everything selected can turn or mirror. The rest act on anything.
 */
internal fun SelAction.appliesTo(f: SelFacts): Boolean = when (this) {
    SelAction.EDIT -> f.editable
    SelAction.NOTE_COLOUR -> f.sticky
    SelAction.STYLE -> f.canStyle
    SelAction.CROP, SelAction.SAVE_IMAGE, SelAction.REPLACE -> f.image
    SelAction.RESET_IMAGE -> f.image && f.imageEdited
    SelAction.ADD_ROW, SelAction.ADD_COLUMN, SelAction.HEADER -> f.table
    SelAction.PASTE -> f.canPaste
    SelAction.ROTATE, SelAction.ROTATE_LEFT -> f.canRotate
    SelAction.FLIP_H, SelAction.FLIP_V -> f.canFlip
    else -> true
}

/**
 * The bar as it ships (the approved mockup): Style or a picture's Crop, or Edit and a note's Colour; a table's tools;
 * Cut, Copy, Paste, Delete; Rotate, a picture's Replace, Select all. Everything else waits in More.
 */
internal val SEL_BAR_DEFAULT: Set<SelAction> = setOf(
    SelAction.EDIT, SelAction.NOTE_COLOUR, SelAction.STYLE, SelAction.CROP,
    SelAction.ADD_ROW, SelAction.ADD_COLUMN, SelAction.HEADER,
    SelAction.CUT, SelAction.COPY, SelAction.PASTE, SelAction.DELETE,
    SelAction.ROTATE, SelAction.REPLACE, SelAction.SELECT_ALL,
)

/** The actions kept on the bar, from the ids Settings saved; null (never customised, or reset) is [SEL_BAR_DEFAULT]. */
internal fun selectionBarChoice(ids: List<String>?): Set<SelAction> =
    ids?.mapNotNullTo(LinkedHashSet()) { SelAction.fromId(it) } ?: SEL_BAR_DEFAULT

/** The ids to save for [chosen], in the fixed order; the default saves as null, so it follows any later default. */
internal fun selectionBarIds(chosen: Set<SelAction>): List<String>? =
    if (chosen == SEL_BAR_DEFAULT) null else SelAction.entries.filter { it in chosen }.map { it.id }

/** [actions] (already in the fixed order) cut into their groups. */
private fun grouped(actions: List<SelAction>): List<List<SelAction>> {
    val out = ArrayList<MutableList<SelAction>>()
    for (a in actions) {
        if (out.isEmpty() || out.last().first().group != a.group) out.add(mutableListOf(a)) else out.last().add(a)
    }
    return out
}

/**
 * The bar's groups in order, each split from the next by a hairline; More follows the last. Only the [chosen] actions
 * that apply to the selection [f], in the fixed order; an empty group is left out.
 */
internal fun selectionGroups(f: SelFacts, chosen: Set<SelAction> = SEL_BAR_DEFAULT): List<List<SelAction>> =
    grouped(SelAction.entries.filter { it in chosen && it.appliesTo(f) })

/** More's groups, a rule between each: every action that applies to [f] and is not [chosen] for the bar. */
internal fun selectionMoreGroups(f: SelFacts, chosen: Set<SelAction> = SEL_BAR_DEFAULT): List<List<SelAction>> =
    grouped(SelAction.entries.filter { it !in chosen && it.appliesTo(f) })

/** More (64), each divider (1 + 6 either side), the pill's padding (10 each end), the gap between its children (2). */
internal const val SEL_MORE_DP = 64
internal const val SEL_DIVIDER_DP = 13
internal const val SEL_PADDING_DP = 20
internal const val SEL_GAP_DP = 2

/** However wide the pane, no more than this many actions sit on the bar for any one selection; the rest go to More. */
internal const val SEL_BAR_MAX = 12

/** The pill's width in dp for [groups] plus More: a divider after each non-empty group, a gap between children. */
internal fun selectionBarWidthDp(groups: List<List<SelAction>>): Int {
    val filled = groups.filter { it.isNotEmpty() }
    val children = filled.sumOf { it.size } + filled.size + 1
    return SEL_PADDING_DP + filled.sumOf { g -> g.sumOf { it.widthDp } } + SEL_MORE_DP + SEL_DIVIDER_DP * filled.size +
        SEL_GAP_DP * (children - 1)
}

/** The bar that fits: its groups, and what moved to the top of More, in the bar's own order. */
internal data class SelBar(val groups: List<List<SelAction>>, val overflow: List<SelAction>)

/**
 * The bar in [maxWidthDp] (the pane less 8 dp each side): drops the lowest-priority action (the highest number) until
 * the pill and More fit and no more than [SEL_BAR_MAX] actions are left, never below three droppable actions, never
 * an action of priority 0 (SC 229).
 */
internal fun fitSelectionBar(groups: List<List<SelAction>>, maxWidthDp: Int): SelBar {
    val shown = groups.map { it.toMutableList() }
    val dropped = mutableSetOf<SelAction>()
    while (selectionBarWidthDp(shown) > maxWidthDp || shown.sumOf { it.size } > SEL_BAR_MAX) {
        val droppable = shown.flatten().filter { it.priority > 0 }
        if (droppable.size <= 3) break
        val drop = droppable.maxBy { it.priority }
        shown.forEach { it.remove(drop) }
        dropped += drop
    }
    return SelBar(shown.filter { it.isNotEmpty() }.map { it.toList() }, groups.flatten().filter { it in dropped })
}

/**
 * The bar and its More menu for one selection: the bar's groups; what the bar had no room for, which leads More in
 * the bar's own order (SC 229); then More's own groups.
 */
internal data class SelMenuLayout(val bar: List<List<SelAction>>, val overflow: List<SelAction>, val more: List<List<SelAction>>) {
    /** Every action the bar shows. */
    val onBar: Set<SelAction> get() = bar.flatten().toSet()
}

internal fun selectionMenuLayout(f: SelFacts, chosen: Set<SelAction>, maxWidthDp: Int): SelMenuLayout {
    val fit = fitSelectionBar(selectionGroups(f, chosen), maxWidthDp)
    return SelMenuLayout(fit.groups, fit.overflow, selectionMoreGroups(f, chosen))
}

/** The kinds of selection Settings previews and limits the bar for, each as a selection that can do all it ever can. */
internal enum class SelKind(val facts: SelFacts) {
    INK(SelFacts(canStyle = true, canRotate = true, canFlip = true, canPaste = true)),
    PICTURE(SelFacts(image = true, canRotate = true, canFlip = true, canPaste = true, imageEdited = true)),
    NOTE(SelFacts(editable = true, sticky = true, canPaste = true)),
    TEXT_BOX(SelFacts(editable = true, canPaste = true)),
    TABLE(SelFacts(editable = true, table = true, canPaste = true)),
}

/** How many actions the bar would hold for [kind] with [chosen] on it, before any narrow-pane fit. */
internal fun selectionBarCount(kind: SelKind, chosen: Set<SelAction>): Int = selectionGroups(kind.facts, chosen).sumOf { it.size }

/** Whether Settings may put [a] on the bar as well: never past [SEL_BAR_MAX] actions for any kind of selection. */
internal fun canPutOnBar(chosen: Set<SelAction>, a: SelAction): Boolean =
    a in chosen || SelKind.entries.all { selectionBarCount(it, chosen + a) <= SEL_BAR_MAX }

/** What a floating toolbar covers of the pane along each edge, in px (ToolbarFrame's LocalToolbarCover); zero when docked. */
internal data class SelInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/**
 * Where the bar goes in the pane (SC 577-579; today's three cases): centred over [sel] (its rect already raised over
 * the rotate grip), 12 dp above it; under it when above would cross [clearTop] (the toolbar line); when neither fits,
 * 12 dp inside its top, never above [clearTop]. Kept 8 dp inside the pane across, and clear of what a floating
 * toolbar covers ([cover]) on every edge.
 */
internal fun selectionBarSpot(sel: IntRect, bar: IntSize, pane: IntSize, clearTop: Int, dp: Float, cover: SelInsets = SelInsets()): IntOffset {
    val gap = px(12, dp)
    val m = px(8, dp)
    val top = max(clearTop, cover.top)
    val bottom = pane.height - cover.bottom
    val x = inside((sel.left + sel.right) / 2 - bar.width / 2, bar.width, cover.left, pane.width - cover.right, m)
    val above = sel.top - gap - bar.height
    val below = sel.bottom + gap
    val y = when {
        above >= top -> above
        below + bar.height <= bottom - m -> below
        else -> (sel.top + gap).coerceIn(top, max(top, bottom - bar.height - m))
    }
    return IntOffset(x, y)
}

/** The rect a menu must stay in: [area] (the editor pane less the toolbar, window px) within [window]; else the window. */
private fun clearArea(window: IntSize, area: IntRect?): IntRect {
    val whole = IntRect(0, 0, window.width, window.height)
    if (area == null || area.width <= 0 || area.height <= 0) return whole
    val r = IntRect(max(area.left, 0), max(area.top, 0), min(area.right, window.width), min(area.bottom, window.height))
    return if (r.width <= 0 || r.height <= 0) whole else r
}

/** Below this much room (dp) on both sides of the bar, a menu takes the area's whole height instead. */
internal const val SEL_MENU_MIN_DP = 160

/**
 * How tall a bar menu or the Change style card may be (px): the room on the roomier side of the bar, 8 dp from it and
 * 8 dp inside [area], so whatever height it gets fits wholly on one side and [selectionMenuSpot] opens it there. A
 * cap measured for one side that then flips to the other is what clipped More, Select all and Lock out of view.
 * Under [SEL_MENU_MIN_DP] on both sides, the area's whole height less its margins.
 */
internal fun selectionMenuMaxHeight(barTop: Int, barBottom: Int, window: IntSize, dp: Float, area: IntRect? = null): Int {
    val r = clearArea(window, area)
    val m = px(8, dp)
    val gap = px(8, dp)
    val full = (r.height - 2 * m).coerceAtLeast(0)
    val room = max(r.bottom - m - (barBottom + gap), barTop - gap - (r.top + m))
    return if (room >= px(SEL_MENU_MIN_DP, dp)) min(room, full) else full
}

/**
 * Where a bar menu or the Change style card goes (SC 580-584): 8 dp under the bar ([barTop]..[barBottom]), its start
 * on its [button]'s, when it fits there; else 8 dp over the bar when it fits there (a bar near the foot); else on the
 * side with more room, kept inside. When that would lie over the selection ([sel], 12 dp clear either side), 16 dp
 * to the selection's left (but no further right than the button), else 16 dp to its right. Kept 8 dp inside [area],
 * the editor pane less what the floating toolbar covers (window px; the window when null), so a menu never lands on
 * the toolbar or crosses a split view's divider into the other pane. It grows from the button's centre, held 16 dp
 * inside its own width. Menus never open over the selection.
 */
internal fun selectionMenuSpot(
    button: IntRect,
    barTop: Int,
    barBottom: Int,
    sel: IntRect,
    menu: IntSize,
    window: IntSize,
    dp: Float,
    area: IntRect? = null,
): PopoverPlacement {
    val r = clearArea(window, area)
    val w = menu.width
    val h = menu.height
    val m = px(8, dp)
    val below = barBottom + px(8, dp)
    val above = barTop - px(8, dp) - h
    val roomBelow = r.bottom - m - below
    val roomAbove = barTop - px(8, dp) - (r.top + m)
    val flip = when {
        h <= roomBelow -> false
        h <= roomAbove -> true
        else -> roomAbove > roomBelow
    }
    val y = inside(if (flip) above else below, h, r.top, r.bottom, m)
    var x = button.left
    if (y < sel.bottom && y + h > sel.top && x < sel.right + px(12, dp) && x + w > sel.left - px(12, dp)) {
        val left = sel.left - px(16, dp) - w
        x = if (left >= r.left + m) min(left, button.left) else sel.right + px(16, dp)
    }
    x = inside(x, w, r.left, r.right, m)
    val inset = px(16, dp)
    val ox = ((button.left + button.right) / 2 - x).coerceIn(inset, max(inset, w - inset))
    return if (flip) {
        PopoverPlacement(x, y, PopoverSide.ABOVE, fraction(ox.toFloat(), w), 1f)
    } else {
        PopoverPlacement(x, y, PopoverSide.BELOW, fraction(ox.toFloat(), w), 0f)
    }
}

/**
 * The long-press card (SC 649-650; today's AbovePressPosition): centred on the [press], 28 dp above it so the finger
 * does not cover it; 28 dp below when there is no room above. Kept 8 dp inside the window; it grows from the press.
 */
internal fun longPressSpot(press: IntOffset, content: IntSize, window: IntSize, dp: Float): PopoverPlacement {
    val w = content.width
    val h = content.height
    val gap = px(28, dp)
    val m = px(8, dp)
    val left = inside(press.x - w / 2, w, window.width, m)
    val ox = fraction((press.x - left).toFloat(), w)
    val above = press.y - gap - h
    return if (above >= m) {
        PopoverPlacement(left, above, PopoverSide.ABOVE, ox, 1f)
    } else {
        PopoverPlacement(left, min(press.y + gap, window.height - h - m).coerceAtLeast(m), PopoverSide.BELOW, ox, 0f)
    }
}

/** A text row of the long-press card (SC 637-640). */
internal enum class LpRow { PASTE, PASTE_IMAGE, SELECT_ALL, COPY_PAGE, SHARE_PAGE }

/** An "Add here" tile (SC 644). */
internal enum class LpTile { STICKY_NOTE, TEXT_BOX, IMAGE, TABLE }

/** What the long-press card holds: [unlockOnly] on a locked object; else [rows], then the [tiles]. */
internal data class LpLayout(
    val unlockOnly: Boolean,
    val rows: List<LpRow>,
    val ruleBeforeSelectAll: Boolean,
    val tiles: List<LpTile>,
) {
    /** A hairline between the rows and "Add here" (SC 642). */
    val ruleBeforeTiles: Boolean get() = rows.isNotEmpty() && tiles.isNotEmpty()
}

/**
 * The card's contents (SC 635-643, 667-670; today's LongPressMenu): only what can happen here. Paste and Paste image
 * while there is something to paste; Select all after a hairline when rows came before it; the page as a picture; then
 * the four tiles, or only Image… where sticky notes, text boxes and tables cannot go (the canvas). A locked object
 * offers only Unlock.
 */
internal fun longPressLayout(
    locked: Boolean,
    clipItems: Boolean,
    clipImage: Boolean,
    selectAll: Boolean,
    sharePage: Boolean,
    insertObjects: Boolean,
): LpLayout {
    if (locked) return LpLayout(unlockOnly = true, rows = emptyList(), ruleBeforeSelectAll = false, tiles = emptyList())
    val rows = buildList {
        if (clipItems) add(LpRow.PASTE)
        if (clipImage) add(LpRow.PASTE_IMAGE)
        if (selectAll) add(LpRow.SELECT_ALL)
        if (sharePage) {
            add(LpRow.COPY_PAGE)
            add(LpRow.SHARE_PAGE)
        }
    }
    val tiles = if (insertObjects) listOf(LpTile.STICKY_NOTE, LpTile.TEXT_BOX, LpTile.IMAGE, LpTile.TABLE) else listOf(LpTile.IMAGE)
    return LpLayout(unlockOnly = false, rows = rows, ruleBeforeSelectAll = selectAll && (clipItems || clipImage), tiles = tiles)
}

private fun px(v: Int, dp: Float): Int = (v * dp).roundToInt()

/** [start] moved so a [size] span stays [margin] inside [0, extent]; a span too big for that starts at [margin]. */
private fun inside(start: Int, size: Int, extent: Int, margin: Int): Int = max(margin, min(start, extent - margin - size))

/** The same within [lo, hi]: a span too big for it starts [margin] after [lo]. */
private fun inside(start: Int, size: Int, lo: Int, hi: Int, margin: Int): Int = max(lo + margin, min(start, hi - margin - size))

/** [p] along a [size] span as a fraction, kept on the span. */
private fun fraction(p: Float, size: Int): Float = if (size <= 0) 0.5f else (p / size).coerceIn(0f, 1f)
