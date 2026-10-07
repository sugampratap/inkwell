package com.xnotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import kotlin.math.roundToInt

// The four text colour pickers, one seam each (Part 6), with Part 5's bodies: the wide picker above the format pill,
// and the narrow one beside the card or strip it is opened from. The callers are unchanged.

/**
 * Format pill › Text colour: the wide picker (620 dp, TX 279-288) 10 dp above the pill, centred on the button, or
 * beside [avoid] (the selection, viewport px) when centred would cover it (TX 887-903). Closes on a pick.
 */
@Composable
internal fun FormatTextColourPicker(initial: Rgba, recents: List<Rgba>, avoid: Rect?, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial, recents, onDismiss, onPick, place = PickerPlace.AbovePill(avoidInWindow(avoid)), wide = true)
}

/** Format pill › Highlight: as [FormatTextColourPicker], opening on #FFEB3B. */
@Composable
internal fun FormatHighlightPicker(initial: Rgba, recents: List<Rgba>, avoid: Rect?, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial, recents, onDismiss, onPick, place = PickerPlace.AbovePill(avoidInWindow(avoid)), wide = true)
}

/** Text options › custom colour: the narrow picker beside the card (x = card.right + 12, y = card.top; TX 1004-1012); live, stays open. */
@Composable
internal fun OptionsColourPicker(initial: Rgba, recents: List<Rgba>, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial, recents, onDismiss, onPick)
}

/** Text-box style pill › sticky note › custom colour: the narrow picker beside the strip; live, stays open. */
@Composable
internal fun StickyCustomColourPicker(initial: Rgba, recents: List<Rgba>, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial, recents, onDismiss, onPick)
}

/** [avoid] (viewport px) in window px, through the pane's canvas origin; null without one. */
@Composable
private fun avoidInWindow(avoid: Rect?): IntRect? {
    val origin = LocalCanvasHost.current?.hostCanvasOrigin() ?: return null
    val r = avoid ?: return null
    return IntRect(
        origin.x + r.left.roundToInt(),
        origin.y + r.top.roundToInt(),
        origin.x + r.right.roundToInt(),
        origin.y + r.bottom.roundToInt(),
    )
}
