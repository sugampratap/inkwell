package com.xnotes.canvas

import com.xnotes.core.FakeRasterSurface
import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Stroke
import com.xnotes.core.pal.Renderer
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import com.xnotes.ui.theme.Palette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page's ink cache is rebuilt off the UI thread from a snapshot of its items, while erasing and
 * drawing patch the surface on screen in place. A build that was already running when the page was
 * patched holds the page as it was before, and must never be published over the patched one: an
 * erased stroke would come back on screen while the note no longer has it, so it could not be
 * erased again. A cold start (the first launch after an update) is when those builds are slow
 * enough to be overtaken.
 */
class InkCacheRaceTest {

    private val queued = ArrayList<() -> Unit>()

    private fun runPending() {
        while (queued.isNotEmpty()) {
            val work = queued.toList()
            queued.clear()
            work.forEach { it() }
        }
    }

    /** A stroke that remembers every surface painter it was drawn into. */
    private class Probe(val stroke: Stroke) : CanvasItem by stroke {
        val paintedOn = ArrayList<Renderer>()
        override fun paint(r: Renderer) {
            paintedOn += r
            stroke.paint(r)
        }
    }

    private fun probe(x: Double, y: Double) = Probe(
        Stroke(
            Tool.PEN,
            ToolDefaults.configFor(Tool.PEN),
            listOf(Sample(x, y, 1.0), Sample(x + 10, y + 10, 1.0), Sample(x + 20, y, 1.0)),
        ),
    )

    private fun state(page: Page) = CanvasState(Document(mutableListOf(page)), FakeSurfaceFactory(), Palette.DEFAULT).apply {
        viewportW = 800
        viewportH = 1000
        relayout()
        runAsync = { queued += it }
    }

    private fun Probe.isOn(entry: CacheEntry) = paintedOn.any { it === (entry.surface as FakeRasterSurface).painter }

    @Test fun anEraseDuringARebuildIsNotUndoneWhenTheBuildLands() {
        val a = probe(20.0, 20.0)
        val b = probe(120.0, 120.0)
        val page = Page(200.0, 200.0, mutableListOf(a, b))
        val st = state(page)
        st.cacheFor(page)
        // A new resolution (the initial fit, a zoom) starts a rebuild from the page as it is.
        st.zoom = 2.0
        st.cacheForOrSchedule(page)
        // The eraser takes A off while that build is still running, patching the old surface.
        page.items.remove(a)
        assertTrue(st.repairRegion(page, a.paintBounds()))
        runPending()
        // The next frame: whatever is shown, or built for it, must not hold A.
        var shown = st.cacheForOrSchedule(page)
        runPending()
        shown = st.cacheForOrSchedule(page)
        assertNotNull(shown)
        assertFalse("an erased stroke came back on screen", a.isOn(shown!!))
        assertTrue(b.isOn(shown))
    }

    @Test fun aStrokeDrawnDuringARebuildIsNotDroppedWhenTheBuildLands() {
        val a = probe(20.0, 20.0)
        val page = Page(200.0, 200.0, mutableListOf<CanvasItem>(a))
        val st = state(page)
        st.cacheFor(page)
        st.zoom = 2.0
        st.cacheForOrSchedule(page) // a rebuild at 2x, from [a]
        st.zoom = 1.0 // back before it lands: the 1x surface is current again
        val c = probe(60.0, 60.0)
        page.items.add(c)
        st.appendToCache(page, c)
        runPending()
        var shown = st.cacheForOrSchedule(page)
        runPending()
        shown = st.cacheForOrSchedule(page)
        assertNotNull(shown)
        assertTrue("a stroke drawn during a rebuild vanished", c.isOn(shown!!))
    }
}
