package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.BeadBrand
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSubstitutionTest {

    private val white = PaletteColor("S01", "#EAEEF3", RgbColor(234, 238, 243), "White", BeadBrand.ARTKAL_S)
    private val burningSand = PaletteColor("S02", "#EE927C", RgbColor(238, 146, 124), "Burning Sand", BeadBrand.ARTKAL_S)
    private val black = PaletteColor("S13", "#292A2B", RgbColor(41, 42, 43), "Black", BeadBrand.ARTKAL_S)
    private val pepper = PaletteColor("S158", "#3A3E42", RgbColor(58, 62, 66), "Pepper", BeadBrand.ARTKAL_S)
    private val red = PaletteColor("S05", "#CB3531", RgbColor(203, 53, 49), "Tall Poppy", BeadBrand.ARTKAL_S)

    @Test
    fun findBestSubstitute_identicalColorGivesZeroDeltaE() {
        val candidates = listOf(white, burningSand, black)
        // 寻找 white 的自身平替（当候选集中只有 white 和其他时，若候选集只有1个则返回自身）
        val sub = ColorSubstitution.findBestSubstitute(white.hex, white.rgb, listOf(white))
        assertNotNull(sub)
        assertEquals("S01", sub!!.substitute.key)
        assertEquals(0.0, sub.deltaE, 0.001)
        assertEquals(SubstitutionRating.PERFECT, sub.rating)
        assertEquals(5, sub.rating.stars)
    }

    @Test
    fun findBestSubstitute_findsClosestCandidate() {
        // black (#292A2B) 缺货，候选集中有 white、burningSand、pepper (#3A3E42)
        // pepper (暗灰/胡椒黑) 是黑色的最近平替
        val inStock = listOf(white, burningSand, pepper)
        val sub = ColorSubstitution.findBestSubstitute(black.hex, black.rgb, inStock)
        assertNotNull(sub)
        assertEquals("S158", sub!!.substitute.key)
        // 黑与胡椒黑色差很小，应当达到 PERFECT 或 GOOD 评级
        assertTrue(sub.deltaE < 8.0)
        assertTrue(sub.rating == SubstitutionRating.PERFECT || sub.rating == SubstitutionRating.GOOD)
    }

    @Test
    fun findBestSubstitute_emptyCandidatesReturnsNull() {
        val sub = ColorSubstitution.findBestSubstitute(black.hex, black.rgb, emptyList())
        assertNull(sub)
    }

    @Test
    fun substituteColorInCells_replacesOnlyTargetColor() {
        val pxBlack = MappedPixel("S13", "#292A2B", false)
        val pxWhite = MappedPixel("S01", "#EAEEF3", false)
        val pxErase = MappedPixel(TRANSPARENT_KEY, "#FFFFFF", true)

        val cells = arrayOf(
            arrayOf(pxBlack, pxWhite),
            arrayOf(pxErase, pxBlack)
        )

        val updated = ColorSubstitution.substituteColorInCells(cells, black.hex, pepper)

        // 验证 S13 被替换为 S158
        assertEquals("S158", updated[0][0].key)
        assertEquals("#3A3E42", updated[0][0].colorHex)
        assertEquals("S158", updated[1][1].key)
        assertEquals("#3A3E42", updated[1][1].colorHex)

        // 验证 S01 与透明格子保持不变
        assertEquals("S01", updated[0][1].key)
        assertEquals("#EAEEF3", updated[0][1].colorHex)
        assertEquals(TRANSPARENT_KEY, updated[1][0].key)
        assertTrue(updated[1][0].isExternal)
    }

    @Test
    fun analyzeMissingColors_identifiesShortagesAndRanksByCount() {
        val pxRed = MappedPixel("S05", "#CB3531", false)
        val pxBlack = MappedPixel("S13", "#292A2B", false)
        val pxWhite = MappedPixel("S01", "#EAEEF3", false)

        val cells = arrayOf(
            arrayOf(pxRed, pxRed, pxBlack),
            arrayOf(pxRed, pxWhite, pxBlack)
        )
        // 红: 3格, 黑: 2格, 白: 1格
        // 假设库存只有 白:
        val inStockHexes = setOf(white.hex.uppercase())
        val inStockPalette = listOf(white)
        val paletteLookup = listOf(white, burningSand, black, red).associateBy { it.hex.uppercase() }

        val missing = ColorSubstitution.analyzeMissingColors(
            cells = cells,
            inStockHexes = inStockHexes,
            inStockPalette = inStockPalette,
            paletteLookup = paletteLookup
        )

        assertEquals(2, missing.size)
        // 排序：所需数量多 (红=3) 在前，其次是 (黑=2)
        assertEquals("S05", missing[0].key)
        assertEquals(3, missing[0].requiredCount)
        assertEquals("S13", missing[1].key)
        assertEquals(2, missing[1].requiredCount)
        // 检查平替推荐均指向库存中唯一的白
        assertEquals("S01", missing[0].substitute?.substitute?.key)
        assertEquals("S01", missing[1].substitute?.substitute?.key)
    }
}
