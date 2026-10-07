package com.xnotes.ui.kit

import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.xnotes.ui.POPOVER_MIN_SIDE_DP
import com.xnotes.ui.PopoverPlacer
import com.xnotes.ui.PopoverSide
import com.xnotes.ui.PopoverSpec
import com.xnotes.ui.placePopover
import com.xnotes.ui.popoverMaxHeight
import com.xnotes.ui.theme.InkMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A button's bounds in the app window's px ([boundsInAppWindow]), recorded for an anchored card. Not snapshot state:
 * written on layout, read by the popup's position provider.
 */
@Stable
internal class PopoverAnchor {
    var bounds: IntRect = IntRect.Zero
        internal set
}

/** Records this element's bounds in the app window ([boundsInAppWindow]) into [anchor] on every layout. */
internal fun Modifier.popoverAnchor(anchor: PopoverAnchor): Modifier = this then AppWindowBoundsElement(anchor) { anchor.bounds = it }

/** The bar a card hangs from: its window bounds and the side cards open on. Provided by ToolbarFrame; header buttons provide none. */
@Stable
internal class PopoverEdge {
    var bounds: IntRect = IntRect.Zero
        internal set
    var prefer: PopoverSide = PopoverSide.BELOW
}

/** Records this element's (the bar's) bounds in the app window ([boundsInAppWindow]) into [edge] on every layout. */
internal fun Modifier.popoverEdge(edge: PopoverEdge): Modifier = this then AppWindowBoundsElement(edge) { edge.bounds = it }

/**
 * The root view of the window every Popup composed under here is placed in: the top-level window (the activity's, or
 * a dialog's: InkDialogHost provides its own) that a Popup's window hangs from. A position provider's offset is in
 * that window's px, whichever window the anchor itself is in. [InkPopover] passes it on into its own window; null at
 * the top, where an element's own window is that window.
 */
internal val LocalAppWindowRoot = staticCompositionLocalOf<View?> { null }

/** [screen] (screen px) in the px of a window whose top-left is at [windowOnScreen]. */
internal fun inAppWindow(screen: IntRect, windowOnScreen: IntOffset): IntRect = screen.translate(-windowOnScreen.x, -windowOnScreen.y)

/**
 * This element's bounds in the px of the window its popups are placed in ([LocalAppWindowRoot]; [view] is the
 * element's own, [appRoot] that local). Inside a card's own Popup, window bounds would start at the card's corner, so
 * a picker meant to open beside the card opened over it at the window's top-left: screen bounds less the app window's
 * screen offset are the same px the popup is placed in.
 */
internal fun LayoutCoordinates.boundsInAppWindow(view: View, appRoot: View?): IntRect {
    val token = view.applicationWindowToken
    val root = appRoot?.takeIf { token != null && it.windowToken === token } ?: view.rootView
    val at = IntArray(2)
    root.getLocationOnScreen(at)
    return inAppWindow(boundsOnScreen(), IntOffset(at[0], at[1]))
}

/** Writes this element's [boundsInAppWindow] to [sink] on every layout; equal (no update) for the same [key]. */
private class AppWindowBoundsElement(private val key: Any, private val sink: (IntRect) -> Unit) : ModifierNodeElement<AppWindowBoundsNode>() {
    override fun create() = AppWindowBoundsNode(sink)

    override fun update(node: AppWindowBoundsNode) {
        node.sink = sink
    }

    override fun equals(other: Any?): Boolean = other is AppWindowBoundsElement && other.key === key

    override fun hashCode(): Int = System.identityHashCode(key)
}

private class AppWindowBoundsNode(var sink: (IntRect) -> Unit) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        sink(coordinates.boundsInAppWindow(currentValueOf(LocalView), currentValueOf(LocalAppWindowRoot)))
    }
}

internal val LocalPopoverEdge = staticCompositionLocalOf<PopoverEdge?> { null }

/**
 * The card an [InkPopover] hosts: its bounds, recorded on layout. A picker opened from inside a card opens beside it
 * (Part 5, `ColorPickerPopup`'s `PickerPlace.Auto`). Null outside any card.
 */
internal val LocalHostCard = staticCompositionLocalOf<PopoverAnchor?> { null }

/**
 * The screen bounds of every [InkPopover] card that is up, keyed by the popover and written on layout (not snapshot
 * state; main thread only). The eyedropper's catcher asks it whether a tap landed on a card rather than on the page.
 */
internal object OpenCards {
    private val cards = LinkedHashMap<Any, IntRect>()

    fun put(key: Any, bounds: IntRect) {
        cards[key] = bounds
    }

    fun remove(key: Any) {
        cards.remove(key)
    }

    /** Whether screen px ([x], [y]) is on any open card (right and bottom edges excluded). */
    fun contains(x: Int, y: Int): Boolean = cards.values.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
}

/** This element's bounds on the screen, in px. */
internal fun LayoutCoordinates.boundsOnScreen(): IntRect {
    val p = positionOnScreen()
    val x = p.x.roundToInt()
    val y = p.y.roundToInt()
    return IntRect(x, y, x + size.width, y + size.height)
}

/**
 * How long a closed card stays composed so its exit can play: the alpha tween is 120 ms and the popover spring has
 * visually settled by about 200 ms. CardSlot drops a card this long after it closes.
 */
internal const val POPOVER_EXIT_MS = 200L

/** How a card hangs off its anchor, in dp; see [PopoverSpec] for each field. */
internal data class PopoverSpecDp(val lead: Dp, val gap: Dp, val margin: Dp, val endAligned: Boolean = false)

/** The mockup's anchors (W 1359-1362). */
internal object PopoverSpecs {
    /** Tool cards: start 26 before the button, 10 below the pill. */
    val ToolCard = PopoverSpecDp(lead = 26.dp, gap = 10.dp, margin = 8.dp)

    /** The ⋯ menu: start 8 before the button, 10 below the pill. */
    val MoreMenu = PopoverSpecDp(lead = 8.dp, gap = 10.dp, margin = 8.dp)

    /** Header menus: start 8 before the button, 8 below it. */
    val HeaderMenu = PopoverSpecDp(lead = 8.dp, gap = 8.dp, margin = 8.dp)

    /** Insert: the card's end 6 past the paperclip's end, 8 below it. */
    val Insert = PopoverSpecDp(lead = 6.dp, gap = 8.dp, margin = 8.dp, endAligned = true)
}

internal fun PopoverSpecDp.toPx(density: Density): PopoverSpec =
    with(density) { PopoverSpec(lead.roundToPx(), gap.roundToPx(), margin.roundToPx(), endAligned) }

/** The card's motion: [move] drives the rise and the scale, [fade] the alpha; both 0 (closed) to 1 (open). */
private class PopoverMotion {
    val move = Animatable(0f)
    val fade = Animatable(0f)

    /** Where the card grows from, set by the position provider once it knows the card's place. Read only in graphicsLayer. */
    val origin: MutableState<TransformOrigin> = mutableStateOf(TransformOrigin(0.5f, 0f))

    suspend fun to(target: Float, snap: Boolean) {
        if (snap) {
            move.snapTo(target)
            fade.snapTo(target)
            return
        }
        coroutineScope {
            launch { fade.animateTo(target, tween(InkMotion.FAST, easing = InkMotion.Standard)) }
            move.animateTo(target, InkMotion.popover())
        }
    }

    /**
     * The exit, animated: over when the fade is. The rise's spring tail would run on about 150 ms more with nothing
     * left to see, so it is cut there; the caller then snaps both to 0.
     */
    suspend fun out() {
        coroutineScope {
            val rise = launch { move.animateTo(0f, InkMotion.popover()) }
            fade.animateTo(0f, tween(InkMotion.FAST, easing = InkMotion.Standard))
            rise.cancel()
        }
    }
}

/**
 * The popover window's flags (WindowManager.LayoutParams; the boolean PopupProperties' own, plus one). Open: watches
 * outside touches (an outside tap dismisses), and is focusable unless [focusable] is false. Closing: neither
 * focusable nor touchable, so for the whole exit every touch, a stroke or a second tap on a row, passes through
 * the fading card to whatever is under it.
 */
internal fun popoverWindowFlags(expanded: Boolean, focusable: Boolean): Int = when {
    !expanded -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    focusable -> WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    else -> WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
}

/**
 * A card's act-and-close taps, at most once per open: the first runs; any other before the card opens again (a
 * double tap that lands before the closing card stops taking touches) does nothing. Not for switch rows, which act
 * on every tap while the card stays open.
 */
internal class ActOnce {
    private var spent = false

    /** The card opened (again): its next tap acts. */
    fun rearm() {
        spent = false
    }

    fun run(action: () -> Unit) {
        if (spent) return
        spent = true
        action()
    }
}

/** An [ActOnce] re-armed each time [expanded] turns true. */
@Composable
internal fun rememberActOnce(expanded: Boolean): ActOnce {
    val once = remember { ActOnce() }
    LaunchedEffect(expanded) { if (expanded) once.rearm() }
    return once
}

/**
 * Places the card with [placer] when one is given, else [placePopover], from the recorded anchor and edge (the popup's
 * own parent bounds until they are known).
 */
private class PopoverPosition(
    private val anchor: PopoverAnchor,
    private val edge: PopoverEdge?,
    private val spec: PopoverSpec,
    private val prefer: PopoverSide,
    private val placer: PopoverPlacer?,
    private val origin: MutableState<TransformOrigin>,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val a = anchor.bounds.takeUnless { it == IntRect.Zero } ?: anchorBounds
        val e = edge?.bounds?.takeUnless { it == IntRect.Zero } ?: a
        val p = placer?.place(a, e, windowSize, popupContentSize) ?: placePopover(a, e, windowSize, popupContentSize, spec, prefer)
        val o = TransformOrigin(p.originX, p.originY)
        if (origin.value != o) origin.value = o
        return IntOffset(p.x, p.y)
    }
}

/**
 * An anchored card (B2 motion sheet b): rises 8 dp and scales .97→1 on InkMotion.popover(), fades over 120 ms
 * (tween), from the anchor's centre on the edge it hangs from; the reverse on close. It draws no surface of its own:
 * it places, animates and hosts [content] (a ToolCardFrame, an InkCard or a menu that draws its own surface).
 * Composed once: opening and closing only move the progress, read in graphicsLayer. When [expanded] goes false it
 * keeps its Popup until the fade ends (120 ms), then removes it; when it leaves composition is its caller's call
 * (CardSlot waits [POPOVER_EXIT_MS]). Snaps (no animation) when the pen is down (LocalPenDown) or animations are off,
 * and a pen landing mid-way snaps it to its end ([animateUnlessPenDown]).
 * Placement: ChromeLogic.placePopover with LocalPopoverEdge (else the anchor itself, BELOW); [prefer] overrides the
 * edge's side. Content gets maxHeight = popoverMaxHeight for its side, so a tall card stays under (or over) its bar
 * and scrolls inside. Focusable Popup while open:
 * outside tap and Back call [onDismiss]. While it fades out its window is neither focusable nor touchable
 * ([popoverWindowFlags]), so a tap or a stroke there reaches what is under it, never the closing card.
 *
 * [focusable] false keeps the Popup non-focusable even while open, so it never takes the keyboard (table menus over
 * a text field). It still watches outside touches ([popoverWindowFlags]): an outside tap calls [onDismiss], and, the
 * window not being focusable, that tap also reaches whatever is under it.
 *
 * [placer] replaces [placePopover] for a card that hangs elsewhere (Part 5: beside a card, under a swatch, above a pill,
 * beside a selection); [spec] and [prefer] still set its margin and the side its max height is measured on, unless the
 * placer measures its own ([PopoverPlacer.maxHeight]: the selection menus, which pick their side). The content
 * gets [LocalHostCard] (this card's own bounds, in the app window's px like every anchor) and [LocalAppWindowRoot], and
 * the card's screen bounds are in [OpenCards] while it is up.
 */
@Composable
internal fun InkPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: PopoverAnchor,
    spec: PopoverSpecDp = PopoverSpecs.ToolCard,
    prefer: PopoverSide? = null,
    focusable: Boolean = true,
    placer: PopoverPlacer? = null,
    content: @Composable () -> Unit,
) {
    val motion = remember { PopoverMotion() }
    // This card's own bounds, for a card opened from inside it (LocalHostCard).
    val card = remember { PopoverAnchor() }
    // Whether the Popup is up: from the first open until the exit animation has ended.
    var present by remember { mutableStateOf(expanded) }
    val penDown = LocalPenDown.current
    LaunchedEffect(expanded) {
        // The pen is read in the effect and in snapshotFlow, never in composition: it never recomposes the chrome.
        // Down at the start, the card snaps; landing mid-way, it jumps to the end (B2 ground rule 1).
        if (expanded) {
            present = true
            animateUnlessPenDown(penDown, settle = { motion.to(1f, snap = true) }) { motion.to(1f, snap = false) }
        } else if (present) {
            animateUnlessPenDown(penDown, settle = { motion.to(0f, snap = true) }) { motion.out() }
            // Invisible means gone: no window left over the page.
            present = false
        }
    }
    if (!present) return

    val density = LocalDensity.current
    val edge = LocalPopoverEdge.current
    val side = prefer ?: edge?.prefer ?: PopoverSide.BELOW
    val specPx = remember(spec, density) { spec.toPx(density) }
    val provider = remember(anchor, edge, specPx, side, placer) { PopoverPosition(anchor, edge, specPx, side, placer, motion.origin) }
    val config = LocalConfiguration.current
    val maxHeight = with(density) {
        val window = IntSize(config.screenWidthDp.dp.roundToPx(), config.screenHeightDp.dp.roundToPx())
        // The edge the card hangs from, as its placement reads it (written on layout; not snapshot state).
        val hangsFrom = edge?.bounds?.takeUnless { it == IntRect.Zero } ?: anchor.bounds.takeUnless { it == IntRect.Zero }
        // A placer that picks its own side measures the cap for that side ([PopoverPlacer.maxHeight]).
        (placer?.maxHeight(window) ?: popoverMaxHeight(window, specPx.margin, hangsFrom, specPx.gap, side, POPOVER_MIN_SIDE_DP.dp.roundToPx())).toDp()
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val open by rememberUpdatedState(expanded)
    // The window this card's Popup hangs from, passed on so a card opened from inside this one measures in it too.
    val appRoot = LocalAppWindowRoot.current ?: LocalView.current.rootView
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = { if (open) dismiss() },
        properties = PopupProperties(flags = popoverWindowFlags(expanded, focusable)),
    ) {
        // Off the eyedropper's list as soon as the Popup goes (the exit has ended, or the caller dropped the card).
        DisposableEffect(motion) { onDispose { OpenCards.remove(motion) } }
        // Provided before the Box: the card's own anchor measures in the app window too.
        CompositionLocalProvider(LocalAppWindowRoot provides appRoot) {
            Box(
                Modifier
                    .heightIn(max = maxHeight)
                    // The card's own bounds, before the motion layer so they are its resting place: a picker opened
                    // from inside it opens beside it (LocalHostCard), and the eyedropper's catcher knows a tap there
                    // is not on the page (OpenCards).
                    .popoverAnchor(card)
                    .onGloballyPositioned { OpenCards.put(motion, it.boundsOnScreen()) }
                    .graphicsLayer {
                        val p = motion.move.value
                        val s = 0.97f + 0.03f * p
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 8.dp.toPx()
                        alpha = motion.fade.value.coerceIn(0f, 1f)
                        transformOrigin = motion.origin.value
                        // Alpha per draw, not an offscreen layer: a layer would cut the card's shadow while it fades.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            ) {
                // A fresh window: whatever frame the caller sits in, this content is not inside it.
                CompositionLocalProvider(LocalInCardFrame provides false, LocalHostCard provides card) { content() }
            }
        }
    }
}
