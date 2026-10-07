package com.xnotes.core.history

import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageOrder

/**
 * Reorder a note's pages as one undo step (the side panel's drag, or its TalkBack actions).
 *
 * Everything a page owns travels with the [Page] object itself: its items, PDF page, style
 * (template, paper colour), margins and markups. Bookmarks name pages by index, so they are
 * re-pointed at the same page on each side of the swap. Guarded like [AddPage]/[DeletePage]: each
 * direction only applies to the order it expects, so a repeat is a no-op.
 */
class MovePages private constructor(
    private val document: Document,
    private val oldOrder: List<Page>,
    private val newOrder: List<Page>,
) : Command {

    override fun redo() = swap(oldOrder, newOrder)

    override fun undo() = swap(newOrder, oldOrder)

    /** Moving pages changes no item: the page caches stay good, only the layout moves. */
    override fun touched(locate: (CanvasItem) -> Page?): List<Pair<Page, CanvasItem>> = emptyList()

    private fun swap(from: List<Page>, to: List<Page>) {
        if (!document.pages.sameRefs(from)) return
        document.pages.clear()
        document.pages.addAll(to)
        repointBookmarks(document, from, to)
    }

    companion object {
        /**
         * Move the pages at [indices] to slot [before] (see [PageOrder.move]) and return the step to
         * push, or null (nothing changed, nothing to record) when the drop leaves the order as it is.
         */
        fun apply(document: Document, indices: List<Int>, before: Int): MovePages? {
            val order = PageOrder.move(document.pages.size, indices, before) ?: return null
            val old = document.pages.toList()
            val cmd = MovePages(document, old, order.map { old[it] })
            cmd.redo()
            return cmd
        }

        private fun repointBookmarks(document: Document, from: List<Page>, to: List<Page>) {
            if (document.bookmarks.isEmpty()) return
            val at = java.util.IdentityHashMap<Page, Int>(to.size * 2)
            to.forEachIndexed { i, p -> at[p] = i }
            for (b in document.bookmarks) {
                val page = from.getOrNull(b.page) ?: continue
                at[page]?.let { b.page = it }
            }
        }

        private fun List<Page>.sameRefs(other: List<Page>): Boolean {
            if (size != other.size) return false
            for (i in indices) if (this[i] !== other[i]) return false
            return true
        }
    }
}
