package com.perlerbeads.generator.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.perlerbeads.generator.algorithm.TextColorMode
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.PixelFont
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

/**
 * 文字拼豆：输入文字 → 选行数/颜色/字体/特效 → 生成网格直接进编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextBeadsScreen(vm: AppViewModel) {
    val chosen = vm.textBeadColor ?: vm.activePalette.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("文字拼豆") },
            navigationIcon = {
                TextButton(onClick = { vm.goBack() }) { Text("返回") }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 文字输入 =====
            OutlinedTextField(
                value = vm.textBeadText,
                onValueChange = { vm.textBeadText = it },
                label = { Text("文字内容") },
                placeholder = { Text("例如：生日快乐") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ===== 字体选择 =====
            Spacer(Modifier.height(20.dp))
            Text("像素字体", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelFont.entries.forEach { font ->
                    FilterChip(
                        selected = vm.textBeadFont == font,
                        onClick = { vm.textBeadFont = font },
                        label = { Text(font.displayName) }
                    )
                }
            }
            Text(
                vm.textBeadFont.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ===== 行数（字号高度） =====
            Spacer(Modifier.height(20.dp))
            Text("网格行数（字号高度）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 32, 50, 72).forEach { size ->
                    FilterChip(
                        selected = vm.textBeadRows == size,
                        onClick = { vm.textBeadRows = size },
                        label = { Text("$size 行") }
                    )
                }
            }

            // ===== 颜色模式 =====
            Spacer(Modifier.height(20.dp))
            Text("颜色模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.textBeadColorMode == TextColorMode.SINGLE,
                    onClick = { vm.textBeadColorMode = TextColorMode.SINGLE },
                    label = { Text("单色") }
                )
                FilterChip(
                    selected = vm.textBeadColorMode == TextColorMode.GRADIENT,
                    onClick = { vm.textBeadColorMode = TextColorMode.GRADIENT },
                    label = { Text("渐变") }
                )
                FilterChip(
                    selected = vm.textBeadColorMode == TextColorMode.RAINBOW,
                    onClick = { vm.textBeadColorMode = TextColorMode.RAINBOW },
                    label = { Text("彩虹") }
                )
            }

            // ===== 文字颜色（主色） =====
            Spacer(Modifier.height(20.dp))
            Text(
                if (vm.textBeadColorMode == TextColorMode.GRADIENT) "起始颜色" else "文字颜色",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.activePalette, key = { it.hex }) { pc ->
                    ColorSwatchBig(
                        pc = pc,
                        selected = chosen?.hex == pc.hex,
                        onClick = { vm.textBeadColor = pc }
                    )
                }
            }

            // ===== 渐变结束颜色 =====
            if (vm.textBeadColorMode == TextColorMode.GRADIENT) {
                Spacer(Modifier.height(16.dp))
                Text("结束颜色", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val endChosen = vm.textBeadGradientEndColor ?: vm.activePalette.lastOrNull()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.activePalette, key = { it.hex }) { pc ->
                        ColorSwatchBig(
                            pc = pc,
                            selected = endChosen?.hex == pc.hex,
                            onClick = { vm.textBeadGradientEndColor = pc }
                        )
                    }
                }
            }

            // ===== 描边 =====
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("描边", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = vm.textBeadOutlineEnabled,
                    onCheckedChange = { vm.textBeadOutlineEnabled = it }
                )
            }
            if (vm.textBeadOutlineEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("描边颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                val outlineChosen = vm.textBeadOutlineColor
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.activePalette, key = { it.hex }) { pc ->
                        ColorSwatchBig(
                            pc = pc,
                            selected = outlineChosen?.hex == pc.hex,
                            onClick = { vm.textBeadOutlineColor = pc }
                        )
                    }
                }
                if (outlineChosen == null) {
                    Text(
                        "未选择则自动使用最深色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 阴影 =====
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("投影", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = vm.textBeadShadowEnabled,
                    onCheckedChange = { vm.textBeadShadowEnabled = it }
                )
            }
            if (vm.textBeadShadowEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("投影颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                val shadowChosen = vm.textBeadShadowColor
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.activePalette, key = { it.hex }) { pc ->
                        ColorSwatchBig(
                            pc = pc,
                            selected = shadowChosen?.hex == pc.hex,
                            onClick = { vm.textBeadShadowColor = pc }
                        )
                    }
                }
                if (shadowChosen == null) {
                    Text(
                        "未选择则自动使用最深色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 背景填白 =====
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = vm.textBeadBgWhite,
                    onClick = { vm.textBeadBgWhite = !vm.textBeadBgWhite },
                    label = { Text("背景填白") }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.textBeadBgWhite) "背景用色板中最白的色铺满" else "背景透明（只拼笔画）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 生成按钮 =====
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { vm.generateTextBeads() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("生成并进入编辑器", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "生成后可在编辑器继续调整或导出；行数、文字重进页面仍会保留",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))  // 底部留白给滚动
        }
    }
}

@Composable
private fun ColorSwatchBig(pc: PaletteColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(if (selected) 10.dp else 6.dp))
                    .background(Color(GridRenderer.parseHex(pc.hex)))
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        } else Modifier
                    )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                pc.key,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
