package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.ActOnce
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.rememberActOnce
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf

/**
 * One row of the ⋯ menu: the hidden [item] (its kind says how the row behaves), as [label]; [on] while
 * that tool is in hand or that switch is on; [onTap] does it. A tool row's tap goes through the bar's
 * card state, which also closes the menu.
 */
internal class MoreRow(val item: MoreItem, val label: String, val on: Boolean, val onTap: () -> Unit)

/** The items [layout] keeps off the bar, in bar order. */
internal fun ToolbarLayout.hiddenItems(): List<ToolbarItem> =
    sections.flatMap { it.entries }.filter { !it.visible }.map { it.item }

/**
 * The key the bar's glider follows (TO 904-905): ⋯ itself while a hidden tool is in hand, so the glider
 * slides under ⋯; otherwise the armed tool's own button, or the pen button for a pen it stands for.
 */
internal fun barGlideKey(tool: Tool, layout: ToolbarLayout, lit: MoreLit): Any =
    if (lit == MoreLit.TOOL_IN_HAND) ToolbarItem.MORE else glideKeyFor(tool, layout)

/** A tool row's icon while that tool is in hand (TO 902): Phosphor Fill where the tool has one. */
private fun moreFill(item: ToolbarItem): ImageVector? = when (item) {
    ToolbarItem.PAN -> Ph.handFill
    ToolbarItem.TEXT_BOX -> Ph.textboxFill
    ToolbarItem.PEN -> Ph.penNibFill
    ToolbarItem.BALLPOINT -> Ph.penFill
    else -> null
}

private val MORE_MENU_W = 300.dp

/**
 * The bar's ⋯ More tools (TO Frame 6): everything the layout hides, one tap away, so a short pill still
 * reaches every tool. Not drawn when nothing is hidden. It is lit two ways (TO 901-905):
 * - a hidden tool in hand: ⋯ is that tool's button, so the glider slides under it ([barGlideKey]) and
 *   its glyph turns `onSolid`;
 * - otherwise a hidden switch that is on (the Ruler): the lit grey behind it.
 *
 * Its menu and a hidden tool's card both hang from it, through the bar's [cards].
 */
@Composable
internal fun MoreToolsButton(rows: List<MoreRow>, lit: MoreLit, host: ToolPopupHost, cards: ToolCardState, surface: ToolSurface) {
    if (rows.isEmpty()) return
    val anchor = remember { PopoverAnchor() }
    Box {
        ToolbarItemButton(
            ToolbarItem.MORE,
            stringResource(R.string.toolbar_more),
            active = lit == MoreLit.TOOL_IN_HAND,
            lit = lit == MoreLit.SWITCH_ON,
            glideKey = ToolbarItem.MORE,
            anchor = anchor,
        ) { cards.toggle(BarCards.MORE) }
        CardSlot(cards, BarCards.MORE, anchor) { dismiss -> MoreMenu(rows, anchor, dismiss) }
        // A hidden tool's card hangs from ⋯, where its row is.
        ToolCardsAt(host, cards, surface, anchor) { t -> rows.any { it.item.kind == MoreKind.TOOL && it.item.item.id == t.id } }
    }
}

/**
 * The menu (TO 82-89, 887-894): 300 dp, padding 8 / 0 / 6. Tool and action rows first, in bar order;
 * then a divider and the switch rows; then a divider and the footer that says where the rest of the
 * tools are chosen.
 */
@Composable
private fun MoreMenu(rows: List<MoreRow>, anchor: PopoverAnchor, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val (switches, others) = rows.partition { it.item.kind == MoreKind.SWITCH }
    // A tool or action row acts once per open; the switches act on every tap, the menu staying open.
    val once = rememberActOnce(LocalCardExpanded.current)
    InkPopover(expanded = LocalCardExpanded.current, onDismiss = onDismiss, anchor = anchor, spec = PopoverSpecs.MoreMenu) {
        BarMenu(MORE_MENU_W) {
            for (r in others) MoreMenuRow(r, once, onDismiss)
            if (switches.isNotEmpty()) {
                if (others.isNotEmpty()) MoreDivider()
                for (r in switches) MoreMenuRow(r, once, onDismiss)
            }
            MoreDivider()
            Text(
                stringResource(R.string.more_tools_footer),
                style = InkType.hint,
                color = ink.text2,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * A ⋯ row (.m-row inset, TO 83-85): a 44 dp pill 8 dp in from the menu's sides, 12 dp padding, 22 dp icon,
 * 15 sp label.
 * - **Tool:** a radio item; on (its tool in hand) it takes the lit grey, goes bold, turns its icon to Fill
 *   (or its active form) and shows the 18 dp check. A tap arms it or opens its card; either closes the menu.
 * - **Switch (Ruler):** the icon stays Regular, a B2 switch sits at the end, and the menu stays open (TO 903, 907).
 * - **Action (Insert image…):** runs, and the menu closes.
 *
 * Tool and action rows go through [once], so a double tap acts once (it would arm a tool and then open its card).
 */
@Composable
private fun MoreMenuRow(row: MoreRow, once: ActOnce, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    val kind = row.item.kind
    val toolOn = kind == MoreKind.TOOL && row.on
    val shape = RoundedCornerShape(cornerOf(12.dp))
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .then(if (toolOn) Modifier.background(ink.selOnRaised) else Modifier)
            .then(
                when (kind) {
                    MoreKind.TOOL -> Modifier.selectable(selected = row.on, role = Role.RadioButton, onClick = { once.run(row.onTap) })
                    MoreKind.SWITCH -> Modifier.toggleable(value = row.on, role = Role.Switch, onValueChange = { row.onTap() })
                    MoreKind.ACTION -> Modifier.clickable(role = Role.Button) {
                        once.run {
                            row.onTap()
                            onDismiss()
                        }
                    }
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MoreRowIcon(row.item.item, filled = toolOn, tint = ink.text)
        Text(
            row.label,
            style = if (toolOn) InkType.row.copy(fontWeight = FontWeight.Bold) else InkType.row,
            color = ink.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when (kind) {
            MoreKind.TOOL -> Icon(Ph.check, contentDescription = null, tint = ink.text, modifier = Modifier.size(18.dp).alpha(if (row.on) 1f else 0f))
            MoreKind.SWITCH -> InkSwitch(checked = row.on, onCheckedChange = null)
            MoreKind.ACTION -> Unit
        }
    }
}

/** A row's 22 dp icon: Regular; a tool row that is on turns to Fill, or its active form where Phosphor has no Fill. */
@Composable
private fun MoreRowIcon(item: ToolbarItem, filled: Boolean, tint: Color) {
    val fill = if (filled) moreFill(item) else null
    if (fill != null) {
        Icon(fill, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    } else {
        ItemIcon(item, tint, 22.dp, filled)
    }
}

/** The menu's divider (.m-hr, TO 86): 1 dp line2 with 6 dp above and below. */
@Composable
private fun MoreDivider() {
    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(LocalInk.current.line2))
}
