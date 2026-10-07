package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings screen's catalogue and its search. */
class SettingsCatalogTest {

    private fun e(id: SettingId, title: String, desc: String, cat: String = "General") =
        SettingSearchEntry(id, title, desc, cat)

    private val entries = listOf(
        e(SettingId.FINGER_DRAWS, "Draw with finger", "When off, only the S Pen writes", "Writing & S Pen"),
        e(SettingId.PEN_BUTTON_HOLD, "Press and hold", "The tool the side button switches to", "Gestures & buttons"),
        e(SettingId.STYLUS_BUTTON_TAP, "Stylus side button (tap)", "Pens whose side button sends a click", "Gestures & buttons"),
        e(SettingId.THEME, "Theme", "Light, dark, or true black for OLED screens", "Appearance"),
        e(SettingId.PAGE_COLOUR, "Page colour", "The paper colour of new pages", "Pages & paper"),
    )

    @Test
    fun everyCategoryButAboutHoldsSettings() {
        for (c in SettingsCategory.entries) {
            val n = SettingId.entries.count { it.category == c }
            if (c == SettingsCategory.ABOUT) assertEquals(0, n) else assertTrue("$c is empty", n > 0)
        }
    }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(searchSettings("   ", entries).isEmpty())
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        assertEquals(listOf(SettingId.STYLUS_BUTTON_TAP, SettingId.PEN_BUTTON_HOLD), searchSettings("side button", entries).map { it.id })
        assertTrue(searchSettings("side colour", entries).isEmpty())
    }

    @Test
    fun matchesCategoryNamesAndIgnoresCaseAndPunctuation() {
        assertEquals(listOf(SettingId.FINGER_DRAWS), searchSettings("WRITING", entries).map { it.id })
        assertEquals(listOf(SettingId.STYLUS_BUTTON_TAP), searchSettings("button (tap", entries).map { it.id })
    }

    @Test
    fun titleHitsRankAboveDescriptionHits() {
        // "colour" is in Page colour's title, and in no other title.
        assertEquals(SettingId.PAGE_COLOUR, searchSettings("colour", entries).first().id)
        // "oled" is only in Theme's description, and still found.
        assertEquals(listOf(SettingId.THEME), searchSettings("oled", entries).map { it.id })
    }

    @Test
    fun accentsAreFolded() {
        val accented = listOf(e(SettingId.THEME, "Thème", "Clair ou sombre"))
        assertEquals(1, searchSettings("theme", accented).size)
        assertEquals(1, searchSettings("THÈME", accented).size)
    }

    @Test fun writingRowsComeInTheScreensOrder() {
        assertEquals(
            listOf(
                SettingId.FINGER_DRAWS, SettingId.LOCKED_TWO_FINGER_SCROLL, SettingId.DETECT_SHAPES, SettingId.PEN_BOX,
                SettingId.PEN_BUTTON_HOLD, SettingId.PEN_BUTTON_HOVER,
                SettingId.STYLUS_DOUBLE_TAP, SettingId.STYLUS_BUTTON_TAP, SettingId.REDMI_BUTTON_1, SettingId.REDMI_BUTTON_2,
                SettingId.TWO_FINGER_TAP, SettingId.THREE_FINGER_TAP, SettingId.ZOOM_LOCK_PAN,
            ),
            SettingId.entries.filter { it.category == SettingsCategory.WRITING },
        )
    }

    @Test fun otherStylusesFoldAndCountWhatIsSet() {
        assertEquals(0, SettingsFold.othersSet(com.xnotes.settings.Preferences()))
        assertEquals(2, SettingsFold.othersSet(com.xnotes.settings.Preferences(stylusDoubleTap = "undo", stylusButton2Tap = "redo")))
        assertTrue(SettingId.REDMI_BUTTON_1 in SettingsFold.OTHER_STYLUSES)
        assertEquals(4, SettingsFold.OTHER_STYLUSES.size)
    }
}
