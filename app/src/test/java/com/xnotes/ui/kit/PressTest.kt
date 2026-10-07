package com.xnotes.ui.kit

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Only the element's identity is tested here: a node test needs Compose UI test infrastructure,
 * which this project doesn't have on the JVM. Equal elements are what lets a host skip.
 */
class PressTest {

    @Test
    fun sameSourceAndScaleIsTheSameModifier() {
        val source = MutableInteractionSource()
        val a = Modifier.pressScale(source, 0.94f)
        val b = Modifier.pressScale(source, 0.94f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun aDifferentSourceOrScaleIsADifferentModifier() {
        val source = MutableInteractionSource()
        val base = Modifier.pressScale(source, 0.94f)
        assertNotEquals(base, Modifier.pressScale(source, 0.92f))
        assertNotEquals(base, Modifier.pressScale(MutableInteractionSource(), 0.94f))
    }
}
