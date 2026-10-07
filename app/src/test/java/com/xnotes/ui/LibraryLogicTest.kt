package com.xnotes.ui

import androidx.compose.ui.geometry.Rect
import com.xnotes.settings.ExplorerSortKey
import com.xnotes.settings.ExplorerView
import com.xnotes.settings.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    // --- the cover shelf (Frame 1: five covers across beside the 256dp sidebar, 24dp gaps) ---

    @Test fun shelfShowsFiveCoversBesideTheSidebarOnTheS8() {
        assertEquals(5, coverColumns(1280, compact = false, TileSize.M))
        assertEquals(6, coverColumns(1280, compact = false, TileSize.S))
        assertEquals(4, coverColumns(1280, compact = false, TileSize.L))
        assertEquals(3, coverColumns(1280, compact = false, TileSize.XL))
    }

    @Test fun shelfKeepsAtLeastTwoColumns() {
        assertEquals(2, coverColumns(412, compact = true, TileSize.M))
        assertEquals(2, coverColumns(800, compact = false, TileSize.M))
        assertEquals(2, coverColumns(360, compact = true, TileSize.XL))
    }

    // --- Sort & view ---

    @Test fun sortMenuListsTheMockupsFieldsInOrder() {
        assertEquals(
            listOf(ExplorerSortKey.MODIFIED, ExplorerSortKey.CREATED, ExplorerSortKey.NAME, ExplorerSortKey.SIZE),
            SORT_MENU_KEYS,
        )
    }

    @Test fun directionOptionsPutTheNaturalDirectionFirst() {
        assertEquals(listOf(true, false), directionOptions(ExplorerSortKey.MODIFIED))
        assertEquals(listOf(true, false), directionOptions(ExplorerSortKey.CREATED))
        assertEquals(listOf(true, false), directionOptions(ExplorerSortKey.SIZE))
        assertEquals(listOf(false, true), directionOptions(ExplorerSortKey.NAME))
    }

    @Test fun pickingANewFieldStartsInItsNaturalDirection() {
        val v = ExplorerView(sortKey = ExplorerSortKey.MODIFIED, descending = false)
        assertEquals(ExplorerView(sortKey = ExplorerSortKey.NAME, descending = false), withSortKey(v, ExplorerSortKey.NAME))
        assertEquals(ExplorerView(sortKey = ExplorerSortKey.SIZE, descending = true), withSortKey(v, ExplorerSortKey.SIZE))
    }

    @Test fun pickingTheFieldInUseKeepsItsDirection() {
        val v = ExplorerView(sortKey = ExplorerSortKey.MODIFIED, descending = false)
        assertSame(v, withSortKey(v, ExplorerSortKey.MODIFIED))
    }

    // --- Continue writing (D6) ---

    @Test fun continueNamesAPageOnlyForAKnownPageOfALongerNote() {
        assertEquals(6, continuePage(6, 12))
        assertNull(continuePage(null, 12))
        assertNull(continuePage(0, 1))
        assertNull(continuePage(12, 12))
        assertNull(continuePage(-1, 12))
    }

    @Test fun progressCountsTheCurrentPage() {
        assertEquals(7f / 12f, continueProgress(6, 12)!!, 1e-6f)   // "Page 7 of 12", 58.3%
        assertEquals(31f / 48f, continueProgress(30, 48)!!, 1e-6f) // "Page 31 of 48", 64.6%
        assertNull(continueProgress(null, 12))
    }

    // --- cover designs (D8) ---

    @Test fun coverDesignIsStableAndSuitsTheCloth() {
        val names = (1..200).map { "Note $it.xnote" }
        for (n in names) {
            assertEquals(coverDesign(n, 0.6f), coverDesign(n, 0.6f))
            assertNotEquals(CoverDesign.STRAP, coverDesign(n, 0.6f))   // gold foil only on dark cloth
            assertNotEquals(CoverDesign.STITCH, coverDesign(n, 0.1f))  // debossing only on light cloth
        }
        assertEquals(setOf(CoverDesign.PLATE, CoverDesign.STITCH), names.map { coverDesign(it, 0.6f) }.toSet())
        assertEquals(setOf(CoverDesign.PLATE, CoverDesign.STRAP), names.map { coverDesign(it, 0.1f) }.toSet())
    }

    @Test fun coverDesignIgnoresTheSuffixAndCase() {
        assertEquals(coverDesign("Weekly.xnote", 0.5f), coverDesign("weekly", 0.5f))
    }

    @Test fun leatherGrainSpansTheWholeRangeAndRepeatsTheSame() {
        val g = leatherGrain(160, 0x1b2L)
        assertEquals(160 * 160, g.size)
        assertEquals(-1f, g.min(), 1e-6f)
        assertEquals(1f, g.max(), 1e-6f)
        assertEquals(g.toList(), leatherGrain(160, 0x1b2L).toList())
    }

    @Test fun leatherGrainEasesBetweenItsLatticePoints() {
        // Smoothstep: the curve is flat at each lattice point, so neighbouring pixels there barely differ,
        // where plain linear blending would step by the same amount at every pixel of a cell.
        val f = valueNoiseEase(0f); val g = valueNoiseEase(1f)
        assertEquals(0f, f, 1e-6f); assertEquals(1f, g, 1e-6f)
        assertEquals(0.5f, valueNoiseEase(0.5f), 1e-6f)
        assertEquals(0.028f, valueNoiseEase(0.1f), 1e-6f)
    }

    // --- the header's search pill ---

    @Test fun pillCentresOnTheHeaderWhenItFits() {
        assertEquals(SearchPillSlot(212f, 600f), pillSlot(1024f, leadEnd = 162f, newStart = 882f, maxW = 600f, minW = 240f, gap = 16f))
    }

    @Test fun pillNarrowsAndMovesRightBesideALongTitle() {
        assertEquals(SearchPillSlot(516f, 350f), pillSlot(1024f, leadEnd = 500f, newStart = 882f, maxW = 600f, minW = 240f, gap = 16f))
    }

    @Test fun pillGivesWayWhenThereIsNoRoom() {
        assertNull(pillSlot(1024f, leadEnd = 700f, newStart = 882f, maxW = 600f, minW = 240f, gap = 16f))
    }

    // --- library -> editor (motion e) ---

    @Test fun openingStartsScaledDownOnTheCoverAndEndsOnTheLanding() {
        val from = Rect(100f, 200f, 272f, 429f)
        val to = Rect(384f, 56f, 896f, 776f)
        val m = pageOpen(from, to)
        // One scale both ways: the page keeps its shape, its width matching the cover's at the start.
        assertEquals(172f / 512f, m.scale(0f), 1e-6f)
        assertEquals(-284f, m.dx(0f), 1e-4f)
        assertEquals(144f, m.dy(0f), 1e-4f)
        // The layer's top corners land on the cover's: (0, 0) and (512, 0) of a layer laid out at the landing.
        assertEquals(100f, to.left + m.dx(0f), 1e-4f)
        assertEquals(272f, to.left + m.dx(0f) + to.width * m.scale(0f), 1e-3f)
        assertEquals(200f, to.top + m.dy(0f), 1e-4f)
        // At rest at the end: the layer is laid out where the page lands.
        assertEquals(1f, m.scale(1f), 1e-6f)
        assertEquals(0f, m.dx(1f), 1e-6f)
        assertEquals(0f, m.dy(1f), 1e-6f)
        assertEquals((172f / 512f + 1f) / 2f, m.scale(0.5f), 1e-6f)
    }

    @Test fun openingShowsOnlyAsMuchPageAsTheSourceHeldThenAllOfIt() {
        val to = Rect(384f, 56f, 896f, 776f)
        // A cover a touch squatter than the page: the page's foot stays hidden until it grows.
        val cover = pageOpen(Rect(100f, 200f, 272f, 429f), to)
        assertEquals(229f / (172f / 512f), cover.reveal(0f), 1e-2f)
        assertEquals(720f, cover.reveal(1f), 1e-4f)
        // A wide Continue card shows the page's top only.
        val card = pageOpen(Rect(32f, 100f, 432f, 228f), to)
        assertEquals(128f / (400f / 512f), card.reveal(0f), 1e-2f)
        // A source taller than the page never reveals more than the page.
        val tall = pageOpen(Rect(0f, 0f, 100f, 400f), to)
        assertEquals(720f, tall.reveal(0f), 1e-4f)
    }

    @Test fun anUprightPageLandsWholeUnderTheHeaderCentred() {
        val r = pageLanding(Rect(0f, 0f, 1280f, 800f), 100f / 141f, top = 56f, margin = 24f)
        assertEquals(720f, r.height, 1e-3f)
        assertEquals(720f * 100f / 141f, r.width, 1e-3f)
        assertEquals(56f, r.top, 1e-3f)
        assertEquals((1280f - r.width) / 2f, r.left, 1e-3f)
    }

    @Test fun aWidePageOnAnUprightScreenLandsWidthFirst() {
        val r = pageLanding(Rect(0f, 0f, 800f, 1280f), 1.41f, top = 56f, margin = 24f)
        assertEquals(752f, r.width, 1e-3f)
        assertEquals(752f / 1.41f, r.height, 1e-3f)
    }

    // --- joined meta lines ---

    @Test fun dotJoinFoldsThePartsThroughTheTwoPartPattern() {
        val p = "%1\$s · %2\$s"
        assertEquals("", dotJoin(p, emptyList()))
        assertEquals("12 pages", dotJoin(p, listOf("12 pages")))
        assertEquals("12 pages · Today", dotJoin(p, listOf("12 pages", "Today")))
        assertEquals("PDF · Page 7 of 12 · 4 min ago", dotJoin(p, listOf("PDF", "Page 7 of 12", "4 min ago")))
        // A language's own pattern decides the order.
        assertEquals("b ← a", dotJoin("%2\$s ← %1\$s", listOf("a", "b")))
    }

    // --- cover titles (D8: .deboss and .foil set a title in two lines) ---

    @Test fun coverTitleSplitsAtTheFirstSpace() {
        assertEquals(CoverTitle("Q4", "Roadmap"), coverTitle("Q4 Roadmap"))
        assertEquals(CoverTitle("Weekly", "2026"), coverTitle("Weekly 2026"))
        assertEquals(CoverTitle("Meeting", "notes for May"), coverTitle("  Meeting   notes for May "))
        assertEquals(CoverTitle("Journal", null), coverTitle("Journal"))
    }

    // --- Continue writing images ---

    @Test fun aContinueImageGoesStaleOnceTheNoteIsSavedAgain() {
        val uri = "content://tree/doc%2FA.xnote"
        assertEquals("$uri#p6@100", continueKey(uri, 6, 100L))
        assertTrue(isStaleContinueKey("$uri#p6@100", uri, 200L))
        assertTrue(isStaleContinueKey("$uri#p2@100", uri, 200L))
        assertFalse(isStaleContinueKey("$uri#p6@200", uri, 200L))
        // Other notes, a longer uri sharing the prefix, and the first-page thumbnails are left alone.
        assertFalse(isStaleContinueKey("content://tree/doc%2FB.xnote#p6@100", uri, 200L))
        assertFalse(isStaleContinueKey("${uri}x#p6@100", uri, 200L))
        assertFalse(isStaleContinueKey(uri, uri, 200L))
        assertFalse(isStaleContinueKey("$uri#page", uri, 200L))
    }
}
