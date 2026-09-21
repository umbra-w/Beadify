package com.perlerbeads.generator.data

import com.perlerbeads.generator.model.CircleGeometry
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PixelationMode
import com.perlerbeads.generator.model.transparentColorData

/** 已保存项目的纯数据快照（无 Android 依赖，便于单测）。 */
data class SavedProject(
    val name: String,
    val shape: GridShape,
    val circle: CircleGeometry?,
    val granularity: Int,
    val mode: PixelationMode,
    val dithering: Boolean,
    val colorSystemKey: String,
    val cells: Array<Array<MappedPixel>>
) {
    val n: Int get() = if (cells.isEmpty()) 0 else cells[0].size
    val m: Int get() = cells.size
}

/**
 * 项目文件编解码（行格式，纯 Kotlin）：
 * ```
 * PERLER_PROJECT 1
 * NAME <名称>
 * SIZE <n> <m>
 * SHAPE SQUARE|CIRCLE
 * CIRCLE <cx> <cy> <r>          （仅圆形）
 * SETTINGS <granularity> <MODE> <dithering> <色号系统>
 * ROWS
 * <行0：逗号分隔，external 格为 E，其余为 key|hex>
 * ...
 * ```
 * 损坏或被篡改的文件解码返回 null，不会半途崩溃。
 */
object ProjectCodec {

    private const val HEADER = "PERLER_PROJECT"

    fun encode(project: SavedProject): String {
        val sb = StringBuilder()
        sb.append(HEADER).append(" 1\n")
        sb.append("NAME ").append(project.name.replace('\n', ' ')).append('\n')
        sb.append("SIZE ").append(project.n).append(' ').append(project.m).append('\n')
        sb.append("SHAPE ").append(project.shape.name).append('\n')
        project.circle?.let {
            sb.append("CIRCLE ").append(it.centerX).append(' ')
                .append(it.centerY).append(' ').append(it.radius).append('\n')
        }
        sb.append("SETTINGS ").append(project.granularity).append(' ')
            .append(project.mode.name).append(' ')
            .append(project.dithering).append(' ')
            .append(project.colorSystemKey).append('\n')
        sb.append("ROWS\n")
        for (row in project.cells) {
            sb.append(
                row.joinToString(",") { c -> if (c.isExternal) "E" else "${c.key}|${c.colorHex}" }
            ).append('\n')
        }
        return sb.toString()
    }

    fun decode(text: String): SavedProject? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim()?.startsWith(HEADER) != true) return null

        var name = "未命名"
        var n = 0
        var m = 0
        var shape = GridShape.SQUARE
        var circle: CircleGeometry? = null
        var granularity = 50
        var mode = PixelationMode.DOMINANT
        var dithering = false
        var colorSystemKey = "MARD"
        val cellLines = ArrayList<String>()

        for (raw in lines.drop(1)) {
            val line = raw.trimEnd()
            if (line.isEmpty()) continue
            val space = line.indexOf(' ')
            val tag = if (space < 0) line else line.substring(0, space)
            val rest = if (space < 0) "" else line.substring(space + 1)
            when (tag) {
                "NAME" -> name = rest.ifBlank { "未命名" }
                "SIZE" -> {
                    val p = rest.split(" ")
                    n = p.getOrNull(0)?.toIntOrNull() ?: return null
                    m = p.getOrNull(1)?.toIntOrNull() ?: return null
                }
                "SHAPE" -> shape = if (rest == "CIRCLE") GridShape.CIRCLE else GridShape.SQUARE
                "CIRCLE" -> {
                    val p = rest.split(" ")
                    val cx = p.getOrNull(0)?.toFloatOrNull() ?: return null
                    val cy = p.getOrNull(1)?.toFloatOrNull() ?: return null
                    val r = p.getOrNull(2)?.toFloatOrNull() ?: return null
                    circle = CircleGeometry(cx, cy, r)
                }
                "SETTINGS" -> {
                    val p = rest.split(" ")
                    granularity = p.getOrNull(0)?.toIntOrNull() ?: 50
                    mode = try {
                        PixelationMode.valueOf(p.getOrNull(1) ?: "DOMINANT")
                    } catch (e: IllegalArgumentException) {
                        PixelationMode.DOMINANT
                    }
                    dithering = p.getOrNull(2) == "true"
                    colorSystemKey = p.getOrNull(3) ?: "MARD"
                }
                "ROWS" -> Unit
                else -> if (tag.isNotEmpty() && cellLines.size < m) cellLines.add(line)
            }
        }
        if (n <= 0 || m <= 0 || cellLines.size != m) return null

        val cells = Array(m) { r ->
            val tokens = cellLines[r].split(",")
            if (tokens.size != n) return null
            Array(n) { c ->
                val token = tokens[c]
                if (token == "E") {
                    transparentColorData
                } else {
                    val sep = token.indexOf('|')
                    if (sep <= 0) return null
                    MappedPixel(token.substring(0, sep), token.substring(sep + 1), false)
                }
            }
        }
        return SavedProject(name, shape, circle, granularity, mode, dithering, colorSystemKey, cells)
    }
}
