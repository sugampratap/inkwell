package com.xnotes.ui

import com.xnotes.core.model.Rgba
import com.xnotes.settings.MaterialColourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The colour mode cards' strips (r2_settings .st-cmp). */
class AppearanceSwatchTest {
    private val red = Rgba(0xF4, 0x43, 0x36)
    private val sea = Rgba(0x0D, 0xB5, 0x7D)
    private val slate = Rgba(0x57, 0x71, 0x8E)

    @Test fun mixingTowardWhiteLightens() {
        assertEquals(Rgba(253, 214, 211), AppearanceSwatch.mix(red, Rgba(255, 255, 255), 0.78))
        assertEquals(red, AppearanceSwatch.mix(red, Rgba(0, 0, 0), 0.0))
    }

    @Test fun singleToneShowsTint_colour_shade() {
        assertEquals(
            listOf(AppearanceSwatch.mix(red, Rgba(255, 255, 255), 0.78), red, AppearanceSwatch.mix(red, Rgba(0, 0, 0), 0.35)),
            AppearanceSwatch.strip(MaterialColourMode.SINGLE, red, sea, slate),
        )
    }

    @Test fun dualToneShowsAccentSurfaceAndAPaleSurface() {
        assertEquals(listOf(sea, slate, AppearanceSwatch.mix(slate, Rgba(255, 255, 255), 0.78)), AppearanceSwatch.strip(MaterialColourMode.DUAL, red, sea, slate))
    }

    @Test fun paperAndSystemDrawFixedStrips() {
        assertNull(AppearanceSwatch.strip(MaterialColourMode.PAPER, red, sea, slate))
        assertNull(AppearanceSwatch.strip(MaterialColourMode.SYSTEM, red, sea, slate))
    }
}
