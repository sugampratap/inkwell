package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropLogicTest {

    @Test fun noAspectLightsFree() {
        assertEquals(CropChip.FREE, litAspect(current = null, source = 1.5))
    }

    @Test fun onAPictureWhoseOwnRatioIsAPresetOnlyOriginalLights() {
        // TI 449: a 4:3 photo cropped at 4:3 lights Original, not 4:3 as well.
        assertEquals(CropChip.ORIGINAL, litAspect(current = 4.0 / 3.0, source = 4.0 / 3.0))
        assertEquals(CropChip.ORIGINAL, litAspect(current = 1.5, source = 1.5))
    }

    @Test fun aPresetLightsWhenItIsNotThePicturesOwn() {
        assertEquals(CropChip.R4_3, litAspect(current = 4.0 / 3.0, source = 1.5))
        assertEquals(CropChip.SQUARE, litAspect(current = 1.0, source = 1.5))
        assertEquals(CropChip.R3_4, litAspect(current = 0.75, source = 1.5))
        assertEquals(CropChip.R16_9, litAspect(current = 16.0 / 9.0, source = 1.5))
    }

    @Test fun aNearMatchStillLightsAndAnOddRatioLightsNothing() {
        assertEquals(CropChip.R4_3, litAspect(current = 4.0 / 3.0 + 1e-4, source = 1.5))
        assertNull(litAspect(current = 1.7, source = 1.5))
    }

    @Test fun eachChipAsksForItsAspect() {
        assertNull(CropChip.FREE.aspectFor(1.5))
        assertEquals(1.5, CropChip.ORIGINAL.aspectFor(1.5)!!, 0.0)
        assertEquals(1.0, CropChip.SQUARE.aspectFor(1.5)!!, 0.0)
        assertEquals(16.0 / 9.0, CropChip.R16_9.aspectFor(1.5)!!, 0.0)
    }

    @Test fun ratioGlyphsFitA16DpSquare() {
        assertEquals(16 to 16, ratioGlyph(1.0))
        assertEquals(16 to 12, ratioGlyph(4.0 / 3.0))
        assertEquals(12 to 16, ratioGlyph(3.0 / 4.0))
        assertEquals(16 to 9, ratioGlyph(16.0 / 9.0))
    }
}
