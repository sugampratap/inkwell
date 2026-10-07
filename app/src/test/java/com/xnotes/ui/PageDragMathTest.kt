package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dragging a page in the two-column grid: the slot under the finger (before page N, or after the
 * last), where its insertion bar stands, and how fast the grid scrolls near its edges.
 */
class PageDragMathTest {

    /** Items 100 × 150, 20px gaps both ways: 0 1 / 2 3 visible. */
    private fun grid(vararg indices: Int): SlotBounds {
        val b = SlotBounds()
        for (i in indices) {
            val col = i % 2
            val row = i / 2
            val l = col * 120f
            val t = row * 170f
            b.add(i, l, t, l + 100f, t + 150f)
        }
        return b
    }

    private val bar = FloatArray(3)

    @Test fun theLeftHalfOfAPageIsBeforeIt() {
        assertEquals(0, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 30f, 50f, 20f, bar))
        assertEquals(-10f, bar[0], 0.01f) // in the gap left of page 0
        assertEquals(0f, bar[1], 0.01f)
        assertEquals(150f, bar[2], 0.01f)
    }

    @Test fun theRightHalfOfALeftPageIsBeforeItsNeighbour() {
        assertEquals(1, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 80f, 50f, 20f, bar))
        assertEquals(110f, bar[0], 0.01f) // the gap between the columns
    }

    @Test fun theRightHalfOfARightPageIsAfterIt() {
        assertEquals(2, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 200f, 50f, 20f, bar))
        assertEquals(230f, bar[0], 0.01f) // right of page 1, not left of page 2 on the next row
        assertEquals(0f, bar[1], 0.01f)
    }

    @Test fun theNearestRowWinsInTheGapBetweenRows() {
        assertEquals(2, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 30f, 165f, 20f, bar))
        assertEquals(170f, bar[1], 0.01f)
        assertEquals(0, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 30f, 152f, 20f, bar))
    }

    @Test fun pastTheVisibleRowsTheEdgeRowIsUsed() {
        assertEquals(0, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 30f, -60f, 20f, bar))
        assertEquals(4, PageDragMath.dropSlot(grid(0, 1, 2, 3), 6, 200f, 900f, 20f, bar))
    }

    @Test fun theEmptyCellAfterAnOddLastPageIsAfterTheLast() {
        assertEquals(3, PageDragMath.dropSlot(grid(0, 1, 2), 3, 200f, 200f, 20f, bar))
        assertEquals(110f, bar[0], 0.01f) // right of page 2
        assertEquals(170f, bar[1], 0.01f)
    }

    @Test fun aScrolledWindowUsesItsOwnIndices() {
        // Rows 3 and 4 in view (pages 6–9), shifted up by the scroll.
        val b = SlotBounds()
        b.add(6, 0f, -40f, 100f, 110f)
        b.add(7, 120f, -40f, 220f, 110f)
        b.add(8, 0f, 130f, 100f, 280f)
        b.add(9, 120f, 130f, 220f, 280f)
        assertEquals(9, PageDragMath.dropSlot(b, 10, 130f, 200f, 20f, bar))
        assertEquals(10, PageDragMath.dropSlot(b, 10, 210f, 200f, 20f, bar))
    }

    @Test fun nothingInViewIsNoSlot() {
        assertEquals(-1, PageDragMath.dropSlot(SlotBounds(), 4, 10f, 10f, 20f, bar))
    }

    @Test fun boundsGrowAndClear() {
        val b = SlotBounds()
        repeat(40) { b.add(it, 0f, it * 10f, 10f, it * 10f + 5f) }
        assertEquals(40, b.size)
        assertEquals(39, b.index[39])
        b.clear()
        assertEquals(0, b.size)
    }

    @Test fun autoScrollIsStillInTheMiddle() {
        assertEquals(0f, PageDragMath.autoScrollStep(500f, 1000f, 100f, 20f), 0.001f)
        assertEquals(0f, PageDragMath.autoScrollStep(100f, 1000f, 100f, 20f), 0.001f)
        assertEquals(0f, PageDragMath.autoScrollStep(900f, 1000f, 100f, 20f), 0.001f)
    }

    @Test fun autoScrollSpeedsUpTowardsAnEdge() {
        assertEquals(-10f, PageDragMath.autoScrollStep(50f, 1000f, 100f, 20f), 0.001f)
        assertEquals(-20f, PageDragMath.autoScrollStep(0f, 1000f, 100f, 20f), 0.001f)
        assertEquals(10f, PageDragMath.autoScrollStep(950f, 1000f, 100f, 20f), 0.001f)
        assertEquals(20f, PageDragMath.autoScrollStep(1000f, 1000f, 100f, 20f), 0.001f)
    }

    @Test fun autoScrollIsCappedPastAnEdge() {
        assertEquals(-20f, PageDragMath.autoScrollStep(-80f, 1000f, 100f, 20f), 0.001f)
        assertEquals(20f, PageDragMath.autoScrollStep(1300f, 1000f, 100f, 20f), 0.001f)
    }
}
