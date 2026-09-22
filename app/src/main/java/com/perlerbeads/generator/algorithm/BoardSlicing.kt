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

/** 网格内容指纹（不含板尺寸），用于跨板尺寸持久化逐格跟做进度。 */
fun gridContentKey(grid: GridData): String {
    var h = -312836211L
    for (row in grid.cells) {
        for (cell in row) {
            h = h xor cell.key.hashCode().toLong()
            h *= 0x1000193L
        }
    }
    val width = max(grid.n, 1)
    return "${grid.n}x${grid.m}_%08x".format(h) + "_w$width"
}

/** 把已完成格子的全局平坦索引集合（row * grid.n + col）压缩成区间串（例如 "1-5,10,12-16"）。 */
fun encodeCellIndices(indices: Set<Int>): String {
    if (indices.isEmpty()) return ""
    val sorted = indices.toList().sorted()
    val sb = StringBuilder()
    var start = sorted[0]
    var end = sorted[0]
    for (i in 1 until sorted.size) {
        val current = sorted[i]
        if (current == end + 1) {
            end = current
        } else {
            if (start == end) sb.append(start) else sb.append(start).append('-').append(end)
            sb.append(',')
            start = current
            end = current
        }
    }
    if (start == end) sb.append(start) else sb.append(start).append('-').append(end)
    return sb.toString()
}

/** 把区间串解码回索引集合。 */
fun decodeCellIndices(raw: String): Set<Int> {
    if (raw.isBlank()) return emptySet()
    val result = HashSet<Int>()
    for (part in raw.split(',')) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val dashIdx = trimmed.indexOf('-')
        if (dashIdx >= 0) {
            val start = trimmed.substring(0, dashIdx).trim().toIntOrNull() ?: continue
            val end = trimmed.substring(dashIdx + 1).trim().toIntOrNull() ?: continue
            for (k in minOf(start, end)..maxOf(start, end)) {
                result.add(k)
            }
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result
}

/** 获取本板所有有效实体豆的全局平坦索引集合。 */
fun sliceValidCellIndices(
    grid: GridData,
    slice: BoardSlice,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): List<Int> {
    val list = ArrayList<Int>()
    for (r in 0 until slice.rows) {
        val gr = slice.rowStart + r
        for (c in 0 until slice.cols) {
            val gc = slice.colStart + c
            if (scope != null && !scope(gr, gc)) continue
            val cell = grid.cells[gr][gc]
            if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                list.add(gr * grid.n + gc)
            }
        }
    }
    return list
}

/** 获取本板某色号所有有效实体豆的全局平坦索引集合。 */
fun sliceColorCellIndices(
    grid: GridData,
    slice: BoardSlice,
    colorKey: String,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): List<Int> {
    val list = ArrayList<Int>()
    for (r in 0 until slice.rows) {
        val gr = slice.rowStart + r
        for (c in 0 until slice.cols) {
            val gc = slice.colStart + c
            if (scope != null && !scope(gr, gc)) continue
            val cell = grid.cells[gr][gc]
            if (!cell.isExternal && cell.key == colorKey) {
                list.add(gr * grid.n + gc)
            }
        }
    }
    return list
}

/** 统计本板已拼颗粒数。 */
fun sliceCompletedCount(
    grid: GridData,
    slice: BoardSlice,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Int {
    var count = 0
    for (r in 0 until slice.rows) {
        val gr = slice.rowStart + r
        for (c in 0 until slice.cols) {
            val gc = slice.colStart + c
            if (scope != null && !scope(gr, gc)) continue
            val cell = grid.cells[gr][gc]
            if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                if ((gr * grid.n + gc) in completedCells) {
                    count++
                }
            }
        }
    }
    return count
}

/** 统计本板指定色号已拼颗粒数。 */
fun colorCompletedCount(
    grid: GridData,
    slice: BoardSlice,
    colorKey: String,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Int {
    var count = 0
    for (r in 0 until slice.rows) {
        val gr = slice.rowStart + r
        for (c in 0 until slice.cols) {
            val gc = slice.colStart + c
            if (scope != null && !scope(gr, gc)) continue
            val cell = grid.cells[gr][gc]
            if (!cell.isExternal && cell.key == colorKey) {
                if ((gr * grid.n + gc) in completedCells) {
                    count++
                }
            }
        }
    }
    return count
}

/** 判定本板是否全部拼完。若本板无需拼豆（total == 0），则也视为完成。 */
fun isBoardComplete(
    grid: GridData,
    slice: BoardSlice,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Boolean {
    if (slice.total == 0) return true
    return sliceCompletedCount(grid, slice, completedCells, scope) >= slice.total
}

/** 切换指定格子的打勾/取消状态。 */
fun toggleCellCompletion(
    grid: GridData,
    row: Int,
    col: Int,
    completedCells: Set<Int>
): Set<Int> {
    if (row !in 0 until grid.m || col !in 0 until grid.n) return completedCells
    val idx = row * grid.n + col
    return if (idx in completedCells) completedCells - idx else completedCells + idx
}

/** 批量切换某板上某色号的打勾状态：若已全勾则全部取消，否则全部补齐打勾。 */
fun toggleColorCompletionOnSlice(
    grid: GridData,
    slice: BoardSlice,
    colorKey: String,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Set<Int> {
    val indices = sliceColorCellIndices(grid, slice, colorKey, scope)
    if (indices.isEmpty()) return completedCells
    val allDone = indices.all { it in completedCells }
    return if (allDone) {
        completedCells - indices.toSet()
    } else {
        completedCells + indices
    }
}

/** 批量切换某板的打勾状态：若全板已完成则全部清空，否则全部标为完成。 */
fun toggleSliceCompletion(
    grid: GridData,
    slice: BoardSlice,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Set<Int> {
    val indices = sliceValidCellIndices(grid, slice, scope)
    if (indices.isEmpty()) return completedCells
    val allDone = indices.all { it in completedCells }
    return if (allDone) {
        completedCells - indices.toSet()
    } else {
        completedCells + indices
    }
}

/** 根据当前已打勾格子集合，重新计算所有板的完成状态集合。 */
fun updateCompletedBoards(
    grid: GridData,
    slices: List<BoardSlice>,
    completedCells: Set<Int>,
    scope: ((row: Int, col: Int) -> Boolean)? = null
): Set<Int> {
    val set = HashSet<Int>()
    for (slice in slices) {
        if (isBoardComplete(grid, slice, completedCells, scope)) {
            set.add(slice.index)
        }
    }
    return set
}
