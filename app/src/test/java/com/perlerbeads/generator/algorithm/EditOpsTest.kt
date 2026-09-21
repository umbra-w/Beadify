package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.transparentColorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditOpsTest {

    @Test
    fun floodFillErase_removesConnectedRegionOnly() {
        val a = MappedPixel("A", "#FF0000", false)
        val b = MappedPixel("B", "#0000FF", false)
        val g = arrayOf(
            arrayOf(a, a, b),
            arrayOf(a, a, b),
            arrayOf(b, b, b)
        )
        // 从 (0,0) 擦除 A：连通的两个 A 变透明，(0,2) 的 A? 不，(0,2) 是 B
        val res = floodFillErase(g, 0, 0, "A")
        assertEquals("ERASE", res[0][0].key)
        assertEquals("ERASE", res[0][1].key)
        assertEquals("ERASE", res[1][0].key)
        assertEquals("ERASE", res[1][1].key)
        assertEquals("B", res[2][0].key)
    }

    @Test
    fun replaceColor_replacesMatchingHex() {
        val red = MappedPixel("R", "#FF0000", false)
        val blue = MappedPixel("B", "#0000FF", false)
        val g = arrayOf(
            arrayOf(red, red),
            arrayOf(blue, red)
        )
        val res = replaceColor(g, MappedPixel("R", "#FF0000", false), MappedPixel("B", "#0000FF", false))
        assertEquals(3, res.replaceCount)
        assertEquals("#0000FF", res.grid[1][1].colorHex.uppercase())
    }

    @Test
    fun paintSinglePixel_changesColor() {
        val a = MappedPixel("A", "#FF0000", false)
        val b = MappedPixel("B", "#0000FF", false)
        val g = arrayOf(arrayOf(a, a), arrayOf(a, a))
        val res = paintSinglePixel(g, 0, 1, b)
        assertTrue(res.hasChange)
        assertEquals("B", res.grid[0][1].key)
        assertEquals("A", res.grid[0][0].key)
    }

    @Test
    fun paintSinglePixel_eraseSetsTransparent() {
        val a = MappedPixel("A", "#FF0000", false)
        val g = arrayOf(arrayOf(a))
        val res = paintSinglePixel(g, 0, 0, transparentColorData)
        assertTrue(res.hasChange)
        assertTrue(res.grid[0][0].isExternal)
    }

    @Test
    fun recalculateStats_ignoresExternalAndTransparent() {
        val a = MappedPixel("A", "#FF0000", false)
        val b = MappedPixel("B", "#0000FF", false)
        val ext = MappedPixel("A", "#FF0000", true)
        val g = arrayOf(
            arrayOf(a, a, b),
            arrayOf(ext, transparentColorData, b)
        )
        val stats = recalculateColorStats(g)
        assertEquals(4, stats.totalCount)
        assertEquals(2, stats.counts["#FF0000"])
        assertEquals(2, stats.counts["#0000FF"])
        assertFalse(stats.counts.containsKey("#FFFFFF"))
    }
}
