package com.xnotes.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import kotlin.math.abs
import kotlin.math.roundToInt

// B2 covers (Frame 1 .cv, .nb, .linen, .leather, .plate, .ribbon, .stitch, .deboss, .strap, .foil).

/** A cover's width over height: a little narrower than A4, as a bound notebook stands. */
internal const val COVER_RATIO = 0.75f

/** A notebook (.cv): r6 at the spine, r14 at the fore-edge. */
internal val NOTEBOOK_SHAPE = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp)

/** A sheet on the shelf: a canvas or a PDF (.cv.paper). */
internal val PAPER_SHAPE = RoundedCornerShape(14.dp)

internal fun coverShape(kind: EntryKind): Shape = if (kind == EntryKind.NOTE || kind == EntryKind.FOLDER) NOTEBOOK_SHAPE else PAPER_SHAPE

/** A canvas's paper and dot grid (.cnv). */
internal val CANVAS_PAPER = Color(0xFFFBFAF7)
private val CANVAS_DOT = Color(0x331F2A44)
private val PAPER_EDGE = Color(0x12000000)
private val PLATE = Color(0xFFF7F1E3)
private val PLATE_EDGE = Color(0x24785A28)
private val PLATE_INK = Color(0xFF1F2A44)
private val STITCH_INK = Color(0x57462D05)
private val DEBOSS_INK = Color(0x75462C06)

private val PlateTitle = TextStyle(fontFamily = FontFamily.Cursive, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, color = PLATE_INK, textAlign = TextAlign.Center)
private val DebossShadow = Shadow(Color.White.copy(alpha = 0.3f), Offset(0f, 1f), 0f)

/** .deboss b: the small spaced capitals (12/14, 800, 3px), and .deboss span: the big line (30/34, 800, -.5px). */
private val DebossTitle = InkType.small.copy(
    fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp, letterSpacing = 3.sp, color = DEBOSS_INK, textAlign = TextAlign.Center,
    shadow = DebossShadow,
)
private val DebossBig = InkType.display.copy(
    fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = DEBOSS_INK,
    textAlign = TextAlign.Center, shadow = DebossShadow,
)

/** .foil b: the big gold line (34/36, 800, -1px), and .foil span: the small capitals under it (9/12, 800, 2.2px, #CDB07A). */
private val FoilTitle = InkType.display.copy(
    fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
    brush = Brush.verticalGradient(listOf(Color(0xFFF1DDAA), Color(0xFFB48C4A))),
)
private val FoilSmall = InkType.small.copy(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp, color = Color(0xFFCDB07A))
private val BadgeText = InkType.small.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp)

/** The plate's corners, made once rather than three times on every cover's composition. */
private val PlateShape = RoundedCornerShape(6.dp)

// Each title's fitted size, kept across compositions: a cover scrolled back into view lays its title out once, not once per step of the search.
private val PlateAutoSize = RememberedAutoSize(TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp))
private val DebossAutoSize = RememberedAutoSize(TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 30.sp))
private val FoilAutoSize = RememberedAutoSize(TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 34.sp))

/**
 * The shelf's covers draw into a layer of their own, kept while nothing on the cover changes: a scroll then moves one
 * finished picture instead of replaying the cloth's gradients, grain, stitching, plate and titles on every frame.
 */
private val CachedArt = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

/**
 * What a note looks like on the shelf: cloth for a notebook, made up as its [CoverDesign]; the drawing on dotted
 * paper for a canvas; the first page for a PDF. One soft shadow each (--sh-cover). [mini] drops the decorations and
 * softens the shadow. [title] goes on the plate, deboss or foil. [cached] keeps a notebook's cloth as a finished layer
 * (the shelf, which scrolls it); leave it off where the cover is drawn scaled, as the opening does, so it stays sharp.
 */
@Composable
internal fun CoverArt(
    editor: Editor,
    e: BrowseEntry,
    kind: EntryKind,
    coverIndex: Int?,
    title: String?,
    modifier: Modifier = Modifier,
    mini: Boolean = false,
    cached: Boolean = false,
) {
    val ink = LocalInk.current
    val lift = if (mini) 1.dp else 6.dp
    when (kind) {
        EntryKind.CANVAS -> {
            val img = rememberThumb(editor, e, whole = true)
            Box(
                modifier
                    .shadow(lift, PAPER_SHAPE, clip = false, ambientColor = ink.shadow, spotColor = ink.shadow)
                    .clip(PAPER_SHAPE)
                    .drawWithCache {
                        val step = (if (mini) 6.dp else 14.dp).toPx()
                        val dot = 2.dp.toPx()
                        val dots = ArrayList<Offset>()
                        var y = step / 2
                        while (y < size.height) { var x = step / 2; while (x < size.width) { dots.add(Offset(x, y)); x += step }; y += step }
                        val drawn = img != null && img.width > 0 && img.height > 0
                        // A drawing brings its own paper: the sheet takes it, read off its corner once, so no seam shows.
                        val sheet = if (drawn) img!!.toPixelMap(0, 0, 1, 1)[0, 0] else CANVAS_PAPER
                        onDrawBehind {
                            drawRect(sheet)
                            if (!drawn) drawPoints(dots, PointMode.Points, CANVAS_DOT, strokeWidth = dot, cap = StrokeCap.Round)
                            if (drawn) {
                                // Cropped to fill, centred (the mockup's xMidYMid slice).
                                val s = maxOf(size.width / img!!.width, size.height / img.height)
                                val w = (img.width * s).roundToInt(); val h = (img.height * s).roundToInt()
                                drawImage(img, dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()), dstSize = IntSize(w, h))
                            }
                        }
                    }
                    .paperEdge(),
            )
        }
        EntryKind.PDF -> {
            val img = rememberThumb(editor, e, whole = true)
            Box(
                modifier
                    .shadow(lift, PAPER_SHAPE, clip = false, ambientColor = ink.shadow, spotColor = ink.shadow)
                    .clip(PAPER_SHAPE)
                    .background(Color.White)
                    .paperEdge(),
            ) {
                if (img != null) {
                    Image(img, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Ph.filePdf, null, tint = ink.text3, modifier = Modifier.size(if (mini) 16.dp else 28.dp).align(Alignment.Center))
                }
            }
        }
        else -> {
            val cloth = CoverPalette.colorFor(e.name, coverIndex)
            ClothCover(cloth, coverDesign(e.name, cloth.luminance()), title, mini, lift, cached, modifier)
        }
    }
}

/**
 * What a shelf tile is, for the grid's reuse of tiles that scrolled away: a notebook by its design, the others by kind.
 * A tile recycled for one of the same make keeps its plate, stitching or strap rather than rebuilding them.
 */
internal fun coverContentType(name: String, kind: EntryKind, coverIndex: Int?): Any = when (kind) {
    EntryKind.CANVAS, EntryKind.PDF -> kind
    else -> coverDesign(name, CoverPalette.colorFor(name, coverIndex).luminance())
}

/** Moves its element down by [fraction] of the height it may take (the cover's), as CSS's `top: 31%` does; no subcomposition. */
private fun Modifier.dropBy(fraction: Float): Modifier = layout { m, c ->
    val p = m.measure(c)
    val y = if (c.hasBoundedHeight) (c.maxHeight * fraction).roundToInt() else 0
    layout(p.width, p.height) { p.placeRelative(0, y) }
}

/** The inset 1dp hairline on paper covers (.cv.paper::after), drawn over their content. */
private fun Modifier.paperEdge(): Modifier = drawWithCache {
    val w = 1.dp.toPx()
    onDrawWithContent {
        drawContent()
        drawRoundRect(PAPER_EDGE, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(14.dp.toPx() - w / 2), style = Stroke(w))
    }
}

@Composable
private fun ClothCover(cloth: Color, design: CoverDesign, title: String?, mini: Boolean, lift: androidx.compose.ui.unit.Dp, cached: Boolean, modifier: Modifier) {
    val ink = LocalInk.current
    val leather = design != CoverDesign.PLATE
    Box(
        modifier
            .shadow(lift, NOTEBOOK_SHAPE, clip = false, ambientColor = ink.shadow, spotColor = ink.shadow)
            .clip(NOTEBOOK_SHAPE)
            // Inside the clip and the cover's own shadow: the layer holds what is on the cloth, the plate's shadow included.
            .then(if (cached) CachedArt else Modifier)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val px = density
                val grain = CoverTextures.brush(leather, px)
                // The navy strap cover's grain is lighter (--texo .30 against .42).
                val grainAlpha = if (design == CoverDesign.STRAP) 0.30f / 0.42f else 1f
                // radial-gradient(140% 80% at 12% 0%, rgba(255,255,255,.30), transparent 58%): a circle squashed to the ellipse.
                val rx = 1.4f * w
                val light = Brush.radialGradient(0f to Color.White.copy(alpha = 0.30f), 0.58f to Color.Transparent, center = Offset(0.12f * w, 0f), radius = rx)
                val squash = (0.8f * h) / rx
                val spineW = (if (mini) 7.dp else 14.dp).toPx()
                val spine = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.30f), 9f / 14f to Color.Black.copy(alpha = 0.10f),
                    11f / 14f to Color.White.copy(alpha = 0.18f), 1f to Color.Transparent,
                    startX = 0f, endX = spineW,
                )
                val foot = Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f))
                val hair = 1.dp.toPx()
                val ring = NOTEBOOK_SHAPE.createOutline(Size(w - hair, h - hair), layoutDirection, this)
                onDrawWithContent {
                    drawRect(cloth)
                    scale(1f, squash, pivot = Offset(0.12f * w, 0f)) { drawRect(light, size = Size(w, h / squash)) }
                    drawRect(spine, size = Size(spineW, h))
                    drawRect(foot)
                    drawRect(grain, alpha = grainAlpha)
                    drawContent()
                    // ::after: inset 1px black .10, a 1px white edge down the fore-edge (.14) and across the top (.12).
                    translate(hair / 2, hair / 2) { drawOutline(ring, Color.Black.copy(alpha = 0.10f), style = Stroke(hair)) }
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(w - hair / 2, 0f), Offset(w - hair / 2, h), hair)
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, hair / 2), Offset(w, hair / 2), hair)
                }
            },
    ) {
        if (mini || title == null) return@Box
        when (design) {
            CoverDesign.PLATE -> {
                BasicText(
                    title,
                    style = PlateTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    autoSize = PlateAutoSize,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 14.dp)
                        .dropBy(0.31f)
                        .fillMaxWidth()
                        .shadow(1.dp, PlateShape, ambientColor = ink.shadow, spotColor = ink.shadow)
                        .background(PLATE, PlateShape)
                        .border(1.dp, PLATE_EDGE, PlateShape)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 7.dp),
                )
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 30.dp).size(11.dp, 34.dp).drawWithCache {
                    val ribbon = Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                        lineTo(size.width / 2f, size.height * 0.8f); lineTo(0f, size.height); close()
                    }
                    val fill = Brush.horizontalGradient(listOf(Color(0xFFB3533A), Color(0xFFD07257), Color(0xFFB3533A)))
                    onDrawBehind { drawPath(ribbon, fill) }
                })
            }
            CoverDesign.STITCH -> {
                Box(Modifier.fillMaxSize().padding(start = 19.dp, top = 9.dp, end = 9.dp, bottom = 9.dp).drawWithCache {
                    val sw = 1.5.dp.toPx()
                    val outline = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 9.dp, bottomEnd = 9.dp).createOutline(size, layoutDirection, this)
                    val dashed = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * sw, 3 * sw)))
                    onDrawBehind { drawOutline(outline, STITCH_INK, style = dashed) }
                })
                // .deboss: the first word in small spaced capitals over the rest, large; a one-word title is the large line alone.
                val t = remember(title) { coverTitle(title) }
                Column(Modifier.padding(start = 19.dp, end = 9.dp).dropBy(0.41f).fillMaxWidth()) {
                    if (t.rest != null) Text(t.head.uppercase(), style = DebossTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    BasicText(
                        t.rest ?: t.head, style = DebossBig, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        autoSize = DebossAutoSize,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            CoverDesign.STRAP -> {
                Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).width(10.dp).fillMaxHeight().drawWithCache {
                    val strap = Brush.horizontalGradient(0f to Color(0xFF141E2E), 0.45f to Color(0xFF2A3A55), 1f to Color(0xFF141E2E))
                    val edge = 1.dp.toPx()
                    onDrawBehind {
                        drawRect(strap)
                        drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, 0f), Offset(0f, size.height), edge)
                    }
                })
                // .foil: the first word large in gold, the rest in small capitals under it.
                val t = remember(title) { coverTitle(title) }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 40.dp, bottom = 20.dp)) {
                    BasicText(
                        t.head, style = FoilTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        autoSize = FoilAutoSize,
                    )
                    if (t.rest != null) Text(t.rest.uppercase(), style = FoilSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A tag on a cover (.badge): a white pill at the top-left, 24dp, with the tag's dot and its name (bold 12),
 * one small shadow. A colour with no name shows its dot alone.
 */
@Composable
internal fun TagBadge(color: Color, name: String?, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier
            .height(24.dp)
            .shadow(1.dp, CircleShape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .background(ink.badge, CircleShape)
            .padding(start = 8.dp, end = if (name != null) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        if (name != null) Text(name, style = BadgeText, color = ink.badgeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The two grains, made once per process and drawn through a repeating shader (no per-frame work, no blend modes,
 * so API 26 draws them too). The CSS overlays them; here lighter-than-mid pixels are white and darker ones black,
 * each at the overlay's strength, which reads the same on any cloth.
 */
private object CoverTextures {
    private var linenDensity = 0f
    private var linen: ImageBitmap? = null

    /** .linen: a 1dp light thread across and a 1dp dark one down, every 3dp, at the overlay's 20%. */
    private fun linen(density: Float): ImageBitmap {
        linen?.takeIf { linenDensity == density }?.let { return it }
        val n = (3 * density).roundToInt().coerceAtLeast(3)
        val t = density.roundToInt().coerceAtLeast(1)
        val px = IntArray(n * n)
        val light = (0.35f * 0.2f * 255).roundToInt() shl 24 or 0xFFFFFF
        val dark = (0.30f * 0.2f * 255).roundToInt() shl 24
        for (y in 0 until n) for (x in 0 until n) px[y * n + x] = when { x < t -> dark; y < t -> light; else -> 0 }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap().also { linen = it; linenDensity = density }
    }

    /** .leather: three octaves of tileable value noise (the SVG's fractal noise, .55), as an overlay at 42%. Built once per process. */
    private val leather: ImageBitmap by lazy {
        val n = 160
        val grain = leatherGrain(n, 0x1b2L)
        val px = IntArray(n * n)
        for (i in grain.indices) {
            val d = grain[i]
            val alpha = (abs(d) * 0.42f * 0.5f * 255).roundToInt().coerceIn(0, 255)
            px[i] = (alpha shl 24) or (if (d > 0f) 0xFFFFFF else 0)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    fun brush(leather: Boolean, density: Float): ShaderBrush =
        if (leather) TileBrush(this.leather, density) else TileBrush(linen(density), 1f)
}

/**
 * [inner]'s fitted size, remembered by text, room and type scale. StepBased lays the title out once per halving of its
 * range (7 to 9 layouts a title), and a lazy grid composes a tile afresh each time it scrolls back into view; the same
 * inputs give the same size, so a remembered one draws exactly as a searched one, after a single layout. Equal only to
 * itself, so a call site keeps one.
 */
internal class RememberedAutoSize(private val inner: TextAutoSize, capacity: Int = 256) : TextAutoSize {
    private val sizes = FitMemo(capacity)

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        sizes.getOrFit(text.text, constraints, density, fontScale) { inner.fit(this, constraints, text) }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private fun TextAutoSize.fit(scope: TextAutoSizeLayoutScope, constraints: Constraints, text: AnnotatedString): TextUnit =
    with(scope) { getFontSize(constraints, text) }

/** Fitted text sizes by text, room and type scale, at most [capacity] of them, the least lately used going first. Any thread. */
internal class FitMemo(private val capacity: Int) {
    private data class Key(val text: String, val constraints: Constraints, val density: Float, val fontScale: Float)

    private val sizes = object : LinkedHashMap<Key, TextUnit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TextUnit>?): Boolean = size > capacity
    }

    fun getOrFit(text: String, constraints: Constraints, density: Float, fontScale: Float, fit: () -> TextUnit): TextUnit {
        val key = Key(text, constraints, density, fontScale)
        synchronized(sizes) { sizes[key] }?.let { return it }
        val size = fit()
        synchronized(sizes) { sizes[key] = size }
        return size
    }
}

/** A repeating bitmap, scaled from px to dp where the bitmap is drawn at one px per dp. */
private class TileBrush(private val image: ImageBitmap, private val scale: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader = ImageShader(image, TileMode.Repeated, TileMode.Repeated).apply {
        if (scale != 1f) setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale) })
    }
}
