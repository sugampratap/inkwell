package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.text.ListKind
import com.xnotes.core.text.ParaAlign
import com.xnotes.core.text.Paragraph
import com.xnotes.core.tools.Tool
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillDivider
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverEdge
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.popoverEdge
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.toComposeColor

private val GlyphAType = InkType.title.copy(fontSize = 18.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.36).sp) // .tx-ga
private val HighlightAType = InkType.buttonSmall.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp) // .tx-gh, 14/800

/** The highlight picker's first colour (today's). */
private val HIGHLIGHT_START = Rgba(255, 235, 59)

/** One menu off the pill at a time (TX 845, closeMenus). */
private enum class PillMenu { FONT, HEADING, LANGUAGE, MORE }

/** One picker off the pill at a time. */
private enum class PillPicker { TEXT, HIGHLIGHT }

// Menus rise 10 dp above the pill (its PopoverEdge), placed along it from their button (TX 845-879).
/** The font list: x = the well's start − 6 (TX 860). */
private val FontMenuSpec = PopoverSpecDp(lead = 6.dp, gap = 10.dp, margin = 8.dp)

/** Heading and the other button menus: x = button centre − 60, i.e. 38 before a 44 dp button (TX 852). */
private val ButtonMenuSpec = PopoverSpecDp(lead = 38.dp, gap = 10.dp, margin = 8.dp)

/** Code language: x = button.x − 40 (TX 871). */
private val LanguageMenuSpec = PopoverSpecDp(lead = 40.dp, gap = 10.dp, margin = 8.dp)

/** More: end-aligned, x = button.right − w + 12 (TX 879). */
private val MoreMenuSpec = PopoverSpecDp(lead = 12.dp, gap = 10.dp, margin = 8.dp, endAligned = true)

/**
 * The floating format pill (r3_text Frame 2), shown while the Text tool is armed and no text box is open. It floats
 * over the canvas's bottom edge (20 dp up, 10 dp above the keyboard or a bottom toolbar) rather than taking a strip
 * of it, so it tells the flow controller how much to keep the caret clear of. Its menus hang above it; while one is
 * up the edit bar steps aside. Inside a table cell the block controls are off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.TextFormatBar(editor: Editor) {
    if (editor.tool != Tool.TEXT || editor.editingField != null) return
    editor.flowSelTick // recompose whenever the caret, selection or pending style moves
    editor.contentVersion
    val density = LocalDensity.current
    val cover = LocalToolbarCover.current
    val gap = formatPillGap(WindowInsets.isImeVisible, cover.calculateBottomPadding().value)
    val clearance = formatPillClearance(gap)

    // The caret keeps clear of the pill: written when the gap changes, read only by the flow controller.
    val flowText = editor.flowText
    val clearancePx = with(density) { clearance.dp.toPx().toDouble() }
    SideEffect {
        if (flowText.bottomClearancePx != clearancePx) {
            flowText.bottomClearancePx = clearancePx
            flowText.ensureCaretVisible()
        }
    }
    DisposableEffect(flowText) { onDispose { flowText.bottomClearancePx = 0.0 } }

    val style = editor.flowCaretStyle()
    val para = editor.flowCaretParagraph()
    val state = textFormatState(
        style = style,
        list = para?.list,
        headingLevel = para?.headingLevel ?: 0,
        codeLang = para?.codeLang,
        align = para?.align,
        indent = para?.indent ?: 0,
        inCell = para?.table != null,
        sessionActive = editor.flowEditingActive,
    )

    var menu by remember { mutableStateOf<PillMenu?>(null) }
    var picker by remember { mutableStateOf<PillPicker?>(null) }
    var tableDialog by remember { mutableStateOf(false) }
    val hub = editor.textChrome
    val busy = menu != null || picker != null
    SideEffect { hub.busy = busy }
    DisposableEffect(hub) { onDispose { hub.busy = false } }

    val edge = remember { PopoverEdge().apply { prefer = PopoverSide.ABOVE } }
    val fontAnchor = remember { PopoverAnchor() }
    val headingAnchor = remember { PopoverAnchor() }
    val codeAnchor = remember { PopoverAnchor() }
    val moreAnchor = remember { PopoverAnchor() }
    val close: () -> Unit = { menu = null }

    BoxWithConstraints(Modifier.matchParentSize()) {
        // Menus over the pill may grow up to 10 dp under the toolbar (TX 860-866).
        val room = roomAbove(
            anchorTop = maxHeight.value - clearance,
            topLimit = (cover.calculateTopPadding() + 10.dp).value,
            gap = 10f,
        ).coerceAtLeast(120f).dp
        CompositionLocalProvider(LocalPopoverEdge provides edge) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .absolutePadding(
                        left = cover.calculateLeftPadding(LayoutDirection.Ltr) + 8.dp,
                        right = cover.calculateRightPadding(LayoutDirection.Ltr) + 8.dp,
                        bottom = gap.dp,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                InkPill(Modifier.popoverEdge(edge), padding = 10.dp, gap = 2.dp, scroll = rememberScrollState()) {
                    // Font and size, in their wells.
                    Box {
                        FontWell(style.face ?: editor.flowDefaultFace(), open = menu == PillMenu.FONT, anchor = fontAnchor, onClick = { menu = PillMenu.FONT })
                        InkPopover(expanded = menu == PillMenu.FONT, onDismiss = close, anchor = fontAnchor, spec = FontMenuSpec) {
                            FontListMenu(
                                current = style.face,
                                withDefault = true,
                                defaultFace = editor.flowDefaultFace(),
                                maxHeight = room,
                                onPick = {
                                    editor.flowSetCharFace(it)
                                    menu = null
                                },
                            )
                        }
                    }
                    SizeWell(
                        style.sizePt ?: editor.flowDefaultSizePt(),
                        onMinus = { editor.flowAdjustSize(-1.0) },
                        onPlus = { editor.flowAdjustSize(1.0) },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    InkPillDivider(margin = 7.dp)

                    InkPillAction(icon = Ph.textB, label = null, onClick = { editor.flowToggleBold() }, on = state.bold, contentDescription = stringResource(R.string.bold))
                    InkPillAction(icon = Ph.textItalic, label = null, onClick = { editor.flowToggleItalic() }, on = state.italic, contentDescription = stringResource(R.string.italic))
                    InkPillAction(icon = Ph.textUnderline, label = null, onClick = { editor.flowToggleUnderline() }, on = state.underline, contentDescription = stringResource(R.string.underline))
                    InkPillAction(icon = Ph.textStrikethrough, label = null, onClick = { editor.flowToggleStrike() }, on = state.strike, contentDescription = stringResource(R.string.strikethrough))
                    InkPillDivider(margin = 7.dp)

                    // Text colour and highlight, each behind its picker seam (Part 5 brings the wide picker).
                    val shownColour = style.color ?: editor.flowDefaultColor()
                    Box {
                        FormatButton(stringResource(R.string.text_colour), onClick = { picker = PillPicker.TEXT }, on = picker == PillPicker.TEXT) {
                            TextColourGlyph(shownColour)
                        }
                        if (picker == PillPicker.TEXT) {
                            FormatTextColourPicker(
                                initial = shownColour,
                                recents = editor.recentColors,
                                // The selection as the picker opens: the edit menu's anchor is gone after any caret move.
                                avoid = remember { editor.flowSelectionViewportRect() ?: editor.flowContextMenu },
                                onDismiss = { picker = null },
                                onPick = {
                                    editor.flowSetCharColor(it)
                                    picker = null
                                },
                            )
                        }
                    }
                    val highlight = style.highlight
                    Box {
                        FormatButton(
                            stringResource(if (highlight != null) R.string.text_remove_highlight else R.string.text_highlight),
                            onClick = { if (highlight != null) editor.flowSetCharHighlight(null) else picker = PillPicker.HIGHLIGHT },
                            on = picker == PillPicker.HIGHLIGHT,
                        ) {
                            HighlightGlyph(highlight)
                        }
                        if (picker == PillPicker.HIGHLIGHT) {
                            FormatHighlightPicker(
                                initial = HIGHLIGHT_START,
                                recents = editor.recentColors,
                                avoid = remember { editor.flowSelectionViewportRect() ?: editor.flowContextMenu },
                                onDismiss = { picker = null },
                                onPick = {
                                    editor.flowSetCharHighlight(it)
                                    picker = null
                                },
                            )
                        }
                    }
                    InkPillDivider(margin = 7.dp)

                    // Blocks: heading, the three lists, code.
                    Box {
                        InkPillAction(
                            icon = barHeadingIcon(state.headingLevel),
                            label = null,
                            onClick = { menu = PillMenu.HEADING },
                            on = state.headingLevel > 0 || menu == PillMenu.HEADING,
                            enabled = state.headingEnabled,
                            modifier = Modifier.popoverAnchor(headingAnchor),
                            contentDescription = stringResource(R.string.heading),
                        )
                        InkPopover(expanded = menu == PillMenu.HEADING, onDismiss = close, anchor = headingAnchor, spec = ButtonMenuSpec) {
                            HeadingMenu(state.headingLevel, editor.markdownInput, room) { n ->
                                editor.flowSetHeading(n)
                                menu = null
                            }
                        }
                    }
                    InkPillAction(
                        icon = Ph.listChecks, label = null, onClick = { editor.flowToggleList(ListKind.CHECK) },
                        on = state.list == ListKind.CHECK, enabled = state.listsEnabled, contentDescription = stringResource(R.string.text_checklist),
                    )
                    InkPillAction(
                        icon = Ph.listBullets, label = null, onClick = { editor.flowToggleList(ListKind.BULLET) },
                        on = state.list == ListKind.BULLET, enabled = state.listsEnabled, contentDescription = stringResource(R.string.bullet_list),
                    )
                    InkPillAction(
                        icon = Ph.listNumbers, label = null, onClick = { editor.flowToggleList(ListKind.ORDERED) },
                        on = state.list == ListKind.ORDERED, enabled = state.listsEnabled, contentDescription = stringResource(R.string.text_numbered_list),
                    )
                    Box {
                        val ink = LocalInk.current
                        FormatButton(
                            stringResource(R.string.code_block),
                            onClick = { editor.flowToggleCode() },
                            modifier = Modifier.popoverAnchor(codeAnchor),
                            on = state.codeOn || menu == PillMenu.LANGUAGE, // lit while its language menu is open, like the heading
                            enabled = state.codeEnabled,
                            onLongClick = { menu = PillMenu.LANGUAGE },
                        ) {
                            Icon(Ph.code, null, tint = ink.text, modifier = Modifier.size(22.dp))
                            state.codeLang?.let { Text(CodeLanguages.tag(it), style = codeType(13.sp, FontWeight.Bold, (-0.26).sp), color = ink.text, maxLines = 1) }
                        }
                        InkPopover(expanded = menu == PillMenu.LANGUAGE, onDismiss = close, anchor = codeAnchor, spec = LanguageMenuSpec) {
                            LanguageMenu(
                                current = CodeLanguages.menuCurrent(state.codeLang, editor.lastCodeLanguage()),
                                choices = editor.codeLanguageChoices(),
                                room = room,
                            ) { token ->
                                editor.flowSetCodeLanguage(token)
                                menu = null
                            }
                        }
                    }
                    InkPillDivider(margin = 7.dp)

                    // Alignment steps; More holds the rest.
                    InkPillAction(
                        icon = alignIcon(state.align), label = null, onClick = { editor.flowCycleAlign() },
                        on = state.alignOn, contentDescription = stringResource(R.string.alignment),
                    )
                    Box {
                        InkPillAction(
                            icon = Ph.dotsThree,
                            label = null,
                            onClick = { menu = PillMenu.MORE },
                            on = menu == PillMenu.MORE,
                            modifier = Modifier.popoverAnchor(moreAnchor),
                            contentDescription = stringResource(R.string.text_more),
                        )
                        InkPopover(expanded = menu == PillMenu.MORE, onDismiss = close, anchor = moreAnchor, spec = MoreMenuSpec) {
                            MoreMenu(
                                editor = editor,
                                state = state,
                                room = room,
                                onLanguage = { menu = PillMenu.LANGUAGE },
                                onTable = {
                                    menu = null
                                    tableDialog = true
                                },
                                onDone = close,
                            )
                        }
                    }
                }
            }
        }
    }
    if (tableDialog) TableDialog(editor, null) { tableDialog = false }
}

/**
 * How much of the canvas's bottom edge the format pill holds now (0 dp when it is hidden): its height and gap.
 * Part 7's player sits 10 dp above this and its toasts above both (round 3 defaults, shared row 5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun formatPillReserve(editor: Editor): Dp {
    if (editor.tool != Tool.TEXT || editor.editingField != null) return 0.dp
    return formatPillClearance(formatPillGap(WindowInsets.isImeVisible, LocalToolbarCover.current.calculateBottomPadding().value)).dp
}

/** Heading level menu (TX 735-738): Body text and Heading 1–6, the current one lit with its icon in Fill, H1–H4 keycaps. */
@Composable
private fun HeadingMenu(level: Int, markdownOn: Boolean, room: Dp, onPick: (Int) -> Unit) {
    TextChoiceSurface(maxHeight = room) {
        for (n in 0..Paragraph.MAX_HEADING) {
            val hint = headingHint(n, markdownOn)
            TextChoiceRow(
                selected = n == level,
                label = if (n == 0) stringResource(R.string.body_text) else stringResource(R.string.heading_n, n),
                onClick = { onPick(n) },
                icon = headingIcon(n, filled = n == level),
                trailing = if (hint != null) { { Keycap(hint) } } else null,
            )
        }
    }
}

/** Code language menu (TX 739-741): a header, then each language's proper name with its tag. */
@Composable
private fun LanguageMenu(current: String, choices: List<String>, room: Dp, onPick: (String) -> Unit) {
    val ink = LocalInk.current
    val plain = stringResource(R.string.text_plain_text)
    TextChoiceSurface(maxHeight = room) {
        TextMenuHeader(stringResource(R.string.text_code_language))
        for (token in choices) {
            TextChoiceRow(
                selected = token == current,
                label = CodeLanguages.name(token) ?: plain,
                onClick = { onPick(token) },
                trailing = { Text(CodeLanguages.tag(token), style = codeType(12.sp, FontWeight.SemiBold), color = ink.text2, maxLines = 1) },
            )
        }
    }
}

/** More (TX 742-752, D9): Indent, Outdent | Equation (checked in math), Insert table… | Code language ›. */
@Composable
private fun MoreMenu(editor: Editor, state: TextFormatState, room: Dp, onLanguage: () -> Unit, onTable: () -> Unit, onDone: () -> Unit) {
    val plain = stringResource(R.string.text_plain_text)
    val current = CodeLanguages.menuCurrent(state.codeLang, editor.lastCodeLanguage())
    TextMenuSurface(minWidth = 240.dp, maxHeight = room) {
        TextMenuRow(Ph.textIndent, stringResource(R.string.indent), onClick = { editor.flowIndent(1); onDone() }, enabled = state.indentEnabled)
        TextMenuRow(Ph.textOutdent, stringResource(R.string.outdent), onClick = { editor.flowIndent(-1); onDone() }, enabled = state.outdentEnabled)
        InkMenuDivider()
        TextMenuRow(
            Ph.function,
            stringResource(R.string.equation),
            onClick = { editor.flowToggleMath(); onDone() },
            enabled = state.equationEnabled,
            sub = stringResource(R.string.text_equation_hint),
            checked = state.mathOn,
        )
        TextMenuRow(Ph.table, stringResource(R.string.text_insert_table), onClick = onTable, enabled = state.tableEnabled)
        InkMenuDivider()
        TextMenuRow(
            Ph.code,
            stringResource(R.string.text_code_language),
            onClick = onLanguage,
            enabled = state.languageEnabled,
            value = CodeLanguages.name(current) ?: plain,
        )
    }
}

/** Text colour (.tx-ga, TX 89-91): an "A" over an 18 × 4 bar in the colour, with the swatch ring (1.5 dp white 50 % on dark). */
@Composable
private fun TextColourGlyph(colour: Rgba) {
    val ink = LocalInk.current
    val dark = ink.isDark
    val fill = colour.toComposeColor()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("A", style = GlyphAType, color = ink.text)
        Spacer(
            Modifier
                .size(18.dp, 4.dp)
                .drawBehind {
                    val r = 2.dp.toPx()
                    drawRoundRect(fill, cornerRadius = CornerRadius(r))
                    if (dark) {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            Color.White.copy(alpha = 0.5f),
                            topLeft = Offset(-w / 2f, -w / 2f),
                            size = Size(size.width + w, size.height + w),
                            cornerRadius = CornerRadius(r + w / 2f),
                            style = Stroke(w),
                        )
                    } else {
                        val w = 1.dp.toPx()
                        drawRoundRect(
                            Color.Black.copy(alpha = 0.10f),
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius((r - w / 2f).coerceAtLeast(0f)),
                            style = Stroke(w),
                        )
                    }
                },
        )
    }
}

/** Highlight (.tx-gh, TX 92-93): a 26 dp r8 tile, outlined when off; filled with the highlight and ringed when on. */
@Composable
private fun HighlightGlyph(colour: Rgba?) {
    val ink = LocalInk.current
    val dark = ink.isDark
    val line3 = ink.line3
    Box(
        Modifier
            .size(26.dp)
            .drawBehind {
                val r = 8.dp.toPx()
                if (colour == null) {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(line3, topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r - w / 2f), style = Stroke(w))
                } else {
                    drawRoundRect(colour.toComposeColor(), cornerRadius = CornerRadius(r))
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        swatchRing(dark),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(r - w / 2f),
                        style = Stroke(w),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val letter = when {
            colour == null -> ink.text
            darkInkOn(colour) -> Color(0xFF1C1C1C)
            else -> Color.White
        }
        Text("A", style = HighlightAType, color = letter)
    }
}

/** The heading button's icon: `text-h` for body text, else the level's. */
private fun barHeadingIcon(level: Int): ImageVector = if (level == 0) Ph.textH else headingIcon(level, filled = false)

/** A heading level's icon (TX 1290), in Fill when chosen. */
private fun headingIcon(level: Int, filled: Boolean): ImageVector = when (level) {
    0 -> if (filled) Ph.paragraphFill else Ph.paragraph
    1 -> if (filled) Ph.textHOneFill else Ph.textHOne
    2 -> if (filled) Ph.textHTwoFill else Ph.textHTwo
    3 -> if (filled) Ph.textHThreeFill else Ph.textHThree
    4 -> if (filled) Ph.textHFourFill else Ph.textHFour
    5 -> if (filled) Ph.textHFiveFill else Ph.textHFive
    else -> if (filled) Ph.textHSixFill else Ph.textHSix
}

private fun alignIcon(align: ParaAlign): ImageVector = when (align) {
    ParaAlign.LEFT -> Ph.textAlignLeft
    ParaAlign.CENTER -> Ph.textAlignCenter
    ParaAlign.RIGHT -> Ph.textAlignRight
    ParaAlign.JUSTIFY -> Ph.textAlignJustify
}
