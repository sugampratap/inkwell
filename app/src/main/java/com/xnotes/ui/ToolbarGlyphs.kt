package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.ui.icons.InkGlyph
import com.xnotes.ui.icons.InkGlyphIcon
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.LocalInk

/**
 * A toolbar item's glyph, shared by the bars, the More menu and the customiser: Phosphor, B2's own lasso, laser and
 * tape, or the colour dots. [Vector.active] is the duotone the glider shows the tool in hand with; [Vector.filled]
 * is the Fill a More row shows when that item is in hand or on (TO 902).
 */
internal sealed interface ItemGlyph {
    class Vector(val regular: ImageVector, val active: ImageVector = regular, val filled: ImageVector = regular) : ItemGlyph
    class Custom(val glyph: InkGlyph) : ItemGlyph
    data object Colours : ItemGlyph
}

internal fun glyphOf(item: ToolbarItem): ItemGlyph = when (item) {
    ToolbarItem.UNDO -> ItemGlyph.Vector(Ph.arrowUUpLeft)
    ToolbarItem.REDO -> ItemGlyph.Vector(Ph.arrowUUpRight)
    ToolbarItem.PEN -> ItemGlyph.Vector(Ph.penNib, Ph.penNibDuotone, Ph.penNibFill)
    ToolbarItem.BALLPOINT -> ItemGlyph.Vector(Ph.pen, Ph.penDuotone, Ph.penFill)
    ToolbarItem.DASHED -> ItemGlyph.Vector(Ph.lineSegments, Ph.lineSegmentsDuotone, Ph.lineSegmentsFill)
    ToolbarItem.CALLIGRAPHY -> ItemGlyph.Vector(Ph.penNibStraight, Ph.penNibStraightDuotone, Ph.penNibStraightFill)
    ToolbarItem.SPEED -> ItemGlyph.Vector(Ph.feather, Ph.featherDuotone, Ph.featherFill)
    ToolbarItem.TAPER -> ItemGlyph.Vector(Ph.paintBrush, Ph.paintBrushDuotone, Ph.paintBrushFill)
    ToolbarItem.PENCIL -> ItemGlyph.Vector(Ph.pencilSimple, Ph.pencilSimpleDuotone, Ph.pencilSimpleFill)
    ToolbarItem.HIGHLIGHTER -> ItemGlyph.Vector(Ph.highlighter, Ph.highlighterDuotone, Ph.highlighterFill)
    ToolbarItem.ERASER -> ItemGlyph.Vector(Ph.eraser, Ph.eraserDuotone, Ph.eraserFill)
    ToolbarItem.LASSO -> ItemGlyph.Custom(InkGlyph.LASSO)
    ToolbarItem.PAN -> ItemGlyph.Vector(Ph.hand, Ph.handDuotone, Ph.handFill)
    ToolbarItem.SELECT -> ItemGlyph.Vector(Ph.selection)
    ToolbarItem.SCREENSHOT -> ItemGlyph.Vector(Ph.scissors)
    ToolbarItem.WAND -> ItemGlyph.Vector(Ph.magicWand)
    ToolbarItem.SHAPE -> ItemGlyph.Vector(Ph.shapes, Ph.shapesDuotone, Ph.shapesFill)
    // The ruler's switch row keeps its Regular icon even when on (TO 903).
    ToolbarItem.RULER -> ItemGlyph.Vector(Ph.ruler)
    ToolbarItem.TEXT -> ItemGlyph.Vector(Ph.textT, Ph.textTDuotone, Ph.textTFill)
    ToolbarItem.TEXT_BOX -> ItemGlyph.Vector(Ph.textbox, Ph.textboxDuotone, Ph.textboxFill)
    ToolbarItem.MARKUP -> ItemGlyph.Vector(Ph.markerCircle, Ph.markerCircleDuotone, Ph.markerCircleFill)
    ToolbarItem.IMAGE -> ItemGlyph.Vector(Ph.image, Ph.imageDuotone, Ph.imageFill)
    ToolbarItem.LASER -> ItemGlyph.Custom(InkGlyph.LASER)
    ToolbarItem.TAPE -> ItemGlyph.Custom(InkGlyph.TAPE)
    ToolbarItem.MORE -> ItemGlyph.Vector(Ph.dotsThree)
    ToolbarItem.COLORS -> ItemGlyph.Colours
    ToolbarItem.HOME -> ItemGlyph.Vector(Ph.house)
    ToolbarItem.TITLE -> ItemGlyph.Vector(Ph.pencilSimpleLine)
    ToolbarItem.SIDEBAR -> ItemGlyph.Vector(Ph.sidebarSimple, filled = Ph.sidebarSimpleFill)
    ToolbarItem.PAGE_NAV -> ItemGlyph.Vector(Ph.files)
    ToolbarItem.STYLES -> ItemGlyph.Vector(Ph.fileText)
    ToolbarItem.MARGINS -> ItemGlyph.Vector(Ph.frameCorners)
    ToolbarItem.VIEW -> ItemGlyph.Vector(Ph.eyeglasses)
    ToolbarItem.ZOOM -> ItemGlyph.Vector(Ph.magnifyingGlassPlus)
    ToolbarItem.FIT -> ItemGlyph.Vector(Ph.arrowsOutLineHorizontal)
    ToolbarItem.ZOOM_LOCK -> ItemGlyph.Vector(Ph.lockSimple, filled = Ph.lockSimpleFill)
    ToolbarItem.FULLSCREEN -> ItemGlyph.Vector(Ph.arrowsOut)
    ToolbarItem.WAYPOINTS -> ItemGlyph.Vector(Ph.mapPin)
    ToolbarItem.MINIMAP -> ItemGlyph.Vector(Ph.mapTrifold, filled = Ph.mapTrifoldFill)
}

/** Three overlapping ink dots (.st-dots): the Colours chip. */
internal val COLOUR_DOTS = listOf(Color(0xFF1F2A44), Color(0xFF2563EB), Color(0xFFDC2626))

/**
 * [item]'s glyph at [size] in [tint]. [active]: the duotone (or the custom glyph's active form) the glider shows the
 * tool in hand with. [filled]: the Fill a More row shows when the item is in hand or on. [active] wins when both are set.
 */
@Composable
internal fun ItemIcon(item: ToolbarItem, tint: Color, size: Dp, active: Boolean = false, filled: Boolean = false) {
    val ink = LocalInk.current
    when (val g = glyphOf(item)) {
        is ItemGlyph.Vector -> Icon(
            when {
                active -> g.active
                filled -> g.filled
                else -> g.regular
            },
            null,
            tint = tint,
            modifier = Modifier.size(size),
        )
        is ItemGlyph.Custom -> InkGlyphIcon(g.glyph, active || filled, tint, null, Modifier.size(size))
        ItemGlyph.Colours -> Canvas(Modifier.size(size)) {
            val r = this.size.minDimension * 0.225f
            COLOUR_DOTS.forEachIndexed { i, c ->
                val cx = r + i * r * 1.33f
                drawCircle(ink.raised, r + 1.5.dp.toPx(), Offset(cx, center.y))
                drawCircle(c, r, Offset(cx, center.y))
            }
        }
    }
}

/** The bar item a tool sits under (PEN for the fountain pen, BALLPOINT … TAPER for the others, HIGHLIGHTER, ERASER, …). */
internal fun Tool.barItem(): ToolbarItem = when (this) {
    Tool.PEN -> ToolbarItem.PEN
    Tool.BALLPOINT -> ToolbarItem.BALLPOINT
    Tool.DASHED -> ToolbarItem.DASHED
    Tool.CALLIGRAPHY -> ToolbarItem.CALLIGRAPHY
    Tool.SPEED -> ToolbarItem.SPEED
    Tool.TAPER -> ToolbarItem.TAPER
    Tool.PENCIL -> ToolbarItem.PENCIL
    Tool.HIGHLIGHTER -> ToolbarItem.HIGHLIGHTER
    Tool.ERASER -> ToolbarItem.ERASER
    Tool.PAN -> ToolbarItem.PAN
    Tool.SELECT -> ToolbarItem.SELECT
    Tool.LASSO -> ToolbarItem.LASSO
    Tool.SCREENSHOT -> ToolbarItem.SCREENSHOT
    Tool.SHAPE -> ToolbarItem.SHAPE
    Tool.TEXT -> ToolbarItem.TEXT
    Tool.TEXT_BOX -> ToolbarItem.TEXT_BOX
    Tool.IMAGE -> ToolbarItem.IMAGE
    Tool.MARKUP -> ToolbarItem.MARKUP
    Tool.LASER -> ToolbarItem.LASER
    Tool.TAPE -> ToolbarItem.TAPE
}

/** A tool's glyph at [size] in [tint]: Phosphor regular at rest, duotone (or the custom glyph's active form) when [active]. */
@Composable
internal fun ToolGlyph(tool: Tool, tint: Color, size: Dp, active: Boolean, modifier: Modifier = Modifier) {
    when (val g = glyphOf(tool.barItem())) {
        is ItemGlyph.Vector -> Icon(if (active) g.active else g.regular, null, tint = tint, modifier = modifier.size(size))
        is ItemGlyph.Custom -> InkGlyphIcon(g.glyph, active, tint, null, modifier.size(size))
        // No tool sits under the Colours item.
        ItemGlyph.Colours -> Unit
    }
}
