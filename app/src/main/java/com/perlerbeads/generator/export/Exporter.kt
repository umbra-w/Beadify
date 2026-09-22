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
     * 内存预算分级：带统计 1200 万像素、不带 1800 万像素 —— 统计表若按图纸全宽缩放
     * 会放大 10 倍导致 OOM（曾导致导出无反应/只有统计没有图纸），故宽度封顶 1600px 水平居中。
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

        val statsWidth = minOf(pattern.width, 1600)
        val statsBmp = renderStatsBitmap(stats, totalCount, width = statsWidth)
        val combined = Bitmap.createBitmap(
            pattern.width, pattern.height + statsBmp.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)
        val statsLeft = (pattern.width - statsWidth) / 2f
        canvas.drawBitmap(pattern, 0f, 0f, null)
        canvas.drawBitmap(statsBmp, statsLeft, pattern.height.toFloat(), null)
        statsBmp.recycle()
        pattern.recycle()
        return combined
    }

    /**
     * 生成颜色统计 PNG：色块 + 色号 + 数量，按数量降序。
     * @param width 输出宽度；拼接到大图下方时传与大图一致的宽度，内部按比例缩放字号
     */
    fun renderStatsBitmap(rows: List<ColorStatRow>, totalCount: Int, width: Int = 480): Bitmap {
        val scale = width / 480f
        val rowHeight = (64 * scale).toInt().coerceAtLeast(24)
        val margin = (24 * scale).toInt()
        val headerHeight = (96 * scale).toInt()
        val titleSize = 40f * scale
        val rowTextSize = 36f * scale
        val swatchSize = (48 * scale).toInt()
        val height = headerHeight + rows.size * rowHeight + (80 * scale).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = titleSize
            isFakeBoldText = true
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = rowTextSize
        }
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }

        canvas.drawText("拼豆颜色统计（共 $totalCount 粒）", margin.toFloat(), (60 * scale), titlePaint)

        rows.forEachIndexed { index, row ->
            val y = headerHeight + index * rowHeight
            val swatchLeft = margin
            val swatchTop = y + (8 * scale).toInt()
            swatchPaint.color = GridRenderer.parseHex(row.hex)
            canvas.drawRect(
                swatchLeft.toFloat(), swatchTop.toFloat(),
                (swatchLeft + swatchSize).toFloat(), (swatchTop + swatchSize).toFloat(),
                swatchPaint
            )
            canvas.drawRect(
                swatchLeft.toFloat(), swatchTop.toFloat(),
                (swatchLeft + swatchSize).toFloat(), (swatchTop + swatchSize).toFloat(),
                borderPaint
            )
            canvas.drawText("${row.key}  ${row.hex}", (swatchLeft + swatchSize + 16 * scale), y + rowHeight * 0.66f, textPaint)
            val countText = "${row.count}"
            canvas.drawText(countText, (width - margin - textPaint.measureText(countText)), y + rowHeight * 0.66f, textPaint)
        }

        val bottomText = "由拼豆图纸生成器导出"
        val smallPaint = Paint(textPaint).apply { textSize = 26f * scale; color = Color.GRAY }
        canvas.drawText(
            bottomText,
            (width - margin - smallPaint.measureText(bottomText)),
            (height - 40 * scale),
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
