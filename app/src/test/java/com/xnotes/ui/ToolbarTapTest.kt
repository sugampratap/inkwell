package com.xnotes.ui

import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolbarTapTest {

    /** Just enough of an editor: the tool in hand, armed by [hostArmTool]. */
    private class Host(var armed: Tool) : ToolPopupHost {
        override fun toolConfig(tool: Tool): ToolConfig = ToolDefaults.configFor(tool)
        override fun updateToolConfig(tool: Tool, config: ToolConfig) {}
        override val hostShapeConfig: ShapeConfig = ShapeConfig()
        override fun updateShapeConfig(config: ShapeConfig) {}
        override val hostToolbarColors: List<Rgba> = emptyList()
        override val hostActiveColorIndex: Int = 0
        override val hostRecentColors: List<Rgba> = emptyList()
        override fun setSwatchColor(index: Int, color: Rgba) {}
        override fun rememberSwatchColor(index: Int) {}
        override val hostTool: Tool get() = armed
        override fun hostArmTool(tool: Tool) { armed = tool }
    }

    @Test fun aDifferentToolIsPickedUpAndTheOpenCardCloses() {
        val host = Host(Tool.PEN)
        val cards = ToolCardState()
        cards.open(CardKey.OfTool(Tool.PEN))
        cards.onToolTap(host, Tool.ERASER, ToolSurface.NOTE)
        assertEquals(Tool.ERASER, host.armed)
        assertNull(cards.open)
    }

    @Test fun theArmedToolOpensItsCardAndASecondTapClosesIt() {
        val host = Host(Tool.ERASER)
        val cards = ToolCardState()
        cards.onToolTap(host, Tool.ERASER, ToolSurface.NOTE)
        assertEquals(CardKey.OfTool(Tool.ERASER), cards.open)
        cards.onToolTap(host, Tool.ERASER, ToolSurface.NOTE)
        assertNull(cards.open)
        assertEquals(Tool.ERASER, host.armed)
    }

    @Test fun aToolWithNoCardIsJustArmedAgain() {
        val host = Host(Tool.PAN)
        val cards = ToolCardState()
        cards.onToolTap(host, Tool.PAN, ToolSurface.NOTE)
        assertNull(cards.open)
        assertEquals(Tool.PAN, host.armed)
    }

    @Test fun theCanvasHasNoTextCard() {
        val host = Host(Tool.TEXT)
        val cards = ToolCardState()
        cards.onToolTap(host, Tool.TEXT, ToolSurface.CANVAS)
        assertNull(cards.open)
        cards.onToolTap(host, Tool.TEXT, ToolSurface.NOTE)
        assertEquals(CardKey.OfTool(Tool.TEXT), cards.open)
    }

    @Test fun anOpenToolCardIsFoundByTheToolItWasOpenedFor() {
        val host = Host(Tool.PEN)
        val cards = ToolCardState()
        cards.onToolTap(host, Tool.PEN, ToolSurface.NOTE)
        // The pen card arms another pen type: the card keeps its key.
        host.hostArmTool(Tool.BALLPOINT)
        assertEquals(listOf(Tool.PEN), cards.toolKeys())
    }

    @Test fun aSheetIsGoneAtOnce() {
        val cards = ToolCardState()
        cards.open(BarCards.STYLES)
        cards.dismissNow(BarCards.STYLES)
        assertNull(cards.open)
    }
}
