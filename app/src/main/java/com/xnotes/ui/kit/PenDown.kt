package com.xnotes.ui.kit

import android.animation.ValueAnimator
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Whether a finger or the pen is on this pane's canvas (`Editor.penDown` / `InfiniteEditor.penDown`),
 * provided per pane by `MainActivity`'s `EditorPane`. B2's rule: nothing in the chrome animates while
 * it is true; an animation due meanwhile snaps, or waits for pen-up.
 *
 * It is a function rather than a value, so providing it never recomposes anything. Call it only where
 * a change cannot recompose: inside `snapshotFlow`, a `LaunchedEffect` (to queue an animation until
 * pen-up), or a `graphicsLayer` / draw lambda. Never call it in composition: that would make the
 * first ink frame of every stroke wait on a recomposition of the chrome.
 */
val LocalPenDown = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * The glider's rule (Toolbar.toolGlide) for any chrome motion, B2 ground rule 1: plays [animate] unless the pen is
 * already down or animations are off, and cuts it short the moment the pen lands mid-way. Either way it ends with
 * [settle], which snaps every value [animate] moves to its end, so whatever follows (a popover taking its window
 * down) runs at once. Call it from an effect, with [penDown] from [LocalPenDown]: it is read here and inside
 * snapshotFlow only. Cancelling the caller (a new target) cancels both, and [settle] does not run.
 */
internal suspend fun animateUnlessPenDown(
    penDown: () -> Boolean,
    settle: suspend () -> Unit,
    animate: suspend CoroutineScope.() -> Unit,
) {
    if (!penDown() && ValueAnimator.areAnimatorsEnabled()) {
        coroutineScope {
            val run = launch(block = animate)
            val pen = launch {
                snapshotFlow { penDown() }.first { it }
                run.cancel()
            }
            run.join()
            pen.cancel()
        }
    }
    settle()
}
