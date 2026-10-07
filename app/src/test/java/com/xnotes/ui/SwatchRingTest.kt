package com.xnotes.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** The swatch ring (--sw-ring): the shared helper must equal the inline copies it replaces. */
class SwatchRingTest {

    @Test
    fun lightIsBlackAtTenPercent() = assertEquals(Color.Black.copy(alpha = 0.10f), swatchRing(dark = false))

    @Test
    fun darkIsWhiteAtEighteenPercent() = assertEquals(Color.White.copy(alpha = 0.18f), swatchRing(dark = true))
}
