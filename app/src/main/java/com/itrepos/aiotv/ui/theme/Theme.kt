package com.itrepos.aiotv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = OnBackground,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = OnSurface,
    secondary = AccentSecondary,
    onSecondary = OnBackground,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceCard,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    error = AccentSecondary,
)

@Composable
fun AioTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AioTypography,
        content = content
    )
}
