package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY

/**
 * 孤立噪点与飞点清理器（基于连通域分析与邻域感知平滑）。
 *
 * 核心目标：
 * 1. 消除手工痛点：清理因图像缩放或 JPEG 伪影产生的 1~2 格孤立杂色点，避免玩家为 1 颗豆专门拆一包。
 * 2. 细线保护（Thinlines Protection）：识别对角线连续的单像素描边线条，保护艺术轮廓不被误抹。
 * 3. 确定性与安全性：纯 Kotlin 实现，深拷贝输入矩阵，提供原地或复制处理。
 */
object IslandCleanup {

    /**
     * 清理网格中面积 <= maxIslandSize 的孤立飞点。
     *
     * @param cells 输入网格矩阵
     * @param maxIslandSize 孤岛阈值（1 表示仅清理单格孤立点；2 表示清理 1~2 格连通块；<=0 表示不清理）
     * @param protectDiagonalLines 是否保护 8-邻域对角连续细线（默认 true）
     * @return 清理后的新网格矩阵
     */
    fun cleanupSpeckles(
        cells: Array<Array<MappedPixel>>,
        maxIslandSize: Int = 2,
        protectDiagonalLines: Boolean = true
    ): Array<Array<MappedPixel>> {
        val m = cells.size
        if (m == 0 || maxIslandSize <= 0) return cells
        val n = cells[0].size
        if (n == 0) return cells

        // 深拷贝一份新矩阵，避免修改源数据
        val out = Array(m) { r ->
            Array(n) { c ->
                val p = cells[r][c]
                MappedPixel(p.key, p.colorHex, p.isExternal)
            }
        }

        val visited = Array(m) { BooleanArray(n) }
        val dr = intArrayOf(-1, 1, 0, 0)
        val dc = intArrayOf(0, 0, -1, 1)

        // 8 邻域偏移
        val dr8 = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        val dc8 = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)

        for (r in 0 until m) {
            for (c in 0 until n) {
                if (visited[r][c]) continue
                val start = out[r][c]
                if (start.isExternal || start.key == TRANSPARENT_KEY) {
                    visited[r][c] = true
                    continue
                }

                // 1. BFS 搜寻 4-连通块
                val component = ArrayList<Pair<Int, Int>>()
                val queue = ArrayDeque<Pair<Int, Int>>()
                val compKey = start.key
                queue.add(Pair(r, c))
                visited[r][c] = true

                while (queue.isNotEmpty()) {
                    val (cr, cc) = queue.removeFirst()
                    component.add(Pair(cr, cc))

                    for (d in 0 until 4) {
                        val nr = cr + dr[d]
                        val nc = cc + dc[d]
                        if (nr in 0 until m && nc in 0 until n && !visited[nr][nc]) {
                            val neighbor = out[nr][nc]
                            if (!neighbor.isExternal && neighbor.key == compKey) {
                                visited[nr][nc] = true
                                queue.add(Pair(nr, nc))
                            }
                        }
                    }
                }

                // 2. 面积超过上限则不是噪点，保留
                if (component.size > maxIslandSize) {
                    continue
                }

                // 3. 细线保护：若是 1 像素点，且在 8-邻域中拥有 >= 2 个同色邻居（呈对角线连续趋势），则认为是线条交叉或对角线
                if (protectDiagonalLines && component.size == 1) {
                    val (cr, cc) = component[0]
                    var sameColorNeighbors8 = 0
                    for (d in 0 until 8) {
                        val nr = cr + dr8[d]
                        val nc = cc + dc8[d]
                        if (nr in 0 until m && nc in 0 until n) {
                            val neighbor = out[nr][nc]
                            if (!neighbor.isExternal && neighbor.key == compKey) {
                                sameColorNeighbors8++
                            }
                        }
                    }
                    if (sameColorNeighbors8 >= 1) {
                        continue // 保护连续线条（包含对角线端点与节点）
                    }
                }

                // 4. 搜寻孤岛周围的外围相邻像素，统计主导颜色
                val neighborFreq = HashMap<String, Int>()
                val neighborHex = HashMap<String, String>()

                for ((cr, cc) in component) {
                    for (d in 0 until 4) {
                        val nr = cr + dr[d]
                        val nc = cc + dc[d]
                        if (nr in 0 until m && nc in 0 until n) {
                            val nb = out[nr][nc]
                            if (nb.key != compKey && !nb.isExternal && nb.key != TRANSPARENT_KEY) {
                                neighborFreq[nb.key] = (neighborFreq[nb.key] ?: 0) + 1
                                neighborHex[nb.key] = nb.colorHex
                            }
                        }
                    }
                }

                // 如果周围全是透明或边界，尝试寻找 8 邻域
                if (neighborFreq.isEmpty()) {
                    for ((cr, cc) in component) {
                        for (d in 0 until 8) {
                            val nr = cr + dr8[d]
                            val nc = cc + dc8[d]
                            if (nr in 0 until m && nc in 0 until n) {
                                val nb = out[nr][nc]
                                if (nb.key != compKey && !nb.isExternal && nb.key != TRANSPARENT_KEY) {
                                    neighborFreq[nb.key] = (neighborFreq[nb.key] ?: 0) + 1
                                    neighborHex[nb.key] = nb.colorHex
                                }
                            }
                        }
                    }
                }

                // 5. 多数投票 + 最小色差平滑替换
                if (neighborFreq.isNotEmpty()) {
                    val origRgb = hexToRgb(start.colorHex) ?: RgbColor(0, 0, 0)
                    // 频次最多且感知色差最小者胜出
                    val bestKey = neighborFreq.keys.maxWithOrNull { k1, k2 ->
                        val f1 = neighborFreq[k1] ?: 0
                        val f2 = neighborFreq[k2] ?: 0
                        if (f1 != f2) {
                            f1.compareTo(f2)
                        } else {
                            val rgb1 = hexToRgb(neighborHex[k1] ?: "#000000") ?: RgbColor(0, 0, 0)
                            val rgb2 = hexToRgb(neighborHex[k2] ?: "#000000") ?: RgbColor(0, 0, 0)
                            val d1 = ColorMath.oklabDistance(origRgb, rgb1)
                            val d2 = ColorMath.oklabDistance(origRgb, rgb2)
                            // 色差越小越优
                            d2.compareTo(d1)
                        }
                    }

                    if (bestKey != null) {
                        val bestHex = neighborHex[bestKey] ?: start.colorHex
                        for ((cr, cc) in component) {
                            out[cr][cc] = MappedPixel(bestKey, bestHex, false)
                        }
                    }
                }
            }
        }

        return out
    }

    /** 统计当前网格中面积 <= maxSize 的有效实体小孤岛数量（用于测试与算法打分） */
    fun countSmallIslands(cells: Array<Array<MappedPixel>>, maxSize: Int = 2): Int {
        val m = cells.size
        if (m == 0) return 0
        val n = cells[0].size
        if (n == 0) return 0

        val visited = Array(m) { BooleanArray(n) }
        val dr = intArrayOf(-1, 1, 0, 0)
        val dc = intArrayOf(0, 0, -1, 1)
        var count = 0

        for (r in 0 until m) {
            for (c in 0 until n) {
                if (visited[r][c]) continue
                val start = cells[r][c]
                if (start.isExternal || start.key == TRANSPARENT_KEY) {
                    visited[r][c] = true
                    continue
                }

                var size = 0
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(Pair(r, c))
                visited[r][c] = true

                while (queue.isNotEmpty()) {
                    val (cr, cc) = queue.removeFirst()
                    size++
                    for (d in 0 until 4) {
                        val nr = cr + dr[d]
                        val nc = cc + dc[d]
                        if (nr in 0 until m && nc in 0 until n && !visited[nr][nc]) {
                            val nb = cells[nr][nc]
                            if (!nb.isExternal && nb.key == start.key) {
                                visited[nr][nc] = true
                                queue.add(Pair(nr, nc))
                            }
                        }
                    }
                }

                if (size in 1..maxSize) {
                    count++
                }
            }
        }
        return count
    }
}
