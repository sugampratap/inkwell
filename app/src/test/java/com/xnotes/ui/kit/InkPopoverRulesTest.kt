package com.xnotes.ui.kit

import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import org.junit.Assert.assertEquals
import org.junit.Test

/** The popover's window and tap rules (review I1): a closing card takes no input, and acts once per open. */
class InkPopoverRulesTest {

    @Test fun anOpenCardIsFocusableAndWatchesOutsideTouches() {
        assertEquals(FLAG_WATCH_OUTSIDE_TOUCH, popoverWindowFlags(expanded = true, focusable = true))
    }

    @Test fun anOpenCardThatMustNotTakeTheKeyboardIsNotFocusable() {
        assertEquals(FLAG_WATCH_OUTSIDE_TOUCH or FLAG_NOT_FOCUSABLE, popoverWindowFlags(expanded = true, focusable = false))
    }

    @Test fun aClosingCardTakesNoTouchesAtAll() {
        // Whatever it was opened as: a stroke or a second tap over the fading card reaches what is under it.
        for (focusable in listOf(true, false)) {
            val flags = popoverWindowFlags(expanded = false, focusable = focusable)
            assertEquals(FLAG_NOT_TOUCHABLE, flags and FLAG_NOT_TOUCHABLE)
            assertEquals(FLAG_NOT_FOCUSABLE, flags and FLAG_NOT_FOCUSABLE)
            assertEquals(0, flags and FLAG_WATCH_OUTSIDE_TOUCH)
        }
    }

    @Test fun aDoubleTapActsOnce() {
        val once = ActOnce()
        var n = 0
        once.run { n++ }
        once.run { n++ }
        assertEquals(1, n)
    }

    @Test fun reopeningTheCardRearmsIt() {
        val once = ActOnce()
        var n = 0
        once.run { n++ }
        once.rearm()
        once.run { n++ }
        once.run { n++ }
        assertEquals(2, n)
    }
}
