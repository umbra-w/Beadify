package com.perlerbeads.generator.ui.crop

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.editor.AppViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_RECT = 0.05f

/** 手势诊断日志标签（定位双指缩放问题用，问题关闭后移除）。 */
private const val TAG = "PerlerGesture"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(vm: AppViewModel) {
    val bmp = vm.bitmap ?: return

    var rect by remember(bmp) { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleTouchPx = with(density) { 48.dp.toPx() }

    // 图片缩放与平移（由下方统一手势处理器驱动）
    var imgZoom by remember { mutableFloatStateOf(1f) }
    var imgPan by remember { mutableStateOf(Offset.Zero) }

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
                val cAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
                val baseW: Float; val baseH: Float; val offX: Float; val offY: Float
                if (cAspect > imgAspect) {
                    baseH = containerSize.height.toFloat(); baseW = baseH * imgAspect
                    offX = (containerSize.width - baseW) / 2f; offY = 0f
                } else {
                    baseW = containerSize.width.toFloat(); baseH = baseW / imgAspect
                    offX = 0f; offY = (containerSize.height - baseH) / 2f
                }

                // 图片层：内容左上角固定在 (offX, offY)，缩放/平移围绕内容左上角，
                // 与裁剪框的绘制/命中数学（rect*base*zoom + off + pan）严格一致。
                // 旧实现用 fillMaxSize+graphicsLayer 围绕容器原点缩放，图片有留白时
                // 一缩放内容就偏离数学位置，表现为"缩放错位/无法缩放"。
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                            .size(with(density) { baseW.toDp() }, with(density) { baseH.toDp() })
                            .graphicsLayer {
                                scaleX = imgZoom; scaleY = imgZoom
                                translationX = imgPan.x; translationY = imgPan.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    )
                }

                // 裁剪框遮罩层（固定不缩放），并承载全部手势：
                // 单指 = 调整/拖动裁剪框，框外拖动 = 新建选框；双指 = 缩放 + 平移图片；双击 = 复位
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerSize, bmp) {
                            val slopPx = viewConfiguration.touchSlop
                            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                            // 双击检测跨手势记忆
                            var lastUpTime = 0L
                            var lastUpPos = Offset.Zero
                            var wasTap = false

                            // 屏幕坐标 → 图片归一化坐标（0..1）
                            fun normX(sx: Float) = ((sx - offX - imgPan.x) / (baseW * imgZoom)).coerceIn(0f, 1f)
                            fun normY(sy: Float) = ((sy - offY - imgPan.y) / (baseH * imgZoom)).coerceIn(0f, 1f)

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)

                                // 双击复位缩放与平移
                                if (wasTap &&
                                    down.uptimeMillis - lastUpTime < doubleTapTimeout &&
                                    (down.position - lastUpPos).getDistance() < slopPx * 2f
                                ) {
                                    imgZoom = 1f
                                    imgPan = Offset.Zero
                                    wasTap = false
                                    down.consume()
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        e.changes.forEach { if (it.pressed) it.consume() }
                                        if (e.changes.none { it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                                wasTap = false

                                var mode = 0        // 0=待定 1=裁剪框 2=图片变换 3=新建选框
                                var rectMode = 0    // detectHandleMode 的操作对象
                                var last = down.position
                                var pastSlop = false
                                var anchor = Offset.Zero      // mode 3 的起点（归一化）
                                var savedRect = rect          // mode 3 失败时回滚
                                var lastUpTimeThis = down.uptimeMillis
                                var lastPointerId = down.id
                                var lastReportedCount = 1

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        lastUpTimeThis = event.changes.maxOf { it.uptimeMillis }
                                        break
                                    }

                                    if (pressed.size != lastReportedCount) {
                                        Log.d(
                                            TAG, "crop pointers=${pressed.size} " +
                                                "zoomChange=${event.calculateZoom()} mode=$mode"
                                        )
                                        lastReportedCount = pressed.size
                                    }

                                    if (pressed.size >= 2) {
                                        // 双指：缩放 + 平移，一旦进入即接管整次手势
                                        if (mode == 3) rect = savedRect // 新建选框被中断则回滚
                                        mode = 2
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange.isFinite() && zoomChange > 0f) {
                                            imgZoom = (imgZoom * zoomChange).coerceIn(1f, 6f)
                                            Log.d(TAG, "crop imgZoom -> $imgZoom")
                                        }
                                        if (panChange.x.isFinite() && panChange.y.isFinite()) {
                                            imgPan = clampPan(
                                                imgPan + panChange, baseW, baseH, offX, offY,
                                                containerSize, imgZoom
                                            )
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        last = pressed[0].position
                                        lastPointerId = pressed[0].id
                                        continue
                                    }

                                    val change = pressed[0]

                                    if (mode == 2) {
                                        // 双指抬起一根：剩余单指继续平移（换指时只重置基准不跳变）
                                        if (change.id != lastPointerId) {
                                            lastPointerId = change.id
                                        } else {
                                            imgPan = clampPan(
                                                imgPan + (change.position - last), baseW, baseH, offX, offY,
                                                containerSize, imgZoom
                                            )
                                        }
                                        last = change.position
                                        if (change.positionChanged()) change.consume()
                                        continue
                                    }

                                    if (!pastSlop) {
                                        val moved = (change.position - down.position).getDistance()
                                        if (moved < slopPx) {
                                            if (change.positionChanged()) change.consume()
                                            continue
                                        }
                                        pastSlop = true
                                        // 用按下位置判定操作对象（角/边/整体/框外），避免滑动中重判导致跳变
                                        val l = rect.left * baseW * imgZoom + offX + imgPan.x
                                        val t = rect.top * baseH * imgZoom + offY + imgPan.y
                                        val r = rect.right * baseW * imgZoom + offX + imgPan.x
                                        val b = rect.bottom * baseH * imgZoom + offY + imgPan.y
                                        rectMode = detectHandleMode(
                                            down.position.x, down.position.y, l, t, r, b, handleTouchPx
                                        )
                                        if (rectMode != 0) {
                                            mode = 1
                                        } else {
                                            // 框外拖动：新建选框
                                            mode = 3
                                            savedRect = rect
                                            anchor = Offset(normX(down.position.x), normY(down.position.y))
                                        }
                                        last = change.position
                                    }

                                    if (mode == 1) {
                                        val dxn = (change.position.x - last.x) / (baseW * imgZoom)
                                        val dyn = (change.position.y - last.y) / (baseH * imgZoom)
                                        last = change.position
                                        if (dxn.isFinite() && dyn.isFinite()) {
                                            rect = adjustRect(rect, rectMode, dxn, dyn)
                                        }
                                    } else if (mode == 3) {
                                        val cx = normX(change.position.x)
                                        val cy = normY(change.position.y)
                                        rect = Rect(
                                            min(anchor.x, cx), min(anchor.y, cy),
                                            max(anchor.x, cx), max(anchor.y, cy)
                                        )
                                        last = change.position
                                    }
                                    if (change.positionChanged()) change.consume()
                                }

                                // 新建选框太小则回滚
                                if (mode == 3 && (rect.width < MIN_RECT || rect.height < MIN_RECT)) {
                                    rect = savedRect
                                }
                                // 记录本次手势供双击检测
                                wasTap = !pastSlop && mode != 2
                                lastUpTime = lastUpTimeThis
                                lastUpPos = down.position
                            }
                        }
                ) {
                    val left = rect.left * baseW * imgZoom + offX + imgPan.x
                    val top = rect.top * baseH * imgZoom + offY + imgPan.y
                    val right = rect.right * baseW * imgZoom + offX + imgPan.x
                    val bottom = rect.bottom * baseH * imgZoom + offY + imgPan.y
                    val cw = right - left; val ch = bottom - top

                    if (cw.isFinite() && ch.isFinite() && cw > 0 && ch > 0) {
                        val scrim = Color.Black.copy(alpha = 0.45f)
                        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                        drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, ch))
                        drawRect(scrim, topLeft = Offset(right, top), size = Size(size.width - right, ch))

                        drawRect(Color.White, topLeft = Offset(left, top), size = Size(cw, ch), style = Stroke(width = 2f))

                        val hs = 20f; val ew = 28f; val et = 10f
                        drawRect(Color.White, Offset(left, top), Size(hs, hs))
                        drawRect(Color.White, Offset(right - hs, top), Size(hs, hs))
                        drawRect(Color.White, Offset(left, bottom - hs), Size(hs, hs))
                        drawRect(Color.White, Offset(right - hs, bottom - hs), Size(hs, hs))
                        drawRect(Color.White, Offset((left + right - ew) / 2f, top - et / 2f), Size(ew, et))
                        drawRect(Color.White, Offset((left + right - ew) / 2f, bottom - et / 2f), Size(ew, et))
                        drawRect(Color.White, Offset(left - et / 2f, (top + bottom - ew) / 2f), Size(et, ew))
                        drawRect(Color.White, Offset(right - et / 2f, (top + bottom - ew) / 2f), Size(et, ew))
                    }
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
                "双指缩放/平移 · 双击复位 · 拖动边框调整 · 框外拖动新建选框",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
            )
        }
    }
}

/**
 * 平移边界钳制：图片（当前缩放下）不滑出容器。
 * 缩放后图片大于容器时，边缘可贴齐容器边（允许在容器内平移）；
 * 未放大时图片只在容器（含留白）内小幅移动。
 */
private fun clampPan(
    pan: Offset,
    baseW: Float,
    baseH: Float,
    offX: Float,
    offY: Float,
    container: IntSize,
    zoom: Float
): Offset {
    val w = baseW * zoom
    val h = baseH * zoom
    val minX = minOf(0f, container.width - w) - offX
    val maxX = maxOf(0f, container.width - w) - offX
    val minY = minOf(0f, container.height - h) - offY
    val maxY = maxOf(0f, container.height - h) - offY
    return Offset(pan.x.coerceIn(minX, maxX), pan.y.coerceIn(minY, maxY))
}

private fun adjustRect(rect: Rect, mode: Int, dxn: Float, dyn: Float): Rect {
    if (!dxn.isFinite() || !dyn.isFinite()) return rect
    return when (mode) {
        1 -> {
            var l = rect.left + dxn; var t = rect.top + dyn
            var r = rect.right + dxn; var b = rect.bottom + dyn
            if (l < 0f) { r -= l; l = 0f }
            if (r > 1f) { l -= (r - 1f); r = 1f }
            if (t < 0f) { b -= t; t = 0f }
            if (b > 1f) { t -= (b - 1f); b = 1f }
            Rect(l, t, r, b)
        }
        2 -> Rect(max(0f, min(rect.right - MIN_RECT, rect.left + dxn)), max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)), rect.right, rect.bottom)
        3 -> Rect(rect.left, max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)), min(1f, max(rect.left + MIN_RECT, rect.right + dxn)), rect.bottom)
        4 -> Rect(max(0f, min(rect.right - MIN_RECT, rect.left + dxn)), rect.top, rect.right, min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn)))
        5 -> Rect(rect.left, rect.top, min(1f, max(rect.left + MIN_RECT, rect.right + dxn)), min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn)))
        6 -> Rect(rect.left, max(0f, min(rect.bottom - MIN_RECT, rect.top + dyn)), rect.right, rect.bottom)
        7 -> Rect(rect.left, rect.top, rect.right, min(1f, max(rect.top + MIN_RECT, rect.bottom + dyn)))
        8 -> Rect(max(0f, min(rect.right - MIN_RECT, rect.left + dxn)), rect.top, rect.right, rect.bottom)
        9 -> Rect(rect.left, rect.top, min(1f, max(rect.left + MIN_RECT, rect.right + dxn)), rect.bottom)
        else -> rect
    }
}

private fun detectHandleMode(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float, tol: Float): Int {
    if (!x.isFinite() || !y.isFinite()) return 0
    if (abs(x - left) < tol && abs(y - top) < tol) return 2
    if (abs(x - right) < tol && abs(y - top) < tol) return 3
    if (abs(x - left) < tol && abs(y - bottom) < tol) return 4
    if (abs(x - right) < tol && abs(y - bottom) < tol) return 5
    if (abs(y - top) < tol && x in left..right) return 6
    if (abs(y - bottom) < tol && x in left..right) return 7
    if (abs(x - left) < tol && y in top..bottom) return 8
    if (abs(x - right) < tol && y in top..bottom) return 9
    return if (x in left..right && y in top..bottom) 1 else 0
}

private fun cropBitmap(src: Bitmap, rect: Rect): Bitmap {
    val w = src.width; val h = src.height
    val left = max(0f, min(rect.left, rect.right) * w).toInt()
    val top = max(0f, min(rect.top, rect.bottom) * h).toInt()
    val right = min(w.toFloat(), max(rect.left, rect.right) * w).toInt()
    val bottom = min(h.toFloat(), max(rect.top, rect.bottom) * h).toInt()
    return Bitmap.createBitmap(src, left, top, max(1, right - left), max(1, bottom - top))
}