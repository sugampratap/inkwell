package com.xnotes.platform

import com.xnotes.core.pal.FontFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontShelvesTest {

    private fun imported(name: String, mono: Boolean = false) = FontPick(FontFace(name), mono, FontShelf.IMPORTED)

    private val picks = fontPicks(listOf(imported("zed Mono", mono = true), imported("Avenir")))

    private fun ids(shelf: Pair<FontShelf, List<FontPick>>) = shelf.second.map { it.face.id }

    @Test fun picksListBuiltInsThenBundledThenImportedByName() {
        assertEquals(
            listOf("sans", "serif", "mono", "hand", "Carlito", "Lora", "JetBrains Mono", "Avenir", "zed Mono"),
            picks.map { it.face.id },
        )
    }

    @Test fun theFullListGroupsInTheMockupsOrder() {
        val shelves = fontShelves(picks, monoOnly = false)
        assertEquals(listOf(FontShelf.IMPORTED, FontShelf.BUILT_IN, FontShelf.SANS, FontShelf.SERIF, FontShelf.MONO), shelves.map { it.first })
        assertEquals(listOf("Avenir", "zed Mono"), ids(shelves[0]))
        assertEquals(listOf("sans", "serif", "mono", "hand"), ids(shelves[1]))
        assertEquals(listOf("Carlito"), ids(shelves[2]))
        assertEquals(listOf("Lora"), ids(shelves[3]))
        assertEquals(listOf("JetBrains Mono"), ids(shelves[4]))
    }

    @Test fun anImportWithABundledOrSystemIdIsDroppedSoTheBundledOneWins() {
        // 1.3.x let users import Carlito; 1.4.0 bundles it. Two "Carlito" picks would give the font list a duplicate key.
        val all = fontPicks(listOf(imported("Carlito"), imported("sans"), imported("JetBrains Mono", mono = true), imported("Avenir")))
        assertEquals(1, all.count { it.face.id == "Carlito" })
        assertEquals(FontShelf.SANS, all.single { it.face.id == "Carlito" }.shelf)
        assertEquals(1, all.count { it.face.id == "sans" })
        assertEquals(1, all.count { it.face.id == "JetBrains Mono" })
        assertEquals(listOf("Avenir"), all.filter { it.shelf == FontShelf.IMPORTED }.map { it.face.id })
        assertEquals(all.size, all.map { it.face.id }.toSet().size)
    }

    @Test fun withNoImportsTheListStartsAtBuiltIn() {
        assertEquals(FontShelf.BUILT_IN, fontShelves(fontPicks(emptyList()), monoOnly = false).first().first)
    }

    @Test fun theCodeFontListKeepsOnlyMonospacedFonts() {
        val shelves = fontShelves(picks, monoOnly = true)
        assertEquals(listOf(FontShelf.IMPORTED, FontShelf.BUILT_IN, FontShelf.MONO), shelves.map { it.first })
        assertEquals(listOf("zed Mono"), ids(shelves[0]))
        assertEquals(listOf("mono"), ids(shelves[1]))
        assertEquals(listOf("JetBrains Mono"), ids(shelves[2]))
    }

    @Test fun anEmptyShelfIsDropped() {
        val shelves = fontShelves(fontPicks(listOf(imported("Avenir"))), monoOnly = true)
        assertEquals(listOf(FontShelf.BUILT_IN, FontShelf.MONO), shelves.map { it.first })
    }

    @Test fun theMonoTagMarksOnlyImportedMonospacedFontsInTheFullList() {
        val zed = imported("zed Mono", mono = true)
        assertTrue(showsMonoTag(zed, monoOnly = false))
        assertFalse(showsMonoTag(zed, monoOnly = true))
        assertFalse(showsMonoTag(imported("Avenir"), monoOnly = false))
        assertFalse(showsMonoTag(FontPick(FontFace("JetBrains Mono"), true, FontShelf.MONO), monoOnly = false))
    }

    @Test fun removedMonospaceFontsFallBackToSystemMono() {
        for (id in listOf("Fira Code", "IBM Plex Mono", "Roboto Mono", "Source Code Pro", "Ubuntu Mono")) {
            assertEquals(id, FontFace.MONO, BundledFonts.fallbackFor(id))
        }
    }

    @Test fun playfairFallsBackToSystemSerifAndTheRestToSans() {
        assertEquals(FontFace.SERIF, BundledFonts.fallbackFor("Playfair Display"))
        for (id in listOf("Fira Sans", "Inter", "Lato", "Montserrat", "Nunito", "Open Sans", "Poppins", "Raleway", "Roboto", "Source Sans 3", "Ubuntu")) {
            assertEquals(id, FontFace.SANS, BundledFonts.fallbackFor(id))
        }
        assertEquals(FontFace.SANS, BundledFonts.fallbackFor("A font nobody has"))
    }

    @Test fun everyFamilyThe13xReleaseBundledIsKeptOrRetired() {
        val released = setOf(
            "Fira Sans", "Inter", "Lato", "Montserrat", "Nunito", "Open Sans", "Poppins", "Raleway", "Roboto", "Source Sans 3",
            "Ubuntu", "Lora", "Playfair Display", "Fira Code", "IBM Plex Mono", "JetBrains Mono", "Roboto Mono", "Source Code Pro",
            "Ubuntu Mono",
        )
        val kept = BundledFonts.ALL.map { it.name }.toSet()
        assertEquals(17, BundledFonts.RETIRED.size)
        assertTrue(BundledFonts.RETIRED.keys.none { it in kept })
        assertEquals(released, (kept - "Carlito") + BundledFonts.RETIRED.keys)
    }

    @Test fun aReimportedRetiredFontWinsOverItsFallback() {
        val bundled = BundledFonts.ALL.map { it.name }.toSet()
        assertEquals(FontSource.IMPORTED, fontSource("Fira Code", bundled, imported = setOf("Fira Code")))
        assertEquals(FontSource.RETIRED, fontSource("Fira Code", bundled, imported = emptySet()))
    }

    @Test fun sourcesResolveInOrder() {
        val bundled = BundledFonts.ALL.map { it.name }.toSet()
        assertEquals(FontSource.GENERIC, fontSource("sans", bundled, emptySet()))
        assertEquals(FontSource.BUNDLED, fontSource("Carlito", bundled, setOf("Carlito")))
        assertEquals(FontSource.UNKNOWN, fontSource("Nope", bundled, emptySet()))
    }
}
