package com.xnotes.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf


/** A disabled .ib fades to .3 (.ib:disabled); the labelled buttons to .4 (the mockup's disabled Empty trash). */
private const val IconDisabledAlpha = 0.3f
private const val ButtonDisabledAlpha = 0.4f

private val GhostTextStyle = InkType.buttonSmall.copy(textDecoration = TextDecoration.Underline)

/**
 * Fades everything drawn below it as one piece, like [alpha], but through a layer that reaches [reach]
 * past the bounds: a graphics layer is bounds-sized and would cut off the New button's raised face.
 */
private fun Modifier.fadeWithReach(alpha: Float, reach: Dp): Modifier =
    if (alpha >= 1f) this else drawWithContent {
        val r = reach.toPx()
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(-r, -r, size.width + r, size.height + r), Paint().apply { this.alpha = alpha })
            drawContent()
            canvas.restore()
        }
    }

/**
 * A round icon button (.ib): 44dp target, shrinks to .94 while held; [on] wears the selected-row fill and
 * is announced as selected. With [toggle] (a button that switches something on and off) it is a switch
 * instead, announced on and off alike. An icon-only button needs a name, so [contentDescription] is required.
 * Sizes below 44dp (e.g. the 36dp .ib.sm) rely on Compose's minimum touch-target expansion for their hit area.
 */
@Composable
fun InkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    on: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = LocalInk.current.text,
    toggle: Boolean = false,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(src, 0.94f)
            .alpha(if (enabled) 1f else IconDisabledAlpha)
            .clip(CircleShape)
            .then(if (on) Modifier.background(ink.sel) else Modifier)
            .then(
                if (toggle) {
                    Modifier.toggleable(on, src, LocalIndication.current, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
                } else {
                    Modifier
                        .then(if (on) Modifier.semantics { selected = true } else Modifier)
                        .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** The primary action inside sheets and cards (.btn-strong): near-black, 48dp, auto width. */
@Composable
fun InkStrongButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .pressScale(src, 0.97f)
            .alpha(if (enabled) 1f else ButtonDisabledAlpha)
            .clip(MaterialTheme.shapes.small)
            .background(ink.solid)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = ink.onSolid, modifier = Modifier.size(18.dp))
        Text(label, style = InkType.button, color = ink.onSolid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An outlined button (.btn-sec): 44dp, hairline that darkens while held. [on] is the chosen state
 * (2dp near-black, announced as selected); [danger] is red text and icon on the usual outline
 * (.btn-danger), as for "Empty trash".
 */
@Composable
fun InkSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    on: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    // Read only while drawing: a press redraws the ring and recomposes nothing.
    val held = src.collectIsPressedAsState()
    val fg = if (danger) ink.danger else ink.text
    val ringWidth = if (on) 2.dp else 1.dp
    val radius = cornerOf(12.dp)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .pressScale(src, 0.96f)
            .alpha(if (enabled) 1f else ButtonDisabledAlpha)
            .clip(MaterialTheme.shapes.small)
            // .btn-sec.btn-danger only recolours the text: the outline stays --line, and goes --text when held or on.
            // Drawn over the content, so over the press overlay too; inset by half its width, like a border.
            .drawWithContent {
                drawContent()
                val w = ringWidth.toPx()
                drawRoundRect(
                    color = if (on || held.value) ink.text else ink.line,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx() - w / 2f),
                    style = Stroke(w),
                )
            }
            .then(if (on) Modifier.semantics { selected = true } else Modifier)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, style = InkType.buttonSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A text action (.btn-ghost): underlined, no outline. Red with [danger] (.btn-danger). */
@Composable
fun InkGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val fg = if (danger) ink.danger else ink.text
    Row(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else ButtonDisabledAlpha)
            .clip(MaterialTheme.shapes.small)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, style = GhostTextStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The app's primary action (New, Create): a solid near-black button with its label in [InkTokens.onSolid], the same
 * near-black as selection and the toolbar's glider (white on Dark and OLED). Flat, one press scale; the user chose it
 * over the marigold gradient (2026-10-07). Keeps its name and call so every caller follows.
 */
@Composable
fun InkBrandButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(cornerOf(12.dp))
    Row(
        modifier
            .height(48.dp)
            .pressScale(src, 0.96f)
            .alpha(if (enabled) 1f else ButtonDisabledAlpha)
            .clip(shape)
            .background(ink.solid)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = ink.onSolid, modifier = Modifier.size(20.dp))
        Text(label, style = InkType.brandButton, color = ink.onSolid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
