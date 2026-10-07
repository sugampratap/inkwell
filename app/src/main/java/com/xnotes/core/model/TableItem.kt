package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import com.xnotes.core.pal.TextFlags
import com.xnotes.core.pal.TextMeasurer
import java.util.concurrent.ConcurrentHashMap

/** One table cell: its typed text and, when shaded, its own fill. */
data class TableCell(val text: String = "", val fill: Rgba? = null)

/**
 * Everything a [TableItem] is apart from where it sits: the grid, its text and its look.
 *
 * Immutable on purpose. Thumbnails and the PDF export paint pages off the main thread, so a table
 * being typed into must never be read half-changed. Every edit builds a new grid and swaps it in
 * through one volatile field, which is the discipline the rest of the model keeps (see
 * [snapshot]); it also makes undo trivial, since a before and an after grid are the whole edit.
 *
 * [rowHeights] are reserved minimums, 0 meaning "as tall as the text": a row grows to fit what is
 * typed into it and never clips, and a vertical resize reserves height the way a text box does.
 */
data class TableGrid(
    val colWidths: List<Double>,
    val rowHeights: List<Double>,
    val cells: List<List<TableCell>>,
    val pointSize: Double = TableItem.DEFAULT_POINT_SIZE,
    val face: FontFace = TableItem.DEFAULT_FACE,
    val textColor: Rgba = Rgba(33, 33, 33),
    val borderColor: Rgba = Rgba(33, 33, 33, 110),
    /** The first row is a header: shaded with [headerFill] and set in bold. */
    val header: Boolean = true,
    val headerFill: Rgba? = Rgba(33, 33, 33, 22),
) {
    val rows: Int get() = cells.size
    val cols: Int get() = colWidths.size

    fun cell(row: Int, col: Int): TableCell = cells.getOrNull(row)?.getOrNull(col) ?: TableCell()

    /** The font row [row] is set in: bold in a header row. */
    fun fontFor(row: Int): FontSpec = FontSpec(pointSize, face, bold = header && row == 0)

    fun withCell(row: Int, col: Int, cell: TableCell): TableGrid {
        if (row !in 0 until rows || col !in 0 until cols) return this
        return copy(cells = cells.mapIndexed { r, line -> if (r != row) line else line.mapIndexed { c, old -> if (c == col) cell else old } })
    }

    fun withCellText(row: Int, col: Int, text: String): TableGrid = withCell(row, col, cell(row, col).copy(text = text))

    fun withCellFill(row: Int, col: Int, fill: Rgba?): TableGrid = withCell(row, col, cell(row, col).copy(fill = fill))

    /** A fresh empty row before index [at] (0..rows), as tall as the text needs. */
    fun insertRow(at: Int): TableGrid {
        val i = at.coerceIn(0, rows)
        val newCells = cells.toMutableList().apply { add(i, List(cols) { TableCell() }) }
        val newHeights = rowHeights.toMutableList().apply { add(i, 0.0) }
        return copy(cells = newCells, rowHeights = newHeights)
    }

    /** A fresh empty column before index [at] (0..cols), as wide as the column it is added beside. */
    fun insertColumn(at: Int): TableGrid {
        val i = at.coerceIn(0, cols)
        val w = colWidths.getOrNull(if (i >= cols) cols - 1 else i) ?: TableItem.DEFAULT_COL_WIDTH
        return copy(
            colWidths = colWidths.toMutableList().apply { add(i, w) },
            cells = cells.map { line -> line.toMutableList().apply { add(i, TableCell()) } },
        )
    }

    /** Without row [at]; the last row is never removed (that is deleting the table). */
    fun deleteRow(at: Int): TableGrid {
        if (rows <= 1 || at !in 0 until rows) return this
        return copy(
            cells = cells.toMutableList().apply { removeAt(at) },
            rowHeights = rowHeights.toMutableList().apply { removeAt(at) },
        )
    }

    /** Without column [at]; the last column is never removed. */
    fun deleteColumn(at: Int): TableGrid {
        if (cols <= 1 || at !in 0 until cols) return this
        return copy(
            colWidths = colWidths.toMutableList().apply { removeAt(at) },
            cells = cells.map { line -> line.toMutableList().apply { removeAt(at) } },
        )
    }

    fun withColumnWidth(col: Int, width: Double): TableGrid {
        if (col !in 0 until cols) return this
        val w = width.coerceAtLeast(TableItem.minColumnWidth(pointSize))
        return copy(colWidths = colWidths.mapIndexed { c, old -> if (c == col) w else old })
    }

    /**
     * Scaled by a resize: column widths by [sx], the rows' current heights (as laid out, [laidOut])
     * reserved and scaled by [sy], and the type by [sx] when the scale was uniform (a corner drag),
     * exactly as a text box treats its wrap width, reserved height and font size.
     */
    fun scaled(sx: Double, sy: Double, uniform: Boolean, laidOut: DoubleArray): TableGrid {
        val pt = if (uniform) (pointSize * sx).coerceAtLeast(MIN_POINT) else pointSize
        val minW = TableItem.minColumnWidth(pt)
        return copy(
            colWidths = colWidths.map { (it * sx).coerceAtLeast(minW) },
            // A uniform scale grows the type with the box, so an auto row stays auto; a stretch
            // reserves what the row showed before scaling it, or dragging an edge would do nothing.
            rowHeights = rowHeights.mapIndexed { i, h ->
                when {
                    uniform -> h * sx
                    sy != 1.0 -> maxOf(h, laidOut.getOrElse(i) { 0.0 }) * sy
                    else -> h
                }
            },
            pointSize = pt,
        )
    }

    companion object {
        private const val MIN_POINT = 4.0

        /** An empty [rows] x [cols] grid whose columns share [width] evenly. */
        fun empty(rows: Int, cols: Int, width: Double, pointSize: Double = TableItem.DEFAULT_POINT_SIZE): TableGrid {
            val r = rows.coerceIn(1, TableItem.MAX_SIZE)
            val c = cols.coerceIn(1, TableItem.MAX_SIZE)
            val w = (width / c).coerceAtLeast(TableItem.minColumnWidth(pointSize))
            return TableGrid(
                colWidths = List(c) { w },
                rowHeights = List(r) { 0.0 },
                cells = List(r) { List(c) { TableCell() } },
                pointSize = pointSize,
            )
        }
    }
}

/**
 * Where a [TableGrid]'s rows and columns fall, relative to the table's top-left: [colX] and [rowY]
 * hold every boundary (one more than there are columns or rows), so cell (r, c) spans
 * `colX[c]..colX[c+1]` by `rowY[r]..rowY[r+1]`. Pure geometry, so it is unit-tested directly.
 */
class TableLayout(val colX: DoubleArray, val rowY: DoubleArray, val padX: Double, val padY: Double) {
    val width: Double get() = colX.last()
    val height: Double get() = rowY.last()
    val rows: Int get() = rowY.size - 1
    val cols: Int get() = colX.size - 1

    fun rowHeight(r: Int): Double = rowY[r + 1] - rowY[r]

    fun rowHeights(): DoubleArray = DoubleArray(rows) { rowHeight(it) }

    /** Cell (r, c)'s box, relative to the table's top-left. */
    fun cellRect(r: Int, c: Int): Rect = Rect(colX[c], rowY[r], colX[c + 1] - colX[c], rowY[r + 1] - rowY[r])

    /** Where cell (r, c)'s text is laid out: the cell inset by the padding. */
    fun textRect(r: Int, c: Int): Rect {
        val cell = cellRect(r, c)
        return Rect(cell.x + padX, cell.y + padY, (cell.w - 2 * padX).coerceAtLeast(1.0), (cell.h - 2 * padY).coerceAtLeast(0.0))
    }

    /** The cell under [p] (relative to the table's top-left), or null when [p] is outside the table. */
    fun cellAt(p: Pt): Pair<Int, Int>? {
        if (p.x < 0.0 || p.y < 0.0 || p.x > width || p.y > height || rows <= 0 || cols <= 0) return null
        var c = 0
        while (c < cols - 1 && p.x > colX[c + 1]) c++
        var r = 0
        while (r < rows - 1 && p.y > rowY[r + 1]) r++
        return r to c
    }

    /**
     * The column boundary within [tol] of [p] horizontally (1..cols: the right edge of column
     * index-1), for dragging a column wider. The table's left edge is not a boundary: it is the
     * table's position, which a move changes.
     */
    fun columnBoundaryAt(p: Pt, tol: Double): Int? {
        if (p.y < -tol || p.y > height + tol) return null
        var best: Int? = null
        var bestD = tol
        for (i in 1..cols) {
            val d = kotlin.math.abs(p.x - colX[i])
            if (d <= bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    companion object {
        /**
         * Lay [g] out: each row as tall as its tallest cell's wrapped text plus padding, or its
         * reserved height if more. [measure] answers "how tall is this text at this wrap width in
         * this font", and must be the same measurer the painter draws with.
         */
        fun compute(g: TableGrid, measure: (String, FontSpec, Double) -> Double): TableLayout {
            val padX = TableItem.padX(g.pointSize)
            val padY = TableItem.padY(g.pointSize)
            val colX = DoubleArray(g.cols + 1)
            for (c in 0 until g.cols) colX[c + 1] = colX[c] + g.colWidths[c]
            val rowY = DoubleArray(g.rows + 1)
            for (r in 0 until g.rows) {
                val font = g.fontFor(r)
                var h = measure(" ", font, 1.0) + 2 * padY // one line, even for an empty row
                for (c in 0 until g.cols) {
                    val text = g.cell(r, c).text
                    if (text.isEmpty()) continue
                    val wrap = (g.colWidths[c] - 2 * padX).coerceAtLeast(1.0)
                    h = maxOf(h, measure(text, font, wrap) + 2 * padY)
                }
                rowY[r + 1] = rowY[r] + maxOf(h, g.rowHeights.getOrElse(r) { 0.0 })
            }
            return TableLayout(colX, rowY, padX, padY)
        }
    }
}

/**
 * A table placed on a page (Insert > Table): a rows x columns grid of typed cells with rounded
 * outer corners and an optional shaded, bold header row. It moves, resizes, copies and deletes as
 * one item; its cells are typed into in place (see the controller's cell editing).
 *
 * Unlike the tables of the text flow ([com.xnotes.core.text.FlowTable]), which are paragraphs
 * that paginate with the text around them, this one is a free object like a text box or an image.
 * Its whole state apart from [pos] is the immutable [grid]; the layout is cached against that very
 * grid instance, so painting never measures twice and never sees a half-made layout.
 */
class TableItem(
    var pos: Pt,
    grid: TableGrid,
    private val measurer: TextMeasurer,
) : CanvasItem {

    @Volatile
    var grid: TableGrid = grid

    override val kind = KIND
    override val resizable = true
    override var locked = false

    /** The layout of [cachedFor], swapped in as one pair so a reader never pairs a grid with another's layout. */
    private class Cached(val grid: TableGrid, val layout: TableLayout)

    @Volatile
    private var cached: Cached? = null

    /** Measured heights by (text, wrap px, font): a resize re-lays out every frame with the same texts. */
    private val heights = ConcurrentHashMap<Triple<String, Int, FontSpec>, Double>()

    fun layout(): TableLayout = layoutOf(grid)

    private fun layoutOf(g: TableGrid): TableLayout {
        cached?.let { if (it.grid === g) return it.layout }
        if (heights.size > HEIGHT_MEMO_MAX) heights.clear()
        val l = TableLayout.compute(g) { text, font, wrap ->
            heights.getOrPut(Triple(text, wrap.toInt(), font)) { measurer.measure(text, font, wrap, FLAGS).h }
        }
        cached = Cached(g, l)
        return l
    }

    override fun bounds(): Rect {
        val l = layout()
        return Rect(pos.x, pos.y, l.width, l.height)
    }

    /** Cell (r, c)'s box in page space. */
    fun cellBounds(r: Int, c: Int): Rect = layout().cellRect(r, c).translate(pos.x, pos.y)

    /** Cell (r, c)'s text box in page space. */
    fun cellTextBounds(r: Int, c: Int): Rect = layout().textRect(r, c).translate(pos.x, pos.y)

    /** The cell under a page-space point, or null. */
    fun cellAt(p: Pt): Pair<Int, Int>? = layout().cellAt(Pt(p.x - pos.x, p.y - pos.y))

    override fun paint(r: Renderer) = paint(r, null)

    /**
     * Draw the table, leaving out the text of [skip] (row, column): the cell being typed into,
     * whose text the live editor field shows instead.
     */
    fun paint(r: Renderer, skip: Pair<Int, Int>?) {
        val g = grid
        val l = layoutOf(g)
        if (g.rows == 0 || g.cols == 0) return
        val ox = pos.x
        val oy = pos.y
        val box = Rect(ox, oy, l.width, l.height)
        val rad = cornerRadius(g.pointSize)
        val lastR = g.rows - 1
        val lastC = g.cols - 1
        // Fills first, each corner cell rounded where the table is, so a shaded corner never pokes
        // out past the outline.
        if (g.header && g.headerFill != null) {
            val hr = Rect(ox, oy, l.width, l.rowHeight(0))
            val bottom = if (lastR == 0) rad else 0.0
            r.fillPolygon(CardPaint.roundRect(hr, rad, rad, bottom, bottom), g.headerFill)
        }
        for (row in 0..lastR) {
            for (col in 0..lastC) {
                val fill = g.cell(row, col).fill ?: continue
                val cr = l.cellRect(row, col).translate(ox, oy)
                val tl = if (row == 0 && col == 0) rad else 0.0
                val tr = if (row == 0 && col == lastC) rad else 0.0
                val br = if (row == lastR && col == lastC) rad else 0.0
                val bl = if (row == lastR && col == 0) rad else 0.0
                r.fillPolygon(CardPaint.roundRect(cr, tl, tr, br, bl), fill)
            }
        }
        val pen = Pen(g.borderColor, borderWidth(g.pointSize), cosmetic = false)
        for (row in 1..lastR) {
            val y = oy + l.rowY[row]
            r.strokePolyline(listOf(Pt(ox, y), Pt(ox + l.width, y)), pen)
        }
        for (col in 1..lastC) {
            val x = ox + l.colX[col]
            r.strokePolyline(listOf(Pt(x, oy), Pt(x, oy + l.height)), pen)
        }
        r.strokePolygon(CardPaint.roundRect(box, rad), pen)
        for (row in 0..lastR) {
            val font = g.fontFor(row)
            for (col in 0..lastC) {
                if (skip != null && skip.first == row && skip.second == col) continue
                val text = g.cell(row, col).text
                if (text.isEmpty()) continue
                r.drawText(text, l.textRect(row, col).translate(ox, oy), font, g.textColor, FLAGS)
            }
        }
    }

    override fun translate(dx: Double, dy: Double) {
        pos = Pt(pos.x + dx, pos.y + dy)
    }

    override fun contains(p: Pt): Boolean = bounds().contains(p)

    override fun centroid(): Pt = bounds().center

    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean =
        bounds().distanceTo(Pt(cx, cy)) <= radius

    override fun snapshotGeometry(): GeometrySnapshot = TableSnapshotGeom(pos, grid)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap !is TableSnapshotGeom) return
        pos = snap.pos
        grid = snap.grid
    }

    /** Tables never rotate (like text). Columns take the horizontal scale, rows the vertical, the
     *  type a uniform one. */
    override fun applyTransform(t: Affine) {
        val laidOut = layout().rowHeights()
        pos = t.apply(pos)
        grid = grid.scaled(t.scaleX, t.scaleY, t.isUniformScale, laidOut)
    }

    companion object {
        const val KIND = "table"
        const val DEFAULT_POINT_SIZE = 12.0
        val DEFAULT_FACE = FontFace.SANS
        const val DEFAULT_COL_WIDTH = 180.0

        /** The insert picker's largest grid, and the most a table is created with. */
        const val MAX_SIZE = 8

        val FLAGS = TextFlags(wordWrap = true, alignLeft = true, alignTop = true)

        private const val HEIGHT_MEMO_MAX = 512

        fun padX(pointSize: Double): Double = pointSize * 0.7
        fun padY(pointSize: Double): Double = pointSize * 0.45
        fun cornerRadius(pointSize: Double): Double = pointSize * 0.75
        fun borderWidth(pointSize: Double): Double = (pointSize * 0.1).coerceAtLeast(0.8)

        /** The narrowest a column may be dragged or scaled: its padding and a few characters. */
        fun minColumnWidth(pointSize: Double): Double = 2 * padX(pointSize) + pointSize * 1.5
    }
}

/** A table's transformable state for the resize tools and their undo: position plus the whole grid. */
private data class TableSnapshotGeom(val pos: Pt, val grid: TableGrid) : GeometrySnapshot
