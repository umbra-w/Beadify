package com.perlerbeads.generator.ui.editor

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.export.ColorStatRow
import com.perlerbeads.generator.export.Exporter
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import kotlin.math.min

enum class EditorTool { BRUSH, ERASER, FLOOD, REPLACE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val grid = vm.gridData ?: return

    val preview = remember(grid, vm.gridVersion) {
        GridRenderer.renderCapped(grid, maxDim = 2048, showBorders = true)
    }
    val cellSizeP = preview.width / grid.n

    var tool by remember { mutableStateOf(EditorTool.BRUSH) }
    var showStats by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showAiConfig by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("图纸 ${grid.n}×${grid.m}") },
                navigationIcon = {
                    TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text("返回") }
                },
                actions = {
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "统计")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Filled.Download, contentDescription = "导出")
                    }
                }
            )

            // ---------- 画布 ----------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A2E))
                    .onSizeChanged { containerSize = it }
            ) {
                if (containerSize != IntSize.Zero && preview.width > 0) {
                    val s0 = min(
                        containerSize.width.toFloat() / preview.width,
                        containerSize.height.toFloat() / preview.height
                    )
                    val baseW = preview.width * s0
                    val baseH = preview.height * s0

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(0.5f, 10f)
                        offset += panChange
                    }

                    // 手势处理 + 位图绘制
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(grid, tool, containerSize) {
                                detectTapGestures(
                                    onTap = { pos -> handleTap(vm, grid, tool, pos, offset, zoom, s0, cellSizeP, baseW, baseH, false) },
                                    onLongPress = { pos -> handleTap(vm, grid, tool, pos, offset, zoom, s0, cellSizeP, baseW, baseH, true) }
                                )
                            }
                            .transformable(transformState)
                    ) {
                        val density = LocalDensity.current
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .size(
                                    width = with(density) { baseW.toDp() },
                                    height = with(density) { baseH.toDp() }
                                )
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                }
                        )
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
            onPattern = {
                showExport = false
                val bmp = runCatching {
                    val cell = maxOf(4, minOf(48, 4096 / maxOf(grid.n, grid.m)))
                    GridRenderer.render(grid, cell, showBorders = true, showKeys = true)
                }.getOrNull()
                if (bmp != null) {
                    val uri = Exporter.savePngToPictures(context, bmp, "拼豆图纸_${grid.n}x${grid.m}.png")
                    share(context, uri, "image/png")
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

private fun handleTap(
    vm: AppViewModel,
    grid: com.perlerbeads.generator.model.GridData,
    tool: EditorTool,
    pos: Offset,
    offset: Offset,
    zoom: Float,
    s0: Float,
    cellSizeP: Int,
    baseW: Float,
    baseH: Float,
    longPress: Boolean
) {
    val centerX = baseW / 2f
    val centerY = baseH / 2f
    val localX = centerX + (pos.x - offset.x - centerX) / zoom
    val localY = centerY + (pos.y - offset.y - centerY) / zoom
    val col = (localX / s0 / cellSizeP).toInt()
    val row = (localY / s0 / cellSizeP).toInt()
    if (col in 0 until grid.n && row in 0 until grid.m) {
        when {
            longPress -> vm.floodErase(row, col)
            tool == EditorTool.BRUSH -> vm.paintCell(row, col, vm.selectedPaintColor)
            tool == EditorTool.ERASER -> vm.eraseCell(row, col)
            tool == EditorTool.FLOOD -> vm.floodErase(row, col)
            tool == EditorTool.REPLACE -> {
                val cell = grid.cells[row][col]
                if (!cell.isExternal && cell.key != TRANSPARENT_KEY) {
                    vm.activePalette.firstOrNull { it.hex.uppercase() == cell.colorHex.uppercase() }
                        ?.let { vm.setSelectedPaint(it) }
                }
            }
        }
    }
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
    onPattern: () -> Unit,
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
                FilterChip(selected = false, onClick = onPattern, label = { Text("带 Key 图纸 PNG") })
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
                    Text("水平镜像图纸", style = MaterialTheme.typography.bodySmall)
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
