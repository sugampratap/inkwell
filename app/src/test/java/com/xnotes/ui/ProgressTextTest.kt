package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The export progress line: MainActivity reports (done, total, "page"|"item"); a negative total is the PDF write, done in permille. */
class ProgressTextTest {

    @Test fun nothingCountedYetIsPreparing() {
        assertEquals(ExportStage.Preparing, ProgressText.exportStage(0, 0, "page"))
        assertNull(ProgressText.exportFraction(0, 0))
    }

    @Test fun pagesCountUpThenTheWriteStarts() {
        assertEquals(ExportStage.Step(4, 12, items = false), ProgressText.exportStage(4, 12, "page"))
        assertEquals(4f / 12f, ProgressText.exportFraction(4, 12)!!, 1e-6f)
        assertEquals(ExportStage.Finishing(12, items = true), ProgressText.exportStage(12, 12, "item"))
    }

    @Test fun theWriteReportsAPercentage() {
        assertEquals(ExportStage.Writing(40), ProgressText.exportStage(400, -1, "page"))
        assertEquals(0.4f, ProgressText.exportFraction(400, -1)!!, 1e-6f)
        assertEquals(1f, ProgressText.exportFraction(2000, -1)!!, 0f)
    }

    @Test fun aBatchFillsByFilesDone() {
        assertEquals(0.5f, ProgressText.batchFraction(2, 4), 0f)
        assertEquals(0f, ProgressText.batchFraction(0, 0), 0f)
    }
}
