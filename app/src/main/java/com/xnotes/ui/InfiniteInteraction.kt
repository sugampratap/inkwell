package com.xnotes.ui

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import com.xnotes.canvas.FingerDecision
import com.xnotes.canvas.InteractionController
import com.xnotes.canvas.PalmJoin
import com.xnotes.canvas.PalmRejection
import com.xnotes.canvas.StylusButtonLatch
import com.xnotes.canvas.StylusProximity
import com.xnotes.core.geometry.Pt
import com.xnotes.core.infinite.CanvasViewport
import com.xnotes.canvas.HandleId
import com.xnotes.canvas.ResizeMath
import com.xnotes.canvas.SelectionMath
import com.xnotes.core.geometry.Rect
import com.xnotes.core.infinite.CanvasSelection
import com.xnotes.core.infinite.EraseSession
import com.xnotes.core.infinite.LiftTransform
import com.xnotes.core.infinite.OverlayTessellator
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.model.TapeItem
import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.ShapeRecognizer
import com.xnotes.core.stroke.StrokeSimplify
import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.TapeConfig
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max

/** What the current gesture is doing. */
enum class CanvasPointerMode { IDLE, PAN, PINCH, DRAW, ERASE, SHAPE, BAND, LASSO, MOVE, RESIZE, ROTATE, TAPE }

/**
 * Gestures on the infinite canvas.
 *
 * The navigation half is what an infinite canvas needs before anything else, and it is
 * deliberately the paged canvas's feel rather than a second one: the velocity smoothing, fling
 * friction and start/stop thresholds are read from [InteractionController]'s constants rather than
 * copied, so tuning either canvas tunes both.
 *
 * Everything a pinch changes is two numbers on [CanvasViewport], and the renderer takes them as
 * shader uniforms. There is no cache to invalidate, no settle debounce, and no zoom clamp against
 * a page: zoom runs the full configured range and scroll runs forever in all four directions.
 */
class InfiniteInteraction(
    private val viewport: CanvasViewport,
    private val requestRender: () -> Unit,
    /** Called whenever the view moved, so the host can refresh a zoom readout or schedule a save. */
    private val onViewChanged: () -> Unit = {},
    /** True while a gesture or a glide is live, so the renderer can keep drawing every refresh. */
    private val setInteractive: (Boolean, Boolean) -> Unit = { _, _ -> },
    /** The style the armed tool draws with, including the toolbar's active ink colour. */
    private val configFor: (Tool) -> ToolConfig = { ToolConfig() },
    /** The wet stroke changed: re-tessellate it into the dynamic buffer, or clear it when null. */
    private val onWetStroke: (Stroke?) -> Unit = {},
    /** Pen up on a finished stroke: add it to the document and push the undo command. */
    private val onCommitStroke: (Stroke) -> Unit = {},
    /** Begin an eraser drag; the host owns the document and the undo stack. */
    private val onEraseBegin: () -> EraseSession? = { null },
    /** The eraser drag ended: push its single undo command. */
    private val onEraseEnd: (EraseSession) -> Unit = {},
    /** An eraser drag lifted, rather than being cut short by a pinch or a cancel. */
    private val onEraseLifted: () -> Unit = {},
    /** Where the eraser cursor sits in viewport pixels, and how wide, or null to hide it. */
    private val onEraserCursor: (Pt?, Double) -> Unit = { _, _ -> },
    /**
     * The shape or strip of tape being dragged out, re-tessellated as it grows, or null to clear the
     * preview.
     */
    private val onPendingShape: (CanvasItem?) -> Unit = {},
    /** A finished shape or strip of tape: add it to the document and push the undo command. */
    private val onCommitShape: (CanvasItem) -> Unit = {},
    /** The tape tool's style. */
    private val tapeConfig: () -> TapeConfig = { TapeConfig() },
    /** A strip of tape was tapped: peel it back, or stick it down again. */
    private val onToggleTape: (TapeItem) -> Unit = {},
    /** The shape tool's style, and the active ink colour it draws in. */
    private val shapeConfig: () -> ShapeConfig = { ShapeConfig() },
    private val inkColor: () -> Rgba = { InkPalette.DEFAULT },
    /** Whether a held pen stroke may snap to a recognized shape. */
    private val detectShapes: () -> Boolean = { true },
    /** The selection, owned by the host so the chrome can read it. */
    private val selection: () -> CanvasSelection? = { null },
    /** Items the selection may be drawn from, culled to a rect. */
    private val itemsIn: (Rect) -> List<CanvasItem> = { emptyList() },
    /** The selection or its overlay changed: rebuild the chrome geometry. */
    private val onSelectionChanged: () -> Unit = {},
    /** A finished selection drag: push its single undo command. */
    private val onCommitSelection: (com.xnotes.core.history.Command?) -> Unit = {},
    /** Draw [items] displaced by the drag so far, rather than moving them. An empty list ends it. */
    private val onLiftSelection: (List<CanvasItem>, LiftTransform) -> Unit = { _, _ -> },
    /** Content pixels per dp, so the speed pen judges gesture speed independently of zoom. */
    private val devicePxPerDp: () -> Double = { 1.0 },
    /** A press that landed on the minimap; returns true when it was consumed as navigation. */
    private val onMinimapPress: (Double, Double) -> Boolean = { _, _ -> false },
    /**
     * A finger held still: open the paste menu at this viewport and content point, or offer to
     * release the locked item it landed on, which arrives as the third argument.
     */
    private val onContextMenu: (Pt, Pt, CanvasItem?) -> Unit = { _, _, _ -> },
    /** Whether copied items or a clipboard image are waiting, so empty canvas has a menu to offer. */
    private val hasPasteContent: () -> Boolean = { false },
    /** A tool this layer armed by itself, so the chrome can follow: a long-press grab and its end. */
    private val onToolChanged: (Tool) -> Unit = {},
) {

    private val choreographer = Choreographer.getInstance()

    companion object {
        /** How near a handle a press has to land to grab it, in device pixels. */
        const val HANDLE_TOUCH_PX = 22.0

        /** Least on-screen distance between lasso vertices, so a slow drag stays cheap. */
        const val LASSO_MIN_STEP_PX = 3.0

        /** How far a press may wander and still count as a tap, in device pixels. */
        const val TAP_SLOP_PX = 12.0

        /** How far (dp) a lasso tap reaches for an object, so a hairline is not a pixel hunt. */
        const val LASSO_TAP_REACH_DP = 10.0

        /** A lift this long (ms) after the last movement is a hand that stopped: no glide. */
        const val FLING_STALE_MS = 60L
    }

    /** The armed tool. */
    var tool: Tool = Tool.PEN

    /** Whether a finger draws, or pans instead. Mirrors the paged canvas's preference. */
    var fingerDraws: Boolean = false

    /** Tool the stylus side button arms while it is held; null leaves the button alone. */
    var penButtonTool: Tool? = Tool.ERASER

    /** Zoom lock: a pinch pans without changing the zoom, mirroring the paged canvas. */
    var zoomLocked: Boolean = false

    /** Disappearing ink is armed, so a held stroke must not become a committed shape. */
    var wandEnabled: Boolean = false

    /** Which pans still move a locked view: "single" (default) | "double" | "none". */
    var zoomLockPan: String = "single"

    /** While zoom is locked, one finger holds the view and only two scroll, mirroring the paged canvas. */
    var lockedTwoFingerScroll: Boolean = true

    /** Where the S Pen is, for palm rejection: fed by hover events and pen down/up, never by moves. */
    val pen = StylusProximity()

    /** This gesture is a palm (from its touch-down, or re-classified since): the rest of it is dropped. */
    private var palmGesture = false

    /**
     * The palm gesture is one fingertip ignored at its touch-down only for the pen being near, so a
     * second fingertip may still pinch ([PalmRejection.palmJoin]). False once anything else happened.
     */
    private var palmFirstFingertip = false

    /** The live gesture was started by the pen (stylus or eraser end); its lift ends it whatever stays down. */
    private var penLeads = false

    /** The scroll a finger pan began at, so a pan the system later calls a palm goes back; null otherwise. */
    private var fingerPanStart: Pt? = null

    /** The live pan holds the view still: zoom is locked and only two fingers scroll. */
    private var panHeld = false

    private val stylusButtons = StylusButtonLatch()

    private var eraseSession: EraseSession? = null

    // The shape being dragged out with the shape tool.
    private var pendingShape: ShapeItem? = null

    // The strip of tape being pulled out, where the press landed, and whether it has left a tap's
    // reach (until then a lift is a tap on the tape under it, not a new strip).
    private var pendingTape: TapeItem? = null
    private var tapeDownAt = Pt.ZERO
    private var tapePulling = false

    // Band and lasso, in content space, live only while their gesture runs.
    var bandRect: Rect? = null
        private set
    val lassoPoints = ArrayList<Pt>()
    private var bandAnchor = Pt.ZERO

    // The live transform drag.
    private var grabHandle: HandleId? = null
    private var moveAnchor = Pt.ZERO
    private var movedBy = Pt.ZERO

    // Where a handle or the rotate grip is being dragged to, and whether the renderer is doing the
    // work. Ink, shapes and placed SVGs map faithfully in the shader, since all three are triangles.
    // A photo or a text box is rebuilt by the model rather than mapped, so a selection holding one is
    // transformed the slow way and stays honest.
    private var transformPointer = Pt.ZERO
    private var liftedTransform = false

    // A finger press landed off a live selection and became a pan; a tap there dismisses it, a
    // real drag pans and leaves it alone.
    private var panMayDismiss = false
    private var panDownAt = Pt.ZERO

    // A finger held still grabs the item under it, or opens the paste menu on empty canvas.
    private var longPressRunnable: Runnable? = null
    private var longPressAt = Pt.ZERO
    private var longPressCandidate: CanvasItem? = null
    private var longPressLocked: CanvasItem? = null

    // The tool a long-press grab borrowed the canvas from, given back when the selection goes.
    private var longPressPrevTool: Tool? = null

    /** Whether a long-press grab is borrowing the selection tool, which it hands back by itself. */
    val grabbing: Boolean get() = longPressPrevTool != null

    // Hold-still-to-snap: a freehand stroke that stops moving becomes the shape it looks like.
    private val handler = Handler(Looper.getMainLooper())
    private var dwellRunnable: Runnable? = null
    private var dwellEligible = false
    private var dwellAnchor = Pt.ZERO

    var mode = CanvasPointerMode.IDLE
        private set

    // The stroke being drawn, live until the pen lifts.
    private var liveStroke: Stroke? = null
    private var drawingPointerId = -1
    private var drawingIsStylus = false

    /** Where the pen is about to be, installed by the host; null predicts nothing. */
    var predictor: com.xnotes.canvas.StrokePredictor? = null

    /**
     * The latest prediction for the live stroke, in content coordinates, and how many points of it
     * there are: valid for the [onWetStroke] call it was made for, and 0 for any other.
     */
    val predictedX = DoubleArray(InteractionController.PREDICTED_POINTS)
    val predictedY = DoubleArray(InteractionController.PREDICTED_POINTS)
    var predictedCount = 0
        private set
    private var strokeStartTimeMs = 0L

    // Pan and inertial fling, in viewport px and viewport px/s.
    private var lastPan = Pt.ZERO
    private var lastMoveMs = 0L
    private var panVel = Pt.ZERO
    private var panFromPenButton = false // a side-button pan parks where the pen lifted: no glide
    private var flinging = false
    private var flingVel = Pt.ZERO
    private var lastFlingMs = 0L
    private val flingFrame = Choreographer.FrameCallback { stepFling(it) }

    // Pinch.
    private var pinchInitDist = 1.0

    /** The two pointers the pinch is following, by id. */
    private var pinchIdA = -1
    private var pinchIdB = -1
    private var pinchInitZoom = 1.0
    private var pinchAnchorContent = Pt.ZERO

    fun onTouch(e: MotionEvent): Boolean {
        predictor?.record(e)
        predictedCount = 0
        // Palm rejection reads the pen's down/up and decides on touch-down, as on the paged canvas;
        // a move is left alone (a palm gesture's moves find the mode IDLE and do nothing).
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                PalmRejection.observePen(pen, e)
                handleDown(e)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                PalmRejection.observePen(pen, e)
                if (!palmGesture) {
                    handlePointerDown(e)
                } else {
                    when (PalmRejection.palmJoin(e, devicePxPerDp(), pen, palmFirstFingertip)) {
                        // The pen landed beside a resting palm: its stroke starts from its own
                        // pointer exactly as a fresh pen down; the palm stays out of it.
                        PalmJoin.PEN_WRITES -> handleDown(e, e.actionIndex)
                        // A second fingertip beside one ignored only for the pen: pinch / two-finger scroll.
                        PalmJoin.PINCH -> {
                            palmGesture = false
                            palmFirstFingertip = false
                            stopFling()
                            handlePointerDown(e)
                        }
                        PalmJoin.STAY_IGNORED -> Unit
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> handleMove(e)
            MotionEvent.ACTION_POINTER_UP -> {
                PalmRejection.observePen(pen, e)
                if (!palmGesture) handlePointerUp(e)
            }
            MotionEvent.ACTION_UP -> {
                PalmRejection.observePen(pen, e)
                when {
                    palmGesture -> palmGesture = false
                    // The system re-classified the panning finger as a palm: the pan never happened.
                    mode == CanvasPointerMode.PAN && fingerPanStart != null && PalmRejection.systemCanceled(e) -> rejectFingerPan()
                    else -> handleUp(e)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                PalmRejection.observePen(pen, e)
                if (palmGesture) {
                    palmGesture = false
                } else {
                    // A finger pan the system cancelled as a palm (caught late) goes back; any other
                    // cancel (a dialog taking focus, a system gesture) leaves the view where it is.
                    if (mode == CanvasPointerMode.PAN &&
                        PalmRejection.cancelRewindsPan(e, e.findPointerIndex(drawingPointerId))
                    ) {
                        restoreFingerPanStart()
                    }
                    abortGesture()
                }
            }
        }
        return true
    }

    /** A hover event, for palm rejection: where the pen is. */
    fun onPenHover(e: MotionEvent) = PalmRejection.observeHover(pen, e)

    /** Put the view back where the finger pan began; the pan is over either way. */
    private fun restoreFingerPanStart() {
        val start = fingerPanStart ?: return
        fingerPanStart = null
        viewport.scrollX = start.x
        viewport.scrollY = start.y
        onViewChanged()
    }

    /** The panning finger was a palm after all: undo its scroll and drop the gesture, with no tap or glide. */
    private fun rejectFingerPan() {
        restoreFingerPanStart()
        abortGesture()
    }

    /**
     * Latch a side button reported only on the hovering generic-motion stream, which is the only
     * place some pens put it.
     */
    fun onGenericMotion(e: MotionEvent) {
        stylusButtons.onGenericMotion(e)
    }

    /** Latch a side button delivered as a key event, which is all Bluetooth and USI pens send. */
    fun onStylusButtonKey(keyCode: Int, down: Boolean): Boolean = stylusButtons.onKey(keyCode, down)

    /** Drop any in-flight gesture and stop a glide, so a document swap cannot bleed into the next. */
    fun resetGestureState() {
        stopFling()
        cancelLongPress()
        longPressPrevTool = null
        mode = CanvasPointerMode.IDLE
        palmGesture = false
        palmFirstFingertip = false
        penLeads = false
        fingerPanStart = null
        panHeld = false
        panVel = Pt.ZERO
        liveStroke = null
        liftedTransform = false
        onWetStroke(null)
        onLiftSelection(emptyList(), LiftTransform.NONE)
        stylusButtons.reset()
    }

    // --- pointer handling ---

    /**
     * A gesture's first pointer touched down: pointer [index], 0 for an ACTION_DOWN, or the pen's
     * pointer when it lands beside a resting palm (then the palm stays out of the gesture, and the
     * moves, lift and pressure all follow [drawingPointerId]).
     */
    private fun handleDown(e: MotionEvent, index: Int = 0) {
        // Palm rejection before anything else: a palm, or a hand resting while the pen writes, must
        // not halt a glide, pan, tap or long-press. The pen's own down returns at once with TOOL.
        val finger = PalmRejection.fingerDecision(
            e, devicePxPerDp(), pen, zoomLocked, zoomLockPan, lockedTwoFingerScroll, fingerDraws, tool.fingerPansWhenOff,
            index,
        )
        palmGesture = finger == FingerDecision.IGNORE
        palmFirstFingertip = palmGesture && PalmRejection.isFingertip(e, index, devicePxPerDp())
        fingerPanStart = null
        if (palmGesture) {
            penLeads = false
            cancelLongPress()
            mode = CanvasPointerMode.IDLE
            return
        }
        stopFling() // a new touch halts any in-progress glide
        // The minimap sits over the canvas, so a press on it navigates rather than draws.
        if (onMinimapPress(e.getX(index).toDouble(), e.getY(index).toDouble())) {
            penLeads = false
            mode = CanvasPointerMode.IDLE
            return
        }
        drawingPointerId = e.getPointerId(index)
        drawingIsStylus = e.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS
        val vx = e.getX(index).toDouble()
        val vy = e.getY(index).toDouble()

        // Which tool this pointer actually drives: the pen's eraser end and its held side button
        // both override the armed tool, and a finger pans unless finger-draw is on. This mirrors
        // the paged canvas so a pen behaves the same on either surface ([finger] says which).
        val toolType = e.getToolType(index)
        penLeads = StylusProximity.isPen(toolType)
        val fingerPans = finger == FingerDecision.PAN || finger == FingerDecision.HOLD
        val buttonHeld = stylusButtons.heldFor(e, index)
        val onSelection = hitsSelection(viewport.viewportToContent(Pt(vx, vy)))
        val effective: Tool = when {
            toolType == MotionEvent.TOOL_TYPE_ERASER -> Tool.ERASER
            buttonHeld && penButtonTool != null -> penButtonTool!!
            // While something is selected, a press on it grabs it rather than inking through it,
            // and a finger may grab it even with finger-draw off. Both are the paged canvas's
            // rules; without them a selection could only be handled by a stylus.
            // A finger counts too, finger-draw on or off: a shape snapped from a held stroke is
            // selected so its handles can be dragged next, by whatever is touching.
            onSelection && (tool.isStroke || tool == Tool.SHAPE || tool == Tool.TAPE) -> Tool.SELECT
            fingerPans && onSelection -> Tool.SELECT
            fingerPans -> Tool.PAN
            else -> tool
        }
        // A press that lands off the selection with anything but the selection tools dismisses it,
        // which is what makes an empty tap put the chrome away.
        panMayDismiss = false
        if (!onSelection && effective != Tool.SELECT && effective != Tool.LASSO) {
            if (effective == Tool.PAN) panMayDismiss = hasSelection() else clearSelection()
        }

        armLongPress(Pt(vx, vy), onSelection, toolType == MotionEvent.TOOL_TYPE_FINGER)

        when {
            effective.isStroke -> beginDraw(vx, vy, effective, e, index)
            effective == Tool.ERASER -> beginErase(vx, vy)
            effective == Tool.SHAPE -> beginShape(vx, vy)
            effective == Tool.TAPE -> beginTape(vx, vy)
            effective == Tool.SELECT -> beginSelect(vx, vy)
            effective == Tool.LASSO -> beginLasso(vx, vy)
            else -> beginPan(
                vx, vy,
                fromPenButton = buttonHeld && penButtonTool == Tool.PAN,
                byFinger = toolType == MotionEvent.TOOL_TYPE_FINGER,
            )
        }
    }

    // --- long press: grab an item, or the paste menu on empty canvas ---

    /**
     * Arm what a finger held still does: pick up the item under it, or open the paste menu when
     * there is nothing there.
     *
     * Finger only, like the paged canvas: the stylus always draws, so resting it never grabs or pops
     * a menu. A press on the live selection is left alone, because that already means something.
     */
    private fun armLongPress(at: Pt, onSelection: Boolean, isFinger: Boolean) {
        cancelLongPress()
        if (!isFinger || onSelection) return
        val hit = itemAt(viewport.viewportToContent(at))
        // The eraser is the one tool a grab would fight with, so it is the one that leaves an item
        // alone. Everything else hands it over, which is what makes a held finger pick something up
        // without having to reach for the selection tool first.
        val grabbable = tool.isStroke || tool == Tool.PAN || tool == Tool.SELECT ||
            tool == Tool.LASSO || tool == Tool.SHAPE || tool == Tool.TAPE
        // A locked item cannot be picked up, so a held finger offers to release it instead. That is
        // the only way back: it is out of reach of the band, the lasso and every tap. The offer
        // stands whatever tool is armed, since no tool can do anything else with one.
        longPressLocked = hit?.takeIf { it.locked }
        if (hit != null && longPressLocked == null && !grabbable) return
        // Like the paged canvas, empty space always opens the context menu (it is not only Paste).
        longPressCandidate = hit?.takeUnless { it.locked }
        longPressAt = at
        val r = Runnable { triggerLongPress() }
        longPressRunnable = r
        handler.postDelayed(r, InteractionController.LONG_PRESS_MS)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        longPressCandidate = null
        longPressLocked = null
    }

    private fun triggerLongPress() {
        longPressRunnable = null
        val candidate = longPressCandidate
        longPressCandidate = null
        val locked = longPressLocked
        longPressLocked = null
        // The gesture underway is only ever a pan or a stroke that has not moved; drop it so what
        // follows is not fighting a drag, and stop the press from also dismissing the selection.
        if (mode == CanvasPointerMode.DRAW) abandonStroke()
        if (mode == CanvasPointerMode.SHAPE) abandonShape()
        if (mode == CanvasPointerMode.TAPE) abandonTape()
        mode = CanvasPointerMode.IDLE
        panMayDismiss = false
        if (candidate != null) {
            grabItem(candidate)
        } else {
            setInteractive(false, true)
            onContextMenu(longPressAt, viewport.viewportToContent(longPressAt), locked)
        }
        requestRender()
    }

    /**
     * Hand the held item to the selection tool and start moving it, so a long press picks something
     * up whatever tool is armed. The borrowed tool comes back when the selection is put away, and
     * the handles arrive with the chrome the moment the finger lifts.
     */
    private fun grabItem(item: CanvasItem) {
        val sel = selection() ?: return
        if (tool != Tool.SELECT) {
            longPressPrevTool = tool
            tool = Tool.SELECT
            onToolChanged(Tool.SELECT)
        }
        sel.select(listOf(item))
        setInteractive(false, false)
        beginMoveAt(sel, viewport.viewportToContent(longPressAt))
        onSelectionChanged()
    }

    private fun handlePointerDown(e: MotionEvent) {
        // A palm, or a finger landing while the pen is near, is left out of the gesture: no pinch,
        // and a long press already armed carries on.
        if (PalmRejection.joiningPointerIgnored(e, devicePxPerDp(), pen)) return
        cancelLongPress() // a second finger is a pinch, never a held press
        // A stylus stroke ignores an incidental palm or second finger; a finger stroke yields to a
        // pinch, since two fingers can only mean a zoom.
        if (mode == CanvasPointerMode.DRAW && drawingIsStylus) return
        if (mode == CanvasPointerMode.ERASE && drawingIsStylus) return
        if (e.pointerCount >= 2) {
            if (mode == CanvasPointerMode.DRAW) abandonStroke()
            // A finger erase yields to a pinch: commit what it already removed rather than lose it.
            if (mode == CanvasPointerMode.ERASE) endErase()
            beginPinch(e)
        }
    }

    private fun handleMove(e: MotionEvent) {
        // A finger that wanders is panning or drawing, not holding still for the menu.
        if (longPressRunnable != null) {
            val moved = Pt(e.getX(0).toDouble(), e.getY(0).toDouble()).distanceTo(longPressAt)
            if (moved > InteractionController.LONG_PRESS_SLOP) cancelLongPress()
        }
        // A stroke or an erase follows its pointer by id (extendDraw / extendErase). Every other
        // single-pointer gesture reads pointer 0, unless the pen leads it: the pen may have landed
        // beside a resting palm, so pointer 0 is the palm and the pen is wherever its id says.
        when (mode) {
            CanvasPointerMode.PINCH -> updatePinch(e)
            CanvasPointerMode.DRAW -> extendDraw(e)
            CanvasPointerMode.ERASE -> extendErase(e)
            CanvasPointerMode.IDLE -> Unit
            else -> {
                val i = leadIndex(e)
                val x = e.getX(i).toDouble()
                val y = e.getY(i).toDouble()
                when (mode) {
                    CanvasPointerMode.PAN -> extendPan(x, y, e.eventTime)
                    CanvasPointerMode.SHAPE -> extendShape(x, y)
                    CanvasPointerMode.TAPE -> extendTape(x, y)
                    CanvasPointerMode.BAND -> extendBand(x, y)
                    CanvasPointerMode.LASSO -> extendLasso(x, y)
                    CanvasPointerMode.MOVE -> extendMove(x, y)
                    CanvasPointerMode.RESIZE -> extendResize(x, y)
                    CanvasPointerMode.ROTATE -> extendRotate(x, y)
                    else -> Unit
                }
            }
        }
    }

    /** The pointer a single-pointer gesture follows: the pen's, by id, when the pen leads; else pointer 0, as ever. */
    private fun leadIndex(e: MotionEvent): Int =
        if (penLeads) e.findPointerIndex(drawingPointerId).coerceAtLeast(0) else 0

    private fun handlePointerUp(e: MotionEvent) {
        // The panning finger (pointer 0, which the pan follows) lifted while a pointer left out of the
        // gesture (a palm) stays down. A cancelled lift was the palm after all, so the pan goes back;
        // a real one ends the pan as a lift would. Either way the palm left behind does nothing.
        if (mode == CanvasPointerMode.PAN && fingerPanStart != null && e.actionIndex == 0) {
            if (PalmRejection.systemCanceled(e)) rejectFingerPan() else handleUp(e)
            palmGesture = true
            palmFirstFingertip = false
            return
        }
        // The pen lifted out of its own gesture while a palm (or a finger left out of it) stays
        // down: the stroke, erase or drag ends as a lift would, and what stays down stays ignored.
        if (PalmRejection.penLiftEndsGesture(
                penLeads,
                e.getPointerId(e.actionIndex) == drawingPointerId,
                mode != CanvasPointerMode.IDLE && mode != CanvasPointerMode.PINCH,
            )
        ) {
            handleUp(e)
            palmGesture = true
            palmFirstFingertip = false
            return
        }
        if (mode != CanvasPointerMode.PINCH) return
        // Dropping to one finger continues as a pan from wherever that finger is, so a pinch that
        // relaxes into a drag does not jump.
        if (e.pointerCount == 2) {
            val remaining = if (e.actionIndex == 0) 1 else 0
            beginPan(
                e.getX(remaining).toDouble(), e.getY(remaining).toDouble(),
                byFinger = e.getToolType(remaining) == MotionEvent.TOOL_TYPE_FINGER,
            )
            return
        }
        // Three or more fingers, one lifting: the pinch carries on with two of the rest. It is
        // re-anchored where they are now, so the view does not jump to the new pair's spread.
        val lifted = e.getPointerId(e.actionIndex)
        if (lifted == pinchIdA || lifted == pinchIdB) beginPinch(e, skipIndex = e.actionIndex)
    }

    private fun handleUp(e: MotionEvent) {
        cancelLongPress()
        val wasMoving = (mode == CanvasPointerMode.PAN && !panHeld && !panFromPenButton) ||
            (mode == CanvasPointerMode.PINCH && pinchPanAllowed())
        // A finger tap off the selection puts it away; a tap is the only way to say so with a
        // finger, since a drag there is a pan.
        val lead = leadIndex(e)
        if (mode == CanvasPointerMode.PAN && panWasTap(e.getX(lead).toDouble(), e.getY(lead).toDouble())) {
            // A tap that puts a selection away does only that; otherwise a tap on a strip of tape
            // peels it back or sticks it down, whatever the tool.
            if (panMayDismiss) clearSelection()
            else toggleTapeAt(viewport.viewportToContent(Pt(e.getX(lead).toDouble(), e.getY(lead).toDouble())))
        }
        panMayDismiss = false
        if (mode == CanvasPointerMode.DRAW) endDraw(e)
        if (mode == CanvasPointerMode.ERASE) {
            extendErase(e) // the lift carries the pen's last positions; a quick flick ends past the last move
            endErase()
            onEraseLifted()
        }
        if (mode == CanvasPointerMode.SHAPE) endShape()
        if (mode == CanvasPointerMode.TAPE) endTape()
        when (mode) {
            CanvasPointerMode.BAND -> endBand()
            CanvasPointerMode.LASSO -> endLasso()
            CanvasPointerMode.MOVE -> endMove()
            CanvasPointerMode.RESIZE, CanvasPointerMode.ROTATE -> endTransform()
            else -> Unit
        }
        mode = CanvasPointerMode.IDLE
        setInteractive(false, true)
        // A hand that stopped before it lifted means to stay put, whatever it was doing before.
        if (wasMoving && e.eventTime - lastMoveMs <= FLING_STALE_MS) startFling(panVel)
        onViewChanged()
        // Once more now the gesture has settled, so chrome that only shows on a still selection
        // gets its chance; during the drag the mode was still MOVE.
        onSelectionChanged()
        requestRender()
    }

    private fun abortGesture() {
        cancelLongPress()
        fingerPanStart = null
        if (mode == CanvasPointerMode.DRAW) abandonStroke()
        if (mode == CanvasPointerMode.ERASE) endErase()
        if (mode == CanvasPointerMode.SHAPE) abandonShape()
        if (mode == CanvasPointerMode.TAPE) abandonTape()
        // A cancelled drag never happened: the model was never touched, so putting the box back and
        // dropping the lift is the whole undo.
        if (mode == CanvasPointerMode.MOVE) {
            selection()?.previewMove(0.0, 0.0)
            onLiftSelection(emptyList(), LiftTransform.NONE)
            onSelectionChanged()
        }
        if ((mode == CanvasPointerMode.ROTATE || mode == CanvasPointerMode.RESIZE) && liftedTransform) {
            selection()?.previewBack()
            onLiftSelection(emptyList(), LiftTransform.NONE)
            liftedTransform = false
            onSelectionChanged()
        }
        // A line's end is dragged on the model itself, so a cancelled one is put back by hand.
        val endHandle = grabHandle
        if (mode == CanvasPointerMode.RESIZE && (endHandle == HandleId.START || endHandle == HandleId.END)) {
            selection()?.restoreStart()
            grabHandle = null
            onSelectionChanged()
        }
        mode = CanvasPointerMode.IDLE
        setInteractive(false, true)
        stopFling()
        requestRender()
    }

    // --- drawing ---

    private fun beginDraw(vx: Double, vy: Double, drawTool: Tool, e: MotionEvent, index: Int = 0) {
        // Only the laser glows; a pen with an old neon setting writes plain ink.
        val base = configFor(drawTool).copy(neon = drawTool.isEphemeral)
        // SCALE off: divide the width by the draw-time zoom so the stroke keeps a constant
        // on-screen thickness whatever zoom it was drawn at. Baked in, so it is ordinary ink after.
        val z = viewport.zoom
        val config = if (base.scale) {
            base
        } else {
            base.copy(
                baseWidth = base.baseWidth / z,
                dashLength = base.dashLength / z,
                dashGap = base.dashGap / z,
                scale = true,
            )
        }
        val straight = drawTool == Tool.HIGHLIGHTER && config.straightLine
        val stroke = Stroke(
            drawTool, InteractionController.brushEnds(config, z),
            speedScale = z / devicePxPerDp().coerceAtLeast(1e-9),
            straight = straight,
            smoothScale = InteractionController.smoothScaleFor(z) * config.stabilisationFactor,
        )
        // Live until the pen lifts, so lift-time rules cannot fire mid-draw.
        stroke.finished = false
        // Inking wants the shortest path from nib to pixel, so it draws on demand: a render thread
        // left running keeps the buffer queue full and puts a frame of lag under the pen.
        setInteractive(false, false)
        strokeStartTimeMs = e.eventTime
        val p = viewport.viewportToContent(Pt(vx, vy))
        stroke.addSample(Sample(p.x, p.y, pressureOf(e, index)))
        liveStroke = stroke
        mode = CanvasPointerMode.DRAW
        // Hold still to snap: a pen becomes the shape it drew, the highlighter straightens into a
        // line. A line that is straight already, the wand and the laser never do.
        dwellEligible = detectShapes() && drawTool.isStroke &&
            !straight && !wandEnabled && !drawTool.isEphemeral
        if (dwellEligible) armDwell(Pt(vx, vy))
        onWetStroke(stroke)
        requestRender()
    }

    private fun extendDraw(e: MotionEvent) {
        val idx = e.findPointerIndex(drawingPointerId)
        if (idx < 0) return
        // Historical points first: the digitizer batches several samples into one event, and
        // dropping them coarsens a fast stroke into visible chords.
        for (h in 0 until e.historySize) {
            addStrokePoint(
                e.getHistoricalX(idx, h).toDouble(),
                e.getHistoricalY(idx, h).toDouble(),
                if (drawingIsStylus) e.getHistoricalPressure(idx, h).toDouble() else 1.0,
                e.getHistoricalEventTime(h),
                force = false,
            )
        }
        addStrokePoint(
            e.getX(idx).toDouble(), e.getY(idx).toDouble(),
            pressureOf(e, idx), e.eventTime, force = false,
        )
        predictTip()
        onWetStroke(liveStroke)
        predictedCount = 0
        // Real movement restarts the clock; holding within the slop lets it mature, so the snap
        // fires only once the pen has actually come to rest.
        if (dwellEligible) {
            val here = Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
            if (here.distanceTo(dwellAnchor) > InteractionController.SHAPE_DWELL_SLOP) armDwell(here)
        }
        requestRender()
    }

    /**
     * Fill [predictedX], [predictedY] with where the pen is heading, in content coordinates. Only
     * for freehand ink on the pen: a straight line is already exactly where it will be.
     */
    private fun predictTip() {
        predictedCount = 0
        val p = predictor ?: return
        val stroke = liveStroke ?: return
        if (!drawingIsStylus || stroke.straight) return
        val n = p.predict(drawingPointerId, predictedX, predictedY)
        for (k in 0 until n) {
            val c = viewport.viewportToContent(Pt(predictedX[k], predictedY[k]))
            predictedX[k] = c.x
            predictedY[k] = c.y
        }
        predictedCount = n
    }

    private fun addStrokePoint(vx: Double, vy: Double, pressure: Double, timeMs: Long, force: Boolean) {
        val stroke = liveStroke ?: return
        val p = viewport.viewportToContent(Pt(vx, vy))
        val t = (timeMs - strokeStartTimeMs).toDouble()
        if (stroke.straight) {
            // The far end follows the pen, flat when it is near an axis, as on a page.
            val start = stroke.samples.firstOrNull()
            val end = if (start != null) snapAxisEndpoint(Pt(start.x, start.y), p) else p
            stroke.setStraightEnd(Sample(end.x, end.y, pressure.coerceIn(0.0, 1.0), t))
            return
        }
        val last = stroke.samples.lastOrNull()
        // Decimate by on-screen spacing rather than content spacing, so drawing while zoomed in
        // keeps its detail instead of faceting into chords a zoom factor long.
        val gate = (InteractionController.MIN_SAMPLE_DIST / viewport.zoom)
            .coerceAtMost(InteractionController.MIN_SAMPLE_DIST)
        if (force || last == null || Pt(last.x, last.y).manhattanTo(p) >= gate) {
            stroke.addSample(Sample(p.x, p.y, pressure.coerceIn(0.0, 1.0), t))
        }
    }

    private fun endDraw(e: MotionEvent) {
        cancelDwell()
        dwellEligible = false
        val idx = e.findPointerIndex(drawingPointerId).coerceAtLeast(0)
        addStrokePoint(
            e.getX(idx).toDouble(), e.getY(idx).toDouble(),
            pressureOf(e, idx), e.eventTime, force = true,
        )
        val stroke = liveStroke
        liveStroke = null
        if (stroke == null || stroke.isEmpty) {
            onWetStroke(null)
            return
        }
        // The pen is up: rebuild with lift-time rules on before the stroke is committed. The wet
        // buffer is deliberately not cleared here: the commit releases it in the same step, so no
        // frame can land between the two and blink.
        stroke.finished = true
        simplifyForCommit(stroke)
        onCommitStroke(stroke)
    }

    // --- selection ---

    fun hasSelection(): Boolean = selection()?.isEmpty == false

    /**
     * Whether [at] grabs the settled selection: its rotate grip, one of its handles, or its body.
     * A press that does is routed to the selection whatever tool is armed and whatever is touching,
     * which is what lets a finger drag a selection while finger-draw is off.
     */
    private fun hitsSelection(at: Pt): Boolean {
        val sel = selection() ?: return false
        if (sel.isEmpty) return false
        val tolerance = HANDLE_TOUCH_PX * devicePxPerDp().coerceAtLeast(1.0) / viewport.zoom
        val grip = sel.rotateGrip(OverlayTessellator.rotateArm(viewport.zoom, devicePxPerDp()))
        if (grip != null && at.distanceTo(grip) <= tolerance) return true
        if (sel.hitHandle(at, tolerance) != null) return true
        return sel.contains(at)
    }

    /** Clear the selection and its chrome, for a tool change or a document swap. */
    fun clearSelection() {
        // A long-press grab only borrowed the selection tool; putting the selection away returns it.
        longPressPrevTool?.let {
            longPressPrevTool = null
            tool = it
            onToolChanged(it)
        }
        selection()?.clear()
        bandRect = null
        lassoPoints.clear()
        onLiftSelection(emptyList(), LiftTransform.NONE)
        onSelectionChanged()
        requestRender()
    }

    /**
     * A press on the settled selection grabs it: the grip rotates, a handle resizes, inside the box
     * moves. Shared by the select and lasso tools so both grab the same way, whatever is pressing.
     */
    private fun tryGrabSelection(sel: CanvasSelection, at: Pt): Boolean {
        if (sel.isEmpty) return false
        val tolerance = HANDLE_TOUCH_PX * devicePxPerDp().coerceAtLeast(1.0) / viewport.zoom
        val grip = sel.rotateGrip(OverlayTessellator.rotateArm(viewport.zoom, devicePxPerDp()))
        if (grip != null && at.distanceTo(grip) <= tolerance) {
            sel.beginTransform(at)
            beginLiftedTransform(sel, at)
            mode = CanvasPointerMode.ROTATE
            return true
        }
        val handle = sel.hitHandle(at, tolerance)
        if (handle != null) {
            grabHandle = handle
            sel.beginTransform()
            if (handle == HandleId.START || handle == HandleId.END) {
                // A line's end moves on its own, which no transform of the whole item can show: a
                // scale would rescale its width too. The line is edited live instead; it is one
                // shape, so that costs a re-mesh of one line per sample.
                transformPointer = at
                liftedTransform = false
            } else {
                beginLiftedTransform(sel, at)
            }
            mode = CanvasPointerMode.RESIZE
            return true
        }
        if (sel.contains(at)) {
            beginMoveAt(sel, at)
            return true
        }
        return false
    }

    private fun beginSelect(vx: Double, vy: Double) {
        val sel = selection() ?: return
        val at = viewport.viewportToContent(Pt(vx, vy))
        setInteractive(false, false)
        // A press on a handle or the grip transforms; inside the box it moves; anywhere else
        // starts a fresh band, which is what makes an empty patch of canvas deselect.
        if (tryGrabSelection(sel, at)) return
        // A press that lands on an item picks that item up, as it does on a note. A band is what
        // the empty canvas between items starts, not what selecting anything at all takes. A locked
        // item is not there as far as this is concerned, so a band can be started over one.
        val hit = grabbableAt(at)
        if (hit != null) {
            if (sel.items.none { it === hit }) sel.select(listOf(hit))
            beginMoveAt(sel, at)
            onSelectionChanged()
            return
        }
        sel.clear()
        bandIsLasso = false
        bandAnchor = at
        bandRect = Rect(at.x, at.y, 0.0, 0.0)
        mode = CanvasPointerMode.BAND
        onSelectionChanged()
    }

    /** A lasso that never left a fingertip's patch of screen was a tap. */
    private fun isLassoTap(extent: Rect): Boolean =
        maxOf(extent.w, extent.h) * viewport.zoom < TAP_SLOP_PX * devicePxPerDp().coerceAtLeast(1.0)

    /**
     * A lasso tap: the topmost unlocked object under [at] that the filter takes, within a
     * fingertip's reach so a hairline can be picked; nothing selected when there is none or tap to
     * select is off.
     */
    private fun selectTapped(sel: CanvasSelection, at: Pt) {
        if (!lassoOptions.tapSelect) return
        val reach = LASSO_TAP_REACH_DP * devicePxPerDp().coerceAtLeast(1.0) / viewport.zoom
        val near = Rect(at.x - reach, at.y - reach, reach * 2, reach * 2)
        val filter = lassoOptions.filter
        val hit = itemsIn(near).lastOrNull {
            !it.locked && filter.accepts(it) && (it.contains(at) || it.intersectsCircle(at.x, at.y, reach))
        }
        if (hit != null) sel.select(listOf(hit))
    }

    /** Start dragging the selection from [at], the tail of every press that grabs one. */
    private fun beginMoveAt(sel: CanvasSelection, at: Pt) {
        sel.beginTransform()
        moveAnchor = at
        movedBy = Pt.ZERO
        mode = CanvasPointerMode.MOVE
        onLiftSelection(sel.items, LiftTransform.NONE)
    }

    /**
     * The topmost item under [content], or null. The index is queried with a slop-sized rect, but
     * whether the press really landed on something is the item's own answer.
     */
    private fun itemAt(content: Pt): CanvasItem? {
        val pad = TAP_SLOP_PX / viewport.zoom
        val near = Rect(content.x - pad, content.y - pad, pad * 2, pad * 2)
        // Last, not first: the index comes back in z-order, so the topmost item is the one hit.
        return itemsIn(near).lastOrNull { it.contains(content) }
    }

    /** [itemAt] but skipping anything pinned, which is what a tap or a drag may actually grab. */
    private fun grabbableAt(content: Pt): CanvasItem? = itemAt(content)?.takeUnless { it.locked }

    private fun extendBand(vx: Double, vy: Double) {
        val at = viewport.viewportToContent(Pt(vx, vy))
        bandRect = Rect.fromPoints(bandAnchor, at)
        onSelectionChanged()
        requestRender()
    }

    private fun endBand() {
        val sel = selection()
        val rect = bandRect
        bandRect = null
        val lasso = bandIsLasso
        bandIsLasso = false
        if (lasso && sel != null && rect != null && isLassoTap(rect)) {
            selectTapped(sel, lassoStart)
        } else if (sel != null && rect != null && (rect.w > 1e-6 || rect.h > 1e-6)) {
            val members = SelectionMath.bandMembers(itemsIn(rect), rect)
            sel.select(if (lasso) members.filter { lassoOptions.filter.accepts(it) } else members)
        }
        onSelectionChanged()
        requestRender()
    }

    /** The lasso's shape, object filter and tap-to-select, from the preferences (host-installed). */
    var lassoOptions = com.xnotes.core.tools.LassoOptions()

    /** Where the current lasso began, for telling a tap from a loop. */
    private var lassoStart = Pt.ZERO

    /** True while the band being dragged is a rectangle lasso rather than the select tool's. */
    private var bandIsLasso = false

    private fun beginLasso(vx: Double, vy: Double) {
        val sel = selection() ?: return
        val at = viewport.viewportToContent(Pt(vx, vy))
        setInteractive(false, false)
        // Grabbing the settled selection comes first, as it does for the select tool: a pen press
        // inside it moves it instead of throwing it away to draw another lasso.
        if (tryGrabSelection(sel, at)) return
        sel.clear()
        lassoStart = at
        if (lassoOptions.shape == com.xnotes.core.tools.LassoShape.RECTANGLE) {
            // The rectangle lasso is the select tool's band, filtered and tap-aware.
            bandIsLasso = true
            bandAnchor = at
            bandRect = Rect(at.x, at.y, 0.0, 0.0)
            mode = CanvasPointerMode.BAND
            onSelectionChanged()
            return
        }
        lassoPoints.clear()
        lassoPoints.add(at)
        mode = CanvasPointerMode.LASSO
        onSelectionChanged()
    }

    private fun extendLasso(vx: Double, vy: Double) {
        val at = viewport.viewportToContent(Pt(vx, vy))
        val last = lassoPoints.lastOrNull()
        // Decimate by on-screen spacing, so a slow drag does not pile up thousands of vertices.
        if (last == null || last.distanceTo(at) * viewport.zoom >= LASSO_MIN_STEP_PX) lassoPoints.add(at)
        onSelectionChanged()
        requestRender()
    }

    private fun endLasso() {
        val sel = selection()
        val filter = lassoOptions.filter
        val extent = if (lassoPoints.isEmpty()) Rect(lassoStart.x, lassoStart.y, 0.0, 0.0) else Rect.bounding(lassoPoints)
        if (sel != null && isLassoTap(extent)) {
            selectTapped(sel, lassoStart)
        } else if (sel != null && lassoPoints.size >= 3) {
            val bounds = Rect.bounding(lassoPoints)
            sel.select(SelectionMath.lassoMembers(itemsIn(bounds), lassoPoints.toList()).filter { filter.accepts(it) })
        }
        lassoPoints.clear()
        onSelectionChanged()
        requestRender()
    }

    /**
     * A drag in progress. The model is left exactly where it was and the renderer is told to draw
     * the selection offset, so a drag costs one uniform however much is selected. The box follows
     * so the chrome tracks the finger.
     */
    private fun extendMove(vx: Double, vy: Double) {
        val sel = selection() ?: return
        val at = viewport.viewportToContent(Pt(vx, vy))
        movedBy = Pt(at.x - moveAnchor.x, at.y - moveAnchor.y)
        sel.previewMove(movedBy.x, movedBy.y)
        onLiftSelection(sel.items, LiftTransform.shift(movedBy.x, movedBy.y))
        onSelectionChanged()
        requestRender()
    }

    /** Finger up: apply the whole move to the model once, then hand the drawing back to it. */
    private fun endMove() {
        val sel = selection() ?: return
        sel.moveLive(movedBy.x, movedBy.y)
        onLiftSelection(emptyList(), LiftTransform.NONE)
        onCommitSelection(sel.buildCommand(movedOnly = true, dx = movedBy.x, dy = movedBy.y))
        onSelectionChanged()
        requestRender()
    }

    /**
     * Arm a handle or grip drag to be drawn by the renderer rather than baked by the model, when
     * everything selected maps faithfully. Ink and shapes do: their geometry is mapped through the
     * transform, which is exactly what the shader does. A placed SVG does too, since it is drawn
     * from triangles rather than from a texture, and it has the most to gain: baking would re-mesh
     * the whole drawing on every pointer sample. A photo or a text box is rebuilt instead of mapped,
     * so those keep the slow path and stay honest.
     */
    private fun beginLiftedTransform(sel: CanvasSelection, at: Pt) {
        transformPointer = at
        liftedTransform = sel.items.all { it is Stroke || it is ShapeItem || it is TapeItem || isVectorImage(it) }
        if (liftedTransform) onLiftSelection(sel.items, LiftTransform.NONE)
    }

    private fun isVectorImage(item: CanvasItem): Boolean =
        item is ImageItem && item.edit.isIdentity && com.xnotes.platform.ImageDecoder.isVector(item.image.file.path)

    /**
     * A resize in progress. The model is left alone and the renderer is handed the map, so a handle
     * drag costs a few uniforms however much is selected.
     */
    private fun extendResize(vx: Double, vy: Double) {
        val sel = selection() ?: return
        val handle = grabHandle ?: return
        val at = viewport.viewportToContent(Pt(vx, vy))
        transformPointer = at
        if (liftedTransform) {
            val map = sel.previewResize(handle, at) ?: return
            onLiftSelection(sel.items, LiftTransform.of(map, sel.transformPivot ?: Pt.ZERO))
        } else {
            sel.resizeLive(handle, at)
        }
        onSelectionChanged()
        requestRender()
    }

    /** A turn in progress, drawn the same way a resize is. */
    private fun extendRotate(vx: Double, vy: Double) {
        val sel = selection() ?: return
        val at = viewport.viewportToContent(Pt(vx, vy))
        transformPointer = at
        if (liftedTransform) {
            val swept = sel.previewRotate(at)
            onLiftSelection(sel.items, LiftTransform.turn(sel.transformPivot ?: Pt.ZERO, swept))
        } else {
            sel.rotateLive(at)
        }
        onSelectionChanged()
        requestRender()
    }

    private fun endTransform() {
        val sel = selection() ?: return
        val handle = grabHandle
        val wasResize = handle != null
        grabHandle = null
        // Finger up on a lifted drag: apply the whole thing to the model once, then hand the drawing
        // back to it.
        if (liftedTransform) {
            if (handle != null) sel.resizeLive(handle, transformPointer)
            else sel.rotateLive(transformPointer)
            onLiftSelection(emptyList(), LiftTransform.NONE)
            liftedTransform = false
        }
        onCommitSelection(sel.buildCommand(movedOnly = false))
        // A resize of an upright box snaps onto the real bounds, which is what keeps the chrome
        // honest around a text box that refused to shrink past its own text. A turned box cannot be
        // re-derived at all: item bounds are axis aligned, so measuring one and then tilting the
        // result grew the box by its own rotation and made every release jump.
        val upright = sel.box?.let { kotlin.math.abs(it.angle) < 1e-9 } == true
        if (wasResize && upright) sel.refreshBox()
        onSelectionChanged()
        requestRender()
    }

    // --- shapes ---

    private fun beginShape(vx: Double, vy: Double) {
        val cfg = shapeConfig()
        val at = viewport.viewportToContent(Pt(vx, vy))
        val ink = inkColor()
        val fill = if (cfg.fill && cfg.shape.isClosed) ink.scaleAlpha(cfg.fillAlpha) else null
        pendingShape = ShapeItem(
            cfg.shape, at, at, ink, cfg.strokeWidth * InteractionController.SHAPE_PEN_PARITY, fill,
            cfg.neon, cfg.neonStrength,
            dashed = cfg.dashed, dashLength = cfg.dashLength, dashGap = cfg.dashGap,
        )
        mode = CanvasPointerMode.SHAPE
        setInteractive(false, false)
        onPendingShape(pendingShape)
        requestRender()
    }

    private fun extendShape(vx: Double, vy: Double) {
        val shape = pendingShape ?: return
        val raw = viewport.viewportToContent(Pt(vx, vy))
        shape.end = when {
            // Line and arrow pin flat when the dragged end lands near an axis.
            shape.shape.isEndpointShape -> snapAxisEndpoint(shape.start, raw)
            // Circle keeps its box square, so it stays a circle rather than becoming an ellipse.
            shape.shape == ShapeKind.CIRCLE -> squareCorner(shape.start, raw)
            else -> raw
        }
        onPendingShape(shape)
        requestRender()
    }

    private fun endShape() {
        val shape = pendingShape
        pendingShape = null
        onPendingShape(null)
        // A tap makes no shape; only a real drag commits one.
        if (shape != null && shape.start.distanceTo(shape.end) > InteractionController.SHAPE_MIN_DRAG) {
            onCommitShape(shape)
        }
        requestRender()
    }

    private fun abandonShape() {
        pendingShape = null
        onPendingShape(null)
    }

    /** Constrain a dragged corner to a square box anchored at [anchor], for the perfect circle. */
    private fun squareCorner(anchor: Pt, p: Pt): Pt {
        val side = max(abs(p.x - anchor.x), abs(p.y - anchor.y))
        val sx = if (p.x >= anchor.x) 1.0 else -1.0
        val sy = if (p.y >= anchor.y) 1.0 else -1.0
        return Pt(anchor.x + sx * side, anchor.y + sy * side)
    }

    /** Snap a line or arrow's dragged end to an exact horizontal or vertical run from [anchor]. */
    private fun snapAxisEndpoint(anchor: Pt, p: Pt): Pt {
        val dx = p.x - anchor.x
        val dy = p.y - anchor.y
        if (dx == 0.0 && dy == 0.0) return p
        val snap = Math.toRadians(InteractionController.SHAPE_AXIS_SNAP_DEG)
        val fromHoriz = atan2(abs(dy), abs(dx)) // 0 is horizontal, PI/2 is vertical
        return when {
            fromHoriz <= snap -> Pt(p.x, anchor.y)
            fromHoriz >= Math.PI / 2.0 - snap -> Pt(anchor.x, p.y)
            else -> p
        }
    }

    // --- tape ---

    /** A press with the tape tool: a strip only appears once the pen pulls past a tap. */
    private fun beginTape(vx: Double, vy: Double) {
        val cfg = tapeConfig()
        val at = viewport.viewportToContent(Pt(vx, vy))
        pendingTape = TapeItem(at, at, cfg.width, cfg.color, cfg.pattern)
        tapeDownAt = Pt(vx, vy)
        tapePulling = false
        mode = CanvasPointerMode.TAPE
        setInteractive(false, false)
    }

    private fun extendTape(vx: Double, vy: Double) {
        val tape = pendingTape ?: return
        if (!tapePulling && Pt(vx, vy).distanceTo(tapeDownAt) > TAP_SLOP_PX) tapePulling = true
        tape.end = TapeItem.snapAxis(tape.start, viewport.viewportToContent(Pt(vx, vy)))
        if (!tapePulling) return
        onPendingShape(tape)
        requestRender()
    }

    private fun endTape() {
        val tape = pendingTape
        val pulled = tapePulling
        abandonTape()
        if (!pulled) {
            toggleTapeAt(viewport.viewportToContent(tapeDownAt))
        } else if (tape != null && tape.start.distanceTo(tape.end) >= InteractionController.TAPE_MIN_LENGTH) {
            onCommitShape(tape)
        }
        requestRender()
    }

    private fun abandonTape() {
        if (tapePulling) onPendingShape(null)
        pendingTape = null
        tapePulling = false
    }

    /** Peel back or stick down the topmost strip of tape under [content]; false when there is none. */
    private fun toggleTapeAt(content: Pt): Boolean {
        val pad = TAP_SLOP_PX / viewport.zoom
        val near = Rect(content.x - pad, content.y - pad, pad * 2, pad * 2)
        val tape = itemsIn(near).lastOrNull { it is TapeItem && it.contains(content) } as? TapeItem ?: return false
        onToggleTape(tape)
        return true
    }

    // --- hold still to snap a freehand stroke into a shape ---

    private fun armDwell(at: Pt) {
        cancelDwell()
        dwellAnchor = at
        val r = Runnable { onDwellElapsed() }
        dwellRunnable = r
        handler.postDelayed(r, InteractionController.SHAPE_DWELL_MS)
    }

    private fun cancelDwell() {
        dwellRunnable?.let { handler.removeCallbacks(it) }
        dwellRunnable = null
    }

    /** The pen has held still: if what it drew reads as a shape, swap the stroke for that shape. */
    private fun onDwellElapsed() {
        dwellRunnable = null
        if (!dwellEligible) return
        val stroke = liveStroke ?: return
        if (stroke.tool == Tool.HIGHLIGHTER) return straightenHighlight(stroke)
        if (stroke.samples.size < InteractionController.SHAPE_MIN_SAMPLES) return
        val rec = ShapeRecognizer.recognize(stroke.samples) ?: return
        // As drawn: colour, neon, dash, and a pencil's graphite at the pressure it was drawn at.
        val shape = ShapeItem.snappedFrom(stroke, rec, stroke.config.baseWidth * InteractionController.SHAPE_PEN_PARITY)
        // The stroke was never committed, so dropping it makes the wet ink vanish the moment it
        // snaps; the eventual pen up then commits nothing.
        liveStroke = null
        onWetStroke(null)
        dwellEligible = false
        cancelDwell()
        onCommitShape(shape)
        mode = CanvasPointerMode.IDLE
        // Leave the new shape selected, as a note does, so it can be resized or turned straight
        // away. The chrome only shows over a settled selection, so the handles arrive at pen up.
        selection()?.select(listOf(shape))
        onSelectionChanged()
        requestRender()
    }

    /**
     * The highlighter held still: from here to the lift it is a straight line from where it landed
     * to the pen, as in Samsung Notes. The line replaces the wet stroke and commits like any other
     * highlight; what had been drawn freehand was never committed and simply stops being shown.
     */
    private fun straightenHighlight(stroke: Stroke) {
        val first = stroke.samples.firstOrNull() ?: return
        val last = stroke.samples.lastOrNull() ?: return
        // A press that has not gone anywhere is a dot, not a line; the next move re-arms.
        val travel = Pt(first.x, first.y).distanceTo(Pt(last.x, last.y)) * viewport.zoom
        if (travel < InteractionController.SHAPE_DWELL_SLOP * 2) return
        val line = Stroke(stroke.tool, stroke.config, emptyList(), stroke.speedScale, straight = true, smoothScale = stroke.smoothScale)
        line.finished = false
        line.addSample(Sample(first.x, first.y, first.pressure, 0.0))
        val end = snapAxisEndpoint(Pt(first.x, first.y), Pt(last.x, last.y))
        line.setStraightEnd(Sample(end.x, end.y, last.pressure, last.t))
        liveStroke = line
        onWetStroke(line)
        dwellEligible = false
        cancelDwell()
        requestRender()
    }

    // --- erasing ---

    /** Eraser radius in content pixels: the tool's width, or a constant on-screen size when off. */
    fun eraserRadius(): Double {
        val cfg = configFor(Tool.ERASER)
        return if (cfg.scale) cfg.baseWidth else cfg.baseWidth / viewport.zoom
    }

    private fun areaErase(): Boolean = configFor(Tool.ERASER).eraseMode == EraseMode.AREA

    private fun beginErase(vx: Double, vy: Double) {
        eraseSession = onEraseBegin() ?: return
        mode = CanvasPointerMode.ERASE
        lastErase = null
        eraseAt(vx, vy)
    }

    private fun extendErase(e: MotionEvent) {
        val idx = e.findPointerIndex(drawingPointerId)
        if (idx < 0) return
        // The historical points matter here as much as when drawing: a fast sweep that only sampled
        // the newest event would skip over strokes between one frame and the next.
        for (h in 0 until e.historySize) {
            eraseAt(e.getHistoricalX(idx, h).toDouble(), e.getHistoricalY(idx, h).toDouble())
        }
        eraseAt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
    }

    /** Where the eraser last stamped, viewport px; the next stamp sweeps from there. */
    private var lastErase: Pt? = null

    /** Erase from where the eraser last was to ([vx], [vy]) in stamps half its width apart, so however
     *  far the pen moved between two events the circle sweeps a continuous band. */
    private fun eraseAt(vx: Double, vy: Double) {
        val session = eraseSession ?: return
        val radius = eraserRadius()
        onEraserCursor(Pt(vx, vy), radius * viewport.zoom)
        val from = lastErase
        lastErase = Pt(vx, vy)
        val step = kotlin.math.max(radius * viewport.zoom * 0.5, 1.0)
        val stamps = if (from == null) 1 else kotlin.math.ceil(from.distanceTo(Pt(vx, vy)) / step).toInt().coerceIn(1, 64)
        val area = areaErase()
        for (k in 1..stamps) {
            val t = k.toDouble() / stamps
            val x = if (from == null) vx else from.x + (vx - from.x) * t
            val y = if (from == null) vy else from.y + (vy - from.y) * t
            val content = viewport.viewportToContent(Pt(x, y))
            session.erase(content.x, content.y, radius, area)
        }
        requestRender()
    }

    private fun endErase() {
        val session = eraseSession ?: return
        eraseSession = null
        onEraserCursor(null, 0.0)
        onEraseEnd(session)
        requestRender()
    }

    /** Drop the wet stroke without committing it, when a second finger turns the gesture into a zoom. */
    private fun abandonStroke() {
        liveStroke = null
        onWetStroke(null)
    }

    /** Shed the samples the ribbon does not need, at the tolerance the draw zoom justifies. */
    private fun simplifyForCommit(stroke: Stroke) {
        if (StrokeSimplify.enabled && !stroke.straight) {
            val eps = (InteractionController.SIMPLIFY_EPS / viewport.zoom)
                .coerceAtMost(InteractionController.SIMPLIFY_EPS)
            val slim = StrokeSimplify.simplify(
                stroke.samples, stroke.geometry().halfWidths, eps,
                stroke.smoothScale, stroke.config.directionStrength,
            )
            if (slim.size != stroke.sampleCount) {
                stroke.setSamples(slim) // allocates exactly, so no trim needed
                stroke.invalidate()
                return
            }
        }
        // Nothing was dropped, so the stroke still carries the slack capture doubling left behind.
        stroke.trimToSize()
    }

    private fun pressureOf(e: MotionEvent, index: Int): Double =
        if (drawingIsStylus) e.getPressure(index).toDouble() else 1.0

    // --- pan ---

    /**
     * [byFinger]: a finger's pan, held still while zoom lock wants two fingers, and put back if the
     * system later calls it a palm. Any pan is held by the older "double"/"none" lock choice.
     */
    private fun beginPan(vx: Double, vy: Double, fromPenButton: Boolean = false, byFinger: Boolean = false) {
        mode = CanvasPointerMode.PAN
        panDownAt = Pt(vx, vy)
        panFromPenButton = fromPenButton
        panHeld = !PalmRejection.lockedPanMoves(zoomLocked, zoomLockPan, lockedTwoFingerScroll, byFinger)
        fingerPanStart = if (byFinger) Pt(viewport.scrollX, viewport.scrollY) else null
        // Moving the view is paced by the display, so the render thread stays up for it.
        setInteractive(true, true)
        startTrackingVelocity(vx, vy, android.os.SystemClock.uptimeMillis())
    }

    /** True when the pan never really moved, so it reads as a tap rather than a scroll. */
    private fun panWasTap(vx: Double, vy: Double): Boolean =
        Pt(vx, vy).distanceTo(panDownAt) <= TAP_SLOP_PX

    private fun extendPan(vx: Double, vy: Double, timeMs: Long) {
        trackVelocity(vx, vy, timeMs)
        if (!panHeld) viewport.panByViewport(vx - lastPan.x, vy - lastPan.y)
        lastPan = Pt(vx, vy)
        onViewChanged()
        requestRender()
    }

    // Once zoom is locked the zoomLockPan preference decides which pans still move the view; an
    // unlocked view always pans. Mirrors the paged canvas so one preference governs both surfaces.
    // A single pointer's pan asks PalmRejection.lockedPanMoves (in beginPan), which adds the
    // two-finger rule for fingers.
    private fun pinchPanAllowed(): Boolean = !(zoomLocked && zoomLockPan == "none")

    // --- pinch ---

    /**
     * Start a pinch on the first two pointers, leaving out [skipIndex] (one that is lifting).
     *
     * The pair is remembered by pointer id, not by index: indices are renumbered whenever a finger
     * comes or goes, and a pinch that read index 0 and 1 would jump to whichever finger now has them.
     */
    private fun beginPinch(e: MotionEvent, skipIndex: Int = -1) {
        var ia = -1
        var ib = -1
        for (i in 0 until e.pointerCount) {
            if (i == skipIndex) continue
            if (ia < 0) ia = i else if (ib < 0) { ib = i; break }
        }
        if (ia < 0 || ib < 0) {
            mode = CanvasPointerMode.IDLE
            return
        }
        mode = CanvasPointerMode.PINCH
        setInteractive(true, true)
        pinchIdA = e.getPointerId(ia)
        pinchIdB = e.getPointerId(ib)
        val a = Pt(e.getX(ia).toDouble(), e.getY(ia).toDouble())
        val b = Pt(e.getX(ib).toDouble(), e.getY(ib).toDouble())
        val mid = (a + b) * 0.5
        pinchInitDist = a.distanceTo(b).coerceAtLeast(1.0)
        pinchInitZoom = viewport.zoom
        pinchAnchorContent = viewport.viewportToContent(mid)
        startTrackingVelocity(mid.x, mid.y, e.eventTime)
    }

    private fun updatePinch(e: MotionEvent) {
        if (e.pointerCount < 2) return
        val ia = e.findPointerIndex(pinchIdA)
        val ib = e.findPointerIndex(pinchIdB)
        // A pair that has gone without a pointer-up (a cancelled pointer) is picked up afresh.
        if (ia < 0 || ib < 0) return beginPinch(e)
        val a = Pt(e.getX(ia).toDouble(), e.getY(ia).toDouble())
        val b = Pt(e.getX(ib).toDouble(), e.getY(ib).toDouble())
        val dist = a.distanceTo(b)
        if (dist < 1e-3) return
        val mid = (a + b) * 0.5
        trackVelocity(mid.x, mid.y, e.eventTime)
        // Zoom about the pinch's own midpoint, and let that midpoint drag the canvas at the same
        // time: the content under the fingers stays under the fingers whether they spread or slide.
        // A locked zoom keeps the zoom it started at, so the pinch is a pan and nothing else.
        viewport.zoom = if (zoomLocked) pinchInitZoom else pinchInitZoom * (dist / pinchInitDist)
        if (pinchPanAllowed()) {
            viewport.scrollX = pinchAnchorContent.x - mid.x / viewport.zoom
            viewport.scrollY = pinchAnchorContent.y - mid.y / viewport.zoom
        }
        lastPan = mid
        onViewChanged()
        requestRender()
    }

    // --- velocity and fling ---

    /**
     * Velocity is timed by the events' own timestamps, which is when the digitizer saw the finger,
     * rather than by when each event happened to be handled: dispatch jitter would otherwise read
     * as speed, and a fling would launch harder or softer than the hand moved.
     */
    private fun startTrackingVelocity(vx: Double, vy: Double, timeMs: Long) {
        stopFling()
        lastPan = Pt(vx, vy)
        velAt = Pt(vx, vy)
        lastMoveMs = timeMs
        panVel = Pt.ZERO
    }

    /** Where the velocity was last sampled; apart from [lastPan], which moves on every event. */
    private var velAt = Pt.ZERO

    private fun trackVelocity(vx: Double, vy: Double, timeMs: Long) {
        val now = timeMs
        // Two events from one digitizer frame carry one timestamp; that is no speed at all.
        if (now <= lastMoveMs) return
        val dt = (now - lastMoveMs) / 1000.0
        val inst = Pt((vx - velAt.x) / dt, (vy - velAt.y) / dt)
        velAt = Pt(vx, vy)
        val k = InteractionController.VEL_SMOOTH
        panVel = Pt(panVel.x * k + inst.x * (1 - k), panVel.y * k + inst.y * (1 - k))
        lastMoveMs = now
    }

    private fun startFling(fingerVel: Pt) {
        if (fingerVel.length() < InteractionController.FLING_MIN_START) return
        setInteractive(true, true) // the glide is still motion, so the render thread stays up for it
        flingVel = fingerVel
        flinging = true
        lastFlingMs = System.nanoTime() / 1_000_000L
        choreographer.postFrameCallback(flingFrame)
    }

    fun stopFling() {
        flinging = false
    }

    private fun stepFling(frameTimeNanos: Long) {
        if (!flinging) return
        val now = frameTimeNanos / 1_000_000L
        val dt = ((now - lastFlingMs).coerceIn(1L, 40L)) / 1000.0
        lastFlingMs = now
        viewport.panByViewport(flingVel.x * dt, flingVel.y * dt)
        val decay = exp(-InteractionController.FLING_FRICTION * dt)
        flingVel = Pt(flingVel.x * decay, flingVel.y * decay)
        onViewChanged()
        requestRender()
        // Nothing bounds an infinite canvas, so a glide only ever ends by running out of speed.
        if (flingVel.length() < InteractionController.FLING_MIN_STOP) {
            flinging = false
            setInteractive(false, true)
        } else {
            choreographer.postFrameCallback(flingFrame)
        }
    }
}
