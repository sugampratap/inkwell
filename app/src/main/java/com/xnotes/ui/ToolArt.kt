package com.xnotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.LassoShape
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.ShapeKind

/** The option cards' viewBox (.to-opt svg, 0 0 72 40), the tiles' (.nib svg, 0 0 44 22), the previews' (0 0 300 56). */
internal const val OPTION_ART_W = 72f
internal const val OPTION_ART_H = 40f
internal const val TILE_ART_W = 44f
internal const val TILE_ART_H = 22f
internal const val PREVIEW_W = 300f

/**
 * One element of a mockup drawing, in viewBox units, as its SVG gives it. All strokes have round caps and joins, and
 * every colour is `currentColor` (the card's text colour) at [opacity].
 */
internal sealed interface ArtPart {
    val opacity: Float

    /** `<path d stroke-width stroke-dasharray>`. */
    data class Stroke(val d: String, val width: Float, val dash: List<Float>? = null, override val opacity: Float = 1f) : ArtPart

    /** A stroked `<rect x y width height rx>`. */
    data class StrokeRect(
        val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float, val width: Float,
        val dash: List<Float>? = null, override val opacity: Float = 1f,
    ) : ArtPart

    /** A filled `<rect>`. */
    data class FillRect(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float, override val opacity: Float = 1f) : ArtPart

    data class StrokeCircle(val cx: Float, val cy: Float, val r: Float, val width: Float, override val opacity: Float = 1f) : ArtPart

    data class FillCircle(val cx: Float, val cy: Float, val r: Float, override val opacity: Float = 1f) : ArtPart

    data class StrokeEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val width: Float, override val opacity: Float = 1f) : ArtPart

    /** The markup tiles' `<text>Text</text>`: centred on [x], its baseline at [baseline], [size] units tall, bold. */
    data class Label(val x: Float, val baseline: Float, val size: Float, override val opacity: Float = 1f) : ArtPart

    /** [parts] with everything inside the circle cut away: the area eraser's SVG mask (TO 610). */
    data class CutCircle(val cx: Float, val cy: Float, val r: Float, val parts: List<ArtPart>, override val opacity: Float = 1f) : ArtPart
}

// --- the eraser (TO 603–611) ---

internal const val ERASER_SQUIGGLE = "M8 25c3-9 7-10 10-3s6 9 10 1 7-9 10-1 6 9 10 1 7-8 10-2 5 5 6 3"

private val ERASER_WHOLE = listOf(
    ArtPart.Stroke(ERASER_SQUIGGLE, 2f, dash = listOf(2.5f, 3f), opacity = 0.34f),
    ArtPart.StrokeCircle(35f, 19f, 7.5f, 1.3f, opacity = 0.75f),
)
private val ERASER_AREA = listOf(
    ArtPart.CutCircle(35f, 20f, 8.5f, listOf(ArtPart.Stroke(ERASER_SQUIGGLE, 2.2f))),
    ArtPart.StrokeCircle(35f, 20f, 7.5f, 1.3f, opacity = 0.75f),
)

/** The drawing on [mode]'s card: a whole stroke faded under the eraser, or the stroke cut where the eraser is. */
internal fun eraserArt(mode: EraseMode): List<ArtPart> = when (mode) {
    EraseMode.STROKE -> ERASER_WHOLE
    EraseMode.AREA -> ERASER_AREA
}

// --- the lasso (SC 515–517) ---

internal const val LASSO_SQUIGGLE = "M22 23c2.4-4 4.6-4.4 6 0s3.6 4 6.4-.4 4.6-4 6.4.4 3.6 3.8 6.6-.6"
internal const val LASSO_LOOP = "M14 18C15 9 30 5.5 44 6.5S63 13 61.5 22 50 34.5 36 34.4 13 27 14 18z"
private val LASSO_DASH = listOf(3.5f, 3f)
private val LASSO_FREE_OUTLINE = ArtPart.Stroke(LASSO_LOOP, 1.5f, dash = LASSO_DASH)
private val LASSO_RECT_OUTLINE = ArtPart.StrokeRect(12f, 7f, 48f, 27f, 2f, 1.5f, dash = LASSO_DASH)
private val LASSO_INK_OFF = ArtPart.Stroke(LASSO_SQUIGGLE, 2.2f, opacity = 0.55f)
private val LASSO_INK_ON = ArtPart.Stroke(LASSO_SQUIGGLE, 2.2f)
private val LASSO_FREE = listOf(LASSO_FREE_OUTLINE, LASSO_INK_OFF)
private val LASSO_FREE_ON = listOf(LASSO_FREE_OUTLINE, LASSO_INK_ON)
private val LASSO_RECT = listOf(LASSO_RECT_OUTLINE, LASSO_INK_OFF)
private val LASSO_RECT_ON = listOf(LASSO_RECT_OUTLINE, LASSO_INK_ON)

/** A dashed loop or box round a squiggle of ink, which turns solid when its card is chosen (SC 32). */
internal fun lassoArt(shape: LassoShape, selected: Boolean): List<ArtPart> = when (shape) {
    LassoShape.FREEFORM -> if (selected) LASSO_FREE_ON else LASSO_FREE
    LassoShape.RECTANGLE -> if (selected) LASSO_RECT_ON else LASSO_RECT
}

// --- shape tiles (TO 673–678) ---

private val SHAPE_LINE = listOf(ArtPart.Stroke("M9 17L35 5", 1.8f))
private val SHAPE_ARROW = listOf(ArtPart.Stroke("M9 17L34 6M27.2 5.2L34 6l-2.6 6.4", 1.8f))
private val SHAPE_RECT = listOf(ArtPart.StrokeRect(9f, 4f, 26f, 14f, 1f, 1.8f))
private val SHAPE_ELLIPSE = listOf(ArtPart.StrokeEllipse(22f, 11f, 14f, 7.5f, 1.8f))
private val SHAPE_CIRCLE = listOf(ArtPart.StrokeCircle(22f, 11f, 8f, 1.8f))
private val SHAPE_TRIANGLE = listOf(ArtPart.Stroke("M22 3.4L31 18.4H13z", 1.8f))

/** [kind]'s tile. The kinds only recognition makes draw as their nearest tool kind. */
internal fun shapeArt(kind: ShapeKind): List<ArtPart> = when (kind) {
    ShapeKind.LINE, ShapeKind.POLYLINE, ShapeKind.CURVE -> SHAPE_LINE
    ShapeKind.ARROW -> SHAPE_ARROW
    ShapeKind.RECTANGLE -> SHAPE_RECT
    ShapeKind.ELLIPSE -> SHAPE_ELLIPSE
    ShapeKind.CIRCLE -> SHAPE_CIRCLE
    ShapeKind.TRIANGLE, ShapeKind.POLYGON -> SHAPE_TRIANGLE
}

// --- PDF markup tiles (TO 645–652) ---

private val MARKUP_WORD = ArtPart.Label(22f, 14.6f, 10.5f)
private val MARKUP_SELECT = listOf(
    ArtPart.FillRect(9f, 5f, 26f, 13f, 1.5f, opacity = 0.14f),
    MARKUP_WORD,
    ArtPart.Stroke("M8.5 5v13M35.5 5v13", 1.3f),
    ArtPart.FillCircle(8.5f, 4f, 1.7f),
    ArtPart.FillCircle(35.5f, 19f, 1.7f),
)
private val MARKUP_HIGHLIGHT = listOf(ArtPart.FillRect(8f, 5f, 28f, 13f, 2f, opacity = 0.24f), MARKUP_WORD)
private val MARKUP_UNDERLINE = listOf(MARKUP_WORD, ArtPart.Stroke("M9 18.5h26", 1.6f))
private val MARKUP_STRIKE = listOf(MARKUP_WORD, ArtPart.Stroke("M8 11h28", 1.6f))
private val MARKUP_SQUIGGLY = listOf(MARKUP_WORD, ArtPart.Stroke("M8 19q2-2.4 4 0t4 0 4 0 4 0 4 0 4 0 4 0", 1.4f))

/** The word "Text" as [mode] marks it. */
internal fun markupArt(mode: MarkupMode): List<ArtPart> = when (mode) {
    MarkupMode.SELECT -> MARKUP_SELECT
    MarkupMode.HIGHLIGHT -> MARKUP_HIGHLIGHT
    MarkupMode.UNDERLINE -> MARKUP_UNDERLINE
    MarkupMode.STRIKEOUT -> MARKUP_STRIKE
    MarkupMode.SQUIGGLY -> MARKUP_SQUIGGLY
}

// --- previews (300×56) ---

/** The shape preview's outline (TO 706–710) as one path, so its fill and its dashes use the same geometry. */
internal fun shapePreviewPath(kind: ShapeKind): String = when (kind) {
    ShapeKind.LINE, ShapeKind.POLYLINE, ShapeKind.CURVE, ShapeKind.ARROW -> "M96 44L204 12"
    ShapeKind.RECTANGLE ->
        "M97.5 10H202.5A1.5 1.5 0 0 1 204 11.5V44.5A1.5 1.5 0 0 1 202.5 46H97.5A1.5 1.5 0 0 1 96 44.5V11.5A1.5 1.5 0 0 1 97.5 10Z"
    ShapeKind.ELLIPSE -> "M92 28A58 18 0 1 0 208 28A58 18 0 1 0 92 28Z"
    ShapeKind.CIRCLE -> "M131 28A19 19 0 1 0 169 28A19 19 0 1 0 131 28Z"
    ShapeKind.TRIANGLE, ShapeKind.POLYGON -> "M150 8.5L174 47H126Z"
}

/** The arrow's head in the preview, never dashed (TO 707). */
internal const val ARROW_HEAD_D = "M188 10.2L204 12l-7.2 14.6"

/** The laser preview's swoop (TO 752). */
internal const val LASER_PREVIEW_D = "M24 38C62 8 108 6 140 26s92 28 136-12"

// --- drawing ---

/** A part ready to draw: its path parsed and its stroke built once. */
internal class ArtPiece(val part: ArtPart, val path: Path?, val stroke: DrawStroke?, val inner: List<ArtPiece>)

/** A drawing ready to draw in a [width]×[height] viewBox. */
internal class PreparedArt(val width: Float, val height: Float, val pieces: List<ArtPiece>)

private fun artStroke(width: Float, dash: List<Float>?): DrawStroke = DrawStroke(
    width = width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
    pathEffect = dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) },
)

private fun artPath(d: String): Path = PathParser().parsePathString(d).toPath()

private fun piece(p: ArtPart): ArtPiece = when (p) {
    is ArtPart.Stroke -> ArtPiece(p, artPath(p.d), artStroke(p.width, p.dash), emptyList())
    is ArtPart.StrokeRect -> ArtPiece(p, null, artStroke(p.width, p.dash), emptyList())
    is ArtPart.StrokeCircle -> ArtPiece(p, null, artStroke(p.width, null), emptyList())
    is ArtPart.StrokeEllipse -> ArtPiece(p, null, artStroke(p.width, null), emptyList())
    is ArtPart.FillRect, is ArtPart.FillCircle, is ArtPart.Label -> ArtPiece(p, null, null, emptyList())
    is ArtPart.CutCircle -> ArtPiece(p, Path().apply { addOval(Rect(Offset(p.cx, p.cy), p.r)) }, null, p.parts.map(::piece))
}

/** [parts] parsed for drawing in a [width]×[height] viewBox. */
internal fun prepareArt(parts: List<ArtPart>, width: Float, height: Float): PreparedArt =
    PreparedArt(width, height, parts.map(::piece))

/** [prepareArt], once per drawing (the tables hand out the same list each time, so the key is stable). */
@Composable
internal fun rememberArt(parts: List<ArtPart>, width: Float, height: Float): PreparedArt =
    remember(parts, width, height) { prepareArt(parts, width, height) }

/**
 * The scale that fits a [designDp]-unit side into a side that `size` reports as [sizePx]. The option card's art scope
 * is already scaled by [density] (one unit is one dp) while `size` still reports pixels, so the box is
 * `sizePx / density` units long, not `sizePx`.
 */
internal fun artScale(sizePx: Float, density: Float, designDp: Float): Float = sizePx / density / designDp

/**
 * Draws [art] fitted and centred in an [com.xnotes.ui.kit.InkOptionCard] art scope, in [tint] (SVG `currentColor`).
 * That scope is in dp but its `size` is in px, so the box is measured in dp first ([artScale]). [label] is the markup
 * tiles' "Text", measured at [ArtPart.Label.size] dp. It is scaled from the measurer's px into viewBox units.
 * No allocation: everything was built by [prepareArt].
 */
internal fun DrawScope.drawArt(art: PreparedArt, tint: Color, label: TextLayoutResult? = null) {
    val k = minOf(artScale(size.width, density, art.width), artScale(size.height, density, art.height))
    val left = (size.width / density - art.width * k) / 2f
    val top = (size.height / density - art.height * k) / 2f
    translate(left, top) {
        scale(k, k, pivot = Offset.Zero) {
            for (p in art.pieces) if (p.part !is ArtPart.Label) drawPiece(p, tint)
        }
    }
    if (label == null) return
    val s = k / density
    for (p in art.pieces) {
        val part = p.part as? ArtPart.Label ?: continue
        translate(left + part.x * k, top + part.baseline * k) {
            scale(s, s, pivot = Offset.Zero) {
                drawText(
                    label,
                    color = tint.copy(alpha = tint.alpha * part.opacity),
                    topLeft = Offset(-label.size.width / 2f, -label.firstBaseline),
                )
            }
        }
    }
}

/** One piece in viewBox units (the caller has scaled the canvas). */
private fun DrawScope.drawPiece(p: ArtPiece, tint: Color) {
    val part = p.part
    val c = tint.copy(alpha = tint.alpha * part.opacity)
    when (part) {
        is ArtPart.Stroke -> {
            val path = p.path ?: return
            val stroke = p.stroke ?: return
            drawPath(path, c, style = stroke)
        }
        is ArtPart.StrokeRect -> {
            val stroke = p.stroke ?: return
            drawRoundRect(c, Offset(part.x, part.y), Size(part.w, part.h), CornerRadius(part.rx), style = stroke)
        }
        is ArtPart.FillRect -> drawRoundRect(c, Offset(part.x, part.y), Size(part.w, part.h), CornerRadius(part.rx))
        is ArtPart.StrokeCircle -> {
            val stroke = p.stroke ?: return
            drawCircle(c, part.r, Offset(part.cx, part.cy), style = stroke)
        }
        is ArtPart.FillCircle -> drawCircle(c, part.r, Offset(part.cx, part.cy))
        is ArtPart.StrokeEllipse -> {
            val stroke = p.stroke ?: return
            drawOval(c, Offset(part.cx - part.rx, part.cy - part.ry), Size(2f * part.rx, 2f * part.ry), style = stroke)
        }
        is ArtPart.CutCircle -> {
            val hole = p.path ?: return
            clipPath(hole, ClipOp.Difference) { for (q in p.inner) drawPiece(q, c) }
        }
        is ArtPart.Label -> Unit
    }
}
