package com.xnotes.ui

import com.xnotes.core.model.Rgba
import com.xnotes.core.text.CharStyle
import com.xnotes.core.text.FlowDefaults
import com.xnotes.core.text.ListKind
import com.xnotes.core.text.ParaAlign
import com.xnotes.core.text.SlashCommands
import kotlin.math.max
import kotlin.math.min

// Part 6's text chrome decisions (r3_text.html), with no Android or Compose types so all of it runs on the JVM.

/** The format pill's height (.tx-fbar, TX 68), which is also the style pill's and the edit bar's. */
internal const val FORMAT_PILL_H_DP = 64f

/** What the format pill and its More menu show and allow at the caret. */
internal data class TextFormatState(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strike: Boolean,
    /** 0 for body text, else 1..6. */
    val headingLevel: Int,
    val list: ListKind,
    /** The caret's code block language: null outside a block, "" for a plain block. */
    val codeLang: String?,
    val align: ParaAlign,
    /** The caret is in a formula: More › Equation shows a check (D9). */
    val mathOn: Boolean,
    val highlightOn: Boolean,
    val headingEnabled: Boolean,
    val listsEnabled: Boolean,
    val codeEnabled: Boolean,
    val indentEnabled: Boolean,
    val outdentEnabled: Boolean,
    val equationEnabled: Boolean,
    val tableEnabled: Boolean,
    val languageEnabled: Boolean,
) {
    val codeOn: Boolean get() = codeLang != null
    val alignOn: Boolean get() = align != ParaAlign.LEFT
}

/**
 * The pill's state from the caret's style and paragraph (null fields: no paragraph). In a table cell the block
 * controls are off (heading, lists, code, indent, Insert table, Code language), as today's bar has them;
 * Equation needs a caret session and Outdent an indent.
 */
internal fun textFormatState(
    style: CharStyle,
    list: ListKind?,
    headingLevel: Int,
    codeLang: String?,
    align: ParaAlign?,
    indent: Int,
    inCell: Boolean,
    sessionActive: Boolean,
): TextFormatState = TextFormatState(
    bold = style.bold,
    italic = style.italic,
    underline = style.underline,
    strike = style.strike,
    headingLevel = headingLevel,
    list = list ?: ListKind.NONE,
    codeLang = codeLang,
    align = align ?: ParaAlign.LEFT,
    mathOn = style.math,
    highlightOn = style.highlight != null,
    headingEnabled = !inCell,
    listsEnabled = !inCell,
    codeEnabled = !inCell,
    indentEnabled = !inCell,
    outdentEnabled = indent > 0,
    equationEnabled = sessionActive,
    tableEnabled = sessionActive && !inCell,
    languageEnabled = !inCell,
)

/** True when a dark letter reads on [c]: luminance over 150 (TX 613), as today's `swatchInk`. */
internal fun darkInkOn(c: Rgba): Boolean = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b > 150

/** The heading menu's keycap for [level]: "#"… only while Markdown shortcuts are on and the level converts. */
internal fun headingHint(level: Int, markdownOn: Boolean): String? =
    if (markdownOn && level in 1..SlashCommands.MARKDOWN_HEADINGS) "#".repeat(level) else null

/** Proper names and short tags for the code-block languages (tree-sitter tokens plus "plain"). */
internal object CodeLanguages {
    const val PLAIN = "plain"

    private val NAMES = mapOf(
        "bash" to "Bash",
        "c" to "C",
        "cpp" to "C++",
        "java" to "Java",
        "javascript" to "JavaScript",
        "json" to "JSON",
        "kotlin" to "Kotlin",
        "python" to "Python",
    )

    /** The language's proper name; null for plain text (the caller's localised "Plain text"). Unknown tokens show as typed. */
    fun name(token: String): String? = if (token.isEmpty() || token == PLAIN) null else NAMES[token] ?: token

    /** The short tag on the code button and in the menu (TX 598): today's `shortLangLabel`, plus "plain". */
    fun tag(token: String): String = when (token) {
        "", PLAIN -> "txt"
        "bash" -> "sh"
        "javascript" -> "js"
        "kotlin" -> "kt"
        "python" -> "py"
        else -> token.take(4)
    }

    /** The language the menu marks: the block's ("" is plain), or outside a block the one the toggle arms next. */
    fun menuCurrent(blockLang: String?, last: String): String = when {
        blockLang == null -> last
        blockLang.isEmpty() -> PLAIN
        else -> blockLang
    }
}

/** The Text options card's "Default for new notes" chip (TX 977-989), exactly as today's checkbox row behaves. */
internal object TextOptionsLogic {
    /** Shown once the card has seen a config other than the saved default, unless the config is the factory one. */
    fun chipShown(shownSoFar: Boolean, config: FlowDefaults, factory: FlowDefaults): Boolean = shownSoFar && config != factory

    /** An apply that differs from the saved new-note default reveals the chip for the rest of the card's session. */
    fun revealsChip(next: FlowDefaults, newNote: FlowDefaults): Boolean = next != newNote

    fun chipOn(config: FlowDefaults, newNote: FlowDefaults, factory: FlowDefaults): Boolean = newNote != factory && config == newNote

    /** What tapping the chip saves as the new-note default. */
    fun toggleTarget(on: Boolean, config: FlowDefaults, factory: FlowDefaults): FlowDefaults = if (on) config else factory
}

/** [start] moved so a [size] span stays [margin] inside 0..[extent]; a span too big starts at [margin]. */
private fun inside(start: Int, size: Int, extent: Int, margin: Int): Int = max(margin, min(start, extent - margin - size))

/** Where the slash menu goes, in canvas px: [rows] list rows, [below] the line or above the caret; [originX] from its left. */
internal data class SlashPlacement(val x: Int, val y: Int, val rows: Int, val below: Boolean, val originX: Int)

/**
 * The slash menu (TX 1157-1168): below the line when at least min(count, [minBelow]) rows fit above
 * [bottomLimit] (the format pill's top − 10), else above the caret up to [topLimit] (the toolbar's bottom + 10);
 * as many rows as fit there, 1..[maxRows]; x starts [lead] before the caret, kept [margin] inside [viewW].
 * When neither side meets its rule it takes the side with more rows and is kept between [topLimit] and [bottomLimit]
 * (under the toolbar first), so it never sits off the top or under the toolbar. [chrome] is the list's padding plus
 * the footer.
 */
internal fun placeSlashMenu(
    count: Int,
    caretTop: Int,
    lineBottom: Int,
    slashLeft: Int,
    topLimit: Int,
    bottomLimit: Int,
    menuW: Int,
    viewW: Int,
    rowH: Int,
    chrome: Int,
    gap: Int,
    lead: Int,
    margin: Int,
    maxRows: Int = 6,
    minBelow: Int = 3,
): SlashPlacement {
    val fitBelow = Math.floorDiv(bottomLimit - (lineBottom + gap) - chrome, rowH)
    val fitAbove = Math.floorDiv((caretTop - gap) - topLimit - chrome, rowH)
    val belowFits = fitBelow >= min(count, minBelow)
    val neither = !belowFits && fitAbove < 1
    val below = belowFits || (neither && fitBelow >= fitAbove)
    val rows = min(min(count, maxRows), if (below) fitBelow else fitAbove).coerceIn(1, maxRows)
    val h = rows * rowH + chrome
    val x = inside(slashLeft - lead, menuW, viewW, margin)
    val side = if (below) lineBottom + gap else caretTop - gap - h
    val y = if (neither) max(topLimit, min(side, bottomLimit - h)) else side
    return SlashPlacement(x, y, rows, below, slashLeft - x)
}

/** Where a bar over a rect goes, in canvas px, and whether it is above the rect. */
internal data class BarSpot(val x: Int, val y: Int, val above: Boolean)

/**
 * A bar centred on a rect (the edit bar TX 1047-1048, the style pill TX 1256-1258): above it by [gapAbove] (plus
 * [stack], the selection bar it rides over), unless that is above [minTop] (the toolbar's bottom + 8); then below
 * by [gapBelow] (plus [stack]). x is kept [margin] inside [viewW].
 */
internal fun placeTextBar(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    barW: Int,
    barH: Int,
    viewW: Int,
    gapAbove: Int,
    gapBelow: Int,
    stack: Int,
    minTop: Int,
    margin: Int,
): BarSpot {
    val x = inside((left + right) / 2 - barW / 2, barW, viewW, margin)
    val above = top - gapAbove - barH - stack
    return if (above >= minTop) BarSpot(x, above, true) else BarSpot(x, bottom + gapBelow + stack, false)
}

/** The format pill's distance from the canvas bottom, in dp: 20, or 10 over the keyboard (TX 801), and 10 above a bottom toolbar. */
internal fun formatPillGap(imeVisible: Boolean, coverBottomDp: Float): Float =
    max(if (imeVisible) 10f else 20f, if (coverBottomDp > 0f) coverBottomDp + 10f else 0f)

/** How far above the canvas bottom the caret must stay while the pill is up: the pill and its gap. */
internal fun formatPillClearance(gapDp: Float): Float = gapDp + FORMAT_PILL_H_DP

/** The height a menu opening upwards from [anchorTop] (less [gap]) has before [topLimit]; never negative. */
internal fun roomAbove(anchorTop: Float, topLimit: Float, gap: Float): Float = max(0f, anchorTop - gap - topLimit)
