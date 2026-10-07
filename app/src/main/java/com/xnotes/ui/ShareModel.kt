package com.xnotes.ui

import androidx.annotation.StringRes
import com.xnotes.R

/** A label from string resources: a plain string, or a plural taking the count. */
internal data class ShareLabel(@param:StringRes val res: Int, val plural: Boolean = false)

/** What the share sheet offers and says, for a note or a canvas, inside a note or from the library. */
internal object ShareModel {

    fun formats(isCanvas: Boolean): List<ShareFormat> =
        if (isCanvas) listOf(ShareFormat.PDF, ShareFormat.NOTE)
        else listOf(ShareFormat.PDF, ShareFormat.EDITABLE_PDF, ShareFormat.NOTE, ShareFormat.IMAGES)

    /** The note file is always the whole note, so it cannot follow "Page N only". */
    fun disabled(format: ShareFormat, range: ShareRange): Boolean = format == ShareFormat.NOTE && range == ShareRange.CURRENT

    /** The format kept when the range changes under it: a disabled one falls back to PDF. */
    fun coerce(format: ShareFormat, range: ShareRange): ShareFormat = if (disabled(format, range)) ShareFormat.PDF else format

    /** Save sits beside Share for a PDF, and for page images inside a note (they need it open). Never for a canvas. */
    fun canSave(format: ShareFormat, isCanvas: Boolean, insideNote: Boolean): Boolean =
        !isCanvas && (format == ShareFormat.PDF || (format == ShareFormat.IMAGES && insideNote))

    /** Bookmarks from headings applies to the two PDFs only. */
    fun headingsApply(format: ShareFormat): Boolean = format == ShareFormat.PDF || format == ShareFormat.EDITABLE_PDF

    /** One page goes: the page in view, or a note of one page. */
    fun single(range: ShareRange, pageCount: Int): Boolean = range == ShareRange.CURRENT || pageCount == 1

    /** The Share button's words. Without a page count ([counted] false, from the library) images are not counted. */
    fun shareLabel(format: ShareFormat, isCanvas: Boolean, single: Boolean, counted: Boolean = true): ShareLabel = when (format) {
        ShareFormat.PDF -> ShareLabel(R.string.share_go_pdf)
        ShareFormat.EDITABLE_PDF -> ShareLabel(R.string.share_go_editable)
        ShareFormat.NOTE -> ShareLabel(if (isCanvas) R.string.share_go_canvas else R.string.share_go_note)
        ShareFormat.IMAGES -> when {
            single -> ShareLabel(R.string.share_go_image)
            !counted -> ShareLabel(R.string.share_go_images_any)
            else -> ShareLabel(R.plurals.share_go_images, plural = true)
        }
    }

    @StringRes
    fun formatTitle(format: ShareFormat, isCanvas: Boolean, single: Boolean): Int = when (format) {
        ShareFormat.PDF -> R.string.share_pdf
        ShareFormat.EDITABLE_PDF -> R.string.share_editable_pdf
        ShareFormat.NOTE -> if (isCanvas) R.string.share_canvas_file else R.string.share_note_file
        ShareFormat.IMAGES -> if (single) R.string.share_image else R.string.share_images
    }

    @StringRes
    fun formatHint(format: ShareFormat, isCanvas: Boolean, single: Boolean, range: ShareRange): Int = when (format) {
        ShareFormat.PDF -> R.string.share_pdf_hint
        ShareFormat.EDITABLE_PDF -> R.string.share_editable_pdf_hint
        ShareFormat.NOTE -> when {
            disabled(format, range) -> R.string.share_note_whole_only
            isCanvas -> R.string.share_canvas_file_hint
            else -> R.string.share_note_file_hint
        }
        ShareFormat.IMAGES -> if (single) R.string.share_image_hint_one else R.string.share_images_hint_png
    }
}
