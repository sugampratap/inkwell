package com.xnotes.ui

import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig

/**
 * What a tool popup needs from whichever editor is open.
 *
 * The popups are the same controls on either surface, so they are the same composables rather than
 * a second set that resembles them. A lookalike drifts: a control added to one, a label reworded, a
 * range widened, and the two stop matching in ways nobody notices until a user does. Taking the few
 * members the popups actually read through an interface is what makes "identical" a property of the
 * code rather than a thing to keep checking.
 */
interface ToolPopupHost {
    /** The stored style for [tool], without any live ink colour folded into it. */
    fun toolConfig(tool: Tool): ToolConfig

    /** Apply and persist a tool's style. */
    fun updateToolConfig(tool: Tool, config: ToolConfig)

    /** The shape tool's style. */
    val hostShapeConfig: ShapeConfig

    fun updateShapeConfig(config: ShapeConfig)

    /** The toolbar's ink swatches, the active one, and the recently picked colours. */
    val hostToolbarColors: List<Rgba>
    val hostActiveColorIndex: Int
    val hostRecentColors: List<Rgba>

    /** Recolour swatch [index] and arm it, live while the picker is open. */
    fun setSwatchColor(index: Int, color: Rgba)

    /** The picker closed: keep the swatch's colour among the recents. */
    fun rememberSwatchColor(index: Int)

    /** Whether the open note has a PDF, whose text markups the eraser can take. */
    val hostHasPdf: Boolean get() = false

    /** The lasso's shape, object filter and tap-to-select, shared by both editors. */
    val hostLassoOptions: com.xnotes.core.tools.LassoOptions get() = com.xnotes.core.tools.LassoOptions()

    /** Apply and persist the lasso's options. */
    fun updateLassoOptions(options: com.xnotes.core.tools.LassoOptions) {}

    // --- the tape ---

    /** The tape tool's colour, print and width, shared by both editors. */
    val hostTapeConfig: com.xnotes.core.tools.TapeConfig get() = com.xnotes.core.tools.TapeConfig()

    /** Apply and persist the tape tool's style. */
    fun updateTapeConfig(config: com.xnotes.core.tools.TapeConfig) {}

    /** How many strips of tape the open note holds, and how many of them are peeled back. */
    fun hostTapeCounts(): Pair<Int, Int> = 0 to 0

    /** Peel back ([revealed]) or stick down every strip of tape in the open note. */
    fun setAllTapeRevealed(revealed: Boolean) {}

    // --- the pen box ---

    /** The armed tool, so the pen box can tell which of its pens is in hand. */
    val hostTool: Tool get() = Tool.PEN

    /** Arm [tool], as its toolbar button would. */
    fun hostArmTool(tool: Tool) {}

    /** How many of [hostToolbarColors] the bar shows. */
    val hostSwatchCount: Int get() = hostToolbarColors.size

    /** Arm swatch [index], as tapping it would. */
    fun hostPickSwatch(index: Int) {}

    /** Saved pens, oldest first; empty on a host without a pen box. */
    val hostPenBox: List<com.xnotes.core.tools.PenPreset> get() = emptyList()

    /** Replace the pen box, and persist it. */
    fun replacePenBox(box: List<com.xnotes.core.tools.PenPreset>) {}

    /** Whether the host has a pen box at all, so the popups only offer to save into a real one. */
    val hostHasPenBox: Boolean get() = false

    /** Whether the pen box rail is unfolded. */
    val hostPenBoxOpen: Boolean get() = true

    fun openPenBox(open: Boolean) {}

    // --- the chrome ---

    /** Whether a finger or the pen is on the canvas (see `Editor.penDown`); the chrome freezes its motion meanwhile. */
    val hostPenDown: Boolean get() = false

    // --- the eyedropper (Part 5) ---

    /** Whether [sampleColourAt] reads a canvas: the colour picker shows its eyedropper only then. */
    val hostCanSampleColour: Boolean get() = false

    /**
     * The colour the canvas shows at screen px ([screenX], [screenY]), opaque, handed to [onResult] on the main
     * thread; null when the point is off the canvas or the read failed. Runs once per eyedropper tap, never per frame.
     */
    fun sampleColourAt(screenX: Int, screenY: Int, onResult: (Rgba?) -> Unit) = onResult(null)

    /** Keep a colour the eyedropper picked among the recent colours. */
    fun rememberPickedColour(color: Rgba) {}

    /** The canvas view's top-left in window px (viewport px + this = window px), or null before it is laid out. */
    fun hostCanvasOrigin(): androidx.compose.ui.unit.IntOffset? = null
}

/**
 * The open pane's canvas, for the colour picker's eyedropper and for placing chrome against viewport rects (Part 5).
 * Provided per pane by MainActivity's EditorPane; null outside an editor (Settings, the library).
 */
internal val LocalCanvasHost = androidx.compose.runtime.staticCompositionLocalOf<ToolPopupHost?> { null }
