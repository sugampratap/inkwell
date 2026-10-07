package com.xnotes.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** What the cover shelf keeps between compositions, so a tile scrolled back into view costs as little as it can. */
class CoverShelfReuseTest {

    private var asked = 0

    /** Stands in for StepBased's search: answers from the room it is given, and counts how often it runs. */
    private fun FitMemo.fitIn(width: Int, text: String, density: Float = 2f, fontScale: Float = 1f): TextUnit =
        getOrFit(text, Constraints(maxWidth = width, maxHeight = 400), density, fontScale) { asked++; (width / 10 + text.length).sp }

    @Test fun aTitleSeenAgainInTheSameRoomTakesItsSizeWithoutAskingAgain() {
        val memo = FitMemo(16)
        val first = memo.fitIn(300, "Field notes")
        val again = memo.fitIn(300, "Field notes")
        assertEquals(first, again)
        assertEquals((300 / 10 + "Field notes".length).sp, first) // the very size the search gives
        assertEquals(1, asked)
    }

    @Test fun anotherTitleRoomOrTypeScaleIsFittedAfresh() {
        val memo = FitMemo(16)
        memo.fitIn(300, "Field notes")
        memo.fitIn(300, "Recipes")
        memo.fitIn(240, "Field notes")
        memo.fitIn(300, "Field notes", fontScale = 1.3f)
        memo.fitIn(300, "Field notes", density = 3f)
        assertEquals(5, asked)
    }

    @Test fun keepsOnlySoManySizesTheLeastLatelyUsedGoingFirst() {
        val memo = FitMemo(2)
        memo.fitIn(300, "a")
        memo.fitIn(300, "b")
        memo.fitIn(300, "a") // a is now the latest used
        memo.fitIn(300, "c") // so b goes
        assertEquals(3, asked)
        memo.fitIn(300, "a")
        assertEquals(3, asked)
        memo.fitIn(300, "b")
        assertEquals(4, asked)
    }

    @Test fun eachCallSiteKeepsItsOwnSizes() {
        val step = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp)
        assertNotEquals(RememberedAutoSize(step), RememberedAutoSize(step))
    }

    @Test fun notebooksAreReusedByDesignAndPapersByKind() {
        val names = listOf("Weekly", "Recipes", "Field notes", "Ideas", "Travel", "Lectures", "Gate long", "Sketches")
        for (n in names) {
            assertEquals(coverDesign(n, CoverPalette.colorFor(n, null).luminance()), coverContentType(n, EntryKind.NOTE, null))
        }
        assertEquals(EntryKind.CANVAS, coverContentType("Board.xcanvas", EntryKind.CANVAS, null))
        assertEquals(EntryKind.PDF, coverContentType("Paper", EntryKind.PDF, null))
        // A picked cloth decides the design as the shelf draws it.
        assertEquals(coverDesign("Weekly", CoverPalette.colors[13].luminance()), coverContentType("Weekly", EntryKind.NOTE, 13))
    }
}
