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
    val initialColorKeys: Set<String>
) {
    fun deepCopyCells(): Array<Array<MappedPixel>> =
        Array(m) { r -> Array(n) { c -> cells[r][c] } }
}
