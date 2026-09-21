package com.perlerbeads.generator.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.perlerbeads.generator.model.CircleGeometry
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData
import com.perlerbeads.generator.ui.components.GridRenderer
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 图纸 PDF 导出：A4 竖版、1 格 = 5mm 实物比例、自动分页，
 * 每页页脚带 10mm 校准刻度线（打印后实测核对缩放），末页为颜色统计表。
 */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val HEADER_H = 30f
    private const val FOOTER_H = 34f
    /** 1 格 = 5mm；1pt = 25.4/72 mm → 5mm ≈ 14.17pt */
    private const val CELL_PT = 5f * 72f / 25.4f

    /** 每页可容纳的格子数（列/行）。 */
    fun pageCapacity(): Pair<Int, Int> {
        val printW = PAGE_W - 2 * MARGIN
        val printH = PAGE_H - 2 * MARGIN - HEADER_H - FOOTER_H
        return floor(printW / CELL_PT).toInt() to floor(printH / CELL_PT).toInt()
    }

    /**
     * 生成图纸 PDF 字节。
     * @param circle 圆形画板几何；圆框外格子按空白处理
     */
    fun buildPatternPdf(
        grid: GridData,
        circle: CircleGeometry?,
        stats: List<ColorStatRow>,
        totalCount: Int
    ): ByteArray {
        val (colsPerPage, rowsPerPage) = pageCapacity()
        val tiles = planPdfTiles(grid.n, grid.m, colsPerPage, rowsPerPage)
        val doc = PdfDocument()

        val scope: ((Int, Int) -> Boolean)? = circle?.let { geo -> { r: Int, c: Int -> geo.contains(r, c) } }
        val pageInfoDrawn = "${grid.n}×${grid.m}"

        tiles.forEach { tile ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, tile.index + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val subCells = Array(tile.rows) { r ->
                Array(tile.cols) { c ->
                    val gr = tile.rowStart + r
                    val gc = tile.colStart + c
                    val cell = grid.cells[gr][gc]
                    if (scope != null && !scope(gr, gc)) transparentColorData else cell
                }
            }
            val subGrid = GridData(tile.cols, tile.rows, subCells, emptySet(), GridShape.SQUARE)

            // 以 3× 渲染位图（≈214dpi @1:1 打印），再缩放贴到页面上
            val renderPx = 3
            val tileBmp = GridRenderer.render(
                subGrid,
                cellSize = renderPx,
                showBorders = true,
                showKeys = true,
                hideWhiteKeys = true,
                mirror = false,
                externalColor = Color.WHITE
            )

            val title = "拼豆图纸 $pageInfoDrawn  第 ${tile.index + 1}/${tiles.size} 页" +
                "（第 ${tile.colStart * colsPerPage + 1}-${tile.colStart * colsPerPage + tile.cols} 列）"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
            canvas.drawText(title, MARGIN, MARGIN + 10f, textPaint)

            val dst = RectF(
                MARGIN,
                MARGIN + HEADER_H,
                MARGIN + tile.cols * CELL_PT,
                MARGIN + HEADER_H + tile.rows * CELL_PT
            )
            canvas.drawBitmap(tileBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            tileBmp.recycle()

            val border = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
            canvas.drawRect(dst, border)

            drawFooter(canvas, tile.index + 1, tiles.size)
            doc.finishPage(page)
        }

        // ---------- 统计页 ----------
        val rowsPerStatsPage = 46
        val statPages = ceil(stats.size / rowsPerStatsPage.toDouble()).toInt().coerceAtLeast(1)
        val totalPages = tiles.size + statPages
        for (sp in 0 until statPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, tiles.size + sp + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 14f; isFakeBoldText = true
            }
            canvas.drawText("颜色统计（共 $totalCount 粒）", MARGIN, MARGIN + 12f, titlePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
            val swatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f
            }
            val start = sp * rowsPerStatsPage
            val end = minOf(stats.size, start + rowsPerStatsPage)
            for (i in start until end) {
                val row = stats[i]
                val y = MARGIN + HEADER_H + (i - start) * 16f
                swatch.color = GridRenderer.parseHex(row.hex)
                canvas.drawRect(MARGIN, y, MARGIN + 12f, y + 12f, swatch)
                canvas.drawRect(MARGIN, y, MARGIN + 12f, y + 12f, border)
                canvas.drawText("${row.key}  ${row.hex}", MARGIN + 20f, y + 10f, textPaint)
                val c = "${row.count}"
                canvas.drawText(c, PAGE_W - MARGIN - textPaint.measureText(c), y + 10f, textPaint)
            }
            if (start >= end) canvas.drawText("无颜色数据", MARGIN, MARGIN + HEADER_H + 20f, textPaint)
            drawFooter(canvas, tiles.size + sp + 1, totalPages)
            doc.finishPage(page)
        }

        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** 页脚：页码 + 10mm 校准刻度线（两段 5mm，打印后实测应为 10mm）。 */
    private fun drawFooter(canvas: Canvas, pageNo: Int, totalPages: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 8f }
        val y = PAGE_H - MARGIN / 2f
        val label = "第 $pageNo/$totalPages 页"
        canvas.drawText(label, MARGIN, y, textPaint)

        // 校准线：右下角 10mm = 2×CELL_PT
        val x1 = PAGE_W - MARGIN - 2 * CELL_PT
        val line = Paint().apply { color = Color.DKGRAY; strokeWidth = 0.6f }
        canvas.drawLine(x1, y - 3f, x1 + 2 * CELL_PT, y - 3f, line)
        canvas.drawLine(x1, y - 5f, x1, y - 1f, line)
        canvas.drawLine(x1 + CELL_PT, y - 4.5f, x1 + CELL_PT, y - 1.5f, line)
        canvas.drawLine(x1 + 2 * CELL_PT, y - 5f, x1 + 2 * CELL_PT, y - 1f, line)
        val note = "打印校准：此线段应实测为 10mm"
        canvas.drawText(note, x1 - textPaint.measureText(note) - 8f, y, textPaint)
    }

    /** 统计一行是否为有效豆（供导出侧复用）。 */
    fun isBeadCell(cell: com.perlerbeads.generator.model.MappedPixel): Boolean =
        !cell.isExternal && cell.key != TRANSPARENT_KEY
}
