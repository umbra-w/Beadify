package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ColorSimilarityMergeTest {

    private val colorRedPrimary = PaletteColor("R01", "#FF0000", RgbColor(255, 0, 0))
    private val colorRedSimilar = PaletteColor("R02", "#FA0505", RgbColor(250, 5, 5))
    private val colorBlueDistant = PaletteColor("B01", "#0000FF", RgbColor(0, 0, 255))
    private val colorGreenDistant = PaletteColor("G01", "#00FF00", RgbColor(0, 255, 0))

    private val testPalette = listOf(colorRedPrimary, colorRedSimilar, colorBlueDistant, colorGreenDistant)

    @Test
    fun thresholdZero_returnsIdenticalGrid() {
        val grid = Array(2) { r ->
            Array(2) { c ->
                if (r == 0) MappedPixel(colorRedPrimary.key, colorRedPrimary.hex)
                else MappedPixel(colorRedSimilar.key, colorRedSimilar.hex)
            }
        }

        val result = ColorQuantizer.mergeSimilarColors(grid, testPalette, threshold = 0)
        assertSame(grid, result)
    }

    @Test
    fun closeColors_mergedToHigherFrequency() {
        // 4x4 网格：12 个 R01，4 个 R02（微弱色差）
        val grid = Array(4) { r ->
            Array(4) { c ->
                if (r < 3) MappedPixel(colorRedPrimary.key, colorRedPrimary.hex)
                else MappedPixel(colorRedSimilar.key, colorRedSimilar.hex)
            }
        }

        val dist = ColorMath.oklabDistance(colorRedPrimary.rgb, colorRedSimilar.rgb)
        // 验证色差较小
        assert(dist in 0.1..15.0) { "Expected small Oklab distance but was $dist" }

        val result = ColorQuantizer.mergeSimilarColors(grid, testPalette, threshold = 15)

        // 所有格子的色号均应被归并为频次更高（12 > 4）的 R01
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                assertEquals("Cell at ($r, $c) should be merged to R01", colorRedPrimary.key, result[r][c].key)
                assertEquals(colorRedPrimary.hex, result[r][c].colorHex)
            }
        }
    }

    @Test
    fun distantColors_notMerged() {
        // 10 个 Red，6 个 Blue
        val grid = Array(4) { r ->
            Array(4) { c ->
                if (r < 2 || (r == 2 && c < 2)) MappedPixel(colorRedPrimary.key, colorRedPrimary.hex)
                else MappedPixel(colorBlueDistant.key, colorBlueDistant.hex)
            }
        }

        val dist = ColorMath.oklabDistance(colorRedPrimary.rgb, colorBlueDistant.rgb)
        assert(dist > 50.0) { "Red and Blue should have large Oklab distance but was $dist" }

        // 即使阈值给到 30，显著不同的颜色也不会被错误合并
        val result = ColorQuantizer.mergeSimilarColors(grid, testPalette, threshold = 30)

        for (r in 0 until 4) {
            for (c in 0 until 4) {
                assertEquals(grid[r][c].key, result[r][c].key)
            }
        }
    }

    @Test
    fun externalAndTransparent_unaffected() {
        val grid = arrayOf(
            arrayOf(
                transparentColorData,
                MappedPixel("EXT", "#DCDCDC", isExternal = true)
            ),
            arrayOf(
                MappedPixel(colorRedSimilar.key, colorRedSimilar.hex),
                MappedPixel(colorRedPrimary.key, colorRedPrimary.hex)
            )
        )

        val result = ColorQuantizer.mergeSimilarColors(grid, testPalette, threshold = 20)

        assertEquals(TRANSPARENT_KEY, result[0][0].key)
        assert(result[0][0].isExternal)
        assertEquals("EXT", result[0][1].key)
        assert(result[0][1].isExternal)

        // 仅非外部网格参与计算与合并
        assertEquals(colorRedPrimary.key, result[1][0].key)
        assertEquals(colorRedPrimary.key, result[1][1].key)
    }

    @Test
    fun multiColorClustering_correctlyAnchorsDominantColors() {
        // 网格包含：
        // 20 个 RedPrimary (高频)
        // 3 个 RedSimilar (微量，应该并入 RedPrimary)
        // 10 个 GreenDistant (次高频，自身为绿系锚点)
        val cells = mutableListOf<MappedPixel>()
        repeat(20) { cells.add(MappedPixel(colorRedPrimary.key, colorRedPrimary.hex)) }
        repeat(3) { cells.add(MappedPixel(colorRedSimilar.key, colorRedSimilar.hex)) }
        repeat(10) { cells.add(MappedPixel(colorGreenDistant.key, colorGreenDistant.hex)) }

        val grid = arrayOf(cells.toTypedArray())
        val result = ColorQuantizer.mergeSimilarColors(grid, testPalette, threshold = 15)

        val keys = result[0].map { it.key }
        val redCount = keys.count { it == colorRedPrimary.key }
        val redSimilarCount = keys.count { it == colorRedSimilar.key }
        val greenCount = keys.count { it == colorGreenDistant.key }

        assertEquals(23, redCount)
        assertEquals(0, redSimilarCount)
        assertEquals(10, greenCount)
    }
}
