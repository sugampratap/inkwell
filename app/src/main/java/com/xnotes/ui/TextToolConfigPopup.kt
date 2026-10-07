package com.xnotes.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.pal.FontFace
import com.xnotes.core.text.FlowDefaults
import com.xnotes.core.text.FlowMargins
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.InkStepGrid
import com.xnotes.ui.kit.InkStepper
import com.xnotes.ui.kit.InkStepperMetrics
import com.xnotes.ui.kit.InkStepperRow
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import kotlin.math.roundToInt

private val LabelType = InkType.buttonSmall // .plab, 14/600
private val FieldType = InkType.row // .tx-dd, 15/500
private val CellLabelType = InkType.chip // .tx-mc span, 13.5/600
private val SwitchTitleType = InkType.buttonSmall.copy(lineHeight = 19.sp) // .tx-swt b, 14/19 600
private val SwitchSubType = InkType.hint.copy(lineHeight = 16.sp) // .tx-swt span, 12.5/16

private val SIZE_RANGE = 6f..96f
private val MARGIN_RANGE = FlowMargins.MIN_MM.toFloat()..FlowMargins.MAX_MM.toFloat()

/** The Font and Code font lists: end-aligned to the field, 6 dp under it (TX 991-1001). */
private val FieldMenuSpec = PopoverSpecDp(lead = 0.dp, gap = 6.dp, margin = 8.dp, endAligned = true)

/**
 * The Text options card (r3_text Frame 1; re-tap the armed Text tool): the note's default font, code font, size and
 * colour, its page margins, and the two typing preferences. Every change applies at once (live reflow), is not
 * undoable, and can be kept as the default for new notes, or Reset. Markdown shortcuts and Slash commands are app
 * preferences, so neither Reset nor the new-note default touches them.
 */
@Composable
internal fun TextToolConfigPopup(editor: Editor, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(editor.flowConfigValue()) }
    // The chip shows once the config has differed from the saved default, for the card's session.
    var shownSoFar by remember { mutableStateOf(editor.flowConfigValue() != editor.newNoteFlow) }

    fun apply(next: FlowDefaults) {
        config = next
        editor.setFlowConfig(next)
        if (TextOptionsLogic.revealsChip(next, editor.newNoteFlow)) shownSoFar = true
    }

    ToolCardFrame(onDismiss) {
        InkCard(stringResource(R.string.tool_text), onClose = onDismiss, width = 340.dp) {
            InkCardSection(first = true) {
                val faceLabel = stringResource(R.string.font)
                OptionRow(faceLabel) {
                    FaceField(faceLabel, config.face, monoOnly = false) { apply(config.copy(face = it)) }
                }
                val codeFaceLabel = stringResource(R.string.text_code_font)
                OptionRow(codeFaceLabel) {
                    FaceField(codeFaceLabel, config.monoFace, monoOnly = true) { apply(config.copy(monoFace = it)) }
                }
                val size = config.sizePt.toFloat()
                InkStepperRow(
                    label = stringResource(R.string.text_size),
                    value = stringResource(R.string.text_size_pt, config.sizePt.roundToInt()),
                    onMinus = { apply(config.copy(sizePt = InkStepGrid.step(value = size, steps = -1, step = 1f, range = SIZE_RANGE).toDouble())) },
                    onPlus = { apply(config.copy(sizePt = InkStepGrid.step(value = size, steps = 1, step = 1f, range = SIZE_RANGE).toDouble())) },
                    canMinus = InkStepGrid.canStepDown(size, SIZE_RANGE),
                    canPlus = InkStepGrid.canStepUp(size, SIZE_RANGE),
                )
                OptionRow(stringResource(R.string.colour)) {
                    ColourChoice(editor, config.color) { apply(config.copy(color = it)) }
                }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.text_page_margins), stringResource(R.string.text_mm))
                MarginGrid(config.margins) { apply(config.copy(margins = it)) }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.text_typing), stringResource(R.string.text_in_every_note))
                TypingSwitch(stringResource(R.string.markdown_shortcuts), stringResource(R.string.text_markdown_hint), editor.markdownInput) {
                    editor.setMarkdownInputPref(it)
                }
                TypingSwitch(stringResource(R.string.slash_commands), stringResource(R.string.text_slash_hint), editor.slashCommands) {
                    editor.setSlashCommandsPref(it)
                }
            }
            val chipOn = TextOptionsLogic.chipOn(config, editor.newNoteFlow, editor.factoryFlow)
            OptionsFooter(
                chipShown = TextOptionsLogic.chipShown(shownSoFar, config, editor.factoryFlow),
                chipOn = chipOn,
                onChip = { editor.saveNewNoteFlow(TextOptionsLogic.toggleTarget(!chipOn, config, editor.factoryFlow)) },
                onReset = { apply(editor.factoryFlow) },
            )
        }
    }
}

/** A labelled row (.tx-or): label at the start, the control at the end, at least 40 dp. */
@Composable
private fun OptionRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LabelType, color = LocalInk.current.text, maxLines = 1, modifier = Modifier.weight(1f))
        control()
    }
}

/**
 * A font field (.tx-dd, TX 162-167): 40 dp, up to 190 wide, r12, 1 dp line ring (2 dp solid while its list is open),
 * the name in its own face. It opens the grouped list under itself; the card's popover edge is the toolbar's, so the
 * list is told to measure from the field instead.
 */
@Composable
private fun FaceField(label: String, current: FontFace, monoOnly: Boolean, onPick: (FontFace) -> Unit) {
    val ink = LocalInk.current
    var open by remember { mutableStateOf(false) }
    val anchor = remember { PopoverAnchor() }
    val src = remember { MutableInteractionSource() }
    val shape = inkRounded(12.dp)
    val name = fontLabel(current)
    Box {
        Row(
            Modifier
                .popoverAnchor(anchor)
                .height(40.dp)
                .widthIn(max = 190.dp)
                .pressScale(src, 0.97f)
                .clip(shape)
                .border(if (open) 2.dp else 1.dp, if (open) ink.solid else ink.line, shape)
                .clickable(src, LocalIndication.current, role = Role.Button) { open = true }
                .semantics {
                    contentDescription = label // "Font" or "Code font", then the face it is set to
                    stateDescription = name
                }
                .padding(start = 14.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = FieldType.copy(fontFamily = current.toComposeFamily()),
                color = ink.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 144.dp),
            )
            Icon(Ph.caretDown, null, tint = ink.text2, modifier = Modifier.size(14.dp))
        }
        CompositionLocalProvider(LocalPopoverEdge provides null) {
            InkPopover(expanded = open, onDismiss = { open = false }, anchor = anchor, spec = FieldMenuSpec, prefer = PopoverSide.BELOW) {
                FontListMenu(current = current, monoOnly = monoOnly, maxHeight = 420.dp, onPick = { picked ->
                    if (picked != null) onPick(picked)
                    open = false
                })
            }
        }
    }
}

/** Colour (.tx-colr, TX 168-172): Auto, then the custom dot that opens the picker (live, stays open). */
@Composable
private fun ColourChoice(editor: Editor, color: Rgba?, onChange: (Rgba?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        InkChip(stringResource(R.string.auto), selected = color == null, onClick = { onChange(null) }, modifier = Modifier.height(32.dp))
        Box {
            // 28 dp to see; the click reaches 10 dp each side (48 wide, up to the Auto chip) and 6 dp above and below
            // (the 40 dp row, short of the Size stepper over it): the 48 x 40 box keeps a 28 dp place in the row.
            InkAddSwatch(
                colour = color,
                lit = picking,
                contentDescription = stringResource(R.string.material_custom_colour),
                modifier = Modifier.reachPast(horizontal = 10.dp, vertical = 6.dp).size(48.dp, 40.dp),
            ) { picking = true }
            if (picking) {
                OptionsColourPicker(
                    initial = color ?: editor.flowDefaultColor(),
                    recents = editor.recentColors,
                    onDismiss = { picking = false },
                    onPick = { onChange(it) },
                )
            }
        }
    }
}

/** Page margins (.tx-mg, TX 173): Left, Right / Top, Bottom; row gap 6, column gap 20. */
@Composable
private fun MarginGrid(m: FlowMargins, onChange: (FlowMargins) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MarginCell(stringResource(R.string.edge_left), m.leftMm, Modifier.weight(1f)) { onChange(m.copy(leftMm = it)) }
            MarginCell(stringResource(R.string.edge_right), m.rightMm, Modifier.weight(1f)) { onChange(m.copy(rightMm = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MarginCell(stringResource(R.string.edge_top), m.topMm, Modifier.weight(1f)) { onChange(m.copy(topMm = it)) }
            MarginCell(stringResource(R.string.edge_bottom), m.bottomMm, Modifier.weight(1f)) { onChange(m.copy(bottomMm = it)) }
        }
    }
}

/** One margin (.tx-mc, TX 174-178): 32 dp, the edge's name, then the Mini stepper (28 dp buttons) in mm. */
@Composable
private fun MarginCell(label: String, mm: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    val v = mm.toFloat()
    Row(modifier.height(32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = CellLabelType, color = LocalInk.current.text, maxLines = 1)
        InkStepper(
            value = mm.roundToInt().toString(),
            onMinus = { onChange(InkStepGrid.step(value = v, steps = -1, step = 1f, range = MARGIN_RANGE).toDouble()) },
            onPlus = { onChange(InkStepGrid.step(value = v, steps = 1, step = 1f, range = MARGIN_RANGE).toDouble()) },
            canMinus = InkStepGrid.canStepDown(v, MARGIN_RANGE),
            canPlus = InkStepGrid.canStepUp(v, MARGIN_RANGE),
            metrics = InkStepperMetrics.Mini,
        )
    }
}

/** A typing preference (.tx-swr, TX 179-182): title and one-line subtitle, the switch at the end; the row toggles. */
@Composable
private fun TypingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SwitchTitleType, color = ink.text, maxLines = 1)
            Text(sub, style = SwitchSubType, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        InkSwitch(checked, onCheckedChange = null)
    }
}

/** The footer (.tx-of, TX 183-188): the new-note chip once something changed, Reset at the end. */
@Composable
private fun OptionsFooter(chipShown: Boolean, chipOn: Boolean, onChip: () -> Unit, onReset: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .textTopRule(ink.line2)
            .padding(start = 20.dp, top = 10.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chipShown) {
            InkChip(
                stringResource(R.string.default_for_new_notes),
                selected = chipOn,
                onClick = onChip,
                modifier = Modifier.height(34.dp).semantics { toggleableState = ToggleableState(chipOn) },
                icon = if (chipOn) Ph.check else null,
                role = Role.Checkbox,
            )
        }
        Spacer(Modifier.weight(1f))
        InkGhostButton(stringResource(R.string.reset), onReset, Modifier.height(36.dp))
    }
}
