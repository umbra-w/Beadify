package com.perlerbeads.generator.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.perlerbeads.generator.algorithm.ColorStats
import com.perlerbeads.generator.algorithm.autoRemoveBackground
import com.perlerbeads.generator.algorithm.calculatePixelGrid
import com.perlerbeads.generator.algorithm.excludeColor
import com.perlerbeads.generator.algorithm.floodFillErase
import com.perlerbeads.generator.algorithm.hexToRgb
import com.perlerbeads.generator.algorithm.paintSinglePixel
import com.perlerbeads.generator.algorithm.recalculateColorStats
import com.perlerbeads.generator.algorithm.replaceColor
import com.perlerbeads.generator.data.PaletteRepository
import com.perlerbeads.generator.data.SettingsStore
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData
import com.perlerbeads.generator.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/** 应用级共享状态与编辑操作。 */
private const val DEFAULT_AI_PROMPT =
    "图片修改为：chibi画风，背景白底。pixel art style, 16-bit, retro game aesthetic, sharp focus, high contrast, clean lines, detailed pixel art, masterpiece, best quality"

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    val paletteRepository = PaletteRepository(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var bitmap by mutableStateOf<Bitmap?>(null)
        private set

    var activePalette by mutableStateOf<List<PaletteColor>>(emptyList())
        private set

    var gridData by mutableStateOf<GridData?>(null)
        private set

    /** 网格每次变更自增，用于驱动画布重新渲染。 */
    var gridVersion by mutableIntStateOf(0)
        private set

    var excludedHexes by mutableStateOf<Set<String>>(emptySet())
        private set

    var selectedPaintColor by mutableStateOf<PaletteColor?>(null)
        private set

    var stats by mutableStateOf<ColorStats?>(null)
        private set

    var processing by mutableStateOf(false)
        private set

    var aiProcessing by mutableStateOf(false)
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    init {
        refreshActivePalette()
    }

    // ---------- 色板 ----------

    fun refreshActivePalette() {
        activePalette = paletteRepository
            .buildActivePalette(settings.loadPaletteSelections(), settings.colorSystem)
            .filter { it.hex.uppercase() !in excludedHexes }
        // 若当前画笔色已被排除/移除，回退到第一个可用色
        val sel = selectedPaintColor
        if (sel != null && activePalette.none { it.hex.uppercase() == sel.hex.uppercase() }) {
            selectedPaintColor = activePalette.firstOrNull()
        }
    }

    fun setColorSystem(cs: ColorSystem) {
        settings.colorSystem = cs
        refreshActivePalette()
    }

    fun setSelectedPaint(color: PaletteColor) {
        selectedPaintColor = color
    }

    fun consumeToast() {
        toast = null
    }

    // ---------- 图片 ----------

    fun onImagePicked(context: Context, uri: Uri) {
        val bmp = decodeSampledBitmap(context, uri) ?: return
        bitmap = bmp
        gridData = null
        stats = null
        screen = Screen.Crop
    }

    fun onCropDone(bmp: Bitmap) {
        bitmap = bmp
        gridData = null
        stats = null
        screen = Screen.Settings
    }

    fun goHome() {
        screen = Screen.Home
    }

    fun navigate(target: Screen) {
        screen = target
    }

    // ---------- 生成 ----------

    fun generate() {
        val bmp = bitmap ?: return
        refreshActivePalette()
        val palette = activePalette
        if (palette.isEmpty()) {
            toast = "当前可用颜色为空，请先恢复或更换色板"
            return
        }
        processing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val t1 = palette.firstOrNull { it.key == "T1" }
                    ?: palette.firstOrNull { it.hex.uppercase() == "#FFFFFF" }
                    ?: palette[0]
                val n = settings.granularity
                val aspect = bmp.height.toDouble() / bmp.width.toDouble()
                val m = Math.max(1, Math.round(n * aspect).toInt())
                // calculatePixelGrid 内部完成 下采样 + RGB距离映射
                val initial = calculatePixelGrid(
                    bmp, n, m, palette, settings.mode, t1
                )

                val initialKeys = HashSet<String>()
                for (row in initial) {
                    for (cell in row) {
                        if (cell.key != TRANSPARENT_KEY && !cell.isExternal) {
                            initialKeys.add(cell.colorHex.uppercase())
                        }
                    }
                }
                GridData(n, m, initial, initialKeys)
            }
            gridData = result
            recomputeStats()
            selectedPaintColor = gridPalette.firstOrNull()
            processing = false
            screen = Screen.Editor
        }
    }

    fun recomputeStats() {
        val g = gridData ?: return
        stats = recalculateColorStats(g.cells)
    }

    // ---------- 编辑 ----------

    fun paintCell(row: Int, col: Int, color: PaletteColor?) {
        val g = gridData ?: return
        val c = color ?: selectedPaintColor ?: return
        val res = paintSinglePixel(g.cells, row, col, MappedPixel(c.key, c.hex, false))
        if (res.hasChange) {
            g.cells = res.grid
            gridVersion++
            recomputeStats()
        }
    }

    fun eraseCell(row: Int, col: Int) {
        val g = gridData ?: return
        val cell = g.cells.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isExternal) return
        val res = paintSinglePixel(g.cells, row, col, transparentColorData)
        if (res.hasChange) {
            g.cells = res.grid
            gridVersion++
            recomputeStats()
        }
    }

    fun floodErase(row: Int, col: Int) {
        val g = gridData ?: return
        val cell = g.cells.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isExternal) return
        g.cells = floodFillErase(g.cells, row, col, cell.key)
        gridVersion++
        recomputeStats()
    }

    fun replaceAll(target: PaletteColor?) {
        val g = gridData ?: return
        val src = selectedPaintColor ?: return
        val t = target ?: return
        val res = replaceColor(
            g.cells,
            MappedPixel(src.key, src.hex, false),
            MappedPixel(t.key, t.hex, false)
        )
        if (res.replaceCount > 0) {
            g.cells = res.grid
            gridVersion++
            recomputeStats()
            toast = "已替换 ${res.replaceCount} 粒"
        } else {
            toast = "没有可替换的单元格"
        }
    }

    fun autoRemoveBackground() {
        val g = gridData ?: return
        val res = autoRemoveBackground(g.cells)
        if (res.removedCount > 0) {
            g.cells = res.grid
            gridVersion++
            recomputeStats()
            toast = "已去除背景 ${res.removedCount} 粒"
        } else {
            toast = "边缘未识别到可去除的背景"
        }
    }

    fun toggleExclude(hex: String) {
        val g = gridData ?: return
        val normalized = hex.uppercase()
        if (normalized in excludedHexes) {
            // 恢复：从排除集移除并触发完整重处理
            excludedHexes = excludedHexes - normalized
            generate()
        } else {
            val otherExcluded = excludedHexes - normalized
            val res = excludeColor(
                g.cells,
                normalized,
                g.initialColorKeys,
                paletteRepository.fullBeadPalette,
                otherExcluded
            )
            if (res.success) {
                g.cells = res.grid
                gridVersion++
                excludedHexes = excludedHexes + normalized
                recomputeStats()
            } else {
                toast = "无法排除该颜色：图中其他可用颜色也已被排除"
            }
        }
    }

    fun restoreAllColors() {
        excludedHexes = emptySet()
        generate()
    }

    // ---------- AI 优化 ----------

    /**
     * 调用后端 AI 优化接口（网页版 /api/ai-optimize 契约）。
     * 流程：压缩编码当前源图 → POST {imageBase64, prompt, reqKey} → 下载结果图 → 替换源图并重新生成。
     * 密钥只存服务端，App 只配服务地址。
     */
    fun aiOptimize() {
        val bmp = bitmap ?: return
        val url = settings.aiServiceUrl.trim()
        if (url.isEmpty()) {
            toast = "请先在「设置」中配置 AI 服务地址"
            return
        }
        if (aiProcessing) return
        aiProcessing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = encodeBitmapForUpload(bmp)
                    val reqBody = JSONObject().apply {
                        put("imageBase64", encoded)
                        put("prompt", DEFAULT_AI_PROMPT)
                        put("reqKey", settings.aiReqKey)
                    }.toString()

                    val conn = URL(url).openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.connectTimeout = 15000
                        conn.readTimeout = 120000
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.outputStream.use { it.write(reqBody.toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode
                        val text = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                        if (code !in 200..299) error("服务返回 HTTP $code")
                        val json = JSONObject(text)
                        if (!json.optBoolean("success")) {
                            error(json.optString("error", json.optString("message", "未知错误")))
                        }
                        val imageUrl = json.optString("imageUrl")
                        if (imageUrl.isEmpty()) error("服务未返回 imageUrl")
                        downloadBitmap(imageUrl) ?: error("下载优化图失败")
                    } finally {
                        conn.disconnect()
                    }
                }
            }
            result.onSuccess { newBmp ->
                bitmap = newBmp
                gridData = null
                stats = null
                excludedHexes = emptySet()
                toast = "AI 优化完成，正在重新生成图纸"
                generate()
            }.onFailure { e ->
                toast = "AI 优化失败：${e.message ?: "未知错误"}"
            }
            aiProcessing = false
        }
    }

    private fun encodeBitmapForUpload(src: Bitmap): String {
        var bmp = src
        val maxDim = max(src.width, src.height)
        if (maxDim > 2048) {
            val scale = 2048f / maxDim
            bmp = Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        }
        var quality = 90
        var encoded = ""
        while (quality >= 40) {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            encoded = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            if (encoded.length * 3L / 4 / 1024 <= 4096) break
            quality -= 10
        }
        return encoded
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        if (imageUrl.startsWith("data:")) {
            val b64 = imageUrl.substringAfter(",")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val conn = URL(imageUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.setRequestProperty("User-Agent", "PerlerBeads-Android")
            return conn.inputStream?.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 派生 ----------

    /** 网格中实际出现的颜色（hex 去重），按使用量降序。 */
    val gridPalette: List<PaletteColor>
        get() {
            val g = gridData ?: return emptyList()
            val statsMap = stats?.counts ?: return emptyList()
            val unique = LinkedHashMap<String, PaletteColor>()
            for ((hex, _) in statsMap.entries.sortedByDescending { it.value }) {
                val pc = activePalette.firstOrNull { it.hex.uppercase() == hex }
                unique[hex] = pc ?: PaletteColor(
                    key = hex,
                    hex = hex,
                    rgb = hexToRgb(hex) ?: RgbColor(0, 0, 0)
                )
            }
            return unique.values.toList()
        }

    val totalBeadCount: Int
        get() = stats?.totalCount ?: 0

    // ---------- 工具 ----------

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val maxDim = Math.max(bounds.outWidth, bounds.outHeight)
        // 阶段7：把降采样阈值从 2000 提到 6000，避免像素化在 1/4 分辨率图上进行（丢细节、无法离线对拍）。
        // 常见照片（≤6000px）按全分辨率解码；超大图仍有内存保护。
        var sample = 1
        while (maxDim / sample > 6000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }
}
