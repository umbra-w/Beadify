package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import kotlin.math.max

/** 单颗颜色的用豆统计。 */
data class BeadCount(val key: String, val hex: String, val count: Int)

/** 一块实体板的切片：覆盖范围 + 用豆统计。 */
data class BoardSlice(
    val index: Int,      // 行优先序号，从 0 开始
    val boardCol: Int,   // 横向第几块板
    val boardRow: Int,   // 纵向第几块板
    val colStart: Int,   // 覆盖的起始列
    val rowStart: Int,   // 覆盖的起始行
    val cols: Int,       // 本板实际列数（边缘板收窄）
    val rows: Int,       // 本板实际行数
    val beads: List<BeadCount>, // 按用量降序
    val total: Int
)

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/** 板数（行优先总数）。 */
fun boardCount(n: Int, m: Int, boardSize: Int): Int {
    require(boardSize > 0) { "boardSize must be positive" }
    if (n <= 0 || m <= 0) return 0
    return ceilDiv(n, boardSize) * ceilDiv(m, boardSize)
}

/**
 * 把网格按 boardSize×boardSize 切成实体板序列（行优先）。
 * 边缘板按网格实际尺寸收窄；external/透明格子不计入用豆。
 *
 * @param cellInScope 可选的格子归属判定（如圆形画板只统计圆框内）；null = 全部
 */
fun sliceBoards(
    grid: GridData,
    boardSize: Int,
    cellInScope: ((row: Int, col: Int) -> Boolean)? = null
): List<BoardSlice> {
    require(boardSize > 0) { "boardSize must be positive" }
    val result = ArrayList<BoardSlice>()
    val boardCols = ceilDiv(grid.n, boardSize)
    val boardRows = ceilDiv(grid.m, boardSize)

    // hex → 显示色号（取网格中首个匹配格子的 key）
    val keyByHex = HashMap<String, String>()
    for (row in grid.cells) {
        for (cell in row) {
            if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                keyByHex.putIfAbsent(cell.colorHex.uppercase(), cell.key)
            }
        }
    }

    for (br in 0 until boardRows) {
        for (bc in 0 until boardCols) {
            val colStart = bc * boardSize
            val rowStart = br * boardSize
            val cols = minOf(boardSize, grid.n - colStart)
            val rows = minOf(boardSize, grid.m - rowStart)
            val counts = HashMap<String, Int>()
            var total = 0
            for (r in rowStart until rowStart + rows) {
                for (c in colStart until colStart + cols) {
                    if (cellInScope != null && !cellInScope(r, c)) continue
                    val cell = grid.cells[r][c]
                    if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                        val hex = cell.colorHex.uppercase()
                        counts[hex] = (counts[hex] ?: 0) + 1
                        total++
                    }
                }
            }
            val beads = counts.entries
                .map { (hex, count) -> BeadCount(keyByHex[hex] ?: hex, hex, count) }
                .sortedByDescending { it.count }
            result.add(BoardSlice(br * boardCols + bc, bc, br, colStart, rowStart, cols, rows, beads, total))
        }
    }
    return result
}

/**
 * 进度存储键：网格尺寸 + 板尺寸 + 网格内容指纹。
 * 换图或重新生成后指纹变化，进度自然失效，不会串到别的图纸上。
 */
fun boardProgressKey(grid: GridData, boardSize: Int): String {
    var h = -312836211L
    for (row in grid.cells) {
        for (cell in row) {
            h = h xor cell.key.hashCode().toLong()
            h *= 0x1000193L
        }
    }
    val width = max(grid.n, 1)
    return "${grid.n}x${grid.m}_b${boardSize}_%08x".format(h) + "_w$width"
}
