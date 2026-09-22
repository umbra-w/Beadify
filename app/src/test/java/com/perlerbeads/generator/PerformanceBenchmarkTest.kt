package com.perlerbeads.generator

import com.perlerbeads.generator.algorithm.BoardSlice
import com.perlerbeads.generator.algorithm.ColorMath
import com.perlerbeads.generator.algorithm.sliceBoards
import com.perlerbeads.generator.data.CsvCodec
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class PerformanceBenchmarkTest {

    @Test
    fun benchmarkOklabDistanceCalculations() {
        // 100,000 次 Oklab 颜色距离计算耗时基准
        val c1 = RgbColor(230, 45, 80)
        val c2 = RgbColor(12, 180, 240)
        // 预热 JIT
        repeat(1000) { ColorMath.oklabDistance(c1, c2) }

        val elapsed = measureTimeMillis {
            var sum = 0.0
            for (i in 0 until 100_000) {
                sum += ColorMath.oklabDistance(c1, c2)
            }
            assertTrue(sum > 0)
        }
        println("[Benchmark] 100,000 次 Oklab 距离计算耗时: ${elapsed}ms")
        assertTrue("Oklab 计算应在 500ms 内完成", elapsed < 500)
    }

    @Test
    fun benchmarkBoardSlicingLargeGrid() {
        // 100x100 网格，切片为 28x28 板（共 16 块板）耗时基准
        val grid = GridData(
            100, 100,
            Array(100) { Array(100) { MappedPixel("A01", "#FF0000", false) } },
            emptySet(), GridShape.SQUARE
        )
        val elapsed = measureTimeMillis {
            repeat(50) {
                val slices = sliceBoards(grid, 28)
                assertTrue(slices.isNotEmpty())
            }
        }
        println("[Benchmark] 50 次 100x100 大图切片总耗时: ${elapsed}ms (平均单次 ${elapsed / 50.0}ms)")
        assertTrue("切片应在 300ms 内完成", elapsed < 300)
    }

    @Test
    fun benchmarkCsvRoundTrip() {
        // 60x60 网格（3,600 颗珠子）CSV 导出与解析耗时基准
        val grid = GridData(
            60, 60,
            Array(60) { r -> Array(60) { c -> MappedPixel("A${(r * 60 + c) % 50}", "#AABBCC", false) } },
            emptySet(), GridShape.SQUARE
        )
        val elapsed = measureTimeMillis {
            repeat(20) {
                val csv = CsvCodec.exportPatternCsv(grid)
                val restored = CsvCodec.importPatternCsv(csv)
                assertTrue(restored.n == 60)
            }
        }
        println("[Benchmark] 20 次 60x60 网格 CSV 编解码总耗时: ${elapsed}ms (平均单次 ${elapsed / 20.0}ms)")
        assertTrue("CSV 编解码应在 500ms 内完成", elapsed < 500)
    }
}
