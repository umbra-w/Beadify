package com.perlerbeads.generator.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.editor.AppViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 简易裁剪。
 * 手柄模式：0=无 1=整体移动 2..5=四角(左上/右上/左下/右下) 6..9=四边(上/下/左/右)。
 * 矩形以图像归一化坐标存储（0..1）。
 */
private const val MIN_RECT = 0.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(vm: AppViewModel) {
    val bmp = vm.bitmap ?: return

    var rect by remember(bmp) { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleTouchPx = with(density) { 48.dp.toPx() } // 增大触控区到 48dp

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("裁剪图片") },
            navigationIcon = {
                TextButton(onClick = { vm.goHome() }) { Text("取消") }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
        ) {
            if (containerSize != IntSize.Zero && bmp.width > 0) {
                val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
                val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
                val dispW: Float
                val dispH: Float
                val offX: Float
                val offY: Float
                if (containerAspect > imgAspect) {
                    dispH = containerSize.height.toFloat()
                    dispW = dispH * imgAspect
                    offX = (containerSize.width - dispW) / 2f
                    offY = 0f
                } else {
                    dispW = containerSize.width.toFloat()
                    dispH = dispW / imgAspect
                    offX = 0f
                    offY = (containerSize.height - dispH) / 2f
                }

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerSize) {
                            // 把显示参数提到 gesture handler 内，避免依赖外部 recomposition
                            val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
                            val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
                            val dispW: Float
                            val dispH: Float
                            val offX: Float
                            val offY: Float
                            if (containerAspect > imgAspect) {
                                dispH = containerSize.height.toFloat()
                                dispW = dispH * imgAspect
                                offX = (containerSize.width - dispW) / 2f
                                offY = 0f
                            } else {
                                dispW = containerSize.width.toFloat()
                                dispH = dispW / imgAspect
                                offX = 0f
                                offY = (containerSize.height - dispH) / 2f
                            }

                            var mode = 0
                            var last = Offset.Zero
                            detectDragGestures(
                                onDragStart = { start ->
                                    val l = rect.left * dispW + offX
                                    val t = rect.top * dispH + offY
                                    val r = rect.right * dispW + offX
                                    val b = rect.bottom * dispH + offY
                                    mode = detectHandleMode(start.x, start.y, l, t, r, b, handleTouchPx)
                                    last = start
                                },
                                onDrag = { change, _ ->
                                    val dxn = (change.position.x - last.x) / dispW
                                    val dyn = (change.position.y - last.y) / dispH
                                    last = change.position
                                    rect = adjustRect(rect, mode, dxn, dyn)
                                    change.consume()
                                },
                                onDragEnd = { mode = 0 },
                                onDragCancel = { mode = 0 }
                            )
                        }
                ) {
                    val left = rect.left * dispW + offX
                    val top = rect.top * dispH + offY
                    val right = rect.right * dispW + offX
                    val bottom = rect.bottom * dispH + offY
                    val cw = right - left
                    val ch = bottom - top

                    // 四块遮罩（不依赖 BlendMode.Clear，避免整块黑屏）
                    val scrim = Color.Black.copy(alpha = 0.45f)
                    // 上
                    drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                    // 下
                    drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                    // 左
                    drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, ch))
                    // 右
                    drawRect(scrim, topLeft = Offset(right, top), size = Size(size.width - right, ch))

                    // 裁剪框边框
                    drawRect(
                        Color.White,
                        topLeft = Offset(left, top),
                        size = Size(cw, ch),
                        style = Stroke(width = 2f)
                    )

                    // 手柄：放大四角方块 + 四边粗条，方便手指触控
                    val hs = 20f
                    val ew = 28f
                    val et = 10f
                    // 角
                    drawRect(Color.White, Offset(left, top), Size(hs, hs))
                    drawRect(Color.White, Offset(right - hs, top), Size(hs, hs))
                    drawRect(Color.White, Offset(left, bottom - hs), Size(hs, hs))
                    drawRect(Color.White, Offset(right - hs, bottom - hs), Size(hs, hs))
                    // 边中点
                    drawRect(Color.White, Offset((left + right - ew) / 2f, top - et / 2f), Size(ew, et))
                    drawRect(Color.White, Offset((left + right - ew) / 2f, bottom - et / 2f), Size(ew, et))
                    drawRect(Color.White, Offset(left - et / 2f, (top + bottom - ew) / 2f), Size(et, ew))
                    drawRect(Color.White, Offset(right - et / 2f, (top + bottom - ew) / 2f), Size(et, ew))
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { vm.onCropDone(cropBitmap(bmp, rect)) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("确定裁剪", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "拖动选区移动；拖边/拖角调整大小",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
            )
        }
    }
}

private fun adjustRect(rect: Rect, mode: Int, dxn: Float, dyn: Float): Rect {
    return when (mode) {
        1 -> { // 整体移动
            var l = rect.left + dxn; var t = rect.top + dyn
            var r = rect.right + dxn; var b = rect.bottom + dyn
            if (l < 0f) { r -= l; l = 0f }
            if (r > 1f) { l -= (r - 1f); r = 1f }
            if (t < 0f) { b -= t; t = 0f }
            if (b > 1f) { t -= (b - 1f); b = 1f }
            Rect(l, t, r, b)
        }
        2 -> Rect( // 左上角
            max(0f, min(rect.right - MIN_RECT, rect.left + dxn)),
            max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)),
            rect.right, rect.bottom
        )
        3 -> Rect( // 右上角
            rect.left,
            max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)),
            min(1f, max(rect.left + MIN_RECT, rect.right + dxn)),
            rect.bottom
        )
        4 -> Rect( // 左下角
            max(0f, min(rect.right - MIN_RECT, rect.left + dxn)),
            rect.top,
            rect.right,
            min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn))
        )
        5 -> Rect( // 右下角
            rect.left,
            rect.top,
            min(1f, max(rect.left + MIN_RECT, rect.right + dxn)),
            min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn))
        )
        6 -> Rect( // 上边
            rect.left,
            max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)),
            rect.right, rect.bottom
        )
        7 -> Rect( // 下边
            rect.left, rect.top,
            rect.right,
            min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn))
        )
        8 -> Rect( // 左边
            max(0f, min(rect.right - MIN_RECT, rect.left + dxn)),
            rect.top,
            rect.right, rect.bottom
        )
        9 -> Rect( // 右边
            rect.left, rect.top,
            min(1f, max(rect.left + MIN_RECT, rect.right + dxn)),
            rect.bottom
        )
        else -> rect
    }
}

private fun detectHandleMode(
    x: Float, y: Float,
    left: Float, top: Float, right: Float, bottom: Float,
    tol: Float
): Int {
    // 优先四角
    if (abs(x - left) < tol && abs(y - top) < tol) return 2
    if (abs(x - right) < tol && abs(y - top) < tol) return 3
    if (abs(x - left) < tol && abs(y - bottom) < tol) return 4
    if (abs(x - right) < tol && abs(y - bottom) < tol) return 5
    // 再四边
    if (abs(y - top) < tol && x in left..right) return 6
    if (abs(y - bottom) < tol && x in left..right) return 7
    if (abs(x - left) < tol && y in top..bottom) return 8
    if (abs(x - right) < tol && y in top..bottom) return 9
    // 框内移动
    return if (x in left..right && y in top..bottom) 1 else 0
}

private fun cropBitmap(src: Bitmap, rect: Rect): Bitmap {
    val w = src.width
    val h = src.height
    val left = max(0f, min(rect.left, rect.right) * w).toInt()
    val top = max(0f, min(rect.top, rect.bottom) * h).toInt()
    val right = min(w.toFloat(), max(rect.left, rect.right) * w).toInt()
    val bottom = min(h.toFloat(), max(rect.top, rect.bottom) * h).toInt()
    val cw = max(1, right - left)
    val ch = max(1, bottom - top)
    return Bitmap.createBitmap(src, left, top, cw, ch)
}
