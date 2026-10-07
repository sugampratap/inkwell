package com.xnotes.ui

import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.LassoShape
import com.xnotes.core.tools.MarkupMode
import com.xnotes.core.tools.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArtTest {

    private val svgPath = Regex("^M[0-9MmLlHhVvCcSsQqTtAaZz .,\\-]*$")

    @Test fun wholeStrokeIsTheFadedDashedSquiggleUnderTheEraser() {
        assertEquals(
            listOf(
                ArtPart.Stroke(ERASER_SQUIGGLE, 2f, dash = listOf(2.5f, 3f), opacity = 0.34f),
                ArtPart.StrokeCircle(35f, 19f, 7.5f, 1.3f, opacity = 0.75f),
            ),
            eraserArt(EraseMode.STROKE),
        )
    }

    @Test fun areaCutsTheSquiggleWhereTheEraserIs() {
        val art = eraserArt(EraseMode.AREA)
        assertEquals(ArtPart.CutCircle(35f, 20f, 8.5f, listOf(ArtPart.Stroke(ERASER_SQUIGGLE, 2.2f))), art[0])
        assertEquals(ArtPart.StrokeCircle(35f, 20f, 7.5f, 1.3f, opacity = 0.75f), art[1])
    }

    @Test fun theLassoSquiggleDarkensWhenItsCardIsChosen() {
        assertEquals(0.55f, lassoArt(LassoShape.FREEFORM, selected = false).last().opacity, 0f)
        assertEquals(1f, lassoArt(LassoShape.FREEFORM, selected = true).last().opacity, 0f)
        assertEquals(ArtPart.Stroke(LASSO_LOOP, 1.5f, dash = listOf(3.5f, 3f)), lassoArt(LassoShape.FREEFORM, false).first())
        assertEquals(ArtPart.StrokeRect(12f, 7f, 48f, 27f, 2f, 1.5f, dash = listOf(3.5f, 3f)), lassoArt(LassoShape.RECTANGLE, false).first())
    }

    @Test fun theTablesHandOutTheSameListSoEachIsPreparedOnce() {
        for (s in LassoShape.entries) for (on in listOf(false, true)) assertTrue(lassoArt(s, on) === lassoArt(s, on))
        for (m in EraseMode.entries) assertTrue(eraserArt(m) === eraserArt(m))
    }

    @Test fun everyShapeKindHasItsMockupTile() {
        assertEquals(listOf(ArtPart.Stroke("M9 17L35 5", 1.8f)), shapeArt(ShapeKind.LINE))
        assertEquals(listOf(ArtPart.Stroke("M9 17L34 6M27.2 5.2L34 6l-2.6 6.4", 1.8f)), shapeArt(ShapeKind.ARROW))
        assertEquals(listOf(ArtPart.StrokeRect(9f, 4f, 26f, 14f, 1f, 1.8f)), shapeArt(ShapeKind.RECTANGLE))
        assertEquals(listOf(ArtPart.StrokeEllipse(22f, 11f, 14f, 7.5f, 1.8f)), shapeArt(ShapeKind.ELLIPSE))
        assertEquals(listOf(ArtPart.StrokeCircle(22f, 11f, 8f, 1.8f)), shapeArt(ShapeKind.CIRCLE))
        assertEquals(listOf(ArtPart.Stroke("M22 3.4L31 18.4H13z", 1.8f)), shapeArt(ShapeKind.TRIANGLE))
    }

    @Test fun everyMarkupTileShowsTheWordText() {
        for (m in MarkupMode.entries) assertTrue(m.name, ArtPart.Label(22f, 14.6f, 10.5f) in markupArt(m))
        assertEquals(ArtPart.Stroke("M8 19q2-2.4 4 0t4 0 4 0 4 0 4 0 4 0 4 0", 1.4f), markupArt(MarkupMode.SQUIGGLY).last())
        assertEquals(ArtPart.FillRect(8f, 5f, 28f, 13f, 2f, opacity = 0.24f), markupArt(MarkupMode.HIGHLIGHT).first())
    }

    @Test fun partsStayInsideTheirViewBox() {
        val big = EraseMode.entries.flatMap { eraserArt(it) } + LassoShape.entries.flatMap { lassoArt(it, false) }
        val small = ShapeKind.DRAW_TOOL_KINDS.flatMap { shapeArt(it) } + MarkupMode.entries.flatMap { markupArt(it) }
        for (p in big) assertTrue("$p", inside(p, OPTION_ART_W, OPTION_ART_H))
        for (p in small) assertTrue("$p", inside(p, TILE_ART_W, TILE_ART_H))
    }

    @Test fun everyPathIsAnSvgPath() {
        val drawn = EraseMode.entries.flatMap { eraserArt(it) } + LassoShape.entries.flatMap { lassoArt(it, true) } +
            ShapeKind.DRAW_TOOL_KINDS.flatMap { shapeArt(it) } + MarkupMode.entries.flatMap { markupArt(it) }
        val strokes = drawn.flatMap { if (it is ArtPart.CutCircle) it.parts else listOf(it) }
            .filterIsInstance<ArtPart.Stroke>().map { it.d }
        val paths = strokes + ShapeKind.DRAW_TOOL_KINDS.map { shapePreviewPath(it) } + ARROW_HEAD_D + LASER_PREVIEW_D
        for (d in paths) assertTrue(d, svgPath.matches(d))
    }

    @Test fun theShapePreviewsAreTheMockups() {
        assertEquals("M96 44L204 12", shapePreviewPath(ShapeKind.LINE))
        assertEquals("M96 44L204 12", shapePreviewPath(ShapeKind.ARROW))
        assertEquals("M188 10.2L204 12l-7.2 14.6", ARROW_HEAD_D)
        assertEquals("M150 8.5L174 47H126Z", shapePreviewPath(ShapeKind.TRIANGLE))
        assertEquals("M24 38C62 8 108 6 140 26s92 28 136-12", LASER_PREVIEW_D)
        // Closed outlines are closed; lines and arrows are not, so they never fill.
        for (k in ShapeKind.DRAW_TOOL_KINDS) assertEquals(k.name, k.isClosed, shapePreviewPath(k).endsWith("Z"))
    }

    @Test fun theArtFitsItsBoxAtEveryDensity() {
        // InkOptionCard's art scope is in dp but reports its size in px, so a 72 dp box is 72 × d px.
        for (d in listOf(1f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            assertEquals("option at $d", 1f, artScale(OPTION_ART_W * d, d, OPTION_ART_W), 1e-6f)
            assertEquals("tile at $d", 1f, artScale(TILE_ART_H * d, d, TILE_ART_H), 1e-6f)
        }
        // A box twice the viewBox draws the art twice as big, whatever the density.
        assertEquals(2f, artScale(144f * 2f, 2f, OPTION_ART_W), 1e-6f)
        assertEquals(0.5f, artScale(36f * 3f, 3f, OPTION_ART_W), 1e-6f)
    }

    private fun inside(p: ArtPart, w: Float, h: Float): Boolean = when (p) {
        is ArtPart.StrokeRect -> p.x >= 0f && p.y >= 0f && p.x + p.w <= w && p.y + p.h <= h
        is ArtPart.FillRect -> p.x >= 0f && p.y >= 0f && p.x + p.w <= w && p.y + p.h <= h
        is ArtPart.StrokeCircle -> p.cx - p.r >= 0f && p.cy - p.r >= 0f && p.cx + p.r <= w && p.cy + p.r <= h
        is ArtPart.FillCircle -> p.cx - p.r >= 0f && p.cy - p.r >= 0f && p.cx + p.r <= w && p.cy + p.r <= h
        is ArtPart.StrokeEllipse -> p.cx - p.rx >= 0f && p.cy - p.ry >= 0f && p.cx + p.rx <= w && p.cy + p.ry <= h
        is ArtPart.CutCircle -> p.cx - p.r >= 0f && p.cy - p.r >= 0f && p.cx + p.r <= w && p.cy + p.r <= h
        is ArtPart.Label -> p.x in 0f..w && p.baseline in 0f..h
        is ArtPart.Stroke -> true
    }
}
