package com.perlerbeads.generator.algorithm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.transparentColorData
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 文字颜色模式。 */
enum class TextColorMode {
    /** 单色：所有笔画使用同一颜色。 */
    SINGLE,
    /** 渐变：按水平位置在 Oklab 空间插值两种颜色。 */
    GRADIENT,
    /** 彩虹：按列位置自动循环色相（Oklab 色轮等分）。 */
    RAINBOW
}

/** 文字阴影配置。 */
data class TextShadowConfig(
    /** 水平偏移（网格格数）。 */
    val offsetCol: Int = 1,
    /** 垂直偏移（网格格数）。 */
    val offsetRow: Int = 1,
    /** 阴影颜色。 */
    val color: PaletteColor
)

/** 文字描边配置。 */
data class TextOutlineConfig(
    /** 描边厚度（1 = 1 格宽 8 邻域膨胀）。 */
    val thickness: Int = 1,
    /** 描边颜色。 */
    val color: PaletteColor
)

/**
 * 文字拼豆风格配置。
 *
 * @param colorMode 颜色模式
 * @param primaryColor 主颜色（SINGLE 模式下的唯一颜色；GRADIENT 模式下的起始颜色）
 * @param secondaryColor GRADIENT 模式下的结束颜色；其他模式忽略
 * @param shadow 阴影配置；null = 无阴影
 * @param outline 描边配置；null = 无描边
 * @param typeface 自定义字体；null = 系统默认
 * @param palette 当前色板（GRADIENT/RAINBOW 模式用于最近邻色板色匹配）
 */
data class TextBeadStyle(
    val colorMode: TextColorMode = TextColorMode.SINGLE,
    val primaryColor: PaletteColor,
    val secondaryColor: PaletteColor? = null,
    val shadow: TextShadowConfig? = null,
    val outline: TextOutlineConfig? = null,
    val typeface: Typeface? = null,
    val palette: List<PaletteColor> = emptyList()
)

/** 文字拼豆：把文字渲染为拼豆网格（笔画用所选颜色，背景透明）。 */
object TextBeads {

    const val MAX_COLS = 200
    const val MIN_ROWS = 8
    const val MAX_ROWS = 120

    // 内部常量：高分辨率渲染字号
    private const val RENDER_SIZE = 200f
    private const val PAD = 20f
    private const val ALPHA_THRESHOLD = 100

    /**
     * 渲染文字为网格（向后兼容的简单接口）。
     * @param text 单行文字
     * @param gridRows 网格行数
     * @param bead 笔画颜色
     * @param bold 是否粗体
     * @param bg 背景豆颜色；null = 背景透明
     */
    fun renderTextGrid(
        text: String,
        gridRows: Int,
        bead: PaletteColor,
        bold: Boolean = true,
        bg: PaletteColor? = null
    ): GridData? {
        val style = TextBeadStyle(
            colorMode = TextColorMode.SINGLE,
            primaryColor = bead
        )
        return renderTextGrid(text, gridRows, style, bold, bg)
    }

    /**
     * 渲染文字为网格（进阶版本：支持渐变、阴影、描边、自定义字体）。
     * @param text 单行文字（换行/多余空白折叠为单个空格）
     * @param gridRows 网格行数（字号高度）；列数按文字宽高比自动推算
     * @param style 文字风格配置
     * @param bold 是否粗体
     * @param bg 背景豆颜色；null = 背景透明（只拼笔画）
     * @return 网格，文字过短（无有效笔画）时返回 null
     */
    fun renderTextGrid(
        text: String,
        gridRows: Int,
        style: TextBeadStyle,
        bold: Boolean = true,
        bg: PaletteColor? = null
    ): GridData? {
        val singleLine = text.replace(Regex("\\s+"), " ").trim()
        if (singleLine.isEmpty()) return null
        val rows = gridRows.coerceIn(MIN_ROWS, MAX_ROWS)

        // 1. 大尺寸渲染文字
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.BLACK
            textSize = RENDER_SIZE
            isFakeBoldText = bold
            textAlign = Paint.Align.CENTER
            style.typeface?.let { this.typeface = it }
        }
        val metrics = paint.fontMetrics
        val textW = paint.measureText(singleLine)
        if (textW <= 0f) return null
        val bmpW = ceil(textW + PAD * 2).toInt().coerceAtLeast(8)
        val bmpH = ceil(metrics.descent - metrics.ascent + PAD * 2).toInt().coerceAtLeast(8)
        val raw = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        Canvas(raw).drawText(singleLine, bmpW / 2f, PAD - metrics.ascent, paint)

        // 2. 列数按宽高比推算
        var cols = (bmpW.toDouble() * rows / bmpH).roundToInt().coerceIn(4, MAX_COLS)
        var actualRows = rows
        if (cols >= MAX_COLS) {
            cols = MAX_COLS
            actualRows = (bmpH.toDouble() * MAX_COLS / bmpW).roundToInt().coerceIn(MIN_ROWS, rows)
        }

        // 3. 缩放到网格分辨率
        val scaled = Bitmap.createScaledBitmap(raw, cols, actualRows, true)
        raw.recycle()

        // 4. 提取文字蒙版 (true = 笔画)
        val mask = Array(actualRows) { r ->
            BooleanArray(cols) { c ->
                Color.alpha(scaled.getPixel(c, r)) >= ALPHA_THRESHOLD
            }
        }
        scaled.recycle()

        // 5. 生成描边蒙版（8 邻域形态学膨胀）
        val outlineMask: Array<BooleanArray>? = style.outline?.let { cfg ->
            morphologicalDilate(mask, actualRows, cols, cfg.thickness)
        }

        // 6. 生成阴影蒙版（文字蒙版平移）
        val shadowMask: Array<BooleanArray>? = style.shadow?.let { cfg ->
            offsetMask(mask, actualRows, cols, cfg.offsetRow, cfg.offsetCol)
        }

        // 7. 组装网格：优先级 文字 > 描边 > 阴影 > 背景
        val bgCell = bg?.let { MappedPixel(it.key, it.hex, false) } ?: transparentColorData
        val cells = Array(actualRows) { r ->
            Array(cols) { c ->
                when {
                    mask[r][c] -> {
                        // 文字笔画：根据颜色模式选色
                        val pc = resolveColor(style, c, cols)
                        MappedPixel(pc.key, pc.hex, false)
                    }
                    outlineMask != null && outlineMask[r][c] -> {
                        val oc = style.outline!!.color
                        MappedPixel(oc.key, oc.hex, false)
                    }
                    shadowMask != null && shadowMask[r][c] -> {
                        val sc = style.shadow!!.color
                        MappedPixel(sc.key, sc.hex, false)
                    }
                    else -> bgCell
                }
            }
        }

        val beadCount = cells.sumOf { row -> row.count { !it.isExternal } }
        if (beadCount == 0) return null
        return GridData(cols, actualRows, cells, emptySet(), GridShape.SQUARE)
    }

    // ==================== 颜色解析 ====================

    /**
     * 根据颜色模式决定某列位置的笔画颜色。
     * - SINGLE: 直接返回 primaryColor
     * - GRADIENT: Oklab 插值 → 色板最近邻
     * - RAINBOW: 色相等分 → 色板最近邻
     */
    internal fun resolveColor(style: TextBeadStyle, col: Int, totalCols: Int): PaletteColor {
        return when (style.colorMode) {
            TextColorMode.SINGLE -> style.primaryColor
            TextColorMode.GRADIENT -> {
                val endColor = style.secondaryColor ?: style.primaryColor
                if (totalCols <= 1) return style.primaryColor
                val t = col.toDouble() / (totalCols - 1).coerceAtLeast(1)
                val labStart = ColorMath.rgbToOklab(style.primaryColor.rgb)
                val labEnd = ColorMath.rgbToOklab(endColor.rgb)
                val interpolated = ColorMath.lerpOklab(labStart, labEnd, t)
                val rgb = ColorMath.oklabToRgb(interpolated)
                snapToPalette(rgb, style.palette, style.primaryColor)
            }
            TextColorMode.RAINBOW -> {
                if (totalCols <= 1) return style.primaryColor
                val t = col.toDouble() / (totalCols - 1).coerceAtLeast(1)
                val hueRgb = hueToRgb(t)
                snapToPalette(hueRgb, style.palette, style.primaryColor)
            }
        }
    }

    /** 在色板中找最近邻色；色板为空时回退到 fallback。 */
    private fun snapToPalette(
        rgb: RgbColor,
        palette: List<PaletteColor>,
        fallback: PaletteColor
    ): PaletteColor {
        if (palette.isEmpty()) return fallback
        return ColorMath.findClosestPaletteColor(rgb, palette)
    }

    /** 色相值 (0..1) 转 RGB，恒定最大饱和度 & 亮度。 */
    internal fun hueToRgb(hue: Double): RgbColor {
        val h = (hue % 1.0 + 1.0) % 1.0
        val sector = (h * 6).toInt()
        val f = h * 6 - sector
        val q = (1 - f)
        val t2 = f
        val (r, g, b) = when (sector % 6) {
            0 -> Triple(1.0, t2, 0.0)
            1 -> Triple(q, 1.0, 0.0)
            2 -> Triple(0.0, 1.0, t2)
            3 -> Triple(0.0, q, 1.0)
            4 -> Triple(t2, 0.0, 1.0)
            else -> Triple(1.0, 0.0, q)
        }
        return RgbColor(
            (r * 255).roundToInt().coerceIn(0, 255),
            (g * 255).roundToInt().coerceIn(0, 255),
            (b * 255).roundToInt().coerceIn(0, 255)
        )
    }

    // ==================== 蒙版操作 ====================

    /**
     * 8 邻域形态学膨胀：将文字蒙版向外扩展 [thickness] 格。
     * 返回的蒙版中，只有「膨胀区域但不在原始蒙版中的格子」为 true（纯描边区域）。
     */
    internal fun morphologicalDilate(
        mask: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        thickness: Int
    ): Array<BooleanArray> {
        var current = mask
        repeat(thickness.coerceAtLeast(1)) {
            val next = Array(rows) { BooleanArray(cols) }
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (current[r][c]) {
                        next[r][c] = true
                        continue
                    }
                    // 8 邻域检查
                    outer@ for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0 until rows && nc in 0 until cols && current[nr][nc]) {
                                next[r][c] = true
                                break@outer
                            }
                        }
                    }
                }
            }
            current = next
        }
        // 只保留膨胀新增区域（排除原始文字蒙版）
        return Array(rows) { r ->
            BooleanArray(cols) { c -> current[r][c] && !mask[r][c] }
        }
    }

    /**
     * 偏移蒙版：将文字蒙版按 (offsetRow, offsetCol) 偏移。
     * 返回的蒙版中，只有「偏移后的位置但不在原始蒙版中的格子」为 true（纯阴影区域）。
     */
    internal fun offsetMask(
        mask: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        offsetRow: Int,
        offsetCol: Int
    ): Array<BooleanArray> {
        return Array(rows) { r ->
            BooleanArray(cols) { c ->
                val srcR = r - offsetRow
                val srcC = c - offsetCol
                val inShadow = srcR in 0 until rows && srcC in 0 until cols && mask[srcR][srcC]
                inShadow && !mask[r][c]  // 排除文字本身
            }
        }
    }
}
