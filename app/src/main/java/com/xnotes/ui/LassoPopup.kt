package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.LassoFilter
import com.xnotes.core.tools.LassoOptions
import com.xnotes.core.tools.LassoShape
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkHint
import com.xnotes.ui.kit.InkOptionCard
import com.xnotes.ui.kit.InkOptionCardRow
import com.xnotes.ui.kit.selOnRaised
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded

/**
 * The lasso's settings (r2_selection_colour Frame 1), opened by tapping the lasso while it is armed, as the pens open
 * theirs: the shape (a drawn loop or a dragged box) as two drawn cards; which kinds of object it picks up (Samsung Notes'
 * "Select") as radio rows; and whether a tap picks the object under it. Every change applies at once and is kept in
 * the preferences, for both editors.
 */
@Composable
fun LassoConfigPopup(host: ToolPopupHost, onDismiss: () -> Unit) {
    var options by remember { mutableStateOf(host.hostLassoOptions) }
    fun emit(next: LassoOptions) {
        options = next
        host.updateLassoOptions(next)
    }
    val freeOn = options.shape == LassoShape.FREEFORM
    val freeArt = rememberArt(lassoArt(LassoShape.FREEFORM, freeOn), OPTION_ART_W, OPTION_ART_H)
    val rectArt = rememberArt(lassoArt(LassoShape.RECTANGLE, !freeOn), OPTION_ART_W, OPTION_ART_H)

    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.lasso_options), onClose = onDismiss) {
            InkCardSection(first = true) {
                InkCardCaption(stringResource(R.string.lasso_shape))
                InkOptionCardRow(Modifier.fillMaxWidth()) {
                    InkOptionCard(
                        selected = freeOn,
                        label = stringResource(R.string.lasso_freeform),
                        onClick = { emit(options.copy(shape = LassoShape.FREEFORM)) },
                        modifier = Modifier.weight(1f),
                    ) { tint -> drawArt(freeArt, tint) }
                    InkOptionCard(
                        selected = !freeOn,
                        label = stringResource(R.string.lasso_rectangle),
                        onClick = { emit(options.copy(shape = LassoShape.RECTANGLE)) },
                        modifier = Modifier.weight(1f),
                    ) { tint -> drawArt(rectArt, tint) }
                }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.lasso_select), stringResource(R.string.to_lasso_select_what))
                Column(
                    Modifier.fillMaxWidth().cardRowBleed().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (f in LassoFilter.entries) {
                        FilterRow(f, selected = options.filter == f) { emit(options.copy(filter = f)) }
                    }
                }
            }
            InkCardSection {
                ToggleRow(stringResource(R.string.lasso_tap_select), options.tapSelect, minHeight = 34.dp) {
                    emit(options.copy(tapSelect = it))
                }
                InkHint(stringResource(R.string.lasso_tap_select_hint), top = 4.dp, bottom = 6.dp)
            }
        }
    }
}

private val FilterLabel = InkType.body
private val FilterLabelOn = InkType.body.copy(fontWeight = FontWeight.Bold)

/**
 * .sc-row (SC 34–39): a 40 dp radio row, r12, a 20 dp icon (Fill when chosen), the name, and a tick that holds its
 * place in every row and shows on the chosen one. Chosen: the lit grey fill (`selOnRaised`) and a bold name.
 */
@Composable
private fun FilterRow(filter: LassoFilter, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(inkRounded(12.dp))
            .background(if (selected) ink.selOnRaised else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(filter.icon(selected), contentDescription = null, tint = ink.text, modifier = Modifier.size(20.dp))
        Text(
            stringResource(filter.labelRes),
            style = if (selected) FilterLabelOn else FilterLabel,
            color = ink.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Ph.check, contentDescription = null, tint = ink.text, modifier = Modifier.size(16.dp).graphicsLayer { alpha = if (selected) 1f else 0f })
    }
}

/** SC 518: selection-all, scribble, image, text-t, shapes; their Fill weights when chosen. */
private fun LassoFilter.icon(selected: Boolean): ImageVector = when (this) {
    LassoFilter.ALL -> if (selected) Ph.selectionAllFill else Ph.selectionAll
    LassoFilter.HANDWRITING -> if (selected) Ph.scribbleFill else Ph.scribble
    LassoFilter.IMAGES -> if (selected) Ph.imageFill else Ph.image
    LassoFilter.TEXT -> if (selected) Ph.textTFill else Ph.textT
    LassoFilter.SHAPES -> if (selected) Ph.shapesFill else Ph.shapes
}

@get:androidx.annotation.StringRes
private val LassoFilter.labelRes: Int
    get() = when (this) {
        LassoFilter.ALL -> R.string.lasso_filter_all
        LassoFilter.HANDWRITING -> R.string.lasso_filter_handwriting
        LassoFilter.IMAGES -> R.string.lasso_filter_images
        LassoFilter.TEXT -> R.string.lasso_filter_text
        LassoFilter.SHAPES -> R.string.lasso_filter_shapes
    }
