package com.xnotes.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One `<path>` of a Phosphor SVG: its data and opacity (a duotone's background layer is 0.2). */
class PhLayer(val d: String, val alpha: Float = 1f)

/**
 * A Phosphor icon: 256-unit viewport, 24dp by default. The black fill is a placeholder the `Icon`
 * tint replaces; the tint keeps each layer's alpha, so a duotone background stays at 20% of it.
 */
fun phosphor(name: String, vararg layers: PhLayer): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    )
    for (l in layers) {
        b.addPath(pathData = PathParser().parsePathString(l.d).toNodes(), fill = SolidColor(Color.Black), fillAlpha = l.alpha)
    }
    return b.build()
}
