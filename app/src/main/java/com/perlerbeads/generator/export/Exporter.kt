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

    /** 生成颜色统计 PNG：色块 + 色号 + 数量，按数量降序。 */
    fun renderStatsBitmap(rows: List<ColorStatRow>, totalCount: Int): Bitmap {
        val rowHeight = 64
        val margin = 24
        val headerHeight = 96
        val width = 480
        val height = headerHeight + rows.size * rowHeight + 80
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            isFakeBoldText = true
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
        }
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        canvas.drawText("拼豆颜色统计（共 $totalCount 粒）", margin.toFloat(), 60f, titlePaint)

        rows.forEachIndexed { index, row ->
            val y = headerHeight + index * rowHeight
            val swatchLeft = margin
            val swatchTop = y + 8
            swatchPaint.color = GridRenderer.parseHex(row.hex)
            canvas.drawRect(
                swatchLeft.toFloat(), swatchTop.toFloat(),
                (swatchLeft + 48).toFloat(), (swatchTop + 48).toFloat(),
                swatchPaint
            )
            canvas.drawRect(
                swatchLeft.toFloat(), swatchTop.toFloat(),
                (swatchLeft + 48).toFloat(), (swatchTop + 48).toFloat(),
                borderPaint
            )
            canvas.drawText("${row.key}  ${row.hex}", (swatchLeft + 64).toFloat(), y + 42f, textPaint)
            val countText = "${row.count}"
            canvas.drawText(countText, (width - margin - textPaint.measureText(countText)).toFloat(), y + 42f, textPaint)
        }

        val bottomText = "由拼豆图纸生成器导出"
        canvas.drawText(
            bottomText,
            (width - margin - textPaint.measureText(bottomText)).toFloat(),
            (height - 40).toFloat(),
            textPaint.apply { textSize = 26f; color = Color.GRAY }
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

    private fun writeBitmapTo(out: OutputStream, bitmap: Bitmap) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
