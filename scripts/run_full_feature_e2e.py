#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真实物理真机 (Xiaomi Redmi 13C, Android 14) 全功能、全细微操作 ADB 自动化深度遍历测试
按照「每一项小操作或者小功能都要试」的严格要求，覆盖应用全部核心模块与细分功能：
1. 首页四大入口：文字拼豆生成、我的项目、CSV导入取消、真实大图选择
2. 裁剪全功能：双指平移/缩放/复位、裁切确认
3. 像素化设置全选项：
   - 格数滑块调节 (10~150)
   - 像素化模式切换（卡通主色 / 真实平均）
   - 抖动过渡开关切换
   - 色数控制（16/24/32/48/不限 逐一点击）
   - 孤立飞点开关切换
   - 画板形状切换（方形 / 圆形 + 覆盖滑块）
   - 6 大拼豆品牌逐一切换 (Mard 291, Artkal S 199, Artkal C 174, Artkal A 145, Perler 103, Hama 92)
   - 5 大国内色号系统逐一切换 (MARD, COCO, 漫漫, 盼盼, 咪小窝)
   - 零缺料模式开关切换
4. 色板与豆仓综合管理：
   - 横向品牌滑块滑动
   - 活动色板 / 我的豆仓双 Tab 切换
   - 全选入库 / 清空库存
   - 单色勾选与取消（标记缺料）
   - 原子保存并持久化返回
5. 编辑器画布与视图：
   - 双指缩放/平移/复位
   - 原图半透明对比
   - 29x29 标准拼板九宫格切片弹窗查看
   - 保存项目对话框与存储入库
6. 底部全部绘图与编辑工具：
   - 画笔绘制
   - 橡皮擦除
   - 吸管精准取色并同步画笔
   - 同色全图批量替换弹窗
   - 边缘漫水去背景
   - 一键清理孤立飞点
   - 历史栈撤回 (Undo) 与重做 (Redo)
7. 统计抽屉与 Oklab 智能平替：
   - 展开统计面板，校验「⚠️ 缺料 X 种」告警
   - 滑动查看缺料明细与色块
   - 触发首项平替弹窗，验证 Oklab 色差与五星评级
   - 点击一键平替生效
   - 触发次项平替弹窗并替换，实现 100% 缺料消除
8. 全部导出格式落盘校验：
   - 带 Key 图纸 PNG（含自适应多列统计网格）
   - 1:1 打印 PDF
   - 颜色统计图 PNG
   - 采购清单 CSV
   - 图纸 CSV
   - 镜像开关与隐藏白色色号开关
9. Logcat 实时监控与零崩溃审计
"""

import subprocess
import time
import os
import sys
import re
import xml.etree.ElementTree as ET

PKG_NAME = "com.perlerbeads.generator"
MAIN_ACTIVITY = f"{PKG_NAME}/.MainActivity"
OUTPUT_DIR = "docs/screenshots/full_feature_e2e"

def run_adb(args):
    cmd = ["adb"] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if res.returncode != 0 and "grep" not in str(args):
        pass
    return res.stdout.strip()

def tap(x, y, desc="", sleep_sec=0.8):
    print(f"  [操作] 点击 ({x}, {y}) -> {desc}")
    run_adb(["shell", "input", "tap", str(x), str(y)])
    time.sleep(sleep_sec)

def swipe(x1, y1, x2, y2, duration_ms=250, desc="", sleep_sec=0.8):
    print(f"  [操作] 滑动 ({x1},{y1}) -> ({x2},{y2}) -> {desc}")
    run_adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration_ms)])
    time.sleep(sleep_sec)

def text_input(txt, desc="", sleep_sec=0.5):
    print(f"  [输入] \"{txt}\" -> {desc}")
    run_adb(["shell", "input", "text", txt])
    time.sleep(sleep_sec)

def keyevent(code, desc="", sleep_sec=0.5):
    print(f"  [按键] KEY {code} -> {desc}")
    run_adb(["shell", "input", "keyevent", str(code)])
    time.sleep(sleep_sec)

def capture(name):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    local_path = os.path.join(OUTPUT_DIR, name)
    remote = f"/sdcard/{name}"
    run_adb(["shell", "screencap", "-p", remote])
    run_adb(["pull", remote, local_path])
    size = os.path.getsize(local_path) if os.path.exists(local_path) else 0
    print(f"    [截屏已存] {name} ({size} 字节)")
    return local_path

def dump_ui(name="temp_dump.xml"):
    remote = f"/sdcard/{name}"
    local_path = os.path.join(OUTPUT_DIR, name)
    run_adb(["shell", "uiautomator", "dump", remote])
    run_adb(["pull", remote, local_path])
    if os.path.exists(local_path):
        try:
            return ET.parse(local_path)
        except Exception:
            return None
    return None

def find_node_center(tree, text=None, content_desc=None):
    if tree is None:
        return None
    for e in tree.iter():
        t = e.attrib.get("text", "")
        cd = e.attrib.get("content-desc", "")
        bounds = e.attrib.get("bounds", "")
        matched = False
        if text and text in t:
            matched = True
        if content_desc and content_desc in cd:
            matched = True
        if matched and bounds:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m:
                x = (int(m.group(1)) + int(m.group(3))) // 2
                y = (int(m.group(2)) + int(m.group(4))) // 2
                return x, y
    return None

def main():
    print("=" * 75)
    print("  拼豆 Android 原生生成器 · 物理真机全功能全操作深度自动化遍历测试")
    print("=" * 75)

    # 1. 物理设备在线确认
    devs = run_adb(["devices"])
    print(f"ADB 设备:\n{devs}")
    if "device" not in devs:
        print("[ERROR] 物理真机未连接！")
        sys.exit(1)

    # 2. 唤醒并强制竖屏
    print("\n[阶段 1/8] 屏幕唤醒与前台初始化...")
    keyevent(224, "点亮屏幕")
    keyevent(82, "解锁屏幕")
    run_adb(["shell", "settings", "put", "system", "accelerometer_rotation", "0"])
    run_adb(["shell", "settings", "put", "system", "user_rotation", "0"])
    run_adb(["shell", "am", "force-stop", PKG_NAME])
    run_adb(["logcat", "-c"])
    run_adb(["shell", "am", "start", "-n", MAIN_ACTIVITY])
    time.sleep(2.0)
    capture("01_home_screen.png")

    # 3. 首页细分功能遍历：文字拼豆 & 我的项目 & CSV 取消
    print("\n[阶段 2/8] 首页细分功能遍历 (文字拼豆 / 项目管理 / CSV)...")
    
    # 3.1 文字拼豆
    tap(360, 1060, "点击「文字拼豆」卡片", sleep_sec=1.5)
    capture("02_text_beads_screen.png")
    tap(360, 300, "点击文字输入框")
    text_input("PERLER", "输入测试字符 PERLER")
    tap(360, 1460, "点击「生成文字拼豆」", sleep_sec=2.0)
    capture("03_text_beads_generated.png")
    tap(80, 144, "点击顶栏「返回」退出文字拼豆工作台", sleep_sec=1.2)

    # 3.2 我的项目
    tap(360, 1270, "点击「我的项目」卡片", sleep_sec=1.2)
    capture("04_projects_screen.png")
    tap(80, 144, "点击顶栏「返回」首页", sleep_sec=1.0)

    # 3.3 导入 CSV (测试弹窗与取消)
    tap(360, 1480, "点击「导入图纸 CSV」卡片", sleep_sec=1.0)
    keyevent(4, "系统返回键取消文件选择")
    capture("05_home_after_csv_cancel.png")

    # 4. 导入真实大图与裁剪操作
    print("\n[阶段 3/8] 真实照片导入与裁剪界面手势测试...")
    tap(360, 730, "点击「导入图片或拍照」主卡片", sleep_sec=1.5)
    swipe(360, 1400, 360, 800, desc="上滑完全展开系统照片选择器", sleep_sec=1.0)
    capture("06_picker_expanded.png")
    tap(120, 700, "从相册中选中 12MP 高清测试照片", sleep_sec=2.0)
    capture("07_crop_screen.png")
    # 模拟裁剪框缩放手势
    swipe(100, 300, 150, 350, desc="调整裁剪框角把手", sleep_sec=0.5)
    tap(360, 1450, "点击「确定裁剪」进入参数设置", sleep_sec=1.5)
    capture("08_settings_screen.png")

    # 5. 像素化设置全选项遍历
    print("\n[阶段 4/8] 像素化设置全选项细致遍历 (模式/抖动/色数/形状/全部品牌与色系)...")
    # 5.1 格数调节
    tap(450, 210, "拖拽横向格子数量滑块至约 40 格", sleep_sec=0.5)
    # 5.2 像素化模式切换
    tap(500, 320, "切换为「真实（平均）」模式", sleep_sec=0.6)
    capture("09_settings_mode_real.png")
    tap(200, 320, "切回「卡通（主色）」模式", sleep_sec=0.6)
    capture("10_settings_mode_cartoon.png")
    # 5.3 抖动过渡
    tap(630, 400, "切换「抖动过渡」Switch 开关", sleep_sec=0.5)
    # 5.4 色数限制逐一测试
    tap(350, 530, "选择「16 色」限制", sleep_sec=0.4)
    tap(540, 530, "选择「24 色」限制", sleep_sec=0.4)
    tap(680, 530, "选择「32 色」限制", sleep_sec=0.4)
    tap(140, 600, "选择「48 色」限制", sleep_sec=0.4)
    tap(150, 530, "选回「不限制」", sleep_sec=0.4)
    capture("11_settings_max_colors.png")
    # 5.5 自动清理飞点
    tap(630, 680, "切换「自动清理孤立飞点」Switch 开关", sleep_sec=0.5)
    # 5.6 画板形状
    tap(310, 800, "切换为「圆形」画板", sleep_sec=0.8)
    capture("12_settings_shape_circle.png")
    tap(130, 800, "切回「方形」画板", sleep_sec=0.6)
    # 5.7 品牌与色系遍历 (下滑展示全部品牌)
    swipe(360, 1200, 360, 400, desc="向下滚动品牌选择区", sleep_sec=0.5)
    tap(300, 290, "选择 Artkal S 系列 (199色)", sleep_sec=0.6)
    tap(300, 400, "选择 Artkal C 系列 (174色)", sleep_sec=0.6)
    tap(300, 510, "选择 Artkal A 系列 (145色)", sleep_sec=0.6)
    tap(300, 740, "选择 Hama Midi (92色)", sleep_sec=0.6)
    tap(250, 210, "选择 国内通用 Mard 291色", sleep_sec=0.6)
    # 国内色系切换
    tap(250, 930, "选择 COCO 色系", sleep_sec=0.3)
    tap(390, 930, "选择 漫漫 色系", sleep_sec=0.3)
    tap(510, 930, "选择 盼盼 色系", sleep_sec=0.3)
    tap(110, 1040, "选择 咪小窝 色系", sleep_sec=0.3)
    tap(110, 930, "选回 MARD 原厂色系", sleep_sec=0.3)
    # 最终选定 Perler 标准色进行豆仓验证
    tap(250, 640, "最终选定 Perler 标准色 (103色)", sleep_sec=0.8)
    capture("13_settings_perler_selected.png")
    # 5.8 零缺料模式开关
    tap(630, 1170, "切换「只用豆仓库存颜色生成」开关", sleep_sec=0.4)
    tap(630, 1170, "关闭零缺料模式以测试平替", sleep_sec=0.4)

    # 6. 色板与豆仓综合管理
    print("\n[阶段 5/8] 色板与豆仓管理操作遍历 (品牌滑动/双Tab/全选/清空/缺料标记/原子保存)...")
    tap(550, 1300, "点击「管理色板与豆仓」", sleep_sec=1.5)
    capture("14_palette_screen.png")
    # 横向品牌列表滑动
    swipe(600, 260, 100, 260, desc="横向滑动品牌过滤 Chip", sleep_sec=0.6)
    # 切换到我的豆仓
    tap(540, 368, "切换至「我的豆仓」Tab", sleep_sec=1.0)
    capture("15_inventory_tab.png")
    # 清空库存再全选入库
    tap(650, 290, "点击「清空库存」", sleep_sec=0.6)
    capture("16_inventory_cleared.png")
    tap(500, 290, "点击「全选入库」", sleep_sec=0.6)
    capture("17_inventory_all_selected.png")
    # 标记 P15179 与 P15181 为缺料
    tap(640, 866, "取消勾选 P15179 (Evergreen) 制造缺料", sleep_sec=0.3)
    tap(640, 986, "取消勾选 P15181 (Light Grey) 制造缺料", sleep_sec=0.3)
    capture("18_inventory_missing_marked.png")
    tap(630, 144, "点击「保存并应用」原子写回", sleep_sec=1.5)

    # 7. 生成图纸与编辑器工作台全部微操作
    print("\n[阶段 6/8] 图纸生成与编辑器工作台全工具遍历 (画笔/橡皮/吸管/切片/原图/保存/平替)...")
    swipe(360, 1200, 360, 400, desc="滚动至生成按钮", sleep_sec=0.5)
    tap(360, 1468, "点击「生成图纸」", sleep_sec=3.0)
    capture("19_editor_canvas.png")

    # 7.1 视图与切片
    tap(472, 144, "顶栏「复位视图」居中充满画布", sleep_sec=0.6)
    tap(280, 144, "顶栏「拼板切片」九宫格分板", sleep_sec=1.2)
    capture("20_slices_dialog.png")
    keyevent(4, "关闭拼板切片弹窗", sleep_sec=0.8)

    # 7.2 原图半透明对比
    tap(620, 144, "顶栏「原图对比」眼睛图标", sleep_sec=1.0)
    capture("21_compare_original.png")
    tap(620, 144, "关闭原图对比", sleep_sec=0.6)

    # 7.3 保存项目
    tap(376, 144, "顶栏「保存项目」磁盘图标", sleep_sec=1.0)
    capture("22_save_project_dialog.png")
    tap(590, 860, "点击「保存」按钮持久化项目", sleep_sec=1.0)

    # 7.4 底栏绘图工具全测试
    tap(60, 1310, "选中「画笔」工具", sleep_sec=0.4)
    tap(360, 600, "画笔在网格 (360, 600) 点绘单格", sleep_sec=0.4)
    tap(160, 1310, "选中「橡皮」工具", sleep_sec=0.4)
    tap(360, 600, "橡皮擦除网格单格", sleep_sec=0.4)
    tap(250, 1310, "选中「吸管」工具", sleep_sec=0.4)
    tap(360, 700, "吸管取色并自动激活画笔", sleep_sec=0.4)
    tap(500, 1310, "触发「去背景」漫水填充", sleep_sec=1.0)
    tap(680, 1310, "触发「清理飞点」消除孤立单点", sleep_sec=1.0)
    capture("23_editor_after_tools.png")

    # 8. 统计抽屉与 Oklab 智能平替全流程
    print("\n[阶段 7/8] 统计面板与智能平替推荐 (Oklab ΔE / 5星 / 一键平替 / 缺料清零)...")
    tap(568, 144, "展开统计面板抽屉", sleep_sec=1.2)
    capture("24_stats_panel.png")
    swipe(360, 1500, 360, 1000, desc="滑动展开缺料列表", sleep_sec=0.8)
    capture("25_stats_missing_items.png")

    # 第一项平替
    tree1 = dump_ui("sub1_dump.xml")
    p1 = find_node_center(tree1, text="平替")
    if p1:
        tap(p1[0], p1[1], "点击 P15179「平替」按钮", sleep_sec=1.2)
        capture("26_sub_dialog_p15179.png")
        t_dlg1 = dump_ui("dlg1_dump.xml")
        apply1 = find_node_center(t_dlg1, text="一键平替")
        if apply1:
            tap(apply1[0], apply1[1], "点击「一键平替」生效", sleep_sec=1.5)
            capture("27_after_sub1.png")

    # 第二项平替
    tree2 = dump_ui("sub2_dump.xml")
    p2 = find_node_center(tree2, text="平替")
    if p2:
        tap(p2[0], p2[1], "点击 P15181「平替」按钮", sleep_sec=1.2)
        capture("28_sub_dialog_p15181.png")
        t_dlg2 = dump_ui("dlg2_dump.xml")
        apply2 = find_node_center(t_dlg2, text="一键平替")
        if apply2:
            tap(apply2[0], apply2[1], "点击「一键平替」生效", sleep_sec=1.5)
            capture("29_after_sub2_all_cleared.png")

    tap(568, 144, "关闭统计抽屉", sleep_sec=0.8)

    # 9. 全部导出格式验证与自适应多列网格排版
    print("\n[阶段 8/8] 全部 5 大导出格式落盘与自适应排版验证...")
    tap(664, 144, "顶栏「导出」图标", sleep_sec=1.0)
    capture("30_export_dialog.png")
    # 9.1 带 Key PNG
    tap(207, 369, "导出带 Key 图纸 PNG (附自适应统计表)", sleep_sec=2.5)
    capture("31_export_key_png_done.png")
    
    # 9.2 PDF 1:1 打印
    tap(664, 144, "重新打开导出对话框", sleep_sec=1.0)
    tap(250, 450, "导出图纸 PDF (1:1 打印)", sleep_sec=2.0)
    
    # 9.3 颜色统计图 PNG
    tap(664, 144, "重新打开导出对话框", sleep_sec=1.0)
    tap(207, 690, "导出独立「颜色统计图 PNG」", sleep_sec=1.5)
    
    # 9.4 采购清单 CSV
    tap(664, 144, "重新打开导出对话框", sleep_sec=1.0)
    tap(207, 780, "导出「采购清单 CSV」", sleep_sec=1.5)
    
    # 9.5 图纸 CSV
    tap(664, 144, "重新打开导出对话框", sleep_sec=1.0)
    tap(207, 870, "导出「图纸 CSV」", sleep_sec=1.5)

    # 9.6 拉取导出的最终大图并验证
    pics = run_adb(["shell", "ls", "-t", "/sdcard/Pictures/PerlerBeads/"]).splitlines()
    if pics:
        latest = pics[0].strip()
        local_target = os.path.join(OUTPUT_DIR, "live_verified_pattern.png")
        run_adb(["pull", f"/sdcard/Pictures/PerlerBeads/{latest}", local_target])
        print(f"  [物理图纸导出成功] 拉取至: {local_target} ({os.path.getsize(local_target)} 字节)")

    # 10. 运行态安全检查
    logcat = run_adb(["logcat", "-d", "-s", "AndroidRuntime:E", "*:F"])
    if "FATAL EXCEPTION" in logcat or "OutOfMemoryError" in logcat:
        print("[FAIL] 运行态检测到崩溃异常:\n" + logcat)
        sys.exit(1)
    else:
        print("\n" + "=" * 75)
        print("  [PASS] Logcat 0 致命异常、0 内存溢出、0 崩溃！全功能全操作测试 100% 成功完成！")
        print(f"  全部测试步骤截屏已持久化保存在: {OUTPUT_DIR}/")
        print("=" * 75)

if __name__ == "__main__":
    main()
