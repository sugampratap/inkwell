package com.xnotes.settings

import com.xnotes.ui.SEL_BAR_DEFAULT
import com.xnotes.ui.SelAction
import com.xnotes.ui.selectionBarChoice
import com.xnotes.ui.selectionBarIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › General › Selection bar in the settings file (its own file, so the merge with SettingsTest stays easy). */
class SelectionBarSettingTest {

    @Test fun anOldSettingsFileLoadsTheDefaultBar() {
        val old = Settings.fromJson(JSONObject())
        assertNull(old.selectionBar)
        assertEquals(SEL_BAR_DEFAULT, selectionBarChoice(old.selectionBar))
        // The default is not written at all, so a later default reaches it.
        assertFalse(Settings().toJson().has("selection_bar"))
    }

    @Test fun aChosenBarRoundTrips() {
        val ids = selectionBarIds(setOf(SelAction.STYLE, SelAction.CUT, SelAction.DUPLICATE, SelAction.LOCK))
        val back = Settings.fromJson(Settings(selectionBar = ids).toJson())
        assertEquals(listOf("style", "cut", "duplicate", "lock"), back.selectionBar)
        assertEquals(setOf(SelAction.STYLE, SelAction.CUT, SelAction.DUPLICATE, SelAction.LOCK), selectionBarChoice(back.selectionBar))
    }

    @Test fun anEmptiedBarStaysEmpty() {
        val back = Settings.fromJson(Settings(selectionBar = emptyList()).toJson())
        assertEquals(emptyList<String>(), back.selectionBar)
        assertEquals(emptySet<SelAction>(), selectionBarChoice(back.selectionBar))
    }

    @Test fun blanksAndRepeatsDropOutAndUnknownIdsAreSkipped() {
        val junk = JSONObject().put("selection_bar", JSONArray().put("cut").put("").put("cut").put("teleport").put("lock"))
        val read = Settings.fromJson(junk)
        assertEquals(listOf("cut", "teleport", "lock"), read.selectionBar)
        assertEquals(setOf(SelAction.CUT, SelAction.LOCK), selectionBarChoice(read.selectionBar))
    }
}
