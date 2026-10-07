package com.xnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.pal.FontFace
import com.xnotes.platform.FontCatalog
import com.xnotes.platform.FontPick
import com.xnotes.platform.FontShelf
import com.xnotes.platform.fontShelves
import com.xnotes.platform.showsMonoTag
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded

private val NameType = InkType.row.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal) // .tx-fr span, 17/22
private val DefaultTitleType = InkType.button.copy(lineHeight = 19.sp) // .tx-fr.def, 15/19 700
private val ShelfType = InkType.small.copy(fontWeight = FontWeight.Bold) // .tx-fm-h, 12/16 700
private val TagType = InkType.tiny.copy(fontWeight = FontWeight.Bold) // .tx-fr em, 11/700

/**
 * The grouped font list (r3_text Frame 3): 330 wide, r16; the list scrolls inside [maxHeight] with sticky shelf
 * headers (Imported, Built in, Sans serif, Serif, Monospace; empty ones left out), and the footer stays pinned.
 * Every name is set in its own face; the chosen row takes the grey pill and the check, never bold, so the preview
 * keeps its true weight. [withDefault] leads with Default (a null pick: inherit [defaultFace], the note's font);
 * [monoOnly] is the Code font list. The caller hosts it in an InkPopover and closes it on a pick.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FontListMenu(
    current: FontFace?,
    onPick: (FontFace?) -> Unit,
    monoOnly: Boolean = false,
    withDefault: Boolean = false,
    defaultFace: FontFace = FontFace.SANS,
    maxHeight: Dp = 420.dp,
) {
    val ink = LocalInk.current
    val shelves = fontShelves(FontCatalog.picks(), monoOnly)
    Column(
        Modifier
            .width(330.dp)
            .heightIn(max = maxHeight)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU),
    ) {
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (withDefault) {
                item(key = "default") { DefaultRow(defaultFace, selected = current == null) { onPick(null) } }
            }
            shelves.forEach { (shelf, picks) ->
                // Every header has 6 dp above it (.tx-fm-h margin-top), the first shelf's too.
                item(key = "gap-${shelf.name}") { Spacer(Modifier.height(6.dp)) }
                stickyHeader(key = "head-${shelf.name}") { ShelfHeader(shelfLabel(shelf)) }
                items(picks, key = { "font-${shelf.name}-${it.face.id}" }) { p ->
                    FontRow(p, selected = p.face == current, tag = showsMonoTag(p, monoOnly)) { onPick(p.face) }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .textTopRule(ink.line2)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ph.info, null, tint = ink.text2, modifier = Modifier.size(16.dp))
            // Static text (defaults row 7): Settings › Text & fonts is where fonts are imported.
            Text(stringResource(R.string.font_list_footer), style = InkType.hint, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun shelfLabel(shelf: FontShelf): String = stringResource(
    when (shelf) {
        FontShelf.IMPORTED -> R.string.font_shelf_imported
        FontShelf.BUILT_IN -> R.string.font_shelf_built_in
        FontShelf.SANS -> R.string.font_shelf_sans
        FontShelf.SERIF -> R.string.font_shelf_serif
        FontShelf.MONO -> R.string.font_shelf_mono
    },
)

/** A sticky shelf header (.tx-fm-h): full width on the card's fill, so rows scroll under it; 20 dp in, like the names. */
@Composable
private fun ShelfHeader(label: String) {
    val ink = LocalInk.current
    Text(
        label,
        style = ShelfType,
        color = ink.text2,
        modifier = Modifier
            .fillMaxWidth()
            .background(ink.raised)
            .semantics { heading() }
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Default (.tx-fr.def, TX 722): 52 dp, "Default" over "{the note's font}, the note's font". */
@Composable
private fun DefaultRow(defaultFace: FontFace, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(inkRounded(12.dp))
            .then(if (selected) Modifier.background(ink.selOnRaised) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.default_choice), style = DefaultTitleType, color = ink.text, maxLines = 1)
            Text(
                stringResource(R.string.font_default_sub, fontLabel(defaultFace)),
                style = InkType.hint,
                color = ink.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(16.dp).alpha(if (selected) 1f else 0f))
    }
}

/** One font (.tx-fr, TX 141-148): 40 dp, r12; the name at 17 sp in its face, the "mono" tag after it, the check at the end. */
@Composable
private fun FontRow(pick: FontPick, selected: Boolean, tag: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(inkRounded(12.dp))
            .then(if (selected) Modifier.background(ink.selOnRaised) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fontLabel(pick.face),
                style = NameType.copy(fontFamily = pick.face.toComposeFamily()),
                color = ink.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tag) {
                Text(
                    stringResource(R.string.font_mono_tag),
                    style = TagType,
                    color = ink.text2,
                    modifier = Modifier.border(1.dp, ink.line, inkRounded(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(16.dp).alpha(if (selected) 1f else 0f))
    }
}
