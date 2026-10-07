package com.xnotes.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.settings.ExplorerLayout
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkAddChip
import com.xnotes.ui.kit.InkMarkChip
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Library & files (r2_settings Frame 1): Storage, Home screen, Files (with Tag names), Trash. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySettings(m: SettingsModel, onPickRoot: () -> Unit) {
    val editor = m.editor
    val prefs = m.prefs
    val root = editor.browseRoot
    val rootName by produceState(root?.let { editor.cachedRootName(it) }, root) {
        value = root?.let { r -> withContext(Dispatchers.IO) { editor.browseRootName(r) } }
    }
    SettingsSection(stringResource(R.string.settings_sec_storage)) {
        NavRow(
            SettingId.NOTES_FOLDER,
            if (root == null) stringResource(R.string.settings_notes_folder_none) else rootName ?: stringResource(R.string.folder),
            icon = Ph.folderSimple,
            onClick = onPickRoot,
        )
    }

    SettingsSection(stringResource(R.string.settings_sec_home)) {
        // The layouts' icons are the library view switcher's (layoutIcon, ExplorerChrome.kt).
        ChoiceRow(
            SettingId.HOME_LAYOUT,
            ExplorerLayout.entries.map { it to stringResource(it.labelRes) },
            editor.explorerView.layout,
            icons = { layoutIcon(it) },
        ) { editor.setDefaultLayout(it) }
        SettingRow(
            SettingId.SWITCHER_LAYOUTS,
            below = {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerLayout.entries.forEach { l ->
                        val on = l in prefs.switcherLayouts
                        InkMarkChip(stringResource(l.labelRes), on, {
                            // At least one layout stays in the switcher.
                            val next = if (on) prefs.switcherLayouts - l else prefs.switcherLayouts + l
                            if (next.isNotEmpty()) m.updateHome(prefs.copy(switcherLayouts = ExplorerLayout.entries.filter { it in next }))
                        }, icon = layoutIcon(l))
                    }
                }
            },
        )
        SettingRow(
            SettingId.SIDEBAR_SECTIONS,
            below = {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun section(label: String, icon: ImageVector, on: Boolean, flip: () -> Preferences) =
                        InkMarkChip(label, on, { m.updateHome(flip()) }, icon = icon)
                    section(stringResource(R.string.recent), Ph.clock, prefs.sidebarRecent) { prefs.copy(sidebarRecent = !prefs.sidebarRecent) }
                    section(stringResource(R.string.settings_sidebar_pinned_folders), Ph.pushPin, prefs.sidebarPinned) { prefs.copy(sidebarPinned = !prefs.sidebarPinned) }
                    section(stringResource(R.string.settings_sidebar_tags), Ph.tag, prefs.sidebarColours) { prefs.copy(sidebarColours = !prefs.sidebarColours) }
                    // Trash is only offered while deleted notes go to Trash.
                    if (prefs.trashDays != 0) section(stringResource(R.string.trash), Ph.trash, prefs.sidebarTrash) { prefs.copy(sidebarTrash = !prefs.sidebarTrash) }
                }
            },
        )
        val words = rememberExplorerWords()
        ChoiceRow(
            SettingId.DATES,
            listOf(
                "relative" to words.daysAgo(2),
                "day" to "${words.shortWeekday(java.time.DayOfWeek.WEDNESDAY)} ${clockText(words, java.time.LocalTime.of(17, 30), true)}",
                "date" to words.dayMonthYear(16, java.time.Month.SEPTEMBER, 2026),
            ),
            prefs.dateStyle,
        ) { m.updateHome(prefs.copy(dateStyle = it)) }
        SegmentRow(
            SettingId.TAPPING_FILE,
            listOf(false, true),
            prefs.tapPreviews,
            label = { if (it) stringResource(R.string.settings_tap_preview) else stringResource(R.string.settings_tap_open) },
        ) { m.updateHome(prefs.copy(tapPreviews = it)) }
    }

    SettingsSection(stringResource(R.string.settings_sec_files)) {
        SwitchRow(SettingId.PER_FOLDER_VIEWS, prefs.perFolderViews) { m.updateHome(prefs.copy(perFolderViews = it)) }
        SwitchRow(SettingId.CREATE_BUTTON, prefs.showCreateButton) { m.updateHome(prefs.copy(showCreateButton = it)) }
        SwitchRow(SettingId.FOLDER_COUNTS, prefs.showFolderCounts) { m.updateHome(prefs.copy(showFolderCounts = it)) }
        SwitchRow(SettingId.EXTENSIONS, prefs.showExtensions) { m.updateHome(prefs.copy(showExtensions = it)) }
        // Tag names live in the notes folder, so the row needs one.
        if (root != null) ColourNamesRow(editor)
    }

    SettingsSection(stringResource(R.string.settings_sec_trash)) {
        ChoiceRow(
            SettingId.KEEP_DELETED,
            listOf(
                0 to stringResource(R.string.off),
                7 to pluralStringResource(R.plurals.days_count, 7, 7),
                30 to pluralStringResource(R.plurals.days_count, 30, 30),
                Preferences.TRASH_FOREVER to stringResource(R.string.until_emptied),
            ),
            prefs.trashDays,
        ) { m.updateHome(prefs.copy(trashDays = it)) }
    }
}

/**
 * Tag names (r2_settings): a chip per named colour — its dot and name, tap to rename or remove —
 * and "Name a colour", which opens the colour menu and then the Name this colour sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourNamesRow(editor: Editor) {
    val palette = LocalPalette.current
    var namingColor by remember { mutableStateOf<Rgba?>(null) }
    namingColor?.let { c -> ColorNameDialog(editor, c) { namingColor = null } }
    SettingRow(
        SettingId.COLOUR_NAMES,
        below = {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                editor.colorNames.entries.sortedBy { it.value.lowercase() }.forEach { (c, name) ->
                    TagChip(name, codeTint(c, palette)) { namingColor = c }
                }
                var picking by remember { mutableStateOf(false) }
                Box {
                    InkAddChip(stringResource(R.string.name_a_colour), { picking = true })
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        ColorCodeMenuContent { c -> picking = false; if (c != null) namingColor = c }
                    }
                }
            }
        },
    )
}

/** A named tag (.chip with a dot): 36dp pill, hairline ring, the colour's dot and its name. */
@Composable
private fun TagChip(name: String, dot: Color, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(36.dp)
            .pressScale(src, 0.97f)
            .clip(CircleShape)
            .border(1.dp, ink.line, CircleShape)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Text(name, style = InkType.chip, color = ink.text, maxLines = 1)
    }
}
