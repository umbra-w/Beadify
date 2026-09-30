package com.perlerbeads.generator.navigation

/** 应用内页面。状态由 AppViewModel 持有，因此路由无需携带参数。 */
sealed interface Screen {
    data object Home : Screen
    data object Crop : Screen
    data object Settings : Screen
    data object Editor : Screen
    data object Palette : Screen
    data object BoardWork : Screen
    data object TextBeads : Screen
    data object Projects : Screen

    /** 返回上一页默认映射（仅作为无堆栈时的保底回退）。Home 无上一页。 */
    fun back(): Screen? = when (this) {
        Crop -> Home
        Settings -> Crop
        Editor -> Home
        Palette -> Home
        BoardWork -> Editor
        TextBeads -> Home
        Projects -> Home
        Home -> null
    }
}
