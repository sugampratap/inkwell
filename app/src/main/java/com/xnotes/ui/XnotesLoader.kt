package com.xnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * The launch screen: the app's mark and name on the theme's own background, settling in while the
 * session restores and fading out once it has. The window behind it is the same colour, so the
 * launch never flashes a second colour on the way in.
 */
@Composable
fun XnotesLoader(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Box(
        modifier.fillMaxSize().background(palette.bg.toComposeColor()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = appear.value
                val s = 0.94f + 0.06f * appear.value
                scaleX = s
                scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painterResource(R.drawable.inkwell_icon_tile),
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)),
            )
            Text(
                stringResource(R.string.app_name),
                color = palette.text.toComposeColor(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            )
        }
    }
}
