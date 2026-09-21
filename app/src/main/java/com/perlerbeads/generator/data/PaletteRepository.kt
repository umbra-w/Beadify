package com.perlerbeads.generator.data

import android.content.Context
import com.perlerbeads.generator.algorithm.hexToRgb
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.model.PaletteColor

/**
 * 色板仓库：构建全量色板（key=hex），并按色号系统 / 自定义勾选生成活动色板。
 * 对应 page.tsx fullBeadPalette（L71-81）与 colorSystemUtils.convertPaletteToColorSystem（L46-61）。
 */
class PaletteRepository(context: Context) {

    private val appContext = context.applicationContext

    /** 全量 291 色，key=hex。 */
    val fullBeadPalette: List<PaletteColor> by lazy {
        ColorSystemMapping.load(appContext).mapNotNull { (hex, _) ->
            val rgb = hexToRgb(hex) ?: return@mapNotNull null
            PaletteColor(key = hex, hex = hex, rgb = rgb)
        }
    }

    /** 按色号系统转换显示 key。 */
    fun convertPaletteToColorSystem(palette: List<PaletteColor>, colorSystem: ColorSystem): List<PaletteColor> {
        val mapping = ColorSystemMapping.load(appContext)
        return palette.map { color ->
            val m = mapping[color.hex.uppercase()]
            val key = m?.get(colorSystem)
            if (key != null) color.copy(key = key) else color
        }
    }

    /** 根据勾选生成活动色板；无勾选时默认全量。 */
    fun buildActivePalette(selections: Map<String, Boolean>?, colorSystem: ColorSystem): List<PaletteColor> {
        val filtered = fullBeadPalette.filter { pc ->
            selections?.get(pc.hex.uppercase()) ?: true
        }
        return convertPaletteToColorSystem(filtered, colorSystem)
    }
}
