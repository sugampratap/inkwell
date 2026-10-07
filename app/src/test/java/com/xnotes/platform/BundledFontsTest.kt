package com.xnotes.platform

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled list and the asset folders must match exactly: a leftover folder bloats the APK, a missing one breaks a font. */
class BundledFontsTest {

    private val root = File("src/main/assets/fonts")

    private fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun theBundledListMatchesTheAssetFolders() {
        val folders = root.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet()
        assertEquals(BundledFonts.ALL.map { it.slug }.toSet(), folders)
    }

    @Test fun theCutLeavesCarlitoLoraAndJetBrainsMono() {
        assertEquals(setOf("carlito", "lora", "jetbrains-mono"), BundledFonts.ALL.map { it.slug }.toSet())
    }

    @Test fun everyFolderHoldsItsLicenceAndExactlyTheFilesTheLoaderOpens() {
        for (f in BundledFonts.ALL) {
            val want = buildSet {
                add("LICENSE.txt")
                if (f.variable) {
                    add("regular.ttf")
                    if (f.hasItalic) add("italic.ttf")
                } else {
                    addAll(listOf("regular.ttf", "bold.ttf", "italic.ttf", "bolditalic.ttf"))
                }
            }
            val have = File(root, f.slug).listFiles()!!.map { it.name }.toSet()
            assertEquals(f.slug, want, have)
        }
    }

    @Test fun carlitoShipsUnmodified() {
        // Reserved Font Name "Carlito": a changed file would have to be renamed. These are the staged google/fonts files.
        val expected = mapOf(
            "regular.ttf" to "f6418f708baede9789daef5d458c0f53d2a888af9820e8062934e504fedc6595",
            "bold.ttf" to "bb5d20f79b82599ec72983597437373a80f2d2085fa91fc144fd74e876a594db",
            "italic.ttf" to "0b019225e58d702bfedcbd35c21696769f8ee115cb6343f84c2f240312450d1c",
            "bolditalic.ttf" to "b32928186c119599e03ca6a1ffc680fdcb7fac95772f4b95d989cf6cd3861517",
        )
        for ((name, hash) in expected) assertEquals(name, hash, sha256(File(root, "carlito/$name")))
    }

    @Test fun carlitoKeepsItsLicenceAndReservedName() {
        val licence = File(root, "carlito/LICENSE.txt").readText()
        assertTrue(licence.contains("Reserved Font Name \"Carlito\""))
        assertTrue(licence.contains("SIL Open Font License, Version 1.1"))
    }
}
