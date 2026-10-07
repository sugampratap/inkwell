package com.xnotes.ui.kit

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The sheet's height rules: never taller than 90% of the screen, so some scrim always shows. */
class InkSheetGeometryTest {

    @Test fun aSheetIsAtMostNineTenthsOfTheScreen() {
        assertEquals(720f, sheetMaxHeight(800.dp).value, 0.01f)
    }

    @Test fun aFixedHeightIsKeptWithinTheMax() {
        // Page setup asks for 752dp (the mockup's sheet on an 800dp screen).
        assertEquals(720f, sheetHeight(752.dp, 800.dp)!!.value, 0.01f)
        assertEquals(500f, sheetHeight(500.dp, 800.dp)!!.value, 0.01f)
    }

    @Test fun noHeightLetsTheSheetWrap() {
        assertNull(sheetHeight(null, 800.dp))
    }
}
