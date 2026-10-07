package com.xnotes.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.settings.Preferences
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import kotlin.math.roundToInt

/** Export & sharing: the one export setting, and where the rest are chosen. */
@Composable
internal fun ExportSettings(m: SettingsModel) {
    val prefs = m.prefs
    SettingsSection(stringResource(R.string.settings_sec_pdf), footer = stringResource(R.string.settings_export_footer)) {
        SwitchRow(SettingId.PDF_BOOKMARKS, prefs.pdfHeadingBookmarks) { m.updateHome(prefs.copy(pdfHeadingBookmarks = it)) }
    }
}

/** Advanced (r2_settings Frame 1): Performance, Diagnostics, Reset. */
@Composable
internal fun AdvancedSettings(m: SettingsModel) {
    val ink = LocalInk.current
    val prefs = m.prefs
    SettingsSection(stringResource(R.string.settings_sec_performance), footer = stringResource(R.string.settings_low_latency_help)) {
        // Steps of 512 px from 1024 to 4096, the seven sizes the cache is tuned for.
        SliderRow(
            SettingId.CACHE_RESOLUTION,
            stringResource(R.string.settings_px, prefs.maxCacheResolution),
            prefs.maxCacheResolution.toFloat(),
            1024f..4096f,
        ) { v ->
            val px = ((v / 512f).roundToInt() * 512).coerceIn(1024, 4096)
            if (px != m.prefs.maxCacheResolution) m.update(m.prefs.copy(maxCacheResolution = px))
        }
        // Shown the way round people think of it: on is the fast path, off the workaround.
        SwitchRow(SettingId.FRONT_BUFFERING, !prefs.disableFrontBuffering) { m.update(prefs.copy(disableFrontBuffering = !it)) }
    }
    SettingsSection(stringResource(R.string.settings_sec_diagnostics)) {
        SettingRow(SettingId.DEBUG_OVERLAY) { SettingsTag(stringResource(R.string.settings_four_finger_tap)) }
    }
    var confirming by remember { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_sec_reset)) {
        SettingRow(SettingId.RESET_ALL, onClick = { confirming = true }) {
            SettingsPillButton(stringResource(R.string.reset), danger = true) { confirming = true }
        }
    }
    if (confirming) {
        // P8-6: a titled sheet without a close button; the footer is the only way on.
        InkSheet(
            title = stringResource(R.string.settings_reset_confirm_title),
            onDismiss = { confirming = false },
            width = 440.dp,
            showClose = false,
            footer = {
                Spacer(Modifier.weight(1f))
                InkGhostButton(stringResource(R.string.cancel), { confirming = false })
                InkSecondaryButton(stringResource(R.string.reset), {
                    confirming = false
                    // One apply and one save: the full path also sets the corners and the toolbar look at once.
                    m.update(Preferences(), resetAll = true)
                }, danger = true)
            },
        ) {
            Text(
                stringResource(R.string.settings_reset_confirm_body),
                style = InkType.body.copy(lineHeight = 21.sp),
                color = ink.text2,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
            )
        }
    }
}
