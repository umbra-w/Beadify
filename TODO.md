# Beadify 迭代计划与待办清单 (TODO List)

## 待实现功能 (Pending Features)

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
  - [ ] **设置/状态模型**：在 `AppViewModel` 及 `ExportDialog` 中增加 `gridInterval: Int`（支持 0=关闭、5、10）及 `gridLineColorHex: String`；
  - [ ] **导出弹窗交互 (`ExportDialog.kt`)**：新增网格辅助线分段选择器（关 / 5格 / 10格）与对比色色卡切换；
  - [ ] **渲染引擎适配 (`GridRenderer.kt`)**：参数化步长与线宽/颜色，导出图纸与 PDF 同步生效；
  - [ ] **实时编辑画布适配 (`EditorScreen.kt`)**：在可视范围内同步绘制选定间隔的彩色区分线，编辑与对照时更清晰。
