package com.xnotes.core.history

import com.xnotes.core.model.Bookmark
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageMargins
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reordering pages as one undo step: the pages, and what hangs off them, go where they go. */
class MovePagesTest {

    private fun doc(n: Int): Document {
        val d = Document()
        repeat(n) { d.pages.add(Page(100.0, 140.0)) }
        return d
    }

    @Test fun applyReordersThePages() {
        val d = doc(4)
        val (a, b, c, e) = d.pages.toList()
        val cmd = MovePages.apply(d, listOf(0), 3)
        assertTrue(cmd != null)
        assertEquals(listOf(b, c, a, e), d.pages)
    }

    @Test fun aNoOpRecordsNothing() {
        val d = doc(3)
        val before = d.pages.toList()
        assertNull(MovePages.apply(d, listOf(1), 2))
        assertEquals(before, d.pages)
    }

    @Test fun undoPutsThePagesBackAndRedoMovesThemAgain() {
        val d = doc(5)
        val original = d.pages.toList()
        val cmd = MovePages.apply(d, listOf(1, 3), 0)!!
        val moved = d.pages.toList()
        cmd.undo()
        assertEquals(original, d.pages)
        cmd.undo() // idempotent
        assertEquals(original, d.pages)
        cmd.redo()
        assertEquals(moved, d.pages)
        cmd.redo() // idempotent
        assertEquals(moved, d.pages)
    }

    @Test fun throughHistoryItIsOneStep() {
        val d = doc(3)
        val original = d.pages.toList()
        val h = History()
        h.push(MovePages.apply(d, listOf(2), 0)!!)
        h.undo()
        assertEquals(original, d.pages)
        h.redo()
        assertSame(original[2], d.pages[0])
    }

    @Test fun bookmarksFollowTheirPages() {
        val d = doc(4)
        d.bookmarks.add(Bookmark(0, "first"))
        d.bookmarks.add(Bookmark(3, "last"))
        d.bookmarks.add(Bookmark(1, "second"))
        val cmd = MovePages.apply(d, listOf(0), 4)!! // order is now 1, 2, 3, 0
        assertEquals(listOf(3, 2, 0), d.bookmarks.map { it.page })
        cmd.undo()
        assertEquals(listOf(0, 3, 1), d.bookmarks.map { it.page })
        cmd.redo()
        assertEquals(listOf(3, 2, 0), d.bookmarks.map { it.page })
    }

    @Test fun aBookmarkPastTheEndIsLeftAlone() {
        val d = doc(2)
        d.bookmarks.add(Bookmark(9, "stale"))
        MovePages.apply(d, listOf(1), 0)
        assertEquals(9, d.bookmarks[0].page)
    }

    @Test fun aPageKeepsItsPdfPageStyleAndMargins() {
        val d = doc(3)
        val pdf = d.pages[2]
        pdf.pdfPage = 7
        pdf.style = PageStyle(pageColor = Rgba(250, 240, 220, 255))
        pdf.margins = PageMargins(top = 12.0)
        MovePages.apply(d, listOf(2), 0)
        assertSame(pdf, d.pages[0])
        assertEquals(7, d.pages[0].pdfPage)
        assertEquals(Rgba(250, 240, 220, 255), d.pages[0].style.pageColor)
        assertEquals(12.0, d.pages[0].margins.top!!, 0.0)
    }

    @Test fun itTouchesNoItems() {
        val d = doc(2)
        val cmd = MovePages.apply(d, listOf(1), 0)!!
        assertEquals(0, cmd.touched { null }!!.size)
    }
}
