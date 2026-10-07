package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The header capsule's lit states (mockup §1.4, defaults row 2). */
class NoteHeaderLogicTest {

    private fun lit(
        panelOpen: Boolean = false,
        searchTab: Boolean = false,
        bookmarked: Boolean = false,
        setupOpen: Boolean = false,
        viewOpen: Boolean = false,
        insertOpen: Boolean = false,
        recording: Boolean = false,
    ) = headerLit(panelOpen, searchTab, bookmarked, setupOpen, viewOpen, insertOpen, recording)

    @Test fun nothingIsLitAtRest() {
        assertEquals(HeaderLit(pages = false, find = false, bookmark = false, setup = false, view = false, insert = false), lit())
    }

    @Test fun pagesStaysLitWhileThePanelIsOpenOnAnyTab() {
        assertTrue(lit(panelOpen = true).pages)
        assertTrue(lit(panelOpen = true, searchTab = true).pages)
    }

    @Test fun findLightsOnlyWhileThePanelIsOpenOnSearch() {
        assertTrue(lit(panelOpen = true, searchTab = true).find)
        assertFalse(lit(panelOpen = true, searchTab = false).find)
        // The panel remembers its Search tab while closed; a closed panel lights nothing.
        assertFalse(lit(panelOpen = false, searchTab = true).find)
    }

    @Test fun bookmarkFollowsThisPagesMark() {
        assertTrue(lit(bookmarked = true).bookmark)
        assertFalse(lit(bookmarked = false).bookmark)
    }

    @Test fun insertLightsWhileItsCardIsUpOrARecordingRuns() {
        assertTrue(lit(insertOpen = true).insert)
        assertTrue(lit(recording = true).insert)
        assertFalse(lit().insert)
    }

    @Test fun pageSetupAndViewLightOnlyWhileTheirSheetOrCardIsUp() {
        assertTrue(lit(setupOpen = true).setup)
        assertTrue(lit(viewOpen = true).view)
        val other = lit(setupOpen = true)
        assertFalse(other.view)
        assertFalse(other.pages)
    }

    // --- the header's fit in a narrow pane (review I3) ---

    /** What the header row offers the counter and capsule in a [pane] dp wide bar: less its padding, Back and Back's gap. */
    private fun offered(pane: Int) = pane - HEADER_PADDING_DP - HEADER_BUTTON_DP - HEADER_GAP_DP

    private fun room(pane: Int, close: Boolean, titleMin: Int = HEADER_TITLE_MIN_NOTE_DP) =
        headerToolsRoom(offered(pane), titleMin, HEADER_GAP_DP, if (close) HEADER_BUTTON_DP else null, HEADER_CAPSULE_END_DP)

    /** The paged counter and capsule at full width: "7 / 12" (52), 14 dp, then 9 buttons, 3 rules (9 each) and 2 + 2. */
    private val fullNoteTools = 52 + 14 + 9 * HEADER_BUTTON_DP + 3 * 9 + 4

    /** The capsule's pinned end: the rule before ⋯ (9), ⋯ (44) and the 2 dp end padding. */
    private val capsuleEnd = 9 + HEADER_BUTTON_DP + 2

    /** The narrowest pane in which the paged title keeps its whole least beside ⋯ and Close pane. */
    private val titleLeastFrom = HEADER_PADDING_DP + HEADER_BUTTON_DP + 4 * HEADER_GAP_DP + HEADER_TITLE_MIN_NOTE_DP + capsuleEnd + HEADER_BUTTON_DP

    @Test fun closePaneKeepsItsFullWidthInEveryPaneFrom320() {
        for (pane in 320..1280) {
            for (tools in listOf(0, room(pane, close = true) / 2, room(pane, close = true))) {
                // The row places Back, the tools (any width up to the room), then Close with what is left after a gap.
                val leftForClose = offered(pane) - tools - HEADER_GAP_DP
                assertTrue("pane $pane", leftForClose >= HEADER_BUTTON_DP)
                // The title, weighted, takes the rest: never under its least once ⋯ has its own.
                val title = leftForClose - HEADER_BUTTON_DP - HEADER_GAP_DP
                if (pane >= titleLeastFrom) assertTrue("pane $pane", title >= HEADER_TITLE_MIN_NOTE_DP)
                // Below that the title gives way to ⋯, but still shows a few letters beside the save status.
                assertTrue("pane $pane", title >= HEADER_SAVE_STATUS_DP + HEADER_SAVE_GAP_DP + HEADER_TITLE_PADDING_DP + 56)
            }
        }
    }

    @Test fun theCanvasTitleKeepsItsLeastToo() {
        for (pane in 320..1280) {
            val title = offered(pane) - room(pane, close = true, titleMin = HEADER_TITLE_MIN_CANVAS_DP) - 2 * HEADER_GAP_DP - HEADER_BUTTON_DP
            assertTrue("pane $pane", title >= HEADER_TITLE_MIN_CANVAS_DP)
        }
    }

    @Test fun theCapsulesPinnedEndFitsInEveryPaneFrom320() {
        // ⋯ never scrolls away, even in the narrowest pane, with Close pane beside it.
        for (pane in 320..1280) assertTrue("pane $pane", room(pane, close = true) >= capsuleEnd)
        assertEquals(capsuleEnd, HEADER_CAPSULE_END_DP)
    }

    @Test fun theWholeCapsuleShowsFrom779WithClosePaneAnd729Without() {
        assertTrue(room(779, close = true) >= fullNoteTools)
        assertTrue(room(778, close = true) < fullNoteTools)
        assertTrue(room(729, close = false) >= fullNoteTools)
        assertTrue(room(728, close = false) < fullNoteTools)
    }

    @Test fun aHalfSplitTabS8PaneScrollsOnlyTheLastTools() {
        // 1280 dp landscape split 50/50: 640 dp. The tools scroll by under about three buttons' worth; ⋯ stays.
        val short = fullNoteTools - room(640, close = true)
        assertTrue("short by $short", short in 2 * HEADER_BUTTON_DP until 4 * HEADER_BUTTON_DP)
    }

    @Test fun aHalfSplitTabS8PaneShowsAReadablePartOfTheNameBesideTheSaveStatus() {
        // The title, weighted, takes what the full-width tools and Close pane leave it.
        val title = offered(640) - room(640, close = true) - 2 * HEADER_GAP_DP - HEADER_BUTTON_DP
        assertEquals(HEADER_TITLE_MIN_NOTE_DP, title)
        val name = title - HEADER_SAVE_STATUS_DP - HEADER_SAVE_GAP_DP - HEADER_TITLE_PADDING_DP
        assertTrue("name $name dp", name >= 80)
    }

    @Test fun theCanvasNameGetsTheSameReadableLeast() {
        assertEquals(HEADER_TITLE_TEXT_MIN_DP + HEADER_TITLE_PADDING_DP, HEADER_TITLE_MIN_CANVAS_DP)
        assertTrue(HEADER_TITLE_TEXT_MIN_DP >= 80)
    }

    @Test fun theRoomIsNeverNegative() {
        assertEquals(0, headerToolsRoom(available = 40, titleMin = HEADER_TITLE_MIN_NOTE_DP, gap = HEADER_GAP_DP, closeButton = HEADER_BUTTON_DP))
    }

    @Test fun withoutClosePaneTheToolsTakeItsPlaceAndGap() {
        assertEquals(HEADER_BUTTON_DP + HEADER_GAP_DP, room(800, close = false) - room(800, close = true))
    }
}
