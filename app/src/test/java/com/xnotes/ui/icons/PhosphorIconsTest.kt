package com.xnotes.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ph.kt is generated from tools/phosphor/icons.txt; this keeps the two in step and every path parsing. */
class PhosphorIconsTest {

    // Unit tests run with the module (app/) as the working directory.
    private val manifest = File("../tools/phosphor/icons.txt")

    private fun kotlinName(name: String, weight: String): String {
        val parts = name.split('-')
        val base = parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        return if (weight == "regular") base else base + weight.replaceFirstChar(Char::uppercaseChar)
    }

    private fun entries(): List<String> = manifest.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .flatMap { line ->
            val name = line.substringBefore(' ')
            line.substringAfter(' ').split(',').map { kotlinName(name, it.trim()) }
        }

    @Test
    fun everyManifestIconIsGeneratedAndParses() {
        assertTrue("manifest at ${manifest.absolutePath}", manifest.exists())
        val names = entries()
        // 233 after Part 3, + 8 Round 3 weights (R0 Task 1), + wave-sine (Part 9), + pencil-simple's duotone and
        // fill (the pencil's bar glyph). Adding an icon means updating this count.
        assertEquals(244, names.size)
        for (n in names) {
            val getter = Ph::class.java.getMethod("get" + n.replaceFirstChar(Char::uppercaseChar))
            val icon = getter.invoke(Ph) as ImageVector
            assertEquals(n, 256f, icon.viewportWidth)
            assertTrue(n, icon.root.size >= 1)
        }
    }

    @Test
    fun roundThreeWeightsAreGenerated() {
        // Part 4: the lasso's chosen Handwriting row (SC 524). Part 6: the heading menu's chosen level (TX 736)
        // and the Body row in Regular (TX 736, 1095).
        val expected = mapOf(
            "scribble-fill" to Ph.scribbleFill,
            "paragraph" to Ph.paragraph,
            "text-h-one-fill" to Ph.textHOneFill,
            "text-h-two-fill" to Ph.textHTwoFill,
            "text-h-three-fill" to Ph.textHThreeFill,
            "text-h-four-fill" to Ph.textHFourFill,
            "text-h-five-fill" to Ph.textHFiveFill,
            "text-h-six-fill" to Ph.textHSixFill,
        )
        for ((label, icon) in expected) {
            assertEquals(label, icon.name)
            assertEquals(label, 256f, icon.viewportWidth)
        }
    }

    @Test
    fun duotoneKeepsItsTwentyPercentLayer() {
        val icon = Ph.eraserDuotone
        val alphas = (0 until icon.root.size).map { (icon.root[it] as VectorPath).fillAlpha }
        assertTrue(alphas.toString(), 0.2f in alphas && 1f in alphas)
    }

    @Test
    fun squigglyMarkHasItsWave() {
        // Part 9: the PDF bars' Squiggly mark (was XnotesIcons.squiggly).
        assertEquals("wave-sine", Ph.waveSine.name)
        assertEquals(256f, Ph.waveSine.viewportWidth)
    }
}
