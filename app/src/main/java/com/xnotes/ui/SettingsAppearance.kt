package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.settings.CornerStyle
import com.xnotes.settings.MaterialColourMode
import com.xnotes.settings.MaterialStyle
import com.xnotes.settings.Preferences
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkTokens
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

private val THEMES = listOf("system", "light", "dark", "oled")
private val MODES = listOf(MaterialColourMode.PAPER, MaterialColourMode.SYSTEM, MaterialColourMode.SINGLE, MaterialColourMode.DUAL)
private val STYLES = listOf(
    MaterialStyle.TONAL_SPOT to R.string.material_style_soft,
    MaterialStyle.VIBRANT to R.string.material_style_vivid,
    MaterialStyle.FIDELITY to R.string.material_style_original,
    MaterialStyle.EXPRESSIVE to R.string.material_style_playful,
    MaterialStyle.FRUIT_SALAD to R.string.material_style_fresh,
    MaterialStyle.RAINBOW to R.string.material_style_clean,
    MaterialStyle.NEUTRAL to R.string.material_style_muted,
    MaterialStyle.MONOCHROME to R.string.material_style_grayscale,
)

/** System's picture: the light half, with the dark one over its right side on a slant (.st-msys). */
private val SystemSplit = GenericShape { size, _ ->
    moveTo(size.width * 0.62f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width * 0.38f, size.height)
    close()
}

/**
 * Appearance (r2_settings Frame 2): Theme and Corners as live pictures, Colour mode as four cards
 * with strips, then, for Single and Dual tone, their colours, Style and Contrast.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSettings(m: SettingsModel) {
    val ink = LocalInk.current
    val prefs = m.prefs
    val editor = m.editor
    val k = prefs.cornerStyle.scale
    // Every picture repaints when one of these changes; each is a real palette for that choice.
    val looks = remember(prefs.materialMode, prefs.materialSingleSeed, prefs.materialDualSeed, prefs.materialSurfaceSeed, prefs.materialStyle, prefs.materialContrast) {
        THEMES.associateWith { t ->
            if (t == "system") null else editor.previewPalette(prefs.copy(uiAppearance = t)).ink
        }
    }
    SettingsSection(stringResource(R.string.settings_sec_look)) {
        SettingsSideRow(SettingId.THEME) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                THEMES.forEach { t ->
                    PictureCard(themeLabel(t), prefs.uiAppearance == t, Modifier.weight(1f), onClick = { if (prefs.uiAppearance != t) m.update(prefs.copy(uiAppearance = t)) }) {
                        Box(Modifier.fillMaxWidth().height(60.dp)) {
                            if (t == "system") {
                                MiniLibrary(looks.getValue("light")!!, k, Modifier.fillMaxSize())
                                MiniLibrary(looks.getValue("dark")!!, k, Modifier.fillMaxSize().clip(SystemSplit))
                            } else {
                                MiniLibrary(looks.getValue(t)!!, k, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        SettingsSideRow(SettingId.CORNERS) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CornerStyle.entries.forEach { c ->
                    PictureCard(cornerLabel(c), prefs.cornerStyle == c, Modifier.weight(1f), onClick = { m.updateHome(prefs.copy(cornerStyle = c)) }) {
                        MiniCorners(c.scale, Modifier.fillMaxWidth().height(60.dp))
                    }
                }
            }
        }
    }
    val custom = prefs.materialMode == MaterialColourMode.SINGLE || prefs.materialMode == MaterialColourMode.DUAL
    SettingsSection(stringResource(R.string.settings_sec_colours)) {
        SettingRow(
            SettingId.COLOUR_MODE,
            below = {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MODES.forEach { mode ->
                        PictureCard(modeLabel(mode), prefs.materialMode == mode, Modifier.weight(1f), sub = modeSub(mode), onClick = { if (mode != prefs.materialMode) m.update(prefs.copy(materialMode = mode)) }) {
                            ColourStrip(mode, prefs)
                        }
                    }
                }
                val hint = when (prefs.materialMode) {
                    MaterialColourMode.PAPER -> stringResource(R.string.appearance_paper_hint)
                    // Wallpaper colours need Android 12; older devices are told what they get instead.
                    MaterialColourMode.SYSTEM -> stringResource(if (android.os.Build.VERSION.SDK_INT < 31) R.string.material_system_fallback else R.string.appearance_system_hint)
                    else -> null
                }
                if (hint != null) Text(hint, style = InkType.hint, color = ink.text2, modifier = Modifier.padding(top = 12.dp))
            },
        )
        if (custom) {
            SettingRowBase(
                title = stringResource(R.string.appearance_colour),
                description = null,
                below = { key(prefs.materialMode) { MaterialColourPicker(prefs, m::update) } },
            ) { Text(stringResource(materialPresetName(prefs)), style = InkType.meta.copy(fontWeight = FontWeight.SemiBold), color = ink.text2) }
            SettingRow(
                SettingId.COLOUR_STYLE,
                below = {
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        STYLES.forEach { (style, label) -> InkChip(stringResource(label), prefs.materialStyle == style, { m.update(prefs.copy(materialStyle = style)) }) }
                    }
                    Text(stringResource(styleDescription(prefs.materialStyle)), style = InkType.hint, color = ink.text2, modifier = Modifier.padding(top = 10.dp))
                },
            )
            val percent = (prefs.materialContrast * 100).roundToInt()
            SliderRow(
                SettingId.CONTRAST,
                if (percent > 0) "+$percent%" else if (percent < 0) "−${-percent}%" else "0%",
                prefs.materialContrast.toFloat(),
                -1f..1f,
                ticks = listOf(stringResource(R.string.appearance_softer), stringResource(R.string.appearance_standard), stringResource(R.string.appearance_crisper)),
            ) { v ->
                val c = Math.round(v * 10) / 10.0
                if (c != m.prefs.materialContrast) m.update(m.prefs.copy(materialContrast = c))
            }
        }
    }
}

/**
 * A choice drawn as a picture (.st-opt): the picture, its name, and an optional line under it; chosen
 * = 2dp near-black ring and extra-bold. Shrinks to .97 under the finger.
 */
@Composable
private fun PictureCard(label: String, selected: Boolean, modifier: Modifier, sub: String? = null, onClick: () -> Unit, picture: @Composable () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .pressScale(src, 0.97f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ink.solid else ink.line, shape)
            .selectable(selected, src, LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        picture()
        Text(label, style = InkType.meta.copy(lineHeight = 17.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold), color = ink.text, textAlign = TextAlign.Center, maxLines = 1)
        if (sub != null) Text(sub, style = InkType.tiny.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium), color = ink.text2, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 0.dp))
    }
}

/**
 * The library in miniature (.st-mini), in [t]'s colours: the sidebar with its selected pill, the
 * title, the near-black New button, three covers; corners scaled by [k] (the Corners setting).
 */
@Composable
private fun MiniLibrary(t: InkTokens, k: Float, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape((9 * k).dp))) {
        val u = size.height / 60f
        drawRect(t.bg)
        val sb = size.width * 0.31f
        drawRect(t.sidebar, size = Size(sb, size.height))
        drawRect(t.line2, Offset(sb - 1f, 0f), Size(1f, size.height))
        val bar = t.text.copy(alpha = 0.26f)
        val x0 = 4 * u
        var y = 7 * u
        fun line(w: Float) { drawRoundRect(bar, Offset(x0, y), Size((sb - 2 * x0) * w, 4 * u), CornerRadius(2 * u)); y += 9 * u }
        line(0.8f)
        drawRoundRect(t.sel, Offset(x0 - u, y - u), Size(sb - 2 * x0 + 2 * u, 9 * u), CornerRadius(3 * u * k))
        drawRoundRect(t.text.copy(alpha = 0.75f), Offset(x0 + 2 * u, y + 2 * u), Size((sb - 2 * x0) * 0.55f, 3 * u), CornerRadius(1.5f * u))
        y += 12 * u
        line(0.62f)
        line(0.72f)
        val mx = sb + 6 * u
        drawRoundRect(t.text.copy(alpha = 0.75f), Offset(mx, 7 * u), Size((size.width - sb) * 0.4f, 5 * u), CornerRadius(2 * u))
        val nw = 18 * u
        drawRoundRect(
            t.solid,
            Offset(size.width - 5 * u - nw, 5 * u), Size(nw, 8 * u), CornerRadius(3.5f * u * k),
        )
        val top = 18 * u
        val coverW = (size.width - mx - 6 * u - 2 * 5 * u) / 3
        listOf(Color(0xFF7FA37A), Color(0xFFFFFDF7), Color(0xFF2E3F5C)).forEachIndexed { i, c ->
            val cx = mx + i * (coverW + 5 * u)
            drawRoundRect(c, Offset(cx, top), Size(coverW, size.height - top - 6 * u), CornerRadius(4 * u * k))
            drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(cx, top), Size(coverW, size.height - top - 6 * u), CornerRadius(4 * u * k), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }
}

/** A card in miniature (.st-cnp): two lines, an outlined chip and a near-black button, its corners at scale [k]. */
@Composable
private fun MiniCorners(k: Float, modifier: Modifier) {
    val ink = LocalInk.current
    Box(modifier.clip(inkRounded(10.dp)).background(ink.surface), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth(0.72f).height(38.dp)) {
            val r = 14.dp.toPx() * k
            drawRoundRect(ink.raised, cornerRadius = CornerRadius(r))
            drawRoundRect(ink.line2, cornerRadius = CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val p = 9.dp.toPx()
            drawRoundRect(ink.text.copy(alpha = 0.26f), Offset(p, 7.dp.toPx()), Size(size.width * 0.55f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(ink.text.copy(alpha = 0.26f), Offset(p, 14.dp.toPx()), Size(size.width * 0.4f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            val h = 11.dp.toPx()
            val cr = CornerRadius(7.dp.toPx() * k)
            drawRoundRect(ink.line3, Offset(p, size.height - 7.dp.toPx() - h), Size(18.dp.toPx(), h), cr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawRoundRect(ink.solid, Offset(size.width - 7.dp.toPx() - 28.dp.toPx(), size.height - 7.dp.toPx() - h), Size(28.dp.toPx(), h), cr)
        }
    }
}

/** The colour mode's strip (.st-cmp): three colours, or the fixed Paper and System strips. */
@Composable
private fun ColourStrip(mode: MaterialColourMode, prefs: Preferences) {
    val ink = LocalInk.current
    val shape = inkRounded(9.dp)
    val colours = AppearanceSwatch.strip(mode, prefs.materialSingleSeed, prefs.materialDualSeed, prefs.materialSurfaceSeed)
    Row(Modifier.fillMaxWidth().height(30.dp).clip(shape).background(ink.line2).border(1.dp, ink.line2, shape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            colours != null -> colours.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it.toComposeColor())) }
            mode == MaterialColourMode.SYSTEM -> Box(Modifier.weight(1f).fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFC9DCD4), Color(0xFF5F9C8F), Color(0xFF2F5A52)))))
            else -> {
                Box(Modifier.weight(1f).fillMaxSize().background(Color.White))
                Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFF3F0EA)))
                Box(Modifier.weight(1f).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFFB43C), Color(0xFFE8821C)))))
            }
        }
    }
}

@Composable
private fun themeLabel(t: String): String = stringResource(
    when (t) { "system" -> R.string.theme_system; "light" -> R.string.theme_light; "dark" -> R.string.theme_dark; else -> R.string.theme_oled },
)

@Composable
private fun cornerLabel(c: CornerStyle): String = stringResource(
    when (c) { CornerStyle.SHARP -> R.string.corners_sharp; CornerStyle.ROUNDED -> R.string.corners_rounded; CornerStyle.SOFT -> R.string.corners_soft },
)

@Composable
private fun modeLabel(m: MaterialColourMode): String = stringResource(
    when (m) {
        MaterialColourMode.PAPER -> R.string.material_paper
        MaterialColourMode.SYSTEM -> R.string.material_system_colours
        MaterialColourMode.SINGLE -> R.string.material_single_tone
        MaterialColourMode.DUAL -> R.string.material_dual_tone
    },
)

@Composable
private fun modeSub(m: MaterialColourMode): String = stringResource(
    when (m) {
        MaterialColourMode.PAPER -> R.string.appearance_paper_sub
        MaterialColourMode.SYSTEM -> R.string.appearance_system_sub
        MaterialColourMode.SINGLE -> R.string.appearance_single_sub
        MaterialColourMode.DUAL -> R.string.appearance_dual_sub
    },
)

private fun styleDescription(s: MaterialStyle): Int = when (s) {
    MaterialStyle.TONAL_SPOT -> R.string.material_style_soft_description
    MaterialStyle.VIBRANT -> R.string.material_style_vivid_description
    MaterialStyle.FIDELITY -> R.string.material_style_original_description
    MaterialStyle.EXPRESSIVE -> R.string.material_style_playful_description
    MaterialStyle.FRUIT_SALAD -> R.string.material_style_fresh_description
    MaterialStyle.RAINBOW -> R.string.material_style_clean_description
    MaterialStyle.NEUTRAL -> R.string.material_style_muted_description
    MaterialStyle.MONOCHROME -> R.string.material_style_grayscale_description
}
