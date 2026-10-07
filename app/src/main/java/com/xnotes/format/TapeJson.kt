package com.xnotes.format

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TapePattern

/**
 * A strip of tape as both file formats write it, so a note and a canvas spell it the same way:
 *
 * ```
 * {"kind":"tape","start":[x,y],"end":[x,y],"width":32,"rgba":[r,g,b,a],"pattern":"stripes",
 *  "seed":123,"revealed":true,"locked":true}
 * ```
 *
 * The kind is new, so a build from before tape skips the entry (both readers ignore kinds they do
 * not know) and every older file reads exactly as it did. `revealed` and `locked` are written only
 * when set, like every other additive field.
 */
internal object TapeJson {

    fun write(j: JsonWrite, t: TapeItem) {
        j.beginObject()
        j.name("kind").value(TapeItem.KIND)
        j.name("start").beginArray().value(t.start.x).value(t.start.y).endArray()
        j.name("end").beginArray().value(t.end.x).value(t.end.y).endArray()
        j.name("width").value(t.width)
        j.name("rgba").beginArray().value(t.color.r).value(t.color.g).value(t.color.b).value(t.color.a).endArray()
        j.name("pattern").value(t.pattern.id)
        j.name("seed").value(t.seed)
        if (t.revealed) j.name("revealed").value(true)
        if (t.locked) j.name("locked").value(true)
        j.endObject()
    }

    /** Rebuild a strip from what a reader collected; null without both ends. */
    fun build(start: Pt?, end: Pt?, width: Double?, rgba: Rgba?, pattern: String?, seed: Int?, revealed: Boolean): TapeItem? {
        if (start == null || end == null) return null
        val w = (width ?: TapeItem.DEFAULT_WIDTH).takeIf { it.isFinite() && it > 0.0 } ?: TapeItem.DEFAULT_WIDTH
        return TapeItem(
            start = start,
            end = end,
            width = w,
            color = rgba ?: com.xnotes.core.tools.TapeConfig.COLORS[0],
            pattern = TapePattern.fromId(pattern),
            revealed = revealed,
            seed = seed ?: 0,
        )
    }
}
