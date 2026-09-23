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
import kotlin.math.ceil

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
        circleOffsetY: Float = 0.5f,
        circle: com.perlerbeads.generator.model.CircleGeometry? = null,
        gridInterval: Int = 10,
        gridLineColor: Int = 0xCC333333.toInt()
    ): Bitmap {
        val maxEdge = maxOf(grid.n, grid.m)
        val cellSize = maxOf(2, minOf(32, maxDim / maxEdge))
        return render(
            grid, cellSize, showBorders, showKeys, hideWhiteKeys,
            circleOffsetX, circleOffsetY, mirror = false, circle = circle,
            gridInterval = gridInterval, gridLineColor = gridLineColor
        )
    }

    /**
     * @param mirror 水平镜像格子位置（美纹纸背面拼贴用）。色号文字不镜像，保持可读。
     * @param circle 圆形画板几何（网格坐标系）。null 时由 circleOffsetX/Y 推导。
     *               编辑页双指调整后的圆框应传入此参数，保证导出所见即所得。
     * @param externalColor external/透明格子的填充色（应用内浅灰，打印可传白色）
     * @param gridInterval 网格粗线分界间隔（0=关闭，5=每5格，10=每10格）
     * @param gridLineColor 网格粗线分界线颜色 ARGB
     */
    fun render(
        grid: GridData,
        cellSize: Int = 24,
        showBorders: Boolean = true,
        showKeys: Boolean = false,
        hideWhiteKeys: Boolean = true,
        circleOffsetX: Float = 0.5f,
        circleOffsetY: Float = 0.5f,
        mirror: Boolean = false,
        circle: com.perlerbeads.generator.model.CircleGeometry? = null,
        externalColor: Int = EXTERNAL_COLOR,
        gridInterval: Int = 10,
        gridLineColor: Int = 0xCC333333.toInt()
    ): Bitmap {
        val gridW = grid.n * cellSize
        val gridH = grid.m * cellSize
        val isCircle = grid.shape?.name == "CIRCLE"

        // 圆形几何：优先用编辑页传递的圆框（所见即所得），否则由覆盖范围滑块推导
        val circleGeo = if (isCircle) {
            circle ?: com.perlerbeads.generator.model.circleGeometry(
                grid.n, grid.m, circleOffsetX, circleOffsetY
            )
        } else null

        // 圆形模式下输出为以圆框直径为边长的正方形，圆外透明
        val circleR = if (isCircle) circleGeo!!.radius * cellSize else 0f
        val circleCenterPxX = if (isCircle) {
            // 镜像导出时圆框随图案同步镜像
            val cx = if (mirror) grid.n - circleGeo!!.centerX else circleGeo!!.centerX
            cx * cellSize
        } else 0f
        val circleCenterPxY = if (isCircle) circleGeo!!.centerY * cellSize else 0f
        val outSize = if (isCircle) ceil(2f * circleR).toInt().coerceAtLeast(cellSize) else maxOf(gridW, gridH)
        val outW = if (isCircle) outSize else gridW
        val outH = if (isCircle) outSize else gridH
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 圆形模式：平移网格位图，使圆框中心位于输出画布中心
        val shiftX: Float
        val shiftY: Float
        if (isCircle) {
            shiftX = outSize / 2f - circleCenterPxX
            shiftY = outSize / 2f - circleCenterPxY
        } else {
            shiftX = 0f; shiftY = 0f
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val linePaint = Paint().apply {
            color = GRID_LINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val keyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
        }
        val keyTextSize = cellSize * 0.5f
        keyFill.textSize = keyTextSize
        keyFill.isFakeBoldText = true

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
                cellPaint.color = if (cell.isExternal) externalColor else parseHex(cell.colorHex)
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
                        // 对比色文字（学网页版 getContrastColor）：亮格黑字、暗格白字，加粗无描边更锐利
                        val argb = parseHex(cell.colorHex)
                        val r = (argb shr 16) and 0xFF
                        val g = (argb shr 8) and 0xFF
                        val b = argb and 0xFF
                        val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                        keyFill.color = if (luma > 0.5f) Color.BLACK else Color.WHITE
                        drawCanvas.drawText(cell.key, cx, cy, keyFill)
                    }
                }
            }
        }

        // 粗线计数分界线，便于数格子（圆形模式随圆裁剪）
        if (showBorders && gridInterval > 0) {
            val boldLine = Paint().apply {
                color = gridLineColor
                style = Paint.Style.STROKE
                strokeWidth = maxOf(2f, cellSize * 0.1f)
            }
            for (c in 0..grid.n step gridInterval) {
                drawCanvas.drawLine((c * cellSize).toFloat(), 0f, (c * cellSize).toFloat(), gridH.toFloat(), boldLine)
            }
            for (r in 0..grid.m step gridInterval) {
                drawCanvas.drawLine(0f, (r * cellSize).toFloat(), gridW.toFloat(), (r * cellSize).toFloat(), boldLine)
            }
        }

        // 圆形裁剪：圆框中心固定在输出画布中心
        if (isCircle && gridBmp != null) {
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