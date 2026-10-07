package com.xnotes.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class TextOverlayMetricsTest {

    @Test fun theCaretStaysAboveTheFormatPill() {
        assertEquals(716.0, clearBottom(viewportH = 800.0, insetBottom = 0.0, clearance = 84.0), 0.0)
    }

    @Test fun withoutThePillTheToolbarCoverRules() {
        assertEquals(732.0, clearBottom(viewportH = 800.0, insetBottom = 68.0, clearance = 0.0), 0.0)
    }

    @Test fun thePillOverABottomToolbarReachesHigherThanItsCover() {
        // A 68 dp bottom bar: the pill sits 10 above it (gap 78) and is 64 tall.
        assertEquals(658.0, clearBottom(viewportH = 800.0, insetBottom = 68.0, clearance = 142.0), 0.0)
    }

    @Test fun thePillReachesPastTheCoverOnlyWhileItIsUp() {
        assertEquals(84.0, pillReachPx(insetBottom = 0.0, clearance = 84.0), 0.0)
        assertEquals(74.0, pillReachPx(insetBottom = 68.0, clearance = 142.0), 0.0)
        assertEquals(0.0, pillReachPx(insetBottom = 68.0, clearance = 0.0), 0.0)
    }

    @Test fun aFittingPageLiftsUntilItsBottomMeetsThePill() {
        // Its bottom rests at 760 of 800; the pill's top is at 716, so it may lift 44.
        assertEquals(44.0, fitLiftMaxPx(restBottom = 760.0, viewportH = 800.0, insetBottom = 0.0, clearance = 84.0), 0.0)
        // Already above the pill: no lift.
        assertEquals(0.0, fitLiftMaxPx(restBottom = 700.0, viewportH = 800.0, insetBottom = 0.0, clearance = 84.0), 0.0)
        // Pill hidden: no lift, however low the page rests.
        assertEquals(0.0, fitLiftMaxPx(restBottom = 790.0, viewportH = 800.0, insetBottom = 0.0, clearance = 0.0), 0.0)
    }

    @Test fun teardropsAreTwentyDpAcross() {
        assertEquals(10.0, TextHandles.RADIUS_DP, 0.0)
    }

    @Test fun theSelectionTintIsFifteenPercent() {
        assertEquals(38, TextHandles.SELECTION_ALPHA)
        assertEquals(0.15, TextHandles.SELECTION_ALPHA / 255.0, 0.005)
    }

    @Test fun theCaretIsTwoDpWide() {
        assertEquals(2.0, FlowTextController.CARET_WIDTH_DP, 0.0)
    }
}
