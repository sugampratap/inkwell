package com.xnotes.core.history

import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import java.io.File

/**
 * Insert > PDF as one undo step: the note's source PDF is swapped for one that also holds the
 * inserted file's pages, and those pages go in at [index].
 *
 * The note's existing pages keep their [Page.pdfPage] numbers because the combined file starts with
 * the old one's pages in their old order; only the inserted pages point past them. So undoing has
 * nothing to renumber either: it takes the inserted pages out and puts the old file back. Neither
 * file is ever deleted here, since either may be needed again by the other half of the cycle; the
 * editor sweeps the one left unused when the note closes.
 *
 * [onPdfChanged] re-opens the renderer over whichever file is current, and runs after every swap.
 */
class InsertPdfPages(
    private val document: Document,
    private val oldPdf: File?,
    private val newPdf: File,
    private val pages: List<Page>,
    private val index: Int,
    private val onPdfChanged: () -> Unit,
) : Command {
    override fun redo() {
        document.pdfFile = newPdf
        val at = index.coerceIn(0, document.pages.size)
        val missing = pages.filter { p -> document.pages.none { it === p } }
        document.pages.addAll(at, missing)
        onPdfChanged()
    }

    override fun undo() {
        document.pages.removeAll { p -> pages.any { it === p } }
        document.pdfFile = oldPdf
        onPdfChanged()
    }
}
