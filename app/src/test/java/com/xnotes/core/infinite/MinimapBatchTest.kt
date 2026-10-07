package com.xnotes.core.infinite

import com.xnotes.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The minimap now draws every marker from one cached batch, laid out from plain numbers. Both
 * have to land on exactly the pixels the per-item path drew: the same mapping to the bit, and the
 * same clip corners for every marker, in the same order.
 */
class MinimapBatchTest {

    private class Case(
        val w: Int,
        val h: Int,
        val insetRight: Double,
        val insetBottom: Double,
        val content: Rect?,
        val scrollX: Double,
        val scrollY: Double,
        val zoom: Double,
    )

    private fun cases(): List<Case> {
        val random = java.util.Random(11)
        val out = ArrayList<Case>()
        repeat(500) {
            val content = when (random.nextInt(4)) {
                0 -> null
                1 -> Rect(random.nextDouble() * 100.0, random.nextDouble() * 100.0, 0.0, 0.0)
                else -> Rect(
                    (random.nextDouble() - 0.5) * 1e5, (random.nextDouble() - 0.5) * 1e5,
                    random.nextDouble() * 4e4, random.nextDouble() * 4e4,
                )
            }
            out += Case(
                800 + random.nextInt(2000), 600 + random.nextInt(1500),
                random.nextDouble() * 120.0, random.nextDouble() * 200.0,
                content,
                (random.nextDouble() - 0.5) * 2e5, (random.nextDouble() - 0.5) * 2e5,
                0.02 + random.nextDouble() * 8.0,
            )
        }
        return out
    }

    private fun bits(v: Double) = v.toRawBits()

    @Test fun theLayoutIsTheRectFormsToTheBit() {
        val m = Minimap.Layout()
        for (c in cases()) {
            m.update(c.w, c.h, c.insetRight, c.insetBottom, c.content, c.scrollX, c.scrollY, c.zoom)
            val panel = Minimap.panel(c.w, c.h, c.insetRight, c.insetBottom)
            val visible = Rect(c.scrollX, c.scrollY, c.w / c.zoom, c.h / c.zoom)
            val extent = Minimap.mappedExtent(c.content, visible)
            assertEquals(bits(panel.x), bits(m.panelX))
            assertEquals(bits(panel.y), bits(m.panelY))
            assertEquals(bits(panel.w), bits(m.panelW))
            assertEquals(bits(panel.h), bits(m.panelH))
            assertEquals(bits(extent.x), bits(m.extentX))
            assertEquals(bits(extent.y), bits(m.extentY))
            assertEquals(bits(extent.w), bits(m.extentW))
            assertEquals(bits(extent.h), bits(m.extentH))
            assertEquals(bits(Minimap.scaleFor(extent, panel)), bits(m.scale))
            val view = Minimap.toPanel(visible, extent, panel)
            assertEquals(bits(view.x), bits(m.viewX))
            assertEquals(bits(view.y), bits(m.viewY))
            assertEquals(bits(view.w), bits(m.viewW))
            assertEquals(bits(view.h), bits(m.viewH))
        }
    }

    /** The clip corners the per-item path uploaded for one marker: x0, y0, x1, y1. */
    private fun perItemCorners(bounds: Rect, extent: Rect, panel: Rect, w: Int, h: Int): FloatArray? {
        val mapped = Minimap.toPanel(bounds, extent, panel)
        val rect = Rect(mapped.x, mapped.y, mapped.w.coerceAtLeast(DOT), mapped.h.coerceAtLeast(DOT))
        if (rect.w <= 0.0 || rect.h <= 0.0) return null
        return floatArrayOf(
            (rect.left / w * 2.0 - 1.0).toFloat(),
            (1.0 - rect.top / h * 2.0).toFloat(),
            (rect.right / w * 2.0 - 1.0).toFloat(),
            (1.0 - rect.bottom / h * 2.0).toFloat(),
        )
    }

    @Test fun everyMarkerIsTheQuadItWasDrawnAsInTheSameOrder() {
        val random = java.util.Random(13)
        val m = Minimap.Layout()
        val dots = Minimap.Dots()
        for (c in cases().take(120)) {
            m.update(c.w, c.h, c.insetRight, c.insetBottom, c.content, c.scrollX, c.scrollY, c.zoom)
            val panel = Minimap.panel(c.w, c.h, c.insetRight, c.insetBottom)
            val extent = Minimap.mappedExtent(c.content, Rect(c.scrollX, c.scrollY, c.w / c.zoom, c.h / c.zoom))
            val items = List(random.nextInt(60)) {
                Rect(
                    (random.nextDouble() - 0.5) * 1e5, (random.nextDouble() - 0.5) * 1e5,
                    random.nextDouble() * (if (random.nextBoolean()) 2.0 else 3e3),
                    random.nextDouble() * (if (random.nextBoolean()) 2.0 else 3e3),
                )
            }
            dots.begin(1, m, c.w, c.h)
            for (b in items) dots.add(b, DOT)
            val expected = items.mapNotNull { perItemCorners(it, extent, panel, c.w, c.h) }
            assertEquals(expected.size * 6, dots.vertexCount)
            for ((i, q) in expected.withIndex()) {
                val (x0, y0, x1, y1) = q.toList()
                // The strip's two triangles: (x0,y0) (x1,y0) (x0,y1) and (x0,y1) (x1,y0) (x1,y1).
                val want = floatArrayOf(x0, y0, x1, y0, x0, y1, x0, y1, x1, y0, x1, y1)
                for (k in want.indices) {
                    assertEquals("marker $i, value $k", want[k].toRawBits(), dots.vertices[12 * i + k].toRawBits())
                }
            }
        }
    }

    @Test fun theBatchIsRebuiltOnlyWhenWhatItShowsHasChanged() {
        val m = Minimap.Layout()
        val dots = Minimap.Dots()
        val content = Rect(0.0, 0.0, 5000.0, 4000.0)
        m.update(1600, 1000, 0.0, 0.0, content, 100.0, 100.0, 1.0)
        assertFalse("a batch never built is not current", dots.isCurrent(3, m, 1600, 1000))
        dots.begin(3, m, 1600, 1000)
        dots.add(Rect(10.0, 10.0, 50.0, 50.0), DOT)
        assertTrue(dots.isCurrent(3, m, 1600, 1000))

        // Panning inside what is drawn moves nothing on the map but its viewport marker.
        m.update(1600, 1000, 0.0, 0.0, content, 400.0, 300.0, 1.0)
        assertTrue(dots.isCurrent(3, m, 1600, 1000))

        assertFalse("new content", dots.isCurrent(4, m, 1600, 1000))
        assertFalse("a resized viewport", dots.isCurrent(3, m, 1600, 900))
        m.update(1600, 1000, 0.0, 0.0, content, -9000.0, 300.0, 1.0)
        assertFalse("a view past the content widens the extent", dots.isCurrent(3, m, 1600, 1000))
        m.update(1600, 1000, 80.0, 0.0, content, 400.0, 300.0, 1.0)
        assertFalse("a toolbar moves the panel", dots.isCurrent(3, m, 1600, 1000))

        val version = dots.version
        dots.begin(4, m, 1600, 1000)
        assertEquals(version + 1, dots.version)
        assertEquals(0, dots.vertexCount)
    }

    companion object {
        /** The scene's MINIMAP_DOT_PX. */
        const val DOT = 1.5
    }
}
