package com.xnotes.canvas

import com.xnotes.core.infinite.ItemMesher
import com.xnotes.core.infinite.MeshPart
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.WetRibbon
import kotlin.math.hypot

/**
 * What a live stroke on the paged front buffer is handed per move: the run that has settled since
 * the last one, once there are enough points for it, and the tail from there to the nib.
 *
 * A settled run is meshed once and never again, and the tail is only the points still in play, so
 * what a move meshes is the length of a run plus the tail, never the length of the stroke. A pen's
 * run is one part; a pencil's is its outer and its pressed core ([ItemMesher.meshGraphiteRun]).
 *
 * Pure, so what a move costs is measurable off the device.
 */
internal class PadFeed {

    /** Ribbon points already handed over as runs. */
    var meshed = 0
        private set

    /** The centreline arc those points spent, which is where the dashed pen's rhythm has got to. */
    private var arc = 0.0

    fun reset() {
        meshed = 0
        arc = 0.0
    }

    /**
     * The run settled since the last one, once at least [runPoints] points have, or nothing. Starts
     * a point back so the quad bridging the two runs belongs to the later one and no gap can open.
     */
    fun settledRun(stroke: Stroke, ribbon: WetRibbon, runPoints: Int): List<MeshPart> {
        val settled = ribbon.settledCount
        if (settled - meshed < runPoints) return emptyList()
        val from = (meshed - 1).coerceAtLeast(0)
        val run = if (stroke.config.grain) {
            ItemMesher.meshGraphiteRun(stroke, ribbon, from, settled - from)
        } else {
            listOfNotNull(ItemMesher.meshRun(stroke, ribbon, from, settled - from, arc))
        }
        for (k in from + 1 until settled) {
            arc += hypot(ribbon.cx(k) - ribbon.cx(k - 1), ribbon.cy(k) - ribbon.cy(k - 1))
        }
        meshed = settled
        return run
    }

    /** The points still in play, from the last one handed over as a run to the nib. */
    fun tail(stroke: Stroke, ribbon: WetRibbon): List<MeshPart> {
        val from = (meshed - 1).coerceAtLeast(0)
        val count = ribbon.pointCount - from
        if (stroke.config.grain) return ItemMesher.meshGraphiteRun(stroke, ribbon, from, count)
        return listOfNotNull(ItemMesher.meshRun(stroke, ribbon, from, count, arc))
    }
}
