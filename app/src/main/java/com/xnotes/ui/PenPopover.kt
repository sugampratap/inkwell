package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.stroke.Sample
import com.xnotes.core.stroke.StrokeEngine
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.PenBox
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardFold
import com.xnotes.ui.kit.InkCardFooter
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkHint
import com.xnotes.ui.kit.InkStepper
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.LocalInCardFrame
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.kit.animateUnlessPenDown
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** .plab (14/600) and .pval (14/600, text2, tabular): InkType.buttonSmall. */
private val RowLabel = InkType.buttonSmall
private val RowValue = InkType.buttonSmall.tnum()

/** .stabseg segments: 13 sp SemiBold, Bold when on. */
private val StabLabel = InkType.meta.copy(fontWeight = FontWeight.SemiBold)
private val StabLabelOn = InkType.meta.copy(fontWeight = FontWeight.Bold)

/** The nib tile label: 11/13 SemiBold, ExtraBold when chosen. */
private val NibLabelOn = InkType.tiny.copy(fontWeight = FontWeight.ExtraBold)

/** The preview's paper, cream in every theme, and its faint inner ring (.pv). */
private val PreviewPaper = ToPaper
private val PreviewRing = ToPreviewRing

/** --seg-sh's .5 px ring (light only; dark themes use the shadow alone). */
private val SegThumbRing = Color(0x0F000000)

/** .pcol: a 28 dp colour dot; Compose widens its touch target to 48 dp without changing layout. */
private val DOT = 28.dp

/** .to-cols: eight dots across the 300 dp content, then the next row. */
private const val COLOURS_PER_ROW = 8

/** The thickness stepper's ends, in px: closer than this to the range's end disables that button. */
private const val WIDTH_EPS = 1e-3f

/** Off, Low, Med, High as drawn, and as TalkBack says them. */
private val STAB_LABELS = listOf(R.string.pen_off, R.string.stab_low, R.string.stab_medium_short, R.string.stab_high)
private val STAB_NAMES = listOf(R.string.pen_off, R.string.stab_low, R.string.stab_medium, R.string.stab_high)

/**
 * The settings of a stroke tool, as a card under its button (mockup §4): the pens share one, with a
 * grid of pen types on top; the highlighter and the laser pointer have their own.
 *
 * Every control applies on the spot, so the page behind is the preview of record; the stroke in the
 * card is drawn by the same engine the page is, through the pen as it is tuned right now.
 */
@Composable
fun PenPopover(host: ToolPopupHost, tool: Tool, onDismiss: () -> Unit) {
    // Picking another pen type arms it and rebuilds the body around its own settings.
    var current by remember { mutableStateOf(tool) }
    ToolCardFrame(onDismiss) {
        // The frame is the card's one surface and its scroller, the anchored one and the dropdown
        // fallback alike, so the InkCard inside draws and scrolls nothing (a scroll inside the
        // dropdown's own scroll would be measured with no height limit and throw).
        CompositionLocalProvider(LocalInCardFrame provides true) {
            InkCard(title = stringResource(current.labelRes), onClose = onDismiss) {
                key(current) {
                    when {
                        current.isPen -> PenBody(host, current, onSaved = onDismiss) { t ->
                            host.hostArmTool(t)
                            current = t
                        }
                        current == Tool.HIGHLIGHTER -> HighlighterBody(host, onSaved = onDismiss)
                        current == Tool.LASER -> LaserBody(host)
                        else -> PenBody(host, current, onSaved = onDismiss) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PenBody(host: ToolPopupHost, tool: Tool, onSaved: () -> Unit, onPickType: (Tool) -> Unit) {
    val ink = LocalInk.current
    val base = remember { host.toolConfig(tool) }
    var width by remember { mutableStateOf(base.baseWidth.toFloat()) }
    // One control for pressure: 0% is no pressure at all, anything above it is how far a light
    // touch thins the line.
    var pressure by remember {
        mutableStateOf(if (base.pressureEnabled) ToolConversions.minFactorToSensitivity(base.pressureMinFactor).toFloat().coerceAtLeast(1f) else 0f)
    }
    val baseStab = remember { stabIndex(base.stabilisation.toFloat()) }
    var stab by remember { mutableIntStateOf(baseStab) }
    var multiplier by remember { mutableStateOf(ToolConversions.directionStrengthToMultiplier(base.directionStrength).toFloat()) }
    var speed by remember { mutableStateOf(ToolConversions.strengthToSpeed(base.speedStrength).toFloat()) }
    var taperTip by remember { mutableStateOf((base.taperMinFactor * 100).toFloat()) }
    var dashLen by remember { mutableStateOf(base.dashLength.toFloat()) }
    var gapLen by remember { mutableStateOf(base.dashGap.toFloat()) }
    var scale by remember { mutableStateOf(base.scale) }
    var colorOverride by remember { mutableStateOf(base.colorOverride) }
    var more by remember { mutableStateOf(false) }

    fun config(): ToolConfig = base.copy(
        baseWidth = width.toDouble(),
        pressureEnabled = pressure > 0.5f,
        pressureMinFactor = if (pressure > 0.5f) ToolConversions.sensitivityToMinFactor(pressure.toDouble()) else base.pressureMinFactor,
        // Untouched, the stored value goes back as it was, so a pen in the box still matches it exactly.
        stabilisation = if (stab == baseStab) base.stabilisation else stabValue(stab).toDouble(),
        directionStrength = if (tool == Tool.CALLIGRAPHY) ToolConversions.multiplierToDirectionStrength(multiplier.toDouble()) else base.directionStrength,
        speedStrength = if (tool == Tool.SPEED) ToolConversions.speedToStrength(speed.toDouble()) else base.speedStrength,
        taperMinFactor = if (tool == Tool.TAPER) taperTip.toDouble() / 100.0 else base.taperMinFactor,
        dashLength = dashLen.toDouble(),
        dashGap = gapLen.toDouble(),
        neon = false,
        scale = scale,
        colorOverride = colorOverride,
    )
    fun emit() = host.updateToolConfig(tool, config())

    val range = ToolConversions.widthRange(tool)
    val widthRange = range.start.toFloat()..range.endInclusive.toFloat()

    if (tool.isPen) {
        InkCardSection(first = true) {
            NibGrid(tool) { t -> if (t != tool) onPickType(t) }
        }
    }
    InkCardSection(first = !tool.isPen) {
        StrokePreview(tool, config(), colorOverride ?: host.inkOf(tool))
        ValueRow(stringResource(R.string.pen_thickness), Modifier.padding(top = 10.dp)) {
            InkStepper(
                value = widthLabelMm(width),
                onMinus = { width = stepWidthPx(width, -1, widthRange); emit() },
                onPlus = { width = stepWidthPx(width, 1, widthRange); emit() },
                canMinus = width > widthRange.start + WIDTH_EPS,
                canPlus = width < widthRange.endInclusive - WIDTH_EPS,
            )
        }
        InkSlider(width, widthRange, Modifier.padding(top = 2.dp)) { width = it; emit() }
    }
    InkCardSection {
        if (tool != Tool.DASHED) {
            ValueRow(stringResource(R.string.pen_pressure)) {
                Text(
                    if (pressure < 0.5f) stringResource(R.string.pen_off) else "${pressure.toInt()}%",
                    style = RowValue,
                    color = ink.text2,
                    maxLines = 1,
                )
            }
            InkSlider(pressure, 0f..100f, Modifier.padding(top = 2.dp)) { pressure = it; emit() }
        }
        if (tool != Tool.CALLIGRAPHY) {
            ValueRow(
                stringResource(R.string.pen_stabilisation),
                Modifier.padding(top = if (tool != Tool.DASHED) 10.dp else 0.dp),
                minHeight = 34.dp,
            ) {
                StabSegmented(stab) { stab = it; emit() }
            }
        }
    }
    InkCardSection {
        InkColourRow(host, tool, colorOverride) { colorOverride = it; emit() }
    }
    // The settings most hands never touch, folded away so the card stays the size of a hand.
    InkCardFold(expanded = more, onToggle = { more = !more }) {
        when (tool) {
            Tool.CALLIGRAPHY -> InkSliderRow(stringResource(R.string.pen_contrast), "%.1f×".format(multiplier), multiplier, 1f..5f) { multiplier = it; emit() }
            Tool.SPEED -> InkSliderRow(stringResource(R.string.pen_speed_thinning), "${speed.toInt()}%", speed, 0f..100f) { speed = it; emit() }
            Tool.TAPER -> InkSliderRow(stringResource(R.string.pen_tip), "${taperTip.toInt()}%", taperTip, 0f..100f) { taperTip = it; emit() }
            Tool.DASHED -> {
                // .to-pair: dash and gap side by side, 20 dp apart.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkSliderRow(stringResource(R.string.caption_dash), widthLabelMm(dashLen), dashLen, 2f..40f) { dashLen = it; emit() }
                    }
                    Box(Modifier.weight(1f)) {
                        InkSliderRow(stringResource(R.string.caption_gap), widthLabelMm(gapLen), gapLen, 2f..40f) { gapLen = it; emit() }
                    }
                }
            }
            else -> Unit
        }
        ToggleRow(stringResource(R.string.pen_same_size_any_zoom), !scale) { scale = !it; emit() }
    }
    if (host.hostHasPenBox && PenBox.holds(tool)) {
        InkCardFooter {
            InkStrongButton(
                stringResource(R.string.pen_box_save),
                onClick = { host.savePenInHand(tool); onSaved() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The highlighter's own colours, which are not the pens': pale enough to read text through. */
private val MARKER_INKS = listOf(
    InkPalette.MARKER_YELLOW,
    Rgba(74, 222, 128, 255),
    Rgba(56, 189, 248, 255),
    Rgba(244, 114, 182, 255),
    Rgba(251, 146, 60, 255),
    // Teal (#2DD4BF, the marker step of B2's teal) in place of the mockup's violet #C084FC: no purple in the app.
    Rgba(45, 212, 191, 255),
)

/**
 * The highlighter card (TO Frame 1): the marker over a line of notes as it will lay; thickness in mm with − / + over a
 * slider; opacity; Always straight, with a hint saying what each state does; the six marker inks and a custom one on
 * eight columns; Lighten instead (and its dark paper) in the fold; Save to pen box.
 */
@Composable
private fun HighlighterBody(host: ToolPopupHost, onSaved: () -> Unit) {
    val tool = Tool.HIGHLIGHTER
    val base = remember { host.toolConfig(tool) }
    var width by remember { mutableStateOf(base.baseWidth.toFloat()) }
    var opacity by remember { mutableStateOf(ToolConversions.highlighterAlphaToIntensity(base.highlighterAlpha).toFloat()) }
    var straight by remember { mutableStateOf(base.straightLine) }
    var inverse by remember { mutableStateOf(base.highlighterInverse) }
    var colorOverride by remember { mutableStateOf(base.colorOverride) }
    var more by remember { mutableStateOf(false) }
    fun config() = base.copy(
        baseWidth = width.toDouble(),
        highlighterAlpha = ToolConversions.intensityToHighlighterAlpha(opacity.toDouble()),
        straightLine = straight,
        highlighterInverse = inverse,
        colorOverride = colorOverride,
    )
    fun emit() = host.updateToolConfig(tool, config())

    val range = ToolConversions.widthRange(tool)
    val widthRange = range.start.toFloat()..range.endInclusive.toFloat()
    InkCardSection(first = true) {
        HighlighterPreview(width, config().highlighterAlpha.toFloat(), straight, inverse, colorOverride ?: host.inkOf(tool))
        ToolStepperSlider(
            label = stringResource(R.string.pen_thickness),
            value = widthLabelMm(width),
            sliderValue = width,
            range = widthRange,
            onMinus = { width = stepWidthPx(width, -1, widthRange); emit() },
            onPlus = { width = stepWidthPx(width, 1, widthRange); emit() },
            modifier = Modifier.padding(top = 12.dp),
        ) { width = it; emit() }
    }
    InkCardSection {
        ToolSliderRow(stringResource(R.string.hl_opacity), "${opacity.toInt()}%", opacity, 10f..90f) { opacity = it; emit() }
        ToggleRow(stringResource(R.string.hl_straight), straight, modifier = Modifier.padding(top = 12.dp), minHeight = 34.dp) {
            straight = it; emit()
        }
        InkHint(stringResource(if (straight) R.string.to_hl_straight_on_hint else R.string.hl_straight_hint))
    }
    InkCardSection {
        InkPresetColours(MARKER_INKS, colorOverride, host) { colorOverride = it; emit() }
    }
    InkCardFold(expanded = more, onToggle = { more = !more }) {
        ToggleRow(stringResource(R.string.hl_dark_paper), inverse, minHeight = 34.dp) { inverse = it; emit() }
        InkHint(stringResource(R.string.to_hl_lighten_hint), top = 2.dp)
    }
    if (host.hostHasPenBox) {
        InkCardFooter {
            InkStrongButton(
                stringResource(R.string.pen_box_save),
                onClick = { host.savePenInHand(tool); onSaved() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The preview's handwriting: the system's cursive (round-3 default 1), SemiBold; its size is set per box in the cache. */
private val HlSample = TextStyle(fontFamily = FontFamily.Cursive, fontWeight = FontWeight.SemiBold)
private val HlRuleLight = Color(0xFFECE7DC)
private val HlRuleDark = Color(0xFF3A3A3A)
private val HlInkLight = Color(0xFF1F2A44)
private val HlInkDark = Color(0xFFEDEDED)

/** The marker covers "Carnot cycle", the first 12 characters of the sample line (TO 578). */
private const val HL_MARKED_CHARS = 12

/**
 * A line of notes with the marker over it (TO 556–561, 575–581), in the 300×56 viewBox of the 56 dp preview. The text
 * layouts, the marker's path and its stroke are built once per tuning in the draw cache; Lighten instead's paper,
 * text and rule cross-fade by alpha (read only in draw), and snap while the pen is down.
 */
@Composable
private fun HighlighterPreview(widthPx: Float, alpha: Float, straight: Boolean, inverse: Boolean, ink: Rgba) {
    val penDown = LocalPenDown.current
    val dark = remember { Animatable(if (inverse) 1f else 0f) }
    LaunchedEffect(inverse) {
        val target = if (inverse) 1f else 0f
        animateUnlessPenDown(penDown, settle = { dark.snapTo(target) }) { dark.animateTo(target, tween(InkMotion.BASE, easing = InkMotion.Standard)) }
    }
    val measurer = rememberTextMeasurer()
    val lead = stringResource(R.string.to_hl_sample_lead)
    val line = stringResource(R.string.to_hl_sample_text)
    val colour = ink.toComposeColor()
    val art = remember(widthPx, alpha, straight, inverse, colour, lead, line, measurer) {
        Modifier.drawWithCache {
            val k = size.width / PREVIEW_W
            val style = HlSample.copy(fontSize = (25f * k).toSp())
            val first = measurer.measure(lead, style)
            val second = measurer.measure(line, style)
            val firstAt = Offset(16f * k, 40f * k - first.firstBaseline)
            val secondAt = Offset(62f * k, 40f * k - second.firstBaseline)
            val marked = minOf(HL_MARKED_CHARS, line.length)
            val x0 = secondAt.x - 4f * k
            val x1 = secondAt.x + second.getHorizontalPosition(marked, usePrimaryDirection = true) + 4f * k
            val lineTop = second.getLineTop(0)
            val y = secondAt.y + lineTop + (second.getLineBottom(0) - lineTop) * 0.58f
            val w = x1 - x0
            val marker = Path().apply {
                if (straight) {
                    moveTo(x0, y)
                    lineTo(x1, y)
                } else {
                    moveTo(x0, y + 1.2f * k)
                    cubicTo(x0 + w * 0.3f, y - 2.2f * k, x0 + w * 0.62f, y + 2.6f * k, x1, y - 1.4f * k)
                }
            }
            val pen = DrawStroke(highlighterPreviewWidth(widthPx) * k, cap = StrokeCap.Round)
            val markerInk = colour.copy(alpha = colour.alpha * alpha)
            val blend = if (inverse) BlendMode.Screen else BlendMode.Multiply
            val ruleY = 47.5f * k
            onDrawBehind {
                val t = dark.value
                drawLine(HlRuleLight, Offset(0f, ruleY), Offset(size.width, ruleY), strokeWidth = k, alpha = 1f - t)
                if (t > 0f) drawLine(HlRuleDark, Offset(0f, ruleY), Offset(size.width, ruleY), strokeWidth = k, alpha = t)
                drawText(first, HlInkLight, firstAt, alpha = 1f - t)
                drawText(second, HlInkLight, secondAt, alpha = 1f - t)
                if (t > 0f) {
                    drawText(first, HlInkDark, firstAt, alpha = t)
                    drawText(second, HlInkDark, secondAt, alpha = t)
                }
                drawPath(marker, markerInk, style = pen, blendMode = blend)
            }
        }
    }
    ToolPreview(dark = { dark.value }) {
        Spacer(Modifier.matchParentSize().then(art))
    }
}

/** The laser's beam colours: bright enough to glow on paper and on dark pages. */
private val LASER_INKS = listOf(
    InkPalette.LASER,
    Rgba(34, 197, 94, 255),
    Rgba(59, 130, 246, 255),
    Rgba(249, 115, 22, 255),
    // Pink #EC4899 (the beam step of B2's pink, as the others are their hues' 500s), the nearest non-purple, in place
    // of the mockup's magenta #D946EF: no purple in the app.
    Rgba(236, 72, 153, 255),
)

/**
 * The laser card (TO Frame 4): what it is for, a glowing swoop that follows Thickness and Glow, the thickness in mm with
 * − / + over a slider, Glow, how long the trail stays, and the five beam colours. No Save: the laser has no pen box.
 */
@Composable
private fun LaserBody(host: ToolPopupHost) {
    val tool = Tool.LASER
    val base = remember { host.toolConfig(tool) }
    var width by remember { mutableStateOf(base.baseWidth.toFloat()) }
    var glow by remember { mutableStateOf(ToolConversions.neonStrengthToIntensity(base.neonStrength).toFloat()) }
    var colour by remember { mutableStateOf(base.colorOverride ?: InkPalette.LASER) }
    var stay by remember { mutableStateOf((base.fadeAfterMs / 1000.0).toFloat()) }
    fun config() = base.copy(
        fadeAfterMs = stay * 1000.0,
        baseWidth = width.toDouble(),
        neon = true,
        neonStrength = ToolConversions.intensityToNeonStrength(glow.toDouble()),
        colorOverride = colour,
    )
    fun emit() = host.updateToolConfig(tool, config())

    val widthRange = LASER_WIDTH_PX
    InkCardSection(first = true) {
        // .to-hint.top: the hint above the controls, 12 dp before them.
        InkHint(stringResource(R.string.laser_hint), top = 0.dp, bottom = 12.dp)
        LaserPreview(width, glow, colour)
        ToolStepperSlider(
            label = stringResource(R.string.pen_thickness),
            value = widthLabelMm(width),
            sliderValue = width,
            range = widthRange,
            onMinus = { width = stepWidthPx(width, -1, widthRange); emit() },
            onPlus = { width = stepWidthPx(width, 1, widthRange); emit() },
            modifier = Modifier.padding(top = 12.dp),
        ) { width = it; emit() }
    }
    InkCardSection {
        ToolSliderRow(stringResource(R.string.pen_glow), "${glow.toInt()}%", glow, 0f..100f) { glow = it; emit() }
        ToolSliderRow(
            stringResource(R.string.laser_stays),
            String.format(Locale.US, "%.1f s", stay),
            stay,
            0.5f..5f,
            modifier = Modifier.padding(top = 12.dp),
        ) { stay = kotlin.math.round(it * 10f) / 10f; emit() }
    }
    // A colour-only last section ends 18 dp above the card's edge (TO 746).
    InkCardSection(bottom = 18.dp) {
        InkPresetColours(LASER_INKS, colour, host, custom = false) { if (it != null) { colour = it; emit() } }
    }
}

/**
 * The laser's swoop (TO 729–732) in the preview's 300×56 viewBox: two halos that widen and brighten with Glow, the core,
 * and a white-hot tip. Path and strokes are built once per tuning in the draw cache; no blur.
 */
@Composable
private fun LaserPreview(widthPx: Float, glow: Float, ink: Rgba) {
    val colour = ink.toComposeColor()
    val art = remember(widthPx, glow, colour) {
        Modifier.drawWithCache {
            val k = size.width / PREVIEW_W
            val path = PathParser().parsePathString(LASER_PREVIEW_D).toPath()
            val w = laserPreviewWidth(widthPx)
            val layers = glowLayers(w, glow)
            val strokes = layers.map { DrawStroke(it.width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
            val tip = Color.White.copy(alpha = 0.85f)
            onDrawBehind {
                scale(k, k, pivot = Offset.Zero) {
                    for (i in layers.indices) {
                        val a = layers[i].alpha
                        if (a > 0f) drawPath(path, colour.copy(alpha = colour.alpha * a), style = strokes[i])
                    }
                    drawCircle(tip, w * 0.9f, Offset(276f, 14f))
                }
            }
        }
    }
    ToolPreview {
        Spacer(Modifier.matchParentSize().then(art))
    }
}

@androidx.annotation.StringRes
private fun penTypeLabel(t: Tool): Int = when (t) {
    Tool.PEN -> R.string.pen_type_fountain
    Tool.BALLPOINT -> R.string.pen_type_ballpoint
    Tool.TAPER -> R.string.pen_type_brush
    Tool.CALLIGRAPHY -> R.string.pen_type_calligraphy
    Tool.SPEED -> R.string.pen_type_quill
    Tool.PENCIL -> R.string.pen_type_pencil
    Tool.DASHED -> R.string.pen_type_dashed
    else -> t.labelRes
}

/** .prow: a label on the left, its control or value on the right. */
@Composable
private fun ValueRow(label: String, modifier: Modifier = Modifier, minHeight: Dp = 28.dp, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = RowLabel, color = LocalInk.current.text, maxLines = 2, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** The pen types (D5), in [Tool.penTypes] order, three to a row. */
@Composable
private fun NibGrid(selected: Tool, onPick: (Tool) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in Tool.penTypes.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) NibTile(t, t == selected, Modifier.weight(1f)) { onPick(t) }
            }
        }
    }
}

/**
 * .nib: a 52 dp tile, r12, with a 1 dp --line edge, the nib drawn above its name. The chosen one takes a
 * 2 dp near-black edge and an extra-bold name. It shrinks to .96 while held.
 */
@Composable
private fun NibTile(tool: Tool, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val corner = cornerOf(12.dp)
    Column(
        modifier
            .height(52.dp)
            .pressScale(src, 0.96f)
            .clip(inkRounded(12.dp))
            .selectable(selected, src, LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .drawBehind {
                val w = (if (selected) 2.dp else 1.dp).toPx()
                drawRoundRect(
                    if (selected) ink.solid else ink.line,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2f),
                    style = DrawStroke(w),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NibArt(tool)
        Spacer(Modifier.height(2.dp))
        // Shrinks to fit rather than clipping: a long name has to read whole in a third of the card.
        BasicText(
            stringResource(penTypeLabel(tool)),
            style = (if (selected) NibLabelOn else InkType.tiny).copy(color = ink.text),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}

/** Whether the hairline before segment [i] shows: never beside the chosen segment [index]. */
private fun stabDividerShown(i: Int, index: Int): Boolean = i != index && i != index + 1

/**
 * .stabseg (W 460–467): 184×34, r10 --seg-tr track; a 45×30 r8 --seg-th thumb with one soft shadow
 * that glides (InkMotion.glide) to the chosen step; 1 dp --line hairlines between the others, fading
 * over 120 ms beside the chosen one; a segment that is not chosen dims to .5 while held.
 *
 * The thumb and hairlines are Animatables read only in layer and draw blocks, so a change recomposes
 * nothing per frame; while the pen is down (LocalPenDown) they snap.
 */
@Composable
private fun StabSegmented(selected: Int, onSelect: (Int) -> Unit) {
    val ink = LocalInk.current
    val penDown = LocalPenDown.current
    val n = STAB_LABELS.size
    val thumb = remember { Animatable(selected.toFloat()) }
    val lines = remember { List(n - 1) { i -> Animatable(if (stabDividerShown(i + 1, selected)) 1f else 0f) } }
    LaunchedEffect(selected) {
        // Snaps with the pen down, and jumps to its end if the pen lands mid-glide.
        animateUnlessPenDown(
            penDown,
            settle = {
                thumb.snapTo(selected.toFloat())
                lines.forEachIndexed { i, a -> a.snapTo(if (stabDividerShown(i + 1, selected)) 1f else 0f) }
            },
        ) {
            launch { thumb.animateTo(selected.toFloat(), InkMotion.glide()) }
            lines.forEachIndexed { i, a ->
                launch { a.animateTo(if (stabDividerShown(i + 1, selected)) 1f else 0f, tween(InkMotion.FAST, easing = InkMotion.Standard)) }
            }
        }
    }
    val thumbShape = inkRounded(8.dp)
    Box(
        Modifier
            .size(184.dp, 34.dp)
            .clip(inkRounded(10.dp))
            .background(ink.segTrack)
            .drawBehind {
                val pad = 2.dp.toPx()
                val seg = (size.width - 2 * pad) / n
                val inset = 9.dp.toPx()
                for (i in 1 until n) {
                    val a = lines[i - 1].value
                    if (a > 0f) {
                        drawRect(
                            ink.line.copy(alpha = ink.line.alpha * a),
                            topLeft = Offset(pad + seg * i - 0.5.dp.toPx(), inset),
                            size = Size(1.dp.toPx(), size.height - 2 * inset),
                        )
                    }
                }
            }
            .padding(2.dp)
            .selectableGroup(),
    ) {
        Box(
            Modifier
                .size(45.dp, 30.dp)
                .graphicsLayer {
                    translationX = size.width * thumb.value
                    shadowElevation = 1.dp.toPx()
                    shape = thumbShape
                    clip = false
                    ambientShadowColor = ink.shadow
                    spotShadowColor = ink.shadow
                }
                .background(ink.segThumb, thumbShape)
                .then(if (ink.isDark) Modifier else Modifier.border(0.5.dp, SegThumbRing, thumbShape)),
        )
        Row(Modifier.matchParentSize()) {
            STAB_LABELS.forEachIndexed { i, res ->
                val on = i == selected
                val src = remember { MutableInteractionSource() }
                val held = src.collectIsPressedAsState()
                val name = stringResource(STAB_NAMES[i])
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(on, src, null, role = Role.RadioButton) { if (!on) onSelect(i) }
                        .semantics { contentDescription = name }
                        .graphicsLayer { alpha = if (held.value && !on) 0.5f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(res),
                        style = if (on) StabLabelOn else StabLabel,
                        color = if (on) ink.text else ink.text2,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/**
 * The bar's swatches, then the starred favourites, then a custom colour. A swatch makes the pen follow
 * the bar again; the custom colour pins this pen to its own ink whatever the bar is set to.
 */
@Composable
private fun InkColourRow(host: ToolPopupHost, tool: Tool, colorOverride: Rgba?, onOverride: (Rgba?) -> Unit) {
    val swatches = host.hostToolbarColors.take(host.hostSwatchCount)
    val favourites = LocalInkFavourites.current?.colors.orEmpty().filter { it !in swatches }
    val dots = ArrayList<@Composable () -> Unit>()
    // Every pen keeps its own colour, so a swatch sets this pen's ink, whichever pen is in hand.
    swatches.forEachIndexed { i, c ->
        dots.add {
            InkSwatch(c.toComposeColor(), selected = colorOverride == c, size = DOT, cell = DOT, contentDescription = inkColourLabel(c, i, swatches.size)) {
                onOverride(c)
                if (host.hostTool == tool) host.hostPickSwatch(i)
            }
        }
    }
    // Starred colours ride along after the bar's own, so a favourite is one tap from any pen.
    for (c in favourites) {
        dots.add {
            val label = stringResource(R.string.ink_colour_starred, hueName(rememberExplorerWords(), c))
            InkSwatch(c.toComposeColor(), selected = colorOverride == c, size = DOT, cell = DOT, contentDescription = label) { onOverride(c) }
        }
    }
    dots.add { AddColourDot(host, colorOverride?.takeIf { it !in swatches && it !in favourites }) { onOverride(it) } }
    ColourGrid(dots)
}

/**
 * A fixed set of inks for a tool with its own palette (the marker's six, the laser's five), plus a custom colour unless
 * [custom] is off, on .to-cols' eight fixed columns (TO 46): a short row keeps the left columns.
 */
@Composable
private fun InkPresetColours(inks: List<Rgba>, current: Rgba?, host: ToolPopupHost, custom: Boolean = true, onPick: (Rgba?) -> Unit) {
    val dots = ArrayList<@Composable () -> Unit>(inks.size + 1)
    inks.forEachIndexed { i, c ->
        dots.add { InkSwatch(c.toComposeColor(), selected = current == c, size = DOT, cell = DOT, contentDescription = inkColourLabel(c, i, inks.size)) { onPick(c) } }
    }
    if (custom) dots.add { AddColourDot(host, current?.takeIf { it !in inks }) { onPick(it) } }
    ToolColourGrid(dots)
}

/**
 * .pcols / .to-cols: one row spreads its dots edge to edge across the content; past [COLOURS_PER_ROW]
 * they wrap, and every row then keeps the eight-column spacing, so later dots line up under the first.
 */
@Composable
private fun ColourGrid(dots: List<@Composable () -> Unit>) {
    val rows = dots.chunked(COLOURS_PER_ROW)
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (dot in row) dot()
                if (rows.size > 1) repeat(COLOURS_PER_ROW - row.size) { Spacer(Modifier.size(DOT)) }
            }
        }
    }
}

/**
 * A colour's TalkBack name (review I5): its hue in the library's colour words ("Blue"), then its place among
 * [count] ("Blue, colour 2 of 5"), so two dots of one hue still differ.
 */
@Composable
internal fun inkColourLabel(c: Rgba, index: Int, count: Int): String =
    stringResource(R.string.ink_colour_of, hueName(rememberExplorerWords(), c), index + 1, count)

/**
 * .padd (TO 583): the + after the inks, opening the shared picker for a colour of the pen's own. It goes solid
 * near-black while the picker is open or a custom colour is in use. The picker opens beside the card (SC 478, TO 592),
 * so the card's preview line takes each pick live; it stays open across picks.
 */
@Composable
private fun AddColourDot(host: ToolPopupHost, custom: Rgba?, onPick: (Rgba) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        InkAddSwatch(colour = null, lit = open || custom != null, contentDescription = stringResource(R.string.material_custom_colour)) { open = true }
        if (open) {
            ColorPickerPopup(
                initial = custom ?: host.hostToolbarColors.getOrNull(host.hostActiveColorIndex),
                recents = host.hostRecentColors,
                onDismiss = { open = false },
                onPick = onPick,
            )
        }
    }
}

/**
 * A sample line through the pen as tuned: an S that presses in and lifts off, drawn by the stroke
 * engine itself so the preview cannot disagree with the page. It sits on cream paper with a faint inner
 * ring in every theme (.pv), [height] tall (48 dp in the pen card).
 *
 * The geometry is built once per tuning: the draw cache is keyed on (tool, config, ink), so a slider
 * tick rebuilds it once and a plain redraw reuses it.
 */
@Composable
private fun StrokePreview(tool: Tool, config: ToolConfig, ink: Rgba, height: Dp = 48.dp) {
    val alpha = if (tool == Tool.HIGHLIGHTER) config.highlighterAlpha.toFloat() else 1f
    val colour = ink.toComposeColor()
    val corner = cornerOf(12.dp)
    val strokes = remember(tool, config, colour) {
        Modifier.drawWithCache {
            if (config.grain) {
                // Graphite is a texture, so it is drawn by the page's own renderer, in dp, where a
                // texel of grain is about what it is on a page at a comfortable zoom.
                val unit = 1.dp.toPx()
                val pencil = previewPencil(config, ink, size, unit)
                return@drawWithCache onDrawBehind {
                    drawIntoCanvas { c ->
                        val r = com.xnotes.platform.AndroidRenderer(c.nativeCanvas)
                        r.scale(unit.toDouble(), unit.toDouble())
                        pencil.paint(r)
                    }
                }
            }
            val shapes = previewShapes(tool, config, size, 1.dp.toPx())
            onDrawBehind {
                for (s in shapes) drawPath(s.path, colour.copy(alpha = colour.alpha * s.alpha), style = s.style)
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(inkRounded(12.dp))
            .drawBehind {
                drawRect(PreviewPaper)
                val w = 1.dp.toPx()
                drawRoundRect(
                    PreviewRing,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2f),
                    style = DrawStroke(w),
                )
            },
    ) {
        // Drawn opaque and faded as one layer, so a translucent marker's body and its round ends
        // do not double up where they overlap, exactly as the page composites it.
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = alpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .then(strokes),
        )
    }
}

/** One filled or stroked piece of the preview, with its share of the ink's alpha. */
private class PreviewShape(val path: Path, val alpha: Float, val style: DrawStyle)

/**
 * The pencil's preview stroke: the same S the other pens draw, as a real [com.xnotes.core.model.Stroke]
 * in dp (the box is [size] px, [unit] px to the dp), so it is painted exactly as the page paints one.
 */
private fun previewPencil(config: ToolConfig, ink: Rgba, size: Size, unit: Float): com.xnotes.core.model.Stroke {
    val w = size.width / unit
    val h = size.height / unit
    val n = 64
    val left = 22.0
    val right = w - 22.0
    val mid = h / 2.0
    val amp = h * 0.22
    val samples = ArrayList<Sample>(n)
    for (i in 0 until n) {
        val u = i / (n - 1.0)
        val e = (1 - cos(PI * u)) / 2
        samples += Sample(left + (right - left) * e, mid - amp * sin(2 * PI * u), 0.25 + 0.65 * sin(PI * u), u * 420.0)
    }
    return com.xnotes.core.model.Stroke(
        Tool.PENCIL,
        config.copy(rgba = ink, baseWidth = config.baseWidth.coerceAtMost(h * 0.7), colorOverride = null),
        samples,
        smoothScale = config.stabilisationFactor,
    )
}

/** The preview's ink for [tool] tuned as [config] in a box of [size] px; [unit] is one dp in px. */
private fun previewShapes(tool: Tool, config: ToolConfig, size: Size, unit: Float): List<PreviewShape> {
    val n = 64
    val left = 22f * unit
    val right = size.width - 22f * unit
    val mid = size.height / 2
    val amp = size.height * 0.22f
    val samples = ArrayList<Sample>(n)
    for (i in 0 until n) {
        val u = i / (n - 1.0)
        // Eased along x, so the middle runs fast and the ends slow, as a hand writes a stroke.
        val e = (1 - cos(PI * u)) / 2
        val x = left + (right - left) * e
        val y = if (tool == Tool.HIGHLIGHTER) mid + amp * 0.15 * sin(2 * PI * u) else mid - amp * sin(2 * PI * u)
        val p = 0.25 + 0.65 * sin(PI * u)
        samples += Sample(x, y, p, u * 420.0)
    }
    if (tool == Tool.DASHED) {
        val path = Path()
        samples.forEachIndexed { i, s -> if (i == 0) path.moveTo(s.x.toFloat(), s.y.toFloat()) else path.lineTo(s.x.toFloat(), s.y.toFloat()) }
        val style = DrawStroke(
            width = (config.baseWidth * unit).toFloat(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf((config.dashLength * unit).toFloat(), (config.dashGap * unit + config.baseWidth * unit).toFloat())),
        )
        return listOf(PreviewShape(path, 1f, style))
    }
    // A page pixel is drawn at about a dp, which is roughly how it looks at a comfortable zoom.
    val width = (config.baseWidth * unit).coerceAtMost(size.height * 0.7)
    val g = StrokeEngine.build(
        samples,
        baseWidth = width,
        pressureEnabled = config.pressureEnabled,
        m = config.pressureMinFactor,
        ds = config.directionStrength,
        speedStrength = config.speedStrength,
        taperEnabled = config.taperEnabled,
        taperMinFactor = config.taperMinFactor,
        speedScale = 1.0 / unit,
        holdEnds = tool == Tool.PEN || tool == Tool.BALLPOINT || tool == Tool.HIGHLIGHTER,
        smoothScale = unit.toDouble() * config.stabilisationFactor,
        inkRev = config.inkRev,
        taperLen = StrokeEngine.BRUSH_TAPER_LEN * unit,
    )
    val out = ArrayList<PreviewShape>(4)
    if (config.neon && tool != Tool.HIGHLIGHTER) {
        // The glow, roughly: a wide soft band under the line.
        val c = g.centerline
        val halo = Path()
        for (i in 0 until c.size / 2) if (i == 0) halo.moveTo(c[0], c[1]) else halo.lineTo(c[2 * i], c[2 * i + 1])
        out += PreviewShape(halo, 0.18f, DrawStroke(width = (width * 4).toFloat(), cap = StrokeCap.Round))
        out += PreviewShape(halo, 0.30f, DrawStroke(width = (width * 2).toFloat(), cap = StrokeCap.Round))
    }
    val l = g.leftRail
    val r = g.rightRail
    if (l.size >= 4 && r.size == l.size) {
        val body = Path()
        body.moveTo(l[0], l[1])
        for (i in 1 until l.size / 2) body.lineTo(l[2 * i], l[2 * i + 1])
        for (i in r.size / 2 - 1 downTo 0) body.lineTo(r[2 * i], r[2 * i + 1])
        body.close()
        out += PreviewShape(body, 1f, Fill)
    }
    // Round ends, which is what both the page and the hand expect of ink.
    val c = g.centerline
    val hw = g.halfWidths
    if (hw.isNotEmpty()) {
        val ends = Path()
        ends.addOval(Rect(Offset(c[0], c[1]), hw[0]))
        val k = hw.size - 1
        ends.addOval(Rect(Offset(c[2 * k], c[2 * k + 1]), hw[k]))
        out += PreviewShape(ends, 1f, Fill)
    }
    return out
}
