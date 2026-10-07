package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.text.FlowTable
import com.xnotes.core.text.TableBorders
import com.xnotes.core.text.TableDefaults
import com.xnotes.core.text.TableFrag
import com.xnotes.core.text.TableSnapshot
import com.xnotes.core.text.TableStyle
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkOptionCard
import com.xnotes.ui.kit.InkOptionCardRow
import com.xnotes.ui.kit.InkOptionCardSize
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillMetrics
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStepperMetrics
import com.xnotes.ui.kit.InkStepperRow
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.InkValueRow
import com.xnotes.ui.kit.POPOVER_EXIT_MS
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.Palette
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Table chrome over the page: the size picker, the restyle card and the edit-mode handles. */
@Composable
fun TableChrome(editor: Editor) {
    TablePickerHost(editor)
    TableStyleHost(editor)
    editor.editingTable?.let { TableEditOverlay(editor, it) }
}

/** The size picker from a long press: centred under the press (half the 340 dp card), 8 dp below it (above when there is no room). */
private val PressPickerSpec = PopoverSpecDp(lead = 170.dp, gap = 8.dp, margin = 8.dp)

/**
 * The table size picker the Insert card or the long-press menu asked for ([Editor.tablePickerRequest]): from Insert it
 * hangs off the paperclip ([Editor.insertAnchor], PopoverSpecs.Insert, TI 832); from a long press it rises from the
 * press point (TI 276). The last request stays composed while its card animates out; each new opening is a fresh
 * card, 3 × 3 again.
 */
@Composable
private fun TablePickerHost(editor: Editor) {
    val req = editor.tablePickerRequest
    val last = remember { arrayOfNulls<TablePickerRequest>(1) }
    val wasOpen = remember { booleanArrayOf(false) }
    val generation = remember { intArrayOf(0) }
    if (req != null && (req !== last[0] || !wasOpen[0])) {
        last[0] = req
        generation[0]++
    }
    wasOpen[0] = req != null
    val shown = last[0] ?: return
    key(generation[0]) {
        val dismiss = { editor.closeTablePicker() }
        val pick = { rows: Int, cols: Int ->
            editor.closeTablePicker()
            editor.insertTableAt(shown.at, rows, cols)
        }
        val at = shown.at
        if (at == null) {
            MediaPopover(expanded = req === shown, onDismiss = dismiss, anchor = editor.insertAnchor, spec = PopoverSpecs.Insert, prefer = PopoverSide.BELOW) {
                TableSizeCard(pick, dismiss)
            }
        } else {
            val anchor = remember { PopoverAnchor() }
            val p = remember { editor.state.contentToViewport(at) }
            Box(Modifier.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.size(1.dp).popoverAnchor(anchor)) {
                MediaPopover(expanded = req === shown, onDismiss = dismiss, anchor = anchor, spec = PressPickerSpec, prefer = PopoverSide.BELOW) {
                    TableSizeCard(pick, dismiss)
                }
            }
        }
    }
}

// --- a table in typed text: the bar ---

/**
 * The table's action bar (TI 1039), on the shared pill: Edit table, Fit columns, Style, Delete. It opens the moment
 * a long press holds the table itself (a rule, padding, empty cell space), 10 dp above the table, or below it when the
 * toolbar is in the way (posOver). Like the text bar it never takes focus, and the next canvas touch retires it. While
 * the style card is up it stays, with Style lit; the focusable card takes the next tap outside.
 */
@Composable
fun FlowTableMenu(editor: Editor) {
    val table = editor.tableMenu ?: editor.tableStyling ?: return
    if (editor.editingTable != null) return
    val styling = editor.tableStyling === table
    editor.tableChromeTick
    val (pi, frag) = editor.tableFrags(table).firstOrNull() ?: return
    val t = editor.pageRectToViewport(pi, frag.rect) ?: return
    val target = IntRect(t.left.roundToInt(), t.top.roundToInt(), t.right.roundToInt(), t.bottom.roundToInt())
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val at = barOver(
                target = target,
                bar = IntSize(p.width, p.height),
                pane = IntSize(constraints.maxWidth, constraints.maxHeight),
                clearTop = (editor.state.insetTop + 12.dp.toPx()).roundToInt(),
                gap = 10.dp.roundToPx(),
                margin = 8.dp.roundToPx(),
            )
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(at) }
        },
    ) {
        InkPill {
            InkPillAction(Ph.pencilSimple, stringResource(R.string.edit_table), { editor.startTableEdit(table) }, width = InkPillMetrics.ActionExtraWide)
            InkPillAction(Ph.arrowsOutLineHorizontal, stringResource(R.string.fit_columns), { editor.tableAutoFit(table) }, width = InkPillMetrics.ActionExtraWide)
            InkPillAction(Ph.palette, stringResource(R.string.sel_style), { if (!styling) editor.openTableStyle(table) }, on = styling)
            InkPillAction(
                Ph.trash,
                stringResource(R.string.delete),
                { editor.tableDelete(table) },
                contentDescription = stringResource(R.string.delete_table),
            )
        }
    }
}

// --- the style card and the insert sheet ---

/** The style card (.ti-ts). */
private val STYLE_CARD_W = 440.dp

/**
 * The Style button's centre from the bar's centre (TI 1039): the pill is 6 + 76 + 76 + 58 + 58 + 6 = 280 wide and
 * Style's centre is 6 + 76 + 76 + 29 = 187 from its start, so 47 past the middle. The card grows from there.
 */
private val STYLE_BUTTON_FROM_CENTRE = 47.dp

/**
 * The insert-table sheet ([table] null, from the format bar's More) or the restyle card (from a table's bar). The
 * signature stays as TextFormatBar calls it.
 */
@Composable
fun TableDialog(editor: Editor, table: FlowTable?, onDismiss: () -> Unit) {
    if (table == null) TableInsertSheet(editor, onDismiss) else TableStyleCard(editor, table, expanded = true, onDismiss = onDismiss)
}

/**
 * The restyle card for [Editor.tableStyling], kept composed while it animates out; each opening is a fresh card. Once
 * its exit has played ([POPOVER_EXIT_MS]) the card leaves composition, so it stops following [Editor.tableChromeTick].
 */
@Composable
private fun TableStyleHost(editor: Editor) {
    val styling = editor.tableStyling
    val last = remember { arrayOfNulls<FlowTable>(1) }
    val wasOpen = remember { booleanArrayOf(false) }
    val generation = remember { intArrayOf(0) }
    if (styling != null && (styling !== last[0] || !wasOpen[0])) {
        last[0] = styling
        generation[0]++
    }
    wasOpen[0] = styling != null
    // Re-opening within the wait restarts this and keeps the card.
    var exited by remember { mutableStateOf(false) }
    LaunchedEffect(styling == null) {
        exited = false
        if (styling == null) {
            delay(POPOVER_EXIT_MS)
            exited = true
        }
    }
    if (styling == null && exited) last[0] = null
    val table = last[0] ?: return
    key(generation[0]) {
        TableStyleCard(editor, table, expanded = styling === table) { editor.closeTableStyle() }
    }
}

/**
 * What the card and the sheet edit. Values start from the saved new-table defaults (restyling: from the table) and
 * stay local, as in the View menu: "Default for new tables" saves them and Reset returns to factory values.
 * Restyling shows live and lands as one undo step when the card closes. [finish] runs once.
 */
@Stable
private class TableLook(private val editor: Editor, val table: FlowTable?) {
    val saved: TableDefaults get() = editor.newTableDefaults
    private val start = editor.newTableDefaults
    private val before: TableSnapshot? = table?.snapshot()
    var rows by mutableIntStateOf(start.rows)
        private set
    var cols by mutableIntStateOf(start.cols)
        private set
    var style by mutableStateOf(table?.style ?: start.style)
        private set
    var showDefaultRow by mutableStateOf(false)
        private set
    private var finished = false

    init {
        showDefaultRow = defaultRowShown(false, current(), saved)
    }

    fun current(): TableDefaults = if (table == null) TableDefaults(rows, cols, style) else saved.copy(style = style)

    fun update(r: Int = rows, c: Int = cols, s: TableStyle = style) {
        rows = r.coerceIn(1, TableDefaults.MAX_ROWS)
        cols = c.coerceIn(1, TableDefaults.MAX_COLS)
        style = s.clamped()
        if (table != null) editor.tablePreview(table, table.snapshot().copy(style = style))
        showDefaultRow = defaultRowShown(showDefaultRow, current(), saved)
    }

    fun reset() {
        val factory = TableDefaults()
        update(factory.rows, factory.cols, factory.style)
    }

    val defaultOn: Boolean get() = defaultRowOn(current(), saved)

    fun setDefault(on: Boolean) {
        editor.saveNewTableDefaults(if (on) current() else TableDefaults())
    }

    fun finish(insert: Boolean, onDismiss: () -> Unit) {
        if (finished) return
        finished = true
        if (table != null && before != null) editor.tableCommitPreview(table, before)
        if (insert) editor.flowInsertTable(rows, cols, style) else if (table == null) editor.flowReshowIme()
        onDismiss()
    }
}

/**
 * The table style card (TI Frame 4, .ti-ts): 440 dp, beside the table rather than over it (styleCardBeside), 10 dp
 * under the toolbar, growing from the Style button. The real table restyles live next to it. Any close (outside tap,
 * Back, ×, Done) lands the change as one undo step.
 */
@Composable
private fun TableStyleCard(editor: Editor, table: FlowTable, expanded: Boolean, onDismiss: () -> Unit) {
    val look = remember(table) { TableLook(editor, table) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pane = IntSize(constraints.maxWidth, constraints.maxHeight)
        editor.tableChromeTick
        val slice = editor.tableFrags(table).firstOrNull()?.let { (pi, f) -> editor.pageRectToViewport(pi, f.rect) }
        val target = slice?.let { IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
            ?: IntRect(0, 0, pane.width, 0)
        val spot = with(density) {
            styleCardBeside(
                table = target,
                cardWidth = STYLE_CARD_W.roundToPx(),
                pane = pane,
                top = (editor.state.insetTop + 10.dp.toPx()).roundToInt(),
                gap = 16.dp.roundToPx(),
                endInset = 44.dp.roundToPx(),
                margin = 8.dp.roundToPx(),
                originX = (target.left + target.right) / 2 + STYLE_BUTTON_FROM_CENTRE.roundToPx(),
            )
        }
        AnchorAt(spot.x, spot.y, spot.originX) { anchor ->
            MediaPopover(
                expanded = expanded,
                onDismiss = { look.finish(insert = false, onDismiss) },
                anchor = anchor,
                spec = PlacedSpec,
                prefer = PopoverSide.BELOW,
            ) {
                StyleCardBody(editor, look) { look.finish(insert = false, onDismiss) }
            }
        }
    }
}

@Composable
private fun StyleCardBody(editor: Editor, look: TableLook, onDone: () -> Unit) {
    val ink = LocalInk.current
    InkCard(title = null, onClose = null, width = STYLE_CARD_W) {
        CardHeader(stringResource(R.string.table_style), onReset = look::reset, onClose = onDone)
        Section(first = true, firstTop = 4.dp) { TablePreview(4, 3, look.style, editor.tableRuleColor(), 76.dp) }
        Section { PaddingAndLines(look) }
        Section { BordersAndLineColour(editor, look) }
        Section { HeaderBandedAndTint(editor, look) }
        Row(
            Modifier
                .fillMaxWidth()
                .topHairline(ink.line2)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DefaultRow(look)
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.done), onDone)
        }
    }
}

/**
 * The insert-table sheet (TI Frame 4, .ti-tsw): 780 dp, Reset in the header. On the left, the preview at the chosen
 * size, then Rows and Columns; on the right, the look. The footer has the default row, then Cancel and Insert.
 */
@Composable
private fun TableInsertSheet(editor: Editor, onDismiss: () -> Unit) {
    val look = remember { TableLook(editor, null) }
    val ink = LocalInk.current
    InkSheet(
        title = stringResource(R.string.insert_table),
        onDismiss = { look.finish(insert = false, onDismiss) },
        width = 780.dp,
        headerActions = { InkGhostButton(stringResource(R.string.reset), look::reset) },
        footer = {
            DefaultRow(look)
            Spacer(Modifier.weight(1f))
            InkGhostButton(stringResource(R.string.cancel), { look.finish(insert = false, onDismiss) })
            InkStrongButton(stringResource(R.string.insert), { look.finish(insert = true, onDismiss) })
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // The column rule, drawn by the row so it runs its full height without intrinsics.
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawRect(ink.line2, Offset(300.dp.toPx() - w, 0f), Size(w, size.height))
                },
        ) {
            Column(Modifier.width(300.dp).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp)) {
                TablePreview(look.rows, look.cols, look.style, editor.tableRuleColor(), 176.dp)
                Text(stringResource(R.string.table_size), style = InkType.label, color = ink.text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                InkStepperRow(
                    label = stringResource(R.string.table_rows),
                    value = "${look.rows}",
                    onMinus = { look.update(r = look.rows - 1) },
                    onPlus = { look.update(r = look.rows + 1) },
                    canMinus = look.rows > 1,
                    canPlus = look.rows < TableDefaults.MAX_ROWS,
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                InkStepperRow(
                    label = stringResource(R.string.table_columns),
                    value = "${look.cols}",
                    onMinus = { look.update(c = look.cols - 1) },
                    onPlus = { look.update(c = look.cols + 1) },
                    canMinus = look.cols > 1,
                    canPlus = look.cols < TableDefaults.MAX_COLS,
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Section(first = true, firstTop = 16.dp) { PaddingAndLines(look) }
                Section { BordersAndLineColour(editor, look) }
                Section { HeaderBandedAndTint(editor, look) }
            }
        }
    }
}

/** The card header (.pp-h with gap 2): title, then ghost Reset (36 high) and ×. */
@Composable
private fun CardHeader(title: String, onReset: () -> Unit, onClose: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 22.dp, top = 10.dp, end = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = InkType.sheetTitle,
            color = ink.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        InkGhostButton(stringResource(R.string.reset), onReset, Modifier.height(36.dp))
        InkIconButton(Ph.x, stringResource(R.string.kit_close), onClose, modifier = Modifier.wrapContentSize(unbounded = true), iconSize = 18.dp)
    }
}

/** A section (.ti-sec): padding 12/20/14 over a line2 rule; the first has no rule and [firstTop] on top. */
@Composable
private fun Section(first: Boolean = false, firstTop: Dp = 4.dp, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.topHairline(ink.line2))
            .padding(start = 20.dp, end = 20.dp, top = if (first) firstTop else 12.dp, bottom = 14.dp),
        content = content,
    )
}

/**
 * Padding and Lines side by side (.ti-2), on the compact stepper; each disables at its limit (TI 1009). Each label
 * stays on one line: in the 440 dp card the Padding column runs on into the gap ([twoColumns]), as in the mockup.
 */
@Composable
private fun PaddingAndLines(look: TableLook) {
    val s = look.style
    TwoColumnRow {
        InkStepperRow(
            label = stringResource(R.string.table_padding),
            value = stringResource(R.string.table_pt_value, "%.0f".format(s.paddingPt)),
            onMinus = { look.update(s = s.copy(paddingPt = s.paddingPt - 1.0)) },
            onPlus = { look.update(s = s.copy(paddingPt = s.paddingPt + 1.0)) },
            canMinus = s.paddingPt > 0.0,
            canPlus = s.paddingPt < TableStyle.MAX_PADDING_PT,
            metrics = InkStepperMetrics.Compact,
            labelMaxLines = 1,
        )
        InkStepperRow(
            label = stringResource(R.string.table_lines),
            value = stringResource(R.string.table_pt_value, "%.2f".format(s.lineWidthPt)),
            onMinus = { look.update(s = s.copy(lineWidthPt = s.lineWidthPt - LINE_STEP)) },
            onPlus = { look.update(s = s.copy(lineWidthPt = s.lineWidthPt + LINE_STEP)) },
            canMinus = s.lineWidthPt > TableStyle.MIN_LINE_PT,
            canPlus = s.lineWidthPt < TableStyle.MAX_LINE_PT,
            metrics = InkStepperMetrics.Compact,
            labelMaxLines = 1,
        )
    }
}

/** The .ti-2 grid's column gap, and the least the first column leaves of it when it runs on ([twoColumns]). */
private val TI2_GAP = 24.dp
private val TI2_LEAST_GAP = 12.dp

/** Two cells in .ti-2's columns ([twoColumns]), centred on each other vertically. Give it exactly two children. */
@Composable
private fun TwoColumnRow(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val first = measurables[0]
        val cols = twoColumns(width, TI2_GAP.roundToPx(), TI2_LEAST_GAP.roundToPx(), first.maxIntrinsicWidth(constraints.maxHeight))
        val a = first.measure(Constraints.fixedWidth(cols.firstWidth))
        val b = measurables[1].measure(Constraints.fixedWidth(cols.secondWidth))
        val h = maxOf(a.height, b.height)
        layout(width, h) {
            a.placeRelative(0, (h - a.height) / 2)
            b.placeRelative(cols.secondStart, (h - b.height) / 2)
        }
    }
}

/** Borders as drawn option cards (.ti-opts, TI 168-174), then Line colour (Auto or custom). */
@Composable
private fun BordersAndLineColour(editor: Editor, look: TableLook) {
    val s = look.style
    InkOptionCardRow {
        for (b in BORDER_CHOICES) {
            InkOptionCard(
                selected = s.borders == b,
                label = stringResource(b.labelRes),
                onClick = { look.update(s = s.copy(borders = b)) },
                modifier = Modifier.weight(1f),
                cardSize = InkOptionCardSize.Compact,
            ) { tint -> borderArt(b, tint) }
        }
    }
    ColourRow(
        label = stringResource(R.string.table_line_colour),
        value = s.lineColor,
        auto = editor.tableRuleColor(),
        recents = editor.recentColors,
        modifier = Modifier.padding(top = 10.dp),
    ) { look.update(s = s.copy(lineColor = it)) }
}

/** The Header row and Banded rows switches (.ti-2), then Tint while either is on (TI 1013). */
@Composable
private fun HeaderBandedAndTint(editor: Editor, look: TableLook) {
    val s = look.style
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SwitchCell(stringResource(R.string.table_header_row), s.headerRow, Modifier.weight(1f)) { look.update(s = s.copy(headerRow = it)) }
        SwitchCell(stringResource(R.string.table_banded_rows), s.banded, Modifier.weight(1f)) { look.update(s = s.copy(banded = it)) }
    }
    if (s.headerRow || s.banded) {
        ColourRow(
            label = stringResource(R.string.table_tint),
            value = s.tint,
            auto = LocalPalette.current.pageAccent,
            recents = editor.recentColors,
            modifier = Modifier.padding(top = 10.dp),
        ) { look.update(s = s.copy(tint = it)) }
    }
}

/** A labelled switch whose whole row is the toggle. */
@Composable
private fun SwitchCell(label: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    InkValueRow(label, modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange), minHeight = 44.dp) {
        InkSwitch(checked, null)
    }
}

/** An Auto chip (null = follow the page) and the custom dot (.ti-cc). */
@Composable
private fun ColourRow(
    label: String,
    value: Rgba?,
    auto: Rgba,
    recents: List<Rgba>,
    modifier: Modifier = Modifier,
    onChange: (Rgba?) -> Unit,
) {
    InkValueRow(label, modifier, minHeight = 44.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkChip(stringResource(R.string.auto), selected = value == null, onClick = { onChange(null) }, modifier = Modifier.height(32.dp))
            Spacer(Modifier.width(6.dp))
            CustomColourDot(colour = value, chosen = value != null, initial = value ?: auto, recents = recents) { onChange(it) }
        }
    }
}

/** "Default for new tables" (.ti-def): the whole row toggles; shown by the session rule (TableLogic). */
@Composable
private fun DefaultRow(look: TableLook) {
    if (!defaultRowVisible(look.showDefaultRow, look.current())) return
    val on = look.defaultOn
    Row(
        Modifier.toggleable(on, role = Role.Switch, onValueChange = look::setDefault),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InkSwitch(on, null)
        Text(stringResource(R.string.default_for_new_tables), style = InkType.chip, color = LocalInk.current.text)
    }
}

/**
 * A small live sketch of the table's look (.ti-prev): fills and rules, on the paper, in the colours the page gives
 * them. The default tint is the page accent and the default rule is picked for the paper, so on the chrome's own
 * surface a dark theme's header would vanish. r12 with a .08 ring.
 */
@Composable
private fun TablePreview(rows: Int, cols: Int, style: TableStyle, autoRule: Rgba, height: Dp) {
    val palette = LocalPalette.current
    val tint = style.tint ?: palette.pageAccent
    val line = (style.lineColor ?: autoRule).toComposeColor()
    val header = tint.withAlpha(com.xnotes.core.text.FlowLayout.HEADER_ALPHA).toComposeColor()
    val band = tint.withAlpha(com.xnotes.core.text.FlowLayout.BAND_ALPHA).toComposeColor()
    val shape = inkRounded(12.dp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(palette.paper.toComposeColor())
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape),
    ) {
        val r = rows.coerceAtMost(PREVIEW_MAX)
        val c = cols.coerceAtMost(PREVIEW_MAX)
        val margin = 10.dp.toPx()
        val w = size.width - 2 * margin
        val h = size.height - 2 * margin
        val cw = w / c
        val rh = h / r
        val lw = (style.lineWidthPt.toFloat() * 0.8f).dp.toPx().coerceAtLeast(1f)
        for (i in 0 until r) {
            val body = i - if (style.headerRow) 1 else 0
            val fill = when {
                style.headerRow && i == 0 -> header
                style.banded && body >= 0 && body % 2 == 1 -> band
                else -> null
            } ?: continue
            drawRect(fill, Offset(margin, margin + i * rh), Size(w, rh))
        }
        fun hLine(y: Float) = drawLine(line, Offset(margin, y), Offset(margin + w, y), lw)
        fun vLine(x: Float) = drawLine(line, Offset(x, margin), Offset(x, margin + h), lw)
        when (style.borders) {
            TableBorders.NONE -> {}
            TableBorders.OUTER -> drawRect(line, Offset(margin, margin), Size(w, h), style = Stroke(lw))
            TableBorders.HORIZONTAL -> for (i in 0..r) hLine(margin + i * rh)
            TableBorders.ALL -> {
                for (i in 0..r) hLine(margin + i * rh)
                for (j in 0..c) vLine(margin + j * cw)
            }
        }
    }
}

/**
 * A border choice's drawing (TI 974-980), on a 44 × 28 grid scaled into the card's art area: stroke 1.6, round caps;
 * the lines a choice leaves out are ghosts (30%, dashed 2 / 2.6). [InkOptionCard] has already scaled the scope to dp
 * while `size` still reports pixels, so the art box is measured in dp here, not scaled a second time.
 */
private fun DrawScope.borderArt(b: TableBorders, tint: Color) {
    val w = size.width / density
    val h = size.height / density
    val k = min(w / 44f, h / 28f)
    translate((w - 44f * k) / 2f, (h - 28f * k) / 2f) {
        scale(k, k, pivot = Offset.Zero) {
            val ghost = tint.copy(alpha = tint.alpha * 0.3f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 2.6f))
            fun line(x0: Float, y0: Float, x1: Float, y1: Float, faint: Boolean) =
                drawLine(if (faint) ghost else tint, Offset(x0, y0), Offset(x1, y1), 1.6f, StrokeCap.Round, if (faint) dash else null)
            fun outer(faint: Boolean) = drawRoundRect(
                if (faint) ghost else tint,
                Offset(4f, 3.5f),
                Size(36f, 21f),
                CornerRadius(1.5f),
                style = Stroke(1.6f, cap = StrokeCap.Round, pathEffect = if (faint) dash else null),
            )
            fun across(faint: Boolean) {
                line(4f, 10.5f, 40f, 10.5f, faint)
                line(4f, 17.5f, 40f, 17.5f, faint)
            }
            fun down(faint: Boolean) {
                line(16f, 3.5f, 16f, 24.5f, faint)
                line(28f, 3.5f, 28f, 24.5f, faint)
            }
            when (b) {
                TableBorders.ALL -> { outer(false); across(false); down(false) }
                TableBorders.OUTER -> { outer(false); across(true); down(true) }
                TableBorders.HORIZONTAL -> {
                    line(4f, 3.5f, 40f, 3.5f, false)
                    line(4f, 24.5f, 40f, 24.5f, false)
                    across(false)
                    line(4f, 3.5f, 4f, 24.5f, true)
                    line(40f, 3.5f, 40f, 24.5f, true)
                    down(true)
                }
                TableBorders.NONE -> { outer(true); across(true); down(true) }
            }
        }
    }
}

// --- edit mode ---

private enum class Axis { ROW, COL }

/** A row or column being dragged to a new place: its [from] index, the finger in viewport px and where it began. */
private data class Reorder(val axis: Axis, val frag: Int, val from: Int, val pos: Offset, val start: Offset)

/**
 * A table slice with its viewport rect; maps page-local coordinates onto the screen. Its chrome is drawn in
 * [accent], the page accent as it reads on the slice's page, with [glyph] ([Palette.onFill]) for what sits on an
 * accent fill, and for the grips' face.
 */
private class Placed(val frag: TableFrag, val vp: Rect, val accent: Color, val glyph: Color) {
    private val sx = vp.w / (frag.right - frag.left)
    private val sy = vp.h / (frag.bottom - frag.top)

    fun x(cx: Double): Float = (vp.left + (cx - frag.left) * sx).toFloat()
    fun y(cy: Double): Float = (vp.top + (cy - frag.top) * sy).toFloat()

    /** Viewport px per content px, vertically. */
    val scaleY: Double get() = sy
}

/**
 * Structure-edit chrome over [table] (Edit table, TI Frame 5): an outline in the page's ink, + discs at every row and
 * column boundary, white grips that drag a row or column to a new place (the lifted grip follows the finger) with an
 * × that deletes it, handles under the bottom edge for the column widths and the table's own width, and on the right
 * edge for row heights. Width and height drags preview live and land as one undo step. A tap anywhere else on the
 * canvas ends the mode (the flow controller's gate).
 */
@Composable
private fun TableEditOverlay(editor: Editor, table: FlowTable) {
    editor.tableChromeTick
    val density = LocalDensity.current
    // The table is on the page, so its chrome is too: each slice in its own page's accent.
    val placed = editor.tableFrags(table).mapNotNull { (pi, f) ->
        editor.pageRectToViewport(pi, f.rect)?.let {
            val accent = editor.pageAccentAt(pi)
            Placed(f, it, accent.toComposeColor(), Palette.onFill(accent).toComposeColor())
        }
    }
    if (placed.isEmpty()) return
    val current by rememberUpdatedState(placed)
    var reorder by remember { mutableStateOf<Reorder?>(null) }
    fun px(d: Dp) = with(density) { d.toPx() }

    fun finishReorder() {
        val r = reorder ?: return
        reorder = null
        val p = current.getOrNull(r.frag) ?: return
        if (r.axis == Axis.COL) {
            val xs = p.frag.colXs.map { p.x(it) }
            val b = xs.indices.minByOrNull { abs(xs[it] - r.pos.x) } ?: return
            val to = if (b > r.from) b - 1 else b
            if (to != r.from) editor.tableMoveCol(table, r.from, to)
        } else {
            val (rows, ys) = rowBoundaries(p)
            val k = ys.indices.minByOrNull { abs(ys[it] - r.pos.y) } ?: return
            val b = rows[k]
            val to = if (b > r.from) b - 1 else b
            if (to != r.from) editor.tableMoveRow(table, r.from, to)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (p in placed) {
                drawRect(
                    p.accent,
                    Offset(p.vp.left.toFloat(), p.vp.top.toFloat()),
                    Size(p.vp.w.toFloat(), p.vp.h.toFloat()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            val r = reorder ?: return@Canvas
            val p = placed.getOrNull(r.frag) ?: return@Canvas
            val bar = 3.dp.toPx()
            // The bar overhangs 6 dp at each end (.ti-insb); its round caps take half the bar's width of that.
            val over = 6.dp.toPx() - bar / 2f
            if (r.axis == Axis.COL) {
                val xs = p.frag.colXs.map { p.x(it) }
                val b = xs.indices.minByOrNull { abs(xs[it] - r.pos.x) } ?: return@Canvas
                drawRect(p.accent.copy(alpha = 0.08f), Offset(xs[r.from], p.vp.top.toFloat()), Size(xs[r.from + 1] - xs[r.from], p.vp.h.toFloat()))
                drawLine(p.accent, Offset(xs[b], p.vp.top.toFloat() - over), Offset(xs[b], p.vp.bottom.toFloat() + over), bar, StrokeCap.Round)
            } else {
                val ys = rowBoundaries(p).second
                val k = ys.indices.minByOrNull { abs(ys[it] - r.pos.y) } ?: return@Canvas
                p.frag.rows.firstOrNull { it.row == r.from }?.let { row ->
                    drawRect(p.accent.copy(alpha = 0.08f), Offset(p.vp.left.toFloat(), p.y(row.top)), Size(p.vp.w.toFloat(), p.y(row.bottom) - p.y(row.top)))
                }
                drawLine(p.accent, Offset(p.vp.left.toFloat() - over, ys[k]), Offset(p.vp.right.toFloat() + over, ys[k]), bar, StrokeCap.Round)
            }
        }

        for ((fi, p) in placed.withIndex()) {
            val f = p.frag
            val cols = f.colXs.size - 1
            val gripY = (p.vp.top - px(18.dp)).toFloat()
            val plusY = (p.vp.top - px(44.dp)).toFloat()
            val gripX = (p.vp.left - px(30.dp)).toFloat()
            val plusX = (p.vp.left - px(68.dp)).toFloat()

            // Columns: + at every boundary, a grip (drag to move) and an x over each column.
            for (b in 0..cols) {
                key("cp", fi, b) {
                    PlusButton(p.x(f.colXs[b]), plusY, p.accent, p.glyph) { editor.tableInsertCol(table, b) }
                }
            }
            for (c in 0 until cols) {
                key("cg", fi, c) {
                    val center by rememberUpdatedState(Offset((p.x(f.colXs[c]) + p.x(f.colXs[c + 1])) / 2f, gripY))
                    Grip(
                        center.x, center.y, vertical = false, p.accent, p.glyph,
                        lifted = { reorder?.let { it.axis == Axis.COL && it.frag == fi && it.from == c } == true },
                        onDelete = { editor.tableDeleteCol(table, c) },
                        onDragStart = { reorder = Reorder(Axis.COL, fi, c, center, center) },
                        onDrag = { d -> reorder = reorder?.let { it.copy(pos = it.pos + d) } },
                        onDragEnd = { finishReorder() },
                        onDragCancel = { reorder = null },
                    )
                }
            }

            // Rows: + where a row starts or ends on this page, a grip and an x beside each row slice.
            val (rowsAt, ysAt) = rowBoundaries(p)
            for (k in rowsAt.indices) {
                key("rp", fi, k) {
                    val at = rowsAt[k]
                    PlusButton(plusX, ysAt[k], p.accent, p.glyph) { editor.tableInsertRow(table, at) }
                }
            }
            for (row in f.rows) {
                key("rg", fi, row.row) {
                    val center by rememberUpdatedState(Offset(gripX, (p.y(row.top) + p.y(row.bottom)) / 2f))
                    Grip(
                        center.x, center.y, vertical = true, p.accent, p.glyph,
                        lifted = { reorder?.let { it.axis == Axis.ROW && it.frag == fi && it.from == row.row } == true },
                        onDelete = { editor.tableDeleteRow(table, row.row) },
                        onDragStart = { reorder = Reorder(Axis.ROW, fi, row.row, center, center) },
                        onDrag = { d -> reorder = reorder?.let { it.copy(pos = it.pos + d) } },
                        onDragEnd = { finishReorder() },
                        onDragCancel = { reorder = null },
                    )
                }
            }

            // Column widths: a handle under every inner boundary; the neighbours trade width.
            val widthY = (p.vp.bottom + px(16.dp)).toFloat()
            for (c in 1 until cols) {
                key("cw", fi, c) {
                    val tableW by rememberUpdatedState(p.vp.w.toFloat())
                    DragHandle(p.x(f.colXs[c]), widthY, vertical = true, table, 0.0, p.accent) { start, _, dx, _, done ->
                        val w = start.widths.toMutableList()
                        val d = (dx / tableW).toDouble().coerceIn(MIN_COL - w[c - 1], w[c] - MIN_COL)
                        w[c - 1] += d
                        w[c] -= d
                        finishDrag(editor, table, start, start.copy(widths = w), done)
                    }
                }
            }

            // The outer boundary moves the table's own right edge, since no column waits beyond
            // it to trade with; the columns keep their shares of whatever width is left. Unlike
            // the boundaries above it this drag moves the table's own width, so it measures
            // against the width at the grab, not the one it is busy changing.
            key("tw", fi) {
                DragHandle(p.x(f.colXs[cols]), widthY, vertical = true, table, p.vp.w, p.accent) { start, grabbed, dx, _, done ->
                    val next = FlowTable.widthAfterDrag(start.width, grabbed, dx.toDouble())
                    finishDrag(editor, table, start, start.copy(width = next), done)
                }
            }

            // Row heights: a handle beside the bottom of each row that ends on this page.
            val heightX = (p.vp.right + px(16.dp)).toFloat()
            for ((k, row) in f.rows.withIndex()) {
                if (k == f.rows.lastIndex && continuesOnNextPage(placed, fi)) continue
                key("rh", fi, row.row) {
                    val scaleY by rememberUpdatedState(p.scaleY)
                    DragHandle(heightX, p.y(row.bottom), vertical = false, table, row.bottom - row.top, p.accent) { start, rowH, _, dy, done ->
                        val h = List(maxOf(start.minHeights.size, row.row + 1)) { start.minHeights.getOrElse(it) { 0.0 } }.toMutableList()
                        h[row.row] = ((rowH + dy / scaleY) / TableStyle.PX_PER_PT).coerceAtLeast(0.0)
                        finishDrag(editor, table, start, start.copy(minHeights = h), done)
                    }
                }
            }

            if (fi == 0) {
                key("done") {
                    Centered(
                        (p.vp.right + px(30.dp)).toFloat(), plusY, 32.dp, 32.dp,
                        Modifier
                            .shadow(1.5.dp, CircleShape)
                            .clip(CircleShape)
                            .background(p.accent)
                            .clickable(role = Role.Button) { editor.endTableEdit() },
                    ) {
                        Icon(Ph.check, stringResource(R.string.done), tint = p.glyph, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        // The lifted grip, drawn on its own input-free layer so the grip under the finger keeps a steady pointer.
        LiftedGrip(reorder = { reorder }, placed = { current })
    }
}

/** Preview [next] while a drag runs; [done] lands it as one undo step (or reverts a cancel). */
private fun finishDrag(editor: Editor, table: FlowTable, start: TableSnapshot, next: TableSnapshot, done: Boolean?) {
    when (done) {
        null -> editor.tablePreview(table, next)
        true -> editor.tableCommitPreview(table, start)
        false -> editor.tablePreview(table, start)
    }
}

/** True when the last row slice of [placed][i] carries on at the top of the next slice. */
private fun continuesOnNextPage(placed: List<Placed>, i: Int): Boolean {
    val next = placed.getOrNull(i + 1)?.frag?.rows?.firstOrNull() ?: return false
    return placed[i].frag.rows.last().row == next.row
}

/** Insertion points on a slice: the row index a boundary inserts before, and its viewport y. */
private fun rowBoundaries(p: Placed): Pair<List<Int>, List<Float>> {
    val rows = mutableListOf<Int>()
    val ys = mutableListOf<Float>()
    val slices = p.frag.rows
    rows.add(slices.first().row)
    ys.add(p.y(slices.first().top))
    for ((k, s) in slices.withIndex()) {
        val nextRow = slices.getOrNull(k + 1)?.row
        if (nextRow == s.row) continue
        rows.add(s.row + 1)
        ys.add(p.y(s.bottom))
    }
    return rows to ys
}

/** A box of [w] x [h] centred on viewport ([x], [y]). */
@Composable
private fun Centered(
    x: Float,
    y: Float,
    w: Dp,
    h: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val wp = with(density) { w.toPx() }
    val hp = with(density) { h.toPx() }
    Box(
        Modifier
            .offset { IntOffset((x - wp / 2f).roundToInt(), (y - hp / 2f).roundToInt()) }
            .size(w, h)
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** An insert button (.ti-plus): a 22 dp disc of [accent] with a 13 dp + in [glyph], in a 36 dp target; presses to .86. */
@Composable
private fun PlusButton(x: Float, y: Float, accent: Color, glyph: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Centered(x, y, 36.dp, 36.dp, Modifier.clip(CircleShape).clickable(src, null, role = Role.Button, onClick = onClick)) {
        Box(
            Modifier
                .size(22.dp)
                .pressScale(src, 0.86f)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ph.plus, stringResource(R.string.insert), tint = glyph, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * A row or column grip (.ti-grip, 52 × 24, r12): a drag area (dots-six-vertical, turned on a column grip) that moves
 * it and an × that deletes it, on the [glyph] face with a faint [accent] ring and one soft shadow. While [lifted] it
 * fades out and [LiftedGrip] draws it under the finger instead; it never moves itself, so its drag keeps a still
 * pointer.
 */
@Composable
private fun Grip(
    x: Float,
    y: Float,
    vertical: Boolean,
    accent: Color,
    glyph: Color,
    lifted: () -> Boolean,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Centered(
        x, y, 52.dp, 24.dp,
        Modifier
            .graphicsLayer { alpha = if (lifted()) 0f else 1f }
            .shadow(1.dp, shape)
            .clip(shape)
            .background(glyph)
            .border(1.dp, accent.copy(alpha = 0.14f), shape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp, 24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start() },
                            onDragEnd = { end() },
                            onDragCancel = { cancel() },
                        ) { change, amount ->
                            change.consume()
                            move(amount)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Ph.dotsSixVertical,
                    stringResource(R.string.move),
                    tint = accent.copy(alpha = 0.68f),
                    modifier = Modifier.size(15.dp).rotate(if (vertical) 0f else 90f),
                )
            }
            Box(
                Modifier
                    .size(24.dp)
                    .drawBehind {
                        drawLine(accent.copy(alpha = 0.12f), Offset(0f, 6.dp.toPx()), Offset(0f, size.height - 6.dp.toPx()), 1.dp.toPx())
                    }
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ph.x, stringResource(R.string.delete), tint = accent, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/**
 * The lifted grip (.ti-grip.lift): the dragged row's or column's grip at the finger, along its axis, scaled 1.06 with
 * an 8 dp shadow. Everything is read in its layer and draw lambdas, so a drag recomposes nothing.
 */
@Composable
private fun LiftedGrip(reorder: () -> Reorder?, placed: () -> List<Placed>) {
    val shape = RoundedCornerShape(12.dp)
    Canvas(
        Modifier
            .size(52.dp, 24.dp)
            .graphicsLayer {
                val r = reorder()
                if (r == null) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val c = if (r.axis == Axis.COL) Offset(r.pos.x, r.start.y) else Offset(r.start.x, r.pos.y)
                alpha = 1f
                translationX = c.x - size.width / 2f
                translationY = c.y - size.height / 2f
                scaleX = 1.06f
                scaleY = 1.06f
                shadowElevation = 8.dp.toPx()
                this.shape = shape
                clip = true
            },
    ) {
        val r = reorder() ?: return@Canvas
        val p = placed().getOrNull(r.frag) ?: return@Canvas
        drawLiftedGrip(p.accent, p.glyph, column = r.axis == Axis.COL)
    }
}

/** The grip's face, drawn: fill, ring, six dots in the 28 dp drag area (turned for a column), the rule and the ×. */
private fun DrawScope.drawLiftedGrip(accent: Color, glyph: Color, column: Boolean) {
    val corner = CornerRadius(12.dp.toPx())
    drawRoundRect(glyph, cornerRadius = corner)
    drawRoundRect(accent.copy(alpha = 0.16f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
    val dot = accent.copy(alpha = 0.68f)
    val cx = 14.dp.toPx()
    val cy = size.height / 2f
    val along = 5.dp.toPx()
    val across = 2.5.dp.toPx()
    val radius = 1.4.dp.toPx()
    for (i in -1..1) {
        for (j in 0..1) {
            val a = i * along
            val b = (j * 2 - 1) * across
            drawCircle(dot, radius, if (column) Offset(cx + a, cy + b) else Offset(cx + b, cy + a))
        }
    }
    drawLine(accent.copy(alpha = 0.12f), Offset(28.dp.toPx(), 6.dp.toPx()), Offset(28.dp.toPx(), size.height - 6.dp.toPx()), 1.dp.toPx())
    val x = Offset(40.dp.toPx(), cy)
    val h = 3.5.dp.toPx()
    val w = 1.5.dp.toPx()
    drawLine(accent, x + Offset(-h, -h), x + Offset(h, h), w, StrokeCap.Round)
    drawLine(accent, x + Offset(-h, h), x + Offset(h, -h), w, StrokeCap.Round)
}

/** One resize drag in flight: the table and the handle's [measure] as it began, and the travel. */
private class HandleDrag {
    var start: TableSnapshot? = null
    var measure = 0.0
    var dx = 0f
    var dy = 0f
}

/**
 * A resize handle at viewport ([x], [y]) (.ti-rh): an 8 × 24 bar (24 × 8 for a row) in [accent], r4, across the edge
 * it moves ([vertical] = a column boundary, dragged sideways). [onChange] receives the table and [measure] as the drag
 * began (the layout moves under a live drag), the travel so far, and done = null while moving, true at the end, false
 * on cancel. A handle that leaves the composition mid-drag (its row moved page) lands what it had.
 */
@Composable
private fun DragHandle(
    x: Float,
    y: Float,
    vertical: Boolean,
    table: FlowTable,
    measure: Double,
    accent: Color,
    onChange: (start: TableSnapshot, measure: Double, dx: Float, dy: Float, done: Boolean?) -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(measure)
    val drag = remember { HandleDrag() }
    fun emit(done: Boolean?) {
        val start = drag.start ?: return
        if (done != null) drag.start = null
        change(start, drag.measure, drag.dx, drag.dy, done)
    }
    DisposableEffect(Unit) { onDispose { emit(true) } }
    Centered(
        x, y, if (vertical) 22.dp else 34.dp, if (vertical) 34.dp else 22.dp,
        Modifier.pointerInput(table) {
            detectDragGestures(
                onDragStart = {
                    drag.start = table.snapshot()
                    drag.measure = current
                    drag.dx = 0f
                    drag.dy = 0f
                },
                onDragEnd = { emit(true) },
                onDragCancel = { emit(false) },
            ) { c, amount ->
                c.consume()
                drag.dx += amount.x
                drag.dy += amount.y
                emit(null)
            }
        },
    ) {
        Box(
            Modifier
                .size(if (vertical) 8.dp else 24.dp, if (vertical) 24.dp else 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent),
        )
    }
}

private val BORDER_CHOICES = listOf(
    TableBorders.ALL,
    TableBorders.OUTER,
    TableBorders.HORIZONTAL,
    TableBorders.NONE,
)

private const val LINE_STEP = 0.25
private const val PREVIEW_MAX = 8

/** Narrowest a column can be dragged, as a fraction of the table. */
private const val MIN_COL = 0.04
