package com.xnotes.canvas

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Obb
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.history.AddItem
import com.xnotes.core.history.AddItems
import com.xnotes.core.history.Command
import com.xnotes.core.history.CompositeCommand
import com.xnotes.core.history.EraseItems
import com.xnotes.core.history.History
import com.xnotes.core.history.LockItems
import com.xnotes.core.history.EditText
import com.xnotes.core.history.MoveItems
import com.xnotes.core.history.ReorderItems
import com.xnotes.core.history.RemoveMarkup
import com.xnotes.core.history.ReplacePageItems
import com.xnotes.core.history.ResizeItem
import com.xnotes.core.history.TableEdit
import com.xnotes.core.history.RestyleItems
import com.xnotes.core.history.RestyleText
import com.xnotes.core.history.TransferItems
import com.xnotes.core.history.TransformItems
import com.xnotes.core.infinite.OverlayTessellator
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Document
import com.xnotes.core.model.DrawStyle
import com.xnotes.core.model.deepCopy
import com.xnotes.core.model.GeoHandle
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.RectHandle
import com.xnotes.core.model.Resizable
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeHandle
import com.xnotes.core.model.GeometrySnapshot
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.StickyColors
import com.xnotes.core.model.Stroke
import com.xnotes.core.model.TableGrid
import com.xnotes.core.model.TableItem
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TextHandle
import com.xnotes.core.model.TextItem
import com.xnotes.core.model.TextStyle
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.core.pdf.MarkupPainter
import com.xnotes.core.stroke.RecognizedShape
import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.ShapeRecognizer
import com.xnotes.core.stroke.StrokeSimplify
import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.ShapeKind
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.TapeConfig
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolDefaults
import com.xnotes.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The pointer state machine modes (spec 06 §1). */
enum class PointerMode {
    IDLE, DRAW, ERASE, BAND, LASSO_DRAW, SHOT, SHAPE, MOVE, RESIZE, TRANSFORM, PAN, PINCH, FLOW_TEXT,
    TEXT_DRAG, RULER_MOVE, RULER_TRANSFORM, RULER_ROTATE, PDF_TEXT, TABLE_COL, TAPE,
}

/**
 * Geometry of the live text editor field: [x]/[y] place its top-left in viewport pixels, while
 * [width]/[height]/[fontPx] are **content-space** (page pixels, pre-zoom) and [zoom] maps them
 * to the screen. The overlay lays text out at content scale and draws scaled by [zoom] — the
 * same mechanism as the baked painter — so its line wrapping matches the canvas exactly.
 */
data class EditingField(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val fontPx: Double,
    val zoom: Double,
    val face: FontFace,
    val rgba: Rgba,
    val text: String,
    /** The page accent as it reads on the page under the field: its outline, caret and selection. */
    val accent: Rgba,
    /** The view's page rotation (deg cw); the overlay spins by this around (x, y). */
    val rotation: Int = 0,
    /** The text sits on something the canvas paints under it (a sticky note's card, a table cell),
     *  so the field draws no border of its own. */
    val card: Boolean = false,
    /** A table cell: Tab and Enter move to the next cell rather than typing. */
    val cell: Boolean = false,
    /** Set in bold (a table's header row). */
    val bold: Boolean = false,
    /** Changes with every new thing being edited, so the field starts over on the new text rather
     *  than carrying the last one's (moving between table cells keeps the same field up). */
    val session: Int = 0,
)

/** The floating text style bar's target: the active box's viewport rect + its style. */
data class TextBar(
    val rect: Rect,
    val face: FontFace,
    val pointSize: Double,
    /** True while the keyboard field is up (vs the box merely being selected). */
    val editing: Boolean,
    /** A sticky note's card colour, or null for a plain text box (no colour control). */
    val fill: Rgba? = null,
)

/**
 * Drives editing from pointer input (spec 06): drawing, the object eraser,
 * rubber-band and lasso selection, moving a selection, plus pan/zoom. Resize,
 * shapes, long-press and text are layered on in later commits.
 */
class InteractionController(
    private val state: CanvasState,
    val history: History,
    private val textMeasurer: TextMeasurer,
    private val requestRender: () -> Unit,
    private val onContentChanged: () -> Unit = {},
    private val onViewChanged: () -> Unit = {},
    /** A pinch just snapped the view to fit-to-width (newly): surface the lock hint. */
    private val onFitWidthSnapped: () -> Unit = {},
    /** A pinch broke past the fit-to-width magnet: dismiss the lock hint. */
    private val onFitWidthReleased: () -> Unit = {},
    private val onSelectionChanged: (Boolean) -> Unit = {},
    private val onToolChanged: (Tool) -> Unit = {},
    private val onTextEditStart: (EditingField?) -> Unit = {},
    private val onTextEditEnd: () -> Unit = {},
    /** Selection menu: a viewport rect to show it anchored to, or null to hide. */
    private val onSelectionMenu: (Rect?) -> Unit = {},
    /** Screenshot menu: a viewport rect to anchor the "copy as image" bar to, or null to hide. */
    private val onScreenshotMenu: (Rect?) -> Unit = {},
    /** Long-press on empty space: open a context menu at (viewport, content). */
    /** Long press on empty space, or on a locked item: the third argument is that item, if any. */
    private val onContextMenu: (Pt, Pt, CanvasItem?) -> Unit = { _, _, _ -> },
    /** Pulled past the document's bottom end far enough and released: append a new page. */
    private val onAddPageAtEnd: () -> Unit = {},
    /** A short haptic tick (e.g. the overscroll pull crossed the add-page threshold). */
    private val onHaptic: () -> Unit = {},
) {
    /** Whether the system clipboard currently holds an image (provided by the host). */
    var clipboardHasImage: () -> Boolean = { false }

    /** The inline-flow caret controller; TEXT-tool gestures route here (installed by the Editor). */
    var flowText: FlowTextController? = null

    /** Host hook for tap-to-open PDF links. A finger tap landed on page [pageIndex] at [pageLocal]
     *  (page-local content px). Returns true if it hit a known link and was handled, so the tap is
     *  consumed (skipping the selection-dismiss / fling). The host parses link rects lazily off the
     *  main thread, so a tap on a not-yet-parsed page returns false and opens the link a moment later
     *  once it is ready. */
    var onLinkTap: ((pageIndex: Int, pageLocal: Pt) -> Boolean)? = null

    /**
     * A tap that may open a markup, at viewport point [at]: a pan tap ([withLink], so a menu can offer
     * the link under it) or the markup tool's. True when a markup or its note icon took it.
     */
    var onMarkupTap: ((at: Pt, withLink: Boolean) -> Boolean)? = null

    /** A pan tap or a markup tool tap at viewport point [at], told before anything acts on it. */
    var onTap: ((at: Pt) -> Unit)? = null

    /** PDF text selection: a free pointer's long press on a PDF page selects there (installed by the Editor). */
    var pdfText: PdfTextController? = null

    /** The open search's matches, tinted under the selections. */
    var searchTints: SearchTints? = null

    /** Text markups the page layers don't show yet, under everything else in the overlay. */
    var markupOverlay: MarkupOverlay? = null

    /** The icons over noted markups, above all the page's content. */
    var noteIcons: NoteIcons? = null
    val document: Document get() = state.document

    var tool: Tool = Tool.DEFAULT
        private set

    /** The single immediately-prior Tool (not a stack); null until the first switch. Used by the
     *  tap-gesture toggles. Ruler/wand are never Tools, so they can't land here. Not persisted. */
    var previousTool: Tool? = null
        private set
    var inkColor: Rgba = InkPalette.DEFAULT

    /** Whether a finger draws (true) or pans (false). The stylus always uses the armed tool. */
    var fingerDraws: Boolean = false

    /** Panning allowed while zoom is locked: "single" (default) | "double" | "none". */
    var zoomLockPan: String = "single"

    /** While zoom is locked, one finger holds the page and only two scroll ([PalmRejection.lockedPanMoves]). */
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

    /** The live pan holds the page still: zoom is locked and only two fingers scroll. */
    private var panHeld = false

    /** Whether holding a freehand ink stroke still snaps it to a recognized shape (spec: "hold to snap"). */
    var detectShapes: Boolean = false

    /** Tool the stylus side button activates while held, or null to ignore the button. */
    var penButtonTool: Tool? = Tool.ERASER

    /** Side button as last seen on the hover/generic-motion stream or a stylus-button KeyEvent
     *  (Feeder C, for Bluetooth pens that report it only there); read only at touch-down, so a
     *  press after the pen is already down does not activate the mapped tool. */
    private var stylusButtonHeld = false

    /** When true, the side-button tool also runs off the hover stream (no contact needed); eraser/pan only. */
    var penButtonHover: Boolean = false

    /** The side-button tool currently running off the hover stream, or null when no hover gesture is live. */
    private var hoverActionTool: Tool? = null

    private val toolConfigs: MutableMap<Tool, ToolConfig> =
        Tool.entries.associateWith { ToolDefaults.configFor(it) }.toMutableMap()

    private var mode = PointerMode.IDLE

    // DRAW
    private var liveStrokeField: Stroke? = null

    /**
     * The stroke under the pen. Clearing it is how every abort path in here ends, so that is where
     * the front buffer is told to give its pixels back; a stroke that reached [fileStroke] has
     * already handed them over and is not disturbed.
     */
    private var liveStroke: Stroke?
        get() = liveStrokeField
        set(value) {
            liveStrokeField = value
            if (value == null) frontInk?.abandon()
        }
    private var strokePageIndex: Int? = null

    /** Event time of the live stroke's first sample; later samples store `eventTime − this` (the speed pen reads it). */
    private var strokeStartTimeMs = 0L
    private var drawingPointerId = -1
    private var drawingIsStylus = false

    // SHAPE SNAP (hold a freehand stroke still → it becomes a real shape)
    /** Pending "pen held still" timer; non-null only while a stroke is eligible and armed. */
    private var dwellRunnable: Runnable? = null
    /** Viewport px of the last point that re-armed the dwell timer (sub-slop jitter doesn't reset it). */
    private var dwellAnchor = Pt.ZERO
    /** True for the current stroke when snapping is allowed (pref on, an ink pen, not a straight line). */
    private var dwellEligible = false
    /** A mid-stroke snap auto-selected a shape; show its menu once the pen lifts (endDraw). */
    private var snappedSelectionPendingMenu = false
    /** This stroke began by dismissing an active selection; a bare tap then leaves no dot. */
    private var strokeDismissedSelection = false

    /** Segments the current stroke left behind on pages it has already walked off, so the whole
     *  crossing lifts and re-lays as one undo step. Empty for a stroke that stayed on its page. */
    private val crossedSegments = mutableListOf<Command>()
    /** Viewport-px down point of the current stroke, for the tap-vs-drag dismiss test. */
    private var drawDownViewport = Pt.ZERO

    // PAN + inertial fling
    private var lastPan = Pt.ZERO
    private var lastMoveMs = 0L
    private var panVel = Pt.ZERO // smoothed finger velocity, viewport px/s
    private var panDownViewport = Pt.ZERO // where the current pan began (viewport px), for tap-to-dismiss
    private var panFromPenButton = false // a side-button pan parks where the pen lifted: no glide
    private var downStoppedFling = false // this touch landed on a moving glide, so its lift isn't a dismiss tap
    private var panMayCommitText = false // pan begun off an open text box: a tap commits it, a drag scrolls
    // Framework singletons, created lazily on first use (always a gesture on the main thread) so
    // the controller's selection/edit logic stays constructible — and unit-testable — off-device.
    private val choreographer by lazy { Choreographer.getInstance() }
    private var flinging = false
    private var flingVel = Pt.ZERO // scroll-space velocity, viewport px/s
    private var lastFlingMs = 0L
    private val flingFrame = Choreographer.FrameCallback { frameTimeNanos -> stepFling(frameTimeNanos) }

    // ELASTIC OVERSCROLL (pull past the bottom end to add a page)
    /** True once the live stretch has crossed the add-page threshold, so the haptic fires once. */
    private var overscrollArmed = false
    private var overscrollSettling = false
    private var lastOverscrollMs = 0L
    private val overscrollFrame = Choreographer.FrameCallback { frameTimeNanos -> stepOverscrollSettle(frameTimeNanos) }

    // PINCH
    private var pinchInitDist = 1.0
    private var pinchInitZoom = 1.0
    private var pinchAnchorContent = Pt.ZERO

    // RULER (transient screen-space straightedge; no model/undo state)
    val ruler = Ruler()
    /** The ruler's colours, pens, fonts, glyph bitmaps and labels, rebuilt only when the palette or density changes. */
    private val rulerChrome = RulerChrome(textMeasurer)
    private var rulerGrabOffset = Pt.ZERO            // ruler.center − grab point, for 1-finger move
    private var rulerXformStartCentroid = Pt.ZERO    // two-finger transform anchors
    private var rulerXformStartFingerAngle = 0.0
    private var rulerXformStartCenter = Pt.ZERO
    private var rulerXformStartRuler = 0.0
    private var rulerRotateSign = 1.0                // +1 if dragging the +direction handle, −1 the other
    // SNAP (per-sample magnet, live for the current stroke only)
    private var snapEngaged = false
    private var snapTopSide = false                  // which long edge the ink is riding
    private var snapRunStartEdge: Pt? = null         // start of the current engaged run, on the edge (viewport)
    private var snapCurrentEdge: Pt? = null          // current point on the edge (viewport)
    private var snapPenViewport: Pt? = null          // actual (unprojected) pen, for readout placement

    // MAGIC WAND (ephemeral "disappearing ink"; no model/undo/cache/save state)
    private var wandMode = false
    private val fadingStrokes = mutableListOf<FadingStroke>()
    private var fadeAlpha = 1.0                       // shared multiplier, 1 = solid, 0 = gone
    private var fading = false                        // true while the fade loop runs
    private var fadeStartMs = 0L
    private val fadeFrame = Choreographer.FrameCallback { t -> stepFade(t) }
    private var fadeTimerRunnable: Runnable? = null

    private class FadingStroke(val stroke: Stroke, val pageIndex: Int)

    // SELECTION
    private val selection = mutableListOf<Selected>()
    private val lassoPoints = mutableListOf<Pt>()

    /** The lasso's shape, object filter and tap-to-select, from the preferences (host-installed). */
    var lassoOptions = com.xnotes.core.tools.LassoOptions()

    /** Where a rectangle lasso was anchored. */
    private var lassoOrigin = Pt.ZERO
    private var bandRect: Rect? = null
    private var moveOrigin = Pt.ZERO
    private var moveOffset = Pt.ZERO

    // SCREENSHOT (drag a rectangle; on release it stays frozen and offers "copy as image")
    /** The capture rectangle in content space: live while dragging (mode == SHOT), then frozen
     *  with its menu showing until copied, dismissed, or the tool changes. */
    var screenshotRect: Rect? = null
        private set
    private var screenshotOrigin = Pt.ZERO

    /**
     * The page a band, lasso or capture drag started on (the page in view when it started between
     * pages): its paper picks the accent the marquee is drawn in. Looked up once per drag rather
     * than on every frame the marquee is drawn.
     */
    private var marqueePageIndex = -1

    // ERASE
    private val eraseRemovals = mutableListOf<Pair<Page, CanvasItem>>()
    /** AREA mode: each touched page's item list snapshotted on first contact this gesture, so the
     *  whole split-and-trim drag undoes/redoes as one [ReplacePageItems] step. */
    private val eraseSnapshots = linkedMapOf<Page, List<CanvasItem>>()
    /** The text markups this erase took off, one [RemoveMarkup] each in the order taken, so undo puts them back in place. */
    private val eraseMarkups = mutableListOf<RemoveMarkup>()
    private var eraserCursor: Pt? = null // viewport pixels
    /** Tool armed just before the eraser was selected, for the "switch back after erasing" option. */
    private var toolBeforeEraser: Tool? = null
    /** Tool armed just before the select tool, for the "switch back after a selection action" option. */
    private var toolBeforeSelect: Tool? = null
    /** Tool armed just before the screenshot tool, to return to after a capture is copied. */
    private var toolBeforeScreenshot: Tool? = null
    /** Tool armed just before the text tool, to return to once an edit is committed. */
    private var toolBeforeText: Tool? = null
    /** Whether the live erase is finger-driven (vs the stylus eraser tip / side button): a finger
     *  erase yields to a two-finger pinch, a stylus erase ignores incidental finger/palm contact. */
    private var erasingWithFinger = false

    // ITEM CLIPBOARD (in-app, for copy/cut/paste/duplicate)
    private val itemClipboard = mutableListOf<CanvasItem>()

    /** Whether the clipboard was filled by a cut. A cut moves the items rather than copying them,
     *  so the first paste puts them back and spends the clipboard; a copy's stays for as long as
     *  the user wants it. */
    private var clipboardFromCut = false
    fun hasClipboardItems(): Boolean = itemClipboard.isNotEmpty()

    // SHAPE
    var shapeConfig: ShapeConfig = ShapeConfig()
    private var pendingShape: ShapeItem? = null
    private var shapePageIndex: Int? = null

    // TAPE
    var tapeConfig: TapeConfig = TapeConfig()
    private var pendingTape: TapeItem? = null
    private var tapePageIndex: Int? = null
    private var tapeDownViewport: Pt = Pt.ZERO

    /** The pull has left a tap's reach, so it lays a strip rather than toggling one. */
    private var tapePulling = false

    // RESIZE
    private var resizeItem: CanvasItem? = null
    private var resizeHandle: HandleId? = null
    private var resizeOldGeom: GeoHandle? = null
    private var resizePageIndex: Int = -1

    // GENERIC TRANSFORM (resize + rotate for any single non-line / multi / mixed selection)
    private var selObb: Obb? = null // the tilting selection box; null when nothing is selected
    private var txItems: List<Selected> = emptyList()
    private var txSnaps: List<GeometrySnapshot> = emptyList()
    private var txStartObb: Obb? = null
    private var txHandle: HandleId? = null // non-null = scale via this box handle; null = rotate
    private var txCenter = Pt.ZERO
    private var txGrabAngle = 0.0
    private var txStartAngle = 0.0

    // LONG-PRESS GRAB
    private val handler by lazy { Handler(Looper.getMainLooper()) } // lazy: see [choreographer]
    private var longPressRunnable: Runnable? = null
    private var longPressStart = Pt.ZERO
    private var longPressContent = Pt.ZERO
    private var longPressCandidate: Selected? = null
    private var longPressLocked: CanvasItem? = null
    private var longPressPrevTool: Tool? = null

    /** The page whose PDF text the armed long press would select, -1 for none. */
    private var longPressTextPage = -1

    /** Where the markup tool pressed, in the viewport, so a lift there counts as a tap. */
    private var markupPressAt: Pt? = null

    // TEXT EDITING
    private var editingText: TextItem? = null
    private var editingIsNew = false
    private var editingOldText = ""
    private var editingPageIndex = -1
    val editingItem: TextItem? get() = editingText
    val editingPage: Int get() = editingPageIndex

    /** The style new text boxes are created with; mirrors the active box while one is open. */
    var textFace: FontFace = TextItem.DEFAULT_FACE
        private set
    var textPointSize: Double = TextItem.DEFAULT_POINT_SIZE
        private set

    /** Bumped for every new edit target; see [EditingField.session]. */
    private var editSession = 0

    /** The colour the next sticky note is made in: the last one picked, so a run of notes match. */
    var nextStickyColor: Rgba = StickyColors.YELLOW

    // TABLE CELL EDITING (a placed table typed into in place; it stays lifted for the whole session)
    private class CellEdit(val table: TableItem, val pageIndex: Int, var row: Int, var col: Int, var before: TableGrid)

    private var cellEdit: CellEdit? = null

    /** Every bound the edited table has had while lifted, so ending the session repairs all of it. */
    private var cellLiftBounds: Rect? = null

    /** The table whose cell is being typed into, or null. */
    val editingTable: TableItem? get() = cellEdit?.table

    /** The cell being typed into (row, column), or null. */
    val editingCell: Pair<Int, Int>? get() = cellEdit?.let { it.row to it.col }

    /** The page of [editingTable], or -1. */
    val editingTablePage: Int get() = cellEdit?.pageIndex ?: -1

    /** Told when the edited cell, the table's structure or its size changes, so its bar can follow. */
    var onTableEditChanged: () -> Unit = {}

    // TABLE COLUMN DRAG (a border of the table being edited, dragged to size the column left of it)
    private var colDragBoundary = -1
    private var colDragStartWidth = 0.0
    private var colDragStartX = 0.0
    private var colDragBefore: TableGrid? = null
    private var colDragMoved = false

    /** A press inside a settled lone sticky note or table: a tap (no drag) opens it for typing. */
    private var selTapEdit: Selected? = null

    /** The pen's still hold armed [longPressRunnable] (vs a finger's). */
    private var longPressPen = false

    // TEXT DRAG-CREATE (drag a rectangle to size a new box; a tap makes a default one)
    private var textDragStart = Pt.ZERO // content space
    private var textDragRect: Rect? = null // content space, for the live preview
    private var textDragPageIndex = -1

    /** Front-buffered wet ink, installed by the host when the device can do it. */
    var frontInk: FrontInk? = null

    /** Where the pen is about to be, so the front buffer can draw up to the nib; null for none. */
    var predictor: StrokePredictor? = null

    /** The latest prediction in viewport, then page-local, coordinates, and how many points it has. */
    private val predX = DoubleArray(PREDICTED_POINTS)
    private val predY = DoubleArray(PREDICTED_POINTS)
    private var predCount = 0

    init {
        state.isLiftedItem = { item ->
            item === editingText || item === cellEdit?.table || frontInk?.holding(item) == true ||
                selection.any { it.item === item }
        }
    }

    val hasSelection: Boolean get() = selection.isNotEmpty()

    fun configFor(t: Tool): ToolConfig = toolConfigs[t] ?: ToolDefaults.configFor(t)

    fun setToolConfig(t: Tool, config: ToolConfig) {
        toolConfigs[t] = config
    }

    fun setTool(t: Tool) {
        if (t == tool) {
            return
        }
        // Remember what to re-arm if the eraser/select/screenshot/text later switches back (the tool it replaced).
        if (t == Tool.ERASER) toolBeforeEraser = tool
        if (t == Tool.SELECT) toolBeforeSelect = tool
        if (t == Tool.SCREENSHOT) toolBeforeScreenshot = tool
        if (t == Tool.TEXT_BOX) toolBeforeText = tool
        commitTextEdit()
        if (t != Tool.TEXT) flowText?.endSession()
        abortGesture()
        clearSelection()
        clearScreenshot()
        eraserCursor = null
        previousTool = tool
        tool = t
        onToolChanged(t)
        requestRender()
    }

    fun rulerVisible(): Boolean = ruler.visible

    /** Toggle the on-screen ruler; on first show it places itself in the current viewport. */
    fun toggleRuler() {
        ruler.visible = !ruler.visible
        if (ruler.visible && !ruler.initialized) {
            ruler.placeDefault(state.viewportW.toDouble(), state.viewportH.toDouble(), state.devicePxPerDp)
        }
        requestRender()
    }

    fun wandEnabled(): Boolean = wandMode

    /**
     * Whether ink from [tool] is held and faded rather than kept: every stroke tool under the wand,
     * and the laser pointer always.
     */
    private fun ephemeral(tool: Tool): Boolean = tool.isEphemeral || (wandMode && tool.isStroke)

    private fun laserArmed(): Boolean = tool == Tool.LASER

    /** Toggle disappearing-ink mode; turning it off vanishes anything currently held or fading. */
    fun toggleWand() {
        wandMode = !wandMode
        if (!wandMode) clearFading()
        requestRender()
    }

    // --- touch entry point ---

    fun onTouch(e: MotionEvent): Boolean {
        predictor?.record(e)
        // Palm rejection reads the pen's down/up and decides on touch-down; a move is left alone
        // (a palm gesture's moves find the mode IDLE and do nothing).
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
                    when (PalmRejection.palmJoin(e, state.devicePxPerDp, pen, palmFirstFingertip)) {
                        // The pen landed beside a resting palm: its stroke starts from its own
                        // pointer exactly as a fresh pen down; the palm stays out of it.
                        PalmJoin.PEN_WRITES -> handleDown(e, e.actionIndex)
                        // A second fingertip beside one ignored only for the pen: pinch / two-finger scroll.
                        PalmJoin.PINCH -> {
                            palmGesture = false
                            palmFirstFingertip = false
                            stopFling()
                            stopOverscrollSettle()
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
                    mode == PointerMode.PAN && fingerPanStart != null && PalmRejection.systemCanceled(e) -> rejectFingerPan()
                    else -> handleUp(e)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                PalmRejection.observePen(pen, e)
                if (palmGesture) {
                    palmGesture = false
                } else {
                    // A finger pan the system cancelled as a palm (caught late) goes back; any other
                    // cancel (a dialog taking focus, a system gesture) leaves the scroll where it is.
                    if (mode == PointerMode.PAN &&
                        PalmRejection.cancelRewindsPan(e, e.findPointerIndex(drawingPointerId))
                    ) {
                        restoreFingerPanStart()
                    }
                    abortGesture()
                    requestRender()
                }
            }
        }
        return true
    }

    /** A hover event, for palm rejection: where the pen is. Told of every hover, before [onHover]. */
    fun onPenHover(e: MotionEvent) = PalmRejection.observeHover(pen, e)

    /** Put the scroll back where the finger pan began; the pan is over either way. */
    private fun restoreFingerPanStart() {
        val start = fingerPanStart ?: return
        fingerPanStart = null
        state.scrollX = start.x
        state.scrollY = start.y
        state.clampScroll()
        onViewChanged()
    }

    /** The panning finger was a palm after all: undo its scroll and drop the gesture, with no tap or glide. */
    private fun rejectFingerPan() {
        restoreFingerPanStart()
        abortGesture()
        requestRender()
    }

    fun onHover(e: MotionEvent): Boolean {
        if (handleHoverAction(e)) return true
        val isEraserPointer = e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        if (tool != Tool.ERASER && !isEraserPointer) return false
        eraserCursor = if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            null
        } else {
            Pt(e.x.toDouble(), e.y.toDouble())
        }
        requestRender()
        return true
    }

    /** Drive the side-button tool (eraser/pan) off the hover stream while the button is held and
     *  "activate during hover" is on, so the pen erases or pans without touching the screen. */
    private fun handleHoverAction(e: MotionEvent): Boolean {
        val buttonNow = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            e.actionMasked != MotionEvent.ACTION_HOVER_EXIT &&
            ((e.buttonState and STYLUS_BUTTON_MASK) != 0 || stylusButtonHeld)
        val want = penButtonHover && buttonNow &&
            (penButtonTool == Tool.ERASER || penButtonTool == Tool.PAN)
        val vx = e.x.toDouble()
        val vy = e.y.toDouble()
        return when {
            want && hoverActionTool == null -> { beginHoverAction(vx, vy); true }
            want && hoverActionTool != null -> { extendHoverAction(vx, vy); true }
            hoverActionTool != null -> { endHoverAction(); true }
            else -> false
        }
    }

    private fun beginHoverAction(vx: Double, vy: Double) {
        hoverActionTool = penButtonTool
        when (penButtonTool) {
            Tool.ERASER -> { clearSelection(); beginErase(vx, vy) }
            Tool.PAN -> beginPan(vx, vy, fromPenButton = true)
            else -> hoverActionTool = null
        }
        requestRender()
    }

    private fun extendHoverAction(vx: Double, vy: Double) {
        when (hoverActionTool) {
            Tool.ERASER -> eraseAt(vx, vy)
            Tool.PAN -> extendPan(vx, vy)
            else -> Unit
        }
    }

    /** End a live hover gesture: commit the erase, or stop the pan (with a flick if it was moving). */
    private fun endHoverAction() {
        when (hoverActionTool) {
            Tool.ERASER -> endErase()
            Tool.PAN -> { mode = PointerMode.IDLE; startPanFling() }
            else -> Unit
        }
        hoverActionTool = null
        requestRender()
    }

    /** Some pens report the side button only on the hovering generic-motion stream
     *  (ACTION_BUTTON_PRESS/RELEASE), never in the touch buttonState. Latch it here; a release
     *  here also ends an in-progress hover gesture even if the pen has not moved. */
    fun onGenericMotion(e: MotionEvent) {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            stylusButtonHeld = (e.buttonState and STYLUS_BUTTON_MASK) != 0
            if (!stylusButtonHeld && hoverActionTool != null) endHoverAction()
        }
    }

    /** Feeder C: Bluetooth/USI pens often deliver the side button only as a KeyEvent, never in any
     *  MotionEvent buttonState. Latch those into the same held flag ([down] true on key-down); a
     *  key-up also ends a live hover gesture. Returns true if the key was a stylus side button, so
     *  the host consumes it. */
    fun onStylusButtonKey(keyCode: Int, down: Boolean): Boolean {
        if (keyCode != KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY &&
            keyCode != KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY &&
            keyCode != KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY &&
            keyCode != KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL &&
            keyCode != VENDOR_HELD_BUTTON_KEYCODE
        ) {
            return false
        }
        stylusButtonHeld = down
        if (!down && hoverActionTool != null) endHoverAction()
        return true
    }

    /**
     * A gesture's first pointer touched down: pointer [index], 0 for an ACTION_DOWN, or the pen's
     * pointer when it lands beside a resting palm (then the palm stays out of the gesture, and the
     * moves, lift and pressure all follow [drawingPointerId]).
     */
    private fun handleDown(e: MotionEvent, index: Int = 0) {
        // Palm rejection before anything else: a palm, or a hand resting while the pen writes, must
        // not end a hover gesture, halt a glide, pan, tap or long-press. The pen's own down returns
        // at once with TOOL.
        val finger = PalmRejection.fingerDecision(
            e, state.devicePxPerDp, pen, state.zoomLocked, zoomLockPan, lockedTwoFingerScroll, fingerDraws, tool.fingerPansWhenOff,
            index,
        )
        palmGesture = finger == FingerDecision.IGNORE
        palmFirstFingertip = palmGesture && PalmRejection.isFingertip(e, index, state.devicePxPerDp)
        fingerPanStart = null
        if (palmGesture) {
            penLeads = false
            mode = PointerMode.IDLE
            return
        }
        if (hoverActionTool != null) endHoverAction() // a hovering side-button gesture yields to contact
        downStoppedFling = flinging // captured before stopping: a tap that only halts a glide must not dismiss
        stopFling() // a new touch halts any in-progress glide
        stopOverscrollSettle() // ...and lets a re-grab take over the elastic mid-spring
        panMayCommitText = false // a fresh gesture; the editing branch below re-arms it if it applies
        markupPressAt = null
        val toolType = e.getToolType(index)
        val vx = e.getX(index).toDouble()
        val vy = e.getY(index).toDouble()
        val content = state.viewportToContent(Pt(vx, vy))
        drawingPointerId = e.getPointerId(index)
        drawingIsStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
        penLeads = StylusProximity.isPen(toolType)

        // Resolve which tool this pointer drives:
        //  - the stylus eraser end, or the held side button, force the eraser/side-button tool;
        //  - a finger pans unless finger-draw is enabled ([finger] says so);
        //  - otherwise the armed tool.
        val fingerPans = finger == FingerDecision.PAN || finger == FingerDecision.HOLD
        val buttonHeld = drawingIsStylus &&
            ((e.buttonState and STYLUS_BUTTON_MASK) != 0 || stylusButtonHeld)
        val effectiveTool: Tool = when {
            toolType == MotionEvent.TOOL_TYPE_ERASER -> Tool.ERASER
            buttonHeld && penButtonTool != null -> penButtonTool!!
            // While something is selected, a press on it grabs it (resize on a handle, move on the
            // body) instead of inking through it: with the pen, and with a finger whether or not
            // fingers draw. A shape just snapped from a held stroke is selected exactly so its
            // handles can be dragged next. Off the selection the press falls through to draw and
            // dismisses the selection (see beginDraw).
            hasSelection && (drawingIsStylus || toolType == MotionEvent.TOOL_TYPE_FINGER) &&
                (tool.isStroke || tool == Tool.SHAPE || tool == Tool.TAPE) && fingerHitsSelection(content) -> Tool.SELECT
            // A finger may grab/resize the ACTIVE selection even when finger-draw is off;
            // off the selection it still pans.
            fingerPans && fingerHitsSelection(content) -> Tool.SELECT
            // A finger otherwise pans instead of drawing/selecting/shaping/erasing (text stays usable by finger).
            fingerPans -> Tool.PAN
            else -> tool
        }
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
        val free = FreePointer.isFree(tool, effectiveTool, isFinger)

        // A press off the box while editing. With a finger we defer: a tap commits (re-arming the
        // prior tool), a drag scrolls the page with the edit kept live (decided in handleUp's PAN
        // branch). A stylus / side-button / eraser press commits now and proceeds as usual. A Text
        // press never spawns a new box on the same gesture (that double-create was a bug).
        // A table being typed into: a column border drags that column with any pointer; a finger
        // defers like the text box (a tap picks the cell it lands on, or ends the edit off the
        // table; a drag scrolls with the edit kept); the pen picks a cell or ends the edit at once.
        val openCell = cellEdit
        if (openCell != null) {
            if (beginColumnDrag(openCell, content)) return
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) {
                panMayCommitText = true
                beginFingerPan(vx, vy)
                return
            }
            val cell = tableCellAt(openCell, content)
            if (cell != null) {
                startCellEdit(openCell.table, openCell.pageIndex, cell.first, cell.second)
                mode = PointerMode.IDLE
                return
            }
            commitTextEdit(restoreTool = true)
            if (effectiveTool == Tool.TEXT_BOX) {
                mode = PointerMode.IDLE
                return
            }
        }
        val openText = editingText
        if (openText != null) {
            // The note's own card around the live field (its padding) is part of the note.
            if (openText.isSticky && pressOnEditingBox(openText, content)) {
                mode = PointerMode.IDLE
                return
            }
            if (toolType == MotionEvent.TOOL_TYPE_FINGER && (effectiveTool == Tool.TEXT_BOX || openText.isSticky)) {
                panMayCommitText = true
                beginFingerPan(vx, vy)
                return
            }
            commitTextEdit(restoreTool = true)
            if (effectiveTool == Tool.TEXT_BOX) {
                mode = PointerMode.IDLE
                return
            }
        }

        // Touching the ruler grabs it before the normal tool dispatch — for the stylus too, so a
        // pen-down ON the body moves it. A pen-down OFF the body falls through to drawing, where the
        // magnet snaps the in-progress stroke to the edge. Its buttons toggle; its body moves it.
        if (ruler.visible) {
            val v = Pt(vx, vy)
            val hi = ruler.hitHandle(v, rulerHandleDist(), (RULER_HANDLE_HIT * state.devicePxPerDp).coerceAtLeast(ruler.handleRadiusPx()))
            if (hi != null && !ruler.lockAngle) {
                rulerRotateSign = if (hi == 0) 1.0 else -1.0
                mode = PointerMode.RULER_ROTATE
                cancelLongPress()
                requestRender()
                return
            }
            val btn = ruler.hitButton(v, (RULER_BTN_HIT * state.devicePxPerDp).coerceAtLeast(ruler.buttonRadiusPx()))
            if (btn != null) {
                when (btn) {
                    RulerButton.LOCK_POS -> ruler.lockPosition = !ruler.lockPosition
                    RulerButton.LOCK_ANGLE -> ruler.lockAngle = !ruler.lockAngle
                }
                mode = PointerMode.IDLE
                requestRender()
                return
            }
            // Move zone: the body, minus an inner margin (as wide as the magnet band) along each edge
            // for the STYLUS — so a pen drawing right along the edge never accidentally grabs the ruler.
            val band = RULER_SNAP_DP * state.devicePxPerDp
            val moveHalf =
                if (toolType == MotionEvent.TOOL_TYPE_STYLUS) (ruler.thicknessPx / 2.0 - band).coerceAtLeast(0.0)
                else ruler.thicknessPx / 2.0
            if (abs(ruler.signedAcross(v)) <= moveHalf) {
                if (ruler.lockPosition) {
                    mode = PointerMode.IDLE // locked: swallow so it neither pans nor draws under the ruler
                } else {
                    mode = PointerMode.RULER_MOVE
                    rulerGrabOffset = ruler.center - v
                }
                requestRender()
                return
            }
        }

        // A PDF text selection's handle drags with any pointer. Otherwise only a pan or a pinch keeps
        // the selection: anything else ends it now, and a tap ends it on release.
        pdfText?.let { text ->
            if (text.press(Pt(vx, vy))) {
                mode = PointerMode.PDF_TEXT
                cancelLongPress()
                return
            }
            if (effectiveTool != Tool.PAN) text.clear()
        }

        when {
            // Zoom lock may hold a single-pointer pan still (two fingers to scroll, or the older
            // "double"/"none" choice). It is still a pan, so a tap and a long press work as they do
            // on an unlocked page, and a second finger pinch-pans.
            effectiveTool == Tool.PAN && isFinger -> beginFingerPan(vx, vy)
            effectiveTool == Tool.PAN -> beginPan(
                vx, vy,
                fromPenButton = buttonHeld && penButtonTool == Tool.PAN,
                held = !PalmRejection.lockedPanMoves(state.zoomLocked, zoomLockPan, lockedTwoFingerScroll, byFinger = false),
            )
            effectiveTool.isStroke -> beginDraw(content, resolvePressure(e, index, toolType), effectiveTool, e.eventTime, Pt(vx, vy))
            effectiveTool == Tool.ERASER -> {
                clearSelection()
                erasingWithFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
                beginErase(vx, vy)
            }
            effectiveTool == Tool.SELECT -> beginSelect(content)
            effectiveTool == Tool.LASSO -> beginLasso(content)
            effectiveTool == Tool.SCREENSHOT -> beginScreenshot(content)
            effectiveTool == Tool.SHAPE -> beginShape(content)
            effectiveTool == Tool.TAPE -> beginTape(content, Pt(vx, vy))
            effectiveTool == Tool.TEXT -> beginTextGesture(content, Pt(vx, vy))
            effectiveTool == Tool.TEXT_BOX -> beginTextBoxGesture(content)
            effectiveTool == Tool.MARKUP -> beginMarkup(content)
            else -> Unit
        }
        // The flow caret owns its own long press (word selection), mid text-drag it is suppressed so
        // a hold-then-drag still sizes a box, and a markup drag held still is just a slow drag.
        if (mode != PointerMode.FLOW_TEXT && mode != PointerMode.TEXT_DRAG && mode != PointerMode.PDF_TEXT) {
            armLongPress(Pt(vx, vy), content, isFinger, free)
        }
    }

    /** The markup tool's press: selects, or marks, the PDF text it then drags over; anywhere else nothing. */
    private fun beginMarkup(content: Pt) {
        val text = pdfText ?: return
        val (page, local) = state.pagePointAt(content) ?: return
        if (state.document.pages.getOrNull(page)?.pdfPage == null) return
        mode = PointerMode.PDF_TEXT
        markupPressAt = state.contentToViewport(content)
        text.beginToolDrag(page, local, mark = configFor(Tool.MARKUP).markupMode != MarkupMode.SELECT)
    }

    private fun handlePointerDown(e: MotionEvent) {
        // A palm, or a finger landing while the pen is near, is left out of the gesture: no pinch,
        // and a long press already armed carries on.
        if (PalmRejection.joiningPointerIgnored(e, state.devicePxPerDp, pen)) return
        cancelLongPress()
        // A second finger turns a PDF text drag into a pinch; the selection stays, a mark in the making goes.
        if (mode == PointerMode.PDF_TEXT) {
            pdfText?.interrupt()
            mode = PointerMode.IDLE
        }
        // A second finger on a ruler being moved twists/translates it instead of pinch-zooming.
        if (mode == PointerMode.RULER_MOVE && e.pointerCount >= 2) {
            beginRulerTransform(e)
            return
        }
        if (mode == PointerMode.RULER_ROTATE) return // handle-drag rotation ignores extra fingers
        if (mode == PointerMode.DRAW && drawingIsStylus) return
        // A finger erase (finger-draw on) yields to a two-finger pinch: commit what was erased so
        // far as one undo step, then start the zoom. A stylus-eraser erase keeps ignoring incidental
        // finger/palm contact, so a resting hand never starts a zoom mid-erase.
        if (mode == PointerMode.ERASE) {
            if (erasingWithFinger && e.pointerCount >= 2) { endErase(); beginPinch(e) }
            return
        }
        if (e.pointerCount >= 2) beginPinch(e)
    }

    private fun handleMove(e: MotionEvent) {
        val idx = e.findPointerIndex(drawingPointerId).coerceAtLeast(0)
        val vx = e.getX(idx).toDouble()
        val vy = e.getY(idx).toDouble()
        val content = state.viewportToContent(Pt(vx, vy))
        maybeCancelLongPress(Pt(vx, vy))
        when (mode) {
            PointerMode.DRAW -> extendDraw(e)
            PointerMode.PAN -> extendPan(vx, vy)
            PointerMode.PINCH -> updatePinch(e)
            PointerMode.RULER_MOVE -> { ruler.center = Pt(vx, vy) + rulerGrabOffset; requestRender() }
            PointerMode.RULER_TRANSFORM -> updateRulerTransform(e)
            PointerMode.RULER_ROTATE -> updateRulerRotate(e)
            PointerMode.ERASE -> {
                // Every sample the frame batched, not just its last: a slow frame must not let the
                // eraser jump over the ink between them.
                for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(idx, h).toDouble(), e.getHistoricalY(idx, h).toDouble())
                eraseAt(vx, vy)
            }
            PointerMode.BAND -> extendBand(content)
            PointerMode.LASSO_DRAW -> extendLasso(content)
            PointerMode.SHOT -> extendScreenshot(content)
            PointerMode.MOVE -> extendMove(content)
            PointerMode.RESIZE -> extendResize(content)
            PointerMode.TRANSFORM -> extendTransform(content)
            PointerMode.SHAPE -> extendShape(content)
            PointerMode.TAPE -> extendTape(Pt(vx, vy), content)
            PointerMode.FLOW_TEXT ->
                // A plain drag with the text tool scrolls the document; only a long-pressed
                // drag extends the selection (dragTo returns true to request the pan handoff).
                if (flowText?.dragTo(content, Pt(vx, vy)) == true) beginPan(vx, vy)
            PointerMode.TEXT_DRAG -> extendTextDrag(content)
            PointerMode.PDF_TEXT -> pdfText?.dragTo(Pt(vx, vy))
            PointerMode.TABLE_COL -> extendColumnDrag(content)
            else -> Unit
        }
    }

    private fun handlePointerUp(e: MotionEvent) {
        // The panning finger lifted while a pointer left out of the gesture (a palm) stays down. A
        // cancelled lift was the palm after all, so the pan goes back; a real one ends the pan as a
        // lift would. Either way the palm left behind does nothing.
        val liftedIsTracked = e.getPointerId(e.actionIndex) == drawingPointerId
        if (mode == PointerMode.PAN && fingerPanStart != null && liftedIsTracked) {
            if (PalmRejection.systemCanceled(e)) rejectFingerPan() else handleUp(e)
            palmGesture = true
            palmFirstFingertip = false
            return
        }
        // The pen lifted out of its own gesture while a palm (or a finger left out of it) stays
        // down: the stroke, erase or drag ends as a lift would, and what stays down stays ignored.
        if (PalmRejection.penLiftEndsGesture(penLeads, liftedIsTracked, mode != PointerMode.IDLE && mode != PointerMode.PINCH)) {
            handleUp(e)
            palmGesture = true
            palmFirstFingertip = false
            return
        }
        if (mode == PointerMode.RULER_TRANSFORM) {
            // One finger lifted: fall back to a single-finger move with whichever finger remains.
            val up = e.actionIndex
            val remaining = (0 until e.pointerCount).firstOrNull { it != up }
            if (remaining != null) {
                drawingPointerId = e.getPointerId(remaining)
                rulerGrabOffset = ruler.center - Pt(e.getX(remaining).toDouble(), e.getY(remaining).toDouble())
                mode = PointerMode.RULER_MOVE
            } else {
                mode = PointerMode.IDLE
            }
            requestRender()
            return
        }
        if (mode == PointerMode.PINCH && e.pointerCount <= 2) endPinch()
    }

    private fun handleUp(e: MotionEvent) {
        cancelLongPress()
        val idx = e.findPointerIndex(drawingPointerId).coerceAtLeast(0)
        val content = state.viewportToContent(Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble()))
        when (mode) {
            PointerMode.DRAW -> endDraw(e)
            PointerMode.PAN -> {
                mode = PointerMode.IDLE
                val upViewport = Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
                val tap = !downStoppedFling && upViewport.distanceTo(panDownViewport) <= TAP_SLOP
                if (tap) onTap?.invoke(upViewport)
                if (panMayCommitText) {
                    // Press off the box while editing: only a tap commits (and re-arms the prior tool);
                    // a drag just scrolled with the edit kept live. Either way settle any elastic/glide.
                    panMayCommitText = false
                    when {
                        tap -> if (!retargetCellAt(content)) commitTextEdit(restoreTool = true)
                        state.overscrollY > 0.0 -> releaseOverscroll()
                        !state.verticalScroll -> endPanPaginated()
                        else -> startPanFling()
                    }
                } else {
                    // A tap ends a PDF text selection and does nothing else.
                    val textCleared = tap && pdfText?.selection != null
                    if (textCleared) pdfText?.clear()
                    // A finger tap (no drag) that didn't just halt a glide, landing off the current
                    // selection, dismisses it — the finger's counterpart to the stylus's empty-tap clear.
                    val linkHandled = !textCleared && tap && state.overscrollY <= 0.0 && tryLinkTap(content)
                    // A tap on a strip of tape peels it back or sticks it down again, whatever the tool.
                    // A tap that puts a selection away does only that, as on the canvas.
                    val tapeToggled = !textCleared && !linkHandled && tap && !hasSelection &&
                        state.overscrollY <= 0.0 && toggleTapeAt(content)
                    // A tap on a sticky note or a table opens it for typing, as in Samsung Notes.
                    val editTapped = !textCleared && !linkHandled && !tapeToggled && tap && state.overscrollY <= 0.0 && beginEditAt(content)
                    when {
                        textCleared || linkHandled || tapeToggled || editTapped -> Unit
                        state.overscrollY > 0.0 -> releaseOverscroll()
                        tap && hasSelection && selectionBoundsContent()?.contains(content) != true -> clearSelection()
                        !state.verticalScroll -> endPanPaginated()
                        else -> startPanFling()
                    }
                }
            }
            PointerMode.PINCH -> endPinch()
            PointerMode.ERASE -> {
                // The lift carries the pen's last positions too; a quick flick ends past the last move.
                for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(idx, h).toDouble(), e.getHistoricalY(idx, h).toDouble())
                eraseAt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
                endErase()
                maybeSwitchBackAfterErase()
            }
            PointerMode.BAND -> endBand()
            PointerMode.LASSO_DRAW -> endLasso()
            PointerMode.SHOT -> endScreenshot()
            PointerMode.MOVE -> endMove(content)
            PointerMode.RESIZE -> endResize()
            PointerMode.TRANSFORM -> endTransform()
            PointerMode.SHAPE -> endShape()
            PointerMode.TAPE -> endTape()
            PointerMode.FLOW_TEXT -> {
                mode = PointerMode.IDLE
                flowText?.release(content, Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble()), e.eventTime)
            }
            PointerMode.TEXT_DRAG -> endTextDrag(content)
            PointerMode.PDF_TEXT -> {
                mode = PointerMode.IDLE
                // A markup tool tap marks nothing; on a markup it opens that markup's menu.
                val pressAt = markupPressAt
                markupPressAt = null
                pdfText?.release()
                val up = Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
                if (pressAt != null && up.distanceTo(pressAt) <= TAP_SLOP) {
                    onTap?.invoke(pressAt)
                    onMarkupTap?.invoke(pressAt, false)
                }
            }
            PointerMode.RULER_MOVE -> { mode = PointerMode.IDLE; requestRender() }
            PointerMode.RULER_TRANSFORM -> { mode = PointerMode.IDLE; requestRender() }
            PointerMode.RULER_ROTATE -> { mode = PointerMode.IDLE; requestRender() }
            PointerMode.TABLE_COL -> endColumnDrag(content)
            else -> Unit
        }
    }

    /** Offer a finger tap at [content] to [onMarkupTap], then as a page + page-space point to [onLinkTap]. */
    private fun tryLinkTap(content: Pt): Boolean {
        if (onMarkupTap?.invoke(state.contentToViewport(content), true) == true) return true
        val cb = onLinkTap ?: return false
        val pageIndex = state.pageIndexAtContent(content) ?: return false
        return cb(pageIndex, state.toPageSpace(pageIndex, content))
    }

    // --- DRAW ---

    private fun beginDraw(content: Pt, pressure: Double, drawTool: Tool, timeMs: Long, downViewport: Pt) {
        val pageIndex = state.pageIndexAtContent(content) ?: return
        // Reaching here with a live selection means the press landed off it (on it the stylus would
        // have grabbed it): dismiss it. A bare tap that only dismissed is dropped in endDraw.
        strokeDismissedSelection = hasSelection
        drawDownViewport = downViewport
        if (hasSelection) clearSelection()
        // Capture content-px → dp scale now, so the speed pen judges gesture speed in
        // zoom- and density-independent dp regardless of how the stroke is later viewed.
        val speedScale = state.zoom / state.devicePxPerDp
        val cfg0 = configFor(drawTool)
        // SCALE off: normalise the stroke to its 100%-zoom size by dividing the spatial
        // dimensions by the draw-time zoom, so it draws at a constant on-screen thickness
        // whatever zoom you are at. Baked into the snapshot, so it is ordinary ink afterwards.
        // A pen with a colour override always draws in its own colour; otherwise it follows the
        // toolbar's active ink colour.
        // Each pen keeps its own colour; one too close to this page's paper to read takes its
        // light or dark counterpart (white ink on a white page writes near-black). PDF pages are
        // left alone: their paper is the PDF's own, which the theme's paper colour does not describe.
        val page = state.document.pages.getOrNull(pageIndex)
        val paper = page?.takeIf { it.pdfPage == null }?.let { state.paperColor(it) }
        val picked = cfg0.colorOverride ?: inkColor
        val drawColor = if (paper == null || drawTool == Tool.HIGHLIGHTER || drawTool.isEphemeral) picked
        else com.xnotes.core.tools.InkContrast.forPaper(picked, paper)
        // Only the laser glows; a pen with an old neon setting writes plain ink. A highlighter on a
        // dark page lightens what it covers, since a multiply has nothing to darken there.
        val darkPaper = paper != null && com.xnotes.core.tools.InkContrast.isDark(paper)
        val cfgBase = cfg0.copy(
            neon = drawTool.isEphemeral,
            highlighterInverse = cfg0.highlighterInverse || (drawTool == Tool.HIGHLIGHTER && darkPaper),
        )
        val cfg = if (cfgBase.scale) {
            cfgBase.copy(rgba = drawColor)
        } else {
            val z = state.zoom
            cfgBase.copy(
                rgba = drawColor,
                baseWidth = cfg0.baseWidth / z,
                dashLength = cfg0.dashLength / z,
                dashGap = cfg0.dashGap / z,
                scale = true,
            )
        }
        val straight = drawTool == Tool.HIGHLIGHTER && cfg.straightLine
        val stroke = Stroke(
            drawTool, brushEnds(cfg, state.zoom), speedScale = speedScale, straight = straight,
            smoothScale = smoothScaleFor(state.zoom) * cfg.stabilisationFactor,
        )
        // Live until pen-up, so lift-time rules (the calligraphy dot swell) can't fire mid-draw.
        stroke.finished = false
        strokeStartTimeMs = timeMs
        // Pen back down: re-solidify the held batch, so a long stroke can't outlive the previous fade.
        if (ephemeral(drawTool)) {
            stopFade()
            fadeAlpha = 1.0
            scheduleFade()
        }
        // Ruler magnet: reset per-stroke engagement, then snap the first sample if it lands in the zone.
        snapEngaged = false
        snapRunStartEdge = null
        snapCurrentEdge = null
        snapPenViewport = null
        val first = state.toPageSpace(pageIndex, state.viewportToContent(magnetize(downViewport)))
        stroke.addSample(Sample(first.x, first.y, pressure)) // first sample: t = 0
        liveStroke = stroke
        strokePageIndex = pageIndex
        mode = PointerMode.DRAW
        // Hold still to snap: an ink pen turns into the shape it drew, the highlighter straightens
        // into a line from where it landed. Never for a line that is straight already, never while
        // the ruler is up (you're drawing straight lines), nor under the wand, whose strokes are
        // ephemeral and must never commit a shape to the page.
        dwellEligible = detectShapes && drawTool.isStroke && !straight &&
            !ruler.visible && !ephemeral(drawTool)
        if (dwellEligible) {
            dwellAnchor = downViewport
            armDwell()
        }
        frontInk?.wet(stroke, pageIndex)
        requestRender()
    }

    private fun extendDraw(e: MotionEvent) {
        val idx = e.findPointerIndex(drawingPointerId)
        if (idx < 0) return
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
            if (drawingIsStylus) e.getPressure(idx).toDouble() else 1.0, e.eventTime, force = false,
        )
        val ink = frontInk
        if (ink != null) {
            predictTip()
            ink.wet(liveStroke, strokePageIndex, predX, predY, predCount)
        }
        requestRender()
    }

    /**
     * Fill [predX], [predY] with where the pen is heading, in the live stroke's page coordinates.
     *
     * Only for freehand ink on the pen. A straight line or a ruler-guided one is already exactly
     * where it will be, and the stroke may be about to hop pages, which a guess must not anticipate.
     */
    private fun predictTip() {
        predCount = 0
        val p = predictor ?: return
        val stroke = liveStroke ?: return
        val pi = strokePageIndex ?: return
        if (!drawingIsStylus || stroke.straight || ruler.visible) return
        if (state.pageRects.getOrNull(pi) == null) return
        val n = p.predict(drawingPointerId, predX, predY)
        for (k in 0 until n) {
            val local = state.toPageSpace(pi, state.viewportToContent(Pt(predX[k], predY[k])))
            predX[k] = local.x
            predY[k] = local.y
        }
        predCount = n
    }

    private fun addStrokePoint(vx: Double, vy: Double, pressure: Double, timeMs: Long, force: Boolean) {
        val stroke = liveStroke ?: return
        val pi = strokePageIndex ?: return
        if (state.pageRects.getOrNull(pi) == null) return
        val vp = magnetize(Pt(vx, vy))
        val content = state.viewportToContent(vp)
        // The pen has walked onto another page: carry the stroke over rather than let it slide
        // under the paper. A straight line is one segment by definition and never hands over.
        if (!stroke.straight) {
            val onPage = state.pageIndexAtContent(content)
            if (onPage != null && onPage != pi) {
                handOverStroke(stroke, pi, onPage, content, pressure, timeMs)
                return
            }
        }
        val local = state.toPageSpace(pi, content)
        if (stroke.straight) {
            // Straight-line mode: the stroke is always pen-down → current point, so the moving
            // endpoint just tracks the pointer (decimation/spacing gates don't apply). Near an axis
            // it snaps flat like a dragged line, unless the ruler is already steering the angle.
            val start = stroke.samples.firstOrNull()
            val end = if (start != null && !ruler.visible) snapAxisEndpoint(Pt(start.x, start.y), local) else local
            stroke.setStraightEnd(Sample(end.x, end.y, pressure.coerceIn(0.0, 1.0), (timeMs - strokeStartTimeMs).toDouble()))
            return
        }
        val last = stroke.samples.lastOrNull()
        // Decimate by on-screen spacing, not content spacing: the gate is MIN_SAMPLE_DIST
        // viewport px (content px ÷ zoom), capped so it never coarsens past the old 1-content-px
        // floor when zoomed out. A fixed content-px gate discarded ever-finer detail the more you
        // zoomed in, so strokes drawn while zoomed faceted into ~zoom-px chords.
        val gate = (MIN_SAMPLE_DIST / state.zoom).coerceAtMost(MIN_SAMPLE_DIST)
        if (force || last == null || Pt(last.x, last.y).manhattanTo(local) >= gate) {
            stroke.addSample(Sample(local.x, local.y, pressure.coerceIn(0.0, 1.0), (timeMs - strokeStartTimeMs).toDouble()))
        }
        // Real movement restarts the hold-to-snap clock; staying within the slop lets it mature,
        // so the snap fires only once the pen has actually come to rest. (Sub-slop jitter is ignored
        // even when a sample is decimated out above, so a trembling-but-still pen still triggers.)
        if (dwellEligible && Pt(vx, vy).distanceTo(dwellAnchor) > SHAPE_DWELL_SLOP) {
            dwellAnchor = Pt(vx, vy)
            armDwell()
        }
    }

    /**
     * Hand the stroke under the pen from one page to the next, so a line drawn across a page
     * boundary keeps going instead of disappearing under the paper.
     *
     * The part already drawn is retired onto the page it belongs to, and a fresh segment picks up
     * on the new one carrying the last point across: that bridging sample lands outside the new
     * page and is clipped there, so the ribbon meets the paper's edge rather than starting a
     * blunt millimetre inside it. Everything the crossing files is one undo step (see [endDraw]).
     */
    private fun handOverStroke(
        stroke: Stroke,
        fromPage: Int,
        toPage: Int,
        content: Pt,
        pressure: Double,
        timeMs: Long,
    ) {
        // A stroke long enough to reach the next page is a line, not a held shape.
        cancelDwell()
        dwellEligible = false
        val last = stroke.samples.lastOrNull()
        retireCrossedSegment(stroke, fromPage)
        val next = Stroke(
            stroke.tool,
            stroke.config,
            speedScale = stroke.speedScale,
            smoothScale = stroke.smoothScale,
        )
        next.finished = false
        if (last != null) {
            val bridge = state.toPageSpace(toPage, state.fromPageSpace(fromPage, Pt(last.x, last.y)))
            next.addSample(Sample(bridge.x, bridge.y, last.pressure, last.t))
        }
        val local = state.toPageSpace(toPage, content)
        next.addSample(
            Sample(local.x, local.y, pressure.coerceIn(0.0, 1.0), (timeMs - strokeStartTimeMs).toDouble()),
        )
        liveStroke = next
        strokePageIndex = toPage
        requestRender()
    }

    /** Put a crossed-off segment where it belongs: ephemeral ink waits for its fade, ordinary ink
     *  joins the page it was drawn on and holds its command back for the one composite undo. */
    private fun retireCrossedSegment(stroke: Stroke, pageIndex: Int) {
        stroke.finished = true
        if (ephemeral(stroke.tool)) {
            fadingStrokes.add(FadingStroke(stroke, pageIndex))
            frontInk?.holdFading(stroke)
            return
        }
        crossedSegments.add(fileStroke(stroke, pageIndex))
    }

    /** Push this stroke's whole edit — every page it crossed plus [last], if any — as one undo
     *  step. A no-op when the stroke neither crossed a page nor committed anything. */
    private fun pushStrokeEdit(last: Command?) {
        val all = if (last == null) crossedSegments.toList() else crossedSegments + last
        crossedSegments.clear()
        if (all.isEmpty()) return
        history.push(if (all.size == 1) all[0] else CompositeCommand(all))
        onContentChanged()
    }

    /** Add a finished stroke to its page and its cache; returns the command that undoes it. */
    private fun fileStroke(stroke: Stroke, pageIndex: Int): Command {
        simplifyForCommit(stroke)
        val page = state.document.pages[pageIndex]
        page.items.add(stroke)
        // The front buffer is still showing this stroke, and drawing it here as well would put two
        // antialiased edges over each other. It goes into the cache once the pad has let go.
        if (frontInk?.hold(stroke, page) != true) state.appendToCache(page, stroke)
        state.document.dirty = true
        return AddItem(page, stroke)
    }

    private fun endDraw(e: MotionEvent) {
        // Drop the dwell timer before the final sample so the lift can't re-arm or fire a snap.
        cancelDwell()
        dwellEligible = false
        val idx = e.findPointerIndex(drawingPointerId).coerceAtLeast(0)
        addStrokePoint(
            e.getX(idx).toDouble(), e.getY(idx).toDouble(),
            if (drawingIsStylus) e.getPressure(idx).toDouble() else 1.0, e.eventTime, force = true,
        )
        // A mid-stroke snap already committed a shape and cleared liveStroke, so this block is
        // skipped and the freehand stroke is intentionally not also committed.
        val stroke = liveStroke
        val pi = strokePageIndex
        val up = Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble())
        if (stroke != null && pi != null && !stroke.isEmpty) {
            // The pen is up: rebuild with lift-time rules on (a dot-sized calligraphy stroke
            // takes the nib's broad face) before the stroke is committed or held for fading.
            stroke.finished = true
            when {
                // A bare tap whose only job was to dismiss the selection: drop the dot it would leave.
                strokeDismissedSelection && up.distanceTo(drawDownViewport) <= TAP_SLOP -> Unit
                ephemeral(stroke.tool) -> {
                    // Disappearing ink: held ephemerally, never committed to the model/undo/cache/save.
                    // A stroke drawn mid-fade cancels the fade and re-solidifies the whole held batch.
                    fadingStrokes.add(FadingStroke(stroke, pi))
                    frontInk?.holdFading(stroke)
                    stopFade()
                    fadeAlpha = 1.0
                    scheduleFade()
                }
                else -> pushStrokeEdit(fileStroke(stroke, pi))
            }
        }
        // Nothing was committed here (a dismissing tap, or ephemeral ink), but a crossing may
        // still have filed segments that need their undo step.
        pushStrokeEdit(null)
        strokeDismissedSelection = false
        liveStroke = null
        strokePageIndex = null
        snapEngaged = false
        snapRunStartEdge = null
        snapCurrentEdge = null
        snapPenViewport = null
        mode = PointerMode.IDLE
        // A mid-stroke snap left a shape selected; now that the gesture is idle, surface its menu.
        if (snappedSelectionPendingMenu) {
            snappedSelectionPendingMenu = false
            refreshSelectionMenu()
        }
        requestRender()
    }

    /** Pen-up sample reduction: like the capture gate, the tolerance is screen-space — viewport
     *  px at the draw zoom (÷ zoom → content px), capped so zoomed-out ink keeps content fidelity.
     *  The stroke's just-built geometry supplies the half-width channel, so pressure/speed width
     *  variation survives the reduction. */
    private fun simplifyForCommit(stroke: Stroke) {
        if (StrokeSimplify.enabled && !stroke.straight) {
            val eps = (SIMPLIFY_EPS / state.zoom).coerceAtMost(SIMPLIFY_EPS)
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

    private fun armDwell() {
        cancelDwell()
        val r = Runnable { onDwellElapsed() }
        dwellRunnable = r
        handler.postDelayed(r, SHAPE_DWELL_MS)
    }

    private fun cancelDwell() {
        dwellRunnable?.let { handler.removeCallbacks(it) }
        dwellRunnable = null
    }

    /** Fired when the pen has held still: snap the live stroke to a shape if it's a confident match. */
    private fun onDwellElapsed() {
        dwellRunnable = null
        if (!dwellEligible) return
        val stroke = liveStroke ?: return
        val pi = strokePageIndex ?: return
        if (stroke.tool == Tool.HIGHLIGHTER) return straightenHighlight(stroke)
        if (stroke.samples.size < SHAPE_MIN_SAMPLES) return // not enough yet; the next move re-arms
        val rec = ShapeRecognizer.recognize(stroke.samples) ?: return // not a shape; the next move re-arms
        commitRecognizedShape(stroke, pi, rec)
    }

    /**
     * The highlighter held still: the mark becomes a straight line from where it landed to the pen,
     * and stays one until the lift, its far end following the pen (flat near an axis), as a
     * highlighter does in Samsung Notes. The line is the stroke itself, so it commits like any
     * other highlight; the ink drawn so far was never filed and simply stops being shown.
     */
    private fun straightenHighlight(stroke: Stroke) {
        val first = stroke.samples.firstOrNull() ?: return
        val last = stroke.samples.lastOrNull() ?: return
        // A press that has not gone anywhere is a dot, not a line; the next move re-arms.
        if (Pt(first.x, first.y).distanceTo(Pt(last.x, last.y)) * state.zoom < SHAPE_DWELL_SLOP * 2) return
        val line = Stroke(stroke.tool, stroke.config, emptyList(), stroke.speedScale, straight = true, smoothScale = stroke.smoothScale)
        line.finished = false
        line.addSample(Sample(first.x, first.y, first.pressure, 0.0))
        val end = if (ruler.visible) Pt(last.x, last.y) else snapAxisEndpoint(Pt(first.x, first.y), Pt(last.x, last.y))
        line.setStraightEnd(Sample(end.x, end.y, last.pressure, last.t))
        liveStrokeField = line
        dwellEligible = false
        cancelDwell()
        onHaptic()
        requestRender()
    }

    /** Replace the (uncommitted) live stroke with a recognized [ShapeItem], as one undoable add. */
    private fun commitRecognizedShape(stroke: Stroke, pageIndex: Int, rec: RecognizedShape) {
        val page = state.document.pages.getOrNull(pageIndex) ?: return
        // As drawn: colour, neon, dash, and a pencil's graphite at the pressure it was drawn at.
        val shape = ShapeItem.snappedFrom(stroke, rec, stroke.config.baseWidth * SHAPE_PEN_PARITY)
        page.items.add(shape)
        state.appendToCache(page, shape)
        history.push(AddItem(page, shape))
        state.document.dirty = true
        // The stroke was never added to the page, so clearing liveStroke makes the live ink preview
        // vanish the instant it snaps; the eventual pen-up in endDraw then commits nothing.
        liveStroke = null
        dwellEligible = false
        cancelDwell()
        // Auto-select the new shape so it can be resized right away. The menu only shows once the
        // gesture settles, so flag it for the pen lift (endDraw) rather than mid-stroke here.
        setSelection(listOf(Selected(pageIndex, shape)))
        snappedSelectionPendingMenu = true
        onContentChanged()
        onHaptic()
        requestRender()
    }

    // --- ERASE ---

    // SCALE off: hold the eraser at a constant on-screen size by shrinking its content-space
    // radius as you zoom in (both the hit-test and the cursor circle derive from this).
    private fun eraserRadius(): Double {
        val cfg = configFor(Tool.ERASER)
        return if (cfg.scale) cfg.baseWidth else cfg.baseWidth / state.zoom
    }

    private fun areaErase(): Boolean = configFor(Tool.ERASER).eraseMode == EraseMode.AREA

    private fun beginErase(vx: Double, vy: Double) {
        eraseRemovals.clear()
        eraseSnapshots.clear()
        eraseMarkups.clear()
        mode = PointerMode.ERASE
        eraserCursor = null // a hover cursor is where the pen was, not where this erase started
        eraseAt(vx, vy)
    }

    /**
     * Erase along the pen's path from where the eraser last was to ([vx], [vy]), in stamps half the
     * eraser's width apart, so the circle sweeps a continuous band however far the pen moved between
     * two events.
     */
    private fun eraseAt(vx: Double, vy: Double) {
        val from = eraserCursor
        eraserCursor = Pt(vx, vy)
        val radius = eraserRadius()
        val step = max(radius * state.zoom * 0.5, 1.0) // viewport px
        val stamps = if (from == null) 1 else ceil(from.distanceTo(Pt(vx, vy)) / step).toInt().coerceIn(1, MAX_ERASE_STAMPS)
        var changed = false
        for (k in 1..stamps) {
            val t = k.toDouble() / stamps
            val x = if (from == null) vx else from.x + (vx - from.x) * t
            val y = if (from == null) vy else from.y + (vy - from.y) * t
            if (eraseStamp(state.viewportToContent(Pt(x, y)), radius)) changed = true
        }
        if (changed) onContentChanged()
        requestRender()
    }

    /** One eraser circle at [content] on every page under it; true if anything came off. */
    private fun eraseStamp(content: Pt, radius: Double): Boolean {
        val eraserBox = Rect(content.x - radius, content.y - radius, radius * 2, radius * 2)
        val area = areaErase()
        var changed = false
        val drawable = state.drawablePageRange()
        for (pi in state.document.pages.indices) {
            if (pi !in drawable) continue // a hidden paginated neighbour can't be erased
            val pr = state.pageRects.getOrNull(pi) ?: continue
            if (!pr.intersects(eraserBox)) continue // skip pages the eraser isn't over
            val page = state.document.pages[pi]
            val local = state.toPageSpace(pi, content)
            val cx = local.x
            val cy = local.y
            val dirty = if (area) eraseAreaFromPage(page, cx, cy, radius)
            else eraseStrokesFromPage(page, cx, cy, radius)
            if (dirty != null) {
                // Repaint only the erased area in place; fall back to a full
                // rebuild only when the page has no live cache yet.
                val rect = dirty.outset(REPAIR_PAD)
                if (!state.repairRegion(page, rect)) state.invalidatePage(page)
                changed = true
            }
            if (eraseMarkupsFromPage(page, cx, cy, radius)) changed = true
        }
        return changed
    }

    /** Takes off [page]'s text markups the eraser circle touches, when the eraser is set to; true if any. */
    private fun eraseMarkupsFromPage(page: Page, cx: Double, cy: Double, radius: Double): Boolean {
        if (page.markups.isEmpty() || !configFor(Tool.ERASER).eraseMarkups) return false
        val ptPerPx = 72.0 / state.document.dpi
        val hit = page.markups.filter { MarkupPainter.touches(it, cx * ptPerPx, cy * ptPerPx, radius * ptPerPx) }
        if (hit.isEmpty()) return false
        for (m in hit) eraseMarkups += RemoveMarkup(page, m).also { it.redo() }
        state.rebakeBackground(page, emptyList())
        return true
    }

    /** STROKE mode: remove every stroke/shape the eraser circle touches. Images and text boxes are
     *  deliberately-placed and protected (delete those via select + delete). Returns the repaint
     *  region, or null if nothing changed. */
    private fun eraseStrokesFromPage(page: Page, cx: Double, cy: Double, radius: Double): Rect? {
        val toRemove = page.items.filter {
            !it.locked && it !is ImageItem && it !is TextItem && it !is TableItem && it !is com.xnotes.core.model.AudioItem &&
                it.intersectsCircle(cx, cy, radius)
        }
        if (toRemove.isEmpty()) return null
        var dirty: Rect? = null
        for (item in toRemove) {
            page.items.remove(item)
            eraseRemovals.add(page to item)
            val b = item.paintBounds()
            dirty = dirty?.union(b) ?: b
        }
        return dirty
    }

    /** AREA mode: replace each touched stroke or shape with the fragments that survive the eraser
     *  circle, spliced in at the original's z-position. Text and images are left untouched. Returns
     *  the repaint region, or null if nothing changed. */
    private fun eraseAreaFromPage(page: Page, cx: Double, cy: Double, radius: Double): Rect? {
        var dirty: Rect? = null
        var i = 0
        while (i < page.items.size) {
            val item = page.items[i]
            val frags: List<CanvasItem>? = if (item.locked) {
                null
            } else {
                when (item) {
                    is Stroke -> item.erasedBy(cx, cy, radius)
                    is ShapeItem -> item.erasedBy(cx, cy, radius)
                    // Tape is never cut in two: whatever touches a strip peels all of it off.
                    is TapeItem -> if (item.intersectsCircle(cx, cy, radius)) emptyList() else null
                    else -> null
                }
            }
            if (frags == null) {
                i++
                continue
            }
            // Snapshot the page's items on first contact this gesture, before mutating it.
            if (!eraseSnapshots.containsKey(page)) eraseSnapshots[page] = page.items.toList()
            val b = item.paintBounds()
            dirty = dirty?.union(b) ?: b
            page.items.removeAt(i)
            page.items.addAll(i, frags)
            i += frags.size // step past the freshly-inserted fragments
        }
        return dirty
    }

    private fun endErase() {
        val cmds = ArrayList<Command>()
        if (areaErase()) {
            // One drag may split/trim many strokes across pages; commit each touched page's
            // net before/after as one undo step.
            eraseSnapshots.mapNotNullTo(cmds) { (page, before) ->
                val after = page.items.toList()
                if (after != before) ReplacePageItems(page, before, after) else null
            }
        } else if (eraseRemovals.isNotEmpty()) {
            cmds += EraseItems(eraseRemovals.toList())
        }
        cmds += eraseMarkups
        if (cmds.isNotEmpty()) {
            history.push(cmds.singleOrNull() ?: CompositeCommand(cmds))
            state.document.dirty = true
            onContentChanged()
        }
        eraseRemovals.clear()
        eraseSnapshots.clear()
        eraseMarkups.clear()
        eraserCursor = null
        mode = PointerMode.IDLE
        requestRender()
    }

    /**
     * "Switch back after erasing": once a toolbar-eraser drag lifts, re-arm the pen/highlighter
     * that was active before the eraser. Only when the armed tool is the eraser (so a stylus-tip or
     * side-button erase, which never changed the armed tool, is left alone) and the remembered tool
     * is a stroke tool (a pen or the highlighter).
     */
    private fun maybeSwitchBackAfterErase() {
        if (tool != Tool.ERASER || !configFor(Tool.ERASER).switchBackAfterErase) return
        val back = toolBeforeEraser ?: return
        if (back.isStroke) setTool(back)
    }

    /**
     * "Switch back after a selection action": once a select-tool action (move, resize, or a menu
     * op like delete/cut/copy/duplicate) completes, re-arm the pen/highlighter that was active
     * before the select tool. Only when the armed tool is the select tool and the remembered tool
     * is a stroke tool; a long-press temporary grab is left to its own restore on deselect.
     */
    private fun maybeSwitchBackAfterSelect() {
        if (longPressPrevTool != null) return
        if (tool != Tool.SELECT || !configFor(Tool.SELECT).switchBackAfterSelect) return
        val back = toolBeforeSelect ?: return
        if (back.isStroke) setTool(back)
    }

    // --- SELECT / BAND ---

    /** The single selected line/arrow whose two endpoints are its own resize handles, or null.
     *  Every other selection (single non-line, multi, mixed) uses the generic box handles. */
    private fun singleEndpointShape(): Selected? {
        val sel = selection.singleOrNull() ?: return null
        val item = sel.item
        return if (item is ShapeItem && item.shape.isEndpointShape) sel else null
    }

    /** Resize handles for the current selection in content space: a single line/arrow's two
     *  endpoint handles, else the eight handles of the oriented selection box. */
    private fun selectionResizeHandles(): List<ResizeHandle> {
        val endpoint = singleEndpointShape()
        if (endpoint != null) return endpointHandles(endpoint)
        return selObb?.let { ResizeMath.obbHandles(it) } ?: emptyList()
    }

    /** A line/arrow's two endpoint handles, mapped from page space into content space. */
    private fun endpointHandles(sel: Selected): List<ResizeHandle> {
        val item = sel.item as? ShapeItem ?: return emptyList()
        if (state.pageRects.getOrNull(sel.pageIndex) == null) return emptyList()
        return listOf(
            ResizeHandle(HandleId.START, state.fromPageSpace(sel.pageIndex, item.start)),
            ResizeHandle(HandleId.END, state.fromPageSpace(sel.pageIndex, item.end)),
        )
    }

    /** Strokes, shapes and images rotate; text doesn't. A mixed selection rotates only when every
     *  member is rotatable. */
    private fun selectionIsRotatable(): Boolean =
        selection.isNotEmpty() && selection.all { it.item is Stroke || it.item is ShapeItem || it.item is ImageItem || it.item is TapeItem }

    /** Rotate-grip centre (content space, no move offset), out past the oriented box's top edge, or
     *  null when the selection can't rotate or is a single line/arrow (reoriented by an endpoint). */
    private fun selectionRotatePoint(): Pt? {
        if (!selectionIsRotatable() || singleEndpointShape() != null) return null
        val obb = selObb ?: return null
        return ResizeMath.obbRotateGrip(obb, OverlayTessellator.rotateArm(state.zoom, chromeDpPx()))
    }

    /** Device px per dp for the selection chrome's sizes (SC 46-49 are in dp). */
    private fun chromeDpPx(): Double = state.devicePxPerDp.coerceAtLeast(1.0)

    /** Touch radius of a selection grip, in viewport px: fingertip-sized at any density. */
    private fun handleHitPx(): Double = HANDLE_HIT * state.devicePxPerDp.coerceAtLeast(1.0)

    /** True when [content] lands on the active selection — a resize or rotate handle, or inside the
     *  oriented box. Lets a finger grab the selection even when finger-draw is off (off the
     *  selection the finger still pans). */
    private fun fingerHitsSelection(content: Pt): Boolean {
        if (selection.isEmpty()) return false
        val tol = handleHitPx() / state.zoom
        selectionRotatePoint()?.let { if (it.distanceTo(content) <= tol) return true }
        if (ResizeMath.hitHandle(selectionResizeHandles(), content, tol) != null) return true
        return selObb?.contains(content) == true
    }

    /** If [content] grabs a resize or rotate handle of the settled selection, begin that gesture
     *  and return true. Handles only; the caller handles an inside-the-bounds move. Shared by the
     *  select and lasso tools so both grab handles the same way. */
    private fun tryGrabSelectionHandle(content: Pt): Boolean {
        if (selection.isEmpty()) return false
        val tol = handleHitPx() / state.zoom
        selectionRotatePoint()?.let {
            if (it.distanceTo(content) <= tol) {
                beginTransform(null, content)
                return true
            }
        }
        val endpoint = singleEndpointShape()
        if (endpoint != null) {
            val id = ResizeMath.hitHandle(endpointHandles(endpoint), content, tol) ?: return false
            beginResize(endpoint, id)
            return true
        }
        val obb = selObb ?: return false
        val id = ResizeMath.hitHandle(ResizeMath.obbHandles(obb), content, tol) ?: return false
        beginTransform(id, content)
        return true
    }

    private fun beginSelect(content: Pt) {
        if (tryGrabSelectionHandle(content)) return
        // Inside the settled selection the press moves it, whatever it landed on. Hit-testing
        // first would re-pick the stroke under the finger, and you meant to drag what is
        // selected, not to select what happens to sit inside it.
        if (selObb?.contains(content) == true) {
            val tapTarget = selection.singleOrNull()?.takeIf { isTypable(it.item) }
            beginMove(content)
            selTapEdit = tapTarget
            return
        }
        val pageIndex = state.pageIndexAtContent(content)
        if (pageIndex != null) {
            val local = state.toPageSpace(pageIndex, content)
            val hit = state.document.pages[pageIndex].items.lastOrNull { !it.locked && it.contains(local) }
            if (hit != null) {
                if (selection.none { it.item === hit }) setSelection(listOf(Selected(pageIndex, hit)))
                beginMove(content)
                return
            }
        }
        clearSelection()
        mode = PointerMode.BAND
        moveOrigin = content
        marqueePageIndex = pageIndex ?: state.currentPageIndex()
        bandRect = Rect.fromPoints(content, content)
    }

    private fun extendBand(content: Pt) {
        bandRect = Rect.fromPoints(moveOrigin, content)
        requestRender()
    }

    private fun endBand() {
        bandRect?.let { band ->
            val drawable = state.drawablePageRange()
            setSelection(
                SelectionMath.bandMembers(state.document.pages, state.pageRects, band) { i, r ->
                    state.fromPageSpaceRect(i, r)
                }.filter { it.pageIndex in drawable }, // hidden paginated neighbours don't select
            )
        }
        bandRect = null
        mode = PointerMode.IDLE
        refreshSelectionMenu()
        requestRender()
    }

    // --- LASSO ---

    private fun beginLasso(content: Pt) {
        // A tap on the settled selection grabs it (a resize/rotate handle, or a move when inside the
        // oriented box) instead of starting a fresh lasso.
        if (tryGrabSelectionHandle(content)) return
        if (selObb?.contains(content) == true) {
            beginMove(content)
            return
        }
        clearSelection()
        lassoPoints.clear()
        lassoPoints.add(content)
        lassoOrigin = content
        marqueePageIndex = state.pageIndexAtContent(content) ?: state.currentPageIndex()
        mode = PointerMode.LASSO_DRAW
    }

    private fun extendLasso(content: Pt) {
        if (lassoOptions.shape == com.xnotes.core.tools.LassoShape.RECTANGLE) {
            // A dragged box, kept as its closed outline so the marquee and the membership test
            // are the freeform lasso's own.
            val o = lassoOrigin
            lassoPoints.clear()
            lassoPoints.add(o)
            lassoPoints.add(Pt(content.x, o.y))
            lassoPoints.add(content)
            lassoPoints.add(Pt(o.x, content.y))
            lassoPoints.add(o)
        } else {
            lassoPoints.add(content)
        }
        requestRender()
    }

    private fun endLasso() {
        val filter = lassoOptions.filter
        val extent = if (lassoPoints.isEmpty()) Rect(0.0, 0.0, 0.0, 0.0) else Rect.bounding(lassoPoints)
        val tap = maxOf(extent.w, extent.h) * state.zoom < TAP_SLOP * state.devicePxPerDp.coerceAtLeast(1.0)
        if (tap) {
            // A tap, not a loop: pick the object under it, as Samsung Notes' lasso does.
            if (lassoOptions.tapSelect && lassoPoints.isNotEmpty()) selectTapped(lassoPoints[0]) else clearSelection()
        } else if (lassoPoints.size >= 3) {
            val drawable = state.drawablePageRange()
            val members = SelectionMath.lassoMembers(state.document.pages, state.pageRects, lassoPoints) { i, p ->
                state.fromPageSpace(i, p)
            }.filter { it.pageIndex in drawable && filter.accepts(it.item) } // hidden paginated neighbours don't select
            if (members.isEmpty()) {
                clearSelection()
            } else {
                setSelection(members)
            }
        } else {
            clearSelection()
        }
        lassoPoints.clear()
        mode = PointerMode.IDLE
        refreshSelectionMenu()
        requestRender()
    }

    /**
     * Select the topmost unlocked object under [content] that the lasso's filter takes, within a
     * fingertip's reach so a thin line can be picked, or put the selection away when there is none.
     */
    private fun selectTapped(content: Pt) {
        val pageIndex = state.pageIndexAtContent(content)
        if (pageIndex == null || state.pageRects.getOrNull(pageIndex) == null) {
            clearSelection()
            return
        }
        val local = state.toPageSpace(pageIndex, content)
        val reach = TAP_REACH_DP * state.devicePxPerDp.coerceAtLeast(1.0) / state.zoom
        val filter = lassoOptions.filter
        val hit = state.document.pages[pageIndex].items.lastOrNull {
            !it.locked && filter.accepts(it) && (it.contains(local) || it.intersectsCircle(local.x, local.y, reach))
        }
        if (hit == null) clearSelection() else setSelection(listOf(Selected(pageIndex, hit)))
    }

    // --- SCREENSHOT ---

    private fun beginScreenshot(content: Pt) {
        clearScreenshot() // drop any previous frozen capture + its menu
        mode = PointerMode.SHOT
        screenshotOrigin = content
        marqueePageIndex = state.pageIndexAtContent(content) ?: state.currentPageIndex()
        screenshotRect = Rect.fromPoints(content, content)
    }

    private fun extendScreenshot(content: Pt) {
        screenshotRect = Rect.fromPoints(screenshotOrigin, content)
        requestRender()
    }

    private fun endScreenshot() {
        mode = PointerMode.IDLE
        val rect = screenshotRect
        // A tap or a sliver isn't a capture: drop it. Otherwise freeze the rect and show its menu.
        if (rect == null || rect.w < SHOT_MIN || rect.h < SHOT_MIN) clearScreenshot()
        else refreshScreenshotMenu()
        requestRender()
    }

    /** Drop the capture rectangle and hide its menu (after a copy, a tool change, or a re-drag). */
    fun clearScreenshot() {
        if (screenshotRect == null) return
        screenshotRect = null
        onScreenshotMenu(null)
        requestRender()
    }

    /** After a capture is copied, return to the previous pen, mirroring the eraser's switch-back. */
    fun switchBackAfterScreenshot() {
        if (tool != Tool.SCREENSHOT) return
        val back = toolBeforeScreenshot ?: return
        if (back.isStroke) setTool(back)
    }

    private fun refreshScreenshotMenu() {
        val rect = screenshotRect
        onScreenshotMenu(if (rect != null && mode == PointerMode.IDLE) screenshotRectViewport(rect) else null)
    }

    private fun screenshotRectViewport(rect: Rect): Rect {
        val tl = state.contentToViewport(rect.topLeft)
        val br = state.contentToViewport(Pt(rect.right, rect.bottom))
        return Rect.fromPoints(tl, br)
    }

    // --- MOVE ---

    private fun beginMove(content: Pt) {
        selTapEdit = null
        mode = PointerMode.MOVE
        moveOrigin = content
        moveOffset = Pt.ZERO
        onSelectionMenu(null) // hide while dragging
    }

    private fun extendMove(content: Pt) {
        moveOffset = content - moveOrigin
        requestRender()
    }

    private fun endMove(content: Pt) {
        // A tap (not a drag) on a lone selected sticky note or table opens it for typing.
        val tapEdit = selTapEdit
        selTapEdit = null
        if (tapEdit != null && (content - moveOrigin).length() * state.zoom <= TAP_SLOP) {
            moveOffset = Pt.ZERO
            mode = PointerMode.IDLE
            if (state.pageRects.getOrNull(tapEdit.pageIndex) != null) {
                editItem(tapEdit.pageIndex, tapEdit.item, state.toPageSpace(tapEdit.pageIndex, content))
            }
            requestRender()
            return
        }
        moveOffset = content - moveOrigin
        val moved = abs(moveOffset.x) > MOVE_EPS || abs(moveOffset.y) > MOVE_EPS
        if (moved) {
            val items = selection.map { it.item }
            // Items hold page-space geometry: rotate the on-screen offset into page space.
            val local = state.vectorToPageSpace(moveOffset)
            for (item in items) item.translate(local.x, local.y)
            selObb = selObb?.translate(moveOffset.x, moveOffset.y)
            val move: Command = MoveItems(items, local.x, local.y)
            val transfer = reassignSelectionPages()
            history.push(if (transfer == null) move else CompositeCommand(listOf(move, transfer)))
            state.document.dirty = true
            // Moved items stay lifted (drawn live in the overlay), so the ink cache — which
            // already excludes them — needs no repair; it is repainted at their final spot
            // when the selection is later cleared.
            onContentChanged()
        }
        moveOffset = Pt.ZERO
        mode = PointerMode.IDLE
        refreshSelectionMenu()
        requestRender()
        if (moved) maybeSwitchBackAfterSelect()
    }

    // --- RESIZE ---

    private fun beginResize(sel: Selected, handle: HandleId) {
        resizeItem = sel.item
        resizeHandle = handle
        resizePageIndex = sel.pageIndex
        resizeOldGeom = (sel.item as Resizable).geometry()
        mode = PointerMode.RESIZE
        onSelectionMenu(null) // hide while resizing
    }

    private fun extendResize(content: Pt) {
        val item = resizeItem ?: return
        val handle = resizeHandle ?: return
        if (state.pageRects.getOrNull(resizePageIndex) == null) return
        val local = state.toPageSpace(resizePageIndex, content)
        when (item) {
            is ImageItem -> item.setGeometry(RectHandle(ResizeMath.resizeImage(item.rect, handle, local)))
            is TextItem -> {
                // Resize against the displayed bounds (grown-to-fit height) so handles track the box.
                val (pos, w, h) = ResizeMath.resizeText(item.pos, item.width, item.bounds().h, handle, local)
                item.setGeometry(TextHandle(pos, w, h))
            }
            is ShapeItem -> {
                val (s, en) = when {
                    item.shape.isEndpointShape -> ResizeMath.resizeOpenShape(item.start, item.end, handle, local)
                    item.shape == ShapeKind.CIRCLE -> ResizeMath.resizeSquareShape(item.start, item.end, handle, local)
                    else -> ResizeMath.resizeClosedShape(item.start, item.end, handle, local)
                }
                item.setGeometry(ShapeHandle(s, en))
            }
        }
        requestRender()
    }

    private fun endResize() {
        val item = resizeItem as? Resizable
        val old = resizeOldGeom
        var changed = false
        if (item != null && old != null) {
            val new = item.geometry()
            if (new != old) {
                changed = true
                history.push(ResizeItem(item, old, new))
                state.document.dirty = true
                // The resized item stays lifted (overlay-drawn); the ink cache it was already
                // lifted out of needs no repair — it is repainted at its new size on deselect.
                onContentChanged()
            }
        }
        resizeItem = null
        resizeHandle = null
        resizeOldGeom = null
        // The endpoint resize (a single line/arrow) reshapes the box; refit it upright.
        selObb = selectionBoundsContent()?.let { Obb.fromAabb(it) }
        mode = PointerMode.IDLE
        refreshSelectionMenu()
        requestRender()
        if (changed) maybeSwitchBackAfterSelect()
    }

    // --- TRANSFORM (generic resize + rotate) ---

    /** Begin a resize ([handle] non-null) or rotate ([handle] null) of the whole selection. Snapshots
     *  every member and the oriented box so each move can restore-then-rebake without drift. */
    private fun beginTransform(handle: HandleId?, content: Pt) {
        txItems = selection.toList()
        txSnaps = txItems.map { it.item.snapshotGeometry() }
        txStartObb = selObb
        txHandle = handle
        if (handle == null) {
            val c = selObb?.center ?: content
            txCenter = c
            txGrabAngle = atan2(content.y - c.y, content.x - c.x)
            txStartAngle = selObb?.angle ?: 0.0
        }
        mode = PointerMode.TRANSFORM
        onSelectionMenu(null) // hide while transforming
    }

    private fun extendTransform(content: Pt) {
        val obb0 = txStartObb ?: return
        // Restore every member to its gesture-start geometry, then bake the current transform so the
        // result is a pure function of the pointer (no per-frame compounding, e.g. of a rotation).
        txItems.forEachIndexed { i, sel -> sel.item.restoreGeometry(txSnaps[i]) }
        val world: Affine
        val handle = txHandle
        if (handle != null) {
            val res = ResizeMath.obbResize(obb0, handle, content, uniformEdges = txItems.any { it.item is ImageItem })
            selObb = res.obb
            world = res.transform
        } else {
            val theta = atan2(content.y - txCenter.y, content.x - txCenter.x) - txGrabAngle
            selObb = obb0.copy(angle = txStartAngle + theta)
            world = Affine.rotateAbout(txCenter, theta)
        }
        // Items hold page-space geometry, so express the content-space transform per page
        // (a translation shift, plus the display rotation when the view is rotated).
        for (sel in txItems) {
            if (state.pageRects.getOrNull(sel.pageIndex) == null) continue
            sel.item.applyTransform(state.affineToPageSpace(sel.pageIndex, world))
        }
        requestRender()
    }

    private fun endTransform() {
        val items = txItems.map { it.item }
        var changed = false
        if (items.isNotEmpty()) {
            val after = items.map { it.snapshotGeometry() }
            if (after != txSnaps) {
                changed = true
                val tx: Command = TransformItems(items, txSnaps, after)
                val transfer = reassignSelectionPages()
                history.push(if (transfer == null) tx else CompositeCommand(listOf(tx, transfer)))
                state.document.dirty = true
                // Members stay lifted (overlay-drawn); the ink cache they were lifted out of needs
                // no repair — it is repainted at their new geometry on deselect.
                onContentChanged()
            }
        }
        txItems = emptyList()
        txSnaps = emptyList()
        txStartObb = null
        txHandle = null
        mode = PointerMode.IDLE
        refreshSelectionMenu()
        requestRender()
        if (changed) maybeSwitchBackAfterSelect()
    }

    // --- SHAPE ---

    private fun beginShape(content: Pt) {
        val pageIndex = state.pageIndexAtContent(content) ?: return
        // Off-selection press with the shape tool: dismiss first (a tap makes no shape; endShape's
        // min-drag gate drops it), so dragging out a new shape also clears the old selection.
        clearSelection()
        val startLocal = state.toPageSpace(pageIndex, content)
        val kind = shapeConfig.shape
        val fill = if (shapeConfig.fill && kind.isClosed) inkColor.scaleAlpha(shapeConfig.fillAlpha) else null
        pendingShape = ShapeItem(
            kind, startLocal, startLocal, inkColor, shapeConfig.strokeWidth * SHAPE_PEN_PARITY, fill,
            shapeConfig.neon, shapeConfig.neonStrength,
            dashed = shapeConfig.dashed, dashLength = shapeConfig.dashLength, dashGap = shapeConfig.dashGap,
        )
        shapePageIndex = pageIndex
        mode = PointerMode.SHAPE
        requestRender()
    }

    private fun extendShape(content: Pt) {
        val shape = pendingShape ?: return
        val pi = shapePageIndex ?: return
        if (state.pageRects.getOrNull(pi) == null) return
        val raw = state.toPageSpace(pi, content)
        shape.end = when {
            // Line/arrow: pin the dragged end flat when it lands near an axis.
            shape.shape.isEndpointShape -> snapAxisEndpoint(shape.start, raw)
            // Circle: keep the box square so it stays a perfect circle.
            shape.shape == ShapeKind.CIRCLE -> squareCorner(shape.start, raw)
            else -> raw
        }
        requestRender()
    }

    /** Constrain a dragged corner [p] to a square box anchored at [anchor] (the perfect-circle shape). */
    private fun squareCorner(anchor: Pt, p: Pt): Pt {
        val side = max(abs(p.x - anchor.x), abs(p.y - anchor.y))
        val sx = if (p.x >= anchor.x) 1.0 else -1.0
        val sy = if (p.y >= anchor.y) 1.0 else -1.0
        return Pt(anchor.x + sx * side, anchor.y + sy * side)
    }

    /** Snap a line/arrow's dragged endpoint to an exactly horizontal or vertical run from [anchor]
     *  when it lands within [SHAPE_AXIS_SNAP_DEG] of one (mirrors the recognizer's axis snap). */
    private fun snapAxisEndpoint(anchor: Pt, p: Pt): Pt {
        val dx = p.x - anchor.x
        val dy = p.y - anchor.y
        if (dx == 0.0 && dy == 0.0) return p
        val snap = Math.toRadians(SHAPE_AXIS_SNAP_DEG)
        val fromHoriz = atan2(abs(dy), abs(dx)) // 0 = horizontal, PI/2 = vertical
        return when {
            fromHoriz <= snap -> Pt(p.x, anchor.y)
            fromHoriz >= Math.PI / 2.0 - snap -> Pt(anchor.x, p.y)
            else -> p
        }
    }

    private fun endShape() {
        val shape = pendingShape
        val pi = shapePageIndex
        if (shape != null && pi != null && shape.start.distanceTo(shape.end) > SHAPE_MIN_DRAG) {
            val page = state.document.pages[pi]
            page.items.add(shape)
            state.appendToCache(page, shape)
            history.push(AddItem(page, shape))
            state.document.dirty = true
            onContentChanged()
        }
        pendingShape = null
        shapePageIndex = null
        mode = PointerMode.IDLE
        requestRender()
    }

    // --- TAPE ---

    /**
     * A press with the tape tool. Nothing is drawn until the pen leaves a tap's reach: a tap peels
     * back (or sticks down) the strip under it, and only a pull lays a new one.
     */
    private fun beginTape(content: Pt, viewport: Pt) {
        clearSelection()
        mode = PointerMode.TAPE
        tapeDownViewport = viewport
        tapePulling = false
        val pageIndex = state.pageIndexAtContent(content)
        if (pageIndex == null) {
            pendingTape = null
            tapePageIndex = null
            return
        }
        val local = state.toPageSpace(pageIndex, content)
        val cfg = tapeConfig
        pendingTape = TapeItem(local, local, cfg.width, cfg.color, cfg.pattern)
        tapePageIndex = pageIndex
    }

    /** The strip follows the pen in a straight line, laid level or plumb when the pull nearly is. */
    private fun extendTape(viewport: Pt, content: Pt) {
        if (!tapePulling && viewport.distanceTo(tapeDownViewport) > TAP_SLOP) tapePulling = true
        val tape = pendingTape ?: return
        val pi = tapePageIndex ?: return
        if (state.pageRects.getOrNull(pi) == null) return
        tape.end = TapeItem.snapAxis(tape.start, state.toPageSpace(pi, content))
        if (tapePulling) requestRender()
    }

    private fun endTape() {
        val tape = pendingTape
        val pi = tapePageIndex
        val pulled = tapePulling
        clearPendingTape()
        mode = PointerMode.IDLE
        if (!pulled) {
            toggleTapeAt(state.viewportToContent(tapeDownViewport))
        } else if (tape != null && pi != null && tape.start.distanceTo(tape.end) >= TAPE_MIN_LENGTH) {
            val page = state.document.pages[pi]
            page.items.add(tape)
            state.appendToCache(page, tape)
            history.push(AddItem(page, tape))
            state.document.dirty = true
            onContentChanged()
        }
        requestRender()
    }

    private fun clearPendingTape() {
        pendingTape = null
        tapePageIndex = null
        tapePulling = false
    }

    /**
     * Peel back, or stick down again, the topmost strip of tape under [content]; false when there is
     * none. Whatever is written on top of the strip does not get in the way: the tap is for the tape.
     */
    fun toggleTapeAt(content: Pt): Boolean {
        val pi = state.pageIndexAtContent(content) ?: return false
        val page = state.document.pages.getOrNull(pi) ?: return false
        val local = state.toPageSpace(pi, content)
        val tape = page.items.lastOrNull { it is TapeItem && it.contains(local) } as? TapeItem ?: return false
        tape.revealed = !tape.revealed
        repairTape(page, tape.paintBounds())
        state.document.dirty = true
        onContentChanged()
        requestRender()
        return true
    }

    /**
     * Peel back ([revealed]) or stick down every strip of tape in the note. Like a single tap it is
     * how the tape is read rather than an edit, so it is saved but not undone. Returns how many
     * strips changed.
     */
    fun setAllTapeRevealed(revealed: Boolean): Int {
        var changed = 0
        for (page in state.document.pages) {
            var dirty: Rect? = null
            for (item in page.items) {
                if (item !is TapeItem || item.revealed == revealed) continue
                item.revealed = revealed
                val b = item.paintBounds()
                dirty = dirty?.union(b) ?: b
                changed++
            }
            dirty?.let { repairTape(page, it) }
        }
        if (changed > 0) {
            state.document.dirty = true
            onContentChanged()
            requestRender()
        }
        return changed
    }

    /** Repaint just the strip's own patch of the page cache, which is all a toggle changes. */
    private fun repairTape(page: Page, region: Rect) {
        if (!state.repairRegion(page, region.outset(REPAIR_PAD))) state.invalidatePage(page)
    }

    // --- TEXT ---

    /**
     * A press with the Text tool (and no edit already open). Tapping an existing legacy
     * box is the text box tool's business; here everything routes to the inline-flow
     * caret (tap = place caret / toggle checkbox / fill empty lines, drag = select).
     */
    private fun beginTextGesture(content: Pt, viewport: Pt) {
        clearSelection()
        mode = PointerMode.FLOW_TEXT
        flowText?.pressAt(content, viewport)
        requestRender()
    }

    /**
     * A press with the Text box tool (and no edit already open). Tapping an existing box
     * edits it; otherwise this begins a tap-or-drag to create a new one ([endTextDrag]
     * decides which).
     */
    private fun beginTextBoxGesture(content: Pt) {
        val pi = state.pageIndexAtContent(content) ?: return
        val local = state.toPageSpace(pi, content)
        val page = state.document.pages[pi]
        val table = page.items.lastOrNull { it is TableItem && !it.locked && it.contains(local) } as? TableItem
        if (table != null) {
            clearSelection()
            mode = PointerMode.IDLE
            val cell = table.cellAt(local) ?: (0 to 0)
            startCellEdit(table, pi, cell.first, cell.second)
            return
        }
        val existing = page.items.lastOrNull { it is TextItem && it.contains(local) } as? TextItem
        if (existing != null) {
            startEditing(existing, pi, isNew = false)
            return
        }
        clearSelection()
        mode = PointerMode.TEXT_DRAG
        textDragPageIndex = pi
        textDragStart = content
        textDragRect = null
        requestRender()
    }

    private fun extendTextDrag(content: Pt) {
        textDragRect = Rect.fromPoints(textDragStart, content)
        requestRender()
    }

    /** Finish a tap-or-drag: a real drag (either axis) sizes the box; a tap makes a default one. */
    private fun endTextDrag(content: Pt) {
        val pi = textDragPageIndex
        textDragRect = null
        mode = PointerMode.IDLE
        if (state.pageRects.getOrNull(pi) == null) return
        val page = state.document.pages[pi]
        val startLocal = state.toPageSpace(pi, textDragStart)
        val rect = Rect.fromPoints(startLocal, state.toPageSpace(pi, content))
        val draggedX = rect.w * state.zoom >= TEXT_DRAG_SLOP
        val draggedY = rect.h * state.zoom >= TEXT_DRAG_SLOP
        // Measured to the paper's right edge, margins included: a box may be dropped in one.
        val pageRight = state.footprint(page).right
        val item = if (draggedX || draggedY) {
            // Use the drawn rectangle for whichever axis was actually dragged.
            val left = if (draggedX) rect.left else startLocal.x
            val maxW = (pageRight - left - 8.0).coerceAtLeast(40.0)
            val w = if (draggedX) rect.w.coerceIn(40.0, maxW) else defaultTextWidth(pageRight, left)
            val h = if (draggedY) rect.h else 0.0
            newTextItem(Pt(left, rect.top), w, h)
        } else {
            newTextItem(startLocal, defaultTextWidth(pageRight, startLocal.x), 0.0)
        }
        startEditing(item, pi, isNew = true)
    }

    private fun defaultTextWidth(pageRight: Double, left: Double): Double =
        (pageRight - left - 14.0).coerceIn(80.0, 300.0)

    private fun newTextItem(pos: Pt, width: Double, height: Double): TextItem =
        TextItem(pos, width, height, "", inkColor, textPointSize, textFace, textMeasurer)

    /** Open the in-place editor on [item] (a new draft, or an existing box being re-edited). */
    private fun startEditing(item: TextItem, pi: Int, isNew: Boolean) {
        editingText = item
        editingIsNew = isNew
        editingOldText = item.text
        editingPageIndex = pi
        editSession++
        // The style bar / next new box follow the box being edited.
        textFace = item.face
        textPointSize = item.pointSize
        // An existing box is lifted out of the cache (isLiftedItem) while edited, so only the field
        // shows it. Repair just its region in place — a full invalidatePage flickered the ink layer.
        if (!isNew) repairTextRegion(state.document.pages[pi], item)
        onTextEditStart(editingField())
        requestRender()
    }

    /** Keep the model in sync with the live editor field (for auto-grow / commit). */
    fun updateEditingText(text: String) {
        val cell = cellEdit
        if (cell != null) {
            val before = cell.table.bounds()
            cell.table.grid = cell.table.grid.withCellText(cell.row, cell.col, text)
            val now = cell.table.paintBounds()
            cellLiftBounds = cellLiftBounds?.union(now) ?: now
            // A row that grew pushes the rows under it down, so the canvas copy must follow.
            if (cell.table.bounds() != before) onTableEditChanged()
            requestRender()
            return
        }
        val item = editingText ?: return
        item.text = text
        // A note's card is painted by the canvas under the field, and grows with the text.
        if (item.isSticky) requestRender()
    }

    /**
     * Pan the document while a text edit stays open: a one-finger drag over the editor scrolls the
     * page (the overlay re-tracks the box through [editingField]) instead of scrolling the field's
     * own text. [dxFinger]/[dyFinger] are viewport-space finger deltas; the content follows the
     * finger, as in [extendPan]. A plain drag with no fling/overscroll.
     */
    fun panWhileEditing(dxFinger: Double, dyFinger: Double) {
        if (editingText == null) return
        state.scrollBy(-dxFinger, -dyFinger)
        onViewChanged()
        requestRender()
    }

    /** Current on-screen geometry of the editor field, or null when not editing. */
    fun editingField(): EditingField? {
        cellEdit?.let { return cellField(it) }
        val item = editingText ?: return null
        if (state.pageRects.getOrNull(editingPageIndex) == null) return null
        // Anchor at the text's page-space top-left corner (inside a note's padding); a rotated view
        // spins the overlay around that anchor (EditingField.rotation) so it stays glued to the text.
        val pad = item.padding()
        val topLeft = state.contentToViewport(state.fromPageSpace(editingPageIndex, Pt(item.pos.x + pad, item.pos.y + pad)))
        return EditingField(
            x = topLeft.x,
            y = topLeft.y,
            width = item.textWidth(),
            height = (item.bounds().h - 2 * pad).coerceAtLeast(0.0),
            fontPx = item.pointSize * com.xnotes.platform.AndroidText.POINTS_TO_PX,
            zoom = state.zoom,
            face = item.face,
            rgba = item.rgba,
            text = item.text,
            // A sticky note's text sits on its card, a plain box's on the page.
            accent = item.fill?.let { state.accentOnPaper(it) } ?: state.pageAccentAt(editingPageIndex),
            rotation = state.rotationDeg,
            card = item.isSticky,
            session = editSession,
        )
    }

    /** The live field over cell [e]: its text box inside the padding, in the table's type. */
    private fun cellField(e: CellEdit): EditingField? {
        if (state.pageRects.getOrNull(e.pageIndex) == null) return null
        val g = e.table.grid
        if (e.row !in 0 until g.rows || e.col !in 0 until g.cols) return null
        val box = e.table.cellTextBounds(e.row, e.col)
        val topLeft = state.contentToViewport(state.fromPageSpace(e.pageIndex, box.topLeft))
        return EditingField(
            x = topLeft.x,
            y = topLeft.y,
            width = box.w,
            height = box.h,
            fontPx = g.pointSize * com.xnotes.platform.AndroidText.POINTS_TO_PX,
            zoom = state.zoom,
            face = g.face,
            rgba = g.textColor,
            text = g.cell(e.row, e.col).text,
            accent = state.pageAccentAt(e.pageIndex),
            rotation = state.rotationDeg,
            card = true,
            cell = true,
            bold = g.fontFor(e.row).bold,
            session = editSession,
        )
    }

    /**
     * Commit (tap outside / Escape / done / tool switch) using the model's current text.
     * An empty box is *deleted* (a new draft is simply dropped; an existing box is removed);
     * a box with content is kept and its change recorded. The single source of truth for
     * ending an edit — the field no longer commits itself, so there is no double-commit.
     */
    fun commitTextEdit(finalText: String? = null, restoreTool: Boolean = false) {
        if (cellEdit != null) {
            finalText?.let { updateEditingText(it) }
            endCellEdit()
            if (restoreTool) restoreToolAfterText()
            return
        }
        val item = editingText ?: return
        finalText?.let { item.text = it }
        val pi = editingPageIndex
        val page = state.document.pages.getOrNull(pi)
        // A sticky note is a visible object even with nothing written on it, so it is kept.
        val empty = item.text.trim().isEmpty() && !item.isSticky
        if (editingIsNew) {
            if (!empty && page != null) {
                page.items.add(item)
                history.push(AddItem(page, item))
                state.document.dirty = true
                onContentChanged()
            }
        } else if (page != null) {
            if (empty) {
                page.items.remove(item)
                history.push(EraseItems(listOf(page to item)))
                state.document.dirty = true
                onContentChanged()
            } else if (item.text != editingOldText) {
                history.push(EditText(item, editingOldText, item.text))
                state.document.dirty = true
                onContentChanged()
            }
        }
        editingText = null
        editingIsNew = false
        editingPageIndex = -1
        editingOldText = ""
        // Repaint just the box's region in place (it is now unlifted, so it bakes back in) rather
        // than rebuilding the whole page — the same smart path selection uses, so no ink flicker.
        page?.let { repairTextRegion(it, item) }
        onTextEditEnd()
        requestRender()
        // Finishing an edit (tap outside / Escape / Back) re-arms whatever tool the text tool replaced,
        // so the pen/highlighter/pan is back without a manual switch. Skipped when the commit is itself
        // a tool switch (setTool passes restoreTool = false), which already arms the chosen tool.
        if (restoreTool) restoreToolAfterText()
    }

    /** Re-arm the tool the text box tool replaced, once an edit it opened is finished. */
    private fun restoreToolAfterText() {
        val back = toolBeforeText
        toolBeforeText = null
        if (back != null && back != Tool.TEXT_BOX && tool == Tool.TEXT_BOX) setTool(back)
    }

    /**
     * Repaint only the text box's region of the ink cache in place — the eraser/selection smart
     * path ([CanvasState.repairRegion]) — instead of a full-page rebuild, which flickered the whole
     * ink layer on every edit start/commit. The box is excluded while lifted (editing) and painted
     * back once unlifted, per isLiftedItem. Falls back to a rebuild only when there is no live cache.
     */
    private fun repairTextRegion(page: Page, box: TextItem) {
        if (!state.repairRegion(page, box.paintBounds().outset(REPAIR_PAD))) state.invalidatePage(page)
    }

    // --- text styling (driven by the floating style bar + the toolbar colour swatches) ---

    /** The text box currently being edited, or the lone selected one — what the style bar targets. */
    fun activeTextItem(): TextItem? = editingText ?: (selection.singleOrNull()?.item as? TextItem)

    /** Where to anchor the style bar and the box's current style, or null when no box is active. */
    fun computeTextBar(): TextBar? {
        val editing = editingText
        if (editing != null) {
            val f = editingField() ?: return null
            // Anchored to the whole box (a note's card, not just the text inside its padding).
            val pad = editing.padding() * f.zoom
            val rect = Rect(f.x - pad, f.y - pad, f.width * f.zoom + 2 * pad, f.height * f.zoom + 2 * pad)
            return TextBar(rect, editing.face, editing.pointSize, editing = true, fill = editing.fill)
        }
        if (mode != PointerMode.IDLE) return null
        val sel = selection.singleOrNull()?.item as? TextItem ?: return null
        val rect = selectionBoundsViewport() ?: return null
        return TextBar(rect, sel.face, sel.pointSize, editing = false, fill = sel.fill)
    }

    fun setTextFace(face: FontFace) {
        textFace = face
        restyleActive { it.face = face }
    }

    fun setTextPointSize(size: Double) {
        val s = size.coerceIn(TEXT_MIN_PT, TEXT_MAX_PT)
        textPointSize = s
        restyleActive { it.pointSize = s }
    }

    /** Set the ink colour (for new strokes/boxes) and recolour the active text box, if any. */
    fun pickInk(c: Rgba) {
        inkColor = c
        restyleActive { it.rgba = c }
    }

    /**
     * Apply a style change to the active text box. A *new draft* mutates directly (its final
     * style is captured by the AddItem on commit); a committed/selected box records a [RestyleText]
     * so it is undoable. Both editing and selected boxes are lifted, so a render shows the change.
     */
    private inline fun restyleActive(mutate: (TextItem) -> Unit) {
        val item = activeTextItem() ?: return
        val isDraft = item === editingText && editingIsNew
        val old = TextStyle.of(item)
        mutate(item)
        if (!isDraft && TextStyle.of(item) != old) {
            history.push(RestyleText(item, old, TextStyle.of(item)))
            state.document.dirty = true
            onContentChanged()
        }
        if (item === editingText) {
            onTextEditStart(editingField()) // refresh the live field's metrics/colour
        } else {
            refreshSelectionMenu() // a size change moved the box; re-anchor its menu
        }
        requestRender()
    }

    // --- LONG-PRESS GRAB ---

    private fun armLongPress(viewport: Pt, content: Pt, isFinger: Boolean, free: Boolean) {
        cancelLongPress()
        // Long press (grab an item, select PDF text, or the context menu on empty space) is a finger
        // gesture. A stylus or mouse only has it when free, under the PAN tool, where it mirrors the
        // finger; otherwise it draws, so resting it never grabs anything. The pen keeps one thing:
        // held perfectly still on bare paper it opens the context menu ([armPenHold]).
        if (!isFinger && !free) {
            armPenHold(viewport, content)
            return
        }
        val grabEligible = tool.isStroke || tool == Tool.PAN || tool == Tool.SELECT ||
            tool == Tool.LASSO || tool == Tool.SHAPE || tool == Tool.TEXT || tool == Tool.TEXT_BOX || tool == Tool.TAPE
        val pageIndex = state.pageIndexAtContent(content)
        val hit = if (pageIndex != null) {
            val local = state.toPageSpace(pageIndex, content)
            state.document.pages[pageIndex].items.lastOrNull { it.contains(local) }
        } else {
            null
        }
        longPressStart = viewport
        longPressContent = content
        // A locked item cannot be picked up, so a held finger offers to release it instead. That is
        // the only way back: it is out of reach of the band, the lasso and every tap.
        longPressLocked = hit?.takeIf { it.locked }
        longPressCandidate =
            if (grabEligible && hit != null && !hit.locked) Selected(pageIndex!!, hit) else null
        // An item on top wins; a free pointer on the bare PDF selects its text.
        val text = pdfText
        if (free && text != null && hit == null && pageIndex != null && state.document.pages[pageIndex].pdfPage != null) {
            longPressTextPage = pageIndex
            text.prefetch(pageIndex)
        }
        // Arm to grab an item, to unlock one, to select text, or (on empty space) to open the context
        // menu, whatever the tool: Samsung Notes answers a held finger on bare paper in every mode,
        // and so must this, clipboard or no clipboard (it has more than Paste on it now).
        val showEmptyMenu = hit == null && emptyMenuAllowed()
        if (longPressCandidate == null && longPressLocked == null && longPressTextPage < 0 && !showEmptyMenu) return
        val r = Runnable { triggerLongPress() }
        longPressRunnable = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    /**
     * Whether a still hold on bare paper in the current gesture may become the context menu: on a
     * pan, a stroke or dot not yet travelled, a band, a lasso, a capture or a shape not yet dragged,
     * but never mid-erase, on a selection grip or the ruler, or while a laser/wand stroke is held.
     */
    private fun emptyMenuAllowed(): Boolean = when (mode) {
        PointerMode.IDLE, PointerMode.PAN, PointerMode.BAND, PointerMode.LASSO_DRAW, PointerMode.SHOT, PointerMode.SHAPE -> true
        PointerMode.TAPE -> !tapePulling
        PointerMode.DRAW -> liveStroke?.let { !ephemeral(it.tool) } ?: true
        else -> false
    }

    /**
     * The S Pen's half of the context menu: pressed and held without moving on bare paper, before
     * any ink has travelled, the pen opens the same menu a held finger does, and the dot it began is
     * dropped. It lives beside the finger's hold rather than in the drawing code: [handleMove]'s
     * [maybeCancelLongPress] disarms it the moment the nib travels past [LONG_PRESS_SLOP], and the
     * hold-to-shape timer can never get there first on a dot (it wants [SHAPE_MIN_SAMPLES] samples
     * before it recognizes anything). Over an image, a text box, a note or a table the pen writes,
     * and a side-button pan is the pen moving the page, so neither arms it.
     */
    private fun armPenHold(viewport: Pt, content: Pt) {
        if (!drawingIsStylus || mode == PointerMode.IDLE || mode == PointerMode.PAN || !emptyMenuAllowed()) return
        val pageIndex = state.pageIndexAtContent(content) ?: return
        val local = state.toPageSpace(pageIndex, content)
        val onObject = state.document.pages[pageIndex].items.any {
            (it is ImageItem || it is TextItem || it is TableItem) && it.contains(local)
        }
        if (onObject) return
        longPressStart = viewport
        longPressContent = content
        longPressPen = true
        val r = Runnable { triggerLongPress() }
        longPressRunnable = r
        handler.postDelayed(r, PEN_HOLD_MS)
    }

    private fun maybeCancelLongPress(viewport: Pt) {
        if (longPressRunnable != null && viewport.distanceTo(longPressStart) > LONG_PRESS_SLOP) cancelLongPress()
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        longPressCandidate = null
        longPressLocked = null
        longPressTextPage = -1
        longPressPen = false
    }

    private fun triggerLongPress() {
        longPressRunnable = null
        longPressPen = false
        val candidate = longPressCandidate
        longPressCandidate = null
        val locked = longPressLocked
        val textPage = longPressTextPage
        longPressTextPage = -1
        // Abort the in-progress gesture (keep eraser removals); commit any text edit. The dot a held
        // pen or drawing finger began is dropped with the live stroke.
        commitTextEdit()
        cancelDwell()
        dwellEligible = false
        if (mode == PointerMode.SHOT) {
            screenshotRect = null
            onScreenshotMenu(null)
        }
        liveStroke = null
        strokePageIndex = null
        pendingShape = null
        shapePageIndex = null
        clearPendingTape()
        bandRect = null
        lassoPoints.clear()
        if (candidate != null) {
            // Grab the item: switch to select, select it, and start a move.
            longPressPrevTool = tool
            tool = Tool.SELECT
            onToolChanged(tool)
            setSelection(listOf(candidate))
            beginMove(state.viewportToContent(longPressStart))
        } else if (textPage >= 0 && pdfText != null) {
            // Select the word under the press, which may drag on to extend it; no text there means
            // empty space, so the paste menu as before.
            mode = PointerMode.PDF_TEXT
            val start = longPressStart
            val content = longPressContent
            pdfText?.longPress(textPage, state.toPageSpace(textPage, content)) {
                onHaptic()
                onContextMenu(start, content, null)
            }
        } else {
            // Empty space, or a locked item: open the context menu at the press point.
            mode = PointerMode.IDLE
            onHaptic()
            onContextMenu(longPressStart, longPressContent, locked)
        }
        longPressLocked = null
        requestRender()
    }

    // --- sticky notes and tables: opening them for typing ---

    /** A sticky note or a table: the objects a tap opens for typing rather than for a move. */
    private fun isTypable(item: CanvasItem): Boolean = (item is TextItem && item.isSticky) || item is TableItem

    /**
     * Open [item] on page [pi] for typing: a text box or note gets the live field, a table the cell
     * under [local] (page space; its first cell when null). False for anything else.
     */
    private fun editItem(pi: Int, item: CanvasItem, local: Pt?): Boolean {
        if (item.locked) return false
        return when (item) {
            is TextItem -> {
                clearSelection()
                startEditing(item, pi, isNew = false)
                true
            }
            is TableItem -> {
                clearSelection()
                val cell = local?.let { item.cellAt(it) } ?: (0 to 0)
                startCellEdit(item, pi, cell.first, cell.second)
                true
            }
            else -> false
        }
    }

    /** A finger tap at [content]: open the topmost sticky note or table there, if there is one. */
    private fun beginEditAt(content: Pt): Boolean {
        val pi = state.pageIndexAtContent(content) ?: return false
        if (pi !in state.drawablePageRange()) return false
        val local = state.toPageSpace(pi, content)
        // The topmost *typable* object, so ink written over a note does not hide the note.
        val hit = state.document.pages[pi].items.lastOrNull { !it.locked && isTypable(it) && it.contains(local) } ?: return false
        return editItem(pi, hit, local)
    }

    /** Whether [content] lands on the open text box [item] (a note's card around its field). */
    private fun pressOnEditingBox(item: TextItem, content: Pt): Boolean {
        if (state.pageRects.getOrNull(editingPageIndex) == null) return false
        return item.bounds().contains(state.toPageSpace(editingPageIndex, content))
    }

    /** The cell of [e]'s table under [content], or null when the press is off that table. */
    private fun tableCellAt(e: CellEdit, content: Pt): Pair<Int, Int>? {
        if (state.pageRects.getOrNull(e.pageIndex) == null) return null
        return e.table.cellAt(state.toPageSpace(e.pageIndex, content))
    }

    /** A tap while a cell is open: move to the cell it landed on. False when it is off the table. */
    private fun retargetCellAt(content: Pt): Boolean {
        val e = cellEdit ?: return false
        val cell = tableCellAt(e, content) ?: return false
        startCellEdit(e.table, e.pageIndex, cell.first, cell.second)
        return true
    }

    /** Open the selected sticky note, text box or table for typing (the selection bar's "Edit"). */
    fun editSelection(): Boolean {
        val sel = selection.singleOrNull() ?: return false
        if (sel.item !is TextItem && sel.item !is TableItem) return false
        return editItem(sel.pageIndex, sel.item, null)
    }

    /** The lone selected sticky note, or null: what a note's colour control acts on. */
    fun selectedSticky(): TextItem? = (selection.singleOrNull()?.item as? TextItem)?.takeIf { it.isSticky }

    /** The lone selected table, or null. */
    fun selectedTable(): TableItem? = selection.singleOrNull()?.item as? TableItem

    /** The lone selected item when it is a text box or a sticky note, for the selection bar's Edit. */
    fun selectedTextBox(): TextItem? = selection.singleOrNull()?.item as? TextItem

    /** Recolour the active sticky note (open or selected) and make it the colour the next one gets. */
    fun setStickyColor(c: Rgba) {
        nextStickyColor = c
        restyleActive { if (it.isSticky) it.fill = c }
    }

    // --- table cell editing ---

    /**
     * Type into cell ([row], [col]) of [table] on page [pi]. Moving between cells of the same table
     * commits the cell being left (one undo step per cell) and keeps the table lifted, so its rows
     * can grow live under the field; anything else being edited is committed first.
     */
    fun startCellEdit(table: TableItem, pi: Int, row: Int, col: Int) {
        val g = table.grid
        if (g.rows == 0 || g.cols == 0) return
        val r = row.coerceIn(0, g.rows - 1)
        val c = col.coerceIn(0, g.cols - 1)
        val open = cellEdit
        if (open != null && open.table === table) {
            commitCellText(open)
            open.row = r
            open.col = c
        } else {
            commitTextEdit()
            val page = state.document.pages.getOrNull(pi) ?: return
            cellEdit = CellEdit(table, pi, r, c, table.grid)
            val b = table.paintBounds()
            cellLiftBounds = b
            // Lifted out of the page cache from here on: repaint its region without it.
            if (!state.repairRegion(page, b.outset(REPAIR_PAD))) state.invalidatePage(page)
        }
        editSession++
        onTextEditStart(editingField())
        onTableEditChanged()
        requestRender()
    }

    /** Record what was typed into the open cell since it opened, as one undo step. */
    private fun commitCellText(e: CellEdit) {
        val after = e.table.grid
        if (after != e.before) {
            history.push(TableEdit(e.table, e.before, after))
            state.document.dirty = true
            onContentChanged()
        }
        e.before = after
    }

    /** Close the cell editor, committing its text, and put the table back into the page cache. */
    private fun endCellEdit() {
        val e = cellEdit ?: return
        commitCellText(e)
        cellEdit = null
        val now = e.table.paintBounds()
        val region = cellLiftBounds?.union(now) ?: now
        cellLiftBounds = null
        state.document.pages.getOrNull(e.pageIndex)?.let { page ->
            if (!state.repairRegion(page, region.outset(REPAIR_PAD))) state.invalidatePage(page)
        }
        onTextEditEnd()
        onTableEditChanged()
        requestRender()
    }

    /** Tab / Enter in a cell: the next cell (the previous one [backward]); past the last, a new row. */
    fun tableAdvanceCell(backward: Boolean) {
        val e = cellEdit ?: return
        val g = e.table.grid
        val next = e.row * g.cols + e.col + if (backward) -1 else 1
        when {
            next < 0 -> return
            next >= g.rows * g.cols -> editTable { grid, _, _ -> Triple(grid.insertRow(grid.rows), grid.rows, 0) }
            else -> startCellEdit(e.table, e.pageIndex, next / g.cols, next % g.cols)
        }
    }

    /**
     * Apply a structural edit to the open table (or, with no cell open, the lone selected one) as one
     * undo step: [op] maps the grid and the open cell to the new grid and the cell to continue in.
     */
    private fun editTable(op: (TableGrid, Int, Int) -> Triple<TableGrid, Int, Int>?) {
        val e = cellEdit
        if (e != null) {
            commitCellText(e)
            val before = e.table.grid
            val (after, r, c) = op(before, e.row, e.col) ?: return
            if (after != before) {
                e.table.grid = after
                history.push(TableEdit(e.table, before, after))
                state.document.dirty = true
                val now = e.table.paintBounds()
                cellLiftBounds = cellLiftBounds?.union(now) ?: now
                onContentChanged()
            }
            e.before = e.table.grid
            startCellEdit(e.table, e.pageIndex, r, c)
            return
        }
        val table = selectedTable() ?: return
        val before = table.grid
        val (after, _, _) = op(before, before.rows - 1, before.cols - 1) ?: return
        if (after == before) return
        table.grid = after
        history.push(TableEdit(table, before, after))
        state.document.dirty = true
        // Still lifted, so nothing to repair; the box around it is what changed.
        selObb = selectionBoundsContent()?.let { Obb.fromAabb(it) }
        onContentChanged()
        onTableEditChanged()
        refreshSelectionMenu()
        requestRender()
    }

    /** A row above or below the open cell (at the end of a selected table). */
    fun tableInsertRow(below: Boolean) = editTable { g, r, c ->
        val at = if (below) r + 1 else r
        Triple(g.insertRow(at), at, c)
    }

    /** A column left or right of the open cell (at the end of a selected table). */
    fun tableInsertColumn(right: Boolean) = editTable { g, r, c ->
        val at = if (right) c + 1 else c
        Triple(g.insertColumn(at), r, at)
    }

    /** Remove the open cell's row; the last row left is the whole table. */
    fun tableDeleteRow() {
        val g = cellEdit?.table?.grid ?: selectedTable()?.grid ?: return
        if (g.rows <= 1) return tableDelete()
        editTable { grid, r, c -> Triple(grid.deleteRow(r), r.coerceAtMost(grid.rows - 2), c) }
    }

    /** Remove the open cell's column; the last column left is the whole table. */
    fun tableDeleteColumn() {
        val g = cellEdit?.table?.grid ?: selectedTable()?.grid ?: return
        if (g.cols <= 1) return tableDelete()
        editTable { grid, r, c -> Triple(grid.deleteColumn(c), r, c.coerceAtMost(grid.cols - 2)) }
    }

    /** Shade the open cell (null clears it). */
    fun tableSetCellFill(fill: Rgba?) {
        if (cellEdit == null) return
        editTable { g, r, c -> Triple(g.withCellFill(r, c, fill), r, c) }
    }

    /** Turn the shaded bold header row on or off. */
    fun tableToggleHeader() = editTable { g, r, c ->
        val shade = g.headerFill ?: g.textColor.withAlpha(HEADER_SHADE_ALPHA)
        Triple(g.copy(header = !g.header, headerFill = shade), r, c)
    }

    /** The open cell's table's look, for its bar: (grid, open row, open column), or null. */
    fun tableUnderEdit(): Triple<TableGrid, Int, Int>? = cellEdit?.let { Triple(it.table.grid, it.row, it.col) }

    /** Delete the open (or selected) table, as one undo step after any text typed into it. */
    fun tableDelete() {
        val e = cellEdit
        if (e == null) {
            if (selectedTable() != null) deleteSelection()
            return
        }
        commitCellText(e)
        cellEdit = null
        val page = state.document.pages.getOrNull(e.pageIndex)
        if (page != null && page.items.remove(e.table)) {
            history.push(EraseItems(listOf(page to e.table)))
            state.document.dirty = true
            val now = e.table.paintBounds()
            val region = cellLiftBounds?.union(now) ?: now
            if (!state.repairRegion(page, region.outset(REPAIR_PAD))) state.invalidatePage(page)
            onContentChanged()
        }
        cellLiftBounds = null
        onTextEditEnd()
        onTableEditChanged()
        requestRender()
    }

    /** A press on a column border of the table being typed into: drag that column wider or narrower. */
    private fun beginColumnDrag(e: CellEdit, content: Pt): Boolean {
        if (state.pageRects.getOrNull(e.pageIndex) == null) return false
        val local = state.toPageSpace(e.pageIndex, content)
        val rel = Pt(local.x - e.table.pos.x, local.y - e.table.pos.y)
        val tol = COL_GRAB_DP * state.devicePxPerDp / state.zoom
        val boundary = e.table.layout().columnBoundaryAt(rel, tol) ?: return false
        commitCellText(e)
        colDragBoundary = boundary
        colDragStartWidth = e.table.grid.colWidths[boundary - 1]
        colDragStartX = local.x
        colDragBefore = e.table.grid
        colDragMoved = false
        mode = PointerMode.TABLE_COL
        return true
    }

    private fun extendColumnDrag(content: Pt) {
        val e = cellEdit ?: return
        val before = colDragBefore ?: return
        if (state.pageRects.getOrNull(e.pageIndex) == null) return
        val local = state.toPageSpace(e.pageIndex, content)
        // Only a real drag moves the border; a tap near it stays a tap (see endColumnDrag).
        if (!colDragMoved && abs(local.x - colDragStartX) * state.zoom < TAP_SLOP / 2) return
        colDragMoved = true
        e.table.grid = before.withColumnWidth(colDragBoundary - 1, colDragStartWidth + (local.x - colDragStartX))
        val now = e.table.paintBounds()
        cellLiftBounds = cellLiftBounds?.union(now) ?: now
        onTextEditStart(editingField()) // the open cell may have moved or narrowed
        onTableEditChanged()
        requestRender()
    }

    /** Lift a column drag at [up]: a drag is one undo step; a press that never moved was a tap
     *  near the border, so it picks the cell it landed on like any other tap. */
    private fun endColumnDrag(up: Pt?) {
        mode = PointerMode.IDLE
        val e = cellEdit
        val before = colDragBefore
        colDragBefore = null
        colDragBoundary = -1
        if (e == null || before == null) return
        val after = e.table.grid
        if (after != before) {
            history.push(TableEdit(e.table, before, after))
            state.document.dirty = true
            onContentChanged()
        } else if (up != null) {
            retargetCellAt(up)
        }
        e.before = after
        requestRender()
    }

    // --- inserting objects (the Insert menu and the long-press menu) ---

    /**
     * The page and page-space point an inserted object centres on: [atContent] when given (a long
     * press), else the middle of the part of the current page that is on screen.
     */
    private fun placementPoint(atContent: Pt?): Pair<Int, Pt>? {
        if (state.document.pages.isEmpty()) return null
        if (atContent != null) {
            val pi = (state.pageIndexAtContent(atContent) ?: state.currentPageIndex()).coerceIn(0, state.document.pages.lastIndex)
            if (state.pageRects.getOrNull(pi) == null) return null
            return pi to state.toPageSpace(pi, atContent)
        }
        val pi = state.currentPageIndex().coerceIn(0, state.document.pages.lastIndex)
        val pr = state.pageRects.getOrNull(pi) ?: return null
        val v = state.visibleContentRect()
        val l = max(pr.left, v.left)
        val t = max(pr.top, v.top)
        val r = min(pr.right, v.right)
        val b = min(pr.bottom, v.bottom)
        val center = if (r > l && b > t) Pt((l + r) / 2.0, (t + b) / 2.0) else pr.center
        return pi to state.toPageSpace(pi, center)
    }

    /** Clear the decks for an insert: no other edit open, no selection, no flow caret. */
    private fun prepareInsert() {
        commitTextEdit()
        flowText?.endSession()
        abortGesture()
        clearSelection()
        clearScreenshot()
    }

    /** A left/top edge that keeps a [size]-long object inside [lo]..[hi] with a small inset, centred on [center]. */
    private fun clampStart(center: Double, size: Double, lo: Double, hi: Double): Double {
        val inset = 8.0
        val min = lo + inset
        val max = (hi - size - inset).coerceAtLeast(min)
        return (center - size / 2.0).coerceIn(min, max)
    }

    /**
     * Insert a sticky note centred on [atContent] (or on the visible part of the current page) in the
     * last-used note colour, and open it with the keyboard up. It joins the page, as one undo step,
     * when the edit ends.
     */
    fun insertStickyNote(atContent: Pt? = null) {
        prepareInsert()
        val (pi, at) = placementPoint(atContent) ?: return
        val page = state.document.pages[pi]
        val cover = state.footprint(page)
        val scale = state.document.dpi / 150.0
        val w = (TextItem.STICKY_SIZE * scale).coerceAtMost(cover.w - 16.0).coerceAtLeast(60.0)
        val h = (TextItem.STICKY_SIZE * scale).coerceAtMost(cover.h - 16.0).coerceAtLeast(60.0)
        val pos = Pt(clampStart(at.x, w, cover.left, cover.right), clampStart(at.y, h, cover.top, cover.bottom))
        val item = TextItem(
            pos, w, h, "", StickyColors.TEXT, TextItem.STICKY_POINT_SIZE * scale, TextItem.STICKY_FACE, textMeasurer,
            fill = nextStickyColor,
        )
        startEditing(item, pi, isNew = true)
    }

    /** Insert an empty text box at [atContent] and open it for typing (the long-press menu's "Text box"). */
    fun insertTextBoxAt(atContent: Pt) {
        prepareInsert()
        val (pi, at) = placementPoint(atContent) ?: return
        val page = state.document.pages[pi]
        val pageRight = state.footprint(page).right
        startEditing(newTextItem(at, defaultTextWidth(pageRight, at.x), 0.0), pi, isNew = true)
    }

    /**
     * Insert a [rows] x [cols] table centred on [atContent] (or on the visible part of the current
     * page) and open its first cell for typing. Inked to read on the page's paper: near-black lines
     * and type on a light page, near-white on a dark one, the header a faint shade of the same.
     */
    fun insertTable(atContent: Pt? = null, rows: Int = 3, cols: Int = 3) {
        prepareInsert()
        val (pi, at) = placementPoint(atContent) ?: return
        val page = state.document.pages[pi]
        val cover = state.footprint(page)
        val scale = state.document.dpi / 150.0
        val c = cols.coerceIn(1, TableItem.MAX_SIZE)
        val pt = TableItem.DEFAULT_POINT_SIZE * scale
        val width = min(cover.w * 0.75, c * TableItem.DEFAULT_COL_WIDTH * scale).coerceAtLeast(c * TableItem.minColumnWidth(pt))
        val paper = state.paperColor(page)
        val dark = (0.299 * paper.r + 0.587 * paper.g + 0.114 * paper.b) / 255.0 < 0.5
        val ink = if (dark) Rgba(236, 236, 236) else Rgba(33, 33, 33)
        val grid = TableGrid.empty(rows, c, width, pt).copy(
            textColor = ink,
            borderColor = ink.withAlpha(if (dark) 120 else 110),
            headerFill = ink.withAlpha(HEADER_SHADE_ALPHA),
        )
        val table = TableItem(Pt.ZERO, grid, textMeasurer)
        val b = table.bounds()
        table.pos = Pt(clampStart(at.x, b.w, cover.left, cover.right), clampStart(at.y, b.h, cover.top, cover.bottom))
        page.items.add(table)
        history.push(AddItem(page, table))
        state.document.dirty = true
        onContentChanged()
        startCellEdit(table, pi, 0, 0)
    }

    /** Select everything unlocked on the page under [content] (the long-press menu's "Select all"). */
    fun selectAllOnPage(content: Pt?) {
        commitTextEdit()
        val pi = (content?.let { state.pageIndexAtContent(it) } ?: state.currentPageIndex())
        val page = state.document.pages.getOrNull(pi) ?: return
        val all = page.items.filter { !it.locked }.map { Selected(pi, it) }
        if (all.isEmpty()) return
        setSelection(all)
        refreshSelectionMenu()
    }

    /** The page a long press at [content] was on (the current page in a gap). */
    fun pageIndexFor(content: Pt): Int =
        (state.pageIndexAtContent(content) ?: state.currentPageIndex()).coerceIn(0, (state.document.pages.size - 1).coerceAtLeast(0))

    // --- selection management ---

    private fun setSelection(items: List<Selected>) {
        // Items whose lifted state flips: those leaving the old selection (repainted back
        // into the cache) and those entering it (lifted out of it). Update the selection
        // first so the in-place repair below sees the new lifted set.
        val touched = selection + items
        selection.clear()
        selection.addAll(items)
        // A fresh selection starts upright: its box is the items' AABB, angle 0.
        selObb = selectionBoundsContent()?.let { Obb.fromAabb(it) }
        repairRegions(dirtyRegions(touched))
        onSelectionChanged(selection.isNotEmpty())
        requestRender()
    }

    fun clearSelection() {
        // Restore the tool a long-press grab temporarily switched away from.
        longPressPrevTool?.let {
            tool = it
            longPressPrevTool = null
            onToolChanged(tool)
        }
        onSelectionMenu(null)
        selObb = null
        if (selection.isEmpty()) return
        val regions = dirtyRegions(selection) // where the now-unlifted items sit (before clearing)
        selection.clear()
        repairRegions(regions) // repaint them back into the cache in place — no full rebuild
        onSelectionChanged(false)
        requestRender()
    }

    /**
     * Page-local dirty rects keyed by page index, unioning each item's paint extent (incl.
     * soft overflow such as neon glow) — the regions whose cached ink must be repaired when
     * these items' lifted state changes (lifting clears them out, unlifting repaints them).
     */
    private fun dirtyRegions(items: List<Selected>): Map<Int, Rect> {
        val regions = HashMap<Int, Rect>()
        for (s in items) {
            val b = s.item.paintBounds()
            regions[s.pageIndex] = regions[s.pageIndex]?.union(b) ?: b
        }
        return regions
    }

    /**
     * Repaint just [regions] of each page's ink cache in place — the eraser's smart path
     * ([CanvasState.repairRegion]), which keeps the cache entry and the (PDF/template)
     * background layer intact, so a selection edit no longer blanks the whole ink layer.
     * Falls back to a full page rebuild only where there is no live cache to repair.
     */
    private fun repairRegions(regions: Map<Int, Rect>) {
        for ((pageIndex, rect) in regions) {
            val page = state.document.pages.getOrNull(pageIndex) ?: continue
            if (!state.repairRegion(page, rect.outset(REPAIR_PAD))) state.invalidatePage(page)
        }
    }

    /**
     * Re-home selected items that a drag or transform carried onto another page. Item geometry is
     * page-local and every page renders clipped to itself, so an item left behind on its old page
     * vanishes the moment it is unlifted. Returns the undoable transfer, or null if nothing crossed.
     */
    private fun reassignSelectionPages(): Command? {
        val transfers = ArrayList<TransferItems.Transfer>()
        val rehomed = ArrayList<Selected>(selection.size)
        for (sel in selection) {
            val from = state.document.pages.getOrNull(sel.pageIndex)
            val target = if (from == null || state.pageRects.getOrNull(sel.pageIndex) == null) {
                null
            } else {
                pageIndexForBounds(state.fromPageSpaceRect(sel.pageIndex, sel.item.bounds()))
            }
            if (from == null || target == null || target == sel.pageIndex) {
                rehomed.add(sel)
                continue
            }
            // Both pages share the view rotation, so the change of frame is a pure page-space shift.
            val d = state.toPageSpace(target, state.fromPageSpace(sel.pageIndex, Pt.ZERO))
            transfers.add(TransferItems.Transfer(from, state.document.pages[target], sel.item, d.x, d.y))
            rehomed.add(Selected(target, sel.item))
        }
        if (transfers.isEmpty()) return null
        val cmd = TransferItems(transfers)
        cmd.redo()
        // The items stay lifted, so no cache is out of date: the old page never held them, and the
        // new one bakes them in when the selection clears (dirtyRegions now names the new page).
        selection.clear()
        selection.addAll(rehomed)
        return cmd
    }

    /** The page an item belongs to after a move: the drawable page its content-space bounds cover
     *  most, falling back to the nearest page when it was dropped in a gap. */
    private fun pageIndexForBounds(b: Rect): Int? {
        val drawable = state.drawablePageRange()
        var best = -1
        var bestArea = 0.0
        var nearest = -1
        var nearestDist = Double.MAX_VALUE
        for (i in state.pageRects.indices) {
            if (i !in drawable) continue
            val pr = state.pageRects[i]
            val w = min(b.right, pr.right) - max(b.left, pr.left)
            val h = min(b.bottom, pr.bottom) - max(b.top, pr.top)
            if (w > 0 && h > 0 && w * h > bestArea) {
                bestArea = w * h
                best = i
            }
            val d = pr.distanceTo(b.center)
            if (d < nearestDist) {
                nearestDist = d
                nearest = i
            }
        }
        return if (best >= 0) best else nearest.takeIf { it >= 0 }
    }

    private fun selectionBoundsContent(): Rect? {
        if (selection.isEmpty()) return null
        var acc: Rect? = null
        for (sel in selection) {
            if (state.pageRects.getOrNull(sel.pageIndex) == null) continue
            val b = state.fromPageSpaceRect(sel.pageIndex, sel.item.bounds())
            acc = acc?.union(b) ?: b
        }
        return acc
    }

    // --- public edit operations (toolbar / context menu) ---

    fun deleteSelection() {
        if (selection.isEmpty()) return
        val removals = selection.map { state.document.pages[it.pageIndex] to it.item }
        for ((page, item) in removals) page.items.remove(item)
        history.push(EraseItems(removals))
        state.document.dirty = true
        // clearSelection repairs the vacated regions in place; the removed items were lifted
        // (already out of the ink cache) so they simply stop being drawn. The background/PDF
        // cache is left untouched — no full flush, no flicker.
        clearSelection()
        onContentChanged()
        maybeSwitchBackAfterSelect()
    }

    fun selectAll() {
        val all = ArrayList<Selected>()
        state.document.pages.forEachIndexed { i, page ->
            page.items.forEach { if (!it.locked) all.add(Selected(i, it)) }
        }
        setSelection(all)
    }

    fun bringToFront() = reorderSelection(toFront = true)

    /** Put the selection under everything else on its page. */
    fun sendToBack() = reorderSelection(toFront = false)

    private fun reorderSelection(toFront: Boolean) {
        if (selection.isEmpty()) return
        val byPage = selection.groupBy { it.pageIndex }
        for ((pageIndex, sels) in byPage) {
            val page = state.document.pages.getOrNull(pageIndex) ?: continue
            val selectedSet = sels.map { it.item }.toSet()
            val old = page.items.toList()
            val kept = old.filter { it !in selectedSet }
            val moved = old.filter { it in selectedSet }
            val new = if (toFront) kept + moved else moved + kept
            if (new != old) {
                history.push(ReorderItems(page, old, new))
                page.items.clear()
                page.items.addAll(new)
                // Selected items are lifted (excluded from the cache); the reorder moves only
                // those, leaving the cached non-lifted items' order unchanged. The new z-order
                // bakes into the cache when the selection is next cleared — no rebuild here.
            }
        }
        state.document.dirty = true
        onContentChanged()
        maybeSwitchBackAfterSelect()
    }

    /** The styles the selection's drawn items carry, for the restyle popup to open on. */
    fun selectionStyles(): List<DrawStyle> = selection.mapNotNull { DrawStyle.of(it.item) }

    /** Styles held from the first [restyleSelection] preview, so a slider drag undoes in one step. */
    private var restyleBaseline: MutableMap<CanvasItem, DrawStyle>? = null

    /**
     * Recolour and/or re-thicken the selection's strokes and shapes. A null [color] or [width]
     * leaves that half of each item's style alone, so a mixed selection can be recoloured without
     * flattening its widths.
     *
     * A [preview] call applies the change without touching history; the next call with [preview]
     * off records everything since as a single undo step. Passing both values null with [preview]
     * off does nothing but close a pending preview, which is how a dismissed popup settles up.
     * No cache repair is needed: a selected item is lifted, so it is drawn live rather than baked.
     */
    fun restyleSelection(color: Rgba?, width: Double?, preview: Boolean = false) {
        if (selection.isEmpty()) return
        val baseline = restyleBaseline ?: HashMap<CanvasItem, DrawStyle>().also { map ->
            for (s in selection) DrawStyle.of(s.item)?.let { map[s.item] = it }
        }
        restyleBaseline = if (preview) baseline else null
        if (color != null || width != null) {
            for (s in selection) {
                val current = DrawStyle.of(s.item) ?: continue
                DrawStyle(color ?: current.color, width ?: current.width).applyTo(s.item)
            }
        }
        if (!preview) {
            val entries = baseline.mapNotNull { (item, before) ->
                DrawStyle.of(item)?.takeIf { it != before }?.let { RestyleItems.Entry(item, before, it) }
            }
            // Pushed after the fact, like every command: redo only ever re-applies an undone edit.
            if (entries.isNotEmpty()) history.push(RestyleItems(entries))
        }
        state.document.dirty = true
        onContentChanged()
        requestRender()
    }

    fun escape() {
        val flow = flowText
        if (flow != null && flow.active) {
            flow.endSession()
            requestRender()
            return
        }
        commitTextEdit(restoreTool = true)
        clearSelection()
        requestRender()
    }

    // --- object tools: arrange, turn, mirror, the picture tools ---

    /** The selection as it stands, for the host's exports. */
    fun selectedItems(): List<Selected> = selection.toList()

    /** The selection when it is exactly one picture. */
    val singleSelectedImage: ImageItem? get() = selection.singleOrNull()?.item as? ImageItem

    /** Everything selected can be turned a quarter (text boxes cannot). */
    fun selectionRotatable(): Boolean = selectionIsRotatable()

    /** Everything selected can be mirrored (text boxes cannot). */
    fun selectionFlippable(): Boolean =
        selection.isNotEmpty() && selection.all { it.item is Stroke || it.item is ShapeItem || it.item is ImageItem || it.item is TapeItem }

    /** Set while the crop tool is open: the selection chrome steps aside and the menu stays down. */
    var cropping: Boolean = false
        set(value) {
            field = value
            refreshSelectionMenu()
            requestRender()
        }

    /** Raise the selection one level on its page, past the next item it overlaps. */
    fun bringForward() = stepSelection(forward = true)

    /** Lower the selection one level on its page, under the next item it overlaps. */
    fun sendBackward() = stepSelection(forward = false)

    private fun stepSelection(forward: Boolean) {
        if (selection.isEmpty()) return
        for ((pageIndex, sels) in selection.groupBy { it.pageIndex }) {
            val page = state.document.pages.getOrNull(pageIndex) ?: continue
            val items = sels.map { it.item }
            var box: Rect? = null
            for (item in items) box = box?.union(item.paintBounds()) ?: item.paintBounds()
            val area = box ?: continue
            val overlaps = { other: CanvasItem -> other.paintBounds().intersects(area) }
            val old = page.items.toList()
            val new = if (forward) {
                com.xnotes.core.infinite.bringForwardOrder(old, items, overlaps)
            } else {
                com.xnotes.core.infinite.sendBackwardOrder(old, items, overlaps)
            }
            if (!com.xnotes.core.infinite.sameOrder(old, new)) {
                history.push(ReorderItems(page, old, new))
                page.items.clear()
                page.items.addAll(new)
            }
        }
        state.document.dirty = true
        onContentChanged()
        requestRender()
    }

    /**
     * Turn the whole selection a quarter turn about its middle. A single picture turns in its own
     * frame instead (its orientation steps), which keeps it upright-cropped and its angle clean.
     */
    fun rotateSelectionQuarter(clockwise: Boolean) {
        if (!selectionIsRotatable()) return
        singleSelectedImage?.let { image ->
            editImage(image) { it.rotateQuarter(clockwise) }
            return
        }
        val obb = selObb ?: return
        val turn = if (clockwise) Math.PI / 2.0 else -Math.PI / 2.0
        transformSelection(Affine.rotateAbout(obb.center, turn), obb.copy(angle = obb.angle + turn))
    }

    /** Mirror the whole selection about its middle, left↔right ([horizontal]) or top↔bottom. */
    fun flipSelection(horizontal: Boolean) {
        if (!selectionFlippable()) return
        singleSelectedImage?.let { image ->
            // As seen on screen: with the view turned sideways, the picture's own axes are swapped.
            val sideways = state.rotationDeg == 90 || state.rotationDeg == 270
            editImage(image) { it.flipShown(horizontal != sideways) }
            return
        }
        val obb = selObb ?: return
        val c = obb.center
        val t = if (horizontal) Affine.scaleAbout(c, -1.0, 1.0) else Affine.scaleAbout(c, 1.0, -1.0)
        transformSelection(t, obb.copy(angle = -obb.angle))
    }

    /** Bake a content-space [world] transform into every selected item, as one undo step. */
    private fun transformSelection(world: Affine, nextBox: Obb) {
        val items = selection.map { it.item }
        val before = items.map { it.snapshotGeometry() }
        for (sel in selection) {
            if (state.pageRects.getOrNull(sel.pageIndex) == null) continue
            sel.item.applyTransform(state.affineToPageSpace(sel.pageIndex, world))
        }
        val after = items.map { it.snapshotGeometry() }
        if (after == before) return
        val tx: Command = TransformItems(items, before, after)
        val transfer = reassignSelectionPages()
        history.push(if (transfer == null) tx else CompositeCommand(listOf(tx, transfer)))
        selObb = nextBox
        state.document.dirty = true
        onContentChanged()
        refreshSelectionMenu()
        requestRender()
    }

    /**
     * Change one picture through [block] as a single undo step: crop, turn, mirror, replace or reset.
     * A selected (lifted) picture is drawn live, so only its box needs refitting; one that is not
     * selected any more (a replace that landed after the selection moved on) is repaired in its
     * page's cache. Returns false when nothing changed or the picture is no longer in the note.
     */
    fun editImage(image: ImageItem, block: (ImageItem) -> Unit): Boolean {
        val pageIndex = state.document.pages.indexOfFirst { p -> p.items.any { it === image } }
        if (pageIndex < 0) return false
        val oldBounds = image.paintBounds()
        val before = image.snapshotGeometry()
        block(image)
        val after = image.snapshotGeometry()
        if (after == before) return false
        history.push(TransformItems(listOf(image), listOf(before), listOf(after)))
        state.document.dirty = true
        val sel = selection.firstOrNull { it.item === image }
        if (sel != null) {
            if (selection.size == 1) selObb = imageObb(sel)
        } else {
            val page = state.document.pages[pageIndex]
            val dirty = oldBounds.union(image.paintBounds()).outset(REPAIR_PAD)
            if (!state.repairRegion(page, dirty)) state.invalidatePage(page)
        }
        onContentChanged()
        refreshSelectionMenu()
        requestRender()
        return true
    }

    /** A picture's own turned box in content space, so the chrome hugs it rather than its bounds. */
    private fun imageObb(sel: Selected): Obb? {
        val image = sel.item as? ImageItem ?: return null
        if (state.pageRects.getOrNull(sel.pageIndex) == null) return null
        val c = state.fromPageSpace(sel.pageIndex, image.rect.center)
        val sideways = state.rotationDeg == 90 || state.rotationDeg == 270
        val hw = (if (sideways) image.rect.h else image.rect.w) / 2.0
        val hh = (if (sideways) image.rect.w else image.rect.h) / 2.0
        return Obb(c, hw, hh, image.angle)
    }

    /** The page a selected item sits on, for the crop tool's mapping. */
    fun pageIndexOf(item: CanvasItem): Int = selection.firstOrNull { it.item === item }?.pageIndex
        ?: state.document.pages.indexOfFirst { p -> p.items.any { it === item } }

    /** Repaint after the crop tool changed the lifted picture's look. */
    fun imageChangedLive() = requestRender()

    /** Select every unlocked item on the page in view (the lasso menu's "Select all"). */
    fun selectAllOnPage() {
        val pageIndex = selection.firstOrNull()?.pageIndex ?: state.currentPageIndex()
        val page = state.document.pages.getOrNull(pageIndex) ?: return
        setSelection(page.items.filter { !it.locked }.map { Selected(pageIndex, it) })
        refreshSelectionMenu()
    }

    /** Paste the copied items just past the selection's bottom-right, and select them. */
    fun pasteNearSelection() {
        val b = selectionBoundsContent()
        if (b == null) {
            pasteItemsAt(state.viewportToContent(Pt(state.viewportW / 2.0, state.viewportH / 2.0)))
            return
        }
        val pageIndex = selection.first().pageIndex
        pasteClonesOnPage(pageIndex, Pt(b.left + PASTE_NUDGE, b.top + PASTE_NUDGE), offsetFromBoundsTopLeft = true, nudge = 0.0)
    }

    // --- clipboard: copy / cut / duplicate / paste ---

    /** Copy the selection into the in-app clipboard. Pure: no tool/selection side effects, so
     *  cut/duplicate can reuse it without tripping the select tool's switch-back. */
    private fun copyToClipboard() {
        if (selection.isEmpty()) return
        itemClipboard.clear()
        clipboardFromCut = false
        selection.forEach { itemClipboard.add(cloneItem(it.item)) }
    }

    fun copySelection() {
        if (selection.isEmpty()) return
        copyToClipboard()
        maybeSwitchBackAfterSelect()
    }

    fun cutSelection() {
        if (selection.isEmpty()) return
        copyToClipboard()
        clipboardFromCut = true
        deleteSelection() // also runs the select tool's switch-back, once
    }

    /**
     * Pin the selection where it is, then put the selection away, since a locked item cannot stay
     * selected. Clearing also repaints the items back into the page cache, which lifting them for
     * the drag had taken them out of.
     */
    fun lockSelection() {
        if (selection.isEmpty()) return
        val items = selection.map { it.item }
        for (item in items) item.locked = true
        history.push(LockItems(items, true))
        clearSelection()
        maybeSwitchBackAfterSelect()
        onContentChanged()
        requestRender()
    }

    /** Release [item], so it can be selected again. Nothing else about it changes. */
    fun unlockItem(item: CanvasItem) {
        if (!item.locked) return
        item.locked = false
        history.push(LockItems(listOf(item), false))
        onContentChanged()
    }

    fun duplicateSelection() {
        if (selection.isEmpty()) return
        copyToClipboard()
        val pageIndex = selection.first().pageIndex
        // Paste offset slightly from the originals (not repositioned to a point).
        pasteClonesOnPage(pageIndex, Pt.ZERO, offsetFromBoundsTopLeft = false, nudge = 24.0)
        maybeSwitchBackAfterSelect()
    }

    /** Paste the clipboard items so their top-left lands at the given content point. */
    fun pasteItemsAt(content: Pt) {
        val pageIndex = state.pageIndexAtContent(content) ?: state.currentPageIndex()
        pasteClonesOnPage(pageIndex, content, offsetFromBoundsTopLeft = true, nudge = 0.0)
    }

    private fun pasteClonesOnPage(pageIndex: Int, target: Pt, offsetFromBoundsTopLeft: Boolean, nudge: Double) {
        if (itemClipboard.isEmpty()) return
        val page = state.document.pages.getOrNull(pageIndex) ?: return
        if (state.pageRects.getOrNull(pageIndex) == null) return
        val clones = itemClipboard.map { cloneItem(it) }
        // Collective bounds (page-local) of the clones.
        var box: Rect? = null
        for (c in clones) box = box?.union(c.bounds()) ?: c.bounds()
        val b = box ?: return
        val targetLocal = state.toPageSpace(pageIndex, target)
        val dx = if (offsetFromBoundsTopLeft) targetLocal.x - b.left + nudge else nudge
        val dy = if (offsetFromBoundsTopLeft) targetLocal.y - b.top + nudge else nudge
        for (c in clones) c.translate(dx, dy)
        page.items.addAll(clones)
        history.push(AddItems(page, clones))
        // A cut's clipboard is spent by the paste that lands it: the items were taken from the
        // page, so putting them down finishes the move rather than starting a series of copies.
        if (clipboardFromCut) {
            itemClipboard.clear()
            clipboardFromCut = false
        }
        state.document.dirty = true
        // The clones are immediately selected (lifted) below; setSelection repairs their
        // region in place, so no separate page rebuild is needed here.
        setSelection(clones.map { Selected(pageIndex, it) })
        refreshSelectionMenu()
        onContentChanged()
    }

    private fun cloneItem(item: CanvasItem): CanvasItem = item.deepCopy(textMeasurer)

    // --- selection menu ---

    /** Show the selection menu when a selection is settled (idle), else hide it. */
    private fun refreshSelectionMenu() {
        onSelectionMenu(if (selection.isNotEmpty() && mode == PointerMode.IDLE && !cropping) selectionBoundsViewport() else null)
    }

    private fun selectionBoundsViewport(): Rect? {
        val content = selectionBoundsContent() ?: return null
        val tl = state.contentToViewport(content.topLeft)
        val br = state.contentToViewport(Pt(content.right, content.bottom))
        val rect = Rect.fromPoints(tl, br)
        // Stretch the menu's anchor over the rotate grip, so the floating action bar never covers
        // it. The grip hangs off the tilted box (selObb), which after a turn can reach well above
        // the items' upright bounds, so it is read where it is rather than assumed above them.
        val grip = selectionRotatePoint()?.let { state.contentToViewport(it) }
        // And over the box's own grips: turned, a corner grip stands above the items' bounds.
        val handles = selectionResizeHandles().map { state.contentToViewport(it.content) }
        return OverlayTessellator.clearRotateGrip(rect, grip, chromeDpPx(), handles)
    }

    // --- PAN ---

    // When zoom is locked, the zoomLockPan preference decides which pan gestures still move the
    // viewport: "single" keeps both, "double" drops the single pointer, "none" freezes the page.
    // A single pointer's pan asks PalmRejection.lockedPanMoves, which adds the two-finger rule.
    private fun pinchPanAllowed(): Boolean = !(state.zoomLocked && zoomLockPan == "none")

    /** [held]: zoom lock keeps this pan from moving the page (it still taps and long-presses). */
    private fun beginPan(vx: Double, vy: Double, fromPenButton: Boolean = false, held: Boolean = false) {
        mode = PointerMode.PAN
        panDownViewport = Pt(vx, vy)
        panFromPenButton = fromPenButton
        panHeld = held
        startTrackingVelocity(vx, vy)
    }

    /** A finger's pan: held still while zoom lock wants two fingers, and put back if it turns out a palm. */
    private fun beginFingerPan(vx: Double, vy: Double) {
        beginPan(vx, vy, held = !PalmRejection.lockedPanMoves(state.zoomLocked, zoomLockPan, lockedTwoFingerScroll, byFinger = true))
        fingerPanStart = Pt(state.scrollX, state.scrollY)
    }

    private fun extendPan(vx: Double, vy: Double) {
        if (panHeld) return // zoom lock holds this pan: no scroll, no glide (its velocity stays zero)
        trackVelocity(vx, vy)
        val dx = -(vx - lastPan.x)
        var dy = -(vy - lastPan.y)
        if (!state.verticalScroll) {
            extendPanPaginated(dx, dy)
            lastPan = Pt(vx, vy)
            onViewChanged()
            requestRender()
            return
        }
        // While the bottom elastic is stretched, finger motion works the elastic first (rubber-band)
        // rather than the scroll, so pulling back relaxes the stretch before the document scrolls.
        // Stretching it further needs the document end on screen: right after a pull adds a page
        // the end sits a full page below, so a quick swipe toward it must scroll, not re-arm.
        if (state.overscrollY > 0.0 && (dy < 0.0 || state.isDocumentEndVisible())) {
            val relaxed = (state.overscrollY + dy * OVERSCROLL_RESIST).coerceAtLeast(0.0)
            val consumed = (relaxed - state.overscrollY) / OVERSCROLL_RESIST
            state.overscrollY = relaxed.coerceAtMost(OVERSCROLL_MAX)
            dy -= consumed
            updateOverscrollArmed()
        }
        // Apply the remaining pan to the scroll; whatever the clamp rejects at the bottom feeds the elastic.
        val beforeY = state.scrollY
        state.scrollBy(dx, dy)
        val leftoverY = dy - (state.scrollY - beforeY)
        // Only feed the elastic when the document's end is actually on screen. Inferring "at the end"
        // from a rejected downward scroll alone is unsafe: a transient bad scroll/layout state right
        // after a document opens can make the clamp fire while there is still document below the fold,
        // spuriously arming add-page (seen on first open, even on long PDFs). isDocumentEndVisible()
        // checks the last page's bottom against the viewport through the same transform that draws the
        // frame, so the affordance can never appear while the user can still see more document below.
        if (leftoverY > 0.0 && state.isDocumentEndVisible()) {
            state.overscrollY = (state.overscrollY + leftoverY * OVERSCROLL_RESIST).coerceAtMost(OVERSCROLL_MAX)
            updateOverscrollArmed()
        }
        lastPan = Pt(vx, vy)
        onViewChanged()
        requestRender()
    }

    /** Fire the threshold haptic once as the live stretch crosses the add-page point. */
    private fun updateOverscrollArmed() {
        val past = state.overscrollY >= OVERSCROLL_TRIGGER
        if (past && !overscrollArmed) onHaptic()
        overscrollArmed = past
    }

    // --- paginated page flip ---

    /**
     * Paginated pan: pan freely inside the current row (the clamp pins the window to it);
     * whatever the clamp rejects horizontally works the edge-pull elastic instead, arming
     * the flip. Relaxing motion unwinds the pull before the scroll moves again, exactly
     * like the bottom overscroll. No add-page elastic in this mode.
     */
    private fun extendPanPaginated(dx0: Double, dy: Double) {
        var dx = dx0
        val pull = state.flipOffsetX
        if (pull > 0.0 && dx < 0.0 || pull < 0.0 && dx > 0.0) {
            val relaxed = pull + dx * FLIP_RESIST
            if (relaxed * pull <= 0.0) { // crossed zero: the leftover motion scrolls
                state.flipOffsetX = 0.0
                dx += pull / FLIP_RESIST
            } else {
                state.flipOffsetX = relaxed
                dx = 0.0
            }
        }
        val beforeX = state.scrollX
        state.scrollBy(dx, dy)
        val leftoverX = dx - (state.scrollX - beforeX)
        if (leftoverX != 0.0) {
            val cap = FLIP_MAX_FRACTION * state.viewportW
            val armedBefore = abs(state.flipOffsetX) >= FLIP_TRIGGER
            state.flipOffsetX = (state.flipOffsetX + leftoverX * FLIP_RESIST).coerceIn(-cap, cap)
            if (!armedBefore && abs(state.flipOffsetX) >= FLIP_TRIGGER) onHaptic()
        }
        // The pull reveals only empty background; the neighbouring row never joins the screen.
    }

    /** Decide what a lifted paginated pan does: flip instantly, add a page, drop the pull, or glide. */
    private fun endPanPaginated() {
        val rows = state.rowRanges().size
        val pull = state.flipOffsetX
        when {
            // Past the last row the pull adds a page, the horizontal counterpart of the bottom
            // elastic. The new page opens its own row, so flipping onto it lands the user there.
            pull >= FLIP_TRIGGER && state.currentRow >= rows - 1 -> {
                clearFlipPull()
                onAddPageAtEnd()
                val grown = state.rowRanges().size
                if (grown > rows) flipTo(grown - 1) else { onViewChanged(); requestRender() }
            }
            pull >= FLIP_TRIGGER && state.currentRow < rows - 1 -> flipTo(state.currentRow + 1)
            pull <= -FLIP_TRIGGER && state.currentRow > 0 -> flipTo(state.currentRow - 1)
            panVel.x <= -FLIP_FLING_VEL && state.atRowEdge(next = true) && state.currentRow < rows - 1 ->
                flipTo(state.currentRow + 1)
            panVel.x >= FLIP_FLING_VEL && state.atRowEdge(next = false) && state.currentRow > 0 ->
                flipTo(state.currentRow - 1)
            pull != 0.0 -> { clearFlipPull(); onViewChanged(); requestRender() }
            else -> startPanFling()
        }
    }

    /**
     * Jump to [rowIndex] with no animation: the new row simply appears at its landing spot
     * ([CanvasState.rowTargetScroll] — zoom kept, top-aligned).
     */
    private fun flipTo(rowIndex: Int) {
        stopFling()
        state.goToPage(state.rowRanges()[rowIndex].first)
        onViewChanged()
        requestRender()
    }

    // --- inertial fling ---

    private fun startTrackingVelocity(vx: Double, vy: Double) {
        stopFling()
        lastPan = Pt(vx, vy)
        lastMoveMs = System.nanoTime() / 1_000_000L
        panVel = Pt.ZERO
    }

    private fun trackVelocity(vx: Double, vy: Double) {
        val now = System.nanoTime() / 1_000_000L
        val dt = ((now - lastMoveMs).coerceAtLeast(1L)) / 1000.0
        val inst = Pt((vx - lastPan.x) / dt, (vy - lastPan.y) / dt)
        panVel = Pt(panVel.x * VEL_SMOOTH + inst.x * (1 - VEL_SMOOTH), panVel.y * VEL_SMOOTH + inst.y * (1 - VEL_SMOOTH))
        lastMoveMs = now
    }

    /** Glide on after a lifted pan, unless the pen's side button drove it: that one stops dead. */
    private fun startPanFling() {
        if (!panFromPenButton) startFling(panVel)
    }

    private fun startFling(fingerVel: Pt) {
        if (fingerVel.length() < FLING_MIN_START) return
        flingVel = Pt(-fingerVel.x, -fingerVel.y) // scroll moves opposite the finger
        flinging = true
        lastFlingMs = System.nanoTime() / 1_000_000L
        choreographer.postFrameCallback(flingFrame)
    }

    private fun stopFling() {
        flinging = false
    }

    private fun stepFling(frameTimeNanos: Long) {
        if (!flinging) return
        val now = frameTimeNanos / 1_000_000L
        val dt = ((now - lastFlingMs).coerceIn(1L, 40L)) / 1000.0
        lastFlingMs = now
        val beforeX = state.scrollX
        val beforeY = state.scrollY
        state.scrollBy(flingVel.x * dt, flingVel.y * dt)
        val decay = exp(-FLING_FRICTION * dt)
        flingVel = Pt(flingVel.x * decay, flingVel.y * decay)
        onViewChanged()
        requestRender()
        val moved = state.scrollX != beforeX || state.scrollY != beforeY
        if (flingVel.length() < FLING_MIN_STOP || !moved) {
            flinging = false
        } else {
            choreographer.postFrameCallback(flingFrame)
        }
    }

    // --- disappearing ink (magic wand) ---

    /** Drop all held ephemeral strokes and stop any pending or running fade. */
    private fun clearFading() {
        cancelFadeTimer()
        stopFade()
        fadingStrokes.clear()
        fadeAlpha = 1.0
    }

    /** (Re)start the debounce so the held batch fades only once drawing has paused. */
    private fun scheduleFade() {
        cancelFadeTimer()
        val r = Runnable { startFade() }
        fadeTimerRunnable = r
        // The laser's trail goes sooner than written ink would: it was only ever pointing.
        val hold = if (fadingStrokes.isNotEmpty() && fadingStrokes.all { it.stroke.tool.isEphemeral }) {
            fadingStrokes.maxOf { it.stroke.config.fadeAfterMs }.toLong()
        } else if (fadingStrokes.isEmpty() && laserArmed()) {
            configFor(Tool.LASER).fadeAfterMs.toLong()
        } else {
            WAND_HOLD_MS
        }
        handler.postDelayed(r, hold)
    }

    private fun cancelFadeTimer() {
        fadeTimerRunnable?.let { handler.removeCallbacks(it) }
        fadeTimerRunnable = null
    }

    private fun startFade() {
        fadeTimerRunnable = null
        if (fadingStrokes.isEmpty()) return
        // Still drawing: the batch only fades once the pen has been up for the whole hold.
        if (liveStroke != null) {
            scheduleFade()
            return
        }
        fading = true
        fadeAlpha = 1.0
        fadeStartMs = System.nanoTime() / 1_000_000L
        choreographer.postFrameCallback(fadeFrame)
    }

    private fun stopFade() {
        fading = false
    }

    private fun stepFade(frameTimeNanos: Long) {
        if (!fading) return
        val now = frameTimeNanos / 1_000_000L
        val t = ((now - fadeStartMs).toDouble() / WAND_FADE_MS).coerceIn(0.0, 1.0)
        fadeAlpha = (1.0 - t) * (1.0 - t) // ease-out so the batch melts away rather than blinking off
        requestRender()
        if (t >= 1.0) {
            fadingStrokes.clear()
            fadeAlpha = 1.0
            fading = false
        } else {
            choreographer.postFrameCallback(fadeFrame)
        }
    }

    // --- elastic overscroll release ---

    /** Finger lifted while the bottom elastic was stretched: add a page if pulled far enough, then spring back. */
    private fun releaseOverscroll() {
        stopFling()
        if (state.overscrollY >= OVERSCROLL_TRIGGER) {
            onAddPageAtEnd()
            // The stretch is spent: sink it below the trigger so a re-grab mid-spring
            // cannot release it as a second add.
            state.overscrollY = OVERSCROLL_TRIGGER - 1.0
        }
        overscrollArmed = false
        if (!overscrollSettling) {
            overscrollSettling = true
            lastOverscrollMs = System.nanoTime() / 1_000_000L
            choreographer.postFrameCallback(overscrollFrame)
        }
    }

    private fun stopOverscrollSettle() {
        overscrollSettling = false
    }

    /** Drop the elastic immediately (no spring) — used when a gesture is cancelled or supplanted. */
    private fun clearOverscroll() {
        overscrollSettling = false
        overscrollArmed = false
        state.overscrollY = 0.0
    }

    /** Fold any paginated edge-pull back into the (clamped) scroll, with no animation. */
    private fun clearFlipPull() {
        if (state.flipOffsetX != 0.0) {
            state.scrollX += state.flipOffsetX
            state.flipOffsetX = 0.0
            state.clampScroll()
        }
    }

    /**
     * Drop any in-flight scroll/zoom physics and partial gesture so the previous document's fling,
     * elastic stretch or half-finished pan can't bleed into a freshly opened one. The editor calls
     * this whenever it swaps the open document — otherwise a stale fling/overscroll could leave the
     * new document at (or believing it is at) its bottom, spuriously arming add-page on first scroll.
     */
    fun resetGestureState() {
        stopFling()
        state.flipOffsetX = 0.0
        clearOverscroll()
        cancelDwell()
        clearFading()
        crossedSegments.clear() // they belong to the outgoing document, whose history is gone
        dwellEligible = false
        mode = PointerMode.IDLE
        lastPan = Pt.ZERO
        panVel = Pt.ZERO
    }

    private fun stepOverscrollSettle(frameTimeNanos: Long) {
        if (!overscrollSettling) return
        val now = frameTimeNanos / 1_000_000L
        val dt = ((now - lastOverscrollMs).coerceIn(1L, 40L)) / 1000.0
        lastOverscrollMs = now
        state.overscrollY *= exp(-OVERSCROLL_SPRING * dt) // exponential ease toward rest
        if (state.overscrollY < 0.5) {
            state.overscrollY = 0.0
            overscrollSettling = false
        } else {
            choreographer.postFrameCallback(overscrollFrame)
        }
        onViewChanged()
        requestRender()
    }

    // --- PINCH ---

    private fun beginPinch(e: MotionEvent) {
        liveStroke = null
        strokePageIndex = null
        cancelDwell() // a second finger turns the gesture into a zoom; don't snap a shape mid-pinch
        dwellEligible = false
        bandRect = null
        lassoPoints.clear()
        if (mode == PointerMode.SHOT) clearScreenshot() // a second finger turns the drag into a zoom
        clearOverscroll() // a second finger ends any bottom-pull; the elastic snaps away
        clearFlipPull() // ...and any paginated edge-pull folds back into the scroll
        mode = PointerMode.PINCH
        val a = Pt(e.getX(0).toDouble(), e.getY(0).toDouble())
        val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
        val mid = (a + b) * 0.5
        pinchInitDist = a.distanceTo(b).coerceAtLeast(1.0)
        pinchInitZoom = state.zoom
        pinchAnchorContent = state.viewportToContent(mid)
        startTrackingVelocity(mid.x, mid.y)
    }

    private fun updatePinch(e: MotionEvent) {
        if (e.pointerCount < 2) return
        val a = Pt(e.getX(0).toDouble(), e.getY(0).toDouble())
        val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
        val dist = a.distanceTo(b)
        if (dist < 1e-3) return
        val mid = (a + b) * 0.5
        trackVelocity(mid.x, mid.y)
        // Zoom lock: pan only (keep the initial zoom).
        val raw = (pinchInitZoom * (dist / pinchInitDist)).coerceIn(state.minZoom, state.maxZoom)
        val wasFit = state.fitWidthActive || state.fitHeightActive
        // Magnetic fit: the live zoom sticks to fit-width — and, paginated, fit-height — while
        // within the band (pinch past it to break free). The lock hint surfaces the moment a
        // magnet grabs and is dismissed the moment it breaks free. A locked pinch is pan-only,
        // so it never snaps.
        val z = if (state.zoomLocked) pinchInitZoom else state.snapZoomToFit(raw)
        // A two-finger pan held at its zoom (locked, or on a fit magnet) keeps prefetching like a one-finger one.
        if (z != pinchInitZoom) state.zoomingInProgress = true
        state.zoom = z
        if (!state.zoomLocked) {
            val nowFit = state.fitWidthActive || state.fitHeightActive
            if (!wasFit && nowFit) onFitWidthSnapped()
            else if (wasFit && !nowFit) onFitWidthReleased()
        }
        if (pinchPanAllowed()) {
            val targetX = pinchAnchorContent.x * z - mid.x
            val targetY = pinchAnchorContent.y * z - mid.y
            state.scrollX = targetX
            state.scrollY = targetY
            state.clampScroll()
            // Only a pan-only (zoom-locked) pinch works the elastics. While the zoom is changing the
            // clamp rejects scroll for reasons that have nothing to do with reaching past the end.
            if (state.zoomLocked) applyPinchElastic(targetX - state.scrollX, targetY - state.scrollY)
        }
        lastPan = mid
        onViewChanged() // live zoom %: refresh the toolbar each pinch frame, not just at the end
        requestRender()
    }

    /**
     * Work the scroll the clamp rejected into the same elastics a one-finger pan drives: the
     * add-page stretch scrolling vertically, the edge-pull paginated. Without this a two-finger
     * pan — the only pan there is with zoom locked to two fingers — could never reach either.
     *
     * The pinch positions the scroll from a fixed content anchor, so the rejected amount is the
     * whole overshoot rather than one frame's worth: the elastic is set, not accumulated.
     */
    private fun applyPinchElastic(overX: Double, overY: Double) {
        if (state.verticalScroll) {
            state.overscrollY =
                if (overY > 0.0 && state.isDocumentEndVisible()) (overY * OVERSCROLL_RESIST).coerceAtMost(OVERSCROLL_MAX)
                else 0.0
            updateOverscrollArmed()
            return
        }
        val cap = FLIP_MAX_FRACTION * state.viewportW
        val armedBefore = abs(state.flipOffsetX) >= FLIP_TRIGGER
        state.flipOffsetX = (overX * FLIP_RESIST).coerceIn(-cap, cap)
        if (!armedBefore && abs(state.flipOffsetX) >= FLIP_TRIGGER) onHaptic()
    }

    private fun endPinch() {
        mode = PointerMode.IDLE
        if (state.zoomingInProgress) {
            state.zoomingInProgress = false
            state.invalidateCachesForZoom() // keep stale surfaces to blit until the sharp rebuild lands
        }
        onViewChanged()
        requestRender()
        if (!pinchPanAllowed()) return
        when {
            state.overscrollY > 0.0 -> releaseOverscroll()
            !state.verticalScroll && state.flipOffsetX != 0.0 -> endPanPaginated()
            else -> startFling(panVel)
        }
    }

    private fun abortGesture() {
        cancelLongPress()
        fingerPanStart = null
        panHeld = false
        cancelDwell()
        dwellEligible = false
        snappedSelectionPendingMenu = false
        strokeDismissedSelection = false
        panMayCommitText = false
        stopFling()
        clearFlipPull()
        clearOverscroll()
        if (mode == PointerMode.ERASE) endErase() // what a cancelled erase took stays one undo step
        if (mode == PointerMode.TABLE_COL) endColumnDrag(null) // the column keeps the width it was dragged to
        selTapEdit = null
        pushStrokeEdit(null) // a cancelled crossing still left segments on the pages behind it
        liveStroke = null
        strokePageIndex = null
        snapEngaged = false
        snapRunStartEdge = null
        snapCurrentEdge = null
        snapPenViewport = null
        pendingShape = null
        shapePageIndex = null
        clearPendingTape()
        bandRect = null
        lassoPoints.clear()
        textDragRect = null
        // Cancel an in-progress capture drag; a frozen capture (mode IDLE) survives.
        if (mode == PointerMode.SHOT) { screenshotRect = null; onScreenshotMenu(null) }
        if (mode == PointerMode.PINCH && state.zoomingInProgress) {
            state.zoomingInProgress = false
            state.invalidateCachesForZoom()
        }
        mode = PointerMode.IDLE
    }

    private fun resolvePressure(e: MotionEvent, pointerIndex: Int, toolType: Int): Double =
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS) e.getPressure(pointerIndex).toDouble().coerceIn(0.0, 1.0) else 1.0

    // --- overlay ---

    fun drawOverlay(r: Renderer) {
        val origin = state.origin()
        r.withSave {
            r.translate(origin.x, origin.y)
            r.scale(state.zoom, state.zoom)

            markupOverlay?.draw(r)

            // Lifted (selected) items, drawn live at the move offset.
            for (sel in selection) {
                val pr = state.pageRects.getOrNull(sel.pageIndex) ?: continue
                r.withSave {
                    r.translate(pr.left + moveOffset.x, pr.top + moveOffset.y)
                    state.applyPageTransform(r, state.document.pages[sel.pageIndex])
                    sel.item.paint(r)
                }
            }

            // The sticky note being typed on shows its card here (the live field draws its text), and
            // the table being typed into shows everything but the open cell's text, plus that cell.
            editingText?.takeIf { it.isSticky }?.let { item -> paintOnPage(r, editingPageIndex) { item.paintCard(r) } }
            cellEdit?.let { e ->
                paintOnPage(r, e.pageIndex) {
                    e.table.paint(r, e.row to e.col)
                    if (e.row < e.table.grid.rows && e.col < e.table.grid.cols) {
                        r.strokeRect(e.table.cellBounds(e.row, e.col), Pen(state.pageAccentAt(e.pageIndex), 2.0, cosmetic = true))
                    }
                }
            }

            // Live in-progress stroke / shape preview, clipped to its page. The stroke goes through
            // the wet cache, which blits whatever has stopped moving instead of refilling it.
            if (frontInk?.live != true) {
                liveStroke?.let { stroke -> paintClippedToPage(r, strokePageIndex) { state.paintLiveStroke(r, stroke) } }
            }
            pendingShape?.let { shape -> paintClippedToPage(r, shapePageIndex) { shape.paint(r) } }
            if (tapePulling) pendingTape?.let { tape -> paintClippedToPage(r, tapePageIndex) { tape.paint(r) } }

            // Disappearing ink (magic wand): ephemeral strokes drawn live at the shared fade alpha,
            // without mutating their stored colour. Highlighters keep their MULTIPLY look while fading.
            for (fs in fadingStrokes) {
                // Still on the front buffer, which would otherwise draw it a second time.
                if (frontInk?.holding(fs.stroke) == true) continue
                paintClippedToPage(r, fs.pageIndex) {
                    r.saveLayerBlended(fs.stroke.paintBounds(), fadeAlpha, fs.stroke.blendMode)
                    fs.stroke.paint(r)
                    r.restore()
                }
            }

            // The selection chrome's and the marquees' one colour this frame: the page accent as it
            // reads on the paper under them. Looked up only on a frame that draws some, so a frame of
            // plain inking asks nothing.
            val marquee = mode == PointerMode.BAND || mode == PointerMode.TEXT_DRAG || mode == PointerMode.LASSO_DRAW
            val chrome = if (marquee || selection.isNotEmpty() || screenshotRect != null) chromeAccent() else null

            // Screenshot capture region: the live drag rect, kept frozen until "copy as image" is used.
            if (chrome != null) screenshotRect?.let { r.strokeRect(it, chromePen(1.6, chrome)) }

            // Search matches, then the flow caret + selection highlight, under the selection chrome; then the PDF's.
            searchTints?.draw(r)
            flowText?.drawOverlay(r)
            pdfText?.drawOverlay(r)

            if (chrome != null) drawSelectionChrome(r, chrome)
        }

        // Note icons: chrome of one size at any zoom, so in viewport space.
        noteIcons?.draw(r)

        // Eraser cursor (viewport space, after the transform is restored).
        eraserCursor?.let {
            val radius = eraserRadius() * state.zoom
            r.strokeEllipse(it, radius, radius, Pen(state.palette.textDim, 1.3, cosmetic = true))
        }

        // Ruler (viewport space; floats above all content and the live stroke).
        if (ruler.visible) drawRuler(r)
    }

    /**
     * A marquee pen: dashed, so chrome reads as chrome rather than as ink, off the same dp runs
     * the infinite canvas tessellates its own from, so a band, a lasso and a selection box look
     * like each other and like themselves on the other canvas. A cosmetic pen takes its dash runs
     * in device px, which is what the conversion here produces.
     */
    private fun chromePen(width: Double, accent: Rgba): Pen = Pen(
        accent,
        width,
        cosmetic = true,
        dashed = true,
        dashOn = OverlayTessellator.DASH_ON_DP * state.devicePxPerDp,
        dashGap = OverlayTessellator.DASH_GAP_DP * state.devicePxPerDp,
    )

    /**
     * What the selection chrome and marquees are drawn in: the page accent as it reads on the page
     * under them, the selection's (its first item's, for one spanning pages), else the page the
     * text-box drag or the marquee started on. Near-black on cream; light on a dark page.
     */
    private fun chromeAccent(): Rgba = state.pageAccentAt(
        when {
            selection.isNotEmpty() -> selection[0].pageIndex
            mode == PointerMode.TEXT_DRAG -> textDragPageIndex
            else -> marqueePageIndex
        },
    )

    /** The marquee being swept out, or the settled selection's frame and grips, in [accent] (content space). */
    private fun drawSelectionChrome(r: Renderer, accent: Rgba) {
        when {
            mode == PointerMode.BAND -> bandRect?.let { r.strokeRect(it, chromePen(1.3, accent)) }
            mode == PointerMode.TEXT_DRAG -> textDragRect?.let { r.strokeRect(it, chromePen(1.3, accent)) }
            mode == PointerMode.LASSO_DRAW && lassoPoints.size >= 2 ->
                r.strokePolyline(lassoPoints, chromePen(1.3, accent))
            selection.isNotEmpty() && !cropping ->
                selObb?.let { obb ->
                    r.strokePolygon(
                        obb.corners().map { Pt(it.x + moveOffset.x, it.y + moveOffset.y) },
                        chromePen(OverlayTessellator.FRAME_DP * chromeDpPx(), accent),
                    )
                }
        }

        // Resize + rotate handles for the settled selection (single, multi, or mixed), sized as
        // SC 47-49 draws them and from the same dp numbers the infinite canvas tessellates.
        if (selection.isEmpty() || mode == PointerMode.BAND || mode == PointerMode.LASSO_DRAW || cropping) return
        val dp = chromeDpPx() / state.zoom
        val drop = OverlayTessellator.SHADOW_DROP_DP * dp
        val grow = OverlayTessellator.SHADOW_GROW_DP * dp
        // What reads on the accent: B2's white on ordinary paper; on a dark page, where the accent
        // turns light, near-black. The grips' faces and the rotate grip's arrow are drawn in it.
        val face = Palette.onFill(accent)
        // Rotate grip: a stem out from the box's top edge up to a filled grip with the clockwise
        // arrow on it, so turning is never mistaken for resizing.
        selectionRotatePoint()?.let { rp ->
            selObb?.let { obb ->
                val base = ResizeMath.obbTopMid(obb)
                val stemTop = Pt(base.x + moveOffset.x, base.y + moveOffset.y)
                val grip = Pt(rp.x + moveOffset.x, rp.y + moveOffset.y)
                r.strokePolyline(listOf(grip, stemTop), Pen(accent, OverlayTessellator.FRAME_DP * chromeDpPx(), cosmetic = true))
                val radius = OverlayTessellator.ROTATE_GRIP_DP / 2.0 * dp
                fillShadow(r, grip, radius + grow, drop, OverlayTessellator.ROTATE_SHADOW)
                r.fillCircle(grip, radius, accent)
                fillRotateGlyph(r, grip, OverlayTessellator.ROTATE_GLYPH_DP * dp, face)
            }
        }
        // Resize grips: a face ringed in the page accent on a flat drop shadow, so they look like
        // something to take hold of.
        val faceR = OverlayTessellator.GRIP_DP / 2.0 * dp
        val ringR = faceR + OverlayTessellator.GRIP_RING_DP * dp
        for (h in selectionResizeHandles()) {
            val c = Pt(h.content.x + moveOffset.x, h.content.y + moveOffset.y)
            fillShadow(r, c, ringR + grow, drop, OverlayTessellator.GRIP_SHADOW)
            r.fillCircle(c, ringR, accent)
            r.fillCircle(c, faceR, face)
        }
    }

    /** A grip's shadow: one flat disc of [radius] under [c], [drop] lower. No blur, no layer, no allocation. */
    private fun fillShadow(r: Renderer, c: Pt, radius: Double, drop: Double, color: Rgba) {
        r.save()
        r.translate(0.0, drop)
        r.fillCircle(c, radius, color)
        r.restore()
    }

    /**
     * The rotate grip's arrow: [RotateGlyph]'s cached outline, [size] content px across and centred
     * on [at], upright as an icon is. Drawn inside a save and restore, so the grips drawn after it
     * see the transform exactly as it was, with no float drift from moving it there and back.
     */
    private fun fillRotateGlyph(r: Renderer, at: Pt, size: Double, color: Rgba) {
        if (size <= 0.0) return
        r.save()
        r.translate(at.x, at.y)
        r.scale(size, size)
        r.fillPolygon(RotateGlyph.outline, color)
        r.restore()
    }

    /** Paint in page [pageIndex]'s space, unclipped like the lifted selection. */
    private inline fun paintOnPage(r: Renderer, pageIndex: Int, crossinline paint: () -> Unit) {
        val pr = state.pageRects.getOrNull(pageIndex) ?: return
        r.withSave {
            r.translate(pr.left, pr.top)
            state.applyPageTransform(r, state.document.pages[pageIndex])
            paint()
        }
    }

    private inline fun paintClippedToPage(r: Renderer, pageIndex: Int?, crossinline paint: () -> Unit) {
        val pi = pageIndex ?: -1
        val pr = state.pageRects.getOrNull(pi) ?: return
        r.withSave {
            r.clipRect(pr)
            r.translate(pr.left, pr.top)
            state.applyPageTransform(r, state.document.pages[pi])
            paint()
        }
    }

    // --- ruler ---

    private fun beginRulerTransform(e: MotionEvent) {
        cancelLongPress()
        val a = Pt(e.getX(0).toDouble(), e.getY(0).toDouble())
        val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
        rulerXformStartCentroid = (a + b) * 0.5
        rulerXformStartFingerAngle = atan2(b.y - a.y, b.x - a.x)
        rulerXformStartCenter = ruler.center
        rulerXformStartRuler = ruler.angleRad
        mode = PointerMode.RULER_TRANSFORM
        requestRender()
    }

    private fun updateRulerTransform(e: MotionEvent) {
        if (e.pointerCount < 2) return
        val a = Pt(e.getX(0).toDouble(), e.getY(0).toDouble())
        val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
        if (!ruler.lockPosition) {
            ruler.center = rulerXformStartCenter + ((a + b) * 0.5 - rulerXformStartCentroid)
        }
        if (!ruler.lockAngle) {
            ruler.angleRad = Ruler.snapToAxes(rulerXformStartRuler + (atan2(b.y - a.y, b.x - a.x) - rulerXformStartFingerAngle))
        }
        requestRender()
    }

    /** How far the rotation handles sit from the ruler centre (kept on-screen). */
    private fun rulerHandleDist(): Double = 0.30 * minOf(state.viewportW, state.viewportH).toDouble()

    /** Drag a rotation handle: spin the ruler about its centre so the grabbed handle tracks the pointer. */
    private fun updateRulerRotate(e: MotionEvent) {
        if (ruler.lockAngle) return
        val idx = e.findPointerIndex(drawingPointerId).coerceAtLeast(0)
        val v = (Pt(e.getX(idx).toDouble(), e.getY(idx).toDouble()) - ruler.center) * rulerRotateSign
        if (v.length() < 1e-3) return
        ruler.angleRad = Ruler.snapToAxes(atan2(v.y, v.x))
        requestRender()
    }

    /**
     * The ruler magnet for a viewport draw point. Engages when the pen enters the snap band beside a
     * long edge, then clamps the ink onto that edge — so a stroke can't pass through the body — and
     * releases only when the pen retreats back out past the band on the engaged side. Updates the
     * engagement state and returns the point to actually draw.
     */
    private fun magnetize(vp: Pt): Pt {
        if (!ruler.visible) return vp
        val ht = ruler.thicknessPx / 2.0
        val band = RULER_SNAP_DP * state.devicePxPerDp
        val across = ruler.signedAcross(vp)
        val active = when {
            snapEngaged -> {
                // Release only on an outward retreat past the band on the engaged side; pushing inward
                // (toward/through the body) stays clamped to the edge, so ink can't cross the ruler.
                val retreated = if (snapTopSide) across > ht + band else across < -(ht + band)
                if (retreated) {
                    snapEngaged = false
                    snapRunStartEdge = null
                    snapCurrentEdge = null
                }
                snapEngaged
            }
            abs(across) <= ht + band -> {
                snapTopSide = across >= 0.0
                snapEngaged = true
                true
            }
            else -> false
        }
        if (!active) return vp
        snapPenViewport = vp
        val edge = ruler.projectToEdge(vp, snapTopSide)
        if (snapRunStartEdge == null) snapRunStartEdge = edge
        snapCurrentEdge = edge
        return edge
    }

    /**
     * The ruler on the page (TO Frame 7), in viewport space: the band and its edges, the ticks with their labels clear
     * of the controls, the two locks, the turn handles and their angles, and the cm readout while ink rides an edge.
     * Colours, pens, fonts, glyph bitmaps, label strings and pill outlines come from [rulerChrome], built once per
     * palette and density, so a frame here makes none of them.
     */
    private fun drawRuler(r: Renderer) {
        val density = state.devicePxPerDp
        val chrome = rulerChrome
        chrome.update(state.palette, density)
        // Visible along-range: the four viewport corners projected onto the band's length axis.
        val d = ruler.direction()
        val cx = ruler.center.x
        val cy = ruler.center.y
        val vw = state.viewportW.toDouble()
        val vh = state.viewportH.toDouble()
        val s0 = -cx * d.x - cy * d.y
        val s1 = (vw - cx) * d.x - cy * d.y
        val s2 = -cx * d.x + (vh - cy) * d.y
        val s3 = (vw - cx) * d.x + (vh - cy) * d.y
        val pad = 4.0 * density
        val sMin = min(min(s0, s1), min(s2, s3)) - pad
        val sMax = max(max(s0, s1), max(s2, s3)) + pad

        // Body: a frosted strip with neutral edges (--to-band, --to-edge).
        val quad = ruler.bodyQuad(sMin, sMax)
        r.fillPolygon(quad, chrome.band)
        r.strokePolyline(listOf(quad[0], quad[1]), chrome.edgePen)
        r.strokePolyline(listOf(quad[3], quad[2]), chrome.edgePen)

        val dist = rulerHandleDist()
        val handles = ruler.handleCenters(dist)
        val phi = Math.toDegrees(atan2(d.y, d.x))
        val deg0 = RulerMath.ccwDegrees(phi)
        val deg1 = RulerMath.cwDegrees(phi)
        // Where the two angle readouts land, kept on screen: the ticks clear their labels from there, the handles draw them.
        val pill0 = rulerReadoutBox(handles[0], deg0, density, chrome)
        val pill1 = rulerReadoutBox(handles[1], deg1, density, chrome)
        drawRulerTicks(r, density, chrome, sMin, sMax, dist, pill0, pill1)
        drawRulerButtons(r, density, chrome)
        drawRulerHandles(r, density, chrome, handles, deg0, deg1, pill0, pill1)

        if (snapEngaged) {
            val a = snapRunStartEdge
            val b = snapCurrentEdge
            val pen = snapPenViewport
            if (a != null && b != null && pen != null) {
                val tenths = RulerMath.lengthTenths(RulerMath.viewportLenToCm(a.distanceTo(b), state.zoom, document.dpi))
                drawReadout(r, chrome.lengthText(tenths), chrome.lengthWidth(tenths), pen.x + 30.0 * density, pen.y - 30.0 * density, density, chrome)
            }
        }
    }

    /**
     * The angle readout's pill for the handle at [h] reading [deg]: 28 dp past the handle's edge, outward along the band,
     * and kept on screen ([RulerMath.pillRect]), so the labels it clears and the pill drawn are in the same place.
     */
    private fun rulerReadoutBox(h: Pt, deg: Int, density: Double, chrome: RulerChrome): Rect {
        val outward = (h - ruler.center).normalized()
        val reach = ruler.handleRadiusPx() + 28.0 * density
        return RulerMath.pillRect(
            h.x + outward.x * reach, h.y + outward.y * reach, chrome.degreeWidth(deg), density,
            state.viewportW.toDouble(), state.viewportH.toDouble(),
        )
    }

    /**
     * The two turn handles (.to-rh, TO 100–101): 44 dp raised discs with a 1.5 dp ring and the arrows-clockwise glyph,
     * at 40% and without their shadow while the angle is locked (they refuse to turn it). Their angles (.to-ro) never
     * dim: the +direction handle reads counter-clockwise from +x ([deg0]), the other clockwise from −x ([deg1]), so the
     * two never swap as the ruler turns; both are rounded before they wrap, so neither reads 360°. [pill0] and [pill1]
     * are where their readouts land ([rulerReadoutBox]).
     */
    private fun drawRulerHandles(
        r: Renderer,
        density: Double,
        chrome: RulerChrome,
        handles: List<Pt>,
        deg0: Int,
        deg1: Int,
        pill0: Rect,
        pill1: Rect,
    ) {
        val radius = ruler.handleRadiusPx()
        val dim = ruler.lockAngle
        val alpha = RulerMath.handleAlpha(dim)
        val glyph = chrome.glyph(RulerGlyph.TURN)
        val g = 20.0 * density
        val ringR = radius + 0.75 * density
        for (i in handles.indices) {
            val h = handles[i]
            if (!dim) r.fillCircle(Pt(h.x, h.y + 1.5 * density), radius + 0.5 * density, chrome.shadow)
            r.fillCircle(h, radius, if (dim) chrome.raisedDim else chrome.raised)
            r.strokeEllipse(h, ringR, ringR, if (dim) chrome.handleRingPenDim else chrome.handleRingPen)
            r.drawRasterBlended(glyph, Rect(h.x - g / 2.0, h.y - g / 2.0, g, g), alpha, com.xnotes.core.pal.BlendMode.SRC_OVER)
            val deg = if (i == 0) deg0 else deg1
            drawReadout(r, chrome.degreeText(deg), chrome.degreeWidth(deg), if (i == 0) pill0 else pill1, density, chrome)
        }
    }

    /**
     * cm/mm graduations on both long edges, spaced by the zoom, 0 at the ruler's centre (TO 848–849); the cm labels
     * (TO 850) upright in system Sans Bold, and left out where a lock, a handle or an angle readout sits ([pill0] and
     * [pill1], where the readouts are drawn once kept on screen).
     */
    private fun drawRulerTicks(
        r: Renderer,
        density: Double,
        chrome: RulerChrome,
        sMin: Double,
        sMax: Double,
        dist: Double,
        pill0: Rect,
        pill1: Rect,
    ) {
        val cmPx = RulerMath.contentPxPerCm(document.dpi) * state.zoom
        if (cmPx <= 0.0) return
        val d = ruler.direction()
        val n = ruler.normal()
        val ht = ruler.thicknessPx / 2.0
        val showMinor = cmPx >= 46.0
        val showLabels = cmPx >= 26.0
        val unitPx = if (showMinor) cmPx / 10.0 else cmPx
        val unitsPerLabel = if (showMinor) 10 else 1
        val step = if (showMinor || cmPx >= 12.0) 1 else 5 // crowd guard when zoomed far out
        val labelH = chrome.tickMetrics.ascent + chrome.tickMetrics.descent
        var j = Math.ceil(sMin / unitPx).toInt()
        val jMax = Math.floor(sMax / unitPx).toInt()
        while (j <= jMax) {
            if (step == 1 || j % step == 0) {
                val along = j * unitPx
                val mid = ruler.center + d * along
                val top = mid + n * ht
                val bot = mid - n * ht
                val len = when {
                    !showMinor || j % 10 == 0 -> ht * 0.46
                    j % 5 == 0 -> ht * 0.30
                    else -> ht * 0.18
                }
                r.strokePolyline(listOf(top, top - n * len), chrome.tickPen)
                r.strokePolyline(listOf(bot, bot + n * len), chrome.tickPen)
                if (showLabels && j % unitsPerLabel == 0) {
                    val cm = abs(j / unitsPerLabel)
                    if (RulerMath.tickLabelShown(along, dist, density, mid.x, mid.y, chrome.numberWidth(cm), labelH, pill0, pill1)) {
                        drawTickLabel(r, cm, mid, chrome)
                    }
                }
            }
            j++
        }
    }

    /** A cm number centred on [center], from the label cache (no string or layout per frame). */
    private fun drawTickLabel(r: Renderer, cm: Int, center: Pt, chrome: RulerChrome) {
        val m = chrome.tickMetrics
        val w = chrome.numberWidth(cm)
        r.drawTextRun(chrome.number(cm), center.x - w / 2.0, center.y + (m.ascent - m.descent) / 2.0, chrome.tickFont, chrome.tickLabel)
    }

    /**
     * The two locks (.to-rbtn, TO 97–99): 38 dp raised discs with a 1 dp line3 ring and one soft shadow, Phosphor
     * crosshair-simple / angle at 18 dp in text2; on, a near-black disc (no ring) with the glyph in onSolid.
     */
    private fun drawRulerButtons(r: Renderer, density: Double, chrome: RulerChrome) {
        val radius = ruler.buttonRadiusPx()
        val g = 18.0 * density
        val ringR = radius + 0.5 * density
        for ((btn, c) in ruler.buttonCenters()) {
            val on = when (btn) {
                RulerButton.LOCK_POS -> ruler.lockPosition
                RulerButton.LOCK_ANGLE -> ruler.lockAngle
            }
            r.fillCircle(Pt(c.x, c.y + 1.5 * density), radius + 0.5 * density, chrome.shadow)
            if (on) {
                r.fillCircle(c, radius, chrome.solid)
            } else {
                r.fillCircle(c, radius, chrome.raised)
                r.strokeEllipse(c, ringR, ringR, chrome.lockRingPen)
            }
            val glyph = when (btn) {
                RulerButton.LOCK_POS -> if (on) RulerGlyph.LOCK_POS_ON else RulerGlyph.LOCK_POS
                RulerButton.LOCK_ANGLE -> if (on) RulerGlyph.LOCK_ANGLE_ON else RulerGlyph.LOCK_ANGLE
            }
            r.drawRaster(chrome.glyph(glyph), Rect(c.x - g / 2.0, c.y - g / 2.0, g, g))
        }
    }

    /**
     * A readout pill (.to-ro, TO 102) round [text] ([textW] px wide), centred on ([cx], [cy]) and kept on screen: its
     * soft shadow, its 1 dp line2 ring and its raised body are each one cached polygon, so a translucent fill never
     * doubles up; then the text in system Sans Bold, centred.
     */
    private fun drawReadout(r: Renderer, text: String, textW: Double, cx: Double, cy: Double, density: Double, chrome: RulerChrome) {
        val box = RulerMath.pillRect(cx, cy, textW, density, state.viewportW.toDouble(), state.viewportH.toDouble())
        drawReadout(r, text, textW, box, density, chrome)
    }

    /** [drawReadout] into [box], a pill already placed by [RulerMath.pillRect]. */
    private fun drawReadout(r: Renderer, text: String, textW: Double, box: Rect, density: Double, chrome: RulerChrome) {
        val body = chrome.bodyPill(box.w)
        r.save()
        r.translate(box.x, box.y + 1.5 * density)
        r.fillPolygon(body, chrome.shadow)
        r.restore()
        r.save()
        r.translate(box.x - density, box.y - density)
        r.fillPolygon(chrome.ringPill(box.w), chrome.line2)
        r.restore()
        r.save()
        r.translate(box.x, box.y)
        r.fillPolygon(body, chrome.raised)
        r.restore()
        val m = chrome.readoutMetrics
        r.drawTextRun(text, box.x + (box.w - textW) / 2.0, box.y + box.h / 2.0 + (m.ascent - m.descent) / 2.0, chrome.readoutFont, chrome.text)
    }

    companion object {
        const val MIN_SAMPLE_DIST = 1.0

        /** Most eraser stamps one move may lay between two events (see [eraseAt]). */
        const val MAX_ERASE_STAMPS = 64

        /** Most predicted points read per event; the tail only ever uses a few. */
        const val PREDICTED_POINTS = 6

        /** Pen-up reduction tolerance, viewport px at the draw zoom (see [simplifyForCommit]). */
        const val SIMPLIFY_EPS = 0.2

        /** Scale on the ink low-pass lengths for a stroke drawn at [zoom], captured at pen-down
         *  and carried on the stroke. Screen-space like the capture gate and the pen-up tolerance:
         *  the smoothing hides the digitizer's jitter, which is a fixed size on screen, so writing
         *  small at high zoom must not be smoothed as if it were written large. Capped at 1 so
         *  zooming out cannot smear content detail the page will keep. */
        fun smoothScaleFor(zoom: Double): Double =
            if (zoom.isFinite() && zoom > 0.0) (1.0 / zoom).coerceAtMost(1.0) else 1.0

        /** [cfg] with the brush's end length baked in for a stroke drawn at [zoom]: a hand gesture,
         *  so quoted at 100% and scaled to the page like the other ink lengths. Other pens pass. */
        fun brushEnds(cfg: com.xnotes.core.tools.ToolConfig, zoom: Double): com.xnotes.core.tools.ToolConfig =
            if (!cfg.taperEnabled) cfg
            else cfg.copy(taperLength = com.xnotes.core.stroke.StrokeEngine.BRUSH_TAPER_LEN * smoothScaleFor(zoom))

        const val MOVE_EPS = 0.01

        /** Vendor key some pens send for a genuinely held side button (OnePlus Pad Go 2 Stylo,
         *  which reports a real down/up pair with auto-repeat rather than a momentary click). */
        const val VENDOR_HELD_BUTTON_KEYCODE = KeyEvent.KEYCODE_F21

        /** Stylus side-button bits, widened past the S-Pen primary so pens on the secondary/tertiary lines count too. */
        val STYLUS_BUTTON_MASK =
            MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY

        /** Magic wand: idle time (ms) after the last stroke before the held batch fades. */
        const val WAND_HOLD_MS = 1000L

        /** Magic wand: fade-out duration (ms) once the batch starts disappearing. */
        const val WAND_FADE_MS = 500.0


        /** Ruler: a stylus-down within this (dp) of a long edge snaps the stroke to that edge. */
        const val RULER_SNAP_DP = 12.0

        /** Ruler: finger hit radius (dp) for the on-ruler control buttons. */
        const val RULER_BTN_HIT = 22.0

        /** Ruler: finger hit radius (dp) for the rotation handles. */
        const val RULER_HANDLE_HIT = 24.0

        /** Max finger drift (viewport px) from touch-down still counted as a tap (e.g. tap-to-dismiss). */
        const val TAP_SLOP = 12.0

        /** Padding (content px) added around erased items' bounds when repairing
         *  the cache, to cover stroke anti-aliasing at the dirty-rect edge. */
        const val REPAIR_PAD = 2.0

        /** How far (dp) a lasso tap reaches for an object, so a hairline is not a pixel hunt. */
        const val TAP_REACH_DP = 10.0

        /** Content px a pasted copy lands from the selection it is pasted beside. */
        const val PASTE_NUDGE = 24.0

        /** Touch radius (dp) around a grip centre, the rotate grip's included: larger than the
         *  drawn grip (OverlayTessellator.GRIP_DP) so a fingertip can grab it without the grips
         *  obscuring content. */
        const val HANDLE_HIT = 24.0
        const val SHAPE_MIN_DRAG = 3.0

        /** Shortest strip of tape a pull lays, page px; anything shorter was a fumbled tap. */
        const val TAPE_MIN_LENGTH = 6.0

        /** Min capture size (content px, both axes) for the screenshot tool; below it a drag is a tap. */
        const val SHOT_MIN = 6.0

        /** Min drag (viewport px, either axis) for a text-box gesture to size a box rather than tap-create. */
        const val TEXT_DRAG_SLOP = 14.0

        /** Text point-size clamp for the style bar. */
        const val TEXT_MIN_PT = 6.0
        const val TEXT_MAX_PT = 96.0
        const val LONG_PRESS_MS = 450L
        const val LONG_PRESS_SLOP = 6.0

        /** How long the pen must rest without moving on bare paper before it opens the context menu. */
        const val PEN_HOLD_MS = 600L

        /** Reach (dp) either side of a table's column border that grabs it while a cell is open. */
        const val COL_GRAB_DP = 8.0

        /** Alpha of a table header's shade of its own ink: a hint of tone on any paper. */
        const val HEADER_SHADE_ALPHA = 24

        /** Hold-to-snap: how long the pen must rest (ms) before a freehand stroke snaps to a shape. */
        const val SHAPE_DWELL_MS = 500L

        /** Hold-to-snap: pen drift (viewport px) above which the dwell timer restarts rather than fires. */
        const val SHAPE_DWELL_SLOP = 4.0

        /** Hold-to-snap: minimum samples before recognition is even attempted (mirrors the recognizer). */
        const val SHAPE_MIN_SAMPLES = 8

        /** Shape size -> thickness: the midpoint of the default pen's pressure width range (m=0.35..1.0),
         *  so a shape reads as thick as a same-size pen instead of as a flat full-width line. */
        const val SHAPE_PEN_PARITY = 0.675

        /** A line/arrow drawn with the shape tool snaps flat when within this angle (deg) of an axis.
         *  Half the recognizer's hold-to-snap angle: a live drag is steadier, so it needs less help. */
        const val SHAPE_AXIS_SNAP_DEG = 4.0

        // Inertial fling tuning (viewport px/s).
        const val VEL_SMOOTH = 0.4 // EMA weight on the previous velocity estimate
        const val FLING_FRICTION = 2.5 // higher = stops sooner; lower = floatier
        const val FLING_MIN_START = 120.0 // minimum flick velocity to start a glide
        const val FLING_MIN_STOP = 24.0 // velocity at which the glide ends

        // Elastic overscroll tuning (pull past the bottom end to add a page).
        const val OVERSCROLL_RESIST = 0.5 // fraction of past-end finger travel that becomes stretch (tighter < 1)
        const val OVERSCROLL_MAX = 320.0 // hard cap on the visible stretch (viewport px)
        const val OVERSCROLL_TRIGGER = 250.0 // stretch at which releasing appends a page
        const val OVERSCROLL_SPRING = 14.0 // spring-back rate toward rest (1/s; higher = snappier)

        // Paginated page-flip tuning.
        const val FLIP_RESIST = 0.55 // fraction of past-edge finger travel that becomes pull
        const val FLIP_TRIGGER = 90.0 // pull (viewport px) at which releasing flips the page
        const val FLIP_FLING_VEL = 900.0 // finger velocity (viewport px/s) that flips from the edge
        const val FLIP_MAX_FRACTION = 0.6 // pull cap, as a fraction of the viewport width
    }
}
