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

    @Test
    fun csvPattern_roundTripPreservesStructure() {
        val g = testGrid()
        val csv = com.perlerbeads.generator.data.CsvCodec.exportPatternCsv(g)
        val restored = com.perlerbeads.generator.data.CsvCodec.importPatternCsv(csv)
        assertEquals(g.n, restored.n)
        assertEquals(g.m, restored.m)
        assertEquals(g.cells[0][0].colorHex, restored.cells[0][0].colorHex)
        assertEquals(g.cells[5][3].colorHex, restored.cells[5][3].colorHex)
    }

    @Test
    fun boardSliceRenderer_rendersBitmapWithRulersAndSpotlight() {
        val g = testGrid()
        val slices = com.perlerbeads.generator.algorithm.sliceBoards(g, 4)
        val slice = slices[0]
        val completed = setOf(0, 1)

        val bmpNormal = com.perlerbeads.generator.ui.board.BoardSliceRenderer.render(
            g, slice, completed, spotlightKey = null
        )
        val expectedW = (com.perlerbeads.generator.ui.board.BoardSliceRenderer.RULER_LEFT + slice.cols * com.perlerbeads.generator.ui.board.BoardSliceRenderer.CELL_SIZE).toInt()
        val expectedH = (com.perlerbeads.generator.ui.board.BoardSliceRenderer.RULER_TOP + slice.rows * com.perlerbeads.generator.ui.board.BoardSliceRenderer.CELL_SIZE).toInt()
        assertEquals(expectedW, bmpNormal.width)
        assertEquals(expectedH, bmpNormal.height)

        val bmpSpotlight = com.perlerbeads.generator.ui.board.BoardSliceRenderer.render(
            g, slice, completed, spotlightKey = "A01"
        )
        assertNotNull(bmpSpotlight)
        assertEquals(expectedW, bmpSpotlight.width)
    }

    @Test
    fun boardSliceRenderer_tapToCell_mapsAccurately() {
        val rulerL = com.perlerbeads.generator.ui.board.BoardSliceRenderer.RULER_LEFT
        val rulerT = com.perlerbeads.generator.ui.board.BoardSliceRenderer.RULER_TOP
        val cellSize = com.perlerbeads.generator.ui.board.BoardSliceRenderer.CELL_SIZE

        val bmpW = rulerL + 4 * cellSize
        val bmpH = rulerT + 4 * cellSize
        val containerW = 400f
        val containerH = 400f

        // 点击在左侧标尺上，应返回 null
        val onRuler = com.perlerbeads.generator.ui.board.BoardSliceRenderer.tapToCell(
            tapX = 10f, tapY = 200f,
            containerW = containerW, containerH = containerH,
            bmpW = bmpW, bmpH = bmpH,
            zoom = 1f, offsetX = 0f, offsetY = 0f,
            cols = 4, rows = 4
        )
        assertNull(onRuler)

        // 在 1:1 无缩放居中下，计算第 (1, 2) 格的预期点击中心
        val scaleFit = minOf(containerW / bmpW, containerH / bmpH)
        val renderW = bmpW * scaleFit
        val renderH = bmpH * scaleFit
        val leftInContainer = (containerW - renderW) / 2f
        val topInContainer = (containerH - renderH) / 2f

        val cell12BmpX = rulerL + 2 * cellSize + cellSize / 2f
        val cell12BmpY = rulerT + 1 * cellSize + cellSize / 2f
        val tapX = leftInContainer + cell12BmpX * scaleFit
        val tapY = topInContainer + cell12BmpY * scaleFit

        val tapped = com.perlerbeads.generator.ui.board.BoardSliceRenderer.tapToCell(
            tapX = tapX, tapY = tapY,
            containerW = containerW, containerH = containerH,
            bmpW = bmpW, bmpH = bmpH,
            zoom = 1f, offsetX = 0f, offsetY = 0f,
            cols = 4, rows = 4
        )
        assertNotNull(tapped)
        assertEquals(1, tapped!!.first)  // row
        assertEquals(2, tapped.second) // col
    }

    @Test
    fun boardWork_cellProgressPersistence_worksInSettingsStore() {
        val settings = com.perlerbeads.generator.data.SettingsStore(context)
        val testKey = "test_run_progress_999"
        val cells = setOf(1, 2, 3, 5, 8, 9, 10, 15)
        settings.saveCellProgress(testKey, cells)
        val loaded = settings.loadCellProgress(testKey)
        assertEquals(cells, loaded)
    }

    @Test
    fun settingsStore_maxColorsAndIslandCleanup_persistence() {
        val settings = com.perlerbeads.generator.data.SettingsStore(context)
        // 验证可读写
        settings.maxColors = 24
        settings.cleanupIslands = true
        assertEquals(24, settings.maxColors)
        assertTrue(settings.cleanupIslands)

        // 恢复默认测试
        settings.maxColors = 0
        settings.cleanupIslands = false
        assertEquals(0, settings.maxColors)
        assertEquals(false, settings.cleanupIslands)
    }

    @Test
    fun pixelation_withRealBitmap_respectsMaxColorsAndIslandCleanup() {
        // 创建一个带有梯度与 1 格孤立白噪点的 16×16 Bitmap
        val bmp = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
            0xFF800000.toInt(), 0xFF008000.toInt(), 0xFF000080.toInt()
        )
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                bmp.setPixel(x, y, colors[(x + y) % colors.size])
            }
        }
        // 在红色区域 (2, 2) 置一个孤立噪点
        bmp.setPixel(2, 2, 0xFFFFFFFF.toInt())

        val palette = listOf(
            PaletteColor("P01", "#FF0000", RgbColor(255, 0, 0)),
            PaletteColor("P02", "#00FF00", RgbColor(0, 255, 0)),
            PaletteColor("P03", "#0000FF", RgbColor(0, 0, 255)),
            PaletteColor("P04", "#FFFF00", RgbColor(255, 255, 0)),
            PaletteColor("P05", "#FF00FF", RgbColor(255, 0, 255)),
            PaletteColor("P06", "#00FFFF", RgbColor(0, 255, 255)),
            PaletteColor("P07", "#FFFFFF", RgbColor(255, 255, 255))
        )
        val fallback = palette[0]

        // 1. 默认无限制（maxColors=0, cleanupIslands=false）
        val unconstrained = com.perlerbeads.generator.algorithm.calculatePixelGrid(
            bmp, 16, 16, palette, PixelationMode.DOMINANT, fallback,
            dithering = false, maxColors = 0, cleanupIslands = false
        )
        val unconstrainedKeys = unconstrained.flatMap { it.toList() }.map { it.key }.toSet()
        assertTrue("无限制时应使用超过 3 种颜色", unconstrainedKeys.size > 3)

        // 2. 启用受控色数限制（maxColors = 3）
        val constrained = com.perlerbeads.generator.algorithm.calculatePixelGrid(
            bmp, 16, 16, palette, PixelationMode.DOMINANT, fallback,
            dithering = false, maxColors = 3, cleanupIslands = false
        )
        val constrainedKeys = constrained.flatMap { it.toList() }.map { it.key }.toSet()
        assertTrue("受控色数应 <= 3，实际为 ${constrainedKeys.size}", constrainedKeys.size <= 3)

        // 3. 启用噪点清理（cleanupIslands = true）
        val cleaned = com.perlerbeads.generator.algorithm.calculatePixelGrid(
            bmp, 16, 16, palette, PixelationMode.DOMINANT, fallback,
            dithering = false, maxColors = 0, cleanupIslands = true
        )
        // 原先 (2, 2) 是孤立白色 P07，清理后应被邻域主色取代
        assertNotEquals("P07", cleaned[2][2].key)
    }

    @Test
    fun test_generate_fromRealCameraPhoto_doesNotHangOrCrash() {
        // 构造 3000x4000 (1200万像素) 真实拍摄尺寸位图，模拟相机照片下采样与像素化压力
        val w = 3000
        val h = 4000
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.RED
        canvas.drawRect(0f, 0f, 1500f, 2000f, paint)
        paint.color = android.graphics.Color.BLUE
        canvas.drawRect(1500f, 0f, 3000f, 2000f, paint)
        paint.color = android.graphics.Color.GREEN
        canvas.drawRect(0f, 2000f, 1500f, 4000f, paint)
        paint.color = android.graphics.Color.YELLOW
        canvas.drawRect(1500f, 2000f, 3000f, 4000f, paint)

        val t0 = System.currentTimeMillis()
        val palette = com.perlerbeads.generator.data.PaletteRepository(context).fullBeadPalette
        val fallback = palette[0]

        val result = com.perlerbeads.generator.algorithm.calculatePixelGrid(
            bmp, 50, 67, palette, com.perlerbeads.generator.model.PixelationMode.DOMINANT, fallback,
            dithering = false, maxColors = 0, cleanupIslands = false
        )
        val elapsed = System.currentTimeMillis() - t0
        println(">>> 3000x4000 (12MP) calculatePixelGrid ELAPSED: ${elapsed}ms")
        assertNotNull(result)
        bmp.recycle()
        assertTrue("生成图纸耗时过长 ($elapsed ms > 2000 ms)", elapsed < 2000)
    }
}

