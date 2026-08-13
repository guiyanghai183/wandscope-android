package com.guiyanghai.wandscope

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WandColors {
    val Accent = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val ChartPalette = listOf(Accent, Green, Orange, Purple, Red, Teal)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF111114),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF005BB5),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF17171A),
    surface = Color.White,
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF66666D),
    outline = Color(0xFFD8D8DD),
    error = WandColors.Red,
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF111114),
    primaryContainer = Color(0xFF123A62),
    onPrimaryContainer = Color(0xFFB8DBFF),
    background = Color(0xFF0D0D0F),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF1B1B1E),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA8A8AF),
    outline = Color(0xFF3A3A3F),
    error = WandColors.Red,
)

@Composable
fun WandScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 31.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        ),
        content = content,
    )
}
