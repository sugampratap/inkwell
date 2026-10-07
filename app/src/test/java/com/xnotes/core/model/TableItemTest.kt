package com.xnotes.core.model

import com.xnotes.core.FakeRenderer
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.history.TableEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The placed table's pure parts: layout (rows grow to their tallest cell, never below a reserved
 * height), hit testing (cells and column borders), the structural edits, scaling, and the
 * immutability that lets a copy and an undo share grids safely.
 */
class TableItemTest {

    private val m = FakeTextMeasurer()

    /** FakeTextMeasurer: each newline-separated line is pointSize * 1.3 tall, whatever the width. */
    private fun line(pt: Double) = pt * 1.3

    private fun table(rows: Int = 2, cols: Int = 3, width: Double = 300.0, pt: Double = 10.0) =
        TableItem(Pt(50.0, 80.0), TableGrid.empty(rows, cols, width, pt), m)

    @Test fun emptyGridSharesTheWidthAndStartsAutoHeight() {
        val g = TableGrid.empty(3, 4, 400.0, 10.0)
        assertEquals(3, g.rows)
        assertEquals(4, g.cols)
        assertEquals(listOf(100.0, 100.0, 100.0, 100.0), g.colWidths)
        assertTrue(g.rowHeights.all { it == 0.0 })
        assertTrue(g.cells.flatten().all { it.text.isEmpty() && it.fill == null })
    }

    @Test fun emptyGridIsClampedToTheInsertPickerRange() {
        val g = TableGrid.empty(0, 99, 400.0)
        assertEquals(1, g.rows)
        assertEquals(TableItem.MAX_SIZE, g.cols)
    }

    @Test fun rowsAreOneLineTallWhenEmptyAndGrowWithTheirTallestCell() {
        val t = table()
        val padY = TableItem.padY(10.0)
        val one = line(10.0) + 2 * padY
        var l = t.layout()
        assertEquals(one, l.rowHeight(0), 1e-9)
        assertEquals(one, l.rowHeight(1), 1e-9)

        t.grid = t.grid.withCellText(1, 2, "a\nb\nc")
        l = t.layout()
        assertEquals(one, l.rowHeight(0), 1e-9)
        assertEquals(3 * line(10.0) + 2 * padY, l.rowHeight(1), 1e-9)
        assertEquals(Rect(50.0, 80.0, 300.0, l.rowY[2]), t.bounds())
    }

    @Test fun aReservedRowHeightIsAMinimumNotAClip() {
        val g0 = TableGrid.empty(1, 1, 100.0, 10.0)
        val reserved = g0.copy(rowHeights = listOf(200.0))
        val t = TableItem(Pt.ZERO, reserved, m)
        assertEquals(200.0, t.layout().rowHeight(0), 1e-9)
        t.grid = reserved.withCellText(0, 0, List(30) { "x" }.joinToString("\n"))
        assertTrue(t.layout().rowHeight(0) > 200.0)
    }

    @Test fun cellAtFindsTheCellUnderAPageSpacePoint() {
        val t = table() // columns 100 wide from x=50
        val h = t.layout().rowHeight(0)
        assertEquals(0 to 0, t.cellAt(Pt(51.0, 81.0)))
        assertEquals(0 to 2, t.cellAt(Pt(349.0, 81.0)))
        assertEquals(1 to 1, t.cellAt(Pt(160.0, 80.0 + h + 1.0)))
        assertNull(t.cellAt(Pt(49.0, 81.0)))
        assertNull(t.cellAt(Pt(160.0, 80.0 + 2 * h + 5.0)))
    }

    @Test fun columnBoundariesAreTheInnerAndRightEdgesNotTheLeft() {
        val l = table().layout()
        assertEquals(1, l.columnBoundaryAt(Pt(101.0, 5.0), 4.0))
        assertEquals(3, l.columnBoundaryAt(Pt(298.0, 5.0), 4.0))
        assertNull(l.columnBoundaryAt(Pt(1.0, 5.0), 4.0))
        assertNull(l.columnBoundaryAt(Pt(150.0, 5.0), 4.0))
        assertNull("off the table vertically", l.columnBoundaryAt(Pt(100.0, l.height + 50.0), 4.0))
    }

    @Test fun insertAndDeleteRowsAndColumnsKeepTheGridRectangular() {
        var g = TableGrid.empty(2, 2, 200.0).withCellText(0, 0, "a").withCellText(1, 1, "d")
        g = g.insertRow(1)
        assertEquals(3, g.rows)
        assertEquals("a", g.cell(0, 0).text)
        assertEquals("", g.cell(1, 0).text)
        assertEquals("d", g.cell(2, 1).text)
        g = g.insertColumn(0)
        assertEquals(3, g.cols)
        assertTrue(g.cells.all { it.size == 3 })
        assertEquals("a", g.cell(0, 1).text)
        assertEquals("added beside: same width as its neighbour", 100.0, g.colWidths[0], 1e-9)
        g = g.deleteRow(1).deleteColumn(0)
        assertEquals(2, g.rows)
        assertEquals(2, g.cols)
        assertEquals("a", g.cell(0, 0).text)
        assertEquals("d", g.cell(1, 1).text)
        assertEquals(g.rows, g.rowHeights.size)
    }

    @Test fun theLastRowOrColumnIsNeverRemoved() {
        val g = TableGrid.empty(1, 1, 100.0)
        assertSame(g, g.deleteRow(0))
        assertSame(g, g.deleteColumn(0))
    }

    @Test fun columnWidthHasAFloor() {
        val g = TableGrid.empty(1, 2, 200.0, 10.0).withColumnWidth(0, 1.0)
        assertEquals(TableItem.minColumnWidth(10.0), g.colWidths[0], 1e-9)
    }

    @Test fun aCornerScaleGrowsTheTypeAnEdgeScaleOnlyTheGrid() {
        val t = table(pt = 10.0)
        val uniform = Affine.scaleAbout(t.pos, 2.0, 2.0)
        t.applyTransform(uniform)
        assertEquals(20.0, t.grid.pointSize, 1e-9)
        assertEquals(200.0, t.grid.colWidths[0], 1e-9)
        assertEquals(Pt(50.0, 80.0), t.pos)

        val t2 = table(pt = 10.0)
        val rowBefore = t2.layout().rowHeight(0)
        t2.applyTransform(Affine.scaleAbout(t2.pos, 1.0, 3.0))
        assertEquals(10.0, t2.grid.pointSize, 1e-9)
        assertEquals(100.0, t2.grid.colWidths[0], 1e-9)
        assertEquals("a stretched row reserves what it showed, scaled", rowBefore * 3.0, t2.layout().rowHeight(0), 1e-9)
    }

    @Test fun snapshotRestoreIsExact() {
        val t = table()
        val snap = t.snapshotGeometry()
        t.applyTransform(Affine.scaleAbout(Pt.ZERO, 2.0, 2.0))
        t.restoreGeometry(snap)
        assertEquals(Pt(50.0, 80.0), t.pos)
        assertEquals(TableGrid.empty(2, 3, 300.0, 10.0), t.grid)
    }

    @Test fun aCopySharesTheGridButEditsApart() {
        val t = table()
        t.grid = t.grid.withCellText(0, 0, "x")
        t.locked = true
        val c = t.deepCopy(m) as TableItem
        assertNotSame(t, c)
        assertSame(t.grid, c.grid)
        assertTrue(c.locked)
        c.grid = c.grid.withCellText(0, 0, "y")
        c.translate(5.0, 5.0)
        assertEquals("x", t.grid.cell(0, 0).text)
        assertEquals(Pt(50.0, 80.0), t.pos)
    }

    @Test fun tableEditUndoesAndRedoesTheWholeGrid() {
        val t = table()
        val before = t.grid
        val after = before.insertRow(0).withCellFill(0, 0, StickyColors.PINK)
        t.grid = after
        val cmd = TableEdit(t, before, after)
        cmd.undo()
        assertSame(before, t.grid)
        cmd.redo()
        assertSame(after, t.grid)
    }

    @Test fun paintLeavesOutTheOpenCellsTextOnly() {
        val t = table()
        t.grid = t.grid.withCellText(0, 0, "a").withCellText(1, 2, "b")
        val all = FakeRenderer().also { t.paint(it) }
        val skipping = FakeRenderer().also { t.paint(it, 0 to 0) }
        assertEquals(2, all.ops.count { it == "drawText" })
        assertEquals(1, skipping.ops.count { it == "drawText" })
        assertEquals(all.ops.count { it == "strokePolygon" }, skipping.ops.count { it == "strokePolygon" })
    }

    @Test fun aHeaderRowIsBoldAndShaded() {
        val g = TableGrid.empty(2, 2, 200.0)
        assertTrue(g.fontFor(0).bold)
        assertFalse(g.fontFor(1).bold)
        assertFalse(g.copy(header = false).fontFor(0).bold)
        val shaded = FakeRenderer().also { TableItem(Pt.ZERO, g, m).paint(it) }
        val plain = FakeRenderer().also { TableItem(Pt.ZERO, g.copy(header = false), m).paint(it) }
        assertEquals(1, shaded.ops.count { it == "fillPolygon" } - plain.ops.count { it == "fillPolygon" })
    }

    @Test fun roundRectStaysInsideItsBoxAndSharpCornersAreOnePoint() {
        val r = Rect(10.0, 20.0, 100.0, 50.0)
        val pts = CardPaint.roundRect(r, 8.0)
        assertTrue(pts.all { it.x >= r.left - 1e-9 && it.x <= r.right + 1e-9 && it.y >= r.top - 1e-9 && it.y <= r.bottom + 1e-9 })
        assertEquals(4, CardPaint.roundRect(r, 0.0).size)
        // A radius past half the short side is clamped, never folding the outline over itself.
        assertTrue(CardPaint.roundRect(r, 500.0).all { it.y >= r.top - 1e-9 && it.y <= r.bottom + 1e-9 })
    }
}
