package com.perlerbeads.generator.ui.board

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.perlerbeads.generator.algorithm.BoardSlice
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.ui.components.GridRenderer

/**
 * 分板切片专用渲染引擎：
 * - 顶部与左侧带有刻度序号标尺（每 5 格及边界标号），方便对照实体板点豆
 * - 支持按色号聚焦 Spotlight（非聚焦色号变暗，聚焦色号高亮金边）
 * - 支持逐格打勾展示（已完成格子带半透明遮罩与鲜明绿色勾号 ✓）
 * - 每 5 格绘制加粗辅线
 * - 提供将触摸手势像素精确映射到网格单元格 (row, col) 的数学换算
 */
object BoardSliceRenderer {
    const val RULER_LEFT = 36f
    const val RULER_TOP = 28f
    const val CELL_SIZE = 36f

    fun render(
        grid: GridData,
        slice: BoardSlice,
        completedCells: Set<Int>,
        spotlightKey: String? = null,
        scope: ((row: Int, col: Int) -> Boolean)? = null
    ): Bitmap {
        val bmpW = (RULER_LEFT + slice.cols * CELL_SIZE).toInt()
        val bmpH = (RULER_TOP + slice.rows * CELL_SIZE).toInt()
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 底色
        val bgPaint = Paint().apply { color = 0xFF1E1E22.toInt() }
        canvas.drawRect(0f, 0f, bmpW.toFloat(), bmpH.toFloat(), bgPaint)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val linePaint = Paint().apply {
            color = 0x33FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val boldLinePaint = Paint().apply {
            color = 0x88FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9E9E9E.toInt()
            textSize = 14f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = CELL_SIZE * 0.44f
        }
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF50.toInt()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = CELL_SIZE * 0.68f
        }
        val spotlightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD700.toInt() // 金黄色高亮框
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val doneDimPaint = Paint().apply {
            color = 0x55000000.toInt()
            style = Paint.Style.FILL
        }

        // 1. 顶部刻度（列号）
        for (c in 0 until slice.cols) {
            val num = c + 1
            if (num == 1 || num % 5 == 0 || num == slice.cols) {
                val cx = RULER_LEFT + c * CELL_SIZE + CELL_SIZE / 2f
                val cy = RULER_TOP / 2f - (rulerTextPaint.descent() + rulerTextPaint.ascent()) / 2f
                canvas.drawText("$num", cx, cy, rulerTextPaint)
            }
        }

        // 2. 左侧刻度（行号）
        for (r in 0 until slice.rows) {
            val num = r + 1
            if (num == 1 || num % 5 == 0 || num == slice.rows) {
                val cx = RULER_LEFT / 2f
                val cy = RULER_TOP + r * CELL_SIZE + CELL_SIZE / 2f - (rulerTextPaint.descent() + rulerTextPaint.ascent()) / 2f
                canvas.drawText("$num", cx, cy, rulerTextPaint)
            }
        }

        // 3. 格子主体
        for (r in 0 until slice.rows) {
            val gr = slice.rowStart + r
            val top = RULER_TOP + r * CELL_SIZE
            val bottom = top + CELL_SIZE

            for (c in 0 until slice.cols) {
                val gc = slice.colStart + c
                val left = RULER_LEFT + c * CELL_SIZE
                val right = left + CELL_SIZE
                val inScope = scope == null || scope(gr, gc)
                val cell = grid.cells[gr][gc]
                val isRealBead = !cell.isExternal && cell.key != TRANSPARENT_KEY && inScope

                if (!isRealBead) {
                    cellPaint.color = 0xFF2A2A2E.toInt()
                    canvas.drawRect(left, top, right, bottom, cellPaint)
                    canvas.drawRect(left, top, right, bottom, linePaint)
                    continue
                }

                val globalIdx = gr * grid.n + gc
                val isDone = globalIdx in completedCells
                val isSpotlightMatch = (spotlightKey != null && cell.key == spotlightKey)
                val isSpotlightDimmed = (spotlightKey != null && cell.key != spotlightKey)

                val baseColor = GridRenderer.parseHex(cell.colorHex)
                if (isSpotlightDimmed) {
                    // 非聚焦色号降低饱和度与亮度，保留 18% 颜色辨识度
                    val red = (Color.red(baseColor) * 0.18f + 0x22 * 0.82f).toInt()
                    val green = (Color.green(baseColor) * 0.18f + 0x22 * 0.82f).toInt()
                    val blue = (Color.blue(baseColor) * 0.18f + 0x25 * 0.82f).toInt()
                    cellPaint.color = Color.rgb(red, green, blue)
                } else {
                    cellPaint.color = baseColor
                }
                canvas.drawRect(left, top, right, bottom, cellPaint)

                // 已完成格子遮罩
                if (isDone) {
                    canvas.drawRect(left, top, right, bottom, doneDimPaint)
                }

                // 细网格线
                canvas.drawRect(left, top, right, bottom, linePaint)

                // 聚焦框
                if (isSpotlightMatch) {
                    canvas.drawRect(left + 1.2f, top + 1.2f, right - 1.2f, bottom - 1.2f, spotlightRingPaint)
                }

                // 文字或勾选标记
                val cx = left + CELL_SIZE / 2f
                val cy = top + CELL_SIZE / 2f - (keyTextPaint.descent() + keyTextPaint.ascent()) / 2f

                if (isSpotlightDimmed) {
                    // 非聚焦色号不画字，使聚焦色号极度凸显
                } else if (isDone) {
                    val checkCy = top + CELL_SIZE / 2f - (checkPaint.descent() + checkPaint.ascent()) / 2f
                    canvas.drawText("✓", cx, checkCy, checkPaint)
                } else {
                    val red = Color.red(baseColor)
                    val green = Color.green(baseColor)
                    val blue = Color.blue(baseColor)
                    val luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
                    keyTextPaint.color = if (luma > 0.5f) Color.BLACK else Color.WHITE
                    canvas.drawText(cell.key, cx, cy, keyTextPaint)
                }
            }
        }

        // 4. 每 5 格绘制加粗辅线（横竖），方便实物对齐
        for (c in 0..slice.cols step 5) {
            if (c > 0 && c < slice.cols) {
                val x = RULER_LEFT + c * CELL_SIZE
                canvas.drawLine(x, RULER_TOP, x, bmpH.toFloat(), boldLinePaint)
            }
        }
        for (r in 0..slice.rows step 5) {
            if (r > 0 && r < slice.rows) {
                val y = RULER_TOP + r * CELL_SIZE
                canvas.drawLine(RULER_LEFT, y, bmpW.toFloat(), y, boldLinePaint)
            }
        }

        return bmp
    }

    /**
     * 将屏幕/容器上的触摸点坐标换算为分板上的本地 (row, col)。
     * 若点击在刻度区或板外，返回 null。
     */
    fun tapToCell(
        tapX: Float,
        tapY: Float,
        containerW: Float,
        containerH: Float,
        bmpW: Float,
        bmpH: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        cols: Int,
        rows: Int
    ): Pair<Int, Int>? {
        if (containerW <= 0f || containerH <= 0f || bmpW <= 0f || bmpH <= 0f) return null
        val scaleFit = minOf(containerW / bmpW, containerH / bmpH)
        val renderW = bmpW * scaleFit * zoom
        val renderH = bmpH * scaleFit * zoom

        val leftInContainer = (containerW - renderW) / 2f + offsetX
        val topInContainer = (containerH - renderH) / 2f + offsetY

        val relX = tapX - leftInContainer
        val relY = tapY - topInContainer
        if (relX < 0f || relX > renderW || relY < 0f || relY > renderH) return null

        val bmpX = relX / (renderW / bmpW)
        val bmpY = relY / (renderH / bmpH)

        val cellX = bmpX - RULER_LEFT
        val cellY = bmpY - RULER_TOP
        if (cellX < 0f || cellY < 0f) return null

        val col = (cellX / CELL_SIZE).toInt()
        val row = (cellY / CELL_SIZE).toInt()
        if (row !in 0 until rows || col !in 0 until cols) return null

        return Pair(row, col)
    }
}
