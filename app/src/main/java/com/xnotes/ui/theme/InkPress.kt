package com.xnotes.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import kotlinx.coroutines.launch

/**
 * B2's press feedback for every plain `clickable`: the pressed thing darkens one step (--press over
 * --raised) as the press registers, and eases back over [InkMotion.BASE] (180 ms) on release. Inside
 * a scrollable, `clickable` holds the press back by the tap timeout, so it can register a beat after
 * the finger lands. The tint is drawn under the content, like CSS `:active { background }`, so an
 * opaque child covering the pressed thing hides it. No ripple: it keeps drawing for frames after the
 * finger has gone, and B2 has none.
 */
object InkPress : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkPressNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x1b2
}

private class InkPressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var shown = Animatable(0f)

    override fun onDetach() {
        // A caller that keeps this node across detach and reuse (Modifier.indication) must not get
        // it back frozen mid-fade: the collector that would have finished the fade is gone.
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
        val a = shown.value
        if (a > 0f) {
            val ink = currentValueOf(LocalInk)
            drawRect(ink.text.copy(alpha = (if (ink.isDark) 0.10f else 0.07f) * a))
        }
        drawContent()
    }
}
