package com.xnotes.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import com.xnotes.ui.theme.InkMotion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shrinks to [pressedScale] while [interaction] is held: B2 press feedback (icon buttons .94,
 * tools .92, cards .98, buttons .96–.97, steppers and swatches .9).
 *
 * A press neither recomposes nor relayouts anything: the scale lives in an [Animatable] that is
 * read only inside the layer block, so only the layer is redrawn. A release mid-press retargets
 * from the current scale, so a quick tap never jumps.
 *
 * Hit-testing runs in the scaled space, so at .9–.97 a press that starts within a dp or two of the
 * edge can be cancelled as the element shrinks. Where that matters, put the clickable on an unscaled
 * parent and scale only the visual inside it (see `StepButton`).
 */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float): Modifier =
    this then PressScaleElement(interaction, pressedScale)

private data class PressScaleElement(
    val interaction: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interaction, pressedScale)

    override fun update(node: PressScaleNode) = node.update(interaction, pressedScale)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(
    private var interaction: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {

    private val scale = Animatable(1f)
    private var tracker: Job? = null
    private var animation: Job? = null

    /** One instance for the node's life: a new lambda per placement would re-apply the layer each time. */
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        scaleX = scale.value
        scaleY = scale.value
    }

    override fun onAttach() = track()

    fun update(interaction: InteractionSource, pressedScale: Float) {
        this.pressedScale = pressedScale
        if (interaction !== this.interaction) {
            this.interaction = interaction
            if (isAttached) track()
        }
    }

    /** Follows the source's presses; a new source (or a reattach) starts again from rest. */
    private fun track() {
        tracker?.cancel()
        // Undispatched, so the subscription is live as soon as this returns: no gap after a swap.
        tracker = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val presses = ArrayList<PressInteraction.Press>(1)
            // Settle a scale left over from before a detach, without delaying the subscription below.
            animation = launch { scale.snapTo(1f) }
            interaction.interactions.collect { event ->
                val changed = when (event) {
                    is PressInteraction.Press -> presses.add(event)
                    is PressInteraction.Release -> presses.remove(event.press)
                    is PressInteraction.Cancel -> presses.remove(event.press)
                    else -> false
                }
                if (changed) {
                    val target = if (presses.isEmpty()) 1f else pressedScale
                    // The animation runs in a child job so this collector keeps draining events.
                    animation?.cancel()
                    animation = launch { scale.animateTo(target, InkMotion.press()) }
                }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}
