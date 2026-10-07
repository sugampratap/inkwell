package com.xnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.pal.FontFace
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import java.util.WeakHashMap
import kotlin.math.roundToInt

// The text chrome's shared pieces (r3_text.html): menus and choice lists off the bars, keycaps, the pill's wells and
// glyph buttons, and the flags the format pill and the edit bar share. Placement and state logic is in TextLogic.kt.

private val MenuRowType = InkType.row.copy(fontSize = 14.5.sp) // .tx-menu .m-row
private val MenuSubType = InkType.small.copy(fontWeight = FontWeight.Medium) // .tx-msub small, 12/500
private val MenuValueType = InkType.chip.copy(fontWeight = FontWeight.Medium) // .gv, 13.5/500
private val ChoiceType = InkType.row.copy(fontSize = 14.5.sp) // .tx-ch, 14.5/500
private val ChoiceOnType = ChoiceType.copy(fontWeight = FontWeight.Bold)
private val HeaderType = InkType.small.copy(fontWeight = FontWeight.Bold) // .tx-mh, 12/16 700
private val WellType = InkType.row.copy(fontSize = 16.sp, lineHeight = 20.sp) // .tx-ff, 16/500
private val SizeValueType = InkType.button.tnum() // .tx-fs b, 15/700

/** Per editor: what its format pill and edit bar share. Composition reads and writes it on the main thread only. */
@Stable
internal class TextChromeHub {
    /** A menu or picker off the format pill is up: the edit bar steps aside (round 3 defaults, Part 6 row 6). */
    var busy by mutableStateOf(false)
}

private val hubs = WeakHashMap<Editor, TextChromeHub>()

/** This editor's shared text-chrome flags. */
internal val Editor.textChrome: TextChromeHub
    get() = hubs.getOrPut(this) { TextChromeHub() }

/** The code face of the chrome's keycaps, keywords and tags (defaults row 5): the bundled JetBrains Mono. */
internal val CODE_FACE = FontFace("JetBrains Mono")

/** A code-type style (.tx-md, .tx-kw, .tx-arg, .tx-tag). */
internal fun codeType(size: TextUnit, weight: FontWeight, tracking: TextUnit = 0.sp, line: TextUnit = TextUnit.Unspecified): TextStyle =
    InkType.meta.copy(fontFamily = CODE_FACE.toComposeFamily(), fontSize = size, fontWeight = weight, letterSpacing = tracking, lineHeight = line)

/** A 1 dp hairline across the top, drawn behind (no layout). */
internal fun Modifier.textTopRule(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}

/** The swatch ring (--sw-ring) inside a circle of [radius] at the centre: 1 dp, black 10 % on light, white 18 % on dark. */
internal fun DrawScope.drawSwatchRing(dark: Boolean, radius: Float) {
    drawCircle(
        swatchRing(dark),
        radius - 0.5.dp.toPx(),
        style = Stroke(1.dp.toPx()),
    )
}

/** A menu off a bar (.menu.tx-menu, TX 113): r16, 8 dp top and bottom, at least [minWidth]; scrolls inside [maxHeight]. */
@Composable
internal fun TextMenuSurface(minWidth: Dp, maxHeight: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(min = minWidth)
            .width(IntrinsicSize.Max)
            .heightIn(max = maxHeight)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        content = content,
    )
}

/**
 * A menu row (.tx-menu .m-row): 44 dp (52 with a [sub] line), 21 dp icon, 14.5 sp; .38 when disabled. [value] is the
 * trailing current value with a caret-right (.gv); [checked] shows a trailing check (D9's Equation in math).
 */
@Composable
internal fun TextMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    sub: String? = null,
    value: String? = null,
    checked: Boolean = false,
) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (sub != null) 52.dp else 44.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (checked) Modifier.semantics { selected = true } else Modifier)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MenuRowType, color = ink.text, maxLines = 1)
            if (sub != null) Text(sub, style = MenuSubType, color = ink.text2, maxLines = 1)
        }
        if (value != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MenuValueType, color = ink.text2, maxLines = 1)
                Icon(Ph.caretRight, null, tint = ink.text3, modifier = Modifier.size(15.dp))
            }
        }
        if (checked) Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(18.dp))
    }
}

/** A choice list (.menu.tx-choice, TX 123): r16, 8 dp padding, at least 248 wide; scrolls inside [maxHeight]. */
@Composable
internal fun TextChoiceSurface(maxHeight: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(min = 248.dp)
            .width(IntrinsicSize.Max)
            .heightIn(max = maxHeight)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        content = content,
    )
}

/**
 * One choice (.tx-ch, TX 124-131): 40 dp, r12, 20 dp [icon], 14.5 sp; the chosen one gets the grey pill, Bold and the
 * check (never colour alone). [trailing] (a keycap or a tag) sits before the check.
 */
@Composable
internal fun TextChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(inkRounded(12.dp))
            .then(if (selected) Modifier.background(ink.selOnRaised) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = ink.text, modifier = Modifier.size(20.dp))
        Text(label, style = if (selected) ChoiceOnType else ChoiceType, color = ink.text, maxLines = 1, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(16.dp).alpha(if (selected) 1f else 0f))
    }
}

/** A list's small header (.tx-mh): 12/16 Bold text2. */
@Composable
internal fun TextMenuHeader(text: String) {
    Text(
        text,
        style = HeaderType,
        color = LocalInk.current.text2,
        modifier = Modifier.semantics { heading() }.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A Markdown keycap (.tx-md, TX 133): at least 24 × 22, r6, a 1 dp line ring, code type 11.5/600 text2. */
@Composable
internal fun Keycap(text: String) {
    val ink = LocalInk.current
    Box(
        Modifier
            .defaultMinSize(minWidth = 24.dp)
            .height(22.dp)
            .border(1.dp, ink.line, inkRounded(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = codeType(11.5.sp, FontWeight.SemiBold), color = ink.text2, maxLines = 1)
    }
}

/**
 * The font well (.tx-ff, TX 77-82): 44 dp, 128–172 wide, r22 in the `surface` grey; the name at 16 sp in its own face,
 * then a caret. Presses to .97; while its list is [open] it wears a 2 dp solid ring. Records [anchor], when the caller
 * hangs its list from the well (the style pill hangs it from the pill and passes none).
 */
@Composable
internal fun FontWell(face: FontFace, open: Boolean, anchor: PopoverAnchor?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    val label = stringResource(R.string.font)
    val name = fontLabel(face)
    Row(
        modifier
            .then(if (anchor != null) Modifier.popoverAnchor(anchor) else Modifier)
            .height(44.dp)
            .widthIn(min = 128.dp, max = 172.dp)
            .pressScale(src, 0.97f)
            .clip(shape)
            .background(ink.surface)
            .then(if (open) Modifier.border(2.dp, ink.solid, shape) else Modifier)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = name
            }
            .padding(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bounded rather than weighted, so the well is as wide as its name (128–172) and the caret still fits.
        Text(
            name,
            style = WellType.copy(fontFamily = face.toComposeFamily()),
            color = ink.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 122.dp),
        )
        Icon(Ph.caretDown, null, tint = ink.text2, modifier = Modifier.size(14.dp))
    }
}

/** The size well (.tx-fs, TX 83-87): 44 dp, r22 `surface`; 36 dp − and + (16 dp icons, press .9) round a 15/700 value. */
@Composable
internal fun SizeWell(value: Double, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ink.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WellStep(Ph.minus, stringResource(R.string.smaller), onMinus)
        Text(
            value.roundToInt().toString(),
            style = SizeValueType,
            color = ink.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 30.dp),
        )
        WellStep(Ph.plus, stringResource(R.string.larger), onPlus)
    }
}

/** How far the size well's − and + reach past their 36 dp, for a 48 dp tap. */
private val STEP_REACH = 6.dp

@Composable
private fun WellStep(icon: ImageVector, label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            // 36 dp to see, 48 to tap: the click reaches 6 dp past each edge (the well's padding, the value's
            // edge, 2 dp of the pill), clear of the font well and the divider; the press tint stays on the 36.
            .reachPast(horizontal = STEP_REACH, vertical = STEP_REACH)
            .clickable(src, null, role = Role.Button, onClick = onClick)
            .padding(STEP_REACH)
            .size(36.dp)
            .pressScale(src, 0.9f)
            .clip(CircleShape)
            .indication(src, LocalIndication.current),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = ink.text, modifier = Modifier.size(16.dp))
    }
}

/**
 * A pill button whose face is not a plain icon (.tx-fb, TX 69-74): the text-colour "A", the highlight tile, the code
 * button with its tag, the note colour. At least 44 dp wide, 44 high, r22; presses to .94; [on] (or open) takes the
 * lit grey; .3 when disabled. [onLongClick] serves the code button's language hold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormatButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .defaultMinSize(minWidth = 44.dp)
            .height(44.dp)
            .pressScale(src, 0.94f)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(22.dp))
            .then(if (on) Modifier.background(ink.selOnRaised) else Modifier)
            .combinedClickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                if (on) selected = true
            }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
