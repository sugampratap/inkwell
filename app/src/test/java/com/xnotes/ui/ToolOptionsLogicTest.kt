package com.xnotes.ui

import androidx.compose.ui.unit.dp
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.ShapeKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOptionsLogicTest {

    private val eps = 1e-3f

    @Test fun halfMillimetreStepsSnapToTheGrid() {
        // The eraser's default diameter: 48 px = 8.128 mm. A step lands on the grid (TO 427), not 0.5 past it.
        val range = 2f..160f
        assertEquals(8.5f, pageMm(stepMmPx(48f, 1, 0.5f, range)), eps)
        assertEquals(7.5f, pageMm(stepMmPx(48f, -1, 0.5f, range)), eps)
        // On the grid already: a whole step.
        assertEquals(9.0f, pageMm(stepMmPx(pagePx(8.5f), 1, 0.5f, range)), eps)
    }

    @Test fun tenthStepsAgreeWithThePenCardsStepper() {
        for (px in listOf(3f, 5f, 7.3f, 11f, 16f)) for (s in listOf(-1, 1)) {
            assertEquals("$px $s", stepWidthPx(px, s, 1f..20f), stepMmPx(px, s, 0.1f, 1f..20f), eps)
        }
    }

    @Test fun stepsClampAtTheRangeEnds() {
        assertEquals(96f, stepMmPx(95f, 1, 0.5f, 12f..96f), 0f)
        assertEquals(12f, stepMmPx(12f, -1, 0.5f, 12f..96f), 0f)
    }

    @Test fun theEraserShowsItsDiameter() {
        assertEquals("8.1 mm", eraserSizeLabel(24f))
        assertEquals("0.3 mm", eraserSizeLabel(1f))
        assertEquals("27.1 mm", eraserSizeLabel(80f))
    }

    @Test fun theEraserStepsItsDiameterByHalfMillimetres() {
        assertEquals(8.5f, pageMm(2f * stepEraserRadius(24f, 1)), eps)
        assertEquals(7.5f, pageMm(2f * stepEraserRadius(24f, -1)), eps)
        assertEquals(80f, stepEraserRadius(80f, 1), 0f)
        assertEquals(1f, stepEraserRadius(1f, -1), 0f)
        assertTrue(stepEraserRadius(79f, 1) <= 80f)
    }

    @Test fun tapeStepsAlwaysMoveAndStayWhole() {
        for (v in 12..95) {
            val up = stepTapeWidth(v.toDouble(), 1)
            assertTrue("+ from $v gave $up", up > v && up <= 96.0 && up == Math.rint(up))
        }
        for (v in 13..96) {
            val down = stepTapeWidth(v.toDouble(), -1)
            assertTrue("- from $v gave $down", down < v && down >= 12.0 && down == Math.rint(down))
        }
        // The default 32 px (5.42 mm) lands on the grid either side, 6.0 / 5.0 mm, then whole px (35 / 30).
        assertEquals(35.0, stepTapeWidth(32.0, 1), 0.0)
        assertEquals(30.0, stepTapeWidth(32.0, -1), 0.0)
    }

    @Test fun fillOpacityShowsOnlyForAFilledClosedShape() {
        assertTrue(fillOpacityShown(true, ShapeKind.RECTANGLE))
        assertFalse(fillOpacityShown(false, ShapeKind.RECTANGLE))
        assertFalse(fillOpacityShown(true, ShapeKind.LINE))
        assertFalse(fillOpacityShown(true, ShapeKind.ARROW))
        assertTrue(fillDimmed(ShapeKind.LINE))
        assertTrue(fillDimmed(ShapeKind.ARROW))
        for (k in listOf(ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.CIRCLE, ShapeKind.TRIANGLE)) assertFalse(k.name, fillDimmed(k))
    }

    @Test fun theShapePreviewDrawsMillimetresLarge() {
        // 3 px = 0.508 mm → 1.73 dp; 1 px would be 0.58 dp, held at 1 dp.
        assertEquals(1.727f, shapePreviewWidth(3f), eps)
        assertEquals(1f, shapePreviewWidth(1f), 0f)
        // The gap takes the line's width again so round caps don't close it (TO 705).
        assertArrayEquals(floatArrayOf(5.757f, 6.606f), shapePreviewDash(10f, 8f, 2f), eps)
    }

    @Test fun theQuickColoursHaveNames() {
        assertEquals(InkName.NAVY, inkName(Rgba(31, 42, 68)))
        assertEquals(InkName.BLUE, inkName(Rgba(37, 99, 235)))
        assertEquals(InkName.RED, inkName(Rgba(220, 38, 38)))
        assertEquals(InkName.GREEN, inkName(Rgba(22, 163, 74)))
        assertEquals(InkName.AMBER, inkName(Rgba(217, 119, 6)))
        // Alpha does not change the name.
        assertEquals(InkName.NAVY, inkName(Rgba(31, 42, 68, 128)))
    }

    @Test fun otherInksAreNamedByHex() {
        assertNull(inkName(Rgba(1, 2, 3)))
        assertEquals("#1F2A44", inkHex(Rgba(31, 42, 68)))
        assertEquals("#FF000A", inkHex(Rgba(255, 0, 10, 40)))
    }

    @Test fun onlyHighlightHasAnIntensity() {
        for (m in MarkupMode.entries) assertEquals(m.name, m == MarkupMode.HIGHLIGHT, markupIntensityShown(m))
    }

    @Test fun colourRowsKeepEightColumns() {
        assertEquals(listOf(GridRow(0, 5, 3)), gridRows(5, COLOUR_COLUMNS))
        assertEquals(listOf(GridRow(0, 7, 1)), gridRows(7, COLOUR_COLUMNS))
        assertEquals(listOf(GridRow(0, 8, 0)), gridRows(8, COLOUR_COLUMNS))
        assertEquals(listOf(GridRow(0, 8, 0), GridRow(8, 2, 6)), gridRows(10, COLOUR_COLUMNS))
        assertEquals(listOf(GridRow(0, 3, 0), GridRow(3, 2, 1)), gridRows(5, 3))
        assertTrue(gridRows(0, COLOUR_COLUMNS).isEmpty())
    }

    @Test fun laserGlowGrowsWithTheSetting() {
        assertEquals(listOf(GlowLayer(0.18f, 9.6f), GlowLayer(0.32f, 5.2f), GlowLayer(1f, 2f)), glowLayers(2f, 100f).map { it.round() })
        assertEquals(listOf(GlowLayer(0f, 4.4f), GlowLayer(0f, 2.8f), GlowLayer(1f, 2f)), glowLayers(2f, 0f).map { it.round() })
    }

    @Test fun previewWidthsFollowTheMockup() {
        assertEquals(2.709f, laserPreviewWidth(5f), eps)
        assertEquals(1.2f, laserPreviewWidth(1f), 0f)
        assertEquals(11.92f, highlighterPreviewWidth(16f), 0.01f)
    }

    @Test fun theTapeLineCountsStripsAndPeeledOnes() {
        assertEquals(TapeCountLine.None, tapeCountLine(0 to 0))
        assertEquals(TapeCountLine.Counts(3, 1), tapeCountLine(3 to 1))
        assertEquals(TapeCountLine.Counts(2, 2), tapeCountLine(2 to 5))
    }

    @Test fun aCompactToggleRowTakesA48DpTap() {
        // 34 dp rows reach 7 dp past each edge; the 44 dp rows of the older cards are left as they were.
        assertEquals(7.dp, toggleReach(34.dp))
        assertEquals(48.dp, 34.dp + toggleReach(34.dp) * 2)
        assertEquals(0.dp, toggleReach(44.dp))
        assertEquals(0.dp, toggleReach(56.dp))
    }

    private fun GlowLayer.round() = GlowLayer(Math.round(alpha * 1000) / 1000f, Math.round(width * 1000) / 1000f)
}
