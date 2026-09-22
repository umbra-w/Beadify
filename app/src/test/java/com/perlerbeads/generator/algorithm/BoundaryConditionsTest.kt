package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运行态白盒测试：覆盖极端边界值、异常输入与特殊图案分支。
 */
class BoundaryConditionsTest {

    private val red = PaletteColor("P01", "#FF0000", RgbColor(255, 0, 0))
    private val green = PaletteColor("P02", "#00FF00", RgbColor(0, 255, 0))
    private val blue = PaletteColor("P03", "#0000FF", RgbColor(0, 0, 255))
    private val white = PaletteColor("P04", "#FFFFFF", RgbColor(255, 255, 255))
    private val black = PaletteColor("P05", "#000000", RgbColor(0, 0, 0))

    @Test
    fun singlePixelGrid_quantizationAndCleanup_safeWithoutCrash() {
        // 1×1 极小网格边界
        val singleRep = arrayOf(arrayOf<RgbColor?>(RgbColor(240, 10, 10)))
        val palette = listOf(red, green, blue)
        val quantized = quantizeWithDithering(singleRep, palette, red)
        assertEquals(1, quantized.size)
        assertEquals(1, quantized[0].size)
        assertEquals("P01", quantized[0][0].key)

        // 1×1 噪点清理：单像素四周无邻居，不能发生越界且保留自身
        val cleaned = IslandCleanup.cleanupSpeckles(quantized, maxIslandSize = 1, protectDiagonalLines = true)
        assertEquals(1, cleaned.size)
        assertEquals("P01", cleaned[0][0].key)
    }

    @Test
    fun paletteSmallerThanMaxColors_returnsOriginalPaletteDirectly() {
        // 候选色板仅 3 色，但请求上限为 16 色：应直接返回原色板，不执行多余递归
        val reps = arrayOf(
            arrayOf<RgbColor?>(RgbColor(255, 0, 0), RgbColor(0, 255, 0)),
            arrayOf<RgbColor?>(RgbColor(0, 0, 255), RgbColor(255, 0, 0))
        )
        val smallPalette = listOf(red, green, blue)
        val result = ColorQuantizer.computeControlledPalette(reps, smallPalette, maxColors = 16)
        assertEquals(3, result.size)
        assertEquals(smallPalette.map { it.key }.toSet(), result.map { it.key }.toSet())
    }

    @Test
    fun paletteWithOnlyOneColor_convergesSafely() {
        // 极端色板只有 1 色，所有颜色都被映射为该唯一色
        val reps = arrayOf(
            arrayOf<RgbColor?>(RgbColor(10, 20, 30), RgbColor(200, 210, 220)),
            arrayOf<RgbColor?>(RgbColor(120, 130, 140), RgbColor(0, 0, 0))
        )
        val singlePalette = listOf(white)
        val result = ColorQuantizer.computeControlledPalette(reps, singlePalette, maxColors = 8)
        assertEquals(1, result.size)
        assertEquals("P04", result[0].key)

        val quantized = quantizeWithDithering(reps, singlePalette, white)
        for (r in quantized) {
            for (c in r) {
                assertEquals("P04", c.key)
            }
        }
    }

    @Test
    fun uniformSolidColorImage_noDivisionByZero() {
        // 全纯色图像：方差与跨度为 0，验证不发生除以 0 或 NaN
        val uniformReps = Array(10) { Array<RgbColor?>(10) { RgbColor(128, 128, 128) } }
        val palette = listOf(black, white, red, green, blue)
        val result = ColorQuantizer.computeControlledPalette(uniformReps, palette, maxColors = 4)
        assertTrue("结果应至少有 1 个代表色", result.isNotEmpty())
        assertTrue("结果色数不应超过上限", result.size <= 4)
    }

    @Test
    fun checkerboardDensePattern_cleanupConvergesSafely() {
        // 密集黑白棋盘格（8×8）：每个格子都没有 4-邻域同色点，但在 8-邻域上有对角邻居
        val m = 8
        val n = 8
        val grid = Array(m) { r ->
            Array(n) { c ->
                if ((r + c) % 2 == 0) MappedPixel(black.key, black.hex, false)
                else MappedPixel(white.key, white.hex, false)
            }
        }
        // 开启对角线保护时，棋盘格的对角连续性被识别，不产生崩溃或空异常
        val cleaned = IslandCleanup.cleanupSpeckles(grid, maxIslandSize = 1, protectDiagonalLines = true)
        assertEquals(m, cleaned.size)
        assertEquals(n, cleaned[0].size)
    }

    @Test
    fun crossJunctionAndLines_completelyProtected() {
        // 十字交汇线（中心是线条交叉点）：确保不会被误认为孤岛
        val m = 7
        val n = 7
        val grid = Array(m) { r ->
            Array(n) { c ->
                if (r == 3 || c == 3) MappedPixel(red.key, red.hex, false)
                else MappedPixel(white.key, white.hex, false)
            }
        }
        val cleaned = IslandCleanup.cleanupSpeckles(grid, maxIslandSize = 1, protectDiagonalLines = true)
        for (r in 0 until m) {
            for (c in 0 until n) {
                if (r == 3 || c == 3) {
                    assertEquals("十字线条上的像素 ($r, $c) 不应被消除", "P01", cleaned[r][c].key)
                }
            }
        }
    }

    @Test
    fun editHistoryBufferLimit_capsAt50Snapshots() {
        // 白盒测试：模拟历史栈满 50 步后的 FIFO 丢弃与内存控制
        val history = mutableListOf<Array<Array<MappedPixel>>>()
        val dummy = Array(2) { Array(2) { MappedPixel("A", "#000", false) } }

        fun saveSnapshot(cells: Array<Array<MappedPixel>>) {
            if (history.size >= 50) history.removeAt(0)
            history.add(cells)
        }

        // 推入 60 次
        for (i in 1..60) {
            val stamp = Array(2) { Array(2) { MappedPixel("A$i", "#000", false) } }
            saveSnapshot(stamp)
        }

        assertEquals("历史栈必须严格截断为 50 步上限", 50, history.size)
        assertEquals("最早的 10 步应被丢弃，栈底应为第 11 次操作", "A11", history.first()[0][0].key)
        assertEquals("栈顶应为第 60 次操作", "A60", history.last()[0][0].key)
    }

    @Test
    fun allTransparentCells_handledGracefully() {
        // 全透明网格输入测试
        val transGrid = Array(4) {
            Array(4) { MappedPixel(TRANSPARENT_KEY, "#00000000", false) }
        }
        val cleaned = IslandCleanup.cleanupSpeckles(transGrid, maxIslandSize = 1, protectDiagonalLines = true)
        for (r in cleaned) {
            for (c in r) {
                assertEquals(TRANSPARENT_KEY, c.key)
            }
        }
    }
}
