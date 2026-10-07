package com.xnotes.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.StickyColors
import com.xnotes.core.model.TableItem
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.icons.TableGlyphs
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillDivider
import com.xnotes.ui.kit.InkPillMetrics
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** Cell shadings: the note pastels, translucent so the table's own ink reads on light paper and dark. */
internal val CELL_SHADES: List<Rgba> = StickyColors.ALL.map { it.withAlpha(CELL_SHADE_ALPHA) }

private const val CELL_SHADE_ALPHA = 120

/** The readout under the grid (.ti-szr b): 30/34 ExtraBold, tracked −0.8, tabular. */
private val SizeValueStyle = InkType.display.copy(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp).tnum()

private val GRID_CELL = 30.dp
private val GRID_GAP = 6.dp

/** A 1 dp hairline across the top, drawn behind (no layout). */
internal fun Modifier.topHairline(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}

/**
 * Insert › Table (TI Frame 1): pick the size on a grid of up to 8 × 8, the way Samsung Notes and Word do it, in the
 * shared 340 dp card. A tap or a drag across the grid sizes the table from its first touch to its lift (3 × 3 to begin
 * with); Insert places it. Nothing is placed until Insert, so a stray touch costs nothing; ×, Cancel, Back and a tap
 * outside close it. TableChrome hosts it under the paperclip, or under a long press.
 */
@Composable
internal fun TableSizeCard(onPick: (rows: Int, cols: Int) -> Unit, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    val sizeText = stringResource(
        R.string.ptable_size_text,
        pluralStringResource(R.plurals.ptable_n_rows, rows, rows),
        pluralStringResource(R.plurals.ptable_n_columns, cols, cols),
    )
    InkCard(title = stringResource(R.string.ptable_insert_title), onClose = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SizeGrid(rows, cols, sizeText) { r, c ->
                rows = r
                cols = c
            }
            Text(
                stringResource(R.string.ptable_size_value, rows, cols),
                style = SizeValueStyle,
                color = ink.text,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(sizeText, style = InkType.counter, color = ink.text2)
            Text(
                stringResource(R.string.ptable_insert_hint),
                style = InkType.hint,
                color = ink.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .topHairline(ink.line2)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InkGhostButton(stringResource(R.string.cancel), onDismiss)
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.ptable_insert_action), { onPick(rows, cols) })
        }
    }
}

/**
 * The 8 × 8 grid (.ti-szg), drawn in one canvas: cells 30 dp, 6 apart, r8 with a 1 dp line ring; the picked block
 * solid. Whatever the finger or pen is over, from its first touch to its lift, sizes the table.
 */
@Composable
private fun SizeGrid(rows: Int, cols: Int, state: String, onPick: (rows: Int, cols: Int) -> Unit) {
    val ink = LocalInk.current
    val n = TableItem.MAX_SIZE
    val label = stringResource(R.string.ptable_grid)
    val pick by rememberUpdatedState(onPick)
    Canvas(
        Modifier
            .size(GRID_CELL * n + GRID_GAP * (n - 1))
            .semantics {
                contentDescription = label
                stateDescription = state
            }
            .pointerInput(Unit) {
                val step = (GRID_CELL + GRID_GAP).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float, y: Float) {
                        val (r, c) = gridPick(x, y, step, n)
                        pick(r, c)
                    }
                    at(down.position.x, down.position.y)
                    down.consume()
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        at(ch.position.x, ch.position.y)
                        ch.consume()
                    }
                }
            },
    ) {
        val cell = GRID_CELL.toPx()
        val step = cell + GRID_GAP.toPx()
        val radius = 8.dp.toPx()
        val hair = 1.dp.toPx()
        for (r in 0 until n) {
            for (c in 0 until n) {
                val topLeft = Offset(c * step, r * step)
                if (r < rows && c < cols) {
                    drawRoundRect(ink.solid, topLeft, Size(cell, cell), CornerRadius(radius))
                } else {
                    drawRoundRect(
                        ink.line,
                        topLeft + Offset(hair / 2f, hair / 2f),
                        Size(cell - hair, cell - hair),
                        CornerRadius(radius - hair / 2f),
                        style = Stroke(hair),
                    )
                }
            }
        }
    }
}

// --- the typing bar ---

/** The typing bar's menus (TI 585-586) and the cell-colour card (TI 57-69). */
private enum class TypingMenu { INSERT, DELETE, COLOUR }

/** Menus are .ti-menu's 236 dp, a fixed width, so menuBeside can place them before they lay out. */
private val TABLE_MENU_W = 236.dp

/** The cell-colour card from its parts: padding 10, None 40, 4, rule 1, 4, six 40 dp swatches 2 apart, 2, custom 40, padding 10. */
private val CELL_CARD_W = 10.dp + 40.dp + 4.dp + 1.dp + 4.dp + 40.dp * 6 + 2.dp * 5 + 2.dp + 40.dp + 10.dp

/** Where the bar and its menu buttons were laid out, for placing a menu on a tap. Not state: written on layout. */
private class TypingBarLayout {
    var pane: LayoutCoordinates? = null
    var barBottom = 0
    val buttons = HashMap<TypingMenu, LayoutCoordinates>()
    var closed: TypingMenu? = null
    var closedAt = 0L
}

/**
 * The placed table's typing bar (TI Frames 2-3) on the shared pill: [Insert ▸, Delete ▸, Header, Cell colour ▸] |
 * [Done], 10 dp over the open table, or under it when the toolbar is in the way (posOver). Its menus open one step
 * under the bar and beside the table, never over it (posBeside). Neither the bar nor its menus take focus, so the
 * keyboard stays up; a tap on a lit action closes its menu. A table that is only selected shows nothing here: its
 * actions are on the selection bar.
 */
@Composable
fun TableObjectBar(editor: Editor) {
    val bar = editor.tableBar ?: return
    if (!bar.editing) return
    val density = LocalDensity.current
    val geo = remember { TypingBarLayout() }
    var menu by remember { mutableStateOf<TypingMenu?>(null) }
    var spots by remember { mutableStateOf(emptyMap<TypingMenu, CardSpot>()) }
    val r = bar.rect
    val table = IntRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())

    fun close(m: TypingMenu) {
        if (menu != m) return
        menu = null
        geo.closed = m
        geo.closedAt = SystemClock.uptimeMillis()
    }

    fun tap(m: TypingMenu, width: Dp) {
        val next = menuAfterTap(menu, m, geo.closed, geo.closedAt, SystemClock.uptimeMillis())
        geo.closed = null
        val pane = geo.pane
        val button = geo.buttons[m]
        if (next != null && pane != null && button != null && pane.isAttached && button.isAttached) {
            val b = pane.localBoundingBoxOf(button)
            val spot = with(density) {
                menuBeside(
                    menuWidth = width.roundToPx(),
                    buttonCentreX = b.center.x.roundToInt(),
                    barBottom = geo.barBottom,
                    sel = table,
                    pane = pane.size,
                    gap = 8.dp.roundToPx(),
                    clear = 12.dp.roundToPx(),
                    side = 16.dp.roundToPx(),
                    margin = 8.dp.roundToPx(),
                    originInset = 16.dp.roundToPx(),
                )
            }
            spots = spots + (m to spot)
        }
        menu = next
    }

    fun act(block: () -> Unit) {
        menu = null
        block()
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { geo.pane = it }) {
        Box(
            Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val at = barOver(
                    target = table,
                    bar = IntSize(p.width, p.height),
                    pane = IntSize(constraints.maxWidth, constraints.maxHeight),
                    clearTop = (editor.state.insetTop + 12.dp.toPx()).roundToInt(),
                    gap = 10.dp.roundToPx(),
                    margin = 8.dp.roundToPx(),
                )
                geo.barBottom = at.y + p.height
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(at) }
            },
        ) {
            InkPill {
                InkPillAction(
                    Ph.plus,
                    stringResource(R.string.ptable_insert),
                    { tap(TypingMenu.INSERT, TABLE_MENU_W) },
                    on = menu == TypingMenu.INSERT,
                    modifier = Modifier.onGloballyPositioned { geo.buttons[TypingMenu.INSERT] = it },
                )
                InkPillAction(
                    Ph.trash,
                    stringResource(R.string.ptable_delete),
                    { tap(TypingMenu.DELETE, TABLE_MENU_W) },
                    on = menu == TypingMenu.DELETE,
                    modifier = Modifier.onGloballyPositioned { geo.buttons[TypingMenu.DELETE] = it },
                )
                InkPillAction(
                    if (bar.header) Ph.tableFill else Ph.table,
                    stringResource(R.string.ptable_header_row),
                    { editor.tableToggleHeader() },
                    on = bar.header,
                )
                InkPillAction(
                    Ph.paintBucket,
                    stringResource(R.string.ptable_cell_colour),
                    { tap(TypingMenu.COLOUR, CELL_CARD_W) },
                    on = menu == TypingMenu.COLOUR,
                    modifier = Modifier.onGloballyPositioned { geo.buttons[TypingMenu.COLOUR] = it },
                    width = InkPillMetrics.ActionExtraWide,
                    dot = bar.cellFill?.withAlpha(255)?.toComposeColor(),
                )
                InkPillDivider()
                InkPillAction(Ph.check, stringResource(R.string.ptable_done), { act { editor.commitText() } }, solid = true)
            }
        }
        for (m in TypingMenu.entries) {
            val spot = spots[m] ?: continue
            key(m) {
                AnchorAt(spot.x, spot.y, spot.originX) { anchor ->
                    MediaPopover(
                        expanded = menu == m,
                        onDismiss = { close(m) },
                        anchor = anchor,
                        spec = PlacedSpec,
                        prefer = PopoverSide.BELOW,
                        focusable = false,
                    ) {
                        when (m) {
                            TypingMenu.INSERT -> TableMenuCard {
                                TableMenuRow(Ph.rowsPlusTop, stringResource(R.string.ptable_row_above)) { act { editor.tableInsertRow(below = false) } }
                                TableMenuRow(Ph.rowsPlusBottom, stringResource(R.string.ptable_row_below)) { act { editor.tableInsertRow(below = true) } }
                                TableMenuRow(Ph.columnsPlusLeft, stringResource(R.string.ptable_column_left)) { act { editor.tableInsertColumn(right = false) } }
                                TableMenuRow(Ph.columnsPlusRight, stringResource(R.string.ptable_column_right)) { act { editor.tableInsertColumn(right = true) } }
                            }
                            TypingMenu.DELETE -> TableMenuCard {
                                TableMenuRow(TableGlyphs.rowsMinus, stringResource(R.string.ptable_delete_row)) { act { editor.tableDeleteRow() } }
                                TableMenuRow(TableGlyphs.colsMinus, stringResource(R.string.ptable_delete_column)) { act { editor.tableDeleteColumn() } }
                                InkMenuDivider()
                                TableMenuRow(Ph.trash, stringResource(R.string.ptable_delete_table)) { act { editor.tableDeleteItem() } }
                            }
                            TypingMenu.COLOUR -> CellColourCard(
                                current = bar.cellFill,
                                recents = editor.stickyRecentColors,
                                onPick = { editor.tableSetCellFill(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A menu off the typing bar (.menu.ti-menu): 236 dp, raised r16 with the line2 ring and the one popup shadow. */
@Composable
private fun TableMenuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(TABLE_MENU_W)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .padding(vertical = 8.dp),
        content = content,
    )
}

/** A 42 dp row (.ti-menu .m-row). */
@Composable
private fun TableMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    InkMenuRow(label, onClick, Modifier.height(42.dp), icon = icon)
}

/**
 * The cell-colour card (.ti-mini, TI 57-69): No colour | the six note pastels at full strength | + for the shared
 * picker. The ring marks the open cell's shade. A pastel or a custom pick lands translucent (CELL_SHADE_ALPHA). It
 * stays open across picks, as before.
 */
@Composable
private fun CellColourCard(current: Rgba?, recents: List<Rgba>, onPick: (Rgba?) -> Unit) {
    val ink = LocalInk.current
    val custom = current?.takeIf { it !in CELL_SHADES }
    Row(
        Modifier
            .width(CELL_CARD_W)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoColourDot(selected = current == null) { onPick(null) }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(1.dp, 22.dp).drawBehind { drawRect(ink.line2) })
        Spacer(Modifier.width(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in CELL_SHADES) {
                InkSwatch(c.withAlpha(255).toComposeColor(), selected = current == c, size = 32.dp) { onPick(c) }
            }
        }
        Spacer(Modifier.width(2.dp))
        CustomColourDot(colour = custom, chosen = custom != null, initial = current?.withAlpha(255), recents = recents) {
            onPick(it.withAlpha(CELL_SHADE_ALPHA))
        }
    }
}

/** No colour (.ti-none): a 32 dp ring in line3 with a 1.6 dp diagonal in text2; chosen = ring 2 raised + 4 solid. A 40 dp target. */
@Composable
private fun NoColourDot(selected: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val label = stringResource(R.string.ptable_no_colour)
    Box(
        Modifier
            .size(40.dp)
            .pressScale(src, 0.9f)
            .clip(CircleShape)
            .clickable(src, null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .drawBehind {
                val r = 16.dp.toPx()
                if (selected) {
                    drawCircle(ink.solid, r + 4.dp.toPx())
                    drawCircle(ink.raised, r + 2.dp.toPx())
                }
                drawCircle(ink.line3, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                val half = r - 7.dp.toPx()
                rotate(45f) {
                    drawLine(ink.text2, Offset(center.x, center.y - half), Offset(center.x, center.y + half), 1.6.dp.toPx(), cap = StrokeCap.Round)
                }
            },
    )
}

/**
 * The custom colour dot (.ti-cust): with no custom colour, a dashed 1.5 dp line3 ring and a + (solid while its picker
 * is open); with one, filled with it, ringed while [chosen]. Opens the shared picker beside its card (Part 5),
 * which stays open across picks. Used by the cell card and the table style's Line colour and Tint.
 */
@Composable
internal fun CustomColourDot(colour: Rgba?, chosen: Boolean, initial: Rgba?, recents: List<Rgba>, onPick: (Rgba) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        InkAddSwatch(
            colour = colour?.withAlpha(255),
            lit = open,
            contentDescription = stringResource(R.string.media_custom_colour),
            chosen = chosen,
            size = 32.dp,
            cell = 40.dp,
        ) { open = true }
        // Beside the card it is in (the cell-colour card, the table style card): live, and open across picks.
        if (open) ColorPickerPopup(initial = initial, recents = recents, onDismiss = { open = false }, onPick = onPick)
    }
}
