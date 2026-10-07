package com.xnotes.ui

import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class MoreToolsTest {

    private fun key(tool: Tool, layout: ToolbarLayout = ToolbarLayout.DEFAULT, surface: ToolSurface = ToolSurface.NOTE, ruler: Boolean = false): Any {
        val items = moreItems(layout, surface, hasPdf = false)
        val lit = moreLit(items, tool.barItem()) { it == ToolbarItem.RULER && ruler }
        return barGlideKey(tool, layout, lit)
    }

    @Test fun aHiddenToolInHandPutsTheGliderUnderMore() {
        assertEquals(ToolbarItem.MORE, key(Tool.PAN))
        assertEquals(ToolbarItem.MORE, key(Tool.TEXT_BOX))
        assertEquals(ToolbarItem.MORE, key(Tool.PAN, ToolbarLayout.CANVAS_DEFAULT, ToolSurface.CANVAS))
    }

    @Test fun aToolOnTheBarKeepsItsOwnButton() {
        assertEquals(Tool.ERASER, key(Tool.ERASER))
        assertEquals(Tool.PEN, key(Tool.PEN))
    }

    @Test fun aHiddenPenTypeGlidesToThePenButtonNotToMore() {
        // Ballpoint is hidden, but the pen button stands for it, so the menu does not list it.
        assertEquals(Tool.PEN, key(Tool.BALLPOINT))
    }

    @Test fun theRulerSwitchLightsMoreButLeavesTheGliderAlone() {
        assertEquals(Tool.PEN, key(Tool.PEN, ruler = true))
        val items = moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false)
        assertEquals(MoreLit.SWITCH_ON, moreLit(items, ToolbarItem.PEN) { it == ToolbarItem.RULER })
    }
}
