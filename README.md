# Beadify - 智能拼豆图纸生成器（安卓 App）

原生安卓版 Beadify 智能拼豆图纸生成器。功能与网页版逐行对齐的高性能本地算法移植。

## 功能

- 图片导入（Photo Picker）→ 裁剪 → 像素化设置
- 像素化：卡通（主导色）/ 真实（平均色）两种模式，可调粒度与相似色合并阈值
- 颜色映射到 291 色色板，支持 5 家色号系统（MARD / COCO / 漫漫 / 盼盼 / 咪小窝）
- 手动编辑：画笔 / 橡皮 / 洪水擦除 / 颜色替换 / 一键去背景 / 颜色排除重映射
- 颜色统计（每色用量 + 总粒数）
- 导出：带 Key 图纸 PNG、颜色统计图 PNG、采购清单 CSV（存相册 / 下载，支持分享）
- 自定义色板（按色号分组勾选，本地保存）

## 构建

要求：JDK 17、Android SDK（compileSdk 35）、Android Studio 或命令行 Gradle。

```bash
# 环境变量
export JAVA_HOME="C:\\Android\\jdk17\\jdk-17.0.20+8"
export ANDROID_HOME="C:\\Android\\sdk"

cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 测试

```bash
./gradlew testDebugUnitTest     # 核心算法单元测试（19 个）
```

## 运行

```bash
# 模拟器
emulator -avd perler &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.perlerbeads.generator/.MainActivity

# 真机：USB 调试打开后
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 一键验证（PowerShell）

仓库 `docs/android/verify.ps1` 提供完整验证：单元测试 → 构建 → 启动模拟器 → 安装 → 启动 → 前台/崩溃检查 → 截图 → UI 文本导出。

```powershell
# PowerShell 中运行（在仓库根目录）
powershell -ExecutionPolicy Bypass -File docs\android\verify.ps1
powershell -ExecutionPolicy Bypass -File docs\android\verify.ps1 -SkipBuild     # APK 已存在时
powershell -ExecutionPolicy Bypass -File docs\android\verify.ps1 -NoEmulator    # 真机/已有设备时
```

## 设计 / 开发文档

- `../docs/android/design.md` — 设计方案（架构、算法移植规格、验收标准）
- `../docs/android/dev-doc.md` — 开发文档（环境、结构、构建命令、算法对照表）
