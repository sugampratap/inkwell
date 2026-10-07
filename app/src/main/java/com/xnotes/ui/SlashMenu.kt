package com.xnotes.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.text.SlashCommands
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val ROW_H = 40.dp
private val MENU_W = 352.dp
private val LIST_PAD = 6.dp
private val FOOTER_H = 34.dp

private val LabelType = InkType.meta // .tx-lb, 13
private val FooterType = InkType.tiny.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium) // .tx-sl-f, 11.5/500
private val KeyType = InkType.tiny.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold) // .tx-key, 10.5/700

/**
 * The "/" command menu (r3_text Frame 4), hanging under the caret's line, or over the caret when the format pill
 * leaves no room. Like the edit bar it is a plain offset box, not a popup, so it never takes focus and the keyboard
 * stays up while the query is typed. The query lives in the paragraph itself; this lists what it currently matches,
 * read fresh each recomposition. Enter commits the armed row.
 */
@Composable
fun SlashMenu(editor: Editor) {
    editor.flowSelTick // recompose as the caret moves and the query grows
    editor.contentVersion
    val query = editor.slashQuery() ?: return
    val entries = SlashCommands.candidates(query)
    if (entries.isEmpty()) return
    val anchor = editor.slashAnchor() ?: return
    val density = LocalDensity.current
    val cover = LocalToolbarCover.current
    val viewport = editor.viewportSize()
    // 10 dp over the format pill while it is up (its own rule, formatPillReserve), else over a bottom toolbar.
    val reserve = formatPillReserve(editor)
    val place = with(density) {
        val bottomLimit = viewport.y.toFloat() - (if (reserve > 0.dp) reserve + 10.dp else cover.calculateBottomPadding() + 10.dp).toPx()
        placeSlashMenu(
            count = entries.size,
            caretTop = anchor.top.roundToInt(),
            lineBottom = anchor.bottom.roundToInt(),
            slashLeft = anchor.left.roundToInt(),
            topLimit = (cover.calculateTopPadding() + 10.dp).roundToPx(),
            bottomLimit = bottomLimit.roundToInt(),
            menuW = MENU_W.roundToPx(),
            viewW = viewport.x.roundToInt(),
            rowH = ROW_H.roundToPx(),
            chrome = (LIST_PAD * 2 + FOOTER_H).roundToPx(),
            gap = 6.dp.roundToPx(),
            lead = 6.dp.roundToPx(),
            margin = 8.dp.roundToPx(),
        )
    }
    val armed = editor.slashArmed(query, entries)

    // The rise plays once per opening (this state resets when the menu leaves composition); re-filtering only moves it.
    val move = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val penDown = LocalPenDown.current
    LaunchedEffect(Unit) {
        if (penDown() || !ValueAnimator.areAnimatorsEnabled()) {
            move.snapTo(1f)
            fade.snapTo(1f)
        } else {
            launch { fade.animateTo(1f, tween(InkMotion.FAST, easing = InkMotion.Standard)) }
            move.animateTo(1f, InkMotion.popover())
        }
    }

    // Keep the armed row on screen.
    val scroll = rememberScrollState()
    val armedIndex = entries.indexOfFirst { it === armed }
    val rowPx = with(density) { ROW_H.toPx() }
    LaunchedEffect(armedIndex, entries.size, place.rows) {
        if (armedIndex >= 0) {
            val top = armedIndex * rowPx
            val over = top + rowPx - (scroll.value + rowPx * place.rows)
            if (top < scroll.value) scroll.animateScrollTo(top.toInt())
            else if (over > 0f) scroll.animateScrollTo((scroll.value + over).toInt())
        }
    }

    val ink = LocalInk.current
    Column(
        Modifier
            .offset { IntOffset(place.x, place.y) }
            .width(MENU_W)
            .graphicsLayer {
                val p = move.value
                val s = 0.97f + 0.03f * p
                scaleX = s
                scaleY = s
                translationY = (1f - p) * 8.dp.toPx()
                alpha = fade.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(
                    ((place.originX + 8.dp.toPx()) / size.width).coerceIn(0f, 1f),
                    if (place.below) 0f else 1f,
                )
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .inkSurface(inkRounded(16.dp), InkElevation.POP),
    ) {
        Column(
            Modifier
                .heightIn(max = ROW_H * place.rows + LIST_PAD * 2)
                .verticalScroll(scroll)
                .padding(LIST_PAD),
        ) {
            for (entry in entries) {
                SlashRow(
                    entry,
                    key = SlashCommands.shownKey(query, entry),
                    ready = SlashCommands.ready(query, entry),
                    armed = entry === armed,
                ) { editor.runSlash(query, entry) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(FOOTER_H)
                .textTopRule(ink.line2)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterHint(listOf("↑", "↓"), stringResource(R.string.slash_choose))
            FooterHint(listOf("↵"), stringResource(R.string.slash_run))
            FooterHint(listOf(stringResource(R.string.slash_key_esc)), stringResource(R.string.slash_close))
            FooterHint(listOf("//"), stringResource(R.string.slash_types_slash))
        }
    }
}

/**
 * One row (.tx-sr, TX 194-208): badge, the keyword as it is being typed (so "/colou" reads "colour"), the argument it
 * wants as a dashed blank, the plain name, and the Markdown shortcut it duplicates.
 */
@Composable
private fun SlashRow(entry: SlashCommands.Entry, key: String, ready: Boolean, armed: Boolean, onPick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_H)
            .alpha(if (ready) 1f else 0.42f)
            .clip(inkRounded(11.dp))
            .then(if (armed) Modifier.background(ink.selOnRaised) else Modifier)
            .clickable(enabled = ready, role = Role.Button, onClick = onPick)
            .padding(start = 5.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = when {
            !armed -> ink.iconBadge
            ink.isDark -> ink.iconBadgePressed
            else -> ink.raised
        }
        Box(Modifier.size(30.dp).clip(inkRounded(9.dp)).background(badge), contentAlignment = Alignment.Center) {
            Icon(slashIcon(entry), null, tint = ink.text, modifier = Modifier.size(18.dp))
        }
        Text(
            key,
            style = codeType(14.sp, if (armed) FontWeight.ExtraBold else FontWeight.SemiBold, (-0.14).sp),
            color = ink.text,
            maxLines = 1,
        )
        entry.param?.let { ArgTag(it) }
        Text(
            slashLabel(entry),
            style = LabelType,
            color = if (armed) ink.text else ink.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.markdown?.let { Keycap(it) }
    }
}

/** The argument placeholder (.tx-arg): code type 10/16, a dashed 1 dp line3 ring, r5, pulled 2 dp towards the keyword. */
@Composable
private fun ArgTag(text: String) {
    val ink = LocalInk.current
    Text(
        text,
        style = codeType(10.sp, FontWeight.SemiBold, 0.4.sp, 16.sp),
        color = ink.text2,
        maxLines = 1,
        modifier = Modifier
            .offset(x = (-2).dp)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    ink.line3,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            }
            .padding(horizontal = 5.dp),
    )
}

/** A footer hint (.tx-sl-f): keycaps, then what they do. */
@Composable
private fun FooterHint(keys: List<String>, label: String) {
    val ink = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (k in keys) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                    .border(1.dp, ink.line, inkRounded(5.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(k, style = KeyType, color = ink.text, maxLines = 1)
            }
        }
        Text(label, style = FooterType, color = ink.text2, maxLines = 1)
    }
}

/** The entry's badge icon (TX 1093-1106). */
private fun slashIcon(entry: SlashCommands.Entry): ImageVector = when (entry.kind) {
    SlashCommands.Kind.HEADING -> when (entry.level) {
        1 -> Ph.textHOne
        2 -> Ph.textHTwo
        3 -> Ph.textHThree
        4 -> Ph.textHFour
        5 -> Ph.textHFive
        else -> Ph.textHSix
    }
    SlashCommands.Kind.BODY -> Ph.paragraph
    SlashCommands.Kind.BULLET -> Ph.listBullets
    SlashCommands.Kind.ORDERED -> Ph.listNumbers
    SlashCommands.Kind.TODO -> Ph.listChecks
    SlashCommands.Kind.CODE -> Ph.code
    SlashCommands.Kind.TABLE -> Ph.table
    SlashCommands.Kind.SIZE -> Ph.textAa
    SlashCommands.Kind.COLOR -> Ph.palette
    SlashCommands.Kind.MATH -> Ph.function
    SlashCommands.Kind.DATE -> Ph.calendarBlank
    SlashCommands.Kind.TIME -> Ph.clock
}

/** The entry's visible name (the mockup's labels). Keywords are ASCII; these are the translatable part. */
@Composable
private fun slashLabel(entry: SlashCommands.Entry): String = when (entry.kind) {
    SlashCommands.Kind.HEADING -> stringResource(R.string.heading_n, entry.level)
    SlashCommands.Kind.BODY -> stringResource(R.string.body_text)
    SlashCommands.Kind.BULLET -> stringResource(R.string.bullet_list)
    SlashCommands.Kind.ORDERED -> stringResource(R.string.text_numbered_list)
    SlashCommands.Kind.TODO -> stringResource(R.string.slash_checklist_item)
    SlashCommands.Kind.CODE -> stringResource(R.string.code_block)
    SlashCommands.Kind.TABLE -> stringResource(R.string.slash_table)
    SlashCommands.Kind.SIZE -> stringResource(R.string.slash_text_size)
    SlashCommands.Kind.COLOR -> stringResource(R.string.text_colour)
    SlashCommands.Kind.MATH -> stringResource(R.string.equation)
    SlashCommands.Kind.DATE -> stringResource(R.string.insert_date)
    SlashCommands.Kind.TIME -> stringResource(R.string.insert_time)
}
