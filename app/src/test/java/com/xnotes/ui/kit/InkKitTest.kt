package com.xnotes.ui.kit

import androidx.compose.ui.unit.Density
import com.xnotes.ui.PopoverSpec
import com.xnotes.ui.theme.InkTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class InkKitTest {

    @Test fun litRowsUseTheSelectionGreyWhereItShowsOnACard() {
        assertEquals(InkTokens.LIGHT.sel, InkTokens.LIGHT.selOnRaised)
        assertEquals(InkTokens.DARK.sel, InkTokens.DARK.selOnRaised)
    }

    @Test fun oledLightsRowsWithThePressedGreyBecauseSelEqualsTheCard() {
        // Round 2 default #5: on OLED the on-fill would equal the card (#1E1E1E), so it takes #2A2A2A.
        assertEquals(InkTokens.OLED.press, InkTokens.OLED.selOnRaised)
    }

    @Test fun popoverSpecsConvertAtTheScreensDensity() {
        val d = Density(2f)
        assertEquals(PopoverSpec(lead = 52, gap = 20, margin = 16), PopoverSpecs.ToolCard.toPx(d))
        assertEquals(PopoverSpec(lead = 16, gap = 20, margin = 16), PopoverSpecs.MoreMenu.toPx(d))
        assertEquals(PopoverSpec(lead = 16, gap = 16, margin = 16), PopoverSpecs.HeaderMenu.toPx(d))
        assertEquals(PopoverSpec(lead = 12, gap = 16, margin = 16, endAligned = true), PopoverSpecs.Insert.toPx(d))
    }
}
