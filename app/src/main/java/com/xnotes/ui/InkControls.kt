package com.xnotes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * The controls the Inkwell sheets and popovers are built from: a caption row, a thin slider, a
 * segmented picker and tiles. Material's own are a size too heavy for a popover over a page, so
 * these follow B2 instead: a 4dp near-black track with a white knob, a sliding segment thumb,
 * outlined tiles and swatches that take a 2dp near-black ring when chosen.
 */

/** "Thickness ........ 0.5 mm": what a control sets (semibold body), and what it is set to (tabular, secondary). */
@Composable
internal fun InkCaption(label: String, value: String? = null, enabled: Boolean = true) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = InkType.body.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) ink.text else ink.text3, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = InkType.body.copy(fontWeight = FontWeight.SemiBold).tnum(), color = ink.text2)
    }
}

/**
 * B2's thin slider: a 4dp grey track filled near-black up to a white 24dp knob that grows while
 * dragged. [onChangeFinished] runs when the finger lifts, for callers that persist on release
 * rather than on every step.
 *
 * Only a sideways drag moves it, so a vertical scroll that starts on the slider still scrolls its
 * popover. It reads as a range to accessibility services (which can set it), and once focused the
 * arrow keys step it by 5%, Page Up / Page Down by 10%, and Home / End jump to the ends; each of
 * those counts as a finished change.
 */
@Composable
internal fun InkSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChangeFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    val ink = LocalInk.current
    val fill = if (enabled) ink.solid else ink.text3
    val track = ink.track
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val shown by animateFloatAsState(fraction, tween(60), label = "inkSlider")
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1.14f else 1f, InkMotion.press(), label = "inkThumb")
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val current by rememberUpdatedState(value)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val knobR = 12.dp
    // The knob's centre travels between one radius in from either end, so a touch is read the same way.
    fun at(x: Float, r: Float): Float {
        val f = ((x - r) / (widthPx - 2 * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return range.start + f * span
    }
    // A one-shot set (accessibility or a key): clamp, then finish at once, as a tap does.
    fun commit(v: Float) {
        change(v.coerceIn(range.start, range.endInclusive))
        finished?.invoke()
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            // Measured here rather than written from the draw pass, so drawing stays read-only.
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(range.start, range.endInclusive), range)
                if (enabled) {
                    setProgress { t ->
                        val v = t.coerceIn(range.start, range.endInclusive)
                        if (v == current) false else { commit(v); true }
                    }
                } else {
                    disabled()
                }
            }
            // onKeyEvent sits before focusable() so the focused slider's own key events reach it.
            .then(
                if (!enabled) Modifier
                else Modifier
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val d = when (e.key) {
                            Key.DirectionRight, Key.DirectionUp -> span / 20f
                            Key.DirectionLeft, Key.DirectionDown -> -span / 20f
                            Key.PageUp -> span / 10f
                            Key.PageDown -> -span / 10f
                            Key.MoveHome -> -span
                            Key.MoveEnd -> span
                            else -> return@onKeyEvent false
                        }
                        commit(current + d)
                        true
                    }
                    .focusable(),
            )
            .then(
                if (!enabled) Modifier
                else Modifier
                    .pointerInput(range) {
                        detectTapGestures { change(at(it.x, knobR.toPx())); finished?.invoke() }
                    }
                    .pointerInput(range) {
                        // Sideways only: a vertical drag is left to the popover's scroll.
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; change(at(it.x, knobR.toPx())) },
                            onDragEnd = { dragging = false; finished?.invoke() },
                            onDragCancel = { dragging = false; finished?.invoke() },
                        ) { c, _ ->
                            c.consume()
                            change(at(c.position.x, knobR.toPx()))
                        }
                    },
            )
            .drawBehind {
                val r = knobR.toPx()
                val h = 4.dp.toPx()
                val cy = size.height / 2
                val usable = size.width - 2 * r
                // Under the finger the knob follows it exactly; the short glide is for taps and keys,
                // since the tween restarts on every caller recomposition and would trail a drag.
                val x = r + usable * (if (dragging) fraction else shown)
                drawRoundRect(track, Offset(r, cy - h / 2), Size(usable, h), CornerRadius(h / 2))
                drawRoundRect(fill, Offset(r, cy - h / 2), Size((x - r).coerceAtLeast(0f), h), CornerRadius(h / 2))
                val kr = r * grow
                drawCircle(Color.Black.copy(alpha = 0.10f), kr + 1.dp.toPx(), Offset(x, cy + 2.dp.toPx()))
                drawCircle(Color.White, kr, Offset(x, cy))
                drawCircle(Color.Black.copy(alpha = 0.16f), kr - 0.5.dp.toPx(), Offset(x, cy), style = Stroke(1.dp.toPx()))
            },
    )
}

/** [InkCaption] over an [InkSlider], the shape every tuned value takes in a popover. */
@Composable
internal fun InkSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChangeFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        InkCaption(label, valueText, enabled)
        InkSlider(value, range, enabled = enabled, onChangeFinished = onChangeFinished, onChange = onChange)
    }
}

/**
 * B2's segmented picker (.stabseg): a 34dp grey track whose raised thumb glides to the chosen
 * segment, with hairlines between the others; the chosen label goes bold. Each entry is a
 * label, an icon, or both.
 */
@Composable
internal fun <T> InkSegmented(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String? = { null },
    icon: (T) -> ImageVector? = { null },
    iconRotation: (T) -> Float = { 0f },
    onSelect: (T) -> Unit,
) {
    val ink = LocalInk.current
    val n = options.size.coerceAtLeast(1)
    val index = options.indexOf(selected).coerceAtLeast(0)
    val pos by animateFloatAsState(index.toFloat(), InkMotion.glide(), label = "segThumb")
    val thumbShape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(inkRounded(10.dp))
            .background(ink.segTrack)
            .padding(2.dp),
    ) {
        // Hairlines between the segments, hidden beside the chosen one (.stabseg).
        Box(
            Modifier.matchParentSize().drawBehind {
                val w = size.width / n
                for (i in 1 until n) {
                    if (i != index && i != index + 1) {
                        drawRect(ink.line, Offset(w * i - 0.5.dp.toPx(), 7.dp.toPx()), Size(1.dp.toPx(), size.height - 14.dp.toPx()))
                    }
                }
            },
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / n)
                .graphicsLayer { translationX = size.width * pos }
                .shadow(2.dp, thumbShape, ambientColor = ink.shadow, spotColor = ink.shadow)
                .background(ink.segThumb, thumbShape),
        )
        Row(Modifier.matchParentSize()) {
            options.forEachIndexed { i, o ->
                val on = i == index
                val fg = if (on) ink.text else ink.text2
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(thumbShape)
                        .clickable(role = Role.Tab) { onSelect(o) }
                        .semantics { this.selected = on },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon(o)?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp).rotate(iconRotation(o))) }
                    val text = label(o)
                    if (text != null) {
                        if (icon(o) != null) Spacer(Modifier.width(5.dp))
                        Text(
                            text,
                            style = InkType.body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                            color = fg,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one colour chip (.pcol B 438, .swc B 671, .sc-sw SC 97): a [size] dot with the swatch ring ([drawSwatchRing]);
 * [selected] adds a 2 dp gap in --raised and a 2 dp near-black ring outside it (a shape cue, never colour alone). Its
 * touch box is [cell], the dot plus 4 dp a side unless a packed row passes less (the chosen ring may then reach past
 * it). It shrinks to .9 under the finger. With no [onClick] it is a picture inside a row that takes the tap.
 * [contentDescription] names it for TalkBack; [enabled] false fades it to .4 and takes no taps. Part 5 folded
 * ColourDot, ColorDot, PickerSwatch and MarkupDot into it.
 */
@Composable
internal fun InkSwatch(
    color: Color,
    selected: Boolean,
    size: Dp = 22.dp,
    cell: Dp = size + 8.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val ink = LocalInk.current
    val dot = size
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(cell)
            // Only a disabled swatch takes the layer: a picker shows some sixty, nearly always enabled.
            .then(if (enabled) Modifier else Modifier.alpha(0.4f))
            .then(
                if (onClick == null) Modifier else Modifier
                    .pressScale(src, 0.9f)
                    .clickable(src, null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                    .semantics {
                        this.selected = selected
                        if (contentDescription != null) this.contentDescription = contentDescription
                    },
            )
            .drawBehind {
                val r = dot.toPx() / 2f
                if (selected) {
                    drawCircle(ink.solid, r + 4.dp.toPx())
                    drawCircle(ink.raised, r + 2.dp.toPx())
                }
                drawCircle(color, r)
                drawSwatchRing(ink.isDark, r)
            },
    )
}

/**
 * The custom-colour dot (.padd B 442-444, .tx-cdot TX 170-172, .ti-cust TI 57-69, .pn-add). With no [colour]: a
 * dashed 1.5 dp --line3 ring of whole dashes round a 15 dp plus, solid near-black while [lit] (its picker is open, or
 * a custom colour is in use where the dot never fills, as on the pen card). With a [colour]: an [InkSwatch] of it,
 * ringed while [chosen]. [size] is the dot, [cell] its touch box. It opens nothing itself: the caller composes the
 * picker in the same Box, so the picker hangs from it. Part 5 folded AddColourDot, both CustomColourDots,
 * PaperAddSwatch and the sticky strip's ColorPickerDot into it.
 */
@Composable
internal fun InkAddSwatch(
    colour: Rgba?,
    lit: Boolean,
    contentDescription: String,
    chosen: Boolean = colour != null,
    size: Dp = 28.dp,
    cell: Dp = size,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (colour != null) {
        InkSwatch(colour.copy(a = 255).toComposeColor(), chosen, size, cell, contentDescription = contentDescription, modifier = modifier, onClick = onClick)
        return
    }
    val ink = LocalInk.current
    val dot = size
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(cell)
            .pressScale(src, 0.9f)
            .clickable(src, null, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = lit
            }
            .drawWithCache {
                val w = 1.5.dp.toPx()
                val r = dot.toPx() / 2f - w / 2f
                // Whole dashes round the circle, so no short one where they meet.
                val period = (2 * PI * r / 4.5.dp.toPx()).roundToInt().coerceAtLeast(8)
                val seg = (2 * PI * r / period).toFloat()
                val dashed = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(seg * 0.6f, seg * 0.4f)))
                val solid = Stroke(w)
                onDrawBehind { drawCircle(if (lit) ink.solid else ink.line3, r, style = if (lit) solid else dashed) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ph.plus, null, tint = ink.text, modifier = Modifier.size(15.dp))
    }
}
