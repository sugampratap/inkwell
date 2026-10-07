package com.xnotes.ui

import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every tool has a bar item, so a glyph, a More row and the glider all find it. */
class ToolbarGlyphsTest {

    @Test fun everyToolSitsUnderTheBarItemOfTheSameId() {
        for (t in Tool.entries) assertEquals(t.name, t.id, t.barItem().id)
    }

    @Test fun eachPenTypeHasItsOwnItem() {
        assertEquals(ToolbarItem.PEN, Tool.PEN.barItem())
        assertEquals(ToolbarItem.BALLPOINT, Tool.BALLPOINT.barItem())
        assertEquals(ToolbarItem.TAPER, Tool.TAPER.barItem())
        assertEquals(ToolbarItem.SPEED, Tool.SPEED.barItem())
        assertEquals(ToolbarItem.PENCIL, Tool.PENCIL.barItem())
    }

    @Test fun thePencilWearsPhosphorsPencilInEveryWeight() {
        val g = glyphOf(ToolbarItem.PENCIL) as ItemGlyph.Vector
        assertEquals("pencil-simple", g.regular.name)
        assertEquals("pencil-simple-duotone", g.active.name)
        assertEquals("pencil-simple-fill", g.filled.name)
        // The Quill keeps its feather, and the dashed pen its own glyph.
        assertEquals("feather", (glyphOf(ToolbarItem.SPEED) as ItemGlyph.Vector).regular.name)
        assertEquals("line-segments", (glyphOf(ToolbarItem.DASHED) as ItemGlyph.Vector).regular.name)
    }
}
