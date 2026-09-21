package com.perlerbeads.generator.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.TRANSPARENT_KEY

/** 把网格绘制为 Bitmap（预览与导出共用）。 */
object GridRenderer {
    private const val EXTERNAL_COLOR = 0xFFDCDCDC.toInt()
    private const val GRID_LINE_COLOR = 0x44FFFFFF.toInt()

    fun parseHex(hex: String): Int = try {
        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (e: IllegalArgumentException) {
        Color.MAGENTA
    }

    /**
     * @param circleOffsetX 圆形模式下网格偏移（0..1）。0.5=居中。
     * @param circleOffsetY 同上。
     */
    fun renderCapped(
        grid: GridData,
        maxDim: Int = 2048,
        showBorders: Boolean = true,
        showKeys: Boolean = false,
        hideWhiteKeys: Boolean = true,
        circleOffsetX: Float = 0.5f,
        circleOffsetY: Float = 0.5f
    ): Bitmap {
        val maxEdge = maxOf(grid.n, grid.m)
        val cellSize = maxOf(2, minOf(32, maxDim / maxEdge))
        return render(grid, cellSize, showBorders, showKeys, hideWhiteKeys, circleOffsetX, circleOffsetY)
    }

    /**
     * @param mirror 水平镜像格子位置（美纹纸背面拼贴用）。色号文字不镜像，保持可读。
     */
    fun render(
        grid: GridData,
        cellSize: Int = 24,
        showBorders: Boolean = true,
        showKeys: Boolean = false,
        hideWhiteKeys: Boolean = true,
        circleOffsetX: Float = 0.5f,
        circleOffsetY: Float = 0.5f,
        mirror: Boolean = false
    ): Bitmap {
        val gridW = grid.n * cellSize
        val gridH = grid.m * cellSize
        val isCircle = grid.shape?.name == "CIRCLE"

        // 圆形模式下输出为正方形（直径 = min 边长），圆外透明
        val outSize = if (isCircle) minOf(gridW, gridH) else maxOf(gridW, gridH)
        val outW = if (isCircle) outSize else gridW
        val outH = if (isCircle) outSize else gridH
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 圆形模式：网格在画布内的偏移，使圆始终在画布中心
        val shiftX: Float
        val shiftY: Float
        if (isCircle) {
            // circleOffset: 0=左上对齐, 0.5=居中, 1=右下对齐
            shiftX = -(circleOffsetX * (gridW - outSize))
            shiftY = -(circleOffsetY * (gridH - outSize))
        } else {
            shiftX = 0f; shiftY = 0f
        }

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

        // 圆形模式：先绘网格到临时位图，再裁剪出圆形
        val gridBmp = if (isCircle) {
            Bitmap.createBitmap(gridW, gridH, Bitmap.Config.ARGB_8888)
        } else null
        val drawCanvas = gridBmp?.let { Canvas(it) } ?: canvas

        for (row in 0 until grid.m) {
            for (col in 0 until grid.n) {
                val cell = grid.cells[row][col]
                // 镜像时只翻转格子位置，色号文字仍按镜像后的位置正常绘制
                val drawCol = if (mirror) grid.n - 1 - col else col
                val left = drawCol * cellSize
                val top = row * cellSize
                cellPaint.color = if (cell.isExternal) EXTERNAL_COLOR else parseHex(cell.colorHex)
                drawCanvas.drawRect(
                    left.toFloat(), top.toFloat(),
                    (left + cellSize).toFloat(), (top + cellSize).toFloat(),
                    cellPaint
                )
                if (showBorders) {
                    drawCanvas.drawRect(
                        left.toFloat(), top.toFloat(),
                        (left + cellSize).toFloat(), (top + cellSize).toFloat(),
                        linePaint
                    )
                }
                if (showKeys && !cell.isExternal && cell.key != TRANSPARENT_KEY) {
                    val isWhite = hideWhiteKeys && (
                        cell.colorHex.uppercase() == "#FFFFFF" ||
                        cell.colorHex.uppercase() == "#FFFEFE" ||
                        cell.colorHex.uppercase() == "#FEFEFE"
                    )
                    if (!isWhite) {
                        val cx = left + cellSize / 2f
                        val cy = top + cellSize / 2f - (keyFill.descent() + keyFill.ascent()) / 2f
                        drawCanvas.drawText(cell.key, cx, cy, keyStroke)
                        drawCanvas.drawText(cell.key, cx, cy, keyFill)
                    }
                }
            }
        }

        // 圆形裁剪
        if (isCircle && gridBmp != null) {
            val circleR = outSize / 2f
            val circleCx = outSize / 2f
            val circleCy = outSize / 2f
            val bgPaint = Paint().apply { color = EXTERNAL_COLOR }
            canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), bgPaint)
            val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            // 用 destination-out 实现圆形裁剪
            val layer = canvas.saveLayer(0f, 0f, outW.toFloat(), outH.toFloat(), null)
            canvas.drawBitmap(gridBmp, shiftX, shiftY, null)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawOval(RectF(circleCx - circleR, circleCy - circleR, circleCx + circleR, circleCy + circleR), maskPaint)
            canvas.restoreToCount(layer)
            gridBmp.recycle()

            // 圆形边界
            val circleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawOval(RectF(circleCx - circleR, circleCy - circleR, circleCx + circleR, circleCy + circleR), circleBorder)
        }

        return bmp
    }
}