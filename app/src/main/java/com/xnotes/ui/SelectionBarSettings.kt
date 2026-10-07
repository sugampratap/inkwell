package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Fl
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkGroupFooter
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/** A preview tab's name. */
@StringRes
private fun kindLabel(k: SelKind): Int = when (k) {
    SelKind.INK -> R.string.selbar_kind_ink
    SelKind.PICTURE -> R.string.selbar_kind_picture
    SelKind.NOTE -> R.string.selbar_kind_note
    SelKind.TEXT_BOX -> R.string.selbar_kind_text_box
    SelKind.TABLE -> R.string.selbar_kind_table
}

/** A group's caption, over its card of actions. */
@StringRes
private fun groupLabel(g: SelGroup): Int = when (g) {
    SelGroup.LEAD -> R.string.selbar_group_lead
    SelGroup.TABLE -> R.string.selbar_group_table
    SelGroup.CLIPBOARD -> R.string.selbar_group_clipboard
    SelGroup.PICTURE -> R.string.selbar_group_picture
    SelGroup.TURN -> R.string.selbar_group_turn
    SelGroup.ARRANGE -> R.string.selbar_group_arrange
    SelGroup.EXPORT -> R.string.selbar_group_export
    SelGroup.LOCK -> R.string.selbar_group_lock
}

/** Which selections an action works on, when not every one ([SelAction.appliesTo]); null for an action that works on all. */
@StringRes
private fun scopeLabel(a: SelAction): Int? = when (a) {
    SelAction.EDIT -> R.string.selbar_for_typed
    SelAction.NOTE_COLOUR -> R.string.selbar_for_note
    SelAction.STYLE -> R.string.selbar_for_ink
    SelAction.CROP, SelAction.SAVE_IMAGE, SelAction.REPLACE -> R.string.selbar_for_picture
    SelAction.RESET_IMAGE -> R.string.selbar_for_edited_picture
    SelAction.ADD_ROW, SelAction.ADD_COLUMN, SelAction.HEADER -> R.string.selbar_for_table
    SelAction.PASTE -> R.string.selbar_for_paste
    SelAction.ROTATE, SelAction.ROTATE_LEFT, SelAction.FLIP_H, SelAction.FLIP_V -> R.string.selbar_for_turn
    else -> null
}

/**
 * Selection bar, a sheet from General › Selection bar (beside Customise toolbar): which actions sit on the bar over a
 * selection. A live bar at the top for the kind of selection picked above it; then every action, grouped as the bar
 * groups them and in its order, each with a switch: on keeps it on the bar, off sends it to More. The order and the
 * hairlines follow on their own. One choice for every kind of selection: an action that does not work on one is left
 * off its bar. No more than [SEL_BAR_MAX] fit on the bar for any one kind, so a switch that would pass that stays off.
 * Reset to default puts back the approved bar. Changes apply at once, to both editors.
 */
@Composable
internal fun SelectionBarSheet(editor: Editor, onDismiss: () -> Unit) {
    val chosen = selectionBarChoice(editor.selectionBarIds)
    var kind by rememberSaveable { mutableStateOf(SelKind.INK) }
    fun set(next: Set<SelAction>) = editor.applySelectionBar(selectionBarIds(next))
    InkSheet(
        title = stringResource(R.string.settings_selection_bar),
        onDismiss = onDismiss,
        subtitle = stringResource(R.string.selbar_sub),
        width = 760.dp,
        footer = {
            InkGhostButton(
                stringResource(R.string.customise_reset),
                { editor.applySelectionBar(null) },
                icon = Ph.arrowCounterClockwise,
                enabled = chosen != SEL_BAR_DEFAULT,
            )
            Spacer(Modifier.weight(1f))
            InkStrongButton(stringResource(R.string.done), onDismiss)
        },
    ) {
        val ink = LocalInk.current
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp)) {
            InkBoxSegmented(SelKind.entries, kind, label = { stringResource(kindLabel(it)) }, onSelect = { kind = it }, minSegment = 0.dp)
            SelectionBarPreview(kind, chosen, Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.selbar_preview_hint),
                style = InkType.hint,
                color = ink.text2,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
            val full = stringResource(R.string.selbar_full)
            for (g in SelGroup.entries) {
                SettingsSection(stringResource(groupLabel(g))) {
                    for (a in SelAction.entries) {
                        if (a.group != g) continue
                        val on = a in chosen
                        val allowed = canPutOnBar(chosen, a)
                        SettingRowBase(
                            title = stringResource(selActionLabel(a, bar = false)),
                            description = if (!allowed) full else scopeLabel(a)?.let { stringResource(it) },
                            icon = selActionIcon(a),
                            enabled = allowed,
                            onClick = { set(if (on) chosen - a else chosen + a) },
                        ) {
                            SettingsSwitch(on, allowed) { set(if (it) chosen + a else chosen - a) }
                        }
                    }
                }
            }
            InkGroupFooter(stringResource(R.string.selbar_limit, SEL_BAR_MAX))
        }
    }
}

/**
 * The live bar: the selection bar for [kind] with [chosen] on it, drawn as the bar draws itself (64 dp pill, 24 dp
 * Fluent icons over 12.5 sp labels, hairlines between groups, More last) on the canvas colour, scaled down (transform
 * only) to fit. A picture, not buttons: TalkBack reads it as one line of the actions it shows.
 */
@Composable
private fun SelectionBarPreview(kind: SelKind, chosen: Set<SelAction>, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val groups = fitSelectionBar(selectionGroups(kind.facts, chosen), Int.MAX_VALUE).groups
    val shape = MaterialTheme.shapes.medium
    val names = groups.flatten().map { stringResource(selActionLabel(it, bar = true)) } + stringResource(R.string.more)
    val label = stringResource(R.string.settings_selection_bar)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(ink.canvas)
            .border(1.dp, ink.line2, shape)
            .clearAndSetSemantics { contentDescription = label + ": " + names.joinToString(", ") },
        contentAlignment = Alignment.Center,
    ) {
        val available = with(LocalDensity.current) { (maxWidth - 32.dp).toPx() }
        var natural by remember { mutableIntStateOf(0) }
        Row(
            Modifier
                .wrapContentWidth(unbounded = true)
                .onSizeChanged { natural = it.width }
                .graphicsLayer {
                    val k = if (natural > available && natural > 0) available / natural else 1f
                    scaleX = k
                    scaleY = k
                }
                .height(64.dp)
                .inkSurface(RoundedCornerShape(percent = 50), InkElevation.MENU)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SEL_GAP_DP.dp),
        ) {
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.selbar_preview_empty),
                    style = InkType.meta.copy(fontWeight = FontWeight.SemiBold),
                    color = ink.text2,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            for (group in groups) {
                for (a in group) PreviewAction(a, a.widthDp)
                Box(Modifier.padding(horizontal = 6.dp).size(1.dp, 34.dp).background(ink.line))
            }
            PreviewAction(null, SEL_MORE_DP)
        }
    }
}

/** One preview action (null: More): the bar's icon and label, not a button. */
@Composable
private fun PreviewAction(a: SelAction?, widthDp: Int) {
    val ink = LocalInk.current
    Column(Modifier.width(widthDp.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (a == null) Fl.moreHorizontal else selActionIcon(a), null, tint = ink.text, modifier = Modifier.size(24.dp))
        Text(
            stringResource(if (a == null) R.string.more else selActionLabel(a, bar = true)),
            style = InkType.hint.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
            color = ink.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
