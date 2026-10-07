package com.xnotes.ui

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.res.ResourcesCompat
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkBoxSegmented
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalAppWindowRoot
import com.xnotes.ui.kit.LocalHostCard
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.OpenCards
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverEdge
import com.xnotes.ui.kit.animateUnlessPenDown
import com.xnotes.ui.kit.boundsInAppWindow
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.ColorMath
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

// --- The curated palette ------------------------------------------------------------------------
// Seven columns by six rows, laid out as a pen-colour panel rather than an HSV dump: a neutral row
// from white to black, then five tones of seven hues (red, orange, yellow, green, teal, blue,
// purple) stepping down from a pale tint to the deepest shade. The deepest row doubles as the
// earthy inks — maroon, brown, olive, forest, petrol, navy, plum — that a raw hue grid never has.
// The purple column is the palette's own content, kept exactly (SC 367).

private val SWATCHES: List<List<Rgba>> = listOf(
    intArrayOf(0xFFFFFF, 0xD9D9D9, 0xB0B0B0, 0x858585, 0x5C5C5C, 0x333333, 0x000000),
    intArrayOf(0xFFCDD2, 0xFFE0B2, 0xFFF9C4, 0xC8E6C9, 0xB2EBF2, 0xBBDEFB, 0xE1BEE7), // tint
    intArrayOf(0xE57373, 0xFFB74D, 0xFFF176, 0x81C784, 0x4DD0E1, 0x64B5F6, 0xBA68C8), // light
    intArrayOf(0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA), // vivid
    intArrayOf(0xC62828, 0xE65100, 0xF9A825, 0x2E7D32, 0x00838F, 0x1565C0, 0x6A1B9A), // deep
    intArrayOf(0x7F1D1D, 0x5D4037, 0x827717, 0x1B4D2A, 0x006064, 0x1A237E, 0x4A148C), // deepest
).map { row -> row.map { rgb(it) } }

private fun rgb(v: Int): Rgba = Rgba((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)

// --- Sizes (r2_selection_colour 90-128; r3_text 279-288), dp -------------------------------------

private val PICKER_W = 340.dp // .sc-pick
private val WIDE_W = 620.dp // .tx-pick.wide
private val WIDE_LEFT = 284.dp // its left column
private val WIDE_RIGHT_H = 276.dp // the left column's height (tabs 42 + 12 + pane 222): the right one fills it
private val SIDE = 20.dp // the card's side padding
private val SWATCH = 32.dp // .sc-sw
private val PANE_H = 222.dp // .sc-pane: 6 × 32 + 5 × 6, on both tabs
private val SV_H = 174.dp // .sc-sv
private val HUE_GAP = 12.dp
private val HUE_H = 36.dp // .sc-hue
private val HUE_TRACK = 16.dp
private val HUE_TRAVEL = 14.dp // the hue thumb stops this far in from either end (SC 444)
private val SV_THUMB = 12.dp // .sc-th's fill radius
private val HUE_THUMB = 14.dp // .sc-hue .sc-th's fill radius
private val THUMB_RING = 3.dp // the white ring round both thumbs
private val PAD_CORNER = 12.dp
private val DOT = 24.dp // .sc-dots .sc-sw
private val DOT_CELL = 30.dp // its touch box (inset -3) and the row's height
private const val ROW_SLOTS = 10 // favourites and recents shown
private val CODE_DOT = 26.dp // the colour-code menus' chips

private val TABS = listOf(0, 1)

/** The hue bar's rainbow, red to red in 30° steps. */
private val RAINBOW: List<Color> = (0..12).map { ColorMath.hsvToRgb(it * 30.0, 1.0, 1.0).toComposeColor() }

/** .sc-fav: 12.5 SemiBold. */
private val FavLabel = InkType.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp)

private val PillShape = RoundedCornerShape(percent = 50)

/** The light ring round a low-contrast favourite or recent dot in the dark looks: InkPill's dark dot halo, white 55 %. */
private val DarkSwatchRing = Color.White.copy(alpha = 0.55f)

/**
 * The picker's one live colour, with hue and saturation kept explicitly ([keepHueThroughGreys]), because a colour does
 * not always carry them. Snapshot state read by the draw phase, so a drag repaints the pad and bar without
 * recomposing them.
 */
@Stable
private class PickerState(start: Rgba) {
    var current by mutableStateOf(start)
        private set
    var hsv by mutableStateOf(keepHueThroughGreys(PickerHsv(0.0, 0.0, 0.0), start))
        private set

    /** Show [c] (a swatch, a dot, a typed value, a sample), keeping hue and saturation where it has none. */
    fun show(c: Rgba) {
        current = c
        hsv = keepHueThroughGreys(hsv, c)
    }

    fun pickSatValue(s: Double, v: Double) {
        hsv = hsv.copy(s = s, v = v)
        current = ColorMath.hsvToRgb(hsv.h, s, v)
    }

    fun pickHue(h: Double) {
        hsv = hsv.copy(h = h)
        current = ColorMath.hsvToRgb(h, hsv.s, hsv.v)
    }
}

/** Colours kept for later, and how to keep or let go of one; provided once, above every pane. */
class InkFavourites(val colors: List<Rgba>, val toggle: (Rgba) -> Unit)

/** The favourite colours, wherever a colour is chosen; null where nothing provides them. */
val LocalInkFavourites = androidx.compose.runtime.compositionLocalOf<InkFavourites?> { null }

/** Where the picker opens. The rules are pure and tested in ColourPickerLogic. */
internal sealed interface PickerPlace {
    /** Beside the card it was opened from ([LocalHostCard]; SC 478, TO 592), else under its dot. */
    data object Auto : PickerPlace

    /** Under a toolbar swatch: 40 dp before its centre, 10 dp past the bar ([LocalPopoverEdge]; SC 476). */
    data object UnderSwatch : PickerPlace

    /** Above the pill it hangs from ([LocalPopoverEdge]), centred on its button, beside [avoid] (window px) when it would cover it (TX 892). */
    data class AbovePill(val avoid: IntRect?) : PickerPlace

    /** Change style's +: 12 dp off the selection bar's end ([bar]), its top at [top] window px, read when it is placed (SC 606);
     *  beside its card, as [Auto], when the bar's end has no room. */
    class BesideBar(val bar: PopoverAnchor, val top: () -> Int) : PickerPlace
}

/**
 * The shared colour picker (r2_selection_colour Frames 4–5): a 340 dp card ([wide]: the 620 dp layout of r3_text,
 * for the text format pill) with the pen card's header: "Colour", the eyedropper when a canvas is under it, and ×. Then
 * Swatches | Spectrum over one live colour, the favourites with the heart, the recent colours (hidden when [recents]
 * is empty), and a footer with a large preview beside HEX and R, G, B.
 *
 * Colours are opaque. [onPick] fires on every commit (a swatch, a dot, a spectrum release, a field edit, an eyedropper
 * pick), never per drag sample. The card stays open across picks; ×, an outside tap and Back call [onDismiss], except
 * while picking, when nothing dismisses it.
 *
 * [place]: beside the card it is opened from, under a toolbar swatch, above the format pill, or beside the selection
 * bar. [anchor]: the dot or button it hangs from; with none, the element it is composed in (compose it in the dot's
 * Box, as the old DropdownMenu was). [expanded] false plays the exit (CardSlot); a caller that drops it from
 * composition closes it at once.
 *
 * The eyedropper ([LocalCanvasHost]): its button arms a picking state with a hint; the next tap anywhere ends it, and
 * a tap on the page rather than on a card ([OpenCards]) picks what the page shows there
 * (ToolPopupHost.sampleColourAt), keeps it in Recent and commits it.
 */
@Composable
internal fun ColorPickerPopup(
    initial: Rgba?,
    recents: List<Rgba>,
    onDismiss: () -> Unit,
    onPick: (Rgba) -> Unit,
    expanded: Boolean = true,
    anchor: PopoverAnchor? = null,
    place: PickerPlace = PickerPlace.Auto,
    wide: Boolean = false,
) {
    val own = remember { PopoverAnchor() }
    if (anchor == null) {
        // The element the picker is composed in: a 0-size probe records its parent's bounds, in the app window's px as
        // every anchor is (inside a card, not the card's own window).
        val view = LocalView.current
        val appRoot = LocalAppWindowRoot.current
        Spacer(
            Modifier
                .size(0.dp)
                .onGloballyPositioned { c -> own.bounds = (c.parentLayoutCoordinates ?: c).boundsInAppWindow(view, appRoot) },
        )
    }
    val hangsFrom = anchor ?: own
    val card = LocalHostCard.current
    val edge = LocalPopoverEdge.current
    val dp = LocalDensity.current.density
    val canvas = LocalCanvasHost.current?.takeIf { it.hostCanSampleColour }
    val state = remember { PickerState(initial?.copy(a = 255) ?: Rgba(255, 255, 255)) }
    var tab by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val pick by rememberUpdatedState(onPick)
    val commit: (Rgba) -> Unit = remember(state) { { c: Rgba -> state.show(c); pick(c) } }
    val release: () -> Unit = remember(state) { { pick(state.current) } }
    val placer = remember(place, card, edge, dp) { pickerPlacer(place, card, edge, dp) }
    // The side its max height is measured on: beside a card or the bar it may use the whole height.
    // With no card (a sheet, Settings) it may stand beside its dot rather than under it, so it may use the whole height too.
    val prefer = when (place) {
        PickerPlace.UnderSwatch -> null
        is PickerPlace.AbovePill -> PopoverSide.ABOVE
        PickerPlace.Auto -> PopoverSide.END
        is PickerPlace.BesideBar -> PopoverSide.END
    }
    val hint = stringResource(if (wide) R.string.picker_eyedropper_hint_ink else R.string.picker_eyedropper_hint)
    InkPopover(
        expanded = expanded,
        onDismiss = { if (!picking) onDismiss() },
        anchor = hangsFrom,
        prefer = prefer,
        placer = placer,
    ) {
        PickerCard(
            state = state,
            tab = tab,
            onTab = { tab = it },
            recents = recents,
            wide = wide,
            canPick = canvas != null,
            picking = picking,
            hint = hint,
            onPicking = { picking = it },
            commit = commit,
            release = release,
            onClose = onDismiss,
        )
        if (picking && canvas != null && expanded) {
            EyedropperCatcher(
                hint,
                onTap = { x, y ->
                    if (OpenCards.contains(x, y)) {
                        // On the picker or the card under it: picking ends, nothing is sampled.
                        picking = false
                    } else {
                        canvas.sampleColourAt(x, y) { c ->
                            picking = false
                            if (c != null) {
                                canvas.rememberPickedColour(c)
                                commit(c)
                            }
                        }
                    }
                },
                onCancel = { picking = false },
            )
        }
    }
}

/** [place] as InkPopover's placer: the pure rules, read with the bounds each anchor has when the card is placed. */
private fun pickerPlacer(place: PickerPlace, card: PopoverAnchor?, edge: PopoverEdge?, dp: Float) = PopoverPlacer { anchor, barEdge, window, content ->
    when (place) {
        PickerPlace.Auto -> {
            val c = card?.bounds?.takeUnless { it == IntRect.Zero }
            if (c != null) pickerBesideCard(anchor, c, window, content, dp) else pickerUnderAnchor(anchor, window, content, dp)
        }
        PickerPlace.UnderSwatch -> pickerUnderSwatch(anchor, barEdge, window, content, edge?.prefer ?: PopoverSide.BELOW, dp)
        is PickerPlace.AbovePill -> pickerAbovePill(anchor, barEdge, place.avoid, window, content, dp)
        is PickerPlace.BesideBar ->
            pickerBesideBar(anchor, place.bar.bounds, place.top(), window, content, dp, card?.bounds?.takeUnless { it == IntRect.Zero })
    }
}

/** The card (.sc-pick, or .tx-pick.wide): one surface, scrolling inside when the window is shorter than it. */
@Composable
private fun PickerCard(
    state: PickerState,
    tab: Int,
    onTab: (Int) -> Unit,
    recents: List<Rgba>,
    wide: Boolean,
    canPick: Boolean,
    picking: Boolean,
    hint: String,
    onPicking: (Boolean) -> Unit,
    commit: (Rgba) -> Unit,
    release: () -> Unit,
    onClose: () -> Unit,
) {
    val ink = LocalInk.current
    // Nothing here reads the live colour: the panes and the footer each read it themselves, so a spectrum drag
    // recomposes only the footer, never the tabs or the grid.
    Column(
        Modifier
            .width(if (wide) WIDE_W else PICKER_W)
            .inkSurface(inkRounded(24.dp), InkElevation.MENU)
            .verticalScroll(rememberScrollState()),
    ) {
        PickerHeader(canPick, picking, onPicking, onClose)
        if (wide) {
            // TX 630, 279-288: the hint under the header; Swatches/Spectrum on the left, the rest on the right.
            if (picking) EyeHint(hint, Modifier.padding(start = SIDE, end = SIDE, bottom = 10.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = SIDE, end = SIDE, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.width(WIDE_LEFT)) {
                    PickerTabs(tab, onTab)
                    PickerPane(state, tab, commit, release, Modifier.padding(top = 12.dp))
                }
                Column(Modifier.weight(1f).height(WIDE_RIGHT_H)) {
                    FavouritesSection(state, commit, Modifier.padding(top = 4.dp))
                    if (recents.isNotEmpty()) RecentSection(recents, state, commit, Modifier.padding(top = 10.dp))
                    Spacer(Modifier.weight(1f))
                    PickerFooter(state, commit, Modifier.topHairline(ink.line2).padding(top = 14.dp), fieldPadding = 10.dp)
                }
            }
        } else {
            // SC 409-422: the tabs, the hint under them (SC 410-411, .sc-eyehint), then the pane, favourites, recent, footer.
            PickerTabs(tab, onTab, Modifier.padding(start = SIDE, end = SIDE, top = 2.dp))
            if (picking) EyeHint(hint, Modifier.padding(start = SIDE, end = SIDE, top = 2.dp))
            PickerPane(state, tab, commit, release, Modifier.padding(start = SIDE, end = SIDE, top = 12.dp))
            FavouritesSection(state, commit, Modifier.padding(start = SIDE, end = SIDE, top = 12.dp))
            if (recents.isNotEmpty()) RecentSection(recents, state, commit, Modifier.padding(start = SIDE, end = SIDE, top = 12.dp))
            PickerFooter(
                state,
                commit,
                Modifier
                    .padding(top = 14.dp)
                    .topHairline(ink.line2)
                    .padding(start = SIDE, end = SIDE, top = 12.dp, bottom = 16.dp),
                fieldPadding = 12.dp,
            )
        }
    }
}

/** .pp-h (B 407-411; SC 409): "Colour", the eyedropper (lit while picking) and ×, 44 dp each with 18 dp icons. */
@Composable
private fun PickerHeader(canPick: Boolean, picking: Boolean, onPicking: (Boolean) -> Unit, onClose: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = SIDE, top = 10.dp, end = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.colour),
            style = InkType.sheetTitle,
            color = ink.text,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (canPick) {
            InkIconButton(
                Ph.eyedropper,
                stringResource(R.string.picker_eyedropper),
                { onPicking(!picking) },
                modifier = Modifier.wrapContentSize(unbounded = true),
                on = picking,
                iconSize = 18.dp,
            )
        }
        InkIconButton(
            Ph.x,
            stringResource(R.string.kit_close),
            onClose,
            modifier = Modifier.wrapContentSize(unbounded = true),
            iconSize = 18.dp,
        )
    }
}

/** .sc-eyehint (SC 149-150): a grey 12 dp-corner strip with the eyedropper and what to do, while picking. */
@Composable
private fun EyeHint(text: String, modifier: Modifier) {
    val ink = LocalInk.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(inkRounded(12.dp))
            .background(ink.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ph.eyedropper, null, tint = ink.text, modifier = Modifier.size(16.dp))
        Text(text, style = InkType.counter, color = ink.text)
    }
}

/** .sc-tabs .svseg (SC 93-94, B 234-236): Swatches | Spectrum, 34 dp segments 4 dp apart. */
@Composable
private fun PickerTabs(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    InkBoxSegmented(
        options = TABS,
        selected = tab,
        label = { stringResource(if (it == 0) R.string.swatches else R.string.spectrum) },
        onSelect = onTab,
        modifier = modifier.fillMaxWidth(),
        height = 34.dp,
        fill = true,
        gap = 4.dp,
    )
}

/** .sc-pane (SC 95): 222 dp on both tabs, so switching never makes the card jump. */
@Composable
private fun PickerPane(state: PickerState, tab: Int, commit: (Rgba) -> Unit, release: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(PANE_H)) {
        if (tab == 0) SwatchPane(state, commit) else SpectrumPane(state, release)
    }
}

// --- Swatches -----------------------------------------------------------------------------------

/** .sc-grid (SC 96): seven 32 dp columns edge to edge, 6 dp between rows. */
@Composable
private fun SwatchPane(state: PickerState, onPick: (Rgba) -> Unit) {
    val current = state.current
    val words = rememberExplorerWords()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in SWATCHES) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in row) InkSwatch(c.toComposeColor(), c == current, SWATCH, cell = SWATCH, contentDescription = swatchName(words, c)) { onPick(c) }
            }
        }
    }
}

/** A swatch's TalkBack name: its hue word, then its hex ("Blue, #1E88E5"; R4 #12). Every colour swatch says it so. */
@Composable
internal fun swatchName(words: ExplorerWords, c: Rgba): String =
    stringResource(R.string.picker_swatch, hueName(words, c), Rgba.toHex(c).uppercase())

/**
 * Favourites (SC 107-119): the caption with the heart on the right ("Add to favourites", or "Favourite" with a
 * marigold Fill heart that beats once), then up to ten dots, or the hint when there are none.
 */
@Composable
private fun FavouritesSection(state: PickerState, onPick: (Rgba) -> Unit, modifier: Modifier) {
    val favourites = LocalInkFavourites.current ?: return
    val ink = LocalInk.current
    val current = state.current
    val hearted = current.copy(a = 255) in favourites.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.caption_favourite_colours), style = InkType.label, color = ink.text, modifier = Modifier.weight(1f))
            FavouriteButton(hearted) { favourites.toggle(current) }
        }
        Spacer(Modifier.height(2.dp))
        if (favourites.colors.isEmpty()) {
            Box(Modifier.height(DOT_CELL), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.favourite_colours_empty), style = InkType.hint, color = ink.text2)
            }
        } else {
            DotRow(favourites.colors, current, onPick)
        }
    }
}

/** .sc-fav (SC 109-115): 28 dp, reaching 10 dp past the section's edge; the heart beats on a new favourite (heartpop). */
@Composable
private fun FavouriteButton(on: Boolean, onToggle: () -> Unit) {
    val ink = LocalInk.current
    val penDown = LocalPenDown.current
    val beat = remember { Animatable(1f) }
    // Bumped by the heart itself when it adds a favourite: landing on a colour that already is one (a favourite's dot
    // tapped) lights the heart without a beat.
    var beats by remember { mutableIntStateOf(0) }
    LaunchedEffect(beats) {
        if (beats == 0) return@LaunchedEffect
        // heartpop (B 140): 1.2 at 45 % of 260 ms, then the heart spring home. Snapped while the pen is down.
        animateUnlessPenDown(penDown, settle = { beat.snapTo(1f) }) {
            beat.animateTo(1.2f, tween(117, easing = InkMotion.Standard))
            beat.animateTo(1f, InkMotion.pop())
        }
    }
    LaunchedEffect(on) { if (!on) beat.snapTo(1f) }
    Row(
        Modifier
            .offset(x = 10.dp)
            .height(28.dp)
            .clip(PillShape)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { if (!on) beats++; onToggle() })
            .padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (on) Ph.heartFill else Ph.heart,
            null,
            tint = if (on) ink.brand else ink.text2,
            modifier = Modifier.size(18.dp).graphicsLayer {
                scaleX = beat.value
                scaleY = beat.value
            },
        )
        Text(
            stringResource(if (on) R.string.favourite_colour_remove else R.string.favourite_colour_add),
            style = FavLabel,
            color = if (on) ink.text else ink.text2,
        )
    }
}

/** Recent (SC 417): the caption, then up to ten dots. */
@Composable
private fun RecentSection(recents: List<Rgba>, state: PickerState, onPick: (Rgba) -> Unit, modifier: Modifier) {
    val ink = LocalInk.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.caption_recent), style = InkType.label, color = ink.text)
        }
        Spacer(Modifier.height(2.dp))
        DotRow(recents, state.current, onPick)
    }
}

/** .sc-dots (SC 116-118): ten equal columns, 30 dp tall, 24 dp dots, left-aligned however few there are. */
@Composable
private fun DotRow(colours: List<Rgba>, current: Rgba, onPick: (Rgba) -> Unit) {
    val words = rememberExplorerWords()
    val ink = LocalInk.current
    val card = ink.raised.luminance()
    Row(Modifier.fillMaxWidth().height(DOT_CELL)) {
        for (i in 0 until ROW_SLOTS) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val c = colours.getOrNull(i)?.copy(a = 255)
                if (c != null) {
                    // A navy or black dot all but vanishes on the dark card: a light ring round it, as the dark themes
                    // ring the selection bar's colour dot (InkPill's halo). Under the swatch, so a chosen one's rings cover it.
                    val ringed = Modifier.takeIf { needsLightRing(c, card, ink.isDark) }?.drawBehind {
                        drawCircle(DarkSwatchRing, DOT.toPx() / 2f + 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    } ?: Modifier
                    InkSwatch(c.toComposeColor(), c == current, DOT, cell = DOT_CELL, contentDescription = swatchName(words, c), modifier = ringed) { onPick(c) }
                }
            }
        }
    }
}

/**
 * The palette's own curated grid at a fixed chip size, for menus outside the picker (the note and
 * folder colour codes). Nothing is shown as selected.
 */
@Composable
internal fun FullSwatchGrid(onPick: (Rgba) -> Unit) {
    val words = rememberExplorerWords()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in SWATCHES) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in row) InkSwatch(c.toComposeColor(), false, CODE_DOT, contentDescription = swatchName(words, c)) { onPick(c) }
            }
        }
    }
}

/** The "no colour" chip that leads a colour-code menu: an empty ring struck through, in the cell
 *  size of [FullSwatchGrid] so it lines up with the grid's first column. */
@Composable
internal fun NoColourSwatch() {
    val ink = LocalPalette.current.textDim.toComposeColor()
    Box(
        Modifier
            .size(CODE_DOT + 8.dp)
            .drawBehind {
                val r = CODE_DOT.toPx() / 2f - 0.5.dp.toPx()
                val w = 1.dp.toPx()
                drawCircle(ink, r, style = Stroke(w))
                val d = r * 0.7071f
                drawLine(ink, center + Offset(-d, d), center + Offset(d, -d), w)
            },
    )
}

// --- Spectrum: saturation/value pad over a hue bar ----------------------------------------------

/**
 * The pad and the bar report edits straight into [state] on every touch sample (a repaint, not a
 * recomposition) and call [onRelease] once when the finger lifts, so the caller commits a drag as
 * one pick.
 */
@Composable
private fun SpectrumPane(state: PickerState, onRelease: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SatValPad(state, onRelease, Modifier.fillMaxWidth().height(SV_H))
        Spacer(Modifier.height(HUE_GAP))
        HueBar(state, onRelease, Modifier.fillMaxWidth().height(HUE_H))
    }
}

/** .sc-sv (SC 102): saturation across, value down, r12 with a --line2 hairline. */
@Composable
private fun SatValPad(state: PickerState, onRelease: () -> Unit, modifier: Modifier) {
    val outline = LocalInk.current.line2
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier
            .pointerInput(state) {
                trackDrag(
                    onMove = { p ->
                        val s = (p.x / size.width.coerceAtLeast(1)).toDouble().coerceIn(0.0, 1.0)
                        val v = (1.0 - p.y / size.height.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                        state.pickSatValue(s, v)
                    },
                    onEnd = { release() },
                )
            }
            .drawWithCache {
                val corner = CornerRadius(PAD_CORNER.toPx())
                // Fades over a flat hue fill, so only the fill changes as the hue moves and both gradients stay
                // cached. The white fades to transparent white, not Color.Transparent, or the middle goes muddy.
                val whiteFade = Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)))
                val blackFade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black))
                val hairline = Stroke(1.dp.toPx())
                val thumbR = SV_THUMB.toPx()
                onDrawBehind {
                    val hsv = state.hsv
                    drawRoundRect(ColorMath.hsvToRgb(hsv.h, 1.0, 1.0).toComposeColor(), cornerRadius = corner)
                    drawRoundRect(whiteFade, cornerRadius = corner)
                    drawRoundRect(blackFade, cornerRadius = corner)
                    drawRoundRect(outline, cornerRadius = corner, style = hairline)
                    val at = Offset(hsv.s.toFloat() * size.width, (1f - hsv.v.toFloat()) * size.height)
                    drawThumb(at, state.current.toComposeColor(), thumbR)
                }
            },
    )
}

/** .sc-hue (SC 103-104): a 16 dp rainbow track in a 36 dp bar; the thumb travels 14 dp in from each end. */
@Composable
private fun HueBar(state: PickerState, onRelease: () -> Unit, modifier: Modifier) {
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier
            .pointerInput(state) {
                trackDrag(
                    onMove = { p ->
                        val r = HUE_TRAVEL.toPx()
                        val f = ((p.x - r) / (size.width - 2 * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        state.pickHue(f * 360.0)
                    },
                    onEnd = { release() },
                )
            }
            .drawWithCache {
                val r = HUE_TRAVEL.toPx()
                val track = HUE_TRACK.toPx()
                val top = (size.height - track) / 2f
                val rainbow = Brush.horizontalGradient(RAINBOW, startX = r, endX = size.width - r)
                val thumbR = HUE_THUMB.toPx()
                onDrawBehind {
                    drawRoundRect(rainbow, Offset(0f, top), Size(size.width, track), CornerRadius(track / 2f))
                    val x = r + (size.width - 2 * r) * (state.hsv.h / 360.0).toFloat()
                    drawThumb(Offset(x, size.height / 2f), state.current.toComposeColor(), thumbR)
                }
            },
    )
}

/** .sc-th (SC 105-106): the live colour in a 3 dp white ring, lifted by one flat soft circle (no blur). */
private fun DrawScope.drawThumb(at: Offset, fill: Color, radius: Float) {
    val ring = THUMB_RING.toPx()
    drawCircle(Color.Black.copy(alpha = 0.24f), radius + ring + 1.dp.toPx(), at + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, radius + ring, at)
    drawCircle(fill, radius, at)
}

/**
 * Feeds [onMove] the touch-down and every move of one finger until it lifts, then calls [onEnd].
 * Each change is consumed, so the card's own scroll never steals a drag that starts on a control.
 */
private suspend fun PointerInputScope.trackDrag(onMove: (Offset) -> Unit, onEnd: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onMove(down.position)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) { change.consume(); break }
            onMove(change.position)
            change.consume()
        }
        onEnd()
    }
}

// --- Footer: a large preview beside the HEX / R, G, B fields, shared by both tabs -----------------

/** .sc-pf (SC 120-128): the 56 dp preview, then HEX over R, G, B, 6 dp apart. */
@Composable
private fun PickerFooter(state: PickerState, onColor: (Rgba) -> Unit, modifier: Modifier, fieldPadding: Dp) {
    val ink = LocalInk.current
    val current = state.current
    val fill = current.toComposeColor()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(
            Modifier.size(56.dp).drawBehind {
                val r = size.minDimension / 2f
                drawCircle(fill, r)
                drawSwatchRing(ink.isDark, r)
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HexField(current, onColor, fieldPadding, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelField("R", current.r, fieldPadding, Modifier.weight(1f)) { onColor(current.copy(r = it)) }
                ChannelField("G", current.g, fieldPadding, Modifier.weight(1f)) { onColor(current.copy(g = it)) }
                ChannelField("B", current.b, fieldPadding, Modifier.weight(1f)) { onColor(current.copy(b = it)) }
            }
        }
    }
}

@Composable
private fun HexField(current: Rgba, onColor: (Rgba) -> Unit, padding: Dp, modifier: Modifier) {
    val ink = LocalInk.current
    PickerField(padding, modifier) { onFocus ->
        Text("#", style = InkType.counter, color = ink.text2)
        NativeField(
            value = Rgba.toHex(current).removePrefix("#").uppercase(),
            onText = { raw -> if (raw.length == 6) Rgba.fromHex("#$raw")?.let { onColor(it.copy(a = 255)) } },
            modifier = Modifier.weight(1f),
            maxLen = 6,
            hexOnly = true,
            onFocus = onFocus,
        )
    }
}

@Composable
private fun ChannelField(label: String, value: Int, padding: Dp, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val ink = LocalInk.current
    PickerField(padding, modifier) { onFocus ->
        Text(label, style = InkType.counter, color = ink.text2)
        NativeField(
            value = value.toString(),
            onText = { raw -> raw.toIntOrNull()?.let { onChange(it.coerceIn(0, 255)) } },
            modifier = Modifier.weight(1f),
            numeric = true,
            maxLen = 3,
            endAlign = true,
            onFocus = onFocus,
        )
    }
}

/**
 * .sc-fld (SC 124-128): 38 dp, r12, a 1 dp --line ring inside, 2 dp near-black while typing; its label and value 6 dp
 * apart, [padding] a side (12; 10 in the wide card). The focus is read only while drawing, so it only redraws.
 */
@Composable
private fun PickerField(padding: Dp, modifier: Modifier, content: @Composable RowScope.(onFocus: (Boolean) -> Unit) -> Unit) {
    val ink = LocalInk.current
    val corner = cornerOf(12.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(38.dp)
            .drawBehind {
                val w = (if (focused) 2.dp else 1.dp).toPx()
                drawRoundRect(
                    if (focused) ink.solid else ink.line,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((corner.toPx() - w / 2f).coerceAtLeast(0f)),
                    style = Stroke(w),
                )
            }
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content { focused = it } }
}

/**
 * A platform [EditText] (not a Compose `BasicTextField`) for the footer fields. Compose text fields
 * inside a popup window fight the soft keyboard — the keyboard can flash shut and the menu is left
 * shoved out of place. A native view owns a stable input connection and, via [onKeyPreIme], lets us
 * blur it (clearing the caret) when the keyboard is dismissed by the back gesture, which Compose has
 * no clean hook for. [value] drives the field when it isn't focused; edits flow out through [onText].
 * Set in 14 SemiBold, tabular, tracked .02 em (.sc-fld input); [onFocus] hears focus come and go.
 */
@Composable
internal fun NativeField(
    value: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    maxLen: Int = 6,
    hexOnly: Boolean = false,
    endAlign: Boolean = false,
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val textColor = LocalInk.current.text.toArgb()
    // The view's listeners are set once in the factory; read the callbacks through state so an edit
    // reaches the latest one (an R/G/B field builds its colour from the colour current *now*).
    val onTextNow by rememberUpdatedState(onText)
    val onDoneNow by rememberUpdatedState(onDone)
    val onFocusNow by rememberUpdatedState(onFocus)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PickerEditText(ctx).apply {
                background = null
                setPadding(0, 0, 0, 0)
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                isSingleLine = true
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans_semibold)
                letterSpacing = 0.02f
                fontFeatureSettings = "tnum"
                inputType = if (numeric) {
                    InputType.TYPE_CLASS_NUMBER
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                }
                imeOptions = EditorInfo.IME_ACTION_DONE
                gravity = (if (endAlign) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                val filterList = mutableListOf<InputFilter>(InputFilter.LengthFilter(maxLen))
                if (hexOnly) {
                    filterList.add(InputFilter { src, start, end, _, _, _ ->
                        val kept = StringBuilder()
                        for (i in start until end) {
                            val c = src[i]
                            if (c.isDigit() || c.lowercaseChar() in 'a'..'f') kept.append(c.uppercaseChar())
                        }
                        if (kept.toString() == src.subSequence(start, end).toString()) null else kept.toString()
                    })
                }
                filters = filterList.toTypedArray()
                onImeBack = { clearFocus() }
                setOnFocusChangeListener { _, has -> onFocusNow?.invoke(has) }
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) { onDoneNow?.invoke(); clearFocus(); true } else false
                }
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { if (hasFocus()) onTextNow(s?.toString().orEmpty()) }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
                if (autoFocus) post {
                    requestFocus()
                    selectAll()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, 0)
                }
            }
        },
        update = { et ->
            et.setTextColor(textColor)
            // Mirror the live colour only while the user isn't typing, so an edit isn't clobbered.
            if (!et.hasFocus() && et.text?.toString() != value) {
                et.setText(value)
                et.setSelection(value.length)
            }
        },
    )
}

/** [EditText] that reports a keyboard-dismissing BACK press so the caller can blur it (the platform
 *  otherwise keeps the field focused — and its caret blinking — after the keyboard slides away). */
private class PickerEditText(context: Context) : EditText(context) {
    var onImeBack: (() -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) onImeBack?.invoke()
        return super.onKeyPreIme(keyCode, event)
    }
}

// --- The eyedropper's catcher ---------------------------------------------------------------------

/**
 * While the eyedropper is armed: a transparent window over everything, the picker and the card under it included, so
 * the next tap reaches no stroke, no button and no page (TO 534: picking never closes the picker). It reports that
 * tap's screen px, or Back. It draws nothing and lives only while picking.
 */
@Composable
private fun EyedropperCatcher(label: String, onTap: (Int, Int) -> Unit, onCancel: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Popup(
        popupPositionProvider = WindowOrigin,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
    ) {
        // Written on layout, read on a tap: never snapshot state.
        val origin = remember { FloatArray(2) }
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = label
                    // A TalkBack double-tap has no point on the page to pick: it ends picking rather than sampling
                    // whatever sits under the screen's centre.
                    onClick { onCancel(); true }
                }
                .onGloballyPositioned {
                    val p = it.positionOnScreen()
                    origin[0] = p.x
                    origin[1] = p.y
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        up.consume()
                        tap((origin[0] + up.position.x).roundToInt(), (origin[1] + up.position.y).roundToInt())
                    }
                },
        )
    }
}

/** Puts a Popup at its window's top-left. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset.Zero
}
