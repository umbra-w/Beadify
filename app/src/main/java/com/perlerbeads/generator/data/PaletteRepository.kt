package com.perlerbeads.generator.data

import android.content.Context
import com.perlerbeads.generator.algorithm.hexToRgb
import com.perlerbeads.generator.model.BeadBrand
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * 色板仓库：支持多品牌色板（Mard 291色、Artkal S 199色、Artkal C 174色、Artkal A 145色、Perler 103色、Hama 92色），
 * 并按色号系统 / 自定义勾选 / 豆仓库存生成活动色板。
 */
class PaletteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val brandPaletteCache = ConcurrentHashMap<BeadBrand, List<PaletteColor>>()

    /** 既有属性兼容：默认 MARD 291 色，key=hex。 */
    val fullBeadPalette: List<PaletteColor> by lazy {
        getPaletteForBrand(BeadBrand.MARD)
    }

    /** 获取指定品牌的完整官方色板。 */
    fun getPaletteForBrand(brand: BeadBrand): List<PaletteColor> {
        return brandPaletteCache.computeIfAbsent(brand) {
            loadBrandPalette(brand)
        }
    }

    private fun loadBrandPalette(brand: BeadBrand): List<PaletteColor> {
        if (brand == BeadBrand.MARD || brand.assetPath == null) {
            return ColorSystemMapping.load(appContext).mapNotNull { (hex, mapping) ->
                val rgb = hexToRgb(hex) ?: return@mapNotNull null
                val mardKey = mapping[ColorSystem.MARD] ?: hex
                PaletteColor(
                    key = mardKey,
                    hex = hex.uppercase(),
                    rgb = rgb,
                    name = mardKey,
                    brand = BeadBrand.MARD
                )
            }
        }

        return try {
            val text = appContext.assets.open(brand.assetPath).bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            val list = ArrayList<PaletteColor>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val code = obj.optString("displayCode", obj.optString("code", ""))
                val name = obj.optString("name", code)
                val hex = obj.getString("hex").uppercase()
                val rgbArr = obj.getJSONArray("rgb")
                val rgb = RgbColor(rgbArr.getInt(0), rgbArr.getInt(1), rgbArr.getInt(2))
                list.add(
                    PaletteColor(
                        key = code,
                        hex = hex,
                        rgb = rgb,
                        name = name,
                        brand = brand
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** 按色号系统转换显示 key（仅对 MARD 品牌有效；其余品牌保持原厂官方色号）。 */
    fun convertPaletteToColorSystem(palette: List<PaletteColor>, colorSystem: ColorSystem): List<PaletteColor> {
        val mapping = ColorSystemMapping.load(appContext)
        return palette.map { color ->
            if (color.brand == BeadBrand.MARD) {
                val m = mapping[color.hex.uppercase()]
                val key = m?.get(colorSystem)
                if (key != null) color.copy(key = key) else color
            } else {
                color
            }
        }
    }

    /** 既有两参数兼容重载。 */
    fun buildActivePalette(selections: Map<String, Boolean>?, colorSystem: ColorSystem): List<PaletteColor> {
        return buildActivePalette(
            brand = BeadBrand.MARD,
            selections = selections,
            colorSystem = colorSystem
        )
    }

    /**
     * 全功能活动色板构建：支持指定品牌、勾选过滤、色号系统转换与豆仓库存筛选。
     */
    fun buildActivePalette(
        brand: BeadBrand,
        selections: Map<String, Boolean>?,
        colorSystem: ColorSystem,
        onlyInStock: Boolean = false,
        inStockHexes: Set<String>? = null
    ): List<PaletteColor> {
        val fullList = getPaletteForBrand(brand)
        var filtered = fullList.filter { pc ->
            selections?.get(pc.hex.uppercase()) ?: true
        }

        if (onlyInStock && inStockHexes != null && inStockHexes.isNotEmpty()) {
            val stockFiltered = filtered.filter { inStockHexes.contains(it.hex.uppercase()) }
            if (stockFiltered.isNotEmpty()) {
                filtered = stockFiltered
            }
        }

        if (filtered.isEmpty()) {
            filtered = fullList
        }

        return convertPaletteToColorSystem(filtered, colorSystem)
    }
}
