package com.xnotes.ui

import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The customiser's live bar is built from the layout under it. */
class ToolbarPreviewTest {

    @Test fun theDefaultBarShowsItsShownToolsInSections() {
        val s = ToolbarPreview.visibleSections(ToolbarLayout.DEFAULT)
        // Zoom lock sits beside redo, since it is toggled so often.
        assertEquals(listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK), s[0])
        // Hidden pens wait in More tools; PDF text markup shows only on notes with a PDF.
        assertEquals(
            listOf(ToolbarItem.PEN, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER, ToolbarItem.LASSO, ToolbarItem.SHAPE, ToolbarItem.TEXT, ToolbarItem.IMAGE, ToolbarItem.LASER, ToolbarItem.TAPE, ToolbarItem.MORE),
            s[1],
        )
        assertEquals(listOf(ToolbarItem.COLORS), s[2])
        // The all-hidden chrome section leaves no empty gap on the bar.
        assertEquals(3, s.size)
        assertEquals(ToolbarItem.PEN, ToolbarPreview.activeItem(s))
        assertEquals(11, ToolbarPreview.shown(ToolbarLayout.DEFAULT.sections[1]))
    }

    @Test fun aBarWithNoPenHasNoActiveTool() {
        assertNull(ToolbarPreview.activeItem(listOf(listOf(ToolbarItem.UNDO))))
    }
}
