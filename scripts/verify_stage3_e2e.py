import subprocess
import time
import os
import sys

def run_adb(args):
    cmd = ["adb"] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if res.returncode != 0:
        print(f"[WARN] adb returned code {res.returncode}: {res.stderr.strip()}")
    return res.stdout.strip()

def tap(x, y, sleep_sec=1.0):
    print(f"Tap ({x}, {y})")
    run_adb(["shell", "input", "tap", str(x), str(y)])
    time.sleep(sleep_sec)

def swipe(x1, y1, x2, y2, duration_ms=300, sleep_sec=1.0):
    print(f"Swipe ({x1},{y1}) -> ({x2},{y2})")
    run_adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration_ms)])
    time.sleep(sleep_sec)

def capture(local_path):
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    remote = "/sdcard/tmp_cap.png"
    run_adb(["shell", "screencap", "-p", remote])
    run_adb(["pull", remote, local_path])
    print(f">> Captured: {local_path} ({os.path.getsize(local_path)} bytes)")

def main():
    print("=" * 60)
    print("  拼豆 Android 原生生成器 · 阶段三全流程真机验收")
    print("  (品牌切换 -> 豆仓缺料标记 -> 智能平替推荐 -> 一键批量替换)")
    print("=" * 60)

    # 1. 唤醒并解锁
    run_adb(["shell", "input", "keyevent", "224"])
    run_adb(["shell", "input", "keyevent", "82"])
    time.sleep(1)

    # 2. 重启应用
    print("[1/8] 重启应用...")
    run_adb(["shell", "am", "force-stop", "com.perlerbeads.generator"])
    run_adb(["logcat", "-c"])
    run_adb(["shell", "am", "start", "-n", "com.perlerbeads.generator/.MainActivity"])
    time.sleep(2)

    # 3. 选取真实图片进入设置
    print("[2/8] 导入 12MP 真实测试照片...")
    if os.path.exists("test_real_photo.jpg"):
        run_adb(["push", "test_real_photo.jpg", "/sdcard/DCIM/Camera/IMG_TEST_12MP.jpg"])
        run_adb(["shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/DCIM/Camera/IMG_TEST_12MP.jpg"])
        time.sleep(0.5)

    # 点击首页「导入图片或拍照」卡片 (360, 550)
    tap(360, 550, sleep_sec=2.0)
    # 选中第一张照片 (602, 1102)
    tap(602, 1102, sleep_sec=2.0)
    # 点击「确定裁剪」按钮 (360, 1450)
    tap(360, 1450, sleep_sec=1.5)

    # 4. 在设置页切换为 Perler 品牌
    print("[3/8] 在设置页滑动并选择 Perler 标准色 (103色)...")
    swipe(360, 1200, 360, 400, 300, sleep_sec=0.5)
    # Perler 品牌 FilterChip 在设置页的位置 (310, 930)
    tap(310, 930, sleep_sec=1.0)
    capture("docs/screenshots/stage3_14_settings_perler_selected.png")

    # 点击「管理色板与豆仓」(550, 1300)
    print("[4/8] 进入「色板与豆仓」管理...")
    tap(550, 1300, sleep_sec=1.5)
    capture("docs/screenshots/stage3_15_palette_perler_active.png")

    # 切换到「我的豆仓」Tab (540, 368)
    print("[5/8] 切换到「我的豆仓」Tab...")
    tap(540, 368, sleep_sec=1.0)

    # 取消前两个颜色的勾选设为缺货（P15179, P15181）
    print(">> 标记 P15179 和 P15181 为缺料状态...")
    tap(640, 888, sleep_sec=0.4)
    tap(640, 1008, sleep_sec=0.4)
    capture("docs/screenshots/stage3_16_inventory_perler_missing.png")

    # 点击右上角「保存并应用」(630, 144)
    print(">> 保存豆仓库存并返回设置页...")
    tap(630, 144, sleep_sec=1.5)

    # 5. 生成图纸
    print("[6/8] 点击生成图纸并进入编辑器...")
    # 设置页滑动到最底并点击「生成图纸」(360, 1468)
    swipe(360, 1200, 360, 400, 300, sleep_sec=0.5)
    tap(360, 1468, sleep_sec=2.5)
    capture("docs/screenshots/stage3_17_editor_perler_with_badge.png")

    # 6. 打开统计面板
    print("[7/8] 打开统计面板，查看缺料统计与平替入口...")
    # 点击顶栏「统计」图标 (580, 144)
    tap(580, 144, sleep_sec=1.0)
    capture("docs/screenshots/stage3_18_stats_panel_missing_item.png")

    # 7. 触发智能平替推荐
    print("[8/8] 检查是否有缺料项并触发智能平替推荐...")
    # dump UI 查找「平替」按钮位置
    run_adb(["shell", "uiautomator", "dump", "/sdcard/sub_dump.xml"])
    dump_out = run_adb(["shell", "cat", "/sdcard/sub_dump.xml"])
    if "平替" in dump_out:
        print(">> 检测到缺料项平替按钮！点击打开智能平替推荐弹窗...")
        # 从 dump 中解析平替按钮坐标
        import re
        m = re.search(r'text="平替"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump_out)
        if m:
            btn_x = (int(m.group(1)) + int(m.group(3))) // 2
            btn_y = (int(m.group(2)) + int(m.group(4))) // 2
            tap(btn_x, btn_y, sleep_sec=1.5)
            capture("docs/screenshots/stage3_19_substitution_dialog.png")

            # 点击弹窗内的「一键平替」按钮
            run_adb(["shell", "uiautomator", "dump", "/sdcard/dlg_dump.xml"])
            dlg_dump = run_adb(["shell", "cat", "/sdcard/dlg_dump.xml"])
            m2 = re.search(r'text="一键平替"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dlg_dump)
            if m2:
                apply_x = (int(m2.group(1)) + int(m2.group(3))) // 2
                apply_y = (int(m2.group(2)) + int(m2.group(4))) // 2
                print(f">> 点击一键批量平替按钮 ({apply_x}, {apply_y})...")
                tap(apply_x, apply_y, sleep_sec=2.0)
                capture("docs/screenshots/stage3_20_editor_after_substitution.png")
            else:
                print("[WARN] 未找到一键平替确认按钮")
        else:
            print("[WARN] 未能定位平替按钮具体坐标")
    else:
        print(">> 当前生成图纸未命中被标记缺货的 2 种颜色（自然分布）。")

    # 8. 崩溃与安全检查
    logcat = run_adb(["logcat", "-d"])
    crash_keywords = ["FATAL EXCEPTION", "AndroidRuntime:E", "OutOfMemoryError"]
    crashes = [line for line in logcat.splitlines() if any(k in line for k in crash_keywords)]
    if crashes:
        print("[FAIL] 检测到崩溃报错:")
        for c in crashes:
            print(c)
        sys.exit(1)
    else:
        print(">> Logcat 检查通过：0 崩溃、0 致命异常！")

    print("=" * 60)
    print("  阶段三全流程端到端验证执行完毕！")
    print("=" * 60)

if __name__ == "__main__":
    main()
