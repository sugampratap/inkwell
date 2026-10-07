package com.xnotes.ui

import com.xnotes.core.tools.Tool
import org.junit.Assert.assertEquals
import org.junit.Test

class PenBoxRailTest {

    @Test
    fun aPenSampleIsOnePlusTwoPointFourPerMillimetre() {
        // W 1198-1201: stroke-width = 1 + mm × 2.4.
        assertEquals(2.2f, railSampleWidth(Tool.PEN, 0.5f), 1e-4f)
        assertEquals(3.88f, railSampleWidth(Tool.BALLPOINT, 1.2f), 1e-4f)
    }

    @Test
    fun aHighlighterSampleIsABandThatStopsAtFourteen() {
        assertEquals(5.5f, railSampleWidth(Tool.HIGHLIGHTER, 1.0f), 1e-4f)
        assertEquals(14f, railSampleWidth(Tool.HIGHLIGHTER, 6.8f), 1e-4f)
    }

    @Test
    fun theCaptionNumberIsTheSampleNumber() {
        // The sample's thickness follows the caption, so both read the same rounded millimetres.
        assertEquals(0.5f, railMm(widthValueMm(3f)), 1e-4f)
    }
}
