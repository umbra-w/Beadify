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
