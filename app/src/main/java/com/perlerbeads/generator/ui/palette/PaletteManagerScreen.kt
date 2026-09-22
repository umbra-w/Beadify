package com.perlerbeads.generator.ui.palette

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.perlerbeads.generator.model.BeadBrand
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaletteManagerScreen(vm: AppViewModel) {
    var selectedBrand by remember { mutableStateOf(vm.currentBrand) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: 活动色板, 1: 我的豆仓库存

    val fullPalette = remember(selectedBrand) {
        vm.paletteRepository.getPaletteForBrand(selectedBrand)
    }

    val cs = vm.settings.colorSystem
    val displayPalette = remember(fullPalette, cs, selectedBrand) {
        vm.paletteRepository.convertPaletteToColorSystem(fullPalette, cs)
    }

    // 活动色板勾选
    val paletteSelections = remember(selectedBrand) {
        val saved = vm.settings.loadPaletteSelections(selectedBrand)
        mutableStateMapOf<String, Boolean>().apply {
            fullPalette.forEach { pc ->
                put(pc.hex.uppercase(), saved?.get(pc.hex.uppercase()) ?: true)
            }
        }
    }

    // 豆仓库存状态
    val stockSelections = remember(selectedBrand) {
        val inStockSet = vm.inventoryStore.getInStockHexes(selectedBrand, fullPalette)
        mutableStateMapOf<String, Boolean>().apply {
            fullPalette.forEach { pc ->
                put(pc.hex.uppercase(), inStockSet.contains(pc.hex.uppercase()))
            }
        }
    }

    // 搜索
    var query by remember { mutableStateOf("") }

    val currentList = if (query.isBlank()) displayPalette
    else displayPalette.filter {
        it.key.contains(query, ignoreCase = true) ||
                it.hex.contains(query, ignoreCase = true) ||
                it.name.contains(query, ignoreCase = true)
    }

    // 分组
    val groups = remember(currentList) {
        currentList.groupBy {
            val k = it.key.trim()
            if (k.startsWith("P") && k.length > 2) "P"
            else if (k.startsWith("S") && k.length > 1 && k[1].isDigit()) "S"
            else if (k.startsWith("C") && k.length > 1 && k[1].isDigit()) "C"
            else if (k.startsWith("A") && k.length > 1 && k[1].isDigit()) "A"
            else if (k.startsWith("H") && k.length > 1 && k[1].isDigit()) "H"
            else k.take(1).uppercase()
        }.toSortedMap()
    }

    val collapsed = remember(selectedBrand) {
        mutableStateMapOf<String, Boolean>().apply {
            groups.keys.forEach { put(it, false) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val returnTarget = if (vm.bitmap != null) Screen.Settings else Screen.Home
        TopAppBar(
            title = { Text("色板与豆仓") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(returnTarget) }) { Text("返回") }
            },
            actions = {
                TextButton(onClick = {
                    vm.setBeadBrand(selectedBrand)
                    vm.settings.savePaletteSelections(selectedBrand, paletteSelections.toMap())
                    // 一次性原子批量持久化豆仓库存
                    vm.inventoryStore.saveAllStock(selectedBrand, stockSelections.toMap())
                    vm.refreshActivePalette()
                    vm.navigate(returnTarget)
                }) { Text("保存并应用") }
            }
        )

        // 品牌选择水平滑动行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BeadBrand.entries.forEach { brand ->
                FilterChip(
                    selected = selectedBrand == brand,
                    onClick = { selectedBrand = brand },
                    label = { Text(brand.displayName) }
                )
            }
        }

        // 若为 MARD，展示国内店家切换
        if (selectedBrand == BeadBrand.MARD) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ColorSystem.entries.forEach { item ->
                    FilterChip(
                        selected = cs == item,
                        onClick = { vm.setColorSystem(item) },
                        label = { Text(item.key) }
                    )
                }
            }
        }

        // Tab 切换：活动色板 vs 豆仓库存
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("活动色板 (${paletteSelections.count { it.value }}/${fullPalette.size})") }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("我的豆仓 (${stockSelections.count { it.value }}/${fullPalette.size})") }
            )
        }

        // 快捷操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (activeTab == 0) "已启用 ${paletteSelections.count { it.value }} 色"
                else "手头已有现货 ${stockSelections.count { it.value }} 色",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeTab == 1) {
                    TextButton(onClick = {
                        fullPalette.forEach { stockSelections[it.hex.uppercase()] = true }
                    }) {
                        Text("全选入库")
                    }
                    TextButton(onClick = {
                        fullPalette.forEach { stockSelections[it.hex.uppercase()] = false }
                    }) {
                        Text("清空库存")
                    }
                } else {
                    TextButton(onClick = {
                        fullPalette.forEach { paletteSelections[it.hex.uppercase()] = true }
                    }) {
                        Text("全选")
                    }
                    TextButton(onClick = {
                        fullPalette.forEach { paletteSelections[it.hex.uppercase()] = false }
                    }) {
                        Text("全不选")
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索色号、英文名称或 hex（如 S01 / White / FF0000）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        val targetSelections = if (activeTab == 0) paletteSelections else stockSelections

        LazyColumn(modifier = Modifier.weight(1f)) {
            groups.forEach { (prefix, colors) ->
                item(key = "header_${selectedBrand.id}_$prefix") {
                    val isCollapsed = collapsed[prefix] ?: false
                    val allInGroup = colors.map { it.hex.uppercase() }
                    val selectedInGroup = allInGroup.count { targetSelections[it] == true }
                    val allSelected = selectedInGroup == allInGroup.size

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { collapsed[prefix] = !isCollapsed }
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                            TextButton(onClick = {
                                val newVal = !allSelected
                                allInGroup.forEach { targetSelections[it] = newVal }
                            }) {
                                Text(if (allSelected) "全不选" else "全选")
                            }
                        }
                    }
                }

                item(key = "body_${selectedBrand.id}_$prefix") {
                    AnimatedVisibility(visible = collapsed[prefix] != true) {
                        Column {
                            colors.forEach { pc ->
                                val hex = pc.hex.uppercase()
                                val checked = targetSelections[hex] ?: true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { targetSelections[hex] = !checked }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                pc.key,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (pc.name.isNotBlank() && pc.name != pc.key) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    pc.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(pc.hex, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { targetSelections[hex] = it }
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