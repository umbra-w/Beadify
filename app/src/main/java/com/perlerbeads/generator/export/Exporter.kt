package com.perlerbeads.generator.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.perlerbeads.generator.ui.components.GridRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** 颜色统计行。 */
data class ColorStatRow(
    val key: String,
    val hex: String,
    val count: Int
)

/** 图纸 / 统计 / 清单导出。 */
object Exporter {

    /**
     * 渲染图纸位图（可选拼接统计表）。
     * 内存预算分级：带统计 1200 万像素、不带 1800 万像素。
     * 统计表宽度与图纸严格对齐，内部根据图纸尺寸自适应多列网格排版，
     * 彻底解决单列排版导致大图底部过度拉长与左右留白问题。
     */
    fun renderPatternBitmap(
        grid: com.perlerbeads.generator.model.GridData,
        circle: com.perlerbeads.generator.model.CircleGeometry?,
        stats: List<ColorStatRow>,
        totalCount: Int,
        hideWhite: Boolean,
        mirror: Boolean,
        attachStats: Boolean
    ): Bitmap {
        val budget = if (attachStats) 12_000_000f else 18_000_000f
        val cellByArea = kotlin.math.sqrt(budget / (grid.n * grid.m)).toInt()
        val gridCell = cellByArea.coerceIn(16, 48)
        val circleCell = circle?.let { (4096f / (2f * it.radius)).toInt() } ?: Int.MAX_VALUE
        val cell = maxOf(4, minOf(48, minOf(gridCell, circleCell)))
        val pattern = GridRenderer.render(
            grid, cell, showBorders = true, showKeys = true,
            hideWhiteKeys = hideWhite, mirror = mirror,
            circle = circle
        )
        if (!attachStats) return pattern

        // 统计表宽度与图纸等宽对齐（若图纸极小，保底 480px 居中对齐）
        val statsWidth = maxOf(pattern.width, 480)
        val statsBmp = renderStatsBitmap(stats, totalCount, width = statsWidth)
        val combinedWidth = maxOf(pattern.width, statsBmp.width)
        val combinedHeight = pattern.height + statsBmp.height
        val combined = Bitmap.createBitmap(
            combinedWidth, combinedHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)
        val patternLeft = (combinedWidth - pattern.width) / 2f
        val statsLeft = (combinedWidth - statsBmp.width) / 2f
        canvas.drawBitmap(pattern, patternLeft, 0f, null)
        canvas.drawBitmap(statsBmp, statsLeft, pattern.height.toFloat(), null)
        statsBmp.recycle()
        pattern.recycle()
        return combined
    }

    /**
     * 生成颜色统计 PNG：色块 + 色号 + 数量，按数量降序。
     * 根据图纸/画布宽度自适应计算列数（多列网格排列），避免单列排版导致大图底部过度拉长与留白。
     * @param width 输出宽度；拼接到大图下方时与大图等宽，内部自适应 1~6 列
     */
    fun renderStatsBitmap(rows: List<ColorStatRow>, totalCount: Int, width: Int = 800): Bitmap {
        val safeWidth = maxOf(360, width)
        val margin = (safeWidth * 0.03f).coerceIn(24f, 48f)
        val availW = safeWidth - 2f * margin

        // 每列理想宽度约 300~340px，由此计算自适应列数
        val idealColW = 320f
        val rawCols = (availW / idealColW).toInt().coerceAtLeast(1)
        // 列数不应超过总颜色项数（例如只有 2 种颜色时最多 2 列）
        val numCols = rawCols.coerceIn(1, maxOf(1, rows.size))

        val numRows = if (rows.isEmpty()) 0 else (rows.size + numCols - 1) / numCols
        val rowHeight = 44f
        val headerHeight = 72f
        val footerHeight = 44f
        val swatchSize = 26f
        val colWidth = availW / numCols.toFloat()

        val height = maxOf(100, (headerHeight + numRows * rowHeight + footerHeight).toInt())
        val bmp = Bitmap.createBitmap(safeWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val topDividerPaint = Paint().apply {
            color = 0xFFE0E0E0.toInt()
            strokeWidth = 1.5f
        }
        val headerDividerPaint = Paint().apply {
            color = 0xFFE8E8E8.toInt()
            strokeWidth = 1f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF222222.toInt()
            textSize = 26f
            isFakeBoldText = true
        }
        val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF222222.toInt()
            textSize = 21f
            isFakeBoldText = true
        }
        val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF666666.toInt()
            textSize = 17f
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF333333.toInt()
            textSize = 20f
            textAlign = Paint.Align.RIGHT
        }
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val swatchBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x28000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // 顶部分隔线（与上方图纸产生清晰分界）
        canvas.drawLine(0f, 0f, safeWidth.toFloat(), 0f, topDividerPaint)

        // 标题与总计
        val titleText = "拼豆颜色统计（共 ${rows.size} 色 · 合计 $totalCount 粒）"
        canvas.drawText(titleText, margin, 42f, titlePaint)
        canvas.drawLine(margin, headerHeight - 12f, safeWidth - margin, headerHeight - 12f, headerDividerPaint)

        // 多列排列各颜色项（水平优先，便于横向扫视高频色）
        rows.forEachIndexed { index, row ->
            val r = index / numCols
            val c = index % numCols
            val cellLeft = margin + c * colWidth
            val cellY = headerHeight + r * rowHeight

            // 色块
            val swatchLeft = cellLeft + 4f
            val swatchTop = cellY + (rowHeight - swatchSize) / 2f
            swatchPaint.color = GridRenderer.parseHex(row.hex)
            canvas.drawRect(
                swatchLeft, swatchTop,
                swatchLeft + swatchSize, swatchTop + swatchSize,
                swatchPaint
            )
            canvas.drawRect(
                swatchLeft, swatchTop,
                swatchLeft + swatchSize, swatchTop + swatchSize,
                swatchBorderPaint
            )

            // 文字基线：居中对齐
            val fontMetrics = keyPaint.fontMetrics
            val baseline = cellY + (rowHeight - fontMetrics.ascent - fontMetrics.descent) / 2f

            // 色号与 Hex
            val keyLeft = swatchLeft + swatchSize + 10f
            canvas.drawText(row.key, keyLeft, baseline, keyPaint)
            val hexLeft = keyLeft + keyPaint.measureText("${row.key} ")
            canvas.drawText(row.hex, hexLeft, baseline, hexPaint)

            // 数量（右对齐于该列）
            val countText = "${row.count} 粒"
            val countRight = cellLeft + colWidth - 14f
            canvas.drawText(countText, countRight, baseline, countPaint)
        }

        // 底部落款
        val bottomText = "由拼豆图纸生成器导出"
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f
            color = 0xFF999999.toInt()
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            bottomText,
            safeWidth - margin,
            height - 16f,
            smallPaint
        )
        return bmp
    }

    /** 构建采购清单 CSV：色号,hex,数量（编码 UTF-8 BOM 便于 Excel 打开）。 */
    fun buildShoppingListCsv(rows: List<ColorStatRow>, totalCount: Int): String {
        val sb = StringBuilder()
        sb.append("色号,hex,数量\n")
        rows.forEach { sb.append("${it.key},${it.hex},${it.count}\n") }
        sb.append("合计,,$totalCount\n")
        return sb.toString()
    }

    /** 保存图纸 CSV 到下载目录（可供分享或在其他设备重新导入）。 */
    fun savePatternCsvToDownloads(context: Context, grid: com.perlerbeads.generator.model.GridData, name: String): Uri {
        val csv = com.perlerbeads.generator.data.CsvCodec.exportPatternCsv(grid)
        return saveCsvToDownloads(context, csv, name)
    }

    /**
     * 保存 PNG 到相册（API 29+ 走 MediaStore；旧版本写外部私有目录并触发媒体扫描）。
     * @return 可分享的 Uri
     */
    fun savePngToPictures(context: Context, bitmap: Bitmap, name: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PerlerBeads")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PerlerBeads")
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /** 保存 CSV 到 Downloads（API 29+ MediaStore；否则外部私有目录 + FileProvider）。 */
    fun saveCsvToDownloads(context: Context, content: String, name: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PerlerBeads")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)?.use { out ->
                // UTF-8 BOM
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "PerlerBeads")
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /** 保存 PDF 到 Downloads（API 29+ MediaStore；否则外部私有目录 + FileProvider）。 */
    fun savePdfToDownloads(context: Context, bytes: ByteArray, name: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PerlerBeads")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "PerlerBeads")
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { out -> out.write(bytes) }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private fun writeBitmapTo(out: OutputStream, bitmap: Bitmap) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
