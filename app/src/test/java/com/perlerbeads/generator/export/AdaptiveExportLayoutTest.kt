package com.perlerbeads.generator.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证导出颜色统计表的多列网格自适应计算逻辑。
 * 根据画布/图纸宽度自动适配列数，消除单列纵向无限拉长与留白。
 */
class AdaptiveExportLayoutTest {

    private fun calculateColumns(rowsCount: Int, width: Int): Int {
        val safeWidth = maxOf(360, width)
        val margin = (safeWidth * 0.03f).coerceIn(24f, 48f)
        val availW = safeWidth - 2f * margin
        val idealColW = 320f
        val rawCols = (availW / idealColW).toInt().coerceAtLeast(1)
        return rawCols.coerceIn(1, maxOf(1, rowsCount))
    }

    private fun calculateTotalHeight(rowsCount: Int, numCols: Int): Int {
        val numRows = if (rowsCount == 0) 0 else (rowsCount + numCols - 1) / numCols
        val rowHeight = 44f
        val headerHeight = 72f
        val footerHeight = 44f
        return maxOf(100, (headerHeight + numRows * rowHeight + footerHeight).toInt())
    }

    @Test
    fun smallWidth_usesSingleColumn() {
        // 小尺寸图纸 (宽 360~400) 适配为 1 列
        val cols = calculateColumns(rowsCount = 20, width = 360)
        assertEquals(1, cols)
        val height = calculateTotalHeight(rowsCount = 20, numCols = cols)
        assertEquals(72 + 20 * 44 + 44, height)
    }

    @Test
    fun mediumWidth_adaptsToMultipleColumns() {
        // 中等尺寸 (宽 720, 如手机竖屏图纸) 适配为 2 列
        val cols720 = calculateColumns(rowsCount = 20, width = 720)
        assertEquals(2, cols720)
        val height720 = calculateTotalHeight(rowsCount = 20, numCols = cols720)
        // 20 种颜色在 2 列下仅需 10 行，高度减半！
        assertEquals(72 + 10 * 44 + 44, height720)
    }

    @Test
    fun largeWidth_adaptsToFiveOrMoreColumns() {
        // 大图 (宽 1800) 适配为 5 列
        val cols1800 = calculateColumns(rowsCount = 30, width = 1800)
        assertEquals(5, cols1800)
        val height1800 = calculateTotalHeight(rowsCount = 30, numCols = cols1800)
        // 30 种颜色在 5 列下仅需 6 行！
        assertEquals(72 + 6 * 44 + 44, height1800)
    }

    @Test
    fun columnCountDoesNotExceedRowCount() {
        // 当大图 (宽 2000) 但总共只有 2 种颜色时，列数不超过 2 列
        val cols = calculateColumns(rowsCount = 2, width = 2000)
        assertEquals(2, cols)
    }

    @Test
    fun emptyRowsHandledGracefully() {
        val cols = calculateColumns(rowsCount = 0, width = 800)
        assertEquals(1, cols)
        val height = calculateTotalHeight(rowsCount = 0, numCols = cols)
        assertEquals(116, height)
        assertTrue(height >= 100)
    }
}
