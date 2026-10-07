package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import kotlin.math.abs

/**
 * A pen kept in the pen box: which pen, how it is tuned, and the ink in it. Picking one arms all
 * three at once, which is what switching between "the fine black pen" and "the yellow marker"
 * means to someone writing; the toolbar alone takes three taps to do it.
 */
data class PenPreset(val tool: Tool, val config: ToolConfig, val color: Rgba) {

    /**
     * Whether [tool] tuned as [config], inking in [ink], is this pen. The width and the pressure
     * curve are what make one pen feel unlike another. Where the ink comes from, a swatch or the
     * tool's own pinned colour, is how the pen was picked up and not what it is, so the colour
     * fields of a config are ignored in favour of [ink].
     */
    fun matches(tool: Tool, config: ToolConfig, ink: Rgba): Boolean =
        this.tool == tool && color == ink &&
            abs(this.config.baseWidth - config.baseWidth) < WIDTH_EPS &&
            this.config.copy(
                rgba = config.rgba,
                baseWidth = config.baseWidth,
                colorOverride = config.colorOverride,
            ) == config
}

/** The pen box's rules, pure so they are tested on the JVM. */
object PenBox {

    /** Most pens the box holds; past it the oldest makes room. */
    const val MAX = 12

    /** What a new install starts with: an everyday ink, two colours to mark up with, and a marker. */
    val DEFAULT: List<PenPreset> = listOf(
        // The first is the pen a new install starts with, so the box opens with it in hand.
        PenPreset(Tool.PEN, ToolDefaults.configFor(Tool.PEN), InkPalette.INK),
        PenPreset(Tool.PEN, ToolDefaults.configFor(Tool.PEN).copy(baseWidth = 2.0), InkPalette.PEN_BLUE),
        PenPreset(Tool.PEN, ToolDefaults.configFor(Tool.PEN).copy(baseWidth = 3.0), InkPalette.PEN_RED),
        PenPreset(Tool.HIGHLIGHTER, ToolDefaults.configFor(Tool.HIGHLIGHTER), InkPalette.MARKER_YELLOW),
    )

    /** Tools a pen box can hold: the ones that put ink down. */
    fun holds(tool: Tool): Boolean = tool.isStroke && !tool.isEphemeral

    /**
     * [box] with [pen] added at the end. A pen already in the box moves to the end instead of
     * appearing twice, and a full box drops its oldest pen.
     */
    fun add(box: List<PenPreset>, pen: PenPreset): List<PenPreset> {
        if (!holds(pen.tool)) return box
        val rest = box.filterNot { it.matches(pen.tool, pen.config, pen.color) }
        return (rest + pen).takeLast(MAX)
    }

    fun remove(box: List<PenPreset>, index: Int): List<PenPreset> =
        if (index in box.indices) box.filterIndexed { i, _ -> i != index } else box

    /** The pen in [box] that [tool] tuned as [config] and inking in [ink] is, or -1. */
    fun indexOf(box: List<PenPreset>, tool: Tool, config: ToolConfig, ink: Rgba): Int =
        box.indexOfFirst { it.matches(tool, config, ink) }
}

private const val WIDTH_EPS = 1e-6
