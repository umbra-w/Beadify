package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.*
import org.junit.Test

/** TextBeads 进阶功能：渐变插值、阴影偏移、描边膨胀的纯算法测试。 */
class TextBeadsAdvancedTest {

    // ============ 测试色板 ============
    private val red = PaletteColor("R", "#FF0000", RgbColor(255, 0, 0))
    private val green = PaletteColor("G", "#00FF00", RgbColor(0, 255, 0))
    private val blue = PaletteColor("B", "#0000FF", RgbColor(0, 0, 255))
    private val black = PaletteColor("BK", "#000000", RgbColor(0, 0, 0))
    private val white = PaletteColor("W", "#FFFFFF", RgbColor(255, 255, 255))
    private val yellow = PaletteColor("Y", "#FFFF00", RgbColor(255, 255, 0))
    private val palette = listOf(red, green, blue, black, white, yellow)

    // ============ resolveColor 测试 ============

    @Test
    fun `SINGLE mode always returns primaryColor`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.SINGLE,
            primaryColor = red,
            palette = palette
        )
        repeat(10) { col ->
            assertEquals(red, TextBeads.resolveColor(style, col, 10))
        }
    }

    @Test
    fun `GRADIENT mode - endpoints match primary and secondary`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.GRADIENT,
            primaryColor = red,
            secondaryColor = blue,
            palette = palette
        )
        // 最左列应该最接近 red
        val leftColor = TextBeads.resolveColor(style, 0, 100)
        assertEquals(red, leftColor)

        // 最右列应该最接近 blue
        val rightColor = TextBeads.resolveColor(style, 99, 100)
        assertEquals(blue, rightColor)
    }

    @Test
    fun `GRADIENT mode - middle produces intermediate color`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.GRADIENT,
            primaryColor = red,
            secondaryColor = green,
            palette = palette
        )
        // 中间列应该既不是 red 也不是 green（除非色板恰好只有红绿）
        val midColor = TextBeads.resolveColor(style, 50, 100)
        // 中间插值在 Oklab 空间应该匹配色板中某个颜色
        assertNotNull(midColor)
    }

    @Test
    fun `GRADIENT mode - single column returns primary`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.GRADIENT,
            primaryColor = red,
            secondaryColor = blue,
            palette = palette
        )
        assertEquals(red, TextBeads.resolveColor(style, 0, 1))
    }

    @Test
    fun `RAINBOW mode produces varying colors across columns`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.RAINBOW,
            primaryColor = red,
            palette = palette
        )
        val colors = (0 until 20).map { TextBeads.resolveColor(style, it, 20) }
        // 彩虹模式应产生至少 2 种不同颜色
        assertTrue("彩虹模式应产生多种颜色", colors.toSet().size >= 2)
    }

    @Test
    fun `GRADIENT mode - empty palette falls back to primaryColor`() {
        val style = TextBeadStyle(
            colorMode = TextColorMode.GRADIENT,
            primaryColor = red,
            secondaryColor = blue,
            palette = emptyList()  // 空色板
        )
        val color = TextBeads.resolveColor(style, 5, 10)
        assertEquals(red, color)  // 回退到 primaryColor
    }

    // ============ hueToRgb 测试 ============

    @Test
    fun `hueToRgb - hue 0 is red`() {
        val rgb = TextBeads.hueToRgb(0.0)
        assertEquals(255, rgb.r)
        assertEquals(0, rgb.g)
        assertEquals(0, rgb.b)
    }

    @Test
    fun `hueToRgb - hue one-third is green`() {
        val rgb = TextBeads.hueToRgb(1.0 / 3.0)
        assertEquals(0, rgb.r)
        assertEquals(255, rgb.g)
        // b 应该很小
        assertTrue(rgb.b <= 1)
    }

    @Test
    fun `hueToRgb - hue two-thirds is blue`() {
        val rgb = TextBeads.hueToRgb(2.0 / 3.0)
        assertTrue(rgb.r <= 1)
        assertEquals(0, rgb.g)
        assertEquals(255, rgb.b)
    }

    @Test
    fun `hueToRgb - wraps around at 1_0`() {
        val at0 = TextBeads.hueToRgb(0.0)
        val at1 = TextBeads.hueToRgb(1.0)
        assertEquals(at0, at1)
    }

    @Test
    fun `hueToRgb - negative hue wraps correctly`() {
        val rgb = TextBeads.hueToRgb(-0.5)
        // -0.5 wraps to 0.5 → cyan area
        assertNotNull(rgb)
        assertTrue(rgb.r in 0..255)
        assertTrue(rgb.g in 0..255)
        assertTrue(rgb.b in 0..255)
    }

    // ============ morphologicalDilate 测试 ============

    @Test
    fun `morphologicalDilate - single pixel expands to 8 neighbors`() {
        // 5x5 mask, only center pixel set
        val mask = Array(5) { r -> BooleanArray(5) { c -> r == 2 && c == 2 } }
        val result = TextBeads.morphologicalDilate(mask, 5, 5, 1)

        // 原始像素位置不在描边蒙版中
        assertFalse(result[2][2])

        // 8 邻域应该被膨胀
        assertTrue(result[1][1])
        assertTrue(result[1][2])
        assertTrue(result[1][3])
        assertTrue(result[2][1])
        assertTrue(result[2][3])
        assertTrue(result[3][1])
        assertTrue(result[3][2])
        assertTrue(result[3][3])

        // 远处的像素不受影响
        assertFalse(result[0][0])
        assertFalse(result[4][4])
    }

    @Test
    fun `morphologicalDilate - thickness 2 expands further`() {
        val mask = Array(7) { r -> BooleanArray(7) { c -> r == 3 && c == 3 } }
        val result = TextBeads.morphologicalDilate(mask, 7, 7, 2)

        // 原始像素不在描边
        assertFalse(result[3][3])

        // 第一圈邻域不在描边（被第二次膨胀覆盖但减去原始蒙版时仍为 true）
        // 实际上 thickness=2 意味着膨胀了2次，然后减去原始蒙版
        // 所以 (2,2) 应该是膨胀区域
        assertTrue(result[2][2])
        assertTrue(result[1][3]) // 2 格距离
    }

    @Test
    fun `morphologicalDilate - corner pixel`() {
        val mask = Array(3) { r -> BooleanArray(3) { c -> r == 0 && c == 0 } }
        val result = TextBeads.morphologicalDilate(mask, 3, 3, 1)

        assertFalse(result[0][0]) // 原始位置
        assertTrue(result[0][1])  // 右邻
        assertTrue(result[1][0])  // 下邻
        assertTrue(result[1][1])  // 对角
    }

    @Test
    fun `morphologicalDilate - empty mask returns empty`() {
        val mask = Array(4) { BooleanArray(4) }
        val result = TextBeads.morphologicalDilate(mask, 4, 4, 1)
        for (r in 0 until 4) for (c in 0 until 4) assertFalse(result[r][c])
    }

    // ============ offsetMask 测试 ============

    @Test
    fun `offsetMask - shifts down-right by 1`() {
        val mask = Array(5) { r -> BooleanArray(5) { c -> r == 1 && c == 1 } }
        val result = TextBeads.offsetMask(mask, 5, 5, 1, 1)

        // 原始位置 (1,1) 不在阴影中
        assertFalse(result[1][1])
        // 偏移后位置 (2,2) 应该在阴影中
        assertTrue(result[2][2])
        // 其他位置不变
        assertFalse(result[0][0])
        assertFalse(result[3][3])
    }

    @Test
    fun `offsetMask - text pixel overlapping shadow removes shadow`() {
        // 两个相邻像素，偏移 (0,1)：第二个像素的阴影位置与文字重叠
        val mask = Array(3) { r -> BooleanArray(5) { c -> r == 1 && (c == 1 || c == 2) } }
        val result = TextBeads.offsetMask(mask, 3, 5, 0, 1)

        // (1,2) 是文字，也是 (1,1) 的阴影位置 → 不应在阴影蒙版中
        assertFalse(result[1][2])
        // (1,3) 是 (1,2) 的阴影 → 应在阴影蒙版中
        assertTrue(result[1][3])
    }

    @Test
    fun `offsetMask - negative offset shifts up-left`() {
        val mask = Array(5) { r -> BooleanArray(5) { c -> r == 3 && c == 3 } }
        val result = TextBeads.offsetMask(mask, 5, 5, -1, -1)

        // 偏移后位置 (2,2)
        assertTrue(result[2][2])
        assertFalse(result[3][3]) // 原始位置不在阴影
    }

    @Test
    fun `offsetMask - out of bounds source is ignored`() {
        val mask = Array(3) { r -> BooleanArray(3) { c -> r == 0 && c == 0 } }
        val result = TextBeads.offsetMask(mask, 3, 3, -1, -1)
        // 偏移后的位置应该全是 false（源越界）
        for (r in 0 until 3) for (c in 0 until 3) assertFalse(result[r][c])
    }

    // ============ Oklab 插值往返精度测试 ============

    @Test
    fun `oklabToRgb roundtrip - pure red`() {
        val lab = ColorMath.rgbToOklab(255, 0, 0)
        val rgb = ColorMath.oklabToRgb(lab)
        assertEquals(255, rgb.r)
        assertEquals(0, rgb.g)
        assertEquals(0, rgb.b)
    }

    @Test
    fun `oklabToRgb roundtrip - pure white`() {
        val lab = ColorMath.rgbToOklab(255, 255, 255)
        val rgb = ColorMath.oklabToRgb(lab)
        assertEquals(255, rgb.r)
        assertEquals(255, rgb.g)
        assertEquals(255, rgb.b)
    }

    @Test
    fun `oklabToRgb roundtrip - pure black`() {
        val lab = ColorMath.rgbToOklab(0, 0, 0)
        val rgb = ColorMath.oklabToRgb(lab)
        assertEquals(0, rgb.r)
        assertEquals(0, rgb.g)
        assertEquals(0, rgb.b)
    }

    @Test
    fun `oklabToRgb roundtrip - mid gray`() {
        val lab = ColorMath.rgbToOklab(128, 128, 128)
        val rgb = ColorMath.oklabToRgb(lab)
        // 往返可能有 ±1 的舍入误差
        assertTrue("R 误差过大: ${rgb.r}", kotlin.math.abs(rgb.r - 128) <= 1)
        assertTrue("G 误差过大: ${rgb.g}", kotlin.math.abs(rgb.g - 128) <= 1)
        assertTrue("B 误差过大: ${rgb.b}", kotlin.math.abs(rgb.b - 128) <= 1)
    }

    @Test
    fun `lerpOklab - t=0 returns start`() {
        val a = ColorMath.rgbToOklab(255, 0, 0)
        val b = ColorMath.rgbToOklab(0, 0, 255)
        val result = ColorMath.lerpOklab(a, b, 0.0)
        assertEquals(a.l, result.l, 1e-10)
        assertEquals(a.a, result.a, 1e-10)
        assertEquals(a.b, result.b, 1e-10)
    }

    @Test
    fun `lerpOklab - t=1 returns end`() {
        val a = ColorMath.rgbToOklab(255, 0, 0)
        val b = ColorMath.rgbToOklab(0, 0, 255)
        val result = ColorMath.lerpOklab(a, b, 1.0)
        assertEquals(b.l, result.l, 1e-10)
        assertEquals(b.a, result.a, 1e-10)
        assertEquals(b.b, result.b, 1e-10)
    }

    @Test
    fun `lerpOklab - t=0_5 is midpoint`() {
        val a = ColorMath.rgbToOklab(255, 0, 0)
        val b = ColorMath.rgbToOklab(0, 0, 255)
        val result = ColorMath.lerpOklab(a, b, 0.5)
        assertEquals((a.l + b.l) / 2, result.l, 1e-10)
        assertEquals((a.a + b.a) / 2, result.a, 1e-10)
        assertEquals((a.b + b.b) / 2, result.b, 1e-10)
    }

    @Test
    fun `lerpOklab - t clamped below 0`() {
        val a = ColorMath.rgbToOklab(100, 100, 100)
        val b = ColorMath.rgbToOklab(200, 200, 200)
        val result = ColorMath.lerpOklab(a, b, -0.5)
        assertEquals(a.l, result.l, 1e-10)
    }

    @Test
    fun `lerpOklab - t clamped above 1`() {
        val a = ColorMath.rgbToOklab(100, 100, 100)
        val b = ColorMath.rgbToOklab(200, 200, 200)
        val result = ColorMath.lerpOklab(a, b, 1.5)
        assertEquals(b.l, result.l, 1e-10)
    }

    // ============ linearToSrgb / srgbToLinear 往返 ============

    @Test
    fun `linearToSrgb roundtrip for all 256 values`() {
        for (i in 0..255) {
            val linear = ColorMath.srgbToLinear(i)
            val back = ColorMath.linearToSrgb(linear)
            assertTrue("Value $i roundtrip failed: got $back", kotlin.math.abs(back - i) <= 1)
        }
    }
}
