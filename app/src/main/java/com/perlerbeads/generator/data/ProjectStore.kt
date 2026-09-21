package com.perlerbeads.generator.data

import android.content.Context
import android.graphics.BitmapFactory
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.ui.components.GridRenderer
import java.io.File

/**
 * 项目文件存储：projects 目录下每个项目一个 json，配同名缩略图 PNG。
 * 文件名编码元信息（时间戳/尺寸/名称），列表页无需解析正文。
 */
class ProjectStore(context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "project_thumbs").apply { mkdirs() }

    data class ProjectMeta(
        val id: String,
        val name: String,
        val n: Int,
        val m: Int,
        val savedAt: Long
    )

    fun list(): List<ProjectMeta> =
        dir.listFiles { f -> f.name.startsWith("proj_") && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                // proj_<ts>_<n>x<m>_<name>.json
                val base = f.name.removeSuffix(".json")
                val parts = base.split("_", limit = 4)
                val ts = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val size = parts.getOrNull(2)?.split("x")
                val n = size?.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val m = size?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                ProjectMeta(f.name, parts.getOrNull(3) ?: "未命名", n, m, ts)
            }
            ?.sortedByDescending { it.savedAt }
            ?: emptyList()

    /** 保存项目并生成缩略图；返回项目 id。 */
    fun save(project: SavedProject): String {
        val ts = System.currentTimeMillis()
        val safeName = project.name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(30).ifEmpty { "未命名" }
        val id = "proj_${ts}_${project.n}x${project.m}_$safeName.json"
        File(dir, id).writeText(ProjectCodec.encode(project))

        runCatching {
            val grid = GridData(project.n, project.m, project.cells, emptySet(), project.shape)
            val thumb = GridRenderer.renderCapped(grid, maxDim = 200, showBorders = true)
            java.io.FileOutputStream(File(thumbDir, id.removeSuffix(".json") + ".png")).use {
                thumb.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it)
            }
            thumb.recycle()
        }
        return id
    }

    fun load(id: String): SavedProject? {
        val file = File(dir, id)
        if (!file.exists() || !file.canonicalPath.startsWith(dir.canonicalPath)) return null
        return ProjectCodec.decode(file.readText())
    }

    fun delete(id: String) {
        File(dir, id).delete()
        File(thumbDir, id.removeSuffix(".json") + ".png").delete()
    }

    fun decodeThumbnail(id: String): android.graphics.Bitmap? {
        val f = File(thumbDir, id.removeSuffix(".json") + ".png")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }
}
