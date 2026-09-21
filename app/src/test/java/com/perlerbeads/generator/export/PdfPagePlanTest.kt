package com.perlerbeads.generator.export

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPagePlanTest {

    @Test
    fun singlePageWhenPatternFits() {
        // A4 容量约 36×50 格
        val tiles = planPdfTiles(30, 40, 36, 50)
        assertEquals(1, tiles.size)
        assertEquals(30, tiles[0].cols)
        assertEquals(40, tiles[0].rows)
    }

    @Test
    fun pagesClipAtEdges() {
        // 90×135、页容量 36×50 → 3×3 页，边缘页收窄
        val tiles = planPdfTiles(90, 135, 36, 50)
        assertEquals(9, tiles.size)
        // 首页
        assertEquals(0, tiles[0].colStart)
        assertEquals(36, tiles[0].cols)
        assertEquals(50, tiles[0].rows)
        // 最右列页收窄：90 = 36+36+18
        assertEquals(18, tiles[2].cols)
        assertEquals(72, tiles[2].colStart)
        // 最底行页收窄：135 = 50+50+35
        assertEquals(35, tiles[6].rows)
        assertEquals(100, tiles[6].rowStart)
        // 右下角
        assertEquals(18, tiles[8].cols)
        assertEquals(35, tiles[8].rows)
    }

    @Test
    fun rowMajorOrdering() {
        val tiles = planPdfTiles(72, 100, 36, 50)
        assertEquals(4, tiles.size)
        assertEquals(1, tiles[1].pageCol)
        assertEquals(0, tiles[1].pageRow)
        assertEquals(0, tiles[2].pageCol)
        assertEquals(1, tiles[2].pageRow)
    }

    @Test
    fun emptyGridGivesNoPages() {
        assertEquals(0, planPdfTiles(0, 10, 36, 50).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCapacityRejected() {
        planPdfTiles(10, 10, 0, 50)
    }
}
