package com.xnotes.ui

import com.xnotes.core.model.Rgba
import com.xnotes.core.text.CharStyle
import com.xnotes.core.text.FlowDefaults
import com.xnotes.core.text.ListKind
import com.xnotes.core.text.ParaAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLogicTest {

    // --- the format pill's state ---

    private fun state(
        style: CharStyle = CharStyle(),
        list: ListKind? = null,
        heading: Int = 0,
        code: String? = null,
        align: ParaAlign? = null,
        indent: Int = 0,
        inCell: Boolean = false,
        active: Boolean = true,
    ) = textFormatState(style, list, heading, code, align, indent, inCell, active)

    @Test fun aCaretWithNoParagraphShowsPlainBodyText() {
        val s = state()
        assertEquals(ListKind.NONE, s.list)
        assertEquals(ParaAlign.LEFT, s.align)
        assertEquals(0, s.headingLevel)
        assertFalse(s.codeOn)
        assertFalse(s.alignOn)
    }

    @Test fun inATableCellTheBlockControlsAreOff() {
        val s = state(inCell = true, indent = 2)
        assertFalse(s.headingEnabled)
        assertFalse(s.listsEnabled)
        assertFalse(s.codeEnabled)
        assertFalse(s.indentEnabled)
        assertFalse(s.tableEnabled)
        assertFalse(s.languageEnabled)
        // Outdent and Equation do not depend on the cell (today's bar).
        assertTrue(s.outdentEnabled)
        assertTrue(s.equationEnabled)
    }

    @Test fun equationAndTableNeedACaretSession() {
        val s = state(active = false)
        assertFalse(s.equationEnabled)
        assertFalse(s.tableEnabled)
        assertTrue(s.headingEnabled)
    }

    @Test fun outdentOnlyWhenIndented() {
        assertFalse(state(indent = 0).outdentEnabled)
        assertTrue(state(indent = 1).outdentEnabled)
    }

    @Test fun theMoreMenuChecksEquationWhileTheCaretIsInMath() {
        assertTrue(state(style = CharStyle(math = true)).mathOn)
        assertFalse(state().mathOn)
    }

    @Test fun styleFlagsAndBlockKindsPassThrough() {
        val s = state(
            style = CharStyle(bold = true, strike = true, highlight = Rgba(255, 235, 59)),
            list = ListKind.CHECK, heading = 2, code = "", align = ParaAlign.RIGHT,
        )
        assertTrue(s.bold)
        assertFalse(s.italic)
        assertTrue(s.strike)
        assertTrue(s.highlightOn)
        assertEquals(ListKind.CHECK, s.list)
        assertEquals(2, s.headingLevel)
        assertTrue(s.codeOn) // "" is a plain code block, still a block
        assertTrue(s.alignOn)
    }

    // --- the highlight tile's letter ---

    @Test fun aLightHighlightTakesADarkLetter() {
        assertTrue(darkInkOn(Rgba(255, 235, 59)))
        assertFalse(darkInkOn(Rgba(31, 42, 68)))
    }

    @Test fun theThresholdIsStrictlyOver150() {
        assertFalse(darkInkOn(Rgba(150, 150, 150)))
        assertTrue(darkInkOn(Rgba(151, 151, 151)))
    }

    // --- heading hints ---

    @Test fun headingHintsOnlyForH1ToH4WithShortcutsOn() {
        assertEquals("##", headingHint(2, markdownOn = true))
        assertEquals("####", headingHint(4, markdownOn = true))
        assertNull(headingHint(5, markdownOn = true))
        assertNull(headingHint(0, markdownOn = true))
        assertNull(headingHint(2, markdownOn = false))
    }

    // --- code languages ---

    @Test fun everyHighlightedLanguageHasItsProperName() {
        val names = listOf("bash", "c", "cpp", "java", "javascript", "json", "kotlin", "python").map { CodeLanguages.name(it) }
        assertEquals(listOf("Bash", "C", "C++", "Java", "JavaScript", "JSON", "Kotlin", "Python"), names)
    }

    @Test fun plainTextIsLeftToTheCallerToLocalise() {
        assertNull(CodeLanguages.name("plain"))
        assertNull(CodeLanguages.name(""))
    }

    @Test fun anUnknownLanguageShowsAsTyped() {
        assertEquals("rust", CodeLanguages.name("rust"))
    }

    @Test fun tagsMatchTheMockupAndTodaysButton() {
        val tags = listOf("", "plain", "bash", "c", "cpp", "java", "javascript", "json", "kotlin", "python", "typescript").map { CodeLanguages.tag(it) }
        assertEquals(listOf("txt", "txt", "sh", "c", "cpp", "java", "js", "json", "kt", "py", "type"), tags)
    }

    @Test fun theLanguageMenuMarksTheBlocksLanguageElseTheNextOne() {
        assertEquals("cpp", CodeLanguages.menuCurrent(null, last = "cpp"))
        assertEquals("plain", CodeLanguages.menuCurrent("", last = "cpp"))
        assertEquals("python", CodeLanguages.menuCurrent("python", last = "cpp"))
    }

    // --- the Text options card's "Default for new notes" chip ---

    private val factory = FlowDefaults(sizePt = 18.0)
    private val custom = FlowDefaults(sizePt = 15.0)

    @Test fun theChipShowsOnceSomethingDiffersAndNotForFactorySettings() {
        assertTrue(TextOptionsLogic.chipShown(shownSoFar = true, config = custom, factory = factory))
        assertFalse(TextOptionsLogic.chipShown(shownSoFar = true, config = factory, factory = factory))
        assertFalse(TextOptionsLogic.chipShown(shownSoFar = false, config = custom, factory = factory))
    }

    @Test fun anApplyThatDiffersFromTheNewNoteDefaultRevealsTheChip() {
        assertTrue(TextOptionsLogic.revealsChip(next = custom, newNote = factory))
        assertFalse(TextOptionsLogic.revealsChip(next = factory, newNote = factory))
    }

    @Test fun theChipIsOnWhenThisConfigIsTheSavedDefault() {
        assertTrue(TextOptionsLogic.chipOn(config = custom, newNote = custom, factory = factory))
        assertFalse(TextOptionsLogic.chipOn(config = factory, newNote = factory, factory = factory))
        assertFalse(TextOptionsLogic.chipOn(config = custom, newNote = FlowDefaults(sizePt = 12.0), factory = factory))
    }

    @Test fun togglingTheChipSavesThisConfigOrTheFactoryOne() {
        assertEquals(custom, TextOptionsLogic.toggleTarget(on = true, config = custom, factory = factory))
        assertEquals(factory, TextOptionsLogic.toggleTarget(on = false, config = custom, factory = factory))
    }

    // --- the slash menu (TX 1157-1168), at density 1 ---

    private fun slash(count: Int, caretTop: Int, lineBottom: Int, slashLeft: Int = 100, bottomLimit: Int = 626) =
        placeSlashMenu(
            count = count, caretTop = caretTop, lineBottom = lineBottom, slashLeft = slashLeft,
            topLimit = 84, bottomLimit = bottomLimit, menuW = 352, viewW = 1280,
            rowH = 40, chrome = 46, gap = 6, lead = 6, margin = 8,
        )

    @Test fun theMenuHangsUnderTheLineWhenThreeRowsFit() {
        val p = slash(count = 12, caretTop = 200, lineBottom = 226)
        assertTrue(p.below)
        assertEquals(6, p.rows)
        assertEquals(94, p.x)
        assertEquals(232, p.y)
        assertEquals(6, p.originX)
    }

    @Test fun itFlipsAboveTheCaretWhenThePillLeavesNoRoom() {
        val p = slash(count = 12, caretTop = 520, lineBottom = 546)
        assertFalse(p.below)
        assertEquals(6, p.rows)
        assertEquals(520 - 6 - (6 * 40 + 46), p.y)
    }

    @Test fun oneCandidateNeedsOnlyOneRowBelow() {
        val p = slash(count = 1, caretTop = 474, lineBottom = 500)
        assertTrue(p.below)
        assertEquals(1, p.rows)
    }

    @Test fun withNoRoomEitherSideItKeepsOneRowOnScreen() {
        // Neither side fits a row: it takes the side with more room and moves up to end over the pill's limit.
        val p = slash(count = 6, caretTop = 100, lineBottom = 126, bottomLimit = 200)
        assertTrue(p.below)
        assertEquals(1, p.rows)
        assertEquals(200 - 86, p.y)
    }

    @Test fun withTooFewRowsBelowAndNoneAboveItHangsBelowWithWhatFits() {
        // Two rows fit below (the rule wants three), none above the caret: two rows under the line, not one off the top.
        val p = slash(count = 6, caretTop = 90, lineBottom = 116, bottomLimit = 258)
        assertTrue(p.below)
        assertEquals(2, p.rows)
        assertEquals(122, p.y)
    }

    @Test fun withNoRoomAtAllItStaysUnderTheToolbar() {
        val p = slash(count = 6, caretTop = 100, lineBottom = 126, bottomLimit = 150)
        assertEquals(1, p.rows)
        assertEquals(84, p.y)
    }

    @Test fun itStaysEightInsideTheCanvas() {
        assertEquals(1280 - 8 - 352, slash(count = 3, caretTop = 200, lineBottom = 226, slashLeft = 1250).x)
        assertEquals(8, slash(count = 3, caretTop = 200, lineBottom = 226, slashLeft = 5).x)
    }

    // --- a bar over a rect: the edit bar (TX 1047) and the style pill (TX 1256) ---

    private fun bar(
        top: Int, bottom: Int, left: Int = 400, right: Int = 600, barW: Int = 244,
        gapAbove: Int = 12, gapBelow: Int = 34, stack: Int = 0, viewW: Int = 1280,
    ) = placeTextBar(left, top, right, bottom, barW, barH = 64, viewW = viewW, gapAbove = gapAbove, gapBelow = gapBelow, stack = stack, minTop = 82, margin = 8)

    @Test fun theEditBarSitsCentredAboveTheSelection() {
        assertEquals(BarSpot(378, 224, above = true), bar(top = 300, bottom = 330))
    }

    @Test fun itGoesBelowClearOfTheTeardropsWhenTheToolbarIsInTheWay() {
        assertEquals(BarSpot(378, 184, above = false), bar(top = 120, bottom = 150))
    }

    @Test fun itIsClampedToTheCanvasBothSides() {
        assertEquals(1280 - 244 - 8, bar(top = 300, bottom = 330, left = 1200, right = 1300).x)
        assertEquals(8, bar(top = 300, bottom = 330, left = 0, right = 100).x)
    }

    @Test fun aSelectedBoxsPillStacksOverTheSelectionBar() {
        assertEquals(BarSpot(350, 152, above = true), bar(top = 300, bottom = 360, barW = 300, gapAbove = 10, gapBelow = 10, stack = 74))
        assertEquals(BarSpot(350, 444, above = false), bar(top = 200, bottom = 360, barW = 300, gapAbove = 10, gapBelow = 10, stack = 74))
    }

    @Test fun aBarWiderThanThePaneStartsAtTheMargin() {
        assertEquals(8, bar(top = 300, bottom = 330, barW = 300, viewW = 200).x)
    }

    // --- the format pill's edge (TX 801) and the caret's clearance ---

    @Test fun thePillSitsTwentyAboveTheEdgeOrTenAboveTheKeyboard() {
        assertEquals(20f, formatPillGap(imeVisible = false, coverBottomDp = 0f), 0f)
        assertEquals(10f, formatPillGap(imeVisible = true, coverBottomDp = 0f), 0f)
    }

    @Test fun aBottomToolbarPushesThePillAboveIt() {
        assertEquals(78f, formatPillGap(imeVisible = false, coverBottomDp = 68f), 0f)
        assertEquals(78f, formatPillGap(imeVisible = true, coverBottomDp = 68f), 0f)
    }

    @Test fun theCaretKeepsClearOfThePillAndItsGap() {
        assertEquals(84f, formatPillClearance(20f), 0f)
        assertEquals(74f, formatPillClearance(10f), 0f)
    }

    @Test fun menusOverThePillGetTheRoomUpToTheToolbar() {
        assertEquals(542f, roomAbove(anchorTop = 636f, topLimit = 84f, gap = 10f), 0f)
        assertEquals(0f, roomAbove(anchorTop = 80f, topLimit = 84f, gap = 10f), 0f)
    }
}
