package com.xnotes.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded

private val SegmentLabel = InkType.chip.copy(fontSize = 13.sp)

/**
 * The pill segmented control (.segc): a --surface pill with a raised thumb that glides (glide spring,
 * transform only) to the chosen segment; each segment is [segmentWidth] wide. Apply to in Page setup,
 * Pages in Share.
 */
@Composable
fun <T> InkPillSegmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    segmentWidth: Dp = 112.dp,
) {
    val ink = LocalInk.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val pos = animateFloatAsState(index.toFloat(), InkMotion.glide(), label = "segc")
    Box(modifier.clip(CircleShape).background(ink.surface).border(1.dp, ink.line2, CircleShape).padding(4.dp)) {
        Box(
            Modifier
                .size(segmentWidth, 36.dp)
                .graphicsLayer { translationX = segmentWidth.toPx() * pos.value }
                .shadow(1.dp, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
                .background(ink.raised, CircleShape)
                .border(1.dp, ink.line2, CircleShape),
        )
        Row(Modifier.selectableGroup()) {
            options.forEach { o ->
                val on = o == selected
                val src = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(segmentWidth, 36.dp)
                        .pressScale(src, 0.95f)
                        .clip(CircleShape)
                        .selectable(on, src, null, role = Role.RadioButton) { if (!on) onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), style = SegmentLabel.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold), color = if (on) ink.text else ink.text2, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The box segmented control (.svseg, .st-seg): a --surface track with the theme's small corners and
 * 4dp of padding; the chosen segment is a raised, hairlined button with one soft shadow. Settings
 * rows and the toolbar customiser's tabs. Segments are at least [minSegment] wide, or share the row
 * equally with [fill]. [gap] parts the segments: 2dp in settings (.st-seg), 4dp in Sort & view (.svseg).
 */
@Composable
fun <T> InkBoxSegmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minSegment: Dp = 70.dp,
    height: Dp = 32.dp,
    fill: Boolean = false,
    gap: Dp = 2.dp,
) {
    val ink = LocalInk.current
    val seg = inkRounded(9.dp)
    Row(
        modifier.clip(MaterialTheme.shapes.small).background(ink.surface).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier.widthIn(min = minSegment))
                    .height(height)
                    .then(if (on) Modifier.shadow(1.dp, seg, ambientColor = ink.shadow, spotColor = ink.shadow).background(ink.raised, seg).border(1.dp, ink.line2, seg) else Modifier)
                    .clip(seg)
                    .selectable(on, role = Role.RadioButton) { if (!on) onSelect(o) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = SegmentLabel.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold), color = if (on) ink.text else ink.text2, maxLines = 1)
            }
        }
    }
}
