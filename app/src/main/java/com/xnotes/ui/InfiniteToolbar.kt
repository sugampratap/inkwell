package com.xnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.PopoverAnchor

/** Which tool a toolbar item arms, for the items that are simply a tool button. */
private val CANVAS_TOOL_OF: Map<ToolbarItem, Tool> = mapOf(
    ToolbarItem.PEN to Tool.PEN,
    ToolbarItem.BALLPOINT to Tool.BALLPOINT,
    ToolbarItem.LASER to Tool.LASER,
    ToolbarItem.DASHED to Tool.DASHED,
    ToolbarItem.CALLIGRAPHY to Tool.CALLIGRAPHY,
    ToolbarItem.SPEED to Tool.SPEED,
    ToolbarItem.TAPER to Tool.TAPER,
    ToolbarItem.PENCIL to Tool.PENCIL,
    ToolbarItem.HIGHLIGHTER to Tool.HIGHLIGHTER,
    ToolbarItem.ERASER to Tool.ERASER,
    ToolbarItem.PAN to Tool.PAN,
    ToolbarItem.SELECT to Tool.SELECT,
    ToolbarItem.LASSO to Tool.LASSO,
    ToolbarItem.SHAPE to Tool.SHAPE,
    ToolbarItem.TAPE to Tool.TAPE,
)

/**
 * The infinite canvas's chrome. A separate bar from the paged [Toolbar] because most of that one
 * addresses pages, viewing modes, pagination and text, none of which exist here; but it is built
 * from the same pieces (tool buttons, the pen button, quick colours, the one card host), so the two
 * look and behave like one app rather than two. Tools arm through [InfiniteEditor.armTool]; the canvas
 * has no text or PDF markup cards, its Image button inserts at once, and its zoom level is only shown.
 *
 * It draws from its own [com.xnotes.core.tools.ToolbarLayout], arranged in Preferences exactly as
 * the paged bar is. Its own, not the paged one: page navigation means nothing here and a waypoint
 * means nothing there, so the two layouts hold different items and are stored apart.
 */
@Composable
fun InfiniteToolbar(
    editor: InfiniteEditor,
    onOpenBackstage: () -> Unit,
    onInsertImage: () -> Unit = {},
    onClosePane: (() -> Unit)? = null,
) {
    // One card up on the bar at a time, as on the paged bar.
    val cards = rememberToolCardState()
    val layout = editor.toolbarLayout
    val more = moreItems(layout, ToolSurface.CANVAS, hasPdf = false)
    val lit = moreLit(more, editor.tool.barItem()) { false }

    // Pinned outside the scrolling strip so closing a split pane is always one tap away.
    ToolbarFrame(armed = barGlideKey(editor.tool, layout, lit), trailing = onClosePane?.let { { ClosePaneButton(it) } }) {
        layout.visibleSections.forEachIndexed { index, section ->
            if (index > 0) Separator()
            for (entry in section.visibleEntries) {
                key(entry.item) {
                    when (val item = entry.item) {
                        ToolbarItem.HOME -> ToolbarItemButton(ToolbarItem.HOME, stringResource(R.string.toolbar_home), onClick = onOpenBackstage)
                        // A name has no room down a side rail.
                        ToolbarItem.TITLE -> if (!LocalBar.current.vertical) Label(
                            if (editor.document.displayName == null && editor.document.path == null) stringResource(R.string.untitled) else editor.title,
                            Modifier.padding(end = 4.dp),
                        )

                        // One button for every pen type, as on the paged bar.
                        ToolbarItem.PEN -> PenFamilyButton(editor, layout, cards, ToolSurface.CANVAS, editor::inkOnPaper)

                        in CANVAS_TOOL_OF -> ToolButton(editor, CANVAS_TOOL_OF.getValue(item), cards, ToolSurface.CANVAS)

                        // A direct insert: the canvas has no paste or sticker menu.
                        ToolbarItem.IMAGE -> ToolbarItemButton(ToolbarItem.IMAGE, stringResource(R.string.insert_image), spaced = true, onClick = onInsertImage)

                        ToolbarItem.COLORS -> QuickColours(editor, cards, editor::inkOnPaper)

                        ToolbarItem.UNDO -> ToolbarItemButton(ToolbarItem.UNDO, stringResource(R.string.undo), enabled = editor.canUndo) { editor.undo() }
                        ToolbarItem.REDO -> ToolbarItemButton(ToolbarItem.REDO, stringResource(R.string.redo), enabled = editor.canRedo) { editor.redo() }

                        // Where the paged bar keeps its page, styles and view menus. Waypoints take the
                        // place of pagination: on an unbounded canvas, a saved view is what a page
                        // number was.
                        ToolbarItem.STYLES -> {
                            val anchor = remember { PopoverAnchor() }
                            Box {
                                ToolbarItemButton(ToolbarItem.STYLES, stringResource(R.string.toolbar_styles), active = cards.open == BarCards.STYLES, anchor = anchor) {
                                    cards.toggle(BarCards.STYLES)
                                }
                                CardSlot(cards, BarCards.STYLES, anchor) { dismiss -> CanvasStylesPopup(editor, dismiss) }
                            }
                        }
                        ToolbarItem.WAYPOINTS -> {
                            val anchor = remember { PopoverAnchor() }
                            Box {
                                ToolbarItemButton(ToolbarItem.WAYPOINTS, stringResource(R.string.toolbar_waypoints), active = cards.open == BarCards.WAYPOINTS, anchor = anchor) {
                                    cards.toggle(BarCards.WAYPOINTS)
                                }
                                CardSlot(cards, BarCards.WAYPOINTS, anchor) { dismiss -> CanvasWaypointsPopup(editor, dismiss) }
                            }
                        }
                        ToolbarItem.MINIMAP ->
                            ToolbarItemButton(ToolbarItem.MINIMAP, stringResource(R.string.toolbar_minimap), active = editor.minimapVisible, toggle = true) { editor.toggleMinimap() }

                        // The level is only shown here: the canvas has no zoom limits to set.
                        ToolbarItem.ZOOM -> {
                            ToolbarIcon(Ph.minus, stringResource(R.string.zoom_out), enabled = !editor.zoomLocked) {
                                editor.zoomBy(1.0 / InfiniteEditor.ZOOM_STEP)
                            }
                            Label("${editor.zoomPercent}%")
                            ToolbarIcon(Ph.plus, stringResource(R.string.zoom_in), enabled = !editor.zoomLocked) {
                                editor.zoomBy(InfiniteEditor.ZOOM_STEP)
                            }
                        }
                        ToolbarItem.FIT ->
                            ToolbarItemButton(ToolbarItem.FIT, stringResource(R.string.fit_all), enabled = !editor.zoomLocked) { editor.zoomToFit() }

                        ToolbarItem.ZOOM_LOCK -> ZoomLockButton(editor.zoomLocked) { editor.toggleZoomLock() }

                        ToolbarItem.MORE -> MoreForCanvas(editor, cards, more, lit, onInsertImage)

                        // Everything else belongs to the paged bar (or is retired); a canvas layout never holds one.
                        else -> Unit
                    }
                }
            }
        }

        if (!LocalBar.current.vertical) editor.renderFailure?.let {
            Separator()
            Label(stringResource(R.string.gl_unavailable))
        }
        if (!LocalBar.current.vertical && editor.outOfMemory) {
            Separator()
            Label(stringResource(R.string.canvas_out_of_memory))
        }
    }
}

/** The canvas bar's ⋯: a row per hidden tool and Insert image, from [more]; lit as [lit] says. */
@Composable
private fun MoreForCanvas(editor: InfiniteEditor, cards: ToolCardState, more: List<MoreItem>, lit: MoreLit, onInsertImage: () -> Unit) {
    val rows = ArrayList<MoreRow>(more.size)
    for (m in more) {
        when (m.kind) {
            MoreKind.TOOL -> {
                val t = CANVAS_TOOL_OF[m.item]
                if (t != null) rows += MoreRow(m, stringResource(t.labelRes), editor.tool == t) { cards.onToolTap(editor, t, ToolSurface.CANVAS) }
            }
            MoreKind.ACTION ->
                if (m.item == ToolbarItem.IMAGE) rows += MoreRow(m, stringResource(R.string.insert_image), on = false) { onInsertImage() }
            MoreKind.SWITCH -> Unit
        }
    }
    MoreToolsButton(rows, lit, editor, cards, ToolSurface.CANVAS)
}
