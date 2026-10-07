package com.xnotes.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.LocalInk

/**
 * The select-mode mark on a tile's bottom-right corner (.selc), 26dp:
 * - **Off:** a 28% black disc in a 2dp white ring, readable on cream, cloth and black alike.
 * - **On:** a near-black (dark themes: near-white) disc with a check, inside a 2dp halo of the theme's background.
 *   The halo keeps the disc off a like-coloured page: #F2F2F2 straight on a cream page is 1.10:1.
 *
 * It fades in over 120 ms when select mode starts, and the check cross-fades as it is picked. Both values
 * are read only while drawing. The fade in is [shown], read from one value the select mode's owner animates, so
 * a tile scrolled into view later shows at once rather than fading in again; without it, the mark fades itself in.
 * The tile, not the mark, carries the selected state for accessibility.
 * Give it 8dp from the corner (its box is 30dp, halo included).
 */
@Composable
fun SelectCheck(selected: Boolean, modifier: Modifier = Modifier, shown: (() -> Float)? = null) {
    val ink = LocalInk.current
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(InkMotion.FAST, easing = InkMotion.Standard), label = "selectCheck")
    val own = if (shown == null) remember { Animatable(0f) } else null
    if (own != null) LaunchedEffect(Unit) { own.animateTo(1f, tween(InkMotion.FAST, easing = InkMotion.Standard)) }
    Box(
        modifier
            .size(30.dp)
            .graphicsLayer { alpha = shown?.invoke() ?: own?.value ?: 1f }
            .drawWithCache {
                val r = 13.dp.toPx()
                val ring = 2.dp.toPx()
                val stroke = Stroke(ring)
                onDrawBehind {
                    if (on < 1f) {
                        drawCircle(Color.Black.copy(alpha = 0.28f * (1f - on)), r, center)
                        drawCircle(Color.White.copy(alpha = 1f - on), r - ring / 2f, center, style = stroke)
                    }
                    if (on > 0f) {
                        drawCircle(ink.bg.copy(alpha = on), r + ring, center)
                        drawCircle(ink.solid.copy(alpha = on), r, center)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ph.check, null, tint = ink.onSolid, modifier = Modifier.size(14.dp).graphicsLayer { alpha = on })
    }
}

/**
 * A selected thumbnail's 2dp near-black ring, drawn 2dp *outside* its bounds, so it is set against the background
 * rather than the page, in every theme. Put it before the thumbnail's `clip`, or the clip cuts it off.
 */
@Composable
fun Modifier.inkSelectionRing(selected: Boolean, shape: Shape): Modifier {
    val ink = LocalInk.current
    if (!selected) return this
    return this.drawWithCache {
        val w = 2.dp.toPx()
        val o = 2.dp.toPx() + w / 2f
        val outline = shape.createOutline(Size(size.width + 2 * o, size.height + 2 * o), layoutDirection, this)
        val stroke = Stroke(w)
        onDrawWithContent {
            drawContent()
            translate(-o, -o) { drawOutline(outline, ink.solid, style = stroke) }
        }
    }
}

/** The square check a list row shows in select mode: 20dp, r6; a --line3 outline off, solid with a check on. */
@Composable
fun InkRowCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Box(
        modifier.size(20.dp).drawBehind {
            val r = CornerRadius(6.dp.toPx())
            if (selected) {
                drawRoundRect(ink.solid, cornerRadius = r)
            } else {
                val w = 2.dp.toPx()
                drawRoundRect(ink.line3, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(6.dp.toPx() - w / 2), style = Stroke(w))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Ph.check, null, tint = ink.onSolid, modifier = Modifier.size(14.dp))
    }
}
