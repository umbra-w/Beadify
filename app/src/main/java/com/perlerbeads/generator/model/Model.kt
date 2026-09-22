package com.perlerbeads.generator.model

/** 拼豆色号系统（店家）。 */
enum class ColorSystem(val key: String) {
    MARD("MARD"),
    COCO("COCO"),
    MAN_MAN("漫漫"),
    PAN_PAN("盼盼"),
    MI_XIAO_WO("咪小窝");

    companion object {
        fun fromKey(k: String): ColorSystem = when (k) {
            "COCO" -> COCO
            "漫漫" -> MAN_MAN
            "盼盼" -> PAN_PAN
            "咪小窝" -> MI_XIAO_WO
            else -> MARD
        }
    }
}

/** 像素化模式：卡通（主导色）/ 真实（平均色）。 */
enum class PixelationMode {
    DOMINANT, AVERAGE
}

/** 画板形状。 */
enum class GridShape {
    SQUARE, CIRCLE
}

/** 拼豆实物规格（用于 1:1 实物打印与模板对齐）。 */
enum class BeadPitch(val mm: Float, val label: String) {
    MINI_2_6(2.6f, "2.6mm 迷你豆"),
    STANDARD_5_0(5.0f, "5.0mm 标准豆");

    val cellPt: Float get() = mm * 72f / 25.4f

    companion object {
        fun fromMm(mm: Float): BeadPitch = if (mm < 3.8f) MINI_2_6 else STANDARD_5_0
    }
}

data class RgbColor(val r: Int, val g: Int, val b: Int)

/**
 * 色板色。
 * @param key 当前色号系统下的显示色号（MARD 为 "A01" 等；内部全量色板时 key=hex）
 * @param hex 标准 hex（大写），数据主键
 */
data class PaletteColor(
    val key: String,
    val hex: String,
    val rgb: RgbColor
)

/** 网格单元。@param colorHex hex（大写）。 */
data class MappedPixel(
    val key: String,
    val colorHex: String,
    val isExternal: Boolean = false
)

/** 透明/擦除键与数据，与网页版 pixelEditingUtils 一致。 */
const val TRANSPARENT_KEY = "ERASE"
val transparentColorData = MappedPixel(key = TRANSPARENT_KEY, colorHex = "#FFFFFF", isExternal = true)

/** 像素网格状态。 */
class GridData(
    val n: Int,
    val m: Int,
    var cells: Array<Array<MappedPixel>>,
    /** 初始网格 hex 集合，供颜色排除重映射使用。 */
    val initialColorKeys: Set<String>,
    /** 画板形状。 */
    val shape: GridShape = GridShape.SQUARE
) {
    fun deepCopyCells(): Array<Array<MappedPixel>> =
        Array(m) { r -> Array(n) { c -> cells[r][c] } }
}

/**
 * 圆形画板几何（格子坐标系，单位=格）。
 * 与 GridRenderer 的像素级圆形裁剪语义一致：
 * 圆内 = 会被拼豆/导出/统计的区域；圆外 = 仅显示、不计数、不可编辑。
 */
data class CircleGeometry(
    val centerX: Float,
    val centerY: Float,
    val radius: Float
) {
    /** 格子中心是否在圆内。 */
    fun contains(row: Int, col: Int): Boolean {
        val dx = col + 0.5f - centerX
        val dy = row + 0.5f - centerY
        return dx * dx + dy * dy <= radius * radius
    }
}

/**
 * 由网格尺寸与取景偏移计算圆形几何。
 * 对应 GridRenderer 中 shiftX = -(offsetX * (gridW - outSize)) 的像素裁剪：
 * offsetX=0 时圆覆盖图案左/上部，offsetX=1 时覆盖右/下部，0.5 居中。
 */
fun circleGeometry(n: Int, m: Int, offsetX: Float, offsetY: Float): CircleGeometry {
    val outCells = minOf(n, m)
    val cx = outCells / 2f + offsetX.coerceIn(0f, 1f) * (n - outCells)
    val cy = outCells / 2f + offsetY.coerceIn(0f, 1f) * (m - outCells)
    return CircleGeometry(cx, cy, outCells / 2f)
}

/**
 * 由编辑器「固定圆框 + 图案变换」推导网格坐标系下的圆形几何。
 *
 * 编辑页圆形模式下：圆框固定在屏幕上（中心 F、半径 R 像素），图案以
 * cellPx（zoom=1 时每格像素）* zoom 的比例在框后缩放平移。图案原点
 * O = F - anchor中心*cs + offset（anchor 即初始圆心，始终锚定框中心缩放），
 * 由此框内图案的圆在网格坐标下为：
 *   中心 = anchor中心 - offset/cs，半径 = R/cs
 *
 * @param anchor 初始圆形几何（zoom=1、offset=0 时与框重合）
 * @param zoom 图案缩放（1 = 圆区域恰好填满框）
 * @param offset 图案平移（像素）
 * @param frameRadiusPx 圆框半径（屏幕像素）
 * @param baseCellPx zoom=1 时每格像素 = 2*frameRadiusPx / min(n,m)
 */
fun derivedCircleGeometry(
    anchor: CircleGeometry,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    frameRadiusPx: Float,
    baseCellPx: Float
): CircleGeometry {
    val cs = baseCellPx * zoom
    return CircleGeometry(
        anchor.centerX - offsetX / cs,
        anchor.centerY - offsetY / cs,
        frameRadiusPx / cs
    )
}
