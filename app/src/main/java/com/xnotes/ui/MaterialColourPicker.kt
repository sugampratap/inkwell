package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.settings.MaterialColourMode
import com.xnotes.settings.MaterialStyle
import com.xnotes.settings.Preferences
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

private data class ColourPreset(@param:StringRes val name: Int, val accent: Rgba, val surface: Rgba? = null)
private fun rgb(value: Int) = Rgba.fromArgb(value or (0xff shl 24))

private val singleTonePresets = listOf(
    ColourPreset(R.string.hue_red, Preferences.DEFAULT_MATERIAL_SINGLE),
    ColourPreset(R.string.hue_pink, rgb(0xe91e63)),
    ColourPreset(R.string.hue_purple, rgb(0x9c27b0)),
    ColourPreset(R.string.material_indigo, rgb(0x3f51b5)),
    ColourPreset(R.string.hue_blue, rgb(0x2196f3)),
    ColourPreset(R.string.hue_teal, rgb(0x009688)),
    ColourPreset(R.string.hue_green, rgb(0x4caf50)),
    ColourPreset(R.string.material_amber, rgb(0xffc107)),
)

// Base2Tone D3 accents and B3 surface seeds; attribution and revision in assets/licenses/base2tone.txt.
private val dualTonePresets = listOf(
    ColourPreset(R.string.material_morning, Preferences.DEFAULT_MATERIAL_DUAL, Preferences.DEFAULT_MATERIAL_SURFACE),
    ColourPreset(R.string.material_evening, rgb(0xffa142), rgb(0x9a86fd)),
    ColourPreset(R.string.material_sea, rgb(0x0db57d), rgb(0x57718e)),
    ColourPreset(R.string.material_forest, rgb(0xb1c44f), rgb(0x687d68)),
    ColourPreset(R.string.material_earth, rgb(0xcda956), rgb(0x88786d)),
    ColourPreset(R.string.material_lavender, rgb(0xca80ff), rgb(0xa286fd)),
    ColourPreset(R.string.material_lake, rgb(0xc4b031), rgb(0x499fbc)),
    ColourPreset(R.string.material_desert, rgb(0xe58748), rgb(0x957e50)),
)

/** The colour mode cards' strips, out of Compose. */
internal object AppearanceSwatch {
    private val WHITE = Rgba(255, 255, 255)
    private val BLACK = Rgba(0, 0, 0)

    /** [a] moved [k] of the way to [b] (CSS color-mix). */
    fun mix(a: Rgba, b: Rgba, k: Double): Rgba {
        fun ch(x: Int, y: Int) = (x * (1 - k) + y * k).roundToInt().coerceIn(0, 255)
        return Rgba(ch(a.r, b.r), ch(a.g, b.g), ch(a.b, b.b))
    }

    /** Single: a tint, the colour, a shade. Dual: the accent, the surface, a pale surface. Paper and System draw fixed strips (null). */
    fun strip(mode: MaterialColourMode, single: Rgba, dualAccent: Rgba, dualSurface: Rgba): List<Rgba>? = when (mode) {
        MaterialColourMode.SINGLE -> listOf(mix(single, WHITE, 0.78), single, mix(single, BLACK, 0.35))
        MaterialColourMode.DUAL -> listOf(dualAccent, dualSurface, mix(dualSurface, WHITE, 0.78))
        else -> null
    }
}

/** The preset the colours match, for the "Colour" line's value: its name, or Custom. */
@StringRes
internal fun materialPresetName(prefs: Preferences): Int {
    val dual = prefs.materialMode == MaterialColourMode.DUAL
    val accent = if (dual) prefs.materialDualSeed else prefs.materialSingleSeed
    val presets = if (dual) dualTonePresets else singleTonePresets
    return presets.firstOrNull { it.accent == accent && (!dual || it.surface == prefs.materialSurfaceSeed) }?.name ?: R.string.material_custom
}

/**
 * The colours of Single and Dual tone (r2_settings Frame 2): Dual's eight two-colour preset cards, or
 * Single's eight swatches; then the Accent colour field (and, for Dual, the Surface colour field),
 * each opening the shared picker. Presets dim under Grayscale; Surface dims under Clean and
 * Grayscale, with the untinted note.
 */
@Composable
internal fun MaterialColourPicker(prefs: Preferences, update: (Preferences) -> Unit) {
    val ink = LocalInk.current
    val dual = prefs.materialMode == MaterialColourMode.DUAL
    val accent = if (dual) prefs.materialDualSeed else prefs.materialSingleSeed
    val coloured = prefs.materialStyle != MaterialStyle.MONOCHROME
    val tinted = coloured && prefs.materialStyle != MaterialStyle.RAINBOW
    val presets = if (dual) dualTonePresets else singleTonePresets
    val selected = presets.firstOrNull { it.accent == accent && (!dual || it.surface == prefs.materialSurfaceSeed) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (dual) {
            BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (coloured) 1f else 0.4f)) {
                val perRow = if (maxWidth >= 520.dp) 8 else 4
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.chunked(perRow).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                DualPresetCard(p, p == selected, coloured, Modifier.weight(1f)) {
                                    update(prefs.copy(materialDualSeed = p.accent, materialSurfaceSeed = requireNotNull(p.surface)))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!dual) {
                Row(Modifier.alpha(if (coloured) 1f else 0.4f).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    presets.forEach { p -> InkSwatch(p.accent.toComposeColor(), p == selected, 24.dp) { if (coloured) update(prefs.copy(materialSingleSeed = p.accent)) } }
                }
            }
            ColourField(stringResource(R.string.pref_accent_colour), accent, coloured, Modifier.weight(1f)) {
                update(if (dual) prefs.copy(materialDualSeed = it) else prefs.copy(materialSingleSeed = it))
            }
            if (dual) ColourField(stringResource(R.string.material_surface_colour), prefs.materialSurfaceSeed, tinted, Modifier.weight(1f)) { update(prefs.copy(materialSurfaceSeed = it)) }
        }
        if (dual && !tinted) Text(stringResource(R.string.material_untinted_surfaces), style = InkType.hint, color = ink.text2)
    }
}

/** A Dual tone preset (.st-dp): its accent and surface side by side, its name; chosen = 2dp ring, extra-bold and a tick. */
@Composable
private fun DualPresetCard(p: ColourPreset, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .pressScale(src, 0.97f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ink.solid else ink.line, shape)
            .selectable(selected, src, LocalIndication.current, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp).clip(inkRounded(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(1f).height(22.dp).background(p.accent.toComposeColor()))
            p.surface?.let { Box(Modifier.weight(1f).height(22.dp).background(it.toComposeColor())) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(p.name), style = InkType.small.copy(lineHeight = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold), color = ink.text, maxLines = 1, textAlign = TextAlign.Center)
            if (selected) Icon(Ph.check, null, tint = ink.text, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
    }
}

/** A colour field (.st-cf): its swatch, what it is and its hex, and a palette glyph; opens the shared picker. */
@Composable
private fun ColourField(label: String, colour: Rgba, enabled: Boolean, modifier: Modifier, onPick: (Rgba) -> Unit) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.small
    val src = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressScale(src, 0.98f)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(shape)
                .border(1.dp, ink.line, shape)
                .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button) { open = true }
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(26.dp).clip(inkRounded(8.dp)).background(colour.toComposeColor()).border(1.dp, swatchRing(ink.isDark), inkRounded(8.dp)))
            Column(Modifier.weight(1f)) {
                Text(label, style = InkType.small, color = ink.text2)
                Text(Rgba.toHex(colour).uppercase(), style = InkType.body.copy(fontWeight = FontWeight.Bold).tnum(), color = ink.text)
            }
            Icon(Ph.palette, null, tint = ink.text2, modifier = Modifier.size(18.dp))
        }
        if (open && enabled) ColorPickerPopup(colour, emptyList(), { open = false }, onPick)
    }
}
