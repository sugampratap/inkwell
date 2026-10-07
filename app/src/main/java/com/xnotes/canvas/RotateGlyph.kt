package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.pal.FillRule
import com.xnotes.core.vector.PathFlattener
import com.xnotes.core.vector.Triangulator
import com.xnotes.format.SvgPathData

/**
 * The arrow on the selection's rotate grip: Phosphor's `arrow-clockwise` (regular, 2.1.1), the
 * glyph the mockup sets on it (SC 49), so the one grip that turns says so.
 *
 * Built once, the first time a selection shows its grip, and never again: the outline as one
 * closed ring for a canvas that fills paths, and the same ring as triangles for the infinite
 * canvas's mesh. Both are on a unit box centred on 0,0 (the icon's whole 256-unit box is 1 across,
 * as an icon font sets it to the em), so a renderer only translates it to the grip and scales it
 * to the glyph's size. Nothing is allocated per frame.
 */
object RotateGlyph {

    /** Phosphor 2.1.1 regular `arrow-clockwise` (assets/regular/arrow-clockwise.svg), on 256 units. */
    const val PATH_DATA =
        "M240,56v48a8,8,0,0,1-8,8H184a8,8,0,0,1,0-16H211.4L184.81,71.64l-.25-.24a80,80,0,1,0-1.67,114.78," +
            "8,8,0,0,1,11,11.63A95.44,95.44,0,0,1,128,224h-1.32A96,96,0,1,1,195.75,60L224,85.8V56a8,8,0,1,1,16,0Z"

    /** The icon's box, in its own units. */
    const val VIEW_BOX = 256.0

    /** How closely the curves are followed, in icon units: well under a twentieth of a pixel at 14 dp. */
    private const val FLATTEN_TOLERANCE = 0.25

    /** The arrow's outline: one closed ring, centred on 0,0, the icon's box 1 across. */
    val outline: List<Pt> = run {
        val half = VIEW_BOX / 2.0
        val ring = SvgPathData.parse(PATH_DATA).firstOrNull()?.let { PathFlattener.flatten(it, FLATTEN_TOLERANCE) }.orEmpty()
        ring.map { Pt((it.x - half) / VIEW_BOX, (it.y - half) / VIEW_BOX) }
    }

    /** [outline] as triangles over the same unit-box points; null only if it would not triangulate. */
    val mesh: Triangulator.Mesh? = Triangulator.triangulate(listOf(outline), FillRule.NONZERO)
}
