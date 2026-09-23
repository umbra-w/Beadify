package com.perlerbeads.generator.data

import android.content.Context
import android.content.SharedPreferences
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.PixelationMode
import org.json.JSONObject

/** 用户设置与色板勾选的本地存储。 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("perler_prefs", Context.MODE_PRIVATE)

    var colorSystem: ColorSystem
        get() = ColorSystem.fromKey(prefs.getString(KEY_COLOR_SYSTEM, "MARD") ?: "MARD")
        set(v) { prefs.edit().putString(KEY_COLOR_SYSTEM, v.key).apply() }

    var granularity: Int
        get() = prefs.getInt(KEY_GRANULARITY, 50)
        set(v) { prefs.edit().putInt(KEY_GRANULARITY, v).apply() }

    var mode: PixelationMode
        get() = when (prefs.getString(KEY_MODE, "DOMINANT")) {
            "AVERAGE" -> PixelationMode.AVERAGE
            else -> PixelationMode.DOMINANT
        }
        set(v) { prefs.edit().putString(KEY_MODE, v.name).apply() }

    var gridShape: GridShape
        get() = when (prefs.getString(KEY_SHAPE, "SQUARE")) {
            "CIRCLE" -> GridShape.CIRCLE
            else -> GridShape.SQUARE
        }
        set(v) { prefs.edit().putString(KEY_SHAPE, v.name).apply() }

    /** Floyd-Steinberg 抖动过渡（照片类图片在有限色板下过渡更自然）。 */
    var dithering: Boolean
        get() = prefs.getBoolean(KEY_DITHERING, false)
        set(v) { prefs.edit().putBoolean(KEY_DITHERING, v).apply() }

    /** 圆形模式下，圆心偏移（0..1）。0.5=居中。 */
    var circleOffsetX: Float
        get() = prefs.getFloat(KEY_CIRCLE_X, 0.5f)
        set(v) { prefs.edit().putFloat(KEY_CIRCLE_X, v.coerceIn(0f, 1f)).apply() }

    var circleOffsetY: Float
        get() = prefs.getFloat(KEY_CIRCLE_Y, 0.5f)
        set(v) { prefs.edit().putFloat(KEY_CIRCLE_Y, v.coerceIn(0f, 1f)).apply() }

    /** AI 服务地址（网页版 /api/ai-optimize 的完整 URL），空 = 未配置。 */
    var aiServiceUrl: String
        get() = prefs.getString(KEY_AI_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_AI_URL, v.trim()).apply() }

    /** AI 模型标识：默认 image2。 */
    var aiReqKey: String
        get() = prefs.getString(KEY_AI_REQKEY, "image2") ?: "image2"
        set(v) { prefs.edit().putString(KEY_AI_REQKEY, v.trim()).apply() }

    /** 拼豆品牌/系列，默认 MARD (国内通用 291色)。 */
    var beadBrand: com.perlerbeads.generator.model.BeadBrand
        get() = com.perlerbeads.generator.model.BeadBrand.fromId(prefs.getString(KEY_BEAD_BRAND, "mard") ?: "mard")
        set(v) { prefs.edit().putString(KEY_BEAD_BRAND, v.id).apply() }

    /** 像素化生成约束：仅使用豆仓已有库存颜色。 */
    var onlyInStockGeneration: Boolean
        get() = prefs.getBoolean(KEY_ONLY_IN_STOCK, false)
        set(v) { prefs.edit().putBoolean(KEY_ONLY_IN_STOCK, v).apply() }

    /** 保存当前品牌色板勾选（hex → 是否选中）。 */
    fun savePaletteSelections(selections: Map<String, Boolean>) {
        savePaletteSelections(beadBrand, selections)
    }

    /** 保存指定品牌色板勾选（hex → 是否选中）。 */
    fun savePaletteSelections(brand: com.perlerbeads.generator.model.BeadBrand, selections: Map<String, Boolean>) {
        val obj = JSONObject()
        selections.forEach { (k, v) -> obj.put(k, v) }
        val key = if (brand == com.perlerbeads.generator.model.BeadBrand.MARD) KEY_PALETTE else KEY_PALETTE_PREFIX + brand.id
        prefs.edit().putString(key, obj.toString()).apply()
    }

    /** 读取当前品牌色板勾选；从未保存过则返回 null（表示全量）。 */
    fun loadPaletteSelections(): Map<String, Boolean>? = loadPaletteSelections(beadBrand)

    /** 读取指定品牌色板勾选；从未保存过则返回 null（表示全量）。 */
    fun loadPaletteSelections(brand: com.perlerbeads.generator.model.BeadBrand): Map<String, Boolean>? {
        val key = if (brand == com.perlerbeads.generator.model.BeadBrand.MARD) KEY_PALETTE else KEY_PALETTE_PREFIX + brand.id
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val map = HashMap<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getBoolean(k)
            }
            map
        } catch (e: Exception) {
            null
        }
    }

    /** 分板跟做：实体板尺寸（格），默认 29（MARD 大板）。 */
    var boardSize: Int
        get() = prefs.getInt(KEY_BOARD_SIZE, 29)
        set(v) { prefs.edit().putInt(KEY_BOARD_SIZE, v.coerceIn(8, 96)).apply() }

    /** PDF 导出：拼豆实物规格（2.6mm 迷你豆 / 5.0mm 标准豆），默认 2.6mm。 */
    var pdfBeadPitch: com.perlerbeads.generator.model.BeadPitch
        get() = com.perlerbeads.generator.model.BeadPitch.fromMm(prefs.getFloat("pdf_bead_pitch_mm", 2.6f))
        set(v) { prefs.edit().putFloat("pdf_bead_pitch_mm", v.mm).apply() }

    /** 分板跟做：按进度键保存已完成板序号集合。 */
    fun saveBoardProgress(key: String, completed: Set<Int>) {
        prefs.edit().putString(KEY_BOARD_PROGRESS_PREFIX + key, completed.joinToString(",")).apply()
    }

    /** 分板跟做：读取已完成板序号集合；无记录返回空集。 */
    fun loadBoardProgress(key: String): Set<Int> =
        prefs.getString(KEY_BOARD_PROGRESS_PREFIX + key, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    /** 逐格跟做：保存已完成格子的全局平坦索引集合（压缩区间存储）。 */
    fun saveCellProgress(key: String, completed: Set<Int>) {
        prefs.edit().putString(KEY_CELL_PROGRESS_PREFIX + key, com.perlerbeads.generator.algorithm.encodeCellIndices(completed)).apply()
    }

    /** 逐格跟做：读取已完成格子的全局平坦索引集合。 */
    fun loadCellProgress(key: String): Set<Int> =
        prefs.getString(KEY_CELL_PROGRESS_PREFIX + key, null)?.let {
            com.perlerbeads.generator.algorithm.decodeCellIndices(it)
        } ?: emptySet()

    /** 受控色数上限（0=不限制，16/24/32/48 等）。 */
    var maxColors: Int
        get() = prefs.getInt(KEY_MAX_COLORS, 0)
        set(v) { prefs.edit().putInt(KEY_MAX_COLORS, v).apply() }

    /** 孤立噪点自动清理（去除 1 格无意义孤岛飞点）。 */
    var cleanupIslands: Boolean
        get() = prefs.getBoolean(KEY_CLEANUP_ISLANDS, false)
        set(v) { prefs.edit().putBoolean(KEY_CLEANUP_ISLANDS, v).apply() }

    /** 相似颜色合并阈值 (0=不合并, 1..60，推荐 15)。 */
    var similarityThreshold: Int
        get() = prefs.getInt(KEY_SIMILARITY_THRESHOLD, 0)
        set(v) { prefs.edit().putInt(KEY_SIMILARITY_THRESHOLD, v.coerceIn(0, 100)).apply() }

    /** 网格辅助线间隔 (0=不显示, 5, 10)。默认 10。 */
    var gridInterval: Int
        get() = prefs.getInt(KEY_GRID_INTERVAL, 10)
        set(v) { prefs.edit().putInt(KEY_GRID_INTERVAL, v).apply() }

    /** 网格辅助线颜色 hex，默认深灰 #555555。 */
    var gridLineColorHex: String
        get() = prefs.getString(KEY_GRID_LINE_COLOR, "#555555") ?: "#555555"
        set(v) { prefs.edit().putString(KEY_GRID_LINE_COLOR, v).apply() }

    companion object {
        private const val KEY_COLOR_SYSTEM = "color_system"
        private const val KEY_BEAD_BRAND = "bead_brand"
        private const val KEY_ONLY_IN_STOCK = "only_in_stock_generation"
        private const val KEY_GRANULARITY = "granularity"
        private const val KEY_MODE = "pixelation_mode"
        private const val KEY_SHAPE = "grid_shape"
        private const val KEY_DITHERING = "dithering"
        private const val KEY_CIRCLE_X = "circle_offset_x"
        private const val KEY_CIRCLE_Y = "circle_offset_y"
        private const val KEY_PALETTE = "palette_selections"
        private const val KEY_PALETTE_PREFIX = "palette_selections_"
        private const val KEY_AI_URL = "ai_service_url"
        private const val KEY_AI_REQKEY = "ai_req_key"
        private const val KEY_BOARD_SIZE = "board_size"
        private const val KEY_BOARD_PROGRESS_PREFIX = "board_progress_"
        private const val KEY_CELL_PROGRESS_PREFIX = "cell_progress_"
        private const val KEY_MAX_COLORS = "max_colors"
        private const val KEY_CLEANUP_ISLANDS = "cleanup_islands"
        private const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
        private const val KEY_GRID_INTERVAL = "grid_interval"
        private const val KEY_GRID_LINE_COLOR = "grid_line_color"
    }
}