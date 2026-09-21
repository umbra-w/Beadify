package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FS 抖动量化：输出只含色板色、确定性、中间调混合、透明不扩散。 */
class DitheringTest {

    private val black = PaletteColor("A01", "#000000", RgbColor(0, 0, 0))
    private val white = PaletteColor("T01", "#FFFFFF", RgbColor(255, 255, 255))

    private fun repsOf(n: Int, m: Int, color: RgbColor): Array<Array<RgbColor?>> =
        Array(m) { Array<RgbColor?>(n) { color } }

    @Test
    fun outputOnlyContainsPaletteColors() {
        val palette = listOf(black, white, PaletteColor("M15", "#808080", RgbColor(128, 128, 128)))
        val out = quantizeWithDithering(repsOf(8, 8, RgbColor(90, 90, 90)), palette, black)
        for (row in out) for (cell in row) {
            assertTrue(
                "出现色板外颜色 ${cell.colorHex}",
                palette.any { it.hex.equals(cell.colorHex, ignoreCase = true) }
            )
        }
    }

    @Test
    fun deterministicForSameInput() {
        val palette = listOf(black, white)
        val reps = repsOf(10, 10, RgbColor(120, 120, 120))
        assertEquals(
            quantizeWithDithering(reps, palette, black).contentDeepToString(),
            quantizeWithDithering(reps, palette, black).contentDeepToString()
        )
    }

    @Test
    fun midToneMixesBothColorsWhilePlainMappingPicksOne() {
        // 中间灰在黑白双色板下：无抖动只会得到单色；有抖动应黑白混合
        val palette = listOf(black, white)
        val reps = repsOf(16, 16, RgbColor(128, 128, 128))
        // 无抖动对照：每格独立取最近色
        val plainColors = reps.flatMap { it.toList() }
            .filterNotNull()
            .map { findClosestPaletteColor(it, palette).hex }
            .toSet()
        assertEquals(1, plainColors.size)

        val dithered = quantizeWithDithering(reps, palette, black)
        val ditheredColors = dithered.flatMap { it.toList() }.map { it.colorHex }.toSet()
        assertEquals(2, ditheredColors.size)
    }

    @Test
    fun transparentCellsDoNotPropagateError() {
        val palette = listOf(black, white)
        val reps = arrayOf(
            arrayOf<RgbColor?>(null, RgbColor(255, 255, 255)),
            arrayOf<RgbColor?>(RgbColor(255, 255, 255), RgbColor(255, 255, 255))
        )
        val out = quantizeWithDithering(reps, palette, black)
        // 透明格保持透明，且首行白格不受任何误差影响（误差只能来自左侧/上方）
        assertEquals(TRANSPARENT_KEY, out[0][0].key)
        assertEquals("T01", out[0][1].key)
    }

    @Test
    fun exactPaletteColorHasNoError() {
        val palette = listOf(black, white)
        val out = quantizeWithDithering(repsOf(6, 6, RgbColor(0, 0, 0)), palette, black)
        for (row in out) for (cell in row) {
            assertEquals("A01", cell.key)
        }
    }

    @Test
    fun emptyPaletteFallsBackToErrKey() {
        // 既有语义：空色板时 findClosestPaletteColor 返回 ERR 占位色
        val out = quantizeWithDithering(repsOf(2, 2, RgbColor(10, 10, 10)), emptyList(), black)
        for (row in out) for (cell in row) {
            assertEquals("ERR", cell.key)
        }
    }
}
