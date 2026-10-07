package com.xnotes.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCacheMathTest {

    private fun lru(budget: Long, evicted: MutableList<String> = ArrayList()) =
        ByteLru<String, ByteArray>(budget, { it.size.toLong() }) { k, _ -> evicted.add(k) }

    @Test fun evictsLeastRecentlyUsedByBytes() {
        val evicted = ArrayList<String>()
        val c = lru(100, evicted)
        c.put("a", ByteArray(40))
        c.put("b", ByteArray(40))
        c["a"] // a is now the most recent
        c.put("c", ByteArray(40)) // 120 > 100: b goes
        assertEquals(listOf("b"), evicted)
        assertTrue(c.containsKey("a"))
        assertTrue(c.containsKey("c"))
        assertEquals(80, c.size)
    }

    @Test fun peekDoesNotRefreshAnEntry() {
        val evicted = ArrayList<String>()
        val c = lru(100, evicted)
        c.put("a", ByteArray(50))
        c.put("b", ByteArray(50))
        c.peek("a")
        c.put("c", ByteArray(10))
        assertEquals(listOf("a"), evicted)
    }

    @Test fun aValueBiggerThanTheBudgetIsNotKept() {
        val evicted = ArrayList<String>()
        val c = lru(100, evicted)
        c.put("small", ByteArray(10))
        c.put("huge", ByteArray(500))
        assertFalse(c.containsKey("huge"))
        assertTrue(c.containsKey("small"))
        assertEquals(listOf("huge"), evicted)
    }

    @Test fun replacingAKeyRecountsItsSize() {
        val c = lru(100)
        c.put("a", ByteArray(60))
        c.put("a", ByteArray(20))
        assertEquals(20, c.size)
        assertEquals(1, c.count)
        c.remove("a")
        assertEquals(0, c.size)
    }

    @Test fun loweringTheBudgetEvictsAtOnce() {
        val c = lru(100)
        c.put("a", ByteArray(40))
        c.put("b", ByteArray(40))
        c.budget = 50
        assertFalse(c.containsKey("a"))
        assertTrue(c.containsKey("b"))
    }

    @Test fun bucketsArePowersOfTwoCappedAtTheSource() {
        assertEquals(64, ImageBuckets.edgeFor(1, 4000))
        assertEquals(512, ImageBuckets.edgeFor(300, 4000))
        assertEquals(512, ImageBuckets.edgeFor(512, 4000))
        assertEquals(1024, ImageBuckets.edgeFor(513, 4000))
        // Past the source's own size the source size is the last step.
        assertEquals(3000, ImageBuckets.edgeFor(2500, 3000))
        assertEquals(800, ImageBuckets.edgeFor(5000, 800))
        // Never past the decode cap.
        assertEquals(4096, ImageBuckets.edgeFor(100_000, 10_000))
        // A drag that stays inside one step keeps hitting the same bucket.
        assertEquals(ImageBuckets.edgeFor(700, 4000), ImageBuckets.edgeFor(1000, 4000))
    }

    @Test fun standInPrefersTheNearestLargerSize() {
        assertEquals(1024, ImageBuckets.bestStandIn(listOf(256, 1024, 2048), 512))
        assertEquals(256, ImageBuckets.bestStandIn(listOf(128, 256), 512))
        assertNull(ImageBuckets.bestStandIn(emptyList(), 512))
    }

    @Test fun sampleSizeKeepsAtLeastTheTarget() {
        assertEquals(1, ImageBuckets.sampleSize(1000, 600))
        assertEquals(2, ImageBuckets.sampleSize(4000, 2000))
        assertEquals(4, ImageBuckets.sampleSize(8000, 1500))
        assertTrue(8000 / ImageBuckets.sampleSize(8000, 1500) >= 1500)
    }

    /** Where the stored pixel ([x], [y]) of a [w]×[h] image lands once [t] is applied. */
    private fun apply(t: ExifTransform, x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> {
        var px = if (t.mirror) w - 1 - x else x
        var py = y
        var cw = w
        var ch = h
        repeat(t.degrees / 90) {
            val nx = ch - 1 - py
            val ny = px
            px = nx
            py = ny
            val tmp = cw
            cw = ch
            ch = tmp
        }
        return px to py
    }

    @Test fun exifTagsMatchTheTiffDefinitions() {
        val w = 4
        val h = 3
        // Each tag's definition: where the stored image's first row/column end up when upright.
        assertTrue(ExifTransform.of(1).isIdentity)
        assertTrue(ExifTransform.of(0).isIdentity)
        assertTrue(ExifTransform.of(99).isIdentity)
        assertEquals((w - 1) to 0, apply(ExifTransform.of(2), 0, 0, w, h)) // mirrored
        assertEquals((w - 1) to (h - 1), apply(ExifTransform.of(3), 0, 0, w, h)) // half turn
        assertEquals(0 to (h - 1), apply(ExifTransform.of(4), 0, 0, w, h)) // flipped top to bottom
        assertEquals(1 to 2, apply(ExifTransform.of(5), 2, 1, w, h)) // transposed: (x, y) -> (y, x)
        assertEquals((h - 1) to 0, apply(ExifTransform.of(6), 0, 0, w, h)) // a quarter turn clockwise
        assertEquals((h - 1 - 1) to (w - 1 - 2), apply(ExifTransform.of(7), 2, 1, w, h)) // transverse
        assertEquals(0 to (w - 1), apply(ExifTransform.of(8), 0, 0, w, h)) // a quarter turn back
        assertTrue(ExifTransform.of(6).swapsAxes)
        assertFalse(ExifTransform.of(3).swapsAxes)
    }

    @Test fun importCapsOnlyOversizedPictures() {
        assertEquals(4000 to 3000, ImportSizing.storedSize(4000, 3000))
        assertEquals(4096 to 3072, ImportSizing.storedSize(8192, 6144))
        assertEquals(1536 to 4096, ImportSizing.storedSize(3000, 8000))
    }
}
