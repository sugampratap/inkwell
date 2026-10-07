package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TapePattern
import com.xnotes.core.model.TapeShape
import com.xnotes.core.tools.TapeConfig
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkCard
import com.xnotes.ui.kit.InkCardCaption
import com.xnotes.ui.kit.InkCardSection
import com.xnotes.ui.kit.InkHint
import com.xnotes.ui.kit.InkOptionCard
import com.xnotes.ui.kit.InkOptionCardGrid
import com.xnotes.ui.kit.InkOptionCardSize
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.theme.toComposeColor

/**
 * The tape tool's card (TO Frame 5), opened by tapping the armed tape button as every tool's is: a strip of the roll
 * as it will be laid, its colour, its print (each tile a strip of that print), its width, and the two actions GoodNotes
 * users ask for most, peeling back or covering every strip in the note at once, with how many there are.
 *
 * Shared by both editors through [ToolPopupHost], like the other tool cards, so the two cannot drift apart.
 */
@Composable
fun TapePopover(host: ToolPopupHost, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(host.hostTapeConfig) }
    var counts by remember { mutableStateOf(host.hostTapeCounts()) }

    fun emit(next: TapeConfig) {
        config = next
        host.updateTapeConfig(next)
    }

    ToolCardFrame(onDismiss) {
        InkCard(title = stringResource(R.string.tool_tape), onClose = onDismiss) {
            InkCardSection(first = true) {
                // .to-hint.top: what tape is for, above the preview.
                InkHint(stringResource(R.string.tape_hint), top = 0.dp, bottom = 12.dp)
                val previewLabel = stringResource(R.string.tape_preview)
                ToolPreview(Modifier.semantics { contentDescription = previewLabel }) {
                    TapeStrip(config, Modifier.matchParentSize())
                }
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.tape_colour))
                val dots = ArrayList<@Composable () -> Unit>(TapeConfig.COLORS.size)
                TapeConfig.COLORS.forEachIndexed { i, c ->
                    dots.add {
                        InkSwatch(c.toComposeColor(), selected = c == config.color, size = 28.dp, cell = 28.dp, contentDescription = inkColourLabel(c, i, TapeConfig.COLORS.size)) {
                            emit(config.copy(color = c))
                        }
                    }
                }
                ToolColourGrid(dots)
            }
            InkCardSection {
                InkCardCaption(stringResource(R.string.tape_pattern))
                val patterns = TapePattern.entries
                InkOptionCardGrid(count = patterns.size, columns = 4, modifier = Modifier.fillMaxWidth()) { i ->
                    val p = patterns[i]
                    TapePatternTile(config.color, p, selected = p == config.pattern, modifier = Modifier.weight(1f)) {
                        emit(config.copy(pattern = p))
                    }
                }
            }
            InkCardSection {
                ToolStepperSlider(
                    label = stringResource(R.string.tape_width),
                    value = widthLabelMm(config.width),
                    sliderValue = config.width.toFloat(),
                    range = TAPE_WIDTH_PX,
                    onMinus = { emit(config.copy(width = stepTapeWidth(config.width, -1))) },
                    onPlus = { emit(config.copy(width = stepTapeWidth(config.width, 1))) },
                ) { emit(config.copy(width = kotlin.math.round(it).toDouble())) }
            }
            // The count line and the buttons end 18 dp above the card's edge (TO 811).
            InkCardSection(bottom = 18.dp) {
                when (val line = tapeCountLine(counts)) {
                    TapeCountLine.None -> {
                        InkCardCaption(stringResource(R.string.to_tape_in_note))
                        InkHint(stringResource(R.string.tape_none), top = 0.dp)
                    }
                    is TapeCountLine.Counts -> {
                        InkCardCaption(
                            stringResource(R.string.to_tape_in_note),
                            stringResource(
                                R.string.to_tape_counts,
                                pluralStringResource(R.plurals.to_tape_strips, line.strips, line.strips),
                                pluralStringResource(R.plurals.to_tape_peeled, line.peeled, line.peeled),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InkSecondaryButton(
                                stringResource(R.string.tape_reveal_all),
                                onClick = {
                                    host.setAllTapeRevealed(true)
                                    counts = host.hostTapeCounts()
                                },
                                modifier = Modifier.weight(1f),
                                icon = Ph.eye,
                            )
                            InkSecondaryButton(
                                stringResource(R.string.tape_hide_all),
                                onClick = {
                                    host.setAllTapeRevealed(false)
                                    counts = host.hostTapeCounts()
                                },
                                modifier = Modifier.weight(1f),
                                icon = Ph.eyeSlash,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** TapeItem's two-step drop shadow (alpha 16 and 30), as the page draws it. */
private val TapeShadowFar = Color(0x10000000)
private val TapeShadowNear = Color(0x1E000000)

private const val PREVIEW_SEED = 7

/** The tiles' strips use the mockup's seed (TO 822). */
private const val TILE_SEED = 4

/** A strip laid out once, as paths ready to draw: shadows, body, print, dots and the cut edge. */
private class PreparedTape(shape: TapeShape, color: Rgba, edgeWidth: Float) {
    val far = path(shape.shadowFar)
    val near = path(shape.shadowNear)
    val body = path(shape.body)
    val prints = shape.pattern.map(::path)
    val dots = shape.dots.map { Offset(it.center.x.toFloat(), it.center.y.toFloat()) }
    val radii = shape.dots.map { it.radius.toFloat() }
    val fill = color.toComposeColor()
    val print = TapeItem.patternColor(color).toComposeColor()
    val edge = TapeItem.edgeColor(color).toComposeColor()
    val edgeStroke = Stroke(width = edgeWidth)
}

private fun DrawScope.drawTape(t: PreparedTape) {
    drawPath(t.far, TapeShadowFar)
    drawPath(t.near, TapeShadowNear)
    drawPath(t.body, t.fill)
    for (p in t.prints) drawPath(p, t.print)
    for (i in t.dots.indices) drawCircle(t.print, t.radii[i], t.dots[i])
    drawPath(t.body, t.edge, style = t.edgeStroke)
}

/**
 * A strip of the roll as it will go down, drawn from the same geometry the page uses ([TapeItem.buildShape]), so the
 * preview cannot disagree with the strip it promises. Built once per tape style in the draw cache.
 */
@Composable
private fun TapeStrip(config: TapeConfig, modifier: Modifier) {
    val art = remember(config) {
        Modifier.drawWithCache {
            // Page px to preview px: about how the strip looks on a page at a comfortable zoom, held within the
            // preview's height so the widest roll still fits.
            val across = (config.width * 0.6 * density).coerceIn(8.0 * density, size.height * 0.8)
            val inset = 18.0 * density
            val cy = size.height / 2.0
            val shape = TapeItem.buildShape(Pt(inset, cy), Pt(size.width - inset, cy), across, config.pattern, PREVIEW_SEED)
            val strip = PreparedTape(shape, config.color, density)
            onDrawBehind { drawTape(strip) }
        }
    }
    Spacer(modifier.then(art))
}

/** A pattern tile (.to-pats .nib, TO 809): a 34-unit strip of [pattern] in the current [color], over its name. */
@Composable
private fun TapePatternTile(color: Rgba, pattern: TapePattern, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val strip = remember(color, pattern) {
        val shape = TapeItem.buildShape(Pt(5.0, 11.0), Pt(39.0, 11.0), 13.0, pattern, TILE_SEED)
        PreparedTape(shape, color, TapeItem.edgeWidth(13.0).toFloat())
    }
    InkOptionCard(
        selected = selected,
        label = stringResource(pattern.labelRes),
        onClick = onClick,
        modifier = modifier,
        cardSize = InkOptionCardSize.Tile,
    ) { _ ->
        // The scope is in dp but `size` is in px (InkOptionCard), so the tile is fitted in dp.
        val k = minOf(artScale(size.width, density, TILE_ART_W), artScale(size.height, density, TILE_ART_H))
        translate((size.width / density - TILE_ART_W * k) / 2f, (size.height / density - TILE_ART_H * k) / 2f) {
            scale(k, k, pivot = Offset.Zero) { drawTape(strip) }
        }
    }
}

private fun path(points: List<Pt>): Path = Path().apply {
    if (points.size < 3) return@apply
    moveTo(points[0].x.toFloat(), points[0].y.toFloat())
    for (i in 1 until points.size) lineTo(points[i].x.toFloat(), points[i].y.toFloat())
    close()
}

@get:androidx.annotation.StringRes
private val TapePattern.labelRes: Int
    get() = when (this) {
        TapePattern.SOLID -> R.string.tape_pattern_solid
        TapePattern.STRIPES -> R.string.tape_pattern_stripes
        TapePattern.DOTS -> R.string.tape_pattern_dots
        TapePattern.GRID -> R.string.tape_pattern_grid
    }
