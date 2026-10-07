package com.xnotes.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.DrawStyle
import com.xnotes.core.model.ImageCropSession
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.StickyColors
import com.xnotes.platform.SelectionImageExport
import com.xnotes.ui.icons.Fl
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillDivider
import com.xnotes.ui.kit.InkPillLook
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.InkReadoutRow
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.rememberActOnce
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/**
 * What the selection menu needs from whichever editor is open.
 *
 * The bar is the same bar on either canvas, so it is the same composable rather than a second one
 * that resembles it. Taking the few members it reads through an interface is what makes "identical"
 * a property of the code rather than something to keep checking, exactly as [ToolPopupHost] does
 * for the tool popups.
 */
interface SelectionMenuHost {
    /** Where the settled selection sits in viewport pixels, or null to hide the bar. */
    val selectionMenuRect: com.xnotes.core.geometry.Rect?

    fun deleteSelection()
    fun cutSelection()
    fun copySelection()
    fun bringToFront()
    fun sendToBack()
    fun duplicateSelection()
    fun dismissSelectionMenu()

    /** Pin the selection where it is and put it away; a held finger over it offers to release it. */
    fun lockSelection()

    /** The colour and width of every selected stroke/shape, for the restyle popup to open on. */
    fun selectionStyles(): List<DrawStyle>

    /**
     * Recolour and/or re-thicken the selection; a null [color] or [width] leaves that half alone.
     * A [preview] call skips history, and the next call without it records everything since as one
     * undo step, so dragging the thickness slider is a single edit rather than one per sample.
     */
    fun restyleSelection(color: Rgba?, width: Double?, preview: Boolean = false)

    /** The toolbar's ink swatches and recently picked colours, offered by the restyle popup. */
    val hostToolbarColors: List<Rgba>
    val hostRecentColors: List<Rgba>

    // --- arrange, turn, mirror ---

    /** Raise the selection one level, past the next thing it overlaps. */
    fun bringForward()

    /** Lower the selection one level, under the next thing it overlaps. */
    fun sendBackward()

    /** True when everything selected can be turned (ink, shapes, pictures; not text boxes). */
    val selectionCanRotate: Boolean

    /** True when everything selected can be mirrored. */
    val selectionCanFlip: Boolean

    /** Turn the selection a quarter turn about its middle; a single picture turns in its own frame. */
    fun rotateSelection(clockwise: Boolean)

    /** Mirror the selection about its middle, left↔right ([horizontal]) or top↔bottom. */
    fun flipSelection(horizontal: Boolean)

    /** Whether the in-app clipboard holds copied items to paste. */
    val hasClipboardItems: Boolean

    /** Paste the copied items just beside the selection, and select them. */
    fun pasteItemsNearSelection()

    /** Select everything on the page (the canvas: everything on it). */
    fun selectAllObjects()

    // --- the selection as a picture ---

    /** Render the selection to a PNG and put it on the system clipboard. */
    fun copySelectionAsImage()

    /** Render the selection to a PNG and open the share sheet with it. */
    fun shareSelectionAsImage()

    /** Snapshot the selection for "Save as image"; the file name to offer, or null with nothing selected. */
    fun prepareSelectionImageSave(): String?

    /** Write the snapshot taken by [prepareSelectionImageSave] as a PNG to [uri]. */
    fun saveSelectionImage(uri: Uri)

    // --- one picture ---

    /** The selection when it is exactly one picture, which brings the image tools forward. */
    val selectedImage: ImageItem?

    /** Undo every crop, mirror and turn on the selected picture, as one step. */
    fun resetSelectedImage()

    /** Remember which picture "Replace image" is for, before the file picker opens. */
    fun prepareImageReplace(): Boolean

    /** Swap the remembered picture's file for the one at [uri], keeping its frame. */
    fun replaceSelectedImage(uri: Uri)

    /** Plan saving the selected picture, before the save dialog opens. */
    fun prepareImageSave(): SelectionImageExport.ImageSave?

    /** Write the planned picture to [uri]. */
    fun saveImage(uri: Uri)

    /** The crop in progress, or null. While set the editor shows the whole picture under it. */
    val imageCrop: ImageCropSession?

    /** Open the crop tool on the selected picture. */
    fun beginImageCrop()

    /** Leave the crop tool, keeping the crop ([apply]) or putting the picture back as it was. */
    fun endImageCrop(apply: Boolean)

    /** A point in the picture's own (item) space, in viewport pixels. */
    fun imageCropToViewport(p: Pt): Pt

    // --- a typed object: sticky note, text box or table (the paged note only) ---

    /** Whether the selection is one sticky note, text box or table that can be opened for typing. */
    val selectionEditable: Boolean get() = false

    /** Open the selected note, text box or table for typing. */
    fun editSelection() {}

    /** The colour of the one selected sticky note, or null when the selection is not one. */
    val selectionStickyColor: Rgba? get() = null

    /** Recolour the selected sticky note. */
    fun setStickyColor(color: Rgba) {}

    /** Whether the selection is one table, which brings its row and column actions forward. */
    val selectionIsTable: Boolean get() = false

    /** Whether the one selected table has its header row on, for the bar's lit Header (TI 867). */
    val selectionTableHeader: Boolean get() = false

    fun tableInsertRow(below: Boolean) {}
    fun tableInsertColumn(right: Boolean) {}
    fun tableToggleHeader() {}

    // --- the bar itself ---

    /**
     * The actions the user keeps on the bar, by [SelAction.id] (Settings › General › Selection bar); null is the
     * default bar. Snapshot state on both editors, so a change in Settings reaches an open bar at once.
     */
    val selectionBarIds: List<String>? get() = null
}

/** A "save to a file of this type" picker whose type is chosen per call (a picture keeps its own). */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** A bar action's own menu or card (SC 571-573, 621). Flip and Arrange are flat rows in More (the Fluent bar). */
private enum class SelMenu { MORE, STYLE, NOTE_COLOUR }

/** .mrow (selbar-fluent): 14.5 Medium. */
private val SelRowLabel = InkType.row.copy(fontSize = 14.5.sp)

/** .btn (selbar-fluent): a 24 dp icon 4 over a 12.5 Medium label in --text, on a 14 dp-corner action. */
private val SelBarLook = InkPillLook(
    iconSize = 24.dp,
    label = InkType.hint.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelInText = true,
    gap = 4.dp,
    shape = RoundedCornerShape(14.dp),
)

/** .pill (selbar-fluent): 10 dp at each end; .sep: 34 dp tall. */
private val SelBarPadding = 10.dp
private val SelBarDivider = 34.dp

/** How far below the pane's top the bar stays, so it never lands on the floating toolbar (today's clearOfBar; R4 #9). */
private val TOOLBAR_CLEAR = 120.dp

/** An action's Fluent icon (Regular). Header shows whether the table has its header row on. */
internal fun selActionIcon(a: SelAction, header: Boolean = false): ImageVector = when (a) {
    SelAction.EDIT -> Fl.edit
    SelAction.NOTE_COLOUR, SelAction.STYLE -> Fl.color
    SelAction.CROP -> Fl.crop
    SelAction.ADD_ROW -> Fl.tableInsertRow
    SelAction.ADD_COLUMN -> Fl.tableInsertColumn
    SelAction.HEADER -> if (header) Fl.table else Fl.tableSimple
    SelAction.CUT -> Fl.cut
    SelAction.COPY -> Fl.copy
    SelAction.PASTE -> Fl.clipboardPaste
    SelAction.DELETE -> Fl.delete
    SelAction.DUPLICATE -> Fl.copyAdd
    SelAction.SAVE_IMAGE, SelAction.SAVE_AS_IMAGE -> Fl.arrowDownload
    SelAction.RESET_IMAGE -> Fl.arrowReset
    SelAction.ROTATE -> Fl.arrowRotateClockwise
    SelAction.FLIP_H -> Fl.flipHorizontal
    SelAction.FLIP_V -> Fl.flipVertical
    SelAction.ROTATE_LEFT -> Fl.arrowRotateCounterclockwise
    SelAction.REPLACE -> Fl.arrowSwap
    SelAction.SELECT_ALL -> Fl.selectAllOn
    SelAction.TO_FRONT -> Fl.positionToFront
    SelAction.FORWARD -> Fl.positionForward
    SelAction.BACKWARD -> Fl.positionBackward
    SelAction.TO_BACK -> Fl.positionToBack
    SelAction.COPY_AS_IMAGE -> Fl.imageCopy
    SelAction.SHARE_AS_IMAGE -> Fl.shareAndroid
    SelAction.LOCK -> Fl.lockClosed
}

/** An action's name: the bar's short label when [bar], else the full one More and Settings show (Rotate right, Replace image, Header row). */
@StringRes
internal fun selActionLabel(a: SelAction, bar: Boolean): Int = when (a) {
    SelAction.EDIT -> R.string.sel_edit
    SelAction.NOTE_COLOUR -> R.string.sel_note_colour
    SelAction.STYLE -> R.string.sel_style
    SelAction.CROP -> R.string.sel_crop
    SelAction.ADD_ROW -> R.string.sel_table_add_row
    SelAction.ADD_COLUMN -> R.string.sel_table_add_column
    SelAction.HEADER -> if (bar) R.string.ptable_header_row else R.string.sel_table_header
    SelAction.CUT -> R.string.cut
    SelAction.COPY -> R.string.copy
    SelAction.PASTE -> R.string.paste
    SelAction.DELETE -> R.string.delete
    SelAction.DUPLICATE -> R.string.duplicate
    SelAction.SAVE_IMAGE -> R.string.save_image
    SelAction.RESET_IMAGE -> R.string.reset_image
    SelAction.ROTATE -> if (bar) R.string.sel_rotate else R.string.rotate_right
    SelAction.FLIP_H -> R.string.sel_flip_h
    SelAction.FLIP_V -> R.string.sel_flip_v
    SelAction.ROTATE_LEFT -> R.string.rotate_left
    SelAction.REPLACE -> if (bar) R.string.sel_replace else R.string.replace_image
    SelAction.SELECT_ALL -> R.string.sel_select_all
    SelAction.TO_FRONT -> R.string.bring_to_front
    SelAction.FORWARD -> R.string.bring_forward
    SelAction.BACKWARD -> R.string.send_backward
    SelAction.TO_BACK -> R.string.sel_send_to_back
    SelAction.COPY_AS_IMAGE -> R.string.copy_as_image
    SelAction.SHARE_AS_IMAGE -> R.string.sel_share
    SelAction.SAVE_AS_IMAGE -> R.string.save_as_image
    SelAction.LOCK -> R.string.lock
}

/**
 * The floating action bar over a settled selection (selbar-fluent): the toolbar's sibling, a 64 dp pill of labelled
 * Fluent actions grouped by job and split by hairlines, More last and lit while open. Which actions sit on the bar is
 * the user's choice (Settings › General › Selection bar, [SelectionMenuHost.selectionBarIds]); every other action that
 * works on the selection waits in More, in the same fixed order and groups ([selectionMenuLayout]). One that does not
 * work on it (Style for a picture, Rotate for a text box, Paste with nothing copied) is on neither. The Style button
 * wears the selection's ink. On a narrow pane the bar keeps what fits by priority and the rest lead More
 * ([fitSelectionBar]). Its menus open beside the selection, never over it ([selectionMenuSpot]); they are not
 * focusable, so a tap on the page both closes one and reaches the page. Hidden while moving or resizing; while the
 * crop tool is open it gives way to [ImageCropOverlay].
 *
 * The file pickers it needs (replace, save) are registered here, before anything can return early, so they outlive
 * the bar itself: the bar may well have gone by the time a picker answers.
 */
@Composable
fun SelectionMenu(host: SelectionMenuHost) {
    val replaceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) host.replaceSelectedImage(uri)
    }
    val saveImageLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) host.saveImage(uri)
    }
    val saveSelectionLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) host.saveSelectionImage(uri)
    }

    host.imageCrop?.let { session ->
        ImageCropOverlay(host, session)
        return
    }
    val rect = host.selectionMenuRect ?: return
    val density = LocalDensity.current
    val dp = density.density
    val words = rememberExplorerWords()
    var menu by remember { mutableStateOf<SelMenu?>(null) }
    var lastClosed by remember { mutableStateOf<SelMenu?>(null) }
    var closedAt by remember { mutableLongStateOf(0L) }
    // Bumped by actions that change what the bar reads without moving the selection (a flip, the header, a colour).
    var tick by remember { mutableIntStateOf(0) }
    // Read once per selection and after each change made here (Change style bumps [tick] on a recolour): an image or
    // a text box has no colour-and-width pair to restyle.
    val styles = remember(rect, tick) { host.selectionStyles() }
    val canStyle = styles.isNotEmpty()
    val inkDot = styles.firstOrNull()?.color
    val image = remember(rect, tick) { host.selectedImage }
    val imageEdited = remember(rect, tick) { image?.isEdited == true }
    val canRotate = remember(rect) { host.selectionCanRotate }
    val canFlip = remember(rect) { host.selectionCanFlip }
    val canPaste = host.hasClipboardItems
    val editable = remember(rect, tick) { host.selectionEditable }
    val stickyColor = remember(rect, tick) { host.selectionStickyColor }
    val isTable = remember(rect, tick) { host.selectionIsTable }
    val header = remember(rect, tick) { host.selectionTableHeader }
    val barIds = host.selectionBarIds
    val chosen = remember(barIds) { selectionBarChoice(barIds) }

    // A tap on a lit action closes its menu: the non-focusable menu is dismissed by that tap's touch-down first, so the
    // click that follows must not open it again (Part 7's grace rule, TableLogic.menuAfterTap).
    fun tap(m: SelMenu) {
        menu = menuAfterTap(menu, m, lastClosed, closedAt, SystemClock.uptimeMillis())
    }
    // An outside tap's dismissal: the one close the grace is for, since that tap may be on the lit action itself.
    fun close(m: SelMenu) {
        if (menu != m) return
        menu = null
        lastClosed = m
        closedAt = SystemClock.uptimeMillis()
    }
    // A row's action or a ×: closed by a tap inside the menu, so a tap on the action straight after opens it again.
    fun shut(m: SelMenu) {
        if (menu == m) menu = null
    }
    // A row acts once per open, then its menu closes (Part 3: ActOnce).
    val once = rememberActOnce(menu != null)
    fun act(m: SelMenu, block: () -> Unit) = once.run {
        shut(m)
        block()
    }
    // What each action does, the same from the bar or from a More row. Style and Colour open their own card instead.
    fun run(a: SelAction) {
        when (a) {
            SelAction.EDIT -> host.editSelection()
            SelAction.NOTE_COLOUR, SelAction.STYLE -> Unit
            SelAction.CROP -> host.beginImageCrop()
            SelAction.ADD_ROW -> host.tableInsertRow(below = true)
            SelAction.ADD_COLUMN -> host.tableInsertColumn(right = true)
            SelAction.HEADER -> {
                host.tableToggleHeader()
                tick++
            }
            SelAction.CUT -> host.cutSelection()
            SelAction.COPY -> {
                host.copySelection()
                host.dismissSelectionMenu()
            }
            SelAction.PASTE -> host.pasteItemsNearSelection()
            SelAction.DELETE -> host.deleteSelection()
            SelAction.DUPLICATE -> host.duplicateSelection()
            SelAction.SAVE_IMAGE -> host.prepareImageSave()?.let { saveImageLauncher.launch(it.mime to it.fileName) }
            SelAction.RESET_IMAGE -> {
                host.resetSelectedImage()
                tick++
            }
            SelAction.ROTATE, SelAction.ROTATE_LEFT -> {
                host.rotateSelection(clockwise = a == SelAction.ROTATE)
                tick++
            }
            SelAction.FLIP_H, SelAction.FLIP_V -> {
                host.flipSelection(horizontal = a == SelAction.FLIP_H)
                tick++
            }
            SelAction.REPLACE -> if (host.prepareImageReplace()) replaceLauncher.launch(arrayOf("image/*"))
            SelAction.SELECT_ALL -> host.selectAllObjects()
            SelAction.TO_FRONT -> host.bringToFront()
            SelAction.FORWARD -> host.bringForward()
            SelAction.BACKWARD -> host.sendBackward()
            SelAction.TO_BACK -> host.sendToBack()
            SelAction.COPY_AS_IMAGE -> host.copySelectionAsImage()
            SelAction.SHARE_AS_IMAGE -> host.shareSelectionAsImage()
            SelAction.SAVE_AS_IMAGE -> host.prepareSelectionImageSave()?.let { saveSelectionLauncher.launch("image/png" to it) }
            SelAction.LOCK -> host.lockSelection()
        }
    }
    // Closing Change style, however it closes, settles any preview its slider left open (one undo step).
    var styled by remember { mutableStateOf(false) }
    LaunchedEffect(menu == SelMenu.STYLE) {
        if (menu == SelMenu.STYLE) {
            styled = true
        } else if (styled) {
            styled = false
            host.restyleSelection(null, null)
        }
    }

    val pane = remember { PopoverAnchor() }
    val bar = remember { PopoverAnchor() }
    val more = remember { PopoverAnchor() }
    val buttons = remember { HashMap<SelAction, PopoverAnchor>() }
    fun anchorOf(a: SelAction): PopoverAnchor = buttons.getOrPut(a) { PopoverAnchor() }
    // What a floating toolbar covers of this pane (zero when it is docked: the pane is already clear of it).
    val coverPad = LocalToolbarCover.current
    val direction = LocalLayoutDirection.current
    val cover = with(density) {
        SelInsets(
            coverPad.calculateLeftPadding(direction).roundToPx(),
            coverPad.calculateTopPadding().roundToPx(),
            coverPad.calculateRightPadding(direction).roundToPx(),
            coverPad.calculateBottomPadding().roundToPx(),
        )
    }
    // Every menu and the Change style card: under the bar, beside the selection (both in window px when placed), kept
    // in this pane and off the toolbar; their height cap is measured the way their side is chosen.
    val menuPlacer = remember(rect, dp, cover) {
        object : PopoverPlacer {
            fun area(): IntRect? = pane.bounds.takeUnless { it == IntRect.Zero }?.let {
                IntRect(it.left + cover.left, it.top + cover.top, it.right - cover.right, it.bottom - cover.bottom)
            }

            override fun place(anchor: IntRect, edge: IntRect, window: IntSize, content: IntSize): PopoverPlacement {
                val p = pane.bounds
                val sel = IntRect(
                    p.left + rect.left.roundToInt(),
                    p.top + rect.top.roundToInt(),
                    p.left + rect.right.roundToInt(),
                    p.top + rect.bottom.roundToInt(),
                )
                return selectionMenuSpot(anchor, bar.bounds.top, bar.bounds.bottom, sel, content, window, dp, area())
            }

            override fun maxHeight(window: IntSize): Int = selectionMenuMaxHeight(bar.bounds.top, bar.bounds.bottom, window, dp, area())
        }
    }

    CompositionLocalProvider(LocalPopoverEdge provides null) {
        BoxWithConstraints(Modifier.fillMaxSize().popoverAnchor(pane)) {
            val facts = SelFacts(
                image = image != null,
                canStyle = canStyle,
                canRotate = canRotate,
                canFlip = canFlip,
                canPaste = canPaste,
                editable = editable,
                sticky = stickyColor != null,
                table = isTable,
                imageEdited = imageEdited,
            )
            val layout = selectionMenuLayout(facts, chosen, (maxWidth.value - 16f).toInt())
            val shown = layout.onBar
            val clearTop = with(density) { TOOLBAR_CLEAR.roundToPx() }
            Box(
                Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val sel = IntRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
                    val at = selectionBarSpot(sel, IntSize(p.width, p.height), IntSize(constraints.maxWidth, constraints.maxHeight), clearTop, dp, cover)
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(at.x, at.y) }
                },
            ) {
                InkPill(Modifier.popoverAnchor(bar), padding = SelBarPadding, gap = SEL_GAP_DP.dp) {
                    layout.bar.forEachIndexed { gi, group ->
                        if (gi > 0) InkPillDivider(height = SelBarDivider)
                        for (a in group) key(a) {
                            val at = Modifier.popoverAnchor(anchorOf(a))
                            val label = stringResource(selActionLabel(a, bar = true))
                            when (a) {
                                SelAction.STYLE -> InkPillAction(
                                    Fl.color,
                                    label,
                                    { tap(SelMenu.STYLE) },
                                    on = menu == SelMenu.STYLE,
                                    solid = menu == SelMenu.STYLE,
                                    modifier = at,
                                    width = a.widthDp.dp,
                                    dot = inkDot?.toComposeColor(),
                                    stateDescription = inkDot?.let { hueName(words, it) },
                                    look = SelBarLook,
                                )
                                SelAction.NOTE_COLOUR -> InkPillAction(
                                    Fl.color,
                                    label,
                                    { tap(SelMenu.NOTE_COLOUR) },
                                    on = menu == SelMenu.NOTE_COLOUR,
                                    solid = menu == SelMenu.NOTE_COLOUR,
                                    modifier = at,
                                    width = a.widthDp.dp,
                                    dot = stickyColor?.toComposeColor(),
                                    stateDescription = stickyColor?.let { hueName(words, it) },
                                    look = SelBarLook,
                                )
                                // A toggle: lit while the table has its header row.
                                SelAction.HEADER -> InkPillAction(selActionIcon(a, header), label, { run(a) }, on = header, modifier = at, width = a.widthDp.dp, look = SelBarLook)
                                // "Replace" on the bar, "Replace image" to TalkBack, as in More.
                                SelAction.REPLACE -> InkPillAction(
                                    selActionIcon(a),
                                    label,
                                    { run(a) },
                                    modifier = at,
                                    width = a.widthDp.dp,
                                    contentDescription = stringResource(R.string.replace_image),
                                    look = SelBarLook,
                                )
                                else -> InkPillAction(selActionIcon(a), label, { run(a) }, modifier = at, width = a.widthDp.dp, look = SelBarLook)
                            }
                        }
                    }
                    if (layout.bar.isNotEmpty()) InkPillDivider(height = SelBarDivider)
                    InkPillAction(
                        Fl.moreHorizontal,
                        stringResource(R.string.more),
                        { tap(SelMenu.MORE) },
                        on = menu == SelMenu.MORE,
                        solid = menu == SelMenu.MORE,
                        modifier = Modifier.popoverAnchor(more),
                        width = SEL_MORE_DP.dp,
                        look = SelBarLook,
                    )
                }
            }

            // A note's colour (SC 621): the six note colours, hanging from Colour, or from More.
            val colourAnchor = if (SelAction.NOTE_COLOUR in shown) anchorOf(SelAction.NOTE_COLOUR) else more
            InkPopover(expanded = menu == SelMenu.NOTE_COLOUR, onDismiss = { close(SelMenu.NOTE_COLOUR) }, anchor = colourAnchor, focusable = false, placer = menuPlacer) {
                Row(
                    Modifier
                        .inkSurface(inkRounded(16.dp), InkElevation.MENU)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (c in StickyColors.ALL) {
                        InkSwatch(c.toComposeColor(), selected = c == stickyColor, size = 32.dp, cell = 32.dp, contentDescription = hueName(words, c)) {
                            act(SelMenu.NOTE_COLOUR) { host.setStickyColor(c); tick++ }
                        }
                    }
                }
            }

            // More (selbar-fluent .menu): whatever the bar had no room for first, in the bar's own order (SC 229), then
            // every action not on the bar, in the fixed order, a rule between groups. Flip and Arrange are flat rows.
            InkPopover(expanded = menu == SelMenu.MORE, onDismiss = { close(SelMenu.MORE) }, anchor = more, focusable = false, placer = menuPlacer) {
                val m = SelMenu.MORE
                SelMenuSurface {
                    val sections = if (layout.overflow.isEmpty()) layout.more else listOf(layout.overflow) + layout.more
                    sections.forEachIndexed { si, group ->
                        if (si > 0) SelRule()
                        for (a in group) key(a) {
                            val label = stringResource(selActionLabel(a, bar = false))
                            when (a) {
                                // Change style and the note colours take More's place, hanging from More.
                                SelAction.STYLE -> SelRow(Fl.color, label) { menu = SelMenu.STYLE }
                                SelAction.NOTE_COLOUR -> SelRow(Fl.color, label) { menu = SelMenu.NOTE_COLOUR }
                                else -> SelRow(selActionIcon(a, header), label) { act(m) { run(a) } }
                            }
                        }
                    }
                }
            }

            // Change style (SC Frame 2b): beside the selection like the menus, hanging from Style, or from More.
            val styleAnchor = if (SelAction.STYLE in shown) anchorOf(SelAction.STYLE) else more
            val pickerTop = remember(density) { { pane.bounds.top + clearTop - with(density) { 2.dp.roundToPx() } } }
            InkPopover(expanded = menu == SelMenu.STYLE, onDismiss = { close(SelMenu.STYLE) }, anchor = styleAnchor, focusable = false, placer = menuPlacer) {
                SelectionStyleCard(host, bar, pickerTop, onStyled = { tick++ }) { shut(SelMenu.STYLE) }
            }
        }
    }
}

/** .menu (selbar-fluent): raised, r16, at least 248 wide, 6 dp inside; scrolls when tall. */
@Composable
private fun SelMenuSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(min = 248.dp)
            .width(IntrinsicSize.Max)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        content = content,
    )
}

/** .mrow (selbar-fluent): 42 dp, r10, a 20 dp icon 12 dp before the 14.5 label, 12 dp sides. */
@Composable
internal fun SelRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(inkRounded(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(20.dp))
        Text(label, style = SelRowLabel, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** .mrule (selbar-fluent): a 1 dp --line rule, 4 dp above and below, 8 dp in from the rows' edges. */
@Composable
internal fun SelRule() {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(LocalInk.current.line))
}

/**
 * Change style (SC Frame 2b): every toolbar ink on 8 columns, then + for any other colour (the shared picker, beside
 * the bar and the selection, never over either), the thickness readout and slider, and the hint. Colour and
 * thickness apply live; a whole slider drag is one undo step. It opens on the selection's shared colour, else the
 * first item's, and on the first item's width. [onStyled] follows every recolour, so the bar's Style dot does.
 */
@Composable
private fun SelectionStyleCard(host: SelectionMenuHost, bar: PopoverAnchor, pickerTop: () -> Int, onStyled: () -> Unit, onClose: () -> Unit) {
    val ink = LocalInk.current
    val opened = remember { host.selectionStyles() }
    val first = opened.firstOrNull() ?: return
    val shared = first.color.takeIf { c -> opened.all { it.color == c } }
    var color by remember { mutableStateOf(shared ?: first.color) }
    var width by remember { mutableStateOf(first.width.toFloat()) }
    var picking by remember { mutableStateOf(false) }
    val inks = host.hostToolbarColors
    val place = remember(bar) { PickerPlace.BesideBar(bar, pickerTop) }
    InkCard(stringResource(R.string.title_change_style), onClose = onClose) {
        InkCardSection(first = true) {
            val dots = ArrayList<@Composable () -> Unit>(inks.size + 1)
            inks.forEachIndexed { i, c ->
                dots.add {
                    InkSwatch(c.toComposeColor(), selected = color == c, size = 28.dp, cell = 28.dp, contentDescription = inkColourLabel(c, i, inks.size)) {
                        picking = false
                        color = c
                        host.restyleSelection(c, null)
                        onStyled()
                    }
                }
            }
            dots.add {
                Box {
                    InkAddSwatch(colour = null, lit = picking || color !in inks, contentDescription = stringResource(R.string.material_custom_colour)) { picking = true }
                    if (picking) {
                        ColorPickerPopup(
                            initial = color,
                            recents = host.hostRecentColors,
                            onDismiss = { picking = false },
                            onPick = {
                                color = it
                                host.restyleSelection(it, null)
                                onStyled()
                            },
                            place = place,
                        )
                    }
                }
            }
            ToolColourGrid(dots)
        }
        InkCardSection {
            InkReadoutRow(stringResource(R.string.pen_thickness), widthLabelMm(width.toDouble()))
            // The drag previews live and commits on release, so it is one undo step, not fifty.
            InkSlider(
                width,
                DrawStyle.MIN_WIDTH.toFloat()..DrawStyle.MAX_WIDTH.toFloat(),
                Modifier.padding(top = 6.dp),
                onChangeFinished = { host.restyleSelection(null, null) },
            ) { w ->
                width = w
                host.restyleSelection(null, w.toDouble(), preview = true)
            }
        }
        Text(
            stringResource(R.string.change_style_hint),
            style = InkType.hint,
            color = ink.text2,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        )
    }
}
