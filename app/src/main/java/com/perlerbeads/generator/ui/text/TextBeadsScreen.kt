package com.perlerbeads.generator.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.components.GridRenderer
import com.perlerbeads.generator.ui.editor.AppViewModel

/**
 * 文字拼豆：输入文字 → 选行数与颜色 → 生成网格直接进编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextBeadsScreen(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var rows by remember { mutableIntStateOf(32) }
    var chosen by remember { mutableStateOf(vm.activePalette.firstOrNull()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("文字拼豆") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("返回") }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("文字内容") },
                placeholder = { Text("例如：生日快乐") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text("网格行数（字号高度）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 32, 50, 72).forEach { size ->
                    FilterChip(
                        selected = rows == size,
                        onClick = { rows = size },
                        label = { Text("$size 行") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("文字颜色", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.activePalette, key = { it.hex }) { pc ->
                    ColorSwatchBig(
                        pc = pc,
                        selected = chosen?.hex == pc.hex,
                        onClick = { chosen = pc }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { vm.generateTextBeads(text, rows, chosen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("生成并进入编辑器", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "生成后可直接导出，也可在编辑器继续调整；背景为透明，只拼笔画部分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
