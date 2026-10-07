package com.xnotes.core.infinite

import com.xnotes.canvas.HandleId
import com.xnotes.canvas.ResizeMath
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.tools.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A lone line or arrow on the infinite canvas is resized and turned by its two ends, as on a note.
 * Its box handles used to win instead, and a corner of a box one stroke thick scales along the
 * box's diagonal, which is the line itself: a held-straight vertical line could only get longer.
 */
class CanvasEndpointHandlesTest {

    private val ink = Rgba(20, 30, 40, 255)

    private fun shape(kind: ShapeKind, start: Pt, end: Pt, width: Double = 4.0) =
        ShapeItem(kind, start, end, ink, width)

    private fun docOf(vararg items: CanvasItem) = InfiniteDocument().apply { addAll(items.toList()) }

    private fun selected(vararg items: CanvasItem): CanvasSelection =
        CanvasSelection(docOf(*items)).apply { select(items.toList()) }

    @Test fun aLoneLineIsHandledByItsTwoEnds() {
        val line = shape(ShapeKind.LINE, Pt(100.0, 100.0), Pt(100.0, 400.0))
        val sel = selected(line)
        assertSame(line, sel.endpointShape())
        val handles = sel.handles()
        assertEquals(listOf(HandleId.START, HandleId.END), handles.map { it.id })
        assertEquals(Pt(100.0, 100.0), handles[0].content)
        assertEquals(Pt(100.0, 400.0), handles[1].content)
        assertNull("a line is turned by an end, not by a grip", sel.rotateGrip(30.0))
        assertEquals(HandleId.END, sel.hitHandle(Pt(103.0, 398.0), 12.0))
        assertEquals(HandleId.START, sel.hitHandle(Pt(98.0, 104.0), 12.0))
    }

    @Test fun anArrowIsToo() {
        val arrow = shape(ShapeKind.ARROW, Pt(0.0, 0.0), Pt(200.0, 50.0))
        val sel = selected(arrow)
        assertSame(arrow, sel.endpointShape())
        assertEquals(listOf(HandleId.START, HandleId.END), sel.handles().map { it.id })
    }

    @Test fun anythingElseKeepsTheBoxAndItsGrip() {
        val rect = shape(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(100.0, 80.0))
        val one = selected(rect)
        assertNull(one.endpointShape())
        assertEquals(8, one.handles().size)
        assertNotNull(one.rotateGrip(30.0))

        val line = shape(ShapeKind.LINE, Pt(0.0, 0.0), Pt(100.0, 0.0))
        val image = ImageItem(ImageData(File("none"), 10, 10), Rect(200.0, 200.0, 40.0, 30.0))
        val two = selected(line, image)
        assertNull("a line among others is part of a box", two.endpointShape())
        assertEquals(8, two.handles().size)
        assertNotNull(two.rotateGrip(30.0))
    }

    @Test fun anEndGoesAnywhereAndTheOtherStaysPut() {
        val line = shape(ShapeKind.LINE, Pt(100.0, 100.0), Pt(100.0, 400.0))
        val sel = selected(line)
        sel.beginTransform()
        // Off the line's own axis: before, the end could only slide up and down it.
        sel.resizeLive(HandleId.END, Pt(350.0, 260.0))
        assertEquals(Pt(100.0, 100.0), line.start)
        assertEquals(Pt(350.0, 260.0), line.end)
        assertEquals("an end moving is not a scale: the width stays", 4.0, line.strokeWidth, 0.0)
        assertEquals(ShapeKind.LINE, line.shape)
        // The box follows the line, wherever the end has swung it.
        val box = sel.box!!
        val b = line.bounds()
        assertEquals(b.centerX, box.center.x, 1e-9)
        assertEquals(b.centerY, box.center.y, 1e-9)
        assertEquals(0.0, box.angle, 0.0)
        assertEquals(Pt(350.0, 260.0), sel.handles()[1].content)

        sel.resizeLive(HandleId.START, Pt(-40.0, 120.0))
        assertEquals(Pt(-40.0, 120.0), line.start)
        assertEquals("each sample is measured from the drag's start, not the last one", Pt(100.0, 400.0), line.end)
    }

    @Test fun anEndDragIsTheEndsResizeOnANote() {
        val line = shape(ShapeKind.ARROW, Pt(10.0, 20.0), Pt(300.0, 20.0))
        val sel = selected(line)
        sel.beginTransform()
        val pointer = Pt(250.0, -90.0)
        sel.resizeLive(HandleId.END, pointer)
        val (s, e) = ResizeMath.resizeOpenShape(Pt(10.0, 20.0), Pt(300.0, 20.0), HandleId.END, pointer)
        assertEquals(s, line.start)
        assertEquals(e, line.end)
    }

    @Test fun anEndDragIsOneUndoStep() {
        val line = shape(ShapeKind.LINE, Pt(0.0, 0.0), Pt(0.0, 300.0))
        val sel = selected(line)
        sel.beginTransform()
        sel.resizeLive(HandleId.END, Pt(120.0, 200.0))
        sel.resizeLive(HandleId.END, Pt(180.0, 240.0))
        val cmd = sel.buildCommand(movedOnly = false)!!
        cmd.undo()
        assertEquals(Pt(0.0, 0.0), line.start)
        assertEquals(Pt(0.0, 300.0), line.end)
        cmd.redo()
        assertEquals(Pt(180.0, 240.0), line.end)
    }

    @Test fun aCancelledEndDragPutsTheLineBack() {
        val line = shape(ShapeKind.LINE, Pt(0.0, 0.0), Pt(0.0, 300.0))
        val sel = selected(line)
        val before = sel.box
        sel.beginTransform()
        sel.resizeLive(HandleId.END, Pt(120.0, 200.0))
        sel.restoreStart()
        assertEquals(Pt(0.0, 300.0), line.end)
        assertEquals(before, sel.box)
    }

    @Test fun theEndsRideAlongWithAMovePreview() {
        val line = shape(ShapeKind.LINE, Pt(0.0, 0.0), Pt(0.0, 300.0))
        val sel = selected(line)
        sel.beginTransform()
        sel.previewMove(40.0, -10.0)
        val handles = sel.handles()
        assertEquals(40.0, handles[0].content.x, 1e-9)
        assertEquals(-10.0, handles[0].content.y, 1e-9)
        assertEquals(40.0, handles[1].content.x, 1e-9)
        assertEquals(290.0, handles[1].content.y, 1e-9)
        assertEquals("the model stays put until the finger lifts", Pt(0.0, 0.0), line.start)
    }

    @Test fun noScalePreviewIsOfferedForAnEnd() {
        val line = shape(ShapeKind.LINE, Pt(0.0, 0.0), Pt(0.0, 300.0))
        val sel = selected(line)
        sel.beginTransform()
        assertNull(sel.previewResize(HandleId.END, Pt(50.0, 50.0)))
    }

    @Test fun theChromeIsTwoGripsInTheBoxGripsStyle() {
        val zoom = 2.0
        val dp = 2.5
        val accent = Rgba(10, 120, 200, 255)
        val face = Rgba(255, 255, 255, 255)
        val start = Pt(100.0, 100.0)
        val end = Pt(160.0, 340.0)
        val tol = OverlayTessellator.chromeTolerance(zoom, dp)
        val parts = OverlayTessellator.endpoints(start, end, zoom, accent, tol, dp, face)
        assertEquals(listOf(OverlayTessellator.GRIP_SHADOW, accent, face), parts.map { it.color })
        assertTrue(parts.all { it.pass == InkPass.OPAQUE })

        val faceR = OverlayTessellator.GRIP_DP * dp / zoom / 2.0
        val ringR = faceR + OverlayTessellator.GRIP_RING_DP * dp / zoom
        val drop = OverlayTessellator.SHADOW_DROP_DP * dp / zoom
        val grow = OverlayTessellator.SHADOW_GROW_DP * dp / zoom
        fun discs(mesh: MeshData, r: Double, dy: Double) {
            // Two fans, one per end: every rim vertex at the radius from its own end.
            val p = mesh.positions
            var near = 0
            for (i in 0 until mesh.vertexCount) {
                val x = p[2 * i]
                val y = p[2 * i + 1]
                val ds = kotlin.math.hypot(x - start.x, y - start.y - dy)
                val de = kotlin.math.hypot(x - end.x, y - end.y - dy)
                val d = minOf(ds, de)
                assertTrue("vertex $i is $d from its end, radius $r", d < 1e-9 || kotlin.math.abs(d - r) < 1e-9)
                if (d < 1e-9) near++
            }
            assertEquals("one centre per end", 2, near)
        }
        discs(parts[0].mesh, ringR + grow, drop)
        discs(parts[1].mesh, ringR, 0.0)
        discs(parts[2].mesh, faceR, 0.0)
        // The shadows are cut exactly as finely as a box grip's, which has the same sizes. The box's
        // second part is its eight grip shadows and nothing else.
        val box = OverlayTessellator.selection(
            com.xnotes.core.geometry.Obb.fromAabb(Rect(0.0, 0.0, 50.0, 50.0)), zoom, accent, tol, dp, face,
        )
        assertEquals(OverlayTessellator.GRIP_SHADOW, box[1].color)
        assertEquals(box[1].mesh.vertexCount / 8, parts[0].mesh.vertexCount / 2)
    }
}
