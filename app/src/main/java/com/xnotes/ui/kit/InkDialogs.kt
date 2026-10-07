package com.xnotes.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.delay

private val ConfirmBody = InkType.body.copy(fontSize = 14.5.sp, lineHeight = 21.sp)
private val ProgressBody = InkType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp).tnum()

/**
 * A question before something happens (.ps-cf): the title, one line of what will happen, then Cancel
 * and the action at the end. No header bar and no close: Cancel is the way out, and Back or a tap on
 * the scrim cancel too. [danger] makes the action red text on the outlined button, for what cannot
 * be undone; otherwise it is the near-black strong button. [extra] sits before Cancel (the
 * unsaved-changes prompt's Discard).
 */
@Composable
fun InkConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    confirmIcon: ImageVector? = null,
    dismissLabel: String? = null,
    extra: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = LocalInk.current
    InkDialogHost(onDismiss) {
        Column(modifier.widthIn(max = 460.dp).fillMaxWidth().inkSurface(MaterialTheme.shapes.extraLarge, InkElevation.SHEET)) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp)) {
                Text(title, style = InkType.sheetTitle, color = ink.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(message, style = ConfirmBody, color = ink.text2)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                extra?.invoke(this)
                InkGhostButton(dismissLabel ?: stringResource(R.string.cancel), onDismiss)
                if (danger) InkSecondaryButton(confirmLabel, onConfirm, icon = confirmIcon, danger = true)
                else InkStrongButton(confirmLabel, onConfirm, icon = confirmIcon)
            }
        }
    }
}

/** How long work that cannot be cancelled holds the screen before "Keep in background" shows. */
const val PROGRESS_BACKGROUND_AFTER_MS = 15_000L

/** What Back (and only Back) does on a progress sheet. */
enum class ProgressSheetBack(val closes: Boolean) {
    /** Stops the work: the sheet has a Cancel. */
    CANCEL(true),

    /** Hides the sheet and lets the work run on: "Keep in background" is showing. */
    BACKGROUND(true),

    /** Nothing yet: the work cannot be cancelled and the offer has not shown. */
    NONE(false),
    ;

    companion object {
        fun of(cancellable: Boolean, backgroundOffered: Boolean): ProgressSheetBack = when {
            cancellable -> CANCEL
            backgroundOffered -> BACKGROUND
            else -> NONE
        }
    }
}

/**
 * Work that takes a while (.ps-progd): a bold title, a line saying where it has got to, a 4dp
 * near-black bar, then Cancel. [fraction] null runs the bar as a sliding segment (nothing to count).
 * With [onCancel] null there is no Cancel, and neither Back nor the scrim closes it (saving, inserting).
 * Such work can still pass [onBackground]: once it has run [backgroundAfterMs], a "Keep in
 * background" ghost button fades in, and it or Back hides the sheet without stopping the work. The
 * caller does the hiding, and shows the sheet afresh when the work next starts.
 */
@Composable
fun InkProgressSheet(
    title: String,
    line: String,
    fraction: Float?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBackground: (() -> Unit)? = null,
    backgroundAfterMs: Long = PROGRESS_BACKGROUND_AFTER_MS,
) {
    val ink = LocalInk.current
    val canBackground = onCancel == null && onBackground != null
    var offered by remember { mutableStateOf(false) }
    if (canBackground) {
        // One timer for the sheet's life: a coroutine delay, not a per-frame clock.
        LaunchedEffect(backgroundAfterMs) {
            delay(backgroundAfterMs)
            offered = true
        }
    }
    val back = ProgressSheetBack.of(cancellable = onCancel != null, backgroundOffered = canBackground && offered)
    InkDialogHost(
        onDismiss = {
            when (back) {
                ProgressSheetBack.CANCEL -> onCancel?.invoke()
                ProgressSheetBack.BACKGROUND -> onBackground?.invoke()
                ProgressSheetBack.NONE -> Unit
            }
        },
        dismissOnScrim = onCancel != null,
        dismissOnBack = back.closes,
    ) {
        val button = onCancel != null || (canBackground && offered)
        Column(modifier.widthIn(max = 380.dp).fillMaxWidth().inkSurface(MaterialTheme.shapes.extraLarge, InkElevation.SHEET)) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (button) 4.dp else 24.dp)) {
                Text(title, style = InkType.cardTitle, color = ink.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(line, style = ProgressBody, color = ink.text2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                InkProgressBar(fraction, Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
            if (onCancel != null) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp), horizontalArrangement = Arrangement.End) {
                    InkGhostButton(stringResource(R.string.cancel), onCancel)
                }
            } else if (canBackground && offered && onBackground != null) {
                // Opacity only: the button arrives in place rather than sliding in.
                val shown = remember { Animatable(0f) }
                LaunchedEffect(Unit) { shown.animateTo(1f, InkMotion.fade()) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)
                        .graphicsLayer { alpha = shown.value },
                    horizontalArrangement = Arrangement.End,
                ) {
                    InkGhostButton(stringResource(R.string.keep_in_background), onBackground)
                }
            }
        }
    }
}

/**
 * The 4dp progress bar (.ps-pbar): a near-black fill on the --track colour, never marigold (marigold
 * is for reading progress only). A [fraction] glides to each new value over 180 ms; null slides a
 * 30% segment across, for work with nothing to count. Only the draw pass reads either animation.
 */
@Composable
fun InkProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val shown = animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(InkMotion.BASE, easing = LinearEasing), label = "progress")
    val sweep = if (fraction == null) {
        rememberInfiniteTransition(label = "progressSweep").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "sweep")
    } else null
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { progressBarRangeInfo = if (fraction != null) ProgressBarRangeInfo(fraction, 0f..1f) else ProgressBarRangeInfo.Indeterminate },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(ink.track, cornerRadius = r)
        if (sweep != null) {
            val w = size.width * 0.3f
            val x = sweep.value * (size.width + w) - w
            clipRect { drawRoundRect(ink.solid, Offset(x, 0f), Size(w, size.height), r) }
        } else {
            drawRoundRect(ink.solid, size = Size(size.width * shown.value, size.height), cornerRadius = r)
        }
    }
}
