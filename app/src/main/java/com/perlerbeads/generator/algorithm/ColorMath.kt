package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Oklab 感知色彩空间坐标 (Björn Ottosson, 2020)。
 * @param l 亮度 (0..1)
 * @param a 绿-红轴 (约 -0.4..+0.4)
 * @param b 蓝-黄轴 (约 -0.4..+0.4)
 */
data class Oklab(val l: Double, val a: Double, val b: Double) {
    /** 距离平方（免去开方，加速最近邻搜索）。 */
    fun distanceSquared(other: Oklab): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return dl * dl + da * da + db * db
    }

    /** 感知色差（乘以 100 与 0..100 阈值输入平滑对齐）。 */
    fun distance(other: Oklab): Double = sqrt(distanceSquared(other)) * 100.0
}

object ColorMath {

    private val oklabCache = ConcurrentHashMap<Int, Oklab>()

    /** sRGB 单通道去伽马线性化。 */
    fun srgbToLinear(c: Int): Double {
        val normalized = c.coerceIn(0, 255) / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    /** sRGB 转 Oklab。内置哈希缓存。 */
    fun rgbToOklab(r: Int, g: Int, b: Int): Oklab {
        val key = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        return oklabCache.computeIfAbsent(key) {
            val lr = srgbToLinear(r)
            val lg = srgbToLinear(g)
            val lb = srgbToLinear(b)

            val lCone = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
            val mCone = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
            val sCone = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb

            val lRoot = cbrt(lCone)
            val mRoot = cbrt(mCone)
            val sRoot = cbrt(sCone)

            Oklab(
                l = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
                a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
                b = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
            )
        }
    }

    fun rgbToOklab(rgb: RgbColor): Oklab = rgbToOklab(rgb.r, rgb.g, rgb.b)

    /**
     * Oklab 感知色彩距离（乘以 100）。
     * 替换原有 RGB 欧氏距离，彻底消除蓝紫色相偏移与深色误判。
     */
    fun oklabDistance(rgb1: RgbColor, rgb2: RgbColor): Double {
        val o1 = rgbToOklab(rgb1)
        val o2 = rgbToOklab(rgb2)
        return o1.distance(o2)
    }

    /**
     * 在给定色板中寻找 Oklab 感知距离最接近的颜色。
     * 使用 distanceSquared 比较，避免循环内频繁调用 sqrt。
     */
    fun findClosestPaletteColor(target: RgbColor, palette: List<PaletteColor>): PaletteColor {
        if (palette.isEmpty()) return PaletteColor("ERR", "#000000", RgbColor(0, 0, 0))
        val targetLab = rgbToOklab(target)
        var minD2 = Double.MAX_VALUE
        var closest = palette[0]
        for (pc in palette) {
            val pcLab = rgbToOklab(pc.rgb)
            val d2 = targetLab.distanceSquared(pcLab)
            if (d2 < minD2) {
                minD2 = d2
                closest = pc
                if (d2 == 0.0) break
            }
        }
        return closest
    }
}
