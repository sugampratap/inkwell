package com.xnotes.gl

import com.xnotes.core.infinite.InkPass
import com.xnotes.core.infinite.MeshPart

/**
 * Which live ink the front buffer ([GlWetPad]) takes, for both canvases.
 *
 * The pad rebuilds every pixel it touches from geometry, into a scratch cleared to transparent,
 * and is composited over the canvas as a premultiplied layer. So it can take anything that is
 * fully described by its own triangles: a pen's opaque ones, and the pencil's, whose two passes
 * and grain [WetPadGraphite] lays exactly as committed. What it cannot take is ink that composites
 * against what is under it (the highlighter's multiply, a translucent pen's layer, neon's halos),
 * because the pad has no copy of the page.
 *
 * Pure, so the rule is tested off the device.
 */
internal object WetPadRoute {

    /** Whether a part meshing to [pass] can go on the pad; the pencil only where it has the grain. */
    fun takes(pass: InkPass, graphite: Boolean): Boolean =
        pass == InkPass.OPAQUE || (graphite && pass == InkPass.GRAPHITE)

    /** Whether every one of [parts] can go on the pad, and there is something to draw. */
    fun takesAll(parts: List<MeshPart>, graphite: Boolean): Boolean {
        if (parts.isEmpty()) return false
        for (part in parts) if (!takes(part.pass, graphite)) return false
        return true
    }

    /**
     * Whether a live stroke is published as runs and a tail at all, which is what the pad draws:
     * ink that can be laid in pieces ([com.xnotes.core.model.Stroke.wetCacheable]), and the pencil.
     */
    fun inRuns(wetCacheable: Boolean, grain: Boolean): Boolean = wetCacheable || grain

    /**
     * The paged canvas's whole rule for a stroke meshing to [pass]: the pad takes it, and the view
     * is upright, since the pad's ink shader has room for a scroll and a zoom and not a turn.
     */
    fun paged(pass: InkPass, rotationDeg: Int, graphite: Boolean): Boolean =
        rotationDeg == 0 && takes(pass, graphite)
}
