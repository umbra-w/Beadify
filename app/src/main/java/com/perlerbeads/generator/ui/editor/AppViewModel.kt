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
import com.perlerbeads.generator.algorithm.boardCount
import com.perlerbeads.generator.algorithm.boardProgressKey
import com.perlerbeads.generator.algorithm.calculatePixelGrid
import com.perlerbeads.generator.algorithm.excludeColor
import com.perlerbeads.generator.algorithm.floodFillErase
import com.perlerbeads.generator.algorithm.hexToRgb
import com.perlerbeads.generator.algorithm.paintSinglePixel
import com.perlerbeads.generator.algorithm.recalculateColorStats
import com.perlerbeads.generator.algorithm.replaceColor
import com.perlerbeads.generator.data.PaletteRepository
import com.perlerbeads.generator.data.ProjectStore
import com.perlerbeads.generator.data.SavedProject
import com.perlerbeads.generator.data.SettingsStore
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.model.CircleGeometry
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.circleGeometry
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
    val projectStore = ProjectStore(app)

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

    /**
     * 圆形画板几何（网格坐标系，单位=格）：圆框圈住的格子 = 最终产品。
     * 生成时由设置里的覆盖范围滑块初始化，编辑页双指缩放/平移会实时更新它，
     * 统计、可编辑区域、导出全部以此为准。方形画板为 null。
     */
    var circleFrame by mutableStateOf<CircleGeometry?>(null)
        private set

    var processing by mutableStateOf(false)
        private set

    var aiProcessing by mutableStateOf(false)
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    // ---------- 编辑历史（撤回/重做） ----------

    private val editHistory = mutableListOf<Array<Array<MappedPixel>>>()
    private var redoStack = mutableListOf<Array<Array<MappedPixel>>>()

    /** 是否有操作可撤回。 */
    val canUndo: Boolean get() = editHistory.isNotEmpty()

    /** 是否有操作可重做。 */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** 保存当前网格快照到历史栈（上限 50 步）。 */
    private fun saveSnapshot() {
        val g = gridData ?: return
        if (editHistory.size >= 50) editHistory.removeAt(0)
        editHistory.add(g.deepCopyCells())
        redoStack.clear() // 新操作清空重做栈
    }

    /** 撤回一步。 */
    fun undo() {
        val g = gridData ?: return
        if (editHistory.isEmpty()) return
        redoStack.add(g.deepCopyCells())
        g.cells = editHistory.removeAt(editHistory.lastIndex)
        gridVersion++
        recomputeStats()
        toast = "已撤回"
    }

    /** 重做一步。 */
    fun redo() {
        val g = gridData ?: return
        if (redoStack.isEmpty()) return
        editHistory.add(g.deepCopyCells())
        g.cells = redoStack.removeAt(redoStack.lastIndex)
        gridVersion++
        recomputeStats()
        toast = "已重做"
    }

    /** 清空编辑历史（换图/调参时调用）。 */
    fun clearEditHistory() {
        editHistory.clear()
        redoStack.clear()
    }

    init {
        refreshActivePalette()
        refreshProjects()
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
        circleFrame = null
        screen = Screen.Crop
    }

    fun onCropDone(bmp: Bitmap) {
        bitmap = bmp
        gridData = null
        stats = null
        circleFrame = null
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
                // calculatePixelGrid 内部完成 下采样 + RGB距离映射（可选 FS 抖动）
                val initial = calculatePixelGrid(
                    bmp, n, m, palette, settings.mode, t1, settings.dithering
                )

                val initialKeys = HashSet<String>()
                for (row in initial) {
                    for (cell in row) {
                        if (cell.key != TRANSPARENT_KEY && !cell.isExternal) {
                            initialKeys.add(cell.colorHex.uppercase())
                        }
                    }
                }
                GridData(n, m, initial, initialKeys, settings.gridShape)
            }
            gridData = result
            // 圆形画板：用设置的覆盖范围滑块初始化圆框；编辑页手势会实时更新它
            circleFrame = if (settings.gridShape == GridShape.CIRCLE) {
                circleGeometry(result.n, result.m, settings.circleOffsetX, settings.circleOffsetY)
            } else {
                null
            }
            clearEditHistory()
            recomputeStats()
            selectedPaintColor = gridPalette.firstOrNull()
            processing = false
            screen = Screen.Editor
        }
    }

    fun recomputeStats() {
        val g = gridData ?: return
        stats = recalculateColorStats(g.cells, circleFilter(g))
    }

    /** 圆形画板：只统计/编辑圆框内格子；方形返回 null（不过滤）。 */
    private fun circleFilter(g: GridData): ((Int, Int) -> Boolean)? {
        val geo = effectiveCircleFrame(g) ?: return null
        return { row, col -> geo.contains(row, col) }
    }

    /** 当前生效的圆框：编辑页手势更新过的优先，否则用设置的覆盖范围滑块推导。 */
    private fun effectiveCircleFrame(g: GridData): CircleGeometry? {
        if (g.shape != GridShape.CIRCLE) return null
        return circleFrame ?: circleGeometry(g.n, g.m, settings.circleOffsetX, settings.circleOffsetY)
    }

    /** 该格子是否可编辑（越界或圆形画板圆框外不可编辑）。 */
    private fun isEditable(g: GridData, row: Int, col: Int): Boolean {
        if (row !in 0 until g.m || col !in 0 until g.n) return false
        val geo = effectiveCircleFrame(g) ?: return true
        return geo.contains(row, col)
    }

    /** 编辑页双指调整圆框取位后调用：更新几何并重算统计。 */
    fun updateCircleFrame(geo: CircleGeometry) {
        val g = gridData ?: return
        if (g.shape != GridShape.CIRCLE) return
        circleFrame = geo
        recomputeStats()
    }

    // ---------- 编辑 ----------

    // 笔画级撤销：beginStroke 存一次快照，strokePaint/strokeErase 不逐步存，
    // endStroke 时无变化则回滚快照 —— 一笔拖动 = 一步撤回。

    private var strokeActive = false
    private var strokeChanged = false

    fun beginStroke() {
        if (strokeActive) return
        saveSnapshot()
        strokeActive = true
        strokeChanged = false
    }

    fun strokePaint(row: Int, col: Int) {
        val g = gridData ?: return
        if (!isEditable(g, row, col)) return
        val c = selectedPaintColor ?: return
        if (!strokeActive) beginStroke()
        val res = paintSinglePixel(g.cells, row, col, MappedPixel(c.key, c.hex, false))
        if (res.hasChange) {
            g.cells = res.grid
            strokeChanged = true
            gridVersion++
        }
    }

    fun strokeErase(row: Int, col: Int) {
        val g = gridData ?: return
        if (!isEditable(g, row, col)) return
        val cell = g.cells.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isExternal) return
        if (!strokeActive) beginStroke()
        val res = paintSinglePixel(g.cells, row, col, transparentColorData)
        if (res.hasChange) {
            g.cells = res.grid
            strokeChanged = true
            gridVersion++
        }
    }

    fun endStroke() {
        if (!strokeActive) return
        if (strokeChanged) {
            recomputeStats()
        } else if (editHistory.isNotEmpty()) {
            editHistory.removeAt(editHistory.lastIndex)
        }
        strokeActive = false
        strokeChanged = false
    }

    /** 取消进行中的笔画并回滚已涂的格子（双指接管缩放时调用，避免捏合误涂）。 */
    fun cancelStroke() {
        if (!strokeActive) return
        if (editHistory.isNotEmpty()) {
            val snapshot = editHistory.removeAt(editHistory.lastIndex)
            gridData?.let { g ->
                g.cells = snapshot
                gridVersion++
                recomputeStats()
            }
        }
        strokeActive = false
        strokeChanged = false
    }

    fun floodErase(row: Int, col: Int) {
        val g = gridData ?: return
        if (!isEditable(g, row, col)) return
        val cell = g.cells.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isExternal) return
        saveSnapshot()
        g.cells = floodFillErase(g.cells, row, col, cell.key)
        gridVersion++
        recomputeStats()
    }

    fun replaceAll(target: PaletteColor?) {
        val g = gridData ?: return
        val src = selectedPaintColor ?: return
        val t = target ?: return
        saveSnapshot()
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
        saveSnapshot()
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

    // ---------- 分板跟做 ----------

    var boardSize by mutableStateOf(settings.boardSize)
        private set

    var completedBoards by mutableStateOf<Set<Int>>(emptySet())
        private set

    var currentBoard by mutableStateOf(0)
        private set

    /** 当前网格的归属判定（圆形画板 = 圆框内），供分板统计使用。 */
    fun scopeFilter(): ((row: Int, col: Int) -> Boolean)? {
        val g = gridData ?: return null
        return circleFilter(g)
    }

    /** 进入分板页：加载进度并定位到第一块未完成板。 */
    fun enterBoardWork() {
        val g = gridData ?: return
        completedBoards = settings.loadBoardProgress(boardProgressKey(g, boardSize))
        currentBoard = firstIncompleteBoard(g)
        screen = Screen.BoardWork
    }

    fun changeBoardSize(size: Int) {
        val next = size.coerceIn(8, 96)
        if (next == boardSize) return
        boardSize = next
        settings.boardSize = next
        val g = gridData ?: return
        completedBoards = settings.loadBoardProgress(boardProgressKey(g, boardSize))
        currentBoard = firstIncompleteBoard(g)
    }

    fun selectBoard(index: Int) {
        currentBoard = index
    }

    /** 标记/取消标记一块板完成；标记后自动跳到下一块未完成板。 */
    fun toggleBoardDone(index: Int) {
        val g = gridData ?: return
        val next = if (index in completedBoards) completedBoards - index else completedBoards + index
        completedBoards = next
        settings.saveBoardProgress(boardProgressKey(g, boardSize), next)
        if (index in next) {
            currentBoard = firstIncompleteBoard(g)
        }
    }

    private fun firstIncompleteBoard(g: GridData): Int {
        val total = boardCount(g.n, g.m, boardSize)
        if (total <= 0) return 0
        return (0 until total).firstOrNull { it !in completedBoards } ?: total - 1
    }

    // ---------- 文字拼豆 ----------

    /** 由文字直接生成网格（笔画用所选颜色，背景透明），成功后进入编辑器。 */
    fun generateTextBeads(text: String, gridRows: Int, color: PaletteColor?) {
        val c = color ?: activePalette.firstOrNull() ?: run {
            toast = "当前色板为空，请先在色板设置中选择颜色"
            return
        }
        if (text.isBlank()) {
            toast = "请输入文字"
            return
        }
        processing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                com.perlerbeads.generator.algorithm.TextBeads.renderTextGrid(text, gridRows, c)
            }
            processing = false
            if (result == null) {
                toast = "文字无法生成有效笔画"
                return@launch
            }
            gridData = result
            circleFrame = null
            bitmap = null
            excludedHexes = emptySet()
            clearEditHistory()
            recomputeStats()
            selectedPaintColor = c
            toast = null
            screen = Screen.Editor
        }
    }

    // ---------- 项目保存/加载 ----------

    var projects by mutableStateOf<List<ProjectStore.ProjectMeta>>(emptyList())
        private set

    fun refreshProjects() {
        projects = projectStore.list()
    }

    /** 保存当前图纸为项目（不含原图，打开后可编辑/导出/分板，无法重新像素化）。 */
    fun saveCurrentProject(name: String) {
        val g = gridData ?: return
        val saved = SavedProject(
            name = name.ifBlank { "未命名" },
            shape = g.shape,
            circle = circleFrame,
            granularity = settings.granularity,
            mode = settings.mode,
            dithering = settings.dithering,
            colorSystemKey = settings.colorSystem.key,
            cells = g.cells
        )
        projectStore.save(saved)
        refreshProjects()
        toast = "已保存到我的项目"
    }

    fun openProject(id: String) {
        val p = projectStore.load(id) ?: run {
            toast = "项目读取失败"
            return
        }
        gridData = GridData(p.n, p.m, p.cells, emptySet(), p.shape)
        circleFrame = p.circle
        bitmap = null
        excludedHexes = emptySet()
        settings.granularity = p.granularity
        settings.mode = p.mode
        settings.dithering = p.dithering
        settings.colorSystem = ColorSystem.fromKey(p.colorSystemKey)
        refreshActivePalette()
        clearEditHistory()
        recomputeStats()
        selectedPaintColor = gridPalette.firstOrNull()
        toast = "已打开项目"
        screen = Screen.Editor
    }

    fun deleteProject(id: String) {
        projectStore.delete(id)
        refreshProjects()
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
