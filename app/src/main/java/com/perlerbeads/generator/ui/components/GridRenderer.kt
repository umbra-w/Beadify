package com.perlerbeads.generator.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.TRANSPARENT_KEY

/** 把网格绘制为 Bitmap（预览与导出共用）。 */
object GridRenderer {
    private const val EXTERNAL_COLOR = 0xFFDCDCDC.toInt() // 浅灰：外部背景
    private const val GRID_LINE_COLOR = 0x44FFFFFF.toInt()

    fun parseHex(hex: String): Int = try {
        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (e: IllegalArgumentException) {
        Color.MAGENTA
    }

    /**
     * 预览渲染：自动按最大边长控制内存（默认最长边 ≤ 2048px）。
     */
    fun renderCapped(
        grid: GridData,
        maxDim: Int = 2048,
        showBorders: Boolean = true,
        showKeys: Boolean = false
    ): Bitmap {
        val maxEdge = maxOf(grid.n, grid.m)
        val cellSize = maxOf(2, minOf(32, maxDim / maxEdge))
        return render(grid, cellSize, showBorders, showKeys)
    }

    /**
     * @param cellSize 每格像素
     * @param showBorders 是否绘制网格线
     * @param showKeys 是否在每个内部单元中央绘制色号 Key
     */
    fun render(
        grid: GridData,
        cellSize: Int = 24,
        showBorders: Boolean = true,
        showKeys: Boolean = false
    ): Bitmap {
        val width = grid.n * cellSize
        val height = grid.m * cellSize
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val linePaint = Paint().apply {
            color = GRID_LINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val keyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            textAlign = Paint.Align.CENTER
        }
        val keyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
        }

        val keyTextSize = cellSize * 0.62f
        keyStroke.textSize = keyTextSize
        keyFill.textSize = keyTextSize

        for (row in 0 until grid.m) {
            for (col in 0 until grid.n) {
                val cell = grid.cells[row][col]
                val left = col * cellSize
                val top = row * cellSize
                cellPaint.color = if (cell.isExternal) EXTERNAL_COLOR else parseHex(cell.colorHex)
                canvas.drawRect(
                    left.toFloat(), top.toFloat(),
                    (left + cellSize).toFloat(), (top + cellSize).toFloat(),
                    cellPaint
                )
                if (showBorders) {
                    canvas.drawRect(
                        left.toFloat(), top.toFloat(),
                        (left + cellSize).toFloat(), (top + cellSize).toFloat(),
                        linePaint
                    )
                }
                if (showKeys && !cell.isExternal && cell.key != TRANSPARENT_KEY) {
                    val cx = left + cellSize / 2f
                    val cy = top + cellSize / 2f - (keyFill.descent() + keyFill.ascent()) / 2f
                    canvas.drawText(cell.key, cx, cy, keyStroke)
                    canvas.drawText(cell.key, cx, cy, keyFill)
                }
            }
        }
        return bmp
    }
}
