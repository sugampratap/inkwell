package com.xnotes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

internal val penButtonOptions = listOf("eraser" to R.string.tool_eraser, "pan" to R.string.tool_pan, "select" to R.string.tool_select, "none" to R.string.none)

/** The side-button menu's icons (r2_settings Frame 3). */
private val penButtonIcons = mapOf("eraser" to Ph.eraser, "pan" to Ph.hand, "select" to Ph.selection, "none" to Ph.prohibit)

internal val tapGestureOptions = listOf(
    "none" to R.string.none,
    "undo" to R.string.undo,
    "redo" to R.string.redo,
    "toggle_pan" to R.string.settings_tap_pan,
    "toggle_eraser" to R.string.settings_tap_eraser,
    "toggle_previous" to R.string.settings_tap_previous,
)

/** The indent of a row that depends on the one above it (.st-dep, .st-sub). */
private val DEPENDENT_INDENT = 20.dp

/** Writing & S Pen (r2_settings Frame 3): Writing, Pen buttons (other styluses folded), Finger taps. */
@Composable
internal fun WritingSettings(m: SettingsModel) {
    val prefs = m.prefs
    val tapOptions = tapGestureOptions.map { (id, res) -> id to stringResource(res) }
    SettingsSection(stringResource(R.string.settings_sec_writing)) {
        SwitchRow(SettingId.FINGER_DRAWS, prefs.fingerDraws) { m.update(prefs.copy(fingerDraws = it)) }
        // Kept in step with "Scrolling while zoom is locked" below (see Preferences.withLockedTwoFingerScroll).
        SwitchRow(SettingId.LOCKED_TWO_FINGER_SCROLL, prefs.lockedTwoFingerScroll) { m.update(prefs.withLockedTwoFingerScroll(it)) }
        SwitchRow(SettingId.DETECT_SHAPES, prefs.detectShapes) { m.update(prefs.copy(detectShapes = it)) }
        SwitchRow(SettingId.PEN_BOX, prefs.showPenBox) { m.update(prefs.copy(showPenBox = it)) }
    }

    val highlight = LocalSettingsHighlight.current
    var othersOpen by rememberSaveable { mutableStateOf(false) }
    // A search result inside the fold opens it first, so the row it scrolls to and flashes is there.
    LaunchedEffect(highlight) { if (highlight != null && highlight.id in SettingsFold.OTHER_STYLUSES) othersOpen = true }
    SettingsSection(stringResource(R.string.settings_sec_pen_buttons)) {
        ChoiceRow(
            SettingId.PEN_BUTTON_HOLD,
            penButtonOptions.map { (id, res) -> id to stringResource(res) },
            prefs.penButtonTool,
            icons = { penButtonIcons[it] },
        ) { m.update(prefs.copy(penButtonTool = it)) }
        // Hover only means something for a tool that acts without contact: the eraser and pan.
        val hoverOk = prefs.penButtonTool == "eraser" || prefs.penButtonTool == "pan"
        SwitchRow(
            SettingId.PEN_BUTTON_HOVER,
            prefs.penButtonHover,
            enabled = hoverOk,
            indent = DEPENDENT_INDENT,
            description = if (hoverOk) null else stringResource(R.string.settings_hover_why_off),
        ) { m.update(prefs.copy(penButtonHover = it)) }
        val set = SettingsFold.othersSet(prefs)
        SettingRowBase(
            title = stringResource(R.string.settings_other_styluses),
            description = stringResource(R.string.settings_other_styluses_desc),
            onClick = { othersOpen = !othersOpen },
        ) {
            FoldValue(if (set == 0) stringResource(R.string.settings_others_none) else pluralStringResource(R.plurals.settings_others_set, set, set), othersOpen)
        }
        if (othersOpen) {
            ChoiceRow(SettingId.STYLUS_DOUBLE_TAP, tapOptions, prefs.stylusDoubleTap, indent = DEPENDENT_INDENT, animateIn = true) { m.update(prefs.copy(stylusDoubleTap = it)) }
            ChoiceRow(SettingId.STYLUS_BUTTON_TAP, tapOptions, prefs.stylusButtonTap, indent = DEPENDENT_INDENT, animateIn = true) { m.update(prefs.copy(stylusButtonTap = it)) }
            ChoiceRow(SettingId.REDMI_BUTTON_1, tapOptions, prefs.stylusButton1Tap, indent = DEPENDENT_INDENT, animateIn = true) { m.update(prefs.copy(stylusButton1Tap = it)) }
            ChoiceRow(SettingId.REDMI_BUTTON_2, tapOptions, prefs.stylusButton2Tap, indent = DEPENDENT_INDENT, animateIn = true) { m.update(prefs.copy(stylusButton2Tap = it)) }
        }
    }

    SettingsSection(stringResource(R.string.settings_sec_finger_taps)) {
        ChoiceRow(SettingId.TWO_FINGER_TAP, tapOptions, prefs.twoFingerTap) { m.update(prefs.copy(twoFingerTap = it)) }
        ChoiceRow(SettingId.THREE_FINGER_TAP, tapOptions, prefs.threeFingerTap) { m.update(prefs.copy(threeFingerTap = it)) }
        SegmentRow(
            SettingId.ZOOM_LOCK_PAN,
            listOf("single", "double", "none"),
            prefs.zoomLockPanShown,
            label = {
                when (it) {
                    "single" -> stringResource(R.string.settings_pan_one)
                    "double" -> stringResource(R.string.settings_pan_two)
                    else -> stringResource(R.string.none)
                }
            },
        ) { m.update(prefs.withZoomLockPan(it)) }
    }
}

/** The fold row's value (.gv + .st-caretd): "2 set" and a caret that turns over as it opens (read only in the layer). */
@Composable
private fun FoldValue(text: String, open: Boolean) {
    val ink = LocalInk.current
    val turn = animateFloatAsState(if (open) 180f else 0f, tween(InkMotion.BASE, easing = InkMotion.Glide), label = "fold")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = InkType.body, color = ink.text2, maxLines = 1)
        Icon(Ph.caretDown, null, tint = ink.text3, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn.value })
    }
}
