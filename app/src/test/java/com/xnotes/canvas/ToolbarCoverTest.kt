package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Document
import com.xnotes.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Test

/** A floating toolbar covers an edge of the page view; every page must still be reachable clear of it. */
class ToolbarCoverTest {

    private fun state(pages: Int = 3, verticalScroll: Boolean = true): CanvasState =
        CanvasState(Document.blank(pages), FakeSurfaceFactory(), Palette.DEFAULT).apply {
            this.verticalScroll = verticalScroll
            viewportW = 1000
            viewportH = 1400
            relayout()
        }

    @Test fun theFirstPageOpensBelowATopBar() {
        for (vertical in listOf(true, false)) {
            val st = state(verticalScroll = vertical)
            st.insetTop = 90.0
            st.establishInitialView()
            val top = st.contentToViewport(Pt(0.0, st.pageRects[0].top)).y
            assertEquals(90.0 + CanvasState.TOP_GAP, top, 1e-6)
        }
    }

    @Test fun theLastPageScrollsClearOfABottomBar() {
        val st = state(pages = 6)
        st.insetBottom = 90.0
        st.establishInitialView()
        st.scrollBy(0.0, 1e9)
        val end = st.contentToViewport(Pt(0.0, st.contentH)).y
        assertEquals(st.viewportH - 90.0, end, 1.0)
    }

    @Test fun fitWidthFillsTheWidthBesideASideRail() {
        val st = state()
        st.insetLeft = 90.0
        st.establishInitialView()
        assertEquals(st.clearW, st.contentW * st.zoom, 1e-6)
        assertEquals(90.0, st.contentToViewport(Pt(0.0, 0.0)).x, 1e-6)
    }

    @Test fun theFormatPillLetsTheLastPageScrollClearOfIt() {
        val st = state(pages = 6)
        st.insetBottom = 90.0
        st.establishInitialView()
        val hiddenMax = st.maxScrollY()
        st.bottomReachPx = 200.0 // the pill reaches 110 past the cover
        assertEquals(hiddenMax + 110.0, st.maxScrollY(), 1.0)
        st.scrollBy(0.0, 1e9)
        assertEquals(st.viewportH - 200.0, st.contentToViewport(Pt(0.0, st.contentH)).y, 1.0)
        st.bottomReachPx = 0.0
        assertEquals(hiddenMax, st.maxScrollY(), 0.0)
    }

    @Test fun aPillNoHigherThanTheCoverChangesNothing() {
        val st = state(pages = 6)
        st.insetBottom = 90.0
        st.establishInitialView()
        val hiddenMax = st.maxScrollY()
        st.bottomReachPx = 60.0
        assertEquals(hiddenMax, st.maxScrollY(), 0.0)
    }

    @Test fun aFittingPageLiftsClearOfThePillAndNoFurther() {
        for (vertical in listOf(true, false)) {
            val st = state(pages = 1, verticalScroll = vertical)
            st.setView(0.1, 0.0, 0.0)
            st.clampScroll()
            val rest = st.origin().y
            // Pill hidden: a scroll does not move a page that fits.
            st.scrollBy(0.0, 500.0)
            assertEquals(rest, st.origin().y, 1e-6)
            // The pill's top sits 100 above the content's resting bottom: it may lift exactly that far.
            val restBottom = rest + st.contentH * st.zoom
            st.bottomReachPx = st.viewportH - restBottom + 100.0
            assertEquals(rest, st.origin().y, 1e-6) // showing the pill alone does not lift it
            st.scrollBy(0.0, 40.0)
            assertEquals(rest - 40.0, st.origin().y, 1e-6)
            st.scrollBy(0.0, 1e9)
            assertEquals(rest - 100.0, st.origin().y, 1e-6)
            st.bottomReachPx = 0.0
            assertEquals(rest, st.origin().y, 1e-6)
        }
    }

    @Test fun aShortDocumentCentresInTheClearArea() {
        val st = state(pages = 1)
        st.insetTop = 200.0
        st.setView(0.1, 0.0, 0.0)
        val o = st.origin()
        val h = st.contentH * st.zoom
        assertEquals(200.0 + (st.clearH - h) / 2.0, o.y, 1e-6)
    }
}
