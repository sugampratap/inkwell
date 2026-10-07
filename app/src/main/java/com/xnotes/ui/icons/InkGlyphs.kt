package com.xnotes.ui.icons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.InkMotion

/** The three tools Phosphor has no right shape for. */
enum class InkGlyph { LASSO, LASER, TAPE }

private const val ANT_DASH = 2.5f
private const val ANT_GAP = 2.1f
private const val ANT_PERIOD = ANT_DASH + ANT_GAP

private val line = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The lasso's marching dashes at [phase]. A new effect per phase, so [antsRest] serves every still frame. */
private fun ants(phase: Float) = Stroke(
    1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(ANT_DASH, ANT_GAP), phase),
)

// Lazy: a top-level dashPathEffect would reach android.graphics during class init and break JVM tests.
private val antsRest by lazy { ants(0f) }

private fun path(d: String) = PathParser().parsePathString(d).toPath()
private val lassoRope by lazy { path("M7.3 13.6c-1.5 1.3-2.2 3-1.9 4.9") }
private val laserRays by lazy { path("M19 5l1.8-1.8M19.7 8.4h2.4M15.7 4.7V2.4") }
private val tapeTint by lazy { path("M8.6 3.8a5.6 5.6 0 1 0 0 11.2h12l-1.4 1.5 1.4 1.5-1.4 1.5 1.4 1.5H8.6V15a5.6 5.6 0 0 1 0-11.2z") }
private val tapeStrip by lazy { path("M8.6 15h12l-1.4 1.5 1.4 1.5-1.4 1.5 1.4 1.5H8.6") }
private val tapeTear by lazy { path("M12.4 21l2.6-6M16 21l2.6-6") }

/**
 * A custom tool glyph drawn as B2 draws it, a drop-in for Material's `Icon`: it is 22dp unless the
 * caller's [modifier] fixes a size, and a non-null [contentDescription] is exposed as an image for
 * TalkBack (so a toolbar button can take its label from it, as it does from an `Icon`).
 *
 * [active] adds the Duotone-style 20% tint and fills the solid parts (never a solid blob: the Fill
 * lasso was rejected as cheap). The lasso's dashes march once, over [InkMotion.ANTS], as it becomes
 * active.
 */
@Composable
fun InkGlyphIcon(
    glyph: InkGlyph,
    active: Boolean,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Only the lasso animates; the other glyphs carry no animation state at all.
    val antsPhase = if (glyph == InkGlyph.LASSO) rememberAntsPhase(active) else null
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(modifier.size(22.dp).then(semantics)) {
        val k = size.minDimension / 24f
        // Centre the 24-unit grid in a non-square canvas, like SVG's xMidYMid meet.
        translate((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f) {
            scale(k, k, pivot = Offset.Zero) {
                when (glyph) {
                    InkGlyph.LASSO -> lasso(active, tint, antsPhase?.value ?: 0f)
                    InkGlyph.LASER -> laser(active, tint)
                    InkGlyph.TAPE -> tape(active, tint)
                }
            }
        }
    }
}

/**
 * The lasso's dash phase: one full period to 0 over [InkMotion.ANTS] whenever [active] turns on.
 * Read it in a draw block only, so the march redraws without recomposing. Turning [active] off
 * rests at 0 at once, as the mockup drops the animation with `.on`.
 */
@Composable
private fun rememberAntsPhase(active: Boolean): State<Float> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            phase.snapTo(ANT_PERIOD)
            phase.animateTo(0f, tween(InkMotion.ANTS, easing = InkMotion.Standard))
        } else {
            phase.snapTo(0f)
        }
    }
    return phase.asState()
}

private fun DrawScope.lasso(active: Boolean, tint: Color, phase: Float) {
    rotate(-10f, pivot = Offset(13.4f, 9.2f)) {
        val topLeft = Offset(13.4f - 8.2f, 9.2f - 5.6f)
        val oval = Size(16.4f, 11.2f)
        if (active) drawOval(tint.copy(alpha = tint.alpha * 0.2f), topLeft, oval, style = Fill)
        drawOval(tint, topLeft, oval, style = if (phase == 0f) antsRest else ants(phase))
    }
    drawPath(lassoRope, tint, style = line)
    if (active) drawCircle(tint, 1.6f, Offset(5.4f, 20.1f))
    drawCircle(tint, 1.6f, Offset(5.4f, 20.1f), style = line)
}

private fun DrawScope.laser(active: Boolean, tint: Color) {
    rotate(-45f, pivot = Offset(8.7f, 15.4f)) {
        val topLeft = Offset(2.4f, 13.3f)
        val bar = Size(12.6f, 4.2f)
        if (active) drawRoundRect(tint.copy(alpha = tint.alpha * 0.2f), topLeft, bar, CornerRadius(2.1f))
        drawRoundRect(tint, topLeft, bar, CornerRadius(2.1f), style = line)
    }
    if (active) drawCircle(tint, 1.6f, Offset(16.6f, 7.4f))
    drawCircle(tint, 1.6f, Offset(16.6f, 7.4f), style = line)
    drawPath(laserRays, tint, style = line)
}

private fun DrawScope.tape(active: Boolean, tint: Color) {
    if (active) drawPath(tapeTint, tint.copy(alpha = tint.alpha * 0.2f))
    drawCircle(tint, 5.6f, Offset(8.6f, 9.4f), style = line)
    drawCircle(tint, 2f, Offset(8.6f, 9.4f), style = line)
    drawPath(tapeStrip, tint, style = line)
    drawPath(tapeTear, tint, style = line)
}
