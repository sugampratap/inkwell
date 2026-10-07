package com.xnotes.core.infinite

import com.xnotes.canvas.HandleId
import com.xnotes.canvas.SelectionMath
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanvasSelectionTest {

    private fun box(x: Double, y: Double, w: Double = 40.0, h: Double = 30.0): ImageItem =
        ImageItem(ImageData(File("none"), 10, 10), Rect(x, y, w, h))

    private fun line(x: Double, y: Double): Stroke = Stroke(
        Tool.PEN,
        ToolDefaults.configFor(Tool.PEN),
        mutableListOf(Sample(x, y, 1.0), Sample(x + 40.0, y, 1.0)),
    )

    private fun docOf(vararg items: CanvasItem) = InfiniteDocument().apply { addAll(items.toList()) }

    // --- membership ---

    @Test fun aBandTakesWhateverItOverlaps() {
        val inside = box(10.0, 10.0)
        val outside = box(500.0, 500.0)
        val hits = SelectionMath.bandMembers(listOf(inside, outside), Rect(0.0, 0.0, 100.0, 100.0))
        assertEquals(listOf<CanvasItem>(inside), hits)
    }

    @Test fun aBandTakesAnItemItOnlyClips() {
        val straddling = box(90.0, 10.0)
        val hits = SelectionMath.bandMembers(listOf(straddling), Rect(0.0, 0.0, 100.0, 100.0))
        assertEquals(1, hits.size)
    }

    @Test fun aLassoTakesWhateverItEncloses() {
        val inside = box(40.0, 40.0, 10.0, 10.0)
        val outside = box(400.0, 400.0, 10.0, 10.0)
        val loop = listOf(Pt(0.0, 0.0), Pt(200.0, 0.0), Pt(200.0, 200.0), Pt(0.0, 200.0))
        val hits = SelectionMath.lassoMembers(listOf(inside, outside), loop)
        assertEquals(listOf<CanvasItem>(inside), hits)
    }

    @Test fun aDegenerateLassoSelectsNothing() {
        assertTrue(SelectionMath.lassoMembers(listOf(box(0.0, 0.0)), listOf(Pt(0.0, 0.0), Pt(1.0, 1.0))).isEmpty())
    }

    // --- the box ---

    @Test fun theBoxWrapsEverythingSelected() {
        val doc = docOf()
        val sel = CanvasSelection(doc)
        sel.select(listOf(box(0.0, 0.0, 10.0, 10.0), box(90.0, 40.0, 10.0, 10.0)))
        val b = sel.box!!
        assertEquals(50.0, b.center.x, 1e-9)
        assertEquals(25.0, b.center.y, 1e-9)
        assertEquals(50.0, b.halfW, 1e-9)
        assertEquals(25.0, b.halfH, 1e-9)
        assertEquals(0.0, b.angle, 1e-12)
    }

    @Test fun anEmptySelectionHasNoBox() {
        val sel = CanvasSelection(docOf())
        assertTrue(sel.isEmpty)
        assertNull(sel.box)
        assertTrue(sel.handles().isEmpty())
        assertNull(sel.rotateGrip(30.0))
    }

    @Test fun aPressInsideTheBoxGrabsIt() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 100.0)))
        assertTrue(sel.contains(Pt(50.0, 50.0)))
        assertTrue(!sel.contains(Pt(500.0, 50.0)))
    }

    @Test fun everyCornerAndEdgeHasAHandle() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 60.0)))
        assertEquals(8, sel.handles().size)
        assertEquals(HandleId.TL, sel.hitHandle(Pt(0.0, 0.0), 5.0))
        assertEquals(HandleId.BR, sel.hitHandle(Pt(100.0, 60.0), 5.0))
        assertNull(sel.hitHandle(Pt(50.0, 30.0), 5.0))
    }

    @Test fun theRotateGripSitsAboveTheBox() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 60.0)))
        val grip = sel.rotateGrip(30.0)!!
        assertEquals(50.0, grip.x, 1e-9)
        assertEquals(-30.0, grip.y, 1e-9)
    }

    // --- moving ---

    @Test fun aMoveShiftsEveryItemTogether() {
        val a = box(0.0, 0.0)
        val b = box(100.0, 0.0)
        val doc = docOf(a, b)
        val sel = CanvasSelection(doc)
        sel.select(listOf(a, b))
        sel.beginTransform()
        sel.moveLive(25.0, 10.0)
        assertEquals(25.0, a.rect.x, 1e-9)
        assertEquals(125.0, b.rect.x, 1e-9)
        assertEquals(10.0, a.rect.y, 1e-9)
    }

    @Test fun aMoveDragDoesNotCompound() {
        val a = box(0.0, 0.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.moveLive(10.0, 0.0)
        sel.moveLive(20.0, 0.0)
        sel.moveLive(30.0, 0.0)
        assertEquals("each frame is measured from the gesture start", 30.0, a.rect.x, 1e-9)
    }

    @Test fun theBoxFollowsAMove() {
        val a = box(0.0, 0.0, 100.0, 100.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.moveLive(40.0, 0.0)
        assertEquals(90.0, sel.box!!.center.x, 1e-9)
    }

    @Test fun aMoveIsUndoable() {
        val a = box(0.0, 0.0)
        val doc = docOf(a)
        val sel = CanvasSelection(doc)
        sel.select(listOf(a))
        sel.beginTransform()
        sel.moveLive(25.0, 10.0)
        val cmd = sel.buildCommand(movedOnly = true, dx = 25.0, dy = 10.0)!!
        cmd.undo()
        assertEquals(0.0, a.rect.x, 1e-9)
        cmd.redo()
        assertEquals(25.0, a.rect.x, 1e-9)
    }

    @Test fun aMoveOfNothingRecordsNothing() {
        val a = box(0.0, 0.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        assertNull(sel.buildCommand(movedOnly = true, dx = 0.0, dy = 0.0))
    }

    @Test fun aMoveRefilesTheSpatialIndex() {
        val a = box(0.0, 0.0)
        val doc = docOf(a)
        val sel = CanvasSelection(doc)
        sel.select(listOf(a))
        sel.beginTransform()
        sel.moveLive(9000.0, 9000.0)
        assertTrue(doc.itemsIn(Rect(-10.0, -10.0, 100.0, 100.0)).isEmpty())
        assertSame(a, doc.itemsIn(Rect(8990.0, 8990.0, 100.0, 100.0)).single())
    }

    // --- resizing ---

    @Test fun aCornerDragScalesTheSelection() {
        val a = box(0.0, 0.0, 100.0, 100.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        assertTrue("the box must have grown", sel.box!!.halfW > 50.0)
        assertTrue(a.rect.w > 100.0)
    }

    @Test fun aResizeAnchorsAtTheOppositeCorner() {
        val a = box(0.0, 0.0, 100.0, 100.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        assertEquals("the far corner stays put", 0.0, a.rect.x, 1e-6)
        assertEquals(0.0, a.rect.y, 1e-6)
    }

    @Test fun aResizeDragDoesNotCompound() {
        val a = box(0.0, 0.0, 100.0, 100.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        val once = a.rect.w
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        assertEquals("the same pointer must give the same size", once, a.rect.w, 1e-9)
    }

    @Test fun aResizeIsUndoable() {
        val a = box(0.0, 0.0, 100.0, 100.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        val cmd = sel.buildCommand(movedOnly = false)!!
        cmd.undo()
        assertEquals(100.0, a.rect.w, 1e-6)
        cmd.redo()
        assertTrue(a.rect.w > 100.0)
    }

    @Test fun scalingAStrokeScalesItsWidthToo() {
        val s = line(0.0, 0.0)
        val before = s.config.baseWidth
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        sel.beginTransform()
        sel.resizeLive(HandleId.BR, Pt(200.0, 200.0))
        assertTrue("a resized stroke should look zoomed, not just longer", s.config.baseWidth > before)
    }

    // --- rotating ---

    @Test fun aRotationTurnsTheBox() {
        val a = box(0.0, 0.0, 100.0, 60.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        sel.beginTransform()
        sel.rotateLive(Pt(200.0, 30.0)) // pointer to the right of centre
        assertTrue("the box must have turned", kotlin.math.abs(sel.box!!.angle) > 1e-6)
    }

    @Test fun aRotationLeavesTheCentreWhereItWas() {
        val s = line(0.0, 0.0)
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        val centre = sel.box!!.center
        sel.beginTransform()
        sel.rotateLive(Pt(centre.x, centre.y - 100.0))
        assertEquals(centre.x, sel.box!!.center.x, 1e-6)
        assertEquals(centre.y, sel.box!!.center.y, 1e-6)
    }

    @Test fun aRotationIsUndoable() {
        val s = line(10.0, 10.0)
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        val before = s.samples.map { it.x to it.y }
        sel.beginTransform()
        sel.rotateLive(Pt(500.0, 500.0))
        val cmd = sel.buildCommand(movedOnly = false)!!
        cmd.undo()
        assertEquals(before, s.samples.map { it.x to it.y })
        cmd.redo()
        assertTrue(before != s.samples.map { it.x to it.y })
    }

    @Test fun aRotationDragDoesNotCompound() {
        val s = line(0.0, 0.0)
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        sel.beginTransform()
        sel.rotateLive(Pt(0.0, -100.0))
        val once = s.samples.map { it.x to it.y }
        sel.rotateLive(Pt(0.0, -100.0))
        assertEquals(once, s.samples.map { it.x to it.y })
    }

    @Test fun grabbingTheGripOffCentreDoesNotSnapTheSelection() {
        val a = box(0.0, 0.0, 100.0, 60.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        val grip = sel.rotateGrip(34.0)!!
        // A press at the far edge of the grip's touch target, then no movement at all.
        val grab = Pt(grip.x + 22.0, grip.y)
        sel.beginTransform(grab)
        sel.rotateLive(grab)
        assertEquals("a press alone must not turn anything", 0.0, sel.box!!.angle, 1e-9)
    }

    @Test fun aRotationTurnsByWhatTheFingerSwept() {
        val a = box(0.0, 0.0, 100.0, 60.0)
        val sel = CanvasSelection(docOf(a))
        sel.select(listOf(a))
        val centre = sel.box!!.center
        val grab = Pt(centre.x + 22.0, centre.y - 100.0)
        sel.beginTransform(grab)
        // A quarter turn of the grab point about the centre must turn the box a quarter turn.
        val swept = Pt(centre.x + 100.0, centre.y + 22.0)
        sel.rotateLive(swept)
        assertEquals(Math.PI / 2.0, sel.box!!.angle, 1e-9)
    }

    @Test fun aRotatedBoxKeepsItsOwnShape() {
        val s = line(0.0, 0.0)
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        val wide = sel.box!!.halfW
        val thin = sel.box!!.halfH
        val centre = sel.box!!.center
        sel.beginTransform(Pt(centre.x, centre.y - 100.0))
        sel.rotateLive(Pt(centre.x + 100.0, centre.y))
        assertEquals("a turn must not resize the box", wide, sel.box!!.halfW, 1e-9)
        assertEquals(thin, sel.box!!.halfH, 1e-9)
    }

    @Test fun refreshingTheBoxBringsItBackUpright() {
        val s = line(0.0, 0.0)
        val sel = CanvasSelection(docOf(s))
        sel.select(listOf(s))
        val centre = sel.box!!.center
        sel.beginTransform(Pt(centre.x, centre.y - 100.0))
        sel.rotateLive(Pt(centre.x + 100.0, centre.y))
        sel.refreshBox()
        assertEquals("item bounds cannot say what angle the ink is at", 0.0, sel.box!!.angle, 1e-9)
    }

    // --- overlay geometry ---

    @Test fun theOverlayScalesItsOutlineWithTheZoom() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 100.0)))
        val zoomedIn = OverlayTessellator.selection(sel.box!!, 8.0, com.xnotes.core.model.Rgba(0, 255, 0, 255), 0.01)
        val zoomedOut = OverlayTessellator.selection(sel.box!!, 0.5, com.xnotes.core.model.Rgba(0, 255, 0, 255), 0.01)
        assertNotNull(zoomedIn.firstOrNull())
        assertNotNull(zoomedOut.firstOrNull())
        // A constant device width means the content-space geometry is wider when zoomed out.
        val inSpan = span(zoomedIn[0].mesh)
        val outSpan = span(zoomedOut[0].mesh)
        assertTrue("the outline must stay one thickness on screen", outSpan > inSpan)
    }

    @Test fun theBandAndLassoProduceGeometry() {
        val accent = com.xnotes.core.model.Rgba(0, 255, 0, 255)
        assertTrue(OverlayTessellator.band(Rect(0.0, 0.0, 50.0, 40.0), 1.0, accent, 0.01).isNotEmpty())
        val loop = listOf(Pt(0.0, 0.0), Pt(50.0, 0.0), Pt(25.0, 40.0))
        assertTrue(OverlayTessellator.lasso(loop, 1.0, accent, 0.01).isNotEmpty())
        assertTrue(OverlayTessellator.lasso(listOf(Pt(0.0, 0.0)), 1.0, accent, 0.01).isEmpty())
    }

    // --- selection chrome, as SC 46-49 draws it ---

    @Test fun theChromeIsSizedAsTheMockupDrawsIt() {
        // SC 46: a 1.5 frame dashed 6/5. SC 47: 14 grips, a 1.5 ring. SC 48-49: a 28 stem up to a
        // 26 rotate grip with a 14 arrow. All dp.
        assertEquals(1.5, OverlayTessellator.FRAME_DP, 0.0)
        assertEquals(6.0, OverlayTessellator.DASH_ON_DP, 0.0)
        assertEquals(5.0, OverlayTessellator.DASH_GAP_DP, 0.0)
        assertEquals(14.0, OverlayTessellator.GRIP_DP, 0.0)
        assertEquals(1.5, OverlayTessellator.GRIP_RING_DP, 0.0)
        assertEquals(28.0, OverlayTessellator.ROTATE_STEM_DP, 0.0)
        assertEquals(26.0, OverlayTessellator.ROTATE_GRIP_DP, 0.0)
        assertEquals(14.0, OverlayTessellator.ROTATE_GLYPH_DP, 0.0)
        assertEquals("the grip's centre: the stem and its radius", 41.0, OverlayTessellator.ROTATE_ARM_DP, 0.0)
        assertEquals("the grip's rim, which the bar clears (SC 577: grip=54)", 54.0, OverlayTessellator.ROTATE_REACH_DP, 0.0)
        // SC 47/49: shadows 1 down, black at 22 % and 25 %.
        assertEquals(1.0, OverlayTessellator.SHADOW_DROP_DP, 0.0)
        assertEquals(com.xnotes.core.model.Rgba(0, 0, 0, 56), OverlayTessellator.GRIP_SHADOW)
        assertEquals(com.xnotes.core.model.Rgba(0, 0, 0, 64), OverlayTessellator.ROTATE_SHADOW)
    }

    @Test fun theSelectionIsDrawnFrameThenShadowsThenRingsThenFaces() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 100.0)))
        val accent = com.xnotes.core.model.Rgba(0x22, 0x22, 0x22, 255)
        val face = com.xnotes.core.model.Rgba(255, 255, 255, 255)
        val parts = OverlayTessellator.selection(sel.box!!, 1.0, accent, 0.01, 1.0, face)
        assertEquals(
            listOf(accent, OverlayTessellator.GRIP_SHADOW, OverlayTessellator.ROTATE_SHADOW, accent, face),
            parts.map { it.color },
        )
        assertTrue(parts.all { it.pass == InkPass.OPAQUE })
    }

    @Test fun theRotateGripSitsOnItsStemAndScalesWithTheDensityNotTheZoom() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 100.0)))
        val obb = sel.box!!
        val top = obb.corners().minOf { it.y }
        // 2 px per dp at zoom 4: a dp is half a content px.
        val parts = OverlayTessellator.selection(obb, 4.0, com.xnotes.core.model.Rgba(0, 0, 0, 255), 0.001, 2.0)
        val marks = parts[3].mesh
        var minY = Double.POSITIVE_INFINITY
        for (i in 0 until marks.vertexCount) minY = minOf(minY, marks.positions[2 * i + 1])
        assertEquals("the grip's rim is 54 dp above the top edge", top - 54.0 * 0.5, minY, 0.05)
        // Its shadow is a dp lower and half a dp wider, so its top sits half a dp under the rim.
        val shadow = parts[2].mesh
        var shadowTop = Double.POSITIVE_INFINITY
        for (i in 0 until shadow.vertexCount) shadowTop = minOf(shadowTop, shadow.positions[2 * i + 1])
        assertEquals(top - 54.0 * 0.5 + 0.5 * 0.5, shadowTop, 0.05)
        // The cover bounds hold the whole grip.
        assertTrue(OverlayTessellator.selectionBounds(obb, 4.0, 2.0).top <= minY)
    }

    @Test fun theBarAnchorIsRaisedClearOfTheRotateGripAndKeepsItsBottom() {
        // Upright, at 2 px per dp: the grip's centre is 41 dp over the top edge, so its rim is 54 dp over it.
        val box = Rect(10.0, 200.0, 80.0, 60.0)
        val grip = Pt(50.0, 200.0 - 82.0)
        val raised = OverlayTessellator.clearRotateGrip(box, grip, 2.0)
        assertEquals(200.0 - 108.0, raised.top, 1e-9)
        assertEquals(box.bottom, raised.bottom, 1e-9)
        assertEquals(box.left, raised.left, 1e-9)
        assertEquals(box.w, raised.w, 1e-9)
        assertSame("no grip, nothing to clear", box, OverlayTessellator.clearRotateGrip(box, null, 2.0))
    }

    @Test fun afterATurnTheBarAnchorClearsTheGripWhereTheTiltedBoxHangsIt() {
        // A 100 dp diagonal stroke turned 45° so it lies flat (I-3): its upright bounds are a thin band, but the grip
        // hangs off the tilted box, whose top-mid is 35 dp above that band. The old fixed 54 dp lift stopped short.
        val obb = com.xnotes.core.geometry.Obb(Pt(50.0, 50.0), 50.0, 50.0, Math.PI / 4)
        val items = Rect(50.0 - 70.7, 49.0, 141.4, 2.0)
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(obb, OverlayTessellator.rotateArm(1.0, 1.0))
        assertTrue("the old lift left the bar on the grip", items.top - OverlayTessellator.ROTATE_REACH_DP > grip.y - 13.0)
        val anchor = OverlayTessellator.clearRotateGrip(items, grip, 1.0)
        assertEquals("over the grip's rim", grip.y - 13.0, anchor.top, 1e-9)
        assertEquals(items.bottom, anchor.bottom, 1e-9)
        assertEquals(items.left, anchor.left, 1e-9)
        assertEquals(items.w, anchor.w, 1e-9)
    }

    @Test fun turnedTheBarAnchorAlsoClearsTheCornerGripLikeTheRotateGrip() {
        // Gate: turned 30°, the box's top corner stands above the items' bounds, and the bar 12 dp over the bounds sat
        // on its grip. The anchor now reaches over each grip's ringed rim (7 + 1.5 dp), as over the rotate grip's.
        val obb = com.xnotes.core.geometry.Obb(Pt(100.0, 100.0), 60.0, 20.0, Math.PI / 6)
        val handles = com.xnotes.canvas.ResizeMath.obbHandles(obb).map { it.content }
        val items = Rect(50.0, 70.0, 100.0, 60.0)
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(obb, OverlayTessellator.rotateArm(1.0, 1.0))
        val anchor = OverlayTessellator.clearRotateGrip(items, grip, 1.0, handles)
        val topGrip = handles.minOf { it.y }
        val bottomGrip = handles.maxOf { it.y }
        assertTrue("over the corner grip's rim", anchor.top <= topGrip - 8.5 + 1e-9)
        assertTrue("still over the rotate grip's", anchor.top <= grip.y - 13.0 + 1e-9)
        assertEquals("under the lowest grip's rim", bottomGrip + 8.5, anchor.bottom, 1e-9)
        assertEquals(items.left, anchor.left, 1e-9)
        assertEquals(items.w, anchor.w, 1e-9)
    }

    @Test fun pastAHalfTurnTheGripHangsBelowAndTheAnchorReachesDownOverIt() {
        val obb = com.xnotes.core.geometry.Obb(Pt(50.0, 50.0), 40.0, 20.0, Math.PI)
        val items = Rect(10.0, 30.0, 80.0, 40.0)
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(obb, OverlayTessellator.rotateArm(1.0, 1.0))
        assertEquals("the grip now points down", 50.0 + 20.0 + 41.0, grip.y, 1e-9)
        val anchor = OverlayTessellator.clearRotateGrip(items, grip, 1.0)
        assertEquals("no lift wasted over the box", items.top, anchor.top, 1e-9)
        assertEquals("the below-the-selection spot clears the grip", grip.y + 13.0, anchor.bottom, 1e-9)
    }

    @Test fun theRotateArmIsOneFormulaForTheDrawingAndTheHitTests() {
        assertEquals(41.0 * 2.0 / 4.0, OverlayTessellator.rotateArm(4.0, 2.0), 1e-12)
        val obb = com.xnotes.core.geometry.Obb(Pt(0.0, 0.0), 30.0, 20.0, 0.0)
        val faces = OverlayTessellator.selection(obb, 4.0, com.xnotes.core.model.Rgba(0, 0, 0, 255), 0.01, 2.0)[4].mesh
        val glyph = com.xnotes.canvas.RotateGlyph.mesh!!
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(obb, OverlayTessellator.rotateArm(4.0, 2.0))
        // The arrow is drawn on the grip's centre: its 14 dp box (3.5 content px a side here) holds every point.
        for (k in faces.vertexCount - glyph.points.size until faces.vertexCount) {
            assertTrue(kotlin.math.abs(faces.positions[2 * k + 1] - grip.y) <= 3.5 + 1e-9)
        }
    }

    // --- the chrome's weight (I-2) ---

    @Test fun aTypicalSelectionsChromeStaysLight() {
        // A 200 x 120 dp box on a 2.625 px/dp screen at 100 %, as the canvas builds it.
        val px = 2.625
        val box = com.xnotes.core.geometry.Obb(Pt(500.0, 400.0), 100.0 * px, 60.0 * px, 0.0)
        val accent = com.xnotes.core.model.Rgba(0x22, 0x22, 0x22, 255)
        val light = OverlayTessellator.selection(box, 1.0, accent, OverlayTessellator.chromeTolerance(1.0, px), px)
        val heavy = OverlayTessellator.selection(box, 1.0, accent, StrokeTessellator.DEFAULT_TOLERANCE, px)
        val after = light.sumOf { it.mesh.vertexCount }
        val before = heavy.sumOf { it.mesh.vertexCount }
        val discsAfter = light.drop(1).sumOf { it.mesh.vertexCount }
        val discsBefore = heavy.drop(1).sumOf { it.mesh.vertexCount }
        println("selection chrome vertices: before $before (grips $discsBefore), after $after (grips $discsAfter)")
        // Most of it is the dashed frame: some sixty round-capped dashes, which the paged canvas draws the same way.
        assertTrue("whole chrome: $after vertices", after <= 2100)
        assertTrue("grips, shadows and arrow: $discsAfter vertices", discsAfter <= 700)
        assertTrue("at least 3x lighter than at ink's tolerance ($before)", after * 3 <= before)
    }

    @Test fun aGripIsCutIntoAboutADozenSegmentsAtAnyZoom() {
        for (px in listOf(1.0, 2.625, 3.5)) for (zoom in listOf(0.1, 1.0, 8.0, 64.0)) {
            val dp = px / zoom
            val tol = OverlayTessellator.chromeTolerance(zoom, px)
            val face = MeshBuilder.circleSegments(OverlayTessellator.GRIP_DP / 2.0 * dp, tol)
            val rotate = MeshBuilder.circleSegments(OverlayTessellator.ROTATE_GRIP_DP / 2.0 * dp, tol)
            assertTrue("face at $px px/dp, zoom $zoom: $face", face in 10..20)
            assertTrue("rotate grip at $px px/dp, zoom $zoom: $rotate", rotate in 12..22)
        }
    }

    @Test fun aMovedBoxsChromeIsTheBuiltChromeShifted() {
        val accent = com.xnotes.core.model.Rgba(0x22, 0x22, 0x22, 255)
        val box = com.xnotes.core.geometry.Obb(Pt(10.0, 20.0), 50.0, 30.0, 0.3)
        val tol = OverlayTessellator.chromeTolerance(2.0, 1.0)
        val built = OverlayTessellator.selection(box, 2.0, accent, tol)
        val moved = OverlayTessellator.selection(box.translate(12.5, -7.0), 2.0, accent, tol)
        val shifted = OverlayTessellator.translated(built, 12.5, -7.0)
        assertEquals(moved.size, shifted.size)
        for (k in moved.indices) {
            val a = moved[k].mesh
            val b = shifted[k].mesh
            assertEquals(a.vertexCount, b.vertexCount)
            for (i in a.positions.indices) assertEquals(a.positions[i], b.positions[i], 1e-9)
            assertSame("the triangles are shared, not copied", built[k].mesh.indices, b.indices)
            assertEquals(moved[k].color, shifted[k].color)
        }
        assertSame("no move, nothing new", built, OverlayTessellator.translated(built, 0.0, 0.0))
    }

    @Test fun reusedBuildersBuildTheSameChrome() {
        val accent = com.xnotes.core.model.Rgba(0x22, 0x22, 0x22, 255)
        val scratch = OverlayTessellator.Scratch()
        val big = com.xnotes.core.geometry.Obb(Pt(0.0, 0.0), 400.0, 300.0, 0.0)
        val small = com.xnotes.core.geometry.Obb(Pt(5.0, 5.0), 40.0, 30.0, 0.7)
        OverlayTessellator.selection(big, 1.0, accent, 0.25, 1.0, scratch = scratch)
        val reused = OverlayTessellator.selection(small, 1.0, accent, 0.25, 1.0, scratch = scratch)
        val fresh = OverlayTessellator.selection(small, 1.0, accent, 0.25, 1.0)
        assertEquals(fresh.size, reused.size)
        for (k in fresh.indices) {
            assertTrue(fresh[k].mesh.positions.contentEquals(reused[k].mesh.positions))
            assertTrue(fresh[k].mesh.indices.contentEquals(reused[k].mesh.indices))
        }
    }

    @Test fun theRotateArrowIsPhosphorsGlyphBuiltOnceOnAUnitBox() {
        val ring = com.xnotes.canvas.RotateGlyph.outline
        assertTrue("an arc and an arrowhead need many points", ring.size >= 24)
        assertTrue(ring.all { it.x >= -0.5 && it.x <= 0.5 && it.y >= -0.5 && it.y <= 0.5 })
        // Phosphor's arrow-clockwise spans x 32..240 and down to y 224 of its 256 box.
        assertEquals((32.0 - 128.0) / 256.0, ring.minOf { it.x }, 0.01)
        assertEquals((240.0 - 128.0) / 256.0, ring.maxOf { it.x }, 0.01)
        assertEquals((224.0 - 128.0) / 256.0, ring.maxOf { it.y }, 0.01)
        val built = com.xnotes.canvas.RotateGlyph.mesh
        assertNotNull("the glyph must triangulate", built)
        val mesh = built!!
        // The triangles cover exactly what the outline encloses.
        var area = 0.0
        var i = 0
        while (i + 2 < mesh.indices.size) {
            val a = mesh.points[mesh.indices[i]]
            val b = mesh.points[mesh.indices[i + 1]]
            val c = mesh.points[mesh.indices[i + 2]]
            area += kotlin.math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2.0
            i += 3
        }
        val enclosed = kotlin.math.abs(com.xnotes.core.vector.Triangulator.signedArea(ring))
        assertEquals(enclosed, area, enclosed * 1e-6)
        assertTrue("a stroked arc, not a disc", enclosed > 0.05 && enclosed < 0.3)
        assertSame("cached, not rebuilt", ring, com.xnotes.canvas.RotateGlyph.outline)
    }

    @Test fun theOverlayCarriesTheArrowOnTheRotateGrip() {
        val sel = CanvasSelection(docOf())
        sel.select(listOf(box(0.0, 0.0, 100.0, 100.0)))
        val face = com.xnotes.core.model.Rgba(255, 255, 255, 255)
        val faces = OverlayTessellator.selection(sel.box!!, 1.0, com.xnotes.core.model.Rgba(0, 0, 0, 255), 0.01, 1.0, face)[4].mesh
        val glyph = com.xnotes.canvas.RotateGlyph.mesh!!
        assertTrue(faces.vertexCount > glyph.points.size + 8)
        // The arrow's points are the last ones in, inside its 14 dp box round the grip's centre.
        val grip = com.xnotes.canvas.ResizeMath.obbRotateGrip(sel.box!!, 41.0)
        for (k in faces.vertexCount - glyph.points.size until faces.vertexCount) {
            assertTrue(kotlin.math.abs(faces.positions[2 * k] - grip.x) <= 7.0 + 1e-9)
            assertTrue(kotlin.math.abs(faces.positions[2 * k + 1] - grip.y) <= 7.0 + 1e-9)
        }
    }

    /** How far the mesh reaches beyond the box it outlines, which is its content-space thickness. */
    private fun span(mesh: MeshData): Double {
        var maxX = Double.NEGATIVE_INFINITY
        for (i in 0 until mesh.vertexCount) maxX = maxOf(maxX, mesh.positions[2 * i])
        return maxX - 100.0
    }
}
