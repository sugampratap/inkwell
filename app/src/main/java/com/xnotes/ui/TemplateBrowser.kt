package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.core.model.PageTemplates
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/**
 * Every template, grouped (see [TemplateGroups.sections]): Basics, My templates, In this note (Page
 * setup only), then the bundled ones by use. Each tile is drawn in this note's paper, line and
 * accent colours, so changing the paper recolours the whole browser. A tap hands its key to
 * [onPick]. In Page setup ([setup]) My templates ends with the Import tile, a long press on an
 * imported template offers to remove it ([onRemove]) and on a note-carried one to keep it ([onKeep]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TemplateBrowser(
    entries: List<TemplateLibrary.Entry>,
    shownKey: String,
    setup: Boolean,
    tileWidth: Dp,
    pageMm: Pair<Double, Double>,
    look: TemplateLook,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImport: (() -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    onKeep: ((String) -> Unit)? = null,
) {
    val ink = LocalInk.current
    val byKey = remember(entries) { entries.associateBy { it.key } }
    val sections = remember(entries, setup) {
        TemplateGroups.sections(entries.map { TemplateGroups.Item(it.key, it.source, it.template.tags) }, setup)
    }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        for (s in sections) key(s.group) {
            Column {
                Text(stringResource(s.group.title), style = InkType.label, color = ink.text, maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))
                // Tiles wrap inside their section: a section that fits sits beside the others (the
                // mockup's side-by-side groups); a long one (Writing & study, many imports) wraps
                // under its heading instead of squeezing its last tiles to nothing.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in s.keys) key(k) {
                        val e = if (k == PageTemplates.NONE) null else byKey[k]
                        if (k == PageTemplates.NONE || e != null) {
                            val menu = when (e?.source) {
                                TemplateLibrary.Source.IMPORTED -> onRemove?.let { r -> TileMenu.Remove { r(k) } }
                                TemplateLibrary.Source.NOTE -> onKeep?.let { kp -> TileMenu.Keep { kp(k) } }
                                else -> null
                            }
                            PaperTemplateTile(e, k == shownKey, tileWidth, pageMm, look, onSelect = { onPick(k) }, menu = menu)
                        }
                    }
                    if (s.import && onImport != null) PaperImportTile(tileWidth, pageMm, onImport)
                }
            }
        }
    }
}
