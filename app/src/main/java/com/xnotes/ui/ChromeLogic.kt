package com.xnotes.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.model.PageSize
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.settings.ToolbarPosition
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The editor chrome's rules (B2 Part 3), pure so they are tested on the JVM: where an anchored card
 * goes, which taps open a card, what the More menu holds and when it lights, the pen card's steps and
 * millimetres, the save label, the Insert card's tiles and the pen box rail's place.
 */

// --- anchored cards ---

/** Which side of its reference edge an anchored card opens on. */
internal enum class PopoverSide { BELOW, ABOVE, END, START }

/**
 * How a card hangs off its anchor, in px. [lead]: along the cross axis the card starts this far before the anchor
 * button's start (tool cards 26 dp, the ⋯ menu 8 dp); with [endAligned] the card's END edge sits [lead] px past the
 * anchor's end instead (Insert: 6 dp). [gap]: px between the reference edge and the card (10 dp below the toolbar pill,
 * 8 dp below a header button). [margin]: kept inside the window (8 dp).
 */
internal data class PopoverSpec(val lead: Int, val gap: Int, val margin: Int, val endAligned: Boolean = false)

/** Where the card goes ([x], [y] window px), the side it took, and its transform origin as fractions of its size. */
internal data class PopoverPlacement(val x: Int, val y: Int, val side: PopoverSide, val originX: Float, val originY: Float)

/**
 * A card's own placement rule, for a card that hangs where [placePopover] does not put it: beside the card it came
 * from, under a swatch, above a pill, beside a selection (Part 5). [anchor] and [edge] are what InkPopover's position
 * provider reads (the edge is the anchor when no bar provides one); [window] and [content] are the window and the
 * card, in px.
 *
 * [maxHeight], when a placer gives one, is the card's height cap in px for [window], measured the way [place] chooses
 * its side, so the cap and the side never disagree (a cap measured for one side, then a flip to the other, clips the
 * card). Null keeps InkPopover's own [popoverMaxHeight].
 */
internal fun interface PopoverPlacer {
    fun place(anchor: IntRect, edge: IntRect, window: IntSize, content: IntSize): PopoverPlacement

    fun maxHeight(window: IntSize): Int? = null
}

/**
 * [anchor]: the button, window px. [edge]: what the gap is measured from: the toolbar pill for tool cards, else the
 * button itself. [prefer]: BELOW for a top bar and the header, ABOVE for a bottom bar, END for a left rail, START for
 * a right rail. Falls to the opposite side only when the preferred one has no room and the other does; otherwise
 * stays and is clamped. Always clamped [PopoverSpec.margin] inside [window] on both axes.
 */
internal fun placePopover(
    anchor: IntRect,
    edge: IntRect,
    window: IntSize,
    content: IntSize,
    spec: PopoverSpec,
    prefer: PopoverSide,
): PopoverPlacement {
    val w = content.width
    val h = content.height
    val m = spec.margin
    if (prefer == PopoverSide.BELOW || prefer == PopoverSide.ABOVE) {
        val below = edge.bottom + spec.gap
        val above = edge.top - spec.gap - h
        val fitsBelow = below + h <= window.height - m
        val fitsAbove = above >= m
        val side = if (prefer == PopoverSide.BELOW) {
            if (!fitsBelow && fitsAbove) PopoverSide.ABOVE else PopoverSide.BELOW
        } else {
            if (!fitsAbove && fitsBelow) PopoverSide.BELOW else PopoverSide.ABOVE
        }
        val x = inside(if (spec.endAligned) anchor.right + spec.lead - w else anchor.left - spec.lead, w, window.width, m)
        val y = inside(if (side == PopoverSide.BELOW) below else above, h, window.height, m)
        val originX = fraction((anchor.left + anchor.right) / 2f - x, w)
        return PopoverPlacement(x, y, side, originX, if (side == PopoverSide.BELOW) 0f else 1f)
    }
    val end = edge.right + spec.gap
    val start = edge.left - spec.gap - w
    val fitsEnd = end + w <= window.width - m
    val fitsStart = start >= m
    val side = if (prefer == PopoverSide.END) {
        if (!fitsEnd && fitsStart) PopoverSide.START else PopoverSide.END
    } else {
        if (!fitsStart && fitsEnd) PopoverSide.END else PopoverSide.START
    }
    val x = inside(if (side == PopoverSide.END) end else start, w, window.width, m)
    val y = inside(if (spec.endAligned) anchor.bottom + spec.lead - h else anchor.top - spec.lead, h, window.height, m)
    val originY = fraction((anchor.top + anchor.bottom) / 2f - y, h)
    return PopoverPlacement(x, y, side, if (side == PopoverSide.END) 0f else 1f, originY)
}

/**
 * A header card ([p], [content] px) kept clear of the pane's top toolbar ([bar], window px; null or empty when there
 * is none): a card hanging under a header button would otherwise lie over the bar's end (its swatches), so when the two
 * meet it drops to [gap] under the bar, as a tool card hangs from it, kept [margin] inside the window. A card that
 * misses the bar, opens above, or has a bar along another edge ([barTop] false) is left where it is.
 */
internal fun clearOfTopBar(p: PopoverPlacement, content: IntSize, bar: IntRect?, barTop: Boolean, window: IntSize, gap: Int, margin: Int): PopoverPlacement {
    if (bar == null || bar.width <= 0 || bar.height <= 0 || !barTop || p.side != PopoverSide.BELOW) return p
    val meets = p.x < bar.right && p.x + content.width > bar.left && p.y < bar.bottom && p.y + content.height > bar.top
    if (!meets) return p
    return p.copy(y = inside(bar.bottom + gap, content.height, window.height, margin))
}

/** Tallest a card may be so it stays [margin] px inside the window: window.height - 2 * margin. Cards scroll inside. */
internal fun popoverMaxHeight(window: IntSize, margin: Int): Int = (window.height - 2 * margin).coerceAtLeast(0)

/**
 * Tallest a card hanging off [edge] on [side] may be, so it stays on that side and scrolls rather than being clamped
 * up (or down) over the bar it hangs from: the room between the edge (plus [gap]) and the window's [margin] on that
 * side. If that side has under [minSide], the other side's room when it has [minSide] (the card then flips there);
 * else the window less both margins, as [popoverMaxHeight]. Rail cards (END, START) and a card with no [edge] yet
 * take the full height too. Never more than the full height.
 */
internal fun popoverMaxHeight(window: IntSize, margin: Int, edge: IntRect?, gap: Int, side: PopoverSide, minSide: Int): Int {
    val full = popoverMaxHeight(window, margin)
    if (edge == null) return full
    val below = window.height - margin - (edge.bottom + gap)
    val above = edge.top - gap - margin
    val (mine, other) = when (side) {
        PopoverSide.BELOW -> below to above
        PopoverSide.ABOVE -> above to below
        PopoverSide.END, PopoverSide.START -> return full
    }
    val room = when {
        mine >= minSide -> mine
        other >= minSide -> other
        else -> full
    }
    return room.coerceIn(0, full)
}

/** Below this much room on its side (dp) a card stops hanging there and takes the other side, or the whole height. */
internal const val POPOVER_MIN_SIDE_DP = 240

/** [start] moved so a [size] span stays [margin] inside [0, extent]; a span too big for that starts at [margin]. */
private fun inside(start: Int, size: Int, extent: Int, margin: Int): Int = max(margin, min(start, extent - margin - size))

/** [px] along a [size] span as a fraction, kept on the span. */
private fun fraction(px: Float, size: Int): Float = if (size <= 0) 0.5f else (px / size).coerceIn(0f, 1f)

// --- tools and their cards ---

/** The surface a bar belongs to. */
internal enum class ToolSurface { NOTE, CANVAS }

/**
 * Whether tapping [this] tool again (while it is armed) opens a card on [surface]. NOTE: the strokes, SHAPE, ERASER,
 * LASSO, TEXT, MARKUP and TAPE. CANVAS: the strokes, ERASER, SHAPE, LASSO and TAPE; the canvas has no text or markup
 * tool. SELECT has none (round-3 default 4): no bar button can re-tap it, so its old card could never open.
 */
internal fun Tool.hasCard(surface: ToolSurface): Boolean = when (surface) {
    ToolSurface.NOTE -> isStroke || this == Tool.SHAPE || this == Tool.ERASER ||
        this == Tool.LASSO || this == Tool.TEXT || this == Tool.MARKUP || this == Tool.TAPE
    ToolSurface.CANVAS -> isStroke || this == Tool.ERASER || this == Tool.SHAPE ||
        this == Tool.LASSO || this == Tool.TAPE
}

internal enum class ToolTap { ARM, OPEN_CARD, CLOSE_CARD }

/**
 * A tap on [tapped] with [armed] in hand and [open] the tool whose card is up (or null). A different tool: ARM. The
 * armed tool with its card up: CLOSE_CARD. The armed tool with a card to open: OPEN_CARD. Otherwise ARM.
 */
internal fun toolTap(armed: Tool, tapped: Tool, open: Tool?, surface: ToolSurface): ToolTap = when {
    tapped != armed -> ToolTap.ARM
    open == tapped -> ToolTap.CLOSE_CARD
    tapped.hasCard(surface) -> ToolTap.OPEN_CARD
    else -> ToolTap.ARM
}

// --- the More menu ---

/** A ⋯ row: a tool (arms it), a switch (Ruler; toggles, the menu stays open) or an action (Insert image…; runs, closes). */
internal enum class MoreKind { TOOL, SWITCH, ACTION }

internal data class MoreItem(val item: ToolbarItem, val kind: MoreKind)

/**
 * The ⋯ menu's rows, in bar order: `layout.hiddenItems()` minus HEADER_ITEMS, RETIRED, MARKUP without a PDF, and pens
 * the pen button covers (`penButtonCovers`), and minus the dead rows (survey §2.5: WAND, SIDEBAR, ZOOM_LOCK,
 * FULLSCREEN, MINIMAP, FIT, all header or retired items). NOTE gives TOOL rows for hidden tools, SWITCH for RULER,
 * ACTION for IMAGE (Insert image…). CANVAS gives TOOL rows (its own items only) and ACTION for IMAGE.
 */
internal fun moreItems(layout: ToolbarLayout, surface: ToolSurface, hasPdf: Boolean): List<MoreItem> =
    layout.hiddenItems().mapNotNull { item ->
        when {
            item in ToolbarLayout.HEADER_ITEMS || item in ToolbarLayout.RETIRED -> null
            // Checked before the tool lookup: Tool.fromId("image") is the Image tool, but the row inserts a picture.
            item == ToolbarItem.IMAGE -> MoreItem(item, MoreKind.ACTION)
            item == ToolbarItem.RULER -> if (surface == ToolSurface.NOTE) MoreItem(item, MoreKind.SWITCH) else null
            item == ToolbarItem.MARKUP && (surface == ToolSurface.CANVAS || !hasPdf) -> null
            surface == ToolSurface.CANVAS && item !in ToolbarLayout.CANVAS_ITEMS -> null
            else -> {
                val tool = Tool.fromId(item.id)
                // Not a tool (undo, redo, the colours, More itself), or a pen reached through the pen button.
                if (tool == null || layout.penButtonCovers(tool)) null else MoreItem(item, MoreKind.TOOL)
            }
        }
    }

/** The ⋯ button's two lit states (mockup TO 901-905): a hidden tool in hand wins over a hidden switch that is on. */
internal enum class MoreLit { NONE, SWITCH_ON, TOOL_IN_HAND }

/** [inHand]: the armed tool's bar item (`tool.barItem()`), or null. [switchOn]: whether a SWITCH row's item is on. */
internal fun moreLit(items: List<MoreItem>, inHand: ToolbarItem?, switchOn: (ToolbarItem) -> Boolean): MoreLit = when {
    inHand != null && items.any { it.kind == MoreKind.TOOL && it.item == inHand } -> MoreLit.TOOL_IN_HAND
    items.any { it.kind == MoreKind.SWITCH && switchOn(it.item) } -> MoreLit.SWITCH_ON
    else -> MoreLit.NONE
}

// --- the pen card ---

/**
 * The pen card's four stabilisation steps (Off, Low, Med, High). Task 12 retires PenPopover's private copy. The
 * stored `ToolConfig.stabilisation` is a Double: callers pass `.toFloat()` in and take `.toDouble()` out.
 */
internal val STAB_STEPS: List<Float> = listOf(0f, 1f / 3, 2f / 3, 1f)

/** The step a stored stabilisation reads as: the nearest, so a value saved between steps snaps to one. */
internal fun stabIndex(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    return STAB_STEPS.indices.minByOrNull { abs(STAB_STEPS[it] - v) } ?: 0
}

/** The stabilisation step [index] stores, clamped to the four steps. */
internal fun stabValue(index: Int): Float = STAB_STEPS[index.coerceIn(0, STAB_STEPS.lastIndex)]

private const val MM_PER_INCH = 25.4f

/** A width in page pixels as the millimetres a page prints it at ("0.5 mm"); same maths as before it moved here. */
internal fun widthLabelMm(px: Float): String =
    String.format(Locale.US, "%.1f mm", px * MM_PER_INCH / PageSize.DEFAULT_DPI)

/** [widthLabelMm] for today's callers, which hold a Double (ToolConfig, TapeConfig, the selection's width). */
internal fun widthLabelMm(px: Double): String = widthLabelMm(px.toFloat())

/** The same width as a bare number to one decimal ("0.5"), for the pen box's captions. Today's `PenBoxRail.widthMm` maths. */
internal fun widthValueMm(px: Float): String {
    val mm = px * MM_PER_INCH / PageSize.DEFAULT_DPI
    return String.format(Locale.US, "%.1f", (mm * 10).roundToInt() / 10.0)
}

/** [widthValueMm] for a pen box preset's `config.baseWidth`, a Double. */
internal fun widthValueMm(px: Double): String = widthValueMm(px.toFloat())

/**
 * The pen card's thickness stepper: [px] moved [steps] tenths of a millimetre, from the tenth it reads as (so the label
 * always moves by exactly 0.1 mm), clamped to the tool's [rangePx].
 */
internal fun stepWidthPx(px: Float, steps: Int, rangePx: ClosedFloatingPointRange<Float>): Float {
    val tenths = (px * MM_PER_INCH / PageSize.DEFAULT_DPI * 10).roundToInt() + steps
    return (tenths / 10f * PageSize.DEFAULT_DPI / MM_PER_INCH).coerceIn(rangePx.start, rangePx.endInclusive)
}

// --- the header's fit in a narrow pane ---

/** The header bar's widths, dp (mockup §1.1-1.3): its padding (8 + 12), the gap between its items, a 44 dp button. */
internal const val HEADER_PADDING_DP = 20
internal const val HEADER_GAP_DP = 6
internal const val HEADER_BUTTON_DP = 44

/**
 * The least of the note's name the header keeps readable in a narrow pane, dp: a word or two, ellipsised. A little over
 * 80, so the name keeps 80 even where the save status comes out a few dp wider than [HEADER_SAVE_STATUS_DP].
 */
internal const val HEADER_TITLE_TEXT_MIN_DP = 84

/** The title's own room around its text: 4 dp of padding at each side of its tap target. */
internal const val HEADER_TITLE_PADDING_DP = 8

/** The save status's widest label ("Saving…", as wide as "✓ Saved"), dp, and its 12 dp gap after the name. */
internal const val HEADER_SAVE_STATUS_DP = 56
internal const val HEADER_SAVE_GAP_DP = 12

/**
 * The least the title block keeps before the capsule gives way: [HEADER_TITLE_TEXT_MIN_DP] of the name and its padding,
 * then, paged, the save status and its gap; the canvas has the name alone. At the Tab S8's 50/50 split (a 640 dp pane)
 * this is what the title shows, and the capsule's last tools scroll under its pinned ⋯.
 */
internal const val HEADER_TITLE_MIN_CANVAS_DP = HEADER_TITLE_TEXT_MIN_DP + HEADER_TITLE_PADDING_DP
internal const val HEADER_TITLE_MIN_NOTE_DP = HEADER_TITLE_MIN_CANVAS_DP + HEADER_SAVE_GAP_DP + HEADER_SAVE_STATUS_DP

/** The capsule's pinned end, dp: the rule before ⋯ (1 + 4 + 4), ⋯ itself and the 2 dp end padding. */
internal const val HEADER_CAPSULE_END_DP = 9 + HEADER_BUTTON_DP + 2

/**
 * How wide the counter and capsule may be, out of [available]: what the header row offers them once Back (and any
 * recorder) are placed. The rest is kept for the title's least, [titleMin], and, with [closeButton] (a split pane),
 * for Close pane and its gap, so Close pane keeps its full width at any pane width. All in one unit (px or dp); the
 * header's items are [gap] apart. Past this the capsule's tools scroll under its pinned ⋯. In a pane too narrow for
 * both, the capsule's pinned end ([endMin]) wins over the title's least, so ⋯ is never cut.
 */
internal fun headerToolsRoom(available: Int, titleMin: Int, gap: Int, closeButton: Int?, endMin: Int = 0): Int {
    val close = closeButton?.let { gap + it } ?: 0
    return maxOf(available - gap - titleMin - close, minOf(endMin, available - gap - close)).coerceAtLeast(0)
}

// --- the header's save status ---

/** Save status. */
internal enum class SaveLabel { SAVING, EDITED, SAVED }

/** Saving while a save runs, else Edited while there are unsaved changes, else Saved. */
internal fun saveLabel(saving: Boolean, dirty: Boolean): SaveLabel = when {
    saving -> SaveLabel.SAVING
    dirty -> SaveLabel.EDITED
    else -> SaveLabel.SAVED
}

/** What the header shows next: while the pen is down it keeps what it shows; otherwise the latest. */
internal fun nextShownLabel(shown: SaveLabel, latest: SaveLabel, penDown: Boolean): SaveLabel = if (penDown) shown else latest

/** The tick springs in only on the way into SAVED. */
internal fun playsSavedCheck(from: SaveLabel, to: SaveLabel): Boolean = to == SaveLabel.SAVED && from != SaveLabel.SAVED

// --- the Insert card ---

/** The Insert card's tiles. */
internal enum class InsertTile { PDF, VOICE, IMAGE, CAMERA, SCAN, AUDIO_FILE, STICKY_NOTE, TABLE }

/** The insert a tile stands for; null for the sticky note and the table, which have their own callbacks. */
internal val InsertTile.kind: InsertKind?
    get() = when (this) {
        InsertTile.PDF -> InsertKind.PDF
        InsertTile.VOICE -> InsertKind.VOICE
        InsertTile.IMAGE -> InsertKind.IMAGE
        InsertTile.CAMERA -> InsertKind.CAMERA
        InsertTile.SCAN -> InsertKind.SCAN
        InsertTile.AUDIO_FILE -> InsertKind.AUDIO_FILE
        InsertTile.STICKY_NOTE, InsertTile.TABLE -> null
    }

internal data class InsertLayout(val wide: List<InsertTile>, val grid: List<InsertTile>)

/**
 * Wide row = PDF, VOICE (those of them in [kinds]); grid = IMAGE, CAMERA, SCAN, AUDIO_FILE in [kinds], then STICKY_NOTE
 * if [sticky], TABLE if [table]. Canvas (IMAGE, CAMERA, SCAN only) → no wide row, one grid row.
 */
internal fun insertLayout(kinds: Set<InsertKind>, sticky: Boolean, table: Boolean): InsertLayout {
    val wide = listOf(InsertTile.PDF, InsertTile.VOICE).filter { it.kind in kinds }
    val media = listOf(InsertTile.IMAGE, InsertTile.CAMERA, InsertTile.SCAN, InsertTile.AUDIO_FILE).filter { it.kind in kinds }
    val placed = listOfNotNull(InsertTile.STICKY_NOTE.takeIf { sticky }, InsertTile.TABLE.takeIf { table })
    return InsertLayout(wide, media + placed)
}

// --- the pen box rail ---

/** Below this screen height (dp) the rail stays away: a phone in landscape has no room to spare. */
private const val PEN_RAIL_MIN_HEIGHT_DP = 480

/** The pen box rail: on the left only when the toolbar is on the right. */
internal fun penRailOnLeft(position: ToolbarPosition): Boolean = position == ToolbarPosition.RIGHT

/** The rail shows with a pen box and >= 480 dp of screen height. */
internal fun showsPenRail(hasPenBox: Boolean, screenHeightDp: Int): Boolean = hasPenBox && screenHeightDp >= PEN_RAIL_MIN_HEIGHT_DP
