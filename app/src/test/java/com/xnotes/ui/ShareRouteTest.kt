package com.xnotes.ui

import com.xnotes.core.util.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the library sends a stored file in each share format. */
class ShareRouteTest {

    @Test fun pdfFormatsRenderAPdf() {
        assertEquals(ShareRoute.PDF, ShareRoute.of(DocumentKind.NOTE, ShareFormat.PDF))
        assertEquals(ShareRoute.EDITABLE_PDF, ShareRoute.of(DocumentKind.NOTE, ShareFormat.EDITABLE_PDF))
        assertEquals(ShareRoute.PDF, ShareRoute.of(DocumentKind.CANVAS, ShareFormat.PDF))
    }

    @Test fun imagesRenderTheNotesPages() {
        assertEquals(ShareRoute.PAGE_IMAGES, ShareRoute.of(DocumentKind.NOTE, ShareFormat.IMAGES))
    }

    @Test fun theNoteFormatSendsTheFileItself() {
        assertEquals(ShareRoute.FILE, ShareRoute.of(DocumentKind.NOTE, ShareFormat.NOTE))
        assertEquals(ShareRoute.FILE, ShareRoute.of(DocumentKind.CANVAS, ShareFormat.NOTE))
    }

    @Test fun aCanvasHasNoPagesSoImagesStayTheFile() {
        // The sheet never offers Images for a canvas; if asked anyway it shares the canvas as before.
        assertEquals(ShareRoute.FILE, ShareRoute.of(DocumentKind.CANVAS, ShareFormat.IMAGES))
    }

    @Test fun pageImagesAreNamedByNoteAndPage() {
        assertEquals("Lecture 3-p01.png", ShareRoute.pageImageName("Lecture 3", 0))
        assertEquals("Lecture 3-p12.png", ShareRoute.pageImageName("Lecture 3", 11))
        assertEquals("a-p120.png", ShareRoute.pageImageName("a", 119))
    }
}
