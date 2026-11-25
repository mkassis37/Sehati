package com.mkassis37.sehati.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SehatiLightColors = lightColorScheme(
    primary = Color(0xFF075E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0F0EE),
    onPrimaryContainer = Color(0xFF002F32),
    secondary = Color(0xFF426B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC5E7E8),
    onSecondaryContainer = Color(0xFF0B3438),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF191D1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191D1D),
    surfaceVariant = Color(0xFFE1E9E7),
    onSurfaceVariant = Color(0xFF414A49),
    error = Color(0xFFBA1A1A)
)

private val SehatiDarkColors = darkColorScheme(
    primary = Color(0xFF8AD3D2),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFFA6F0EF),
    secondary = Color(0xFFA9CFD1),
    onSecondary = Color(0xFF183638),
    secondaryContainer = Color(0xFF2E4D50),
    onSecondaryContainer = Color(0xFFC5E7E8),
    background = Color(0xFF101919),
    onBackground = Color(0xFFE0E5E4),
    surface = Color(0xFF172121),
    onSurface = Color(0xFFE0E5E4),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFC0C9C7),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SehatiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SehatiDarkColors else SehatiLightColors,
        content = content
    )
}
