package com.xnotes.ui

import androidx.compose.ui.unit.dp
import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BarMetricsTest {

    @Test fun regularIsTheMockupsPill() {
        val m = barMetrics(ToolbarSize.REGULAR)
        assertEquals(44.dp, m.button)
        assertEquals(22.dp, m.icon)
        assertEquals(26.dp, m.swatch)
        assertEquals(36.dp, m.swatchHit)
        assertEquals(60.dp, m.thickness)
        assertEquals(24.dp, m.rule)
    }

    @Test fun compactAndComfortableScaleAroundIt() {
        val c = barMetrics(ToolbarSize.COMPACT)
        assertEquals(listOf(36.dp, 20.dp, 22.dp, 30.dp, 52.dp, 20.dp), listOf(c.button, c.icon, c.swatch, c.swatchHit, c.thickness, c.rule))
        val l = barMetrics(ToolbarSize.COMFORTABLE)
        assertEquals(listOf(52.dp, 26.dp, 30.dp, 42.dp, 68.dp, 28.dp), listOf(l.button, l.icon, l.swatch, l.swatchHit, l.thickness, l.rule))
    }

    @Test fun aTopBarSits14dpUnderTheHeaderAndTheOthers8dpFromTheirEdge() {
        assertEquals(74.dp, floatingCover(ToolbarLook(ToolbarPosition.TOP, ToolbarSize.REGULAR, floating = true)))
        assertEquals(68.dp, floatingCover(ToolbarLook(ToolbarPosition.BOTTOM, ToolbarSize.REGULAR, floating = true)))
        assertEquals(60.dp, floatingCover(ToolbarLook(ToolbarPosition.LEFT, ToolbarSize.COMPACT, floating = true)))
        assertEquals(76.dp, floatingCover(ToolbarLook(ToolbarPosition.RIGHT, ToolbarSize.COMFORTABLE, floating = true)))
    }

    @Test fun cardsOpenAwayFromTheBarsEdge() {
        assertEquals(PopoverSide.BELOW, cardSideFor(ToolbarPosition.TOP))
        assertEquals(PopoverSide.ABOVE, cardSideFor(ToolbarPosition.BOTTOM))
        assertEquals(PopoverSide.END, cardSideFor(ToolbarPosition.LEFT))
        assertEquals(PopoverSide.START, cardSideFor(ToolbarPosition.RIGHT))
    }
}
