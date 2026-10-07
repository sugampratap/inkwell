package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkGroupFooter
import com.xnotes.ui.kit.InkGroupHeader
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkSearchField
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.delay

/*
 * The parts the Settings screen is built from (r2_settings): a caption over an inset card of rows,
 * each row its title, one plain line, and its control at the end. Hairlines between the rows are
 * drawn from where the rows land, so a row left out never leaves a stray divider behind.
 */

/** A row to scroll to and flash, set when a search result or a deep link opens it; [nonce] re-fires the same row. */
@Immutable
internal data class SettingsHighlight(val id: SettingId, val nonce: Int)

internal val LocalSettingsHighlight = compositionLocalOf<SettingsHighlight?> { null }

/** Whether the settings page is too narrow for a segmented picker beside its title. */
internal val LocalSettingsNarrow = compositionLocalOf { false }

/** The widest a settings page's cards grow; past this the rows read as a line of text across a desk. */
internal val SETTINGS_MAX_WIDTH = 760.dp

/** Where a row's top hairline starts (.gr+.gr::before): 18dp in, or 38dp under an indented row. */
@Immutable
private data class Seam(val inset: Dp)

private val RowValue = InkType.body
private val RowTitle = InkType.rowStrong

/**
 * Scrolls this row into view and flashes it (--press, up for 180 ms, held, eased out: about 1.5 s)
 * when a search result or a deep link opens one of [ids]. The flash is read only while drawing.
 */
@Composable
internal fun Modifier.settingsFlash(ids: Set<SettingId>): Modifier {
    val ink = LocalInk.current
    val highlight = LocalSettingsHighlight.current
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    if (highlight != null && highlight.id in ids) {
        LaunchedEffect(highlight.nonce) {
            delay(60) // a frame for the page to lay out before its scroll brings the row in
            requester.bringIntoView()
            flash.animateTo(1f, tween(180, easing = InkMotion.Standard))
            delay(800)
            flash.animateTo(0f, tween(520, easing = InkMotion.Standard))
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .drawBehind {
            val a = flash.value
            if (a > 0f) drawRect(ink.press.copy(alpha = ink.press.alpha * a))
        }
}

/** A caption (.group-h), a card of rows (.group) and an optional footnote (.group-f). */
@Composable
internal fun SettingsSection(
    caption: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.medium
    Column(modifier.widthIn(max = SETTINGS_MAX_WIDTH).fillMaxWidth()) {
        if (caption != null) InkGroupHeader(caption)
        SettingsRows(Modifier.fillMaxWidth().clip(shape).background(ink.raised).border(1.dp, ink.line2, shape), content)
        if (footer != null) InkGroupFooter(footer)
    }
}

/** Rows stacked full width, with a hairline above each but the first, starting at the row's own [Seam]. */
@Composable
private fun SettingsRows(modifier: Modifier, content: @Composable () -> Unit) {
    val ink = LocalInk.current
    // (top, inset) of every seam, written as the rows are placed and read only while drawing.
    var seams by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            val h = 1.dp.toPx()
            for ((y, inset) in seams) drawRect(ink.line2, Offset(inset, y), Size(size.width - inset, h))
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val child = Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(child) }
        layout(width, placeables.sumOf { it.height }) {
            var y = 0
            val next = ArrayList<Pair<Float, Float>>(placeables.size)
            placeables.forEachIndexed { i, p ->
                if (y > 0 && p.height > 0) next += y.toFloat() to ((measurables[i].layoutId as? Seam)?.inset ?: 18.dp).toPx()
                p.placeRelative(0, y)
                y += p.height
            }
            if (next != seams) seams = next
        }
    }
}

/**
 * One row of a settings card, its title and line from [id] (or [description] in its place). It
 * scrolls into view and flashes when a search opens it. [trailing] sits at the row's end; [below]
 * runs under the text, full width, for a control too wide to share the line (a slider, a chip row).
 * [indent] moves a dependent or folded row in (and its hairline to 38dp); [animateIn] lets a row that
 * has just appeared (a fold opening) rise 6dp and fade in.
 */
@Composable
internal fun SettingRow(
    id: SettingId,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    icon: ImageVector? = null,
    indent: Dp = 0.dp,
    description: String? = null,
    animateIn: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ids = remember(id) { setOf(id) }
    SettingRowBase(
        title = stringResource(id.title),
        description = description ?: stringResource(id.description),
        modifier = Modifier.settingsFlash(ids),
        enabled = enabled,
        icon = icon,
        indent = indent,
        animateIn = animateIn,
        onClick = onClick,
        below = below,
        trailing = trailing,
    )
}

/** [SettingRow] without the catalogue: for rows made from data (one per imported font) or not searchable (the fold). */
@Composable
internal fun SettingRowBase(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleStyle: TextStyle = TextStyle.Default,
    icon: ImageVector? = null,
    indent: Dp = 0.dp,
    animateIn: Boolean = false,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = LocalInk.current
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    if (animateIn) LaunchedEffect(Unit) { enter.animateTo(1f, tween(InkMotion.BASE, easing = InkMotion.Glide)) }
    Column(
        modifier
            .layoutId(Seam(if (indent > 0.dp) 38.dp else 18.dp))
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (enter.value - 1f) * 6.dp.toPx()
            }
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(start = 18.dp + indent, end = 16.dp, top = 10.dp, bottom = if (below != null) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Icon(icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = RowTitle.merge(titleStyle), color = ink.text)
                if (!description.isNullOrEmpty()) Text(description, style = InkType.meta, color = ink.text2)
            }
            trailing?.invoke()
        }
        if (below != null) Column(Modifier.fillMaxWidth(), content = below)
    }
}

/**
 * A row whose choices are pictures (.gr.st-side): its title and line in a 170dp column, the
 * [content] (picture cards) filling the rest. Theme and Corners.
 */
@Composable
internal fun SettingsSideRow(id: SettingId, content: @Composable () -> Unit) {
    val ink = LocalInk.current
    val ids = remember(id) { setOf(id) }
    val rowModifier = Modifier
        .layoutId(Seam(18.dp))
        .settingsFlash(ids)
        .fillMaxWidth()
        .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    val words: @Composable () -> Unit = {
        Text(stringResource(id.title), style = RowTitle, color = ink.text)
        Text(stringResource(id.description), style = InkType.meta, color = ink.text2)
    }
    if (LocalSettingsNarrow.current) {
        // Narrow: the title and description over the content, which gets the full width.
        Column(rowModifier) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { words() }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) { content() }
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { words() }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** The app's switch: B2's [InkSwitch]. */
@Composable
internal fun SettingsSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    InkSwitch(checked, onChange, enabled = enabled)
}

/** A row the whole of which flips its switch. */
@Composable
internal fun SwitchRow(
    id: SettingId,
    checked: Boolean,
    enabled: Boolean = true,
    indent: Dp = 0.dp,
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(id, enabled = enabled, onClick = { onChange(!checked) }, indent = indent, description = description) {
        SettingsSwitch(checked, enabled, onChange)
    }
}

/** The value a row is set to, and what a tap does with it (.gv): ↕ for a menu, › for a sheet, ↗ for the browser. */
@Composable
private fun ValueMark(value: String?, icon: ImageVector) {
    val ink = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!value.isNullOrEmpty()) {
            Text(value, style = RowValue, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
        Icon(icon, null, tint = ink.text3, modifier = Modifier.size(16.dp))
    }
}

/**
 * A row showing its current value with ↕; a tap opens a menu of the choices under the value (the
 * current one bold with a tick, [icons] beside them when given).
 */
@Composable
internal fun <T> ChoiceRow(
    id: SettingId,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    icons: ((T) -> ImageVector?)? = null,
    indent: Dp = 0.dp,
    animateIn: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    SettingRow(id, enabled = enabled, onClick = { open = true }, indent = indent, animateIn = animateIn) {
        Box {
            ValueMark(current, Ph.caretUpDown)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 232.dp)) {
                options.forEach { (value, label) ->
                    val on = value == selected
                    InkMenuRow(label, { open = false; if (!on) onSelect(value) }, icon = icons?.invoke(value), checked = on)
                }
            }
        }
    }
}

/** A row that leads somewhere else (a sheet, a picker, the browser), showing [value] if it has one. */
@Composable
internal fun NavRow(
    id: SettingId,
    value: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    external: Boolean = false,
    description: String? = null,
    onClick: () -> Unit,
) {
    SettingRow(id, enabled = enabled, onClick = onClick, icon = icon, description = description) {
        ValueMark(value, if (external) Ph.arrowSquareOut else Ph.caretRight)
    }
}

/**
 * A row with a box segmented control (.svseg.st-seg) beside its title, or under it when the page is
 * narrow or [stacked]. [segment] is kept for old callers; segments size to their labels (70dp at least).
 */
@Composable
internal fun <T> SegmentRow(
    id: SettingId,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    enabled: Boolean = true,
    stacked: Boolean = false,
    @Suppress("UNUSED_PARAMETER") segment: Dp = 0.dp,
    onSelect: (T) -> Unit,
) {
    val below = stacked || LocalSettingsNarrow.current
    val pick: (T) -> Unit = { if (enabled && it != selected) onSelect(it) }
    if (below) {
        SettingRow(id, enabled = enabled, below = { InkBoxSegmented(options, selected, label, pick, Modifier.fillMaxWidth(), fill = true) })
    } else {
        SettingRow(id, enabled = enabled) { InkBoxSegmented(options, selected, label, pick) }
    }
}

/** A row with its value at the end and a slider under it (.st-stack.st-slr); [ticks] label the slider's ends and middle. */
@Composable
internal fun SliderRow(
    id: SettingId,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    ticks: List<String>? = null,
    onChange: (Float) -> Unit,
) {
    val ink = LocalInk.current
    SettingRow(
        id,
        enabled = enabled,
        below = {
            InkSlider(value, range, enabled = enabled, onChange = onChange)
            if (ticks != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ticks.forEach { Text(it, style = InkType.small, color = ink.text3) }
                }
            }
        },
    ) {
        Text(valueText, style = InkType.body.copy(fontWeight = FontWeight.Bold).tnum(), color = ink.text)
    }
}

/** A row's small outlined button (.st-pill): Import, Reset, Remove; [danger] is red text. */
@Composable
internal fun SettingsPillButton(label: String, danger: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    InkSecondaryButton(label, onClick, Modifier.height(36.dp), icon = icon, danger = danger)
}

/** A quiet tag at a row's end (.st-tagc): "Four-finger tap". */
@Composable
internal fun SettingsTag(text: String) {
    val ink = LocalInk.current
    Box(Modifier.height(24.dp).background(ink.surface, CircleShape).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, style = InkType.small.copy(fontWeight = FontWeight.Bold), color = ink.text2, maxLines = 1)
    }
}

/** The search field over the category list (.field.st-srch): the theme's small corners, a clear button while there is text. */
@Composable
internal fun SettingsSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val shown = if (field.text == query) field else TextFieldValue(query, TextRange(query.length))
    InkSearchField(
        value = shown,
        onValueChange = { field = it; onQuery(it.text) },
        placeholder = stringResource(R.string.settings_search),
        modifier = modifier,
        pill = false,
    )
}

/**
 * One entry of the category list or a search result (.st-cat): a 22dp icon (its Fill version,
 * [selectedIcon], when chosen), the title (bold when chosen) and a quiet line, on the selected-row
 * pill when [selected]. Shrinks to .99 under the finger. [showChevron] in the narrow, one-pane layout.
 */
@Composable
internal fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    showChevron: Boolean = false,
    selectedIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(src, 0.99f)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) ink.sel else Color.Transparent)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(if (selected && selectedIcon != null) selectedIcon else icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = InkType.row.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = InkType.caption, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showChevron) Icon(Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(16.dp))
    }
}
