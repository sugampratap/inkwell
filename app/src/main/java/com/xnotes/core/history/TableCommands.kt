package com.xnotes.core.history

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.TableGrid
import com.xnotes.core.model.TableItem

/**
 * One edit to a placed table: a cell's text, a row or column added or taken away, a shading, a
 * column dragged wider. The grid is immutable, so the grid before and the grid after are the whole
 * edit, whatever kind it was; [posBefore]/[posAfter] cover the rare edit that also shifts it.
 */
class TableEdit(
    private val item: TableItem,
    private val before: TableGrid,
    private val after: TableGrid,
    private val posBefore: Pt = item.pos,
    private val posAfter: Pt = item.pos,
) : Command {
    override fun redo() {
        item.grid = after
        item.pos = posAfter
    }

    override fun undo() {
        item.grid = before
        item.pos = posBefore
    }

    override fun touched(locate: (CanvasItem) -> Page?): List<Pair<Page, CanvasItem>> =
        locate(item)?.let { listOf(it to item) } ?: emptyList()
}
