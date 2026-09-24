# Beadify 自动化与辅助测试工具集 (Test Automation Tools)

本目录包含了工程的**辅助与端到端测试工具**，作为 Android 原生 JVM 单元测试（`app/src/test/`）的补充，支撑全生命周期自动化验证。

---

## 1. 物理真机端到端全覆盖自动化测试

基于开源规范 [skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills) 的 ADB 自动化控制体系开发，覆盖 Beadify 的全部 8 大主页面与 7 大核心业务套件。

### 运行方式

确保物理 Android 设备已连接并开启 USB 调试：

```bash
# 全量运行所有 7 大测试套件并自动安全熄屏
python scripts/run_full_feature_e2e.py

# 指定仅执行某一个套件（如 Suite 1 主链路生成）
python scripts/run_full_feature_e2e.py --suite 1

# 多设备环境指定设备序列号，且测试完成后保持亮屏
python scripts/run_full_feature_e2e.py -s <DEVICE_SERIAL> --skip-screen-off

# 极速运行（不捕获各步骤高精度截屏）
python scripts/run_full_feature_e2e.py --no-screenshots
```

### 覆盖测试套件矩阵

| 套件编号 | 名称 | 覆盖模块与操作 |
| :--- | :--- | :--- |
| **Suite 1** | **主链路全流程** | 选图、裁剪页（自由比例、1:1 正方形、1:2/2:1 拼接长方形比例预设）、参数配置、50×50 Oklab 量化生成 |
| **Suite 2** | **编辑器画布工具** | 画笔手绘点涂、橡皮擦除、一键清理孤立飞点、视口缩放重置 |
| **Suite 3** | **分板施工模式** | 28×28 主流大板/16×16 规格切换、标尺坐标定位、逐格施工打勾标记 |
| **Suite 4** | **项目存档与恢复** | 本地工程保存、项目列表元数据加载、恢复工程至编辑器、项目删除 |
| **Suite 5** | **文字拼豆生成器** | 自定义文本输入、行数规格、颜色选取、背景填白、切入画布二次修饰 |
| **Suite 6** | **色板与豆仓管理** | 国际品牌切换（Artkal C 等）、我的豆仓库存 Tab、原子化持久化保存 |
| **Suite 7** | **多格式导出校验** | 带 Key 图纸 PNG、颜色统计图 PNG、采购清单 CSV、网格图纸 CSV、300 DPI 矢量 PDF |
| **Phase 8** | **系统级稳定性审计** | Logcat 0 致命崩溃校验、0 ANR 校验、内存占用（PSS）审计、安全熄屏恢复 |

---

## 2. 算法质量基准对标测试

```bash
# 评估 Oklab 与 RGB 量化在 PSNR、SSIM、飞点消减率、色差偏差上的量化对比
python scripts/benchmark_algorithms.py
```

---

## 3. 产物管理与 Git 规范

为保证代码仓库轻量与纯净：
* 所有的临时截屏、UI dump、拉取的测试图纸（PDF/PNG/CSV）均默认保存至 `scripts/artifacts/` 目录；
* 该目录已配置于根目录 `.gitignore` 中，杜绝任何二进制产物或临时数据被意外提交。
