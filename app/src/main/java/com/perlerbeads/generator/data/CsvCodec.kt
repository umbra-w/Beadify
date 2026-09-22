package com.perlerbeads.generator.data

import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData

/**
 * 拼豆图纸 CSV 编解码器（纯 Kotlin，支持与 Zippland 网页版双向互通）。
 * 对齐 Adam-pd PR #24：大小写容错、透明格正确标记 isExternal。
 */
object CsvCodec {

    /**
     * 把图纸网格序列化为标准图纸 CSV（每行代表图纸的一行，逗号分隔，透明格为 TRANSPARENT）。
     */
    fun exportPatternCsv(grid: GridData): String {
        val sb = StringBuilder()
        for (r in 0 until grid.m) {
            for (c in 0 until grid.n) {
                if (c > 0) sb.append(",")
                val cell = grid.cells[r][c]
                if (cell.isExternal || cell.key == TRANSPARENT_KEY) {
                    sb.append("TRANSPARENT")
                } else {
                    sb.append(cell.colorHex.uppercase())
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * 从图纸 CSV 文本反序列化为 GridData。
     * @param csvContent CSV 文件文本
     * @param fullPalette 全量色板（用于通过 hex 反查店家显示色号 key）
     * @throws IllegalArgumentException 当格式不合法时抛出清晰异常
     */
    fun importPatternCsv(
        csvContent: String,
        fullPalette: List<PaletteColor> = emptyList()
    ): GridData {
        // 去除 UTF-8 BOM
        val clean = csvContent.removePrefix("\uFEFF").trim()
        require(clean.isNotEmpty()) { "CSV 文件内容为空" }

        val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "CSV 文件没有有效行" }

        // 识别是否误选了采购清单 CSV
        if (lines[0].contains("色号") && lines[0].contains("数量")) {
            throw IllegalArgumentException("所选文件是采购清单，而非网格图纸 CSV")
        }

        val rows = lines.size
        val firstRowTokens = lines[0].split(",").map { it.trim() }
        val cols = firstRowTokens.size
        require(cols > 0) { "CSV 第一行无有效列" }

        val paletteByHex = fullPalette.associateBy { it.hex.uppercase() }
        val hexRegex = Regex("^#?[0-9A-Fa-f]{6}$")

        val cells = Array(rows) { r ->
            val tokens = lines[r].split(",").map { it.trim() }
            require(tokens.size == cols) {
                "第 ${r + 1} 行列数不匹配：期望 $cols 列，实际 ${tokens.size} 列"
            }
            Array(cols) { c ->
                val rawVal = tokens[c].removeSurrounding("\"").trim()
                val upper = rawVal.uppercase()
                if (upper == "TRANSPARENT" || upper == "ERASE" || upper.isEmpty()) {
                    transparentColorData
                } else if (hexRegex.matches(rawVal)) {
                    val hex = if (rawVal.startsWith("#")) upper else "#$upper"
                    val matchedKey = paletteByHex[hex]?.key ?: hex
                    MappedPixel(key = matchedKey, colorHex = hex, isExternal = false)
                } else {
                    // 尝试匹配色号 key
                    val matchedColor = fullPalette.firstOrNull { it.key.equals(rawVal, ignoreCase = true) }
                    if (matchedColor != null) {
                        MappedPixel(key = matchedColor.key, colorHex = matchedColor.hex.uppercase(), isExternal = false)
                    } else {
                        MappedPixel(key = rawVal, colorHex = "#000000", isExternal = false)
                    }
                }
            }
        }

        val initialColors = HashSet<String>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = cells[r][c]
                if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                    initialColors.add(cell.colorHex.uppercase())
                }
            }
        }

        return GridData(n = cols, m = rows, cells = cells, initialColorKeys = initialColors, shape = GridShape.SQUARE)
    }
}
