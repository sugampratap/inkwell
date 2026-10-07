package com.xnotes.ui

import androidx.annotation.StringRes
import com.xnotes.R
import com.xnotes.core.model.PageTemplates
import com.xnotes.platform.TemplateLibrary

/** The template browser's headings, in the mockup's order. */
internal enum class TemplateGroup(@param:StringRes val title: Int) {
    BASICS(R.string.templates_basics),
    MINE(R.string.templates_mine),
    IN_NOTE(R.string.templates_in_note),
    WRITING(R.string.templates_writing),
    PLANNING(R.string.templates_planning),
    MATHS(R.string.templates_maths),
    DRAWING(R.string.templates_drawing),
    MUSIC(R.string.templates_music),
    MORE(R.string.templates_more),
}

/** How "All templates" is laid out: the user's own first, then the bundled ones by what they are for. */
internal object TemplateGroups {

    /** One template as the browser sees it. */
    data class Item(val key: String, val source: TemplateLibrary.Source, val tags: List<String>)

    /** A heading and its tiles in order; [import] ends the row with the Import tile. Blank is [PageTemplates.NONE]. */
    data class Section(val group: TemplateGroup, val keys: List<String>, val import: Boolean = false)

    // First match wins, in this order: a template tagged both "grid" and "drawing" is for drawing.
    private val RULES: List<Pair<TemplateGroup, Set<String>>> = listOf(
        TemplateGroup.MUSIC to setOf("music", "guitar"),
        TemplateGroup.PLANNING to setOf("planner", "lists"),
        TemplateGroup.DRAWING to setOf("drawing", "film", "journal", "isometric", "perspective"),
        TemplateGroup.MATHS to setOf("math", "science", "graph", "engineering", "chemistry", "games"),
        TemplateGroup.WRITING to setOf("lines", "writing", "study", "school", "lettering", "calligraphy", "legal"),
    )

    private val BUNDLED_ORDER = listOf(
        TemplateGroup.WRITING, TemplateGroup.PLANNING, TemplateGroup.MATHS, TemplateGroup.DRAWING, TemplateGroup.MUSIC, TemplateGroup.MORE,
    )

    /** A bundled template's heading, from the tags in its own file. */
    fun groupOf(tags: List<String>): TemplateGroup {
        val t = tags.map { it.lowercase() }.toSet()
        return RULES.firstOrNull { (_, keys) -> keys.any { it in t } }?.first ?: TemplateGroup.MORE
    }

    /**
     * The browser's sections: Basics (Blank and the built-in rulings); the user's imported templates
     * (with the Import tile in Page setup); in Page setup only, the templates this note carries; then
     * the bundled ones under their headings, in library order. Empty sections are left out, except
     * My templates in Page setup, which always holds the Import tile.
     */
    fun sections(items: List<Item>, setup: Boolean): List<Section> {
        val out = ArrayList<Section>()
        out += Section(TemplateGroup.BASICS, listOf(PageTemplates.NONE) + items.filter { it.source == TemplateLibrary.Source.BUILT_IN }.map { it.key })
        val mine = items.filter { it.source == TemplateLibrary.Source.IMPORTED }.map { it.key }
        if (setup || mine.isNotEmpty()) out += Section(TemplateGroup.MINE, mine, import = setup)
        if (setup) {
            val carried = items.filter { it.source == TemplateLibrary.Source.NOTE }.map { it.key }
            if (carried.isNotEmpty()) out += Section(TemplateGroup.IN_NOTE, carried)
        }
        val bundled = items.filter { it.source == TemplateLibrary.Source.BUNDLED }.groupBy { groupOf(it.tags) }
        for (g in BUNDLED_ORDER) bundled[g]?.let { out += Section(g, it.map { e -> e.key }) }
        return out
    }
}
