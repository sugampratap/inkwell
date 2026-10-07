package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.PenBox
import com.xnotes.core.tools.PenPreset
import com.xnotes.core.tools.Tool
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkPopover
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.animateUnlessPenDown
import com.xnotes.ui.kit.LocalPopoverEdge
import com.xnotes.ui.kit.PopoverAnchor
import com.xnotes.ui.kit.PopoverSpecs
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.kit.popoverAnchor
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.launch

/** The ink [tool] draws in: its own pinned colour if it has one, else the armed swatch's. */
internal fun ToolPopupHost.inkOf(tool: Tool): Rgba =
    toolConfig(tool).colorOverride
        ?: hostToolbarColors.getOrNull(hostActiveColorIndex)
        ?: InkPalette.INK

/** Which pen in the box is in hand, or -1 when the armed tool is tuned like none of them. */
internal fun ToolPopupHost.activePen(): Int {
    val tool = hostTool
    if (!PenBox.holds(tool)) return -1
    return PenBox.indexOf(hostPenBox, tool, toolConfig(tool), inkOf(tool))
}

/**
 * Pick [pen] up. Its ink comes from the swatch that holds that colour when one does; a colour no
 * swatch holds, like the marker's yellow, is pinned to the tool instead, so picking a pen never
 * repaints a swatch the hand has its own use for. The ink goes first, because a host folds the
 * live ink into the config it stores.
 */
internal fun ToolPopupHost.armPen(pen: PenPreset) {
    val i = hostToolbarColors.take(hostSwatchCount).indexOf(pen.color)
    val pinned = pinnedByBox.getOrPut(this) { HashSet() }
    if (i >= 0) {
        hostPickSwatch(i)
        updateToolConfig(pen.tool, pen.config.copy(colorOverride = null))
        pinned -= pen.tool
    } else {
        updateToolConfig(pen.tool, pen.config.copy(colorOverride = pen.color))
        pinned += pen.tool
    }
    hostArmTool(pen.tool)
}

/** Tools whose colour the pen box pinned, per host: a swatch tap is free to take them back. */
private val pinnedByBox = java.util.WeakHashMap<ToolPopupHost, MutableSet<Tool>>()

/**
 * A swatch was tapped. If the pen in hand only had its colour because the pen box pinned it, the
 * tap means "this colour now", so the pin comes off; a colour pinned in the pen's own settings is
 * the user's and stays.
 */
internal fun ToolPopupHost.releasePenBoxInk() {
    val pinned = pinnedByBox[this] ?: return
    val tool = hostTool
    if (!pinned.remove(tool)) return
    updateToolConfig(tool, toolConfig(tool).copy(colorOverride = null))
}

/** Put the pen in hand into the box, if it is a pen, with its colour as it inks now. */
internal fun ToolPopupHost.savePenInHand(tool: Tool = hostTool) {
    if (!PenBox.holds(tool)) return
    val pen = PenPreset(tool, toolConfig(tool).copy(colorOverride = null), inkOf(tool))
    replacePenBox(PenBox.add(hostPenBox, pen))
}

/** The caption's millimetres ([widthValueMm], "0.5") as a number, so the sample is as thick as its label says. */
internal fun railMm(caption: String): Float = caption.toFloatOrNull() ?: 0f

/**
 * The stroke width, in viewBox units (= dp), of a pen's sample on its rail tile: `1 + mm × 2.4` for a
 * pen (W 1198-1201); a highlighter's flat band is `4 + mm × 1.5`, at most 14.
 */
internal fun railSampleWidth(tool: Tool, mm: Float): Float =
    if (tool == Tool.HIGHLIGHTER) (4f + mm * 1.5f).coerceAtMost(14f) else 1f + mm * 2.4f

/** .rail: 68 dp wide, 16 dp in from the screen edge (W 401). */
private val RAIL_WIDTH = 68.dp
private val RAIL_EDGE = 16.dp

/** How far the rail slides on collapse: past its own width and the edge gap, so none of it stays on screen. */
private val RAIL_SLIDE = 96.dp

/** The long-press menu's width: fits "Remove from pen box" with its icon. */
private val REMOVE_MENU_WIDTH = 240.dp

/** The stroke sample's paper, cream in every theme, and its faint inner ring (.pp). */
private val SamplePaper = ToPaper
private val SampleRing = ToPreviewRing

/**
 * The rail's caption (small.tnum): 11/13 SemiBold, Bold when the pen is in hand. Lazy, so the JVM test
 * of this file's pure helpers does not have to build the type scale.
 */
private val PresetMm by lazy { InkType.tiny.tnum() }
private val PresetMmOn by lazy { InkType.tiny.copy(fontWeight = FontWeight.Bold).tnum() }

/** The sample stroke, in the 46×34 viewBox (W 407). Lazy: parsing reaches android.graphics. */
private val SAMPLE_CURVE by lazy { PathParser().parsePathString("M8 22C12 12 17 10 20.5 16.5S29 25 38 12").toPath() }

/** A highlighter's sample: a flat band across the middle. */
private val SAMPLE_BAND by lazy { PathParser().parsePathString("M9 17H37").toPath() }

/**
 * The pen box: the pens kept in it, down the canvas's edge, each one tap from being in hand
 * (mockup §5). It sits on the edge the toolbar does not, vertically centred.
 *
 * The caret on top slides it off past the edge (transform and opacity only, InkMotion.glide), leaving
 * a pull-tab that slides it back; the open/closed state persists through [ToolPopupHost.openPenBox].
 * Collapsed, it takes no input: its buttons drop their click handlers and it ends fully off screen.
 * A long press offers to take a pen out; the plus at the foot keeps the pen in hand.
 */
@Composable
internal fun BoxScope.PenBoxRail(host: ToolPopupHost) {
    if (!showsPenRail(host.hostHasPenBox, LocalConfiguration.current.screenHeightDp)) return
    val onLeft = penRailOnLeft(LocalToolbarLook.current.position)
    val open = host.hostPenBoxOpen
    val active = host.activePen()
    val penDown = LocalPenDown.current
    // 0 = on the canvas, 1 = slid off past the edge; and the rail's opacity. Read only in layers.
    val out = remember { Animatable(if (open) 0f else 1f) }
    val shown = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        val to = if (open) 0f else 1f
        // Snaps with the pen down, and jumps to its end if the pen lands while it slides (B2 ground rule 1).
        animateUnlessPenDown(penDown, settle = { out.snapTo(to); shown.snapTo(1f - to) }) {
            launch { out.animateTo(to, InkMotion.glide()) }
            shown.animateTo(1f - to, tween(InkMotion.BASE, easing = InkMotion.Standard))
        }
    }
    val towardEdge = if (onLeft) -1f else 1f
    Column(
        Modifier
            .align(if (onLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
            .padding(horizontal = RAIL_EDGE, vertical = RAIL_EDGE)
            .graphicsLayer {
                translationX = towardEdge * RAIL_SLIDE.toPx() * out.value
                alpha = shown.value
            }
            .width(RAIL_WIDTH)
            .inkSurface(inkRounded(20.dp), InkElevation.SOFT)
            .then(if (open) Modifier else Modifier.clearAndSetSemantics {})
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailButton(
            if (onLeft) Ph.caretLeft else Ph.caretRight,
            stringResource(R.string.pen_box_fold),
            enabled = open,
        ) { host.openPenBox(false) }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), enabled = open),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            host.hostPenBox.forEachIndexed { i, pen ->
                // Keyed by the pen as well as its place: after a removal the next pen moves up into a fresh slot,
                // never into the removed pen's, whose Remove menu may still be closing.
                key(i, pen) {
                    PresetTile(
                        pen,
                        active = i == active,
                        enabled = open,
                        onLeft = onLeft,
                        onPick = { host.armPen(pen) },
                        onRemove = { host.replacePenBox(PenBox.remove(host.hostPenBox, i)) },
                    )
                }
            }
        }
        if (PenBox.holds(host.hostTool) && active < 0) {
            RailButton(Ph.plus, stringResource(R.string.pen_box_save_current), enabled = open) { host.savePenInHand() }
        }
    }
    PullTab(onLeft, shown = { 1f - shown.value }, enabled = !open) { host.openPenBox(true) }
}

/**
 * .preset: a 56×62 r14 tile with the pen's stroke on cream above its millimetres. The pen in hand
 * takes a 2 dp near-black inset ring and a bold caption. It shrinks to .95 while held; a long press
 * opens the Remove menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetTile(pen: PenPreset, active: Boolean, enabled: Boolean, onLeft: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    val ink = LocalInk.current
    var menu by remember { mutableStateOf(false) }
    val anchor = remember { PopoverAnchor() }
    val src = remember { MutableInteractionSource() }
    val colour = Color(pen.color.r, pen.color.g, pen.color.b, 255)
    val mm = widthValueMm(pen.config.baseWidth.toFloat())
    val name = stringResource(pen.tool.labelRes)
    val description = stringResource(R.string.pen_box_pen, name, mm)
    val removeLabel = stringResource(R.string.pen_box_remove)
    val corner = cornerOf(14.dp)
    Box {
        Column(
            Modifier
                .size(56.dp, 62.dp)
                .popoverAnchor(anchor)
                .pressScale(src, 0.95f)
                .clip(inkRounded(14.dp))
                .then(
                    if (enabled) {
                        Modifier.combinedClickable(
                            interactionSource = src,
                            indication = null,
                            onLongClickLabel = removeLabel,
                            onLongClick = { menu = true },
                            onClick = onPick,
                        )
                    } else Modifier,
                )
                .semantics {
                    contentDescription = description
                    selected = active
                }
                .drawBehind {
                    if (active) {
                        val w = 2.dp.toPx()
                        drawRoundRect(
                            ink.solid,
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(corner.toPx() - w / 2f),
                            style = Stroke(w),
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            PenSample(pen, colour, railMm(mm))
            Text(mm, style = if (active) PresetMmOn else PresetMm, color = if (active) ink.text else ink.text2, maxLines = 1)
        }
        // The rail is not a toolbar: the menu hangs off the tile itself, toward the canvas.
        CompositionLocalProvider(LocalPopoverEdge provides null) {
            InkPopover(
                expanded = menu,
                onDismiss = { menu = false },
                anchor = anchor,
                spec = PopoverSpecs.MoreMenu,
                prefer = if (onLeft) PopoverSide.END else PopoverSide.START,
            ) {
                Column(
                    Modifier
                        .width(REMOVE_MENU_WIDTH)
                        .inkSurface(inkRounded(16.dp), InkElevation.MENU)
                        .padding(vertical = 8.dp),
                ) {
                    // Once per open: a second tap while the menu closes must not take out another pen.
                    InkMenuRow(removeLabel, onClick = { if (menu) { menu = false; onRemove() } }, icon = Ph.trash, danger = true)
                }
            }
        }
    }
}

/** .pp: a 46×34 r9 cream sample with a faint inner ring, the pen's stroke drawn on it in its ink. */
@Composable
private fun PenSample(pen: PenPreset, colour: Color, mm: Float) {
    val corner = cornerOf(9.dp)
    val marker = pen.tool == Tool.HIGHLIGHTER
    // A pencil's graphite is translucent on the page, so its sample is too.
    val alpha = if (marker) pen.config.highlighterAlpha.toFloat().coerceIn(0.2f, 0.9f) else if (pen.config.grain) 0.7f else 1f
    val strokeWidth = railSampleWidth(pen.tool, mm)
    Spacer(
        Modifier
            .size(46.dp, 34.dp)
            .drawWithCache {
                val r = CornerRadius(corner.toPx())
                val w = 1.dp.toPx()
                val ring = Stroke(w)
                val k = size.width / 46f
                val stroke = if (marker) Stroke(strokeWidth, cap = StrokeCap.Butt) else Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val path = if (marker) SAMPLE_BAND else SAMPLE_CURVE
                val ink = colour.copy(alpha = alpha)
                onDrawBehind {
                    drawRoundRect(SamplePaper, cornerRadius = r)
                    drawRoundRect(SampleRing, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), CornerRadius(r.x - w / 2f), style = ring)
                    scale(k, k, pivot = Offset.Zero) { drawPath(path, ink, style = stroke) }
                }
            },
    )
}

/** .rail-h: a 44×36 r12 button with an 18 dp text2 icon (the collapse caret on top, the plus at the foot). */
@Composable
private fun RailButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .size(44.dp, 36.dp)
            .clip(inkRounded(12.dp))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink.text2, modifier = Modifier.size(18.dp))
    }
}

/**
 * .rail-tab: a 28×56 tab flush with the screen edge, rounded 14 dp on the canvas side, one soft
 * shadow, a 16 dp caret pointing in. It fades in as the rail slides off ([shown], read in the layer)
 * and only takes taps while the rail is collapsed.
 */
@Composable
private fun BoxScope.PullTab(onLeft: Boolean, shown: () -> Float, enabled: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    val r = cornerOf(14.dp)
    val shape = remember(onLeft, r) {
        if (onLeft) AbsoluteRoundedCornerShape(topRight = r, bottomRight = r) else AbsoluteRoundedCornerShape(topLeft = r, bottomLeft = r)
    }
    val label = stringResource(R.string.pen_box_unfold)
    Box(
        Modifier
            .align(if (onLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
            .graphicsLayer { alpha = shown() }
            .size(28.dp, 56.dp)
            .inkSurface(shape, InkElevation.SOFT)
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }
                else Modifier.clearAndSetSemantics {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (onLeft) Ph.caretRight else Ph.caretLeft, contentDescription = null, tint = ink.text2, modifier = Modifier.size(16.dp))
    }
}
