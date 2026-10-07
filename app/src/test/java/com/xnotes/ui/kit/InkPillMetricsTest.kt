package com.xnotes.ui.kit

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pill's width maths (TI 28–45): 6 dp a side, actions edge to edge, and a 1 dp divider with 6 dp either side
 * between groups. Task 6 adds every Round 3 bar's real width.
 */
class InkPillMetricsTest {

    private fun w(
        groups: List<List<Dp>>,
        padding: Dp = InkPillMetrics.Padding,
        dividerMargin: Dp = InkPillMetrics.DividerMargin,
        gap: Dp = 0.dp,
    ): Float = InkPillMetrics.width(groups, padding, dividerMargin, gap).value

    @Test
    fun anEmptyPillIsItsPadding() {
        assertEquals(12f, w(emptyList()), 0.001f)
    }

    @Test
    fun oneGroupHasNoDivider() {
        assertEquals(12f + 116f, w(listOf(listOf(58.dp, 58.dp))), 0.001f)
    }

    @Test
    fun eachBreakBetweenGroupsAddsADividerAndItsMargins() {
        // 12 padding + 3 × 58 + 2 × (1 + 6 + 6)
        assertEquals(212f, w(listOf(listOf(58.dp), listOf(58.dp), listOf(58.dp))), 0.001f)
    }

    @Test
    fun anEmptyGroupAddsNoDivider() {
        // A group whose actions are all hidden (e.g. no Paste) must not leave two dividers side by side.
        assertEquals(12f + 116f + 13f, w(listOf(listOf(58.dp), emptyList(), listOf(58.dp))), 0.001f)
    }

    @Test
    fun theGapCountsBetweenEveryChildIncludingDividers() {
        // The format pill (TX 68, 75): 10 a side, gap 2, dividers 7 either side. 3 buttons + 1 divider = 4 children, 3 gaps.
        val width = w(listOf(listOf(44.dp, 44.dp), listOf(44.dp)), padding = 10.dp, dividerMargin = 7.dp, gap = 2.dp)
        assertEquals(20f + 132f + 15f + 6f, width, 0.001f)
    }

    private val a = InkPillMetrics.ActionWidth
    private val wide = InkPillMetrics.ActionWide
    private val xw = InkPillMetrics.ActionExtraWide

    @Test
    fun theTextEditBar() {
        // Part 6, TX 1027–1031: Cut, Copy, Paste, Delete in one group.
        assertEquals(12f + 4 * 58f, w(listOf(listOf(a, a, a, a))), 0.001f)
    }

    @Test
    fun theTableTypingBar() {
        // Part 7, TI 868: [Insert, Delete, Header, Cell colour (76)] | [Done].
        assertEquals(12f + 3 * 58f + 76f + 13f + 58f, w(listOf(listOf(a, a, a, xw), listOf(a))), 0.001f)
    }

    @Test
    fun theFlowTableBar() {
        // Part 7, TI 1039: [Edit table (76), Fit columns (76), Style, Delete].
        assertEquals(12f + 2 * 76f + 2 * 58f, w(listOf(listOf(xw, xw, a, a))), 0.001f)
    }

    @Test
    fun theSelectedTablePill() {
        // TI 867: [Edit] | [Add row, Add column (76), Header] | [Cut, Copy, Paste, Duplicate (66), Delete] | [Arrange (66)] | [More].
        val groups = listOf(listOf(a), listOf(a, xw, a), listOf(a, a, a, wide, a), listOf(wide), listOf(a))
        assertEquals(12f + 672f + 4 * 13f, w(groups), 0.001f)
    }
}
