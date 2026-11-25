package com.mkassis37.sehati.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class AppearanceSettings(
    val palette: SehatiPalette = SehatiPalette.MEDICAL_TEAL,
    val mode: ThemeMode = ThemeMode.SYSTEM
)

enum class ThemeMode(val title: String) {
    LIGHT("فاتح"), DARK("داكن"), SYSTEM("حسب إعدادات الهاتف");

    fun useDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemIsDark
    }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Eight preset palettes that share fixed semantic error and success colors. */
enum class SehatiPalette(
    val title: String,
    val primarySeed: Color,
    val secondarySeed: Color,
    val tertiarySeed: Color
) {
    MEDICAL_TEAL("تركوازي طبي", Color(0xFF075E63), Color(0xFF426B70), Color(0xFF41645A)),
    CALM_BLUE("أزرق هادئ", Color(0xFF315AA8), Color(0xFF52688F), Color(0xFF45675F)),
    NATURAL_GREEN("أخضر طبيعي", Color(0xFF32633A), Color(0xFF526A50), Color(0xFF52634E)),
    VIOLET("بنفسجي", Color(0xFF6650A4), Color(0xFF655A7D), Color(0xFF76516C)),
    SOFT_ROSE("وردي هادئ", Color(0xFF874A60), Color(0xFF765A62), Color(0xFF735A48)),
    NAVY("كحلي", Color(0xFF405A82), Color(0xFF586477), Color(0xFF4D655E)),
    ELEGANT_GRAY("رمادي أنيق", Color(0xFF555B60), Color(0xFF5C6063), Color(0xFF59635D)),
    WARM_ORANGE("برتقالي دافئ", Color(0xFF864B00), Color(0xFF735B42), Color(0xFF626047));

    companion object {
        fun fromStoredValue(value: String?): SehatiPalette = entries.firstOrNull { it.name == value } ?: MEDICAL_TEAL
    }
}

@Composable
fun SehatiTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    systemIsDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val darkTheme = appearance.mode.useDarkTheme(systemIsDark)
    MaterialTheme(
        colorScheme = appearance.palette.colorScheme(darkTheme),
        content = content
    )
}

private fun SehatiPalette.colorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = mix(primarySeed, Color.White, 0.43f),
        onPrimary = mix(Color(0xFF101919), primarySeed, 0.18f),
        primaryContainer = mix(Color(0xFF263536), primarySeed, 0.24f),
        onPrimaryContainer = mix(Color(0xFFE8F2F0), primarySeed, 0.18f),
        secondary = mix(secondarySeed, Color.White, 0.40f),
        onSecondary = mix(Color(0xFF101919), secondarySeed, 0.16f),
        secondaryContainer = mix(Color(0xFF2D3939), secondarySeed, 0.22f),
        onSecondaryContainer = mix(Color(0xFFE5ECEA), secondarySeed, 0.17f),
        tertiary = mix(tertiarySeed, Color.White, 0.42f),
        onTertiary = Color(0xFF10201D),
        tertiaryContainer = mix(Color(0xFF293A35), tertiarySeed, 0.24f),
        onTertiaryContainer = mix(Color(0xFFE5F0E9), tertiarySeed, 0.17f),
        background = mix(Color(0xFF101919), primarySeed, 0.035f),
        onBackground = Color(0xFFE0E5E4),
        surface = mix(Color(0xFF172121), primarySeed, 0.045f),
        onSurface = Color(0xFFE0E5E4),
        surfaceVariant = mix(Color(0xFF3F4948), primarySeed, 0.07f),
        onSurfaceVariant = Color(0xFFC0C9C7),
        outline = Color(0xFF899391),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
} else {
    lightColorScheme(
        primary = primarySeed,
        onPrimary = Color.White,
        primaryContainer = mix(Color(0xFFEAF1EF), primarySeed, 0.12f),
        onPrimaryContainer = mix(primarySeed, Color.Black, 0.12f),
        secondary = secondarySeed,
        onSecondary = Color.White,
        secondaryContainer = mix(Color(0xFFE9EFED), secondarySeed, 0.11f),
        onSecondaryContainer = mix(secondarySeed, Color.Black, 0.10f),
        tertiary = tertiarySeed,
        onTertiary = Color.White,
        tertiaryContainer = mix(Color(0xFFEDF1EC), tertiarySeed, 0.12f),
        onTertiaryContainer = mix(tertiarySeed, Color.Black, 0.10f),
        background = mix(Color(0xFFF7FAF9), primarySeed, 0.018f),
        onBackground = Color(0xFF191D1D),
        surface = mix(Color.White, primarySeed, 0.012f),
        onSurface = Color(0xFF191D1D),
        surfaceVariant = mix(Color(0xFFE1E9E7), primarySeed, 0.055f),
        onSurfaceVariant = Color(0xFF414A49),
        outline = Color(0xFF717A78),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

private fun mix(base: Color, tint: Color, tintAmount: Float): Color = Color(
    red = base.red * (1f - tintAmount) + tint.red * tintAmount,
    green = base.green * (1f - tintAmount) + tint.green * tintAmount,
    blue = base.blue * (1f - tintAmount) + tint.blue * tintAmount,
    alpha = 1f
)
