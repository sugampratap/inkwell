package com.xnotes.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One `<path>` of a Fluent SVG: its data, and whether it fills by the even-odd rule (`fill-rule="evenodd"`). */
class FlPath(val d: String, val evenOdd: Boolean = false)

/**
 * A Fluent System Icon (Regular): a 24-unit viewport, 24dp by default, filled paths only. The black fill is a
 * placeholder the `Icon` tint replaces, so the icon takes the theme's ink like the Phosphor set does.
 */
fun fluent(name: String, vararg paths: FlPath): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (p in paths) {
        b.addPath(
            pathData = PathParser().parsePathString(p.d).toNodes(),
            pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }
    return b.build()
}
