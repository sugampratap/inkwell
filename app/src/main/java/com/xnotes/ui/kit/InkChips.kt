package com.xnotes.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/**
 * The chip's frame: a pill, a ring, a fill, a press shrink. [interaction] (the click or toggle) goes
 * inside the clip, so the press tint stays inside the pill.
 */
@Composable
private fun ChipFrame(
    modifier: Modifier,
    interaction: Modifier,
    height: Dp,
    ring: Dp,
    ringColor: Color,
    fill: Color,
    src: MutableInteractionSource,
    hPad: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(height)
            .pressScale(src, 0.96f)
            .clip(CircleShape)
            .then(interaction)
            .background(fill)
            .border(ring, ringColor, CircleShape)
            .padding(horizontal = hPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A chip that is on or off (Match case, Whole words): announced as a checkbox; on = 2dp near-black ring and extra-bold. */
@Composable
fun InkToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, height: Dp = 32.dp) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    ChipFrame(
        modifier, Modifier.toggleable(checked, src, LocalIndication.current, role = Role.Checkbox, onValueChange = onCheckedChange),
        height, if (checked) 2.dp else 1.dp, if (checked) ink.solid else ink.line, Color.Transparent, src, hPad = 12.dp,
    ) {
        Text(label, style = InkType.chip.copy(fontSize = 13.sp, fontWeight = if (checked) FontWeight.ExtraBold else FontWeight.SemiBold), color = ink.text, maxLines = 1)
    }
}

/**
 * A chip that adds to or takes from a set (layouts in the switcher, sidebar sections): an optional
 * leading [icon], the label, and a trailing check when [on] or plus when not. On also takes the 2dp
 * ring and extra-bold. Announced as a checkbox.
 */
@Composable
fun InkMarkChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    ChipFrame(
        modifier, Modifier.toggleable(on, src, LocalIndication.current, role = Role.Checkbox, onValueChange = { onClick() }),
        36.dp, if (on) 2.dp else 1.dp, if (on) ink.solid else ink.line, Color.Transparent, src,
    ) {
        if (icon != null) Icon(icon, null, tint = ink.text, modifier = Modifier.size(16.dp))
        Text(label, style = InkType.chip.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold), color = ink.text, maxLines = 1)
        Icon(if (on) Ph.check else Ph.plus, null, tint = ink.text, modifier = Modifier.size(16.dp))
    }
}

/** An action chip on a --line3 ring (.chip.st-add): "+ Name a colour". */
@Composable
fun InkAddChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Ph.plus) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    ChipFrame(modifier, Modifier.clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick), 36.dp, 1.dp, ink.line3, Color.Transparent, src) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(16.dp))
        Text(label, style = InkType.chip, color = ink.text, maxLines = 1)
    }
}

/** A small action chip inside a menu row (.ps-mch button: Image, PDF): 32dp, 13sp semibold, a hairline ring. */
@Composable
fun InkSmallChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    ChipFrame(modifier, Modifier.clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick), 32.dp, 1.dp, ink.line, Color.Transparent, src, hPad = 12.dp) {
        Text(label, style = InkType.chip.copy(fontSize = 13.sp), color = ink.text, maxLines = 1)
    }
}

/**
 * Page setup's Default chip (.pn-defc): a quiet filled pill while the value already is the default
 * (nothing to clear), a plain chip that clears it otherwise. Announced as a button either way.
 */
@Composable
fun InkDefaultChip(label: String, isDefault: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    ChipFrame(
        modifier, Modifier.clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick),
        32.dp, 1.dp, if (isDefault) ink.line2 else ink.line, if (isDefault) ink.sel else Color.Transparent, src, hPad = 12.dp,
    ) {
        Text(label, style = InkType.chip.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (isDefault) ink.text2 else ink.text, maxLines = 1)
    }
}
