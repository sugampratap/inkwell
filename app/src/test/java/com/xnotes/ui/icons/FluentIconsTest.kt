package com.xnotes.ui.icons

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fl.kt is generated from tools/fluent/icons.txt; this keeps the two in step and every path parsing. */
class FluentIconsTest {

    // Unit tests run with the module (app/) as the working directory.
    private val manifest = File("../tools/fluent/icons.txt")

    private fun kotlinName(name: String): String {
        val parts = name.split('_')
        return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun names(): List<String> = manifest.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }

    @Test
    fun everyManifestIconIsGeneratedAndParses() {
        assertTrue("manifest at ${manifest.absolutePath}", manifest.exists())
        val names = names()
        // The 28 approved for the selection bar plus 5 for the hold-to-paste menu (2026-10-07). Adding an icon means updating this count.
        assertEquals(33, names.size)
        for (n in names) {
            val getter = Fl::class.java.getMethod("get" + kotlinName(n).replaceFirstChar(Char::uppercaseChar))
            val icon = getter.invoke(Fl) as ImageVector
            assertEquals(n, icon.name)
            assertEquals(n, 24f, icon.viewportWidth)
            assertEquals(n, 24f, icon.viewportHeight)
            assertTrue(n, icon.root.size >= 1)
            for (i in 0 until icon.root.size) {
                val path = icon.root[i] as VectorPath
                assertTrue("$n has an empty path", path.pathData.isNotEmpty())
                assertEquals(n, PathFillType.NonZero, path.pathFillType)
            }
        }
    }

    @Test
    fun anEvenOddPathKeepsItsRule() {
        val icon = fluent("test", FlPath("M0 0h24v24H0Z"), FlPath("M4 4h16v16H4Z", evenOdd = true))
        assertEquals(PathFillType.NonZero, (icon.root[0] as VectorPath).pathFillType)
        assertEquals(PathFillType.EvenOdd, (icon.root[1] as VectorPath).pathFillType)
    }

    @Test
    fun theLicenceShipsWithTheApp() {
        val licence = File("src/main/assets/licenses/fluent-system-icons.txt")
        assertTrue(licence.exists())
        assertTrue(licence.readText().contains("Copyright (c) 2020 Microsoft Corporation"))
    }
}
