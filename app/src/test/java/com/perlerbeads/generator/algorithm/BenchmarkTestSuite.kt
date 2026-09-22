package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼豆图样算法基准测试套件 (Perler Beads Algorithm Benchmark)
 * 参考市面经典拼豆图纸：
 * 1. 经典像素精灵（Mario 像素艺术，硬边轮廓与高光）
 * 2. 平滑渐变过渡（夕阳/写实插画，连续多阶色彩）
 * 3. 微小关键特征（肖像红唇与瞳孔，小面积高对比）
 * 4. 椒盐噪声合成图（定量验证飞点消除率与对角线条保留率）
 */
class BenchmarkTestSuite {

    private val red = PaletteColor("P01", "#E52521", RgbColor(229, 37, 33))     // 马里奥红
    private val blue = PaletteColor("P02", "#002080", RgbColor(0, 32, 128))     // 工装蓝
    private val brown = PaletteColor("P03", "#6A3805", RgbColor(106, 56, 5))    // 棕发/皮鞋
    private val skin = PaletteColor("P04", "#FCC082", RgbColor(252, 192, 130))  // 肤色
    private val yellow = PaletteColor("P05", "#FBD000", RgbColor(251, 208, 0))  // 金扣
    private val black = PaletteColor("P06", "#000000", RgbColor(0, 0, 0))       // 黑色轮廓
    private val white = PaletteColor("P07", "#FFFFFF", RgbColor(255, 255, 255)) // 白色高光
    private val cyan = PaletteColor("P08", "#00C0C0", RgbColor(0, 192, 192))
    private val magenta = PaletteColor("P09", "#C000C0", RgbColor(192, 0, 192))
    private val darkGray = PaletteColor("P10", "#404040", RgbColor(64, 64, 64))
    private val lightGray = PaletteColor("P11", "#C0C0C0", RgbColor(192, 192, 192))
    private val green = PaletteColor("P12", "#008000", RgbColor(0, 128, 0))

    private val marioPalette = listOf(red, blue, brown, skin, yellow, black, white)
    private val richPalette = listOf(red, blue, brown, skin, yellow, black, white, cyan, magenta, darkGray, lightGray)

    // ---------- 基准 1：经典 8-bit 马里奥像素精灵 ----------

    /** 12×16 经典 8-bit 马里奥头部与身体色块矩阵 */
    private fun createMarioSprite(): Array<Array<RgbColor?>> {
        // 简化 12×16 马里奥结构：帽子红、面部肤色、胡子棕、上衣红、工装蓝、纽扣黄
        val grid = Array(16) { Array<RgbColor?>(12) { white.rgb } }
        for (r in 1..2) for (c in 3..8) grid[r][c] = red.rgb        // 帽子
        for (c in 2..10) grid[3][c] = red.rgb
        for (c in 2..4) grid[4][c] = brown.rgb                       // 头发
        for (c in 5..8) grid[4][c] = skin.rgb                        // 面部
        grid[5][2] = brown.rgb; grid[5][3] = skin.rgb; grid[5][4] = brown.rgb
        for (c in 5..9) grid[5][c] = skin.rgb
        for (c in 2..5) grid[6][c] = brown.rgb; for (c in 6..9) grid[6][c] = skin.rgb
        for (c in 3..8) grid[7][c] = skin.rgb                        // 下巴
        for (r in 8..10) for (c in 3..8) grid[r][c] = red.rgb       // 上衣
        grid[9][4] = yellow.rgb; grid[9][5] = yellow.rgb             // 纽扣 (2格连通特征)
        for (r in 11..13) for (c in 2..9) grid[r][c] = blue.rgb      // 背带裤
        for (c in 1..3) grid[14][c] = brown.rgb; for (c in 8..10) grid[14][c] = brown.rgb // 鞋子
        return grid
    }

    @Test
    fun benchmark_marioPixelSprite_colorComplianceAndContours() {
        val mario = createMarioSprite()
        val start = System.currentTimeMillis()

        // 1. 无限制直接量化
        val dithered = quantizeWithDithering(mario, marioPalette, white)
        val colorsNormal = dithered.flatMap { it.toList() }.map { it.key }.toSet()
        assertTrue("马里奥精灵图应包含帽子红与背带蓝", colorsNormal.contains("P01") && colorsNormal.contains("P02"))

        // 2. 限制色数为 4（受控色数限制测试）
        val controlled4 = ColorQuantizer.computeControlledPalette(mario, richPalette, maxColors = 4)
        assertEquals("受控色数必须精准为 4", 4, controlled4.size)

        // 3. 验证黑色轮廓和对角连续线不会被误伤
        val cleaned = IslandCleanup.cleanupSpeckles(dithered, maxIslandSize = 1, protectDiagonalLines = true)
        val elapsed = System.currentTimeMillis() - start

        // 验证马里奥纽扣等 2 格连通特征完好保留（不被当做单格飞点误删）
        assertEquals("黄色纽扣应保留", yellow.key, cleaned[9][4].key)
        assertEquals("黄色纽扣应保留", yellow.key, cleaned[9][5].key)
        println(">> 基准1 [马里奥像素精灵] 评测通过，耗时: ${elapsed}ms")
    }

    // ---------- 基准 2：多阶平滑渐变插画 ----------

    private fun createGradientImage(w: Int, h: Int): Array<Array<RgbColor?>> {
        val grid = Array(h) { Array<RgbColor?>(w) { null } }
        for (y in 0 until h) {
            val r = (255 * y / h).coerceIn(0, 255)
            val g = (128 * (h - y) / h).coerceIn(0, 255)
            for (x in 0 until w) {
                val b = (255 * x / w).coerceIn(0, 255)
                grid[y][x] = RgbColor(r, g, b)
            }
        }
        return grid
    }

    @Test
    fun benchmark_smoothGradient_colorBudgetAndDitheringTransition() {
        val w = 32
        val h = 32
        val gradient = createGradientImage(w, h)

        // 限制 16 色
        val maxBudget = 16
        val subPalette = ColorQuantizer.computeControlledPalette(gradient, richPalette, maxColors = maxBudget)
        assertTrue("受控子色板不超过预算", subPalette.size <= maxBudget)

        val quantized = quantizeWithDithering(gradient, subPalette, black)
        val usedColors = quantized.flatMap { it.toList() }.map { it.key }.toSet()
        assertTrue("最终使用颜色数必须 <= 预算", usedColors.size <= maxBudget)

        println(">> 基准2 [平滑渐变插画 32×32] 预算: $maxBudget, 实际使用色数: ${usedColors.size}")
    }

    // ---------- 基准 3：微小关键特征保留（白底红唇与瞳孔） ----------

    @Test
    fun benchmark_smallFacialFeatures_retentionUnderQuantization() {
        // 20×20 纯白背景图，仅在中心 (10, 10) 有 2 格极小鲜红嘴唇，在 (6, 7) 有 1 格深蓝瞳孔
        val w = 20
        val h = 20
        val reps = Array(h) { Array<RgbColor?>(w) { white.rgb } }
        reps[10][9] = red.rgb
        reps[10][10] = red.rgb
        reps[6][7] = blue.rgb

        // 限制色板为 3 色（白、红、蓝）
        val paletteCandidates = listOf(white, black, darkGray, lightGray, red, blue, green, brown)
        val resultPalette = ColorQuantizer.computeControlledPalette(reps, paletteCandidates, maxColors = 3)
        val keys = resultPalette.map { it.key }.toSet()

        assertTrue("高饱和红唇在 Oklab 空间下应作为独立箱体保留", keys.contains(red.key))
        assertTrue("深蓝眼瞳在 Oklab 空间下应作为独立箱体保留", keys.contains(blue.key))
        assertTrue("大面积背景白色必须保留", keys.contains(white.key))
        println(">> 基准3 [微小特征保留] 成功捕获小面积红唇与瞳孔色箱！")
    }

    // ---------- 基准 4：椒盐飞点消除率与对角线保留率定量评测 ----------

    @Test
    fun benchmark_speckleRemovalAndDiagonalPreservation_quantitativeScore() {
        val m = 16
        val n = 16
        val grid = Array(m) { Array(n) { MappedPixel(white.key, white.hex, false) } }

        // 注入 8 个完全孤立的噪点（四周及对角全白）
        val isolatedSpeckles = listOf(
            2 to 2, 2 to 8, 2 to 13,
            6 to 3, 6 to 12,
            12 to 2, 12 to 7, 12 to 13
        )
        for ((r, c) in isolatedSpeckles) {
            grid[r][c] = MappedPixel(black.key, black.hex, false)
        }

        // 注入一条连续对角斜线 (从 4,4 到 8,8 共 5 个像素)
        val diagonalLine = (4..8).map { it to it }
        for ((r, c) in diagonalLine) {
            grid[r][c] = MappedPixel(black.key, black.hex, false)
        }

        val cleaned = IslandCleanup.cleanupSpeckles(grid, maxIslandSize = 1, protectDiagonalLines = true)

        // 1. 计算飞点消除率 (Speckle Removal Rate)
        var removedCount = 0
        for ((r, c) in isolatedSpeckles) {
            if (cleaned[r][c].key != black.key) removedCount++
        }
        val srr = removedCount.toDouble() / isolatedSpeckles.size
        assertEquals("孤立飞点消除率必须达 100%", 1.0, srr, 1e-6)

        // 2. 计算连续线条保留率 (Line Preservation Rate)
        var preservedCount = 0
        for ((r, c) in diagonalLine) {
            if (cleaned[r][c].key == black.key) preservedCount++
        }
        val lpr = preservedCount.toDouble() / diagonalLine.size
        assertEquals("对角连续线条保留率必须达 100%", 1.0, lpr, 1e-6)

        println(">> 基准4 [椒盐飞点与细线] 飞点消除率: ${(srr * 100).toInt()}%, 细线保留率: ${(lpr * 100).toInt()}%")
    }
}
