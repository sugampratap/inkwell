package com.xnotes.core.tools

/** The tool set (spec 04 §1). */
enum class Tool(val id: String) {
    PEN("pen"),

    /** A ballpoint: an even line that answers pressure only a little, as a ballpoint does. */
    BALLPOINT("ballpoint"),
    DASHED("dashed"),
    CALLIGRAPHY("calligraphy"),
    /** The Quill: thins as it moves faster. Retired from the pen card's grid by the pencil, but kept
     *  whole, so its strokes, toolbar buttons, pen-box pens and configs all work as they did. */
    SPEED("speed"),
    TAPER("taper"),

    /** Graphite: a thin line laid through the paper's grain, translucent so strokes build up, and
     *  darker the harder it is pressed ([com.xnotes.core.stroke.Graphite]). */
    PENCIL("pencil"),
    HIGHLIGHTER("highlighter"),
    ERASER("eraser"),
    PAN("pan"),
    SELECT("select"),
    LASSO("lasso"),
    SCREENSHOT("screenshot"),
    SHAPE("shape"),
    TEXT("text"),
    TEXT_BOX("text_box"),
    IMAGE("image"),

    /** Marks a PDF's text: highlights and lines along it, or selects it ([MarkupMode]). */
    MARKUP("markup"),

    /**
     * The laser pointer: a glowing trail for pointing things out while presenting or explaining.
     * It inks like a pen but nothing it draws is kept: the trail fades on its own once the pen
     * has paused, and never reaches the page, the undo history or the file.
     */
    LASER("laser"),

    /**
     * Study tape: a straight strip pulled across the page that hides what is under it until it is
     * tapped, the active-recall tool GoodNotes and Starnote call Tape. Not ink: each pull lays one
     * [com.xnotes.core.model.TapeItem].
     */
    TAPE("tape");

    /** Tools that produce ink via the stroke engine. */
    val isStroke: Boolean get() = isPen || this == HIGHLIGHTER || this == LASER

    /**
     * The pens proper, which share one button on the bar and one popover, and differ only in the
     * nib: picking another pen type swaps which of these is armed.
     */
    val isPen: Boolean get() = this == PEN || this == BALLPOINT || this == DASHED || this == CALLIGRAPHY ||
        this == SPEED || this == TAPER || this == PENCIL

    /** Ink that is never kept: drawn, held while the hand keeps going, then faded away. */
    val isEphemeral: Boolean get() = this == LASER

    /**
     * Tools the "draw with finger" gate covers: when finger-draw is off, a finger pans
     * instead of activating these (the stroke tools plus select/lasso/shape/eraser/markup). The
     * stylus always uses the armed tool (its eraser tip still erases); text and pan stay
     * usable by finger either way.
     */
    val fingerPansWhenOff: Boolean get() = isStroke ||
        this == SELECT || this == LASSO || this == SCREENSHOT || this == SHAPE || this == ERASER || this == MARKUP ||
        this == TAPE

    /** Render-time ink alpha scale: the highlighter is translucent (spec 03 §3). */
    val alphaScale: Double get() = if (this == HIGHLIGHTER) 0.35 else 1.0

    companion object {
        fun fromId(id: String?): Tool? = entries.firstOrNull { it.id == id }

        /** Armed at startup: a note app opens ready to write. */
        val DEFAULT = PEN

        /** Quick-tool-wheel order (spec 06 §11 / 10 §7). */
        val wheelOrder = listOf(PEN, BALLPOINT, DASHED, CALLIGRAPHY, SPEED, TAPER, PENCIL, HIGHLIGHTER, LASER, TAPE, ERASER, SELECT, LASSO, SCREENSHOT, SHAPE, TEXT, MARKUP, PAN)

        /**
         * The pen types, in the order the pen popover's grid offers them. The pencil took the
         * Quill's tile; the Quill itself is still a pen ([allPenTypes]), only no longer offered here.
         */
        val penTypes = listOf(PEN, BALLPOINT, TAPER, CALLIGRAPHY, PENCIL, DASHED)

        /** Every pen there is: the grid's, then the Quill, which the grid no longer shows. */
        val allPenTypes = penTypes + SPEED
    }
}
