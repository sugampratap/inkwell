package com.xnotes.ui

import com.xnotes.core.model.PageEdge
import com.xnotes.core.model.PageMargins
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperMathsTest {

    private val a4 = 210.0 to 297.0

    @Test fun anAllPagesEdgeLeftUnsetIsNone() {
        assertEquals(0.0, PaperMaths.edgeValue(true, PageMargins(), PageMargins(left = 0.3), PageEdge.LEFT), 0.0)
    }

    @Test fun aPageEdgeFollowsTheNoteUntilItHasItsOwn() {
        val doc = PageMargins(left = 0.12)
        assertEquals(0.12, PaperMaths.edgeValue(false, doc, PageMargins(), PageEdge.LEFT), 1e-9)
        assertEquals(0.4, PaperMaths.edgeValue(false, doc, PageMargins(left = 0.4), PageEdge.LEFT), 1e-9)
        assertEquals(0.0, PaperMaths.edgeValue(false, doc, PageMargins(), PageEdge.TOP), 0.0)
    }

    @Test fun marginsAddPaperAndTheTemplateSpansIt() {
        val m = PaperMaths.effectiveMargins(true, PageMargins(left = 0.5, bottom = 0.1), PageMargins())
        val (w, h) = PaperMaths.withMargins(a4, m)
        assertEquals(315.0, w, 1e-9)
        assertEquals(326.7, h, 1e-9)
        assertTrue(m.any)
        assertFalse(EdgeFractions().any)
    }

    @Test fun aPageFitsItsBoxAtItsOwnProportions() {
        val (w, h) = PaperMaths.fit(a4, 176f, 252f)
        assertEquals(176f, w, 0.01f)
        assertEquals(248.91f, h, 0.01f)
        // Landscape: full width, half height.
        val (lw, lh) = PaperMaths.fit(297.0 to 210.0, 176f, 252f)
        assertEquals(176f, lw, 0.01f)
        assertEquals(124.44f, lh, 0.01f)
        // Too tall for the box: the height wins.
        val (tw, th) = PaperMaths.fit(100.0 to 400.0, 176f, 252f)
        assertEquals(63f, tw, 0.01f)
        assertEquals(252f, th, 0.01f)
    }

    @Test fun theGuideSitsOnTheChosenEdgeOfTheEnlargedPage() {
        val m = EdgeFractions(left = 0.12, right = 0.2)
        assertEquals(0.12 / 1.32, PaperMaths.guideFraction(PageEdge.LEFT, m), 1e-9)
        assertEquals(1.12 / 1.32, PaperMaths.guideFraction(PageEdge.RIGHT, m), 1e-9)
        assertEquals(0.0, PaperMaths.guideFraction(PageEdge.TOP, m), 1e-9)
        assertEquals(1.0, PaperMaths.guideFraction(PageEdge.BOTTOM, m), 1e-9)
        assertTrue(PaperMaths.isVertical(PageEdge.RIGHT))
        assertFalse(PaperMaths.isVertical(PageEdge.TOP))
    }

    @Test fun darkPaperTakesALightGuide() {
        assertTrue(PaperMaths.guideIsLight(Rgba(0x2B, 0x2D, 0x31)))
        assertFalse(PaperMaths.guideIsLight(Rgba(0xFB, 0xF6, 0xE9)))
    }

    @Test fun lengthsSnapToTheTemplatesStepOrToTenthsAndHalves() {
        assertEquals(7.5, PaperMaths.snapLength(7.26, null, 10.0), 1e-9)
        assertEquals(7.3, PaperMaths.snapLength(7.26, null, 4.0), 1e-9)
        assertEquals(7.25, PaperMaths.snapLength(7.26, 0.25, 10.0), 1e-9)
    }

    @Test fun millimetresReadWithoutATrailingZero() {
        assertEquals("7", PaperMaths.formatMm(7.0))
        // The decimal separator is the device's, as today.
        assertEquals("%.1f".format(7.5), PaperMaths.formatMm(7.46))
        assertEquals(12, PaperMaths.percent(0.12))
    }

    @Test fun aTranslucentRulingIsSeenOverThePaper() {
        assertEquals(Rgba(229, 229, 229), PaperMaths.over(Rgba(150, 150, 150, 64), Rgba(255, 255, 255)))
        assertEquals(128, PaperMaths.lifted(Rgba(0, 0, 0, 64)).a)
        assertTrue(PaperMaths.sameRgb(Rgba(1, 2, 3, 10), Rgba(1, 2, 3, 200)))
    }

    @Test fun theShownTemplateJoinsTheFrontOfTheRow() {
        val base = listOf("lines", "dots", "grid", "cornell", "staves")
        assertEquals(listOf("seyes", "lines", "dots", "grid", "cornell"), PaperMaths.quickRow(base, "seyes"))
        assertEquals(base, PaperMaths.quickRow(base, "dots"))
        assertEquals(base, PaperMaths.quickRow(base, null))
    }

    @Test fun aPageResolvesOverTheNote() {
        val doc = PageStyle(template = "lines", spacing = 40.0, pageColor = Rgba(1, 2, 3))
        val r = PaperMaths.resolve(PageStyle(spacing = 30.0), doc)
        assertEquals("lines", r.template)
        assertEquals(30.0, r.spacing!!, 1e-9)
        assertEquals(Rgba(1, 2, 3), r.pageColor)
        // A page with its own, unrelated template does not take the note's spacing.
        assertNull(PaperMaths.resolve(PageStyle(template = "xtemplate.examples.cornell"), doc).spacing)
        // Nothing below: the style is returned as it is.
        assertEquals(doc, PaperMaths.resolve(doc, null))
    }
}
