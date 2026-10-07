package com.xnotes.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded

/** .to-opt / .sc-opt label (TO 36, SC 26): 13/16 SemiBold. */
private val LargeLabel = InkType.tileSmall
private val LargeLabelOn = LargeLabel.copy(fontWeight = FontWeight.ExtraBold)

/** .ti-opt label (TI 169): 12/14 SemiBold. */
private val CompactLabel = InkType.tiny.copy(fontSize = 12.sp, lineHeight = 14.sp)
private val CompactLabelOn = CompactLabel.copy(fontWeight = FontWeight.ExtraBold)

/** .nib label (B 418): 11/13 SemiBold. */
private val TileLabel = InkType.tiny
private val TileLabelOn = TileLabel.copy(fontWeight = FontWeight.ExtraBold)

/** .to-dis (TO 30). */
private const val DisabledAlpha = 0.42f

/** The gap between option cards in a row and between rows (.to-two, .ti-opts, .to-kinds, .to-pats: 8). */
private val OptionGap = 8.dp

/** The three sizes of drawn choice the Round 3 mockups use. */
internal enum class InkOptionCardSize(val height: Dp, val artWidth: Dp, val artHeight: Dp, val gap: Dp) {
    /** .to-opt / .sc-opt (TO 35–40, SC 25–32): eraser modes, lasso shapes. Two to a row. */
    Large(84.dp, 72.dp, 40.dp, 6.dp),

    /** .ti-opt (TI 168–174): table borders. Four to a row. */
    Compact(60.dp, 44.dp, 26.dp, 4.dp),

    /** .nib (B 416–422): shape kinds (three to a row), tape patterns (four). */
    Tile(52.dp, 44.dp, 22.dp, 2.dp),
}

private fun labelStyle(size: InkOptionCardSize, selected: Boolean): TextStyle = when (size) {
    InkOptionCardSize.Large -> if (selected) LargeLabelOn else LargeLabel
    InkOptionCardSize.Compact -> if (selected) CompactLabelOn else CompactLabel
    InkOptionCardSize.Tile -> if (selected) TileLabelOn else TileLabel
}

/**
 * A drawn choice (.to-opt / .sc-opt / .ti-opt / .nib): a picture of the option over its name, r12, a 1 dp --line ring
 * that becomes 2 dp near-black with an ExtraBold name when [selected]. It shrinks to .96 while held and reads as a
 * radio button. Put a set of them in an [InkOptionCardRow] or [InkOptionCardGrid], so they read as one group.
 *
 * [art] draws the picture in an art box of [cardSize]'s `artWidth × artHeight`, in the card's ink ([tint] = --text).
 * **One unit in [art] is one dp:** the scope is pre-scaled by the screen density with its pivot at the top left. So a
 * mockup svg whose viewBox equals its box (72 × 40, 44 × 26, 44 × 22) draws with its own numbers, stroke widths
 * included. `size` inside [art] still reports pixels. Build paths once (`remember`) outside [art]: the lambda runs
 * on every redraw of the card. A picture that changes with the choice (the lasso squiggle's .55 → 1, SC 32) reads
 * [selected] from the caller's scope.
 */
@Composable
internal fun InkOptionCard(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: InkOptionCardSize = InkOptionCardSize.Large,
    enabled: Boolean = true,
    art: DrawScope.(tint: Color) -> Unit,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val corner = cornerOf(12.dp)
    val tint = ink.text
    val ring = if (selected) ink.solid else ink.line
    val ringWidth = if (selected) 2.dp else 1.dp
    val style = labelStyle(cardSize, selected)
    Column(
        modifier
            .height(cardSize.height)
            .pressScale(src, 0.96f)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clip(inkRounded(12.dp))
            .selectable(selected, src, LocalIndication.current, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .drawBehind {
                val w = ringWidth.toPx()
                drawRoundRect(
                    ring,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2f),
                    style = Stroke(w),
                )
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cardSize.gap, Alignment.CenterVertically),
    ) {
        Spacer(
            Modifier
                .size(cardSize.artWidth, cardSize.artHeight)
                .drawBehind { scale(density, density, Offset.Zero) { art(tint) } },
        )
        // Shrinks to fit rather than clipping: a long name has to read whole in a quarter of a card.
        BasicText(
            label,
            style = style.copy(color = tint),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
    }
}

/**
 * One row of option cards (.to-two, .sc-shapes, .ti-opts): full width, 8 dp apart, one radio group. Give each card
 * `Modifier.weight(1f)`.
 */
@Composable
internal fun InkOptionCardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(OptionGap),
        content = content,
    )
}

/**
 * [count] option cards, [columns] to a row, 8 dp apart both ways, as one radio group (.to-kinds: six shape kinds in
 * threes, TO 41). A short last row keeps its cards at the full rows' width. [item] draws card `index`; give it
 * `Modifier.weight(1f)`.
 */
@Composable
internal fun InkOptionCardGrid(
    count: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    item: @Composable RowScope.(index: Int) -> Unit,
) {
    require(columns > 0) { "columns must be positive, was $columns" }
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(OptionGap)) {
        for (start in 0 until count step columns) {
            val end = minOf(start + columns, count)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OptionGap)) {
                for (i in start until end) item(i)
                repeat(start + columns - end) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
