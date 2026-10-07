package com.xnotes.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum

private val ChipLabel = InkType.chip.copy(fontWeight = FontWeight.SemiBold)
private val ChipLabelSelected = InkType.chip.copy(fontWeight = FontWeight.ExtraBold)

/** .tval: 15/20 bold, which is InkType.button, with tabular figures. */
private val StepperValue = InkType.button.tnum()

/** .tval at 14 (TI 167, TX 178): the table style card's and the text card's margin steppers. */
private val StepperValueSmall = InkType.button.copy(fontSize = 14.sp).tnum()

/**
 * B2's switch (.sw): 48×32, a near-black track when on, and a white knob that slides 16dp and
 * shows a check. In dark themes the knob turns black when on. Pass a null [onCheckedChange] when a
 * whole row is the toggle.
 */
@Composable
fun InkSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val held by src.collectIsPressedAsState()
    val t = animateFloatAsState(if (checked) 1f else 0f, tween(InkMotion.BASE, easing = InkMotion.Glide), label = "switch")
    val squeeze = animateFloatAsState(if (held) 0.88f else 1f, InkMotion.press(), label = "knob")
    val knob = if (ink.isDark && checked) Color.Black else Color.White
    val mark = if (ink.isDark && checked) Color.White else Color(0xFF222222)
    Box(
        modifier
            .size(48.dp, 32.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(ink.track, cornerRadius = r)
                drawRoundRect(ink.solid.copy(alpha = ink.solid.alpha * t.value), cornerRadius = r)
            }
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, src, null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(28.dp)
                .graphicsLayer {
                    translationX = 16.dp.toPx() * t.value
                    scaleX = squeeze.value
                    scaleY = squeeze.value
                    shadowElevation = 2.dp.toPx()
                    shape = CircleShape
                    clip = true
                }
                .background(knob),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ph.check, null, tint = mark, modifier = Modifier.size(14.dp).graphicsLayer { alpha = t.value })
        }
    }
}

/**
 * A single-choice chip (.chip): 36dp pill; the chosen one gets a 2dp near-black ring and goes bold,
 * never colour alone. Wrap a row of chips in `Modifier.selectableGroup()` so they read as one group.
 *
 * [role] is what the chip is announced as. Only a [Role.RadioButton] chip reports [selected] to
 * accessibility; any other role (a one-shot action is a [Role.Button]) just wears the ring and
 * weight when [selected] is true, as a stronger look.
 */
@Composable
fun InkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .height(36.dp)
            .pressScale(src, 0.96f)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ink.solid else ink.line, shape)
            .clickable(src, LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
            .then(if (role == Role.RadioButton) Modifier.semantics { this.selected = selected } else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = ink.text, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = if (selected) ChipLabelSelected else ChipLabel,
            color = ink.text,
            maxLines = 1,
        )
    }
}

/**
 * The sizes of an [InkStepper]. Every preset keeps B2's look: ringed round buttons that darken while held and
 * shrink to .9, and a bold tabular value. They differ only in size.
 */
@Immutable
class InkStepperMetrics(
    val button: Dp,
    val icon: Dp,
    val gap: Dp,
    val valueMinWidth: Dp,
    val valueStyle: TextStyle,
) {
    companion object {
        /** .stepper (B 429–435): 32 dp buttons with 15 dp icons, gaps of 10, a 62 dp value at 15/20 bold. 146 dp in all. */
        val Regular = InkStepperMetrics(32.dp, 15.dp, 10.dp, 62.dp, StepperValue)

        /** The table style card and sheet (TI 166–167): gaps of 6, a 56 dp value at 14 bold. 132 dp in all. */
        val Compact = InkStepperMetrics(32.dp, 15.dp, 6.dp, 56.dp, StepperValueSmall)

        /** .tx-mst (TX 176–178), the text card's margin cells: 28 dp buttons with 13 dp icons, gaps of 2, a 25 dp value at 14 bold. 85 dp in all. */
        val Mini = InkStepperMetrics(28.dp, 13.dp, 2.dp, 25.dp, StepperValueSmall)
    }
}

/**
 * −  value  + (.stepper): by default 32 + 10 + 62 + 10 + 32 = 146dp, with a bold tabular value; the ring
 * darkens while held. [metrics] picks a smaller preset. Compose widens each button's hit area to the 48dp minimum
 * without changing layout. To pair it with a label over a slider, use [InkStepperRow].
 */
@Composable
fun InkStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    canMinus: Boolean = true,
    canPlus: Boolean = true,
    metrics: InkStepperMetrics = InkStepperMetrics.Regular,
) {
    val ink = LocalInk.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        StepButton(Ph.minus, stringResource(R.string.kit_decrease), canMinus, metrics, onMinus)
        Text(
            value,
            style = metrics.valueStyle,
            color = ink.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = metrics.valueMinWidth)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(Ph.plus, stringResource(R.string.kit_increase), canPlus, metrics, onPlus)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, metrics: InkStepperMetrics, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val held = src.collectIsPressedAsState()
    Box(
        Modifier
            .size(metrics.button)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pressScale(src, 0.9f)
            .alpha(if (enabled) 1f else 0.3f)
            .drawBehind {
                val w = 1.dp.toPx()
                drawCircle(if (held.value) ink.text else ink.line3, radius = (size.minDimension - w) / 2f, style = Stroke(w))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = ink.text, modifier = Modifier.size(metrics.icon))
    }
}
