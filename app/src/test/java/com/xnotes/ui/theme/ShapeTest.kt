package com.xnotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.xnotes.settings.CornerStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Corners: Rounded is the mockup's radii; Sharp and Soft scale every one of them. */
class ShapeTest {

    @Test fun roundedKeepsTheMockupRadii() {
        val s = uiShapes(CornerStyle.ROUNDED)
        assertEquals(RoundedCornerShape(8.dp), s.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), s.small)
        assertEquals(RoundedCornerShape(16.dp), s.medium)
        assertEquals(RoundedCornerShape(28.dp), s.extraLarge)
    }

    @Test fun sharpAndSoftScaleEveryRadius() {
        assertEquals(4.2f, scaledCorner(12.dp, CornerStyle.SHARP).value, 1e-4f)
        assertEquals(21f, scaledCorner(12.dp, CornerStyle.SOFT).value, 1e-4f)
        // A radius with no Material role (the segmented track's 10dp) scales the same way.
        assertEquals(17.5f, scaledCorner(10.dp, CornerStyle.SOFT).value, 1e-4f)
        assertEquals(RoundedCornerShape(49.dp), uiShapes(CornerStyle.SOFT).extraLarge)
    }
}
