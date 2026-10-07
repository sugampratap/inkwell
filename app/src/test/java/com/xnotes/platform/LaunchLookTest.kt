package com.xnotes.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchLookTest {
    @Test fun appearanceNamesPickTheirLaunchLook() {
        assertEquals(LaunchLook.SYSTEM, LaunchLook.of("system"))
        assertEquals(LaunchLook.LIGHT, LaunchLook.of("light"))
        assertEquals(LaunchLook.DARK, LaunchLook.of("dark"))
        assertEquals(LaunchLook.OLED, LaunchLook.of("oled"))
        // The app reads anything else as Light (SettingsTest.malformedAppearanceFallsBackToLight).
        assertEquals(LaunchLook.LIGHT, LaunchLook.of("sepia"))
    }
}
