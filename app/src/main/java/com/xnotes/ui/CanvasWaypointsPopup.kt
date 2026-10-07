package com.xnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkHint
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum

/** The zoom beside each view: 13/18 Medium, tabular. */
private val WaypointZoom = InkType.meta.tnum()

/**
 * Saved views, on the shared card. An infinite canvas has no pages to navigate by, so a named viewport is what stands
 * in for a bookmark: save where you are, jump back to it later.
 */
@Composable
fun CanvasWaypointsPopup(editor: InfiniteEditor, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    var name by remember { mutableStateOf(TextFieldValue("")) }
    fun save() {
        if (name.text.isNotBlank()) {
            editor.saveWaypoint(name.text)
            name = TextFieldValue("")
        }
    }

    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.title_waypoints), onClose = onDismiss) {
            InkCardSection(first = true) {
                if (editor.waypoints.isEmpty()) {
                    InkHint(stringResource(R.string.no_waypoints), top = 0.dp)
                } else {
                    Column(Modifier.fillMaxWidth().cardRowBleed(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (waypoint in editor.waypoints) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(inkRounded(12.dp))
                                    .clickable(role = Role.Button) { editor.jumpTo(waypoint); onDismiss() }
                                    .padding(start = 12.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Ph.mapPin, contentDescription = null, tint = ink.text2, modifier = Modifier.size(20.dp))
                                Text(
                                    waypoint.name,
                                    style = InkType.body,
                                    color = ink.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${Math.round(waypoint.zoom * 100)}%", style = WaypointZoom, color = ink.text2)
                                InkIconButton(
                                    Ph.x,
                                    stringResource(R.string.remove),
                                    onClick = { editor.removeWaypoint(waypoint) },
                                    size = 36.dp,
                                    iconSize = 16.dp,
                                )
                            }
                        }
                    }
                }
            }
            InkCardSection {
                val caption = stringResource(R.string.caption_save_view)
                InkCardCaption(caption)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f).semantics { contentDescription = caption },
                        height = 44.dp,
                    )
                    InkSecondaryButton(stringResource(R.string.save), onClick = { save() })
                }
            }
            InkCardSection(bottom = 18.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(stringResource(R.string.toolbar_minimap), editor.minimapVisible) { editor.toggleMinimap() }
                    ActionChip(stringResource(R.string.fit_all)) { editor.zoomToFit(); onDismiss() }
                }
            }
        }
    }
}
