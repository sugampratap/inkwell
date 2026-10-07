package com.xnotes.ui

import com.xnotes.core.tools.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One open card per bar, and the one still animating out. */
class ToolCardStateTest {

    private val pen = CardKey.OfTool(Tool.PEN)
    private val eraser = CardKey.OfTool(Tool.ERASER)

    @Test fun openingShowsTheCard() {
        val s = ToolCardState()
        s.open(pen)
        assertEquals(pen, s.open)
        assertNull(s.shown)
    }

    @Test fun aClosedCardStaysShownUntilItsExitEnds() {
        val s = ToolCardState()
        s.open(pen)
        s.close()
        assertNull(s.open)
        assertEquals(pen, s.shown)
        s.closed(pen)
        assertNull(s.shown)
    }

    @Test fun reopeningACardThatIsAnimatingOutTakesItBack() {
        val s = ToolCardState()
        s.open(pen)
        s.close()
        s.open(pen)
        assertEquals(pen, s.open)
        assertNull(s.shown)
        // The first close's late "exit over" changes nothing.
        s.closed(pen)
        assertEquals(pen, s.open)
    }

    @Test fun toggleOpensThenCloses() {
        val s = ToolCardState()
        s.toggle(pen)
        assertEquals(pen, s.open)
        s.toggle(pen)
        assertNull(s.open)
        assertEquals(pen, s.shown)
    }

    @Test fun openingAnotherCardLetsTheOldOneAnimateOut() {
        val s = ToolCardState()
        s.open(pen)
        s.open(eraser)
        assertEquals(eraser, s.open)
        assertEquals(pen, s.shown)
        // Only the card that is shown can finish its exit.
        s.closed(eraser)
        assertEquals(pen, s.shown)
        s.closed(pen)
        assertNull(s.shown)
        assertEquals(eraser, s.open)
    }

    @Test fun keysCompareByWhatTheyName() {
        assertEquals(CardKey.OfTool(Tool.PEN), pen)
        assertEquals(CardKey.OfSwatch(2), CardKey.OfSwatch(2))
        assertEquals(CardKey.Named("view"), CardKey.Named("view"))
    }
}
