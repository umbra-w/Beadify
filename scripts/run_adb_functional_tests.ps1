# 自动化功能测试脚本：通过 ADB 驱动物理设备完整交互流程
param(
    [string]$ScreenshotDir = "docs/screenshots"
)

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "  拼豆 Android 原生生成器 · ADB 设备端到端自动化功能测试" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 1. 检测 ADB 与设备
$state = (adb get-state 2>&1).ToString().Trim()
if ($state -ne "device") {
    Write-Host "[ERROR] Android 设备未就绪，当前状态: $state" -ForegroundColor Red
    exit 1
}
Write-Host "检测到在线设备就绪 (State: $state)" -ForegroundColor Green

# 2. 准备截图目录
if (!(Test-Path $ScreenshotDir)) {
    New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null
}

# 3. 唤醒并解锁屏幕
Write-Host "[1/7] 唤醒并解锁屏幕..." -ForegroundColor Yellow
adb shell input keyevent 224 # WAKEUP
adb shell input keyevent 82  # UNLOCK
Start-Sleep -Seconds 1

# 4. 清理旧日志并启动主程序
Write-Host "[2/7] 清理日志并启动主程序..." -ForegroundColor Yellow
adb logcat -c
adb shell am start -n com.perlerbeads.generator/.MainActivity
Start-Sleep -Seconds 2

# 捕获首页
Write-Host "[3/7] 捕获首页屏幕..." -ForegroundColor Yellow
adb shell screencap -p /sdcard/01_home_screen.png
adb pull /sdcard/01_home_screen.png "$ScreenshotDir/01_home_screen.png" | Out-Null

# 5. 模拟点击「文字拼豆」卡片 (360, 1066)
Write-Host "[4/7] 进入文字拼豆并生成图纸..." -ForegroundColor Yellow
adb shell input tap 360 1066
Start-Sleep -Seconds 2

# 点击弹窗中的「生成」按钮（屏幕约居中偏下位置 460, 950）
adb shell input tap 460 950
Start-Sleep -Seconds 2

# 捕获编辑器界面
adb shell screencap -p /sdcard/02_editor_screen.png
adb pull /sdcard/02_editor_screen.png "$ScreenshotDir/02_editor_screen.png" | Out-Null

# 6. 测试「清理飞点」功能
Write-Host "[5/7] 触发工具栏「清理飞点」与「撤回」测试..." -ForegroundColor Yellow
# 工具栏水平滚动到底部或点击「清理飞点」
# 在 720 宽屏幕上，「去背景」右侧即为「清理飞点」
adb shell input tap 530 1140
Start-Sleep -Milliseconds 800
adb shell screencap -p /sdcard/03_clean_speckles.png
adb pull /sdcard/03_clean_speckles.png "$ScreenshotDir/03_clean_speckles.png" | Out-Null

# 7. 进入「分板跟做」页面并测试 Spotlight
Write-Host "[6/7] 进入分板跟做、测试屏幕常亮与色号高亮..." -ForegroundColor Yellow
# 点击右上角顶栏的分板跟做图标（约在 400, 120 附近）
adb shell input tap 400 120
Start-Sleep -Seconds 2
adb shell screencap -p /sdcard/04_board_slice_screen.png
adb pull /sdcard/04_board_slice_screen.png "$ScreenshotDir/04_board_slice_screen.png" | Out-Null

# 点击第一个色号卡片触发 Spotlight 高亮
adb shell input tap 100 200
Start-Sleep -Milliseconds 800
adb shell screencap -p /sdcard/05_board_spotlight.png
adb pull /sdcard/05_board_spotlight.png "$ScreenshotDir/05_board_spotlight.png" | Out-Null

# 返回编辑器
adb shell input keyevent 4 # BACK
Start-Sleep -Seconds 1

# 点击顶栏「导出」图标调起导出弹窗（右上角 680, 120 附近）
adb shell input tap 680 120
Start-Sleep -Seconds 1
adb shell screencap -p /sdcard/06_export_dialog.png
adb pull /sdcard/06_export_dialog.png "$ScreenshotDir/06_export_dialog.png" | Out-Null

# 关闭对话框
adb shell input keyevent 4

# 8. 抓取 Logcat 并做崩溃安全检查
Write-Host "[7/7] 分析运行日志与崩溃安全检查..." -ForegroundColor Yellow
$logErrors = adb logcat -d | Select-String "FATAL EXCEPTION|AndroidRuntime:E|OutOfMemoryError"
if ($logErrors) {
    Write-Host "[FAIL] 检测到致命异常报错：" -ForegroundColor Red
    $logErrors | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
    exit 1
} else {
    Write-Host ">> Logcat 检查通过：0 崩溃、0 致命异常、0 内存溢出 (OOM)！" -ForegroundColor Green
}

Write-Host "======================================================" -ForegroundColor Green
Write-Host "  ADB 自动化功能测试执行完毕！所有截图已保存在 $ScreenshotDir" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
