package com.perlerbeads.generator.navigation

/**
 * 应用全局页面导航栈管理器。
 *
 * 维护用户的前进与回退历史栈，确保无论从何处进入特定页面（例如从文字拼豆/工程库/设置页进入编辑器），
 * 点击顶栏“返回”或触发系统返回手势时，都能精确返回上一级来源页面，避免误跳至首页。
 */
class NavigationManager(initialScreen: Screen = Screen.Home) {

    private val stack = mutableListOf<Screen>()

    var current: Screen = initialScreen
        private set

    /** 是否可以回退（非首页或栈内有历史记录）。 */
    val canGoBack: Boolean
        get() = current != Screen.Home || stack.isNotEmpty()

    /** 导航到目标页面并压入当前页面至历史栈。 */
    fun navigate(target: Screen) {
        if (target == current) return
        stack.add(current)
        current = target
    }

    /** 返回首页并清空所有历史栈。 */
    fun goHome() {
        stack.clear()
        current = Screen.Home
    }

    /**
     * 弹出上一级页面并切换。
     * 若历史栈为空但当前不在首页，则保底回退至首页。
     * @return 是否成功执行了回退操作
     */
    fun goBack(): Boolean {
        // 清理栈顶可能存在的重复页面
        while (stack.isNotEmpty() && stack.last() == current) {
            stack.removeAt(stack.lastIndex)
        }
        if (stack.isNotEmpty()) {
            current = stack.removeAt(stack.lastIndex)
            return true
        }
        if (current != Screen.Home) {
            current = Screen.Home
            return true
        }
        return false
    }

    /** 获取当前历史栈的只读快照（用于单元测试与调试排查）。 */
    fun getStackSnapshot(): List<Screen> = stack.toList()
}
