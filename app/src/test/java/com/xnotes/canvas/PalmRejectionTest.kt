package com.xnotes.canvas

import android.view.MotionEvent
import com.xnotes.canvas.FingerDecision.HOLD
import com.xnotes.canvas.FingerDecision.IGNORE
import com.xnotes.canvas.FingerDecision.PAN
import com.xnotes.canvas.FingerDecision.TOOL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Palm rejection and the zoom-lock pan rule: what a finger's touch-down is allowed to do. */
class PalmRejectionTest {

    private val finger = MotionEvent.TOOL_TYPE_FINGER
    private val stylus = MotionEvent.TOOL_TYPE_STYLUS
    private val eraser = MotionEvent.TOOL_TYPE_ERASER
    private val mouse = MotionEvent.TOOL_TYPE_MOUSE
    private val palm = PalmRejection.TOOL_TYPE_PALM

    /** A fingertip's contact, well under the palm size. */
    private val tipDp = 50.0

    private fun decide(
        toolType: Int = finger,
        touchMajorDp: Double = tipDp,
        systemPalm: Boolean = false,
        nowMs: Long = 100_000L,
        pen: StylusProximity = StylusProximity(),
        zoomLocked: Boolean = false,
        zoomLockPan: String = "single",
        lockedTwoFinger: Boolean = true,
        fingerDraws: Boolean = false,
        pansWhenOff: Boolean = true,
    ): FingerDecision = PalmRejection.fingerDecision(
        toolType, touchMajorDp, systemPalm, nowMs, pen, zoomLocked, zoomLockPan, lockedTwoFinger, fingerDraws, pansWhenOff,
    )

    // --- the plain finger, pen far away ---

    @Test fun aFingerPansWhenFingersDoNotDraw() {
        assertEquals(PAN, decide())
    }

    @Test fun aFingerUsesTheToolWhenFingersDraw() {
        assertEquals(TOOL, decide(fingerDraws = true))
    }

    @Test fun aFingerUsesATextLikeToolEvenWithFingerDrawOff() {
        assertEquals(TOOL, decide(pansWhenOff = false))
    }

    @Test fun thePenAndOtherPointersAlwaysUseTheTool() {
        val near = StylusProximity().apply { onHover(MotionEvent.ACTION_HOVER_ENTER, stylus, 100_000L) }
        for (t in listOf(stylus, eraser, mouse, MotionEvent.TOOL_TYPE_UNKNOWN)) {
            // Size, pen proximity and the lock are a finger's business only.
            assertEquals(TOOL, decide(toolType = t, touchMajorDp = 500.0, pen = near, zoomLocked = true))
        }
    }

    // --- (b) palm-sized contacts ---

    @Test fun aContactAtThePalmThresholdStillCounts() {
        assertEquals(PAN, decide(touchMajorDp = PalmRejection.PALM_TOUCH_MAJOR_DP))
    }

    @Test fun aContactJustOverThePalmThresholdIsIgnored() {
        assertEquals(IGNORE, decide(touchMajorDp = PalmRejection.PALM_TOUCH_MAJOR_DP + 0.1))
        assertEquals(IGNORE, decide(touchMajorDp = 400.0, fingerDraws = true))
    }

    @Test fun aDriverThatReportsNoSizeNeverRejects() {
        assertEquals(PAN, decide(touchMajorDp = 0.0))
    }

    @Test fun thePalmThresholdSitsBetweenAFlatThumbAndAPalm() {
        // ~16 mm flat thumb and ~25 mm palm, at the 160 dp-per-inch reference density.
        val dpPerMm = 160.0 / 25.4
        assertTrue(PalmRejection.PALM_TOUCH_MAJOR_DP > 15.0 * dpPerMm)
        assertTrue(PalmRejection.PALM_TOUCH_MAJOR_DP < 25.0 * dpPerMm)
    }

    @Test fun aPalmToolTypeIsIgnored() {
        assertEquals(IGNORE, decide(toolType = palm))
        assertEquals(IGNORE, decide(toolType = palm, fingerDraws = true))
    }

    @Test fun aContactTheSystemCancelledIsIgnored() {
        assertEquals(IGNORE, decide(systemPalm = true))
        assertEquals(IGNORE, decide(systemPalm = true, fingerDraws = true, pansWhenOff = false))
    }

    @Test fun theCanceledFlagCountsOnlyFromApi33() {
        val flag = PalmRejection.FLAG_CANCELED
        assertTrue(PalmRejection.systemCanceled(flag, 33))
        assertTrue(PalmRejection.systemCanceled(flag or 0x1, 36))
        assertFalse(PalmRejection.systemCanceled(flag, 32))
        assertFalse(PalmRejection.systemCanceled(0, 36))
        assertFalse(PalmRejection.systemCanceled(0x1, 36))
    }

    // --- (a) the pen nearby ---

    @Test fun aFingerIsIgnoredWhileThePenHovers() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_ENTER, stylus, 1_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 1_200L))
        pen.onHover(MotionEvent.ACTION_HOVER_MOVE, stylus, 4_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 4_400L))
        // Finger draw on: a palm would draw, which is worse, so it is ignored all the same.
        assertEquals(IGNORE, decide(pen = pen, nowMs = 4_400L, fingerDraws = true))
    }

    @Test fun theEraserEndCountsAsThePen() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_ENTER, eraser, 1_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 1_100L))
    }

    @Test fun aFingerHoverIsNotThePen() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_ENTER, finger, 1_000L)
        pen.onHover(MotionEvent.ACTION_HOVER_MOVE, mouse, 1_000L)
        assertFalse(pen.isNear(1_000L))
        assertEquals(PAN, decide(pen = pen, nowMs = 1_000L))
    }

    @Test fun thePenStaysNearForHalfASecondAfterItLeavesHover() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_ENTER, stylus, 1_000L)
        pen.onHover(MotionEvent.ACTION_HOVER_EXIT, stylus, 2_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 2_000L + PalmRejection.PEN_RECENT_MS))
        assertEquals(PAN, decide(pen = pen, nowMs = 2_000L + PalmRejection.PEN_RECENT_MS + 1))
    }

    @Test fun thePenIsNearWhileItTouchesAndForHalfASecondAfterItLifts() {
        val pen = StylusProximity()
        pen.onPenDown(5_000L)
        assertTrue(pen.down)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 9_000L))
        pen.onPenUp(9_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 9_500L))
        assertEquals(PAN, decide(pen = pen, nowMs = 9_501L))
    }

    @Test fun aTouchDownEndsHoverUntilThePenHoversAgain() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_MOVE, stylus, 1_000L)
        pen.onPenDown(1_100L)
        pen.onPenUp(1_200L)
        assertFalse(pen.hovering)
        assertEquals(PAN, decide(pen = pen, nowMs = 1_701L))
    }

    @Test fun aHoverThatNeverSaidGoodbyeGoesStale() {
        // A lost HOVER_EXIT must not lock fingers out for good.
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_HOVER_MOVE, stylus, 1_000L)
        assertEquals(IGNORE, decide(pen = pen, nowMs = 1_000L + PalmRejection.PEN_STALE_MS))
        assertEquals(PAN, decide(pen = pen, nowMs = 1_000L + PalmRejection.PEN_STALE_MS + 1))
    }

    @Test fun aPenThatNeverAppearedIsFar() {
        val pen = StylusProximity()
        assertFalse(pen.isNear(0L))
        assertFalse(pen.isNear(Long.MAX_VALUE))
        assertEquals(PAN, decide(pen = pen, nowMs = 0L))
    }

    @Test fun resetForgetsThePen() {
        val pen = StylusProximity()
        pen.onPenDown(1_000L)
        pen.reset()
        assertFalse(pen.isNear(1_000L))
    }

    @Test fun theHoverStreamsOtherActionsChangeNothing() {
        val pen = StylusProximity()
        pen.onHover(MotionEvent.ACTION_BUTTON_PRESS, stylus, 1_000L)
        assertFalse(pen.isNear(1_000L))
    }

    // --- a second pointer joining a finger gesture ---

    @Test fun aJoiningPalmOrAFingerWhileThePenTouchesIsIgnored() {
        val far = StylusProximity()
        assertFalse(PalmRejection.joiningPointerIgnored(finger, tipDp, false, far))
        assertTrue(PalmRejection.joiningPointerIgnored(finger, 200.0, false, far))
        assertTrue(PalmRejection.joiningPointerIgnored(palm, tipDp, false, far))
        assertTrue(PalmRejection.joiningPointerIgnored(finger, tipDp, true, far))
        val touching = StylusProximity().apply { onPenDown(1_000L) }
        assertTrue(PalmRejection.joiningPointerIgnored(finger, tipDp, false, touching))
        // The pen itself joining is the controllers' business, as before.
        assertFalse(PalmRejection.joiningPointerIgnored(stylus, 200.0, false, touching))
    }

    @Test fun aSecondFingertipPinchesWhileThePenOnlyHovers() {
        // I4: write, then pinch; or hold the pen hovering and two-finger scroll with the other hand.
        val hovering = StylusProximity().apply { onHover(MotionEvent.ACTION_HOVER_ENTER, stylus, 1_000L) }
        assertFalse(PalmRejection.joiningPointerIgnored(finger, tipDp, false, hovering))
        val justLifted = StylusProximity().apply { onPenDown(1_000L); onPenUp(1_200L) }
        assertFalse(PalmRejection.joiningPointerIgnored(finger, tipDp, false, justLifted))
    }

    // --- a pointer joining a palm gesture (the whole gesture was ignored at its touch-down) ---

    private fun palmJoin(
        joinToolType: Int = finger,
        joinMajorDp: Double = tipDp,
        otherToolType: Int = finger,
        otherMajorDp: Double = tipDp,
        pointerCount: Int = 2,
        systemPalm: Boolean = false,
        firstWasFingertip: Boolean = true,
        pen: StylusProximity = StylusProximity().apply { onHover(MotionEvent.ACTION_HOVER_ENTER, stylus, 1_000L) },
    ): PalmJoin = PalmRejection.palmJoin(
        joinToolType, joinMajorDp, otherToolType, otherMajorDp, pointerCount, systemPalm, firstWasFingertip, pen,
    )

    @Test fun thePenLandingWhileARejectedPalmRestsWrites() {
        // C1: palm DOWN is ignored; the pen's POINTER_DOWN starts a stroke exactly as a fresh pen down.
        val pen = StylusProximity().apply { onHover(MotionEvent.ACTION_HOVER_MOVE, stylus, 1_000L) }
        assertEquals(IGNORE, decide(touchMajorDp = 300.0, pen = pen, nowMs = 1_100L))
        pen.onPenDown(1_150L)
        assertEquals(PalmJoin.PEN_WRITES, palmJoin(joinToolType = stylus, otherMajorDp = 300.0, firstWasFingertip = false, pen = pen))
        // ...and the pen's own decision is the armed tool, so the stroke starts (DRAW).
        assertEquals(TOOL, decide(toolType = stylus, nowMs = 1_150L, pen = pen))
    }

    @Test fun thePenWritesOverAnyPalmGesture() {
        val touching = StylusProximity().apply { onPenDown(1_000L) }
        for (other in listOf(finger, palm)) {
            for (count in 2..4) {
                assertEquals(
                    PalmJoin.PEN_WRITES,
                    palmJoin(joinToolType = stylus, otherToolType = other, otherMajorDp = 400.0, pointerCount = count, firstWasFingertip = false, pen = touching),
                )
            }
        }
        assertEquals(PalmJoin.PEN_WRITES, palmJoin(joinToolType = eraser, pen = touching))
    }

    @Test fun aSecondFingertipRevivesAFingertipIgnoredForThePen() {
        assertEquals(PalmJoin.PINCH, palmJoin())
        val justLifted = StylusProximity().apply { onPenDown(1_000L); onPenUp(1_200L) }
        assertEquals(PalmJoin.PINCH, palmJoin(pen = justLifted))
        assertEquals(PalmJoin.PINCH, palmJoin(joinMajorDp = PalmRejection.PALM_TOUCH_MAJOR_DP, otherMajorDp = 0.0))
    }

    @Test fun noPinchWhileThePenTouches() {
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(pen = StylusProximity().apply { onPenDown(1_000L) }))
    }

    @Test fun noPinchWhenEitherContactIsAPalm() {
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(joinMajorDp = 200.0))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(otherMajorDp = 200.0))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(joinToolType = palm))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(otherToolType = palm))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(systemPalm = true))
    }

    @Test fun noPinchWhenTheFirstContactWasIgnoredForItsSize() {
        // A palm that shrank as it lifted is still the palm.
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(firstWasFingertip = false))
    }

    @Test fun noPinchFromAThirdContactOrANonFinger() {
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(pointerCount = 3))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(joinToolType = mouse))
        assertEquals(PalmJoin.STAY_IGNORED, palmJoin(otherToolType = mouse))
    }

    @Test fun aFingertipIsAFingerUnderThePalmSize() {
        assertTrue(PalmRejection.isFingertip(finger, tipDp, false))
        assertTrue(PalmRejection.isFingertip(finger, 0.0, false))
        assertFalse(PalmRejection.isFingertip(finger, 200.0, false))
        assertFalse(PalmRejection.isFingertip(finger, tipDp, true))
        assertFalse(PalmRejection.isFingertip(palm, tipDp, false))
        assertFalse(PalmRejection.isFingertip(stylus, tipDp, false))
    }

    // --- the pen lifting out of a gesture it leads ---

    @Test fun thePenLiftingWhileAPalmStaysEndsTheStroke() {
        assertTrue(PalmRejection.penLiftEndsGesture(penLeads = true, liftedIsTracked = true, gestureLive = true))
        // The palm lifting leaves the pen writing.
        assertFalse(PalmRejection.penLiftEndsGesture(penLeads = true, liftedIsTracked = false, gestureLive = true))
        // A finger-led gesture (a pinch, a pan) handles its own lifts.
        assertFalse(PalmRejection.penLiftEndsGesture(penLeads = false, liftedIsTracked = true, gestureLive = true))
        assertFalse(PalmRejection.penLiftEndsGesture(penLeads = true, liftedIsTracked = true, gestureLive = false))
    }

    // --- a cancelled finger pan ---

    @Test fun onlyAPalmCancelPutsAFingerPanBack() {
        assertTrue(PalmRejection.cancelRewindsPan(systemCanceled = true, toolType = finger))
        assertTrue(PalmRejection.cancelRewindsPan(systemCanceled = false, toolType = palm))
        // A dialog taking focus, a system gesture, a parent intercepting: the scroll stays where it is.
        assertFalse(PalmRejection.cancelRewindsPan(systemCanceled = false, toolType = finger))
    }

    // --- the editor's chrome: a palm is not a touch ---

    @Test fun aPalmDownIsNotACanvasTouch() {
        val down = MotionEvent.ACTION_DOWN
        assertTrue(PalmRejection.palmDown(down, finger, 200.0, false))
        assertTrue(PalmRejection.palmDown(down, palm, tipDp, false))
        assertTrue(PalmRejection.palmDown(down, finger, tipDp, true))
        assertFalse(PalmRejection.palmDown(down, finger, tipDp, false))
        // The pen is always a touch, whatever it reports.
        assertFalse(PalmRejection.palmDown(down, stylus, 400.0, true))
        assertFalse(PalmRejection.palmDown(down, eraser, 400.0, true))
        // Only the touch-down asks.
        assertFalse(PalmRejection.palmDown(MotionEvent.ACTION_MOVE, finger, 200.0, false))
        assertFalse(PalmRejection.palmDown(MotionEvent.ACTION_POINTER_DOWN, finger, 200.0, false))
    }

    // --- a touch stream that starts without the pen ---

    @Test fun aTouchThatStartsWithoutThePenClearsAStuckPenDown() {
        val pen = StylusProximity()
        pen.onPenDown(1_000L) // its UP never arrived
        pen.onTouchStartedWithoutPen()
        assertFalse(pen.down)
        // Only the stuck touch is forgotten: the pen was still seen recently.
        assertEquals(1_000L, pen.lastStylusSeenAtMs)
        assertTrue(pen.isNear(1_000L + PalmRejection.PEN_RECENT_MS))
        assertFalse(pen.isNear(1_000L + PalmRejection.PEN_RECENT_MS + 1))
    }

    // --- the zoom-lock pan rule ---

    @Test fun anUnlockedViewAlwaysPans() {
        for (mode in listOf("single", "double", "none")) {
            for (two in listOf(true, false)) {
                assertEquals(PAN, decide(zoomLocked = false, zoomLockPan = mode, lockedTwoFinger = two))
                assertTrue(PalmRejection.lockedPanMoves(false, mode, two, byFinger = true))
                assertTrue(PalmRejection.lockedPanMoves(false, mode, two, byFinger = false))
            }
        }
    }

    @Test fun aLockedViewTakesTwoFingersToScrollByDefault() {
        assertEquals(HOLD, decide(zoomLocked = true))
        assertFalse(PalmRejection.lockedPanMoves(true, "single", lockedTwoFinger = true, byFinger = true))
    }

    @Test fun turningTheTwoFingerRuleOffLetsOneFingerScrollALockedView() {
        assertEquals(PAN, decide(zoomLocked = true, lockedTwoFinger = false))
    }

    @Test fun theOlderZoomLockPanChoicesStillHold() {
        assertEquals(HOLD, decide(zoomLocked = true, zoomLockPan = "double", lockedTwoFinger = false))
        assertEquals(HOLD, decide(zoomLocked = true, zoomLockPan = "none", lockedTwoFinger = false))
    }

    @Test fun theTwoFingerRuleIsAboutFingersNotThePenButton() {
        // A pen-button (or pan-tool) pan is no palm: only the older preference governs it.
        assertTrue(PalmRejection.lockedPanMoves(true, "single", lockedTwoFinger = true, byFinger = false))
        assertFalse(PalmRejection.lockedPanMoves(true, "double", lockedTwoFinger = true, byFinger = false))
        assertFalse(PalmRejection.lockedPanMoves(true, "none", lockedTwoFinger = false, byFinger = false))
    }

    @Test fun aFingerThatDrawsIsNotHeldByTheLock() {
        assertEquals(TOOL, decide(zoomLocked = true, fingerDraws = true))
    }

    @Test fun palmRejectionComesBeforeTheLock() {
        assertEquals(IGNORE, decide(zoomLocked = true, touchMajorDp = 300.0))
    }
}
