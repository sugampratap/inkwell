package com.xnotes.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

class InkMotionTest {

    /** B2: overshoot only on tiny glyphs (heart, check), never on chrome that moves. */
    @Test
    fun chromeEasingsNeverOvershootOrReverse() {
        for (e in listOf(InkMotion.Standard, InkMotion.Glide, InkMotion.Emphasized)) {
            var prev = 0f
            for (i in 0..100) {
                val y = e.transform(i / 100f)
                assertTrue("$e at $i: $y", y >= -0.0001f && y <= 1.0001f)
                assertTrue("$e reverses at $i", y + 0.0001f >= prev)
                prev = y
            }
        }
    }

    @Test
    fun durationsAreTheMockupTokens() {
        assertEquals(120, InkMotion.FAST)
        assertEquals(180, InkMotion.BASE)
        assertEquals(280, InkMotion.SLOW)
        assertEquals(420, InkMotion.ANTS)
    }

    @Test
    fun pressIsAFastStandardTween() {
        val spec = tween(InkMotion.press())
        assertEquals(InkMotion.FAST, spec.durationMillis)
        assertSame(InkMotion.Standard, spec.easing)
    }

    @Test
    fun fadeIsABaseStandardTween() {
        val spec = tween(InkMotion.fade())
        assertEquals(InkMotion.BASE, spec.durationMillis)
        assertSame(InkMotion.Standard, spec.easing)
    }

    @Test
    fun screenIsASlowEmphasizedTween() {
        val spec = tween(InkMotion.screen())
        assertEquals(InkMotion.SLOW, spec.durationMillis)
        assertSame(InkMotion.Emphasized, spec.easing)
    }

    @Test
    fun springsKeepTheirTunedDampingAndStiffness() {
        assertSpring(InkMotion.glide(), damping = 0.85f, stiffness = 600f)
        assertSpring(InkMotion.popover(), damping = 0.85f, stiffness = 500f)
        assertSpring(InkMotion.pop(), damping = 0.5f, stiffness = 800f)
        assertSpring(InkMotion.check(), damping = 0.6f, stiffness = 700f)
    }

    /**
     * B2: chrome springs settle without a visible bounce; only the glyph springs may overshoot.
     * The first overshoot of an underdamped spring is exp(-πζ / √(1 - ζ²)) of the travel.
     */
    @Test
    fun onlyTheGlyphSpringsMayBounce() {
        assertTrue(overshoot(InkMotion.glide()) < 0.01)
        assertTrue(overshoot(InkMotion.popover()) < 0.01)
        assertTrue(overshoot(InkMotion.pop()) > 0.05)
        assertTrue(overshoot(InkMotion.check()) > 0.05)
    }

    private fun overshoot(spring: SpringSpec<Float>): Double {
        val zeta = spring.dampingRatio.toDouble()
        return exp(-PI * zeta / sqrt(1 - zeta * zeta))
    }

    private fun tween(spec: FiniteAnimationSpec<Float>): TweenSpec<Float> {
        assertTrue("expected a TweenSpec, was $spec", spec is TweenSpec<*>)
        return spec as TweenSpec<Float>
    }

    private fun assertSpring(spec: SpringSpec<Float>, damping: Float, stiffness: Float) {
        assertEquals(damping, spec.dampingRatio, 0f)
        assertEquals(stiffness, spec.stiffness, 0f)
    }
}
