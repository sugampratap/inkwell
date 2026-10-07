package com.xnotes.ui

import com.xnotes.core.tools.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NibArtTest {

    @Test
    fun everyPenTypeHasArt() {
        for (t in Tool.allPenTypes) assertTrue("$t has no nib art", nibArt(t).isNotEmpty())
    }

    @Test
    fun theGridOffersThePencilWhereTheQuillWas() {
        assertEquals(
            listOf(Tool.PEN, Tool.BALLPOINT, Tool.TAPER, Tool.CALLIGRAPHY, Tool.PENCIL, Tool.DASHED),
            Tool.penTypes,
        )
        assertTrue(Tool.SPEED !in Tool.penTypes)
        assertTrue(Tool.SPEED in Tool.allPenTypes && Tool.SPEED.isPen)
    }

    @Test
    fun thePencilIsAPencilLikeThePensBesideIt() {
        val art = nibArt(Tool.PENCIL)
        // The painted body in the theme's colour, as every pen's is, and lying the same way.
        val body = art[0] as NibPart.RoundRect
        assertEquals(NibInk.Current, body.ink)
        assertEquals(8f, body.h, 0f)
        assertEquals(11f, body.y + body.h / 2f, 1e-6f)
        // Sharpened wood and a graphite point at the tip, on the left like the others.
        val fills = art.filterIsInstance<NibPart.FillPath>()
        assertEquals(listOf(NibInk.Fixed(0xFF9A6B43), NibInk.Current), fills.map { it.ink })
        assertTrue(fills.last().d.startsWith("M10.6 10L5.4 11"))
    }

    @Test
    fun theDashedPenIsAPenDrawingItsDashedLine() {
        val art = nibArt(Tool.DASHED)
        val body = art[0] as NibPart.RoundRect
        assertEquals(NibInk.Current, body.ink)
        assertEquals(11f, body.y + body.h / 2f, 1e-6f)
        val dashes = art.filterIsInstance<NibPart.StrokePath>().filter { it.dash != null }
        assertEquals(1, dashes.size)
        assertTrue(dashes[0].roundCap)
    }

    @Test
    fun toolsThatAreNotPensHaveNone() {
        for (t in Tool.entries.filterNot { it.isPen }) assertTrue("$t should have no nib art", nibArt(t).isEmpty())
    }

    @Test
    fun quillDrawsTheMockupSpeedStroke() {
        val art = nibArt(Tool.SPEED)
        assertEquals(NibPart.StrokePath("M12 15C20 9 29 7 41 8", NibInk.Current, 3f, roundCap = true), art[0])
        assertEquals(NibPart.StrokePath("M2.5 10.5h6M4 14.5h5", NibInk.Current, 1.4f, roundCap = true, opacity = 0.55f), art[1])
    }

    @Test
    fun dashedLaysOneDashedLineFromItsTip() {
        val art = nibArt(Tool.DASHED)
        val dash = art.last() as NibPart.StrokePath
        assertEquals(listOf(1f, 3f), dash.dash)
        assertEquals(1.6f, dash.width, 0f)
    }

    @Test
    fun fountainKeepsTheMockupMetals() {
        val fixed = nibArt(Tool.PEN).map { it.ink }.filterIsInstance<NibInk.Fixed>().map { it.argb }.toSet()
        assertEquals(setOf(0xFF9A9A9AL, 0xFFC9A15AL, 0xFF6E5222L), fixed)
    }

    @Test
    fun rectsAndCirclesStayInsideTheViewBox() {
        for (t in Tool.allPenTypes) for (p in nibArt(t)) when (p) {
            is NibPart.RoundRect -> assertTrue("$t $p", p.x >= 0f && p.y >= 0f && p.x + p.w <= NIB_ART_W && p.y + p.h <= NIB_ART_H)
            is NibPart.Circle -> assertTrue("$t $p", p.cx - p.r >= 0f && p.cy - p.r >= 0f && p.cx + p.r <= NIB_ART_W && p.cy + p.r <= NIB_ART_H)
            else -> Unit
        }
    }
}
