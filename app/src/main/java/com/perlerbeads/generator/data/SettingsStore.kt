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

    /** 保存色板勾选（hex → 是否选中）。 */
    fun savePaletteSelections(selections: Map<String, Boolean>) {
        val obj = JSONObject()
        selections.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_PALETTE, obj.toString()).apply()
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

    /** 读取色板勾选；从未保存过则返回 null（表示全量）。 */
    fun loadPaletteSelections(): Map<String, Boolean>? {
        val raw = prefs.getString(KEY_PALETTE, null) ?: return null
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

    companion object {
        private const val KEY_COLOR_SYSTEM = "color_system"
        private const val KEY_GRANULARITY = "granularity"
        private const val KEY_MODE = "pixelation_mode"
        private const val KEY_SHAPE = "grid_shape"
        private const val KEY_DITHERING = "dithering"
        private const val KEY_CIRCLE_X = "circle_offset_x"
        private const val KEY_CIRCLE_Y = "circle_offset_y"
        private const val KEY_PALETTE = "palette_selections"
        private const val KEY_AI_URL = "ai_service_url"
        private const val KEY_AI_REQKEY = "ai_req_key"
        private const val KEY_BOARD_SIZE = "board_size"
        private const val KEY_BOARD_PROGRESS_PREFIX = "board_progress_"
    }
}