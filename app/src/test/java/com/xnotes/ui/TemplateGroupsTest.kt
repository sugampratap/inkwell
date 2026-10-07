package com.xnotes.ui

import com.xnotes.core.model.PageTemplates
import com.xnotes.platform.TemplateLibrary.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateGroupsTest {

    private val bundled = mapOf(
        "calligraphy" to listOf("lettering", "calligraphy"), "cornell" to listOf("lines", "study"),
        "daily-planner" to listOf("planner"), "dot-grid" to listOf("dots", "journal"),
        "engineering" to listOf("grid", "math", "engineering"), "guitar-tab" to listOf("music", "guitar"),
        "hexagons" to listOf("grid", "games", "chemistry"), "isometric-dots" to listOf("dots", "drawing", "isometric"),
        "isometric" to listOf("grid", "drawing", "isometric"), "musical-staves" to listOf("music"),
        "perspective" to listOf("drawing", "perspective"), "pleading" to listOf("legal", "lines"),
        "polar" to listOf("math", "grid"), "primary-ruled" to listOf("lines", "writing", "school"),
        "ruled" to listOf("lines", "writing"), "semilog" to listOf("math", "graph", "science"),
        "seyes" to listOf("lines", "writing", "school"), "storyboard" to listOf("film", "drawing"),
        "todo" to listOf("planner", "lists"), "weekly-planner" to listOf("planner"),
    )

    @Test fun everyBundledTemplateLandsUnderTheMockupsHeading() {
        val g = bundled.mapValues { (_, tags) -> TemplateGroups.groupOf(tags) }
        assertEquals(setOf("calligraphy", "cornell", "pleading", "primary-ruled", "ruled", "seyes"), g.filterValues { it == TemplateGroup.WRITING }.keys)
        assertEquals(setOf("daily-planner", "todo", "weekly-planner"), g.filterValues { it == TemplateGroup.PLANNING }.keys)
        assertEquals(setOf("engineering", "hexagons", "polar", "semilog"), g.filterValues { it == TemplateGroup.MATHS }.keys)
        assertEquals(setOf("dot-grid", "isometric-dots", "isometric", "perspective", "storyboard"), g.filterValues { it == TemplateGroup.DRAWING }.keys)
        assertEquals(setOf("guitar-tab", "musical-staves"), g.filterValues { it == TemplateGroup.MUSIC }.keys)
        assertEquals(TemplateGroup.MORE, TemplateGroups.groupOf(listOf("unheard-of")))
    }

    private val items = listOf(
        TemplateGroups.Item("lines", Source.BUILT_IN, emptyList()),
        TemplateGroups.Item("dots", Source.BUILT_IN, emptyList()),
        TemplateGroups.Item("kanji", Source.IMPORTED, listOf("lines")),
        TemplateGroups.Item("lab", Source.NOTE, emptyList()),
        TemplateGroups.Item("ruled", Source.BUNDLED, listOf("lines", "writing")),
        TemplateGroups.Item("staves", Source.BUNDLED, listOf("music")),
    )

    @Test fun pageSetupShowsYoursThenThisNotesThenTheBundledGroups() {
        val s = TemplateGroups.sections(items, setup = true)
        assertEquals(listOf(TemplateGroup.BASICS, TemplateGroup.MINE, TemplateGroup.IN_NOTE, TemplateGroup.WRITING, TemplateGroup.MUSIC), s.map { it.group })
        assertEquals(listOf(PageTemplates.NONE, "lines", "dots"), s[0].keys)
        assertTrue(s[1].import)
    }

    @Test fun newNotebookLeavesOutTheNotesOwnAndTheImportTile() {
        val s = TemplateGroups.sections(items, setup = false)
        assertFalse(s.any { it.group == TemplateGroup.IN_NOTE })
        assertFalse(s.first { it.group == TemplateGroup.MINE }.import)
        // With nothing imported, My templates is left out of New notebook altogether.
        val none = TemplateGroups.sections(items.filter { it.source != Source.IMPORTED }, setup = false)
        assertFalse(none.any { it.group == TemplateGroup.MINE })
    }
}
