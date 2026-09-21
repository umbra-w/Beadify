package com.perlerbeads.generator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFE8590C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9C2),
    onPrimaryContainer = Color(0xFF582100),
    secondary = Color(0xFF77574A),
    background = Color(0xFFFFF8F5),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5E1D00),
    primaryContainer = Color(0xFF7B3200),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE7BEAE),
    background = Color(0xFF1E130E),
    surface = Color(0xFF1E130E),
)

@Composable
fun PerlerBeadsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
