package com.xnotes.core.model

import com.xnotes.core.FakeRenderer
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.tools.LassoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tape strip's geometry: where it is, what it hides, how it moves, and how it is laid out. */
class TapeItemTest {

    private fun level(pattern: TapePattern = TapePattern.STRIPES) =
        TapeItem(Pt(100.0, 200.0), Pt(300.0, 200.0), 40.0, Rgba(250, 220, 120), pattern, seed = 42)

    /** A strip at 45 degrees, from (0,0) to (100,100), 20 across. */
    private fun diagonal() = TapeItem(Pt(0.0, 0.0), Pt(100.0, 100.0), 20.0, Rgba(140, 196, 236), seed = 1)

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) = assertEquals(expected, actual, eps)

    // --- hit testing ---

    @Test fun aLevelStripCoversItsOwnRectangle() {
        val t = level()
        val b = t.bounds()
        near(100.0, b.left); near(180.0, b.top); near(300.0, b.right); near(220.0, b.bottom)
        assertTrue(t.contains(Pt(200.0, 200.0)))
        assertTrue(t.contains(Pt(101.0, 219.0)))
        assertFalse(t.contains(Pt(200.0, 230.0)))
        assertFalse(t.contains(Pt(320.0, 200.0)))
        near(200.0, t.centroid().x); near(200.0, t.centroid().y)
    }

    @Test fun aTurnedStripIsHitAlongItselfNotAcrossItsBox() {
        val t = diagonal()
        assertTrue(t.contains(Pt(50.0, 50.0)))
        assertTrue(t.contains(Pt(55.0, 45.0))) // 7 px off the spine, inside the 10 px half-width
        // Inside the bounding box but far off the strip.
        assertTrue(t.bounds().contains(Pt(90.0, 10.0)))
        assertFalse(t.contains(Pt(90.0, 10.0)))
    }

    @Test fun theEraserTouchesOnlyWhenItReachesTheStrip() {
        val t = diagonal()
        // (90,10) is (80/sqrt2) = 56.6 px from the spine, so 46.6 from the edge.
        assertFalse(t.intersectsCircle(90.0, 10.0, 40.0))
        assertTrue(t.intersectsCircle(90.0, 10.0, 50.0))
        // Past the far end along the spine.
        assertFalse(t.intersectsCircle(110.0, 110.0, 10.0))
        assertTrue(t.intersectsCircle(110.0, 110.0, 15.0))
    }

    // --- pulling it out ---

    @Test fun aNearlyLevelPullLiesLevelAndANearlyPlumbOneLiesPlumb() {
        val a = Pt(0.0, 0.0)
        assertEquals(Pt(200.0, 0.0), TapeItem.snapAxis(a, Pt(200.0, 15.0))) // 4.3 degrees
        assertEquals(Pt(0.0, 200.0), TapeItem.snapAxis(a, Pt(-12.0, 200.0)))
        assertEquals(Pt(200.0, 60.0), TapeItem.snapAxis(a, Pt(200.0, 60.0))) // 16.7 degrees: free
        assertEquals(a, TapeItem.snapAxis(a, a))
    }

    // --- moving it ---

    @Test fun aQuarterTurnKeepsTheWidthAndTurnsTheStrip() {
        val t = level()
        t.applyTransform(Affine.rotateAbout(Pt(200.0, 200.0), Math.PI / 2))
        near(40.0, t.width)
        near(200.0, t.start.x); near(100.0, t.start.y)
        near(200.0, t.end.x); near(300.0, t.end.y)
    }

    @Test fun stretchingAlongTheStripKeepsItsWidthAndAcrossItWidensIt() {
        val along = level()
        along.applyTransform(Affine.scaleAbout(Pt(100.0, 200.0), 2.0, 1.0))
        near(40.0, along.width)
        near(500.0, along.end.x)
        val across = level()
        across.applyTransform(Affine.scaleAbout(Pt(100.0, 200.0), 1.0, 1.5))
        near(60.0, across.width)
        val uniform = diagonal()
        uniform.applyTransform(Affine.scaleAbout(Pt.ZERO, 2.0, 2.0))
        near(40.0, uniform.width)
    }

    @Test fun aSnapshotPutsTheStripBack() {
        val t = level()
        val snap = t.snapshotGeometry()
        t.applyTransform(Affine.scaleAbout(Pt.ZERO, 3.0, 3.0))
        t.translate(10.0, 10.0)
        t.restoreGeometry(snap)
        assertEquals(Pt(100.0, 200.0), t.start)
        assertEquals(Pt(300.0, 200.0), t.end)
        near(40.0, t.width)
    }

    @Test fun aCopyIsItsOwnStripWithTheSameLookAndState() {
        val t = level(TapePattern.DOTS).also { it.revealed = true; it.locked = true }
        val c = t.deepCopy(FakeTextMeasurer()) as TapeItem
        assertNotSame(t, c)
        assertEquals(t.start, c.start)
        assertEquals(t.end, c.end)
        assertEquals(t.width, c.width, 0.0)
        assertEquals(t.color, c.color)
        assertEquals(TapePattern.DOTS, c.pattern)
        assertTrue(c.revealed)
        assertTrue(c.locked)
        assertEquals(t.seed, c.seed)
        c.translate(5.0, 0.0)
        assertEquals(Pt(100.0, 200.0), t.start)
    }

    @Test fun theLassoTakesTapeWithTheShapes() {
        assertTrue(LassoFilter.SHAPES.accepts(level()))
        assertTrue(LassoFilter.ALL.accepts(level()))
        assertFalse(LassoFilter.HANDWRITING.accepts(level()))
    }

    // --- its look ---

    @Test fun theOutlineStaysOnTheStripAndTheTornEndsAreDeterministic() {
        val t = level()
        val s = t.shape()
        val box = t.bounds().outset(1e-6)
        assertTrue(s.body.size > 8)
        assertTrue(s.body.all { box.contains(it) })
        // Same seed, same tear; another seed tears differently.
        assertEquals(s.body, TapeItem.buildShape(t.start, t.end, t.width, t.pattern, 42).body)
        assertFalse(s.body == TapeItem.buildShape(t.start, t.end, t.width, t.pattern, 43).body)
    }

    @Test fun everyPrintStaysInsideTheStrip() {
        for (pattern in TapePattern.entries) {
            val t = diagonal().also { it.pattern = pattern }
            val s = t.shape()
            for (poly in s.pattern) for (p in poly) assertTrue("$pattern $p", t.contains(p))
            for (d in s.dots) assertTrue(t.contains(d.center))
            when (pattern) {
                TapePattern.SOLID -> assertTrue(s.pattern.isEmpty() && s.dots.isEmpty())
                TapePattern.DOTS -> assertTrue(s.dots.isNotEmpty())
                else -> assertTrue(s.pattern.isNotEmpty())
            }
        }
    }

    @Test fun theLayoutIsCachedUntilTheStripChanges() {
        val t = level()
        val first = t.shape()
        assertSame(first, t.shape())
        t.end = Pt(320.0, 200.0)
        assertNotSame(first, t.shape())
    }

    @Test fun thePaintBoundsHoldTheShadow() {
        val t = level()
        val pb = t.paintBounds()
        val s = t.shape()
        for (p in s.shadowFar + s.shadowNear + s.body) assertTrue(pb.contains(p))
    }

    @Test fun coveredItPaintsShadowBodyAndPrintPeeledBackOnlyAGhost() {
        val covered = FakeRenderer()
        level().paint(covered)
        // Two shadow layers, the body, then the print, then the edge.
        assertTrue(covered.ops.count { it == "fillPolygon" } > 3)
        assertEquals("strokePolygon", covered.ops.last())
        val revealed = FakeRenderer()
        level().also { it.revealed = true }.paint(revealed)
        // No shadow when peeled back: one fewer pair of fills.
        assertEquals(covered.ops.count { it == "fillPolygon" } - 2, revealed.ops.count { it == "fillPolygon" })
        assertTrue(TapeItem.ghostPattern(Rgba(250, 220, 120)).a in 30..45) // about 15 percent
    }

    @Test fun aStripWithNoLengthYetStillLaysOut() {
        val t = TapeItem(Pt(10.0, 10.0), Pt(10.0, 10.0), 30.0, Rgba(0, 0, 0))
        assertTrue(t.shape().body.isNotEmpty())
        assertEquals(Rect(10.0, -5.0, 0.0, 30.0), t.bounds())
    }
}
