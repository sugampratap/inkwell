package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageEdge
import com.xnotes.core.model.PageMargins
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.template.ParamType
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkDefaultChip
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkPillSegmented
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import kotlin.math.roundToInt

/** Which style level the sheet edits: the current page's own, or the note's. */
private enum class ApplyTo { THIS_PAGE, ALL_PAGES }

/** Where Page setup opens: at the top, or scrolled to Page margins (the Margins button, More › Margins). */
enum class PageSetupStart { TOP, MARGINS }

private val SETUP_W = 1120.dp
private val SETUP_H = 752.dp
private val PREVIEW_PANE_W = 288.dp

/**
 * Page setup (r2_page_notebook Frames 1–2): the paper, the ruling and the margins of the current note
 * or page, edited live. "Apply to" picks the level: This page edits the page's own setup (anything it
 * leaves unset follows All pages), All pages the note's; moving This page → All pages hands the
 * page's setup to the note. Every change goes straight to the [Editor], which persists it but never
 * undoes it, so the page behind follows every tap and drag and the primary is Done. Reset clears the
 * level's template, paper and ruling; Reset margins its margins. "Save as my default" stamps the setup
 * shown onto new notes. Size and orientation set the size of the notes made next: a note keeps the
 * size it was made with. For paged notes only; the canvas keeps its own Styles popup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageSetupSheet(editor: Editor, onImportTemplate: () -> Unit, startAt: PageSetupStart = PageSetupStart.TOP, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val palette = LocalPalette.current
    var docStyle by remember { mutableStateOf(editor.documentStyle) }
    var pageStyle by remember { mutableStateOf(editor.currentPageStyle) }
    var docMargins by remember { mutableStateOf(editor.documentMargins) }
    var pageMargins by remember { mutableStateOf(editor.currentPageMargins) }
    // A page that already has a setup of its own opens on it; otherwise the note's is what to edit.
    var applyTo by remember {
        mutableStateOf(
            if (!editor.currentPageStyle.isEmpty || (startAt == PageSetupStart.MARGINS && !editor.currentPageMargins.isEmpty)) ApplyTo.THIS_PAGE else ApplyTo.ALL_PAGES,
        )
    }
    var prefs by remember { mutableStateOf(editor.preferences) }
    var browsing by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(PageEdge.LEFT) }
    // The dashed guide shows while the margins are being set, and fades once anything else is touched.
    var guideOn by remember { mutableStateOf(startAt == PageSetupStart.MARGINS) }

    val allPages = applyTo == ApplyTo.ALL_PAGES
    val style = if (allPages) docStyle else pageStyle
    val below = if (allPages) null else docStyle

    fun applyStyle(next: PageStyle) {
        guideOn = false
        if (allPages) {
            docStyle = next; editor.setDocumentStyle(next)
        } else {
            pageStyle = next; editor.setCurrentPageStyle(next)
        }
    }

    fun applyMargins(next: PageMargins) {
        guideOn = true
        if (allPages) {
            docMargins = next; editor.setDocumentMargins(next)
        } else {
            pageMargins = next; editor.setCurrentPageMargins(next)
        }
    }

    fun switchApplyTo(to: ApplyTo) {
        if (to == applyTo) return
        if (to == ApplyTo.ALL_PAGES && !pageStyle.isEmpty) {
            val merged = PaperMaths.resolve(pageStyle, docStyle)
            docStyle = merged; editor.setDocumentStyle(merged)
            pageStyle = PageStyle(); editor.setCurrentPageStyle(PageStyle())
        }
        applyTo = to
    }

    fun updatePrefs(next: Preferences) {
        if (next == prefs) return
        guideOn = false
        prefs = next
        editor.applyPreferences(next)
    }

    // What this level shows with no template of its own: the note's, on This page.
    val inherited = below?.template ?: PageTemplates.NONE
    val shownKey = style.template ?: inherited
    val libraryVersion = TemplateLibraryUi.version
    val choices = remember(libraryVersion) { editor.templateChoices() }
    val template = remember(shownKey, libraryVersion) { if (shownKey == PageTemplates.NONE) null else editor.templateFor(shownKey) }
    // The level below whose parameters show through, when they belong to the shown template.
    val lower = below?.takeIf { b -> b.template.let { it == null || PageTemplates.compatible(it, shownKey) } }
    // Unset paper falls back as the canvas does: the Preferences page colour, then the theme's.
    val defaultPaper = (if (prefs.defaultTemplate == "color") prefs.pageColor else null) ?: palette.paper
    val look = TemplateLook.of(style, below, defaultPaper)
    val pageMm = remember { editor.currentPageMm }
    val dpi = editor.documentDpi
    val values = paperTemplateValues(template, style, lower, dpi)
    val margins = PaperMaths.effectiveMargins(allPages, docMargins, pageMargins)
    val shownMm = PaperMaths.withMargins(pageMm, margins)
    val unsetLabel = stringResource(if (below == null) R.string.page_setup_default else R.string.page_setup_as_all_pages)

    val scroll = rememberScrollState()
    val marginsRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(browsing) { if (browsing) scroll.scrollTo(0) }
    LaunchedEffect(Unit) {
        if (startAt == PageSetupStart.MARGINS) {
            withFrameNanos {} // one frame for the column to lay out
            marginsRequester.bringIntoView()
        }
    }

    InkSheet(
        title = stringResource(if (browsing) R.string.page_setup_all_templates else R.string.page_setup_title),
        onDismiss = onDismiss,
        subtitle = if (browsing) {
            pluralStringResource(if (allPages) R.plurals.page_setup_browse_sub_all else R.plurals.page_setup_browse_sub_page, choices.size, choices.size)
        } else {
            stringResource(R.string.page_setup_sub, editor.title, editor.pageIndex + 1, editor.pageCount)
        },
        width = SETUP_W,
        height = SETUP_H,
        showClose = false,
        bodyScrolls = false,
        footerMinHeight = 81.dp,
        navigation = if (browsing) {
            { InkIconButton(Ph.caretLeft, stringResource(R.string.page_setup_back), { browsing = false }, iconSize = 18.dp) }
        } else null,
        headerActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.page_setup_apply_to), style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = ink.text2)
                InkPillSegmented(
                    listOf(ApplyTo.THIS_PAGE, ApplyTo.ALL_PAGES),
                    applyTo,
                    label = { stringResource(if (it == ApplyTo.THIS_PAGE) R.string.page_setup_this_page else R.string.page_setup_all_pages) },
                    onSelect = { switchApplyTo(it) },
                )
            }
            if (!browsing) InkGhostButton(stringResource(R.string.reset), { applyStyle(PageStyle()) }, icon = Ph.arrowCounterClockwise)
        },
        footer = {
            if (browsing) {
                BrowseHint(stringResource(R.string.page_setup_browse_hint))
                Spacer(Modifier.weight(1f))
                InkStrongButton(stringResource(R.string.done), onDismiss)
            } else {
                SetupFooter(editor, style, below, onImportTemplate, onDismiss)
            }
        },
    ) {
        // Inside the sheet's window: Back while browsing goes back to the options, not out of the sheet.
        BackHandler(enabled = browsing) { browsing = false }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On a narrow window the page behind is the preview; the options get the whole card.
            val showPreview = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (showPreview) {
                    Column(
                        Modifier
                            .width(PREVIEW_PANE_W)
                            .fillMaxHeight()
                            .background(ink.surface)
                            .drawBehind { drawLine(ink.line2, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx()) }
                            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(252.dp), contentAlignment = Alignment.Center) {
                            val (w, h) = PaperMaths.fit(shownMm, 176f, 252f)
                            PaperLivePage(template, shownKey, shownMm, look, values, w.dp, h.dp, PaperGuide(edge, PaperMaths.guideFraction(edge, margins), guideOn))
                        }
                        PaperPreviewCaption(stringResource(R.string.page_setup_live_preview, paperSizeCaption(pageMm)) + if (margins.any) stringResource(R.string.page_setup_plus_margins) else "")
                        Spacer(Modifier.weight(1f))
                        ForNextNotes(prefs, ::updatePrefs, Modifier.padding(top = 16.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 36.dp)) {
                        if (browsing) {
                            TemplateBrowser(
                                choices, shownKey, setup = true, SETUP_TILE_W, pageMm, look,
                                onPick = { applyStyle(style.withTemplate(it, inherited)) },
                                onImport = onImportTemplate,
                                onRemove = { editor.removeTemplate(it) },
                                onKeep = { editor.keepNoteTemplate(it) },
                            )
                        } else {
                            PaperSectionHeader(
                                stringResource(R.string.page_setup_template),
                                lead = if (!allPages) {
                                    {
                                        InkDefaultChip(stringResource(R.string.page_setup_default), pageStyle.template == null, { applyStyle(style.withTemplate(null, inherited)) })
                                        if (pageStyle.template == null) Text(stringResource(R.string.page_setup_as_all_pages_hint), style = InkType.meta, color = ink.text2)
                                    }
                                } else null,
                                trailing = { PaperLink(stringResource(R.string.page_setup_browse_all, choices.size)) { browsing = true } },
                            )
                            Spacer(Modifier.height(10.dp))
                            val quick = remember(choices, shownKey) { paperQuickTemplates(choices, shownKey) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PaperTemplateTile(null, shownKey == PageTemplates.NONE, SETUP_TILE_W, pageMm, look, onSelect = { applyStyle(style.withTemplate(PageTemplates.NONE, inherited)) })
                                for (e in quick) key(e.key) {
                                    PaperTemplateTile(e, e.key == shownKey, SETUP_TILE_W, pageMm, look, onSelect = { applyStyle(style.withTemplate(e.key, inherited)) })
                                }
                            }
                            PaperTemplateDescription(template, shownKey)
                            PaperCells(setupCells(template, style, below, lower, look, defaultPaper, unsetLabel, dpi, ::applyStyle))
                            MarginsSection(
                                Modifier.bringIntoViewRequester(marginsRequester),
                                allPages, docMargins, pageMargins, edge,
                                onEdge = { edge = it; guideOn = true },
                                onMargins = ::applyMargins,
                            )
                            // Without the preview pane, the size for new notes moves under the options.
                            if (!showPreview) ForNextNotes(prefs, ::updatePrefs, Modifier.padding(top = 24.dp))
                        }
                    }
                    // A long column scrolls inside the sheet; a soft fade at its foot says there is more below.
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(Color.Transparent, ink.raised))))
                }
            }
        }
    }
}

/**
 * The cells under the template row: paper colour; then, for a ruled template, the line colour, the
 * accent colour (templates that draw in it), spacing and every other parameter (each with its
 * Default chip, which clears the level's own value), and line strength; for Blank, a line saying
 * there is nothing else to set.
 */
@Composable
private fun setupCells(
    template: com.xnotes.core.template.Template?,
    style: PageStyle,
    below: PageStyle?,
    lower: PageStyle?,
    look: TemplateLook,
    defaultPaper: com.xnotes.core.model.Rgba,
    unsetLabel: String,
    dpi: Int,
    applyStyle: (PageStyle) -> Unit,
): List<PaperCell> {
    val ink = LocalInk.current
    val cells = ArrayList<PaperCell>()
    cells += PaperCell("paper") {
        PaperColourCell(stringResource(R.string.page_setup_paper_colour), style.pageColor, below?.pageColor ?: defaultPaper, unsetLabel, PAPER_PRESETS) { applyStyle(style.copy(pageColor = it)) }
    }
    if (template == null) {
        cells += PaperCell("none") {
            Text(stringResource(R.string.page_setup_no_ruling), style = InkType.meta.copy(lineHeight = 19.sp), color = ink.text2, modifier = Modifier.padding(top = 2.dp))
        }
        return cells
    }
    // A picked hue keeps the strength already chosen for the lines (or draws at full strength).
    val inkAlpha = (style.patternColor ?: below?.patternColor)?.a ?: 255
    cells += PaperCell("line") {
        PaperColourCell(
            stringResource(R.string.page_setup_line_colour), style.patternColor,
            PaperMaths.over(below?.patternColor ?: PageStyle.DEFAULT_PATTERN_COLOR, look.paper), unsetLabel, LINE_PRESETS,
        ) { applyStyle(style.copy(patternColor = it?.copy(a = inkAlpha))) }
    }
    if (template.usesAccent) {
        val accentAlpha = (style.accentColor ?: below?.accentColor)?.a ?: 255
        cells += PaperCell("accent") {
            PaperColourCell(
                stringResource(R.string.page_setup_accent_colour), style.accentColor,
                PaperMaths.over(below?.accentColor ?: PageStyle.DEFAULT_ACCENT_COLOR, look.paper), unsetLabel, ACCENT_PRESETS,
            ) { applyStyle(style.copy(accentColor = it?.copy(a = accentAlpha))) }
        }
    }
    template.spacingParam?.let { sp ->
        cells += PaperCell("spacing:${sp.name}") {
            PaperSpacingCell(sp, style.spacing, lower?.spacing, dpi, onDefault = { applyStyle(style.copy(spacing = null)) }) { px -> applyStyle(style.copy(spacing = px)) }
        }
    }
    for (p in template.params) {
        if (p === template.spacingParam) continue
        cells += PaperCell("param:${p.name}") {
            if (p.type == ParamType.COLOR) {
                PaperColourCell(
                    p.label ?: p.name.replaceFirstChar { it.uppercase() },
                    style.colors?.get(p.name),
                    PaperMaths.over(lower?.colors?.get(p.name) ?: p.defaultColor ?: look.ink, look.paper),
                    unsetLabel, emptyList(),
                ) { c ->
                    val rest = style.colors?.minus(p.name) ?: emptyMap()
                    applyStyle(style.copy(colors = (if (c == null) rest else rest + (p.name to c)).takeIf { it.isNotEmpty() }))
                }
            } else {
                PaperParamCell(
                    p, style.params?.get(p.name), lower?.params?.get(p.name),
                    onDefault = { applyStyle(style.copy(params = style.params?.minus(p.name)?.takeIf { it.isNotEmpty() })) },
                ) { v -> applyStyle(style.copy(params = (style.params ?: emptyMap()) + (p.name to v))) }
            }
        }
    }
    cells += PaperCell("strength") {
        val pct = look.ink.a * 100f / 255f
        PaperSliderCell(
            stringResource(R.string.page_setup_line_strength), stringResource(R.string.page_setup_percent, pct.roundToInt()),
            pct.coerceIn(5f, 100f), 5f..100f, isDefault = false, onDefault = null,
        ) { v ->
            val a = (v / 100f * 255f).roundToInt().coerceIn(0, 255)
            if (a != look.ink.a) applyStyle(style.copy(patternColor = look.ink.copy(a = a)))
        }
    }
    return cells
}

/**
 * Page margins (.pn-msec, moved from the Margins popup): extra paper on any edge at the level being
 * edited. Edge picks Left / Top / Right / Bottom; the slider sets 0–100 % of the page's width (left,
 * right) or height (top, bottom); Default clears the edge so it inherits (a page edge follows All
 * pages, an All pages edge falls to none); Reset margins clears the level's four.
 */
@Composable
private fun MarginsSection(
    modifier: Modifier,
    allPages: Boolean,
    docMargins: PageMargins,
    pageMargins: PageMargins,
    edge: PageEdge,
    onEdge: (PageEdge) -> Unit,
    onMargins: (PageMargins) -> Unit,
) {
    val ink = LocalInk.current
    val level = if (allPages) docMargins else pageMargins
    val own = level.edge(edge)
    val pct = PaperMaths.percent(PaperMaths.edgeValue(allPages, docMargins, pageMargins, edge))
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .drawBehind { drawLine(ink.line2, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = 20.dp),
    ) {
        PaperSectionHeader(
            stringResource(R.string.page_setup_margins),
            trailing = { InkGhostButton(stringResource(R.string.page_setup_reset_margins), { onMargins(PageMargins()) }, Modifier.height(32.dp), icon = Ph.arrowCounterClockwise) },
        )
        Text(
            stringResource(R.string.page_setup_margins_note),
            style = InkType.hint,
            color = ink.text2,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp).widthIn(max = 640.dp),
        )
        PaperCells(
            listOf(
                PaperCell("edge") {
                    Column {
                        PaperCaption(stringResource(R.string.page_setup_edge), null)
                        InkSegmented(PaperMaths.EDGE_ORDER, edge, label = { stringResource(it.labelRes) }) { onEdge(it) }
                    }
                },
                PaperCell("value") {
                    Column {
                        PaperCaption(
                            stringResource(R.string.page_setup_edge_value, stringResource(edge.labelRes), pct),
                            if (own == null) stringResource(R.string.page_setup_default_paren) else null,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Whole percents, as the caption shows them: a drag re-lays the note only when the value moves.
                            InkSlider(pct.toFloat(), 0f..100f, Modifier.weight(1f)) { onMargins(level.withEdge(edge, it.roundToInt() / 100.0)) }
                            InkDefaultChip(stringResource(R.string.page_setup_default), own == null, { onMargins(level.withEdge(edge, null)) })
                        }
                    }
                },
            ),
            divided = false,
        )
    }
}

/** "For the notes you create next" (.pn-nn): size and orientation under a hairline, and why they leave this note alone. */
@Composable
private fun ForNextNotes(prefs: Preferences, update: (Preferences) -> Unit, modifier: Modifier) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(ink.line2, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .then(modifier),
    ) {
        Text(stringResource(R.string.page_setup_for_next), style = InkType.label.copy(fontWeight = FontWeight.ExtraBold), color = ink.text, modifier = Modifier.padding(bottom = 12.dp))
        PaperSizeControls(prefs, update, stringResource(R.string.page_setup_size))
        Text(stringResource(R.string.page_setup_size_note_short), style = InkType.small.copy(fontWeight = FontWeight.Medium, lineHeight = 17.sp), color = ink.text2, modifier = Modifier.padding(top = 10.dp))
    }
}

/** The footer while editing (.ms-f): Save as my default (a toggle), Import template, the live hint and Done. */
@Composable
private fun RowScope.SetupFooter(editor: Editor, style: PageStyle, below: PageStyle?, onImportTemplate: () -> Unit, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val effective = PaperMaths.resolve(style, below)
    val saved = !editor.newNoteStyle.isEmpty && editor.newNoteStyle == effective
    InkSecondaryButton(
        stringResource(if (saved) R.string.page_setup_saved_default else R.string.page_setup_save_default),
        { editor.saveNewNoteStyle(if (saved) PageStyle() else effective) },
        icon = if (saved) Ph.check else Ph.bookmarkSimple,
        on = saved,
    )
    InkGhostButton(stringResource(R.string.page_setup_import), onImportTemplate, icon = Ph.fileArrowDown)
    Spacer(Modifier.weight(1f))
    Text(stringResource(R.string.page_setup_live_hint), style = InkType.caption, color = ink.text2, maxLines = 1)
    InkStrongButton(stringResource(R.string.done), onDismiss)
}
