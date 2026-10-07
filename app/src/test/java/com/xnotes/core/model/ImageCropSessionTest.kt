package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageCropSessionTest {

    private val eps = 1e-6

    private fun item(rect: Rect = Rect(100.0, 100.0, 200.0, 100.0)) =
        ImageItem(ImageData(File("x.png"), 400, 200), rect)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.w, actual.w, eps)
        assertEquals(expected.h, actual.h, eps)
    }

    @Test fun uncroppedPictureIsItsOwnFullBox() {
        val s = ImageCropSession(item())
        assertRect(Rect(100.0, 100.0, 200.0, 100.0), s.full)
        assertTrue(s.isWhole)
        assertFalse(s.changed)
    }

    @Test fun draggingTheLeftEdgeCropsTheLeftOfTheSource() {
        val it = item()
        val s = ImageCropSession(it)
        s.drag(CropHandle.L, s.box, Pt(100.0, 150.0), Pt(150.0, 150.0), minSide = 10.0)
        assertRect(Rect(150.0, 100.0, 150.0, 100.0), s.box)
        s.applyResult()
        val c = it.crop!!
        assertEquals(0.25, c.l, eps)
        assertEquals(1.0, c.r, eps)
        assertRect(Rect(150.0, 100.0, 150.0, 100.0), it.rect)
    }

    @Test fun reopeningACropShowsTheWholePictureAroundIt() {
        val it = item()
        it.crop = ImageCrop(0.25, 0.0, 1.0, 1.0)
        it.rect = Rect(150.0, 100.0, 150.0, 100.0)
        val s = ImageCropSession(it)
        assertRect(Rect(100.0, 100.0, 200.0, 100.0), s.full)
        s.applyFull()
        assertNull(it.crop)
        assertRect(Rect(100.0, 100.0, 200.0, 100.0), it.rect)
        s.reset()
        s.applyResult()
        assertNull(it.crop)
    }

    @Test fun cancelPutsEverythingBack() {
        val it = item()
        it.crop = ImageCrop(0.0, 0.0, 0.5, 1.0)
        it.rect = Rect(0.0, 0.0, 100.0, 100.0)
        val s = ImageCropSession(it)
        s.applyFull()
        s.restore()
        assertEquals(ImageCrop(0.0, 0.0, 0.5, 1.0), it.crop)
        assertRect(Rect(0.0, 0.0, 100.0, 100.0), it.rect)
    }

    @Test fun theBoxNeverLeavesThePictureOrCollapses() {
        val s = ImageCropSession(item())
        s.drag(CropHandle.TL, s.box, Pt(100.0, 100.0), Pt(-500.0, -500.0), minSide = 10.0)
        assertRect(s.full, s.box)
        s.drag(CropHandle.R, s.box, Pt(300.0, 150.0), Pt(0.0, 150.0), minSide = 10.0)
        assertEquals(10.0, s.box.w, eps)
        val moved = s.box
        s.drag(CropHandle.MOVE, moved, Pt(105.0, 150.0), Pt(10_000.0, 150.0), minSide = 10.0)
        assertEquals(300.0, s.box.right, eps)
    }

    @Test fun aspectPresetsHoldTheirShape() {
        val s = ImageCropSession(item())
        s.setAspect(1.0)
        assertEquals(s.box.w, s.box.h, eps)
        assertEquals(100.0, s.box.h, eps)
        assertEquals(200.0, s.box.centerX, eps)
        s.drag(CropHandle.BR, s.box, Pt(s.box.right, s.box.bottom), Pt(s.box.right - 40.0, s.box.bottom - 10.0), minSide = 10.0)
        assertEquals(s.box.w, s.box.h, eps)
        assertEquals(75.0, s.box.w, eps)
        s.drag(CropHandle.L, s.box, Pt(s.box.left, s.box.centerY), Pt(s.box.left - 20.0, s.box.centerY), minSide = 10.0)
        assertEquals(s.box.w, s.box.h, eps)
        assertTrue(s.box.top >= s.full.top - eps && s.box.bottom <= s.full.bottom + eps)
    }

    @Test fun handlesAreFoundByTheirCornersAndEdges() {
        val s = ImageCropSession(item())
        assertEquals(CropHandle.TL, s.hit(Pt(102.0, 98.0), 5.0))
        assertEquals(CropHandle.R, s.hit(Pt(299.0, 150.0), 5.0))
        assertEquals(CropHandle.MOVE, s.hit(Pt(200.0, 150.0), 5.0))
        assertNull(s.hit(Pt(500.0, 500.0), 5.0))
    }

    @Test fun aTurnedPictureCropsInItsOwnFrame() {
        val it = item()
        it.angle = Math.PI / 2.0
        val s = ImageCropSession(it)
        // Cropping the frame's right half away: on screen, the picture is turned a quarter, so its
        // centre moves up rather than left.
        s.drag(CropHandle.R, s.box, Pt(300.0, 150.0), Pt(200.0, 150.0), minSide = 10.0)
        s.applyResult()
        assertEquals(0.5, it.crop!!.r, eps)
        assertEquals(200.0, it.rect.centerX, eps)
        assertEquals(100.0, it.rect.centerY, eps)
        assertEquals(Math.PI / 2.0, it.angle, eps)
    }

    @Test fun aMirroredSidewaysPictureMapsTheCropBackToItsSource() {
        val it = ImageItem(ImageData(File("x.png"), 400, 200), Rect(0.0, 0.0, 100.0, 200.0), orientation = 90, flipX = true)
        val s = ImageCropSession(it)
        // Keep the top half of what is shown.
        s.drag(CropHandle.B, s.box, Pt(50.0, 200.0), Pt(50.0, 100.0), minSide = 10.0)
        s.applyResult()
        val back = ImageGeometry.sourceRectToDisplay(it.crop!!, 90, true, false)
        assertEquals(0.0, back.t, eps)
        assertEquals(0.5, back.b, eps)
        assertEquals(0.0, back.l, eps)
        assertEquals(1.0, back.r, eps)
    }
}
