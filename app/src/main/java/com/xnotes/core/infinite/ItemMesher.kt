package com.xnotes.core.infinite

import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.Graphite
import com.xnotes.core.stroke.RibbonPoints
import com.xnotes.core.stroke.ScaledRibbon
import com.xnotes.core.tools.Tool

/** How an item's geometry has to reach the framebuffer. */
enum class InkPass {
    /** Straight into the batched draw; the colour is baked into the vertices. */
    OPAQUE,

    /** Stencilled, then covered once at its own alpha, so self-overlap cannot darken. */
    TRANSLUCENT,

    /**
     * Stencilled with an inverting fill, then covered once. Coverage parity is the even-odd rule
     * exactly, for any outline at all, holes and self-crossings included, with no triangulation.
     * A vector fill falls back to this when the triangulator refuses the path; it costs a pair of
     * draw calls, so it is never the default.
     */
    EVEN_ODD,

    /** Like [TRANSLUCENT] but multiplied, and always drawn last, exactly as the paged canvas does. */
    MULTIPLY,

    /** [MULTIPLY]'s mirror image: screened, so the inverse highlighter lightens a dark page. */
    SCREEN,

    /** Blurred into a halo and composited under the item, which is what makes neon glow. */
    GLOW,

    /**
     * Like [TRANSLUCENT], but the cover samples the paper's grain in world space, so the ink takes
     * the tooth of the paper: the pencil's graphite ([com.xnotes.core.stroke.Graphite]).
     */
    GRAPHITE,
}

/**
 * Everything neon needs, from one run of triangles.
 *
 * All four layers are the same geometry: two blurred halos, the lit body, and the white core at a
 * fraction of the width. The renderer draws them from a single buffer slice by overriding the
 * colour and scaling the stored spine offset, so a neon stroke costs one tessellation and one copy
 * rather than three of each. That matters most while the pen is down, when the whole thing is
 * rebuilt on every sample.
 */
class GlowSpec(
    val wideRadius: Double,
    val wideAlpha: Double,
    val tightRadius: Double,
    val tightAlpha: Double,
    /** The lit tube, drawn over the halos. */
    val bodyColor: Rgba,
    /** The white-hot core, at [coreScale] of the body's width. */
    val coreColor: Rgba,
    val coreScale: Double,
)

/** One run of triangles in a single colour. An item is one or more of these, drawn in order. */
class MeshPart(
    val mesh: MeshData,
    /** Ink colour including its alpha; baked into the vertices or used as the cover, per [pass]. */
    val color: Rgba,
    val pass: InkPass,
    /** Set on a [InkPass.GLOW] part: the halos to build from this geometry. */
    val glow: GlowSpec? = null,
    /**
     * Set on a pencil's pressed core ([InkPass.GRAPHITE]): the alpha of the outer pass it is laid
     * over, which the front buffer needs to lay the two as the committed stroke does
     * ([com.xnotes.gl.WetPadGraphite]). Negative on every other part, the pencil's outer included.
     */
    val under: Double = -1.0,
)

/** An item turned into triangles, with everything the renderer needs to draw it. */
class MeshedItem(
    /** Back to front: a shape's fill, then its outline over it. */
    val parts: List<MeshPart>,
    /** Content-space region the item paints into, used for culling and for the cover quad. */
    val bounds: Rect,
    /**
     * The item's thinnest half-width in content pixels, or 0 when it has no line. Zoomed out far
     * enough this falls below a pixel on screen, and the renderer fades the item rather than
     * letting it shimmer at a width the display cannot hold.
     */
    val minHalfWidth: Double = 0.0,
) {
    val isEmpty: Boolean get() = parts.isEmpty()
}

/**
 * Turns a model item into the triangles the GL canvas draws. Pure Kotlin so the pass choice and
 * the geometry are both unit-testable; the renderer only uploads what comes out.
 *
 * The pass choice is where parity with the paged canvas lives. Opaque ink can simply be drawn,
 * because overlapping the same colour twice is still that colour. A translucent stroke cannot: its
 * ribbon and its round ends overlap, and a second blend would leave the crossing darker than the
 * stroke, which the paged canvas avoids by accumulating the stroke opaquely and compositing once.
 *
 * Shapes need none of that. A fill is a simple polygon that never overlaps itself, and an outline
 * is drawn opaque, so both composite correctly drawn straight. The exception is a shape snapped
 * from the pencil, whose outline is graphite and takes the pencil's two passes
 * ([ShapeTessellator]).
 */
object ItemMesher {

    fun mesh(item: CanvasItem, tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE): MeshedItem? =
        when (item) {
            is Stroke -> meshStroke(item, tolerance)
            is ShapeItem -> meshShape(item, tolerance)
            is com.xnotes.core.model.TapeItem -> TapeTessellator.mesh(item, tolerance)
            else -> null // images carry a texture rather than a colour, and take their own path
        }

    private fun meshStroke(stroke: Stroke, tolerance: Double): MeshedItem? {
        if (stroke.isEmpty) return null
        if (stroke.tool == Tool.DASHED) return meshDashed(stroke, tolerance)
        if (stroke.config.grain) return meshGraphite(stroke, tolerance)
        val mesh = StrokeTessellator.tessellate(stroke.geometry(), tolerance)
        if (mesh.isEmpty) return null
        if (stroke.config.neon && stroke.tool != Tool.HIGHLIGHTER) {
            return neonStroke(stroke, mesh, tolerance)
        }
        val part = MeshPart(mesh, stroke.renderColor, passFor(stroke))
        return MeshedItem(listOf(part), stroke.paintBounds(), narrowestHalfWidth(stroke))
    }

    /**
     * One run of a stroke still under the pen, meshed straight off its live ribbon rather than off
     * a built geometry. The infinite canvas draws a wet stroke as runs — the settled ones uploaded
     * once each, the moving one re-meshed every frame — so what a frame costs stops growing with
     * the stroke.
     *
     * Only for ink the runs can be laid over each other freely, which is opaque and un-neon ink
     * ([Stroke.wetCacheable]); anything else is meshed whole by [mesh]. [dashPhase] is the arc the
     * runs before this one spent, so the dashed pen's rhythm carries across the join.
     */
    fun meshRun(
        stroke: Stroke,
        ribbon: RibbonPoints,
        from: Int,
        count: Int,
        dashPhase: Double,
        tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE,
    ): MeshPart? {
        if (count <= 0) return null
        val mesh = if (stroke.tool == Tool.DASHED) {
            StrokeTessellator.tessellateDashed(
                ribbon, from, count,
                stroke.config.dashLength, stroke.config.dashGap, stroke.config.baseWidth / 2.0,
                dashPhase, tolerance,
            )
        } else {
            StrokeTessellator.tessellate(ribbon, from, count, tolerance)
        }
        if (mesh.isEmpty) return null
        return MeshPart(mesh, stroke.renderColor, passFor(stroke))
    }

    /**
     * One run of a live pencil stroke, as [meshGraphite] makes the whole of one: the run's outer
     * ribbon first, then its pressed core when any point of the run has one, each at its own alpha
     * through the grain. The scene stencils every run's outer together and covers them once, then
     * does the same for the cores, so the runs mask exactly what the stroke meshed whole masks and
     * neither pass ever blends twice where two runs meet.
     *
     * Only the run's own points are read, the core radii included, so a move costs the tail it
     * re-meshes and nothing of the stroke behind it.
     */
    fun meshGraphiteRun(
        stroke: Stroke,
        ribbon: RibbonPoints,
        from: Int,
        count: Int,
        tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE,
    ): List<MeshPart> {
        if (count <= 0) return emptyList()
        val mesh = StrokeTessellator.tessellate(ribbon, from, count, tolerance)
        if (mesh.isEmpty) return emptyList()
        val color = stroke.renderColor
        val outer = MeshPart(mesh, color.scaleAlpha(Graphite.OUTER_ALPHA), InkPass.GRAPHITE)
        val widths = FloatArray(count)
        for (k in 0 until count) widths[k] = ribbon.hw(from + k).toFloat()
        val radii = FloatArray(count)
        val c = stroke.config
        if (!Graphite.coreRadii(widths, 0, count, c.baseWidth, c.pressureEnabled, c.pressureMinFactor, radii, 0)) {
            return listOf(outer)
        }
        val core = StrokeTessellator.tessellate(ScaledRibbon(ribbon, radii, from), from, count, tolerance)
        if (core.isEmpty) return listOf(outer)
        return listOf(outer, corePart(core, color))
    }

    /**
     * The dashed pen draws a broken, constant-width line down its centreline rather than a solid
     * ribbon, and that beats neon in [Stroke]'s own painter, so it does here too. The full ribbon is
     * still what bounds and hit-tests the stroke, so it stays selectable through the gaps.
     */
    private fun meshDashed(stroke: Stroke, tolerance: Double): MeshedItem? {
        val half = stroke.config.baseWidth / 2.0
        val mesh = StrokeTessellator.tessellateDashed(
            stroke.geometry(),
            stroke.config.dashLength,
            stroke.config.dashGap,
            half,
            tolerance,
        )
        if (mesh.isEmpty) return null
        return MeshedItem(listOf(MeshPart(mesh, stroke.renderColor, passFor(stroke))), stroke.bounds(), half)
    }

    /**
     * Neon, as four layers back to front: a wide faint halo, a tighter brighter one, the tube body
     * lifted toward white so it reads as lit, and a solid white core down the middle. The layout and
     * every constant come from [Stroke]'s own painter, so the two renderers glow alike.
     */
    private fun neonStroke(stroke: Stroke, ribbon: MeshData, tolerance: Double): MeshedItem {
        val strength = stroke.config.neonStrength.coerceIn(0.0, 1.0)
        val body = stroke.renderColor.withAlpha(255)
        val wideRadius = stroke.neonGlowRadius()
        val tightRadius = (
            stroke.config.baseWidth *
                (Stroke.NEON_BLOOM_TIGHT_FACTOR_MIN + Stroke.NEON_BLOOM_TIGHT_FACTOR_SPAN * strength)
            ).coerceAtLeast(Stroke.NEON_BLOOM_TIGHT_MIN)
        val glow = GlowSpec(
            wideRadius = wideRadius,
            wideAlpha = Stroke.NEON_BLOOM_WIDE_ALPHA_MIN + Stroke.NEON_BLOOM_WIDE_ALPHA_SPAN * strength,
            tightRadius = tightRadius,
            tightAlpha = Stroke.NEON_BLOOM_TIGHT_ALPHA_MIN + Stroke.NEON_BLOOM_TIGHT_ALPHA_SPAN * strength,
            bodyColor = Stroke.lighten(body, Stroke.NEON_BODY_LIGHTEN),
            coreColor = Rgba(255, 255, 255, 255),
            coreScale = Stroke.NEON_CORE_FRAC,
        )
        return MeshedItem(
            listOf(MeshPart(ribbon, body, InkPass.GLOW, glow)),
            stroke.paintBounds(),
            narrowestHalfWidth(stroke) * Stroke.NEON_CORE_FRAC,
        )
    }

    /**
     * The pencil, as the paged canvas paints it: the full-width ribbon and its pressed core, each
     * stencilled and then covered once through the grain at its own alpha, the core over the outer.
     * The core is the same tessellation run over the ribbon drawn in toward its centreline
     * ([ScaledRibbon]), so it keeps the ribbon's joins and ends.
     */
    private fun meshGraphite(stroke: Stroke, tolerance: Double): MeshedItem? {
        val g = stroke.geometry()
        val mesh = StrokeTessellator.tessellate(g, tolerance)
        if (mesh.isEmpty) return null
        val color = stroke.renderColor
        val parts = ArrayList<MeshPart>(2)
        parts += MeshPart(mesh, color.scaleAlpha(Graphite.OUTER_ALPHA), InkPass.GRAPHITE)
        stroke.graphiteCore(g)?.let { radii ->
            val core = StrokeTessellator.tessellate(ScaledRibbon(g, radii), tolerance)
            if (!core.isEmpty) parts += corePart(core, color)
        }
        return MeshedItem(parts, stroke.paintBounds(), narrowestHalfWidth(stroke))
    }

    /** A pencil's pressed core, knowing the outer it goes over (see [MeshPart.under]). */
    private fun corePart(core: MeshData, color: Rgba): MeshPart = MeshPart(
        core, color.scaleAlpha(Graphite.CORE_ALPHA), InkPass.GRAPHITE,
        under = color.scaleAlpha(Graphite.OUTER_ALPHA).a / 255.0,
    )

    /**
     * The thinnest the stroke gets, in content pixels. The renderer needs it to know when the
     * stroke has fallen below a pixel on screen and should be faded rather than fattened.
     */
    private fun narrowestHalfWidth(stroke: Stroke): Double {
        val widths = stroke.geometry().halfWidths
        if (widths.isEmpty()) return 0.0
        var min = Double.MAX_VALUE
        for (w in widths) if (w > 0f && w < min) min = w.toDouble()
        return if (min == Double.MAX_VALUE) 0.0 else min
    }

    private fun meshShape(shape: ShapeItem, tolerance: Double): MeshedItem? {
        val parts = ShapeTessellator.tessellate(shape, tolerance)
        if (parts.isEmpty()) return null
        return MeshedItem(parts, shape.paintBounds(), shape.strokeWidth / 2.0)
    }

    /** The pass a stroke has to take, from its tool and its resolved ink alpha. */
    fun passFor(stroke: Stroke): InkPass = when {
        stroke.config.grain -> InkPass.GRAPHITE
        stroke.tool == Tool.HIGHLIGHTER ->
            if (stroke.config.highlighterInverse) InkPass.SCREEN else InkPass.MULTIPLY
        stroke.renderColor.a >= 255 -> InkPass.OPAQUE
        else -> InkPass.TRANSLUCENT
    }

    /** [c] lerped toward white by `1 - alpha`: the colour a multiply blend tints by [alpha]. */
    fun multiplyColor(c: Rgba, alpha: Double): Rgba {
        val f = alpha.coerceIn(0.0, 1.0)
        return Rgba(
            (255 - (255 - c.r) * f).toInt().coerceIn(0, 255),
            (255 - (255 - c.g) * f).toInt().coerceIn(0, 255),
            (255 - (255 - c.b) * f).toInt().coerceIn(0, 255),
            255,
        )
    }

    /** [c] scaled toward black by [alpha]: the colour a screen blend lightens by [alpha]. */
    fun screenColor(c: Rgba, alpha: Double): Rgba {
        val f = alpha.coerceIn(0.0, 1.0)
        return Rgba(
            (c.r * f).toInt().coerceIn(0, 255),
            (c.g * f).toInt().coerceIn(0, 255),
            (c.b * f).toInt().coerceIn(0, 255),
            255,
        )
    }
}
