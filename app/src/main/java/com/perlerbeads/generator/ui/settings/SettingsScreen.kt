package com.perlerbeads.generator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.PixelationMode
import com.perlerbeads.generator.model.ColorSystem
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.editor.AppViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var granularity by remember { mutableFloatStateOf(vm.settings.granularity.toFloat()) }
    var mode by remember { mutableStateOf(vm.settings.mode) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("像素化设置") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(Screen.Crop) }) { Text("返回") }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 粒度
            Text("横向格子数量：${granularity.toInt()}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = granularity,
                onValueChange = { granularity = it },
                valueRange = 20f..200f,
                steps = 17
            )

            Spacer(Modifier.height(16.dp))

            // 模式
            Text("像素化模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == PixelationMode.DOMINANT,
                    onClick = { mode = PixelationMode.DOMINANT },
                    label = { Text("卡通（主色）") }
                )
                FilterChip(
                    selected = mode == PixelationMode.AVERAGE,
                    onClick = { mode = PixelationMode.AVERAGE },
                    label = { Text("真实（平均）") }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 画板形状
            Text("画板形状", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = vm.settings.gridShape == GridShape.SQUARE,
                    onClick = { vm.settings.gridShape = GridShape.SQUARE },
                    label = { Text("方形") }
                )
                FilterChip(
                    selected = vm.settings.gridShape == GridShape.CIRCLE,
                    onClick = { vm.settings.gridShape = GridShape.CIRCLE },
                    label = { Text("圆形") }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 色号系统
            Text("色号系统", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSystem.entries.forEach { cs ->
                    FilterChip(
                        selected = vm.settings.colorSystem == cs,
                        onClick = { vm.setColorSystem(cs) },
                        label = { Text(cs.key) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 当前色板概览
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前色板：${vm.activePalette.size} 色",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { vm.navigate(Screen.Palette) }) {
                    Text("管理色板")
                }
            }

            Spacer(Modifier.height(32.dp))

            // 生成按钮
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        vm.settings.granularity = granularity.toInt()
                        vm.settings.mode = mode
                        vm.generate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("生成图纸", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (vm.processing) {
                Spacer(Modifier.height(16.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}