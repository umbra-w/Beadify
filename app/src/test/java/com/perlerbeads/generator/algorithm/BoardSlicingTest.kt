package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.transparentColorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSlicingTest {

    private fun cell(key: String, hex: String) = MappedPixel(key, hex, false)

    private fun gridOf(n: Int, m: Int, fill: (row: Int, col: Int) -> MappedPixel): GridData =
        GridData(n, m, Array(m) { r -> Array(n) { c -> fill(r, c) } }, emptySet())

    @Test
    fun boardCountMath() {
        assertEquals(0, boardCount(0, 10, 4))
        assertEquals(2, boardCount(4, 6, 4))   // 1×2 块
        assertEquals(9, boardCount(29, 29, 10)) // 3×3 块
        assertEquals(1, boardCount(10, 10, 29)) // 单板装下
    }

    @Test
    fun sliceExactAndPartialBoards() {
        // 4×6 网格，板尺寸 4：第一块 4×4，第二块（下边缘）4×2
        val g = gridOf(4, 6) { _, _ -> cell("A01", "#000000") }
        val slices = sliceBoards(g, 4)
        assertEquals(2, slices.size)
        assertEquals(0, slices[0].boardCol)
        assertEquals(0, slices[0].boardRow)
        assertEquals(4, slices[0].cols)
        assertEquals(4, slices[0].rows)
        assertEquals(0, slices[1].boardCol)
        assertEquals(1, slices[1].boardRow)
        assertEquals(16, slices[0].total)
        assertEquals(8, slices[1].total)
        // 起始行正确
        assertEquals(0, slices[0].rowStart)
        assertEquals(4, slices[1].rowStart)
    }

    @Test
    fun sliceRowMajorOrdering() {
        // 6×6 网格、板尺寸 3 → 2×2 块，行优先编号
        val g = gridOf(6, 6) { _, _ -> cell("A01", "#000000") }
        val slices = sliceBoards(g, 3)
        assertEquals(4, slices.size)
        assertEquals(1, slices[1].boardCol)
        assertEquals(0, slices[1].boardRow)
        assertEquals(0, slices[2].boardCol)
        assertEquals(1, slices[2].boardRow)
        assertEquals(1, slices[3].boardCol)
        assertEquals(1, slices[3].boardRow)
    }

    @Test
    fun countsExcludeTransparentAndExternal() {
        val g = gridOf(2, 2) { r, c ->
            when {
                r == 0 && c == 0 -> transparentColorData           // 透明
                r == 1 && c == 1 -> MappedPixel("X", "#FFFFFF", isExternal = true)
                else -> cell("A01", "#000000")
            }
        }
        val slices = sliceBoards(g, 4)
        assertEquals(1, slices.size)
        assertEquals(2, slices[0].total)
        assertEquals(listOf(BeadCount("A01", "#000000", 2)), slices[0].beads)
    }

    @Test
    fun beadsSortedByCountDescending() {
        val g = gridOf(2, 2) { r, c ->
            if (r + c == 0) cell("B02", "#111111") else cell("A01", "#000000")
        }
        val slices = sliceBoards(g, 4)
        assertEquals(listOf(BeadCount("A01", "#000000", 3), BeadCount("B02", "#111111", 1)), slices[0].beads)
    }

    @Test
    fun scopePredicateLimitsCounting() {
        val g = gridOf(4, 4) { _, _ -> cell("A01", "#000000") }
        // 只统计左上 2×2
        val slices = sliceBoards(g, 4) { r, c -> r < 2 && c < 2 }
        assertEquals(4, slices[0].total)
    }

    @Test
    fun progressKeyStableAndContentSensitive() {
        val g1 = gridOf(4, 4) { _, _ -> cell("A01", "#000000") }
        val g2 = gridOf(4, 4) { _, _ -> cell("A01", "#000000") }
        val g3 = gridOf(4, 4) { r, c -> if (r + c == 0) cell("B02", "#111111") else cell("A01", "#000000") }
        assertEquals(boardProgressKey(g1, 29), boardProgressKey(g2, 29))
        assertNotEquals(boardProgressKey(g1, 29), boardProgressKey(g3, 29))
        assertNotEquals(boardProgressKey(g1, 29), boardProgressKey(g1, 16))
        assertTrue(boardProgressKey(g1, 29).startsWith("4x4_b29_"))
    }

    @Test
    fun circleShapeGridSlicesLikeSquare() {
        // 形状不影响切片逻辑（归属由 cellInScope 决定）
        val g = GridData(4, 4, Array(4) { Array(4) { cell("A01", "#000000") } }, emptySet(), GridShape.CIRCLE)
        assertEquals(1, sliceBoards(g, 29).size)
    }
}
