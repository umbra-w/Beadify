package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData

/** 一键去背景结果。 */
data class BackgroundResult(
    val grid: Array<Array<MappedPixel>>,
    val removedCount: Int
)

/**
 * 一键去背景：识别边界出现最多的色键，从边界做洪水填充去除。
 * 对应 page.tsx L1272-1369。
 */
fun autoRemoveBackground(grid: Array<Array<MappedPixel>>): BackgroundResult {
    val m = grid.size
    val n = grid[0].size

    val borderCounts = HashMap<String, Int>()
    fun countBorder(row: Int, col: Int) {
        val cell = grid.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isExternal || cell.key == TRANSPARENT_KEY) return
        borderCounts[cell.key] = (borderCounts[cell.key] ?: 0) + 1
    }
    for (col in 0 until n) {
        countBorder(0, col)
        if (m > 1) countBorder(m - 1, col)
    }
    for (row in 1 until m - 1) {
        countBorder(row, 0)
        if (n > 1) countBorder(row, n - 1)
    }
    if (borderCounts.isEmpty()) return BackgroundResult(grid, 0)

    var targetKey = ""
    var maxCount = -1
    for ((k, c) in borderCounts) {
        if (c > maxCount) {
            maxCount = c
            targetKey = k
        }
    }

    val newGrid = Array(m) { r -> Array(n) { c -> grid[r][c] } }
    val visited = Array(m) { Array(n) { false } }
    val stack = ArrayList<Pair<Int, Int>>()

    fun pushIfTarget(row: Int, col: Int) {
        if (row < 0 || row >= m || col < 0 || col >= n || visited[row][col]) return
        val cell = newGrid[row][col]
        if (cell.isExternal || cell.key != targetKey) return
        visited[row][col] = true
        stack.add(row to col)
    }
    for (col in 0 until n) {
        pushIfTarget(0, col)
        if (m > 1) pushIfTarget(m - 1, col)
    }
    for (row in 1 until m - 1) {
        pushIfTarget(row, 0)
        if (n > 1) pushIfTarget(row, n - 1)
    }
    if (stack.isEmpty()) return BackgroundResult(grid, 0)

    var removedCount = 0
    while (stack.isNotEmpty()) {
        val (row, col) = stack.removeAt(stack.size - 1)
        newGrid[row][col] = transparentColorData
        removedCount++
        pushIfTarget(row - 1, col)
        pushIfTarget(row + 1, col)
        pushIfTarget(row, col - 1)
        pushIfTarget(row, col + 1)
    }
    return BackgroundResult(newGrid, removedCount)
}
