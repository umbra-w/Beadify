package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor

/**
 * 平替色推荐等级（基于 Oklab 感知色差）。
 */
enum class SubstitutionRating(val stars: Int, val label: String) {
    PERFECT(5, "几乎无色差"),
    GOOD(4, "推荐平替"),
    ACCEPTABLE(3, "相近替代"),
    POOR(2, "色差偏大");

    companion object {
        fun fromDeltaE(deltaE: Double): SubstitutionRating = when {
            deltaE <= 5.0 -> PERFECT
            deltaE <= 10.0 -> GOOD
            deltaE <= 18.0 -> ACCEPTABLE
            else -> POOR
        }
    }
}

/**
 * 平替色推荐结果。
 */
data class SubstitutionResult(
    val substitute: PaletteColor,
    val deltaE: Double,
    val rating: SubstitutionRating
)

/**
 * 缺料颜色详情。
 */
data class MissingColorItem(
    val hex: String,
    val key: String,
    val name: String,
    val requiredCount: Int,
    val substitute: SubstitutionResult?
)

object ColorSubstitution {

    /**
     * 在可用/库存候选色列表中，为目标颜色寻找最佳平替色。
     */
    fun findBestSubstitute(
        targetHex: String,
        targetRgb: RgbColor?,
        inStockCandidates: List<PaletteColor>
    ): SubstitutionResult? {
        if (inStockCandidates.isEmpty()) return null
        val rgb = targetRgb ?: hexToRgb(targetHex) ?: return null
        val targetLab = ColorMath.rgbToOklab(rgb)

        var bestColor: PaletteColor? = null
        var bestDistSquared = Double.MAX_VALUE

        val upperTargetHex = targetHex.uppercase()

        for (candidate in inStockCandidates) {
            if (candidate.hex.uppercase() == upperTargetHex && inStockCandidates.size > 1) {
                continue
            }
            val candidateLab = ColorMath.rgbToOklab(candidate.rgb)
            val d2 = targetLab.distanceSquared(candidateLab)
            if (d2 < bestDistSquared) {
                bestDistSquared = d2
                bestColor = candidate
                if (d2 == 0.0) break
            }
        }

        val chosen = bestColor ?: inStockCandidates.firstOrNull() ?: return null
        val deltaE = kotlin.math.sqrt(bestDistSquared) * 100.0
        return SubstitutionResult(
            substitute = chosen,
            deltaE = deltaE,
            rating = SubstitutionRating.fromDeltaE(deltaE)
        )
    }

    /**
     * 在二维网格中将所有指定颜色的格子替换为平替色。
     */
    fun substituteColorInCells(
        cells: Array<Array<MappedPixel>>,
        fromHex: String,
        toColor: PaletteColor
    ): Array<Array<MappedPixel>> {
        val targetHex = fromHex.uppercase()
        val newHex = toColor.hex.uppercase()
        val newKey = toColor.key

        return Array(cells.size) { r ->
            Array(cells[r].size) { c ->
                val px = cells[r][c]
                if (!px.isExternal && px.colorHex.uppercase() == targetHex) {
                    px.copy(colorHex = newHex, key = newKey)
                } else {
                    px
                }
            }
        }
    }

    /**
     * 统计网格中使用的所有颜色与库存对比，计算缺料清单及各自的最佳平替。
     */
    fun analyzeMissingColors(
        cells: Array<Array<MappedPixel>>,
        inStockHexes: Set<String>,
        inStockPalette: List<PaletteColor>,
        paletteLookup: Map<String, PaletteColor>,
        cellFilter: ((Int, Int) -> Boolean)? = null
    ): List<MissingColorItem> {
        val counts = mutableMapOf<String, Int>()
        for (r in cells.indices) {
            for (c in cells[r].indices) {
                val px = cells[r][c]
                if (px.isExternal || px.key == com.perlerbeads.generator.model.TRANSPARENT_KEY) continue
                if (cellFilter != null && !cellFilter(r, c)) continue
                val hex = px.colorHex.uppercase()
                counts[hex] = (counts[hex] ?: 0) + 1
            }
        }

        val missingList = mutableListOf<MissingColorItem>()
        for ((hex, count) in counts) {
            if (!inStockHexes.contains(hex)) {
                val pc = paletteLookup[hex]
                val key = pc?.key ?: hex
                val name = pc?.name ?: ""
                val sub = findBestSubstitute(hex, pc?.rgb, inStockPalette)
                missingList.add(
                    MissingColorItem(
                        hex = hex,
                        key = key,
                        name = name,
                        requiredCount = count,
                        substitute = sub
                    )
                )
            }
        }

        return missingList.sortedByDescending { it.requiredCount }
    }
}
