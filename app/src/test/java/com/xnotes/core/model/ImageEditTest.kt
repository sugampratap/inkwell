package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.history.TransformItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageEditTest {

    private val eps = 1e-9

    private fun image(w: Int = 400, h: Int = 200) = ImageData(File("unused.png"), w, h)

    private fun assertPt(expected: Pt, actual: Pt) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test fun quarterTurnsMapCornersClockwise() {
        // A clockwise turn brings the source's bottom-left corner to the top-left of the display.
        assertPt(Pt(1.0, 0.0), ImageGeometry.sourceToDisplay(0.0, 0.0, 90, false, false))
        assertPt(Pt(0.0, 0.0), ImageGeometry.sourceToDisplay(0.0, 1.0, 90, false, false))
        assertPt(Pt(1.0, 1.0), ImageGeometry.sourceToDisplay(0.0, 0.0, 180, false, false))
        assertPt(Pt(0.0, 1.0), ImageGeometry.sourceToDisplay(0.0, 0.0, 270, false, false))
        assertPt(Pt(1.0, 0.0), ImageGeometry.sourceToDisplay(0.0, 0.0, 0, true, false))
        assertPt(Pt(0.0, 1.0), ImageGeometry.sourceToDisplay(0.0, 0.0, 0, false, true))
    }

    @Test fun displayToSourceInvertsEveryOrientationAndMirror() {
        val probe = Pt(0.2, 0.7)
        for (o in listOf(0, 90, 180, 270, -90, 450)) {
            for (fx in listOf(false, true)) {
                for (fy in listOf(false, true)) {
                    val d = ImageGeometry.sourceToDisplay(probe.x, probe.y, o, fx, fy)
                    assertPt(probe, ImageGeometry.displayToSource(d.x, d.y, o, fx, fy))
                }
            }
        }
    }

    @Test fun cropRectsRoundTripThroughTheDisplay() {
        val c = ImageCrop(0.1, 0.2, 0.6, 0.9)
        for (o in listOf(0, 90, 180, 270)) {
            val d = ImageGeometry.sourceRectToDisplay(c, o, true, false)
            val back = ImageGeometry.displayRectToSource(d, o, true, false)
            assertEquals(c.l, back.l, eps)
            assertEquals(c.t, back.t, eps)
            assertEquals(c.r, back.r, eps)
            assertEquals(c.b, back.b, eps)
        }
        // Turned sideways, the crop's width becomes the display's height.
        val d = ImageGeometry.sourceRectToDisplay(c, 90, false, false)
        assertEquals(c.h, d.w, eps)
        assertEquals(c.w, d.h, eps)
    }

    @Test fun displayFlipFollowsTheTurn() {
        // Upright, a horizontal mirror is the source's x mirror; sideways it is the source's y.
        assertEquals(true to false, ImageGeometry.toggleDisplayFlip(0, false, false, horizontal = true))
        assertEquals(false to true, ImageGeometry.toggleDisplayFlip(90, false, false, horizontal = true))
        assertEquals(true to false, ImageGeometry.toggleDisplayFlip(270, false, false, horizontal = false))
    }

    @Test fun cropOfClampsOrdersAndKeepsAMinimum() {
        val c = ImageCrop.of(0.9, 1.4, 0.1, -0.2)
        assertEquals(0.1, c.l, eps)
        assertEquals(0.0, c.t, eps)
        assertEquals(0.9, c.r, eps)
        assertEquals(1.0, c.b, eps)
        val sliver = ImageCrop.of(0.5, 0.5, 0.5, 0.5)
        assertTrue(sliver.w >= ImageCrop.MIN_FRACTION - eps)
        assertTrue(ImageCrop.FULL.isFull)
        assertTrue(ImageEdit(ImageCrop.FULL).isIdentity)
        assertFalse(ImageEdit(flipY = true).isIdentity)
    }

    @Test fun coverCropFillsTheFrameCentred() {
        // A 2:1 picture into a square frame keeps its middle half across.
        val c = ImageGeometry.coverCrop(400, 200, 100.0, 100.0)!!
        assertEquals(0.25, c.l, eps)
        assertEquals(0.75, c.r, eps)
        assertEquals(0.0, c.t, eps)
        assertEquals(1.0, c.b, eps)
        assertNull(ImageGeometry.coverCrop(400, 200, 200.0, 100.0))
        val tall = ImageGeometry.coverCrop(100, 100, 200.0, 100.0)!!
        assertEquals(0.25, tall.t, eps)
        assertEquals(0.75, tall.b, eps)
    }

    @Test fun rotateQuarterSwapsTheBoxAboutItsCentre() {
        val item = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0))
        item.rotateQuarter(clockwise = true)
        assertEquals(90, item.orientation)
        assertEquals(Rect(10.0, -10.0, 20.0, 40.0), item.rect)
        item.rotateQuarter(clockwise = false)
        item.rotateQuarter(clockwise = false)
        assertEquals(270, item.orientation)
    }

    @Test fun mirrorTransformFlipsInsteadOfMovingOnly() {
        val item = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0), angle = 0.3)
        item.applyTransform(Affine.scaleAbout(Pt(100.0, 10.0), -1.0, 1.0))
        assertTrue(item.flipX)
        assertFalse(item.flipY)
        assertEquals(-0.3, item.angle, eps)
        assertEquals(180.0, item.rect.centerX, eps)
        val v = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0), angle = 0.3)
        v.applyTransform(Affine.scaleAbout(Pt(20.0, 10.0), 1.0, -1.0))
        assertTrue(v.flipY)
        assertFalse(v.flipX)
        assertEquals(-0.3, v.angle, eps)
    }

    @Test fun plainTransformsStillAddTheirTurn() {
        val item = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0))
        item.applyTransform(Affine.rotateAbout(Pt(20.0, 10.0), 0.5))
        assertEquals(0.5, item.angle, eps)
        assertFalse(item.flipX || item.flipY)
    }

    @Test fun resetBringsBackTheWholeUprightPictureAtTheSameScale() {
        val item = ImageItem(image(400, 200), Rect(0.0, 0.0, 100.0, 100.0), orientation = 90, angle = 0.4)
        item.crop = ImageCrop(0.0, 0.0, 0.5, 1.0) // 200x200 of source, shown at 100x100: half scale
        item.flipX = true
        item.resetEdits()
        assertNull(item.crop)
        assertFalse(item.flipX)
        assertEquals(0, item.orientation)
        assertEquals(0.0, item.angle, eps)
        assertEquals(200.0, item.rect.w, eps)
        assertEquals(100.0, item.rect.h, eps)
        assertEquals(50.0, item.rect.centerX, eps)
    }

    @Test fun replaceKeepsTheFrameAndCoversIt() {
        val item = ImageItem(image(400, 200), Rect(0.0, 0.0, 100.0, 100.0), orientation = 90)
        item.flipY = true
        val next = image(300, 100)
        item.replaceKeepingFrame(next)
        assertSame(next, item.image)
        assertEquals(Rect(0.0, 0.0, 100.0, 100.0), item.rect)
        assertEquals(0, item.orientation)
        assertFalse(item.flipY)
        assertNotNull(item.crop)
    }

    @Test fun oneSnapshotUndoesEveryImageEdit() {
        val item = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0))
        val before = item.snapshotGeometry()
        item.crop = ImageCrop(0.1, 0.1, 0.9, 0.9)
        item.rotateQuarter(true)
        item.flipShown(true)
        item.image = image(10, 10)
        val after = item.snapshotGeometry()
        val cmd = TransformItems(listOf(item), listOf(before), listOf(after))
        cmd.undo()
        assertNull(item.crop)
        assertEquals(0, item.orientation)
        assertFalse(item.flipX || item.flipY)
        assertEquals(400, item.image.width)
        cmd.redo()
        assertEquals(90, item.orientation)
        assertEquals(10, item.image.width)
    }

    @Test fun deepCopyCarriesTheEdits() {
        val item = ImageItem(image(), Rect(0.0, 0.0, 40.0, 20.0), crop = ImageCrop(0.0, 0.0, 0.5, 0.5), flipX = true)
        val copy = item.deepCopy(com.xnotes.core.FakeTextMeasurer()) as ImageItem
        assertEquals(item.crop, copy.crop)
        assertTrue(copy.flipX)
    }

    @Test fun mirroredPolygonShapesMirrorTheirVertices() {
        val tri = ShapeItem.poly(
            com.xnotes.core.tools.ShapeKind.POLYGON,
            listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 10.0)),
            Rgba(0, 0, 0, 255),
        )
        tri.applyTransform(Affine.scaleAbout(Pt(5.0, 5.0), -1.0, 1.0))
        val v = tri.vertices()!!
        // The right angle that sat at the left now sits at the right.
        assertTrue(v.any { kotlin.math.abs(it.x - 10.0) < eps && kotlin.math.abs(it.y - 0.0) < eps })
        assertTrue(v.any { kotlin.math.abs(it.x - 10.0) < eps && kotlin.math.abs(it.y - 10.0) < eps })
        val up = ShapeItem(com.xnotes.core.tools.ShapeKind.TRIANGLE, Pt(0.0, 0.0), Pt(10.0, 10.0), Rgba(0, 0, 0, 255))
        up.applyTransform(Affine.scaleAbout(Pt(5.0, 5.0), 1.0, -1.0))
        assertEquals(com.xnotes.core.tools.ShapeKind.POLYGON, up.shape)
        // The apex that was at the top is now at the bottom.
        assertTrue(up.vertices()!!.any { kotlin.math.abs(it.x - 5.0) < eps && kotlin.math.abs(it.y - 10.0) < eps })
    }
}
