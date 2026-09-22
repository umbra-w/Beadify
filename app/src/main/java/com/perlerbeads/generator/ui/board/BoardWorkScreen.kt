package com.perlerbeads.generator.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.algorithm.BoardSlice
import com.perlerbeads.generator.algorithm.sliceBoards
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.transparentColorData
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

/**
 * 分板跟做：把图纸按实体板尺寸切片，逐板跟做。
 * 交互参考成熟拼豆工具的通用流程（板总览 → 当前板大图 → 标记完成 → 自动跳下一未完成板）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BoardWorkScreen(vm: AppViewModel) {
    val grid = vm.gridData ?: return
    val scope = vm.scopeFilter()
    val slices = remember(grid, vm.boardSize) { sliceBoards(grid, vm.boardSize, scope) }
    val slice = slices.getOrNull(vm.currentBoard)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("分板跟做  ${vm.completedBoards.size}/${slices.size}") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(com.perlerbeads.generator.navigation.Screen.Editor) }) {
                    Text("返回")
                }
            }
        )

        // ---------- 板尺寸 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("板尺寸", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16, 29, 50).forEach { size ->
                    FilterChip(
                        selected = vm.boardSize == size,
                        onClick = { vm.changeBoardSize(size) },
                        label = { Text("${size}×$size") }
                    )
                }
            }
        }

        // ---------- 板总览（大图纸板数多时限制高度并可滚动） ----------
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .heightIn(max = 92.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { s ->
                val done = s.index in vm.completedBoards
                val current = s.index == vm.currentBoard
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                            modifier = Modifier.size(20.dp)
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

        // ---------- 当前板大图 ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(Color(0xFF2A2A2E), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (slice != null) {
                val bitmap = remember(grid, slice) { boardBitmap(grid, slice, scope) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "第 ${slice.index + 1} 块板",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }

        // ---------- 当前板信息 ----------
        if (slice != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "第 ${slice.index + 1} 块（第 ${slice.boardRow + 1} 行 第 ${slice.boardCol + 1} 列）· " +
                        "${slice.cols}×${slice.rows} 格 · 共 ${slice.total} 粒",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    summarizeBeads(slice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        // ---------- 操作按钮 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                    Text(if (done) "取消完成" else "本板完成")
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
                Text("下一未完成")
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

/** 渲染一块板的位图（带色号与边框）。圆框外的格子显示为透明。 */
private fun boardBitmap(
    grid: GridData,
    slice: BoardSlice,
    scope: ((row: Int, col: Int) -> Boolean)?
): android.graphics.Bitmap {
    val cells = Array(slice.rows) { r ->
        Array(slice.cols) { c ->
            val gr = slice.rowStart + r
            val gc = slice.colStart + c
            val cell = grid.cells[gr][gc]
            if (scope != null && !scope(gr, gc)) transparentColorData else cell
        }
    }
    return GridRenderer.render(
        GridData(slice.cols, slice.rows, cells, emptySet(), GridShape.SQUARE),
        cellSize = 24,
        showBorders = true,
        showKeys = true
    )
}

/** 用豆摘要：按用量降序，最多显示 8 种，其余以 "+N 种" 收尾。 */
private fun summarizeBeads(slice: BoardSlice): String {
    if (slice.beads.isEmpty()) return "本板无需拼豆"
    val shown = slice.beads.take(8).joinToString("  ") { "${it.key}×${it.count}" }
    val rest = slice.beads.size - 8
    return if (rest > 0) "$shown  +$rest 种" else shown
}
