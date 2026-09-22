package com.perlerbeads.generator.algorithm

import android.graphics.Bitmap
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.PixelationMode
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.transparentColorData

/** 解析 "#RRGGBB"。对应 pixelation.ts L34。 */
fun hexToRgb(hex: String): RgbColor? {
    val h = hex.removePrefix("#")
    if (h.length != 6) return null
    val r = h.substring(0, 2).toIntOrNull(16) ?: return null
    val g = h.substring(2, 4).toIntOrNull(16) ?: return null
    val b = h.substring(4, 6).toIntOrNull(16) ?: return null
    return RgbColor(r, g, b)
}

/**
 * 颜色感知距离（基于 Oklab 感知色彩空间，对应 Zippland PR #10 与 QiaoGrid）。
 * 消除 RGB 欧氏距离在深色/蓝紫色相上的失真，平滑兼容 0..100 阈值。
 */
fun colorDistance(rgb1: RgbColor, rgb2: RgbColor): Double =
    ColorMath.oklabDistance(rgb1, rgb2)

/** 查找最近色板色；基于 Oklab 感知色彩空间。空色板回退 ERR。 */
fun findClosestPaletteColor(target: RgbColor, palette: List<PaletteColor>): PaletteColor =
    ColorMath.findClosestPaletteColor(target, palette)

/**
 * 根据 Bitmap、网格尺寸、色板与模式计算像素网格。
 * 对应 pixelation.ts L162（calculatePixelGrid）。
 * 内部先 getPixels 读一次全图，再按窗口遍历，等价于网页版 getImageData。
 *
 * @param dithering 开启 Floyd-Steinberg 误差扩散抖动（照片类图片过渡更自然）
 * @param maxColors 限制最大使用颜色数（0 为不限制，如 16/24/32/48）
 * @param cleanupIslands 是否自动清理孤立的 1 格噪点飞点
 */
fun calculatePixelGrid(
    bitmap: Bitmap,
    n: Int,
    m: Int,
    palette: List<PaletteColor>,
    mode: PixelationMode,
    fallback: PaletteColor,
    dithering: Boolean = false,
    maxColors: Int = 0,
    cleanupIslands: Boolean = false
): Array<Array<MappedPixel>> {
    val reps = Array(m) { arrayOfNulls<RgbColor>(n) }
    val imgWidth = bitmap.width
    val imgHeight = bitmap.height
    val fullImage = IntArray(imgWidth * imgHeight)
    bitmap.getPixels(fullImage, 0, imgWidth, 0, 0, imgWidth, imgHeight)

    val cellWidthOriginal = imgWidth.toDouble() / n
    val cellHeightOriginal = imgHeight.toDouble() / m

    for (j in 0 until m) {
        for (i in 0 until n) {
            val startX = Math.floor(i * cellWidthOriginal).toInt()
            val startY = Math.floor(j * cellHeightOriginal).toInt()
            val endX = Math.min(imgWidth, Math.ceil((i + 1) * cellWidthOriginal).toInt())
            val endY = Math.min(imgHeight, Math.ceil((j + 1) * cellHeightOriginal).toInt())
            val cw = Math.max(1, endX - startX)
            val ch = Math.max(1, endY - startY)

            reps[j][i] = calculateCellRepresentativeColor(fullImage, imgWidth, startX, startY, cw, ch, mode)
        }
    }

    // 1. 如果启用了受控色数限制且候选色大于上限，先由 Oklab 中位切割聚类计算优选子色板
    val activePalette = if (maxColors > 0 && palette.size > maxColors) {
        ColorQuantizer.computeControlledPalette(reps, palette, maxColors)
    } else {
        palette
    }

    // 2. 映射量化（误差扩散抖动或最近邻）
    var grid = if (dithering) {
        quantizeWithDithering(reps, activePalette, fallback)
    } else {
        Array(m) { j ->
            Array(n) { i ->
                reps[j][i]?.let { rep ->
                    val closest = findClosestPaletteColor(rep, activePalette)
                    MappedPixel(closest.key, closest.hex, false)
                } ?: transparentColorData
            }
        }
    }

    // 3. 如果开启了噪点清理，平滑 1 格孤岛并保护连续线条与笔画
    if (cleanupIslands) {
        grid = IslandCleanup.cleanupSpeckles(grid, maxIslandSize = 1, protectDiagonalLines = true)
    }

    return grid
}

/**
 * Floyd-Steinberg 误差扩散量化（纯函数，无 Android 依赖，便于单测）。
 * 逐格取「代表色 + 累积误差」的最近色板色，把量化误差按经典权重扩散：
 * 右 7/16、下左 3/16、下 5/16、下右 1/16。透明格（rep=null）不产生误差。
 * 输出确定性（无随机数），同输入必同输出。
 */
fun quantizeWithDithering(
    reps: Array<Array<RgbColor?>>,
    palette: List<PaletteColor>,
    fallback: PaletteColor
): Array<Array<MappedPixel>> {
    val m = reps.size
    val n = if (m > 0) reps[0].size else 0
    val out = Array(m) { Array(n) { MappedPixel(fallback.key, fallback.hex, false) } }
    val errR = Array(m) { FloatArray(n) }
    val errG = Array(m) { FloatArray(n) }
    val errB = Array(m) { FloatArray(n) }

    fun push(j: Int, i: Int, er: Float, eg: Float, eb: Float, weight: Float) {
        if (j < 0 || j >= m || i < 0 || i >= n) return
        errR[j][i] += er * weight
        errG[j][i] += eg * weight
        errB[j][i] += eb * weight
    }

    for (j in 0 until m) {
        for (i in 0 until n) {
            val rep = reps[j][i]
            if (rep == null) {
                out[j][i] = transparentColorData
                continue
            }
            // 目标色 = 代表色 + 累积误差（钳制到有效范围）
            val tr = (rep.r + errR[j][i]).toInt().coerceIn(0, 255)
            val tg = (rep.g + errG[j][i]).toInt().coerceIn(0, 255)
            val tb = (rep.b + errB[j][i]).toInt().coerceIn(0, 255)
            val chosen = findClosestPaletteColor(RgbColor(tr, tg, tb), palette)
            out[j][i] = MappedPixel(chosen.key, chosen.hex, false)

            // 新误差 = 目标色 - 实际选中的色板色
            val er = (rep.r + errR[j][i]) - chosen.rgb.r
            val eg = (rep.g + errG[j][i]) - chosen.rgb.g
            val eb = (rep.b + errB[j][i]) - chosen.rgb.b
            push(j, i + 1, er, eg, eb, 7f / 16f)
            push(j + 1, i - 1, er, eg, eb, 3f / 16f)
            push(j + 1, i, er, eg, eb, 5f / 16f)
            push(j + 1, i + 1, er, eg, eb, 1f / 16f)
        }
    }
    return out
}

/** 计算单元代表色。对应 pixelation.ts L89（calculateCellRepresentativeColor）。 */
private fun calculateCellRepresentativeColor(
    data: IntArray,
    imgWidth: Int,
    startX: Int,
    startY: Int,
    width: Int,
    height: Int,
    mode: PixelationMode
): RgbColor? {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var pixelCount = 0
    val colorCounts = HashMap<String, Int>()
    var dominant: RgbColor? = null
    var maxCount = 0

    val endX = startX + width
    val endY = startY + height
    for (y in startY until endY) {
        for (x in startX until endX) {
            val argb = data[y * imgWidth + x]
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 128) continue
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            pixelCount++
            if (mode == PixelationMode.AVERAGE) {
                rSum += r
                gSum += g
                bSum += b
            } else {
                val key = "$r,$g,$b"
                val c = (colorCounts[key] ?: 0) + 1
                colorCounts[key] = c
                if (c > maxCount) {
                    maxCount = c
                    dominant = RgbColor(r, g, b)
                }
            }
        }
    }
    if (pixelCount == 0) return null
    return if (mode == PixelationMode.AVERAGE) {
        RgbColor(
            Math.round(rSum.toDouble() / pixelCount).toInt(),
            Math.round(gSum.toDouble() / pixelCount).toInt(),
            Math.round(bSum.toDouble() / pixelCount).toInt()
        )
    } else {
        dominant
    }
}
