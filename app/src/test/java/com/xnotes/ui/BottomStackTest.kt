package com.xnotes.ui

import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** The bottom edge (round-3 defaults, Shared row 5; TX 801, AU 44, AU 109). */
class BottomStackTest {

    @Test fun theFormatPillSits20UpOr10OverAKeyboardAndClearsABottomBar() {
        assertEquals(20, formatPillBottomDp(imeUp = false, coverBottomDp = 0))
        assertEquals(10, formatPillBottomDp(imeUp = true, coverBottomDp = 0))
        assertEquals(70, formatPillBottomDp(imeUp = false, coverBottomDp = 60))
    }

    @Test fun thePlayerSits16UpAlone() {
        assertEquals(16, playerBottomDp(formatPillShown = false, imeUp = false, coverBottomDp = 0))
        assertEquals(76, playerBottomDp(formatPillShown = false, imeUp = false, coverBottomDp = 60))
    }

    @Test fun thePlayerSits10OverTheFormatPill() {
        assertEquals(20 + 64 + 10, playerBottomDp(formatPillShown = true, imeUp = false, coverBottomDp = 0))
        assertEquals(10 + 64 + 10, playerBottomDp(formatPillShown = true, imeUp = true, coverBottomDp = 0))
    }

    @Test fun aToastRestsAt30AndRises16OverTheHighestPill() {
        assertEquals(30, toastBottomDp(playerBottomDp = null, formatPillBottomDp = null))
        assertEquals(96, toastBottomDp(playerBottomDp = 16, formatPillBottomDp = null))
        assertEquals(100, toastBottomDp(playerBottomDp = null, formatPillBottomDp = 20))
        assertEquals(94 + 64 + 16, toastBottomDp(playerBottomDp = 94, formatPillBottomDp = 20))
    }

    @Test fun onlyAFloatingBottomBarCoversTheBottomEdge() {
        // A regular floating bottom bar: 52 thick + 8 from the edge, as ToolbarAround covers.
        assertEquals(68, bottomCoverDp(ToolbarLook(ToolbarPosition.BOTTOM, ToolbarSize.REGULAR, floating = true)))
        assertEquals(
            floatingCover(ToolbarLook(ToolbarPosition.BOTTOM, ToolbarSize.COMPACT, floating = true)).value.toInt(),
            bottomCoverDp(ToolbarLook(ToolbarPosition.BOTTOM, ToolbarSize.COMPACT, floating = true)),
        )
        assertEquals(0, bottomCoverDp(ToolbarLook(ToolbarPosition.BOTTOM, ToolbarSize.REGULAR, floating = false)))
        for (p in listOf(ToolbarPosition.TOP, ToolbarPosition.LEFT, ToolbarPosition.RIGHT)) {
            assertEquals(0, bottomCoverDp(ToolbarLook(p, ToolbarSize.REGULAR, floating = true)))
        }
    }
}
