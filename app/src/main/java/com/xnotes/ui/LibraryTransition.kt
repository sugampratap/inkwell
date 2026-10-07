package com.xnotes.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * A note opening from the library: where its cover or card sat (window px, unclipped), the page's shape, the page
 * image, and what to draw over it at first ([art], the cover, fading as the page shows), or null when the source
 * already showed this page (a Continue writing card), so it shows from the start.
 */
internal class OpeningNote(
    val from: Rect,
    val pageRatio: Float,
    val page: ImageBitmap?,
    val art: (@Composable () -> Unit)?,
)

/** The library → editor hand-off. One per process, like the library marks; main thread only. */
@Stable
internal object LibraryOpen {
    var opening: OpeningNote? by mutableStateOf(null)
        private set

    /** Continue writing cards by note uri, for the rect an opening grows from (shelf tiles register with their TileHost). */
    val cards = HashMap<String, LayoutCoordinates>()

    /** Starts the overlay, unless there is nothing to grow from or the system has animations off ("Remove animations"). */
    fun begin(from: Rect?, pageRatio: Float, page: ImageBitmap?, art: (@Composable () -> Unit)?) {
        if (from == null || from.isEmpty || !ValueAnimator.areAnimatorsEnabled()) return
        opening = OpeningNote(from, pageRatio, page, art)
    }

    fun end() {
        opening = null
    }
}

/** The page's own corners (.pg, r2), laid out at the page's size; one instance, so the layer block allocates nothing per frame. */
private val OpeningShape = RoundedCornerShape(2.dp)

/**
 * Drawn over everything, after the editor panes (Motion sheet e). The layer is laid out where the page lands and
 * starts scaled down onto the cover, one scale both ways, so the page keeps its shape as it grows; only its
 * transform, its alpha and how much of it shows (a clip, drawn) animate, so nothing recomposes or relayouts while
 * it plays. The cover's art is laid out at the cover's own size and scaled up with it, so it starts exactly as the
 * shelf showed it. It holds on the page until the note is open (2.5 s at most, for a slow open), then fades; an open
 * that fails ends it at once. It has no pointer input, so touches always reach the editor beneath it.
 */
@Composable
internal fun LibraryOpenTransition(editor: Editor) {
    val o = LibraryOpen.opening ?: return
    val paper = LocalPalette.current.paper.toComposeColor()
    val grow = remember(o) { Animatable(0f) }
    val fade = remember(o) { Animatable(1f) }
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(o) {
        val message = editor.message
        var reading = false
        // 1 opened, -1 failed, 0 still going. A note's read raises `opening` and lowers it again, with the note open
        // or an error said; a canvas says nothing when it fails, so it ends on the time limit.
        val outcome = coroutineScope {
            val growing = launch { grow.animateTo(1f, InkMotion.screen()) }
            val r = withTimeoutOrNull(2_500) {
                snapshotFlow {
                    if (editor.opening) reading = true
                    when {
                        editor.noteOpen || editor.canvasOpen -> 1
                        reading && !editor.opening -> -1
                        editor.message != null && editor.message != message -> -1
                        else -> 0
                    }
                }.first { it != 0 }
            } ?: 0
            if (r < 0) growing.cancel() else growing.join()
            r
        }
        // No cheap signal says a finger is down on the editor beneath, and the ink path is not to be touched for
        // one, so a touch during the hold does not cut it short; the hold ends as soon as the note is open.
        if (outcome >= 0) fade.animateTo(0f, tween(InkMotion.FAST, easing = InkMotion.Standard))
        LibraryOpen.end()
    }
    BoxWithConstraints(Modifier.fillMaxSize().onPlaced { box = it }) {
        val coords = box ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val area = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val to = with(density) { pageLanding(area, o.pageRatio, top = 56.dp.toPx(), margin = 24.dp.toPx()) }
        val from = Rect(coords.windowToLocal(o.from.topLeft), o.from.size)
        val move = remember(from, to) { pageOpen(from, to) }
        val float = with(density) { InkElevation.FLOAT.shadow.toPx() }
        Box(
            Modifier
                // Window px, not start/end: the rects are absolute, so a right-to-left layout must not mirror them.
                .absoluteOffset { IntOffset(to.left.roundToInt(), to.top.roundToInt()) }
                .size(with(density) { to.width.toDp() }, with(density) { to.height.toDp() })
                .graphicsLayer {
                    val t = grow.value
                    val s = move.scale(t)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = move.dx(t)
                    translationY = move.dy(t)
                    alpha = fade.value
                    // The layer's outline is the whole page while its foot is still clipped away, so its one shadow
                    // comes in as the page lands rather than hang under what is not yet shown.
                    shadowElevation = float * t * t * t * t
                    shape = OpeningShape
                    clip = true
                }
                .drawWithContent {
                    val shown = move.reveal(grow.value)
                    if (shown >= size.height) drawContent() else clipRect(bottom = shown) { this@drawWithContent.drawContent() }
                },
        ) {
            val art = o.art
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (art != null) Modifier.graphicsLayer { alpha = grow.value } else Modifier)
                    .background(paper),
            ) {
                if (o.page != null) Image(o.page, null, contentScale = ContentScale.Fit, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
            if (art != null) {
                // The cover at its own size, scaled up as one with the page, so it starts as the shelf drew it.
                val up = to.width / from.width
                Box(
                    Modifier
                        .size(with(density) { from.width.toDp() }, with(density) { from.height.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = up
                            scaleY = up
                            alpha = 1f - grow.value
                        },
                ) { art() }
            }
        }
    }
}
