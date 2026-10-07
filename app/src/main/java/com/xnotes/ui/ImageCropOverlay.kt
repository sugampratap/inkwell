package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.ImageCropSession
import com.xnotes.core.model.ImageGeometry
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkPill
import com.xnotes.ui.kit.InkPillAction
import com.xnotes.ui.kit.InkPillDivider
import kotlin.math.hypot

/**
 * The crop tool, drawn over either editor while [SelectionMenuHost.imageCrop] is open (TI Frame 6).
 *
 * The editor shows the whole picture underneath (the session put it there); this layer dims what falls outside the
 * crop box, draws the box with Samsung-style corner brackets, edge grips (each on a dark halo, so they read on a white
 * photo) and a rule-of-thirds grid, and turns drags into [ImageCropSession.drag] calls: a corner or an edge moves
 * that side, a press inside slides the box. The shared pill at the bottom offers Cancel | the ratio chips | Reset,
 * Done; exactly one chip lights. Back cancels.
 *
 * It owns every touch while open, so a stray finger cannot pan the page out from under the crop. The session works in
 * the picture's own frame; [SelectionMenuHost.imageCropToViewport] places that frame on screen, and the inverse used
 * for touches is worked out from three mapped points, so the same layer serves the paged editor (page space, any view
 * rotation) and the canvas (content space).
 */
@Composable
fun ImageCropOverlay(host: SelectionMenuHost, session: ImageCropSession) {
    val density = LocalDensity.current
    var tick by remember(session) { mutableIntStateOf(0) }
    BackHandler { host.endImageCrop(apply = false) }

    // Frame -> viewport, through the item space the host maps.
    fun toView(p: Pt): Pt = host.imageCropToViewport(session.toItem(p))

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(session) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val map = FrameMap.of(::toView)
                        val scale = map.scale.coerceAtLeast(1e-6)
                        val at = map.toFrame(down.position.x.toDouble(), down.position.y.toDouble())
                        val tolerance = with(density) { 26.dp.toPx() } / scale
                        val handle = session.hit(at, tolerance) ?: return@awaitEachGesture
                        val from = session.box
                        val minSide = with(density) { 36.dp.toPx() } / scale
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChange() != Offset.Zero) {
                                val p = map.toFrame(change.position.x.toDouble(), change.position.y.toDouble())
                                session.drag(handle, from, at, p, minSide)
                                tick++
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") tick // redraw on every drag step
                val full = corners(session.full).map { toView(it) }
                val box = corners(session.box).map { toView(it) }
                val dim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addPolygon(full)
                    addPolygon(box)
                }
                drawPath(dim, Color.Black.copy(alpha = 0.58f))
                val white = Color.White
                // Rule of thirds, faint, inside the box.
                for (k in 1..2) {
                    val f = k / 3f
                    drawLine(white.copy(alpha = 0.4f), lerp(box[0], box[3], f), lerp(box[1], box[2], f), strokeWidth = 1.dp.toPx())
                    drawLine(white.copy(alpha = 0.4f), lerp(box[0], box[1], f), lerp(box[3], box[2], f), strokeWidth = 1.dp.toPx())
                }
                drawPath(Path().apply { addPolygon(box) }, white, style = Stroke(width = 1.5.dp.toPx()))
                // Corner brackets and edge grips, a fixed size on screen whatever the zoom: every grip on a dark
                // halo first (TI 1155), then all of them in white, so no halo covers a neighbour's white.
                val arm = 20.dp.toPx()
                val grips = ArrayList<Pair<Offset, Offset>>(12)
                for (i in 0 until 4) {
                    val c = box[i]
                    val next = box[(i + 1) % 4]
                    val prev = box[(i + 3) % 4]
                    grips += c.offset() to towards(c, next, arm)
                    grips += c.offset() to towards(c, prev, arm)
                    val mid = Pt((c.x + next.x) / 2.0, (c.y + next.y) / 2.0)
                    grips += towards(mid, c, arm * 0.6f) to towards(mid, next, arm * 0.6f)
                }
                val halo = Color.Black.copy(alpha = 0.42f)
                for ((a, b) in grips) drawLine(halo, a, b, strokeWidth = 6.5.dp.toPx(), cap = StrokeCap.Round)
                for ((a, b) in grips) drawLine(white, a, b, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        // The bar: Cancel | the ratios (one lights) | Reset, Done.
        val sourceAspect = remember(session) {
            val item = session.item
            val (w, h) = ImageGeometry.displaySize(item.image.width, item.image.height, null, item.orientation)
            if (h > 0.0) w / h else 1.0
        }
        // The lit chip follows every ratio pick and Reset. Derived, so a drag step (which bumps the tick too) recomposes
        // the bar only if it changes which chip lights; the drags themselves only redraw the canvas above.
        val lit by remember(session, sourceAspect) {
            derivedStateOf {
                @Suppress("UNUSED_EXPRESSION") tick
                litAspect(session.aspect, sourceAspect)
            }
        }
        InkPill(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
            InkPillAction(Ph.x, stringResource(R.string.crop_cancel), { host.endImageCrop(apply = false) })
            InkPillDivider()
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                for (chip in CropChip.entries) {
                    InkChip(
                        stringResource(chip.labelRes()),
                        selected = lit == chip,
                        onClick = {
                            session.setAspect(chip.aspectFor(sourceAspect))
                            tick++
                        },
                        modifier = Modifier.padding(horizontal = 2.dp),
                        icon = chip.glyph(),
                    )
                }
            }
            InkPillDivider()
            InkPillAction(Ph.clockCounterClockwise, stringResource(R.string.crop_reset), {
                session.reset()
                tick++
            })
            InkPillAction(Ph.check, stringResource(R.string.crop_done), { host.endImageCrop(apply = true) }, solid = true)
        }
    }
}

private fun CropChip.labelRes(): Int = when (this) {
    CropChip.FREE -> R.string.crop_free
    CropChip.ORIGINAL -> R.string.crop_original
    CropChip.SQUARE -> R.string.crop_square
    CropChip.R4_3 -> R.string.crop_4_3
    CropChip.R3_4 -> R.string.crop_3_4
    CropChip.R16_9 -> R.string.crop_16_9
}

private fun CropChip.glyph(): ImageVector = when (this) {
    CropChip.FREE -> FreeGlyph
    CropChip.ORIGINAL -> Ph.image
    CropChip.SQUARE -> SquareGlyph
    CropChip.R4_3 -> FourThreeGlyph
    CropChip.R3_4 -> ThreeFourGlyph
    CropChip.R16_9 -> SixteenNineGlyph
}

private val FreeGlyph: ImageVector by lazy { dashedRatioIcon() }
private val SquareGlyph: ImageVector by lazy { ratioIcon("ratio-1-1", 1.0) }
private val FourThreeGlyph: ImageVector by lazy { ratioIcon("ratio-4-3", 4.0 / 3.0) }
private val ThreeFourGlyph: ImageVector by lazy { ratioIcon("ratio-3-4", 3.0 / 4.0) }
private val SixteenNineGlyph: ImageVector by lazy { ratioIcon("ratio-16-9", 16.0 / 9.0) }

/** A ratio chip's rect (.ti-ar): [ratioGlyph]'s size centred in 16 × 16, a 1.6 border, r2.5; the chip tints it. */
private fun ratioIcon(name: String, q: Double): ImageVector {
    val (w, h) = ratioGlyph(q)
    val half = 0.8f
    val x0 = (16 - w) / 2f + half
    val y0 = (16 - h) / 2f + half
    val x1 = x0 + w - 2 * half
    val y1 = y0 + h - 2 * half
    val r = 2.5f - half
    return ImageVector.Builder(name, 16.dp, 16.dp, 16f, 16f).addPath(
        pathData = PathData {
            moveTo(x0 + r, y0)
            lineTo(x1 - r, y0)
            quadTo(x1, y0, x1, y0 + r)
            lineTo(x1, y1 - r)
            quadTo(x1, y1, x1 - r, y1)
            lineTo(x0 + r, y1)
            quadTo(x0, y1, x0, y1 - r)
            lineTo(x0, y0 + r)
            quadTo(x0, y0, x0 + r, y0)
            close()
        },
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
    ).build()
}

/** Free's glyph (.ti-ar.free): a dashed 14 × 11 rect, dashes 2.2 on and 1.6 off, 1.6 wide. */
private fun dashedRatioIcon(): ImageVector {
    val l = 1.8f
    val t = 3.3f
    val r = 14.2f
    val b = 12.7f
    return ImageVector.Builder("ratio-free", 16.dp, 16.dp, 16f, 16f).addPath(
        pathData = PathData {
            fun side(x0: Float, y0: Float, x1: Float, y1: Float) {
                val len = hypot(x1 - x0, y1 - y0)
                var s = 0f
                while (s < len) {
                    val e = minOf(len, s + 2.2f)
                    moveTo(x0 + (x1 - x0) * s / len, y0 + (y1 - y0) * s / len)
                    lineTo(x0 + (x1 - x0) * e / len, y0 + (y1 - y0) * e / len)
                    s += 3.8f
                }
            }
            side(l, t, r, t)
            side(r, t, r, b)
            side(r, b, l, b)
            side(l, b, l, t)
        },
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
    ).build()
}

private fun corners(r: Rect): List<Pt> = listOf(
    Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom),
)

private fun Pt.offset(): Offset = Offset(x.toFloat(), y.toFloat())

private fun lerp(a: Pt, b: Pt, f: Float): Offset =
    Offset((a.x + (b.x - a.x) * f).toFloat(), (a.y + (b.y - a.y) * f).toFloat())

/** The point [len] pixels from [from] towards [to] (or [to] itself when closer). */
private fun towards(from: Pt, to: Pt, len: Float): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val d = hypot(dx, dy)
    if (d < 1e-6) return from.offset()
    val f = minOf(1.0, len / d)
    return Offset((from.x + dx * f).toFloat(), (from.y + dy * f).toFloat())
}

private fun Path.addPolygon(points: List<Pt>) {
    if (points.isEmpty()) return
    moveTo(points[0].x.toFloat(), points[0].y.toFloat())
    for (i in 1 until points.size) lineTo(points[i].x.toFloat(), points[i].y.toFloat())
    close()
}

/**
 * The frame-to-viewport map as an affine, read off three mapped points, and its inverse for
 * touches. [scale] is viewport pixels per frame unit, for sizing tolerances.
 */
private class FrameMap(
    private val ox: Double,
    private val oy: Double,
    private val ax: Double,
    private val ay: Double,
    private val bx: Double,
    private val by: Double,
) {
    val scale: Double get() = hypot(ax, ay)

    fun toFrame(x: Double, y: Double): Pt {
        val det = ax * by - bx * ay
        if (kotlin.math.abs(det) < 1e-12) return Pt(0.0, 0.0)
        val dx = x - ox
        val dy = y - oy
        return Pt((dx * by - bx * dy) / det, (ax * dy - dx * ay) / det)
    }

    companion object {
        fun of(map: (Pt) -> Pt): FrameMap {
            val o = map(Pt(0.0, 0.0))
            val a = map(Pt(1.0, 0.0))
            val b = map(Pt(0.0, 1.0))
            return FrameMap(o.x, o.y, a.x - o.x, a.y - o.y, b.x - o.x, b.y - o.y)
        }
    }
}
