package com.xnotes.core.model

import com.xnotes.core.pal.TextMeasurer

/**
 * A deep, independent copy of a canvas item. Mutable geometry (stroke samples) is duplicated so the
 * copy and original can be edited apart; image rasters are *shared* because their pixels are
 * immutable. [measurer] is needed to lay out a copied text box. Used for copy/paste/duplicate of
 * both items and whole pages (see [Page.deepCopy]).
 */
fun CanvasItem.deepCopy(measurer: TextMeasurer): CanvasItem = when (this) {
    is Stroke -> Stroke(this)
    is ImageItem -> ImageItem(image, rect, orientation, angle, crop, flipX, flipY)
    // The audio file is shared like an image's: nothing writes to it once it exists.
    is AudioItem -> AudioItem(audio, pos, title, durationMs, voice, recording)
    is TextItem -> TextItem(pos, width, height, text, rgba, pointSize, face, measurer, fill)
    // The grid is immutable, so the copy shares it until either one is edited (which swaps a new one in).
    is TableItem -> TableItem(pos, grid, measurer)
    is ShapeItem ->
        ShapeItem(
            shape, start, end, strokeRgba, strokeWidth, fillRgba, neon, neonStrength, points?.toList(), dashed, dashLength, dashGap,
            grain, grainPressure,
        )
    // A copy is a strip of its own, but torn the same way and peeled back or not as the original.
    is TapeItem -> TapeItem(start, end, width, color, pattern, revealed, seed)
    else -> this
    // Carried, not reset: autosave snapshots the document through this, so dropping the lock here
    // would quietly unlock everything on the next save.
}.also { it.locked = locked }

/** A deep copy of a page — its items cloned ([deepCopy]) — keeping the size, PDF link, style, margins and markups. */
fun Page.deepCopy(measurer: TextMeasurer): Page =
    Page(width, height, items.mapTo(mutableListOf()) { it.deepCopy(measurer) }, pdfPage, style, margins, markups)

/**
 * A deep copy of a document: pages cloned, bookmarks copied, the source PDF file and styles shared
 * (immutable for the copy's lifetime). Cheap now that image bytes are shared, so it can snapshot the
 * live document on the main thread before an off-thread save, keeping the writer off the mutating
 * model (no [ConcurrentModificationException]).
 */
fun Document.deepCopy(measurer: TextMeasurer): Document {
    // Items are cloned page by page, so the copies' stamps are carried across by position.
    val stamps = java.util.IdentityHashMap<CanvasItem, AudioStamp>()
    val copied = pages.mapTo(mutableListOf()) { page ->
        page.deepCopy(measurer).also { copy ->
            for (i in page.items.indices) audioStamps[page.items[i]]?.let { stamps[copy.items[i]] = it }
        }
    }
    return deepCopyOf(copied).also { it.audioStamps = stamps }
}

private fun Document.deepCopyOf(copiedPages: MutableList<Page>): Document = Document(
    pages = copiedPages,
    dpi = dpi,
    path = path,
    displayName = displayName,
    dirty = dirty,
    pdfFile = pdfFile,
    bookmarks = bookmarks.mapTo(mutableListOf()) { Bookmark(it.page, it.label) },
    style = style,
    margins = margins,
    // The flow must be cloned too or the autosave snapshot would race live edits.
    flow = flow.deepCopy(),
    created = created,
    templates = templates,
)

/**
 * The document as the writer should see it: fresh [Page] objects over fresh item *lists*, sharing
 * the items themselves. O(items) pointers rather than O(samples) floats, so an autosave no longer
 * needs a second copy of the note on the heap (which is what used to run a big note out of it).
 *
 * What makes sharing safe is that the main thread only ever *replaces* an item's state, never
 * edits it under a reader: adding, deleting and reordering touch the live lists this snapshot
 * copied, and a [Stroke]'s samples are published whole through one volatile field. So the writer
 * always serializes a coherent item, though an edit landing mid-write may or may not be in the
 * file; that edit marks the document dirty again and the next autosave carries it.
 *
 * The flow is the exception: it is a tree of mutable lists with no such discipline, so it is still
 * deep-copied. It is small. Use [deepCopy] wherever the copy has to be independently editable.
 */
fun Document.snapshot(): Document = snapshotOf().also {
    // The writer reads its own copy off the main thread; the live table keeps changing under it.
    it.audioStamps = java.util.IdentityHashMap(audioStamps)
}

private fun Document.snapshotOf(): Document = Document(
    pages = pages.mapTo(mutableListOf()) {
        Page(it.width, it.height, ArrayList(it.items), it.pdfPage, it.style, it.margins, it.markups)
    },
    dpi = dpi,
    path = path,
    displayName = displayName,
    dirty = dirty,
    pdfFile = pdfFile,
    bookmarks = bookmarks.mapTo(mutableListOf()) { Bookmark(it.page, it.label) },
    style = style,
    margins = margins,
    flow = flow.deepCopy(),
    created = created,
    templates = templates,
)
