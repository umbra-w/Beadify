package com.perlerbeads.generator.ui.editor

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.export.ColorStatRow
import com.perlerbeads.generator.export.Exporter
import com.perlerbeads.generator.export.PdfExporter
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.circleGeometry
import com.perlerbeads.generator.model.derivedCircleGeometry
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class EditorTool { BRUSH, ERASER, FLOOD, REPLACE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val grid = vm.gridData ?: return

    var tool by remember { mutableStateOf(EditorTool.BRUSH) }
    var showStats by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showAiConfig by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // hex → Compose Color 缓存（普通 HashMap，绘制期写入安全）
    val colorCache = remember(grid) { HashMap<String, Color>() }
    // 初始圆框（圆形画板）：zoom=1、offset=0 时图案的圆区域恰好填满屏幕上的固定圆框
    val anchor = remember(grid) {
        vm.circleFrame
            ?: circleGeometry(grid.n, grid.m, vm.settings.circleOffsetX, vm.settings.circleOffsetY)
    }

    // ---------- 画布几何（containerSize 就绪后有效；方形模式 frameR 无用） ----------
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val isCircle = grid.shape == GridShape.CIRCLE
    val marginPx = with(LocalDensity.current) { 12.dp.toPx() }
    val fitR = if (isCircle && containerSize != IntSize.Zero) min(cw, ch) / 2f - marginPx else 0f
    val frameCx = cw / 2f
    val frameCy = ch / 2f
    // zoom=1 时每格像素：初始圆区域（min(n,m) 格）恰好等于适配圆框直径
    val baseCell = when {
        containerSize == IntSize.Zero -> 1f
        isCircle -> 2f * fitR / min(grid.n, grid.m)
        else -> min(cw / grid.n, ch / grid.m)
    }
    // 圆框半径可拖拽调整：从已保存的圆框半径重建，未保存过则适配容器
    var frameR by remember(grid, containerSize) {
        mutableFloatStateOf(
            if (isCircle && fitR > 0f) (anchor.radius * baseCell).coerceIn(0.2f * fitR, fitR) else 0f
        )
    }
    // 圆环拖拽把手的判定带宽（圆环中线 ± 24dp）
    val resizeTolPx = with(LocalDensity.current) { 24.dp.toPx() }
    val zoomMax = 8f

    fun zoomMinNow(): Float =
        if (isCircle) (frameR / (baseCell * 0.85f * max(grid.n, grid.m))).coerceIn(0.2f, 1f) else 0.5f

    /** 图案原点（格子 0,0 的屏幕位置）。圆形模式锚定初始圆心，方形模式居中。 */
    fun patternOrigin(cell: Float): Offset =
        if (isCircle) {
            Offset(
                frameCx - anchor.centerX * cell + offset.x,
                frameCy - anchor.centerY * cell + offset.y
            )
        } else {
            Offset((cw - grid.n * cell) / 2f + offset.x, (ch - grid.m * cell) / 2f + offset.y)
        }

    fun cellAt(pos: Offset): Pair<Int, Int>? {
        val cell = baseCell * zoom
        if (cell <= 0f) return null
        val o = patternOrigin(cell)
        val col = floor((pos.x - o.x) / cell).toInt()
        val row = floor((pos.y - o.y) / cell).toInt()
        return if (col in 0 until grid.n && row in 0 until grid.m) row to col else null
    }

    fun clampOffset(candidate: Offset): Offset {
        return if (!isCircle) {
            clampEditorOffset(candidate, cw, ch, grid.n * baseCell * zoom, grid.m * baseCell * zoom)
        } else {
            // 圆框至少与图案相交 0.7 半径，防止图案被完全拖出框
            val cs = baseCell * zoom
            val r = frameR / cs
            Offset(
                candidate.x.coerceIn(
                    (anchor.centerX - (grid.n + 0.7f * r)) * cs,
                    (anchor.centerX + 0.7f * r) * cs
                ),
                candidate.y.coerceIn(
                    (anchor.centerY - (grid.m + 0.7f * r)) * cs,
                    (anchor.centerY + 0.7f * r) * cs
                )
            )
        }
    }

    /** 把当前变换对应的圆框几何写回 ViewModel（统计/编辑/导出随之更新）。 */
    fun pushCircleFrame() {
        if (!isCircle) return
        val clamped = clampOffset(offset)
        vm.updateCircleFrame(
            derivedCircleGeometry(anchor, zoom, clamped.x, clamped.y, frameR, baseCell)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("图纸 ${grid.n}×${grid.m}") },
                navigationIcon = {
                    TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text("返回") }
                },
                actions = {
                    IconButton(onClick = { vm.enterBoardWork() }) {
                        Icon(Icons.Filled.Dashboard, contentDescription = "分板跟做")
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Filled.Save, contentDescription = "保存项目")
                    }
                    IconButton(onClick = {
                        zoom = 1f
                        offset = Offset.Zero
                        if (grid.shape == GridShape.CIRCLE) {
                            frameR = (anchor.radius * baseCell).coerceIn(0.2f * fitR, fitR)
                            vm.updateCircleFrame(anchor)
                        }
                    }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "复位视图")
                    }
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "统计")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Filled.Download, contentDescription = "导出")
                    }
                }
            )

            // ---------- 画布（矢量分层渲染：格子层 / 网格线层 / 圆形遮罩层） ----------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A2E))
                    .onSizeChanged { containerSize = it }
            ) {
                if (containerSize != IntSize.Zero) {
                    // 兜底缩放路径：transformable 在同节点上（旧编辑页已验证可用的配方）。
                    // 主手势处理器在 Main pass 优先消费事件；只有主处理器未消费时它才会接管，
                    // 用于在主处理器双指路径失效的设备上保底缩放。
                    val fallbackState = rememberTransformableState { zoomChange, panChange, _ ->
                        if (zoomChange.isFinite() && zoomChange > 0f) {
                            zoom = (zoom * zoomChange).coerceIn(zoomMinNow(), zoomMax)
                        }
                        if (panChange.x.isFinite() && panChange.y.isFinite()) {
                            offset = clampOffset(offset + panChange)
                        }
                        pushCircleFrame()
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(fallbackState)
                            .pointerInput(grid, containerSize) {
                                // 统一手势：单指=工具（单击/拖动连涂/长按洪水擦除），双指=缩放平移
                                val slopPx = viewConfiguration.touchSlop
                                val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downCell = cellAt(down.position)
                                    var mode = 0            // 0 待定 1 工具 2 缩放平移
                                    var painting = false    // 笔画进行中
                                    var longFired = false
                                    var pastSlop = false
                                    var lastCell: Pair<Int, Int>? = null
                                    var last = down.position
                                    var lastPointerId = down.id

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        if (pressed.size >= 2) {
                                            // 双指：回滚误涂笔画（捏合不应落笔），进入缩放平移
                                            if (painting) { vm.cancelStroke(); painting = false }
                                            mode = 2
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            if (zoomChange.isFinite() && zoomChange > 0f) {
                                                zoom = (zoom * zoomChange).coerceIn(zoomMinNow(), zoomMax)
                                            }
                                            if (panChange.x.isFinite() && panChange.y.isFinite()) {
                                                offset = clampOffset(offset + panChange)
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            last = pressed[0].position
                                            lastPointerId = pressed[0].id
                                            continue
                                        }

                                        val change = pressed[0]

                                        if (mode == 2) {
                                            // 双指抬一根：剩余单指继续平移（换指时只重置基准不跳变）
                                            if (change.id != lastPointerId) {
                                                lastPointerId = change.id
                                            } else {
                                                offset = clampOffset(offset + (change.position - last))
                                            }
                                            last = change.position
                                            if (change.positionChanged()) change.consume()
                                            continue
                                        }

                                        if (!pastSlop) {
                                            val moved = (change.position - down.position).getDistance()
                                            if (!longFired && moved < slopPx &&
                                                change.uptimeMillis - down.uptimeMillis > longPressTimeout
                                            ) {
                                                // 长按：洪水擦除（任意工具）
                                                longFired = true
                                                downCell?.let { vm.floodErase(it.first, it.second) }
                                                change.consume()
                                                continue
                                            }
                                            if (moved >= slopPx && !longFired) {
                                                pastSlop = true
                                                if (isCircle &&
                                                    abs((down.position - Offset(frameCx, frameCy)).getDistance() - frameR) <= resizeTolPx
                                                ) {
                                                    // 按在圆环附近：拖拽调整圆框大小（图案不动）
                                                    mode = 4
                                                } else if (tool == EditorTool.BRUSH || tool == EditorTool.ERASER) {
                                                    mode = 1
                                                    vm.beginStroke()
                                                    painting = true
                                                    forEachCellBetween(null, downCell) { r, c -> applyPaint(vm, tool, r, c) }
                                                    lastCell = downCell
                                                    val cur = cellAt(change.position)
                                                    if (cur != null && cur != lastCell) {
                                                        forEachCellBetween(lastCell, cur) { r, c -> applyPaint(vm, tool, r, c) }
                                                        lastCell = cur
                                                    }
                                                } else {
                                                    // 洪水/替换工具下单指拖动 = 平移画布
                                                    mode = 2
                                                    last = change.position
                                                }
                                            }
                                        } else if (mode == 4) {
                                            // 拖拽圆框：半径跟随手指到圆心的距离
                                            frameR = (change.position - Offset(frameCx, frameCy))
                                                .getDistance()
                                                .coerceIn(0.2f * fitR, fitR)
                                            change.consume()
                                        } else if (painting) {
                                            // 拖动连涂：补上两事件之间跳过的格子
                                            val cur = cellAt(change.position)
                                            if (cur != null && cur != lastCell) {
                                                forEachCellBetween(lastCell, cur) { r, c -> applyPaint(vm, tool, r, c) }
                                                lastCell = cur
                                            }
                                        }
                                        if (change.positionChanged()) change.consume()
                                    }

                                    // 手势结束
                                    if (painting) {
                                        vm.endStroke()
                                    } else if (mode == 2 || mode == 4) {
                                        // 缩放/平移/调框结束：把最终圆框写回（统计与导出随之更新）
                                        pushCircleFrame()
                                    } else if (!pastSlop && !longFired && mode != 2) {
                                        // 单击
                                        downCell?.let { (row, col) ->
                                            when (tool) {
                                                EditorTool.BRUSH -> { vm.beginStroke(); vm.strokePaint(row, col); vm.endStroke() }
                                                EditorTool.ERASER -> { vm.beginStroke(); vm.strokeErase(row, col); vm.endStroke() }
                                                EditorTool.FLOOD -> vm.floodErase(row, col)
                                                EditorTool.REPLACE -> pickReplaceSource(vm, grid, row, col)
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // 读取 gridVersion 建立绘制依赖：格子数据变化时重绘
                        @Suppress("UNUSED_VARIABLE")
                        val version = vm.gridVersion
                        val cells = grid.cells

                        val cell = baseCell * zoom
                        val o = patternOrigin(cell)
                        val ox = o.x
                        val oy = o.y

                        // 只绘制可视范围内的格子
                        val colStart = maxOf(0, floor(-ox / cell).toInt())
                        val colEnd = minOf(grid.n - 1, ceil((size.width - ox) / cell).toInt())
                        val rowStart = maxOf(0, floor(-oy / cell).toInt())
                        val rowEnd = minOf(grid.m - 1, ceil((size.height - oy) / cell).toInt())

                        if (colStart <= colEnd && rowStart <= rowEnd) {
                            for (r in rowStart..rowEnd) {
                                for (c in colStart..colEnd) {
                                    val px = cells[r][c]
                                    val color = if (px.isExternal) {
                                        EXTERNAL_CELL_COLOR
                                    } else {
                                        colorCache.getOrPut(px.colorHex) { Color(GridRenderer.parseHex(px.colorHex)) }
                                    }
                                    // +1px 重叠避免高缩放下的抗锯齿缝隙
                                    drawRect(
                                        color,
                                        topLeft = Offset(ox + c * cell, oy + r * cell),
                                        size = Size(cell + 1f, cell + 1f)
                                    )
                                }
                            }
                            // 网格线：格子够大才画，屏幕空间恒定 1px —— 放大不会出现粗白线
                            if (cell >= 8f) {
                                val left = ox + colStart * cell
                                val right = ox + (colEnd + 1) * cell
                                val top = oy + rowStart * cell
                                val bottom = oy + (rowEnd + 1) * cell
                                for (c in colStart..colEnd + 1) {
                                    val x = ox + c * cell
                                    drawLine(GRID_LINE_COLOR, Offset(x, top), Offset(x, bottom), 1f)
                                }
                                for (r in rowStart..rowEnd + 1) {
                                    val y = oy + r * cell
                                    drawLine(GRID_LINE_COLOR, Offset(left, y), Offset(right, y), 1f)
                                }
                            }
                        }

                        // 圆形遮罩层（固定层）：圆框外压暗 + 白色圆环，屏幕坐标固定，
                        // 图案在框后缩放平移 —— 框即圆板，框内 = 最终产品；拖动圆环边缘可调整大小
                        if (isCircle) {
                            val dim = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addOval(
                                    Rect(
                                        frameCx - frameR, frameCy - frameR,
                                        frameCx + frameR, frameCy + frameR
                                    )
                                )
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(dim, Color.Black.copy(alpha = 0.62f))
                            drawCircle(
                                Color.White,
                                radius = frameR,
                                center = Offset(frameCx, frameCy),
                                style = Stroke(2.5f)
                            )
                            // 四个方位的拖拽提示点，暗示圆环可抓取
                            val dotR = 3f
                            listOf(
                                Offset(frameCx, frameCy - frameR),
                                Offset(frameCx, frameCy + frameR),
                                Offset(frameCx - frameR, frameCy),
                                Offset(frameCx + frameR, frameCy)
                            ).forEach { p ->
                                drawCircle(Color.White, radius = dotR, center = p)
                            }
                        }
                    }
                }
            }

            // ---------- 工具栏 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToolButton(
                    selected = tool == EditorTool.BRUSH,
                    icon = Icons.Filled.Brush, label = "画笔",
                    onClick = { tool = EditorTool.BRUSH }
                )
                ToolButton(
                    selected = tool == EditorTool.ERASER,
                    icon = Icons.AutoMirrored.Filled.Backspace, label = "橡皮",
                    onClick = { tool = EditorTool.ERASER }
                )
                ToolButton(
                    selected = tool == EditorTool.FLOOD,
                    icon = Icons.Filled.Highlight, label = "擦除",
                    onClick = { tool = EditorTool.FLOOD }
                )
                ToolButton(
                    selected = tool == EditorTool.REPLACE,
                    icon = Icons.Filled.SwapHoriz, label = "替换",
                    onClick = { tool = EditorTool.REPLACE }
                )
                TextButton(onClick = { vm.autoRemoveBackground() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("去背景")
                }
                // 撤回
                TextButton(
                    onClick = { vm.undo() },
                    enabled = vm.canUndo
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("撤回")
                }
                // 重做
                TextButton(
                    onClick = { vm.redo() },
                    enabled = vm.canRedo
                ) {
                    Icon(Icons.Filled.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("重做")
                }
                TextButton(
                    onClick = {
                        if (vm.settings.aiServiceUrl.isBlank()) {
                            showAiConfig = true
                        } else {
                            vm.aiOptimize()
                        }
                    },
                    enabled = !vm.aiProcessing
                ) {
                    if (vm.aiProcessing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.aiProcessing) "AI 处理中" else "AI 优化")
                }
            }

            // ---------- 当前画笔信息 ----------
            val sel = vm.selectedPaintColor
            if (sel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSwatch(sel, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (tool == EditorTool.REPLACE) "当前画笔（源色）→ 点下方色板选择替换目标"
                        else "当前画笔：${sel.key}  ${sel.hex}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text("共 ${vm.totalBeadCount} 粒", style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------- 色板行 ----------
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                items(vm.gridPalette, key = { it.hex }) { pc ->
                    val selected = vm.selectedPaintColor?.hex == pc.hex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (tool == EditorTool.REPLACE) {
                                vm.replaceAll(pc)
                            } else {
                                vm.setSelectedPaint(pc)
                                tool = EditorTool.BRUSH
                            }
                        }
                    ) {
                        ColorSwatch(pc, 36.dp, selected = selected)
                        Text(
                            pc.key,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- 统计面板 ----------
            if (showStats) {
                StatsPanel(vm)
            }
        }

        // ---------- Toast ----------
        vm.toast?.let { msg ->
            LaunchedEffect(vm.toast) {
                kotlinx.coroutines.delay(3000)
                vm.consumeToast()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(msg)
            }
        }
    }

    // ---------- 导出对话框 ----------
    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onPattern = { hideWhite, mirror ->
                showExport = false
                val bmp = runCatching {
                    // 圆形画板按圆框直径限制导出尺寸，保证大圆框不超内存上限
                    val gridCell = 4096 / maxOf(grid.n, grid.m)
                    val circleCell = vm.circleFrame?.let { (4096f / (2f * it.radius)).toInt() } ?: Int.MAX_VALUE
                    val cell = maxOf(4, minOf(48, minOf(gridCell, circleCell)))
                    GridRenderer.render(
                        grid, cell, showBorders = true, showKeys = true,
                        hideWhiteKeys = hideWhite, mirror = mirror,
                        circle = vm.circleFrame
                    )
                }.getOrNull()
                if (bmp != null) {
                    val uri = Exporter.savePngToPictures(context, bmp, "拼豆图纸_${grid.n}x${grid.m}.png")
                    share(context, uri, "image/png")
                }
            },
            onPdf = { hideWhite, mirror ->
                showExport = false
                val bytes = runCatching {
                    PdfExporter.buildPatternPdf(
                        grid,
                        circle = vm.circleFrame,
                        stats = statsRows(vm),
                        totalCount = vm.totalBeadCount
                    )
                }.getOrNull()
                if (bytes != null) {
                    val uri = Exporter.savePdfToDownloads(context, bytes, "拼豆图纸_${grid.n}x${grid.m}.pdf")
                    share(context, uri, "application/pdf")
                }
            },
            onStats = {
                showExport = false
                val bmp = Exporter.renderStatsBitmap(statsRows(vm), vm.totalBeadCount)
                val uri = Exporter.savePngToPictures(context, bmp, "拼豆颜色统计.png")
                share(context, uri, "image/png")
            },
            onList = {
                showExport = false
                val csv = Exporter.buildShoppingListCsv(statsRows(vm), vm.totalBeadCount)
                val uri = Exporter.saveCsvToDownloads(context, csv, "拼豆采购清单.csv")
                share(context, uri, "text/csv")
            }
        )
    }

    // ---------- 保存项目对话框 ----------
    if (showSaveDialog) {
        var saveName by remember { mutableStateOf("拼豆_${grid.n}x${grid.m}") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存为项目") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveCurrentProject(saveName)
                    showSaveDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }

    // ---------- AI 配置对话框（未配置时内联弹出） ----------
    if (showAiConfig) {
        AiConfigDialog(
            onDismiss = { showAiConfig = false },
            onConfirm = { url, reqKey ->
                vm.settings.aiServiceUrl = url
                vm.settings.aiReqKey = reqKey
                showAiConfig = false
                vm.aiOptimize()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, reqKey: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var reqKey by remember { mutableStateOf("image2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 AI 服务") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("AI 服务地址") },
                    placeholder = { Text("https://example.com/api/ai-optimize") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("AI 模型", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("即梦AI" to "jimeng_t2i_v40", "image2" to "image2").forEach { (label, value) ->
                        FilterChip(
                            selected = reqKey == value,
                            onClick = { reqKey = value },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim(), reqKey) }) {
                Text("保存并优化")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** external/已擦除格子的显示色（与 GridRenderer.EXTERNAL_COLOR 一致）。 */
private val EXTERNAL_CELL_COLOR = Color(0xFFDCDCDC)
private val GRID_LINE_COLOR = Color(0x33FFFFFF)

private fun applyPaint(vm: AppViewModel, tool: EditorTool, row: Int, col: Int) {
    when (tool) {
        EditorTool.BRUSH -> vm.strokePaint(row, col)
        EditorTool.ERASER -> vm.strokeErase(row, col)
        else -> {}
    }
}

private fun pickReplaceSource(
    vm: AppViewModel,
    grid: com.perlerbeads.generator.model.GridData,
    row: Int,
    col: Int
) {
    val cell = grid.cells.getOrNull(row)?.getOrNull(col) ?: return
    if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
        vm.activePalette.firstOrNull { it.hex.uppercase() == cell.colorHex.uppercase() }
            ?.let { vm.setSelectedPaint(it) }
    }
}

/** 涂色路径插值：把 from→to 之间跳过的格子逐个涂上，快速滑动不留断点。 */
private fun forEachCellBetween(
    from: Pair<Int, Int>?,
    to: Pair<Int, Int>?,
    action: (Int, Int) -> Unit
) {
    if (to == null) return
    if (from == null) {
        action(to.first, to.second)
        return
    }
    val (r0, c0) = from
    val (r1, c1) = to
    val steps = maxOf(abs(r1 - r0), abs(c1 - c0))
    if (steps == 0) {
        action(r1, c1)
        return
    }
    for (i in 1..steps) {
        action(r0 + (r1 - r0) * i / steps, c0 + (c1 - c0) * i / steps)
    }
}

/**
 * 平移钳制：网格中心对齐容器中心时，允许的最大偏移为 |容器-网格|/2，
 * 即网格不会整体滑出容器。
 */
private fun clampEditorOffset(off: Offset, cw: Float, ch: Float, gw: Float, gh: Float): Offset {
    val maxX = abs(cw - gw) / 2f
    val maxY = abs(ch - gh) / 2f
    return Offset(off.x.coerceIn(-maxX, maxX), off.y.coerceIn(-maxY, maxY))
}

private fun statsRows(vm: AppViewModel): List<ColorStatRow> {
    val counts = vm.stats?.counts ?: return emptyList()
    return vm.gridPalette.mapNotNull { pc ->
        counts[pc.hex.uppercase()]?.let { ColorStatRow(pc.key, pc.hex, it) }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorSwatch(pc: PaletteColor, size: Dp, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (selected) 10.dp else 6.dp))
            .background(Color(GridRenderer.parseHex(pc.hex)))
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(2.dp)
            .background(Color(GridRenderer.parseHex(pc.hex)), RoundedCornerShape(if (selected) 8.dp else 4.dp))
    )
}

@Composable
private fun StatsPanel(vm: AppViewModel) {
    val counts = vm.stats?.counts ?: return
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text("颜色统计（共 ${vm.totalBeadCount} 粒）", style = MaterialTheme.typography.titleMedium)
            vm.gridPalette.forEach { pc ->
                val count = counts[pc.hex.uppercase()] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleExclude(pc.hex) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSwatch(pc, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${pc.key}  ${pc.hex}", modifier = Modifier.weight(1f))
                    Text("×${count}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (pc.hex.uppercase() in vm.excludedHexes) "恢复" else "排除",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "排除后自动重映射到邻近颜色；点击“恢复”重新生成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onPattern: (hideWhite: Boolean, mirror: Boolean) -> Unit,
    onPdf: (hideWhite: Boolean, mirror: Boolean) -> Unit,
    onStats: () -> Unit,
    onList: () -> Unit
) {
    var hideWhite by remember { mutableStateOf(true) }
    var mirror by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出") },
        text = {
            Column {
                FilterChip(
                    selected = false,
                    onClick = { onPattern(hideWhite, mirror) },
                    label = { Text("带 Key 图纸 PNG") }
                )
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = false,
                    onClick = { onPdf(hideWhite, mirror) },
                    label = { Text("图纸 PDF（1:1 打印）") }
                )
                Text(
                    "勾选下方选项后点击上面按钮生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FilterChip(selected = false, onClick = onStats, label = { Text("颜色统计图 PNG") })
                Spacer(Modifier.height(8.dp))
                FilterChip(selected = false, onClick = onList, label = { Text("采购清单 CSV") })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideWhite, onCheckedChange = { hideWhite = it })
                    Text("隐藏白色格子色号", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mirror, onCheckedChange = { mirror = it })
                    Text("水平镜像图纸（色号文字不镜像）", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun share(context: android.content.Context, uri: android.net.Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}
