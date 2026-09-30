package com.perlerbeads.generator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.perlerbeads.generator.navigation.Screen
import com.perlerbeads.generator.ui.board.BoardWorkScreen
import com.perlerbeads.generator.ui.crop.CropScreen
import com.perlerbeads.generator.ui.editor.AppViewModel
import com.perlerbeads.generator.ui.editor.EditorScreen
import com.perlerbeads.generator.ui.home.HomeScreen
import com.perlerbeads.generator.ui.palette.PaletteManagerScreen
import com.perlerbeads.generator.ui.projects.ProjectsScreen
import com.perlerbeads.generator.ui.settings.SettingsScreen
import com.perlerbeads.generator.ui.text.TextBeadsScreen
import com.perlerbeads.generator.ui.theme.PerlerBeadsTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            PerlerBeadsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 系统返回键 / 左滑手势 → 精准返回上一功能页（由全局返回栈管理）
                    BackHandler(vm.canGoBack()) {
                        vm.goBack()
                    }

                    when (vm.screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Crop -> CropScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.Editor -> EditorScreen(vm)
                        Screen.Palette -> PaletteManagerScreen(vm)
                        Screen.BoardWork -> BoardWorkScreen(vm)
                        Screen.TextBeads -> TextBeadsScreen(vm)
                        Screen.Projects -> ProjectsScreen(vm)
                    }
                }
            }
        }
    }
}
