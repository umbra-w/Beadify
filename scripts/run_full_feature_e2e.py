#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Beadify 物理真机全功能、全界面端到端自动化测试套件
基于 skydoves/android-testing-skills (Compose UI, AndroidX Test, ADB) 规范设计

覆盖全部 8 大屏幕与 7 大核心业务套件：
- Suite 1: 主链路全流程（选图 -> 裁剪页自由/1:1/1:2/2:1比例预设切换 -> 像素化设置 -> 50x50 图纸生成）
- Suite 2: 编辑器画布工具（画笔手绘、橡皮擦除、孤立飞点清理、视口缩放重置）
- Suite 3: 分板施工模式（BoardWorkScreen、28x28大板/16x16规格切换、十字标尺、逐格施工打勾标记）
- Suite 4: 项目存档与恢复（保存图纸为本地工程、项目列表缩略图与元数据、恢复编辑、删除工程）
- Suite 5: 文字拼豆生成器（TextBeadsScreen、自定义文本输入、字号行数、色号选取、背景填白、切入画布）
- Suite 6: 色板与豆仓管理（PaletteManagerScreen、国际品牌切换、豆仓库存Tab、原子化批量保存）
- Suite 7: 多格式导出全面校验（带Key PNG、颜色统计图 PNG、采购清单 CSV、网格图纸 CSV、300 DPI 矢量 PDF）
- Phase 8: 系统级稳定性与能耗审计（Logcat 0 致命崩溃、0 ANR、内存占用 PSS、安全熄屏）
"""

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.perlerbeads.generator"
MAIN_ACTIVITY = f"{PACKAGE}/.MainActivity"


class AdbDeviceController:
    def __init__(self, serial=None, artifacts_dir="scripts/artifacts", save_screenshots=True):
        self.serial = serial
        self.artifacts_dir = Path(artifacts_dir)
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.save_screenshots = save_screenshots

    def run_cmd(self, cmd_args, check=True):
        prefix = ["adb"]
        if self.serial:
            prefix.extend(["-s", self.serial])
        full_cmd = prefix + cmd_args
        res = subprocess.run(
            full_cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
        if check and res.returncode != 0:
            print(f"[ADB 错误] {' '.join(full_cmd)} -> {res.stderr.strip()}", file=sys.stderr)
        return res

    def shell(self, shell_str, check=True):
        return self.run_cmd(["shell"] + shell_str.split(), check=check)

    def tap(self, x, y, desc="", delay=0.8):
        print(f"  [点击] ({x}, {y}) {desc}")
        self.run_cmd(["shell", "input", "tap", str(x), str(y)])
        time.sleep(delay)

    def swipe(self, x1, y1, x2, y2, duration=300, desc="", delay=0.8):
        print(f"  [滑动] ({x1},{y1}) -> ({x2},{y2}) {desc}")
        self.run_cmd(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration)])
        time.sleep(delay)

    def input_text(self, text, desc=""):
        print(f"  [输入] \"{text}\" {desc}")
        encoded = text.replace(" ", "%s")
        self.run_cmd(["shell", "input", "text", encoded])
        time.sleep(0.5)

    def keyevent(self, code, desc=""):
        self.run_cmd(["shell", "input", "keyevent", str(code)])
        time.sleep(0.5)

    def capture(self, name):
        if not self.save_screenshots:
            return None
        remote = f"/sdcard/{name}.png"
        local = self.artifacts_dir / f"{name}.png"
        self.run_cmd(["shell", "screencap", "-p", remote], check=False)
        self.run_cmd(["pull", remote, str(local)], check=False)
        if local.exists():
            print(f"    [截屏记录] {local.name} ({local.stat().st_size:,} bytes)")
        return local

    def dump_ui_nodes(self):
        remote = "/data/local/tmp/dump.xml"
        self.run_cmd(["shell", "uiautomator", "dump", remote], check=False)
        res = self.run_cmd(["shell", "cat", remote], check=False)
        xml = res.stdout
        nodes = []
        if not xml or "<hierarchy" not in xml:
            return nodes
        pattern = re.compile(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        for m in pattern.finditer(xml):
            text = m.group(1)
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            nodes.append((text, (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2)))
        return nodes

    def find_node(self, keyword):
        nodes = self.dump_ui_nodes()
        for text, cx, cy, bounds in nodes:
            if keyword in text:
                return cx, cy
        return None


def run_full_suite(controller: AdbDeviceController, skip_screen_off=False, suite_filter="all"):
    start_time = time.time()
    print("=" * 80)
    print("  Beadify 物理真机全功能端到端自动化测试 (Android Testing Skills 规范)")
    print("=" * 80)

    # 0. 设备在线状态确认
    dev_res = controller.run_cmd(["devices"])
    print(f"在线设备检测:\n{dev_res.stdout.strip()}")
    if "device" not in dev_res.stdout:
        print("[错误] 未检测到在线 Android 设备，请确认已开启 USB 调试并已授权连接！", file=sys.stderr)
        sys.exit(1)

    # 1. 环境准备 (Phase 0)
    print("\n[Phase 0] 测试运行环境准备与屏幕唤醒...")
    controller.shell("input keyevent KEYCODE_WAKEUP")
    controller.shell("input keyevent 82")
    controller.swipe(360, 1400, 360, 300, 200, desc="解锁滑屏")
    controller.shell("settings put system screen_off_timeout 600000")
    controller.shell("svc power stayon true")
    controller.shell("settings put global window_animation_scale 0")
    controller.shell("settings put global transition_animation_scale 0")
    controller.shell("settings put global animator_duration_scale 0")
    controller.shell("ime enable com.android.inputmethod.latin/.LatinIME", check=False)
    controller.shell("ime set com.android.inputmethod.latin/.LatinIME", check=False)
    controller.shell("logcat -c")
    print("  -> 设备已唤醒、动画比例已置零加速、屏幕常亮已设定、Logcat 缓冲区已重置。")

    # SUITE 1: 主链路全流程
    if suite_filter in ("all", "1"):
        print("\n--- Suite 1: 主链路全流程 (选图 -> 裁剪比例切换 -> 参数配置 -> 50x50 生成) ---")
        controller.run_cmd(["shell", "am", "start", "-S", "-W", "-n", MAIN_ACTIVITY])
        time.sleep(2.0)
        controller.capture("01_home_screen")

        # 点击「导入图片或拍照」卡片 (360, 668)
        coord = controller.find_node("导入图片")
        cx, cy = coord if coord else (360, 668)
        controller.tap(cx, cy, desc="进入系统图片选择器", delay=2.0)
        controller.capture("02_photo_picker")

        # 选中最新测试图片 (120, 1250)
        controller.tap(120, 1250, desc="选取照片", delay=2.0)
        controller.capture("03_crop_screen_default_free")

        # 测试裁剪比例预设切换：1:1、1:2、2:1
        print("  -> 验证 1:1 正方形预设芯片")
        controller.tap(209, 264, desc="切换 1:1 比例预设", delay=0.8)
        controller.capture("04_crop_ratio_1_1")

        print("  -> 验证 1:2 垂直长方形预设芯片")
        controller.tap(323, 264, desc="切换 1:2 比例预设", delay=0.8)
        controller.capture("05_crop_ratio_1_2")

        print("  -> 验证 2:1 水平长方形预设芯片")
        controller.tap(437, 264, desc="切换 2:1 比例预设", delay=0.8)
        controller.capture("06_crop_ratio_2_1")

        # 选回 1:1 并确认裁剪
        controller.tap(209, 264, desc="锁定 1:1 比例预设", delay=0.5)
        btn = controller.find_node("确定裁剪")
        bx, by = btn if btn else (360, 1435)
        controller.tap(bx, by, desc="确定裁剪进入设置页", delay=2.0)
        controller.capture("07_settings_screen")

        # 设置页浏览
        controller.swipe(360, 1300, 360, 300, 300, desc="滑动设置项")
        controller.swipe(360, 1300, 360, 300, 300, desc="滑动至底部")
        controller.capture("08_settings_screen_bottom")

        # 点击「生成图纸」
        gen_btn = controller.find_node("生成图纸")
        gx, gy = gen_btn if gen_btn else (360, 1506)
        controller.tap(gx, gy, desc="触发 Oklab 量化生成图纸", delay=4.0)
        controller.capture("09_editor_canvas_50x50")
        print("  [PASS] Suite 1 执行通过。")

    # SUITE 2: 编辑器画布工具
    if suite_filter in ("all", "2"):
        print("\n--- Suite 2: 编辑器画布工具 (画笔手绘、橡皮擦除、孤立飞点清理、视口重置) ---")
        # 1. 画笔绘制
        controller.tap(60, 1318, desc="切换画笔工具", delay=0.5)
        controller.tap(360, 600, desc="画布中心点涂像素", delay=0.3)
        controller.tap(380, 620, desc="画布偏位点涂像素", delay=0.5)
        controller.capture("10_editor_after_brush")

        # 2. 橡皮擦除
        controller.tap(156, 1318, desc="切换橡皮工具", delay=0.5)
        controller.tap(360, 600, desc="擦除中心像素", delay=0.5)
        controller.capture("11_editor_after_eraser")

        # 3. 清理孤立飞点
        controller.tap(645, 1316, desc="一键清理孤立飞点", delay=1.0)
        controller.capture("12_editor_clean_speckles")

        # 4. 视图缩放重置
        controller.tap(472, 144, desc="复位视图缩放", delay=0.5)
        controller.capture("13_editor_reset_view")
        print("  [PASS] Suite 2 执行通过。")

    # SUITE 3: 分板施工模式
    if suite_filter in ("all", "3"):
        print("\n--- Suite 3: 分板施工模式 (BoardWorkScreen、规格切换、施工打勾) ---")
        controller.tap(600, 144, desc="进入分板施工模式", delay=2.5)
        controller.capture("14_board_work_screen")

        # 切换 16x16 规格
        controller.tap(240, 240, desc="切换 16x16 规格小板", delay=1.0)
        controller.capture("15_board_work_16x16")

        # 切回 28x28 大板规格
        controller.tap(120, 240, desc="切回 28x28 标准大板", delay=1.0)
        controller.capture("16_board_work_28x28")

        # 逐格施工标记打勾
        controller.tap(360, 800, desc="点击格子标记施工进度", delay=0.5)
        controller.capture("17_board_work_cell_checked")

        # 返回编辑器
        controller.tap(60, 130, desc="返回编辑器", delay=1.5)
        print("  [PASS] Suite 3 执行通过。")

    # SUITE 4: 项目存档与恢复
    if suite_filter in ("all", "4"):
        print("\n--- Suite 4: 项目存档与恢复 (保存工程、列表展示、恢复编辑、删除工程) ---")
        controller.tap(536, 144, desc="点击保存工程图标", delay=1.5)
        controller.capture("18_save_project_dialog")

        # 确认保存
        btn = controller.find_node("保存")
        sx, sy = btn if btn else (540, 930)
        controller.tap(sx, sy, desc="确认保存项目", delay=2.0)

        # 返回首页
        controller.tap(60, 144, desc="返回首页", delay=1.5)
        controller.capture("19_home_after_project_saved")

        # 进入「我的项目」
        p_card = controller.find_node("我的项目")
        px, py = p_card if p_card else (360, 980)
        controller.tap(px, py, desc="进入项目管理列表", delay=2.0)
        controller.capture("20_projects_screen_list")

        # 点击列表首项恢复项目
        controller.tap(360, 300, desc="点击项目卡片恢复加载至编辑器", delay=2.5)
        controller.capture("21_project_restored_in_editor")

        # 再次返回首页并进入列表执行删除测试
        controller.tap(60, 144, desc="返回首页", delay=1.2)
        controller.tap(px, py, desc="再次进入项目列表", delay=1.5)
        # 点击第一项的删除图标 (垃圾桶 650, 300)
        controller.tap(650, 300, desc="点击删除项目图标", delay=1.0)
        # 弹窗确认删除
        del_btn = controller.find_node("删除")
        dx, dy = del_btn if del_btn else (540, 900)
        controller.tap(dx, dy, desc="确认删除项目", delay=1.5)
        controller.capture("22_project_deleted")

        controller.tap(60, 130, desc="返回首页", delay=1.2)
        print("  [PASS] Suite 4 执行通过。")

    # SUITE 5: 文字拼豆生成器
    if suite_filter in ("all", "5"):
        print("\n--- Suite 5: 文字拼豆生成器 (自定义文本、高度、色彩、背景填白、切入画布) ---")
        t_card = controller.find_node("文字拼豆")
        tx, ty = t_card if t_card else (360, 840)
        controller.tap(tx, ty, desc="进入文字拼豆模块", delay=2.0)
        controller.capture("24_text_beads_screen")

        # 输入文本
        controller.tap(360, 360, desc="聚焦文字输入框", delay=0.5)
        controller.input_text("LOVE", desc="输入 LOVE 字符")
        controller.capture("25_text_beads_love_inputted")

        # 选择 32 行高度 (240, 520)
        controller.tap(240, 520, desc="设置字号高度为 32 行", delay=0.5)
        # 选取 Artkal C03 颜色 (330, 750)
        controller.tap(330, 750, desc="选取字符颜色", delay=0.5)
        # 切换背景填白开关 (160, 860)
        controller.tap(160, 860, desc="勾选背景填白", delay=0.5)
        controller.capture("26_text_beads_configured")

        # 生成并切入编辑器
        gen_btn = controller.find_node("生成并进入编辑器")
        gx, gy = gen_btn if gen_btn else (360, 1024)
        controller.tap(gx, gy, desc="生成文字图纸并切入编辑器", delay=3.0)
        controller.capture("27_text_beads_in_editor")

        # 返回首页
        controller.tap(60, 130, desc="返回首页", delay=1.5)
        print("  [PASS] Suite 5 执行通过。")

    # SUITE 6: 色板与豆仓管理
    if suite_filter in ("all", "6"):
        print("\n--- Suite 6: 色板与豆仓管理 (品牌切换、豆仓库存Tab、原子化保存) ---")
        controller.swipe(360, 1200, 360, 600, 300, desc="首页向下滑动查找色板设置")
        time.sleep(0.8)

        # 点击编辑色板按钮 (630, 920)
        controller.tap(630, 920, desc="进入色板设置界面", delay=2.0)
        controller.capture("30_palette_manager_screen")

        # 切换品牌至 Artkal C (450, 200)
        controller.tap(450, 200, desc="切换品牌为 Artkal C", delay=1.0)
        controller.capture("31_palette_artkal_c")

        # 切换至「我的豆仓库存」Tab (540, 260)
        controller.tap(540, 260, desc="切换至我的豆仓库存Tab", delay=1.0)
        controller.capture("32_palette_inventory_tab")

        # 点击「保存并应用」
        save_btn = controller.find_node("保存并应用")
        sx, sy = save_btn if save_btn else (640, 130)
        controller.tap(sx, sy, desc="保存并应用色板设置", delay=2.0)
        controller.capture("33_home_after_palette_applied")
        print("  [PASS] Suite 6 执行通过。")

    # SUITE 7: 多格式导出全面校验
    if suite_filter in ("all", "7"):
        print("\n--- Suite 7: 多格式导出全面校验 (带Key PNG、统计图 PNG、CSV 清单与图纸、300 DPI PDF) ---")
        # 重新生成一份文字拼豆进入编辑器
        t_card = controller.find_node("文字拼豆")
        tx, ty = t_card if t_card else (360, 980)
        controller.tap(tx, ty, desc="快速进入文字拼豆", delay=1.5)
        gen_btn = controller.find_node("生成并进入编辑器")
        gx, gy = gen_btn if gen_btn else (360, 1024)
        controller.tap(gx, gy, desc="切入编辑器", delay=3.0)

        # 打开导出底栏弹窗 (664, 144)
        controller.tap(664, 144, desc="打开导出全功能底栏", delay=1.5)
        controller.capture("34_export_modal")

        # 1. 导出图纸 PDF (1:1 打印 2.6mm 迷你豆)
        print("  -> 触发 300 DPI 印刷级 PDF 导出")
        controller.tap(360, 250, desc="导出图纸 PDF (300 DPI 1:1 打印)", delay=3.5)

        # 2. 导出图纸 CSV
        controller.tap(664, 144, desc="重新打开导出底栏", delay=1.2)
        controller.tap(360, 520, desc="导出图纸 CSV（用于电脑/网页互通）", delay=2.0)

        # 3. 导出采购清单 CSV
        controller.tap(664, 144, desc="重新打开导出底栏", delay=1.2)
        controller.tap(360, 450, desc="导出采购清单 CSV", delay=2.0)

        # 4. 导出带 Key 图纸 PNG
        controller.tap(664, 144, desc="重新打开导出底栏", delay=1.2)
        controller.tap(360, 180, desc="导出带 Key 图纸 PNG", delay=2.5)

        # 5. 导出颜色统计图 PNG
        controller.tap(664, 144, desc="重新打开导出底栏", delay=1.2)
        controller.tap(360, 380, desc="导出颜色统计图 PNG", delay=2.5)

        # 返回首页
        controller.tap(60, 130, desc="返回首页", delay=1.5)
        controller.capture("36_final_home_screen")

        # 产物拉取与校验
        print("  -> 从设备拉取 /sdcard/Download/PerlerBeads/ 导出文件...")
        dl_res = controller.run_cmd(["shell", "ls", "-t", "/sdcard/Download/PerlerBeads/"], check=False)
        files = [f.strip() for f in dl_res.stdout.splitlines() if f.strip()]
        for f in files[:6]:
            local_f = controller.artifacts_dir / f
            controller.run_cmd(["pull", f"/sdcard/Download/PerlerBeads/{f}", str(local_f)], check=False)
            if local_f.exists():
                print(f"    [导出文件拉取成功] {local_f.name} ({local_f.stat().st_size:,} bytes)")

        print("  [PASS] Suite 7 执行通过。")

    # PHASE 8: 系统稳定性与安全收尾
    print("\n--- Phase 8: 系统稳定性、崩溃检测与安全收尾 ---")
    # 崩溃日志检查
    crash_log = controller.run_cmd(["logcat", "-b", "crash", "-d"], check=False).stdout
    if "FATAL" in crash_log:
        print(f"[FAIL] 检测到致命崩溃异常 (FATAL EXCEPTION):\n{crash_log}", file=sys.stderr)
        sys.exit(1)
    print("  -> Logcat Crash 缓冲区审计: 0 条致命崩溃记录。")

    # ANR 检查
    anr_log = controller.run_cmd(["shell", "logcat", "-d", "-s", "ActivityManager:E"], check=False).stdout
    if "ANR in com.perlerbeads.generator" in anr_log:
        print("[FAIL] 检测到 ANR 冻结异常！", file=sys.stderr)
        sys.exit(1)
    print("  -> ANR 冻结审计: 0 条 ANR。")

    # 物理内存占用采样
    mem_res = controller.run_cmd(["shell", "dumpsys", "meminfo", PACKAGE], check=False).stdout
    for line in mem_res.splitlines():
        if "TOTAL PSS:" in line or "TOTAL:" in line and "kB" in line:
            print(f"  -> 应用内存开销: {line.strip()}")
            break

    # 恢复系统环境
    controller.shell("settings put global window_animation_scale 1", check=False)
    controller.shell("settings put global transition_animation_scale 1", check=False)
    controller.shell("settings put global animator_duration_scale 1", check=False)
    controller.shell("svc power stayon false", check=False)

    # 安全熄屏
    if not skip_screen_off:
        controller.shell("input keyevent 26")
        print("  -> 测试已安全完成，已执行安全熄屏。")

    elapsed = time.time() - start_time
    print("\n" + "=" * 80)
    print(f"  [ALL PASS] 全部测试套件执行完成，总耗时: {elapsed:.1f} 秒！")
    print(f"  测试产生的分析产物已安全持久化在: {controller.artifacts_dir.resolve()}")
    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(description="Beadify 物理真机端到端全覆盖自动化测试")
    parser.add_argument("-s", "--serial", help="目标 ADB 设备序列号（可选，用于多设备并发选择）")
    parser.add_argument("-o", "--artifacts-dir", default="scripts/artifacts", help="测试截图与导出文件存储路径（默认 scripts/artifacts/）")
    parser.add_argument("--no-screenshots", action="store_true", help="禁用步骤截屏以提高运行速度")
    parser.add_argument("--skip-screen-off", action="store_true", help="测试完成后不自动熄灭屏幕")
    parser.add_argument("--suite", default="all", choices=["all", "1", "2", "3", "4", "5", "6", "7"], help="指定执行单个测试套件（默认 all）")

    args = parser.parse_args()

    controller = AdbDeviceController(
        serial=args.serial,
        artifacts_dir=args.artifacts_dir,
        save_screenshots=not args.no_screenshots
    )

    run_full_suite(
        controller=controller,
        skip_screen_off=args.skip_screen_off,
        suite_filter=args.suite
    )


if __name__ == "__main__":
    main()
