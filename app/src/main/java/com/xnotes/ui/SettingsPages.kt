package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageSize
import com.xnotes.settings.Preferences
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStepper
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** Pages & paper (r2_settings Frame 1): New pages, Paper, View. */
@Composable
internal fun PageSettings(m: SettingsModel) {
    val prefs = m.prefs
    var sizing by remember { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_sec_new_pages)) {
        ChoiceRow(SettingId.PAGE_SIZE, PageSize.entries.map { it to pageSizeLabel(it) }, prefs.defaultPageSize) {
            m.update(prefs.copy(defaultPageSize = it))
        }
        if (prefs.defaultPageSize == PageSize.CUSTOM) {
            // A custom page is taken as typed, so orientation has nothing to say about it and is
            // left out rather than shown doing nothing.
            NavRow(
                SettingId.CUSTOM_SIZE,
                stringResource(R.string.settings_custom_size_value, formatMm(prefs.customPageWidthMm), formatMm(prefs.customPageHeightMm)),
            ) { sizing = true }
        } else {
            SegmentRow(
                SettingId.ORIENTATION,
                listOf(Orientation.PORTRAIT, Orientation.LANDSCAPE),
                prefs.defaultPageOrientation,
                label = { if (it == Orientation.PORTRAIT) stringResource(R.string.orientation_portrait) else stringResource(R.string.orientation_landscape) },
            ) { m.update(prefs.copy(defaultPageOrientation = it)) }
        }
    }
    if (sizing) CustomPageSizeSheet(m) { sizing = false }

    SettingsSection(stringResource(R.string.settings_sec_paper)) {
        SettingRow(SettingId.PAGE_COLOUR) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                pageColorPresets.forEach { c ->
                    InkSwatch(c.toComposeColor(), prefs.pageColor == c, 24.dp) { m.update(prefs.copy(pageColor = c)) }
                }
                ColorPickerDot(
                    prefs.pageColor,
                    custom = prefs.pageColor != null && prefs.pageColor !in pageColorPresets,
                    onPick = { m.update(m.prefs.copy(pageColor = it)) },
                    dismissOnPick = false,
                ) { onDismiss, onPick -> PageColorGridPopup(m.prefs.pageColor, onDismiss, onPick) }
            }
        }
        SwitchRow(SettingId.PAGE_FOLLOWS_THEME, prefs.pageColor == null) {
            m.update(prefs.copy(pageColor = if (it) null else pageColorPresets.first()))
        }
        SwitchRow(SettingId.HIDE_BORDERS, prefs.hidePageBorders) { m.update(prefs.copy(hidePageBorders = it)) }
    }

    SettingsSection(stringResource(R.string.settings_sec_view)) {
        SliderRow(
            SettingId.SIDE_MARGIN,
            stringResource(R.string.settings_px, prefs.sideMargin.toInt()),
            prefs.sideMargin.toFloat(),
            0f..64f,
        ) { v ->
            // Whole pixels, as the value reads: each change re-lays the open note out, so a drag
            // should not do that for every fraction of a pixel.
            val px = v.roundToInt().toDouble()
            if (px != m.prefs.sideMargin) m.update(m.prefs.copy(sideMargin = px))
        }
        ZoomLimitRows(m)
    }
}

/**
 * The page zoom floor and ceiling, the same two limits the zoom menu sets, kept consistent the
 * same way: switching one on, or stepping it, never lets the floor rise above the ceiling. The
 * stepper (.st-stepper) shows only while its limit is on.
 */
@Composable
private fun ZoomLimitRows(m: SettingsModel) {
    val p = m.prefs
    val lo = Preferences.ZOOM_LIMIT_MIN_PCT
    val hi = Preferences.ZOOM_LIMIT_MAX_PCT
    val minTop = if (p.maxZoomEnabled) p.maxZoomPercent else hi
    val maxBottom = if (p.minZoomEnabled) p.minZoomPercent else lo
    SettingRow(SettingId.MIN_ZOOM, onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.minZoomEnabled) {
                InkStepper(
                    stringResource(R.string.settings_percent, p.minZoomPercent),
                    onMinus = { m.update(p.copy(minZoomPercent = (p.minZoomPercent - 10).coerceIn(lo, hi).coerceAtMost(minTop))) },
                    onPlus = { m.update(p.copy(minZoomPercent = (p.minZoomPercent + 10).coerceIn(lo, hi).coerceAtMost(minTop))) },
                    canMinus = p.minZoomPercent > lo,
                    canPlus = p.minZoomPercent < minTop,
                )
            }
            SettingsSwitch(p.minZoomEnabled) { on ->
                val pct = if (on && p.maxZoomEnabled && p.minZoomPercent > p.maxZoomPercent) p.maxZoomPercent else p.minZoomPercent
                m.update(p.copy(minZoomEnabled = on, minZoomPercent = pct))
            }
        }
    }
    SettingRow(SettingId.MAX_ZOOM, onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.maxZoomEnabled) {
                InkStepper(
                    stringResource(R.string.settings_percent, p.maxZoomPercent),
                    onMinus = { m.update(p.copy(maxZoomPercent = (p.maxZoomPercent - 10).coerceIn(lo, hi).coerceAtLeast(maxBottom))) },
                    onPlus = { m.update(p.copy(maxZoomPercent = (p.maxZoomPercent + 10).coerceIn(lo, hi).coerceAtLeast(maxBottom))) },
                    canMinus = p.maxZoomPercent > maxBottom,
                    canPlus = p.maxZoomPercent < hi,
                )
            }
            SettingsSwitch(p.maxZoomEnabled) { on ->
                val pct = if (on && p.minZoomEnabled && p.maxZoomPercent < p.minZoomPercent) p.minZoomPercent else p.maxZoomPercent
                m.update(p.copy(maxZoomEnabled = on, maxZoomPercent = pct))
            }
        }
    }
}

/** Custom size (r2_settings): width and height in millimetres, applied as they are typed; Done closes. */
@Composable
private fun CustomPageSizeSheet(m: SettingsModel, onDone: () -> Unit) {
    InkSheet(
        title = stringResource(R.string.settings_custom_size),
        onDismiss = onDone,
        subtitle = stringResource(R.string.settings_custom_size_range),
        width = 440.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.done), onDone)
        },
    ) {
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MillimetreField(stringResource(R.string.width_mm), m.prefs.customPageWidthMm, Modifier.weight(1f)) {
                m.update(m.prefs.copy(customPageWidthMm = it))
            }
            MillimetreField(stringResource(R.string.height_mm), m.prefs.customPageHeightMm, Modifier.weight(1f)) {
                m.update(m.prefs.copy(customPageHeightMm = it))
            }
        }
    }
}

/**
 * One side of a custom page, in millimetres. The field keeps whatever is typed so a number can be
 * cleared and retyped; only a value that parses inside the settable range reaches the preference.
 */
@Composable
private fun MillimetreField(label: String, value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    val ink = LocalInk.current
    var field by remember { mutableStateOf(TextFieldValue(formatMm(value), TextRange(formatMm(value).length))) }
    // Adopt an outside change (Reset all settings) without ever rewriting what is being typed.
    LaunchedEffect(value) { if (field.text.trim().toDoubleOrNull() != value) field = TextFieldValue(formatMm(value), TextRange(formatMm(value).length)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = InkType.label, color = ink.text2)
        InkTextField(
            field,
            { next ->
                field = next
                next.text.trim().toDoubleOrNull()?.let {
                    if (it in Preferences.CUSTOM_PAGE_MIN_MM..Preferences.CUSTOM_PAGE_MAX_MM) onChange(it)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
    }
}

/** Drop a whole number's ".0" so the field reads "210", not "210.0". */
internal fun formatMm(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toInt().toString() else v.toString()
