package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorQuantizerTest {

    private fun col(key: String, r: Int, g: Int, b: Int) =
        PaletteColor(key, String.format("#%02X%02X%02X", r, g, b), RgbColor(r, g, b))

    @Test
    fun unlimitedMaxColorsReturnsFullPalette() {
        val palette = listOf(
            col("A01", 255, 0, 0),
            col("A02", 0, 255, 0),
            col("A03", 0, 0, 255)
        )
        val reps = Array(2) { arrayOfNulls<RgbColor>(2) }
        val res = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = 0)
        assertEquals(palette, res)

        val resNegative = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = -1)
        assertEquals(palette, resNegative)
    }

    @Test
    fun controlledPaletteStrictlyLimitsSize() {
        // 创建 10 种不同色相的调色板
        val palette = listOf(
            col("C1", 255, 0, 0),
            col("C2", 0, 255, 0),
            col("C3", 0, 0, 255),
            col("C4", 255, 255, 0),
            col("C5", 255, 0, 255),
            col("C6", 0, 255, 255),
            col("C7", 128, 0, 0),
            col("C8", 0, 128, 0),
            col("C9", 0, 0, 128),
            col("C10", 128, 128, 128)
        )

        // 生成包含所有 10 种颜色的 reps 网格 (10x10)
        val reps = Array(10) { r ->
            Array(10) { c ->
                palette[(r * 10 + c) % palette.size].rgb
            }
        }

        // 限制在 4 色
        val result4 = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = 4)
        assertTrue("色数应 <= 4，实际为 ${result4.size}", result4.size <= 4)
        assertTrue("色数应 >= 1", result4.isNotEmpty())

        // 限制在 6 色
        val result6 = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = 6)
        assertTrue("色数应 <= 6，实际为 ${result6.size}", result6.size <= 6)
    }

    @Test
    fun keyAccentColorsPreservedDespiteLowFrequency() {
        // 测试关键特征色保留：背景是大面积白色 (100颗)，有少量的纯红(3颗)和纯黑(5颗)
        val white = col("WHITE", 255, 255, 255)
        val red = col("RED", 255, 0, 0)
        val black = col("BLACK", 0, 0, 0)
        val blue = col("BLUE", 0, 0, 255)
        val green = col("GREEN", 0, 255, 0)
        val palette = listOf(white, red, black, blue, green)

        val reps = Array(10) { r ->
            Array(10) { c ->
                when {
                    r == 0 && c < 3 -> RgbColor(255, 0, 0)   // 少量红色唇色
                    r == 1 && c < 5 -> RgbColor(0, 0, 0)     // 少量黑色线条
                    else -> RgbColor(255, 255, 255)          // 大面积白色
                }
            }
        }

        // 限制为 3 色
        val result = ColorQuantizer.computeControlledPalette(reps, palette, maxColors = 3)
        assertEquals(3, result.size)
        val keys = result.map { it.key }.toSet()
        assertTrue("红色特征色应被中位切割保留", "RED" in keys)
        assertTrue("黑色特征色应被中位切割保留", "BLACK" in keys)
        assertTrue("白色主背景应被保留", "WHITE" in keys)
    }
}
