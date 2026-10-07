package com.xnotes.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkTokens
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded

/** Lit grey for rows and buttons on a raised surface: `sel`, or `press` when they are equal (OLED, round 2 default #5). */
internal val InkTokens.selOnRaised: Color
    get() = if (sel == raised) press else sel

/**
 * True inside a ToolCardFrame, which already draws the card's one surface and scrolls it: an [InkCard] there draws
 * neither (one surface per card; two nested vertical scrolls would crash). InkPopover resets it to false.
 */
internal val LocalInCardFrame = staticCompositionLocalOf { false }

/** A 1 dp hairline across the top, drawn behind (no layout). */
private fun Modifier.topRule(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}

/**
 * 340 dp (or [width]) card (mockup §7.3, TO 20-33): raised, r24, 1 dp line2, InkElevation.MENU; header 52 dp
 * (padding 10/10/2/22, sheetTitle, 44 dp × with Ph.x at 18) when [title] != null; body scrolls. Hosted straight in an
 * [InkPopover] (whose max height bounds it, so the body's scroll engages) it draws its own surface; inside a
 * ToolCardFrame ([LocalInCardFrame]) the frame draws the surface and scrolls, so the card draws and scrolls nothing.
 */
@Composable
internal fun InkCard(
    title: String?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    width: Dp = 340.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    val framed = LocalInCardFrame.current
    Column(modifier.width(width).then(if (framed) Modifier else Modifier.inkSurface(inkRounded(24.dp), InkElevation.MENU))) {
        if (title != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 22.dp, top = 10.dp, end = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = InkType.sheetTitle,
                    color = ink.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onClose != null) {
                    // 44 dp target in a 40 dp row, overflowing it evenly as the mockup's .xb does.
                    InkIconButton(
                        Ph.x,
                        stringResource(R.string.kit_close),
                        onClose,
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        iconSize = 18.dp,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().then(if (framed) Modifier else Modifier.verticalScroll(rememberScrollState())),
            content = content,
        )
    }
}

/** A section: padding 12/20/14 with a 1 dp line2 rule on top; [first] drops the rule and uses 6 dp on top. */
@Composable
internal fun InkCardSection(first: Boolean = false, bottom: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.topRule(ink.line2))
            .padding(start = 20.dp, end = 20.dp, top = if (first) 6.dp else 12.dp, bottom = bottom),
        content = content,
    )
}

/**
 * "More options" / "Fewer options" fold (TO 52-59): 40 dp toggle (14 SemiBold, caret-down 16 text2 rotating 180° on
 * InkMotion.glide() in graphicsLayer), body shown/hidden with no height animation.
 */
@Composable
internal fun InkCardFold(expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    val turn by animateFloatAsState(if (expanded) 180f else 0f, InkMotion.glide(), label = "foldCaret")
    Column(
        Modifier
            .fillMaxWidth()
            .topRule(ink.line2)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(inkRounded(12.dp))
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(if (expanded) R.string.kit_fewer_options else R.string.kit_more_options),
                style = InkType.buttonSmall,
                color = ink.text,
            )
            // The turn is read in the layer only: the caret rotates without recomposing the card.
            Icon(Ph.caretDown, null, tint = ink.text2, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn })
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp), content = content)
        }
    }
}

/** The card's foot (.pp-f): padding 2/20/20, for the strong Save button. */
@Composable
internal fun InkCardFooter(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp), content = content)
}

/** A hint under a control (.to-hint): InkType.hint, text2, 8 dp above. */
@Composable
internal fun InkHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = InkType.hint, color = LocalInk.current.text2, modifier = modifier.padding(top = 8.dp))
}

/** A section caption (.to-cap): 13/18 Bold text, then an optional value in 13/18 Medium text2 on its baseline; 10 dp below. */
@Composable
internal fun InkCardCaption(label: String, value: String? = null) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = InkType.label, color = ink.text, modifier = Modifier.alignByBaseline())
        if (value != null) Text(value, style = InkType.meta, color = ink.text2, modifier = Modifier.alignByBaseline())
    }
}

/**
 * A hint with the mockup's other spacings (TO 24–26): [top] 2 dp directly under a toggle; 0 with [bottom] 12 dp for a
 * hint above the controls (.to-hint.top); 4 and 6 under the lasso's toggle (SC 526). [InkHint] (text, modifier) keeps
 * the 8 dp it has under a control.
 */
@Composable
internal fun InkHint(text: String, top: Dp, bottom: Dp = 0.dp, modifier: Modifier = Modifier) {
    Text(text, style = InkType.hint, color = LocalInk.current.text2, modifier = modifier.padding(top = top, bottom = bottom))
}
