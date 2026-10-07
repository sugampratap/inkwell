package com.xnotes.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded

// A constant r24, not 50%: a pill at the 48dp one-line height, but a long message keeps the same corners (.au-toast).

/**
 * The B2 toast: an ink card (light in dark themes) with r24 corners, 48dp tall for one line, bold message,
 * an optional underlined action. A long message wraps to 3 lines (12dp above and below, as .au-toast) and
 * the toast is at most 560dp wide (.au-toast) unless the caller's [modifier] fixes a width (.ti-tw uses 360).
 *
 * [liveRegion] makes the toast announce itself politely. Pass false when the host already does, as
 * Material's `SnackbarHost` wraps each snackbar in its own live region: nested ones are read twice.
 */
@Composable
fun InkToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    liveRegion: Boolean = true,
) {
    val ink = LocalInk.current
    val toastShape = inkRounded(24.dp)
    Row(
        modifier
            .then(if (liveRegion) Modifier.semantics { this.liveRegion = LiveRegionMode.Polite } else Modifier)
            .widthIn(max = 560.dp)
            .heightIn(min = 48.dp)
            .shadow(10.dp, toastShape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .clip(toastShape)
            .background(ink.toast)
            .padding(start = if (icon != null) 14.dp else 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = ink.toastInk, modifier = Modifier.size(20.dp))
        Text(message, style = InkType.body.copy(fontWeight = FontWeight.Bold), color = ink.toastInk, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = InkType.body.copy(fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline),
                color = ink.toastInk,
                modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 6.dp, vertical = 10.dp),
            )
        }
    }
}

private val heart by lazy {
    PathParser().parsePathString(
        "M16 27.4C9.6 23.3 3.6 18.3 3.6 11.7 3.6 8 6.5 5.1 10.1 5.1c2.4 0 4.6 1.2 5.9 3.1 1.3-1.9 3.5-3.1 5.9-3.1 3.6 0 6.5 2.9 6.5 6.6 0 6.6-6 11.6-12.4 15.7z",
    ).toPath()
}

/**
 * The favourite heart: a 24dp glyph (white outline, dimmed fill; marigold when on) in a 44dp
 * target. Turning it on beats it once, 1 → 1.2 → 1; only the glyph's layer scales.
 */
@Composable
fun HeartToggle(on: Boolean, onToggle: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val beat = remember { Animatable(1f) }
    var was by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !was) {
            beat.animateTo(1.2f, tween(90, easing = InkMotion.Standard))
            beat.animateTo(1f, InkMotion.pop())
        } else {
            // Turned off mid-beat: this effect restarted, so the cancelled beat must not leave the glyph enlarged.
            beat.snapTo(1f)
        }
        was = on
    }
    Box(
        modifier
            .size(44.dp)
            .toggleable(on, remember { MutableInteractionSource() }, null, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp).graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
            val k = size.minDimension / 32f
            scale(k, k, pivot = Offset.Zero) {
                drawPath(heart, if (on) ink.brand else Color.Black.copy(alpha = 0.42f))
                drawPath(heart, Color.White, style = Stroke(2.1f, join = StrokeJoin.Round))
            }
        }
    }
}

/** A 4dp reading-progress line (.prog): --line2 track, marigold fill. [fraction] is 0..1. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(ink.line2, cornerRadius = r)
        drawRoundRect(ink.brand, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}
