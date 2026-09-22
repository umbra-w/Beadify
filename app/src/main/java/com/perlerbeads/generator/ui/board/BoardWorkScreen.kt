package com.perlerbeads.generator.ui.board

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perlerbeads.generator.algorithm.BoardSlice
import com.perlerbeads.generator.algorithm.colorCompletedCount
import com.perlerbeads.generator.algorithm.sliceBoards
import com.perlerbeads.generator.algorithm.sliceCompletedCount
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

/**
 * 分板跟做：把图纸按实体板尺寸切片，逐板跟做。
 *
 * 核心升级：
 * 1. 屏幕常亮：进入页面自动保持屏幕开启，避免点珠过程中手机息屏。
 * 2. 标尺坐标：顶部与左侧显示行列标号（1, 5, 10...），每 5 格加粗辅助线。
 * 3. 逐格打勾：点击网格格子即可标记已完成/取消打勾，实时统计已拼进度。
 * 4. 按色聚焦（Color Spotlight）：点击单选色号，非聚焦色号变暗，聚焦色号高亮金边。
 * 5. 颜色批量标记：点击色卡勾号一键标记/取消当前板该色号全部珠子。
 * 6. 双指缩放 + 拖拽平移 + 双击放大 + 悬浮缩放按钮，大板小板均可清晰对准。
 * 7. 规格新增 28×28 国内主流大方板规格。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BoardWorkScreen(vm: AppViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val grid = vm.gridData ?: return
    val scope = vm.scopeFilter()
    val slices = remember(grid, vm.boardSize) { sliceBoards(grid, vm.boardSize, scope) }
    val slice = slices.getOrNull(vm.currentBoard)

    val totalBeads = remember(slices) { slices.sumOf { it.total } }
    val totalDone = remember(slices, vm.completedCells) {
        slices.sumOf { sliceCompletedCount(grid, it, vm.completedCells, scope) }
    }
    val totalPercent = if (totalBeads > 0) totalDone * 100 / totalBeads else 100

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("分板跟做", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已拼 $totalDone / $totalBeads 颗 ($totalPercent%) · 完成板 ${vm.completedBoards.size}/${slices.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(Screen.Editor) }) {
                    Text("返回")
                }
            }
        )

        // ---------- 板尺寸选择 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("板规格", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16, 28, 29, 50).forEach { size ->
                    FilterChip(
                        selected = vm.boardSize == size,
                        onClick = { vm.changeBoardSize(size) },
                        label = { Text("${size}×$size") }
                    )
                }
            }
        }

        // ---------- 板总览导航 ----------
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .heightIn(max = 84.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { s ->
                val done = s.index in vm.completedBoards
                val current = s.index == vm.currentBoard
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            when {
                                done -> Color(0xFF2E7D32)
                                current -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .then(
                            if (current) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            } else Modifier
                        )
                        .clickable { vm.selectBoard(s.index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已完成",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            "${s.index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------- 色号聚焦与统计 Chips (Horizontal Scroll) ----------
        if (slice != null && slice.beads.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全部颜色 chip
                val isAllActive = vm.spotlightKey == null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAllActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { vm.setSpotlight(null) }
                ) {
                    Text(
                        "全景",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 各种颜色的 Chip
                slice.beads.forEach { bead ->
                    val doneCount = colorCompletedCount(grid, slice, bead.key, vm.completedCells, scope)
                    val isDone = doneCount >= bead.count && bead.count > 0
                    val isSpotlight = vm.spotlightKey == bead.key
                    val hexColor = GridRenderer.parseHex(bead.hex)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isSpotlight -> Color(0xFFFFF9C4) // 淡黄高亮背景
                            isDone -> Color(0xFFE8F5E9)      // 淡绿完成背景
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = when {
                            isSpotlight -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB300))
                            isDone -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                            else -> androidx.compose.foundation.BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                        },
                        modifier = Modifier.clickable { vm.setSpotlight(bead.key) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            // 颜色圆点
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(hexColor))
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${bead.key} $doneCount/${bead.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSpotlight) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(2.dp))
                            // 勾选按键
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { vm.toggleColorDoneOnBoard(slice, bead.key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "标记该色号完成",
                                    tint = if (isDone) Color(0xFF2E7D32) else Color.Gray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 当前板大图画布（支持手势交互） ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color(0xFF1E1E22), RoundedCornerShape(10.dp))
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (slice != null) {
                var zoom by remember(slice.index) { mutableFloatStateOf(1f) }
                var offset by remember(slice.index) { mutableStateOf(Offset.Zero) }
                var containerSize by remember { mutableStateOf(IntSize.Zero) }

                val bitmap = remember(grid, slice, vm.completedCells, vm.spotlightKey) {
                    BoardSliceRenderer.render(grid, slice, vm.completedCells, vm.spotlightKey, scope)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(slice.index, containerSize, bitmap) {
                            val slopPx = viewConfiguration.touchSlop
                            var lastTapTime = 0L
                            var lastTapPos = Offset.Zero

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var isTransform = false
                                var pastSlop = false
                                var last = down.position
                                var lastPointerId = down.id

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        if (!isTransform && !pastSlop) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < 320L && (down.position - lastTapPos).getDistance() < 50f) {
                                                // 双击缩放切换
                                                zoom = if (zoom > 1.2f) 1f else 2.5f
                                                offset = Offset.Zero
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = now
                                                lastTapPos = down.position
                                                // 单击打勾/取消打勾
                                                val tapped = BoardSliceRenderer.tapToCell(
                                                    tapX = down.position.x,
                                                    tapY = down.position.y,
                                                    containerW = containerSize.width.toFloat(),
                                                    containerH = containerSize.height.toFloat(),
                                                    bmpW = bitmap.width.toFloat(),
                                                    bmpH = bitmap.height.toFloat(),
                                                    zoom = zoom,
                                                    offsetX = offset.x,
                                                    offsetY = offset.y,
                                                    cols = slice.cols,
                                                    rows = slice.rows
                                                )
                                                if (tapped != null) {
                                                    val (r, c) = tapped
                                                    val gr = slice.rowStart + r
                                                    val gc = slice.colStart + c
                                                    vm.toggleCellDone(gr, gc, slice)
                                                }
                                            }
                                        }
                                        break
                                    }

                                    if (pressed.size >= 2) {
                                        isTransform = true
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange.isFinite() && zoomChange > 0f) {
                                            zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                        }
                                        if (panChange.x.isFinite() && panChange.y.isFinite() && zoom > 1.05f) {
                                            offset = clampOffset(
                                                offset + panChange, zoom,
                                                containerSize.width.toFloat(), containerSize.height.toFloat(),
                                                bitmap.width.toFloat(), bitmap.height.toFloat()
                                            )
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        last = pressed[0].position
                                        lastPointerId = pressed[0].id
                                        continue
                                    }

                                    val change = pressed[0]
                                    val moved = (change.position - down.position).getDistance()
                                    if (moved > slopPx) {
                                        pastSlop = true
                                    }

                                    if (pastSlop && zoom > 1.05f) {
                                        val delta = change.position - last
                                        offset = clampOffset(
                                            offset + delta, zoom,
                                            containerSize.width.toFloat(), containerSize.height.toFloat(),
                                            bitmap.width.toFloat(), bitmap.height.toFloat()
                                        )
                                        last = change.position
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "第 ${slice.index + 1} 块板",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }

                // 聚焦模式提示徽标（点击关闭）
                if (vm.spotlightKey != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clickable { vm.setSpotlight(null) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD000000)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "聚焦高亮: ${vm.spotlightKey} · 点此取消",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD700)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "取消聚焦",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 悬浮缩放与重置控制小面板
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xBB1E1E22),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                        IconButton(
                            onClick = {
                                zoom = (zoom - 0.5f).coerceIn(1f, 5f)
                                offset = clampOffset(
                                    offset, zoom,
                                    containerSize.width.toFloat(), containerSize.height.toFloat(),
                                    bitmap.width.toFloat(), bitmap.height.toFloat()
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "缩小", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${(zoom * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clickable {
                                    zoom = 1f
                                    offset = Offset.Zero
                                }
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = {
                                zoom = (zoom + 0.5f).coerceIn(1f, 5f)
                                offset = clampOffset(
                                    offset, zoom,
                                    containerSize.width.toFloat(), containerSize.height.toFloat(),
                                    bitmap.width.toFloat(), bitmap.height.toFloat()
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "放大", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // ---------- 当前板状态统计 ----------
        if (slice != null) {
            val sliceDone = sliceCompletedCount(grid, slice, vm.completedCells, scope)
            val slicePercent = if (slice.total > 0) sliceDone * 100 / slice.total else 100

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "第 ${slice.index + 1} 块（第 ${slice.boardRow + 1} 行 第 ${slice.boardCol + 1} 列）· ${slice.cols}×${slice.rows} 格",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "已拼 $sliceDone / ${slice.total} 粒 ($slicePercent%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sliceDone >= slice.total && slice.total > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                )
            }
        }

        // ---------- 底部操作栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (vm.currentBoard > 0) vm.selectBoard(vm.currentBoard - 1)
                },
                enabled = vm.currentBoard > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("上一板")
            }

            if (slice != null) {
                val done = slice.index in vm.completedBoards
                TextButton(onClick = { vm.toggleBoardDone(slice.index) }) {
                    Icon(
                        if (done) Icons.Filled.Flag else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(if (done) "取消完成" else "本板全勾")
                }

                IconButton(
                    onClick = { vm.resetSliceProgress(slice) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "清空本板进度",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            TextButton(
                onClick = {
                    val next = slices.firstOrNull { it.index !in vm.completedBoards && it.index != vm.currentBoard }
                    if (next != null) vm.selectBoard(next.index)
                },
                enabled = slices.any { it.index !in vm.completedBoards }
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("下一未完")
            }

            TextButton(
                onClick = {
                    if (vm.currentBoard < slices.lastIndex) vm.selectBoard(vm.currentBoard + 1)
                },
                enabled = vm.currentBoard < slices.lastIndex
            ) {
                Text("下一板")
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 限制缩放后平移边界，防止画布移出屏幕视区。 */
private fun clampOffset(
    offset: Offset,
    zoom: Float,
    containerW: Float,
    containerH: Float,
    bmpW: Float,
    bmpH: Float
): Offset {
    if (containerW <= 0f || containerH <= 0f || bmpW <= 0f || bmpH <= 0f) return Offset.Zero
    val scaleFit = minOf(containerW / bmpW, containerH / bmpH)
    val renderW = bmpW * scaleFit * zoom
    val renderH = bmpH * scaleFit * zoom

    val maxOffsetX = maxOf(0f, (renderW - containerW) / 2f)
    val maxOffsetY = maxOf(0f, (renderH - containerH) / 2f)

    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

/** ContextWrapper 遍历查找宿主 Activity，以安全操作 WindowManager。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
