package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/** Text & fonts (r2_settings Frame 1): Typing; Code (only with highlighting); imported fonts in their own face. */
@Composable
internal fun TextSettings(m: SettingsModel, onImportCodeTheme: () -> Unit, onImportFont: () -> Unit) {
    val ink = LocalInk.current
    val editor = m.editor
    val prefs = m.prefs
    SettingsSection(stringResource(R.string.settings_sec_typing)) {
        // Both save themselves through the editor (the text tool's popup flips them too).
        SwitchRow(SettingId.MARKDOWN, editor.markdownInput) { editor.setMarkdownInputPref(it); m.resync() }
        SwitchRow(SettingId.SLASH_COMMANDS, editor.slashCommands) { editor.setSlashCommandsPref(it); m.resync() }
    }
    if (editor.treeSitterAvailable) {
        SettingsSection(stringResource(R.string.settings_sec_code), footer = stringResource(R.string.settings_code_footer)) {
            val languages = remember(editor) { editor.scmLanguages() }
            ChoiceRow(
                SettingId.CODE_LANGUAGE,
                listOf(CodeLanguages.PLAIN to stringResource(R.string.text_code_plain_settings)) +
                    languages.map { it to (CodeLanguages.name(it) ?: it) },
                prefs.defaultCodeLanguage,
            ) { m.update(prefs.copy(defaultCodeLanguage = it)) }
            SettingRow(SettingId.CODE_THEME) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (editor.hasCustomCodeTheme) prefs.codeThemeName ?: stringResource(R.string.custom_theme) else stringResource(R.string.settings_code_theme_builtin),
                        style = InkType.body,
                        color = ink.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    if (editor.hasCustomCodeTheme) SettingsPillButton(stringResource(R.string.reset)) { editor.resetCodeTheme() }
                    SettingsPillButton(stringResource(R.string.settings_import), icon = Ph.downloadSimple) { onImportCodeTheme() }
                }
            }
        }
    }
    SettingsSection(stringResource(R.string.settings_sec_fonts)) {
        SettingRow(SettingId.FONTS) {
            SettingsPillButton(stringResource(R.string.settings_import), icon = Ph.downloadSimple) { onImportFont() }
        }
        for (font in editor.customFonts) {
            SettingRowBase(
                title = font.label,
                description = if (font.mono) stringResource(R.string.mono_suffix).trim() else null,
                titleStyle = TextStyle(fontFamily = font.face.toComposeFamily()),
            ) {
                SettingsPillButton(stringResource(R.string.remove), danger = true) { editor.removeCustomFont(font.face) }
            }
        }
    }
}
