package com.xnotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutTest {

    private fun sec(vararg items: ToolbarItem) = ToolbarSection(items.map { ToolbarEntry(it) })
    private fun ToolbarLayout.toRaw(): List<List<Pair<String, Boolean>>> =
        sections.map { s -> s.entries.map { it.item.id to it.visible } }
    private fun ToolbarLayout.items() = sections.flatMap { it.entries }.map { it.item }

    @Test fun defaultShowsTheWritingToolsAndHidesTheRest() {
        val d = ToolbarLayout.DEFAULT
        val shown = d.visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertEquals(
            listOf(
                ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK,
                ToolbarItem.PEN, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER,
                ToolbarItem.LASSO, ToolbarItem.SHAPE, ToolbarItem.TEXT, ToolbarItem.MARKUP,
                ToolbarItem.IMAGE, ToolbarItem.LASER, ToolbarItem.TAPE, ToolbarItem.MORE, ToolbarItem.COLORS,
            ),
            shown,
        )
        // The note's own controls are the header's, so none of them is on the bar, but zoom lock:
        // it is wanted so often that it sits beside redo as well.
        assertEquals(listOf(ToolbarItem.ZOOM_LOCK), shown.filter { it in ToolbarLayout.HEADER_ITEMS })
        // An all-hidden section paints nothing, so it adds no separator either.
        assertEquals(d.sections.size - 1, d.visibleSections.size)
        // The More menu is what makes hiding safe: it is on the bar whenever anything is hidden.
        assertTrue(d.sections.flatMap { it.entries }.any { !it.visible })
        assertTrue(ToolbarItem.MORE in shown)
    }

    @Test fun canvasDefaultHasItsMoreMenuToo() {
        val d = ToolbarLayout.CANVAS_DEFAULT
        val shown = d.visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertTrue(ToolbarItem.MORE in shown)
        assertTrue(ToolbarItem.PEN in shown)
        assertFalse(ToolbarItem.PAN in shown)
        assertEquals(listOf(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK), shown.take(3))
        assertEquals(ToolbarLayout.CANVAS_ITEMS, d.sections.flatMap { it.entries }.map { it.item }.toSet())
    }

    @Test fun defaultContainsEveryItemOnce() {
        val items = ToolbarLayout.DEFAULT.items()
        assertEquals(ToolbarLayout.NOTE_ITEMS, items.toSet())
        assertEquals(ToolbarLayout.NOTE_ITEMS.size, items.size)
    }

    @Test fun defaultRoundTripsThroughRaw() {
        assertEquals(ToolbarLayout.DEFAULT, ToolbarLayout.fromRaw(ToolbarLayout.DEFAULT.toRaw()))
    }

    @Test fun emptyRawYieldsDefault() {
        assertEquals(ToolbarLayout.DEFAULT, ToolbarLayout.fromRaw(emptyList()))
        assertEquals(ToolbarLayout.DEFAULT, ToolbarLayout.fromRaw(listOf(emptyList(), emptyList())))
    }

    @Test fun unknownIdsDropped() {
        val raw = ToolbarLayout.DEFAULT.toRaw().toMutableList()
        raw[0] = listOf("frobnicate" to true) + raw[0]
        val back = ToolbarLayout.fromRaw(raw)
        assertEquals(ToolbarLayout.NOTE_ITEMS.size, back.items().size)
        assertEquals(ToolbarLayout.NOTE_ITEMS, back.items().toSet())
    }

    @Test fun duplicateIdsKeepFirst() {
        val raw = ToolbarLayout.DEFAULT.toRaw().toMutableList()
        raw[1] = raw[1] + ("pen" to true)
        val back = ToolbarLayout.fromRaw(raw)
        assertEquals(1, back.items().count { it == ToolbarItem.PEN })
    }

    @Test fun missingItemsAppendedToLastSectionVisible() {
        val raw = ToolbarLayout.DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.FULLSCREEN.id || it.first == ToolbarItem.STYLES.id } }
        val back = ToolbarLayout.fromRaw(raw)
        assertEquals(ToolbarLayout.NOTE_ITEMS, back.items().toSet())
        val last = back.sections.last().entries
        assertTrue(last.any { it.item == ToolbarItem.FULLSCREEN && it.visible })
        assertTrue(last.any { it.item == ToolbarItem.STYLES && it.visible })
    }

    @Test fun missingTextBoxSlotsInAfterText() {
        // A layout stored before the text box tool existed: it must appear right after
        // the inline text item, not at the end of the bar.
        val raw = ToolbarLayout.DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.TEXT_BOX.id } }
        val back = ToolbarLayout.fromRaw(raw)
        val sec = back.sections.first { s -> s.entries.any { it.item == ToolbarItem.TEXT } }
        val items = sec.entries.map { it.item }
        assertEquals(items.indexOf(ToolbarItem.TEXT) + 1, items.indexOf(ToolbarItem.TEXT_BOX))
    }

    @Test fun missingMarkupSlotsInAfterTextBox() {
        // A layout stored before the text markup tool existed gains it beside the text box.
        val raw = ToolbarLayout.DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.MARKUP.id } }
        val back = ToolbarLayout.fromRaw(raw)
        val sec = back.sections.first { s -> s.entries.any { it.item == ToolbarItem.TEXT_BOX } }
        val items = sec.entries.map { it.item }
        assertEquals(items.indexOf(ToolbarItem.TEXT_BOX) + 1, items.indexOf(ToolbarItem.MARKUP))
        assertTrue(ToolbarItem.MARKUP !in ToolbarLayout.CANVAS_ITEMS)
    }

    @Test fun missingViewSlotsInAfterStyles() {
        // A layout stored before the View menu existed: it must appear right after Styles.
        val raw = ToolbarLayout.DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.VIEW.id } }
        val back = ToolbarLayout.fromRaw(raw)
        val sec = back.sections.first { s -> s.entries.any { it.item == ToolbarItem.STYLES } }
        val items = sec.entries.map { it.item }
        assertEquals(items.indexOf(ToolbarItem.STYLES) + 1, items.indexOf(ToolbarItem.VIEW))
    }

    @Test fun missingTextBoxWithoutTextAppendsToLastSection() {
        val raw = ToolbarLayout.DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.TEXT_BOX.id || it.first == ToolbarItem.TEXT.id } }
            .filter { it.isNotEmpty() }
        val back = ToolbarLayout.fromRaw(raw)
        assertEquals(ToolbarLayout.NOTE_ITEMS, back.items().toSet())
        val last = back.sections.last().entries.map { it.item }
        // TEXT lands at the end first (enum order), then TEXT_BOX slots in after it.
        assertEquals(last.indexOf(ToolbarItem.TEXT) + 1, last.indexOf(ToolbarItem.TEXT_BOX))
    }

    @Test fun hiddenFlagSurvivesRaw() {
        val hidden = ToolbarLayout.DEFAULT.toggleVisible(2, 0)
        assertFalse(hidden.sections[2].entries[0].visible)
        assertEquals(hidden, ToolbarLayout.fromRaw(hidden.toRaw()))
    }

    @Test fun addSectionAppendsEmpty() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME))).addSection()
        assertEquals(2, l.sections.size)
        assertTrue(l.sections.last().entries.isEmpty())
    }

    @Test fun removeMiddleSectionMergesIntoPrevious() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME, ToolbarItem.TITLE), sec(ToolbarItem.SIDEBAR), sec(ToolbarItem.PEN)))
        val r = l.removeSection(1)
        assertEquals(2, r.sections.size)
        assertEquals(listOf(ToolbarItem.HOME, ToolbarItem.TITLE, ToolbarItem.SIDEBAR), r.sections[0].entries.map { it.item })
    }

    @Test fun removeFirstSectionMergesIntoNext() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME, ToolbarItem.TITLE), sec(ToolbarItem.SIDEBAR)))
        val r = l.removeSection(0)
        assertEquals(1, r.sections.size)
        assertEquals(listOf(ToolbarItem.HOME, ToolbarItem.TITLE, ToolbarItem.SIDEBAR), r.sections[0].entries.map { it.item })
    }

    @Test fun removeLastRemainingSectionIsNoOp() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME)))
        assertEquals(l, l.removeSection(0))
    }

    @Test fun moveSectionReorders() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME), sec(ToolbarItem.SIDEBAR), sec(ToolbarItem.PEN)))
        val r = l.moveSection(0, 2)
        assertEquals(listOf(ToolbarItem.HOME), r.sections.last().entries.map { it.item })
        assertEquals(listOf(ToolbarItem.SIDEBAR), r.sections[0].entries.map { it.item })
    }

    @Test fun moveItemWithinSectionClampsForwardTarget() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME, ToolbarItem.TITLE, ToolbarItem.SIDEBAR)))
        val r = l.moveItem(0, 0, 0, 2)
        assertEquals(listOf(ToolbarItem.TITLE, ToolbarItem.HOME, ToolbarItem.SIDEBAR), r.sections[0].entries.map { it.item })
    }

    @Test fun moveItemAcrossSections() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME, ToolbarItem.TITLE), sec(ToolbarItem.SIDEBAR)))
        val r = l.moveItem(0, 1, 1, 0)
        assertEquals(listOf(ToolbarItem.HOME), r.sections[0].entries.map { it.item })
        assertEquals(listOf(ToolbarItem.TITLE, ToolbarItem.SIDEBAR), r.sections[1].entries.map { it.item })
    }

    @Test fun toggleVisibleFlipsOnlyTarget() {
        val l = ToolbarLayout(listOf(sec(ToolbarItem.HOME, ToolbarItem.TITLE)))
        val r = l.toggleVisible(0, 0)
        assertFalse(r.sections[0].entries[0].visible)
        assertTrue(r.sections[0].entries[1].visible)
    }

    @Test fun visibleSectionsSkipsEmptyAndAllHidden() {
        val l = ToolbarLayout(
            listOf(
                sec(ToolbarItem.HOME),
                ToolbarSection(listOf(ToolbarEntry(ToolbarItem.TITLE, visible = false))),
                ToolbarSection(emptyList()),
                sec(ToolbarItem.PEN),
            ),
        )
        assertEquals(2, l.visibleSections.size)
    }

    /** A layout stored before tape existed gains it right after the laser, visible, untouched otherwise. */
    @Test fun aStoredLayoutWithoutTapeGainsItBesideTheLaser() {
        for ((layout, among) in listOf(
            ToolbarLayout.DEFAULT to ToolbarLayout.NOTE_ITEMS,
            ToolbarLayout.CANVAS_DEFAULT to ToolbarLayout.CANVAS_ITEMS,
        )) {
            val raw = layout.toRaw().map { sec -> sec.filter { it.first != ToolbarItem.TAPE.id } }
            val back = ToolbarLayout.fromRaw(raw, among, layout)
            assertEquals(layout, back)
            val sec = back.sections.first { s -> s.entries.any { it.item == ToolbarItem.TAPE } }
            val items = sec.entries.map { it.item }
            assertEquals(items.indexOf(ToolbarItem.LASER) + 1, items.indexOf(ToolbarItem.TAPE))
            assertTrue(sec.entries.first { it.item == ToolbarItem.TAPE }.visible)
        }
    }

    // --- the canvas bar, which is its own layout with its own items ---

    @Test fun canvasDefaultHoldsEveryCanvasItemOnce() {
        val items = ToolbarLayout.CANVAS_DEFAULT.items()
        assertEquals(ToolbarLayout.CANVAS_ITEMS, items.toSet())
        assertEquals(ToolbarLayout.CANVAS_ITEMS.size, items.size)
    }

    @Test fun theTwoBarsDoNotShareTheirOwnItems() {
        assertTrue(ToolbarItem.WAYPOINTS !in ToolbarLayout.NOTE_ITEMS)
        assertTrue(ToolbarItem.MINIMAP !in ToolbarLayout.NOTE_ITEMS)
        assertTrue(ToolbarItem.PAGE_NAV !in ToolbarLayout.CANVAS_ITEMS)
        assertTrue(ToolbarItem.TEXT !in ToolbarLayout.CANVAS_ITEMS)
    }

    @Test fun theCanvasDefaultRoundTripsThroughRaw() {
        assertEquals(
            ToolbarLayout.CANVAS_DEFAULT,
            ToolbarLayout.fromRaw(
                ToolbarLayout.CANVAS_DEFAULT.toRaw(),
                ToolbarLayout.CANVAS_ITEMS,
                ToolbarLayout.CANVAS_DEFAULT,
            ),
        )
    }

    /** A stored paged layout handed to the canvas must not put page tools on it. */
    @Test fun aCanvasLayoutDropsWhatBelongsToTheOtherBar() {
        val back = ToolbarLayout.fromRaw(
            ToolbarLayout.DEFAULT.toRaw(),
            ToolbarLayout.CANVAS_ITEMS,
            ToolbarLayout.CANVAS_DEFAULT,
        )
        assertEquals(ToolbarLayout.CANVAS_ITEMS, back.items().toSet())
    }

    @Test fun aCanvasLayoutStoredBeforeAnItemExistedGrowsIt() {
        val raw = ToolbarLayout.CANVAS_DEFAULT.toRaw()
            .map { s -> s.filterNot { it.first == ToolbarItem.MINIMAP.id } }
        val back = ToolbarLayout.fromRaw(raw, ToolbarLayout.CANVAS_ITEMS, ToolbarLayout.CANVAS_DEFAULT)
        assertEquals(ToolbarLayout.CANVAS_ITEMS, back.items().toSet())
        assertTrue(back.sections.last().entries.any { it.item == ToolbarItem.MINIMAP && it.visible })
    }

    @Test fun anEmptyCanvasLayoutFallsBackToTheCanvasDefault() {
        assertEquals(
            ToolbarLayout.CANVAS_DEFAULT,
            ToolbarLayout.fromRaw(emptyList(), ToolbarLayout.CANVAS_ITEMS, ToolbarLayout.CANVAS_DEFAULT),
        )
    }
    // --- zoom lock joins the bar, once, for a layout saved before it did ---

    /** [layout] as it was stored before zoom lock was on the bar: hidden at the end of the last section. */
    private fun ToolbarLayout.withZoomLockHidden(): List<List<Pair<String, Boolean>>> {
        val raw = toRaw().map { s -> s.filter { it.first != ToolbarItem.ZOOM_LOCK.id } }
        return raw.dropLast(1) + listOf(raw.last() + (ToolbarItem.ZOOM_LOCK.id to false))
    }

    @Test fun anOlderLayoutGainsZoomLockVisibleRightAfterRedo() {
        for ((layout, among) in listOf(
            ToolbarLayout.DEFAULT to ToolbarLayout.NOTE_ITEMS,
            ToolbarLayout.CANVAS_DEFAULT to ToolbarLayout.CANVAS_ITEMS,
        )) {
            val back = ToolbarLayout.fromRaw(layout.withZoomLockHidden(), among, layout, revision = 2)
            assertEquals(layout, back)
            assertEquals(1, back.items().count { it == ToolbarItem.ZOOM_LOCK })
        }
    }

    @Test fun zoomLockFollowsRedoWhereverTheUserPutIt() {
        // Redo moved into the tools section by the user: zoom lock lands beside it there.
        val moved = ToolbarLayout.DEFAULT.moveItem(0, 1, 1, 0)
        val raw = moved.withZoomLockHidden()
        val back = ToolbarLayout.fromRaw(raw, revision = 2)
        val items = back.sections[1].entries.map { it.item }
        assertEquals(listOf(ToolbarItem.REDO, ToolbarItem.ZOOM_LOCK), items.take(2))
        assertTrue(back.sections[1].entries[1].visible)
    }

    @Test fun aZoomLockTheUserAlreadyShowedStaysWhereTheyPutIt() {
        val raw = ToolbarLayout.DEFAULT.withZoomLockHidden().map { s ->
            s.map { if (it.first == ToolbarItem.ZOOM_LOCK.id) it.first to true else it }
        }
        val back = ToolbarLayout.fromRaw(raw, revision = 2)
        assertEquals(ToolbarItem.ZOOM_LOCK, back.sections.last().entries.last().item)
        assertTrue(back.sections.last().entries.last().visible)
        assertEquals(1, back.items().count { it == ToolbarItem.ZOOM_LOCK })
    }

    @Test fun aCurrentLayoutKeepsAZoomLockTheUserHid() {
        val raw = ToolbarLayout.DEFAULT.withZoomLockHidden()
        val back = ToolbarLayout.fromRaw(raw) // saved at the current revision: the move already ran
        assertFalse(back.sections.last().entries.last { it.item == ToolbarItem.ZOOM_LOCK }.visible)
        assertEquals(listOf(ToolbarItem.UNDO, ToolbarItem.REDO), back.sections[0].entries.map { it.item })
    }

    @Test fun aLayoutWithoutRedoGetsZoomLockAtTheFront() {
        val raw = ToolbarLayout.DEFAULT.withZoomLockHidden().map { s -> s.filter { it.first != ToolbarItem.REDO.id } }
        val back = ToolbarLayout.fromRaw(raw, revision = 2)
        assertEquals(ToolbarEntry(ToolbarItem.ZOOM_LOCK), back.sections[0].entries[0])
        // Redo itself comes back as any missing item does.
        assertEquals(1, back.items().count { it == ToolbarItem.REDO })
    }

    @Test fun theRevisionThatResetsALayoutIsOlderThanTheCurrentOne() {
        assertTrue(ToolbarLayout.RESET_BELOW < ToolbarLayout.REVISION)
        assertTrue(ToolbarLayout.ZOOM_LOCK_ON_BAR <= ToolbarLayout.REVISION)
        assertTrue(ToolbarLayout.ZOOM_LOCK_ON_BAR > ToolbarLayout.RESET_BELOW)
    }
}
