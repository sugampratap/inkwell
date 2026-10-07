package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.history.History
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.StickyColors
import com.xnotes.core.model.TableItem
import com.xnotes.core.model.TextItem
import com.xnotes.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sticky notes and placed tables through the controller, as the Insert menu and the long-press
 * menu drive them: what lands on the page, what is open for typing, and what one undo takes back.
 */
class StickyTableControllerTest {

    private val m = FakeTextMeasurer()

    private fun setup(): Pair<CanvasState, InteractionController> {
        val doc = Document(mutableListOf(Page(1240.0, 1754.0)))
        val st = CanvasState(doc, FakeSurfaceFactory(), Palette.DEFAULT).apply {
            viewportW = 1600
            viewportH = 2400
            relayout()
        }
        return st to InteractionController(st, History(), m, requestRender = {})
    }

    // --- sticky notes ---

    @Test fun aStickyNoteOpensForTypingAndJoinsThePageOnCommit() {
        val (st, ctrl) = setup()
        val page = st.document.pages[0]
        ctrl.insertStickyNote(null)
        val note = ctrl.editingItem
        assertNotNull(note)
        assertTrue(note!!.isSticky)
        assertEquals(StickyColors.YELLOW, note.fill)
        assertTrue("not on the page until the edit ends", page.items.isEmpty())
        val field = ctrl.editingField()!!
        assertTrue("the card is drawn by the canvas, so the field has no border", field.card)
        assertEquals(note.textWidth(), field.width, 1e-9)

        ctrl.updateEditingText("call Sam")
        ctrl.commitTextEdit()
        assertSame(note, page.items.single())
        assertEquals("call Sam", note.text)
        ctrl.history.undo()
        assertTrue("one undo takes the whole note away", page.items.isEmpty())
    }

    @Test fun anEmptyStickyNoteIsKeptUnlikeAnEmptyTextBox() {
        val (st, ctrl) = setup()
        ctrl.insertStickyNote(null)
        ctrl.commitTextEdit()
        assertEquals(1, st.document.pages[0].items.size)

        ctrl.insertTextBoxAt(Pt(300.0, 300.0))
        ctrl.commitTextEdit()
        assertEquals("an empty text box is dropped as before", 1, st.document.pages[0].items.size)
    }

    @Test fun aStickyNoteSitsInsideThePageAroundThePressPoint() {
        val (st, ctrl) = setup()
        val pr = st.pageRects[0]
        ctrl.insertStickyNote(Pt(pr.right - 2.0, pr.bottom - 2.0)) // pressed in the very corner
        val note = ctrl.editingItem!!
        val cover = st.footprint(st.document.pages[0])
        val b = note.bounds()
        assertTrue(b.right <= cover.right && b.bottom <= cover.bottom && b.left >= cover.left && b.top >= cover.top)
    }

    @Test fun recolouringANoteIsOneUndoStepAndSticksForTheNextOne() {
        val (st, ctrl) = setup()
        ctrl.insertStickyNote(null)
        ctrl.updateEditingText("x")
        ctrl.commitTextEdit()
        val note = st.document.pages[0].items.single() as TextItem
        ctrl.selectAllOnPage(null)
        assertSame(note, ctrl.selectedSticky())
        ctrl.setStickyColor(StickyColors.BLUE)
        assertEquals(StickyColors.BLUE, note.fill)
        ctrl.history.undo()
        assertEquals(StickyColors.YELLOW, note.fill)
        assertEquals(StickyColors.BLUE, ctrl.nextStickyColor)
    }

    @Test fun aStickyNotesTextSitsInsideItsPadding() {
        val note = TextItem(Pt(0.0, 0.0), 300.0, 0.0, "", StickyColors.TEXT, 14.0, TextItem.STICKY_FACE, m, fill = StickyColors.PINK)
        val pad = TextItem.stickyPadding(14.0)
        assertEquals(pad, note.textRect().x, 1e-9)
        assertEquals(300.0 - 2 * pad, note.textRect().w, 1e-9)
        assertEquals("an empty note is one line plus its padding", 14.0 * 1.3 + 2 * pad, note.bounds().h, 1e-9)
        assertTrue("the shadow paints past the card", note.paintBounds().h > note.bounds().h)
        val plain = TextItem(Pt(0.0, 0.0), 300.0, 0.0, "", StickyColors.TEXT, 14.0, TextItem.STICKY_FACE, m)
        assertEquals(plain.bounds(), plain.textRect())
        assertEquals(plain.bounds(), plain.paintBounds())
    }

    // --- tables ---

    @Test fun insertingATableOpensItsFirstCell() {
        val (st, ctrl) = setup()
        ctrl.insertTable(null, rows = 2, cols = 4)
        val t = st.document.pages[0].items.single() as TableItem
        assertSame(t, ctrl.editingTable)
        assertEquals(0 to 0, ctrl.editingCell)
        assertEquals(2, t.grid.rows)
        assertEquals(4, t.grid.cols)
        val f = ctrl.editingField()!!
        assertTrue(f.cell)
        assertTrue("header row is bold", f.bold)
    }

    @Test fun typingIsOneUndoStepPerCellAndTabMovesOn() {
        val (st, ctrl) = setup()
        val history = ctrl.history
        ctrl.insertTable(null, rows = 1, cols = 2)
        val t = st.document.pages[0].items.single() as TableItem
        ctrl.updateEditingText("a")
        ctrl.updateEditingText("ab")
        ctrl.tableAdvanceCell(backward = false)
        assertEquals(0 to 1, ctrl.editingCell)
        ctrl.updateEditingText("c")
        // Past the last cell, Tab makes a new row (Word's and Samsung's behaviour).
        ctrl.tableAdvanceCell(backward = false)
        assertEquals(2, t.grid.rows)
        assertEquals(1 to 0, ctrl.editingCell)
        ctrl.commitTextEdit()
        assertNull(ctrl.editingTable)
        assertEquals("ab", t.grid.cell(0, 0).text)
        assertEquals("c", t.grid.cell(0, 1).text)

        history.undo() // the added row
        assertEquals(1, t.grid.rows)
        history.undo() // cell (0, 1)
        assertEquals("", t.grid.cell(0, 1).text)
        assertEquals("ab", t.grid.cell(0, 0).text)
        history.undo() // cell (0, 0)
        assertEquals("", t.grid.cell(0, 0).text)
        history.undo() // the insert
        assertTrue(st.document.pages[0].items.isEmpty())
    }

    @Test fun structuralEditsFollowTheOpenCell() {
        val (st, ctrl) = setup()
        ctrl.insertTable(null, rows = 2, cols = 2)
        val t = st.document.pages[0].items.single() as TableItem
        ctrl.tableInsertRow(below = true)
        assertEquals(3, t.grid.rows)
        assertEquals(1 to 0, ctrl.editingCell)
        ctrl.tableInsertColumn(right = false)
        assertEquals(3, t.grid.cols)
        assertEquals(1 to 0, ctrl.editingCell)
        ctrl.tableSetCellFill(StickyColors.GREEN)
        assertEquals(StickyColors.GREEN, t.grid.cell(1, 0).fill)
        ctrl.tableToggleHeader()
        assertFalse(t.grid.header)
        ctrl.tableDeleteColumn()
        ctrl.tableDeleteRow()
        assertEquals(2, t.grid.rows)
        assertEquals(2, t.grid.cols)
    }

    @Test fun deletingTheLastRowDeletesTheTable() {
        val (st, ctrl) = setup()
        ctrl.insertTable(null, rows = 1, cols = 1)
        ctrl.tableDeleteRow()
        assertTrue(st.document.pages[0].items.isEmpty())
        assertNull(ctrl.editingTable)
        ctrl.history.undo()
        assertEquals(1, st.document.pages[0].items.size)
    }

    @Test fun aSelectedTableCanGrowWithoutOpeningIt() {
        val (st, ctrl) = setup()
        ctrl.insertTable(null, rows = 2, cols = 2)
        ctrl.commitTextEdit()
        val t = st.document.pages[0].items.single() as TableItem
        ctrl.selectAllOnPage(null)
        assertSame(t, ctrl.selectedTable())
        ctrl.tableInsertRow(below = true)
        ctrl.tableInsertColumn(right = true)
        assertEquals(3, t.grid.rows)
        assertEquals(3, t.grid.cols)
        assertTrue(ctrl.editSelection())
        assertSame(t, ctrl.editingTable)
    }

    @Test fun aTableIsInkedForItsPaper() {
        val (st, ctrl) = setup()
        ctrl.insertTable(null)
        val t = st.document.pages[0].items.single() as TableItem
        val paper = st.paperColor(st.document.pages[0])
        val paperLum = 0.299 * paper.r + 0.587 * paper.g + 0.114 * paper.b
        val inkLum = 0.299 * t.grid.textColor.r + 0.587 * t.grid.textColor.g + 0.114 * t.grid.textColor.b
        assertTrue("type contrasts with the paper", kotlin.math.abs(paperLum - inkLum) > 120)
    }
}
