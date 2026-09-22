package com.perlerbeads.generator.algorithm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.transparentColorData
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 文字拼豆：把文字渲染为拼豆网格（笔画用所选颜色，背景透明）。 */
object TextBeads {

    const val MAX_COLS = 200
    const val MIN_ROWS = 8
    const val MAX_ROWS = 120

    /**
     * 渲染文字为网格。
     * @param text 单行文字（换行/多余空白会被折叠为单个空格）
     * @param gridRows 网格行数（字号高度）；列数按文字宽高比自动推算
     * @param bg 背景豆颜色；null = 背景透明（只拼笔画）
     * @return 网格，文字过短（无有效笔画）时返回 null
     */
    fun renderTextGrid(
        text: String,
        gridRows: Int,
        bead: PaletteColor,
        bold: Boolean = true,
        bg: PaletteColor? = null
    ): GridData? {
        val singleLine = text.replace(Regex("\\s+"), " ").trim()
        if (singleLine.isEmpty()) return null
        val rows = gridRows.coerceIn(MIN_ROWS, MAX_ROWS)

        // 1. 大尺寸渲染文字（200px 字号保证降采样质量）
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.BLACK
            textSize = 200f
            isFakeBoldText = bold
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        val pad = 20f
        val textW = paint.measureText(singleLine)
        if (textW <= 0f) return null
        val bmpW = ceil(textW + pad * 2).toInt().coerceAtLeast(8)
        val bmpH = ceil(metrics.descent - metrics.ascent + pad * 2).toInt().coerceAtLeast(8)
        val raw = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        Canvas(raw).drawText(singleLine, bmpW / 2f, pad - metrics.ascent, paint)

        // 2. 列数按宽高比推算，超宽则压行数
        var cols = (bmpW.toDouble() * rows / bmpH).roundToInt().coerceIn(4, MAX_COLS)
        var actualRows = rows
        if (cols >= MAX_COLS) {
            cols = MAX_COLS
            actualRows = (bmpH.toDouble() * MAX_COLS / bmpW).roundToInt().coerceIn(MIN_ROWS, rows)
        }

        // 3. 缩放到网格分辨率，按透明度阈值判定笔画
        val scaled = Bitmap.createScaledBitmap(raw, cols, actualRows, true)
        val bgCell = bg?.let { MappedPixel(it.key, it.hex, false) } ?: transparentColorData
        val cells = Array(actualRows) { r ->
            Array(cols) { c ->
                val alpha = Color.alpha(scaled.getPixel(c, r))
                if (alpha >= 100) MappedPixel(bead.key, bead.hex, false) else bgCell
            }
        }
        scaled.recycle()
        raw.recycle()

        val beadCount = cells.sumOf { row -> row.count { !it.isExternal } }
        if (beadCount == 0) return null
        return GridData(cols, actualRows, cells, emptySet(), GridShape.SQUARE)
    }
}
