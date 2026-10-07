package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.infinite.CanvasBackground
import com.xnotes.core.model.PagePattern
import com.xnotes.core.model.PageStyle
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** The pattern chips, in today's order. */
private val STYLE_PATTERNS = listOf(
    PagePattern.NONE to R.string.none,
    PagePattern.LINES to R.string.pattern_lines,
    PagePattern.DOTS to R.string.pattern_dots,
    PagePattern.GRID to R.string.pattern_grid,
)

/**
 * The infinite canvas's background styles on the shared card: pattern, spacing, pattern colour and its opacity, and
 * paper colour.
 *
 * Unlike the paged [PageSetupSheet] there is no inheritance to express, because a canvas has no page level under it,
 * so every control sets a real value rather than choosing between "default" and an override. Everything here is per
 * canvas and saved with it, apart from "Default for new canvases", which stamps the current background onto every
 * canvas made from then on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasStylesPopup(editor: InfiniteEditor, onDismiss: () -> Unit) {
    var background by remember { mutableStateOf(editor.document.background) }
    // The row shows once the background differs from the saved new-canvas default and stays for the card's session;
    // a stock background hides it.
    var showNewCanvasRow by remember { mutableStateOf(editor.document.background != editor.newCanvasBackground) }

    fun apply(next: CanvasBackground) {
        background = next
        editor.setBackground(next)
        if (next != editor.newCanvasBackground) showNewCanvasRow = true
    }

    val patterned = background.pattern != PagePattern.NONE
    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.title_styles), onClose = onDismiss) {
            InkCardSection(first = true) {
                InkCardCaption(stringResource(R.string.caption_pattern))
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((p, label) in STYLE_PATTERNS) {
                        ModeChip(stringResource(label), background.pattern == p) { apply(background.copy(pattern = p)) }
                    }
                }
                ToolSliderRow(
                    stringResource(R.string.caption_spacing),
                    "%.0f".format(background.clampedSpacing),
                    background.clampedSpacing.toFloat(),
                    PageStyle.MIN_SPACING.toFloat()..PageStyle.MAX_SPACING.toFloat(),
                    modifier = Modifier.padding(top = 14.dp),
                    enabled = patterned,
                ) { apply(background.copy(spacing = it.toDouble())) }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.caption_pattern_colour))
                ColorPickerDot(
                    background.patternColor.copy(a = 255), // the hue at full strength; opacity is its own control
                    custom = true,
                    onPick = { apply(background.copy(patternColor = it.copy(a = background.patternColor.a))) },
                    dismissOnPick = false,
                ) { d, p -> PageColorGridPopup(background.patternColor.copy(a = 255), d, p) }
                val pct = background.patternColor.a / 255f * 100f
                ToolSliderRow(
                    stringResource(R.string.caption_opacity),
                    "${pct.roundToInt()}%",
                    pct,
                    5f..100f,
                    modifier = Modifier.padding(top = 12.dp),
                    enabled = patterned,
                ) { next ->
                    val alpha = (next / 100f * 255f).roundToInt().coerceIn(0, 255)
                    apply(background.copy(patternColor = background.patternColor.copy(a = alpha)))
                }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.caption_paper))
                // Theme, the five presets and the custom dot are one row on the 340 dp card (about 290 dp at 6 dp apart;
                // at 8 the dot wrapped onto a line of its own). It still wraps rather than clip at a large font scale.
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeChip(stringResource(R.string.paper_theme), background.paperColor == null) { apply(background.copy(paperColor = null)) }
                    val words = rememberExplorerWords()
                    pageColorPresets.forEach { c ->
                        InkSwatch(
                            c.toComposeColor(),
                            background.paperColor == c,
                            22.dp,
                            contentDescription = swatchName(words, c), // R4 #12: "Blue, #1E88E5"
                        ) { apply(background.copy(paperColor = c)) }
                    }
                    ColorPickerDot(
                        background.paperColor,
                        custom = background.paperColor != null && background.paperColor !in pageColorPresets,
                        onPick = { apply(background.copy(paperColor = it)) },
                        dismissOnPick = false,
                    ) { d, p -> PageColorGridPopup(background.paperColor, d, p) }
                }
            }
            InkCardSection(bottom = 18.dp) {
                if (showNewCanvasRow && background != CanvasBackground()) {
                    ToggleRow(
                        stringResource(R.string.default_for_new_canvases),
                        background == editor.newCanvasBackground,
                        minHeight = 34.dp,
                    ) { on -> editor.saveNewCanvasBackground(if (on) background else null) }
                    Spacer(Modifier.height(10.dp))
                }
                InkSecondaryButton(stringResource(R.string.reset), onClick = { apply(CanvasBackground()) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
