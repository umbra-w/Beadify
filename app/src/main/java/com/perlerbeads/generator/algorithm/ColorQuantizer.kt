package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor

/**
 * 受控色数限制器（基于 Oklab 感知空间的中位切割算法 Median Cut）。
 *
 * 核心目标：
 * 1. 严格防止「色号爆炸」：将整图使用的拼豆色号控制在用户指定的色数上限（如 16 / 24 / 32 / 48 色）内。
 * 2. 感知保真度优异：在 Oklab 空间进行颜色聚类，相比 RGB 空间能更自然地保留画面中的微小高饱和特征（如唇色、高光）。
 * 3. 纯 Kotlin 实现，零依赖，高确定性。
 */
object ColorQuantizer {

    private data class ColorPoint(
        val rgb: RgbColor,
        val l: Double,
        val a: Double,
        val b: Double,
        val count: Int
    )

    private class ColorBox(val points: MutableList<ColorPoint>) {
        var count: Int = points.sumOf { it.count }
        var minL: Double = 0.0; var maxL: Double = 0.0
        var minA: Double = 0.0; var maxA: Double = 0.0
        var minB: Double = 0.0; var maxB: Double = 0.0

        init {
            recalculateBounds()
        }

        fun recalculateBounds() {
            count = points.sumOf { it.count }
            if (points.isEmpty()) return
            minL = points[0].l; maxL = points[0].l
            minA = points[0].a; maxA = points[0].a
            minB = points[0].b; maxB = points[0].b
            for (p in points) {
                if (p.l < minL) minL = p.l
                if (p.l > maxL) maxL = p.l
                if (p.a < minA) minA = p.a
                if (p.a > maxA) maxA = p.a
                if (p.b < minB) minB = p.b
                if (p.b > maxB) maxB = p.b
            }
        }

        val spanL: Double get() = maxL - minL
        val spanA: Double get() = maxA - minA
        val spanB: Double get() = maxB - minB

        /** 加权离散体积：用于优先选择分布范围最广、数量最多的箱体进行切分 */
        val volume: Double get() = (spanL * 1.5 + spanA + spanB) * (count.coerceAtLeast(1))

        /** 找出离散跨度最大的坐标轴：0 -> L, 1 -> a, 2 -> b */
        val longestAxis: Int
            get() {
                val sl = spanL * 1.5
                val sa = spanA
                val sb = spanB
                return if (sl >= sa && sl >= sb) 0 else if (sa >= sb) 1 else 2
            }

        /** 计算箱体内颜色的加权中心 RGB */
        fun averageRgb(): RgbColor {
            if (points.isEmpty()) return RgbColor(128, 128, 128)
            var sumR = 0L; var sumG = 0L; var sumB = 0L
            for (p in points) {
                sumR += p.rgb.r * p.count
                sumG += p.rgb.g * p.count
                sumB += p.rgb.b * p.count
            }
            val total = count.coerceAtLeast(1)
            return RgbColor(
                (sumR / total).toInt().coerceIn(0, 255),
                (sumG / total).toInt().coerceIn(0, 255),
                (sumB / total).toInt().coerceIn(0, 255)
            )
        }
    }

    /**
     * 根据输入图像各单元格的代表色，计算限制在 maxColors 个以内的受控拼豆子色板。
     *
     * @param reps 图像网格下采样得到的各单元格代表颜色（可含 null/透明）
     * @param fullPalette 当前激活的完整候选色板
     * @param maxColors 目标色数上限（<= 0 表示不限制，直接返回 fullPalette）
     * @return 过滤出的候选色板子集（大小保证 <= maxColors 且包含至少 1 个回退色）
     */
    fun computeControlledPalette(
        reps: Array<out Array<out RgbColor?>>,
        fullPalette: List<PaletteColor>,
        maxColors: Int
    ): List<PaletteColor> {
        if (fullPalette.isEmpty() || maxColors <= 0 || maxColors >= fullPalette.size) {
            return fullPalette
        }

        // 1. 统计下采样网格中各 RGB 颜色的频次
        val freqMap = HashMap<RgbColor, Int>()
        for (row in reps) {
            for (cell in row) {
                if (cell != null) {
                    freqMap[cell] = (freqMap[cell] ?: 0) + 1
                }
            }
        }
        if (freqMap.isEmpty()) return fullPalette

        // 若原图实际颜色种类本就小于等于限制，直接映射即可
        val distinctColors = freqMap.keys.toList()
        if (distinctColors.size <= maxColors) {
            val mapped = distinctColors.map { ColorMath.findClosestPaletteColor(it, fullPalette) }.distinctBy { it.key }
            return if (mapped.isNotEmpty()) mapped else fullPalette
        }

        // 2. 转换至 Oklab 空间并构造点集
        val points = distinctColors.map { rgb ->
            val lab = ColorMath.rgbToOklab(rgb)
            ColorPoint(rgb, lab.l, lab.a, lab.b, freqMap[rgb] ?: 1)
        }.toMutableList()

        // 3. 中位切割细分箱体
        val boxes = mutableListOf(ColorBox(points))
        val targetK = maxColors.coerceIn(1, fullPalette.size)

        while (boxes.size < targetK) {
            // 每次选取体积最大的可切分箱子进行分裂
            val splittable = boxes.filter { it.points.size >= 2 }.maxByOrNull { it.volume } ?: break
            boxes.remove(splittable)

            val axis = splittable.longestAxis
            when (axis) {
                0 -> splittable.points.sortBy { it.l }
                1 -> splittable.points.sortBy { it.a }
                else -> splittable.points.sortBy { it.b }
            }

            // 按频次权重寻找中位数切分点
            val targetHalfCount = splittable.count / 2
            var acc = 0
            var splitIdx = 1
            for (i in 0 until splittable.points.size - 1) {
                acc += splittable.points[i].count
                if (acc >= targetHalfCount) {
                    splitIdx = i + 1
                    break
                }
            }
            splitIdx = splitIdx.coerceIn(1, splittable.points.size - 1)

            val leftList = splittable.points.subList(0, splitIdx).toMutableList()
            val rightList = splittable.points.subList(splitIdx, splittable.points.size).toMutableList()

            boxes.add(ColorBox(leftList))
            boxes.add(ColorBox(rightList))
        }

        // 4. 将每个箱体的代表色投影到完整调色板中最近的实体拼豆色号
        val selected = LinkedHashSet<PaletteColor>()
        for (box in boxes) {
            val avg = box.averageRgb()
            val closest = ColorMath.findClosestPaletteColor(avg, fullPalette)
            selected.add(closest)
        }

        // 若投影去重后超过上限（罕见），按箱体像素数量从大到小取前 maxColors
        val result = selected.toList()
        return if (result.size > targetK) {
            result.take(targetK)
        } else if (result.isEmpty()) {
            fullPalette
        } else {
            result
        }
    }
}
