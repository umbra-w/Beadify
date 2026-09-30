# Beadify 迭代计划与待办清单 (TODO List)

## 待实现功能与优化 (Pending Features & Enhancements)

---

### 1. 高对比度网格区分线设置 (Grid Interval & Color Accent Lines)
- **需求背景**：
  - 在拼豆图纸（尤其大尺寸图纸）制作与实体拼豆对照过程中，用户需要依靠规律的加粗/彩色分界线来快速数格子（十字绣与拼豆经典辅助线）。
  - 网页版原型（`perler-beads-ai`）已具备此功能，支持用户按 5 格或 10 格划分区块，并可选高对比度线条颜色。
- **对标实现 (Web 原型参考)**：
  - `src/types/downloadTypes.ts` 中的 `gridInterval` 与 `gridLineColor`；
  - `src/components/DownloadSettingsModal.tsx` 中提供的 5/10/N 格滑动调节与 6 种预设高对比色（深灰 `#555555`、红 `#FF0000`、蓝 `#0000FF`、绿 `#008000`、紫 `#800080`、橙 `#FFA500`）；
  - `src/utils/imageDownloader.ts` 中的跨行跨列步进绘制逻辑。
- **Android 端现状分析**：
  - `GridRenderer.kt`：目前写死了 `step 10` 和固定深灰色（`0xCC333333`），无法在导出时自选 5 格、10 格或关闭，也无法选色；
  - `EditorScreen.kt`：实时画板目前仅绘制全局 1px 细线，未实现 5/10 格区分线的动态渲染。
- **技术落地方案设计**：
  - [x] **设置/状态模型**：在 `AppViewModel`、`SettingsStore` 及 `ExportDialog` 中增加 `gridInterval: Int`（支持 0=关闭、5、10）及 `gridLineColorHex: String`；
  - [x] **导出弹窗交互 (`ExportDialog.kt`)**：新增网格辅助线分段选择器（关 / 5格 / 10格）与 6 色高对比色卡切换；
  - [x] **渲染引擎适配 (`GridRenderer.kt`)**：参数化步长与线宽/颜色，导出图纸与预览同步生效；
  - [x] **实时编辑画布适配 (`EditorScreen.kt`)**：在可视范围内同步绘制选定间隔的彩色区分线，编辑与对照时更清晰。

---

### 2. 相似颜色合并阈值调节 (Color Similarity Merge Threshold)
- **需求背景**：
  - 网页端（`perler-beads-ai`）提供了「颜色合并阈值 (0-100)」设置项，能够有效减少拼豆成品中微小的相近色种。
  - **与现有色数控制 (Max Colors) 的互补价值**：
    - `Max Colors`（中位切割）是全局硬性卡死“最多 N 色”，适合严格限制总采购色种的场景；
    - `Similarity Threshold`（相似合并）则是柔性合并：画面中如果存在色差小于阈值的微差色（如 3 种极近的浅肤色或阴影灰），自动将低频色并入高频主色，无需强制卡死全局总色数，保留自然色彩分布的同时大幅消除多余散色，非常贴合手作采购需求。
- **对标实现 (Web 原型参考)**：
  - `src/app/page.tsx` 中的全局颜色合并逻辑：
    1. 统计当前图纸全部颜色使用频次并降序排列；
    2. 从高频色向低频色遍历，计算颜色距离；
    3. 若两色距离低于设定阈值，则将低频色折叠替换为高频色，并将其标记为已替换。
- **Android 端落地方案设计**：
  - [x] **数据与算法扩展 (`ColorQuantizer.kt` / `Pixelation.kt`)**：
    - 新增基于 Oklab 感知色差的频次优先软性合并函数 `mergeSimilarColors(grid, palette, threshold)`；
    - 按像素出现频次排序，阈值范围内自动合并低频色到高频色，并编写完备单测 `ColorSimilarityMergeTest`；
  - [x] **设置页交互 (`SettingsScreen.kt`)**：
    - 在「像素化设置」中新增「相似颜色合并」滑块（0..60），提供动态推荐值提示（0 关闭、15 轻度合并推荐、30 适度合并、强力合并）；
  - [x] **性能保护**：在低分辨率网格完成映射后进行查表合并，纯 Kotlin 零装箱损耗，耗时 < 3ms，零卡顿。

---

### 3. 原生沉浸式边缘到边缘 (Edge-to-Edge) 与状态栏泛白修复
- **问题现象与原因分析**：
  - 现象：在 Android 10~14 真机（如 Xiaomi Redmi 13C / MIUI / HyperOS）上启动应用时，顶部系统状态栏出现一层发白发蒙的半透明蒙层（Scrim），无法实现现代原生 App 那样与界面背景浑然一体的通透纯净效果。
  - 原因：
    1. `MainActivity.kt` 尚未引入 AndroidX 标准的 `enableEdgeToEdge()` API；
    2. `themes.xml` 使用了旧版 `android:Theme.Material.Light.NoActionBar`，未启用现代 Material 3 Window 标志，系统在未知状态栏文字颜色安全性时自动叠加了白底保护蒙层；
    3. 页面未充分适配 `WindowInsets` / `statusBarsPadding()`。
- **技术落地方案设计**：
  - [x] **Activity 接入现代沉浸式**：在 `MainActivity.onCreate()` 中调用 `enableEdgeToEdge()`；
  - [x] **状态栏图标亮暗自适应**：
    - 亮色主题下配置状态栏图标为深色（Dark Icons），背景完全透明（零白色 Scrim）；
    - 暗色主题下配置状态栏图标为浅色（Light Icons）；
  - [x] **顶栏与底栏安全边距处理 (`WindowInsets`)**：
    - 针对 `HomeScreen`、`SettingsScreen`、`EditorScreen`、`CropScreen` 统一适配 `statusBarsPadding()` 与 `navigationBarsPadding()`，彻底解决手势横条遮挡按钮与状态栏遮罩问题。

---

### 4. 点击色号图纸联动高亮与施工辅助 (Interactive Bead Highlighting & Assembly Mode)
- **需求背景**：
  - 用户在对照实体拼豆施工时，最痛的场景是在密密麻麻的百格图纸中搜寻某种特定颜色（如“在 50×50 的画布里插完所有 M02 浅粉色”）。
  - 在查阅/施工模式下点击底部用料清单中的某个色号，画布上属于该色号的所有格子实时高亮显眼显示（加金色/白色呼吸边框），其余颜色半透明暗化，拼装效率提升数倍。
- **对标实现与参考链接**：
  - [`LunarXuan/Pindo`](https://github.com/LunarXuan/Pindo)（400+⭐，参考其色号交互高亮与用量联动逻辑）；
  - [`image-to-bead-pattern`](https://github.com/cocobanyuexing/image-to-bead-pattern)（点击色卡高亮图案中对应颜色与定位辅助）。
- **Android 端技术落地方案设计**：
  - [ ] **状态管理 (`AppViewModel.kt`)**：新增 `highlightColorKey: StateFlow<String?>`；
  - [ ] **画布渲染适配 (`GridRenderer.kt` / `BoardWorkScreen.kt`)**：
    - 当激活高亮色时，非目标格子降低透明度（`alpha = 0.25f`），目标色保持 100% 不透明并绘制对比描边或外发光；
  - [ ] **用量统计组件联动**：支持点击色卡切换高亮，再次点击或点击空白处取消高亮。

---

### 5. AI 像素图自动网格探测与抗锯齿硬化算法 (AI Pixel Grid Snapping & Anti-Aliasing Removal)
- **需求背景**：
  - 许多用户直接上传 AI 生成（如 Midjourney、DALL-E、小红书）或缩放后的像素插画。这类图像通常经过了插值放大，存在半像素相位错位、边缘半透明抗锯齿模糊与微小杂色，直接采样容易导致图纸发糊失真。
- **对标实现与参考链接**：
  - [`theamusing/perfectPixel`](https://github.com/theamusing/perfectPixel)（2100+⭐，基于自相关与梯度的自适应像素网格探测）；
  - [`Hugo-Dz/spritefusion-pixel-snapper`](https://github.com/Hugo-Dz/spritefusion-pixel-snapper)（3200+⭐，AI 像素画自动网格对齐工具）；
  - [`HappyOnigiri/PixelRefiner`](https://github.com/HappyOnigiri/PixelRefiner)（250+⭐，抗锯齿消除与最佳网格尺寸探测算法）。
- **Android 端技术落地方案设计**：
  - [ ] **自适应网格步长与相位探测 (`PixelSnapper.kt`)**：
    - 通过一维差分梯度自相关与水平/垂直投影分析，自动推算原图真实的逻辑像素块倍数（cell size）与亚像素相位偏移量（phase offset $x_0, y_0$）；
  - [ ] **核心采样与去抗锯齿**：
    - 跳过边缘插值模糊区域，直接在逻辑网格中心采样，恢复刀刻般纯净的硬边缘点阵；
  - [ ] **导入面板接入**：在图片裁剪/导入环节提供「AI 像素画修复 / 网格硬化」开关。

---

### 6. 边缘防灰光晕与 Alpha 权重反污染 (Anti-Halo Clean Edges Compositing)
- **需求背景**：
  - 在处理半透明背景 PNG 或抠图边缘时，深色主体边缘若与白底或透明底平权下采样，极易在物体边缘产生一层脏灰色的过渡杂色豆圈。
- **对标实现与参考链接**：
  - [`Chen-Y-Dan/Bead-Studio`](https://github.com/Chen-Y-Dan/Bead-Studio)（参考其 clean edges / no gray halo 边缘保护方案）。
- **Android 端技术落地方案设计**：
  - [ ] **Alpha 加权与预乘去除 (`Pixelation.kt`)**：
    - 下采样求平均色时引入 Alpha 通道加权（$R_{avg} = \frac{\sum r \cdot \alpha}{\sum \alpha}$），仅统计前景有效像素，防止浅色/透明底渗透入轮廓线；
  - [ ] **轮廓外圈二值化门限保护**：确保外围轮廓紧贴主体，零外圈灰度噪点。

---

### 7. 感知空间蛇形误差扩散抖动 (Perceptual Serpentine Dithering)
- **需求背景**：
  - 目前 Beadify 的 Floyd-Steinberg 抖动在 sRGB 空间单向扩散，容易在暗部产生方向性条纹（directional worm artifacts）与暗部噪点。
- **对标实现与参考链接**：
  - [`zwhy149/bead-grid-studio`](https://github.com/zwhy149/bead-grid-studio)（参考 `packages/core` 中的感知空间抖动与扩散权重）；
  - [`yourlin/Fusible-Beads-Studio`](https://github.com/yourlin/Fusible-Beads-Studio)（基于 CIEDE2000 / Oklab 的客户端平滑抖动）。
- **Android 端技术落地方案设计**：
  - [ ] **蛇形扫描扩散（Serpentine Scan）**：偶数行从左往右、奇数行从右往左交替扩散误差，彻底打散方向性虫纹；
  - [ ] **Oklab 感知空间误差扩散**：在均匀的感知空间累积误差，使渐变过渡更加柔和自然；
  - [ ] **Atkinson 抖动模式可选**：支持 Atkinson 抖动（只扩散 75% 误差），更加突出高对比边缘，画面比经典 Floyd 更加清爽干净。

---

### 8. 跨品牌色卡图纸一键无损转换 (Cross-Brand Palette Recolor with Contrast Preservation)
- **需求背景**：
  - 玩家手中持有的品牌豆（如 MARD）与所获得图纸标注的品牌（如 Perler 或 Artkal）往往不一致。当前需要手动一个个替换，体验繁琐且容易配错。
- **对标实现与参考链接**：
  - [`liberatrrot/BeadPalette`](https://github.com/liberatrrot/BeadPalette)（统一感知色彩空间跨品牌重映射与受控量化）。
- **Android 端技术落地方案设计**：
  - [ ] **感知空间全局重映射 (`Remap.kt`)**：
    - 在 Oklab 空间计算两品牌之间的最佳最小 $\Delta E$ 对齐映射表；
  - [ ] **结构对比度保留防碰撞**：
    - 针对原图中邻近但不同的两色，避免在目标色卡中退化合并为同一颜色；
  - [ ] **编辑页与项目页提供「一键转换为其他品牌」功能**。

---

### 9. 实体底板施工图标注与 1:1 比例 PDF 导出 (Physical Board Layout & 1:1 Pegboard PDF)
- **需求背景**：
  - 实体拼豆使用的拼豆板通常是标准尺寸（大方板 28×28、小方板 14×14、微豆板 50×50）。用户打印出来后，常希望能够将透明拼豆板直接叠在纸上 1:1 穿豆。
- **对标实现与参考链接**：
  - [`zwhy149/bead-grid-studio`](https://github.com/zwhy149/bead-grid-studio)（底板网格划分、边缘拼接导向与标准 PDF 施工图生成）。
- **Android 端技术落地方案设计**：
  - [ ] **拼豆底板阵列拼接导向**：为大图提供多块拼豆板编号（如 A1、A2、B1、B2）拼接示意图与边界对齐线；
  - [ ] **1:1 毫米级精确打印**：PDF 导出时根据拼豆物理直径（5.0mm 或 2.6mm）设置毫米级单元格尺寸，支持直接贴板施工。

---

### 10. 两阶段混合聚类：Median Cut + K-Means++ 迭代微调 (Hybrid Color Quantization)
- **需求背景**：
  - 当前纯中位切割（Median Cut）切分方式为正交轴分割，在用户设置极低色数（如 $\le 16$ 色）时，聚类中心容易受边缘孤立点偏离；
  - 通过两阶段混合算法（中位切割生成初始质心 + 3~5 次 K-Means++ 迭代微调），可大幅提升极低色数限制下的色彩保真度。
- **对标实现与参考链接**：
  - [`gametorch/image_to_pixel_art_wasm`](https://github.com/gametorch/image_to_pixel_art_wasm)（WASM 端 K-Means 色彩量化聚类）；
  - [`liberatrrot/BeadPalette`](https://github.com/liberatrrot/BeadPalette)（受控两阶段色彩量化引擎）。
- **Android 端技术落地方案设计**：
  - [ ] **算法扩展 (`ColorQuantizer.kt`)**：以 Median Cut 为初值运行少量迭代的 K-Means++ 聚类微调质心；
  - [ ] **移动端性能优化**：单次聚类耗时控制在 10ms 以内，保障实时滑动交互零卡顿。

---

### 11. 权威国内拼豆色卡数据扩充与校准 (Domestic Brand Palette Expansion & Calibration)
- **需求背景**：
  - 补充和校准国内主流拼豆品牌色号（MARD 291 全色系、Artkal 418、COCO 291、盼盼、漫漫、咪小窝），修复上游错码与缺失色号。
- **对标实现与参考链接**：
  - [`HansBug/pindou-color-data`](https://github.com/HansBug/pindou-color-data)（国内拼豆色卡清洗数据仓库，交叉比对多方上游消除错码与不可辨识色号）。
- **Android 端技术落地方案设计**：
  - [ ] **色板数据同步 (`assets/colors/`)**：基于清洗校准后的 JSON 补充 MARD 291 与 COCO 等常用品牌色卡；
  - [ ] **色号映射校准 (`ColorSystemMapping.kt`)**：更新品牌间交叉映射对照表。

---

### 12. 生活实物场景与 3D 质感海报渲染 (Lifestyle Mockup & 3D Melt Preview)
- **需求背景**：
  - 玩家制作完拼豆后，非常热衷于在小红书、朋友圈等平台分享。相比单纯的平面图纸，生成逼真的“实物钥匙扣效果”、“手托效果”、“冰箱贴效果”或带有圆柱穿孔与微熨烫质感的 3D 效果海报，能大幅提升作品的传播力和手作成就感。
- **对标实现与参考链接**：
  - [`TrinityYao54/photo-to-pindou`](https://github.com/TrinityYao54/photo-to-pindou)（生活场景实物图生成：手持挂件、钥匙扣、冰箱贴实物光影与厚度）；
  - [`m00lee/PealerBeads`](https://github.com/m00lee/PealerBeads) / [`knight02-bit/PinDouGenerator`](https://github.com/knight02-bit/PinDouGenerator)（3D 拼豆视角与熨烫融合效果预览）。
- **Android 端技术落地方案设计**：
  - [ ] **分享海报生成器 (`MockupRenderer.kt`)**：
    - 支持合成“塑料质感圆柱豆（带微倒角与中心孔洞）+ 投影”的实物渲染海报；
    - 提供钥匙扣挂绳与手托背景等分享模板。

---

### 13. 开箱即用离线精选图纸库 (Curated Offline Pattern Library)
- **需求背景**：
  - 新手用户刚下载 App 时，可能手头没有合适的素材图。提供内置分类图纸库（如宝可梦、马里奥、可爱小动物、像素表情、节日主题），可以让用户开箱即拼。
- **对标实现与参考链接**：
  - [`xuange6610/PindouAI`](https://github.com/xuange6610/PindouAI)（内置 1200+ 离线图纸库，开箱即选即拼）。
- **Android 端技术落地方案设计**：
  - [ ] **本地精选图纸打包 (`assets/patterns/`)**：收录 50~100 张经典公有领域/开源像素图纸并内置元数据；
  - [ ] **图纸发现页 (`PatternLibraryScreen.kt`)**：支持按分类浏览、一键导入画板进行二次创作或直接施工。


