package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.Rgba
import com.xnotes.settings.MaterialColourMode
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor

internal val pageColorPresets = listOf(
    Rgba(22, 22, 22), Rgba(13, 13, 13), Rgba(255, 255, 255), Rgba(247, 243, 233), Rgba(232, 232, 232),
)

/** Below this width the category list and the open category take turns, phone-style. */
private val TWO_PANE_MIN_WIDTH = 640.dp
/** Below this width a page stacks its segmented pickers under their titles. */
private val NARROW_PAGE_WIDTH = 560.dp

/**
 * The preferences an open Settings screen edits. Every change is pushed straight to the [Editor]
 * (and persisted), so theme and page tweaks are seen immediately, including in the surrounding
 * backstage. [update] refreshes the open note's canvas; [updateHome] is for the home, explorer and
 * export settings, which leave the note alone and so skip that refresh.
 */
@Stable
internal class SettingsModel(val editor: Editor) {
    var prefs by mutableStateOf(editor.preferences)

    /** [resetAll] only from "Reset all": it alone resets the player's speed too ([Editor.applyPreferences]). */
    fun update(p: Preferences, resetAll: Boolean = false) {
        prefs = p
        editor.applyPreferences(p, resetAll)
    }

    fun updateHome(p: Preferences) {
        prefs = p
        editor.applyHomePreferences(p)
    }

    /**
     * Re-read after a setting the editor saves on its own (full screen, Markdown, slash commands),
     * so the next [update] does not write the old value of that setting back.
     */
    fun resync() {
        prefs = editor.preferences
    }
}

/**
 * Settings as a backstage pane, laid out the way Samsung Notes and Starnote lay theirs out on a
 * tablet: a list of categories on the left, with a search field over it, and the chosen
 * category's settings on the right as captioned, rounded cards of rows. In a narrow window (split
 * screen, portrait with the sidebar open) the two take turns: the list, then a category with a
 * back arrow. Only the open category is composed, so opening the screen costs one page.
 */
@Composable
fun PreferencesPane(
    editor: Editor,
    compact: Boolean,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onBackToHome: () -> Unit,
    onImportCodeTheme: () -> Unit = {},
    onImportFont: () -> Unit = {},
    onPickRoot: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val model = remember(editor) { SettingsModel(editor) }
    // Follow out-of-pane preference changes too (the .scm import round-trips a picker).
    LaunchedEffect(editor.prefsVersion) { model.resync() }

    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryName?.let { n -> SettingsCategory.entries.firstOrNull { it.name == n } }
    var query by rememberSaveable { mutableStateOf("") }
    var highlight by remember { mutableStateOf<SettingsHighlight?>(null) }
    var nonce by remember { mutableIntStateOf(0) }

    fun open(c: SettingsCategory) {
        categoryName = c.name
        highlight = null
    }
    fun openSetting(id: SettingId) {
        open(id.category)
        highlight = SettingsHighlight(id, ++nonce)
        focusManager.clearFocus()
    }

    // Another screen asked to show one setting (Trash's Change): open it here, once.
    val linked = SettingsLink.pending
    LaunchedEffect(linked) {
        if (linked != null) SettingsLink.take()?.let { openSetting(it) } // null once it has gone stale
    }

    val entries = rememberSearchEntries(model)
    val results = remember(query, entries) { searchSettings(query, entries) }
    val categoryHits = categoriesMatching(query)

    val divider = LocalInk.current.line2
    // A tap on empty space dismisses a text field's focus; children consume their own taps.
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH

        // Clearing the search sits under the page-level back so it is only consulted once no
        // drilled-in category is left to close.
        BackHandler(enabled = query.isNotEmpty() && (twoPane || category == null)) { query = "" }
        BackHandler(enabled = !twoPane && category != null) { categoryName = null }

        val list: @Composable (selected: SettingsCategory?) -> Unit = { selected ->
            CategoryColumn(
                compact = compact,
                sidebarOpen = sidebarOpen,
                onShowSidebar = onShowSidebar,
                onBackToHome = onBackToHome,
                query = query,
                onQuery = { query = it },
                selected = selected,
                results = results,
                categoryHits = categoryHits,
                chevrons = !twoPane,
                onCategory = ::open,
                onSetting = ::openSetting,
            )
        }
        val page: @Composable (SettingsCategory, Boolean) -> Unit = { c, back ->
            CompositionLocalProviderHighlight(highlight) {
                CategoryPage(
                    model = model,
                    category = c,
                    onBack = if (back) ({ categoryName = null }) else null,
                    onImportCodeTheme = onImportCodeTheme,
                    onImportFont = onImportFont,
                    onPickRoot = onPickRoot,
                )
            }
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(304.dp)
                        .fillMaxHeight()
                        .drawBehind { drawLine(divider, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx()) },
                ) { list(category ?: SettingsCategory.GENERAL) }
                Box(Modifier.weight(1f).fillMaxHeight()) { page(category ?: SettingsCategory.GENERAL, false) }
            }
        } else if (category == null) {
            list(null)
        } else {
            page(category, true)
        }
    }
}

@Composable
private fun CompositionLocalProviderHighlight(highlight: SettingsHighlight?, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsHighlight provides highlight, content = content)
}

/** The searchable settings, in screen order, with words resolved; rows the screen hides now are left out. */
@Composable
private fun rememberSearchEntries(model: SettingsModel): List<SettingSearchEntry> {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val editor = model.editor
    // Read through derived state, so a slider dragged elsewhere on the screen does not recompose
    // the whole pane: only a change to which rows exist does.
    val customColours by remember { derivedStateOf { model.prefs.materialMode.let { it == MaterialColourMode.SINGLE || it == MaterialColourMode.DUAL } } }
    val customPage by remember { derivedStateOf { model.prefs.defaultPageSize == PageSize.CUSTOM } }
    val code = editor.treeSitterAvailable
    val library by remember { derivedStateOf { editor.browseRoot != null } }
    return remember(config, customColours, customPage, code, library) {
        val res = ctx.resources
        SettingId.entries
            .filter { id ->
                when (id) {
                    SettingId.COLOUR_STYLE, SettingId.CONTRAST -> customColours
                    SettingId.CUSTOM_SIZE -> customPage
                    SettingId.ORIENTATION -> !customPage
                    SettingId.CODE_LANGUAGE, SettingId.CODE_THEME -> code
                    SettingId.COLOUR_NAMES -> library
                    else -> true
                }
            }
            .map { SettingSearchEntry(it, res.getString(it.title), res.getString(it.description), res.getString(it.category.title)) }
    }
}

@Composable
private fun categoriesMatching(query: String): List<SettingsCategory> {
    if (query.isBlank()) return emptyList()
    val q = query.trim()
    return SettingsCategory.entries.filter { stringResource(it.title).contains(q, ignoreCase = true) }
}

/** A category's icon, and its Fill version for when it is chosen (.st-cat.on). */
private fun categoryIcon(c: SettingsCategory, filled: Boolean = false): ImageVector = when (c) {
    SettingsCategory.GENERAL -> if (filled) Ph.toggleRightFill else Ph.toggleRight
    SettingsCategory.APPEARANCE -> if (filled) Ph.paletteFill else Ph.palette
    SettingsCategory.WRITING -> if (filled) Ph.penFill else Ph.pen
    SettingsCategory.PAGES -> if (filled) Ph.fileTextFill else Ph.fileText
    SettingsCategory.TEXT -> if (filled) Ph.textAaFill else Ph.textAa
    SettingsCategory.LIBRARY -> if (filled) Ph.folderSimpleFill else Ph.folderSimple
    SettingsCategory.EXPORT -> if (filled) Ph.shareNetworkFill else Ph.shareNetwork
    SettingsCategory.ADVANCED -> if (filled) Ph.slidersHorizontalFill else Ph.slidersHorizontal
    SettingsCategory.ABOUT -> if (filled) Ph.infoFill else Ph.info
}

/** The left column: the screen's title, the search field, and the categories or the search results. */
@Composable
private fun CategoryColumn(
    compact: Boolean,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onBackToHome: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    selected: SettingsCategory?,
    results: List<SettingSearchEntry>,
    categoryHits: List<SettingsCategory>,
    chevrons: Boolean,
    onCategory: (SettingsCategory) -> Unit,
    onSetting: (SettingId) -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxSize()) {
        // The title row (.st-colh), 84dp with a hairline; a back arrow to Home on compact, else a sidebar button when hidden.
        Row(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .drawBehind { drawLine(ink.line2, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
                .padding(start = if (compact || !sidebarOpen) 12.dp else 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (compact) InkIconButton(Ph.caretLeft, stringResource(R.string.back_to_home), onBackToHome)
            else if (!sidebarOpen) InkIconButton(Ph.sidebarSimple, stringResource(R.string.show_sidebar), onShowSidebar)
            Text(stringResource(R.string.library_settings), style = InkType.display, color = ink.text)
        }
        SettingsSearchField(query, onQuery, Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp))
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (query.isBlank()) {
                SettingsCategory.entries.forEach { c ->
                    // Advanced and About are set apart at the bottom.
                    if (c == SettingsCategory.ADVANCED) Spacer(Modifier.height(12.dp))
                    SettingsListItem(categoryIcon(c), stringResource(c.title), stringResource(c.summary), selected = c == selected, showChevron = chevrons, selectedIcon = categoryIcon(c, filled = true)) { onCategory(c) }
                }
            } else {
                categoryHits.forEach { c ->
                    SettingsListItem(categoryIcon(c), stringResource(c.title), stringResource(c.summary), selected = false, showChevron = chevrons) { onCategory(c) }
                }
                if (results.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.settings_results, results.size, results.size),
                        style = InkType.small.copy(fontWeight = FontWeight.Bold), color = ink.text2,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                results.forEach { r ->
                    SettingsListItem(categoryIcon(r.id.category), r.title, "${r.categoryTitle} · ${r.description}", selected = false, showChevron = chevrons) { onSetting(r.id) }
                }
                if (results.isEmpty() && categoryHits.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_results, query.trim()),
                        style = InkType.body, color = ink.text2,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}

/** A category's page: its title (with a back arrow when drilled into) over its cards. */
@Composable
private fun CategoryPage(
    model: SettingsModel,
    category: SettingsCategory,
    onBack: (() -> Unit)?,
    onImportCodeTheme: () -> Unit,
    onImportFont: () -> Unit,
    onPickRoot: () -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxSize()) {
        // The pane header (.st-ph): 84dp, the category and its one line, a hairline under it.
        Row(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .drawBehind { drawLine(ink.line2, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
                .padding(start = if (onBack != null) 12.dp else 40.dp, end = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) InkIconButton(Ph.caretLeft, stringResource(R.string.settings_back), onBack)
            Column(Modifier.weight(1f)) {
                Text(stringResource(category.title), style = InkType.display.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp), color = ink.text)
                Text(stringResource(category.summary), style = InkType.meta, color = ink.text2, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (category == SettingsCategory.ABOUT) {
            AboutPane()
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < NARROW_PAGE_WIDTH
            CompositionLocalProvider(LocalSettingsNarrow provides narrow) {
                // Keyed so each category opens at its top; it fades up 6dp as it opens (.st-body.st-enter).
                key(category) {
                    val enter = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { enter.animateTo(1f, tween(InkMotion.BASE, easing = InkMotion.Glide)) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = enter.value
                                translationY = (1f - enter.value) * 6.dp.toPx()
                            }
                            // Ending at the keyboard's edge scrolls a focused field up out from under it.
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 40.dp, end = 40.dp, bottom = 64.dp),
                    ) {
                        when (category) {
                            SettingsCategory.GENERAL -> GeneralSettings(model)
                            SettingsCategory.APPEARANCE -> AppearanceSettings(model)
                            SettingsCategory.WRITING -> WritingSettings(model)
                            SettingsCategory.PAGES -> PageSettings(model)
                            SettingsCategory.TEXT -> TextSettings(model, onImportCodeTheme, onImportFont)
                            SettingsCategory.LIBRARY -> LibrarySettings(model, onPickRoot)
                            SettingsCategory.EXPORT -> ExportSettings(model)
                            SettingsCategory.ADVANCED -> AdvancedSettings(model)
                            SettingsCategory.ABOUT -> Unit
                        }
                    }
                }
            }
        }
    }
}

// --- shared pieces ---

/** A spectrum wheel signals that this dot opens the full picker. */
private val spectrumBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
    ),
)

/**
 * A spectrum dot that opens [grid], a popup of colour swatches — shared by the accent and
 * page-colour rows. Until a colour outside the row's presets is chosen the dot shows the
 * spectrum wheel; once one is, it fills with that colour and reads as selected.
 */
@Composable
internal fun ColorPickerDot(
    current: Rgba?,
    custom: Boolean,
    onPick: (Rgba) -> Unit,
    dismissOnPick: Boolean = true,
    enabled: Boolean = true,
    grid: @Composable (onDismiss: () -> Unit, onPick: (Rgba) -> Unit) -> Unit,
) {
    val ink = LocalInk.current
    val label = stringResource(R.string.material_custom_colour)
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(30.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .semantics {
                    contentDescription = label
                    selected = custom
                }
                .then(if (custom) Modifier.border(2.dp, ink.solid, CircleShape) else Modifier)
                .padding(4.dp)
                .clip(CircleShape)
                .then(if (custom && current != null) Modifier.background(current.toComposeColor()) else Modifier.background(spectrumBrush))
                .border(1.dp, ink.line, CircleShape)
                .clickable(enabled = enabled) { open = true },
        )
        // A live picker (e.g. the page/ink popup) edits across several taps, so it stays open until a
        // tap outside; a one-shot grid (the accent swatches) closes the moment a colour is chosen.
        if (open) grid({ open = false }, { onPick(it); if (dismissOnPick) open = false })
    }
}

/**
 * The colour-code picker shown inside a note/folder's overflow menu: a None row to clear the colour,
 * then the picker's curated palette as round chips (a neutral row, then pale tints through the
 * deepest shades). [onPick] is called with null for None. Rendered directly inside the menu's own
 * dropdown column.
 */
@Composable
internal fun ColorCodeMenuContent(onPick: (Rgba?) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { onPick(null) }.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoColourSwatch()
            Text(stringResource(R.string.none), color = ink.text, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }
        FullSwatchGrid { onPick(it) }
    }
}

/**
 * Page/pattern colour picker: the shared [ColorPickerPopup] (Swatches + Spectrum tabs and HEX/RGB
 * fields), whose palette runs from pale tints to the deepest shades plus a neutral row, so
 * paper-like and muted page backgrounds are reachable, not just the saturated ones. It keeps no
 * recents list.
 */
@Composable
internal fun PageColorGridPopup(initial: Rgba?, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial = initial, recents = emptyList(), onDismiss = onDismiss, onPick = onPick)
}
