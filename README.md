# Beadify - 智能拼豆图纸生成器 (Android)

基于 Kotlin + Jetpack Compose 的原生拼豆与像素艺术设计工坊。

[English](./README_EN.md) · [简体中文](./README.md) · [测试规范文档](./docs/TEST_SPECIFICATION.md) · [全量测试报告](./docs/TEST_REPORT.md) · [算法基准评测](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## 关于 Beadify

Beadify 是一款专为拼豆（Perler / Fuse / Hama / Artkal Beads）手作玩家与像素艺术爱好者打造的 Android 应用。

传统拼豆图纸工具受限于 Web 端的内存崩溃、单调的色板匹配以及排版拉长等问题。Beadify 采用原生 Kotlin 协同硬件加速构建核心算法，在真机上实现 1200 万像素大图 0.71 秒瞬时转换、7 大品牌色系互转、个人豆仓库存管理与 Oklab 缺色智能平替，以及 1:1 毫米级物理尺寸打印与多板切片聚光灯辅助。

---

## 核心特性

### 1. 极致性能与智能像素化引擎
- **双模式像素量化**：
  - 卡通模式（Dominant Color）：基于局部色彩直方图提取主导色，轮廓清晰锐利，适合二次元动漫；
  - 写实照片模式（Average Color）：RGB 空间加权空间采样，渐变过渡自然平滑；
- **孤岛飞点平滑清洗**：采用 8-邻域连通分量分析，智能识别并消除 1~2 粒的离散杂色飞点，提升实体手作可拼性；
- **Floyd-Steinberg 误差扩散抖动**：细腻呈现色彩过渡，消除大面积平涂阶梯感；
- **极速算法与内存保护**：实测 1200 万像素（4000×3000）原图处理仅需 0.71 秒，严格执行双重降采样与分块处理，杜绝 OOM。

### 2. 工业级权威品牌色系体系
- 内置完整 291 色标准色卡数据库；
- 支持 7 大主流拼豆品牌即时无损重映射：
  - Artkal S（软豆）：国际高精度手作标杆（235 色权威色系）；
  - Perler：经典欧美拼豆标准；
  - 国内流行品牌：MARD / COCO / 漫漫 / 盼盼 / 咪小窝；
- 采用 CIEDE2000 色差模型与加权感知色差，色彩还原度达 Delta E < 2.5 优秀级别。

### 3. 个人豆仓管理与 Oklab 缺色智能平替
- **本地豆仓状态持久化**：支持快速勾选拥有状态，支持品牌色号模糊检索；
- **缺色视觉警报**：画布中缺货豆子呈现小红点角标预警，用量统计表红色高亮标记缺货品种；
- **智能平替算法**：基于感知色差模型，在当前现有库存中搜索色差最小的替代色；自动呈现五星推荐度，支持一键全图批量替换。

### 4. 多板切片与聚光灯插豆辅助
- **大图分板切片**：支持自动切割为标准方板（如 29×29、50×50 等），自动标注子板坐标；
- **聚光灯模式**：一键高亮当前选中的单板或特定色号，暗化其余区域，对照实体拼豆板插豆护眼不串行。

### 5. 1:1 物理尺寸导出与自适应排版
- **真实毫米级图纸输出**：精确支持 2.6mm（小豆）与 5.0mm（大豆）真实物理孔距，A4/A3 打印后可直接将透明插豆板覆盖在图纸上作业；
- **自适应多列颜色统计表**：依据图纸实际宽高动态分列（1~6 列响应式排列），告别传统单列无限纵向拉长；
- **多格式全量导出**：高清图纸 PNG（带坐标系、色号标记与格线）、自适应色用量表 PNG、采购清单 CSV（UTF-8 BOM 编码）。

### 6. 专业像素级手工精修与文字拼豆
- **全套画板工具箱**：画笔、橡皮、油漆桶洪水填充、全局颜色替换、一键去背景、指定颜色排除；
- **完整撤销与重做**：支持多步 Undo / Redo 历史记录栈；
- **文字拼豆生成**：支持自定义中文、英文或数字输入，自动栅格化为拼豆图纸；
- **本地项目工程存档**：支持工程保存、读取与命名管理。

---

## 架构与技术栈

项目遵循 Clean Architecture + MVI/MVVM 架构：

```
perler-beads-android/
├── app/
│   ├── src/main/java/com/perlerbeads/generator/
│   │   ├── algorithm/     # 核心算法（像素化、色彩空间、孤岛消除、平替、抖动、切片）
│   │   ├── data/          # 数据层（色板数据库、豆仓持久化、工程序列化）
│   │   ├── export/        # 导出引擎（1:1 毫米级绘图、自适应多列排版、CSV 编码）
│   │   ├── model/         # 数据模型与不可变状态
│   │   ├── navigation/    # Compose Navigation 导航路由
│   │   └── ui/            # 表现层（M3 设计系统、主页、裁剪、编辑器、色板、库存、设置）
│   └── src/test/          # 白盒自动化单元测试套件（110 项）
├── docs/                  # 测试规范、测试报告与基准评测报告
└── scripts/               # 真机 ADB 自动化端到端测试与性能基准脚本
```

核心技术栈：Kotlin 2.0+、Jetpack Compose（Material Design 3）、Kotlin Coroutines + StateFlow、AndroidX Photo Picker、JUnit 4 / Robolectric / ADB Shell。

---

## 算法性能基准测试

在小米 Redmi 13C（MediaTek Helio G85，Android 14）真机上的实测数据：

| 场景 | Beadify | 对比传统 Web 方案 |
| :--- | :--- | :--- |
| 1200 万像素超大图生成 | **0.71 秒** | ~4.2 秒（5.9 倍提速） |
| 内存峰值占用 | **56 MB** | >380 MB（降低 85%） |
| 杂色孤岛消除率 | **98.4%** | 存在大量飞点 |
| 色板映射准确率 | **CIEDE2000 (Delta E <= 2.1)** | 欧式距离 (Delta E ~ 4.8) |

详细评测数据见 [算法基准评测报告](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)。

---

## 质量保证与测试体系

### 四维测试体系

- **静态代码审查**：Lint 0 Errors / 0 Warnings，零内存泄漏
- **白盒运行态测试**：21 个核心测试类，110 个用例 100% 通过，边界条件全覆盖
- **ADB 真机全操作测试**：物理真机实时连接，31 项微操作全遍历，零崩溃零白屏零死锁
- **算法对标评测**：4 大典型场景，可拼性量化对比，Delta E < 2.5

### 运行测试

```bash
# 单元测试
./gradlew testDebugUnitTest

# 真机 ADB 自动化端到端测试（需连接开启 USB 调试的 Android 设备）
powershell -ExecutionPolicy Bypass -File scripts/run_full_feature_e2e_tests.ps1
```

测试用例说明参见 [测试规范说明书](./docs/TEST_SPECIFICATION.md)，详细测试数据与截屏参见 [全量测试报告](./docs/TEST_REPORT.md)。

---

## 快速开始

### 环境要求
- JDK 17 或更高版本
- Android SDK（Compile SDK 35 / Min SDK 26）
- Gradle 8.7+

### 编译与安装

```bash
# 克隆仓库
git clone https://github.com/umbra-w/Beadify.git
cd Beadify

# 编译 Debug APK
./gradlew assembleDebug

# 产物位于 app/build/outputs/apk/debug/app-debug.apk

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.perlerbeads.generator/.MainActivity
```

---

## 文档索引

- [标准化测试规范说明书](./docs/TEST_SPECIFICATION.md)
- [全量测试执行与真机报告](./docs/TEST_REPORT.md)
- [算法质量基准对比报告](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## 项目参考 (References)

本项目在开发与演进过程中参考了以下项目与设计：

- **perler-beads / perler-beads-ai** - 网页端拼豆原型项目，启发了初始双模式像素化方案与基础业务流程设计。
- [beadcolors](https://github.com/maxcleme/beadcolors) - 国际拼豆品牌色号与 HEX 基准数据集。
- [pindou-format-tool](https://github.com/GarrusHuang/pindou-format-tool) - 国内拼豆色板与格式映射参考。
- [Oklab](https://bottosson.github.io/posts/oklab/) - 感知均匀色彩空间算法模型。

---

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。