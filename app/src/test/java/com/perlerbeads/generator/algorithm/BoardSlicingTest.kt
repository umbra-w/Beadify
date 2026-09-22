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

    @Test
    fun rangeCodecRoundTripAndEdgeCases() {
        assertEquals("", encodeCellIndices(emptySet()))
        assertEquals(emptySet<Int>(), decodeCellIndices(""))
        assertEquals(emptySet<Int>(), decodeCellIndices("   "))

        val set1 = setOf(5)
        assertEquals("5", encodeCellIndices(set1))
        assertEquals(set1, decodeCellIndices("5"))

        val set2 = setOf(1, 2, 3, 4, 7, 10, 11, 12, 15)
        val encoded2 = encodeCellIndices(set2)
        assertEquals("1-4,7,10-12,15", encoded2)
        assertEquals(set2, decodeCellIndices(encoded2))

        // 包含倒序或脏数据也能健壮容错
        val decodedDirty = decodeCellIndices(" 10-12, 7, 4-1 , abc, , 15 ")
        assertEquals(setOf(1, 2, 3, 4, 7, 10, 11, 12, 15), decodedDirty)
    }

    @Test
    fun gridContentKeyIndependentOfBoardSize() {
        val g1 = gridOf(4, 4) { _, _ -> cell("A01", "#000000") }
        val g2 = gridOf(4, 4) { _, _ -> cell("A01", "#000000") }
        val g3 = gridOf(4, 4) { r, c -> if (r == 0 && c == 0) cell("B02", "#111111") else cell("A01", "#000000") }
        assertEquals(gridContentKey(g1), gridContentKey(g2))
        assertNotEquals(gridContentKey(g1), gridContentKey(g3))
        assertTrue(gridContentKey(g1).startsWith("4x4_"))
    }

    @Test
    fun cellAndColorCompletionTracking() {
        // 4x4 网格，板尺寸 2x2（共 4 块板）
        // (0,0)=A01, (0,1)=A01, (1,0)=B02, (1,1)=A01 -> 第 0 块板有 3 颗 A01，1 颗 B02
        val g = gridOf(4, 4) { r, c ->
            if (r == 1 && c == 0) cell("B02", "#111111") else cell("A01", "#000000")
        }
        val slices = sliceBoards(g, 2)
        val slice0 = slices[0]
        assertEquals(4, slice0.total)

        var completed = emptySet<Int>()
        assertEquals(0, sliceCompletedCount(g, slice0, completed))
        assertEquals(0, colorCompletedCount(g, slice0, "A01", completed))
        assertEquals(false, isBoardComplete(g, slice0, completed))

        // 打勾 (0, 0)
        completed = toggleCellCompletion(g, 0, 0, completed)
        assertTrue(0 in completed)
        assertEquals(1, sliceCompletedCount(g, slice0, completed))
        assertEquals(1, colorCompletedCount(g, slice0, "A01", completed))
        assertEquals(0, colorCompletedCount(g, slice0, "B02", completed))
        assertEquals(false, isBoardComplete(g, slice0, completed))

        // 再次打勾 (0, 0) 取消
        completed = toggleCellCompletion(g, 0, 0, completed)
        assertTrue(0 !in completed)
        assertEquals(0, sliceCompletedCount(g, slice0, completed))

        // 批量打勾色号 A01（第 0 块板有 3 颗：(0,0)=0, (0,1)=1, (1,1)=5）
        completed = toggleColorCompletionOnSlice(g, slice0, "A01", completed)
        assertEquals(3, sliceCompletedCount(g, slice0, completed))
        assertEquals(3, colorCompletedCount(g, slice0, "A01", completed))
        assertEquals(false, isBoardComplete(g, slice0, completed))

        // 再次触发色号 A01，因为已全部打勾，应全部取消
        val untoggled = toggleColorCompletionOnSlice(g, slice0, "A01", completed)
        assertEquals(0, sliceCompletedCount(g, slice0, untoggled))

        // 批量打勾全板
        completed = toggleSliceCompletion(g, slice0, completed)
        assertEquals(4, sliceCompletedCount(g, slice0, completed))
        assertEquals(true, isBoardComplete(g, slice0, completed))

        // 全板打勾后，updateCompletedBoards 应包含板 0
        val completedBoardIndices = updateCompletedBoards(g, slices, completed)
        assertEquals(setOf(0), completedBoardIndices)

        // 再次触发全板，应清空该板
        val cleared = toggleSliceCompletion(g, slice0, completed)
        assertEquals(0, sliceCompletedCount(g, slice0, cleared))
        assertEquals(false, isBoardComplete(g, slice0, cleared))
        assertEquals(emptySet<Int>(), updateCompletedBoards(g, slices, cleared))
    }
}

