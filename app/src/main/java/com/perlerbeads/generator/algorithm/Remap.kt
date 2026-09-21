package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY

/** 颜色排除结果。 */
data class ExcludeResult(
    val grid: Array<Array<MappedPixel>>,
    val success: Boolean,
    val remappedCount: Int
)

/**
 * 把现有网格整体重映射到目标色板：每格 hex 就近取新色板色，
 * 色号 key 同步更新为新色板体系。external/透明格原样保留。
 * 同 hex 共享映射结果（缓存），保证同色必同映射。
 */
fun remapGridToNearestPalette(
    cells: Array<Array<MappedPixel>>,
    palette: List<PaletteColor>
): Array<Array<MappedPixel>> {
    if (palette.isEmpty()) return cells
    val cache = HashMap<String, MappedPixel>()
    return Array(cells.size) { r ->
        Array(cells[r].size) { c ->
            val cell = cells[r][c]
            if (cell.isExternal || cell.key == TRANSPARENT_KEY) {
                cell
            } else {
                cache.getOrPut(cell.colorHex.uppercase()) {
                    val rgb = hexToRgb(cell.colorHex) ?: return@getOrPut cell
                    val closest = findClosestPaletteColor(rgb, palette)
                    MappedPixel(closest.key, closest.hex, false)
                }
            }
        }
    }
}

/**
 * 颜色排除与重映射。
 * 对应 page.tsx L1142-1214。
 *
 * @param hexKey 要排除的颜色 hex（大写）
 * @param initialColorKeys 初始网格中出现过的 hex 集合
 * @param fullPalette 全量色板（key=hex）
 * @param otherExcluded 其他已排除的 hex 集合
 */
fun excludeColor(
    grid: Array<Array<MappedPixel>>,
    hexKey: String,
    initialColorKeys: Set<String>,
    fullPalette: List<PaletteColor>,
    otherExcluded: Set<String>
): ExcludeResult {
    if (initialColorKeys.isEmpty()) return ExcludeResult(grid, false, 0)

    val potentialRemapHexKeys = HashSet(initialColorKeys)
    potentialRemapHexKeys.remove(hexKey)
    for (ex in otherExcluded) potentialRemapHexKeys.remove(ex)

    val remapTargetPalette = fullPalette.filter { potentialRemapHexKeys.contains(it.hex.uppercase()) }
    if (remapTargetPalette.isEmpty()) return ExcludeResult(grid, false, 0)

    val excludedColorData = fullPalette.find { it.hex.uppercase() == hexKey }
        ?: return ExcludeResult(grid, false, 0)

    var remappedCount = 0
    val newGrid = Array(grid.size) { r -> Array(grid[0].size) { c -> grid[r][c] } }
    for (j in newGrid.indices) {
        for (i in newGrid[0].indices) {
            val cell = newGrid[j][i]
            if (!cell.isExternal && cell.colorHex.uppercase() == hexKey) {
                val replacement = findClosestPaletteColor(excludedColorData.rgb, remapTargetPalette)
                newGrid[j][i] = MappedPixel(replacement.key, replacement.hex, false)
                remappedCount++
            }
        }
    }
    return ExcludeResult(newGrid, true, remappedCount)
}
