package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RulerTest {

    private fun ruler(angle: Double = 0.0) = Ruler().apply {
        center = Pt(100.0, 100.0)
        angleRad = angle
        thicknessPx = 20.0   // ±10 across
        visible = true
    }

    @Test fun signedAcrossAndAlong() {
        val r = ruler()
        // Horizontal: normal is (0,1), direction (1,0).
        assertEquals(8.0, r.signedAcross(Pt(140.0, 108.0)), 1e-9)   // 8 below the centre line
        assertEquals(-5.0, r.signedAcross(Pt(140.0, 95.0)), 1e-9)
        assertEquals(40.0, r.along(Pt(140.0, 108.0)), 1e-9)         // 40 along from centre
    }

    @Test fun bodyContainsIgnoresLength() {
        val r = ruler()
        assertTrue(r.bodyContains(Pt(100.0, 100.0)))      // centre
        assertTrue(r.bodyContains(Pt(99999.0, 109.0)))    // far along but within thickness -> still on body
        assertFalse(r.bodyContains(Pt(100.0, 115.0)))     // beyond thickness
    }

    @Test fun projectToEdgeKeepsAlongClampsAcross() {
        val r = ruler()
        // Top edge is across = +10 (y = 110); project keeps x, sets y to 110.
        val top = r.projectToEdge(Pt(140.0, 130.0), topSide = true)
        assertEquals(140.0, top.x, 1e-9)
        assertEquals(110.0, top.y, 1e-9)
        val bot = r.projectToEdge(Pt(70.0, 50.0), topSide = false)
        assertEquals(70.0, bot.x, 1e-9)
        assertEquals(90.0, bot.y, 1e-9)
    }

    @Test fun projectToEdgeRotated90() {
        // direction (0,1), normal (-1,0): the +normal edge is at x = 90.
        val r = ruler(PI / 2)
        val top = r.projectToEdge(Pt(40.0, 160.0), topSide = true)
        assertEquals(90.0, top.x, 1e-9)
        assertEquals(160.0, top.y, 1e-9)   // along (y) preserved
    }

    @Test fun bodyQuadSpan() {
        val q = ruler().bodyQuad(-30.0, 50.0)
        assertEquals(Pt(70.0, 110.0), q[0])
        assertEquals(Pt(150.0, 110.0), q[1])
        assertEquals(Pt(150.0, 90.0), q[2])
        assertEquals(Pt(70.0, 90.0), q[3])
    }

    @Test fun handles() {
        val r = ruler() // centre (100,100), horizontal, thickness 20 -> handleRadius 4
        val hs = r.handleCenters(50.0)
        assertEquals(Pt(150.0, 100.0), hs[0]) // +direction
        assertEquals(Pt(50.0, 100.0), hs[1])  // −direction
        assertEquals(0, r.hitHandle(Pt(150.0, 100.0), 50.0, r.handleRadiusPx()))
        assertEquals(1, r.hitHandle(Pt(50.0, 100.0), 50.0, r.handleRadiusPx()))
        assertNull(r.hitHandle(Pt(100.0, 100.0), 50.0, r.handleRadiusPx())) // centre misses both
    }

    @Test fun hitButton() {
        val r = ruler()
        val centers = r.buttonCenters()
        assertEquals(2, centers.size)
        val (btn, at) = centers[0]
        assertEquals(btn, r.hitButton(at, r.buttonRadiusPx()))
        assertNull(r.hitButton(Pt(100.0, 200.0), r.buttonRadiusPx()))
    }

    @Test fun snapToAxesPullsNearbyAngles() {
        val snap = Math.toRadians(Ruler.AXIS_SNAP_DEG)
        assertEquals(0.0, Ruler.snapToAxes(snap * 0.9), 1e-9)
        assertEquals(0.0, Ruler.snapToAxes(-snap * 0.9), 1e-9)
        assertEquals(PI / 2, Ruler.snapToAxes(PI / 2 - snap * 0.5), 1e-9)
        assertEquals(PI, Ruler.snapToAxes(PI + snap * 0.9), 1e-9)
        assertEquals(-PI / 2, Ruler.snapToAxes(-PI / 2 + snap * 0.9), 1e-9)
    }

    @Test fun snapToAxesLeavesFarAnglesAlone() {
        val off = Math.toRadians(10.0)
        assertEquals(off, Ruler.snapToAxes(off), 1e-9)
        assertEquals(PI / 4, Ruler.snapToAxes(PI / 4), 1e-9)          // 45 never snaps
        assertEquals(PI / 2 + off, Ruler.snapToAxes(PI / 2 + off), 1e-9)
    }

    @Test fun snapToAxesHandlesUnwoundAngles() {
        // Multi-turn angles from the two-finger rotate accumulate past 2*PI.
        val snap = Math.toRadians(Ruler.AXIS_SNAP_DEG)
        assertEquals(5 * PI / 2, Ruler.snapToAxes(5 * PI / 2 + snap * 0.5), 1e-9)
        assertEquals(-3 * PI, Ruler.snapToAxes(-3 * PI - snap * 0.5), 1e-9)
    }

    @Test fun pxToCm() {
        // 150 px at 150 dpi = 1 inch = 2.54 cm.
        assertEquals(2.54, RulerMath.contentPxToCm(150.0, 150), 1e-9)
    }

    @Test fun viewportLenToCmScalesWithZoom() {
        assertEquals(2.54, RulerMath.viewportLenToCm(150.0, 1.0, 150), 1e-9)
        assertEquals(1.27, RulerMath.viewportLenToCm(150.0, 2.0, 150), 1e-9)
    }

    // --- B2: readouts and labels (TO Frame 7) ---

    @Test fun anglesRoundBeforeTheyWrapSoNoneReads360() {
        assertEquals(0, RulerMath.ccwDegrees(-0.2))
        assertEquals(0, RulerMath.cwDegrees(-0.2))
        assertEquals(0, RulerMath.ccwDegrees(359.6))
        assertEquals(0, RulerMath.cwDegrees(359.6))
        assertEquals(0, RulerMath.cwDegrees(0.4))
    }

    @Test fun eachHandleReadsItsOwnWay() {
        assertEquals(330, RulerMath.ccwDegrees(30.0))
        assertEquals(30, RulerMath.cwDegrees(30.0))
        assertEquals(270, RulerMath.ccwDegrees(90.0))
        assertEquals(90, RulerMath.cwDegrees(90.0))
        assertEquals(90, RulerMath.ccwDegrees(-90.0))
        assertEquals(270, RulerMath.cwDegrees(-90.0))
        assertEquals("42°", RulerMath.degreeLabel(42))
    }

    @Test fun tickLabelsKeepClearOfTheLocks() {
        val d = 2.0
        val dist = 240.0 * d
        assertFalse(RulerMath.tickLabelShown(0.0, dist, d))
        assertFalse(RulerMath.tickLabelShown(56.0 * d, dist, d))
        assertFalse(RulerMath.tickLabelShown(-56.0 * d, dist, d))
        assertTrue(RulerMath.tickLabelShown(57.0 * d, dist, d))
    }

    @Test fun tickLabelsKeepClearOfTheHandlesAndTheirAngles() {
        val d = 2.0
        val dist = 240.0 * d
        assertFalse(RulerMath.tickLabelShown(dist, dist, d))
        assertFalse(RulerMath.tickLabelShown(dist + 30.0 * d, dist, d))
        assertFalse(RulerMath.tickLabelShown(dist + 50.0 * d, dist, d))
        assertFalse(RulerMath.tickLabelShown(-(dist + 84.0 * d), dist, d))
        assertTrue(RulerMath.tickLabelShown(dist + 85.0 * d, dist, d))
        assertTrue(RulerMath.tickLabelShown(dist - 31.0 * d, dist, d))
    }

    @Test fun tickLabelsKeepClearOfAPillPushedBackOnScreen() {
        // A level ruler centred at (700, 400) on a 1280 × 800 screen, its +handle 20 dp past the right edge: the angle
        // pill would sit off screen, so pillRect slides it back to x 1204–1278, over labels the band rule leaves in.
        val d = 2.0
        val cx = 700.0
        val cy = 400.0
        val dist = 620.0
        val pill = RulerMath.pillRect(cx + dist + 100.0, cy, 30.0, d, 1280.0, 800.0)
        assertEquals(1204.0, pill.x, 1e-9)
        val far = RulerMath.pillRect(cx - dist - 100.0, cy, 30.0, d, 1280.0, 800.0)
        val w = 14.0
        val h = 20.0
        val under = 520.0 // x 1220, inside the pill
        assertTrue(RulerMath.tickLabelShown(under, dist, d))
        assertFalse(RulerMath.tickLabelShown(under, dist, d, cx + under, cy, w, h, pill, far))
        // Within 4 dp (8 px) of the pill's edge still clears; past that the label shows.
        assertFalse(RulerMath.tickLabelShown(490.0, dist, d, cx + 490.0, cy, w, h, pill, far)) // right edge 1197
        assertTrue(RulerMath.tickLabelShown(480.0, dist, d, cx + 480.0, cy, w, h, pill, far)) // right edge 1187
        // The band rule still holds where the pills are not.
        assertFalse(RulerMath.tickLabelShown(0.0, dist, d, cx, cy, w, h, pill, far))
        assertFalse(RulerMath.tickLabelShown(dist, dist, d, cx + dist, cy, w, h, pill, far))
    }

    @Test fun aLabelClearsAPillByFourDp() {
        val d = 2.0
        val pill = Rect(100.0, 100.0, 74.0, 56.0)
        // A 10 × 10 box: clear once its edge is more than 8 px (4 dp) from the pill's, on any side.
        assertTrue(RulerMath.labelClearOf(pill, 100.0 - 8.0 - 5.0 - 0.1, 128.0, 10.0, 10.0, d))
        assertFalse(RulerMath.labelClearOf(pill, 100.0 - 8.0 - 5.0 + 0.1, 128.0, 10.0, 10.0, d))
        assertTrue(RulerMath.labelClearOf(pill, 137.0, 156.0 + 8.0 + 5.0 + 0.1, 10.0, 10.0, d))
        assertFalse(RulerMath.labelClearOf(pill, 137.0, 156.0 + 8.0 + 5.0 - 0.1, 10.0, 10.0, d))
        assertFalse(RulerMath.labelClearOf(pill, 137.0, 128.0, 10.0, 10.0, d))
    }

    @Test fun handlesDimWhileTheAngleIsLocked() {
        assertEquals(0.4, RulerMath.handleAlpha(lockAngle = true), 0.0)
        assertEquals(1.0, RulerMath.handleAlpha(lockAngle = false), 0.0)
    }

    @Test fun aReadoutPillIs28TallWith11EachSide() {
        assertEquals(Rect(63.0, 72.0, 74.0, 56.0), RulerMath.pillRect(100.0, 100.0, 30.0, 2.0, 1280.0, 800.0))
    }

    @Test fun aReadoutPillStaysOnScreen() {
        assertEquals(1204.0, RulerMath.pillRect(1270.0, 100.0, 30.0, 2.0, 1280.0, 800.0).x, 1e-9)
        val corner = RulerMath.pillRect(10.0, 5.0, 30.0, 2.0, 1280.0, 800.0)
        assertEquals(2.0, corner.x, 1e-9)
        assertEquals(2.0, corner.y, 1e-9)
    }

    @Test fun aPillOutlineHasRoundEndsInsideItsBox() {
        val pts = RulerMath.pillPoints(74.0, 56.0, segments = 8)
        assertEquals(18, pts.size)
        assertEquals(46.0, pts[0].x, 1e-9)
        assertEquals(0.0, pts[0].y, 1e-9)
        assertEquals(74.0, pts[4].x, 1e-9)
        assertEquals(28.0, pts[4].y, 1e-9)
        for (p in pts) assertTrue("$p", p.x in -1e-9..74.0 + 1e-9 && p.y in -1e-9..56.0 + 1e-9)
    }

    @Test fun theLengthReadsInTenthsOfACentimetre() {
        assertEquals(124, RulerMath.lengthTenths(12.36))
        assertEquals("12.4 cm", RulerMath.lengthLabel(124))
        assertEquals("0.0 cm", RulerMath.lengthLabel(RulerMath.lengthTenths(0.04)))
    }

    @Test fun labelSizesAreDpInThePointsTheRendererTakes() {
        // FontSpec sizes are points at 150 dpi page px (AndroidText.POINTS_TO_PX = 150/72); viewport px = dp × density.
        assertEquals(10.08, RulerMath.pointsForDp(10.5, 2.0), 1e-9)
    }
}
