package com.xnotes.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Back does on a progress sheet: cancel what can be cancelled, else send it to the background once offered. */
class ProgressSheetBackTest {

    @Test fun aCancellableSheetCancels() {
        assertEquals(ProgressSheetBack.CANCEL, ProgressSheetBack.of(cancellable = true, backgroundOffered = false))
        // Keep in background is never offered beside Cancel; Cancel still wins if it were.
        assertEquals(ProgressSheetBack.CANCEL, ProgressSheetBack.of(cancellable = true, backgroundOffered = true))
    }

    @Test fun workThatCannotBeCancelledHoldsBackUntilTheOfferShows() {
        assertEquals(ProgressSheetBack.NONE, ProgressSheetBack.of(cancellable = false, backgroundOffered = false))
    }

    @Test fun onceOfferedBackKeepsTheWorkGoingInTheBackground() {
        assertEquals(ProgressSheetBack.BACKGROUND, ProgressSheetBack.of(cancellable = false, backgroundOffered = true))
    }

    @Test fun theOfferWaitsAboutFifteenSeconds() {
        assertEquals(15_000L, PROGRESS_BACKGROUND_AFTER_MS)
        assertTrue(ProgressSheetBack.BACKGROUND.closes && ProgressSheetBack.CANCEL.closes && !ProgressSheetBack.NONE.closes)
    }
}
