package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.text.TableDefaults
import com.xnotes.core.text.TableStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableLogicTest {

    private val pane = IntSize(1280, 800)

    // --- the size picker's grid (TI 827-828): one step = a 30 dp cell + a 6 dp gap ---

    @Test fun theFirstTouchPicksOneByOneAtTheCorner() {
        assertEquals(1 to 1, gridPick(0f, 0f, 36f))
    }

    @Test fun aStepBoundaryMovesToTheNextCell() {
        assertEquals(1 to 1, gridPick(35.9f, 35.9f, 36f))
        assertEquals(1 to 2, gridPick(36f, 0f, 36f))
        assertEquals(2 to 3, gridPick(100f, 40f, 36f))
    }

    @Test fun aTouchOffTheGridIsKeptOnIt() {
        assertEquals(1 to 1, gridPick(-5f, -40f, 36f))
        assertEquals(8 to 8, gridPick(1000f, 1000f, 36f))
    }

    // --- a bar over a table (posOver, TI 591-593) ---

    private val bar = IntSize(300, 64)

    @Test fun theBarSitsCentredAboveTheTableWhenThereIsRoom() {
        assertEquals(IntOffset(450, 226), barOver(IntRect(400, 300, 800, 500), bar, pane, clearTop = 100, gap = 10, margin = 8))
    }

    @Test fun theBarGoesBelowWhenAboveWouldCrossTheToolbar() {
        assertEquals(IntOffset(450, 510), barOver(IntRect(400, 150, 800, 500), bar, pane, clearTop = 100, gap = 10, margin = 8))
    }

    @Test fun theBarStaysInsideThePane() {
        assertEquals(8, barOver(IntRect(0, 300, 100, 500), bar, pane, 100, 10, 8).x)
        assertEquals(1280 - 8 - 300, barOver(IntRect(1200, 300, 1280, 500), bar, pane, 100, 10, 8).x)
        assertEquals(800 - 8 - 64, barOver(IntRect(400, 120, 800, 780), bar, pane, 100, 10, 8).y)
    }

    // --- a menu off a bar button (posBeside, TI 595-597) ---

    private fun beside(sel: IntRect, barBottom: Int, buttonX: Int = 600) = menuBeside(
        menuWidth = 236, buttonCentreX = buttonX, barBottom = barBottom, sel = sel, pane = pane,
        gap = 8, clear = 12, side = 16, margin = 8, originInset = 16,
    )

    @Test fun aMenuClearOfTheTableIsCentredOnItsButton() {
        val s = beside(IntRect(400, 300, 800, 500), barBottom = 600)
        assertEquals(CardSpot(x = 482, y = 608, originX = 600), s)
    }

    @Test fun aMenuThatWouldCoverTheTableGoesToItsLeft() {
        val s = beside(IntRect(400, 300, 800, 500), barBottom = 226)
        assertEquals(400 - 16 - 236, s.x)
        assertEquals(234, s.y)
        // Grows from the button, kept 16 inside the menu's own span.
        assertEquals(148 + 236 - 16, s.originX)
    }

    @Test fun withNoRoomOnTheLeftItGoesToTheRight() {
        val s = beside(IntRect(100, 300, 800, 500), barBottom = 226)
        assertEquals(816, s.x)
        assertEquals(816 + 16, s.originX)
    }

    @Test fun theRightHandFallbackIsKeptInsideThePane() {
        val s = beside(IntRect(100, 300, 1200, 500), barBottom = 226)
        assertEquals(1280 - 8 - 236, s.x)
    }

    // --- the style card beside a typed-text table (G9, TI 1055) ---

    private fun card(table: IntRect) = styleCardBeside(
        table = table, cardWidth = 440, pane = pane, top = 76, gap = 16, endInset = 44, margin = 8, originX = 500,
    )

    @Test fun theStyleCardGoesAfterTheTableWhenItFits() {
        assertEquals(CardSpot(516, 76, 500), card(IntRect(100, 200, 500, 400)))
    }

    @Test fun elseBeforeIt() {
        assertEquals(344, card(IntRect(800, 200, 1200, 400)).x)
    }

    @Test fun elseAgainstThePanesEndAsTheMockupDrawsIt() {
        assertEquals(1280 - 44 - 440, card(IntRect(290, 200, 990, 400)).x)
    }

    // --- a tap on a typing-bar action (TI 884) ---

    private enum class M { A, B }

    @Test fun aTapOpensItsMenuOrClosesItWhenOpen() {
        assertEquals(M.A, menuAfterTap(null, M.A, null, 0L, 1_000L))
        assertNull(menuAfterTap(M.A, M.A, null, 0L, 1_000L))
        assertEquals(M.B, menuAfterTap(M.A, M.B, null, 0L, 1_000L))
    }

    @Test fun theTapThatJustDismissedAMenuDoesNotReopenIt() {
        // A non-focusable popup is dismissed by the same tap's touch-down, before the action's click arrives.
        assertNull(menuAfterTap(null, M.A, M.A, 1_000L, 1_100L))
        assertEquals(M.A, menuAfterTap(null, M.A, M.A, 1_000L, 1_000L + TOGGLE_GRACE_MS))
        assertEquals(M.B, menuAfterTap(null, M.B, M.A, 1_000L, 1_100L))
    }

    // --- "Default for new tables" (TI 1006-1016) ---

    private val factory = TableDefaults()
    private val custom = TableDefaults(rows = 4, style = TableStyle(paddingPt = 6.0))

    @Test fun theDefaultRowAppearsOnceSomethingDiffersAndStaysForTheSession() {
        assertFalse(defaultRowShown(false, factory, factory))
        assertTrue(defaultRowShown(false, custom, factory))
        assertTrue(defaultRowShown(true, factory, factory))
    }

    @Test fun theDefaultRowIsHiddenWhileTheStyleIsFactory() {
        assertFalse(defaultRowVisible(shown = true, current = factory))
        assertTrue(defaultRowVisible(shown = true, current = custom))
        assertFalse(defaultRowVisible(shown = false, current = custom))
    }

    @Test fun theDefaultRowIsOnOnlyWhenTheStyleIsTheSavedNonFactoryDefault() {
        assertTrue(defaultRowOn(current = custom, saved = custom))
        assertFalse(defaultRowOn(current = custom, saved = factory))
        assertFalse(defaultRowOn(current = factory, saved = factory))
        assertFalse(defaultRowOn(current = factory, saved = custom))
    }

    // --- the .ti-2 row of Padding and Lines (TI 165) ---

    @Test fun inTheStyleCardPaddingRunsIntoTheGapAndLinesStaysOnTheGrid() {
        // 440 dp card less 2 x 20: 400 dp; columns of 188, 24 apart. "Padding" (56) + 10 + a 132 dp stepper is 198.
        val c = twoColumns(width = 400, gap = 24, leastGap = 12, firstNeeds = 198)
        assertEquals(198, c.firstWidth)
        assertEquals(212, c.secondStart)
        assertEquals(188, c.secondWidth)
        assertTrue(c.secondStart - c.firstWidth >= 12)
    }

    @Test fun inTheWiderSheetBothColumnsKeepTheGrid() {
        val c = twoColumns(width = 440, gap = 24, leastGap = 12, firstNeeds = 198)
        assertEquals(TwoColumns(208, 232, 208), c)
    }

    @Test fun theFirstColumnNeverTakesMoreThanTheGapLess12() {
        val c = twoColumns(width = 400, gap = 24, leastGap = 12, firstNeeds = 260)
        assertEquals(200, c.firstWidth)
        assertEquals(212, c.secondStart)
    }
}
