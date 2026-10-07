package com.xnotes.core.infinite

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.TapeItem

/**
 * Turns a [TapeItem] into the GL canvas's triangles, from the very polygons the paged renderer
 * fills ([TapeItem.shape]), so a strip looks the same laid on either surface.
 *
 * Covered, the strip and its print are opaque and go straight into the batch; only the two shadow
 * layers composite, each once. Peeled back, every layer is faint, so each is stencilled and covered
 * at its own alpha: the print's bands overlap the ghosted body, and one part per layer keeps the
 * crossing from darkening twice inside it.
 */
object TapeTessellator {

    fun mesh(tape: TapeItem, tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE): MeshedItem? {
        val s = tape.shape()
        if (s.body.size < 3) return null
        val parts = ArrayList<MeshPart>(5)
        fun fill(points: List<Pt>): MeshData = MeshBuilder(points.size, points.size * 3).apply { polygon(points) }.build()
        fun print(): MeshData {
            val b = MeshBuilder()
            for (poly in s.pattern) b.polygon(poly)
            for (dot in s.dots) b.circle(dot.center.x, dot.center.y, dot.radius, tolerance)
            return b.build()
        }
        fun edge(width: Double): MeshData =
            MeshBuilder().apply { polylineRibbon(s.body, width / 2.0, closed = true, tolerance = tolerance) }.build()
        fun add(mesh: MeshData, color: com.xnotes.core.model.Rgba, pass: InkPass) {
            if (!mesh.isEmpty) parts.add(MeshPart(mesh, color, pass))
        }
        if (tape.revealed) {
            add(fill(s.body), TapeItem.ghostFill(tape.color), InkPass.TRANSLUCENT)
            add(print(), TapeItem.ghostPattern(tape.color), InkPass.TRANSLUCENT)
            add(edge(TapeItem.outlineWidth(tape.width)), TapeItem.ghostEdge(tape.color), InkPass.TRANSLUCENT)
        } else {
            add(fill(s.shadowFar), SHADOW_FAR, InkPass.TRANSLUCENT)
            add(fill(s.shadowNear), SHADOW_NEAR, InkPass.TRANSLUCENT)
            add(fill(s.body), tape.color.withAlpha(255), InkPass.OPAQUE)
            add(print(), TapeItem.patternColor(tape.color), InkPass.OPAQUE)
            add(edge(TapeItem.edgeWidth(tape.width)), TapeItem.edgeColor(tape.color), InkPass.OPAQUE)
        }
        if (parts.isEmpty()) return null
        // The strip is a fill, wide at any zoom worth drawing, so it never needs the hairline fade.
        return MeshedItem(parts, tape.paintBounds(), tape.width / 2.0)
    }

    private val SHADOW_FAR = com.xnotes.core.model.Rgba(0, 0, 0, 16)
    private val SHADOW_NEAR = com.xnotes.core.model.Rgba(0, 0, 0, 30)
}
