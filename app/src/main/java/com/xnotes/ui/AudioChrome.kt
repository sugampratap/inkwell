package com.xnotes.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.AudioItem
import com.xnotes.settings.PlaybackSpeed
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkConfirmSheet
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuHeader
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecDp
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The note's audio chrome (r3_audio): the recorder capsule in the header, and (Task 12) the player pill at the
 * bottom with its speed chip and the tap-to-seek ring. Every per-tick value (clock, level, play head) is read in a
 * leaf composable or a draw lambda, so a tick never recomposes the header or the pill.
 */

/** The recorder clock (.au-rt b): 15/18 ExtraBold, −0.1, tabular. */
private val RecClockStyle = InkType.row.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.1).sp).tnum()

/** The line under the clock (.au-rt span): 11.5/14 SemiBold. */
private val RecSubStyle = InkType.small.copy(fontSize = 11.5.sp, lineHeight = 14.sp)

/** The only red on screen (AU 24-25), private to the recorder (round-3 defaults, Part 7 row 10). */
private val RecRedLight = Color(0xFFE0362C)
private val RecRedDark = Color(0xFFFF5A4E)

/** The live dot's breath (AU 24): cubic-bezier(.4, 0, .6, 1), 700 ms each way. */
private val PulseEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

/** 20 bars of 3 dp, 2 apart (.au-meter). */
private val TRACE_W = 3.dp * TRACE_BARS + 2.dp * (TRACE_BARS - 1)

/** A surface's way in and out (.au-rec, .au-player): opacity on Standard and the move on Glide, both 180 ms. */
private class Reveal(initial: Float) {
    val move = Animatable(initial)
    val fade = Animatable(initial)

    suspend fun to(target: Float, snap: Boolean) {
        if (snap) {
            move.snapTo(target)
            fade.snapTo(target)
            return
        }
        coroutineScope {
            launch { fade.animateTo(target, tween(InkMotion.BASE, easing = InkMotion.Standard)) }
            move.animateTo(target, tween(InkMotion.BASE, easing = InkMotion.Glide))
        }
    }
}

/**
 * The recorder (AU Frame 1, .au-rec): a second capsule in the note's header while a voice recording runs, with the
 * live dot, the clock, a rolling trace of the last two seconds or so of microphone level, Pause / Resume, Stop and
 * insert, and Discard. NoteHeader places it straight after the title block (Part 3's `recorder` slot). In a pane
 * under 1000 dp it is compact: no trace, no "Recording" line, and the clock keeps no 60 dp floor, about 225 dp in all
 * (so it leaves a 640 dp split header room). The pane's width comes from the width the header offers the capsule
 * ([recorderCompact]), so opening or closing the split, or turning the device, flips it at once.
 */
@Composable
internal fun RecorderCapsule(editor: Editor) {
    val media = editor.media
    val active = media.recordingActive
    val penDown = LocalPenDown.current
    val reveal = remember { Reveal(if (active) 1f else 0f) }
    var present by remember { mutableStateOf(active) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        // Read here, not in composition: the pen-down signal never recomposes the header.
        val snap = penDown() || !ValueAnimator.areAnimatorsEnabled()
        if (active) {
            present = true
            reveal.to(1f, snap)
        } else if (present) {
            reveal.to(0f, snap)
            present = false
        }
    }
    if (present) BoxWithConstraints {
        val ink = LocalInk.current
        val compact = recorderCompact(maxWidth.value)
        val paused = media.recordingPaused
        Row(
            Modifier
                .graphicsLayer {
                    alpha = reveal.fade.value.coerceIn(0f, 1f)
                    val m = reveal.move.value
                    translationY = (1f - m) * -6.dp.toPx()
                    val s = 0.97f + 0.03f * m
                    scaleX = s
                    scaleY = s
                }
                .height(48.dp)
                .background(ink.chrome, CircleShape)
                .border(1.dp, ink.line, CircleShape)
                .padding(start = 18.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(paused)
            Column(Modifier.padding(start = 10.dp).then(if (compact) Modifier else Modifier.widthIn(min = 60.dp))) {
                RecClock(media)
                if (!compact) {
                    Text(
                        stringResource(if (paused) R.string.recording_paused else R.string.recording),
                        style = RecSubStyle,
                        color = ink.text2,
                    )
                }
            }
            if (compact) Spacer(Modifier.width(8.dp)) else LevelTrace(media)
            if (paused) {
                InkIconButton(Ph.play, stringResource(R.string.recording_resume), { media.resumeRecording() }, iconSize = 21.dp)
            } else {
                InkIconButton(Ph.pause, stringResource(R.string.recording_pause), { media.pauseRecording() }, iconSize = 21.dp)
            }
            StopButton { media.stopRecording(insert = true) }
            Box(Modifier.padding(horizontal = 3.dp).size(1.dp, 20.dp).background(ink.line))
            InkIconButton(Ph.trash, stringResource(R.string.recording_cancel), { confirmDiscard = true }, iconSize = 21.dp, tint = ink.text2)
        }
    }
    if (confirmDiscard) {
        InkConfirmSheet(
            title = stringResource(R.string.recording_discard_title),
            message = stringResource(R.string.recording_discard_text),
            confirmLabel = stringResource(R.string.discard_recording),
            onConfirm = {
                confirmDiscard = false
                media.stopRecording(insert = false)
            },
            onDismiss = { confirmDiscard = false },
            danger = true,
            confirmIcon = Ph.trash,
        )
    }
}

/** The live dot (.au-dot): breathes while recording, steady at .35 while paused, steady and full while the pen is down. */
@Composable
private fun LiveDot(paused: Boolean) {
    val ink = LocalInk.current
    val red = if (ink.isDark) RecRedDark else RecRedLight
    val penDown = LocalPenDown.current
    val a = remember { Animatable(1f) }
    LaunchedEffect(paused) {
        if (paused) {
            a.snapTo(0.35f)
            return@LaunchedEffect
        }
        snapshotFlow { penDown() }.collectLatest { down ->
            a.snapTo(1f)
            if (down || !ValueAnimator.areAnimatorsEnabled()) return@collectLatest
            while (true) {
                a.animateTo(0.25f, tween(700, easing = PulseEasing))
                a.animateTo(1f, tween(700, easing = PulseEasing))
            }
        }
    }
    Box(Modifier.size(8.dp).graphicsLayer { alpha = a.value }.background(red, CircleShape))
}

/** The clock: recomposes once a second, not per 120 ms tick. */
@Composable
private fun RecClock(media: NoteAudio) {
    val text by remember(media) { derivedStateOf { recorderClock(media.recordingElapsedMs) } }
    Text(text, style = RecClockStyle, color = LocalInk.current.text)
}

/**
 * The rolling trace (.au-meter): bar 0 the oldest at .28 opacity, bar 19 the newest at full, each `max(.12, level)`
 * of 24 dp from its centre; all at .12 while paused. Drawn only: a new level redraws this canvas (levelTick) and
 * nothing else, about 8 times a second, and that goes on while the pen is down (round-3 defaults, Part 7 row 4).
 */
@Composable
private fun LevelTrace(media: NoteAudio) {
    val ink = LocalInk.current
    Canvas(Modifier.padding(start = 8.dp, end = 10.dp).size(TRACE_W, 28.dp)) {
        @Suppress("UNUSED_EXPRESSION") media.levelTick // draw-only read: a new level redraws, nothing recomposes
        val paused = media.recordingPaused
        val bw = 3.dp.toPx()
        val step = bw + 2.dp.toPx()
        val full = 24.dp.toPx()
        for (i in 0 until TRACE_BARS) {
            val h = full * traceScale(media.levelHistory[i], paused)
            drawRoundRect(
                ink.text.copy(alpha = ink.text.alpha * traceAlpha(i)),
                Offset(i * step, (size.height - h) / 2f),
                Size(bw, h),
                CornerRadius(min(2.dp.toPx(), h / 2f)),
            )
        }
    }
}

/** Stop and insert (.au-stop): a 36 dp solid disc with stop-fill in a 44 dp target; presses to .92. */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val label = stringResource(R.string.recording_stop)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(src, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).pressScale(src, 0.92f).background(ink.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Ph.stopFill, null, tint = ink.onSolid, modifier = Modifier.size(15.dp))
        }
    }
}

// --- the player ---

/** The player's title (.au-pt b): 15/20 Bold, −0.1. */
private val PlayerTitleStyle = InkType.rowStrong.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp)

/** The sync hint (.au-pt span): 12.5/17 Medium. */
private val PlayerHintStyle = InkType.hint.copy(lineHeight = 17.sp)

/** Elapsed and total time (.au-tm): 13 SemiBold, tabular. */
private val PlayerTimeStyle = InkType.counter.tnum()

/** The speed chip (.au-spd): 13 Bold tabular; 800 while not 1×. */
private val SpeedChipStyle = InkType.label.tnum()
private val SpeedChipOnStyle = InkType.label.copy(fontWeight = FontWeight.ExtraBold).tnum()

/** The speed menu's rows (.au-spdm .m-row): the menu row type, tabular; bold for the current one. */
private val SpeedRowStyle = InkType.row.tnum()
private val SpeedRowOnStyle = InkType.row.copy(fontWeight = FontWeight.Bold).tnum()

/** The speed menu's footer (.au-spdf): 12/17 Medium. */
private val SpeedNoteStyle = InkType.small.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)

/** The speed menu opens upward off the chip: its end 6 past the chip's, 14 above it (AU 61); it grows from the chip. */
private val SpeedMenuSpec = PopoverSpecDp(lead = 6.dp, gap = 14.dp, margin = 8.dp, endAligned = true)

/**
 * The note's audio overlay, inside the page's box (MainActivity): the tap-to-seek ring and the player pill. Neither
 * takes a touch it does not draw a control for.
 */
@Composable
internal fun AudioBars(editor: Editor) {
    val media = editor.media
    Box(Modifier.fillMaxSize()) {
        SeekRingLayer(media)
        PlayerPill(editor, media, Modifier.align(Alignment.BottomCenter))
    }
}

/** The scrub's drag: while held, the time shows the knob's place, and the seek happens on release. */
private class Scrub {
    var dragging by mutableStateOf(false)
    var fraction by mutableFloatStateOf(0f)
}

/**
 * The player (AU Frame 2, .au-player): the bottom pill while a chip plays, hidden while a recording runs. It sits
 * 16 dp up, or 10 over the text format pill (BottomStack), read while laying out, so the pill coming and going moves
 * the player without recomposing it.
 *
 * The format pill counts as shown while [formatPillReserve] is above 0 dp (as in [toastBottomFor]). The player reads
 * it itself, so a tool switch or the keyboard re-runs only the player, not the page's box around it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPill(editor: Editor, media: NoteAudio, modifier: Modifier) {
    val visible = media.playerItem != null && !media.recordingActive
    val penDown = LocalPenDown.current
    val reveal = remember { Reveal(if (visible) 1f else 0f) }
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        val snap = penDown() || !ValueAnimator.areAnimatorsEnabled()
        if (visible) {
            present = true
            reveal.to(1f, snap)
        } else if (present) {
            reveal.to(0f, snap)
            present = false
        }
    }
    // The last item stays on the pill while it animates out.
    val last = remember { arrayOfNulls<AudioItem>(1) }
    media.playerItem?.let { last[0] = it }
    val item = last[0]
    if (!present || item == null) return
    val ink = LocalInk.current
    val imeUp = WindowInsets.isImeVisible
    // What a floating bottom bar covers: snapshot-backed, the same source as formatPillReserve, so the bar moving
    // or coming up moves the player.
    val cover = LocalToolbarCover.current.calculateBottomPadding().value.roundToInt()
    val pillUp = formatPillReserve(editor) > 0.dp
    val scrub = remember { Scrub() }
    InkPill(
        modifier
            .offset {
                IntOffset(0, -playerBottomDp(pillUp, imeUp, cover).dp.roundToPx())
            }
            .graphicsLayer {
                alpha = reveal.fade.value.coerceIn(0f, 1f)
                translationY = (1f - reveal.move.value) * 8.dp.toPx()
            }
            .padding(horizontal = 8.dp)
            .widthIn(max = 772.dp)
            .fillMaxWidth(),
        padding = 8.dp,
    ) {
        PlayPause(media)
        Column(Modifier.padding(start = 12.dp).widthIn(max = 236.dp).weight(1f)) {
            Text(item.title, style = PlayerTitleStyle, color = ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.playerSynced) {
                Text(
                    stringResource(R.string.player_sync_hint),
                    style = PlayerHintStyle,
                    color = ink.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ElapsedTime(media, scrub)
        ScrubBar(media, scrub, Modifier.weight(1f).padding(horizontal = 12.dp))
        Text(
            AudioItem.formatDuration(media.playerDurationMs),
            style = PlayerTimeStyle,
            color = ink.text2,
            modifier = Modifier.width(44.dp),
        )
        SpeedChip(editor, media)
        InkIconButton(Ph.x, stringResource(R.string.player_close), { media.closePlayer() }, iconSize = 18.dp, tint = ink.text2)
    }
}

/** Play / Pause (.au-pp): a 48 dp solid disc, never the accent; presses to .94. */
@Composable
private fun PlayPause(media: NoteAudio) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val playing = media.playerPlaying
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        Modifier
            .size(48.dp)
            .pressScale(src, 0.94f)
            .clip(CircleShape)
            .background(ink.solid)
            .clickable(src, null, role = Role.Button) { if (media.playerPlaying) media.pause() else media.resume() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Ph.pauseFill else Ph.playFill, null, tint = ink.onSolid, modifier = Modifier.size(20.dp))
    }
}

/** Elapsed time: recomposes once a second (or with the drag), not per 40 ms tick. */
@Composable
private fun ElapsedTime(media: NoteAudio, scrub: Scrub) {
    val shown by remember(media, scrub) {
        derivedStateOf {
            AudioItem.formatDuration(scrubShownMs(scrub.dragging, scrub.fraction, media.playerPositionMs, media.playerDurationMs))
        }
    }
    Text(shown, style = PlayerTimeStyle, color = LocalInk.current.text2, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
}

/** The scrub: the only part of the pill that follows the play head; held, it shows the knob, and it seeks on release. */
@Composable
private fun ScrubBar(media: NoteAudio, scrub: Scrub, modifier: Modifier) {
    val value by remember(media, scrub) {
        derivedStateOf {
            if (scrub.dragging) {
                scrub.fraction
            } else {
                val f = (media.playerPositionMs.toFloat() / media.playerDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                (f * 1000f).roundToInt() / 1000f
            }
        }
    }
    InkSlider(
        value = value,
        range = 0f..1f,
        modifier = modifier,
        onChangeFinished = {
            if (scrub.dragging) media.seekTo((scrub.fraction * media.playerDurationMs.coerceAtLeast(1L)).toLong())
            scrub.dragging = false
        },
    ) {
        scrub.dragging = true
        scrub.fraction = it
    }
}

/**
 * The speed chip (NEW; AU 55-60): "1×" in a ringed chip, ringed solid and heavier at any other speed. It opens the
 * speed menu upward. A pick is persisted process-wide and applied live (Editor.setPlaybackSpeedPref).
 */
@Composable
private fun SpeedChip(editor: Editor, media: NoteAudio) {
    val ink = LocalInk.current
    var open by remember { mutableStateOf(false) }
    val anchor = remember { PopoverAnchor() }
    val src = remember { MutableInteractionSource() }
    val speed = media.playbackSpeed
    val on = !PlaybackSpeed.isNormal(speed)
    val label = speedLabel(speed)
    val a11y = stringResource(R.string.player_speed_a11y, label)
    // Closing the player closes the menu at once (AU 546).
    LaunchedEffect(media.playerItem) { if (media.playerItem == null) open = false }
    Box(Modifier.padding(start = 6.dp, end = 4.dp)) {
        // The tap reaches 48 dp tall, 6 above and below the 36 dp chip; the press tint and the scale stay on the chip.
        Box(
            Modifier
                .height(48.dp)
                .clickable(src, null, role = Role.Button) { open = true }
                .semantics { contentDescription = a11y },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .popoverAnchor(anchor)
                    .height(36.dp)
                    .widthIn(min = 54.dp)
                    .pressScale(src, 0.95f)
                    .clip(CircleShape)
                    .border(if (on) 2.dp else 1.dp, if (on) ink.solid else ink.line, CircleShape)
                    .indication(src, LocalIndication.current)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) SpeedChipOnStyle else SpeedChipStyle, color = ink.text)
            }
        }
        MediaPopover(expanded = open, onDismiss = { open = false }, anchor = anchor, spec = SpeedMenuSpec, prefer = PopoverSide.ABOVE) {
            SpeedMenu(current = speed) { v ->
                open = false
                editor.setPlaybackSpeedPref(v)
            }
        }
    }
}

/** The speed menu (.au-spdm): 212 dp, "Playback speed", six radio rows (1× says "Normal"), and the footer note. */
@Composable
private fun SpeedMenu(current: Float, onPick: (Float) -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier
            .width(212.dp)
            .inkSurface(inkRounded(16.dp), InkElevation.MENU)
            .padding(vertical = 8.dp)
            .selectableGroup(),
    ) {
        InkMenuHeader(stringResource(R.string.player_speed))
        for (v in PlaybackSpeed.ALL) SpeedRow(v, on = v == current) { onPick(v) }
        InkMenuDivider()
        Text(
            stringResource(R.string.player_speed_note),
            style = SpeedNoteStyle,
            color = ink.text2,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SpeedRow(v: Float, on: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(speedLabel(v), style = if (on) SpeedRowOnStyle else SpeedRowStyle, color = ink.text)
        if (v == PlaybackSpeed.NORMAL) {
            Text(stringResource(R.string.player_speed_normal), style = InkType.meta, color = ink.text2, modifier = Modifier.padding(start = 22.dp))
        }
        Spacer(Modifier.weight(1f))
        if (on) Icon(Ph.check, null, tint = ink.text, modifier = Modifier.size(18.dp))
    }
}

/**
 * The tap-to-seek ring (AU Frame 4, .au-ring): where a tap on a synced note landed, full for 650 ms, then faded and
 * grown over 420 ms. Drawn only, never hit-testable. If the pen goes down meanwhile it goes at once. Its ink is the
 * accent of the page it landed on ([SeekTap.ink]): #222 on cream (AU 83-85), light on a dark paper.
 */
@Composable
private fun SeekRingLayer(media: NoteAudio) {
    val tap = media.seekTap ?: return
    val penDown = LocalPenDown.current
    val clock = remember(tap) { Animatable(0f) }
    val ringInk = remember(tap) { tap.ink.toComposeColor() }
    LaunchedEffect(tap) {
        if (ValueAnimator.areAnimatorsEnabled()) {
            coroutineScope {
                val run = launch { clock.animateTo(RING_TOTAL_MS.toFloat(), tween(RING_TOTAL_MS.toInt(), easing = LinearEasing)) }
                val pen = launch {
                    snapshotFlow { penDown() }.first { it }
                    run.cancel()
                }
                run.join()
                pen.cancel()
            }
        }
        media.clearSeekTap(tap)
    }
    Canvas(Modifier.fillMaxSize()) {
        val f = seekRingFrame(clock.value.toLong()) { InkMotion.Standard.transform(it) } ?: return@Canvas
        val c = Offset(tap.x, tap.y)
        val r = 23.dp.toPx() * f.scale
        drawCircle(ringInk.copy(alpha = 0.06f * f.alpha), r, c)
        drawCircle(ringInk.copy(alpha = 0.28f * f.alpha), r + 0.75.dp.toPx() * f.scale, c, style = Stroke(1.5.dp.toPx() * f.scale))
        drawCircle(ringInk.copy(alpha = 0.45f * f.alpha), 3.dp.toPx() * f.scale, c)
    }
}
