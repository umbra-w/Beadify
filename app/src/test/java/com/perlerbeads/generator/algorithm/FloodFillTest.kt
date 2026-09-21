package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodFillTest {

    @Test
    fun removesConnectedBackgroundFromBorder() {
        val a = MappedPixel("A", "#FFFFFF", false)
        val b = MappedPixel("B", "#FF0000", false)
        val g = Array(6) { r -> Array(6) { c -> if (r in 1..2 && c in 1..2) b else a } }
        val res = autoRemoveBackground(g)
        assertEquals(36 - 4, res.removedCount)
        // 所有 A（连通到边界）被置为透明；内部 B 块保留
        assertEquals("ERASE", res.grid[0][0].key)
        assertEquals("B", res.grid[1][1].key)
        assertTrue(res.grid[1][1].isExternal.not())
    }

    @Test
    fun keepsIslandEnclosedByOtherColor() {
        // B 环包围内部 A 岛，岛不与边界连通
        val a = MappedPixel("A", "#FFFFFF", false)
        val b = MappedPixel("B", "#FF0000", false)
        val g = Array(8) { r -> Array(8) { c ->
            when {
                r in 2..4 && c in 2..4 && (r == 2 || r == 4 || c == 2 || c == 4) -> b
                r == 3 && c == 3 -> a
                else -> a
            }
        } }
        val res = autoRemoveBackground(g)
        // 岛保留
        assertEquals("A", res.grid[3][3].key)
        // 外围背景被移除
        assertEquals("ERASE", res.grid[0][0].key)
    }

    @Test
    fun noRemovalWhenEdgeIsSingleEnclosedColor() {
        val a = MappedPixel("A", "#FFFFFF", false)
        // 全部同色，无其他颜色构成“内容”，背景填充会把所有 A 移除
        val g = Array(3) { Array(3) { a } }
        val res = autoRemoveBackground(g)
        assertEquals(9, res.removedCount)
    }
}
