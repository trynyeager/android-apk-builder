package com.example.sampleapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GhCanvasDark = Color(0xFF0D1117)
val GhCardBorder = Color(0xFF30363D)
val GhCardBg = Color(0xFF161B22)
val GhTextPrimary = Color(0xFFC9D1D9)
val GhTextMuted = Color(0xFF8B949E)
val GhGreenAccent = Color(0xFF2EA043)
val GhGreenDark = Color(0xFF238636)
val GhRedAccent = Color(0xFFDA3633)

private val GhColorScheme = darkColorScheme(
    primary = GhGreenAccent,
    secondary = GhGreenDark,
    background = GhCanvasDark,
    surface = GhCardBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GhTextPrimary,
    onSurface = GhTextPrimary
)

@Composable
fun JeeFocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GhColorScheme,
        content = content
    )
}