package com.perlerbeads.generator.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationManagerTest {

    private lateinit var nav: NavigationManager

    @Before
    fun setUp() {
        nav = NavigationManager(Screen.Home)
    }

    @Test
    fun testInitialState() {
        assertEquals(Screen.Home, nav.current)
        assertFalse(nav.canGoBack)
        assertTrue(nav.getStackSnapshot().isEmpty())
        assertFalse(nav.goBack())
    }

    @Test
    fun testTextBeadsBackNavigation() {
        // 1. 首页 -> 文字拼豆
        nav.navigate(Screen.TextBeads)
        assertEquals(Screen.TextBeads, nav.current)
        assertTrue(nav.canGoBack)
        assertEquals(listOf(Screen.Home), nav.getStackSnapshot())

        // 2. 文字拼豆 -> 生成并进入编辑器
        nav.navigate(Screen.Editor)
        assertEquals(Screen.Editor, nav.current)
        assertTrue(nav.canGoBack)
        assertEquals(listOf(Screen.Home, Screen.TextBeads), nav.getStackSnapshot())

        // 3. 从编辑器点击返回 -> 必须精准返回文字拼豆页，而非首页！
        assertTrue(nav.goBack())
        assertEquals(Screen.TextBeads, nav.current)
        assertEquals(listOf(Screen.Home), nav.getStackSnapshot())

        // 4. 从文字拼豆页点击返回 -> 回到首页
        assertTrue(nav.goBack())
        assertEquals(Screen.Home, nav.current)
        assertFalse(nav.canGoBack)
        assertTrue(nav.getStackSnapshot().isEmpty())

        // 5. 首页再次回退 -> 无上一页
        assertFalse(nav.goBack())
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun testProjectsBackNavigation() {
        // 1. 首页 -> 我的项目
        nav.navigate(Screen.Projects)
        assertEquals(Screen.Projects, nav.current)

        // 2. 打开项目 -> 进入编辑器
        nav.navigate(Screen.Editor)
        assertEquals(Screen.Editor, nav.current)

        // 3. 编辑器返回 -> 必须返回我的项目列表页
        assertTrue(nav.goBack())
        assertEquals(Screen.Projects, nav.current)

        // 4. 我的项目返回 -> 回到首页
        assertTrue(nav.goBack())
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun testPhotoPipelineNavigation() {
        // 首页 -> 裁剪 -> 设置 -> 编辑器
        nav.navigate(Screen.Crop)
        nav.navigate(Screen.Settings)
        nav.navigate(Screen.Editor)
        assertEquals(listOf(Screen.Home, Screen.Crop, Screen.Settings), nav.getStackSnapshot())

        // 逐级回退
        assertTrue(nav.goBack())
        assertEquals(Screen.Settings, nav.current)

        assertTrue(nav.goBack())
        assertEquals(Screen.Crop, nav.current)

        assertTrue(nav.goBack())
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun testBoardWorkSubNavigation() {
        // 文字拼豆 -> 编辑器 -> 分板跟做
        nav.navigate(Screen.TextBeads)
        nav.navigate(Screen.Editor)
        nav.navigate(Screen.BoardWork)
        assertEquals(Screen.BoardWork, nav.current)
        assertEquals(listOf(Screen.Home, Screen.TextBeads, Screen.Editor), nav.getStackSnapshot())

        // 分板跟做返回 -> 编辑器
        assertTrue(nav.goBack())
        assertEquals(Screen.Editor, nav.current)

        // 编辑器返回 -> 文字拼豆
        assertTrue(nav.goBack())
        assertEquals(Screen.TextBeads, nav.current)
    }

    @Test
    fun testPaletteNavigationFromDifferentOrigins() {
        // 场景 A: 从首页进入色板
        nav.navigate(Screen.Palette)
        assertEquals(Screen.Palette, nav.current)
        assertTrue(nav.goBack())
        assertEquals(Screen.Home, nav.current)

        // 场景 B: 从设置页进入色板
        nav.navigate(Screen.Crop)
        nav.navigate(Screen.Settings)
        nav.navigate(Screen.Palette)
        assertEquals(Screen.Palette, nav.current)
        assertTrue(nav.goBack())
        assertEquals(Screen.Settings, nav.current)
    }

    @Test
    fun testGoHomeClearsStack() {
        nav.navigate(Screen.TextBeads)
        nav.navigate(Screen.Editor)
        nav.navigate(Screen.BoardWork)

        nav.goHome()
        assertEquals(Screen.Home, nav.current)
        assertTrue(nav.getStackSnapshot().isEmpty())
        assertFalse(nav.canGoBack)
    }

    @Test
    fun testDuplicateNavigationPrevented() {
        nav.navigate(Screen.TextBeads)
        nav.navigate(Screen.TextBeads) // 重复导航
        assertEquals(Screen.TextBeads, nav.current)
        assertEquals(listOf(Screen.Home), nav.getStackSnapshot())
    }

    @Test
    fun testEmptyStackFallbackToHome() {
        // 直接构造栈为空但在非首页的情况
        val customNav = NavigationManager(Screen.Editor)
        assertTrue(customNav.canGoBack)
        assertTrue(customNav.goBack())
        assertEquals(Screen.Home, customNav.current)
    }
}
