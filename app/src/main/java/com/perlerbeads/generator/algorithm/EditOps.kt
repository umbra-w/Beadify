package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData

/** 颜色替换结果。 */
data class ReplaceResult(
    val grid: Array<Array<MappedPixel>>,
    val replaceCount: Int
)

/** 单像素上色结果。 */
data class PaintResult(
    val grid: Array<Array<MappedPixel>>,
    val previous: MappedPixel?,
    val hasChange: Boolean
)

/** 颜色统计结果：hex → 数量，及总粒数。 */
data class ColorStats(
    val counts: Map<String, Int>,
    val totalCount: Int
)

/**
 * 洪水填充擦除连通同色区域。对应 pixelEditingUtils.ts L22。
 */
fun floodFillErase(
    grid: Array<Array<MappedPixel>>,
    startRow: Int,
    startCol: Int,
    targetKey: String
): Array<Array<MappedPixel>> {
    val m = grid.size
    val n = grid[0].size
    val newGrid = Array(m) { r -> Array(n) { c -> grid[r][c] } }
    val visited = Array(m) { Array(n) { false } }
    val stack = ArrayList<Pair<Int, Int>>()
    stack.add(startRow to startCol)

    while (stack.isNotEmpty()) {
        val (row, col) = stack.removeAt(stack.size - 1)
        if (row < 0 || row >= m || col < 0 || col >= n || visited[row][col]) continue
        val cell = newGrid[row][col]
        if (cell.isExternal || cell.key != targetKey) continue
        visited[row][col] = true
        newGrid[row][col] = transparentColorData
        stack.add(row - 1 to col)
        stack.add(row + 1 to col)
        stack.add(row to col - 1)
        stack.add(row to col + 1)
    }
    return newGrid
}

/**
 * 颜色替换（按 colorHex 匹配）。对应 pixelEditingUtils.ts L77。
 */
fun replaceColor(
    grid: Array<Array<MappedPixel>>,
    sourceColor: MappedPixel,
    targetColor: MappedPixel
): ReplaceResult {
    val newGrid = Array(grid.size) { r -> Array(grid[0].size) { c -> grid[r][c] } }
    var replaceCount = 0
    for (j in newGrid.indices) {
        for (i in newGrid[0].indices) {
            val cell = newGrid[j][i]
            if (!cell.isExternal && cell.colorHex.uppercase() == sourceColor.colorHex.uppercase()) {
                newGrid[j][i] = MappedPixel(targetColor.key, targetColor.colorHex, false)
                replaceCount++
            }
        }
    }
    return ReplaceResult(newGrid, replaceCount)
}

/**
 * 单像素上色。对应 pixelEditingUtils.ts L115。
 */
fun paintSinglePixel(
    grid: Array<Array<MappedPixel>>,
    row: Int,
    col: Int,
    newColor: MappedPixel
): PaintResult {
    val cell = grid.getOrNull(row)?.getOrNull(col) ?: return PaintResult(grid, null, false)
    val previousKey = cell.key
    val wasExternal = cell.isExternal
    val newCell = if (newColor.key == TRANSPARENT_KEY) {
        transparentColorData
    } else {
        MappedPixel(newColor.key, newColor.colorHex, false)
    }
    val hasChange = newCell.key != previousKey || newCell.isExternal != wasExternal
    if (!hasChange) return PaintResult(grid, cell, false)

    val newGrid = Array(grid.size) { r -> Array(grid[0].size) { c -> grid[r][c] } }
    newGrid[row][col] = newCell
    return PaintResult(newGrid, cell, true)
}

/**
 * 重新统计颜色与总数，忽略 external/透明。对应 pixelEditingUtils.ts L166。
 * @param cellFilter 可选格子过滤（如圆形画板只统计圆内）；null = 全部格子。
 */
fun recalculateColorStats(
    grid: Array<Array<MappedPixel>>,
    cellFilter: ((row: Int, col: Int) -> Boolean)? = null
): ColorStats {
    val counts = HashMap<String, Int>()
    var total = 0
    for (r in grid.indices) {
        val row = grid[r]
        for (c in row.indices) {
            val cell = row[c]
            if (cellFilter != null && !cellFilter(r, c)) continue
            if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                val hex = cell.colorHex.uppercase()
                counts[hex] = (counts[hex] ?: 0) + 1
                total++
            }
        }
    }
    return ColorStats(counts, total)
}
