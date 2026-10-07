package com.xnotes.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Table glyphs Phosphor has no shape for: the minus twins of rows-plus-bottom and columns-plus-right (TI 564-565). */
object TableGlyphs {
    val rowsMinus: ImageVector by lazy {
        phosphor(
            "rows-minus",
            PhLayer("M208,112H48a16,16,0,0,0-16,16v24a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V128A16,16,0,0,0,208,112Zm0,40H48V128H208v24Zm0-112H48A16,16,0,0,0,32,56V80A16,16,0,0,0,48,96H208a16,16,0,0,0,16-16V56A16,16,0,0,0,208,40Zm0,40H48V56H208V80ZM160,216a8,8,0,0,1-8,8H104a8,8,0,0,1,0-16h48A8,8,0,0,1,160,216Z"),
        )
    }

    val colsMinus: ImageVector by lazy {
        phosphor(
            "cols-minus",
            PhLayer("M80,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H80a16,16,0,0,0,16-16V48A16,16,0,0,0,80,32Zm0,176H56V48H80ZM152,32H128a16,16,0,0,0-16,16V208a16,16,0,0,0,16,16h24a16,16,0,0,0,16-16V48A16,16,0,0,0,152,32Zm0,176H128V48h24Zm96-80a8,8,0,0,1-8,8H192a8,8,0,0,1,0-16h48A8,8,0,0,1,248,128Z"),
        )
    }
}
