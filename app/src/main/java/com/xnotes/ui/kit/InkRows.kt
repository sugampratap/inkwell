package com.xnotes.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum

/** .plab (B 427): 14/600 in --text. */
private val RowLabel = InkType.buttonSmall

/** .pval (B 428): 14/600 in --text2, tabular. */
private val RowValue = InkType.buttonSmall.tnum()

/** The space between a value row and the slider under it (TO 31–32). */
internal object InkRowSpacing {
    /** .prow + .sl: the slider sits 2 dp under its row. */
    val AboveSlider = 2.dp
}

/**
 * .prow (B 425–428): [label] on the left in 14/600, taking the room left over (up to [labelMaxLines] lines, two by
 * default; one keeps it on a single line, never broken mid-word), and [trailing] on the right, at least [minHeight]
 * tall and [gap] apart. With `minHeight = 34.dp, gap = 12.dp` and an [InkSwitch] it is the tool cards' toggle row
 * .to-tog (TO 27). Make the whole row the toggle with the caller's modifier.
 */
@Composable
internal fun InkValueRow(
    label: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 28.dp,
    gap: Dp = 10.dp,
    labelMaxLines: Int = 2,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(
            label,
            style = RowLabel,
            color = LocalInk.current.text,
            maxLines = labelMaxLines,
            softWrap = labelMaxLines > 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * SLROW's row (TO 422): [label] with its read-only [value] (.pval, 14/600 --text2, tabular), over a slider the caller
 * puts [InkRowSpacing.AboveSlider] below it.
 */
@Composable
internal fun InkReadoutRow(label: String, value: String, modifier: Modifier = Modifier) {
    InkValueRow(label, modifier) {
        Text(value, style = RowValue, color = LocalInk.current.text2, maxLines = 1)
    }
}

/**
 * STEPROW's row (TO 421): [label] with an [InkStepper] showing [value]. It is used over a slider, which the caller puts
 * [InkRowSpacing.AboveSlider] below it: one value, two controls. Work out the steps and the − / + flags with
 * [InkStepGrid] in the unit the row shows. [metrics] is [InkStepperMetrics.Compact] in the table style card (TI 166).
 */
@Composable
internal fun InkStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean = true,
    canPlus: Boolean = true,
    modifier: Modifier = Modifier,
    metrics: InkStepperMetrics = InkStepperMetrics.Regular,
    labelMaxLines: Int = 2,
) {
    InkValueRow(label, modifier, labelMaxLines = labelMaxLines) {
        InkStepper(value, onMinus, onPlus, canMinus = canMinus, canPlus = canPlus, metrics = metrics)
    }
}
