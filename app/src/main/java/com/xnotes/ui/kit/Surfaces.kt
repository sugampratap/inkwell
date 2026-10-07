package com.xnotes.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf

/** B2's elevation levels: each is one soft shadow plus a hairline ring, never more (perf rule). */
enum class InkElevation(val shadow: Dp) {
    /** Pen-box rail, cards (--sh-soft). */
    SOFT(3.dp),

    /**
     * Popovers and menus in a popup window (--sh-pop, cut to fit). Every `DropdownMenu`, `Popup` or
     * `Popup`-hosted card must use this, not [POP]: Compose's `PopupLayout` reserves surface insets
     * for about 8dp of elevation only, so a deeper shadow is clipped at the popup window's edge.
     */
    MENU(8.dp),

    /** The floating toolbar (--sh-float). */
    FLOAT(10.dp),

    /**
     * Popovers and menus drawn in the main window (--sh-pop). Not inside a `Popup`: its 14dp
     * shadow clips at the popup window's edge there, so use [MENU].
     */
    POP(14.dp),

    /** Modal sheets. */
    SHEET(28.dp),
}

/** A raised B2 surface: the one shadow, the fill (default --raised) and a --line2 hairline, clipped to [shape]. */
@Composable
fun Modifier.inkSurface(shape: Shape, elevation: InkElevation = InkElevation.POP, color: Color = LocalInk.current.raised): Modifier {
    val ink = LocalInk.current
    return this
        .shadow(elevation.shadow, shape, clip = false, ambientColor = ink.shadow, spotColor = ink.shadow)
        .clip(shape)
        .background(color)
        .border(1.dp, ink.line2, shape)
}

/**
 * The neutral 40dp badge an Insert or "Add here" tile's icon sits on (.ibx): Regular icon, ink colour.
 *
 * [pressed] is read while drawing, so a press only redraws the fill. Pass a lambda over the state, not
 * its value: `val pressed by interactionSource.collectIsPressedAsState()` then `IconBadge(icon, pressed = { pressed })`.
 */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, pressed: () -> Boolean = { false }) {
    val r = cornerOf(12.dp)
    val ink = LocalInk.current
    Box(
        modifier.size(40.dp).drawBehind {
            drawRoundRect(if (pressed()) ink.iconBadgePressed else ink.iconBadge, cornerRadius = CornerRadius(r.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
    }
}

/**
 * A menu row (.m-row): 44dp, 22dp icon, 15sp.
 *
 * [checked] null makes an action row (a button). Non-null makes a choice row (a radio item): it keeps an
 * 18dp trailing slot for the tick whether or not it is chosen, so chosen and unchosen rows line up, and
 * only the chosen one goes bold and shows the tick. With [toggle] a checked row is a switch rather than a
 * choice (Zoom lock), announced on and off alike; it looks the same.
 */
@Composable
fun InkMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    toggle: Boolean = false,
) {
    val ink = LocalInk.current
    val fg = when {
        !enabled -> ink.text3
        danger -> ink.danger
        else -> ink.text
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                when {
                    checked != null && toggle -> Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
                    checked != null -> Modifier.selectable(selected = checked, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                    else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                },
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = InkType.row.copy(fontWeight = if (checked == true) FontWeight.Bold else FontWeight.Medium),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        if (checked != null) {
            Box(Modifier.size(18.dp)) {
                if (checked) Icon(Ph.check, null, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A menu's section header (.m-h). */
@Composable
fun InkMenuHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = InkType.label,
        color = LocalInk.current.text,
        modifier = modifier.semantics { heading() }.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** A menu's divider (.m-hr). */
@Composable
fun InkMenuDivider(modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(LocalInk.current.line2))
}

/** Header over a grouped list (.group-h); [first] drops the 22dp top margin (.group-h:first-child). */
@Composable
fun InkGroupHeader(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text,
        style = InkType.label,
        color = LocalInk.current.text,
        modifier = modifier.semantics { heading() }.padding(start = 4.dp, end = 4.dp, top = if (first) 0.dp else 22.dp, bottom = 8.dp),
    )
}

/** Footnote under a grouped list (.group-f). */
@Composable
fun InkGroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = InkType.hint,
        color = LocalInk.current.text2,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

/** An inset card of hairline-separated rows (.group): Settings, Share options. */
@Composable
fun InkGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.medium
    Column(modifier.fillMaxWidth().clip(shape).background(ink.raised).border(1.dp, ink.line2, shape), content = content)
}

/**
 * One row of an [InkGroup] (.gr): at least 60dp; optional icon; title over subtitle; [trailing] for
 * a value, switch or chevron. A hairline inset by 18dp separates it from the row above unless [first].
 */
@Composable
fun InkGroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    first: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = LocalInk.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind {
                if (!first) {
                    val inset = 18.dp.toPx()
                    drawRect(ink.line2, Offset(inset, 0f), Size(size.width - inset, 1.dp.toPx()))
                }
            }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = InkType.rowStrong, color = ink.text)
            if (subtitle != null) Text(subtitle, style = InkType.meta, color = ink.text2)
        }
        trailing?.invoke(this)
    }
}

/** The value and chevron a navigating row ends with (.gv). */
@Composable
fun InkRowValue(value: String?, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!value.isNullOrEmpty()) {
            Text(value, style = InkType.body, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
        Icon(Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(16.dp))
    }
}

/**
 * A text field (.field): 48dp, r12, a 1dp --line ring that becomes a 2dp near-black ring while focused.
 *
 * A field with no visible label has no accessible name: the caller gives it one with
 * `modifier.semantics { contentDescription = "…" }`.
 */
@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(48.dp),
        singleLine = true,
        textStyle = InkType.row.copy(color = ink.text),
        cursorBrush = SolidColor(ink.solid),
        interactionSource = src,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .background(ink.raised, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) ink.solid else ink.line, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, null, tint = ink.text2, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = InkType.row, color = ink.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        },
    )
}
