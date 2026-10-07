package com.xnotes.canvas

import android.view.MotionEvent

/**
 * What a finger's touch-down may do, decided once on ACTION_DOWN by [PalmRejection.fingerDecision].
 * Both canvases ask the same question, so a resting hand behaves the same on either surface.
 */
enum class FingerDecision {
    /** A palm, or a hand resting while the pen writes: the whole gesture is dropped. No pan, tap or long press. */
    IGNORE,

    /** The finger pans the page (finger draw is off). */
    PAN,

    /**
     * A pan that holds the page still: zoom is locked and only two fingers scroll. It is still a
     * pan in every other way, so a tap and a long press work and a second finger pinch-pans.
     */
    HOLD,

    /** The finger drives the armed tool: finger draw is on, or the tool is one fingers use (text). */
    TOOL,
}

/**
 * What a pointer joining a palm gesture (one ignored from its touch-down) does, decided on its
 * ACTION_POINTER_DOWN by [PalmRejection.palmJoin]. The palm itself stays ignored either way.
 */
enum class PalmJoin {
    /** Nothing: the gesture stays a palm. */
    STAY_IGNORED,

    /** The pen landed beside the resting palm: it starts its stroke exactly as a fresh pen down would. */
    PEN_WRITES,

    /** A second fingertip beside a fingertip ignored only for the pen being near: a pinch (or two-finger scroll). */
    PINCH,
}

/**
 * Where the S Pen is, from the hover stream and the touch stream: hovering, touching, or seen a
 * moment ago. Fed only on hover events and on pen down/up, never on a stroke's moves.
 *
 * Times are `MotionEvent.getEventTime()` (the uptime clock), the same clock for both streams.
 */
class StylusProximity {
    /** The pen is over the screen, between HOVER_ENTER/MOVE and HOVER_EXIT. */
    var hovering: Boolean = false
        private set

    /** The pen is on the screen. */
    var down: Boolean = false
        private set

    /** When the pen was last hovering, touching or lifting; [NEVER] until it shows up. */
    var lastStylusSeenAtMs: Long = NEVER
        private set

    /** Feed a hover event: [action] is `actionMasked`, [toolType] pointer 0's tool type. */
    fun onHover(action: Int, toolType: Int, timeMs: Long) {
        if (!isPen(toolType)) return
        when (action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering = true
                lastStylusSeenAtMs = timeMs
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hovering = false
                lastStylusSeenAtMs = timeMs
            }
        }
    }

    /** The pen touched down. Touch replaces hover; the pen hovers again (ENTER) once it lifts. */
    fun onPenDown(timeMs: Long) {
        down = true
        hovering = false
        lastStylusSeenAtMs = timeMs
    }

    /** The pen lifted, or its gesture was cancelled. */
    fun onPenUp(timeMs: Long) {
        down = false
        lastStylusSeenAtMs = timeMs
    }

    /**
     * A touch stream began (ACTION_DOWN) with something other than the pen, so the pen is not on
     * the screen: a pen-up that never arrived is forgotten. When the pen was last seen is kept, so
     * the half-second after it still counts as near.
     */
    fun onTouchStartedWithoutPen() {
        down = false
    }

    fun reset() {
        hovering = false
        down = false
        lastStylusSeenAtMs = NEVER
    }

    /**
     * Whether a finger landing at [nowMs] is most likely the writing hand: the pen hovers or
     * touches, or did so within [PalmRejection.PEN_RECENT_MS]. A hover or touch older than
     * [PalmRejection.PEN_STALE_MS] with no event since is treated as lost (a HOVER_EXIT the view never
     * got), so a missed event cannot lock fingers out for good.
     */
    fun isNear(nowMs: Long): Boolean {
        if (lastStylusSeenAtMs == NEVER) return false
        val age = nowMs - lastStylusSeenAtMs
        if ((hovering || down) && age <= PalmRejection.PEN_STALE_MS) return true
        return age <= PalmRejection.PEN_RECENT_MS
    }

    companion object {
        const val NEVER = Long.MIN_VALUE

        fun isPen(toolType: Int): Boolean =
            toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }
}

/**
 * Palm rejection, always on: a finger contact that is most likely the writing hand is ignored as a
 * whole gesture. And the zoom-lock pan rule: while zoom is locked, a single finger holds the page
 * and only two fingers scroll (Settings, "Two fingers to scroll when zoom is locked", on by default).
 *
 * Pure decisions, so every rule and boundary is unit-tested on the JVM. The controllers ask them
 * once per touch-down, per joining pointer and per cancel; nothing here runs on a stroke's moves.
 */
object PalmRejection {
    /** A finger within this long (ms) of the pen hovering, touching or lifting is the writing hand. */
    const val PEN_RECENT_MS = 500L

    /** A hover or touch with no event for this long (ms) is taken as lost; see [StylusProximity.isNear]. */
    const val PEN_STALE_MS = 10_000L

    /**
     * A finger contact whose major axis is wider than this (dp) is a palm. A fingertip is about
     * 8–12 mm across and a flat thumb about 15–18 mm; a resting palm or the side of the hand is
     * over 25 mm. At the 160 dp-per-inch reference 100 dp is 15.9 mm; on a Galaxy Tab S8 (density
     * 2.0, 274 ppi) it is ~18.5 mm, so 100 dp sits just above a flat thumb and well under a palm.
     * A driver that reports no size (0) never trips it.
     */
    const val PALM_TOUCH_MAJOR_DP = 100.0

    /** `MotionEvent.TOOL_TYPE_PALM` (API 33): the system classified the contact as a palm. */
    const val TOOL_TYPE_PALM = 5

    /** `MotionEvent.FLAG_CANCELED` (API 33): the pointer was cancelled, most often as a palm. */
    const val FLAG_CANCELED = 0x20

    /** Whether an event's [flags] say the system cancelled it; the flag means nothing before API 33. */
    fun systemCanceled(flags: Int, sdkInt: Int): Boolean = sdkInt >= 33 && (flags and FLAG_CANCELED) != 0

    /** A contact that is a palm by its own account: the palm tool type, a cancelled flag, or a palm's size. */
    fun isPalmContact(toolType: Int, touchMajorDp: Double, systemPalm: Boolean): Boolean =
        toolType == TOOL_TYPE_PALM || systemPalm ||
            (toolType == MotionEvent.TOOL_TYPE_FINGER && touchMajorDp > PALM_TOUCH_MAJOR_DP)

    /**
     * Whether a single-pointer pan moves the view. An unlocked view always pans. Locked, the older
     * "Scrolling while zoom is locked" choice ([zoomLockPan] "single" | "double" | "none") applies to
     * every pan, and [lockedTwoFinger] additionally holds a finger's ([byFinger]) single-pointer pan
     * still. A pen-button or pan-tool pan is no palm, so the two-finger rule leaves it alone.
     */
    fun lockedPanMoves(zoomLocked: Boolean, zoomLockPan: String, lockedTwoFinger: Boolean, byFinger: Boolean): Boolean =
        !zoomLocked || (zoomLockPan == "single" && !(byFinger && lockedTwoFinger))

    /**
     * What a touch-down with tool [toolType] does. Anything but a finger (or a palm) keeps the armed
     * tool, as before. A finger is ignored when it is palm-sized or flagged a palm, or when the pen
     * is near ([pen] at [nowMs]); that holds with finger draw on too, since a palm would draw.
     * Otherwise it uses the tool when [fingerDraws] is on or the tool is not one a finger pans over
     * ([pansWhenOff]), and pans, or holds the locked page still, when it is.
     */
    fun fingerDecision(
        toolType: Int,
        touchMajorDp: Double,
        systemPalm: Boolean,
        nowMs: Long,
        pen: StylusProximity,
        zoomLocked: Boolean,
        zoomLockPan: String,
        lockedTwoFinger: Boolean,
        fingerDraws: Boolean,
        pansWhenOff: Boolean,
    ): FingerDecision {
        if (toolType != MotionEvent.TOOL_TYPE_FINGER && toolType != TOOL_TYPE_PALM) return FingerDecision.TOOL
        if (isPalmContact(toolType, touchMajorDp, systemPalm)) return FingerDecision.IGNORE
        if (pen.isNear(nowMs)) return FingerDecision.IGNORE
        if (fingerDraws || !pansWhenOff) return FingerDecision.TOOL
        return if (lockedPanMoves(zoomLocked, zoomLockPan, lockedTwoFinger, byFinger = true)) FingerDecision.PAN else FingerDecision.HOLD
    }

    /**
     * Whether a pointer joining a live gesture (ACTION_POINTER_DOWN) is left out of it: a palm, or
     * a finger landing while the pen touches. A fingertip joining while the pen only hovers (or
     * lifted a moment ago) is the other hand pinching or two-finger scrolling, so it joins. A pen
     * joining is left to the controllers.
     */
    fun joiningPointerIgnored(toolType: Int, touchMajorDp: Double, systemPalm: Boolean, pen: StylusProximity): Boolean {
        if (toolType != MotionEvent.TOOL_TYPE_FINGER && toolType != TOOL_TYPE_PALM) return false
        return isPalmContact(toolType, touchMajorDp, systemPalm) || pen.down
    }

    /** A finger contact that is not a palm: under the palm size, not the palm tool type, not flagged. */
    fun isFingertip(toolType: Int, touchMajorDp: Double, systemPalm: Boolean): Boolean =
        toolType == MotionEvent.TOOL_TYPE_FINGER && !isPalmContact(toolType, touchMajorDp, systemPalm)

    /**
     * What a pointer joining a palm gesture does. The pen ([joinToolType] stylus or eraser) always
     * writes: the resting palm stays ignored and the stroke starts from the pen's pointer. A finger
     * revives the gesture as a pinch only when it makes exactly two contacts with the first, both
     * are fingertips now, the first was ignored at its touch-down only for the pen being near
     * ([firstWasFingertip]: not for its size or a flag), and the pen is not touching.
     */
    fun palmJoin(
        joinToolType: Int,
        joinMajorDp: Double,
        otherToolType: Int,
        otherMajorDp: Double,
        pointerCount: Int,
        systemPalm: Boolean,
        firstWasFingertip: Boolean,
        pen: StylusProximity,
    ): PalmJoin {
        if (StylusProximity.isPen(joinToolType)) return PalmJoin.PEN_WRITES
        val pinch = pointerCount == 2 && firstWasFingertip && !pen.down &&
            isFingertip(joinToolType, joinMajorDp, systemPalm) && isFingertip(otherToolType, otherMajorDp, systemPalm)
        return if (pinch) PalmJoin.PINCH else PalmJoin.STAY_IGNORED
    }

    /**
     * Whether a pointer lifting ends the gesture while others stay down: the pen leads it ([penLeads]),
     * the lifting pointer is the one it follows ([liftedIsTracked]) and it is still doing something
     * ([gestureLive]: not idle, not a pinch). What stays down was left out of it, so it stays ignored.
     */
    fun penLiftEndsGesture(penLeads: Boolean, liftedIsTracked: Boolean, gestureLive: Boolean): Boolean =
        penLeads && liftedIsTracked && gestureLive

    /**
     * Whether a cancelled finger pan goes back to where it began: only when the system says the
     * finger was a palm ([systemCanceled], or the palm tool type). Any other cancel (a dialog taking
     * focus, a system gesture, a parent intercepting) leaves the scroll where it is.
     */
    fun cancelRewindsPan(systemCanceled: Boolean, toolType: Int): Boolean =
        systemCanceled || toolType == TOOL_TYPE_PALM

    /**
     * Whether a touch-down ([action] ACTION_DOWN) is a palm by its own account ([isPalmContact]),
     * for the editor's chrome: such a touch closes no menu and does not count as the pen being down.
     * The pen is never a palm.
     */
    fun palmDown(action: Int, toolType: Int, touchMajorDp: Double, systemPalm: Boolean): Boolean =
        action == MotionEvent.ACTION_DOWN && !StylusProximity.isPen(toolType) &&
            isPalmContact(toolType, touchMajorDp, systemPalm)

    // --- MotionEvent adapters, for the controllers (not unit-tested: they only read the event) ---

    /** The pointer at [index] of [e], as [fingerDecision] / [joiningPointerIgnored] want it. */
    fun touchMajorDp(e: MotionEvent, index: Int, pxPerDp: Double): Double =
        e.getTouchMajor(index) / pxPerDp.coerceAtLeast(1e-6)

    fun systemCanceled(e: MotionEvent): Boolean = systemCanceled(e.flags, android.os.Build.VERSION.SDK_INT)

    /**
     * [fingerDecision] for the touch-down of pointer [index]: pointer 0 of an ACTION_DOWN, or the
     * pen's pointer when it lands beside a palm. A pen's or mouse's down returns
     * [FingerDecision.TOOL] before reading anything else, so a stroke's first event costs one
     * tool-type read.
     */
    fun fingerDecision(
        e: MotionEvent,
        pxPerDp: Double,
        pen: StylusProximity,
        zoomLocked: Boolean,
        zoomLockPan: String,
        lockedTwoFinger: Boolean,
        fingerDraws: Boolean,
        pansWhenOff: Boolean,
        index: Int = 0,
    ): FingerDecision {
        val toolType = e.getToolType(index)
        if (toolType != MotionEvent.TOOL_TYPE_FINGER && toolType != TOOL_TYPE_PALM) return FingerDecision.TOOL
        return fingerDecision(
            toolType, touchMajorDp(e, index, pxPerDp), systemCanceled(e), e.eventTime, pen,
            zoomLocked, zoomLockPan, lockedTwoFinger, fingerDraws, pansWhenOff,
        )
    }

    /** [isPalmContact] for pointer [index] of [e]. */
    fun isPalmPointer(e: MotionEvent, index: Int, pxPerDp: Double): Boolean =
        isPalmContact(e.getToolType(index), touchMajorDp(e, index, pxPerDp), systemCanceled(e))

    /** [isFingertip] for pointer [index] of [e]; asked of an ignored touch-down only. */
    fun isFingertip(e: MotionEvent, index: Int, pxPerDp: Double): Boolean =
        isFingertip(e.getToolType(index), touchMajorDp(e, index, pxPerDp), systemCanceled(e))

    /** [joiningPointerIgnored] for an ACTION_POINTER_DOWN's new pointer. */
    fun joiningPointerIgnored(e: MotionEvent, pxPerDp: Double, pen: StylusProximity): Boolean {
        val i = e.actionIndex
        val toolType = e.getToolType(i)
        if (toolType != MotionEvent.TOOL_TYPE_FINGER && toolType != TOOL_TYPE_PALM) return false
        return joiningPointerIgnored(toolType, touchMajorDp(e, i, pxPerDp), systemCanceled(e), pen)
    }

    /**
     * [palmJoin] for an ACTION_POINTER_DOWN's new pointer. Asked only while the gesture is a palm,
     * so a live gesture's pointer-down pays nothing for it. The pen answers at its tool type.
     */
    fun palmJoin(e: MotionEvent, pxPerDp: Double, pen: StylusProximity, firstWasFingertip: Boolean): PalmJoin {
        val i = e.actionIndex
        val toolType = e.getToolType(i)
        if (StylusProximity.isPen(toolType)) return PalmJoin.PEN_WRITES
        val n = e.pointerCount
        if (n != 2 || !firstWasFingertip || pen.down) return PalmJoin.STAY_IGNORED
        val other = 1 - i
        return palmJoin(
            toolType, touchMajorDp(e, i, pxPerDp), e.getToolType(other), touchMajorDp(e, other, pxPerDp),
            n, systemCanceled(e), firstWasFingertip, pen,
        )
    }

    /** [cancelRewindsPan] for an ACTION_CANCEL, about the panning pointer at [index]. */
    fun cancelRewindsPan(e: MotionEvent, index: Int): Boolean =
        cancelRewindsPan(systemCanceled(e), e.getToolType(index.coerceIn(0, e.pointerCount - 1)))

    /**
     * [palmDown] for an event the editor sees: false at once for anything but an ACTION_DOWN, and
     * for the pen's after one tool-type read.
     */
    fun isPalmDown(e: MotionEvent, pxPerDp: Double): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return false
        val toolType = e.getToolType(0)
        if (StylusProximity.isPen(toolType)) return false
        return palmDown(MotionEvent.ACTION_DOWN, toolType, touchMajorDp(e, 0, pxPerDp), systemCanceled(e))
    }

    /** Feed a hover event to [pen]. */
    fun observeHover(pen: StylusProximity, e: MotionEvent) {
        pen.onHover(e.actionMasked, e.getToolType(0), e.eventTime)
    }

    /** Track the pen on a touch event; call on DOWN / POINTER_DOWN / UP / POINTER_UP / CANCEL only. */
    fun observePen(pen: StylusProximity, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                if (StylusProximity.isPen(e.getToolType(0))) pen.onPenDown(e.eventTime)
                else if (pen.down) pen.onTouchStartedWithoutPen()
            MotionEvent.ACTION_POINTER_DOWN ->
                if (StylusProximity.isPen(e.getToolType(e.actionIndex))) pen.onPenDown(e.eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                if (StylusProximity.isPen(e.getToolType(e.actionIndex))) pen.onPenUp(e.eventTime)
            MotionEvent.ACTION_CANCEL -> if (pen.down) pen.onPenUp(e.eventTime)
        }
    }
}
