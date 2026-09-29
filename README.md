# Beadify - 智能拼豆图纸生成器 (Android)

原生 Android 拼豆与像素艺术设计工坊，基于 Kotlin + Jetpack Compose 构建。

[English](./README_EN.md) · [简体中文](./README.md) · [测试规范文档](./docs/TEST_SPECIFICATION.md) · [全量测试报告](./docs/TEST_REPORT.md) · [算法基准评测](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## 软件简介

Beadify 是一款专为拼豆（Perler / Fuse / Hama / Artkal Beads）手作玩家、名字牌制作者与像素艺术爱好者打造的 Android 工具软件。

传统网页端工具通常存在大图易崩溃、缺少中文像素字形、色彩映射偏移以及打印尺寸不准等问题。Beadify 采用原生 Android 架构与 Oklab 均匀感知色彩空间算法，实现了从「图像与文字输入 $\rightarrow$ 智能色彩量化与描边 $\rightarrow$ 画布精修与分板施工 $\rightarrow$ 1:1 毫米级印刷导出」的完整闭环。

---

## 核心功能

### 1. 图像转拼豆与像素化引擎
- **双算法模式**：
  - **卡通模式（Dominant Color）**：基于局部色彩直方图提取主导色，轮廓清晰锐利，适合二次元插画与动漫线稿；
  - **写实模式（Average Color）**：空间加权颜色平均采样，色彩过渡平滑细腻；
- **智能裁剪与比例预设**：支持自由裁剪，内置 1:1（正方形）、1:2（垂直长方）、2:1（水平长方）预设比例，支持缩放时锁定比例，方便匹配常用底板规格（如 28×28 标板）；
- **杂色孤岛自动清理**：基于 8 邻域连通分量分析算法，自动识别并过滤 1~2 粒的离散杂色飞点，大幅降低实体手作拼装难度；
- **色彩控制与优化**：
  - 支持 Floyd-Steinberg 误差扩散抖动，呈现细腻色彩阶调；
  - 支持相似色合并阈值调节，自动归拢相近颜色，减少碎色种类与采购成本。

### 2. 文字拼豆工作台
- **文本排版与字号高度**：支持输入中英文文本与数字符号，可自由选择 24 行、32 行、50 行、72 行等网格字号高度；
- **内置开源像素字体**：内置 3 款遵循 SIL OFL 1.1 开源协议的经典像素字体，并支持系统默认字体：
  - **方舟像素 12px**（Ark Pixel，TakWolf）：经典中文复古点阵风格；
  - **缝合像素 12px**（Fusion Pixel，TakWolf）：全 CJK 字库覆盖的像素字体；
  - **Press Start 2P**（Google Fonts）：8px 街机风英文字体；
- **多样化色彩模式**：
  - **单色模式**：快速生成纯色文本图纸；
  - **渐变模式**：支持选取起始色与结束色，在 Oklab 空间进行平滑线性插值，并自动对齐物理色板最近邻色号；
  - **彩虹模式**：沿字符列向自动循环色相环分布；
- **文字特效图层**：
  - **文字描边**：采用 8 邻域形态学膨胀算法提取外边框，支持自选描边色号；
  - **文字投影**：独立坐标层平移，自动排除文字重叠区域，支持自选阴影色号；
  - 合成图层优先级：文字笔画 > 描边 > 投影 > 背景；
- **背景选项**：支持背景填白或纯文字透明底自由切换。

### 3. 画布手工精修与分板施工
- **画板编辑工具箱**：
  - 手绘画笔涂色、橡皮擦除；
  - 孤立飞点一键清理；
  - 画布视口缩放、双指平移与一键复位居中；
- **分板施工模式（Board Work）**：
  - 支持 28×28（标准大板）与 16×16（迷你板）规格自由切换；
  - 十字网格十字标尺定位，清晰标注当前子板行号与列号；
  - 逐格打勾施工标记，防止实体拼装插豆串行；
- **工程存档管理**：支持图纸工程多档本地保存、列表缩略图展示、恢复编辑与删除。

### 4. 品牌色板与个人豆仓管理
- **色卡数据库**：内置 290+ 色完整标准色卡，覆盖国际与国内主流拼豆品牌：
  - Artkal S（软豆 235 色）、Artkal C（硬豆 174 色）；
  - Perler（欧美经典标准）；
  - MARD、COCO、漫漫、盼盼、咪小窝等国内品牌；
- **个人库存豆仓**：支持标记自备颜色，用量统计表红色高亮标记缺货品种，画布缺色标红警示。

### 5. 1:1 毫米级物理尺寸与全格式导出
- **高清矢量 PDF 打印**：
  - 采用 300 DPI 矢量级绘图输出，色号与格线放大不失真；
  - 精确支持 **2.6mm（迷你豆）** 与 **5.0mm（标准豆）** 真实物理孔距 1:1 打印，打印后可直接将透明插豆板覆盖在图纸上方施工；
- **网格辅助线定制**：支持设置每 5 格或每 10 格加粗参考线，并可自定义参考线颜色；
- **多格式文件导出**：
  - 高清图纸 PNG（带坐标系、分界线与色号标记）；
  - 自适应多列颜色用量统计表 PNG（动态根据图纸宽高分列排版，告别单列无限纵向拉长）；
  - 采购清单 CSV（包含品牌色号、HEX 代码与用量统计）；
  - 网格图纸 CSV（用于电脑与网页工具互通）。

---

## 架构与技术栈

项目采用 Clean Architecture 分层架构与 MVI/MVVM 状态流：

```
perler-beads-android/
├── app/
│   ├── src/main/
│   │   ├── assets/fonts/       # 内置开源像素字体 (Ark Pixel, Fusion Pixel, Press Start 2P)
│   │   ├── java/com/perlerbeads/generator/
│   │   │   ├── algorithm/     # 核心算法（Oklab转换、像素化量化、TextBeads形态学膨胀、抖动、孤岛消除）
│   │   │   ├── data/          # 数据层（色板数据库、豆仓持久化、工程本地存储）
│   │   │   ├── export/        # 导出引擎（300 DPI 矢量 PDF、多格式 CSV 与 PNG 渲染）
│   │   │   ├── model/         # 数据模型与不可变状态
│   │   │   ├── navigation/    # Compose Navigation 路由导航
│   │   │   └── ui/            # UI 层（Material 3、首页、裁剪、文字拼豆、编辑器、色板、分板）
│   └── src/test/              # JVM 白盒单元测试套件（150 项测试）
├── docs/                      # 测试规范、测试报告与基准评测报告
├── release/                   # 发布说明与发行 APK 产物
└── scripts/                   # 基于 android-testing-skills 的真机 ADB 自动化端到端测试套件
```

- **语言与框架**：Kotlin 2.0+、Jetpack Compose、Material Design 3
- **异步与响应式**：Kotlin Coroutines、StateFlow
- **系统适配**：Edge-to-Edge 全屏显示适配、AndroidX Photo Picker

---

## 质量保证与测试体系

### 1. JVM 白盒单元测试
覆盖色彩转换往返精度（Oklab $\leftrightarrow$ sRGB）、TextBeads 渐变插值与形态学膨胀边界、PDF 导出布局、切片算法等关键模块：
- **测试数量**：150 个用例全部通过（0 failure）
- **执行命令**：`./gradlew testReleaseUnitTest --offline`

### 2. 真机自动化端到端测试（ADB E2E）
基于 `android-testing-skills` 规范编写，在物理真机（Xiaomi Redmi 13C，Android 14）上自动化执行：
- **Suite 1**：主链路全流程（选图 $\rightarrow$ 自由/1:1/1:2/2:1 比例预设切换 $\rightarrow$ 参数配置 $\rightarrow$ 50×50 生成）
- **Suite 2**：编辑器画布工具（画笔手绘、橡皮擦除、飞点清理、视口重置）
- **Suite 3**：分板施工模式（16×16 / 28×28 规格切换、打勾施工标记）
- **Suite 4**：项目存档与恢复（本地工程保存、列表读取、恢复编辑、删除工程）
- **Suite 5**：文字拼豆（自定义输入、像素字体切换、渐变色彩、描边开启、生成切入）
- **Suite 6**：色板与豆仓管理（品牌切换、库存管理、原子化保存）
- **Suite 7**：多格式导出（300 DPI PDF、图纸与采购清单 CSV、带 Key PNG）
- **Phase 8**：稳定性与能耗审计（Logcat 0 Crash、0 ANR、内存占用正常）

---

## 快速开始

### 环境要求
- JDK 17+
- Android SDK（Compile SDK 35 / Min SDK 24 / Target SDK 35）
- Gradle 8.13+

### 编译与运行

```bash
# 克隆仓库
git clone https://github.com/umbra-w/Beadify.git
cd Beadify

# 运行单元测试
./gradlew testReleaseUnitTest

# 编译 Release APK
./gradlew assembleRelease

# 运行真机端到端自动化测试（需连接已开启 USB 调试的 Android 设备）
python scripts/run_full_feature_e2e.py
```

---

## 开源协议与致谢

- 本项目基于 [MIT License](LICENSE) 开源。
- 内置字体资产遵循 [SIL Open Font License 1.1](https://openfontlicense.org/) 授权：
  - **方舟像素字体 (Ark Pixel Font)**：由 [TakWolf](https://github.com/TakWolf/ark-pixel-font) 创作。
  - **缝合像素字体 (Fusion Pixel Font)**：由 [TakWolf](https://github.com/TakWolf/fusion-pixel-font) 创作。
  - **Press Start 2P**：由 CodeMan38 创作，托管于 [Google Fonts](https://fonts.google.com/specimen/Press+Start+2P)。
- 色彩空间算法参考了 Björn Ottosson 的 [Oklab](https://bottosson.github.io/posts/oklab/) 研究成果。