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
    cleanupIslands: Boolean = false,
    similarityThreshold: Int = 0
): Array<Array<MappedPixel>> {
    // 性能关键保护：如果原图分辨率远超拼豆网格所需（如 3000x4000 照片），
    // 自适应下采样到高质量超采样尺寸（每个格子对应 3~4 个采样像素，上限 800px），
    // 既能保留全部微小特征与边缘轮廓，又彻底避免 1200 万像素在低端 CPU 上的 GC 卡顿与转圈。
    val maxInputDim = maxOf(bitmap.width, bitmap.height)
    val maxGridDim = maxOf(n, m)
    val targetMaxDim = (maxGridDim * 3).coerceIn(300, 800)

    val (workBmp, needRecycle) = if (maxInputDim > targetMaxDim * 1.25f) {
        val scale = targetMaxDim.toFloat() / maxInputDim
        val targetW = Math.max(n, Math.round(bitmap.width * scale).toInt())
        val targetH = Math.max(m, Math.round(bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        scaled to (scaled !== bitmap)
    } else {
        bitmap to false
    }

    val reps = Array(m) { arrayOfNulls<RgbColor>(n) }
    val imgWidth = workBmp.width
    val imgHeight = workBmp.height
    val fullImage = IntArray(imgWidth * imgHeight)
    workBmp.getPixels(fullImage, 0, imgWidth, 0, 0, imgWidth, imgHeight)

    if (needRecycle && !workBmp.isRecycled) {
        workBmp.recycle()
    }

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

    // 2. 映射量化（误差扩散抖动或最近邻）：引入单次会话色板索引与代表色复用缓存
    var grid = if (dithering) {
        quantizeWithDithering(reps, activePalette, fallback)
    } else {
        val paletteArray = activePalette.toTypedArray()
        val paletteLabs = Array(activePalette.size) { ColorMath.rgbToOklab(activePalette[it].rgb) }
        val repCache = HashMap<RgbColor, MappedPixel>()

        Array(m) { j ->
            Array(n) { i ->
                val rep = reps[j][i]
                if (rep == null) {
                    transparentColorData
                } else {
                    repCache.getOrPut(rep) {
                        val targetLab = ColorMath.rgbToOklab(rep)
                        var minD2 = Double.MAX_VALUE
                        var bestIdx = 0
                        for (idx in paletteLabs.indices) {
                            val d2 = targetLab.distanceSquared(paletteLabs[idx])
                            if (d2 < minD2) {
                                minD2 = d2
                                bestIdx = idx
                                if (d2 == 0.0) break
                            }
                        }
                        val chosen = paletteArray[bestIdx]
                        MappedPixel(chosen.key, chosen.hex, false)
                    }
                }
            }
        }
    }

    // 3. 如果开启了相似颜色合并，基于 Oklab 色差频次优先归并微小散色
    if (similarityThreshold > 0) {
        grid = ColorQuantizer.mergeSimilarColors(grid, activePalette, similarityThreshold)
    }

    // 4. 如果开启了噪点清理，平滑 1 格孤岛并保护连续线条与笔画
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

    // 单元格内自适应采样步长：每个格子最多采样 8x8 = 64 个点即可完美识别主色，消除过密采样的性能惩罚
    val stepX = maxOf(1, width / 8)
    val stepY = maxOf(1, height / 8)

    val colorCounts = HashMap<Int, Int>()
    var dominantKey: Int? = null
    var maxCount = 0

    val endX = startX + width
    val endY = startY + height
    var y = startY
    while (y < endY) {
        var x = startX
        while (x < endX) {
            val argb = data[y * imgWidth + x]
            val alpha = (argb ushr 24) and 0xFF
            if (alpha >= 128) {
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                pixelCount++
                if (mode == PixelationMode.AVERAGE) {
                    rSum += r
                    gSum += g
                    bSum += b
                } else {
                    // 原生 Int 键，0 字符串分配与 0 装箱损耗
                    val key = (r shl 16) or (g shl 8) or b
                    val c = (colorCounts[key] ?: 0) + 1
                    colorCounts[key] = c
                    if (c > maxCount) {
                        maxCount = c
                        dominantKey = key
                    }
                }
            }
            x += stepX
        }
        y += stepY
    }
    if (pixelCount == 0) return null
    return if (mode == PixelationMode.AVERAGE) {
        RgbColor(
            Math.round(rSum.toDouble() / pixelCount).toInt(),
            Math.round(gSum.toDouble() / pixelCount).toInt(),
            Math.round(bSum.toDouble() / pixelCount).toInt()
        )
    } else {
        dominantKey?.let {
            val r = (it ushr 16) and 0xFF
            val g = (it ushr 8) and 0xFF
            val b = it and 0xFF
            RgbColor(r, g, b)
        }
    }
}
