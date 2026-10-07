package com.xnotes.core.infinite

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TapeItem
import com.xnotes.core.model.TapePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tape on the GL canvas: how it is meshed, and how the eraser treats it. */
class TapeMeshTest {

    private fun tape(revealed: Boolean = false, pattern: TapePattern = TapePattern.STRIPES) =
        TapeItem(Pt(0.0, 0.0), Pt(200.0, 0.0), 40.0, Rgba(250, 220, 120), pattern, revealed, seed = 5)

    @Test fun aCoveredStripIsOpaqueOverATranslucentShadow() {
        val meshed = ItemMesher.mesh(tape())
        assertNotNull(meshed)
        val passes = meshed!!.parts.map { it.pass }
        assertEquals(listOf(InkPass.TRANSLUCENT, InkPass.TRANSLUCENT, InkPass.OPAQUE, InkPass.OPAQUE, InkPass.OPAQUE), passes)
        // The body is the tape's own opaque colour.
        assertEquals(Rgba(250, 220, 120, 255), meshed.parts[2].color)
        // Everything it draws stays inside the bounds the renderer culls and covers by.
        for (part in meshed.parts) {
            val pos = part.mesh.positions
            for (i in 0 until pos.size / 2) {
                assertTrue(meshed.bounds.outset(1.0).contains(Pt(pos[2 * i], pos[2 * i + 1])))
            }
        }
    }

    @Test fun aPeeledStripIsOnlyFaintLayers() {
        val meshed = ItemMesher.mesh(tape(revealed = true))!!
        assertTrue(meshed.parts.isNotEmpty())
        assertTrue(meshed.parts.all { it.pass == InkPass.TRANSLUCENT && it.color.a < 255 })
    }

    @Test fun aPlainStripHasNoPrintPart() {
        val meshed = ItemMesher.mesh(tape(pattern = TapePattern.SOLID))!!
        assertEquals(4, meshed.parts.size)
    }

    @Test fun eitherEraserTakesTheWholeStrip() {
        for (area in listOf(false, true)) {
            val t = tape()
            val doc = InfiniteDocument().apply { addAll(listOf(t)) }
            val session = EraseSession(doc)
            assertNull(session.erase(100.0, 200.0, 10.0, area)) // nowhere near
            assertNotNull(session.erase(100.0, 25.0, 10.0, area)) // brushes the bottom edge
            assertTrue(doc.items.isEmpty())
            // Undo puts the very same strip back.
            session.buildCommand()!!.undo()
            assertEquals(listOf(t), doc.items)
        }
    }
}
