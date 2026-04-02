package com.wealth.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    secondary = TextSecondary,
    error = Warning,
    outline = Outline
)

@Composable
fun WealthManagerTheme(
    primaryColor: Color = Primary,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = getLightColorScheme(primaryColor),
        typography = Typography,
        content = content
    )
}
