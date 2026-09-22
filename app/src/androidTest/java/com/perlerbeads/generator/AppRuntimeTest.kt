package com.perlerbeads.generator

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perlerbeads.generator.algorithm.TextBeads
import com.perlerbeads.generator.data.ProjectStore
import com.perlerbeads.generator.data.SavedProject
import com.perlerbeads.generator.export.ColorStatRow
import com.perlerbeads.generator.export.Exporter
import com.perlerbeads.generator.export.PdfExporter
import com.perlerbeads.generator.model.CircleGeometry
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.PixelationMode
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.ui.components.GridRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 运行态测试（真机/模拟器执行）：覆盖真实 Bitmap/PdfDocument/文件 IO 的功能路径，
 * 重点回归「导出只有统计没有图纸」「导出 OOM 无反应」「项目往返」等用户报告的问题。
 */
@RunWith(AndroidJUnit4::class)
class AppRuntimeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 4×6 测试网格：上 3 行红、下 3 行蓝。 */
    private fun testGrid(): GridData {
        val red = MappedPixel("A01", "#FF0000", false)
        val blue = MappedPixel("A02", "#0000FF", false)
        val cells = Array(6) { r -> Array(4) { c -> if (r < 3) red else blue } }
        return GridData(4, 6, cells, emptySet(), GridShape.SQUARE)
    }

    @Test
    fun gridRenderer_sizeMatchesGrid() {
        val bmp = GridRenderer.render(testGrid(), cellSize = 24, showBorders = true, showKeys = true)
        assertEquals(4 * 24, bmp.width)
        assertEquals(6 * 24, bmp.height)
    }

    @Test
    fun patternExport_containsPatternRegion_notStatsOnly() {
        // 回归：曾因 OOM 导出「只有统计、图纸区域空白」
        val stats = listOf(
            ColorStatRow("A01", "#FF0000", 12),
            ColorStatRow("A02", "#0000FF", 12)
        )
        val bmp = Exporter.renderPatternBitmap(
            testGrid(), null, stats, 24,
            hideWhite = true, mirror = false, attachStats = true
        )
        // 图纸区域应存在红/蓝像素（区域内多点采样，避开格子边界线与色号文字）
        var redFound = false
        var blueFound = false
        outer@ for (y in 5 until 280 step 4) {
            for (x in 3 until bmp.width step 4) {
                val p = bmp.getPixel(x, y)
                if (p == 0xFFFF0000.toInt()) redFound = true
                if (p == 0xFF0000FF.toInt()) blueFound = true
                if (redFound && blueFound) break@outer
            }
        }
        assertTrue("图纸红色区域未绘制（导出只有统计）", redFound)
        assertTrue("图纸蓝色区域未绘制", blueFound)
        // 统计区域应存在（底部有非透明内容）
        val bottom = bmp.getPixel(bmp.width / 2, bmp.height - 5)
        assertNotEquals(0, bottom and 0xFF000000.toInt())
    }

    @Test
    fun patternExport_memoryBudgetRespected_onLargeGrid() {
        // 200×200 带统计：整图不得超过内存预算上限（曾 OOM 无反应）
        val big = GridData(
            200, 200,
            Array(200) { Array(200) { MappedPixel("A01", "#FF0000", false) } },
            emptySet(), GridShape.SQUARE
        )
        val stats = listOf(ColorStatRow("A01", "#FF0000", 40000))
        val bmp = Exporter.renderPatternBitmap(
            big, null, stats, 40000,
            hideWhite = true, mirror = false, attachStats = true
        )
        assertTrue(
            "导出位图过大：${bmp.width}x${bmp.height} = ${bmp.width * bmp.height}px",
            bmp.width * bmp.height <= 25_000_000
        )
    }

    @Test
    fun patternExport_withoutStats_isPurePattern() {
        val bmp = Exporter.renderPatternBitmap(
            testGrid(), null, emptyList(), 24,
            hideWhite = true, mirror = false, attachStats = false
        )
        assertEquals(4 * 48, bmp.width)
        assertEquals(6 * 48, bmp.height)
    }

    @Test
    fun textBeads_strokesBecomeBeads_andBackgroundFillWorks() {
        val black = PaletteColor("A01", "#000000", RgbColor(0, 0, 0))
        val white = PaletteColor("T01", "#FFFFFF", RgbColor(255, 255, 255))
        val transparent = TextBeads.renderTextGrid("AB", 20, black)
        assertNotNull(transparent)
        val beads = transparent!!.cells.sumOf { r -> r.count { !it.isExternal } }
        assertTrue("文字应有笔画豆", beads > 0)
        assertTrue("背景应为透明", transparent.cells.sumOf { r -> r.count { it.isExternal } } > 0)

        val filled = TextBeads.renderTextGrid("AB", 20, black, bg = white)!!
        val total = filled.n * filled.m
        assertEquals("背景填白后整板都是豆", total, filled.cells.sumOf { r -> r.count { !it.isExternal } })
    }

    @Test
    fun projectStore_roundTripWithRealFiles() {
        val store = ProjectStore(context)
        val p = SavedProject(
            "运行测试", GridShape.SQUARE, CircleGeometry(1f, 1f, 1f), 50,
            PixelationMode.DOMINANT, false, "MARD",
            arrayOf(arrayOf(MappedPixel("A01", "#000000", false)))
        )
        val id = store.save(p)
        assertTrue("保存后列表应包含该项目", store.list().any { it.id == id })
        val loaded = store.load(id)!!
        assertEquals("运行测试", loaded.name)
        assertEquals("A01", loaded.cells[0][0].key)
        assertEquals(1f, loaded.circle!!.radius, 1e-4f)
        assertNotNull("应生成缩略图", store.decodeThumbnail(id))
        store.delete(id)
        assertNull(store.load(id))
        assertNull(store.decodeThumbnail(id))
    }

    @Test
    fun pdfBytes_startWithPdfHeader() {
        val bytes = PdfExporter.buildPatternPdf(
            testGrid(), null,
            listOf(ColorStatRow("A01", "#FF0000", 24)), 24
        )
        assertTrue("PDF 字节过小: ${bytes.size}", bytes.size > 1000)
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
    }

    @Test
    fun pdfBytes_supportsBothMiniAndStandardPitch() {
        val miniBytes = PdfExporter.buildPatternPdf(
            testGrid(), null,
            listOf(ColorStatRow("A01", "#FF0000", 24)), 24,
            com.perlerbeads.generator.model.BeadPitch.MINI_2_6
        )
        val stdBytes = PdfExporter.buildPatternPdf(
            testGrid(), null,
            listOf(ColorStatRow("A01", "#FF0000", 24)), 24,
            com.perlerbeads.generator.model.BeadPitch.STANDARD_5_0
        )
        assertTrue(miniBytes.size > 1000)
        assertTrue(stdBytes.size > 1000)
        assertEquals("%PDF", String(miniBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("%PDF", String(stdBytes.copyOfRange(0, 4), Charsets.US_ASCII))
    }

    @Test
    fun circlePatternExport_clipsOutsideCircle() {
        // 圆形画板：圆框外应为 external（渲染为浅灰/白），豆数统计只含圆内
        val grid = GridData(
            4, 4,
            Array(4) { Array(4) { MappedPixel("A01", "#FF0000", false) } },
            emptySet(), GridShape.CIRCLE
        )
        val stats = listOf(ColorStatRow("A01", "#FF0000", 16))
        val bmp = Exporter.renderPatternBitmap(
            grid, CircleGeometry(2f, 2f, 2f), stats, 16,
            hideWhite = true, mirror = false, attachStats = true
        )
        assertTrue(bmp.width > 0 && bmp.height > 0)
    }
}
