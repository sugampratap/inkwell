package com.xnotes.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarEntry
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.tools.ToolbarSection
import com.xnotes.settings.ToolbarPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeLogicTest {

    private val eps = 1e-4f
    private val window = IntSize(1280, 800)

    // The mockup's specs at 1x (px = dp): tool cards, the More menu, the Insert card.
    private val toolCard = PopoverSpec(lead = 26, gap = 10, margin = 8)
    private val moreMenu = PopoverSpec(lead = 8, gap = 10, margin = 8)
    private val insert = PopoverSpec(lead = 6, gap = 8, margin = 8, endAligned = true)

    /** The floating pill below the header: 768 wide, centred on 1280, 60 tall. */
    private val topPill = IntRect(256, 26, 1024, 86)

    private fun button(left: Int, top: Int) = IntRect(left, top, left + 44, top + 44)

    private fun ToolbarLayout.withHidden(vararg items: ToolbarItem): ToolbarLayout = ToolbarLayout(
        sections.map { s -> s.copy(entries = s.entries.map { if (it.item in items) it.copy(visible = false) else it }) },
    )

    // --- placePopover: below a top bar ---

    @Test fun aToolCardHangsTenBelowThePillAndStartsTwentySixBeforeItsButton() {
        val p = placePopover(button(400, 34), topPill, window, IntSize(340, 400), toolCard, PopoverSide.BELOW)
        assertEquals(374, p.x)
        assertEquals(96, p.y)
        assertEquals(PopoverSide.BELOW, p.side)
        // Origin: the button's centre (422) on the card's top edge.
        assertEquals(48f / 340f, p.originX, eps)
        assertEquals(0f, p.originY, eps)
    }

    @Test fun aCardNearTheLeftEdgeIsKeptEightInside() {
        val p = placePopover(button(20, 34), topPill, window, IntSize(340, 400), toolCard, PopoverSide.BELOW)
        assertEquals(8, p.x)
        assertEquals((42f - 8f) / 340f, p.originX, eps)
    }

    @Test fun aCardNearTheRightEdgeIsKeptEightInside() {
        val p = placePopover(button(1200, 34), topPill, window, IntSize(340, 400), toolCard, PopoverSide.BELOW)
        assertEquals(1280 - 8 - 340, p.x)
        assertEquals((1222f - 932f) / 340f, p.originX, eps)
    }

    @Test fun theOriginNeverLeavesTheCard() {
        // A button scrolled partly past the window's edge (centre 1286, past the clamped card's end at 1272)
        // still gives an origin on the card.
        val p = placePopover(IntRect(1264, 34, 1308, 78), topPill, window, IntSize(340, 400), toolCard, PopoverSide.BELOW)
        assertEquals(1f, p.originX, eps)
    }

    @Test fun theMoreMenuStartsEightBeforeItsButtonSoTheOriginIsThirtyIn() {
        val p = placePopover(button(900, 34), topPill, window, IntSize(300, 200), moreMenu, PopoverSide.BELOW)
        assertEquals(892, p.x)
        assertEquals(96, p.y)
        assertEquals(30f / 300f, p.originX, eps)
    }

    @Test fun theInsertCardsEndSitsSixPastTheButtonAndEightBelowIt() {
        val clip = button(1100, 32)
        val p = placePopover(clip, clip, window, IntSize(348, 339), insert, PopoverSide.BELOW)
        assertEquals(1144 + 6 - 348, p.x)
        assertEquals(76 + 8, p.y)
        assertEquals((1122f - 802f) / 348f, p.originX, eps)
        assertEquals(0f, p.originY, eps)
    }

    // --- flipping and clamping ---

    @Test fun aCardWithNoRoomBelowFlipsAbove() {
        val lowPill = IntRect(256, 600, 1024, 660)
        val p = placePopover(button(400, 608), lowPill, window, IntSize(340, 300), toolCard, PopoverSide.BELOW)
        assertEquals(PopoverSide.ABOVE, p.side)
        assertEquals(600 - 10 - 300, p.y)
        assertEquals(1f, p.originY, eps)
    }

    @Test fun aBottomBarOpensItsCardsAbove() {
        val bottomPill = IntRect(256, 714, 1024, 774)
        val p = placePopover(button(400, 722), bottomPill, window, IntSize(340, 400), toolCard, PopoverSide.ABOVE)
        assertEquals(PopoverSide.ABOVE, p.side)
        assertEquals(714 - 10 - 400, p.y)
        assertEquals(374, p.x)
    }

    @Test fun aBottomBarCardWithNoRoomAboveFallsBelow() {
        val highPill = IntRect(256, 40, 1024, 100)
        val p = placePopover(button(400, 48), highPill, window, IntSize(340, 300), toolCard, PopoverSide.ABOVE)
        assertEquals(PopoverSide.BELOW, p.side)
        assertEquals(110, p.y)
    }

    @Test fun withRoomOnNeitherSideTheCardStaysAndIsClampedInside() {
        val midPill = IntRect(256, 300, 1024, 360)
        val p = placePopover(button(400, 308), midPill, window, IntSize(340, 700), toolCard, PopoverSide.BELOW)
        assertEquals(PopoverSide.BELOW, p.side)
        assertEquals(800 - 8 - 700, p.y)
    }

    @Test fun aCardTallerThanTheWindowStartsAtTheMargin() {
        val p = placePopover(button(400, 34), topPill, window, IntSize(340, 900), toolCard, PopoverSide.BELOW)
        assertEquals(8, p.y)
    }

    // --- side rails ---

    @Test fun aLeftRailOpensItsCardsToTheEnd() {
        val rail = IntRect(8, 100, 68, 700)
        val p = placePopover(button(16, 300), rail, window, IntSize(340, 400), toolCard, PopoverSide.END)
        assertEquals(PopoverSide.END, p.side)
        assertEquals(68 + 10, p.x)
        assertEquals(300 - 26, p.y)
        assertEquals(0f, p.originX, eps)
        assertEquals((322f - 274f) / 400f, p.originY, eps)
    }

    @Test fun aRightRailOpensItsCardsToTheStart() {
        val rail = IntRect(1212, 100, 1272, 700)
        val p = placePopover(button(1220, 300), rail, window, IntSize(340, 400), toolCard, PopoverSide.START)
        assertEquals(PopoverSide.START, p.side)
        assertEquals(1212 - 10 - 340, p.x)
        assertEquals(274, p.y)
        assertEquals(1f, p.originX, eps)
    }

    @Test fun anEndCardWithNoRoomOnTheRightFlipsToTheStart() {
        val rail = IntRect(1000, 100, 1060, 700)
        val p = placePopover(button(1008, 300), rail, window, IntSize(340, 400), toolCard, PopoverSide.END)
        assertEquals(PopoverSide.START, p.side)
        assertEquals(1000 - 10 - 340, p.x)
    }

    @Test fun aRailCardNearTheBottomIsClampedUp() {
        val rail = IntRect(8, 100, 68, 790)
        val p = placePopover(button(16, 700), rail, window, IntSize(340, 400), toolCard, PopoverSide.END)
        assertEquals(800 - 8 - 400, p.y)
    }

    @Test fun cardsMayBeAsTallAsTheWindowLessBothMargins() {
        assertEquals(784, popoverMaxHeight(window, 8))
        assertEquals(0, popoverMaxHeight(IntSize(100, 10), 8))
    }

    // --- a tall card stays on its side (review M3) ---

    @Test fun aTallCardUnderATopBarIsCappedByTheRoomBelowIt() {
        // 800 tall: the pill's bottom at 86, 10 dp gap, 8 dp margin: 800 - 8 - 96.
        val max = popoverMaxHeight(window, 8, topPill, 10, PopoverSide.BELOW, 240)
        assertEquals(696, max)
        // At that height it fits below, so it stays under its bar instead of climbing over it.
        val p = placePopover(button(400, 34), topPill, window, IntSize(340, max), toolCard, PopoverSide.BELOW)
        assertEquals(PopoverSide.BELOW, p.side)
        assertEquals(96, p.y)
    }

    @Test fun aTallCardOverABottomBarIsCappedByTheRoomAboveIt() {
        val bottomPill = IntRect(256, 714, 1024, 774)
        val max = popoverMaxHeight(window, 8, bottomPill, 10, PopoverSide.ABOVE, 240)
        assertEquals(714 - 10 - 8, max)
        val p = placePopover(button(400, 722), bottomPill, window, IntSize(340, max), toolCard, PopoverSide.ABOVE)
        assertEquals(PopoverSide.ABOVE, p.side)
        assertEquals(8, p.y)
    }

    @Test fun withTooLittleRoomOnItsSideTheCardTakesTheOtherSide() {
        // A header button near the bottom of a short window: 100 dp below, 500 above.
        val low = IntRect(400, 548, 444, 592)
        val short = IntSize(1280, 700)
        val max = popoverMaxHeight(short, 8, low, 8, PopoverSide.BELOW, 240)
        assertEquals(548 - 8 - 8, max)
        val p = placePopover(low, low, short, IntSize(260, max), PopoverSpec(lead = 8, gap = 8, margin = 8), PopoverSide.BELOW)
        assertEquals(PopoverSide.ABOVE, p.side)
    }

    @Test fun withTooLittleRoomEitherSideTheCardMayBeTheWholeWindow() {
        val mid = IntRect(400, 150, 444, 194)
        assertEquals(344 - 16, popoverMaxHeight(IntSize(1280, 344), 8, mid, 8, PopoverSide.BELOW, 240))
    }

    @Test fun railCardsAndUnplacedEdgesTakeTheWholeHeight() {
        val rail = IntRect(8, 200, 88, 600)
        assertEquals(784, popoverMaxHeight(window, 8, rail, 10, PopoverSide.END, 240))
        assertEquals(784, popoverMaxHeight(window, 8, rail, 10, PopoverSide.START, 240))
        assertEquals(784, popoverMaxHeight(window, 8, null, 10, PopoverSide.BELOW, 240))
    }

    // --- which tools open a card ---

    @Test fun pagedToolsWithACardAreTodaysHasSettings() {
        val withCard = setOf(
            Tool.PEN, Tool.BALLPOINT, Tool.DASHED, Tool.CALLIGRAPHY, Tool.SPEED, Tool.TAPER, Tool.PENCIL, Tool.HIGHLIGHTER, Tool.LASER,
            Tool.SHAPE, Tool.ERASER, Tool.LASSO, Tool.TEXT, Tool.MARKUP, Tool.TAPE,
        )
        for (t in Tool.entries) assertEquals(t.name, t in withCard, t.hasCard(ToolSurface.NOTE))
    }

    @Test fun canvasToolsWithACardAreTheOnesItsBarOpens() {
        val withCard = setOf(
            Tool.PEN, Tool.BALLPOINT, Tool.DASHED, Tool.CALLIGRAPHY, Tool.SPEED, Tool.TAPER, Tool.PENCIL, Tool.HIGHLIGHTER, Tool.LASER,
            Tool.ERASER, Tool.SHAPE, Tool.LASSO, Tool.TAPE,
        )
        for (t in Tool.entries) assertEquals(t.name, t in withCard, t.hasCard(ToolSurface.CANVAS))
    }

    @Test fun selectHasNoCardOnEitherSurface() {
        assertFalse(Tool.SELECT.hasCard(ToolSurface.NOTE))
        assertFalse(Tool.SELECT.hasCard(ToolSurface.CANVAS))
        // Armed by the V key or the S Pen button, a re-tap has nothing to open, so it just stays armed.
        assertEquals(ToolTap.ARM, toolTap(armed = Tool.SELECT, tapped = Tool.SELECT, open = null, surface = ToolSurface.NOTE))
    }

    // --- a tap on a tool ---

    @Test fun tappingAnotherToolArmsIt() {
        assertEquals(ToolTap.ARM, toolTap(armed = Tool.PEN, tapped = Tool.ERASER, open = null, surface = ToolSurface.NOTE))
        assertEquals(ToolTap.ARM, toolTap(armed = Tool.PEN, tapped = Tool.ERASER, open = Tool.PEN, surface = ToolSurface.NOTE))
    }

    @Test fun tappingTheToolInHandOpensItsCard() {
        assertEquals(ToolTap.OPEN_CARD, toolTap(armed = Tool.ERASER, tapped = Tool.ERASER, open = null, surface = ToolSurface.NOTE))
    }

    @Test fun tappingTheToolInHandWithItsCardUpClosesIt() {
        assertEquals(ToolTap.CLOSE_CARD, toolTap(armed = Tool.ERASER, tapped = Tool.ERASER, open = Tool.ERASER, surface = ToolSurface.CANVAS))
    }

    @Test fun aToolWithNoCardJustStaysArmed() {
        assertEquals(ToolTap.ARM, toolTap(armed = Tool.PAN, tapped = Tool.PAN, open = null, surface = ToolSurface.NOTE))
        // The canvas has no text card.
        assertEquals(ToolTap.ARM, toolTap(armed = Tool.TEXT, tapped = Tool.TEXT, open = null, surface = ToolSurface.CANVAS))
    }

    // --- the More menu ---

    @Test fun thePagedMoreMenuShipsWithPanTextBoxAndTheRuler() {
        val expected = listOf(
            MoreItem(ToolbarItem.PAN, MoreKind.TOOL),
            MoreItem(ToolbarItem.TEXT_BOX, MoreKind.TOOL),
            MoreItem(ToolbarItem.RULER, MoreKind.SWITCH),
        )
        assertEquals(expected, moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false))
        assertEquals(expected, moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = true))
    }

    @Test fun theCanvasMoreMenuShipsWithPanOnly() {
        assertEquals(listOf(MoreItem(ToolbarItem.PAN, MoreKind.TOOL)), moreItems(ToolbarLayout.CANVAS_DEFAULT, ToolSurface.CANVAS, hasPdf = false))
    }

    @Test fun hiddenToolsJoinInBarOrder() {
        val layout = ToolbarLayout.DEFAULT.withHidden(ToolbarItem.HIGHLIGHTER, ToolbarItem.LASER)
        assertEquals(
            listOf(ToolbarItem.HIGHLIGHTER, ToolbarItem.PAN, ToolbarItem.TEXT_BOX, ToolbarItem.LASER, ToolbarItem.RULER),
            moreItems(layout, ToolSurface.NOTE, hasPdf = false).map { it.item },
        )
    }

    @Test fun aHiddenImageIsTheInsertImageAction() {
        val note = moreItems(ToolbarLayout.DEFAULT.withHidden(ToolbarItem.IMAGE), ToolSurface.NOTE, hasPdf = false)
        assertEquals(MoreItem(ToolbarItem.IMAGE, MoreKind.ACTION), note[2])
        val canvas = moreItems(ToolbarLayout.CANVAS_DEFAULT.withHidden(ToolbarItem.IMAGE), ToolSurface.CANVAS, hasPdf = false)
        assertEquals(listOf(MoreItem(ToolbarItem.PAN, MoreKind.TOOL), MoreItem(ToolbarItem.IMAGE, MoreKind.ACTION)), canvas)
    }

    @Test fun markupWaitsInTheMenuOnlyOnNotesWithAPdf() {
        val layout = ToolbarLayout.DEFAULT.withHidden(ToolbarItem.MARKUP)
        assertFalse(moreItems(layout, ToolSurface.NOTE, hasPdf = false).any { it.item == ToolbarItem.MARKUP })
        assertEquals(MoreItem(ToolbarItem.MARKUP, MoreKind.TOOL), moreItems(layout, ToolSurface.NOTE, hasPdf = true)[2])
    }

    @Test fun headerItemsRetiredItemsAndNonToolsNeverAppear() {
        // Hiding undo, redo and the colours leaves the menu as it ships; the hidden header and retired items never show.
        val layout = ToolbarLayout.DEFAULT.withHidden(ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.COLORS)
        assertEquals(moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false), moreItems(layout, ToolSurface.NOTE, hasPdf = false))
        val dead = ToolbarLayout(
            listOf(
                ToolbarSection(
                    listOf(
                        ToolbarEntry(ToolbarItem.WAND, visible = false), ToolbarEntry(ToolbarItem.SELECT, visible = false),
                        ToolbarEntry(ToolbarItem.SIDEBAR, visible = false), ToolbarEntry(ToolbarItem.ZOOM_LOCK, visible = false),
                        ToolbarEntry(ToolbarItem.FULLSCREEN, visible = false), ToolbarEntry(ToolbarItem.MINIMAP, visible = false),
                        ToolbarEntry(ToolbarItem.FIT, visible = false),
                    ),
                ),
            ),
        )
        assertTrue(moreItems(dead, ToolSurface.NOTE, hasPdf = true).isEmpty())
        assertTrue(moreItems(dead, ToolSurface.CANVAS, hasPdf = true).isEmpty())
    }

    @Test fun theCanvasMenuLeavesOutWhatTheCanvasCannotDo() {
        val layout = ToolbarLayout(
            listOf(
                ToolbarSection(
                    listOf(
                        ToolbarEntry(ToolbarItem.TEXT_BOX, visible = false), ToolbarEntry(ToolbarItem.RULER, visible = false),
                        ToolbarEntry(ToolbarItem.PAN, visible = false), ToolbarEntry(ToolbarItem.MARKUP, visible = false),
                    ),
                ),
            ),
        )
        assertEquals(listOf(MoreItem(ToolbarItem.PAN, MoreKind.TOOL)), moreItems(layout, ToolSurface.CANVAS, hasPdf = true))
        assertEquals(
            listOf(
                MoreItem(ToolbarItem.TEXT_BOX, MoreKind.TOOL), MoreItem(ToolbarItem.RULER, MoreKind.SWITCH),
                MoreItem(ToolbarItem.PAN, MoreKind.TOOL), MoreItem(ToolbarItem.MARKUP, MoreKind.TOOL),
            ),
            moreItems(layout, ToolSurface.NOTE, hasPdf = true),
        )
    }

    // --- the More button's two lit states (TO 901-905) ---

    @Test fun aHiddenToolInHandWinsOverASwitchThatIsOn() {
        val items = moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false)
        assertEquals(MoreLit.TOOL_IN_HAND, moreLit(items, ToolbarItem.PAN) { true })
        assertEquals(MoreLit.TOOL_IN_HAND, moreLit(items, ToolbarItem.TEXT_BOX) { false })
    }

    @Test fun aHiddenSwitchThatIsOnLightsTheButton() {
        val items = moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false)
        assertEquals(MoreLit.SWITCH_ON, moreLit(items, ToolbarItem.PEN) { it == ToolbarItem.RULER })
    }

    @Test fun nothingHiddenInHandOrOnLeavesItUnlit() {
        val items = moreItems(ToolbarLayout.DEFAULT, ToolSurface.NOTE, hasPdf = false)
        assertEquals(MoreLit.NONE, moreLit(items, ToolbarItem.PEN) { false })
        assertEquals(MoreLit.NONE, moreLit(items, null) { false })
        // An action is never "on".
        assertEquals(MoreLit.NONE, moreLit(listOf(MoreItem(ToolbarItem.IMAGE, MoreKind.ACTION)), ToolbarItem.IMAGE) { true })
    }

    // --- stabilisation steps ---

    @Test fun stabilisationHasFourEvenSteps() {
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), STAB_STEPS)
        for (i in 0..3) assertEquals(i, stabIndex(stabValue(i)))
    }

    @Test fun aStoredValueBetweenStepsSnapsToTheNearest() {
        assertEquals(0, stabIndex(0.1f))
        assertEquals(1, stabIndex(0.2f))
        assertEquals(1, stabIndex(0.45f))
        assertEquals(2, stabIndex(0.55f))
        assertEquals(3, stabIndex(0.9f))
    }

    @Test fun stabilisationOutOfRangeIsClamped() {
        assertEquals(0, stabIndex(-1f))
        assertEquals(3, stabIndex(5f))
        assertEquals(0f, stabValue(-1), 0f)
        assertEquals(1f, stabValue(9), 0f)
    }

    // --- widths in millimetres ---

    @Test fun widthsReadInMillimetresWithAndWithoutTheUnit() {
        // 150 dpi: 1 mm = 5.9055 px.
        assertEquals("0.5 mm", widthLabelMm(3f))
        assertEquals("1.0 mm", widthLabelMm(150 / 25.4f))
        assertEquals("0.2 mm", widthLabelMm(1f))
        assertEquals("3.4 mm", widthLabelMm(20f))
        assertEquals("0.5", widthValueMm(3f))
        assertEquals("3.4", widthValueMm(20f))
    }

    @Test fun theDoubleOverloadsReadTheSame() {
        // Today's callers (PenPopover, SelectionMenu, TapePopover, the pen box) pass a Double.
        assertEquals(widthLabelMm(3f), widthLabelMm(3.0))
        assertEquals(widthLabelMm(20f), widthLabelMm(20.0))
        assertEquals(widthValueMm(7.5f), widthValueMm(7.5))
    }

    @Test fun oneThicknessStepIsExactlyATenthOfAMillimetre() {
        val range = 1f..20f
        assertEquals(0.6f * 150 / 25.4f, stepWidthPx(3f, 1, range), 1e-4f)
        assertEquals("0.6 mm", widthLabelMm(stepWidthPx(3f, 1, range)))
        assertEquals("0.4 mm", widthLabelMm(stepWidthPx(3f, -1, range)))
        assertEquals("0.8 mm", widthLabelMm(stepWidthPx(3f, 3, range)))
        // A width off the 0.1 mm grid snaps to it first (3.2 px = 0.54 mm reads 0.5, so one step is 0.6).
        assertEquals("0.6 mm", widthLabelMm(stepWidthPx(3.2f, 1, range)))
    }

    @Test fun theStepperStopsAtTheToolsRange() {
        val range = 1f..20f
        assertEquals(20f, stepWidthPx(20f, 1, range), 1e-4f)
        assertEquals(1f, stepWidthPx(1f, -1, range), 1e-4f)
    }

    // --- save status ---

    @Test fun saveLabelPutsSavingFirstThenEdited() {
        assertEquals(SaveLabel.SAVING, saveLabel(saving = true, dirty = true))
        assertEquals(SaveLabel.SAVING, saveLabel(saving = true, dirty = false))
        assertEquals(SaveLabel.EDITED, saveLabel(saving = false, dirty = true))
        assertEquals(SaveLabel.SAVED, saveLabel(saving = false, dirty = false))
    }

    @Test fun theHeaderKeepsItsLabelWhileThePenIsDown() {
        assertEquals(SaveLabel.SAVED, nextShownLabel(SaveLabel.SAVED, SaveLabel.SAVING, penDown = true))
        assertEquals(SaveLabel.SAVING, nextShownLabel(SaveLabel.SAVED, SaveLabel.SAVING, penDown = false))
    }

    @Test fun theTickPlaysOnlyOnTheWayIntoSaved() {
        assertTrue(playsSavedCheck(SaveLabel.SAVING, SaveLabel.SAVED))
        assertTrue(playsSavedCheck(SaveLabel.EDITED, SaveLabel.SAVED))
        assertFalse(playsSavedCheck(SaveLabel.SAVED, SaveLabel.SAVED))
        assertFalse(playsSavedCheck(SaveLabel.SAVED, SaveLabel.EDITED))
        assertFalse(playsSavedCheck(SaveLabel.EDITED, SaveLabel.SAVING))
    }

    // --- the Insert card ---

    @Test fun aPagedNoteGetsTheWideRowThenTheGridOfSix() {
        val l = insertLayout(NOTE_INSERT_KINDS, sticky = true, table = true)
        assertEquals(listOf(InsertTile.PDF, InsertTile.VOICE), l.wide)
        assertEquals(
            listOf(InsertTile.IMAGE, InsertTile.CAMERA, InsertTile.SCAN, InsertTile.AUDIO_FILE, InsertTile.STICKY_NOTE, InsertTile.TABLE),
            l.grid,
        )
    }

    @Test fun theCanvasGetsOneRowOfPictures() {
        val l = insertLayout(CANVAS_INSERT_KINDS, sticky = false, table = false)
        assertTrue(l.wide.isEmpty())
        assertEquals(listOf(InsertTile.IMAGE, InsertTile.CAMERA, InsertTile.SCAN), l.grid)
    }

    @Test fun stickyNoteAndTableShowOnlyWhenOffered() {
        val l = insertLayout(NOTE_INSERT_KINDS, sticky = false, table = true)
        assertEquals(listOf(InsertTile.IMAGE, InsertTile.CAMERA, InsertTile.SCAN, InsertTile.AUDIO_FILE, InsertTile.TABLE), l.grid)
    }

    @Test fun eachMediaTileNamesItsInsertKind() {
        assertEquals(InsertKind.PDF, InsertTile.PDF.kind)
        assertEquals(InsertKind.AUDIO_FILE, InsertTile.AUDIO_FILE.kind)
        assertNull(InsertTile.STICKY_NOTE.kind)
        assertNull(InsertTile.TABLE.kind)
    }

    // --- the pen box rail ---

    @Test fun theRailIsOnTheLeftOnlyWhenTheBarIsOnTheRight() {
        assertTrue(penRailOnLeft(ToolbarPosition.RIGHT))
        assertFalse(penRailOnLeft(ToolbarPosition.LEFT))
        assertFalse(penRailOnLeft(ToolbarPosition.TOP))
        assertFalse(penRailOnLeft(ToolbarPosition.BOTTOM))
    }

    @Test fun theRailNeedsABoxAndFourHundredEightyDpOfHeight() {
        assertTrue(showsPenRail(hasPenBox = true, screenHeightDp = 480))
        assertFalse(showsPenRail(hasPenBox = true, screenHeightDp = 479))
        assertFalse(showsPenRail(hasPenBox = false, screenHeightDp = 800))
    }

    // --- a header card clear of the pane's top bar (gate: the canvas Styles card over the bar's swatches) ---

    @Test fun aHeaderCardThatMeetsTheTopBarDropsUnderIt() {
        val bar = IntRect(200, 96, 1080, 156)
        val p = PopoverPlacement(900, 78, PopoverSide.BELOW, 0.5f, 0f)
        val moved = clearOfTopBar(p, IntSize(340, 450), bar, barTop = true, window = window, gap = 10, margin = 8)
        assertEquals(166, moved.y)
        assertEquals(900, moved.x)
    }

    @Test fun aHeaderCardClearOfTheBarOrWithASideBarStaysPut() {
        val p = PopoverPlacement(1100, 78, PopoverSide.BELOW, 0.5f, 0f)
        val bar = IntRect(200, 96, 1080, 156)
        assertEquals(p, clearOfTopBar(p, IntSize(170, 450), bar, barTop = true, window = window, gap = 10, margin = 8))
        val q = p.copy(x = 900)
        assertEquals(q, clearOfTopBar(q, IntSize(340, 450), IntRect(0, 70, 76, 800), barTop = false, window = window, gap = 10, margin = 8))
        assertEquals(q, clearOfTopBar(q, IntSize(340, 450), null, barTop = true, window = window, gap = 10, margin = 8))
        assertEquals(q, clearOfTopBar(q, IntSize(340, 450), IntRect.Zero, barTop = true, window = window, gap = 10, margin = 8))
    }
}
