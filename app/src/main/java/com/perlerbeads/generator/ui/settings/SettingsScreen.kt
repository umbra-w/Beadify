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
    var gridShape by remember { mutableStateOf(vm.settings.gridShape) }
    var circleOffsetX by remember { mutableFloatStateOf(vm.settings.circleOffsetX) }
    var circleOffsetY by remember { mutableFloatStateOf(vm.settings.circleOffsetY) }

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
                    selected = gridShape == GridShape.SQUARE,
                    onClick = { gridShape = GridShape.SQUARE },
                    label = { Text("方形") }
                )
                FilterChip(
                    selected = gridShape == GridShape.CIRCLE,
                    onClick = { gridShape = GridShape.CIRCLE },
                    label = { Text("圆形") }
                )
            }

            // 圆形模式下显示圆板覆盖范围控制：
            // 横图只有左右位置有意义，竖图只有上下位置有意义，接近正方形时无需调整
            if (gridShape == GridShape.CIRCLE) {
                Spacer(Modifier.height(12.dp))
                Text("圆形画板覆盖范围", style = MaterialTheme.typography.titleMedium)
                Text(
                    "生成图纸后可双指缩放/拖动取位，拖动圆环边缘调整圆板大小",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val imgAspect = vm.bitmap?.let { it.height.toFloat() / it.width.toFloat() } ?: 1f
                if (imgAspect > 1.05f) {
                    Text(
                        "图案比圆形画板高，选择圆板圈住图案的哪一段：${describeCircleOffset(circleOffsetY, true)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(value = circleOffsetY, onValueChange = { circleOffsetY = it }, valueRange = 0f..1f)
                } else if (imgAspect < 0.95f) {
                    Text(
                        "图案比圆形画板宽，选择圆板圈住图案的哪一段：${describeCircleOffset(circleOffsetX, false)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(value = circleOffsetX, onValueChange = { circleOffsetX = it }, valueRange = 0f..1f)
                } else {
                    Text(
                        "图案接近正方形，圆板可完整覆盖，无需调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        vm.settings.gridShape = gridShape
                        vm.settings.circleOffsetX = circleOffsetX
                        vm.settings.circleOffsetY = circleOffsetY
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
/**
 * 把圆板覆盖位置的 0..1 滑块值转成用户能读懂的描述。
 * @param vertical 竖图（上下取景）用「顶部/底部」，横图（左右取景）用「左侧/右侧」。
 */
private fun describeCircleOffset(v: Float, vertical: Boolean): String {
    val pct = (v * 100).toInt()
    val pos = when {
        v < 0.2f -> if (vertical) "最顶部" else "最左侧"
        v < 0.4f -> if (vertical) "偏上" else "偏左"
        v < 0.6f -> "居中"
        v < 0.8f -> if (vertical) "偏下" else "偏右"
        else -> if (vertical) "最底部" else "最右侧"
    }
    return "$pos（$pct%）"
}
