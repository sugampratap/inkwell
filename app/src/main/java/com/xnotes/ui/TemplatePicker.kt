package com.xnotes.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.core.template.Template
import com.xnotes.core.template.TemplatePage
import com.xnotes.core.template.TemplatePainter
import com.xnotes.core.template.TemplateValues
import com.xnotes.platform.AndroidRenderer
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Bumped when the template library changes, so open pickers list it again. */
internal object TemplateLibraryUi {
    var version by mutableIntStateOf(0)
}

/** Page setup's tile width, so the thumbnails PrewarmTemplateThumbs draws are the ones its tiles ask for. */
private val THUMB_W = SETUP_TILE_W

@Composable
private fun thumbPx(width: Dp): Int = with(LocalDensity.current) { width.toPx() }.toInt().coerceAtLeast(8)

/** Template tile thumbnails drawn ahead by [prewarm], so Page setup opens with them in place. */
internal object TemplateThumbs {

    data class Spec(
        val key: String,
        val pageMm: Pair<Double, Double>,
        val ink: Rgba,
        val accent: Rgba,
        val paper: Rgba,
        val numbers: Map<String, Double>,
        val colors: Map<String, Rgba>,
        val wPx: Int,
    )

    private const val CAPACITY = 96

    private val cache = object : LinkedHashMap<Spec, ImageBitmap>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Spec, ImageBitmap>) = size > CAPACITY
    }

    /** Thumbnails show the ruling plainly, whatever opacity the page itself uses. */
    fun spec(key: String, pageMm: Pair<Double, Double>, ink: Rgba, accent: Rgba, paper: Rgba, values: TemplateValues, wPx: Int) =
        Spec(key, pageMm, ink.copy(a = max(ink.a, 170)), accent.copy(a = max(accent.a, 190)), paper, values.numbers, values.colors, wPx)

    fun cached(s: Spec): ImageBitmap? = synchronized(cache) { cache[s] }

    fun render(t: Template, s: Spec, keep: Boolean): ImageBitmap? {
        if (keep) cached(s)?.let { return it }
        val (w, h) = s.pageMm
        val hPx = (s.wPx * h / w).toInt().coerceAtLeast(8)
        val img = runCatching {
            val bmp = Bitmap.createBitmap(s.wPx, hPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(s.paper.toArgb())
            val values = TemplateValues(s.numbers, s.colors)
            val out = TemplateLibrary.layouts.layout(t, TemplatePage(w, h, Rect(0.0, 0.0, w, h), s.ink, s.accent, values))
            TemplatePainter.paint(AndroidRenderer(c), out, s.wPx / w, Rect(0.0, 0.0, s.wPx.toDouble(), hPx.toDouble()))
            bmp.asImageBitmap()
        }.getOrNull() ?: return null
        if (keep) synchronized(cache) { cache[s] = img }
        return img
    }

    /** Draw the template tiles' thumbnails for each of [looks] that are not kept yet, one at a time. */
    suspend fun prewarm(entries: List<TemplateLibrary.Entry>, pageMm: Pair<Double, Double>, looks: List<TemplateLook>, wPx: Int) =
        withContext(Dispatchers.Default) {
            for (look in looks.distinct()) for (e in entries) {
                ensureActive()
                render(e.template, spec(e.key, pageMm, look.ink, look.accent, look.paper, TemplateValues.DEFAULTS, wPx), keep = true)
            }
        }
}

/** The effective colours a style level draws its template with. */
internal data class TemplateLook(val ink: Rgba, val accent: Rgba, val paper: Rgba) {
    companion object {
        /** [style]'s colours over [below] (the document's style, under a page's own). */
        fun of(style: PageStyle, below: PageStyle?, defaultPaper: Rgba) = TemplateLook(
            ink = style.patternColor ?: below?.patternColor ?: PageStyle.DEFAULT_PATTERN_COLOR,
            accent = style.accentColor ?: below?.accentColor ?: PageStyle.DEFAULT_ACCENT_COLOR,
            paper = style.pageColor ?: below?.pageColor ?: defaultPaper,
        )
    }
}

/** Keeps Page setup's thumbnails drawn for the open note, again after each close. */
@Composable
internal fun PrewarmTemplateThumbs(editor: Editor, popupOpen: Boolean) {
    val defaultPaper = LocalPalette.current.paper
    val wPx = thumbPx(THUMB_W)
    val version = TemplateLibraryUi.version
    LaunchedEffect(popupOpen, editor.noteOpen, editor.title, editor.pageIndex, version, defaultPaper, wPx) {
        if (popupOpen || !editor.noteOpen) return@LaunchedEffect
        delay(PREWARM_DELAY_MS)
        val doc = editor.documentStyle
        val looks = listOf(TemplateLook.of(doc, null, defaultPaper), TemplateLook.of(editor.currentPageStyle, doc, defaultPaper))
        TemplateThumbs.prewarm(editor.templateChoices(), editor.currentPageMm, looks, wPx)
    }
}

/** Lets a just-opened note draw its first pages before thumbnails take the CPU. */
private const val PREWARM_DELAY_MS = 600L
