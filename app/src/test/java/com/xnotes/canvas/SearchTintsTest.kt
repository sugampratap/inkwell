package com.xnotes.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

/** Find in note on the page: grey washes, the current one stronger and boxed in ink (r2_panel_share_empty .ps-mk). */
class SearchTintsTest {
    @Test fun matchesAreTheMockupsGreys() {
        assertEquals(Math.round(0.09f * 255), SearchTints.FAINT_ALPHA)
        assertEquals(Math.round(0.12f * 255), SearchTints.CURRENT_ALPHA)
        assertEquals(2.0, SearchTints.CURRENT_RING_DP, 0.0)
    }
}
