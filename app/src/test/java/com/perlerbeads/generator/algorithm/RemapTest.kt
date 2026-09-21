package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemapTest {

    @Test
    fun excludeRemapsToClosestAmongInitialColors() {
        val red = MappedPixel("R", "#FF0000", false)
        val darkRed = MappedPixel("DR", "#CC0000", false)
        val blue = MappedPixel("B", "#0000FF", false)
        val g = arrayOf(
            arrayOf(red, darkRed),
            arrayOf(blue, red)
        )
        val fullPalette = listOf(
            PaletteColor("#FF0000", "#FF0000", RgbColor(255, 0, 0)),
            PaletteColor("#CC0000", "#CC0000", RgbColor(204, 0, 0)),
            PaletteColor("#0000FF", "#0000FF", RgbColor(0, 0, 255))
        )
        val initial = setOf("#FF0000", "#CC0000", "#0000FF")
        val res = excludeColor(g, "#FF0000", initial, fullPalette, emptySet())
        assertTrue(res.success)
        assertEquals(2, res.remappedCount)
        // 两个红色单元重映射到最近的 #CC0000
        assertEquals("#CC0000", res.grid[0][0].colorHex.uppercase())
        assertEquals("#CC0000", res.grid[1][1].colorHex.uppercase())
        // 其他颜色不受影响
        assertEquals("#CC0000", res.grid[0][1].colorHex.uppercase())
        assertEquals("#0000FF", res.grid[1][0].colorHex.uppercase())
    }

    @Test
    fun excludeBlockedWhenNoOtherInitialColorAvailable() {
        val red = MappedPixel("R", "#FF0000", false)
        val g = arrayOf(arrayOf(red, red))
        val fullPalette = listOf(
            PaletteColor("#FF0000", "#FF0000", RgbColor(255, 0, 0))
        )
        val initial = setOf("#FF0000")
        val res = excludeColor(g, "#FF0000", initial, fullPalette, emptySet())
        assertFalse(res.success)
        assertEquals(0, res.remappedCount)
    }

    @Test
    fun excludeRespectsOtherExclusions() {
        val red = MappedPixel("R", "#FF0000", false)
        val green = MappedPixel("G", "#00FF00", false)
        val g = arrayOf(arrayOf(red, green))
        val fullPalette = listOf(
            PaletteColor("#FF0000", "#FF0000", RgbColor(255, 0, 0)),
            PaletteColor("#00FF00", "#00FF00", RgbColor(0, 255, 0))
        )
        val initial = setOf("#FF0000", "#00FF00")
        // 已排除 #00FF00 后再排除 #FF0000 → 无可用目标 → 阻止
        val res = excludeColor(g, "#FF0000", initial, fullPalette, setOf("#00FF00"))
        assertFalse(res.success)
    }
}
