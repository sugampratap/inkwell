package com.xnotes.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/** .ti-act > span (TI 32): 11.5/14 SemiBold. */
private val PillLabel = InkType.tiny.copy(fontSize = 11.5.sp, lineHeight = 14.sp)

/** .ti-act.on > span and .solid > span (TI 36, 40): the same at 700. */
private val PillLabelOn = PillLabel.copy(fontWeight = FontWeight.Bold)

/** r999: the pill and every action in it are fully round, whatever the Corners setting. */
private val PillShape = RoundedCornerShape(percent = 50)

/** .tx-act[disabled] (TX 108). */
private const val LabelledDisabledAlpha = 0.32f

/** .tx-fb[disabled] (TX 74). */
private const val IconDisabledAlpha = 0.3f

/** The dark themes' second ring round the ink dot (TI 43): white at .55. */
private val DarkDotHalo = Color.White.copy(alpha = 0.55f)

/**
 * A bar's own look for its labelled [InkPillAction]s, where it differs from the kit's .ti-act (the selection bar's
 * Fluent look, selbar-fluent .btn): the [iconSize], the [label] style (also while on or solid), the label in --text
 * rather than --text2 when [labelInText], the [gap] from icon to label and the action's [shape].
 */
internal class InkPillLook(
    val iconSize: Dp,
    val label: TextStyle,
    val labelInText: Boolean,
    val gap: Dp,
    val shape: Shape,
)

/**
 * The shared floating pill's sizes, read off .ti-bar / .ti-act / .ti-bsep / .ti-dot (TI 28–45), .tx-sbar / .tx-act
 * (TX 98–108) and .au-bar / .au-act / .au-bsep (AU 92–101), which are the same pill. Plain values and maths, so
 * placement code and tests can size a pill before it is laid out.
 */
internal object InkPillMetrics {
    /** The pill's height (64). */
    val Height = 64.dp

    /** Padding at each end (0 6). The text format pill uses 10 (TX 68). */
    val Padding = 6.dp

    /** A labelled action (.ti-act): 58 × 52, r26. */
    val ActionWidth = 58.dp
    val ActionHeight = 52.dp

    /** .wide: Duplicate, Arrange (TI 37, 578). */
    val ActionWide = 66.dp

    /** .xw: Add column, Cell colour, Edit table, Fit columns (TI 38, 578). */
    val ActionExtraWide = 76.dp

    /** An icon-only action (.tx-fb, TX 69): 44 high, at least 44 wide, 10 a side. */
    val IconActionSize = 44.dp
    val IconActionPadding = 10.dp

    /** Every action's icon (22). */
    val IconSize = 22.dp

    /** Icon to label (gap 3). */
    val LabelGap = 3.dp

    /** The divider (.ti-bsep): 1 × 28 in --line, 6 either side. The format pill's .tx-fsep has 7 (TX 75). */
    val DividerWidth = 1.dp
    val DividerHeight = 28.dp
    val DividerMargin = 6.dp

    /** The ink dot (.ti-dot, TI 41): 10 across, its top 9 down, its left edge 5 right of the action's centre. */
    val DotSize = 10.dp
    val DotTop = 9.dp
    val DotOffset = 5.dp

    /** The dot's ring in the pill's own fill (2), and the dark themes' halo out to 3.5 (TI 41–44). */
    val DotRing = 2.dp
    val DotHalo = 3.5.dp

    /**
     * The width of a pill holding [groups] of actions (each width in dp), with one divider between neighbouring
     * non-empty groups. The selection, table, audio and edit bars have no [gap]; the format pill passes its own
     * [padding], [dividerMargin] and [gap] (10, 7, 2). An empty group adds nothing, not even a divider.
     */
    fun width(
        groups: List<List<Dp>>,
        padding: Dp = Padding,
        dividerMargin: Dp = DividerMargin,
        gap: Dp = 0.dp,
    ): Dp {
        val filled = groups.filter { it.isNotEmpty() }
        if (filled.isEmpty()) return padding * 2
        val actions = filled.flatten()
        val dividers = filled.size - 1
        val children = actions.size + dividers
        return padding * 2 +
            actions.fold(0.dp) { sum, w -> sum + w } +
            (DividerWidth + dividerMargin * 2) * dividers +
            gap * (children - 1)
    }
}

/**
 * The shared floating pill (.ti-bar / .tx-sbar / .au-bar, TI 28; TX 98; AU 92): 64 dp, fully round, --raised with one
 * soft shadow and a --line2 ring, its children in a row centred on its height. It holds [InkPillAction]s split by
 * [InkPillDivider]s, and it is the text edit bar, the text box's style pill, the table and media bars, and
 * later Part 5's selection bar.
 *
 * - [elevation]: [InkElevation.FLOAT] (--sh-float) over the canvas. Pass [InkElevation.MENU] when the pill is hosted in a
 *   `Popup`, whose window clips a deeper shadow.
 * - [padding] / [gap]: 6 / 0 for every labelled bar; the text format pill is 10 / 2 (TX 68).
 * - [scroll]: when given, the row scrolls sideways inside the pill, as the format pill must in a narrow pane (TX 386).
 *
 * The pill does not animate. A caller that fades or rises it does so in `graphicsLayer`, and snaps while
 * [LocalPenDown] is true. Taps that land between the actions stop at the pill and never reach the page under it.
 */
@Composable
internal fun InkPill(
    modifier: Modifier = Modifier,
    elevation: InkElevation = InkElevation.FLOAT,
    padding: Dp = InkPillMetrics.Padding,
    gap: Dp = 0.dp,
    scroll: ScrollState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(InkPillMetrics.Height)
            .inkSurface(PillShape, elevation)
            .semantics { isTraversalGroup = true }
            .stopTapsAtPill()
            .then(if (scroll != null) Modifier.horizontalScroll(scroll) else Modifier)
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * Makes the pill a hit target of its own. Without a pointer handler, a touch between two actions falls through to
 * the canvas under the pill and starts a stroke. It only watches (on the final pass) and consumes nothing, so the
 * actions and a [horizontalScroll] still get every event.
 */
private fun Modifier.stopTapsAtPill(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Final)
    }
}

/**
 * One action on an [InkPill].
 *
 * **With a [label]** (.ti-act / .tx-act / .au-act, TI 29–40): a [width] × 52 pill (58, or [InkPillMetrics.ActionWide] /
 * [InkPillMetrics.ActionExtraWide]) with the 22 dp [icon] over the 11.5/14 label in --text2.
 * - [on]: the lit fill (`selOnRaised`) and a Bold label in --text. Use it for a toggle that is on, or while the
 *   action's own menu is open. It is announced as selected.
 * - [solid]: near-black, with icon and label in --on-solid (Done). It takes no press tint.
 * - [danger]: red icon and label, never a red fill. No Round 3 mockup uses it; it is kept for the selection bar.
 * - [dot]: the Cell colour's shade dot (TI 41–44), 10 dp at the icon's top right, ringed in the action's fill. Null hides it.
 * - The label is the action's spoken name, unless [contentDescription] is given: then that is spoken instead (a
 *   short label that needs its object, "Delete" → "Delete table"). [stateDescription] adds a state, e.g. the dot's
 *   colour name.
 *
 * **With `label = null`** (.tx-fb, TX 69–74): a 44 dp round icon button, for the text format pill's toggles.
 * [on] is its on / open state. [contentDescription] is its spoken name and must be given. [width] and [dot] are
 * ignored.
 *
 * Press: the app's press tint and a .94 scale over 120 ms (`pressScale`, layer only). Disabled: .32 (labelled) or .3.
 */
@Composable
internal fun InkPillAction(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    on: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    width: Dp = InkPillMetrics.ActionWidth,
    dot: Color? = null,
    contentDescription: String? = null,
    stateDescription: String? = null,
    look: InkPillLook? = null,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val lit = ink.selOnRaised
    val shape = look?.shape ?: PillShape
    val iconSize = look?.iconSize ?: InkPillMetrics.IconSize
    val fg = when {
        solid -> ink.onSolid
        danger -> ink.danger
        else -> ink.text
    }
    val fill = when {
        solid -> ink.solid
        on -> lit
        else -> Color.Transparent
    }
    val sized = if (label != null) {
        modifier.size(width, InkPillMetrics.ActionHeight)
    } else {
        modifier.height(InkPillMetrics.IconActionSize).widthIn(min = InkPillMetrics.IconActionSize)
    }
    val base = sized
        .pressScale(src, 0.94f)
        .alpha(if (enabled) 1f else if (label != null) LabelledDisabledAlpha else IconDisabledAlpha)
        .clip(shape)
        .background(fill)
        .then(if (on) Modifier.semantics { selected = true } else Modifier)
        .then(if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier)
        .then(if (label != null && contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
        .clickable(src, if (solid) null else LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
    if (label == null) {
        Box(base.padding(horizontal = InkPillMetrics.IconActionPadding), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(iconSize))
        }
    } else {
        val labelColour = when {
            solid -> ink.onSolid
            danger -> ink.danger
            on -> ink.text
            look?.labelInText == true -> ink.text
            else -> ink.text2
        }
        Column(
            base.drawWithContent {
                drawContent()
                if (dot != null) {
                    val r = InkPillMetrics.DotSize.toPx() / 2f
                    val centre = Offset(size.width / 2f + InkPillMetrics.DotOffset.toPx() + r, InkPillMetrics.DotTop.toPx() + r)
                    if (ink.isDark) drawCircle(DarkDotHalo, r + InkPillMetrics.DotHalo.toPx(), centre)
                    drawCircle(if (solid) ink.solid else if (on) lit else ink.raised, r + InkPillMetrics.DotRing.toPx(), centre)
                    drawCircle(dot, r, centre)
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(look?.gap ?: InkPillMetrics.LabelGap, Alignment.CenterVertically),
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize))
            Text(
                label,
                style = look?.label ?: if (on || solid) PillLabelOn else PillLabel,
                color = labelColour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A given contentDescription replaces the label as the spoken name, so the label is not read as well.
                modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
            )
        }
    }
}

/**
 * The hairline between groups on an [InkPill] (.ti-bsep, TI 45): 1 × 28 in --line, [margin] either side (7 on the
 * format pill, TX 75). The selection bar's is [height] 34 (selbar-fluent .sep).
 */
@Composable
internal fun InkPillDivider(margin: Dp = InkPillMetrics.DividerMargin, height: Dp = InkPillMetrics.DividerHeight) {
    Box(
        Modifier
            .padding(horizontal = margin)
            .size(InkPillMetrics.DividerWidth, height)
            .background(LocalInk.current.line),
    )
}
