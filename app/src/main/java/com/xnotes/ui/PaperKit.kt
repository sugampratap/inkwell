package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageEdge
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.model.Rgba
import com.xnotes.core.template.ParamType
import com.xnotes.core.template.Template
import com.xnotes.core.template.TemplateParam
import com.xnotes.core.template.TemplateValues
import com.xnotes.platform.TemplateLibrary
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkDefaultChip
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkPressOver
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.cornerOf
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A preset colour and the name its caption shows. */
internal class NamedColour(val rgba: Rgba, @param:StringRes val name: Int)

/** White, cream, warm grey, mint, dark and black (the mockup's PAPERS). */
internal val PAPER_PRESETS = listOf(
    NamedColour(Rgba(255, 255, 255), R.string.paper_white), NamedColour(Rgba(0xFB, 0xF6, 0xE9), R.string.paper_cream),
    NamedColour(Rgba(0xF1, 0xEF, 0xE8), R.string.paper_warm_grey), NamedColour(Rgba(0xE8, 0xF0, 0xEA), R.string.paper_mint),
    NamedColour(Rgba(0x2B, 0x2D, 0x31), R.string.paper_dark), NamedColour(Rgba(0x11, 0x11, 0x11), R.string.paper_black),
)

/** Rulings that sit back behind the ink (LINES). */
internal val LINE_PRESETS = listOf(
    NamedColour(Rgba(0xD7, 0xE3, 0xEC), R.string.line_pale_blue), NamedColour(Rgba(0xE3, 0xDE, 0xD4), R.string.paper_warm_grey),
    NamedColour(Rgba(0xF2, 0xC9, 0xC9), R.string.line_rose), NamedColour(Rgba(0xCF, 0xE3, 0xD2), R.string.line_sage),
)

/** Margin-line colours for templates that draw in the accent (ACCS). */
internal val ACCENT_PRESETS = listOf(
    NamedColour(Rgba(0xE8, 0xA0, 0xA0), R.string.accent_red), NamedColour(Rgba(0x9D, 0xB8, 0xD9), R.string.accent_blue),
    NamedColour(Rgba(0xA9, 0xC9, 0xA3), R.string.accent_green), NamedColour(Rgba(0xD9, 0xB5, 0x6A), R.string.accent_ochre),
)

internal val PAPER_SIZE_CHOICES = listOf(PageSize.A4, PageSize.LETTER, PageSize.A5, PageSize.CUSTOM)

/** The template row's fixed picks after Blank: the three built-in rulings, then two bundled examples by id. */
private val QUICK_TEMPLATE_KEYS = listOf("lines", "dots", "grid")
private val QUICK_TEMPLATE_IDS = listOf("xtemplate.examples.cornell", "xtemplate.examples.musical-staves")

/** Template tiles: 76dp in Page setup, 72dp in New notebook (the mockup's --tw). */
internal val SETUP_TILE_W = 76.dp
internal val NOTEBOOK_TILE_W = 72.dp

private val TileName = InkType.small.copy(lineHeight = 15.sp)
private val Caption = InkType.hint.copy(fontWeight = FontWeight.SemiBold)
private val PAGE_SHAPE = RoundedCornerShape(3.dp)
private val THUMB_SHAPE = RoundedCornerShape(4.dp)

/** The template row's picks from [choices], with the shown template moved to the front when it is none of them. */
internal fun paperQuickTemplates(choices: List<TemplateLibrary.Entry>, shownKey: String): List<TemplateLibrary.Entry> {
    val base = QUICK_TEMPLATE_KEYS.mapNotNull { k -> choices.firstOrNull { it.key == k } } +
        QUICK_TEMPLATE_IDS.mapNotNull { id -> choices.firstOrNull { it.template.id == id } }
    val shown = if (shownKey == PageTemplates.NONE) null else choices.firstOrNull { it.key == shownKey }
    return PaperMaths.quickRow(base, shown)
}

/** The template's parameter values on a style level, over [lower]'s; spacing turned to template mm. */
internal fun paperTemplateValues(t: Template?, style: PageStyle, lower: PageStyle?, dpi: Int): TemplateValues {
    if (t == null) return TemplateValues.DEFAULTS
    val numbers = HashMap<String, Double>()
    lower?.params?.let(numbers::putAll)
    style.params?.let(numbers::putAll)
    val sp = t.spacingParam
    (style.spacing ?: lower?.spacing)?.let { px -> if (sp != null) numbers[sp.name] = px * 25.4 / dpi }
    val colors = (lower?.colors ?: emptyMap()) + (style.colors ?: emptyMap())
    return TemplateValues(numbers, colors)
}

/** "A4 portrait", or the sides in mm for a page of no named size. */
@Composable
internal fun paperSizeCaption(mm: Pair<Double, Double>): String {
    val (w, h) = mm
    val short = min(w, h)
    val long = max(w, h)
    val named = PageSize.entries.firstOrNull { it != PageSize.CUSTOM && abs(it.shortMm - short) < 1.0 && abs(it.longMm - long) < 1.0 }
    return if (named != null) {
        stringResource(R.string.page_setup_size_named, pageSizeLabel(named), stringResource(if (w > h) R.string.page_setup_landscape else R.string.page_setup_portrait))
    } else {
        stringResource(R.string.page_setup_size_mm, PaperMaths.formatMm(w), PaperMaths.formatMm(h))
    }
}

/** Where Page setup's dashed margin guide sits; [visible] fades it in and out over 180 ms. */
internal class PaperGuide(val edge: PageEdge, val fraction: Double, val visible: Boolean)

private data class PaperPreviewJob(val t: Template, val spec: TemplateThumbs.Spec)

/**
 * The live page (.pn-page): the paper and its template at [pageMm] (margins included), drawn off the
 * main thread. While a slider drags, renders run one at a time and skip to the newest values
 * (conflate), and the last picture stays up until the next is ready, so the preview never blanks.
 * The ruling's opacity is lifted because sub-pixel lines would otherwise vanish. [guide], when given,
 * is a 1.5dp dashed line on the chosen margin edge, light on dark paper.
 */
@Composable
internal fun PaperLivePage(
    t: Template?,
    key: String,
    pageMm: Pair<Double, Double>,
    look: TemplateLook,
    values: TemplateValues,
    width: Dp,
    height: Dp,
    guide: PaperGuide? = null,
) {
    val ink = LocalInk.current
    val wPx = with(LocalDensity.current) { width.toPx() }.toInt().coerceAtLeast(8)
    val dash = rememberDash(4.dp, 3.dp)
    val job = t?.let {
        PaperPreviewJob(it, TemplateThumbs.Spec(key, pageMm, PaperMaths.lifted(look.ink), PaperMaths.lifted(look.accent), look.paper, values.numbers, values.colors, wPx))
    }
    val latest by rememberUpdatedState(job)
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect { j ->
            shown = if (j == null) null else withContext(Dispatchers.Default) { TemplateThumbs.render(j.t, j.spec, keep = false) } ?: shown
        }
    }
    val guideAlpha = remember { Animatable(0f) }
    LaunchedEffect(guide?.visible) { guideAlpha.animateTo(if (guide?.visible == true) 1f else 0f, InkMotion.fade()) }
    val light = PaperMaths.guideIsLight(look.paper)
    Box(
        Modifier
            .size(width, height)
            // One soft shadow in light themes; dark themes keep only the faint ring (.pn-page).
            .then(if (!ink.isDark) Modifier.shadow(12.dp, PAGE_SHAPE, ambientColor = ink.shadow, spotColor = ink.shadow) else Modifier)
            .clip(PAGE_SHAPE)
            .background(look.paper.toComposeColor())
            .border(1.dp, if (ink.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f), PAGE_SHAPE)
            .drawWithContent {
                drawContent()
                val g = guide ?: return@drawWithContent
                val a = guideAlpha.value
                if (a <= 0f) return@drawWithContent
                val base = if (light) Color.White.copy(alpha = 0.75f) else Color(0xFF222222).copy(alpha = 0.62f)
                val c = base.copy(alpha = base.alpha * a)
                val w = 1.5.dp.toPx()
                if (PaperMaths.isVertical(g.edge)) {
                    val x = (size.width * g.fraction).toFloat()
                    drawLine(c, Offset(x, 0f), Offset(x, size.height), w, pathEffect = dash)
                } else {
                    val y = (size.height * g.fraction).toFloat()
                    drawLine(c, Offset(0f, y), Offset(size.width, y), w, pathEffect = dash)
                }
            },
    ) {
        val bmp = shown
        if (t != null && bmp != null) Image(bmp, contentDescription = t.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}

/** The caption under a preview (.pn-pcap): a small ink dot ("live") and the page it shows. */
@Composable
internal fun PaperPreviewCaption(text: String, modifier: Modifier = Modifier, live: Boolean = true) {
    val ink = LocalInk.current
    Row(modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) Box(Modifier.size(6.dp).background(ink.text, CircleShape))
        Text(text, style = Caption, color = ink.text2, textAlign = TextAlign.Center)
    }
}

/** A tile thumbnail: from the shared cache when drawn already, else drawn off the main thread and kept. */
@Composable
internal fun rememberPaperThumb(t: Template, spec: TemplateThumbs.Spec): ImageBitmap? =
    produceState(TemplateThumbs.cached(spec), spec) {
        value = TemplateThumbs.cached(spec) ?: withContext(Dispatchers.Default) { TemplateThumbs.render(t, spec, keep = true) }
    }.value

/** What a long press on a tile offers: an imported template can be removed, a note-carried one kept. */
internal sealed interface TileMenu {
    class Remove(val onRemove: () -> Unit) : TileMenu
    class Keep(val onKeep: () -> Unit) : TileMenu
}

/**
 * A template choice (.pn-t): its thumbnail at the page's proportions (bare paper for Blank, when
 * [entry] is null) over its name, two lines at most. Chosen: a 2dp near-black ring outside a 2dp gap,
 * and the name extra-bold. The whole tile shrinks to .96 under the finger and the press tint is drawn
 * over the picture. A template the note carries wears "IN NOTE". [menu] adds a long-press menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PaperTemplateTile(
    entry: TemplateLibrary.Entry?,
    selected: Boolean,
    tileWidth: Dp,
    pageMm: Pair<Double, Double>,
    look: TemplateLook,
    onSelect: () -> Unit,
    menu: TileMenu? = null,
) {
    val ink = LocalInk.current
    val thumbH = tileWidth * (pageMm.second / pageMm.first).coerceIn(0.5, 2.0).toFloat()
    val wPx = with(LocalDensity.current) { tileWidth.toPx() }.toInt().coerceAtLeast(8)
    val src = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    val name = entry?.template?.name ?: stringResource(R.string.page_setup_blank)
    Column(Modifier.width(tileWidth).pressScale(src, 0.96f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(tileWidth, thumbH)
                .drawBehind {
                    if (selected) {
                        val g = 2.dp.toPx()
                        val r = 4.dp.toPx()
                        drawRoundRect(ink.solid, Offset(-2 * g, -2 * g), Size(size.width + 4 * g, size.height + 4 * g), CornerRadius(r + 2 * g))
                        drawRoundRect(ink.raised, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(r + g))
                    }
                }
                .clip(THUMB_SHAPE)
                .background(look.paper.toComposeColor())
                .combinedClickable(
                    interactionSource = src,
                    indication = InkPressOver(4.dp),
                    role = Role.RadioButton,
                    onLongClick = menu?.let { { open = true } },
                    onClick = onSelect,
                )
                .semantics {
                    this.selected = selected
                    contentDescription = name
                },
        ) {
            if (entry != null) {
                val spec = TemplateThumbs.spec(entry.key, pageMm, look.ink, look.accent, look.paper, TemplateValues.DEFAULTS, wPx)
                val bmp = rememberPaperThumb(entry.template, spec)
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            if (entry?.source == TemplateLibrary.Source.NOTE) InNoteBadge(Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (!selected) Box(Modifier.matchParentSize().border(1.dp, ink.line, THUMB_SHAPE))
        }
        Text(
            name,
            style = TileName.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = if (selected) ink.text else ink.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(tileWidth + 4.dp).heightIn(min = 30.dp),
        )
        if (menu != null) TileMenuPopup(name, menu, open) { open = false }
    }
}

/** "IN NOTE" (.pn-src): on the paper, so its colours are literal. */
@Composable
private fun InNoteBadge(modifier: Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier.height(16.dp).clip(shape).background(Color.White.copy(alpha = 0.94f)).border(1.dp, Color.Black.copy(alpha = 0.08f), shape).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.page_setup_in_note),
            style = TextStyle(fontFamily = InkType.Jakarta, fontSize = 9.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp),
            color = Color(0xFF3A3A3A),
        )
    }
}

/** The long-press menu on your own templates (.pn-lp): the name, then one two-line row. */
@Composable
private fun TileMenuPopup(name: String, menu: TileMenu, expanded: Boolean, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.width(264.dp)) {
        Text(name, style = InkType.small.copy(fontWeight = FontWeight.Bold), color = ink.text2, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        when (menu) {
            is TileMenu.Remove -> TwoLineMenuRow(Ph.trash, stringResource(R.string.remove_template_b2), stringResource(R.string.remove_template_hint), danger = true) { onDismiss(); menu.onRemove() }
            is TileMenu.Keep -> TwoLineMenuRow(Ph.plusCircle, stringResource(R.string.keep_template_b2), stringResource(R.string.keep_template_hint)) { onDismiss(); menu.onKeep() }
        }
    }
}

@Composable
private fun TwoLineMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, danger: Boolean = false, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The icon stays in ink even on the red row (.pn-lp .btn-danger i).
        Icon(icon, null, tint = ink.text, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = InkType.row, color = if (danger) ink.danger else ink.text)
            Text(hint, style = InkType.caption, color = ink.text2)
        }
    }
}

/** The browser's dashed Import tile (.pn-imp), the same language as the library's New note tile. */
@Composable
internal fun PaperImportTile(tileWidth: Dp, pageMm: Pair<Double, Double>, onImport: () -> Unit) {
    val ink = LocalInk.current
    val thumbH = tileWidth * (pageMm.second / pageMm.first).coerceIn(0.5, 2.0).toFloat()
    val src = remember { MutableInteractionSource() }
    val dash = rememberDash(5.dp, 4.dp)
    val corner = cornerOf(6.dp)
    Column(Modifier.width(tileWidth).pressScale(src, 0.96f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(tileWidth, thumbH)
                .clip(inkRounded(6.dp))
                .clickable(src, LocalIndication.current, role = Role.Button, onClick = onImport)
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        ink.line3, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(corner.toPx()),
                        style = Stroke(w, pathEffect = dash),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).background(ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ph.plus, null, tint = ink.text, modifier = Modifier.size(18.dp))
            }
        }
        Text(stringResource(R.string.page_setup_import_tile), style = TileName, color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 30.dp))
    }
}

/** "**Name.** what it is" under the template row (.pn-tdesc). */
@Composable
internal fun PaperTemplateDescription(t: Template?, key: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val name = t?.name ?: stringResource(R.string.page_setup_blank)
    val desc = when {
        key == PageTemplates.NONE || t == null -> stringResource(R.string.page_setup_blank_desc)
        else -> t.description ?: stringResource(R.string.page_setup_builtin_desc)
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink.text)) { append("$name.") }
            append(" ")
            append(desc)
        },
        style = InkType.meta.copy(lineHeight = 19.sp),
        color = ink.text2,
        modifier = modifier.padding(top = 12.dp),
    )
}

/** A section's title row (.pn-sh): the title (with [lead] beside it), and an action at the end. */
@Composable
internal fun PaperSectionHeader(title: String, modifier: Modifier = Modifier, lead: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val ink = LocalInk.current
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = InkType.label, color = ink.text)
            lead?.invoke(this)
        }
        trailing?.invoke()
    }
}

/** The browsing footer's hint (.pn-fhint): a pointing hand and one line. */
@Composable
internal fun BrowseHint(text: String) {
    val ink = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Ph.handPointing, null, tint = ink.text, modifier = Modifier.size(16.dp))
        Text(text, style = InkType.caption, color = ink.text2, maxLines = 1)
    }
}

/** "Browse all 26 ›" (.pn-link). */
@Composable
internal fun PaperLink(label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.height(32.dp).clip(inkRounded(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = InkType.label, color = ink.text)
        Icon(Ph.caretRight, null, tint = ink.text, modifier = Modifier.size(16.dp))
    }
}

/** A cell's caption (.pn-cap): what it sets, bold, and what it is set to, quiet. */
@Composable
internal fun PaperCaption(label: String, value: String?, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(modifier.fillMaxWidth().heightIn(min = 18.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = InkType.label, color = ink.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (value != null) Text(value, style = InkType.meta.tnum(), color = ink.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * A colour cell (.pn-cell + .swrow): the unset choice first (in [unset], the colour it falls back
 * to, and captioned [unsetLabel] while chosen), a hairline, the [presets], then the dashed "+" that
 * opens the shared picker. [onPick] gets null for the unset choice. Presets match on hue alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaperColourCell(label: String, own: Rgba?, unset: Rgba, unsetLabel: String, presets: List<NamedColour>, onPick: (Rgba?) -> Unit) {
    val ink = LocalInk.current
    val match = own?.let { o -> presets.firstOrNull { PaperMaths.sameRgb(it.rgba, o) } }
    val value = when {
        own == null -> unsetLabel
        match != null -> stringResource(match.name)
        else -> stringResource(R.string.page_setup_custom)
    }
    Column {
        PaperCaption(label, value)
        // InkSwatch keeps a 4dp halo each side, so 2dp between them reads as the mockup's 10dp gap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            InkSwatch(unset.copy(a = 255).toComposeColor(), own == null, 32.dp) { onPick(null) }
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(ink.line))
            for (c in presets) InkSwatch(c.rgba.toComposeColor(), own != null && PaperMaths.sameRgb(c.rgba, own), 32.dp) { onPick(c.rgba) }
            PaperAddSwatch(own?.copy(a = 255), custom = own != null && match == null) { onPick(it) }
        }
    }
}

/**
 * The custom colour swatch (.pn-add): a dashed 32dp circle with a plus; solid-ringed while its picker
 * is open; filled with the colour and ringed like a chosen swatch once a custom colour is in use. The
 * shared picker stays open across picks, as today.
 */
@Composable
private fun PaperAddSwatch(current: Rgba?, custom: Boolean, onPick: (Rgba) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val filled = custom && current != null
    Box {
        InkAddSwatch(
            colour = current?.takeIf { filled },
            lit = open,
            contentDescription = stringResource(R.string.page_setup_custom_colour),
            chosen = filled,
            size = 32.dp,
            cell = 40.dp,
        ) { open = !open }
        if (open) PageColorGridPopup(current, { open = false }) { onPick(it) }
    }
}

/**
 * A slider cell: caption (with " (default)" while [isDefault] and a Default chip is offered), then the
 * slider, with the Default chip beside it when [onDefault] is given.
 */
@Composable
internal fun PaperSliderCell(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isDefault: Boolean,
    onDefault: (() -> Unit)?,
    onChange: (Float) -> Unit,
) {
    Column {
        PaperCaption(label, if (onDefault != null && isDefault) valueText + stringResource(R.string.page_setup_default_suffix) else valueText)
        if (onDefault != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InkSlider(value, range, Modifier.weight(1f), onChange = onChange)
                InkDefaultChip(stringResource(R.string.page_setup_default), isDefault, onDefault)
            }
        } else {
            InkSlider(value, range, onChange = onChange)
        }
    }
}

/**
 * The template's spacing parameter in mm, named by the template ("Line spacing", "Dot spacing"), over
 * its declared range; snapped as [PaperMaths.snapLength]. The style keeps it in content px at [dpi].
 */
@Composable
internal fun PaperSpacingCell(sp: TemplateParam, ownPx: Double?, inheritedPx: Double?, dpi: Int, onDefault: (() -> Unit)?, onSet: (Double) -> Unit) {
    val k = dpi / 25.4
    val lo = sp.min ?: (sp.default / 4)
    val hi = (sp.max ?: (sp.default * 4)).coerceAtLeast(lo + 0.1)
    val mm = ((ownPx ?: inheritedPx)?.let { it / k } ?: sp.default).coerceIn(lo, hi)
    PaperSliderCell(
        sp.label ?: stringResource(R.string.page_setup_spacing),
        stringResource(R.string.page_setup_mm, PaperMaths.formatMm(mm)),
        mm.toFloat(), lo.toFloat()..hi.toFloat(),
        isDefault = ownPx == null, onDefault = onDefault,
    ) { v ->
        val next = PaperMaths.snapLength(v.toDouble(), sp.step, hi - lo).coerceIn(lo, hi)
        if (abs(next - mm) > 1e-6) onSet(next * k)
    }
}

/** Any other number a template declares: a length in mm, a whole number, or a plain number. */
@Composable
internal fun PaperParamCell(p: TemplateParam, own: Double?, inherited: Double?, onDefault: (() -> Unit)?, onSet: (Double) -> Unit) {
    val value = p.clamp(own ?: inherited ?: p.default)
    val lo = p.min ?: if (p.default > 0) p.default / 4 else p.default - 10
    val hi = (p.max ?: if (p.default > 0) p.default * 4 else p.default + 10).coerceAtLeast(lo + 1e-6)
    val shown = when (p.type) {
        ParamType.LENGTH -> stringResource(R.string.page_setup_mm, PaperMaths.formatMm(value))
        ParamType.INTEGER -> value.roundToInt().toString()
        else -> "%.2f".format(value).trimEnd('0').trimEnd('.', ',')
    }
    PaperSliderCell(
        p.label ?: p.name.replaceFirstChar { it.uppercase() }, shown,
        value.toFloat().coerceIn(lo.toFloat(), hi.toFloat()), lo.toFloat()..hi.toFloat(),
        isDefault = own == null, onDefault = onDefault,
    ) { v ->
        val raw = v.toDouble()
        val next = p.clamp(
            when (p.type) {
                ParamType.LENGTH -> PaperMaths.snapLength(raw, p.step, hi - lo)
                else -> p.step?.let { s -> Math.round(raw / s) * s } ?: raw
            },
        )
        if (abs(next - value) > 1e-9) onSet(next)
    }
}

/** One option in a two-column cell grid, keyed so its own state follows it. */
internal class PaperCell(val key: String, val content: @Composable () -> Unit)

/** The two-column cell grid (.pn-cells): 22dp between rows, 36dp between columns; [divided] adds the hairline and room above. */
@Composable
internal fun PaperCells(cells: List<PaperCell>, modifier: Modifier = Modifier, divided: Boolean = true) {
    val ink = LocalInk.current
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (divided) Modifier.padding(top = 20.dp).drawBehind { drawLine(ink.line2, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 20.dp)
                else Modifier,
            ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        for (pair in cells.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                for (c in pair) key(c.key) { Box(Modifier.weight(1f)) { c.content() } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Size and orientation for the notes made next (.pn-nn): A4 / Letter / A5 / Custom (plus a size only
 * Preferences offers, when it is the one set), and Portrait / Landscape. A custom size is taken as
 * typed, so turning it swaps its sides rather than setting the orientation it ignores.
 */
@Composable
internal fun PaperSizeControls(prefs: Preferences, update: (Preferences) -> Unit, sizeTitle: String, modifier: Modifier = Modifier) {
    val size = prefs.defaultPageSize
    val options = if (size in PAPER_SIZE_CHOICES) PAPER_SIZE_CHOICES else PAPER_SIZE_CHOICES.dropLast(1) + size + PageSize.CUSTOM
    val custom = size == PageSize.CUSTOM
    val orientation = if (custom) {
        if (prefs.customPageWidthMm > prefs.customPageHeightMm) Orientation.LANDSCAPE else Orientation.PORTRAIT
    } else prefs.defaultPageOrientation
    Column(modifier.fillMaxWidth()) {
        PaperCaption(
            sizeTitle,
            if (custom) stringResource(R.string.page_setup_custom_size_value, PaperMaths.formatMm(prefs.customPageWidthMm), PaperMaths.formatMm(prefs.customPageHeightMm))
            else pageSizeLabel(size),
        )
        InkSegmented(options, size, label = { pageSizeLabel(it) }) { update(prefs.copy(defaultPageSize = it)) }
        Spacer(Modifier.height(14.dp))
        val orientationLabel: @Composable (Orientation) -> String = {
            stringResource(if (it == Orientation.LANDSCAPE) R.string.new_notebook_landscape_title else R.string.new_notebook_portrait_title)
        }
        PaperCaption(stringResource(R.string.page_setup_orientation), orientationLabel(orientation))
        InkSegmented(
            listOf(Orientation.PORTRAIT, Orientation.LANDSCAPE),
            orientation,
            label = { orientationLabel(it) },
            icon = { Ph.rectangle },
            iconRotation = { if (it == Orientation.PORTRAIT) 90f else 0f },
        ) { o ->
            if (o == orientation) return@InkSegmented
            update(
                if (custom) prefs.copy(customPageWidthMm = prefs.customPageHeightMm, customPageHeightMm = prefs.customPageWidthMm)
                else prefs.copy(defaultPageOrientation = o),
            )
        }
    }
}

/** An [on]-[off] dash, made once per density rather than in every draw. */
@Composable
private fun rememberDash(on: Dp, off: Dp): PathEffect {
    val d = LocalDensity.current
    return remember(d, on, off) { with(d) { PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())) } }
}
