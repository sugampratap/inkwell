package com.xnotes.core.infinite

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.tools.ShapeKind

/**
 * Turns a [ShapeItem] into triangles: the fill first, then the outline over it, each with its own
 * colour, exactly as the paged renderer paints them.
 *
 * The outline needs no path stroker. Every kind reduces to a polyline, closed or open, and a
 * constant-width ribbon with a disc at each vertex draws the same silhouette a round-capped,
 * round-joined pen would. Only the fill needs real triangulation, and three of the four fillable
 * kinds are analytic; a recognized polygon is the one case that ear-clips, and it arrives with a
 * few dozen vertices at most.
 *
 * Pure Kotlin, so the geometry is unit-testable without a canvas.
 */
object ShapeTessellator {

    fun tessellate(shape: ShapeItem, tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE): List<MeshPart> {
        val parts = ArrayList<MeshPart>(2)
        shape.fillRgba?.let { fill ->
            val mesh = fillMesh(shape, tolerance)
            if (!mesh.isEmpty) parts.add(MeshPart(mesh, fill, InkPass.OPAQUE))
        }
        if (shape.graphite) return graphite(shape, tolerance, parts)
        val outline = outlineMesh(shape, tolerance)
        if (!outline.isEmpty) parts.add(MeshPart(outline, shape.strokeRgba, InkPass.OPAQUE))
        return parts
    }

    /**
     * A pencil shape's outline ([ShapeItem.graphite]), as [ItemMesher] meshes a pencil stroke: the
     * outline at its full width and then its pressed core, each stencilled and covered once through
     * the grain at its own alpha. Both run down [ShapeItem.graphitePath], the path the paged
     * canvas fills, arrowhead included, so the outline is one shape and never darkens where it
     * meets itself.
     */
    private fun graphite(shape: ShapeItem, tolerance: Double, parts: ArrayList<MeshPart>): List<MeshPart> {
        val path = shape.graphitePath()
        val outer = MeshBuilder().apply { polylineRibbon(path, shape.strokeWidth / 2.0, closed = false, tolerance = tolerance) }.build()
        if (outer.isEmpty) return parts
        val outerColor = shape.strokeRgba.scaleAlpha(Graphite.OUTER_ALPHA)
        parts.add(MeshPart(outer, outerColor, InkPass.GRAPHITE))
        val coreHalf = shape.graphiteCoreHalfWidth()
        if (coreHalf <= 0.0) return parts
        val core = MeshBuilder().apply { polylineRibbon(path, coreHalf, closed = false, tolerance = tolerance) }.build()
        if (!core.isEmpty) {
            parts.add(
                MeshPart(core, shape.strokeRgba.scaleAlpha(Graphite.CORE_ALPHA), InkPass.GRAPHITE, under = outerColor.a / 255.0),
            )
        }
        return parts
    }

    /** The closed shape's interior. Open kinds fill nothing. */
    fun fillMesh(shape: ShapeItem, tolerance: Double): MeshData {
        val b = MeshBuilder()
        val box = shape.box
        when (shape.shape) {
            ShapeKind.RECTANGLE -> b.rect(box.x, box.y, box.w, box.h)
            ShapeKind.ELLIPSE, ShapeKind.CIRCLE ->
                b.ellipse(box.centerX, box.centerY, box.w / 2.0, box.h / 2.0, tolerance)
            ShapeKind.TRIANGLE -> {
                val v = shape.triangleVertices()
                if (v.size == 3) {
                    b.triangle(
                        b.vertex(v[0].x, v[0].y),
                        b.vertex(v[1].x, v[1].y),
                        b.vertex(v[2].x, v[2].y),
                    )
                }
            }
            ShapeKind.POLYGON -> b.polygon(shape.absPoints())
            ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.POLYLINE, ShapeKind.CURVE -> Unit
        }
        return b.build()
    }

    /** The shape's stroked outline, plus an arrow's head where it has one. */
    fun outlineMesh(shape: ShapeItem, tolerance: Double): MeshData {
        val b = MeshBuilder()
        val half = shape.strokeWidth / 2.0
        val (points, closed) = outlinePath(shape)
        if (points.size >= 2) {
            if (shape.dashed) {
                for (run in MeshBuilder.dashRuns(points, shape.dashLength, shape.dashGap, closed)) {
                    b.polylineRibbon(run, half, closed = false, tolerance = tolerance)
                }
            } else {
                b.polylineRibbon(points, half, closed, tolerance)
            }
        }
        // The arrowhead is always solid: a dash break across a short barb would maim the point.
        if (shape.shape == ShapeKind.ARROW) {
            val head = shape.arrowHead()
            if (head.size == 3) b.polylineRibbon(head, half, closed = false, tolerance = tolerance)
        }
        return b.build()
    }

    /** The polyline a shape's outline traces, and whether it closes back on itself. */
    fun outlinePath(shape: ShapeItem): Pair<List<Pt>, Boolean> {
        val box = shape.box
        return when (shape.shape) {
            ShapeKind.LINE, ShapeKind.ARROW -> listOf(shape.start, shape.end) to false
            ShapeKind.RECTANGLE -> listOf(
                Pt(box.left, box.top),
                Pt(box.right, box.top),
                Pt(box.right, box.bottom),
                Pt(box.left, box.bottom),
            ) to true
            ShapeKind.ELLIPSE, ShapeKind.CIRCLE -> shape.ellipsePolygon(ELLIPSE_SEGMENTS) to true
            ShapeKind.TRIANGLE -> shape.triangleVertices() to true
            ShapeKind.POLYGON -> shape.absPoints() to true
            ShapeKind.POLYLINE, ShapeKind.CURVE -> shape.absPoints() to false
        }
    }

    /**
     * Segments an ellipse outline is cut into. Fixed rather than derived from the radius, matching
     * the paged renderer's own polygon so a circle drawn on either canvas has the same silhouette.
     */
    const val ELLIPSE_SEGMENTS = 96
}
