package com.xnotes.kit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xnotes.ui.InkSegmented
import com.xnotes.ui.InkSliderRow
import com.xnotes.ui.InkSwatch
import com.xnotes.ui.icons.InkGlyph
import com.xnotes.ui.icons.InkGlyphIcon
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.HeartToggle
import com.xnotes.ui.kit.IconBadge
import com.xnotes.ui.kit.InkBrandButton
import com.xnotes.ui.kit.InkChip
import com.xnotes.ui.kit.InkConfirmSheet
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.InkField
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkGroup
import com.xnotes.ui.kit.InkGroupRow
import com.xnotes.ui.kit.InkIconButton
import com.xnotes.ui.kit.InkMenuDivider
import com.xnotes.ui.kit.InkMenuHeader
import com.xnotes.ui.kit.InkMenuRow
import com.xnotes.ui.kit.InkProgressSheet
import com.xnotes.ui.kit.InkRowValue
import com.xnotes.ui.kit.InkSecondaryButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStepper
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkSwitch
import com.xnotes.ui.kit.InkToast
import com.xnotes.ui.kit.ProgressLine
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.PaperPalette
import com.xnotes.ui.theme.XnotesTheme
import kotlinx.coroutines.delay

/** Every B2 kit component on one page, in the Inkwell look's three appearances. Debug builds only. */
class KitGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KitGallery() }
    }
}

@Composable
private fun KitGallery() {
    var look by remember { mutableStateOf("light") }
    XnotesTheme(PaperPalette.forAppearance(look)) {
        val ink = LocalInk.current
        Column(
            Modifier.fillMaxSize().background(ink.bg).verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("Inkwell kit", style = InkType.display, color = ink.text)
            InkSegmented(listOf("light", "dark", "oled"), look, Modifier.width(300.dp), label = { it.uppercase() }) { look = it }

            Section("Buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkBrandButton("New", {}, icon = Ph.plus)
                    InkIconButton(Ph.magnifyingGlass, "Search", {})
                    InkStrongButton("Save to pen box", {})
                    InkSecondaryButton("Sort & view", {}, icon = Ph.sortAscending)
                    InkSecondaryButton("Empty trash", {}, danger = true)
                    InkGhostButton("Import", {})
                    InkIconButton(Ph.x, "Close", {})
                    InkIconButton(Ph.bookmarkSimpleFill, "Bookmark", {}, on = true)
                }
            }

            Section("Shadows") {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (e in listOf(InkElevation.SOFT, InkElevation.FLOAT, InkElevation.POP)) {
                        Box(
                            Modifier.size(width = 200.dp, height = 120.dp).inkSurface(RoundedCornerShape(16.dp), e),
                            contentAlignment = Alignment.Center,
                        ) { Text(e.name, style = InkType.body, color = ink.text) }
                    }
                }
            }

            Section("Switch, chips, stepper") {
                var a by remember { mutableStateOf(true) }
                var scope by remember { mutableIntStateOf(0) }
                var v by remember { mutableIntStateOf(5) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkSwitch(a, { a = it })
                    InkSwitch(!a, { a = !it })
                    InkChip("This page", scope == 0, { scope = 0 })
                    InkChip("All pages", scope == 1, { scope = 1 })
                    InkStepper("0.$v mm", { v-- }, { v++ }, canMinus = v > 1, canPlus = v < 9)
                }
            }

            Section("Segmented, slider, swatches") {
                var stab by remember { mutableIntStateOf(1) }
                var f by remember { mutableFloatStateOf(0.4f) }
                var pick by remember { mutableIntStateOf(0) }
                InkSegmented(listOf(0, 1, 2, 3), stab, Modifier.width(184.dp), label = { listOf("Off", "Low", "Mid", "High")[it] }) { stab = it }
                Box(Modifier.width(300.dp)) { InkSliderRow("Thickness", "%.1f mm".format(f), f, 0f..1f) { f = it } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0xFF1F2A44, 0xFF2563EB, 0xFFDC2626, 0xFF16A34A, 0xFFD97706).forEachIndexed { i, c ->
                        InkSwatch(Color(c), i == pick, 28.dp) { pick = i }
                    }
                }
            }

            Section("Grouped list, field, menu") {
                var finger by remember { mutableStateOf(false) }
                var q by remember { mutableStateOf("") }
                InkGroup(Modifier.width(560.dp)) {
                    InkGroupRow("Theme", first = true, subtitle = "Light, Dark or OLED", icon = Ph.palette, onClick = {}) { InkRowValue("Light") }
                    InkGroupRow("Draw with finger", subtitle = "Off: a finger pans", icon = Ph.handPointing) { InkSwitch(finger, { finger = it }) }
                }
                InkField(q, { q = it }, Modifier.width(360.dp), placeholder = "Search notes", leadingIcon = Ph.magnifyingGlass)
                Column(Modifier.width(260.dp).inkSurface(RoundedCornerShape(16.dp)).padding(vertical = 8.dp)) {
                    InkMenuHeader("Sort by")
                    InkMenuRow("Modified", {}, checked = true)
                    InkMenuRow("Name", {}, checked = false)
                    InkMenuDivider()
                    InkMenuRow("Move to Trash", {}, icon = Ph.trash, danger = true)
                }
            }

            Section("Feedback") {
                var fav by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF7A9A8C))) {
                        HeartToggle(fav, { fav = !fav }, "Favourite", Modifier.align(Alignment.TopEnd))
                    }
                    ProgressLine(7f / 12f, Modifier.width(200.dp))
                    IconBadge(Ph.microphone)
                    InkToast("3 scanned pages inserted", icon = Ph.checkCircle, actionLabel = "Undo", onAction = {})
                }
                Text("Toast, long", style = InkType.body, color = ink.text2)
                InkToast(
                    "3 pages moved to Trash from Maths notes. Tap Undo to bring them back.",
                    Modifier.width(360.dp),
                    icon = Ph.trash,
                    actionLabel = "Undo",
                    onAction = {},
                )
            }

            Section("Toolbar glyphs: tap to arm") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (g in InkGlyph.entries) {
                        var on by remember { mutableStateOf(false) }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (on) ink.solid else Color.Transparent).clickable { on = !on },
                            contentAlignment = Alignment.Center,
                        ) { InkGlyphIcon(g, on, if (on) ink.onSolid else ink.text, contentDescription = null) }
                    }
                    for (i in listOf(Ph.penNibDuotone, Ph.highlighterDuotone, Ph.eraserDuotone, Ph.shapesDuotone, Ph.textTDuotone, Ph.imageDuotone)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(ink.solid), contentAlignment = Alignment.Center) {
                            Icon(i, null, tint = ink.onSolid, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            Section("Sheet") {
                var open by remember { mutableStateOf(false) }
                InkStrongButton("Open a sheet", { open = true })
                if (open) {
                    InkSheet(
                        "Page setup",
                        { open = false },
                        subtitle = "Apply to: This page",
                        footer = {
                            InkGhostButton("Import", {})
                            Box(Modifier.weight(1f))
                            InkStrongButton("Done", { open = false })
                        },
                    ) {
                        Text("Body content scrolls here.", style = InkType.body, color = ink.text, modifier = Modifier.padding(24.dp))
                    }
                }
            }

            Section("Confirm and progress") {
                var confirm by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkSecondaryButton("Delete permanently?", { confirm = true })
                    InkSecondaryButton("Export progress", { progress = true })
                }
                if (confirm) {
                    InkConfirmSheet(
                        "Delete permanently?", "“Sprint retro – Sept” will be permanently deleted. This can’t be undone.",
                        "Delete permanently", { confirm = false }, { confirm = false }, danger = true, confirmIcon = Ph.trash,
                    )
                }
                if (progress) {
                    var done by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { while (done < 12) { delay(400); done++ } }
                    InkProgressSheet("Exporting to PDF…", "page $done / 12", done / 12f, onCancel = { progress = false })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = InkType.title, color = LocalInk.current.text)
        content()
    }
}
