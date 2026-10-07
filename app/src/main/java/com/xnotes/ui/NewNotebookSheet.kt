package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.template.Template
import com.xnotes.platform.TemplateLibrary
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBrandButton
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** A new note is laid out at the default resolution (see [com.xnotes.core.model.Document.blankPixels]), so its spacing converts at it. */
private const val NB_DPI = PageSize.DEFAULT_DPI

/** Every cover cloth's name, in [CoverPalette.colors]' order (stored picks index it, so names follow it too). */
private val COVER_NAMES = listOf(
    R.string.cover_petrol, R.string.cover_sand, R.string.cover_sage, R.string.cover_plum, R.string.cover_denim,
    R.string.cover_oat, R.string.cover_slate, R.string.cover_terracotta, R.string.cover_forest, R.string.cover_heather,
    R.string.cover_navy, R.string.cover_teal, R.string.cover_sage, R.string.cover_mustard, R.string.cover_terracotta, R.string.cover_blush,
)

/**
 * The New notebook sheet (r2_page_notebook Frame 3): the paper a note is made on, chosen before it
 * is made. Nothing touches the editor while it is open. Create stamps the choices as the defaults for
 * new notes (the new-note style Page setup's "Save as my default" writes, and the size and
 * orientation Preferences sets) and then has [onCreate] write the file, which takes them from there,
 * so the note is made in one write with its paper in place and its undo history empty; the next
 * notebook starts from these choices too. [onCreate] makes the file named as given (blank takes the
 * filename template) and returns its uri, or null; [onCreated] follows a success. [folderName] is the
 * folder on screen, where the notebook is made.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewNotebookSheet(
    editor: Editor,
    initialName: String,
    folderName: String? = null,
    onCreate: suspend (name: String) -> String?,
    onCreated: (uri: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    val marks = rememberLibraryMarks()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val startName = remember { initialName }
    var name by remember { mutableStateOf(TextFieldValue(startName, selection = TextRange(0, startName.length))) }
    var style by remember { mutableStateOf(startingStyle(editor.newNoteStyle)) }
    var prefs by remember { mutableStateOf(editor.preferences) }
    var cover by remember { mutableStateOf<Int?>(null) }
    var browsing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.err_create_note)

    val shownKey = style.template ?: PageTemplates.NONE
    val libraryVersion = TemplateLibraryUi.version
    val choices = remember(libraryVersion) { TemplateLibrary.all() }
    val template = remember(shownKey, libraryVersion) { if (shownKey == PageTemplates.NONE) null else TemplateLibrary.entry(shownKey)?.template }
    // Unset paper falls back as the canvas does: the Preferences page colour, then the theme's.
    val defaultPaper = (if (prefs.defaultTemplate == "color") prefs.pageColor else null) ?: palette.paper
    val look = TemplateLook.of(style, null, defaultPaper)
    val pageMm = prefs.newPageMm()
    val values = paperTemplateValues(template, style, null, NB_DPI)
    val shownName = name.text.trim().ifEmpty { startName }
    val autoCover = CoverPalette.autoIndex(shownName)

    fun pickTemplate(key: String) {
        style = style.withTemplate(key, PageTemplates.NONE)
    }

    fun create() {
        if (busy) return
        busy = true
        error = null
        // Stamped first: the file is written from the new-note defaults, off the main thread.
        val now = editor.preferences
        val next = now.copy(
            defaultPageSize = prefs.defaultPageSize,
            defaultPageOrientation = prefs.defaultPageOrientation,
            customPageWidthMm = prefs.customPageWidthMm,
            customPageHeightMm = prefs.customPageHeightMm,
        )
        // Only new notes read these, so the open note's canvas needs no refresh.
        if (next != now) editor.applyHomePreferences(next)
        editor.saveNewNoteStyle(style)
        val n = name.text.trim()
        val pickedCover = cover
        scope.launch {
            val uri = onCreate(n)
            if (uri == null) {
                busy = false
                error = failed
                return@launch
            }
            if (pickedCover != null) marks.setCover(uri, pickedCover)
            onCreated(uri)
        }
    }

    InkSheet(
        title = stringResource(if (browsing) R.string.new_notebook_all_templates else R.string.new_notebook_title),
        onDismiss = { if (!busy) onDismiss() },
        subtitle = if (browsing) pluralStringResource(R.plurals.new_notebook_browse_sub, choices.size, choices.size)
        else folderName?.let { stringResource(R.string.new_notebook_in_folder, it) },
        subtitleIcon = if (browsing) null else Ph.folderSimple,
        width = 1120.dp,
        height = 752.dp,
        dismissOnScrim = !busy,
        showClose = false,
        bodyScrolls = false,
        footerMinHeight = 81.dp,
        navigation = if (browsing) {
            { InkIconButton(Ph.caretLeft, stringResource(R.string.new_notebook_back), { browsing = false }, iconSize = 18.dp) }
        } else null,
        footer = {
            if (browsing) {
                BrowseHint(stringResource(R.string.new_notebook_browse_hint))
                Spacer(Modifier.weight(1f))
            } else {
                val err = error
                Text(
                    err ?: stringResource(R.string.new_notebook_remembered),
                    style = InkType.caption.copy(fontWeight = if (err != null) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (err != null) ink.danger else ink.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                InkGhostButton(stringResource(R.string.new_notebook_cancel), { if (!busy) onDismiss() })
                InkBrandButton(stringResource(R.string.new_notebook_create), { create() }, icon = Ph.plus, enabled = !busy)
            }
        },
    ) {
        // Inside the sheet's window: Back while browsing goes back to the options, not out of the sheet.
        BackHandler(enabled = browsing) { browsing = false }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showPreview = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (showPreview) {
                    Column(
                        Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(ink.surface)
                            .drawBehind { drawLine(ink.line2, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx()) }
                            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // The library's own cover renderer, so the notebook looks here as it will on the shelf.
                                val shelfEntry = remember(shownName) { BrowseEntry(shownName, "", isDir = false) }
                                CoverArt(editor, shelfEntry, EntryKind.NOTE, cover, shownName, Modifier.width(132.dp).aspectRatio(COVER_RATIO))
                                Text(stringResource(R.string.new_notebook_on_shelf), style = InkType.caption.copy(fontWeight = FontWeight.SemiBold), color = ink.text2)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(140.dp, 198.dp), contentAlignment = Alignment.BottomCenter) {
                                    val (w, h) = PaperMaths.fit(pageMm, 140f, 198f)
                                    PaperLivePage(template, shownKey, pageMm, look, values, w.dp, h.dp)
                                }
                                Text(stringResource(R.string.new_notebook_first_page, paperSizeCaption(pageMm)), style = InkType.caption.copy(fontWeight = FontWeight.SemiBold), color = ink.text2)
                            }
                        }
                        CoverColours(cover, autoCover, Modifier.padding(top = 22.dp)) { cover = it }
                        Spacer(Modifier.weight(1f))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { drawLine(ink.line2, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                                .padding(top = 16.dp),
                        ) {
                            PaperSizeControls(prefs, { prefs = it }, stringResource(R.string.new_notebook_page_size))
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 36.dp)) {
                    if (browsing) {
                        TemplateBrowser(choices, shownKey, setup = false, NOTEBOOK_TILE_W, pageMm, look, onPick = { pickTemplate(it); browsing = false })
                    } else {
                        Text(stringResource(R.string.new_notebook_name), style = InkType.label, color = ink.text, modifier = Modifier.padding(bottom = 10.dp))
                        val nameLabel = stringResource(R.string.new_notebook_name)
                        InkTextField(
                            name, { name = it },
                            modifier = Modifier.semantics { contentDescription = nameLabel },
                            placeholder = startName,
                            // The keyboard's Done only puts the keyboard away, to leave the paper in view.
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            onKeyEvent = { ev ->
                                when {
                                    ev.type != KeyEventType.KeyDown -> false
                                    ev.key == Key.Enter || ev.key == Key.NumPadEnter -> { create(); true }
                                    ev.key == Key.Escape -> { if (!busy) onDismiss(); true }
                                    else -> false
                                }
                            },
                        )
                        NameHint(startName)
                        PaperSectionHeader(
                            stringResource(R.string.new_notebook_template),
                            Modifier.padding(top = 22.dp, bottom = 10.dp),
                            trailing = { PaperLink(stringResource(R.string.new_notebook_browse_all, choices.size)) { browsing = true } },
                        )
                        val quick = remember(choices, shownKey) { paperQuickTemplates(choices, shownKey) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaperTemplateTile(null, shownKey == PageTemplates.NONE, NOTEBOOK_TILE_W, pageMm, look, onSelect = { pickTemplate(PageTemplates.NONE) })
                            for (e in quick) key(e.key) {
                                PaperTemplateTile(e, e.key == shownKey, NOTEBOOK_TILE_W, pageMm, look, onSelect = { pickTemplate(e.key) })
                            }
                        }
                        PaperTemplateDescription(template, shownKey)
                        PaperCells(notebookCells(template, style, look, defaultPaper) { style = it })
                        // Without the preview pane, the cover colours and the page size move under the options.
                        if (!showPreview) {
                            CoverColours(cover, autoCover, Modifier.padding(top = 24.dp)) { cover = it }
                            Column(
                                Modifier
                                    .padding(top = 20.dp)
                                    .fillMaxWidth()
                                    .drawBehind { drawLine(ink.line2, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                                    .padding(top = 16.dp),
                            ) {
                                PaperSizeControls(prefs, { prefs = it }, stringResource(R.string.new_notebook_page_size))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Paper colour; for a ruled template, line colour, accent colour (when it draws in it) and spacing. */
@Composable
private fun notebookCells(template: Template?, style: PageStyle, look: TemplateLook, defaultPaper: com.xnotes.core.model.Rgba, update: (PageStyle) -> Unit): List<PaperCell> {
    val unsetLabel = stringResource(R.string.new_notebook_default)
    val cells = ArrayList<PaperCell>()
    cells += PaperCell("paper") {
        PaperColourCell(stringResource(R.string.new_notebook_paper), style.pageColor, defaultPaper, unsetLabel, PAPER_PRESETS) { update(style.copy(pageColor = it)) }
    }
    if (template == null) return cells
    val inkAlpha = style.patternColor?.a ?: 255
    cells += PaperCell("line") {
        PaperColourCell(stringResource(R.string.new_notebook_lines), style.patternColor, PaperMaths.over(PageStyle.DEFAULT_PATTERN_COLOR, look.paper), unsetLabel, LINE_PRESETS) {
            update(style.copy(patternColor = it?.copy(a = inkAlpha)))
        }
    }
    if (template.usesAccent) {
        val accentAlpha = style.accentColor?.a ?: 255
        cells += PaperCell("accent") {
            PaperColourCell(stringResource(R.string.new_notebook_accent), style.accentColor, PaperMaths.over(PageStyle.DEFAULT_ACCENT_COLOR, look.paper), unsetLabel, ACCENT_PRESETS) {
                update(style.copy(accentColor = it?.copy(a = accentAlpha)))
            }
        }
    }
    template.spacingParam?.let { sp ->
        cells += PaperCell("spacing:${sp.name}") {
            PaperSpacingCell(sp, style.spacing, null, NB_DPI, onDefault = null) { px -> update(style.copy(spacing = px)) }
        }
    }
    return cells
}

/** The hint under the name (.group-f): what a blank name becomes, and the two keys, as keycaps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameHint(untitled: String) {
    val ink = LocalInk.current
    val style = InkType.hint
    FlowRow(Modifier.padding(start = 2.dp, top = 8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.new_notebook_blank_hint, untitled) + " ", style = style, color = ink.text2)
        NotebookKeycap(stringResource(R.string.new_notebook_key_enter))
        Text(stringResource(R.string.new_notebook_enter_hint), style = style, color = ink.text2)
        NotebookKeycap(stringResource(R.string.new_notebook_key_esc))
        Text(stringResource(R.string.new_notebook_esc_hint), style = style, color = ink.text2)
    }
}

/** A key name in a small ringed box (.pn-kbd). */
@Composable
private fun NotebookKeycap(label: String) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.padding(horizontal = 1.dp).height(18.dp).border(1.dp, ink.line, shape).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(label, style = InkType.tiny.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp), color = ink.text)
    }
}

/**
 * The cover colour (.pn-covc): Automatic first (the colour the name gives it, [auto]), a hairline,
 * then the ten cloths [CoverPalette.offered] in two rows of five, the second under the first. Picks
 * are indices into [CoverPalette.colors]. Kept against the note once it is made, as its menu's Cover
 * colour is.
 */
@Composable
private fun CoverColours(current: Int?, auto: Int, modifier: Modifier, onPick: (Int?) -> Unit) {
    val ink = LocalInk.current
    Column(modifier.fillMaxWidth()) {
        PaperCaption(
            stringResource(R.string.new_notebook_cover),
            if (current == null) stringResource(R.string.new_notebook_cover_auto_named, stringResource(COVER_NAMES[auto])) else stringResource(COVER_NAMES[current]),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            InkSwatch(CoverPalette.colors[auto], current == null, 32.dp) { onPick(null) }
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(ink.line))
            for (i in CoverPalette.offered.take(5)) InkSwatch(CoverPalette.colors[i], current == i, 32.dp) { onPick(i) }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            // Under the first cloth: the Automatic swatch (40), the gap (2), the hairline (9), the gap (2).
            Spacer(Modifier.width(51.dp))
            for (i in CoverPalette.offered.drop(5)) InkSwatch(CoverPalette.colors[i], current == i, 32.dp) { onPick(i) }
        }
        Text(stringResource(R.string.new_notebook_cover_hint), style = InkType.small.copy(fontWeight = FontWeight.Medium, lineHeight = 17.sp), color = ink.text2, modifier = Modifier.padding(top = 10.dp))
    }
}

/** The page a new note is made at under these preferences, in mm. */
private fun Preferences.newPageMm(): Pair<Double, Double> {
    val (w, h) = newPagePixels(NB_DPI)
    return PageSize.pxToMm(w, NB_DPI) to PageSize.pxToMm(h, NB_DPI)
}

/**
 * The saved new-note style to start from, less a template the library no longer has: a new note
 * would fall back from it to Blank anyway, so the sheet says so.
 */
private fun startingStyle(saved: PageStyle): PageStyle {
    val key = saved.template ?: return saved
    if (key == PageTemplates.NONE || TemplateLibrary.entry(key) != null) return saved
    return saved.withTemplate(null, PageTemplates.NONE)
}
