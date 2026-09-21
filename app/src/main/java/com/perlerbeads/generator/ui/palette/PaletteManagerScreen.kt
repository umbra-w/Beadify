package com.perlerbeads.generator.ui.palette

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaletteManagerScreen(vm: AppViewModel) {
    val saved = remember { vm.settings.loadPaletteSelections() }
    val fullPalette = vm.paletteRepository.fullBeadPalette
    val cs = vm.settings.colorSystem
    val displayPalette = remember(fullPalette, cs) {
        vm.paletteRepository.convertPaletteToColorSystem(fullPalette, cs)
    }

    // 勾选状态：未保存过则默认全选
    val selections = remember {
        mutableStateMapOf<String, Boolean>().apply {
            fullPalette.forEach { pc ->
                put(pc.hex.uppercase(), saved?.get(pc.hex.uppercase()) ?: true)
            }
        }
    }
    val selectedCount = selections.count { it.value }

    // 搜索：按色号或 hex 过滤（291 色中快速定位）
    var query by remember { mutableStateOf("") }

    // 按前缀字母分组
    val filtered = if (query.isBlank()) displayPalette
    else displayPalette.filter {
        it.key.contains(query, ignoreCase = true) || it.hex.contains(query, ignoreCase = true)
    }
    val groups = filtered.groupBy { it.key.take(1).uppercase() }.toSortedMap()

    // 每组折叠状态：默认全部展开
    val collapsed = remember { mutableStateMapOf<String, Boolean>().apply {
        groups.keys.forEach { put(it, false) }
    } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("色板管理") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text("返回") }
            },
            actions = {
                TextButton(onClick = {
                    vm.settings.savePaletteSelections(selections.toMap())
                    vm.refreshActivePalette()
                    vm.navigate(Screen.Settings)
                }) { Text("保存并应用") }
            }
        )

        // 色号系统切换
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorSystem.entries.forEach { item ->
                FilterChip(
                    selected = cs == item,
                    onClick = { vm.setColorSystem(item) },
                    label = { Text(item.key) }
                )
            }
        }

        Text(
            "已选 ${selectedCount} / ${fullPalette.size} 色",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索色号或 hex（如 A01 / FF0000）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            groups.forEach { (prefix, colors) ->
                // 分组标题行：可点击折叠 + 全选/全不选
                item(key = "header_$prefix") {
                    val isCollapsed = collapsed[prefix] ?: false
                    val allInGroup = colors.map { it.hex.uppercase() }
                    val selectedInGroup = allInGroup.count { selections[it] == true }
                    val allSelected = selectedInGroup == allInGroup.size

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { collapsed[prefix] = !isCollapsed }
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 折叠箭头
                            Text(
                                if (isCollapsed) "▶" else "▼",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                "$prefix 区（$selectedInGroup/${allInGroup.size}）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            // 全选/全不选
                            TextButton(onClick = {
                                val newVal = !allSelected
                                allInGroup.forEach { selections[it] = newVal }
                            }) {
                                Text(if (allSelected) "全不选" else "全选")
                            }
                        }
                    }
                }

                // 颜色列表（可折叠）
                item(key = "body_$prefix") {
                    AnimatedVisibility(visible = collapsed[prefix] != true) {
                        Column {
                            colors.forEach { pc ->
                                val hex = pc.hex.uppercase()
                                val checked = selections[hex] ?: true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selections[hex] = !checked }
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(GridRenderer.parseHex(pc.hex)))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pc.key, style = MaterialTheme.typography.bodyLarge)
                                        Text(pc.hex, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { selections[hex] = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}