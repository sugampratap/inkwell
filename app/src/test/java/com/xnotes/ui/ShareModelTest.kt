package com.xnotes.ui

import com.xnotes.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareModelTest {

    @Test fun aCanvasOffersOnlyWhatItCanMake() {
        assertEquals(listOf(ShareFormat.PDF, ShareFormat.NOTE), ShareModel.formats(isCanvas = true))
        assertEquals(listOf(ShareFormat.PDF, ShareFormat.EDITABLE_PDF, ShareFormat.NOTE, ShareFormat.IMAGES), ShareModel.formats(isCanvas = false))
    }

    @Test fun theNoteFileIsAlwaysTheWholeNote() {
        assertTrue(ShareModel.disabled(ShareFormat.NOTE, ShareRange.CURRENT))
        assertFalse(ShareModel.disabled(ShareFormat.NOTE, ShareRange.ALL))
        assertEquals(ShareFormat.PDF, ShareModel.coerce(ShareFormat.NOTE, ShareRange.CURRENT))
        assertEquals(ShareFormat.IMAGES, ShareModel.coerce(ShareFormat.IMAGES, ShareRange.CURRENT))
    }

    @Test fun saveSitsBesideWhatCanBeSaved() {
        assertTrue(ShareModel.canSave(ShareFormat.PDF, isCanvas = false, insideNote = true))
        assertTrue(ShareModel.canSave(ShareFormat.IMAGES, isCanvas = false, insideNote = true))
        // Page images need the note open: the library sheet saves PDFs only.
        assertFalse(ShareModel.canSave(ShareFormat.IMAGES, isCanvas = false, insideNote = false))
        assertFalse(ShareModel.canSave(ShareFormat.EDITABLE_PDF, isCanvas = false, insideNote = true))
        assertFalse(ShareModel.canSave(ShareFormat.PDF, isCanvas = true, insideNote = true))
    }

    @Test fun headingBookmarksApplyToThePdfsOnly() {
        assertTrue(ShareModel.headingsApply(ShareFormat.EDITABLE_PDF))
        assertFalse(ShareModel.headingsApply(ShareFormat.IMAGES))
    }

    @Test fun theShareButtonSaysWhatGoes() {
        assertEquals(ShareLabel(R.string.share_go_image), ShareModel.shareLabel(ShareFormat.IMAGES, isCanvas = false, single = true))
        assertEquals(ShareLabel(R.plurals.share_go_images, plural = true), ShareModel.shareLabel(ShareFormat.IMAGES, isCanvas = false, single = false))
        assertEquals(ShareLabel(R.string.share_go_canvas), ShareModel.shareLabel(ShareFormat.NOTE, isCanvas = true, single = false))
        assertTrue(ShareModel.single(ShareRange.CURRENT, 12))
        assertTrue(ShareModel.single(ShareRange.ALL, 1))
    }
}
