package com.xnotes.settings

import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarEntry
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.tools.ToolbarSection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test fun penBoxRoundTripsAndStartsFull() {
        assertEquals(com.xnotes.core.tools.PenBox.DEFAULT, Settings.fromJson(JSONObject()).penBox)
        val box = listOf(
            com.xnotes.core.tools.PenPreset(
                com.xnotes.core.tools.Tool.CALLIGRAPHY,
                com.xnotes.core.tools.ToolDefaults.configFor(com.xnotes.core.tools.Tool.CALLIGRAPHY).copy(baseWidth = 7.5),
                Rgba(10, 20, 30, 255),
            ),
        )
        val back = Settings.fromJson(Settings(penBox = box, penBoxOpen = false).toJson())
        assertEquals(1, back.penBox.size)
        val p = back.penBox[0]
        assertEquals(com.xnotes.core.tools.Tool.CALLIGRAPHY, p.tool)
        assertEquals(Rgba(10, 20, 30, 255), p.color)
        assertTrue(p.matches(box[0].tool, box[0].config, box[0].color))
        assertFalse(back.penBoxOpen)
        // An emptied box stays empty rather than refilling with the defaults.
        assertEquals(emptyList<com.xnotes.core.tools.PenPreset>(), Settings.fromJson(Settings(penBox = emptyList()).toJson()).penBox)
        // A pen that cannot ink is dropped on load.
        val junk = JSONObject().put("pen_box", org.json.JSONArray().put(JSONObject().put("tool", "eraser").put("color", org.json.JSONArray().put(1).put(2).put(3).put(255))))
        assertEquals(emptyList<com.xnotes.core.tools.PenPreset>(), Settings.fromJson(junk).penBox)
    }

    @Test fun libraryFavouritesAndCoversRoundTripAndStayOutWhenUnset() {
        // Nothing starred and no cover picked writes nothing, and reads back empty.
        val bare = Settings().toJson()
        assertFalse(bare.has("favourites"))
        assertFalse(bare.has("cover_colors"))
        assertEquals(emptyList<String>(), Settings.fromJson(bare).favourites)
        assertEquals(emptyMap<String, Int>(), Settings.fromJson(bare).coverColors)
        val back = Settings.fromJson(
            Settings(favourites = listOf("a|notes/one.xnote", "a|two.xnote"), coverColors = mapOf("a|two.xnote" to 3)).toJson(),
        )
        assertEquals(listOf("a|notes/one.xnote", "a|two.xnote"), back.favourites)
        assertEquals(mapOf("a|two.xnote" to 3), back.coverColors)
        // Tolerant read: blanks and duplicates drop out of favourites, a nonsense cover index is ignored.
        val junk = JSONObject()
            .put("favourites", org.json.JSONArray().put("k").put("").put("k"))
            .put("cover_colors", JSONObject().put("x", -4).put("y", "blue").put("z", 2))
        val read = Settings.fromJson(junk)
        assertEquals(listOf("k"), read.favourites)
        assertEquals(mapOf("z" to 2), read.coverColors)
    }

    @Test fun emptyJsonYieldsDefaults() {
        val s = Settings.fromJson(JSONObject())
        assertEquals(15, s.toolbarColors.size)
        assertEquals(5, s.toolbarColorCount)
        assertEquals(0, s.activeColor)
        assertEquals(1.0, s.renderScale, 1e-9)
        assertFalse(s.sidebarVisible)
        assertEquals(PageSize.A4, s.prefs.defaultPageSize)
        assertEquals("light", s.prefs.uiAppearance)
        assertEquals(com.xnotes.canvas.ViewSettings(), s.viewDefaults)
    }

    @Test fun viewDefaultsRoundTrip() {
        val original = Settings(
            viewDefaults = com.xnotes.canvas.ViewSettings(
                mode = com.xnotes.canvas.ViewingMode.DOUBLE,
                invert = 100,
                keepImages = true,
                scrollbar = true,
            ),
        )
        val back = Settings.fromJson(original.toJson())
        assertEquals(original.viewDefaults, back.viewDefaults)
    }

    @Test fun legacyPdfDarkModeSeedsTheViewDefaults() {
        // Settings written before the View menu's Global tab: the old checkboxes migrate.
        val legacy = JSONObject().put(
            "prefs",
            JSONObject().put("pdf_dark_mode", true).put("pdf_keep_image_colors", true),
        )
        val s = Settings.fromJson(legacy)
        assertEquals(100, s.viewDefaults.invert)
        assertTrue(s.viewDefaults.keepImages)
        // Once written back, the migrated defaults persist on their own.
        val back = Settings.fromJson(s.toJson())
        assertEquals(100, back.viewDefaults.invert)
        assertTrue(back.viewDefaults.keepImages)
    }

    @Test fun roundTripPreservesValues() {
        val original = Settings(
            tools = mapOf(Tool.PEN to com.xnotes.core.tools.ToolConfig(5.0, false, 0.2, 0.1, Rgba(1, 2, 3, 255))),
            toolbarColors = listOf(Rgba(0, 230, 118), Rgba(1, 1, 1), Rgba(2, 2, 2), Rgba(3, 3, 3), Rgba(4, 4, 4)),
            activeColor = 2,
            renderScale = 1.5,
            sidebarVisible = true,
            prefs = Preferences(
                uiAppearance = "light",
                defaultPageSize = PageSize.LETTER,
                defaultPageOrientation = Orientation.LANDSCAPE,
                pageColor = Rgba(20, 20, 20),
                hidePageBorders = true,
            ),
        )
        val back = Settings.fromJson(original.toJson())
        assertEquals(5.0, back.configFor(Tool.PEN).baseWidth, 1e-9)
        assertFalse(back.configFor(Tool.PEN).pressureEnabled)
        assertEquals(2, back.activeColor)
        assertEquals(1.5, back.renderScale, 1e-9)
        assertTrue(back.sidebarVisible)
        assertEquals("light", back.prefs.uiAppearance)
        assertEquals(PageSize.LETTER, back.prefs.defaultPageSize)
        assertEquals(Orientation.LANDSCAPE, back.prefs.defaultPageOrientation)
        assertEquals(Rgba(20, 20, 20, 255), back.prefs.pageColor)
        assertTrue(back.prefs.hidePageBorders)
    }

    @Test fun theMarkupToolsModeAndIntensityRoundTrip() {
        val markup = com.xnotes.core.tools.ToolConfig(markupMode = com.xnotes.core.tools.MarkupMode.SQUIGGLY, markupIntensity = 0.8)
        val back = Settings.fromJson(Settings(tools = mapOf(Tool.MARKUP to markup)).toJson())
        assertEquals(com.xnotes.core.tools.MarkupMode.SQUIGGLY, back.configFor(Tool.MARKUP).markupMode)
        assertEquals(0.8, back.configFor(Tool.MARKUP).markupIntensity, 1e-9)
        val wild = Settings(tools = mapOf(Tool.MARKUP to markup)).toJson()
        wild.getJSONObject("tools").getJSONObject("markup").put("markup_intensity", 7.0)
        assertEquals(1.0, Settings.fromJson(wild).configFor(Tool.MARKUP).markupIntensity, 1e-9)
    }

    @Test fun theEraserTakesTextMarkupsUnlessSwitchedOff() {
        assertTrue(Settings().configFor(Tool.ERASER).eraseMarkups)
        val off = Settings().configFor(Tool.ERASER).copy(eraseMarkups = false)
        val json = Settings(tools = mapOf(Tool.ERASER to off)).toJson()
        assertFalse(Settings.fromJson(json).configFor(Tool.ERASER).eraseMarkups)
        // Settings saved before the switch existed read as on.
        json.getJSONObject("tools").getJSONObject("eraser").remove("erase_markups")
        assertTrue(Settings.fromJson(json).configFor(Tool.ERASER).eraseMarkups)
    }

    @Test fun everyTapGestureMappingRoundTrips() {
        val prefs = Preferences(
            twoFingerTap = "undo",
            threeFingerTap = "redo",
            stylusDoubleTap = "toggle_eraser",
            stylusButtonTap = "toggle_pan",
            stylusButton1Tap = "toggle_previous",
            stylusButton2Tap = "undo",
        )
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals("undo", back.twoFingerTap)
        assertEquals("redo", back.threeFingerTap)
        assertEquals("toggle_eraser", back.stylusDoubleTap)
        assertEquals("toggle_pan", back.stylusButtonTap)
        assertEquals("toggle_previous", back.stylusButton1Tap)
        assertEquals("undo", back.stylusButton2Tap)
    }

    @Test fun frontBufferingIsOnUntilItIsTurnedOff() {
        // Settings written before the switch existed, which is every install that has one.
        assertFalse(Settings.fromJson(JSONObject()).prefs.disableFrontBuffering)
        val back = Settings.fromJson(Settings(prefs = Preferences(disableFrontBuffering = true)).toJson()).prefs
        assertTrue(back.disableFrontBuffering)
    }

    @Test fun headingBookmarksAreOnUntilTurnedOff() {
        assertTrue(Settings.fromJson(JSONObject()).prefs.pdfHeadingBookmarks)
        val back = Settings.fromJson(Settings(prefs = Preferences(pdfHeadingBookmarks = false)).toJson()).prefs
        assertFalse(back.pdfHeadingBookmarks)
    }

    @Test fun customPageSizeRoundTripsAndSizesANewPage() {
        val prefs = Preferences(
            defaultPageSize = PageSize.CUSTOM,
            defaultPageOrientation = Orientation.LANDSCAPE,
            customPageWidthMm = 254.0,
            customPageHeightMm = 127.0,
        )
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals(PageSize.CUSTOM, back.defaultPageSize)
        assertEquals(254.0, back.customPageWidthMm, 1e-9)
        assertEquals(127.0, back.customPageHeightMm, 1e-9)
        // Taken as typed: the landscape chip does not swap a custom page's sides.
        val (w, h) = back.newPagePixels(150)
        assertEquals(1500.0, w, 1e-6)
        assertEquals(750.0, h, 1e-6)
    }

    @Test fun aNamedSizeStillFollowsTheOrientation() {
        val prefs = Preferences(defaultPageSize = PageSize.LEGAL, defaultPageOrientation = Orientation.LANDSCAPE)
        val (w, h) = prefs.newPagePixels(150)
        assertEquals(PageSize.mmToPx(355.6, 150), w, 1e-6)
        assertEquals(PageSize.mmToPx(215.9, 150), h, 1e-6)
    }

    @Test fun anOutOfRangeCustomSideIsPulledBackIn() {
        val o = JSONObject().put(
            "prefs",
            JSONObject().put("custom_page_width_mm", 9000.0).put("custom_page_height_mm", 0.0),
        )
        val back = Settings.fromJson(o).prefs
        assertEquals(Preferences.CUSTOM_PAGE_MAX_MM, back.customPageWidthMm, 1e-9)
        assertEquals(Preferences.CUSTOM_PAGE_MIN_MM, back.customPageHeightMm, 1e-9)
    }

    @Test fun malformedAppearanceFallsBackToLight() {
        val o = JSONObject().put("prefs", JSONObject().put("ui_appearance", "rainbow"))
        assertEquals("light", Settings.fromJson(o).prefs.uiAppearance)
        val system = JSONObject().put("prefs", JSONObject().put("ui_appearance", "system"))
        assertEquals("system", Settings.fromJson(system).prefs.uiAppearance)
    }

    @Test fun toolbarColorsPaddedToFifteen() {
        val o = JSONObject().put(
            "toolbar_colors",
            org.json.JSONArray().put(org.json.JSONArray().put(0).put(0).put(0).put(255)),
        )
        assertEquals(15, Settings.fromJson(o).toolbarColors.size)
    }

    @Test fun aNewInstallStartsWithTheB2QuickColours() {
        val d7 = listOf(
            Rgba(0x1F, 0x2A, 0x44, 255), // navy
            Rgba(0x25, 0x63, 0xEB, 255), // blue
            Rgba(0xDC, 0x26, 0x26, 255), // red
            Rgba(0x16, 0xA3, 0x4A, 255), // green
            Rgba(0xD9, 0x77, 0x06, 255), // amber
        )
        assertEquals(d7, Settings.fromJson(JSONObject()).toolbarColors.take(5))
        assertEquals(d7, Settings().toolbarColors.take(5))
        assertFalse("no violet on the bar", com.xnotes.core.tools.InkPalette.PEN_VIOLET in Settings().toolbarColors.take(5))
        assertEquals(15, Settings().toolbarColors.size)
    }

    @Test fun savedSwatchesAreKeptAcrossTheNewDefaults() {
        // Someone who used the app before D7 has the old five on disk, violet fifth: they keep them, except the
        // old default violet, which 1.4.0 swaps for the B2 amber once (the user's call, 2026-10-07).
        val before = listOf(Rgba(31, 42, 68, 255), Rgba(37, 99, 235, 255), Rgba(220, 38, 38, 255), Rgba(21, 128, 61, 255), Rgba(124, 58, 237, 255))
        val arr = org.json.JSONArray()
        before.forEach { arr.put(org.json.JSONArray().put(it.r).put(it.g).put(it.b).put(it.a)) }
        val read = Settings.fromJson(JSONObject().put("toolbar_colors", arr)).toolbarColors
        assertEquals(before.dropLast(1) + com.xnotes.core.tools.InkPalette.QUICK_AMBER, read.take(5))
        assertEquals(15, read.size)
        // And a saved bar survives a save and reload untouched.
        assertEquals(read, Settings.fromJson(Settings(toolbarColors = read).toJson()).toolbarColors)
    }

    @Test fun toolbarColorCountDefaultsToFive() {
        assertEquals(5, Settings.fromJson(JSONObject()).toolbarColorCount)
    }

    @Test fun toolbarColorCountRoundTripsAndClamps() {
        assertEquals(15, Settings.fromJson(Settings(toolbarColorCount = 15).toJson()).toolbarColorCount)
        assertEquals(1, Settings.fromJson(Settings(toolbarColorCount = 0).toJson()).toolbarColorCount)
        assertEquals(15, Settings.fromJson(Settings(toolbarColorCount = 99).toJson()).toolbarColorCount)
    }

    @Test fun rememberColorDedupesAndCaps() {
        var s = Settings()
        repeat(30) { s = s.rememberColor(Rgba(it, it, it)) }
        assertEquals(24, s.recentColors.size)
        s = s.rememberColor(Rgba(5, 5, 5))
        assertEquals(Rgba(5, 5, 5, 255), s.recentColors.first())
        assertEquals(24, s.recentColors.size)
    }

    @Test fun pageColorNullByDefault() {
        assertNull(Settings.fromJson(JSONObject()).prefs.pageColor)
    }

    @Test fun newNoteStyleEmptyByDefaultAndUnwritten() {
        val s = Settings.fromJson(JSONObject())
        assertTrue(s.newNoteStyle.isEmpty)
        assertFalse(s.toJson().has("new_note_style"))
    }

    @Test fun newNoteStyleRoundTrips() {
        val style = com.xnotes.core.model.PageStyle(
            pageColor = Rgba(255, 250, 230),
            template = "0123456789abcdef",
            patternColor = Rgba(100, 120, 140, 80),
            spacing = 48.0,
            accentColor = Rgba(200, 10, 10, 150),
            params = mapOf("rows" to 7.0),
            colors = mapOf("frame" to Rgba(1, 2, 3)),
        )
        val back = Settings.fromJson(Settings(newNoteStyle = style).toJson())
        assertEquals(style, back.newNoteStyle)
    }

    @Test fun newNoteStylePartialFieldsStayNull() {
        val style = com.xnotes.core.model.PageStyle(template = com.xnotes.core.model.PagePattern.LINES.id)
        val back = Settings.fromJson(Settings(newNoteStyle = style).toJson())
        assertEquals(style, back.newNoteStyle)
        assertNull(back.newNoteStyle.pageColor)
        assertNull(back.newNoteStyle.spacing)
    }

    @Test fun newCanvasBackgroundNullByDefaultAndUnwritten() {
        val s = Settings.fromJson(JSONObject())
        assertNull(s.newCanvasBackground)
        assertFalse(s.toJson().has("new_canvas_background"))
    }

    @Test fun newCanvasBackgroundRoundTrips() {
        val bg = com.xnotes.core.infinite.CanvasBackground(
            pattern = com.xnotes.core.model.PagePattern.DOTS,
            patternColor = Rgba(40, 60, 90, 120),
            spacing = 52.0,
            paperColor = Rgba(250, 244, 226),
        )
        val back = Settings.fromJson(Settings(newCanvasBackground = bg).toJson())
        assertEquals(bg, back.newCanvasBackground)
    }

    @Test fun newCanvasBackgroundKeepsThemePaperWhenUnset() {
        val bg = com.xnotes.core.infinite.CanvasBackground(pattern = com.xnotes.core.model.PagePattern.NONE)
        val back = Settings.fromJson(Settings(newCanvasBackground = bg).toJson())
        assertEquals(bg, back.newCanvasBackground)
        assertNull(back.newCanvasBackground?.paperColor)
    }

    @Test fun fingerDrawAutoCheckedDefaultsFalse() {
        assertFalse(Settings.fromJson(JSONObject()).fingerDrawAutoChecked)
    }

    @Test fun fingerDrawAutoCheckedRoundTrips() {
        val back = Settings.fromJson(Settings(fingerDrawAutoChecked = true).toJson())
        assertTrue(back.fingerDrawAutoChecked)
    }

    @Test fun toolbarLayoutDefaultsWhenAbsent() {
        assertEquals(ToolbarLayout.DEFAULT, Settings.fromJson(JSONObject()).toolbarLayout)
    }

    @Test fun toolbarLayoutRoundTrips() {
        val custom = ToolbarLayout.DEFAULT.toggleVisible(2, 0).addSection()
        val back = Settings.fromJson(Settings(toolbarLayout = custom).toJson())
        assertEquals(custom, back.toolbarLayout)
    }

    @Test fun tapGesturesDefaultToUndoAndRedo() {
        val p = Preferences.fromJson(JSONObject())
        assertEquals("undo", p.twoFingerTap)
        assertEquals("redo", p.threeFingerTap)
        assertTrue(p.detectShapes)
    }

    @Test fun olderSettingsPickUpTheNewGestureDefaultsButKeepChoices() {
        val old = JSONObject().put("ui_appearance", "light")
            .put("two_finger_tap", "none").put("three_finger_tap", "toggle_eraser").put("detect_shapes", false)
        val p = Preferences.fromJson(old)
        assertEquals("undo", p.twoFingerTap)
        assertEquals("toggle_eraser", p.threeFingerTap)
        assertTrue(p.detectShapes)
        // Written back at the current revision, a choice made afterwards sticks.
        val again = Preferences.fromJson(p.copy(twoFingerTap = "none", detectShapes = false).toJson())
        assertEquals("none", again.twoFingerTap)
        assertFalse(again.detectShapes)
    }

    @Test fun tapGesturesRoundTrip() {
        val back = Preferences.fromJson(
            Preferences(twoFingerTap = "undo", threeFingerTap = "toggle_eraser").toJson(),
        )
        assertEquals("undo", back.twoFingerTap)
        assertEquals("toggle_eraser", back.threeFingerTap)
    }

    @Test fun tapGestureMalformedFallsBackToTheDefault() {
        val o = JSONObject().put("two_finger_tap", "explode").put("prefs_rev", Preferences.REVISION)
        assertEquals("undo", Preferences.fromJson(o).twoFingerTap)
    }

    @Test fun startFullscreenNullByDefaultAndUnwritten() {
        assertNull(Preferences.fromJson(JSONObject()).startFullscreen)
        assertFalse(Preferences().toJson().has("start_fullscreen"))
    }

    @Test fun classicPaletteKeysAreDroppedOnLoad() {
        val o = JSONObject().put("accent_color", "#ff8a1e").put("oled_palette_style", "classic").put("dark_palette_style", "classic")
        val json = Preferences.fromJson(o).toJson()
        for (key in listOf("accent_color", "system_palette_style", "dark_palette_style", "light_palette_style", "oled_palette_style")) {
            assertFalse(key, json.has(key))
        }
    }

    @Test fun materialDefaultsUsePaperWithFirstPresetsReady() {
        val defaults = Preferences.fromJson(JSONObject())
        assertEquals(MaterialColourMode.PAPER, defaults.materialMode)
        assertEquals(Rgba(244, 67, 54), defaults.materialSingleSeed)
        assertEquals(Rgba(154, 124, 66), defaults.materialDualSeed)
        assertEquals(Rgba(61, 117, 230), defaults.materialSurfaceSeed)
        for (key in listOf("material_mode", "material_seed", "material_single_seed", "material_dual_seed", "material_surface_seed")) {
            assertFalse(defaults.toJson().has(key))
        }
    }

    @Test fun legacySingleToneMigratesOnlyItsOwnAccent() {
        val loaded = Preferences.fromJson(JSONObject().put("material_seed", "#2196f3"))
        assertEquals(MaterialColourMode.SINGLE, loaded.materialMode)
        assertEquals(Rgba(33, 150, 243), loaded.materialSingleSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        assertEquals(loaded, Preferences.fromJson(loaded.toJson()))
    }

    @Test fun legacyDualToneMigratesOnlyItsOwnColours() {
        val loaded = Preferences.fromJson(JSONObject()
            .put("material_dual_tone", true).put("material_seed", "#800000")
            .put("material_surface_seed", "#000080"))
        assertEquals(MaterialColourMode.DUAL, loaded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, loaded.materialSingleSeed)
        assertEquals(Rgba(128, 0, 0), loaded.materialDualSeed)
        assertEquals(Rgba(0, 0, 128), loaded.materialSurfaceSeed)
        assertEquals(loaded, Preferences.fromJson(loaded.toJson()))
    }

    @Test fun legacyDualToneRetainsOldFallbackColours() {
        val json = JSONObject().put("material_dual_tone", true)
        val loaded = Preferences.fromJson(json)
        assertEquals(Preferences.DEFAULT_ACCENT, loaded.materialDualSeed)
        assertEquals(Rgba(33, 150, 243), loaded.materialSurfaceSeed)
        val classicAccent = Preferences.fromJson(json.put("accent_color", "#123456"))
        assertEquals(Rgba(18, 52, 86), classicAccent.materialDualSeed)
        assertEquals(classicAccent, Preferences.fromJson(classicAccent.toJson()))
    }

    @Test fun customMaterialOptionsDefaultForgivingly() {
        for (value in listOf<Any>(JSONObject.NULL, "unknown", "medium", "high", 123, JSONObject())) {
            val p = Preferences.fromJson(JSONObject().put("material_style", value).put("material_contrast", value))
            assertEquals(MaterialStyle.TONAL_SPOT, p.materialStyle)
            assertFalse(p.toJson().has("material_contrast"))
        }
        val defaults = Preferences.fromJson(JSONObject())
        assertEquals(MaterialStyle.TONAL_SPOT, defaults.materialStyle)
        assertFalse(defaults.toJson().has("material_style"))
        assertFalse(defaults.toJson().has("material_contrast"))
    }

    @Test fun materialOptionsDefaultForgivingly() {
        for (value in listOf<Any>(JSONObject.NULL, "unknown", 123, JSONObject())) {
            val loaded = Preferences.fromJson(JSONObject()
                .put("material_mode", value).put("material_dual_tone", value)
                .put("material_seed", value).put("material_single_seed", value)
                .put("material_dual_seed", value).put("material_surface_seed", value))
            assertEquals(MaterialColourMode.PAPER, loaded.materialMode)
            assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, loaded.materialSingleSeed)
            assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
            assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        }
    }

    @Test fun materialModesKeepSeparateColoursAcrossSwitchesAndRestarts() {
        fun reload(p: Preferences) = Preferences.fromJson(p.toJson())
        val single = reload(Preferences(materialMode = MaterialColourMode.SINGLE)
            .copy(materialSingleSeed = Rgba(4, 5, 6)))
        val firstDual = reload(single.copy(materialMode = MaterialColourMode.DUAL))
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, firstDual.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, firstDual.materialSurfaceSeed)
        val dual = reload(firstDual.copy(materialDualSeed = Rgba(7, 8, 9), materialSurfaceSeed = Rgba(10, 11, 12)))
        val backToSingle = reload(dual.copy(materialMode = MaterialColourMode.SINGLE))
        assertEquals(Rgba(4, 5, 6), backToSingle.materialSingleSeed)
        val changedSingle = reload(backToSingle.copy(materialSingleSeed = Rgba(13, 14, 15)))
        val system = reload(changedSingle.copy(materialMode = MaterialColourMode.SYSTEM))
        assertFalse(system.toJson().has("material_seed"))
        assertFalse(system.toJson().has("material_dual_tone"))
        val backToDual = reload(system.copy(materialMode = MaterialColourMode.DUAL))
        assertEquals(Rgba(7, 8, 9), backToDual.materialDualSeed)
        assertEquals(Rgba(10, 11, 12), backToDual.materialSurfaceSeed)
        assertEquals(Rgba(13, 14, 15), backToDual.materialSingleSeed)
    }

    @Test fun firstSingleToneDoesNotInheritDualTone() {
        val dual = Preferences(materialMode = MaterialColourMode.DUAL,
            materialDualSeed = Rgba(4, 5, 6), materialSurfaceSeed = Rgba(7, 8, 9))
        val single = Preferences.fromJson(dual.copy(materialMode = MaterialColourMode.SINGLE).toJson())
        assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, single.materialSingleSeed)
        assertEquals(dual.materialDualSeed, single.materialDualSeed)
        assertEquals(dual.materialSurfaceSeed, single.materialSurfaceSeed)
    }

    @Test fun materialColoursAndStylesSurviveAllModesAndAppearanceSwitches() {
        for (style in MaterialStyle.entries) {
            for (mode in MaterialColourMode.entries) {
                val p = Preferences(materialMode = mode, materialSingleSeed = Rgba(128, 0, 0),
                    materialDualSeed = Rgba(0, 128, 0), materialSurfaceSeed = Rgba(0, 0, 128), materialStyle = style)
                assertEquals(p, Preferences.fromJson(p.toJson()))
                for (appearance in listOf("light", "dark", "oled", "system")) {
                    val changed = p.copy(uiAppearance = appearance)
                    assertEquals(changed, Preferences.fromJson(changed.toJson()))
                }
            }
        }
    }

    @Test fun materialContrastRoundTripsAndRejectsOutOfRange() {
        for (level in listOf(-1.0, -0.3, 0.5, 1.0)) {
            val p = Preferences(materialContrast = level)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        assertEquals(0.0, Preferences.fromJson(JSONObject().put("material_contrast", 1.5)).materialContrast, 0.0)
    }

    @Test fun cornerStyleRoundTripsAndDefaultsToRounded() {
        for (style in CornerStyle.entries) {
            val p = Preferences(cornerStyle = style)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        assertEquals(CornerStyle.ROUNDED, Preferences.fromJson(JSONObject().put("corner_style", "blobby")).cornerStyle)
    }

    @Test fun toolbarLookRoundTripsAndDefaults() {
        for (size in ToolbarSize.entries) for (position in ToolbarPosition.entries) for (floating in listOf(false, true)) {
            val p = Preferences(toolbarLook = ToolbarLook(position, size, floating))
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        val junk = JSONObject().put("toolbar_size", "huge").put("toolbar_position", "middle")
        assertEquals(ToolbarLook(), Preferences.fromJson(junk).toolbarLook)
        assertFalse(Preferences().toJson().has("toolbar_size"))
        assertFalse(Preferences().toJson().has("toolbar_position"))
        assertFalse(Preferences().toJson().has("toolbar_floating"))
    }

    @Test fun defaultPresetsRoundTripInEveryMode() {
        for (mode in MaterialColourMode.entries) {
            val p = Preferences(materialMode = mode)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
    }

    @Test fun startFullscreenRoundTrips() {
        assertEquals(false, Preferences.fromJson(Preferences(startFullscreen = false).toJson()).startFullscreen)
        assertEquals(true, Preferences.fromJson(Preferences(startFullscreen = true).toJson()).startFullscreen)
    }

    @Test fun markdownInputDefaultsOnAndRoundTrips() {
        assertTrue(Preferences().markdownInput)
        assertTrue(Settings.fromJson(JSONObject()).prefs.markdownInput)
        val off = Settings(prefs = Preferences(markdownInput = false))
        assertFalse(Settings.fromJson(off.toJson()).prefs.markdownInput)
    }

    @Test fun slashCommandsDefaultsOnAndRoundTrips() {
        assertTrue(Preferences().slashCommands)
        assertTrue(Settings.fromJson(JSONObject()).prefs.slashCommands)
        val off = Settings(prefs = Preferences(slashCommands = false))
        assertFalse(Settings.fromJson(off.toJson()).prefs.slashCommands)
    }

    @Test fun theTwoTypingPreferencesAreIndependent() {
        val s = Settings(prefs = Preferences(markdownInput = false, slashCommands = true))
        val back = Settings.fromJson(s.toJson()).prefs
        assertFalse(back.markdownInput)
        assertTrue(back.slashCommands)
    }

    @Test fun resettingEverySettingKeepsTheToolbarLayoutsAndColours() {
        val note = ToolbarLayout.DEFAULT.toggleVisible(1, 0).addSection()
        val canvas = ToolbarLayout.CANVAS_DEFAULT.addSection()
        val before = Settings(
            toolbarLayout = note,
            canvasToolbarLayout = canvas,
            toolbarColorCount = 9,
            prefs = Preferences(fingerDraws = true, trashDays = 7),
        )
        // Reset all settings replaces the preferences only (Editor.applyPreferences: settings.copy(prefs = p)).
        val back = Settings.fromJson(before.copy(prefs = Preferences()).toJson())
        assertEquals(note, back.toolbarLayout)
        assertEquals(canvas, back.canvasToolbarLayout)
        assertEquals(9, back.toolbarColorCount)
        assertFalse(back.prefs.fingerDraws)
        assertEquals(Preferences().trashDays, back.prefs.trashDays)
    }
    // --- two fingers to scroll while zoom is locked ---

    @Test fun lockedTwoFingerScrollIsOnByDefaultAndForOlderSettings() {
        assertTrue(Preferences().lockedTwoFingerScroll)
        assertTrue(Preferences.fromJson(JSONObject()).lockedTwoFingerScroll)
        // Settings saved before the switch existed (no key) pick up the new default.
        val older = JSONObject().put("finger_draws", false).put("zoom_lock_pan", "single").put("prefs_rev", Preferences.REVISION)
        assertTrue(Preferences.fromJson(older).lockedTwoFingerScroll)
    }

    @Test fun lockedTwoFingerScrollRoundTrips() {
        val off = Preferences(lockedTwoFingerScroll = false)
        assertFalse(off.toJson().getBoolean("locked_two_finger_scroll"))
        assertFalse(Preferences.fromJson(off.toJson()).lockedTwoFingerScroll)
        assertTrue(Preferences.fromJson(Preferences(lockedTwoFingerScroll = true).toJson()).lockedTwoFingerScroll)
    }

    @Test fun theZoomLockPanRowShowsWhatAFingerActuallyDoes() {
        assertEquals("double", Preferences(zoomLockPan = "single", lockedTwoFingerScroll = true).zoomLockPanShown)
        assertEquals("single", Preferences(zoomLockPan = "single", lockedTwoFingerScroll = false).zoomLockPanShown)
        assertEquals("double", Preferences(zoomLockPan = "double", lockedTwoFingerScroll = true).zoomLockPanShown)
        assertEquals("none", Preferences(zoomLockPan = "none", lockedTwoFingerScroll = true).zoomLockPanShown)
        assertEquals("none", Preferences(zoomLockPan = "none", lockedTwoFingerScroll = false).zoomLockPanShown)
    }

    @Test fun theSwitchAndTheZoomLockPanRowNeverDisagree() {
        // Turning the switch off lets one finger scroll again, whatever the row said.
        val off = Preferences(zoomLockPan = "double", lockedTwoFingerScroll = true).withLockedTwoFingerScroll(false)
        assertFalse(off.lockedTwoFingerScroll)
        assertEquals("single", off.zoomLockPanShown)
        // ...but "none" (nothing scrolls) is a choice of its own and stays.
        assertEquals("none", Preferences(zoomLockPan = "none").withLockedTwoFingerScroll(false).zoomLockPanShown)
        // Turning it on makes one finger hold still.
        val on = Preferences(zoomLockPan = "single", lockedTwoFingerScroll = false).withLockedTwoFingerScroll(true)
        assertEquals("double", on.zoomLockPanShown)
        // Picking "One finger" in the row turns the switch off; "Two fingers" turns it on.
        val one = Preferences().withZoomLockPan("single")
        assertFalse(one.lockedTwoFingerScroll)
        assertEquals("single", one.zoomLockPanShown)
        val two = one.withZoomLockPan("double")
        assertTrue(two.lockedTwoFingerScroll)
        assertEquals("double", two.zoomLockPanShown)
        assertEquals("none", two.withZoomLockPan("none").zoomLockPanShown)
    }

    // --- zoom lock joins the bar once ---

    private fun layoutJson(layout: ToolbarLayout, rev: Int): JSONObject {
        val o = Settings(toolbarLayout = layout, canvasToolbarLayout = ToolbarLayout.CANVAS_DEFAULT).toJson()
        o.getJSONObject("toolbar_layout").put("rev", rev)
        return o
    }

    /** The bar as it shipped before zoom lock was on it: hidden in the last section. */
    private fun withZoomLockHidden(layout: ToolbarLayout): ToolbarLayout {
        val without = layout.sections.map { s -> ToolbarSection(s.entries.filter { it.item != ToolbarItem.ZOOM_LOCK }) }
        return ToolbarLayout(without.dropLast(1) + ToolbarSection(without.last().entries + ToolbarEntry(ToolbarItem.ZOOM_LOCK, visible = false)))
    }

    @Test fun aLayoutSavedBeforeZoomLockWasOnTheBarGainsItOnce() {
        val old = withZoomLockHidden(ToolbarLayout.DEFAULT.toggleVisible(1, 0)) // a user's own tweak
        val loaded = Settings.fromJson(layoutJson(old, 2)).toolbarLayout
        val first = loaded.sections[0].entries
        assertEquals(listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK), first.map { it.item })
        assertTrue(first.last().visible)
        assertFalse(loaded.sections[1].entries[0].visible) // the user's tweak survives
        // Hidden again by the user and saved, it stays hidden: the migration has run.
        val hidden = loaded.toggleVisible(0, 2)
        val again = Settings.fromJson(Settings(toolbarLayout = hidden).toJson()).toolbarLayout
        assertEquals(hidden, again)
        assertFalse(again.sections[0].entries[2].visible)
    }

    @Test fun theCanvasLayoutGainsZoomLockOnceToo() {
        val old = withZoomLockHidden(ToolbarLayout.CANVAS_DEFAULT)
        val o = Settings(canvasToolbarLayout = old).toJson()
        o.getJSONObject("canvas_toolbar_layout").put("rev", 2)
        val loaded = Settings.fromJson(o).canvasToolbarLayout
        assertEquals(listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK), loaded.sections[0].entries.map { it.item })
        assertTrue(loaded.sections[0].entries[2].visible)
        val hidden = loaded.toggleVisible(0, 2)
        assertEquals(hidden, Settings.fromJson(Settings(canvasToolbarLayout = hidden).toJson()).canvasToolbarLayout)
    }

    @Test fun aLayoutFromBeforeTheRedesignStillStartsAgainFromTheDefault() {
        val old = withZoomLockHidden(ToolbarLayout.DEFAULT.toggleVisible(1, 0))
        assertEquals(ToolbarLayout.DEFAULT, Settings.fromJson(layoutJson(old, 1)).toolbarLayout)
    }

    @Test fun playbackSpeedDefaultsToNormalAndIsNotWrittenThen() {
        assertEquals(1f, Preferences().playbackSpeed)
        assertEquals(1f, Settings.fromJson(JSONObject()).prefs.playbackSpeed)
        assertFalse(Preferences().toJson().has("playback_speed"))
    }

    @Test fun everyPlaybackSpeedRoundTrips() {
        for (v in PlaybackSpeed.ALL) {
            assertEquals(v, Settings.fromJson(Settings(prefs = Preferences(playbackSpeed = v)).toJson()).prefs.playbackSpeed)
        }
        assertEquals(1.5, Preferences(playbackSpeed = 1.5f).toJson().getDouble("playback_speed"), 0.0)
    }

    @Test fun aStoredPlaybackSpeedIsReadForgivingly() {
        fun read(v: Any) = Preferences.fromJson(JSONObject().put("playback_speed", v)).playbackSpeed
        assertEquals(1f, read("fast"))
        assertEquals(1.25f, read(1.3))
        assertEquals(2f, read(9.0))
        assertEquals(1f, read(-1.0))
        assertEquals(0.75f, read(0.75))
    }

    @Test fun resettingEverySettingResetsThePlaybackSpeed() {
        val before = Settings(prefs = Preferences(playbackSpeed = 2f))
        assertEquals(1f, Settings.fromJson(before.copy(prefs = Preferences()).toJson()).prefs.playbackSpeed)
    }

    @Test fun aSettingsDraftKeepsTheLiveSpeedButResetAllDoesNot() {
        val live = Preferences(playbackSpeed = 1.5f)
        // Opened before the player changed the speed, so the draft still says 1×.
        val draft = Preferences(trashDays = 7)
        assertEquals(1.5f, keepLiveSpeed(draft, live).playbackSpeed)
        assertEquals(7, keepLiveSpeed(draft, live).trashDays)
        assertEquals(1f, keepLiveSpeed(Preferences(), live, resetAll = true).playbackSpeed)
    }

    @Test fun aDraftThatIsAllFactoryValuesStillKeepsTheLiveSpeed() {
        // The only changed setting was flipped back to factory: that is not Reset all, so the player's 1.5× stays.
        val live = Preferences(playbackSpeed = 1.5f, trashDays = 7)
        assertEquals(1.5f, keepLiveSpeed(Preferences(), live).playbackSpeed)
        assertEquals(Preferences().trashDays, keepLiveSpeed(Preferences(), live).trashDays)
    }

    @Test
    fun aPreB2BarSwapsItsVioletForAmberOnce() {
        val violet = com.xnotes.core.tools.InkPalette.PEN_VIOLET
        val amber = com.xnotes.core.tools.InkPalette.QUICK_AMBER
        val saved = Settings(toolbarColors = listOf(com.xnotes.core.tools.InkPalette.INK, violet)).toJson()
        saved.remove("palette_b2")
        val migrated = Settings.fromJson(saved)
        assertEquals(amber, migrated.toolbarColors[1])
        // Once migrated, a violet the user picks again is theirs.
        val again = Settings.fromJson(migrated.copy(toolbarColors = listOf(violet) + migrated.toolbarColors.drop(1)).toJson())
        assertEquals(violet, again.toolbarColors[0])
    }
}
