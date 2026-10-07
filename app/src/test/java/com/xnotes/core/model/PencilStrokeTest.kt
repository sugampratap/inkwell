package com.xnotes.core.model

import com.xnotes.core.FakeRenderer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.infinite.InkPass
import com.xnotes.core.infinite.ItemMesher
import com.xnotes.core.pal.Renderer
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PencilStrokeTest {

    /** Records every graphite pass; everything else goes to a [FakeRenderer]. */
    private class GrainRecorder(val base: FakeRenderer = FakeRenderer()) : Renderer by base {
        class Pass(val count: Int, val color: Rgba, val radii: FloatArray, val centers: FloatArray)

        val passes = ArrayList<Pass>()

        override fun fillGrainRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
            passes += Pass(count, color, radii.copyOfRange(from, from + count), centers.copyOfRange(2 * from, 2 * (from + count)))
        }

        override fun fillDiskRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) =
            base.fillDiskRibbon(centers, radii, from, count, color)
    }

    private fun path(count: Int, pressure: (Int) -> Double): List<Sample> = (0 until count).map { i ->
        Sample(20.0 + i * 1.7, 40.0 + sin(i * 0.2) * 12.0, pressure(i), i * 6.0)
    }

    private fun pencil(samples: List<Sample>, live: Boolean = false): Stroke {
        val s = Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL))
        if (live) {
            s.finished = false
            for (p in samples) s.addSample(p)
        } else {
            s.setSamples(samples)
        }
        return s
    }

    @Test fun thePencilIsAThinGraphitePen() {
        val c = ToolDefaults.configFor(Tool.PENCIL)
        assertTrue(Tool.PENCIL.isPen && Tool.PENCIL.isStroke)
        assertTrue(c.grain)
        assertTrue(c.pressureEnabled)
        assertTrue(c.baseWidth < ToolDefaults.configFor(Tool.PEN).baseWidth)
        for (t in Tool.entries.filter { it != Tool.PENCIL }) assertFalse("$t", ToolDefaults.configFor(t).grain)
    }

    @Test fun aPressedStrokeLaysTheOuterPassAndThenTheCore() {
        val s = pencil(path(40) { 0.95 })
        val r = GrainRecorder()
        s.paint(r)
        assertEquals(2, r.passes.size)
        val (outer, core) = r.passes
        assertEquals((255 * Graphite.OUTER_ALPHA).toInt(), outer.color.a)
        assertEquals((255 * Graphite.CORE_ALPHA).toInt(), core.color.a)
        assertEquals(s.config.rgba.withAlpha(255), outer.color.withAlpha(255))
        assertArrayEquals(s.geometry().halfWidths, outer.radii, 0f)
        // The core runs inside the stroke, never out to its edge.
        for (i in 0 until core.count) assertTrue(core.radii[i] <= outer.radii[i] * Graphite.CORE_MAX + 1e-6f)
        assertTrue(core.radii.any { it > 0f })
        // Graphite never goes through the solid ribbon.
        assertTrue(r.base.ribbonRuns.isEmpty())
    }

    @Test fun aLightStrokeIsTheOuterPassAlone() {
        val r = GrainRecorder()
        pencil(path(40) { 0.05 }).paint(r)
        assertEquals(1, r.passes.size)
    }

    @Test fun theLiveStrokePaintsWhatItsGeometryHolds() {
        val samples = path(60) { i -> 0.2 + 0.7 * i / 59.0 }
        val live = pencil(samples, live = true)
        assertNotNull(live.wetRibbon)
        val r = GrainRecorder()
        live.paint(r)
        val g = live.geometry()
        assertEquals(2, r.passes.size)
        assertEquals(g.pointCount, r.passes[0].count)
        assertArrayEquals(g.halfWidths, r.passes[0].radii, 0f)
        assertArrayEquals(g.centerline, r.passes[0].centers, 0f)
        // The core the live path presses is the core the committed path (and the GL mesher) reads.
        assertArrayEquals(live.graphiteCore(g), r.passes[1].radii, 0f)
        // Painting again reuses its buffer and paints the same.
        val again = GrainRecorder()
        live.paint(again)
        assertArrayEquals(r.passes[1].radii, again.passes[1].radii, 0f)
    }

    @Test fun graphiteIsNeverWetCachedButOtherInkStillIs() {
        assertFalse(pencil(path(10) { 0.5 }).wetCacheable)
        val pen = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), path(10) { 0.5 })
        assertTrue(pen.wetCacheable)
        val r = GrainRecorder()
        pen.paint(r)
        assertTrue(r.passes.isEmpty())
        assertEquals(1, r.base.ribbonRuns.size)
    }

    @Test fun theCoreIsWorkedOutOncePerGeometry() {
        val s = pencil(path(30) { 0.9 })
        val g = s.geometry()
        val a = s.graphiteCore(g)
        assertTrue(a === s.graphiteCore(g))
        assertNull(pencil(path(30) { 0.0 }).let { it.graphiteCore(it.geometry()) })
    }

    @Test fun aPencilStrokeHitTestsLikeAnyOther() {
        val s = pencil(path(30) { 0.6 })
        val c = s.geometry()
        val mid = Pt(c.cx(15), c.cy(15))
        assertTrue(s.contains(mid))
        assertTrue(s.intersectsCircle(mid.x, mid.y, 2.0))
        assertFalse(s.contains(Pt(mid.x, mid.y + 50.0)))
        val parts = s.erasedBy(s.xAt(15), s.yAt(15), 3.0)
        assertNotNull(parts)
        assertTrue(parts!!.all { it.tool == Tool.PENCIL && it.config.grain })
    }

    @Test fun theCanvasMeshesThePencilAsTwoGrainPasses() {
        val s = pencil(path(40) { 0.95 })
        assertEquals(InkPass.GRAPHITE, ItemMesher.passFor(s))
        val meshed = ItemMesher.mesh(s)!!
        assertEquals(2, meshed.parts.size)
        assertTrue(meshed.parts.all { it.pass == InkPass.GRAPHITE })
        assertEquals((255 * Graphite.OUTER_ALPHA).toInt(), meshed.parts[0].color.a)
        assertEquals((255 * Graphite.CORE_ALPHA).toInt(), meshed.parts[1].color.a)
        assertTrue(meshed.parts[1].mesh.triangleCount > 0)
        // A light one has no core to mesh.
        assertEquals(1, ItemMesher.mesh(pencil(path(40) { 0.05 }))!!.parts.size)
        // Every other pen is untouched.
        assertEquals(InkPass.OPAQUE, ItemMesher.passFor(Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), path(5) { 0.5 })))
    }
}
