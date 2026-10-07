package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.util.NameTemplate
import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** One-tap filename templates offered beside the free-text field. */
internal val NAME_TEMPLATE_PRESETS = listOf(NameTemplate.DEFAULT, "note_YYYY-MM-DD", "note_YYYY-MM-DD_HH-mm")

/** The three toolbar-look settings live in one card, so a search for any of them opens and flashes it. */
private val TOOLBAR_LOOK_IDS = setOf(SettingId.TOOLBAR_STYLE, SettingId.TOOLBAR_POSITION, SettingId.TOOLBAR_SIZE)

/** General (r2_settings Frame 1): start-up, new note names, and the toolbar (its look card, colours, Customise toolbar and Selection bar). */
@Composable
internal fun GeneralSettings(m: SettingsModel) {
    val editor = m.editor
    val prefs = m.prefs
    var naming by remember { mutableStateOf(false) }
    var customising by rememberSaveable { mutableStateOf(false) }
    var choosingSelectionBar by rememberSaveable { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_sec_startup)) {
        SwitchRow(SettingId.START_FULLSCREEN, editor.fullscreen) { editor.setFullscreenPref(it); m.resync() }
        ChoiceRow(
            SettingId.HOME_OPENS_TO,
            listOf(
                "top" to stringResource(R.string.top_folder),
                "last" to stringResource(R.string.last_folder),
                "shelves" to stringResource(R.string.recent_and_pinned),
            ),
            prefs.homeOpensTo,
        ) { m.updateHome(prefs.copy(homeOpensTo = it)) }
    }
    SettingsSection(stringResource(R.string.settings_sec_new_notes)) {
        NavRow(SettingId.FILENAME_TEMPLATE, prefs.newNoteNameTemplate) { naming = true }
    }
    SettingsSection(stringResource(R.string.settings_sec_toolbar_look)) {
        ToolbarLookCard(m)
        SliderRow(
            SettingId.TOOLBAR_COLOURS,
            editor.toolbarColorCount.toString(),
            editor.toolbarColorCount.toFloat(),
            1f..InkPalette.MAX_SWATCHES.toFloat(),
        ) { v -> v.roundToInt().let { if (it != editor.toolbarColorCount) editor.applyToolbarColorCount(it) } }
        NavRow(SettingId.CUSTOMISE_TOOLBAR, description = stringResource(R.string.settings_customise_toolbar_sub)) { customising = true }
        NavRow(SettingId.SELECTION_BAR, description = stringResource(R.string.settings_selection_bar_sub)) { choosingSelectionBar = true }
    }
    if (naming) FilenameTemplateSheet(m) { naming = false }
    if (customising) ToolbarCustomizerSheet(editor) { customising = false }
    if (choosingSelectionBar) SelectionBarSheet(editor) { choosingSelectionBar = false }
}

/**
 * The toolbar card (.st-tlook): a small live picture of a page with the bar where and how it will
 * sit, beside Style, Position and Button size. One setting for both bars, as the caption says.
 */
@Composable
private fun ToolbarLookCard(m: SettingsModel) {
    val ink = LocalInk.current
    val prefs = m.prefs
    val look = prefs.toolbarLook
    fun set(next: ToolbarLook) = m.updateHome(prefs.copy(toolbarLook = next))
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LookRow(stringResource(R.string.settings_look_style)) {
                InkBoxSegmented(listOf(false, true), look.floating, label = { stringResource(if (it) R.string.toolbar_floating else R.string.toolbar_docked) }, onSelect = { set(look.copy(floating = it)) }, minSegment = 0.dp)
            }
            LookRow(stringResource(R.string.settings_look_position)) {
                InkBoxSegmented(
                    listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM, ToolbarPosition.LEFT, ToolbarPosition.RIGHT), look.position,
                    label = {
                        stringResource(
                            when (it) {
                                ToolbarPosition.TOP -> R.string.edge_top
                                ToolbarPosition.BOTTOM -> R.string.edge_bottom
                                ToolbarPosition.LEFT -> R.string.edge_left
                                ToolbarPosition.RIGHT -> R.string.edge_right
                            },
                        )
                    },
                    onSelect = { set(look.copy(position = it)) }, minSegment = 0.dp,
                )
            }
            LookRow(stringResource(R.string.settings_look_size)) {
                InkBoxSegmented(
                    listOf(ToolbarSize.COMPACT, ToolbarSize.REGULAR, ToolbarSize.COMFORTABLE), look.size,
                    label = {
                        stringResource(
                            when (it) {
                                ToolbarSize.COMPACT -> R.string.toolbar_size_compact
                                ToolbarSize.REGULAR -> R.string.toolbar_size_regular
                                ToolbarSize.COMFORTABLE -> R.string.toolbar_size_comfortable
                            },
                        )
                    },
                    onSelect = { set(look.copy(size = it)) }, minSegment = 0.dp,
                )
            }
            Text(stringResource(R.string.settings_look_caption), style = InkType.caption, color = ink.text2)
        }
    }
    val card = Modifier.settingsFlash(TOOLBAR_LOOK_IDS).fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    // A narrow page has no room beside the picture, so the controls go under it.
    if (LocalSettingsNarrow.current) {
        Column(card, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ToolbarLookPicture(look, m.editor.toolbarColorCount, m.editor.toolbarColors)
            controls(Modifier.fillMaxWidth())
        }
    } else {
        Row(card, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ToolbarLookPicture(look, m.editor.toolbarColorCount, m.editor.toolbarColors)
            controls(Modifier.weight(1f))
        }
    }
}

/**
 * A label and its control: side by side when the whole label fits beside the control, else the
 * label above it, so a wide control (four positions) never squeezes the label to a letter a line.
 */
@Composable
private fun LookRow(label: String, control: @Composable () -> Unit) {
    val ink = LocalInk.current
    Layout(
        content = {
            Text(label, style = InkType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = ink.text)
            Box { control() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val (labelM, controlM) = measurables
        val gap = 12.dp.roundToPx()
        val ctrl = controlM.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val beside = labelM.maxIntrinsicWidth(Constraints.Infinity) + gap + ctrl.width <= constraints.maxWidth
        if (beside) {
            val lab = labelM.measure(Constraints(maxWidth = constraints.maxWidth - gap - ctrl.width))
            val h = maxOf(lab.height, ctrl.height)
            layout(constraints.maxWidth, h) {
                lab.place(0, (h - lab.height) / 2)
                ctrl.place(constraints.maxWidth - ctrl.width, (h - ctrl.height) / 2)
            }
        } else {
            val lab = labelM.measure(Constraints(maxWidth = constraints.maxWidth))
            val under = 8.dp.roundToPx()
            layout(constraints.maxWidth, lab.height + under + ctrl.height) {
                lab.place(0, 0)
                ctrl.place(0, lab.height + under)
            }
        }
    }
}

/**
 * The toolbar look's picture (.st-tbp): 208 × 128 on the canvas colour, a ruled page, and the bar —
 * docked along the chosen edge or floating 8dp in from it, its buttons the chosen size, the first
 * [count] toolbar colours at its end (as many as fit when it stands vertically).
 */
@Composable
private fun ToolbarLookPicture(look: ToolbarLook, count: Int, colours: List<Rgba>) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.small
    Canvas(Modifier.size(208.dp, 128.dp).clip(shape).background(ink.canvas).border(1.dp, ink.line, shape)) {
        // The page: 92 × 150 from 36dp down, ruled every 10dp below its head, with an ink title.
        val pw = 92.dp.toPx()
        val px = (size.width - pw) / 2
        val py = 36.dp.toPx()
        drawRect(ToPaper, Offset(px, py), Size(pw, 150.dp.toPx()))
        var ry = py + 33.dp.toPx()
        while (ry < size.height) {
            drawRect(Color(0xFFE8E3D7), Offset(px, ry), Size(pw, 1.dp.toPx()))
            ry += 10.dp.toPx()
        }
        drawRoundRect(Color(0xFF1F2A44).copy(alpha = 0.7f), Offset(px + 9.dp.toPx(), py + 10.dp.toPx()), Size(46.dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawLookBar(look, count, colours, ink)
    }
}

private enum class BarPart { SQUARE, SEP, ACTIVE, COLOUR }

private fun DrawScope.drawLookBar(look: ToolbarLook, count: Int, colours: List<Rgba>, ink: com.xnotes.ui.theme.InkTokens) {
    val s = when (look.size) { ToolbarSize.COMPACT -> 7.dp; ToolbarSize.REGULAR -> 9.dp; ToolbarSize.COMFORTABLE -> 11.dp }.toPx()
    val gap = (s * 0.45f).roundToInt().toFloat()
    val vertical = look.position.vertical
    val docked = !look.floating
    val fixed = listOf(BarPart.SQUARE, BarPart.SQUARE, BarPart.SEP, BarPart.ACTIVE) + List(4) { BarPart.SQUARE } + BarPart.SEP
    // Standing up, only as many colours as fit the 112dp the picture leaves (the mockup's rule).
    val room = if (vertical) ((112.dp.toPx() - (s + gap) * 7) / (s + gap)).toInt().coerceAtLeast(1) else count
    val parts = fixed + List(minOf(count, room, colours.size)) { BarPart.COLOUR }
    fun along(p: BarPart) = if (p == BarPart.SEP) 3.dp.toPx() else s
    val pad = 7.dp.toPx()
    val length = parts.sumOf { along(it).toDouble() }.toFloat() + gap * (parts.size - 1) + pad * 2
    val thick = s + 12.dp.toPx()
    val inset = 8.dp.toPx()
    val (x0, y0, w, h) = when {
        !vertical && docked -> listOf(0f, if (look.position == ToolbarPosition.TOP) 0f else size.height - thick, size.width, thick)
        !vertical -> listOf((size.width - length) / 2, if (look.position == ToolbarPosition.TOP) inset else size.height - inset - thick, length, thick)
        docked -> listOf(if (look.position == ToolbarPosition.LEFT) 0f else size.width - thick, 0f, thick, size.height)
        else -> listOf(if (look.position == ToolbarPosition.LEFT) inset else size.width - inset - thick, (size.height - length) / 2, thick, length)
    }
    val corner = if (docked) CornerRadius.Zero else CornerRadius(thick / 2)
    drawRoundRect(ink.raised, Offset(x0, y0), Size(w, h), corner)
    drawRoundRect(if (docked) ink.line else ink.line2, Offset(x0, y0), Size(w, h), corner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    // Items run from the bar's centre line, centred along it when docked.
    var at = if (docked) ((if (vertical) h else w) - (length - pad * 2)) / 2 else pad
    var colour = 0
    for (p in parts) {
        val len = along(p)
        val cx = if (vertical) x0 + w / 2 else x0 + at + len / 2
        val cy = if (vertical) y0 + at + len / 2 else y0 + h / 2
        when (p) {
            BarPart.SQUARE -> drawRoundRect(ink.text.copy(alpha = 0.42f), Offset(cx - s / 2, cy - s / 2), Size(s, s), CornerRadius(3.dp.toPx()))
            BarPart.SEP -> if (vertical) drawRect(ink.text.copy(alpha = 0.3f), Offset(cx - (s + 2.dp.toPx()) / 2, cy), Size(s + 2.dp.toPx(), 1.dp.toPx()))
            else drawRect(ink.text.copy(alpha = 0.3f), Offset(cx, cy - (s + 2.dp.toPx()) / 2), Size(1.dp.toPx(), s + 2.dp.toPx()))
            BarPart.ACTIVE -> drawCircle(ink.solid, s / 2, Offset(cx, cy))
            BarPart.COLOUR -> {
                drawCircle(colours[colour++].toComposeColor(), s / 2, Offset(cx, cy))
                drawCircle(swatchRing(ink.isDark), s / 2, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
        }
        at += len + gap
    }
}

/**
 * Name new notes (r2_settings): the template field, what the codes mean, three one-tap presets, and
 * the name the next note would get. Changes apply as they are typed; Done closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilenameTemplateSheet(m: SettingsModel, onDone: () -> Unit) {
    val ink = LocalInk.current
    val prefs = m.prefs
    var field by remember { mutableStateOf(TextFieldValue(prefs.newNoteNameTemplate, TextRange(prefs.newNoteNameTemplate.length))) }
    val shown = if (field.text == prefs.newNoteNameTemplate) field else TextFieldValue(prefs.newNoteNameTemplate, TextRange(prefs.newNoteNameTemplate.length))
    InkSheet(
        title = stringResource(R.string.settings_filename_dialog),
        onDismiss = onDone,
        width = 560.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.done), onDone)
        },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_filename_help_b2), style = InkType.body.copy(lineHeight = 21.sp), color = ink.text2)
            InkTextField(
                shown,
                { field = it; m.update(m.prefs.copy(newNoteNameTemplate = it.text)) },
                placeholder = NameTemplate.DEFAULT,
                keyboardActions = KeyboardActions(onDone = { onDone() }),
            )
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NAME_TEMPLATE_PRESETS.forEach { t ->
                    InkChip(t, prefs.newNoteNameTemplate == t, { m.update(prefs.copy(newNoteNameTemplate = t)) })
                }
            }
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.settings_next_note_named))
                    withStyle(SpanStyle(color = ink.text, fontWeight = FontWeight.Bold)) { append("${m.editor.newNoteStem(emptySet())}.xnote") }
                },
                style = InkType.meta, color = ink.text2,
            )
        }
    }
}
