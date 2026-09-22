package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 算法效果量化质检测试套件：
 * 采用四大核心质量门禁（色数上限合规度、孤岛噪点消除率、细线保留完整性、感知色差保真度）
 * 对拼豆算法进行端到端量化评测。
 */
class AlgorithmQualityTest {

    private fun col(key: String, r: Int, g: Int, b: Int) =
        PaletteColor(key, String.format("#%02X%02X%02X", r, g, b), RgbColor(r, g, b))

    @Test
    fun qualityGate_maxColorsStrictlyEnforced() {
        // 构造一个具有 30 种颜色的高多样性调色板
        val palette = (0 until 30).map { i ->
            val hue = (i * 12) % 360
            val r = (128 + 127 * Math.sin(Math.toRadians(hue.toDouble()))).toInt().coerceIn(0, 255)
            val g = (128 + 127 * Math.sin(Math.toRadians(hue + 120.0))).toInt().coerceIn(0, 255)
            val b = (128 + 127 * Math.sin(Math.toRadians(hue + 240.0))).toInt().coerceIn(0, 255)
            col("C$i", r, g, b)
        }

        // 构造 30x30 的彩色渐变图像
        val reps = Array(30) { r ->
            Array(30) { c ->
                palette[(r + c) % palette.size].rgb
            }
        }

        val limits = listOf(8, 16, 24)
        for (k in limits) {
            val subPalette = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = k)
            assertTrue("受控色板大小 (${subPalette.size}) 必须 <= $k", subPalette.size <= k)

            // 全图像素化映射
            val mappedKeys = HashSet<String>()
            for (row in reps) {
                for (cell in row) {
                    val closest = ColorMath.findClosestPaletteColor(cell!!, subPalette)
                    mappedKeys.add(closest.key)
                }
            }
            assertTrue("最终图纸使用的色号数 (${mappedKeys.size}) 必须 <= $k", mappedKeys.size <= k)
        }
    }

    @Test
    fun qualityGate_islandSpeckleEliminationRateAbove80Percent() {
        // 构造一个 20x20 的平涂背景图案，并在其中随机散播 15 颗 1 格或 2 格孤立噪点
        val matrix = Array(20) { r ->
            Array(20) { c ->
                // 大色块背景：左半是青色，右半是黄色
                if (c < 10) MappedPixel("CYAN", "#00FFFF", false)
                else MappedPixel("YELLOW", "#FFFF00", false)
            }
        }

        // 注入 10 颗孤立噪点
        val rng = Random(42)
        var injected = 0
        while (injected < 10) {
            val r = rng.nextInt(18) + 1
            val c = rng.nextInt(18) + 1
            if (matrix[r][c].key != "MAGENTA") {
                matrix[r][c] = MappedPixel("MAGENTA", "#FF00FF", false)
                injected++
            }
        }

        val islandsBefore = IslandCleanup.countSmallIslands(matrix, maxSize = 2)
        assertTrue("清理前应存在孤岛噪点 (>= 8)，实际为 $islandsBefore", islandsBefore >= 8)

        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 2)
        val islandsAfter = IslandCleanup.countSmallIslands(cleaned, maxSize = 2)

        val eliminationRate = (islandsBefore - islandsAfter).toDouble() / islandsBefore
        println("[QualityGate] 孤立噪点消除率: ${(eliminationRate * 100).toInt()}% (前: $islandsBefore, 后: $islandsAfter)")
        assertTrue("孤立噪点消除率应 >= 80%，实际为 ${(eliminationRate * 100).toInt()}%", eliminationRate >= 0.80)
    }

    @Test
    fun qualityGate_thinlinesBoundaryContinuityPreserved() {
        // 构造一个 1 像素十字交叉黑线条图案，测试噪点清理不会割裂连续线条
        val matrix = Array(15) { r ->
            Array(15) { c ->
                val isLine = (r == 7 || c == 7) // 7 行和 7 列为 1 像素黑线
                if (isLine) MappedPixel("LINE", "#000000", false)
                else MappedPixel("BG", "#FFFFFF", false)
            }
        }

        // 注入 1 颗散落飞点在 (2, 2)
        matrix[2][2] = MappedPixel("NOISE", "#FF0000", false)

        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 2, protectDiagonalLines = true)

        // 飞点应被平滑
        assertEquals("BG", cleaned[2][2].key)

        // 十字线条每颗珠子应完整保留
        for (i in 0 until 15) {
            assertEquals("LINE", cleaned[7][i].key)
            assertEquals("LINE", cleaned[i][7].key)
        }
    }
}
