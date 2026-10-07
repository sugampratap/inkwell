package com.xnotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.xnotes.R
import com.xnotes.canvas.PdfColorFilter
import com.xnotes.canvas.ViewOverrides
import com.xnotes.canvas.ViewSettings
import com.xnotes.canvas.ViewingMode
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardFold
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkDialogHost
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkHint
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkOptionCard
import com.xnotes.ui.kit.InkOptionCardGrid
import com.xnotes.ui.kit.InkOptionCardRow
import com.xnotes.ui.kit.InkOptionCardSize
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.InkRowCheck
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkStepper
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** The viewing modes, in the card's order. */
private val VIEW_MODES = listOf(ViewingMode.SINGLE, ViewingMode.DOUBLE, ViewingMode.COVER)

/** The rotations the card offers, clockwise degrees: the four stops the old slider snapped to. */
private val VIEW_ROTATIONS = listOf(0, 90, 180, 270)

/**
 * The View card (the header eye, the toolbar's View item): one set of controls always showing the open note's
 * effective (resolved) view settings; a change writes that field's per-note override — stored app-side like
 * zoom/scroll, never in the file itself. Like [PageSetupSheet], the current values can be saved as the global
 * defaults every note without overrides follows ("Default for all notes"), and Reset drops the note's overrides
 * back onto them.
 */
@Composable
fun ViewMenuPopup(editor: Editor, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val defaults = editor.viewDefaults
    val overrides = editor.viewOverrides
    val vs = editor.viewSettings
    // Same session-sticky rule as the styles popup's "Default for new notes" row.
    var showDefaultRow by remember { mutableStateOf(editor.viewSettings != editor.viewDefaults) }
    // Unchecking "Default for all notes" goes back to the defaults this card opened with.
    val openedDefaults = remember { editor.viewDefaults }

    fun apply(new: ViewOverrides) {
        editor.updateViewOverrides(new)
        if (editor.viewSettings != editor.viewDefaults) showDefaultRow = true
    }
    fun setMode(v: ViewingMode) = apply(overrides.copy(mode = v))
    fun setVerticalScroll(v: Boolean) = apply(overrides.copy(verticalScroll = v))
    fun setContrast(v: Int) = apply(overrides.copy(contrast = v))
    fun setInvert(v: Int) = apply(overrides.copy(invert = v))
    fun setBrightness(v: Int) = apply(overrides.copy(brightness = v))
    fun setSepia(v: Int) = apply(overrides.copy(sepia = v))
    fun setMultiply(v: Rgba) = apply(overrides.copy(multiply = v))
    fun setScreen(v: Rgba) = apply(overrides.copy(screen = v))
    fun setKeepImages(v: Boolean) = apply(overrides.copy(keepImages = v))
    fun setRotation(v: Int) = apply(overrides.copy(rotation = v))
    fun setScrollbar(v: Boolean) = apply(overrides.copy(scrollbar = v))

    ChromeCard(stringResource(R.string.title_view), onDismiss) {
        InkCardSection(first = true) {
            InkCardCaption(stringResource(R.string.caption_viewing_mode))
            InkBoxSegmented(
                options = VIEW_MODES,
                selected = vs.mode,
                label = { m ->
                    stringResource(
                        when (m) {
                            ViewingMode.SINGLE -> R.string.view_single
                            ViewingMode.DOUBLE -> R.string.view_double
                            ViewingMode.COVER -> R.string.view_cover
                        },
                    )
                },
                onSelect = { setMode(it) },
                modifier = Modifier.fillMaxWidth(),
                fill = true,
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.caption_vertical_scrolling), vs.verticalScroll) { setVerticalScroll(it) }
        }
        InkCardSection {
            InkCardCaption(stringResource(R.string.caption_pdf_filters))
            FilterSpinRow(stringResource(R.string.filter_contrast), vs.contrast, 0, 200) { setContrast(it) }
            FilterSpinRow(stringResource(R.string.filter_invert), vs.invert, 0, 100) { setInvert(it) }
            FilterSpinRow(stringResource(R.string.filter_brightness), vs.brightness, 0, 200) { setBrightness(it) }
            FilterSpinRow(stringResource(R.string.filter_sepia), vs.sepia, 0, 200) { setSepia(it) }
            FilterColorRow(stringResource(R.string.filter_multiply), vs.multiply, PdfColorFilter.MULTIPLY_OFF) { setMultiply(it) }
            FilterColorRow(stringResource(R.string.filter_screen), vs.screen, PdfColorFilter.SCREEN_OFF) { setScreen(it) }
            ToggleRow(stringResource(R.string.caption_keep_images), vs.keepImages) { setKeepImages(it) }
        }
        InkCardSection {
            InkCardCaption(stringResource(R.string.view_rotate))
            InkBoxSegmented(
                options = VIEW_ROTATIONS,
                selected = vs.rotation,
                label = { stringResource(R.string.view_degrees, it) },
                onSelect = { setRotation(it) },
                modifier = Modifier.fillMaxWidth(),
                fill = true,
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.caption_scrollbar), vs.scrollbar) { setScrollbar(it) }
        }
        InkCardSection(bottom = 18.dp) {
            if (showDefaultRow && overrides != ViewOverrides()) {
                val asDefault = vs == defaults
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .toggleable(asDefault, role = Role.Checkbox) { on ->
                            editor.updateViewDefaults(if (on) vs else openedDefaults.takeIf { it != vs } ?: ViewSettings())
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InkRowCheck(asDefault)
                    Text(stringResource(R.string.default_for_all_notes), style = InkType.body, color = ink.text, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            InkSecondaryButton(stringResource(R.string.reset), onClick = { apply(ViewOverrides()) }, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** A labelled percentage stepper (−  100%  +, stepping by 5) for the PDF filters. */
@Composable
private fun FilterSpinRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = InkType.buttonSmall, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        InkStepper(
            value = stringResource(R.string.value_percent, value),
            onMinus = { onChange((value - 5).coerceIn(min, max)) },
            onPlus = { onChange((value + 5).coerceIn(min, max)) },
            canMinus = value > min,
            canPlus = value < max,
        )
    }
}

/**
 * A labelled colour row for the two blend filters: the shared picker dot plus an Off chip that writes the blend's
 * identity colour (white for multiply, black for screen), so "no filter" and "blend with the no-op colour" are the
 * same state and the row needs no separate enable flag.
 */
@Composable
private fun FilterColorRow(label: String, value: Rgba, off: Rgba, onChange: (Rgba) -> Unit) {
    val ink = LocalInk.current
    val on = value != off
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = InkType.buttonSmall, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        ColorPickerDot(
            current = value,
            custom = on,
            onPick = onChange,
            dismissOnPick = false,
        ) { d, p -> PageColorGridPopup(value, d, p) }
        ModeChip(stringResource(R.string.off), !on) { onChange(off) }
    }
}

/** The page field: the row type in SemiBold, right-aligned tabular figures, like the counter it answers. */
private val JumpField = InkType.row.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End).tnum()

/**
 * Go to page (the header counter, the toolbar's page counter): type a page number (1-based) and jump to it; Go,
 * or the keyboard's Go or Done, commits and closes. The field opens focused with the current page selected.
 */
@Composable
fun PageJumpPopup(editor: Editor, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val start = "${editor.pageIndex + 1}"
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun go() {
        val n = value.text.toIntOrNull() ?: return
        editor.goToPage(n - 1)
        onDismiss()
    }
    val title = stringResource(R.string.title_go_to_page)
    ChromeCard(title, onDismiss, width = 300.dp) {
        InkCardSection(first = true, bottom = 18.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkTextField(
                    value = value,
                    onValueChange = { v -> if (v.text.length <= 5 && v.text.all(Char::isDigit)) value = v },
                    modifier = Modifier.width(96.dp).semantics { contentDescription = title },
                    textStyle = JumpField,
                    focusRequester = focus,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }, onDone = { go() }),
                )
                Text(
                    stringResource(R.string.page_jump_of, editor.pageCount),
                    style = InkType.counter.tnum(),
                    color = ink.text2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                InkStrongButton(stringResource(R.string.go), onClick = { go() })
            }
        }
    }
}

/**
 * The Zoom card (the toolbar's zoom %): optional MIN/MAX zoom limits. While a limit is on, every zoom path (pinch,
 * buttons, keyboard, fit) clamps to it; its stepper greys out while the switch is off.
 */
@Composable
fun ZoomMenuPopup(editor: Editor, onDismiss: () -> Unit) {
    val base = remember { editor.preferences }
    var minOn by remember { mutableStateOf(base.minZoomEnabled) }
    var minPct by remember { mutableStateOf(base.minZoomPercent) }
    var maxOn by remember { mutableStateOf(base.maxZoomEnabled) }
    var maxPct by remember { mutableStateOf(base.maxZoomPercent) }

    fun emit() = editor.applyPreferences(
        editor.preferences.copy(
            minZoomEnabled = minOn, minZoomPercent = minPct,
            maxZoomEnabled = maxOn, maxZoomPercent = maxPct,
        ),
    )

    ChromeCard(stringResource(R.string.title_zoom), onDismiss) {
        InkCardSection(first = true, bottom = 18.dp) {
            ZoomLimitRow(
                stringResource(R.string.caption_min_zoom), minOn, minPct,
                onToggle = {
                    minOn = it
                    if (minOn && maxOn && minPct > maxPct) minPct = maxPct
                    emit()
                },
                onValue = {
                    minPct = it.coerceIn(Preferences.ZOOM_LIMIT_MIN_PCT, Preferences.ZOOM_LIMIT_MAX_PCT)
                        .coerceAtMost(if (maxOn) maxPct else Preferences.ZOOM_LIMIT_MAX_PCT)
                    emit()
                },
            )
            ZoomLimitRow(
                stringResource(R.string.caption_max_zoom), maxOn, maxPct,
                onToggle = {
                    maxOn = it
                    if (maxOn && minOn && maxPct < minPct) maxPct = minPct
                    emit()
                },
                onValue = {
                    maxPct = it.coerceIn(Preferences.ZOOM_LIMIT_MIN_PCT, Preferences.ZOOM_LIMIT_MAX_PCT)
                        .coerceAtLeast(if (minOn) minPct else Preferences.ZOOM_LIMIT_MIN_PCT)
                    emit()
                },
            )
        }
    }
}

/** One zoom-limit row: label, a percent stepper (stepping by 10, greyed while off), a switch. */
@Composable
private fun ZoomLimitRow(label: String, enabled: Boolean, value: Int, onToggle: (Boolean) -> Unit, onValue: (Int) -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = InkType.buttonSmall,
            color = if (enabled) ink.text else ink.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        InkStepper(
            value = stringResource(R.string.value_percent, value),
            onMinus = { onValue(value - 10) },
            onPlus = { onValue(value + 10) },
            canMinus = enabled,
            canPlus = enabled,
        )
        InkSwitch(enabled, onCheckedChange = onToggle)
    }
}

/**
 * The frame the View, Go to page and Zoom cards share (mockup §7.3): an [InkCard] of [width] in the anchored
 * popover, hung off the button that opened it: [LocalCardAnchor], provided by [CardSlot] in the header and the
 * toolbar, opening and closing with [LocalCardExpanded]. Under a header button (no [LocalPopoverEdge]) it sits 8 dp
 * below, under the toolbar 10 dp below the pill. With no card anchor (a caller outside the hosts) it falls back to
 * the B2 dropdown, so nothing breaks.
 */
@Composable
private fun ChromeCard(title: String, onDismiss: () -> Unit, width: Dp = 340.dp, content: @Composable ColumnScope.() -> Unit) {
    val anchor = LocalCardAnchor.current
    if (anchor != null) {
        val spec = if (LocalPopoverEdge.current == null) PopoverSpecs.HeaderMenu else PopoverSpecs.ToolCard
        InkPopover(expanded = LocalCardExpanded.current, onDismiss = onDismiss, anchor = anchor, spec = spec) {
            // A card opened from inside this one (a filter row's colour) is not this card: it must not hang off this
            // card's button, nor close with it. As ToolCardFrame does.
            CompositionLocalProvider(LocalCardAnchor provides null, LocalCardExpanded provides true) {
                InkCard(title, onClose = onDismiss, width = width, content = content)
            }
        }
    } else {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            Column(Modifier.width(width)) {
                Box(Modifier.padding(start = 22.dp, end = 10.dp)) { PopupTitle(title, onClose = onDismiss) }
                content()
            }
        }
    }
}

/**
 * The eraser's card (TO Frame 2): Whole stroke or Area as drawn cards, each with its hint; the size as the eraser's
 * diameter in mm, stepping 0.5 mm, over a slider (the store keeps the radius); Switch back after erasing; Erase PDF text
 * markups in a note with a PDF; and Same size at any zoom in the fold.
 */
@Composable
fun EraserConfigPopup(editor: ToolPopupHost, onDismiss: () -> Unit) {
    val base = remember { editor.toolConfig(Tool.ERASER) }
    var area by remember { mutableStateOf(base.eraseMode == EraseMode.AREA) }
    var size by remember { mutableStateOf(base.baseWidth.toFloat()) }
    var switchBack by remember { mutableStateOf(base.switchBackAfterErase) }
    var scale by remember { mutableStateOf(base.scale) }
    var markups by remember { mutableStateOf(base.eraseMarkups) }
    var more by remember { mutableStateOf(false) }

    fun emit() = editor.updateToolConfig(
        Tool.ERASER,
        base.copy(
            baseWidth = size.toDouble(),
            eraseMode = if (area) EraseMode.AREA else EraseMode.STROKE,
            switchBackAfterErase = switchBack,
            scale = scale,
            eraseMarkups = markups,
        ),
    )

    val wholeArt = rememberArt(eraserArt(EraseMode.STROKE), OPTION_ART_W, OPTION_ART_H)
    val areaArt = rememberArt(eraserArt(EraseMode.AREA), OPTION_ART_W, OPTION_ART_H)
    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.title_eraser), onClose = onDismiss) {
            InkCardSection(first = true) {
                InkOptionCardRow(Modifier.fillMaxWidth()) {
                    InkOptionCard(
                        selected = !area,
                        label = stringResource(R.string.eraser_stroke),
                        onClick = { area = false; emit() },
                        modifier = Modifier.weight(1f),
                    ) { tint -> drawArt(wholeArt, tint) }
                    InkOptionCard(
                        selected = area,
                        label = stringResource(R.string.eraser_area),
                        onClick = { area = true; emit() },
                        modifier = Modifier.weight(1f),
                    ) { tint -> drawArt(areaArt, tint) }
                }
                InkHint(stringResource(if (area) R.string.to_eraser_hint_area else R.string.to_eraser_hint_stroke))
            }
            InkCardSection {
                ToolStepperSlider(
                    label = stringResource(R.string.caption_size),
                    value = eraserSizeLabel(size),
                    sliderValue = size,
                    range = ERASER_RADIUS_PX,
                    onMinus = { size = stepEraserRadius(size, -1); emit() },
                    onPlus = { size = stepEraserRadius(size, 1); emit() },
                ) { size = it; emit() }
            }
            InkCardSection {
                // Re-arm the previous pen or highlighter once an erase lifts, so a quick fix doesn't strand you in the eraser.
                ToggleRow(stringResource(R.string.to_eraser_switch_back), switchBack, minHeight = 34.dp) { switchBack = it; emit() }
                InkHint(stringResource(R.string.to_eraser_switch_back_hint), top = 2.dp)
                if (editor.hostHasPdf) {
                    ToggleRow(
                        stringResource(R.string.caption_erase_markups),
                        markups,
                        modifier = Modifier.padding(top = 12.dp),
                        minHeight = 34.dp,
                    ) { markups = it; emit() }
                    InkHint(stringResource(R.string.to_eraser_markups_hint), top = 2.dp)
                }
            }
            InkCardFold(expanded = more, onToggle = { more = !more }) {
                // On: the eraser holds a constant on-screen size whatever the zoom (scale off), as the pen card words it.
                ToggleRow(stringResource(R.string.pen_same_size_any_zoom), !scale, minHeight = 34.dp) { scale = !it; emit() }
                InkHint(stringResource(R.string.to_eraser_zoom_hint), top = 2.dp)
            }
        }
    }
}

/**
 * The shape tool's card (TO Frame 3): the six kinds as drawn tiles; a preview of the shape as it will draw, in the
 * toolbar's ink, and that ink's name; thickness in mm with − / + over a slider; Fill and its opacity (dimmed, with the
 * reason, for lines and arrows, which have no inside); Dashed, with dash and gap.
 */
@Composable
fun ShapeConfigPopup(editor: ToolPopupHost, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(editor.hostShapeConfig.shape) }
    var width by remember { mutableStateOf(editor.hostShapeConfig.strokeWidth.toFloat()) }
    var fill by remember { mutableStateOf(editor.hostShapeConfig.fill) }
    var fillOpacity by remember { mutableStateOf((editor.hostShapeConfig.fillAlpha * 100).toFloat()) }
    var glow by remember { mutableStateOf(editor.hostShapeConfig.neon) }
    var glowIntensity by remember { mutableStateOf(ToolConversions.neonStrengthToIntensity(editor.hostShapeConfig.neonStrength).toFloat()) }
    var dashed by remember { mutableStateOf(editor.hostShapeConfig.dashed) }
    var dashLen by remember { mutableStateOf(editor.hostShapeConfig.dashLength.toFloat()) }
    var gapLen by remember { mutableStateOf(editor.hostShapeConfig.dashGap.toFloat()) }

    fun emit() = editor.updateShapeConfig(
        ShapeConfig(
            shape = kind,
            strokeWidth = width.toDouble(),
            fill = fill,
            fillAlpha = (fillOpacity / 100.0).coerceIn(ShapeConfig.FILL_ALPHA_MIN, ShapeConfig.FILL_ALPHA_MAX),
            neon = false,
            neonStrength = ToolConversions.intensityToNeonStrength(glowIntensity.toDouble()),
            dashed = dashed,
            dashLength = dashLen.toDouble(),
            dashGap = gapLen.toDouble(),
        ),
    )

    val kinds = ShapeKind.DRAW_TOOL_KINDS
    val arts = remember { kinds.map { prepareArt(shapeArt(it), TILE_ART_W, TILE_ART_H) } }
    // The shape tool draws in the toolbar's active ink, so the preview and its name follow a swatch change.
    val ink = editor.hostToolbarColors.getOrNull(editor.hostActiveColorIndex) ?: InkPalette.INK
    val widthRange = 1f..20f
    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.title_shape), onClose = onDismiss) {
            InkCardSection(first = true) {
                InkOptionCardGrid(count = kinds.size, columns = 3, modifier = Modifier.fillMaxWidth()) { i ->
                    val k = kinds[i]
                    InkOptionCard(
                        selected = kind == k,
                        label = stringResource(k.toolLabelRes),
                        onClick = { kind = k; emit() },
                        modifier = Modifier.weight(1f),
                        cardSize = InkOptionCardSize.Tile,
                    ) { tint -> drawArt(arts[i], tint) }
                }
            }
            InkCardSection {
                ShapePreview(kind, width, fill, fillOpacity / 100f, dashed, dashLen, gapLen, ink)
                ShapeInkHint(ink)
                ToolStepperSlider(
                    label = stringResource(R.string.pen_thickness),
                    value = widthLabelMm(width),
                    sliderValue = width,
                    range = widthRange,
                    onMinus = { width = stepWidthPx(width, -1, widthRange); emit() },
                    onPlus = { width = stepWidthPx(width, 1, widthRange); emit() },
                    modifier = Modifier.padding(top = 12.dp),
                ) { width = it; emit() }
            }
            InkCardSection {
                // A line or an arrow has no inside: the switch dims and keeps its value for the next closed kind.
                ToggleRow(stringResource(R.string.caption_fill), fill, minHeight = 34.dp, enabled = !fillDimmed(kind)) { fill = it; emit() }
                if (fillOpacityShown(fill, kind)) {
                    val minPct = (ShapeConfig.FILL_ALPHA_MIN * 100).toFloat()
                    ToolSliderRow(
                        stringResource(R.string.to_fill_opacity),
                        "${fillOpacity.roundToInt()}%",
                        fillOpacity,
                        minPct..100f,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { fillOpacity = it; emit() }
                }
                if (fillDimmed(kind)) InkHint(stringResource(R.string.to_fill_open_hint))
            }
            InkCardSection {
                ToggleRow(stringResource(R.string.caption_dashed), dashed, minHeight = 34.dp) { dashed = it; emit() }
                if (dashed) {
                    // .to-pair: dash and gap side by side, 20 dp apart.
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ToolSliderRow(stringResource(R.string.caption_dash), widthLabelMm(dashLen), dashLen, 2f..40f, modifier = Modifier.weight(1f)) {
                            dashLen = it; emit()
                        }
                        ToolSliderRow(stringResource(R.string.caption_gap), widthLabelMm(gapLen), gapLen, 2f..40f, modifier = Modifier.weight(1f)) {
                            gapLen = it; emit()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shape as it will draw (TO 702–710), in the preview's 300×56 viewBox: mm × 3.4 dp wide, round caps and joins,
 * dashed with the gap widened by the line's width, filled only when Fill is on and the kind is closed; an arrow's
 * head is never dashed. Paths and strokes are built once per tuning in the draw cache.
 */
@Composable
private fun ShapePreview(kind: ShapeKind, widthPx: Float, fill: Boolean, fillAlpha: Float, dashed: Boolean, dashPx: Float, gapPx: Float, ink: Rgba) {
    val colour = ink.toComposeColor()
    val art = remember(kind, widthPx, fill, fillAlpha, dashed, dashPx, gapPx, colour) {
        Modifier.drawWithCache {
            val k = size.width / PREVIEW_W
            val outline = PathParser().parsePathString(shapePreviewPath(kind)).toPath()
            val head = if (kind == ShapeKind.ARROW) PathParser().parsePathString(ARROW_HEAD_D).toPath() else null
            val w = shapePreviewWidth(widthPx)
            val dashes = if (dashed) PathEffect.dashPathEffect(shapePreviewDash(dashPx, gapPx, w)) else null
            val line = DrawStroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dashes)
            val plain = DrawStroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val filled = fillOpacityShown(fill, kind)
            val fillInk = colour.copy(alpha = colour.alpha * fillAlpha)
            onDrawBehind {
                scale(k, k, pivot = Offset.Zero) {
                    if (filled) drawPath(outline, fillInk)
                    drawPath(outline, colour, style = line)
                    if (head != null) drawPath(head, colour, style = plain)
                }
            }
        }
    }
    ToolPreview {
        Spacer(Modifier.matchParentSize().then(art))
    }
}

private const val INK_DOT = "inkDot"

/** Stands for the dot and the name in `to_shape_ink_hint`'s %1$s, wherever a language puts it. */
private const val INK_MARK = "￼"

/** "Draws in the toolbar's ink, ● Navy." (TO 687, 701): a 10 dp dot (.to-dot) and the ink's name, or its hex. */
@Composable
private fun ShapeInkHint(ink: Rgba) {
    val tokens = LocalInk.current
    val name = inkName(ink)?.let { stringResource(it.labelRes) } ?: inkHex(ink)
    val full = stringResource(R.string.to_shape_ink_hint, INK_MARK)
    val at = full.indexOf(INK_MARK)
    val text = buildAnnotatedString {
        if (at < 0) {
            append(full)
            append(' ')
            append(name)
        } else {
            append(full.substring(0, at))
            appendInlineContent(INK_DOT, "●")
            append(' ')
            append(name)
            append(full.substring(at + INK_MARK.length))
        }
    }
    val dot = ink.toComposeColor()
    val ring = if (tokens.isDark) ToSwatchRingDark else ToSwatchRingLight
    val inline = mapOf(
        INK_DOT to InlineTextContent(Placeholder(13.sp, 10.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 1.dp, end = 2.dp)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        drawCircle(dot, r)
                        drawCircle(ring, r - 0.5.dp.toPx(), style = DrawStroke(1.dp.toPx()))
                    },
            )
        },
    )
    Text(text, style = InkType.hint, color = tokens.text2, inlineContent = inline, modifier = Modifier.padding(top = 8.dp))
}

/**
 * The PDF text markup card (TO 645–667): what a drag over the PDF's text does, as five drawn tiles (select it, or mark
 * it), and for a highlight how deep it goes. A mark takes the toolbar's ink.
 */
@Composable
fun MarkupToolPopup(editor: ToolPopupHost, onDismiss: () -> Unit) {
    val base = remember { editor.toolConfig(Tool.MARKUP) }
    var mode by remember { mutableStateOf(base.markupMode) }
    var intensity by remember { mutableStateOf((base.markupIntensity * 100).toFloat()) }

    fun emit() = editor.updateToolConfig(Tool.MARKUP, base.copy(markupMode = mode, markupIntensity = intensity / 100.0))

    val modes = MarkupMode.entries
    val arts = remember { modes.map { prepareArt(markupArt(it), TILE_ART_W, TILE_ART_H) } }
    val measurer = rememberTextMeasurer()
    val sample = stringResource(R.string.to_markup_art_text)
    val wordSize = with(LocalDensity.current) { 10.5.dp.toSp() }
    val word = remember(measurer, sample, wordSize) { measurer.measure(sample, MarkupArtWord.copy(fontSize = wordSize)) }
    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.tool_markup), onClose = onDismiss) {
            InkCardSection(first = true) {
                InkCardCaption(stringResource(R.string.to_markup_caption))
                InkOptionCardGrid(count = modes.size, columns = 3, modifier = Modifier.fillMaxWidth()) { i ->
                    val m = modes[i]
                    InkOptionCard(
                        selected = mode == m,
                        label = stringResource(m.labelRes),
                        onClick = { mode = m; emit() },
                        modifier = Modifier.weight(1f),
                        cardSize = InkOptionCardSize.Tile,
                    ) { tint -> drawArt(arts[i], tint, word) }
                }
            }
            if (markupIntensityShown(mode)) {
                InkCardSection {
                    val min = (ToolConfig.MARKUP_INTENSITY_MIN * 100).toFloat()
                    ToolSliderRow(stringResource(R.string.caption_intensity), "${intensity.roundToInt()}%", intensity, min..100f) {
                        intensity = it; emit()
                    }
                    InkHint(stringResource(R.string.to_markup_intensity_hint))
                }
            }
        }
    }
}

/** The markup tiles' word (TO 646): Plus Jakarta Sans Bold; its 10.5 dp size is set where it is measured. */
private val MarkupArtWord = InkType.label.copy(fontWeight = FontWeight.Bold)

/**
 * Colour switcher (SC Frame 4): the toolbar swatch's picker, under the swatch (40 dp before its centre, 10 dp past the
 * bar), recolouring it live. When it closes, by any path, the swatch's colour joins Recent. Hosted by CardSlot, so it
 * plays its exit.
 */
@Composable
fun ColorSwitcherPopup(host: ToolPopupHost, index: Int, onDismiss: () -> Unit) {
    val expanded = LocalCardExpanded.current
    LaunchedEffect(expanded) { if (!expanded) host.rememberSwatchColor(index) }
    ColorPickerPopup(
        initial = host.hostToolbarColors.getOrNull(index),
        recents = host.hostRecentColors,
        onDismiss = onDismiss,
        onPick = { host.setSwatchColor(index, it) },
        expanded = expanded,
        anchor = LocalCardAnchor.current,
        place = PickerPlace.UnderSwatch,
    )
}

@Composable
internal fun PopupTitle(text: String, onClose: (() -> Unit)? = null) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = InkType.sheetTitle, color = ink.text, modifier = Modifier.weight(1f))
        if (onClose != null) InkIconButton(Ph.x, stringResource(R.string.kit_close), onClose, iconSize = 18.dp)
    }
}

/** The least a row's tap may be: 48 dp tall, the platform's minimum touch target. */
private val TOGGLE_TOUCH_MIN = 48.dp

/**
 * How far a [ToggleRow] of [minHeight] takes taps above and below itself so its target is [TOGGLE_TOUCH_MIN] tall.
 * Only the compact rows (under 44 dp) reach: they always sit by a hint or 10 dp or more of air, while the 44 dp rows
 * of the older cards can sit flush on another control, which a reach would take taps from.
 */
internal fun toggleReach(minHeight: Dp): Dp =
    if (minHeight < 44.dp) ((TOGGLE_TOUCH_MIN - minHeight) / 2).coerceAtLeast(0.dp) else 0.dp

/**
 * Lays this out [reach] taller above and below than the space it keeps: the content is measured with the extra
 * height and placed up by [reach], so whatever follows (a padding, then the row) keeps its place while the
 * modifiers before it see the larger box.
 */
private fun Modifier.reachPast(reach: Dp): Modifier = if (reach <= 0.dp) this else layout { measurable, constraints ->
    val r = reach.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = 2 * r))
    layout(placeable.width, placeable.height - 2 * r) { placeable.place(0, -r) }
}

/**
 * [reachPast] on both axes: lays this out [horizontal] wider each side and [vertical] taller above and below than the
 * space it keeps. A small control puts its click before this and a matching padding after it, so it takes a larger
 * tap with nothing moved; the caller keeps the reach clear of its neighbours.
 */
internal fun Modifier.reachPast(horizontal: Dp, vertical: Dp): Modifier =
    if (horizontal <= 0.dp && vertical <= 0.dp) this else layout { measurable, constraints ->
        val h = horizontal.coerceAtLeast(0.dp).roundToPx()
        val v = vertical.coerceAtLeast(0.dp).roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * h, vertical = 2 * v))
        layout((placeable.width - 2 * h).coerceAtLeast(0), (placeable.height - 2 * v).coerceAtLeast(0)) { placeable.place(-h, -v) }
    }

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val reach = toggleReach(minHeight)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            // A 34 dp row still takes a 48 dp tap: the toggle reaches 7 dp past each edge, nothing moves, and the
            // press shows on the row itself.
            .reachPast(reach)
            .toggleable(checked, src, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = reach)
            .heightIn(min = minHeight)
            // .to-dis (TO 30): a row that cannot apply here dims and takes no taps; its value is kept.
            .alpha(if (enabled) 1f else 0.42f)
            .clip(MaterialTheme.shapes.small)
            .indication(src, LocalIndication.current),
    ) {
        Text(label, style = InkType.body.copy(fontWeight = FontWeight.SemiBold), color = ink.text, modifier = Modifier.weight(1f))
        InkSwitch(checked, onCheckedChange = null)
    }
}

/**
 * The app's dropdown menu: material's, drawn as B2's popover card. A raised surface with the
 * theme's medium corners (16dp at Rounded) and a hairline (line2) border, so it reads against
 * same-tone surfaces (the backstage, OLED black) where a shadow alone vanishes, and the MENU lift
 * with no tonal tint (POP's deeper shadow would be clipped at the popup window's edge). Shadows
 * material3's composable for every same-package caller that doesn't import material's directly.
 */
@Composable
internal fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = LocalMenuOffset.current,
        properties = properties,
        shape = MaterialTheme.shapes.medium,
        containerColor = ink.raised,
        tonalElevation = 0.dp,
        shadowElevation = InkElevation.MENU.shadow,
        border = BorderStroke(1.dp, ink.line2),
    ) {
        // A menu opened from inside this one hangs off its own row, not beside the rail.
        CompositionLocalProvider(LocalMenuOffset provides DpOffset.Zero) { content() }
    }
}

/**
 * The app's dialog for the callers that still pass Material slots (rename note, table size, markup
 * note): a B2 card on [InkDialogHost], so its shadow is never cut and a scrim tap dismisses it. The
 * title takes the sheet-title type, the text B2's body type in the secondary colour, and the buttons
 * sit at the end, dismiss before confirm.
 */
@Composable
internal fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = LocalInk.current.raised,
) {
    val ink = LocalInk.current
    InkDialogHost(onDismissRequest) {
        Column(
            modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .inkSurface(shape, InkElevation.SHEET, containerColor)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp),
        ) {
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides ink.text) { ProvideTextStyle(InkType.sheetTitle) { title() } }
                Spacer(Modifier.height(16.dp))
            }
            if (text != null) {
                // As material's: the text gives way first when the window is short (a keyboard is up).
                Box(Modifier.weight(1f, fill = false)) {
                    CompositionLocalProvider(LocalContentColor provides ink.text2) { ProvideTextStyle(InkType.body) { text() } }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/**
 * A text-label chip for one of several choices (e.g. a viewing mode, a page edge): B2's [InkChip],
 * announced as a radio button. A one-shot action takes an [ActionChip] instead.
 */
@Composable
internal fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    InkChip(label, selected, onClick)
}

/**
 * A chip that does something once (Reset, Save, Go): B2's [InkChip] announced as a button, with no
 * selected state. [strong] gives it the chosen chip's 2dp ring and bold label, for the action a
 * popover exists for.
 */
@Composable
internal fun ActionChip(label: String, strong: Boolean = false, onClick: () -> Unit) {
    InkChip(label, strong, onClick, role = Role.Button)
}
