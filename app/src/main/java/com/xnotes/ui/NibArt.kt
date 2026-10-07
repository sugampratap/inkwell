package com.xnotes.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.xnotes.core.tools.Tool
import com.xnotes.ui.theme.LocalInk

/** The nib drawings' SVG viewBox (0 0 44 22), drawn 1:1 in dp. */
internal const val NIB_ART_W = 44f
internal const val NIB_ART_H = 22f

/** A colour in the nib art: the theme's text colour (SVG `currentColor`) or one of the mockup's fixed metals and woods. */
internal sealed interface NibInk {
    data object Current : NibInk
    data class Fixed(val argb: Long) : NibInk
}

/**
 * One element of a nib drawing, in viewBox units, exactly as the mockup's SVG gives it. Pure data, so
 * the table is tested on the JVM; [NibArt] parses the paths and draws.
 */
internal sealed interface NibPart {
    val ink: NibInk
    val opacity: Float

    /** `<rect x y width height rx>`; SVG uses rx for both radii when ry is absent. */
    data class RoundRect(
        val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float,
        override val ink: NibInk, override val opacity: Float = 1f,
    ) : NibPart

    data class Circle(val cx: Float, val cy: Float, val r: Float, override val ink: NibInk, override val opacity: Float = 1f) : NibPart

    /** A filled `<path d>`. */
    data class FillPath(val d: String, override val ink: NibInk, override val opacity: Float = 1f) : NibPart

    /** A stroked `<path d>`: butt caps unless [roundCap] (`stroke-linecap="round"`); [dash] is `stroke-dasharray`. */
    data class StrokePath(
        val d: String, override val ink: NibInk, val width: Float,
        val roundCap: Boolean = false, val dash: List<Float>? = null, override val opacity: Float = 1f,
    ) : NibPart
}

private val GREY_9A = NibInk.Fixed(0xFF9A9A9A)
private val GREY_A6 = NibInk.Fixed(0xFFA6A6A6)
private val GREY_6E = NibInk.Fixed(0xFF6E6E6E)
private val BRASS = NibInk.Fixed(0xFFC9A15A)
private val BRASS_DARK = NibInk.Fixed(0xFF6E5222)
private val WOOD = NibInk.Fixed(0xFF9A6B43)

// W 1078: a capped body, a grey section and a brass nib with its slit and breather hole.
private val FOUNTAIN = listOf(
    NibPart.RoundRect(21f, 7f, 21f, 8f, 3f, NibInk.Current),
    NibPart.RoundRect(16f, 8f, 6f, 6f, 1.5f, GREY_9A),
    NibPart.FillPath("M16.5 8.2L4.5 11l12 2.8z", BRASS),
    NibPart.StrokePath("M5.5 11h8", BRASS_DARK, 0.8f),
    NibPart.Circle(13.2f, 11f, 1f, BRASS_DARK),
)

// W 1079: a round body, a grey cone, the ball and the clip.
private val BALLPOINT = listOf(
    NibPart.RoundRect(18f, 7f, 24f, 8f, 4f, NibInk.Current),
    NibPart.FillPath("M18.5 7.6L7 11l11.5 3.4z", GREY_A6),
    NibPart.Circle(6.6f, 11f, 1.4f, NibInk.Current),
    NibPart.RoundRect(28f, 5.6f, 11f, 2f, 1f, NibInk.Current, opacity = 0.55f),
)

// W 1080 "brush": the app's Brush pen (TAPER).
private val BRUSH = listOf(
    NibPart.RoundRect(23f, 7.4f, 19f, 7.2f, 3.4f, NibInk.Current),
    NibPart.RoundRect(17f, 7.6f, 7f, 6.8f, 1.2f, GREY_A6),
    NibPart.FillPath("M17.4 7.8C11 8.6 6.8 10 2.6 11c4.2 1 8.4 2.4 14.8 3.2z", WOOD),
)

// W 1081: a broad, square-edged nib.
private val CALLIGRAPHY = listOf(
    NibPart.RoundRect(21f, 7f, 21f, 8f, 3f, NibInk.Current),
    NibPart.FillPath("M21.5 7.4L7 6.6v8.8l14.5-.8z", GREY_A6),
    NibPart.StrokePath("M7 6.6v8.8", NibInk.Current, 1.6f, roundCap = true),
    NibPart.StrokePath("M8 11h9", GREY_6E, 0.8f),
)

// W 1086 "speed": the app's Quill (SPEED), the pen that thins with speed (round-2 default 1).
private val SPEED = listOf(
    NibPart.StrokePath("M12 15C20 9 29 7 41 8", NibInk.Current, 3f, roundCap = true),
    NibPart.StrokePath("M2.5 10.5h6M4 14.5h5", NibInk.Current, 1.4f, roundCap = true, opacity = 0.55f),
)

// A hexagonal pencil, lying tip-left like the others: the painted body, the metal ferrule with its
// crimp line and the eraser past it, then the sharpened wood cone and its graphite point.
private val PENCIL = listOf(
    NibPart.RoundRect(21f, 7f, 16.8f, 8f, 1f, NibInk.Current),
    NibPart.RoundRect(37.4f, 6.8f, 2.8f, 8.4f, 0.6f, GREY_A6),
    NibPart.StrokePath("M38.8 7.2V14.8", GREY_6E, 0.5f),
    NibPart.RoundRect(40f, 7.3f, 2.6f, 7.4f, 1.3f, NibInk.Current, opacity = 0.55f),
    NibPart.FillPath("M21.6 7.2L10.2 10.1v1.8l11.4 2.9z", WOOD),
    NibPart.FillPath("M10.6 10L5.4 11l5.2 1z", NibInk.Current),
)

// The dashed pen: a fineliner like the others (body, grey section, metal tube tip), laying its dashed
// line out of the tip, so the tile shows a pen and what it draws.
private val DASHED = listOf(
    NibPart.RoundRect(24f, 7f, 18f, 8f, 3f, NibInk.Current),
    NibPart.RoundRect(19.4f, 8f, 5.2f, 6f, 1.2f, GREY_9A),
    NibPart.FillPath("M19.8 9.3L14.6 10.3v1.4l5.2 1z", GREY_A6),
    NibPart.StrokePath("M12.6 11H2.2", NibInk.Current, 1.6f, roundCap = true, dash = listOf(1f, 3f)),
)

/** The drawing on [tool]'s tile in the pen card, back to front; empty for a tool that is not a pen. */
internal fun nibArt(tool: Tool): List<NibPart> = when (tool) {
    Tool.PEN -> FOUNTAIN
    Tool.BALLPOINT -> BALLPOINT
    Tool.TAPER -> BRUSH
    Tool.CALLIGRAPHY -> CALLIGRAPHY
    Tool.SPEED -> SPEED
    Tool.PENCIL -> PENCIL
    Tool.DASHED -> DASHED
    else -> emptyList()
}

/** A part ready to draw: its path parsed and its stroke built once per draw cache, never per frame. */
private class PreparedPart(val part: NibPart, val path: Path?, val stroke: Stroke?)

private fun prepare(part: NibPart): PreparedPart = when (part) {
    is NibPart.RoundRect, is NibPart.Circle -> PreparedPart(part, null, null)
    is NibPart.FillPath -> PreparedPart(part, PathParser().parsePathString(part.d).toPath(), null)
    is NibPart.StrokePath -> PreparedPart(
        part,
        PathParser().parsePathString(part.d).toPath(),
        Stroke(
            width = part.width,
            cap = if (part.roundCap) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = part.dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) },
        ),
    )
}

private fun DrawScope.drawPart(p: PreparedPart, current: Color) {
    val part = p.part
    val base = when (val i = part.ink) {
        NibInk.Current -> current
        is NibInk.Fixed -> Color(i.argb)
    }
    val c = base.copy(alpha = base.alpha * part.opacity)
    val path = p.path
    val stroke = p.stroke
    when (part) {
        is NibPart.RoundRect -> drawRoundRect(c, Offset(part.x, part.y), Size(part.w, part.h), CornerRadius(part.rx))
        is NibPart.Circle -> drawCircle(c, part.r, Offset(part.cx, part.cy))
        is NibPart.FillPath -> if (path != null) drawPath(path, c)
        is NibPart.StrokePath -> if (path != null && stroke != null) drawPath(path, c, style = stroke)
    }
}

/**
 * [tool]'s nib, as the pen card's tiles draw it (mockup §4.3): the 44×22 viewBox at 44×22 dp, the pen
 * lying tip-left, its body in the theme's text colour and its metal and wood in fixed colours. Decorative:
 * the tile's label names the pen. Paths and strokes are built in the draw cache, so a redraw allocates nothing.
 */
@Composable
internal fun NibArt(tool: Tool, modifier: Modifier = Modifier) {
    val current = LocalInk.current.text
    val parts = remember(tool) { nibArt(tool) }
    Spacer(
        modifier
            .size(NIB_ART_W.dp, NIB_ART_H.dp)
            .drawWithCache {
                val k = size.width / NIB_ART_W
                val drawn = parts.map(::prepare)
                onDrawBehind {
                    scale(k, k, pivot = Offset.Zero) {
                        for (p in drawn) drawPart(p, current)
                    }
                }
            },
    )
}
