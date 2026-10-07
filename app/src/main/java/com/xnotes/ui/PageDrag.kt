package com.xnotes.ui

import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageOrder

/**
 * The visible grid cells' bounds, in the panel's own pixels, for a drag to find its slot in. Held
 * in growable primitive arrays and refilled in place, so a drag allocates nothing per frame.
 */
internal class SlotBounds {
    var size = 0
        private set
    var index = IntArray(INITIAL)
        private set
    var left = FloatArray(INITIAL)
        private set
    var top = FloatArray(INITIAL)
        private set
    var right = FloatArray(INITIAL)
        private set
    var bottom = FloatArray(INITIAL)
        private set

    fun clear() { size = 0 }

    fun add(i: Int, l: Float, t: Float, r: Float, b: Float) {
        if (size == index.size) grow()
        index[size] = i
        left[size] = l
        top[size] = t
        right[size] = r
        bottom[size] = b
        size++
    }

    private fun grow() {
        val n = index.size * 2
        index = index.copyOf(n)
        left = left.copyOf(n)
        top = top.copyOf(n)
        right = right.copyOf(n)
        bottom = bottom.copyOf(n)
    }

    private companion object { const val INITIAL = 24 }
}

/** The sums behind dragging a page in the panel's grid, tested on the JVM. */
internal object PageDragMath {

    /**
     * The slot under the finger at ([x], [y]): "before page N" (0 until [total]) or [total] for after
     * the last. The row is the nearest one to [y]; within it, the slot is before the first page whose
     * middle is right of [x], else after the row's last page. Writes where its insertion bar stands
     * into [bar] (x, top, bottom): centred in the [gap] beside the page it is anchored to. -1 when
     * nothing is in view.
     */
    fun dropSlot(b: SlotBounds, total: Int, x: Float, y: Float, gap: Float, bar: FloatArray): Int {
        if (b.size == 0) return -1
        var row = 0
        var rowDist = Float.MAX_VALUE
        for (i in 0 until b.size) {
            val d = if (y < b.top[i]) b.top[i] - y else if (y > b.bottom[i]) y - b.bottom[i] else 0f
            if (d < rowDist) { rowDist = d; row = i }
        }
        val rowTop = b.top[row]
        var before = -1
        var last = -1
        for (i in 0 until b.size) {
            if (kotlin.math.abs(b.top[i] - rowTop) > 0.5f) continue
            if (last < 0 || b.index[i] > b.index[last]) last = i
            if (x < (b.left[i] + b.right[i]) / 2f && (before < 0 || b.index[i] < b.index[before])) before = i
        }
        val anchor: Int
        val slot: Int
        if (before >= 0) {
            anchor = before
            slot = b.index[before]
            bar[0] = b.left[before] - gap / 2f
        } else {
            anchor = last
            slot = b.index[last] + 1
            bar[0] = b.right[last] + gap / 2f
        }
        bar[1] = b.top[anchor]
        bar[2] = b.bottom[anchor]
        return slot.coerceIn(0, total.coerceAtLeast(0))
    }

    /**
     * How far to scroll the grid this frame while a dragged page is held at [y] in a [viewport]-tall
     * grid: nothing in the middle, then up to [maxStep] px a frame, rising linearly across the [edge]
     * band at the top (negative, up) or bottom (positive, down), and capped past the edge.
     */
    fun autoScrollStep(y: Float, viewport: Float, edge: Float, maxStep: Float): Float {
        if (edge <= 0f) return 0f
        return when {
            y < edge -> -maxStep * ((edge - y) / edge).coerceAtMost(1f)
            y > viewport - edge -> maxStep * ((y - (viewport - edge)) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }
}

/**
 * What a long-pressed page carries if it is dragged: the page (and the index it had), the pages that
 * move with it, where its cell and thumbnail stood in the panel (px), and how its thumbnail looks.
 */
internal class PageLift(
    val page: Page,
    val index: Int,
    /** The indices that move, in document order: the picked pages when the page was one of them. */
    val moving: List<Int>,
    /** True when the picked pages go with it (every picked cell dims, the ghost shows the count). */
    val group: Boolean,
    val cellLeft: Float,
    val cellTop: Float,
    val thumbLeft: Float,
    val thumbTop: Float,
    val thumbW: Float,
    val thumbH: Float,
    val bitmap: ImageBitmap?,
    val paper: Color,
)

/**
 * A page drag in the side panel's grid. A page's long-press [arm]s it; the panel's pointer handler
 * (which watches every pointer on the way down, before the grid or the cells) lifts it once the
 * finger moves past the touch slop, moves it, and drops or cancels it. Lifting without moving
 * hands back to the long-press as it was (select mode).
 *
 * Frame-rate state is read only where it is drawn: the finger in the ghost's `offset {}`, the bar in
 * a draw block. Composition sees only [lifted], which changes twice a drag.
 */
@Stable
internal class PageDragState(private val gapPx: Float) {
    /** The panel's pointer handler keeps these; nothing composes on them. */
    var pointerDown = false
    var rawX = 0f
    var rawY = 0f
    var panelCoords: LayoutCoordinates? = null

    private var armed: PageLift? = null
    private var onHold: (() -> Unit)? = null
    private var armX = 0f
    private var armY = 0f

    /** Back (or a lost pointer) ended the drag: the rest of the gesture is swallowed and drops nothing. */
    var cancelled = false
        private set

    /** The lifted page, while dragging. */
    var lifted by mutableStateOf<PageLift?>(null)
        private set

    val fingerX = mutableFloatStateOf(0f)
    val fingerY = mutableFloatStateOf(0f)
    var grabX = 0f
        private set
    var grabY = 0f
        private set

    val barShown = mutableStateOf(false)
    val barX = mutableFloatStateOf(0f)
    val barTop = mutableFloatStateOf(0f)
    val barBottom = mutableFloatStateOf(0f)

    private var slot = -1
    private var moving = IntArray(0)
    private val bounds = SlotBounds()
    private var boundsOf: LazyGridLayoutInfo? = null
    private val bar = FloatArray(3)
    // Grid layout offsets -> panel px, measured on the lifted cell itself.
    private var dx = 0f
    private var dy = 0f

    /** Whether [page] (picked or not) is being carried, for its cell to dim. Read in a layer block. */
    fun carries(page: Page, picked: Boolean): Boolean {
        val l = lifted ?: return false
        return page === l.page || (l.group && picked)
    }

    /**
     * A long-press on a page: wait to see whether the finger moves. False when no finger is down
     * (a keyboard or TalkBack long-click), so the caller does what a long-press always did.
     */
    fun arm(lift: PageLift, onHold: () -> Unit): Boolean {
        if (!pointerDown || cancelled || lifted != null) return false
        armed = lift
        this.onHold = onHold
        armX = rawX
        armY = rawY
        return true
    }

    /** The finger moved to ([x], [y]) in panel px. True when the drag owns the event (consume it). */
    fun onMove(x: Float, y: Float, slop: Float, info: LazyGridLayoutInfo): Boolean {
        rawX = x
        rawY = y
        if (cancelled) return true
        if (lifted == null) {
            val a = armed ?: return false
            if (kotlin.math.abs(x - armX) <= slop && kotlin.math.abs(y - armY) <= slop) return false
            lift(a, info)
        }
        fingerX.floatValue = x
        fingerY.floatValue = y
        updateSlot(info)
        return true
    }

    private fun lift(a: PageLift, info: LazyGridLayoutInfo) {
        grabX = armX - a.thumbLeft
        grabY = armY - a.thumbTop
        moving = a.moving.toIntArray().also { it.sort() }
        dx = 0f
        dy = 0f
        val items = info.visibleItemsInfo
        for (i in items.indices) {
            val item = items[i]
            if (item.index == a.index) { dx = a.cellLeft - item.offset.x; dy = a.cellTop - item.offset.y; break }
        }
        boundsOf = null
        slot = -1
        lifted = a
    }

    /** Find the slot under the finger from the cached cell bounds (refilled only when the grid moved). */
    fun updateSlot(info: LazyGridLayoutInfo) {
        if (lifted == null) return
        if (info !== boundsOf) {
            bounds.clear()
            val items = info.visibleItemsInfo
            for (i in items.indices) {
                val item = items[i]
                val l = item.offset.x + dx
                val t = item.offset.y + dy
                bounds.add(item.index, l, t, l + item.size.width, t + item.size.height)
            }
            boundsOf = info
        }
        val s = PageDragMath.dropSlot(bounds, info.totalItemsCount, fingerX.floatValue, fingerY.floatValue, gapPx, bar)
        slot = s
        val show = s >= 0 && !PageOrder.isNoOp(moving, s)
        if (show) {
            barX.floatValue = bar[0]
            barTop.floatValue = bar[1]
            barBottom.floatValue = bar[2]
        }
        if (barShown.value != show) barShown.value = show
    }

    /**
     * The finger lifted. A drag hands back what it carried and the slot it dropped into, for the
     * caller to move; a press that never moved runs the long-press as before. Null when nothing moves.
     */
    fun onUp(): Pair<PageLift, Int>? {
        val l = lifted
        val drop = if (l != null && !cancelled && barShown.value && slot >= 0) l to slot else null
        val hold = if (l == null && !cancelled) onHold.takeIf { armed != null } else null
        reset()
        hold?.invoke()
        return drop
    }

    /** Back, or the gesture was taken away: put the page down where it was. */
    fun cancel() {
        if (lifted == null && armed == null) return
        val down = pointerDown
        reset()
        cancelled = down // swallow the rest of a gesture still in progress
    }

    /** The gesture is over, however it ended. */
    fun gestureEnded() {
        pointerDown = false
        reset()
    }

    private fun reset() {
        armed = null
        onHold = null
        lifted = null
        cancelled = false
        slot = -1
        boundsOf = null
        if (barShown.value) barShown.value = false
    }
}
