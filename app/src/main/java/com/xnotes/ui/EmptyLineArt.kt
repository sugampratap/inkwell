package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.InkTokens
import com.xnotes.ui.theme.LocalInk

/** The four empty-state pictures (r2_panel_share_empty Frame 5). */
internal enum class LineArt { PAGES, SEARCH, RECENT, TRASH }

private const val VIEW_W = 200f

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()
private fun scribble(x: Float, y: Float) = path("M${x + 11} ${y + 16}c2.4-3.6 4.2 2.6 6.6 0s4.2-3.6 6.6 0 4.2 2.6 6.6 0 4.2-3.6 6.6 0")

// Parsed once, in the 200 × 150 view box of the mockup's SVG.
private val scribbleOnTop by lazy { scribble(82f, 26f) }
private val scribbleOnSheet by lazy { scribble(48f, 30f) }
private val penNib by lazy { path("M-4.5 14L0 28L4.5 14Z") }
private val lensGlint by lazy { path("M114 76a13 13 0 0 1 8-9") }
private val binBody by lazy { path("M66 50L71 114a4 4 0 0 0 4 4h50a4 4 0 0 0 4-4L134 50Z") }
private val binLid by lazy { path("M85 50V42a4 4 0 0 1 4-4h22a4 4 0 0 1 4 4v8") }
private val binSparkle by lazy { path("M151 38l7-7M156 50h9M144 30v-9") }

/**
 * B2's empty-state line art: 1.5dp ink strokes in the Phosphor style, sheets filled with the raised
 * surface, faint rules, and one pale marigold sun behind. Painted from the theme, so it suits Light,
 * Dark and OLED. 200 × 150 by default; any size keeps the 4:3 shape. Decorative: no semantics.
 */
@Composable
internal fun InkLineArt(art: LineArt, modifier: Modifier = Modifier.size(200.dp, 150.dp)) {
    val ink = LocalInk.current
    Canvas(modifier) {
        val k = size.width / VIEW_W
        val stroke = Stroke(width = 1.5.dp.toPx() / k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(k, k, pivot = Offset.Zero) {
            drawCircle(ink.brandSoft, 58f, Offset(100f, 76f))
            when (art) {
                LineArt.PAGES -> pages(ink, stroke)
                LineArt.SEARCH -> search(ink, stroke)
                LineArt.RECENT -> recent(ink, stroke)
                LineArt.TRASH -> trash(ink, stroke)
            }
        }
    }
}

/** A ruled 64 × 84 sheet at ([x], [y]), turned [degrees] about its centre, with an optional line of writing. */
private fun DrawScope.sheet(ink: InkTokens, stroke: Stroke, x: Float, y: Float, degrees: Float, writing: Path? = null) {
    rotate(degrees, Offset(x + 32f, y + 42f)) {
        drawRoundRect(ink.raised, Offset(x, y), Size(64f, 84f), CornerRadius(5f))
        drawRoundRect(ink.text, Offset(x, y), Size(64f, 84f), CornerRadius(5f), style = stroke)
        val rule = ink.text.copy(alpha = 0.34f)
        for (i in 0 until 5) {
            val ry = y + 29f + i * 10.5f
            drawLine(rule, Offset(x + 11f, ry), Offset(x + 53f, ry), stroke.width, StrokeCap.Round)
        }
        if (writing != null) drawPath(writing, ink.text, style = stroke)
    }
}

/** A filled outline: the raised surface under an ink stroke. */
private fun DrawScope.filled(ink: InkTokens, stroke: Stroke, p: Path) {
    drawPath(p, ink.raised)
    drawPath(p, ink.text, style = stroke)
}

private fun DrawScope.pages(ink: InkTokens, stroke: Stroke) {
    sheet(ink, stroke, 44f, 34f, -9f)
    sheet(ink, stroke, 82f, 26f, 6f, scribbleOnTop)
    translate(152f, 70f) {
        rotate(26f, Offset.Zero) {
            drawRoundRect(ink.raised, Offset(-4.5f, -40f), Size(9f, 54f), CornerRadius(4.5f))
            drawRoundRect(ink.text, Offset(-4.5f, -40f), Size(9f, 54f), CornerRadius(4.5f), style = stroke)
            drawLine(ink.text, Offset(-4.5f, -27f), Offset(4.5f, -27f), stroke.width, StrokeCap.Round)
            filled(ink, stroke, penNib)
            drawLine(ink.text, Offset(0f, 20f), Offset(0f, 24f), stroke.width, StrokeCap.Round)
        }
    }
}

private fun DrawScope.search(ink: InkTokens, stroke: Stroke) {
    sheet(ink, stroke, 48f, 30f, -6f, scribbleOnSheet)
    drawCircle(ink.raised, 21f, Offset(126f, 80f))
    drawCircle(ink.text, 21f, Offset(126f, 80f), style = stroke)
    drawPath(lensGlint, ink.text, style = stroke)
    translate(141f, 95f) {
        rotate(-45f, Offset.Zero) {
            drawRoundRect(ink.raised, Offset(-4.5f, 0f), Size(9f, 26f), CornerRadius(4.5f))
            drawRoundRect(ink.text, Offset(-4.5f, 0f), Size(9f, 26f), CornerRadius(4.5f), style = stroke)
        }
    }
}

private fun DrawScope.recent(ink: InkTokens, stroke: Stroke) {
    sheet(ink, stroke, 48f, 30f, -6f, scribbleOnSheet)
    val c = Offset(128f, 76f)
    drawCircle(ink.raised, 24f, c)
    drawCircle(ink.text, 24f, c, style = stroke)
    val w = stroke.width
    drawLine(ink.text, Offset(128f, 56f), Offset(128f, 59f), w, StrokeCap.Round)
    drawLine(ink.text, Offset(148f, 76f), Offset(145f, 76f), w, StrokeCap.Round)
    drawLine(ink.text, Offset(128f, 96f), Offset(128f, 93f), w, StrokeCap.Round)
    drawLine(ink.text, Offset(108f, 76f), Offset(111f, 76f), w, StrokeCap.Round)
    drawLine(ink.text, c, Offset(128f, 64f), w, StrokeCap.Round)
    drawLine(ink.text, c, Offset(137f, 81f), w, StrokeCap.Round)
    drawCircle(ink.text, 1.6f, c)
}

private fun DrawScope.trash(ink: InkTokens, stroke: Stroke) {
    filled(ink, stroke, binBody)
    val w = stroke.width
    drawLine(ink.text, Offset(58f, 50f), Offset(142f, 50f), w, StrokeCap.Round)
    drawPath(binLid, ink.text, style = stroke)
    for (x in listOf(89f, 100f, 111f)) drawLine(ink.text, Offset(x, 66f), Offset(x, 102f), w, StrokeCap.Round)
    drawPath(binSparkle, ink.text, style = stroke)
}
