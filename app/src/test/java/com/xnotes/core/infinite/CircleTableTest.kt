package com.xnotes.core.infinite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The disc and ellipse fans read their corners from a table now rather than from the angle. The
 * table has to give back exactly the vertices the angle gave, to the bit, or every round cap and
 * join on the canvas would move.
 */
class CircleTableTest {

    /** The fan [MeshBuilder.circle] built before the table: centre, then one vertex per segment. */
    private fun referenceCircle(cx: Double, cy: Double, radius: Double, tolerance: Double): Pair<DoubleArray, DoubleArray> {
        val segments = MeshBuilder.circleSegments(radius, tolerance)
        val pos = ArrayList<Double>()
        val off = ArrayList<Double>()
        pos += cx; pos += cy; off += 0.0; off += 0.0
        val step = 2.0 * PI / segments
        for (k in 0 until segments) {
            val a = k * step
            val dx = radius * cos(a)
            val dy = radius * sin(a)
            pos += cx + dx; pos += cy + dy
            off += dx; off += dy
        }
        return pos.toDoubleArray() to off.toDoubleArray()
    }

    private fun referenceEllipse(cx: Double, cy: Double, rx: Double, ry: Double, tolerance: Double): DoubleArray {
        val segments = MeshBuilder.circleSegments(maxOf(rx, ry), tolerance)
        val pos = ArrayList<Double>()
        pos += cx; pos += cy
        val step = 2.0 * PI / segments
        for (k in 0 until segments) {
            val a = k * step
            pos += cx + rx * cos(a); pos += cy + ry * sin(a)
        }
        return pos.toDoubleArray()
    }

    @Test fun everySegmentCountsTableIsTheAngleItself() {
        for (n in MeshBuilder.MIN_CIRCLE_SEGMENTS..MeshBuilder.MAX_CIRCLE_SEGMENTS) {
            val step = 2.0 * PI / n
            val c = MeshBuilder.unitCos(n)
            val s = MeshBuilder.unitSin(n)
            assertEquals(n, c.size)
            for (k in 0 until n) {
                assertEquals("cos $k/$n", cos(k * step).toRawBits(), c[k].toRawBits())
                assertEquals("sin $k/$n", sin(k * step).toRawBits(), s[k].toRawBits())
            }
        }
    }

    @Test fun aDiscIsBitForBitTheDiscTheAngleBuilt() {
        val random = java.util.Random(3)
        val tolerances = doubleArrayOf(StrokeTessellator.DEFAULT_TOLERANCE, 0.05, 0.25, 1.0, 4.0)
        repeat(400) {
            val cx = (random.nextDouble() - 0.5) * 2e6
            val cy = (random.nextDouble() - 0.5) * 2e6
            val r = 0.01 + random.nextDouble() * 60.0
            val tol = tolerances[random.nextInt(tolerances.size)]
            val b = MeshBuilder()
            b.circle(cx, cy, r, tol)
            val mesh = b.build()
            val (pos, off) = referenceCircle(cx, cy, r, tol)
            assertArrayEquals(pos, mesh.positions, 0.0)
            assertArrayEquals(off, mesh.offsets, 0.0)
            assertEquals(MeshBuilder.circleSegments(r, tol) * 3, mesh.indices.size)
        }
    }

    @Test fun anEllipseIsBitForBitTheEllipseTheAngleBuilt() {
        val random = java.util.Random(5)
        repeat(200) {
            val rx = 0.5 + random.nextDouble() * 200.0
            val ry = 0.5 + random.nextDouble() * 200.0
            val tol = 0.01 + random.nextDouble()
            val b = MeshBuilder()
            b.ellipse(10.0, -20.0, rx, ry, tol)
            assertArrayEquals(referenceEllipse(10.0, -20.0, rx, ry, tol), b.build().positions, 0.0)
        }
    }

    @Test fun aScreenRelativeToleranceWouldNotKeepTheHighlightersDiscsRound() {
        // Geometry is baked once and then drawn at every zoom, so a disc's fineness has to hold at
        // the deepest zoom it will be seen at, not the one it was drawn at. A highlighter's 8 px
        // disc gets the 64-segment cap, and stays well under half a device pixel of chord error at
        // 400 %. Cut for half a pixel at 100 % instead it would be a 9-gon, a couple of pixels out
        // at 400 %: visibly faceted. So the tolerance stays, and only the trig goes.
        val radius = 8.0
        val baked = MeshBuilder.circleSegments(radius, StrokeTessellator.DEFAULT_TOLERANCE)
        assertEquals(MeshBuilder.MAX_CIRCLE_SEGMENTS, baked)
        val bakedError = radius * (1.0 - cos(PI / baked))
        assertTrue("baked disc at 400 %: ${bakedError * 4.0} px", bakedError * 4.0 < 0.5)

        val screen = MeshBuilder.circleSegments(radius, 0.5)
        val screenError = radius * (1.0 - cos(PI / screen))
        assertTrue("a $screen-gon at 400 % is only ${screenError * 4.0} px out", screenError * 4.0 > 1.0)
    }
}
