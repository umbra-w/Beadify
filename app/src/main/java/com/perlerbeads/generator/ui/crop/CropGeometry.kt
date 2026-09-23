package com.perlerbeads.generator.ui.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 裁剪长宽比预设（对标实体拼豆方板与拼板规格）。 */
enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("自由", null),
    SQUARE("1:1", 1.0f),
    RATIO_1_2("1:2", 0.5f),
    RATIO_2_1("2:1", 2.0f)
}

/**
 * 当切换长宽比时，以当前选框中心（或图像中心）为基准，
 * 计算在图像范围 [0..1, 0..1] 内最大化适配目标比例的 Rect。
 *
 * @param oldRect 当前选框（归一化坐标 0..1）
 * @param targetRatio 目标像素长宽比（width / height），null 表示自由比例
 * @param imgAspect 图像原始宽高比（bmp.width / bmp.height）
 */
fun computeFittedRect(oldRect: Rect, targetRatio: Float?, imgAspect: Float): Rect {
    if (targetRatio == null || !targetRatio.isFinite() || targetRatio <= 0f || imgAspect <= 0f) {
        return oldRect
    }

    // 归一化坐标系下的长宽比：normW / normH = targetRatio / imgAspect
    val normRatio = targetRatio / imgAspect
    val cx = oldRect.center.x.coerceIn(0.1f, 0.9f)
    val cy = oldRect.center.y.coerceIn(0.1f, 0.9f)

    // 在 [0..1, 0..1] 内以 0.9 为上限计算最大尺寸
    val maxBoundW = 0.9f
    val maxBoundH = 0.9f
    val w: Float
    val h: Float
    if (normRatio > (maxBoundW / maxBoundH)) {
        w = maxBoundW
        h = (w / normRatio).coerceAtMost(maxBoundH)
    } else {
        h = maxBoundH
        w = (h * normRatio).coerceAtMost(maxBoundW)
    }

    var l = cx - w / 2f
    var r = cx + w / 2f
    var t = cy - h / 2f
    var b = cy + h / 2f

    if (l < 0f) { r -= l; l = 0f }
    if (r > 1f) { l -= (r - 1f); r = 1f }
    if (t < 0f) { b -= t; t = 0f }
    if (b > 1f) { t -= (b - 1f); b = 1f }

    return Rect(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

/**
 * 拖动调整选框时的几何约束计算。
 * 支持自由比例与固定比例两种模式，保证边界始终位于 [0..1, 0..1]，且固定比例下绝对不变形。
 *
 * @param mode 1:整体平移 2:左上角 3:右上角 4:左下角 5:右下角 6:上边 7:下边 8:左边 9:右边
 */
fun adjustRectWithAspect(
    rect: Rect,
    mode: Int,
    dxn: Float,
    dyn: Float,
    targetRatio: Float?,
    imgAspect: Float,
    minRect: Float = 0.05f
): Rect {
    if (!dxn.isFinite() || !dyn.isFinite()) return rect

    // 模式 1：整体移动（长宽不变，直接平移钳制）
    if (mode == 1) {
        var l = rect.left + dxn; var t = rect.top + dyn
        var r = rect.right + dxn; var b = rect.bottom + dyn
        if (l < 0f) { r -= l; l = 0f }
        if (r > 1f) { l -= (r - 1f); r = 1f }
        if (t < 0f) { b -= t; t = 0f }
        if (b > 1f) { t -= (b - 1f); b = 1f }
        return Rect(l, t, r, b)
    }

    // 自由模式：原样八向独立调整
    if (targetRatio == null || !targetRatio.isFinite() || targetRatio <= 0f || imgAspect <= 0f) {
        return when (mode) {
            2 -> Rect(max(0f, min(rect.right - minRect, rect.left + dxn)), max(0f, min(rect.bottom - minRect, rect.top + dyn)), rect.right, rect.bottom)
            3 -> Rect(rect.left, max(0f, min(rect.bottom - minRect, rect.top + dyn)), min(1f, max(rect.left + minRect, rect.right + dxn)), rect.bottom)
            4 -> Rect(max(0f, min(rect.right - minRect, rect.left + dxn)), rect.top, rect.right, min(1f, max(rect.top + minRect, rect.bottom + dyn)))
            5 -> Rect(rect.left, rect.top, min(1f, max(rect.left + minRect, rect.right + dxn)), min(1f, max(rect.top + minRect, rect.bottom + dyn)))
            6 -> Rect(rect.left, max(0f, min(rect.bottom - minRect, rect.top + dyn)), rect.right, rect.bottom)
            7 -> Rect(rect.left, rect.top, rect.right, min(1f, max(rect.top + minRect, rect.bottom + dyn)))
            8 -> Rect(max(0f, min(rect.right - minRect, rect.left + dxn)), rect.top, rect.right, rect.bottom)
            9 -> Rect(rect.left, rect.top, min(1f, max(rect.left + minRect, rect.right + dxn)), rect.bottom)
            else -> rect
        }
    }

    val normRatio = targetRatio / imgAspect
    val w = rect.width
    val h = rect.height
    val minW = max(minRect, minRect * normRatio)

    return when (mode) {
        2 -> { // 左上角，锚点为右下角 (right, bottom)
            val newW = if (abs(dxn) > abs(dyn * normRatio)) w - dxn else (h - dyn) * normRatio
            val maxW = minOf(rect.right, rect.bottom * normRatio)
            val finalW = newW.coerceIn(minW, max(minW, maxW))
            val finalH = finalW / normRatio
            Rect(rect.right - finalW, rect.bottom - finalH, rect.right, rect.bottom)
        }
        3 -> { // 右上角，锚点为左下角 (left, bottom)
            val newW = if (abs(dxn) > abs(dyn * normRatio)) w + dxn else (h - dyn) * normRatio
            val maxW = minOf(1f - rect.left, rect.bottom * normRatio)
            val finalW = newW.coerceIn(minW, max(minW, maxW))
            val finalH = finalW / normRatio
            Rect(rect.left, rect.bottom - finalH, rect.left + finalW, rect.bottom)
        }
        4 -> { // 左下角，锚点为右上角 (right, top)
            val newW = if (abs(dxn) > abs(dyn * normRatio)) w - dxn else (h + dyn) * normRatio
            val maxW = minOf(rect.right, (1f - rect.top) * normRatio)
            val finalW = newW.coerceIn(minW, max(minW, maxW))
            val finalH = finalW / normRatio
            Rect(rect.right - finalW, rect.top, rect.right, rect.top + finalH)
        }
        5 -> { // 右下角，锚点为左上角 (left, top)
            val newW = if (abs(dxn) > abs(dyn * normRatio)) w + dxn else (h + dyn) * normRatio
            val maxW = minOf(1f - rect.left, (1f - rect.top) * normRatio)
            val finalW = newW.coerceIn(minW, max(minW, maxW))
            val finalH = finalW / normRatio
            Rect(rect.left, rect.top, rect.left + finalW, rect.top + finalH)
        }
        6 -> { // 上边拖动，锚点为 bottom，水平居中缩放
            val newH = (h - dyn).coerceIn(minW / normRatio, rect.bottom)
            val finalW = minOf(1f, newH * normRatio)
            val finalH = finalW / normRatio
            val cx = rect.center.x
            var l = cx - finalW / 2f
            var r = cx + finalW / 2f
            if (l < 0f) { r -= l; l = 0f }
            if (r > 1f) { l -= (r - 1f); r = 1f }
            Rect(l, rect.bottom - finalH, r, rect.bottom)
        }
        7 -> { // 下边拖动，锚点为 top，水平居中缩放
            val newH = (h + dyn).coerceIn(minW / normRatio, 1f - rect.top)
            val finalW = minOf(1f, newH * normRatio)
            val finalH = finalW / normRatio
            val cx = rect.center.x
            var l = cx - finalW / 2f
            var r = cx + finalW / 2f
            if (l < 0f) { r -= l; l = 0f }
            if (r > 1f) { l -= (r - 1f); r = 1f }
            Rect(l, rect.top, r, rect.top + finalH)
        }
        8 -> { // 左边拖动，锚点为 right，垂直居中缩放
            val newW = (w - dxn).coerceIn(minW, rect.right)
            val finalH = minOf(1f, newW / normRatio)
            val finalW = finalH * normRatio
            val cy = rect.center.y
            var t = cy - finalH / 2f
            var b = cy + finalH / 2f
            if (t < 0f) { b -= t; t = 0f }
            if (b > 1f) { t -= (b - 1f); b = 1f }
            Rect(rect.right - finalW, t, rect.right, b)
        }
        9 -> { // 右边拖动，锚点为 left，垂直居中缩放
            val newW = (w + dxn).coerceIn(minW, 1f - rect.left)
            val finalH = minOf(1f, newW / normRatio)
            val finalW = finalH * normRatio
            val cy = rect.center.y
            var t = cy - finalH / 2f
            var b = cy + finalH / 2f
            if (t < 0f) { b -= t; t = 0f }
            if (b > 1f) { t -= (b - 1f); b = 1f }
            Rect(rect.left, t, rect.left + finalW, b)
        }
        else -> rect
    }
}

/**
 * 框外拖动新建选框时的几何计算。
 */
fun createNewSelectionWithAspect(
    anchor: Offset,
    cur: Offset,
    targetRatio: Float?,
    imgAspect: Float
): Rect {
    if (targetRatio == null || !targetRatio.isFinite() || targetRatio <= 0f || imgAspect <= 0f) {
        return Rect(
            min(anchor.x, cur.x), min(anchor.y, cur.y),
            max(anchor.x, cur.x), max(anchor.y, cur.y)
        )
    }

    val normRatio = targetRatio / imgAspect
    val rawW = abs(cur.x - anchor.x)
    val rawH = abs(cur.y - anchor.y)
    val dirX = if (cur.x >= anchor.x) 1f else -1f
    val dirY = if (cur.y >= anchor.y) 1f else -1f

    val candidateW = maxOf(rawW, rawH * normRatio)
    val maxAllowedW = if (dirX > 0) 1f - anchor.x else anchor.x
    val maxAllowedH = if (dirY > 0) 1f - anchor.y else anchor.y
    val finalW = minOf(candidateW, maxAllowedW, maxAllowedH * normRatio)
    val finalH = finalW / normRatio

    val endX = anchor.x + dirX * finalW
    val endY = anchor.y + dirY * finalH

    return Rect(
        min(anchor.x, endX), min(anchor.y, endY),
        max(anchor.x, endX), max(anchor.y, endY)
    )
}
