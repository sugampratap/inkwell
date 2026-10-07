package com.xnotes.settings

enum class MaterialColourMode(val id: String) {
    /** The hand-tuned warm paper look, the default; the others are built from Material seeds. */
    PAPER("paper"), SYSTEM("system"), SINGLE("single"), DUAL("dual");

    companion object {
        fun fromId(id: String): MaterialColourMode? = entries.firstOrNull { it.id == id }
    }
}
