package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.ShapeKind
import com.xnotes.ui.kit.InkReadoutRow
import com.xnotes.ui.kit.InkRowSpacing
import com.xnotes.ui.kit.InkStepGrid
import com.xnotes.ui.kit.InkStepperRow
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded

/*
 * Part 4's pieces of the tool cards, on top of Part 3's InkCard sections and R0's rows and option cards: the 56 dp
 * preview, the stepper and slider pairs, the eight-column colour rows and the rows that bleed to 12 dp of the edge.
 */

/** .to-pv's paper (TO 62): the page's cream in every theme, so a preview is truthful in dark mode. */
internal val ToPaper = Color(0xFFFFFDF7)

/** .to-pv.dark (TO 63): dark paper, for the highlighter's Lighten instead. */
internal val ToDarkPaper = Color(0xFF2A2A2A)

/** The preview's 1 dp inner ring, rgba(0,0,0,.08). */
internal val ToPreviewRing = Color(0x14000000)

/** --sw-ring on a colour dot drawn outside Part 3's ColourDot (the shape hint's inline dot). */
internal val ToSwatchRingLight = Color(0x1A000000)
internal val ToSwatchRingDark = Color(0x2EFFFFFF)

/** The swatch ring's colour (--sw-ring): [ToSwatchRingDark] on a dark surface, else [ToSwatchRingLight]. */
internal fun swatchRing(dark: Boolean): Color = if (dark) ToSwatchRingDark else ToSwatchRingLight

private val NoDarkPaper: () -> Float = { 0f }

/** .to-ink's size, for the empty columns of a short colour row. */
private val ToColourDot = 28.dp

/**
 * .to-pv (TO 62–65): a 56 dp preview on cream paper with a faint inner ring, r12. [dark] (0..1) fades the dark paper in
 * over it; it is read only while drawing, so a crossfade redraws the preview and recomposes nothing. [content] draws on
 * top, usually one `Spacer(Modifier.matchParentSize().then(cachedDrawing))`.
 */
@Composable
internal fun ToolPreview(modifier: Modifier = Modifier, dark: () -> Float = NoDarkPaper, content: @Composable BoxScope.() -> Unit) {
    val corner = cornerOf(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(inkRounded(12.dp))
            .drawBehind {
                drawRect(ToPaper)
                val a = dark()
                if (a > 0f) drawRect(ToDarkPaper, alpha = a.coerceAtMost(1f))
                val w = 1.dp.toPx()
                drawRoundRect(
                    ToPreviewRing,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2f),
                    style = Stroke(w),
                )
            },
        content = content,
    )
}

/** SLROW (TO 422): a label and its value over a slider on the same value, 2 dp apart. */
@Composable
internal fun ToolSliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        InkReadoutRow(label = label, value = value)
        InkSlider(sliderValue, range, Modifier.padding(top = InkRowSpacing.AboveSlider), enabled = enabled, onChange = onChange)
    }
}

/**
 * STEPROW (TO 421): a label with − value + over a slider: one value, two controls. − and + go grey at the ends of
 * [range] (InkStepGrid's rule); the caller decides how far a step goes (0.1 mm or 0.5 mm).
 */
@Composable
internal fun ToolStepperSlider(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onSlide: (Float) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        InkStepperRow(
            label = label,
            value = value,
            onMinus = onMinus,
            onPlus = onPlus,
            canMinus = InkStepGrid.canStepDown(sliderValue, range),
            canPlus = InkStepGrid.canStepUp(sliderValue, range),
        )
        InkSlider(sliderValue, range, Modifier.padding(top = InkRowSpacing.AboveSlider), onChange = onSlide)
    }
}

/**
 * .to-cols (TO 46): eight fixed 28 dp columns spread across the content, 12 dp between rows. A row with fewer dots keeps
 * the left columns (five beams fill columns 1–5), so every row lines up under the first.
 */
@Composable
internal fun ToolColourGrid(dots: List<@Composable () -> Unit>) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in gridRows(dots.size, COLOUR_COLUMNS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (i in row.start until row.start + row.size) dots[i]()
                repeat(row.empty) { Spacer(Modifier.size(ToColourDot)) }
            }
        }
    }
}

/** `margin: 0 -8px` (SC 33): the rows reach [by] past the section's side padding, 12 dp from the card's edge. */
internal fun Modifier.cardRowBleed(by: Dp = 8.dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val wide = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wide)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/** The quick colours' names in the shape card's hint (TO 701). */
@get:StringRes
internal val InkName.labelRes: Int
    get() = when (this) {
        InkName.NAVY -> R.string.to_ink_navy
        InkName.BLUE -> R.string.to_ink_blue
        InkName.RED -> R.string.to_ink_red
        InkName.GREEN -> R.string.to_ink_green
        InkName.AMBER -> R.string.to_ink_amber
    }

/** A shape tile's name (TO 673–678); the kinds only recognition makes take their nearest tool kind's. */
@get:StringRes
internal val ShapeKind.toolLabelRes: Int
    get() = when (this) {
        ShapeKind.LINE, ShapeKind.POLYLINE, ShapeKind.CURVE -> R.string.to_shape_line
        ShapeKind.ARROW -> R.string.to_shape_arrow
        ShapeKind.RECTANGLE -> R.string.to_shape_rectangle
        ShapeKind.ELLIPSE -> R.string.to_shape_ellipse
        ShapeKind.CIRCLE -> R.string.to_shape_circle
        ShapeKind.TRIANGLE, ShapeKind.POLYGON -> R.string.to_shape_triangle
    }
