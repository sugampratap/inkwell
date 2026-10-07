package com.xnotes.core.model

import com.xnotes.canvas.HandleId
import com.xnotes.core.FakeRenderer
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.infinite.CanvasSelection
import com.xnotes.core.infinite.InfiniteDocument
import com.xnotes.core.infinite.InkPass
import com.xnotes.core.infinite.ItemMesher
import com.xnotes.core.pal.Renderer
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.RecognizedShape
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holding a pencil stroke still snaps it to a shape, and that shape is graphite: the pencil's grain
 * and its two passes at the pressure the stroke was drawn at, not solid ink the colour of a pen.
 */
class PencilShapeTest {

    /** Records every graphite pass; everything else goes to a [FakeRenderer]. */
    private class GrainRecorder(val base: FakeRenderer = FakeRenderer()) : Renderer by base {
        class Pass(val count: Int, val color: Rgba, val radii: FloatArray, val centers: FloatArray)

        val passes = ArrayList<Pass>()

        override fun fillGrainRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) {
            passes += Pass(count, color, radii.copyOfRange(from, from + count), centers.copyOfRange(2 * from, 2 * (from + count)))
        }

        override fun fillDiskRibbon(centers: FloatArray, radii: FloatArray, from: Int, count: Int, color: Rgba) =
            base.fillDiskRibbon(centers, radii, from, count, color)

        override fun strokePolyline(pts: FloatArray, from: Int, count: Int, pen: com.xnotes.core.pal.Pen) =
            base.strokePolyline(pts, from, count, pen)
    }

    private val ink = Rgba(40, 44, 52, 255)

    private fun pencilShape(kind: ShapeKind = ShapeKind.LINE, pressure: Double = 0.8, fill: Rgba? = null) = ShapeItem(
        kind, Pt(10.0, 20.0), Pt(210.0, 120.0), ink, 3.0, fill, grain = true, grainPressure = pressure,
    )

    /** A pencil stroke along a line: a light lead-in, a steady middle, a hard press at the hold. */
    private fun drawnPencil(): Stroke {
        val samples = (0 until 60).map { i ->
            val p = when {
                i < 8 -> 0.1
                i < 48 -> 0.55 + 0.002 * i
                else -> 1.0
            }
            Sample(20.0 + i * 3.0, 50.0 + i * 0.5, p, i * 8.0)
        }
        return Stroke(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL), samples)
    }

    // --- the snap ---

    @Test fun theTypicalWidthIsTheMedianOfWhatWasDrawn() {
        val hw = floatArrayOf(0f, 1f, 5f, 2f, 3f, 0f, 9f)
        // Drawn points 1, 5, 2, 3 (the 9 is past the count): lower middle of 1, 2, 3, 5.
        assertEquals(2.0, Graphite.typicalHalfWidth(hw, 6), 0.0)
        assertEquals(3.0, Graphite.typicalHalfWidth(floatArrayOf(1f, 5f, 3f), 3), 0.0)
        assertEquals(0.0, Graphite.typicalHalfWidth(floatArrayOf(0f, 0f), 2), 0.0)
        assertEquals(0.0, Graphite.typicalHalfWidth(FloatArray(0), 0), 0.0)
    }

    @Test fun aPencilSnapsToGraphiteAtItsMedianPressure() {
        val stroke = drawnPencil()
        val c = stroke.config
        val rec = RecognizedShape(ShapeKind.LINE, Pt(20.0, 50.0), Pt(197.0, 79.5))
        val shape = ShapeItem.snappedFrom(stroke, rec, penWidth = 99.0)
        assertTrue(shape.grain && shape.graphite)
        assertEquals(c.rgba, shape.strokeRgba)

        // The width is the stroke's median drawn width, and the pressure the median pressure the
        // ribbon was drawn at, read back the way the stroke's own core reads it.
        val g = stroke.geometry()
        val widths = (0 until g.pointCount).map { g.halfWidths[it] }.filter { it > 0f }.sorted()
        val medianHw = widths[(widths.size - 1) / 2].toDouble()
        assertEquals(2.0 * medianHw, shape.strokeWidth, 1e-9)
        val pressures = widths.map { Graphite.pressureAt(it, c.baseWidth, c.pressureEnabled, c.pressureMinFactor) }.sorted()
        assertEquals(pressures[(pressures.size - 1) / 2], shape.grainPressure, 1e-9)
        // Neither the light lead-in nor the hard press at the hold sets the tone.
        val lightest = Graphite.pressureAt(widths.first(), c.baseWidth, c.pressureEnabled, c.pressureMinFactor)
        val hardest = Graphite.pressureAt(widths.last(), c.baseWidth, c.pressureEnabled, c.pressureMinFactor)
        assertTrue(shape.grainPressure > lightest + 0.1 && shape.grainPressure < hardest - 0.1)
        // So the core fills the same share of the line as it does of the stroke at that pressure.
        assertEquals(
            shape.strokeWidth / 2.0 * Graphite.coreFraction(shape.grainPressure),
            shape.graphiteCoreHalfWidth(),
            1e-12,
        )
    }

    @Test fun aPencilPolygonSnapsToGraphiteToo() {
        val rec = RecognizedShape(
            ShapeKind.POLYGON, Pt(0.0, 0.0), Pt(100.0, 80.0),
            listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(60.0, 80.0), Pt(10.0, 70.0)),
        )
        val shape = ShapeItem.snappedFrom(drawnPencil(), rec, penWidth = 1.0)
        assertEquals(ShapeKind.POLYGON, shape.shape)
        assertTrue(shape.graphite)
        assertTrue(shape.grainPressure > 0.0)
    }

    @Test fun anInkPenStillSnapsToThePlainShapeItAlwaysDid() {
        val pen = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), drawnPencil().samples)
        val rec = RecognizedShape(ShapeKind.LINE, Pt(20.0, 50.0), Pt(197.0, 79.5))
        val shape = ShapeItem.snappedFrom(pen, rec, penWidth = 4.25)
        assertFalse(shape.grain)
        assertEquals(Graphite.PRESSURE_OFF, shape.grainPressure, 0.0)
        assertEquals(4.25, shape.strokeWidth, 0.0)
        assertEquals(pen.config.rgba, shape.strokeRgba)
        val dashed = ShapeItem.snappedFrom(Stroke(Tool.DASHED, ToolDefaults.configFor(Tool.DASHED), pen.samples), rec, 2.0)
        assertTrue(dashed.dashed)
        assertFalse(dashed.grain)
    }

    // --- the paged renderer ---

    @Test fun aPencilShapeIsLaidInGraphite() {
        val shape = pencilShape(pressure = 0.85)
        val r = GrainRecorder()
        shape.paint(r)
        assertEquals(2, r.passes.size)
        val (outer, core) = r.passes
        assertEquals(ink.scaleAlpha(Graphite.OUTER_ALPHA), outer.color)
        assertEquals(ink.scaleAlpha(Graphite.CORE_ALPHA), core.color)
        for (w in outer.radii) assertEquals(1.5f, w, 0f)
        val coreHalf = (1.5 * Graphite.coreFraction(0.85)).toFloat()
        assertTrue(coreHalf > 0f)
        for (w in core.radii) assertEquals(coreHalf, w, 1e-6f)
        assertEquals(floatArrayOf(10f, 20f, 210f, 120f).toList(), outer.centers.toList())
        // Nothing of it goes out as solid ink.
        assertTrue(r.base.ops.none { it.startsWith("stroke") })
        assertTrue(r.base.ribbonRuns.isEmpty())
    }

    @Test fun aLightPencilShapeIsTheOuterPassAlone() {
        val r = GrainRecorder()
        pencilShape(pressure = 0.1).paint(r)
        assertEquals(1, r.passes.size)
    }

    @Test fun anArrowAndAClosedOutlineAreEachOneRibbonAPass() {
        // One fill a pass, so the arrow's tip and a closed outline's seam never lay graphite twice.
        val arrow = pencilShape(ShapeKind.ARROW)
        val ra = GrainRecorder()
        arrow.paint(ra)
        assertEquals(2, ra.passes.size)
        val head = arrow.arrowHead()
        assertEquals(listOf(arrow.start, head[1], head[0], head[1], head[2]), arrow.graphitePath())
        assertEquals(5, ra.passes[0].count)

        val rect = pencilShape(ShapeKind.RECTANGLE, fill = Rgba(1, 2, 3, 40))
        val rr = GrainRecorder()
        rect.paint(rr)
        val path = rect.graphitePath()
        assertEquals(5, path.size)
        assertEquals(path.first(), path.last())
        assertEquals(2, rr.passes.size)
        assertEquals(listOf("fillRect"), rr.base.ops) // the fill under it, as on any shape
        assertEquals(ShapeItem.ELLIPSE_SEGMENTS + 1, pencilShape(ShapeKind.CIRCLE).graphitePath().size)
    }

    @Test fun aPlainShapeStillStrokesItsOutline() {
        val plain = ShapeItem(ShapeKind.LINE, Pt(10.0, 20.0), Pt(210.0, 120.0), ink, 3.0)
        val r = GrainRecorder()
        plain.paint(r)
        assertTrue(r.passes.isEmpty())
        assertEquals(listOf("strokePolyline"), r.base.ops)
        // Neon and dash, which no pencil makes, win over the grain.
        assertFalse(pencilShape().apply { neon = true }.graphite)
        assertFalse(pencilShape().apply { dashed = true }.graphite)
    }

    // --- the GL canvas ---

    @Test fun theGlCanvasMeshesThePencilShapeAsGraphite() {
        val shape = pencilShape(ShapeKind.RECTANGLE, pressure = 0.9, fill = Rgba(1, 2, 3, 40))
        val parts = ItemMesher.mesh(shape)!!.parts
        assertEquals(listOf(InkPass.OPAQUE, InkPass.GRAPHITE, InkPass.GRAPHITE), parts.map { it.pass })
        assertEquals(ink.scaleAlpha(Graphite.OUTER_ALPHA), parts[1].color)
        assertEquals(ink.scaleAlpha(Graphite.CORE_ALPHA), parts[2].color)
        assertEquals(ink.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0, parts[2].under, 1e-12)
        assertTrue(parts[2].mesh.triangleCount > 0)

        assertEquals(listOf(InkPass.GRAPHITE), ItemMesher.mesh(pencilShape(pressure = 0.1))!!.parts.map { it.pass })
        val plain = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(50.0, 40.0), ink, 3.0)
        assertEquals(listOf(InkPass.OPAQUE), ItemMesher.mesh(plain)!!.parts.map { it.pass })
    }

    // --- editing ---

    private fun assertGraphite(item: CanvasItem, pressure: Double) {
        val shape = item as ShapeItem
        assertTrue(shape.graphite)
        assertEquals(pressure, shape.grainPressure, 0.0)
        val r = GrainRecorder()
        shape.paint(r)
        assertEquals(2, r.passes.size)
        assertTrue(r.base.ops.none { it.startsWith("stroke") })
        assertTrue(ItemMesher.mesh(shape)!!.parts.all { it.pass == InkPass.GRAPHITE })
    }

    @Test fun draggingAPencilLinesEndKeepsItGraphite() {
        val line = pencilShape(pressure = 0.7)
        val doc = InfiniteDocument().apply { add(line) }
        val sel = CanvasSelection(doc).apply { select(listOf(line)) }
        sel.beginTransform()
        sel.moveEndpoint(HandleId.END, Pt(400.0, 300.0))
        assertEquals(Pt(400.0, 300.0), line.end)
        assertGraphite(line, 0.7)
        val r = GrainRecorder()
        line.paint(r)
        assertEquals(400f, r.passes[0].centers[2], 0f)
    }

    @Test fun resizingTurningAndRestylingKeepItGraphite() {
        val rect = pencilShape(ShapeKind.RECTANGLE, pressure = 0.75)
        rect.applyTransform(Affine.scaleAbout(Pt(0.0, 0.0), 2.0, 2.0))
        assertEquals(6.0, rect.strokeWidth, 1e-9)
        assertGraphite(rect, 0.75)
        rect.applyTransform(Affine.rotateAbout(Pt(100.0, 100.0), 0.6))
        assertEquals(ShapeKind.POLYGON, rect.shape) // a turned box is baked into a polygon
        assertGraphite(rect, 0.75)

        val red = Rgba(200, 30, 30, 255)
        DrawStyle(red, 9.0).applyTo(rect)
        assertGraphite(rect, 0.75)
        val r = GrainRecorder()
        rect.paint(r)
        assertEquals(red.scaleAlpha(Graphite.OUTER_ALPHA), r.passes[0].color)
        assertEquals(4.5f, r.passes[0].radii[0], 0f)
        // The core keeps its share of the new width, so the tone holds.
        assertEquals((4.5 * Graphite.coreFraction(0.75)).toFloat(), r.passes[1].radii[0], 1e-6f)
    }

    @Test fun copiesAndErasedPiecesStayGraphite() {
        val line = pencilShape(pressure = 0.66)
        assertGraphite(line.deepCopy(FakeTextMeasurer()), 0.66)
        val pieces = line.erasedBy(110.0, 70.0, 10.0)!!
        assertEquals(2, pieces.size)
        for (p in pieces) assertGraphite(p, 0.66)
    }
}
