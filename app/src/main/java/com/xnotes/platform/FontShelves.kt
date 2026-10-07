package com.xnotes.platform

import com.xnotes.core.pal.FontFace

// The bundled fonts and the grouped font list's shelves, with no Android types so the JVM tests can load them.
// FontCatalog reads the family list from here and resolves through fontSource / BundledFonts.fallbackFor.

/** The grouped font list's shelves (r3_text Frame 3), in list order. */
enum class FontShelf { IMPORTED, BUILT_IN, SANS, SERIF, MONO }

/** One pickable font: the face, whether it is monospaced, and the shelf it sits on. */
data class FontPick(val face: FontFace, val mono: Boolean, val shelf: FontShelf)

/** A bundled family: [name] is its display name and saved id, [slug] its folder under `assets/fonts/`. */
data class BundledFamily(
    val name: String,
    val slug: String,
    val mono: Boolean,
    /** One variable file per slant (bold from the `wght` axis); else four static files. */
    val variable: Boolean,
    val hasItalic: Boolean,
    val shelf: FontShelf,
)

object BundledFonts {
    /** What 1.4.0 bundles (user decision, 2026-10-06): one sans, one serif, one code face. */
    val ALL: List<BundledFamily> = listOf(
        BundledFamily("Carlito", "carlito", mono = false, variable = false, hasItalic = true, shelf = FontShelf.SANS),
        BundledFamily("Lora", "lora", mono = false, variable = true, hasItalic = true, shelf = FontShelf.SERIF),
        BundledFamily("JetBrains Mono", "jetbrains-mono", mono = true, variable = true, hasItalic = true, shelf = FontShelf.MONO),
    )

    /**
     * The families 1.3.x bundled and 1.4.0 dropped, and the system face each now resolves to (round 3 defaults,
     * Part 6 row 2): monospaced ones to Mono so code keeps its columns, the serif display face to Serif, the rest to Sans.
     * Notes keep the id, so importing the font again brings it back.
     */
    val RETIRED: Map<String, FontFace> = buildMap {
        for (id in listOf("Fira Code", "IBM Plex Mono", "Roboto Mono", "Source Code Pro", "Ubuntu Mono")) put(id, FontFace.MONO)
        put("Playfair Display", FontFace.SERIF)
        for (id in listOf("Fira Sans", "Inter", "Lato", "Montserrat", "Nunito", "Open Sans", "Poppins", "Raleway", "Roboto", "Source Sans 3", "Ubuntu")) {
            put(id, FontFace.SANS)
        }
    }

    /** The system face a font that cannot be loaded draws in: its kind's, else Sans. */
    fun fallbackFor(id: String): FontFace = RETIRED[id] ?: FontFace.SANS
}

/** Where a face id resolves from, in the order resolution tries them. */
enum class FontSource { GENERIC, BUNDLED, IMPORTED, RETIRED, UNKNOWN }

private val GENERIC_IDS = setOf(FontFace.SANS.id, FontFace.SERIF.id, FontFace.MONO.id, FontFace.HAND.id)

/** [id]'s source: imports come before the retired table, so a re-imported retired family is the user's file again. */
fun fontSource(id: String, bundled: Set<String>, imported: Set<String>): FontSource = when (id) {
    in GENERIC_IDS -> FontSource.GENERIC
    in bundled -> FontSource.BUNDLED
    in imported -> FontSource.IMPORTED
    in BundledFonts.RETIRED -> FontSource.RETIRED
    else -> FontSource.UNKNOWN
}

/**
 * Every pickable font: the four system faces, the bundled families, then [imported] by name, case-insensitively.
 * An import whose id is a system or bundled id is dropped (the bundled one wins, as in [fontSource]): 1.3.x let users
 * import Carlito before 1.4.0 bundled it, and two picks with one id would give the font list two rows with one key.
 */
fun fontPicks(imported: List<FontPick>): List<FontPick> = buildList {
    add(FontPick(FontFace.SANS, mono = false, shelf = FontShelf.BUILT_IN))
    add(FontPick(FontFace.SERIF, mono = false, shelf = FontShelf.BUILT_IN))
    add(FontPick(FontFace.MONO, mono = true, shelf = FontShelf.BUILT_IN))
    add(FontPick(FontFace.HAND, mono = false, shelf = FontShelf.BUILT_IN))
    for (f in BundledFonts.ALL) add(FontPick(FontFace(f.name), f.mono, f.shelf))
    val taken = mapTo(HashSet()) { it.face.id }
    addAll(imported.filter { it.face.id !in taken }.sortedBy { it.face.id.lowercase() })
}

/** [picks] grouped by shelf in shelf order, empty shelves dropped; [monoOnly] (the Code font list) keeps monospaced fonts only. */
fun fontShelves(picks: List<FontPick>, monoOnly: Boolean): List<Pair<FontShelf, List<FontPick>>> =
    FontShelf.entries.mapNotNull { shelf ->
        val items = picks.filter { it.shelf == shelf && (!monoOnly || it.mono) }
        if (items.isEmpty()) null else shelf to items
    }

/** The "mono" tag (TX 724): imported monospaced fonts, in the full list only (the Code font list is all mono). */
fun showsMonoTag(pick: FontPick, monoOnly: Boolean): Boolean = pick.shelf == FontShelf.IMPORTED && pick.mono && !monoOnly
