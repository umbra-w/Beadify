package com.perlerbeads.generator.data

import android.content.Context
import com.perlerbeads.generator.model.ColorSystem
import org.json.JSONObject

/**
 * 解析 assets/color_system_mapping.json。
 * 结构：{ "#HEX": { "MARD": "A01", "COCO": "...", "漫漫": "...", "盼盼": "...", "咪小窝": "..." } }
 * 对应 colorSystemUtils.ts 中的 typedColorSystemMapping。
 */
object ColorSystemMapping {
    private const val ASSET_NAME = "color_system_mapping.json"

    @Volatile
    private var cache: Map<String, Map<ColorSystem, String>>? = null

    fun load(context: Context): Map<String, Map<ColorSystem, String>> {
        cache?.let { return it }
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val result = HashMap<String, Map<ColorSystem, String>>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val hex = keys.next().uppercase()
            val entry = json.getJSONObject(hex)
            val map = HashMap<ColorSystem, String>()
            for (cs in ColorSystem.entries) {
                if (entry.has(cs.key)) map[cs] = entry.getString(cs.key)
            }
            result[hex] = map
        }
        cache = result
        return result
    }

    /** 通过 hex 获取指定色号系统的色号。对应 colorSystemUtils.getColorKeyByHex。 */
    fun getColorKeyByHex(context: Context, hex: String, colorSystem: ColorSystem): String {
        val mapping = load(context)[hex.uppercase()] ?: return "?"
        return mapping[colorSystem] ?: "?"
    }

    /** 通过色号反查 hex。 */
    fun getHexByColorKey(context: Context, displayKey: String, colorSystem: ColorSystem): String? {
        if (displayKey.startsWith("#") && displayKey.length == 7) return displayKey.uppercase()
        for ((hex, mapping) in load(context)) {
            if (mapping[colorSystem] == displayKey) return hex
        }
        return null
    }
}
