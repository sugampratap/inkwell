package com.xnotes.ui.kit

import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import kotlinx.coroutines.launch

/** The tallest a sheet grows: 90% of the screen, so some scrim always shows above and below it. */
internal fun sheetMaxHeight(screenHeight: Dp): Dp = screenHeight * 0.9f

/** A fixed sheet height kept within [sheetMaxHeight]; null lets the sheet wrap its content. */
internal fun sheetHeight(requested: Dp?, screenHeight: Dp): Dp? = requested?.let { minOf(it, sheetMaxHeight(screenHeight)) }

/** Below this card width a sheet's header actions leave the title's row for a row of their own. */
internal val SHEET_HEADER_STACK_BELOW = 600.dp

/** Whether a card [cardWidth] wide puts its header actions under the title. */
internal fun sheetHeaderStacks(cardWidth: Dp): Boolean = cardWidth < SHEET_HEADER_STACK_BELOW

/**
 * The dim for a modal window opened while [othersOpen] are already up. Each window dims everything
 * behind it, so a second one at full strength would double the dark; it adds a little instead.
 */
internal fun scrimDim(base: Float, othersOpen: Int): Float = if (othersOpen > 0) base * STACKED_DIM else base

private const val STACKED_DIM = 0.4f

/** How many [InkDialogHost]s are up (main thread only: composition and its effects). */
private var openDialogHosts = 0

/** Where the card is, so the scrim can tell a lift on the card from one on the scrim. Not state: read only in input. */
private class ScrimHit {
    var outer: LayoutCoordinates? = null
    var card: LayoutCoordinates? = null

    fun onCard(at: Offset): Boolean {
        val o = outer ?: return false
        val c = card ?: return false
        if (!o.isAttached || !c.isAttached) return false
        return o.localBoundingBoxOf(c).contains(at)
    }
}

/**
 * The window every B2 modal lives in. It covers the whole screen, so a sheet's 28dp shadow is never
 * cut at the window's edge. The scrim is the window's dim (32%, 62% in dark themes; less for a modal
 * opened over another, see [scrimDim]); a tap on it is caught here, in Compose, and dismisses when
 * [dismissOnScrim], but only when it both starts and ends on the scrim. The card ([content]) rises 8dp,
 * grows from .985 and fades in (transform and alpha only), and swallows its own downs so they never
 * reach the scrim. Back dismisses when [dismissOnBack]. The card stays inside the safe drawing area,
 * so the keyboard pushes it up.
 */
@Composable
fun InkDialogHost(
    onDismiss: () -> Unit,
    dismissOnScrim: Boolean = true,
    dismissOnBack: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ink = LocalInk.current
    // Read once, as it opens: a modal over another dims less, so the two together are not twice as dark.
    val othersOpen = remember { openDialogHosts }
    DisposableEffect(Unit) {
        openDialogHosts++
        onDispose { openDialogHosts-- }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBack,
            // The window is the whole screen, so nothing is "outside": the scrim tap below decides.
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val dim = scrimDim(ink.scrim.alpha, othersOpen)
        // Only when the window or the dim changes: both calls relayout the window.
        DisposableEffect(window, dim) {
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setDimAmount(dim)
                @Suppress("DEPRECATION")
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                // The focused window decides the bars: without this a dialog brings them back in full screen.
                followAppSystemBars()
            }
            onDispose {}
        }
        val rise = remember { Animatable(0f) }
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { fade.animateTo(1f, InkMotion.fade()) }
            rise.animateTo(1f, InkMotion.popover())
        }
        val dismiss by rememberUpdatedState(onDismiss)
        val scrimDismisses by rememberUpdatedState(dismissOnScrim)
        val hit = remember { ScrimHit() }
        Box(
            Modifier
                .fillMaxSize()
                .onPlaced { hit.outer = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // The card takes its own downs, so this down is on the scrim; the lift must be too.
                        awaitFirstDown()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (scrimDismisses && !hit.onCard(up.position)) dismiss()
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = fade.value
                        translationY = (1f - rise.value) * 8.dp.toPx()
                        val s = 0.985f + 0.015f * rise.value
                        scaleX = s
                        scaleY = s
                    }
                    .onPlaced { hit.card = it }
                    // The card's children see each down first; whatever they leave, the card takes, so
                    // the scrim's tap detector (which wants an unconsumed down) never fires through it.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } },
            ) { DialogLocals(content) }
        }
    }
}

/**
 * What a dialog's content must not inherit from the screen it opened over: a card or a bar to hang from (a sheet is
 * not a card: its pickers hang from their own dots), the canvas under it (it is hidden behind the scrim, so a picker
 * here has no eyedropper), and the window popups are placed in (this dialog's, not the activity's).
 */
@Composable
internal fun DialogLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppWindowRoot provides LocalView.current.rootView,
        LocalHostCard provides null,
        LocalPopoverEdge provides null,
        com.xnotes.ui.LocalCanvasHost provides null,
        content = content,
    )
}

/**
 * Whether the app has hidden the system bars (full screen). MainActivity keeps it; main thread only. A dialog's window
 * takes the bars over while it is focused, so each one hides them too ([followAppSystemBars]).
 */
internal object AppSystemBars {
    var hidden = false
}

/** This (dialog) window's system bars as the app has them: hidden, swiped in transiently, while the app is full screen. */
internal fun Window.followAppSystemBars() {
    if (!AppSystemBars.hidden) return
    val controller = WindowInsetsControllerCompat(this, decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
}

/**
 * B2's modal sheet (.msheet): a centred card (the theme's extraLarge corners, r28 at Rounded) over
 * the scrim. A 68dp header holds an optional [navigation] (a back arrow), the title and an optional
 * subtitle (with [subtitleIcon]), [headerActions] and, when [showClose], the close button; then the
 * body; then an optional footer bar ([footer] lays out its own buttons, primary last).
 *
 * The card is [width] wide at most. With a [height] it is that tall (within 90% of the screen) and
 * the body fills it; otherwise it wraps its content up to 90%. The body scrolls unless
 * [bodyScrolls] is false, for sheets that arrange their own scrolling panes (Page setup).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleIcon: ImageVector? = null,
    width: Dp = 560.dp,
    height: Dp? = null,
    dismissOnScrim: Boolean = true,
    showClose: Boolean = true,
    bodyScrolls: Boolean = true,
    footerMinHeight: Dp = 0.dp,
    navigation: (@Composable () -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    InkDialogHost(onDismiss, dismissOnScrim = dismissOnScrim) {
        val screen = LocalConfiguration.current.screenHeightDp.dp
        val fixed = sheetHeight(height, screen)
        Column(
            modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .then(if (fixed != null) Modifier.height(fixed) else Modifier.heightIn(max = sheetMaxHeight(screen)))
                .inkSurface(MaterialTheme.shapes.extraLarge, InkElevation.SHEET),
        ) {
            // On a narrow card the header actions get a row of their own under the title, so they
            // never squeeze it to nothing.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(ink.line2, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    },
            ) {
                val stackActions = headerActions != null && sheetHeaderStacks(maxWidth)
                val start = if (navigation != null) 14.dp else 24.dp
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(start = start, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        navigation?.invoke()
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(title, style = InkType.sheetTitle, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                            if (subtitle != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (subtitleIcon != null) Icon(subtitleIcon, null, tint = ink.text2, modifier = Modifier.size(15.dp))
                                    Text(subtitle, style = InkType.meta, color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (!stackActions) headerActions?.invoke(this)
                        if (showClose) InkIconButton(Ph.x, stringResource(R.string.kit_close), onDismiss, iconSize = 18.dp)
                    }
                    if (stackActions) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = start, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) { headerActions?.invoke(this) }
                    }
                }
            }
            val fill = if (fixed != null) Modifier.weight(1f) else Modifier.weight(1f, fill = false)
            Column(if (bodyScrolls) fill.verticalScroll(rememberScrollState()) else fill, content = body)
            if (footer != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = footerMinHeight)
                        .drawBehind { drawLine(ink.line2, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = footer,
                )
            }
        }
    }
}
