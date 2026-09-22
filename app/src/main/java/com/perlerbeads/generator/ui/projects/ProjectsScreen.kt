package com.perlerbeads.generator.ui.projects

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.editor.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 我的项目：缩略图列表，点击打开进编辑器，可删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: AppViewModel) {
    // 每次进入刷新列表（保存/删除后也会自动反映）
    LaunchedEffect(Unit) { vm.refreshProjects() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的项目") },
            navigationIcon = {
                TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("返回") }
            }
        )

        if (vm.projects.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(80.dp))
                Text("暂无项目", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "在编辑器里点击顶栏的保存图标，即可把当前图纸存为项目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(vm.projects, key = { it.id }) { meta ->
                val thumb = remember(meta.id) { vm.projectStore.decodeThumbnail(meta.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.openProject(meta.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Spacer(Modifier.size(56.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(meta.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${meta.n}×${meta.m} · ${dateFormat.format(Date(meta.savedAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { vm.deleteProject(meta.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
