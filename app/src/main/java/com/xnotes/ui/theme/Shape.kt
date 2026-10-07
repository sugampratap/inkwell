package com.xnotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.settings.CornerStyle

/** Material's shape roles, every radius scaled by [style]. Paper and hairlines keep literal corners. */
fun uiShapes(style: CornerStyle): Shapes {
    val k = style.scale
    return Shapes(
        // Soft corners throughout, as a sheet of paper and a pen case have: nothing in the chrome
        // is a hard box, and the smallest chip still reads as rounded.
        extraSmall = RoundedCornerShape((8 * k).dp),
        small = RoundedCornerShape((12 * k).dp),
        medium = RoundedCornerShape((16 * k).dp),
        large = RoundedCornerShape((20 * k).dp),
        extraLarge = RoundedCornerShape((28 * k).dp),
    )
}

/** How much the Corners setting scales a radius: Sharp .35, Rounded 1, Soft 1.75. Provided by [XnotesTheme]. */
val LocalCornerScale = staticCompositionLocalOf { 1f }

/** [base], a radius at Rounded, at the Corners setting [style]. */
fun scaledCorner(base: Dp, style: CornerStyle): Dp = base * style.scale

/** [base], a radius at Rounded, at the Corners setting in force: for corners drawn in a draw lambda. */
@Composable
@ReadOnlyComposable
fun cornerOf(base: Dp): Dp = base * LocalCornerScale.current

/** A rounded shape whose [base] radius (at Rounded) follows the Corners setting, for radii with no Material role (9, 10, 14, 24dp). */
@Composable
fun inkRounded(base: Dp): Shape {
    val k = LocalCornerScale.current
    return remember(base, k) { RoundedCornerShape(base * k) }
}
