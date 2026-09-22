package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun sameColorGivesZeroDistance() {
        val red = RgbColor(255, 0, 0)
        val d = ColorMath.oklabDistance(red, red)
        assertEquals(0.0, d, 1e-6)
    }

    @Test
    fun blackAndWhiteCoordinates() {
        val blackLab = ColorMath.rgbToOklab(0, 0, 0)
        val whiteLab = ColorMath.rgbToOklab(255, 255, 255)

        assertEquals(0.0, blackLab.l, 1e-4)
        assertEquals(1.0, whiteLab.l, 1e-4)
        // 黑白在 a, b 轴（色度）上应接近 0
        assertEquals(0.0, blackLab.a, 1e-4)
        assertEquals(0.0, blackLab.b, 1e-4)
        assertEquals(0.0, whiteLab.a, 1e-4)
        assertEquals(0.0, whiteLab.b, 1e-4)
    }

    @Test
    fun colorDistanceSymmetry() {
        val c1 = RgbColor(120, 200, 50)
        val c2 = RgbColor(30, 80, 220)
        val d12 = ColorMath.oklabDistance(c1, c2)
        val d21 = ColorMath.oklabDistance(c2, c1)
        assertEquals(d12, d21, 1e-6)
    }

    @Test
    fun closestColorMatchesExactPalette() {
        val red = PaletteColor("RED", "#FF0000", RgbColor(255, 0, 0))
        val green = PaletteColor("GRN", "#00FF00", RgbColor(0, 255, 0))
        val blue = PaletteColor("BLU", "#0000FF", RgbColor(0, 0, 255))
        val palette = listOf(red, green, blue)

        // 稍微偏暗的红仍应匹配到红
        val darkRed = RgbColor(200, 10, 10)
        val match = ColorMath.findClosestPaletteColor(darkRed, palette)
        assertEquals("RED", match.key)

        // 浅蓝匹配蓝
        val lightBlue = RgbColor(80, 120, 240)
        val blueMatch = ColorMath.findClosestPaletteColor(lightBlue, palette)
        assertEquals("BLU", blueMatch.key)
    }

    @Test
    fun blueVioletHueSeparation() {
        // 在 RGB 空间中，深蓝 (0,0,139) 与深紫 (75,0,130) 的欧氏距离容易失真
        // 在 Oklab 空间中色相感知线性度显著优于 RGB，蓝紫差异清晰可辨
        val deepBlue = RgbColor(0, 0, 180)
        val deepPurple = RgbColor(120, 0, 180)
        val navy = RgbColor(0, 0, 220)

        val dBlueToNavy = ColorMath.oklabDistance(deepBlue, navy)
        val dBlueToPurple = ColorMath.oklabDistance(deepBlue, deepPurple)

        // 深蓝到深海军蓝同色系，距离应显著小于深蓝到紫色的距离
        assertTrue("同色系深蓝距离 ($dBlueToNavy) 应小于跨色相紫色 ($dBlueToPurple)", dBlueToNavy < dBlueToPurple)
    }
}
