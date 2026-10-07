package com.xnotes.core.tools

import com.xnotes.core.model.Rgba

/** The preset ink colours (spec 04 §4). Ink colour is global across stroke tools. */
object InkPalette {
    val GREEN = Rgba(0, 230, 118, 255) // default "hacker green"
    val NEAR_WHITE = Rgba(236, 236, 236, 255)
    val RED = Rgba(255, 92, 92, 255)
    val AMBER = Rgba(255, 199, 64, 255)
    val CYAN = Rgba(88, 196, 255, 255)
    val VIOLET = Rgba(199, 134, 255, 255)
    val GREY = Rgba(128, 128, 128, 255)
    val ORANGE = Rgba(255, 145, 60, 255)
    val PINK = Rgba(255, 105, 180, 255)
    val BLUE = Rgba(70, 120, 255, 255)
    val LIME = Rgba(190, 240, 60, 255)
    val TEAL = Rgba(0, 190, 180, 255)
    val BROWN = Rgba(160, 110, 70, 255)
    val NAVY = Rgba(40, 60, 140, 255)
    val BLACK = Rgba(20, 20, 20, 255)

    val DEFAULT = GREEN

    // Inks for paper: deep enough to read on the light page, like the pens they are named after.
    val INK = Rgba(31, 42, 68, 255)
    val PEN_BLUE = Rgba(37, 99, 235, 255)
    val PEN_RED = Rgba(220, 38, 38, 255)
    val PEN_GREEN = Rgba(21, 128, 61, 255)
    val PEN_VIOLET = Rgba(124, 58, 237, 255)
    val PEN_ORANGE = Rgba(234, 88, 12, 255)
    val PEN_TEAL = Rgba(13, 148, 136, 255)
    val PEN_PINK = Rgba(219, 39, 119, 255)
    val PEN_SEPIA = Rgba(146, 94, 52, 255)
    val PEN_GREY = Rgba(107, 114, 128, 255)
    val PEN_CYAN = Rgba(8, 145, 178, 255)

    /** The highlighter's own yellow, which the pen box starts with. */
    val MARKER_YELLOW = Rgba(250, 204, 21, 255)

    /** The laser pointer's beam: a hot red that glows on paper and on dark chrome alike. */
    val LASER = Rgba(239, 35, 60, 255)

    /**
     * B2's quick colours (D7, mockup W 1188): the green and the amber of the bar's first five. Both read
     * on white paper; [InkContrast] lifts them on dark paper as it does every ink.
     */
    val QUICK_GREEN = Rgba(22, 163, 74, 255) // #16A34A
    val QUICK_AMBER = Rgba(217, 119, 6, 255) // #D97706

    /**
     * The default toolbar swatches, in order. A new install shows the first five: B2's navy, blue, red,
     * green and amber (no violet). The rest wait behind Settings › Toolbar colours, with the light inks
     * last, for dark paper. A saved bar is the user's own and is never replaced by these.
     */
    val presets = listOf(
        INK, PEN_BLUE, PEN_RED, QUICK_GREEN, QUICK_AMBER,
        BLACK, PEN_ORANGE, PEN_TEAL, PEN_PINK, PEN_SEPIA,
        PEN_GREY, AMBER, PEN_CYAN, NAVY, NEAR_WHITE,
    )

    /** Most swatches the toolbar can show; one per preset. */
    val MAX_SWATCHES = presets.size
}
