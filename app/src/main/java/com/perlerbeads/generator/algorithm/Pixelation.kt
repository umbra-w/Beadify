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

/** RGB 欧氏距离。对应 pixelation.ts L44。 */
fun colorDistance(rgb1: RgbColor, rgb2: RgbColor): Double {
    val dr = rgb1.r - rgb2.r
    val dg = rgb1.g - rgb2.g
    val db = rgb1.b - rgb2.b
    return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
}

/** 查找最近色板色；空色板回退 ERR。对应 pixelation.ts L52。 */
fun findClosestPaletteColor(target: RgbColor, palette: List<PaletteColor>): PaletteColor {
    if (palette.isEmpty()) return PaletteColor("ERR", "#000000", RgbColor(0, 0, 0))
    var minDistance = Double.MAX_VALUE
    var closest = palette[0]
    for (pc in palette) {
        val d = colorDistance(target, pc.rgb)
        if (d < minDistance) {
            minDistance = d
            closest = pc
        }
        if (d == 0.0) break
    }
    return closest
}

/**
 * 根据 Bitmap、网格尺寸、色板与模式计算像素网格。
 * 对应 pixelation.ts L162（calculatePixelGrid）。
 * 内部先 getPixels 读一次全图，再按窗口遍历，等价于网页版 getImageData。
 */
fun calculatePixelGrid(
    bitmap: Bitmap,
    n: Int,
    m: Int,
    palette: List<PaletteColor>,
    mode: PixelationMode,
    fallback: PaletteColor
): Array<Array<MappedPixel>> {
    val mapped = Array(m) { Array(n) { MappedPixel(fallback.key, fallback.hex, false) } }
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

            val rep = calculateCellRepresentativeColor(fullImage, imgWidth, startX, startY, cw, ch, mode)
            mapped[j][i] = if (rep != null) {
                val closest = findClosestPaletteColor(rep, palette)
                MappedPixel(closest.key, closest.hex, false)
            } else {
                transparentColorData
            }
        }
    }
    return mapped
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
