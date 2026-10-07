package com.xnotes.ui

import com.xnotes.core.util.DocumentKind

/**
 * How a stored file goes out in each [ShareFormat], decided by its [DocumentKind]: the library's share
 * reads the file, it does not open it. A canvas has no pages, so Images (which its sheet never offers)
 * stays the canvas file, as it always was.
 */
internal enum class ShareRoute {
    PDF,
    EDITABLE_PDF,

    /** Each page rendered to a PNG, as Share › Images does inside the note. */
    PAGE_IMAGES,

    /** The bundle itself, byte for byte. */
    FILE,
    ;

    companion object {
        fun of(kind: DocumentKind, format: ShareFormat): ShareRoute = when (format) {
            ShareFormat.PDF -> PDF
            ShareFormat.EDITABLE_PDF -> EDITABLE_PDF
            ShareFormat.IMAGES -> if (kind == DocumentKind.CANVAS) FILE else PAGE_IMAGES
            ShareFormat.NOTE -> FILE
        }

        /** A page image's file name: the note, then its 1-based page number, two digits at least. */
        fun pageImageName(stem: String, index: Int): String = "%s-p%02d.png".format(stem, index + 1)
    }
}
