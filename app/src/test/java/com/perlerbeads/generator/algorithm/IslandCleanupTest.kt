package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandCleanupTest {

    private fun cell(key: String, hex: String = "#000000", isExternal: Boolean = false) =
        MappedPixel(key, hex, isExternal)

    @Test
    fun singlePixelStraySpeckleIsSmoothed() {
        // 5x5 矩阵，全部是 WHITE，只有中心 (2, 2) 是一颗孤立的 RED 杂点
        val matrix = Array(5) { r ->
            Array(5) { c ->
                if (r == 2 && c == 2) cell("RED", "#FF0000") else cell("WHITE", "#FFFFFF")
            }
        }

        assertEquals(1, IslandCleanup.countSmallIslands(matrix, maxSize = 2))

        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 1)
        assertEquals("WHITE", cleaned[2][2].key)
        assertEquals(0, IslandCleanup.countSmallIslands(cleaned, maxSize = 2))
    }

    @Test
    fun twoPixelIslandIsSmoothedWhenThresholdIsTwo() {
        // 6x6 矩阵，中心 (2,2) 与 (2,3) 组成一对 2 格孤立点
        val matrix = Array(6) { r ->
            Array(6) { c ->
                if (r == 2 && (c == 2 || c == 3)) cell("RED", "#FF0000") else cell("WHITE", "#FFFFFF")
            }
        }

        assertEquals(1, IslandCleanup.countSmallIslands(matrix, maxSize = 2))

        // 当阈值为 1 时，2 格孤岛不被清理
        val cleaned1 = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 1)
        assertEquals("RED", cleaned1[2][2].key)

        // 当阈值为 2 时，2 格孤岛被平滑消除
        val cleaned2 = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 2)
        assertEquals("WHITE", cleaned2[2][2].key)
        assertEquals("WHITE", cleaned2[2][3].key)
        assertEquals(0, IslandCleanup.countSmallIslands(cleaned2, maxSize = 2))
    }

    @Test
    fun largerRegionsPreserved() {
        // 3 格连通块不属于噪点
        val matrix = Array(6) { r ->
            Array(6) { c ->
                if (r == 2 && c in 2..4) cell("RED", "#FF0000") else cell("WHITE", "#FFFFFF")
            }
        }
        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 2)
        assertEquals("RED", cleaned[2][2].key)
        assertEquals("RED", cleaned[2][3].key)
        assertEquals("RED", cleaned[2][4].key)
    }

    @Test
    fun diagonalThinlinesProtected() {
        // 对角线连续的黑线条：(0,0), (1,1), (2,2), (3,3)
        // 开启 protectDiagonalLines 时线条不应被当成孤点抹掉
        val matrix = Array(4) { r ->
            Array(4) { c ->
                if (r == c) cell("BLACK", "#000000") else cell("WHITE", "#FFFFFF")
            }
        }

        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 2, protectDiagonalLines = true)
        for (i in 0 until 4) {
            assertEquals("BLACK", cleaned[i][i].key)
        }
    }

    @Test
    fun transparentAndExternalCellsIgnored() {
        val matrix = Array(3) { r ->
            Array(3) { c ->
                if (r == 0) transparentColorData
                else if (r == 1 && c == 1) cell("RED", "#FF0000")
                else cell("WHITE", "#FFFFFF")
            }
        }
        val cleaned = IslandCleanup.cleanupSpeckles(matrix, maxIslandSize = 1)
        assertEquals("WHITE", cleaned[1][1].key)
        assertEquals(TRANSPARENT_KEY, cleaned[0][0].key)
    }
}
