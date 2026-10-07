package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TapePattern

/**
 * The tape tool's style: the colour and print of the next strip, and how wide it is. Shared by
 * both editors and kept in its own small file beside the settings (see `TapeConfigStore`), so a
 * strip laid on the canvas and one laid in a notebook come off the same roll.
 *
 * Tape has its own colours rather than the toolbar's inks: it is a pastel washi roll, not a pen, and
 * a strip in the pen's black would hide the answer just as well but look like a redaction.
 */
data class TapeConfig(
    val color: Rgba = COLORS[0],
    val pattern: TapePattern = TapePattern.STRIPES,
    val width: Double = TapeItem.DEFAULT_WIDTH,
) {
    companion object {
        /** The roll's colours, soft enough to sit on a page without shouting. */
        val COLORS: List<Rgba> = listOf(
            Rgba(250, 220, 120), // butter
            Rgba(248, 178, 140), // peach
            Rgba(240, 160, 182), // rose
            Rgba(168, 184, 212), // slate: the mockup's lavender #BAA8E8 turned blue-grey, as the app shows no purple
            Rgba(140, 196, 236), // sky
            Rgba(150, 214, 180), // mint
            Rgba(205, 176, 136), // kraft
            Rgba(112, 118, 130), // graphite
        )

        /** Parse what [encode] wrote, forgiving anything missing or out of range. */
        fun decode(color: String?, pattern: String?, width: Double?): TapeConfig = TapeConfig(
            color = Rgba.fromHex(color)?.withAlpha(255) ?: COLORS[0],
            pattern = TapePattern.fromId(pattern),
            width = (width ?: TapeItem.DEFAULT_WIDTH).takeIf { it.isFinite() }
                ?.coerceIn(TapeItem.MIN_WIDTH, TapeItem.MAX_WIDTH) ?: TapeItem.DEFAULT_WIDTH,
        )
    }
}
