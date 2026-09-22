package com.perlerbeads.generator.data

import android.content.Context
import android.content.SharedPreferences
import com.perlerbeads.generator.model.BeadBrand
import com.perlerbeads.generator.model.PaletteColor
import org.json.JSONObject

/**
 * 用户豆仓库存本地持久化。
 * 按品牌隔离存储每种颜色的库存状态（拥有 / 缺货）。
 */
class InventoryStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("perler_inventory_prefs", Context.MODE_PRIVATE)

    /** 检查用户是否已自定义过该品牌的豆仓库存。未配置时默认全量拥有。 */
    fun isInventoryConfigured(brand: BeadBrand): Boolean =
        prefs.contains(KEY_PREFIX_CONFIGURED + brand.id)

    /** 检查指定品牌下的某个颜色是否在库存中。 */
    fun isInStock(brand: BeadBrand, hex: String): Boolean {
        if (!isInventoryConfigured(brand)) return true
        val jsonStr = prefs.getString(KEY_PREFIX_STOCK + brand.id, null) ?: return true
        return try {
            val obj = JSONObject(jsonStr)
            val upper = hex.uppercase()
            if (obj.has(upper)) obj.getBoolean(upper) else false
        } catch (e: Exception) {
            true
        }
    }

    /** 更新单个颜色的库存状态。 */
    fun setInStock(brand: BeadBrand, hex: String, inStock: Boolean) {
        val map = loadStockMap(brand).toMutableMap()
        map[hex.uppercase()] = inStock
        saveStockMap(brand, map)
    }

    /** 批量更新颜色库存状态（例如全选或按系列选）。 */
    fun setBatchStock(brand: BeadBrand, hexList: Collection<String>, inStock: Boolean) {
        val map = loadStockMap(brand).toMutableMap()
        for (hex in hexList) {
            map[hex.uppercase()] = inStock
        }
        saveStockMap(brand, map)
    }

    /** 重置该品牌库存（例如全部设为在库或全部清空）。 */
    fun resetAll(brand: BeadBrand, allColors: List<PaletteColor>, inStock: Boolean) {
        val map = HashMap<String, Boolean>(allColors.size)
        for (c in allColors) {
            map[c.hex.uppercase()] = inStock
        }
        saveStockMap(brand, map)
    }

    /** 一次性批量保存该品牌的完整库存字典（原子快速持久化）。 */
    fun saveAllStock(brand: BeadBrand, map: Map<String, Boolean>) {
        saveStockMap(brand, map)
    }

    /** 获取当前品牌下已入库的 HEX 集合。 */
    fun getInStockHexes(brand: BeadBrand, fullPalette: List<PaletteColor>): Set<String> {
        if (!isInventoryConfigured(brand)) {
            return fullPalette.map { it.hex.uppercase() }.toSet()
        }
        val stockMap = loadStockMap(brand)
        return fullPalette.filter { stockMap[it.hex.uppercase()] == true }
            .map { it.hex.uppercase() }
            .toSet()
    }

    /** 读取指定品牌的完整库存映射表。 */
    fun loadStockMap(brand: BeadBrand): Map<String, Boolean> {
        val jsonStr = prefs.getString(KEY_PREFIX_STOCK + brand.id, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val map = HashMap<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getBoolean(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveStockMap(brand: BeadBrand, map: Map<String, Boolean>) {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        prefs.edit()
            .putString(KEY_PREFIX_STOCK + brand.id, obj.toString())
            .putBoolean(KEY_PREFIX_CONFIGURED + brand.id, true)
            .apply()
    }

    companion object {
        private const val KEY_PREFIX_STOCK = "stock_"
        private const val KEY_PREFIX_CONFIGURED = "configured_"
    }
}
