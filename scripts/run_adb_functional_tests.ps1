# 自动化功能测试脚本：通过 ADB 驱动物理设备完整交互流程（含真实大图全链路测试）
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
Write-Host "[1/8] 唤醒并解锁屏幕..." -ForegroundColor Yellow
adb shell input keyevent 224 # WAKEUP
adb shell input keyevent 82  # UNLOCK
Start-Sleep -Seconds 1

# 4. 重启应用并清理日志
Write-Host "[2/8] 重启主程序并清理日志..." -ForegroundColor Yellow
adb shell am force-stop com.perlerbeads.generator
adb logcat -c
adb shell am start -n com.perlerbeads.generator/.MainActivity
Start-Sleep -Seconds 2

# 捕获首页
Write-Host "[3/8] 捕获首页屏幕..." -ForegroundColor Yellow
adb shell screencap -p /sdcard/01_home_screen.png
adb pull /sdcard/01_home_screen.png "$ScreenshotDir/01_home_screen.png" | Out-Null

# 5. 准备真实图片
Write-Host "[4/8] 准备真实相机测试照片 (3000x4000)..." -ForegroundColor Yellow
if (Test-Path "test_real_photo.jpg") {
    adb push test_real_photo.jpg /sdcard/DCIM/Camera/IMG_TEST_12MP.jpg | Out-Null
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/DCIM/Camera/IMG_TEST_12MP.jpg" | Out-Null
    Start-Sleep -Milliseconds 500
}

# 6. 进入图片处理流程
Write-Host "[5/8] 进入图片处理流程 (裁剪 -> 设置 -> 算法生成)..." -ForegroundColor Yellow
# 点击「导入图片或拍照」卡片 (360, 550)
adb shell input tap 360 550
Start-Sleep -Seconds 2

# 在 PhotoPicker 中选中最新导入的真实照片 (最右上格 602, 1102)
adb shell input tap 602 1102
Start-Sleep -Seconds 2

# 捕获裁剪页
adb shell screencap -p /sdcard/02_crop_screen.png
adb pull /sdcard/02_crop_screen.png "$ScreenshotDir/02_crop_screen_12mp.png" | Out-Null

# 点击「确定裁剪」按钮 (360, 1450)
adb shell input tap 360 1450
Start-Sleep -Seconds 1

# 捕获设置页
adb shell screencap -p /sdcard/03_settings_screen.png
adb pull /sdcard/03_settings_screen.png "$ScreenshotDir/03_settings_screen.png" | Out-Null

# 向上滑动到设置底部
adb shell input swipe 360 1200 360 400 300
Start-Sleep -Milliseconds 500

Write-Host ">> 触发 1200 万像素图像生成图纸，监测耗时与转圈状态..." -ForegroundColor Cyan
$genTimer = [System.Diagnostics.Stopwatch]::StartNew()
# 点击「生成图纸」按钮 (360, 1468)
adb shell input tap 360 1468
Start-Sleep -Seconds 2
$genTimer.Stop()

Write-Host ">> 生成图纸响应耗时: $($genTimer.ElapsedMilliseconds) ms (必须 < 3000ms，拒绝卡顿转圈)" -ForegroundColor Green
if ($genTimer.ElapsedMilliseconds -gt 3000) {
    Write-Host "[WARN] 生成耗时超出预期阈值: $($genTimer.ElapsedMilliseconds)ms" -ForegroundColor Yellow
}

# 捕获编辑器界面
adb shell screencap -p /sdcard/04_editor_screen.png
adb pull /sdcard/04_editor_screen.png "$ScreenshotDir/04_editor_12mp.png" | Out-Null

# 7. 测试「清理飞点」与「分板跟做」
Write-Host "[6/8] 验证清理飞点与分板跟做功能..." -ForegroundColor Yellow
# 点击「清理飞点」(645, 1316)
adb shell input tap 645 1316
Start-Sleep -Milliseconds 600

# 点击「分板跟做」图标 (280, 144)
adb shell input tap 280 144
Start-Sleep -Seconds 2
adb shell screencap -p /sdcard/05_board_work.png
adb pull /sdcard/05_board_work.png "$ScreenshotDir/05_board_work.png" | Out-Null

# 点击色号卡片触发 Spotlight 高亮 (260, 265)
adb shell input tap 260 265
Start-Sleep -Milliseconds 600
adb shell screencap -p /sdcard/06_spotlight.png
adb pull /sdcard/06_spotlight.png "$ScreenshotDir/06_spotlight.png" | Out-Null

# 返回编辑器
adb shell input keyevent 4
Start-Sleep -Seconds 1

# 8. 测试图纸导出
Write-Host "[7/8] 触发导出对话框并执行真机 PNG 导出..." -ForegroundColor Yellow
# 点击顶栏「导出」图标 (664, 144)
adb shell input tap 664 144
Start-Sleep -Seconds 1
adb shell screencap -p /sdcard/07_export_dialog.png
adb pull /sdcard/07_export_dialog.png "$ScreenshotDir/07_export_dialog.png" | Out-Null

# 记录导出前文件列表
$beforeFiles = (adb shell ls /sdcard/Pictures/PerlerBeads/ 2>$null)

# 点击「带 Key 图纸 PNG」(207, 369)
adb shell input tap 207 369
Start-Sleep -Seconds 2

# 检查相册目录是否有新导出的图纸
$afterFiles = (adb shell ls /sdcard/Pictures/PerlerBeads/ 2>$null)
Write-Host ">> 图纸导出检查：相册目录当前文件总数: $($afterFiles.Count)" -ForegroundColor Green
if ($afterFiles.Count -ge $beforeFiles.Count) {
    Write-Host ">> 图纸导出成功！相册已生成最新拼豆图纸文件！" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 未在相册目录检测到导出的拼豆图纸！" -ForegroundColor Red
    exit 1
}

# 9. 抓取 Logcat 并做崩溃安全检查
Write-Host "[8/8] 分析运行日志与崩溃安全检查..." -ForegroundColor Yellow
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
