package com.xnotes.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * [InkPress] drawn over the content instead of under it: for tiles whose picture fills them (page
 * thumbnails, template tiles), where a tint under the picture never shows. [radius] rounds the tint
 * to the tile's corners. The tint appears as the press registers and eases out over 180 ms.
 */
class InkPressOver(private val radius: Dp = 0.dp) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkPressOverNode(interactionSource, radius)
    override fun equals(other: Any?): Boolean = other is InkPressOver && other.radius == radius
    override fun hashCode(): Int = radius.hashCode()
}

private class InkPressOverNode(private val source: InteractionSource, private val radius: Dp) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var shown = Animatable(0f)

    override fun onDetach() {
        // As in InkPress: a node kept across detach and reuse must not come back frozen mid-fade.
        shown = Animatable(0f)
    }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> launch { shown.snapTo(1f) }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch { shown.animateTo(0f, tween(InkMotion.BASE, easing = InkMotion.Standard)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = shown.value
        if (a > 0f) {
            val ink = currentValueOf(LocalInk)
            drawRoundRect(ink.text.copy(alpha = (if (ink.isDark) 0.14f else 0.10f) * a), cornerRadius = CornerRadius(radius.toPx()))
        }
    }
}
