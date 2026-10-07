package com.xnotes.core.tools

/**
 * User-customisable toolbar layout: ordered [sections], each an ordered list of [ToolbarEntry].
 * Pure model (no Android, no JSON) so the add/remove/move/migration logic is unit-testable on
 * the JVM; the settings layer persists it and the UI renders from it.
 */

/** Every atomic, movable toolbar element. The three block ids each render a fixed multi-control
 *  cluster but move and hide as one unit. [id] is the persistence key (never rename without a
 *  migration); the tool ids match the matching [Tool.id]. */
enum class ToolbarItem(val id: String) {
    HOME("home"),
    TITLE("title"),
    SIDEBAR("sidebar"),
    PEN("pen"),
    BALLPOINT("ballpoint"),
    DASHED("dashed"),
    CALLIGRAPHY("calligraphy"),
    SPEED("speed"),
    TAPER("taper"),

    /** The pencil's own button; like the other pen types it waits hidden, the pen button covering it. */
    PENCIL("pencil"),
    HIGHLIGHTER("highlighter"),
    ERASER("eraser"),
    PAN("pan"),
    SELECT("select"),
    LASSO("lasso"),
    SCREENSHOT("screenshot"),
    WAND("wand"),
    SHAPE("shape"),
    RULER("ruler"),
    TEXT("text"),
    TEXT_BOX("text_box"),

    /** The text markup tool; only notes with a PDF show it. */
    MARKUP("markup"),
    IMAGE("image"),
    UNDO("undo"),
    REDO("redo"),
    PAGE_NAV("page_nav"),
    STYLES("styles"),
    MARGINS("margins"),
    VIEW("view"),
    ZOOM("zoom"),
    FIT("fit"),
    ZOOM_LOCK("zoom_lock"),
    FULLSCREEN("fullscreen"),
    COLORS("colors"),

    /** Canvas only: saved views, which are what page numbers are on an unbounded surface. */
    WAYPOINTS("waypoints"),

    /** Canvas only: the overview map in the corner. */
    MINIMAP("minimap"),

    /** Every item the layout hides, one tap away in a menu, so a short bar loses nothing. */
    MORE("more"),

    /** The laser pointer, a tool of its own rather than a mode of the pens. */
    LASER("laser"),

    /** Study tape, which hides what it covers until tapped. */
    TAPE("tape");

    companion object {
        fun fromId(id: String?): ToolbarItem? = entries.firstOrNull { it.id == id }
    }
}

data class ToolbarEntry(val item: ToolbarItem, val visible: Boolean = true)

data class ToolbarSection(val entries: List<ToolbarEntry>) {
    val visibleEntries: List<ToolbarEntry> get() = entries.filter { it.visible }
    val hasVisible: Boolean get() = entries.any { it.visible }
}

data class ToolbarLayout(val sections: List<ToolbarSection>) {

    /** Sections that paint on the real bar; a separator goes between consecutive ones. */
    val visibleSections: List<ToolbarSection> get() = sections.filter { it.hasVisible }

    fun addSection(): ToolbarLayout = copy(sections = sections + ToolbarSection(emptyList()))

    /**
     * Remove section [i], merging its entries into the previous section, or the next one when [i]
     * is the first. Order and visibility are preserved. No-op when only one section remains.
     */
    fun removeSection(i: Int): ToolbarLayout {
        if (sections.size <= 1 || i !in sections.indices) return this
        val target = if (i == 0) i + 1 else i - 1
        val moved = sections[i].entries
        val rebuilt = sections.mapIndexedNotNull { idx, sec ->
            when (idx) {
                i -> null
                target -> sec.copy(entries = if (target < i) sec.entries + moved else moved + sec.entries)
                else -> sec
            }
        }
        return copy(sections = rebuilt)
    }

    fun moveSection(from: Int, to: Int): ToolbarLayout {
        if (from !in sections.indices || to !in sections.indices || from == to) return this
        val list = sections.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(sections = list)
    }

    fun toggleVisible(sectionIndex: Int, entryIndex: Int): ToolbarLayout {
        val sec = sections.getOrNull(sectionIndex) ?: return this
        val e = sec.entries.getOrNull(entryIndex) ?: return this
        val entries = sec.entries.toMutableList().also { it[entryIndex] = e.copy(visible = !e.visible) }
        return copy(sections = sections.toMutableList().also { it[sectionIndex] = sec.copy(entries = entries) })
    }

    /**
     * Move the entry at ([fromSection], [fromIndex]) to ([toSection], [toIndex]), keeping its
     * visibility. The target index is interpreted against the layout after the source is removed.
     */
    fun moveItem(fromSection: Int, fromIndex: Int, toSection: Int, toIndex: Int): ToolbarLayout {
        val src = sections.getOrNull(fromSection) ?: return this
        val entry = src.entries.getOrNull(fromIndex) ?: return this
        if (toSection !in sections.indices) return this
        val mutable = sections.map { it.entries.toMutableList() }.toMutableList()
        mutable[fromSection].removeAt(fromIndex)
        val adjusted = if (toSection == fromSection && toIndex > fromIndex) toIndex - 1 else toIndex
        mutable[toSection].add(adjusted.coerceIn(0, mutable[toSection].size), entry)
        return copy(sections = mutable.mapIndexed { idx, e -> sections[idx].copy(entries = e) })
    }

    /** Add any of [among] missing from this layout, visible (hidden for [INSERT_HIDDEN]), so the bar never goes stale
     *  across versions: an item with a designated neighbour ([INSERT_AFTER]) slots in right
     *  after it; anything else is appended to the last section. */
    fun withMissingItemsAppended(among: Set<ToolbarItem> = NOTE_ITEMS): ToolbarLayout {
        var layout = this
        for (item in ToolbarItem.entries) {
            if (item !in among) continue
            if (layout.sections.any { s -> s.entries.any { it.item == item } }) continue
            layout = layout.insertMissing(item)
        }
        return layout
    }

    /**
     * The one-time changes a layout stored at [revision] has not had yet. Only items of [among]
     * are touched. Saved back at [REVISION], a layout never has the same change twice, so what the
     * user does afterwards (hiding zoom lock again, say) sticks.
     */
    fun migratedFrom(revision: Int, among: Set<ToolbarItem> = NOTE_ITEMS): ToolbarLayout {
        var layout = this
        if (revision < ZOOM_LOCK_ON_BAR && ToolbarItem.ZOOM_LOCK in among) {
            layout = layout.shownAfter(ToolbarItem.ZOOM_LOCK, ToolbarItem.REDO)
        }
        return layout
    }

    /**
     * Put [item] on the bar right after [anchor]. One the user already shows stays where they put
     * it; a hidden one moves beside [anchor], shown; with no [anchor] it goes first on the bar.
     */
    private fun shownAfter(item: ToolbarItem, anchor: ToolbarItem): ToolbarLayout {
        if (sections.any { s -> s.entries.any { it.item == item && it.visible } }) return this
        val without = sections.map { s -> s.copy(entries = s.entries.filter { it.item != item }) }
        val si = without.indexOfFirst { s -> s.entries.any { it.item == anchor } }
        val entry = ToolbarEntry(item)
        if (si < 0) {
            val first = without.firstOrNull() ?: return copy(sections = listOf(ToolbarSection(listOf(entry))))
            return copy(sections = listOf(first.copy(entries = listOf(entry) + first.entries)) + without.drop(1))
        }
        val sec = without[si]
        val at = sec.entries.indexOfFirst { it.item == anchor } + 1
        val entries = sec.entries.toMutableList().also { it.add(at, entry) }
        return copy(sections = without.toMutableList().also { it[si] = sec.copy(entries = entries) })
    }

    private fun insertMissing(item: ToolbarItem): ToolbarLayout {
        val anchor = INSERT_AFTER[item]
        if (anchor != null) {
            sections.forEachIndexed { si, sec ->
                val i = sec.entries.indexOfFirst { it.item == anchor }
                if (i >= 0) {
                    val entries = sec.entries.toMutableList().also { it.add(i + 1, ToolbarEntry(item, item !in INSERT_HIDDEN)) }
                    return copy(sections = sections.toMutableList().also { it[si] = sec.copy(entries = entries) })
                }
            }
        }
        val last = sections.last()
        return copy(sections = sections.dropLast(1) + last.copy(entries = last.entries + ToolbarEntry(item, item !in INSERT_HIDDEN)))
    }

    companion object {
        /** Later-added items that belong beside an existing one in stored layouts. */
        private val INSERT_AFTER = mapOf(
            ToolbarItem.TEXT_BOX to ToolbarItem.TEXT,
            ToolbarItem.VIEW to ToolbarItem.STYLES,
            ToolbarItem.MARGINS to ToolbarItem.STYLES,
            ToolbarItem.WAND to ToolbarItem.LASSO,
            ToolbarItem.MARKUP to ToolbarItem.TEXT_BOX,
            ToolbarItem.MORE to ToolbarItem.IMAGE,
            ToolbarItem.BALLPOINT to ToolbarItem.PEN,
            ToolbarItem.LASER to ToolbarItem.IMAGE,
            ToolbarItem.TAPE to ToolbarItem.LASER,
            ToolbarItem.PENCIL to ToolbarItem.TAPER,
        )

        /**
         * Later-added items that arrive hidden in a stored layout rather than shown: a pen type, which
         * the pen button already covers, so a bar that never asked for its own button does not grow one.
         */
        private val INSERT_HIDDEN = setOf(ToolbarItem.PENCIL)

        /**
         * The two bars hold different things, so each has its own set and neither can be handed the
         * other's. Page navigation means nothing on an unbounded canvas, and a waypoint means
         * nothing in a paged note; an item outside a layout's set is dropped on load rather than
         * drawn as a dead chip.
         */
        val CANVAS_ITEMS: Set<ToolbarItem> = setOf(
            ToolbarItem.HOME, ToolbarItem.TITLE,
            ToolbarItem.PEN, ToolbarItem.BALLPOINT, ToolbarItem.DASHED, ToolbarItem.CALLIGRAPHY, ToolbarItem.SPEED,
            ToolbarItem.TAPER, ToolbarItem.PENCIL, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER, ToolbarItem.LASER,
            ToolbarItem.TAPE, ToolbarItem.PAN, ToolbarItem.LASSO,
            ToolbarItem.SHAPE,
            ToolbarItem.IMAGE, ToolbarItem.COLORS, ToolbarItem.UNDO, ToolbarItem.REDO,
            ToolbarItem.STYLES, ToolbarItem.WAYPOINTS, ToolbarItem.MINIMAP,
            ToolbarItem.ZOOM, ToolbarItem.FIT, ToolbarItem.ZOOM_LOCK, ToolbarItem.MORE,
        )

        /**
         * Tools the bar no longer offers, on either canvas, because another one does their job: the
         * lasso selects (and taps an object to select it), the laser pointer is the disappearing
         * ink, and a screenshot is the lasso's "Copy as image". A stored layout that still holds
         * them drops them on load, like any item outside its set.
         */
        val RETIRED: Set<ToolbarItem> = setOf(ToolbarItem.SELECT, ToolbarItem.SCREENSHOT, ToolbarItem.WAND)

        /** Everything the paged bar can hold: the whole enum bar the two canvas-only additions. */
        val NOTE_ITEMS: Set<ToolbarItem> =
            ToolbarItem.entries.toSet() - setOf(ToolbarItem.WAYPOINTS, ToolbarItem.MINIMAP) - RETIRED

        /** The revision a layout is stored at: the newest one-time change ([migratedFrom]) it has had. */
        const val REVISION = 3

        /**
         * A stored layout older than this gave way to the redesigned bar: it is read as the new
         * default (see `Settings`). Later revisions are one-time changes applied to the stored
         * layout instead ([migratedFrom]), so a user's own arrangement survives them.
         */
        const val RESET_BELOW = 2

        /** Zoom lock moved onto the bar, right after redo, since it is toggled so often. */
        const val ZOOM_LOCK_ON_BAR = 3

        /**
         * The paged bar as it ships: undo, the tools a page is written with, and the inks. The
         * note's own controls (back, title, pages, page setup, view) live in the header above the
         * page instead, so they are here only hidden, for anyone who wants them back on the bar.
         * The pen button stands for every pen type; the others wait hidden for the customiser.
         */
        val DEFAULT: ToolbarLayout = of(
            listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK),
            listOf(
                ToolbarItem.PEN, hidden(ToolbarItem.BALLPOINT), hidden(ToolbarItem.DASHED), hidden(ToolbarItem.CALLIGRAPHY),
                hidden(ToolbarItem.SPEED), hidden(ToolbarItem.TAPER), hidden(ToolbarItem.PENCIL), ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER,
                ToolbarItem.LASSO, hidden(ToolbarItem.PAN),
                ToolbarItem.SHAPE, ToolbarItem.TEXT, hidden(ToolbarItem.TEXT_BOX), ToolbarItem.MARKUP, ToolbarItem.IMAGE,
                ToolbarItem.LASER, ToolbarItem.TAPE, hidden(ToolbarItem.RULER), ToolbarItem.MORE,
            ),
            listOf(ToolbarItem.COLORS),
            listOf(
                hidden(ToolbarItem.HOME), hidden(ToolbarItem.TITLE), hidden(ToolbarItem.SIDEBAR), hidden(ToolbarItem.PAGE_NAV),
                hidden(ToolbarItem.STYLES), hidden(ToolbarItem.MARGINS), hidden(ToolbarItem.VIEW), hidden(ToolbarItem.ZOOM),
                hidden(ToolbarItem.FIT), hidden(ToolbarItem.FULLSCREEN),
            ),
        )

        /** The canvas bar as it ships, on the same plan as [DEFAULT]. */
        val CANVAS_DEFAULT: ToolbarLayout = of(
            listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK),
            listOf(
                ToolbarItem.PEN, hidden(ToolbarItem.BALLPOINT), hidden(ToolbarItem.DASHED), hidden(ToolbarItem.CALLIGRAPHY),
                hidden(ToolbarItem.SPEED), hidden(ToolbarItem.TAPER), hidden(ToolbarItem.PENCIL), ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER,
                ToolbarItem.LASSO, hidden(ToolbarItem.PAN), ToolbarItem.SHAPE, ToolbarItem.IMAGE,
                ToolbarItem.LASER, ToolbarItem.TAPE, ToolbarItem.MORE,
            ),
            listOf(ToolbarItem.COLORS),
            listOf(
                hidden(ToolbarItem.HOME), hidden(ToolbarItem.TITLE), hidden(ToolbarItem.STYLES), hidden(ToolbarItem.WAYPOINTS),
                hidden(ToolbarItem.MINIMAP), hidden(ToolbarItem.ZOOM), hidden(ToolbarItem.FIT),
            ),
        )

        /**
         * Items the note header always offers, so the More menu need not repeat them: chrome
         * rather than tools. Zoom lock is one, and also sits on the bar by default; hidden from
         * the bar it is still in the header's menu.
         */
        val HEADER_ITEMS: Set<ToolbarItem> = setOf(
            ToolbarItem.HOME, ToolbarItem.TITLE, ToolbarItem.SIDEBAR, ToolbarItem.PAGE_NAV, ToolbarItem.STYLES,
            ToolbarItem.MARGINS, ToolbarItem.VIEW, ToolbarItem.ZOOM, ToolbarItem.FIT, ToolbarItem.ZOOM_LOCK,
            ToolbarItem.FULLSCREEN, ToolbarItem.WAYPOINTS, ToolbarItem.MINIMAP,
        )

        /** An entry for [of] that is in the layout but not on the bar. */
        private fun hidden(item: ToolbarItem): ToolbarEntry = ToolbarEntry(item, visible = false)

        private fun of(vararg groups: List<Any>): ToolbarLayout =
            ToolbarLayout(
                groups.map { g ->
                    ToolbarSection(g.map { if (it is ToolbarEntry) it else ToolbarEntry(it as ToolbarItem) })
                },
            )

        /**
         * Build from raw (id, visible) pairs per section as read from storage: ids outside [among]
         * are dropped, duplicates keep their first occurrence, empty input falls back to [fallback],
         * and any item of [among] not present is appended so the bar never goes stale across
         * versions. [revision] is the one the layout was stored at: the one-time changes since then
         * are applied ([migratedFrom]); it is saved back at [REVISION], so each runs once.
         */
        fun fromRaw(
            rawSections: List<List<Pair<String, Boolean>>>,
            among: Set<ToolbarItem> = NOTE_ITEMS,
            fallback: ToolbarLayout = DEFAULT,
            revision: Int = REVISION,
        ): ToolbarLayout {
            if (rawSections.isEmpty()) return fallback
            val seen = LinkedHashSet<ToolbarItem>()
            val sections = rawSections.map { raw ->
                ToolbarSection(
                    raw.mapNotNull { (id, visible) ->
                        val item = ToolbarItem.fromId(id) ?: return@mapNotNull null
                        if (item !in among) return@mapNotNull null
                        if (!seen.add(item)) return@mapNotNull null
                        ToolbarEntry(item, visible)
                    },
                )
            }
            if (sections.all { it.entries.isEmpty() }) return fallback
            return ToolbarLayout(sections).migratedFrom(revision, among).withMissingItemsAppended(among)
        }
    }
}
