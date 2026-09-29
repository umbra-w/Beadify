package com.perlerbeads.generator.model

/**
 * 内置像素字体。
 * 字体 TTF 文件放在 assets/fonts/ 目录，通过 Typeface.createFromAsset 加载。
 *
 * - Ark Pixel 12px (TakWolf): SIL OFL 1.1, 中英文像素字体
 * - Zpix (SolidZORO): SIL OFL 1.1, 复古中文像素字体
 * - Press Start 2P (Google Fonts): SIL OFL 1.1, 8px 街机英文字体
 */
enum class PixelFont(
    val displayName: String,
    val assetPath: String,
    val description: String
) {
    SYSTEM("系统默认", "", "设备默认字体"),
    ARK_PIXEL_12("方舟像素 12px", "fonts/ark-pixel-12px.ttf", "中英日韩全覆盖像素字体"),
    FUSION_PIXEL_12("缝合像素 12px", "fonts/fusion-pixel-12px.ttf", "中英日韩像素字体 (SIL OFL)"),
    PRESS_START_2P("Press Start 2P", "fonts/PressStart2P-Regular.ttf", "8px 街机风格英文字体");

    /** 是否为系统默认（不需要加载 asset）。 */
    val isSystem: Boolean get() = this == SYSTEM
}
