# 拼豆 Android 原生生成器 · 标准化测试规范说明书 (Test Specification)

版本：v1.2  
适用工程：`perler-beads-android` (Kotlin / Jetpack Compose / Android 14)  
测试环境要求：
- 构建环境：JDK 17+, Gradle 8.x
- 运行环境：Android 14 (物理测试设备: Xiaomi 23129RAA4G / Redmi 13C, 分辨率 720×1612, 320dpi)
- 工具链：Android SDK Platform-Tools (adb, aapt), Android Lint, JUnit4

---

## 1. 测试体系架构

本工程建立“四维一体”的质量保障与测试体系：

```
                    ┌────────────────────────┐
                    │      全流程测试体系     │
                    └───────────┬────────────┘
         ┌──────────────┬───────┴───────┬──────────────┐
         ▼              ▼               ▼              ▼
   【静态代码审查】  【白盒运行态测试】 【ADB设备功能测试】 【算法质量评测】
   - 编译器Warning  - 分支边界值分析  - 自动化脚本驱动   - 典型素材基准集
   - Android Lint   - 异常容错回退    - 真实文件落盘校验 - 色数合规率100%
   - 内存与生命周期 - 历史栈与状态机  - Logcat崩溃/ANR   - 孤立飞点消除率
   - 并发协程安全   - 82+项自动化单测 - 关键节点UI截屏   - 细线保护与PSNR
```

---

## 2. 第一维：静态测试规范 (Static Analysis Specification)

### 2.1 审查目标与门禁标准
1. **编译器零 Warning 门禁**：Kotlin 编译阶段严禁存在废弃 API 引用（如 Compose 弃用图标）或未处理类型转换；
2. **Android Lint 门禁**：
   - `Error`（严重缺陷）：0 个；
   - `Correctness`（正确性）：检查空指针保护、资源引用有效性；
   - `Performance`（性能）：检查内存分配、大对象避免在绘制阶段创建；
   - `Security`（安全性）：SharedPreferences 权限模式、外部存储导出安全作用域（MediaStore）；
3. **人工代码走查重点**：
   - **除以零与越界**：网格采样缩放 `imgWidth / n`，当 `n <= 0` 或 `imgWidth <= 0` 时的边界防御；
   - **大位图内存预算**：`Exporter.renderPatternBitmap` 中网格尺寸 $\ge 200 \times 200$ 时是否按预算压缩 `cellSize`；
   - **协程并发安全性**：ViewModel 中的 `cells` 数组深拷贝与 `gridVersion` 自增在 `Dispatchers.Default` 与 UI 线程间的同步一致性。

---

## 3. 第二维：白盒运行态测试规范 (White-Box Code Testing Specification)

### 3.1 覆盖域划分
| 模块 | 核心类 | 重点覆盖分支与边界条件 |
| :--- | :--- | :--- |
| **色彩计算** | `ColorMath.kt` | sRGB去伽马边界（0, 255）、Oklab单调性、空候选色板回退 `ERR` |
| **受控色数** | `ColorQuantizer.kt` | 候选色少于上限（不切割直接返回）、色板仅1色、大面积单色中极小面积特征保护 |
| **噪点清理** | `IslandCleanup.kt` | 1×1孤立单点消除、对角线端点保留、封闭矩形边框保留、棋盘格密集噪点平滑 |
| **误差扩散** | `Pixelation.kt` | 误差累积截断至 [0, 255]、透明像素不扩散误差、无随机数确定性验证 |
| **分板跟做** | `BoardSlicing.kt` | 奇偶数边界板切片、全局平坦索引区间编解码压缩、已完成状态切换 |
| **状态回退** | `AppViewModel.kt` | 历史栈满50步FIFO丢弃、重做栈在执行新操作时清空、连续双指缩放不误触撤回栈 |

### 3.2 判定基准
- 单测执行命令：`.\gradlew.bat testDebugUnitTest`；
- 所有用例通过率必须达到 **100% (0 Failure, 0 Error)**。

---

## 4. 第三维：ADB 设备自动化功能测试规范 (Functional & ADB Automation)

### 4.1 测试脚本驱动流程 (`scripts/run_adb_functional_tests.ps1`)
1. **环境准备与唤醒**：检测 `adb devices`，点亮屏幕并模拟解锁；
2. **应用启动与前台确认**：启动 `MainActivity`，确认 PID 存活并记录基础内存消耗；
3. **参数设置操作流**：
   - 模拟点击粒度滑块调整；
   - 切换像素化模式（卡通/真实）；
   - 切换色数控制 Chip（16色 / 24色 / 32色）；
   - 切换自动清理孤立飞点 Switch 开关；
4. **生成与画布交互流**：
   - 点击「生成图纸」；
   - 触发「去背景」、「清理飞点」并读取 Toast 提示文本；
   - 触发「撤回」与「重做」并校验图纸一致性；
5. **分板跟做操作流**：
   - 点击「分板跟做」，进入分板页面；
   - 验证屏幕常亮标志 `FLAG_KEEP_SCREEN_ON`；
   - 模拟点击色号卡片触发 Spotlight 高亮，模拟点击格子记录进度；
6. **导出与文件落盘校验**：
   - 触发「导出 PNG 图纸」；
   - 触发「导出 PDF 图纸」；
   - 通过 `adb shell ls -l` 检验 `/sdcard/Pictures/PerlerBeads/` 与下载目录，确认文件物理存在且大小符合预期（PNG > 50KB, PDF > 20KB）；
7. **稳定性与异常监控**：
   - 抓取全过程 `adb logcat -d`，过滤 `AndroidRuntime:E`, `FATAL EXCEPTION`, `OutOfMemoryError`，必须为 0 报错；
   - 采集各主要界面截图存入 `docs/screenshots/`。

---

## 5. 第四维：算法效果与典型素材基准测试规范 (Algorithm Benchmark Specification)

### 5.1 基准素材集设计 (Ground-Truth Benchmark Dataset)
为彻底避免“凭空瞎编”，测试选取市面成熟拼豆与像素艺术界公认的 4 种典型图案结构：

1. **8-bit / 16-bit 像素艺术图 (Pixel Sprite - Mario / Pokemon)**：
   - 特征：清晰色块、1像素宽黑色轮廓线、色彩数量较少；
   - 检验重点：外轮廓连续性是否被噪点清理算法破坏？色数上限是否能精准捕捉主体基色？
2. **多阶过渡渐变插画 (Smooth Gradient Illustration)**：
   - 特征：大面积光影渐变；
   - 检验重点：Floyd-Steinberg 抖动在有限色板下的过渡自然度、是否有横向色带撕裂？
3. **高反差微小细节图 (Small Feature Facial Detail)**：
   - 特征：大面积白底/肤色，但带有仅占 0.5% 面积的高饱和红唇、深蓝瞳孔；
   - 检验重点：在限制 16 色时，中位切割是否能在 Oklab 色度轴上把红唇作为独立箱体保留？
4. **人工合成椒盐噪声与棋盘测试图 (Synthetic Speckle & Diagonal Test)**：
   - 特征：已知位置注入 1 格离散噪点 + 对角线连续单像素线条；
   - 检验重点：定量计算飞点消除率与对角线条豁免率。

### 5.2 算法指标公式与阈值
1. **色数合规度**：
   $$\text{Compliance} = \mathbb{I}(\text{DistinctColors} \le \text{MaxColorsBudget}) = 100\%$$
2. **飞点消除率 (Speckle Removal Rate)**：
   $$\text{SRR} = \frac{\text{消除的孤立噪点数}}{\text{总孤立噪点数}} \ge 85\%$$
3. **细线保留率 (Line Preservation Rate)**：
   $$\text{LPR} = \frac{\text{保留的连续线像素数}}{\text{原始连续线像素数}} = 100\%$$
4. **峰值信噪比 (PSNR)**：
   $$\text{PSNR} = 10 \cdot \log_{10}\left(\frac{MAX_I^2}{MSE}\right) \ge 25\text{ dB}$$

---

## 6. 第五维：多品牌官方色板、豆仓库存与 Oklab 智能平替测试规范 (Stage 3 Specification)

### 6.1 权威数据源对齐与零捏造标准 (Authoritative Palette Alignment)
1. **数据源咬合**：
   - 国际主流品牌（Artkal、Perler、Hama）严格对齐 GitHub 权威拼豆开源库 [`maxcleme/beadcolors`](https://github.com/maxcleme/beadcolors)；
   - 国内通用体系严格对齐拼豆流行格式转换工具 [`GarrusHuang/pindou-format-tool`](https://github.com/GarrusHuang/pindou-format-tool) 的 Mard 291 色及五大店家映射；
   - 严禁任何算法或开发人员自行编造虚构色号或 RGB 估算值。
2. **多品牌资产库容量**：
   - `Artkal S` (5.0mm 软豆)：官方权威 199 色；
   - `Artkal C` (2.6mm 硬豆)：官方权威 174 色；
   - `Artkal A` (2.6mm 软豆)：官方权威 145 色；
   - `Perler` (5.0mm 标准豆)：官方权威 103 色；
   - `Hama Midi` (5.0mm 中豆)：官方权威 92 色；
   - `Domestic Mard` (2.6/5.0mm 五大家族映射)：291 色（MARD / COCO / 漫漫 / 盼盼 / 咪小窝）。

### 6.2 豆仓库存隔离与持久化规范 (Inventory Store Specification)
1. **品牌隔离性**：用户在 Artkal S 中拥有的颜色与在 Perler 中拥有的颜色互不干扰，在本地 `SharedPreferences` 中通过 `stock_{brandId}` 独立存储 JSON 映射；
2. **初始化默认状态**：若用户未针对该品牌自定义过库存（`configured_{brandId} == false`），默认判定全量拥有（100% 可用），避免首次进入全部报缺；
3. **原子批量写入**：用户在 UI 执行全选/清空/批量修改时，必须通过 `saveAllStock` 一次性提交原子写入，杜绝循环单点 `apply()` 导致 I/O 堵塞；
4. **零缺料模式 (Zero-Shortage Generation)**：开启「只用豆仓库存颜色生成」选项后，像素化算法的候选色板自动过滤为仅包含已入库现货颜色，100% 杜绝生成含缺料图纸。

### 6.3 Oklab 智能平替推荐算法规范 (Oklab Smart Substitution Specification)
1. **色差计算模型**：
   平替推荐算法必须在均匀感知的 Oklab 色彩空间下执行欧氏距离计算：
   $$\Delta E_{ok}(C_1, C_2) = \sqrt{(L_1 - L_2)^2 + (a_1 - a_2)^2 + (b_1 - b_2)^2} \times 100$$
2. **星级匹配度分级准则**：
   | 色差阈值 $\Delta E_{ok}$ | 星级评定 | 语义标签 | 视觉说明 |
   | :--- | :---: | :---: | :--- |
   | $\le 5.0$ | ★★★★★ | 极佳平替 | 肉眼几乎不可辨别差异，替代后几乎不影响成品质感 |
   | $\le 10.0$ | ★★★★☆ | 推荐平替 | 极轻微色调差，不仔细对比难以察觉 |
   | $\le 18.0$ | ★★★☆☆ | 可用平替 | 有一定色差，但在无现货时可应急替代 |
   | $> 18.0$ | ★★☆☆☆ | 偏色较大 | 色差显著，弹窗将给予用户醒目偏色告警 |
3. **批量替换与撤销一致性**：
   - 执行「一键平替」时，必须将图纸中所有使用原缺料颜色的网格像素一次性平替为推荐色；
   - 必须保留圆形画板几何遮罩与外部擦除标记（`isExternal`），禁止污染透明通道；
   - 平替操作前必须调用 `saveSnapshot()` 压入历史栈，保证用户随时可以通过编辑页的「撤回」按钮无损回滚。

