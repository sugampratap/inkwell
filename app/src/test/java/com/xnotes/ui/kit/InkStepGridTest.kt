package com.xnotes.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One value, two controls (TO 424–431): − / + land on the step grid, round((v + step·d) / step) · step, clamped;
 * − is off at the bottom of the range and + at the top. Task 6 adds each Round 3 caller's own grid.
 */
class InkStepGridTest {

    private val eraserMm = 0.3f..27.1f

    @Test
    fun anOffGridValueStepsOntoTheGrid() {
        assertEquals(8.5f, InkStepGrid.step(8.1f, 1, 0.5f, eraserMm), 1e-5f)
        assertEquals(7.5f, InkStepGrid.step(8.1f, -1, 0.5f, eraserMm), 1e-5f)
    }

    @Test
    fun anOnGridValueMovesOneStep() {
        assertEquals(9.0f, InkStepGrid.step(8.5f, 1, 0.5f, eraserMm), 1e-5f)
        assertEquals(8.0f, InkStepGrid.step(8.5f, -1, 0.5f, eraserMm), 1e-5f)
    }

    @Test
    fun zeroStepsOnlySnapsAndHalvesRoundUp() {
        assertEquals(5.0f, InkStepGrid.step(5.3f, 0, 1f, 0f..10f), 1e-5f)
        // JavaScript's Math.round, which the mockup uses: .5 goes up.
        assertEquals(6.0f, InkStepGrid.step(5.5f, 0, 1f, 0f..10f), 1e-5f)
    }

    @Test
    fun theResultIsClampedToTheRange() {
        assertEquals(27.1f, InkStepGrid.step(26.9f, 1, 0.5f, eraserMm), 1e-5f)
        assertEquals(0.3f, InkStepGrid.step(0.4f, -1, 0.5f, eraserMm), 1e-5f)
    }

    @Test
    fun minusIsOffAtTheBottomAndPlusAtTheTop() {
        assertFalse(InkStepGrid.canStepDown(0.3f, eraserMm))
        assertTrue(InkStepGrid.canStepDown(0.8f, eraserMm))
        assertFalse(InkStepGrid.canStepUp(27.1f, eraserMm))
        assertTrue(InkStepGrid.canStepUp(26.6f, eraserMm))
        // Float noise at an end still counts as the end.
        assertFalse(InkStepGrid.canStepUp(27.1f - 1e-5f, eraserMm))
    }

    @Test
    fun aStepMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { InkStepGrid.step(1f, 1, 0f, 0f..2f) }
    }

    @Test
    fun part4EraserAndTapeStepOnTheHalfMillimetre() {
        // From the eraser's smallest size, + lands on the grid (TO 615, bindVal step .5): 0.3 → 1.0, not 0.8.
        assertEquals(1.0f, InkStepGrid.step(0.3f, 1, 0.5f, eraserMm), 1e-5f)
        assertEquals(1.5f, InkStepGrid.step(1.0f, 1, 0.5f, eraserMm), 1e-5f)
    }

    @Test
    fun part4ThicknessStepsOnTheTenth() {
        // The thickness stepper's 0.1 mm grid: an off-grid 1.23 mm from the slider goes to 1.3 / 1.1.
        assertEquals(1.3f, InkStepGrid.step(1.23f, 1, 0.1f, 0.1f..20f), 1e-5f)
        assertEquals(1.1f, InkStepGrid.step(1.23f, -1, 0.1f, 0.1f..20f), 1e-5f)
        assertEquals(0.4f, InkStepGrid.step(0.3f, 1, 0.1f, 0.1f..20f), 1e-5f)
    }

    @Test
    fun part6TextSizeStepsByOnePoint() {
        // The text card's Size, 6–96 pt in 1 pt steps (TX 1265).
        val pt = 6f..96f
        assertEquals(16f, InkStepGrid.step(15f, 1, 1f, pt), 1e-5f)
        assertEquals(6f, InkStepGrid.step(6f, -1, 1f, pt), 1e-5f)
        assertFalse(InkStepGrid.canStepDown(6f, pt))
        assertFalse(InkStepGrid.canStepUp(96f, pt))
    }

    @Test
    fun part7TablePaddingAndLines() {
        // Table style card (TI 1009): padding 0–24 in 1 pt steps; lines 0.25–6 in 0.25 pt steps.
        val padding = 0f..24f
        val lines = 0.25f..6f
        assertEquals(5f, InkStepGrid.step(4f, 1, 1f, padding), 1e-5f)
        assertFalse(InkStepGrid.canStepDown(0f, padding))
        assertEquals(1.0f, InkStepGrid.step(0.75f, 1, 0.25f, lines), 1e-5f)
        assertEquals(0.25f, InkStepGrid.step(0.25f, -1, 0.25f, lines), 1e-5f)
        assertFalse(InkStepGrid.canStepDown(0.25f, lines))
        assertFalse(InkStepGrid.canStepUp(6f, lines))
    }
}
