package com.xnotes.settings

import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.PenBox
import com.xnotes.core.tools.PenPreset
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pencil through settings and the pen box, and the Quill still where it was. */
class PencilSettingsTest {

    @Test fun thePencilsConfigRoundTrips() {
        val tuned = ToolDefaults.configFor(Tool.PENCIL).copy(baseWidth = 3.5, pressureMinFactor = 0.3, colorOverride = Rgba(20, 20, 20, 255))
        val back = Settings.fromJson(Settings(tools = mapOf(Tool.PENCIL to tuned)).toJson()).configFor(Tool.PENCIL)
        assertEquals(tuned, back)
        assertTrue(back.grain)
        assertTrue(Tool.PENCIL in ToolDefaults.persistedTools)
    }

    @Test fun aPencilSavedWithoutTheGrainKeyIsStillGraphiteAndNoOtherPenTakesIt() {
        val json = Settings().toJson()
        json.getJSONObject("tools").getJSONObject(Tool.PENCIL.id).remove("grain")
        assertTrue(Settings.fromJson(json).configFor(Tool.PENCIL).grain)
        for (t in ToolDefaults.persistedTools.filter { it != Tool.PENCIL }) {
            assertFalse("$t", Settings.fromJson(json).configFor(t).grain)
            assertFalse("$t", json.getJSONObject("tools").getJSONObject(t.id).has("grain"))
        }
    }

    @Test fun theQuillsConfigIsUntouched() {
        val quill = ToolDefaults.configFor(Tool.SPEED).copy(speedStrength = 0.5)
        val back = Settings.fromJson(Settings(tools = mapOf(Tool.SPEED to quill)).toJson()).configFor(Tool.SPEED)
        assertEquals(quill, back)
        assertTrue(Tool.SPEED in ToolDefaults.persistedTools)
    }

    @Test fun pencilsAndQuillsInThePenBoxRoundTrip() {
        val box = listOf(
            PenPreset(Tool.PENCIL, ToolDefaults.configFor(Tool.PENCIL), Rgba(31, 42, 68, 255)),
            PenPreset(Tool.SPEED, ToolDefaults.configFor(Tool.SPEED), Rgba(200, 30, 30, 255)),
        )
        assertTrue(PenBox.holds(Tool.PENCIL))
        val back = Settings.fromJson(Settings(penBox = box).toJson()).penBox
        assertEquals(box, back)
        assertTrue(back[0].config.grain)
    }

    @Test fun thePencilOrTheQuillInHandComesBack() {
        assertEquals(Tool.PENCIL, Settings.fromJson(Settings(lastTool = Tool.PENCIL).toJson()).lastTool)
        assertEquals(Tool.SPEED, Settings.fromJson(Settings(lastTool = Tool.SPEED).toJson()).lastTool)
    }

    @Test fun aStoredBarGainsThePencilHiddenAndKeepsAQuillButton() {
        // A bar saved before the pencil, with the Quill on a button of its own.
        val stored = ToolbarLayout.DEFAULT.sections.map { s ->
            s.entries.filter { it.item != ToolbarItem.PENCIL }.map { e ->
                e.item.id to (if (e.item == ToolbarItem.SPEED) true else e.visible)
            }
        }
        val back = ToolbarLayout.fromRaw(stored)
        val entries = back.sections.flatMap { it.entries }
        val pencil = entries.single { it.item == ToolbarItem.PENCIL }
        assertFalse(pencil.visible)
        val i = entries.indexOfFirst { it.item == ToolbarItem.TAPER }
        assertEquals(ToolbarItem.PENCIL, entries[i + 1].item)
        assertTrue(entries.single { it.item == ToolbarItem.SPEED }.visible)
        // The same for the canvas bar.
        val canvas = ToolbarLayout.fromRaw(
            ToolbarLayout.CANVAS_DEFAULT.sections.map { s -> s.entries.filter { it.item != ToolbarItem.PENCIL }.map { it.item.id to it.visible } },
            ToolbarLayout.CANVAS_ITEMS,
            ToolbarLayout.CANVAS_DEFAULT,
        )
        assertFalse(canvas.sections.flatMap { it.entries }.single { it.item == ToolbarItem.PENCIL }.visible)
    }

    @Test fun theBarLayoutRoundTripsThroughSettings() {
        val json: JSONObject = Settings().toJson()
        val back = Settings.fromJson(json)
        assertEquals(ToolbarLayout.DEFAULT, back.toolbarLayout)
        assertEquals(ToolbarLayout.CANVAS_DEFAULT, back.canvasToolbarLayout)
    }
}
