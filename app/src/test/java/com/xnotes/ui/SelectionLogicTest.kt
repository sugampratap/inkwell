package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.ui.SelAction.ADD_COLUMN
import com.xnotes.ui.SelAction.ADD_ROW
import com.xnotes.ui.SelAction.BACKWARD
import com.xnotes.ui.SelAction.COPY
import com.xnotes.ui.SelAction.COPY_AS_IMAGE
import com.xnotes.ui.SelAction.CROP
import com.xnotes.ui.SelAction.CUT
import com.xnotes.ui.SelAction.DELETE
import com.xnotes.ui.SelAction.DUPLICATE
import com.xnotes.ui.SelAction.EDIT
import com.xnotes.ui.SelAction.FLIP_H
import com.xnotes.ui.SelAction.FLIP_V
import com.xnotes.ui.SelAction.FORWARD
import com.xnotes.ui.SelAction.HEADER
import com.xnotes.ui.SelAction.LOCK
import com.xnotes.ui.SelAction.NOTE_COLOUR
import com.xnotes.ui.SelAction.PASTE
import com.xnotes.ui.SelAction.REPLACE
import com.xnotes.ui.SelAction.RESET_IMAGE
import com.xnotes.ui.SelAction.ROTATE
import com.xnotes.ui.SelAction.ROTATE_LEFT
import com.xnotes.ui.SelAction.SAVE_AS_IMAGE
import com.xnotes.ui.SelAction.SAVE_IMAGE
import com.xnotes.ui.SelAction.SELECT_ALL
import com.xnotes.ui.SelAction.SHARE_AS_IMAGE
import com.xnotes.ui.SelAction.STYLE
import com.xnotes.ui.SelAction.TO_BACK
import com.xnotes.ui.SelAction.TO_FRONT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The selection bar (selbar-fluent.html, its Settings) and the long-press card (r2_selection_colour Frames 2-3). dp = 1. */
class SelectionLogicTest {

    private val ink = SelFacts(canStyle = true, canRotate = true, canFlip = true)
    private val window = IntSize(1280, 800)

    // --- the default bar and More (selbar-fluent.html) ---

    @Test fun inkLeadsWithStyleThenTheClipboardThenRotateAndSelectAll() {
        assertEquals(listOf(listOf(STYLE), listOf(CUT, COPY, DELETE), listOf(ROTATE, SELECT_ALL)), selectionGroups(ink))
    }

    @Test fun pasteJoinsTheClipboardWhileSomethingIsCopied() {
        assertEquals(listOf(CUT, COPY, PASTE, DELETE), selectionGroups(ink.copy(canPaste = true))[1])
    }

    @Test fun inksMoreIsTheMockupsFiveGroups() {
        assertEquals(
            listOf(
                listOf(DUPLICATE),
                listOf(FLIP_H, FLIP_V, ROTATE_LEFT),
                listOf(TO_FRONT, FORWARD, BACKWARD, TO_BACK),
                listOf(COPY_AS_IMAGE, SHARE_AS_IMAGE, SAVE_AS_IMAGE),
                listOf(LOCK),
            ),
            selectionMoreGroups(ink.copy(canPaste = true)),
        )
    }

    private val picture = SelFacts(image = true, canRotate = true, canFlip = true, canPaste = true)

    @Test fun onePictureLeadsWithCropAndGetsReplace() {
        assertEquals(
            listOf(listOf(CROP), listOf(CUT, COPY, PASTE, DELETE), listOf(ROTATE, REPLACE, SELECT_ALL)),
            selectionGroups(picture),
        )
    }

    @Test fun aPicturesMoreHasItsSaveAndResetAfterAnEdit() {
        assertEquals(
            listOf(
                listOf(DUPLICATE),
                listOf(SAVE_IMAGE, RESET_IMAGE),
                listOf(FLIP_H, FLIP_V, ROTATE_LEFT),
                listOf(TO_FRONT, FORWARD, BACKWARD, TO_BACK),
                listOf(COPY_AS_IMAGE, SHARE_AS_IMAGE, SAVE_AS_IMAGE),
                listOf(LOCK),
            ),
            selectionMoreGroups(picture.copy(imageEdited = true)),
        )
        assertEquals(listOf(SAVE_IMAGE), selectionMoreGroups(picture)[1])
    }

    @Test fun aStickyNoteLeadsWithEditAndItsColour() {
        assertEquals(
            listOf(listOf(EDIT, NOTE_COLOUR), listOf(CUT, COPY, DELETE), listOf(SELECT_ALL)),
            selectionGroups(SelFacts(editable = true, sticky = true)),
        )
    }

    @Test fun aTextBoxLeadsWithEditAndCannotTurn() {
        assertEquals(listOf(listOf(EDIT), listOf(CUT, COPY, DELETE), listOf(SELECT_ALL)), selectionGroups(SelFacts(editable = true)))
        val more = selectionMoreGroups(SelFacts(editable = true)).flatten()
        assertFalse(ROTATE_LEFT in more || FLIP_H in more || FLIP_V in more)
    }

    @Test fun aTableBringsItsRowColumnAndHeaderToolsToTheBar() {
        assertEquals(
            listOf(listOf(EDIT), listOf(ADD_ROW, ADD_COLUMN, HEADER), listOf(CUT, COPY, DELETE), listOf(SELECT_ALL)),
            selectionGroups(SelFacts(editable = true, table = true)),
        )
    }

    @Test fun aMixedSelectionWithNothingToRestyleStartsAtTheClipboard() {
        assertEquals(listOf(listOf(CUT, COPY, DELETE), listOf(SELECT_ALL)), selectionGroups(SelFacts()))
    }

    @Test fun everyActionThatWorksIsOnTheBarOrInMoreExactlyOnce() {
        val choices = listOf(SEL_BAR_DEFAULT, emptySet(), SelAction.entries.toSet(), setOf(LOCK, TO_BACK, CUT))
        for (kind in SelKind.entries) for (chosen in choices) for (width in listOf(Int.MAX_VALUE, 400, 100)) {
            val l = selectionMenuLayout(kind.facts, chosen, width)
            val everywhere = l.bar.flatten() + l.overflow + l.more.flatten()
            assertEquals("$kind $chosen $width", everywhere.size, everywhere.toSet().size)
            assertEquals("$kind $chosen $width", SelAction.entries.filter { it.appliesTo(kind.facts) }.toSet(), everywhere.toSet())
        }
    }

    // --- the user's own bar (Settings › General › Selection bar) ---

    @Test fun aChosenActionJoinsTheBarInItsPlaceAndLeavesMore() {
        val chosen = SEL_BAR_DEFAULT + DUPLICATE + LOCK - SELECT_ALL
        assertEquals(listOf(listOf(STYLE), listOf(CUT, COPY, DELETE, DUPLICATE), listOf(ROTATE), listOf(LOCK)), selectionGroups(ink, chosen))
        val more = selectionMoreGroups(ink, chosen)
        assertEquals(listOf(FLIP_H, FLIP_V, ROTATE_LEFT, SELECT_ALL), more[0])
        assertFalse(DUPLICATE in more.flatten() || LOCK in more.flatten())
    }

    @Test fun anActionThatDoesNotApplyIsSkippedEvenWhenChosen() {
        val bar = selectionGroups(SelFacts(editable = true), SelAction.entries.toSet()).flatten()
        assertFalse(STYLE in bar || CROP in bar || HEADER in bar || ROTATE in bar || PASTE in bar || NOTE_COLOUR in bar)
    }

    @Test fun anEmptyBarLeavesOnlyMore() {
        val l = selectionMenuLayout(ink, emptySet(), 1000)
        assertTrue(l.bar.isEmpty())
        // More (64) and the pill's padding: no divider before More with nothing beside it.
        assertEquals(SEL_PADDING_DP + SEL_MORE_DP, selectionBarWidthDp(l.bar))
        assertEquals(listOf(STYLE), l.more.first())
    }

    @Test fun savedIdsComeBackAsTheChoiceAndTheDefaultSavesAsNull() {
        assertEquals(SEL_BAR_DEFAULT, selectionBarChoice(null))
        assertNull(selectionBarIds(SEL_BAR_DEFAULT))
        val chosen = setOf(LOCK, CUT, STYLE)
        assertEquals(listOf("style", "cut", "lock"), selectionBarIds(chosen))
        assertEquals(chosen, selectionBarChoice(selectionBarIds(chosen)))
        assertEquals(setOf(CUT), selectionBarChoice(listOf("cut", "no_such_action")))
        assertEquals(emptySet<SelAction>(), selectionBarChoice(emptyList()))
        assertEquals(emptyList<String>(), selectionBarIds(emptySet()))
    }

    @Test fun everyIdIsUniqueAndFindsItsAction() {
        assertEquals(SelAction.entries.size, SelAction.entries.map { it.id }.toSet().size)
        for (a in SelAction.entries) assertEquals(a, SelAction.fromId(a.id))
    }

    @Test fun theFixedOrderKeepsEachGroupTogether() {
        val groups = SelAction.entries.map { it.group }
        assertEquals(SelGroup.entries.toList(), groups.distinct())
        assertEquals(groups.sorted(), groups)
    }

    @Test fun settingsStopsAtTwelveForTheFullestKind() {
        // The default table bar holds 9; three more fill it.
        val chosen = SEL_BAR_DEFAULT + DUPLICATE + TO_FRONT + FORWARD
        assertEquals(12, selectionBarCount(SelKind.TABLE, chosen))
        assertFalse(canPutOnBar(chosen, LOCK))
        // A picture's own action does not reach a table's bar, so it still fits.
        assertTrue(canPutOnBar(chosen, SAVE_IMAGE))
        // What is on can always come off.
        assertTrue(canPutOnBar(chosen, FORWARD))
        assertTrue(canPutOnBar(SEL_BAR_DEFAULT, DUPLICATE))
    }

    // --- width and the narrow-pane fit (SC 229) ---

    @Test fun theMockupsInkBarIs599Wide() {
        // 20 padding + Style 64 + (4 × 64) + (Rotate 64 + Select all 72) + More 64 + 3 dividers of 13 + 10 gaps of 2.
        assertEquals(599, selectionBarWidthDp(selectionGroups(ink.copy(canPaste = true))))
    }

    @Test fun aBarThatFitsKeepsEverything() {
        val groups = selectionGroups(ink.copy(canPaste = true))
        val bar = fitSelectionBar(groups, 599)
        assertEquals(groups, bar.groups)
        assertTrue(bar.overflow.isEmpty())
    }

    @Test fun aNarrowPaneDropsTheLowestPriorityFirst() {
        val bar = fitSelectionBar(selectionGroups(ink.copy(canPaste = true)), 500)
        assertEquals(listOf(listOf(STYLE), listOf(CUT, COPY, DELETE), listOf(ROTATE)), bar.groups)
        assertEquals(listOf(PASTE, SELECT_ALL), bar.overflow)
    }

    @Test fun anEmptiedGroupTakesItsDividerWithIt() {
        val bar = fitSelectionBar(selectionGroups(ink.copy(canPaste = true)), 300)
        assertEquals(listOf(listOf(STYLE), listOf(COPY, DELETE)), bar.groups)
        assertEquals(listOf(CUT, PASTE, ROTATE, SELECT_ALL), bar.overflow)
        assertEquals(312, selectionBarWidthDp(bar.groups))
    }

    @Test fun atLeastThreeActionsStayHoweverNarrow() {
        assertEquals(listOf(listOf(STYLE), listOf(COPY, DELETE)), fitSelectionBar(selectionGroups(ink), 100).groups)
    }

    @Test fun editAndColourNeverLeaveTheBar() {
        val bar = fitSelectionBar(selectionGroups(SelFacts(editable = true, sticky = true)), 300)
        assertEquals(listOf(listOf(EDIT, NOTE_COLOUR), listOf(CUT, COPY, DELETE)), bar.groups)
        assertEquals(listOf(SELECT_ALL), bar.overflow)
    }

    @Test fun aTablesToolsGoToMoreBeforeTheClipboardDoes() {
        val bar = fitSelectionBar(selectionGroups(SelFacts(editable = true, table = true)), 500)
        assertEquals(listOf(listOf(EDIT), listOf(CUT, COPY, DELETE), listOf(SELECT_ALL)), bar.groups)
        assertEquals(listOf(ADD_ROW, ADD_COLUMN, HEADER), bar.overflow)
    }

    @Test fun noMoreThanTwelveSitOnTheBarHoweverWide() {
        val bar = fitSelectionBar(selectionGroups(ink.copy(canPaste = true), SelAction.entries.toSet()), Int.MAX_VALUE)
        assertEquals(SEL_BAR_MAX, bar.groups.flatten().size)
        assertEquals(listOf(ROTATE_LEFT, SELECT_ALL, TO_BACK, COPY_AS_IMAGE, SHARE_AS_IMAGE, SAVE_AS_IMAGE, LOCK), bar.overflow)
    }

    @Test fun whatTheBarHadNoRoomForLeadsMore() {
        val l = selectionMenuLayout(picture, SEL_BAR_DEFAULT, 500)
        assertTrue(l.overflow.isNotEmpty())
        assertTrue(l.overflow.none { it in l.onBar })
        assertEquals(listOf(DUPLICATE), l.more.first())
    }

    // --- where the bar goes (SC 577-579) ---

    private val sel = IntRect(124, 300, 272, 474)
    private val barSize = IntSize(589, 64)

    @Test fun theBarSits12OverTheSelectionKeptInThePane() {
        assertEquals(IntOffset(8, 224), selectionBarSpot(sel, barSize, window, clearTop = 120, dp = 1f))
    }

    @Test fun nearTheToolbarTheBarGoesUnderTheSelection() {
        assertEquals(486, selectionBarSpot(IntRect(124, 150, 272, 474), barSize, window, 120, 1f).y)
    }

    @Test fun aSelectionFillingTheViewGetsTheBarJustInsideItsTop() {
        assertEquals(120, selectionBarSpot(IntRect(100, 100, 1100, 780), barSize, window, 120, 1f).y)
    }

    // --- where its menus go (SC 580-584) ---

    private val menu = IntSize(236, 200)

    @Test fun aMenuHangs8UnderTheBarFromItsButton() {
        val p = selectionMenuSpot(IntRect(400, 224, 458, 276), 224, 288, sel, menu, window, 1f)
        assertEquals(400, p.x)
        assertEquals(296, p.y)
        assertEquals(29f / 236f, p.originX, 1e-6f)
        assertEquals(0f, p.originY, 1e-6f)
    }

    @Test fun overTheSelectionWithNoRoomLeftItStepsRightOfIt() {
        val p = selectionMenuSpot(IntRect(200, 224, 258, 276), 224, 288, sel, menu, window, 1f)
        assertEquals(288, p.x)
        assertEquals(16f / 236f, p.originX, 1e-6f)
    }

    @Test fun overTheSelectionWithRoomLeftItStepsLeftOfIt() {
        val p = selectionMenuSpot(IntRect(620, 224, 678, 276), 224, 288, IntRect(600, 300, 800, 474), menu, window, 1f)
        assertEquals(348, p.x)
        assertEquals(220f / 236f, p.originX, 1e-6f)
    }

    @Test fun underABarBelowTheSelectionTheMenuStaysUnderItsButton() {
        assertEquals(200, selectionMenuSpot(IntRect(200, 262, 258, 326), 262, 326, IntRect(124, 100, 272, 250), menu, window, 1f).x)
    }

    @Test fun withNoRoomUnderTheBarAMenuOpensOverItGrowingFromItsFoot() {
        // A bar near the window's foot (680..744 of 800): More, 344 tall, opens 8 over it instead of being pushed up
        // across the bar and its lit button.
        val p = selectionMenuSpot(IntRect(1000, 686, 1058, 738), 680, 744, IntRect(900, 100, 1100, 300), IntSize(236, 344), window, 1f)
        assertEquals(680 - 8 - 344, p.y)
        assertEquals(1000, p.x)
        assertEquals(1f, p.originY, 1e-6f)
    }

    @Test fun overTheBarAMenuStillStepsBesideTheSelection() {
        // Flipped over a bar that sits under the selection, the menu would cover it, so it steps to its left.
        val p = selectionMenuSpot(IntRect(500, 686, 558, 738), 680, 744, IntRect(450, 300, 700, 660), IntSize(236, 344), window, 1f)
        assertEquals(328, p.y)
        assertEquals(450 - 16 - 236, p.x)
    }

    @Test fun withNoRoomEitherSideAMenuStaysUnderTheBarInsideTheWindow() {
        val short = IntSize(1280, 400)
        val p = selectionMenuSpot(IntRect(1000, 150, 1058, 202), 144, 208, IntRect(900, 20, 1100, 120), IntSize(236, 344), short, 1f)
        assertEquals(400 - 8 - 344, p.y)
        assertEquals(0f, p.originY, 1e-6f)
    }

    // --- a menu's height is measured for the side it opens on (emulator pass, 2026-10-07) ---

    @Test fun aMenuCappedToTheRoomUnderTheBarStillOpensUnderIt() {
        // A bar high up (200..264 of 800): More was capped to the room under the button, then flipped over the bar
        // by a few px of difference and clipped there, Select all and Lock scrolled out of view.
        val cap = selectionMenuMaxHeight(200, 264, window, 1f)
        assertEquals(800 - 8 - (264 + 8), cap)
        val p = selectionMenuSpot(IntRect(900, 206, 964, 258), 200, 264, IntRect(100, 300, 400, 500), IntSize(248, cap), window, 1f)
        assertEquals(PopoverSide.BELOW, p.side)
        assertEquals(272, p.y)
        assertTrue(p.y + cap <= 800 - 8)
    }

    @Test fun aMenuTakesTheSideWithRoomAndFitsThere() {
        // A bar low down (600..664): more room over it, so the cap is that room and the menu opens over the bar, whole.
        val cap = selectionMenuMaxHeight(600, 664, window, 1f)
        assertEquals(600 - 8 - 8, cap)
        val p = selectionMenuSpot(IntRect(900, 606, 964, 658), 600, 664, IntRect(100, 100, 400, 550), IntSize(248, cap), window, 1f)
        assertEquals(PopoverSide.ABOVE, p.side)
        assertEquals(8, p.y)
        assertEquals(600 - 8, p.y + cap)
    }

    @Test fun aShortMenuStaysUnderTheBarWhereItFits() {
        val p = selectionMenuSpot(IntRect(900, 606, 964, 658), 600, 664, IntRect(100, 100, 400, 550), IntSize(248, 100), window, 1f)
        assertEquals(PopoverSide.BELOW, p.side)
        assertEquals(672, p.y)
    }

    @Test fun withLittleRoomEitherSideTheCapIsTheWholeArea() {
        // 170 under the bar is enough to hang there; 114 either side is not, so it may take the whole height.
        assertEquals(170, selectionMenuMaxHeight(150, 214, IntSize(1280, 400), 1f))
        assertEquals(400 - 16, selectionMenuMaxHeight(130, 270, IntSize(1280, 400), 1f))
    }

    // --- menus keep to the editor pane and off the toolbar ---

    @Test fun inTheRightPaneOfASplitAMenuNeverCrossesTheDivider() {
        // The right pane is 640..1280; the selection sits at its left, so the menu cannot step left of it.
        val pane = IntRect(640, 0, 1280, 800)
        val p = selectionMenuSpot(IntRect(700, 224, 764, 276), 224, 288, IntRect(660, 300, 900, 474), menu, window, 1f, pane)
        assertEquals(900 + 16, p.x)
        // Stepping left is fine while it stays in the pane.
        val q = selectionMenuSpot(IntRect(1000, 224, 1064, 276), 224, 288, IntRect(950, 300, 1200, 474), menu, window, 1f, pane)
        assertEquals(950 - 16 - 236, q.x)
        assertTrue(q.x >= 640 + 8)
    }

    @Test fun aMenuStaysClearOfAFloatingLeftToolbar() {
        // A picture at the pane's far left, the toolbar covering its left 68 px: More keeps 8 px right of the toolbar.
        val clear = IntRect(68, 0, 1280, 800)
        val p = selectionMenuSpot(IntRect(20, 224, 84, 276), 224, 288, IntRect(500, 600, 700, 700), menu, window, 1f, clear)
        assertEquals(68 + 8, p.x)
    }

    @Test fun aMenuStaysInsideThePanesHeight() {
        // A pane whose foot is at 600 (a bottom toolbar's cover): a menu with no room under the bar opens over it.
        val clear = IntRect(0, 0, 1280, 600)
        val p = selectionMenuSpot(IntRect(900, 486, 964, 538), 480, 544, IntRect(100, 100, 400, 300), IntSize(248, 200), window, 1f, clear)
        assertEquals(PopoverSide.ABOVE, p.side)
        assertEquals(480 - 8 - 200, p.y)
    }

    @Test fun theBarKeepsClearOfAFloatingSideToolbar() {
        val left = SelInsets(left = 68)
        assertEquals(68 + 8, selectionBarSpot(IntRect(0, 300, 100, 474), barSize, window, 120, 1f, left).x)
        val right = SelInsets(right = 68)
        assertEquals(1280 - 68 - 8 - 589, selectionBarSpot(IntRect(1180, 300, 1280, 474), barSize, window, 120, 1f, right).x)
        // A bottom toolbar: no room under the selection above it, so the bar goes just inside the selection's top.
        val bottom = SelInsets(bottom = 76)
        assertEquals(162, selectionBarSpot(IntRect(124, 150, 272, 700), barSize, window, 120, 1f, bottom).y)
    }

    // --- the long-press card (SC 649-650) ---

    private val lp = IntSize(320, 330)

    @Test fun theCardRises28OverThePressCentredOnIt() {
        val p = longPressSpot(IntOffset(590, 580), lp, window, 1f)
        assertEquals(430, p.x)
        assertEquals(222, p.y)
        assertEquals(0.5f, p.originX, 1e-6f)
        assertEquals(1f, p.originY, 1e-6f)
    }

    @Test fun nearTheTopItDrops28UnderThePress() {
        val p = longPressSpot(IntOffset(590, 100), lp, window, 1f)
        assertEquals(128, p.y)
        assertEquals(0f, p.originY, 1e-6f)
    }

    @Test fun nearAnEdgeItStays8InsideTheWindow() {
        val p = longPressSpot(IntOffset(50, 580), lp, window, 1f)
        assertEquals(8, p.x)
        assertEquals(42f / 320f, p.originX, 1e-6f)
    }

    // --- what the long-press card holds (SC 635-643, 667-670) ---

    @Test fun onAPageWithSomethingCopiedItHasEveryRowAndFourTiles() {
        val l = longPressLayout(locked = false, clipItems = true, clipImage = true, selectAll = true, sharePage = true, insertObjects = true)
        assertEquals(listOf(LpRow.PASTE, LpRow.PASTE_IMAGE, LpRow.SELECT_ALL, LpRow.COPY_PAGE, LpRow.SHARE_PAGE), l.rows)
        assertTrue(l.ruleBeforeSelectAll)
        assertEquals(listOf(LpTile.STICKY_NOTE, LpTile.TEXT_BOX, LpTile.IMAGE, LpTile.TABLE), l.tiles)
        assertTrue(l.ruleBeforeTiles)
    }

    @Test fun withNothingToPasteSelectAllLeadsWithNoRule() {
        val l = longPressLayout(false, clipItems = false, clipImage = false, selectAll = true, sharePage = true, insertObjects = true)
        assertEquals(listOf(LpRow.SELECT_ALL, LpRow.COPY_PAGE, LpRow.SHARE_PAGE), l.rows)
        assertFalse(l.ruleBeforeSelectAll)
    }

    @Test fun theCanvasOffersPasteSelectAllAndImageOnly() {
        val l = longPressLayout(false, clipItems = true, clipImage = false, selectAll = true, sharePage = false, insertObjects = false)
        assertEquals(listOf(LpRow.PASTE, LpRow.SELECT_ALL), l.rows)
        assertEquals(listOf(LpTile.IMAGE), l.tiles)
    }

    @Test fun anEmptyCanvasOffersOnlyTheImageTile() {
        val l = longPressLayout(false, clipItems = false, clipImage = false, selectAll = false, sharePage = false, insertObjects = false)
        assertTrue(l.rows.isEmpty())
        assertFalse(l.ruleBeforeTiles)
        assertEquals(listOf(LpTile.IMAGE), l.tiles)
    }

    @Test fun aLockedObjectOffersOnlyUnlock() {
        val l = longPressLayout(true, clipItems = true, clipImage = true, selectAll = true, sharePage = true, insertObjects = true)
        assertTrue(l.unlockOnly)
        assertTrue(l.rows.isEmpty())
        assertTrue(l.tiles.isEmpty())
    }
}
