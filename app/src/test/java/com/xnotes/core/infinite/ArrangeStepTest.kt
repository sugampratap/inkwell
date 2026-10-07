package com.xnotes.core.infinite

import com.xnotes.canvas.HandleId
import com.xnotes.canvas.ResizeMath
import com.xnotes.core.geometry.Obb
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.model.TextItem
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.LassoFilter
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Bring forward / send backward, uniform edge resizing for pictures, and the lasso filter. */
class ArrangeStepTest {

    private fun stroke() = Stroke(Tool.PEN, ToolConfig(), mutableListOf(Sample(0.0, 0.0, 1.0), Sample(10.0, 10.0, 1.0)))

    private fun assertOrder(expected: List<CanvasItem>, actual: List<CanvasItem>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertSame("at $i", expected[i], actual[i])
    }

    @Test fun forwardStepsOverTheNextItem() {
        val a = stroke(); val b = stroke(); val c = stroke(); val d = stroke()
        assertOrder(listOf(a, c, b, d), bringForwardOrder(listOf(a, b, c, d), listOf(b)))
        assertOrder(listOf(b, a, c, d), sendBackwardOrder(listOf(a, b, c, d), listOf(b)))
    }

    @Test fun stepsPassOverWhatDoesNotOverlap() {
        val a = stroke(); val b = stroke(); val far = stroke(); val near = stroke()
        val overlaps = { it: CanvasItem -> it === near || it === a }
        assertOrder(listOf(a, far, near, b), bringForwardOrder(listOf(a, b, far, near), listOf(b), overlaps))
        assertOrder(listOf(b, a, far, near), sendBackwardOrder(listOf(a, far, b, near), listOf(b), overlaps))
    }

    @Test fun nothingAboveOrBelowLeavesTheOrderAlone() {
        val a = stroke(); val b = stroke()
        val list = listOf(a, b)
        assertSame(list, bringForwardOrder(list, listOf(b)))
        assertSame(list, sendBackwardOrder(list, listOf(a)))
    }

    @Test fun aSpreadSelectionGathersAboveTheNextItem() {
        val a = stroke(); val b = stroke(); val c = stroke(); val d = stroke()
        val out = bringForwardOrder(listOf(a, b, c, d), listOf(a, c))
        // The selection (a, c) lands, in list order, just above d, the first item over its top.
        assertOrder(listOf(b, d, a, c), out)
    }

    @Test fun pictureEdgesScaleTheWholeBoxAboutTheOppositeEdge() {
        val obb = Obb(Pt(50.0, 25.0), 50.0, 25.0, 0.0)
        val r = ResizeMath.obbResize(obb, HandleId.R, Pt(200.0, 25.0), uniformEdges = true)
        assertEquals(100.0, r.obb.halfW, 1e-9)
        assertEquals(50.0, r.obb.halfH, 1e-9)
        // Anchored at the left edge's middle: the left edge stays, the middle stays level.
        assertEquals(0.0, r.obb.center.x - r.obb.halfW, 1e-9)
        assertEquals(25.0, r.obb.center.y, 1e-9)
        val free = ResizeMath.obbResize(obb, HandleId.R, Pt(200.0, 25.0))
        assertEquals(25.0, free.obb.halfH, 1e-9)
    }

    @Test fun lassoFilterPicksKinds() {
        val s = stroke()
        val img = ImageItem(ImageData(File("x"), 1, 1), Rect(0.0, 0.0, 1.0, 1.0))
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(1.0, 1.0), Rgba(0, 0, 0, 255))
        val text = TextItem(Pt(0.0, 0.0), text = "x", measurer = com.xnotes.core.FakeTextMeasurer())
        assertTrue(LassoFilter.ALL.accepts(img))
        assertTrue(LassoFilter.HANDWRITING.accepts(s))
        assertFalse(LassoFilter.HANDWRITING.accepts(img))
        assertTrue(LassoFilter.IMAGES.accepts(img))
        assertFalse(LassoFilter.IMAGES.accepts(shape))
        assertTrue(LassoFilter.SHAPES.accepts(shape))
        assertTrue(LassoFilter.TEXT.accepts(text))
        assertFalse(LassoFilter.TEXT.accepts(s))
        assertEquals(LassoFilter.ALL, LassoFilter.fromId("nonsense"))
        assertEquals(LassoFilter.IMAGES, LassoFilter.fromId("images"))
    }
}
