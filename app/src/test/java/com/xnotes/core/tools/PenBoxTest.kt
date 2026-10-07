package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenBoxTest {

    private val black = Rgba(20, 20, 20, 255)
    private val blue = Rgba(0, 0, 200, 255)
    private fun pen(width: Double = 2.0, color: Rgba = black, tool: Tool = Tool.PEN) =
        PenPreset(tool, ToolDefaults.configFor(tool).copy(baseWidth = width), color)

    @Test
    fun aPenMatchesItselfWhateverInkIsBakedIntoItsConfig() {
        val p = pen()
        assertTrue(p.matches(Tool.PEN, p.config.copy(rgba = Rgba(1, 2, 3, 255)), black))
    }

    @Test
    fun widthColourToolAndCurveAllTellPensApart() {
        val p = pen()
        assertFalse(p.matches(Tool.PEN, p.config.copy(baseWidth = 2.5), black))
        assertFalse(p.matches(Tool.PEN, p.config, blue))
        assertFalse(p.matches(Tool.CALLIGRAPHY, p.config, black))
        assertFalse(p.matches(Tool.PEN, p.config.copy(pressureMinFactor = 0.9), black))
    }

    @Test
    fun addingAPenAlreadyInTheBoxMovesItToTheEnd() {
        val a = pen(1.0)
        val b = pen(2.0)
        val box = PenBox.add(PenBox.add(PenBox.add(emptyList(), a), b), a)
        assertEquals(listOf(b, a), box)
    }

    @Test
    fun aFullBoxDropsItsOldestPen() {
        var box = emptyList<PenPreset>()
        for (i in 1..PenBox.MAX + 3) box = PenBox.add(box, pen(i.toDouble()))
        assertEquals(PenBox.MAX, box.size)
        assertEquals(4.0, box.first().config.baseWidth, 0.0)
        assertEquals((PenBox.MAX + 3).toDouble(), box.last().config.baseWidth, 0.0)
    }

    @Test
    fun onlyInkingToolsGoInTheBox() {
        assertEquals(emptyList<PenPreset>(), PenBox.add(emptyList(), pen(tool = Tool.ERASER)))
        assertEquals(emptyList<PenPreset>(), PenBox.add(emptyList(), pen(tool = Tool.LASSO)))
        assertEquals(1, PenBox.add(emptyList(), pen(tool = Tool.HIGHLIGHTER)).size)
    }

    @Test
    fun removeAndIndexOf() {
        val a = pen(1.0)
        val b = pen(2.0, blue)
        val box = listOf(a, b)
        assertEquals(1, PenBox.indexOf(box, Tool.PEN, b.config, blue))
        assertEquals(-1, PenBox.indexOf(box, Tool.PEN, b.config, black))
        assertEquals(listOf(a), PenBox.remove(box, 1))
        assertEquals(box, PenBox.remove(box, 7))
    }

    @Test
    fun theDefaultBoxIsDistinctPens() {
        val d = PenBox.DEFAULT
        assertTrue(d.isNotEmpty())
        for (i in d.indices) assertEquals(i, PenBox.indexOf(d, d[i].tool, d[i].config, d[i].color))
    }
}
