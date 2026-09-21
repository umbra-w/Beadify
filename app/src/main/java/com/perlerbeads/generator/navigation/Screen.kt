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

    /** 返回上一页。Home 无上一页。 */
    fun back(): Screen? = when (this) {
        Crop -> Home
        Settings -> Crop
        Editor -> Settings
        Palette -> Settings
        BoardWork -> Editor
        TextBeads -> Home
        Home -> null
    }
}
