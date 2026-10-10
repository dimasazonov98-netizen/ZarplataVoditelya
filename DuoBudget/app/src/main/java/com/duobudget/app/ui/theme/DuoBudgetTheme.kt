package com.duobudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun lightScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceContainer = surface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = onSurfaceVariant.copy(alpha = .62f),
    outlineVariant = onSurfaceVariant.copy(alpha = .24f),
    error = Color(0xFFB3261E)
)

private fun darkScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF101010),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color(0xFF101010),
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceContainer = surface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = onSurfaceVariant.copy(alpha = .70f),
    outlineVariant = onSurfaceVariant.copy(alpha = .30f),
    error = Color(0xFFFFB4AB)
)

private fun palette(style: ThemeStyle, dark: Boolean): ColorScheme = when (style) {
    // 1. Classic light.
    ThemeStyle.BOTANICAL -> if (dark) darkScheme(
        Color(0xFF8FC8FF), Color(0xFFB1C4D8), Color(0xFF101419), Color(0xFF1B2229), Color(0xFF26313B),
        Color(0xFFF4F8FC), Color(0xFFBBC8D4), Color(0xFF244A68), Color(0xFFD7ECFF)
    ) else lightScheme(
        Color(0xFF2C87D6), Color(0xFF607486), Color(0xFFF6F9FC), Color(0xFFFDFEFF), Color(0xFFEAF0F6),
        Color(0xFF111820), Color(0xFF5D6974), Color(0xFFDCEEFF), Color(0xFF143A59)
    )

    // 2. Classic dark / graphite.
    ThemeStyle.MINIMAL -> if (dark) darkScheme(
        Color(0xFF8FCBFF), Color(0xFF9EAAB5), Color(0xFF07090C), Color(0xFF15191E), Color(0xFF232931),
        Color(0xFFF4F7FA), Color(0xFFB6C0C9), Color(0xFF1E405D), Color(0xFFD7EDFF)
    ) else lightScheme(
        Color(0xFF3A7EAF), Color(0xFF687987), Color(0xFFE9EEF3), Color(0xFFF7F9FB), Color(0xFFDDE4EA),
        Color(0xFF11171C), Color(0xFF5F6C76), Color(0xFFD4E7F4), Color(0xFF1A3B52)
    )

    // 3. Mountain landscape.
    ThemeStyle.NEON -> if (dark) darkScheme(
        Color(0xFF91D8F4), Color(0xFFAFC8D6), Color(0xFF092737), Color(0xFF123A4D), Color(0xFF1E5063),
        Color(0xFFF4FBFF), Color(0xFFC1D5DF), Color(0xFF245D75), Color(0xFFD9F3FF)
    ) else lightScheme(
        Color(0xFF137EA8), Color(0xFF4A7084), Color(0xFFDFF4FD), Color(0xFFF2FBFF), Color(0xFFD0EAF5),
        Color(0xFF0B2633), Color(0xFF4D6977), Color(0xFFCBEFFF), Color(0xFF083E55)
    )

    // 4. Forest nature.
    ThemeStyle.FAMILY -> if (dark) darkScheme(
        Color(0xFFAAD09B), Color(0xFFE6D5A5), Color(0xFF101B13), Color(0xFF203127), Color(0xFF31493A),
        Color(0xFFF5FAF3), Color(0xFFC2D1C0), Color(0xFF36563B), Color(0xFFE0F2D8)
    ) else lightScheme(
        Color(0xFF4F7C52), Color(0xFF927A4E), Color(0xFFDDE6D1), Color(0xFFF0F4E8), Color(0xFFD0DDC8),
        Color(0xFF152019), Color(0xFF566557), Color(0xFFD9EBD3), Color(0xFF213E25)
    )

    // 5. City chic.
    ThemeStyle.AURORA -> if (dark) darkScheme(
        Color(0xFF8FA7FF), Color(0xFFF38BD5), Color(0xFF070B16), Color(0xFF151A2C), Color(0xFF242B45),
        Color(0xFFF7F8FF), Color(0xFFBDC3DB), Color(0xFF32477A), Color(0xFFDCE3FF)
    ) else lightScheme(
        Color(0xFF5267D8), Color(0xFFAE4A91), Color(0xFFDCE3F3), Color(0xFFF2F5FC), Color(0xFFCFD8EC),
        Color(0xFF151A2D), Color(0xFF5D657D), Color(0xFFD8E0FF), Color(0xFF26366C)
    )

    // 6. Sunset.
    ThemeStyle.SAGE -> if (dark) darkScheme(
        Color(0xFFFFB274), Color(0xFFD3A1F2), Color(0xFF27162C), Color(0xFF42293A), Color(0xFF5C3A4E),
        Color(0xFFFFF6F0), Color(0xFFE0C2CE), Color(0xFF70412C), Color(0xFFFFDFC9)
    ) else lightScheme(
        Color(0xFFD55F43), Color(0xFF7657A0), Color(0xFFFFD6B9), Color(0xFFFFE9D8), Color(0xFFF2C5B6),
        Color(0xFF321B21), Color(0xFF75575D), Color(0xFFFFD8C4), Color(0xFF5F2819)
    )

    // 7. Ocean.
    ThemeStyle.MATERIAL -> if (dark) darkScheme(
        Color(0xFF6BEAF1), Color(0xFF7CCBFF), Color(0xFF022736), Color(0xFF073D50), Color(0xFF0C5368),
        Color(0xFFF1FDFF), Color(0xFFB8D9E0), Color(0xFF075E70), Color(0xFFD4FCFF)
    ) else lightScheme(
        Color(0xFF087FA5), Color(0xFF2B84B8), Color(0xFFCFF4F8), Color(0xFFE7FBFD), Color(0xFFBDE7EC),
        Color(0xFF082830), Color(0xFF4B6E76), Color(0xFFC6F7FA), Color(0xFF064B5E)
    )

    // 8. Minimalism.
    ThemeStyle.LUXURY -> if (dark) darkScheme(
        Color(0xFF9EC8E9), Color(0xFFB5C0CA), Color(0xFF111418), Color(0xFF20252B), Color(0xFF30373E),
        Color(0xFFF6F8FA), Color(0xFFC2CBD3), Color(0xFF33495D), Color(0xFFDCEAF4)
    ) else lightScheme(
        Color(0xFF347DB7), Color(0xFF6C7D8B), Color(0xFFF2F5F8), Color(0xFFFCFDFE), Color(0xFFE5EAF0),
        Color(0xFF11161A), Color(0xFF65717B), Color(0xFFDDEBF6), Color(0xFF173A54)
    )

    // 9. Purple night.
    ThemeStyle.CORAL -> if (dark) darkScheme(
        Color(0xFFC18AFF), Color(0xFF8EA2FF), Color(0xFF09061C), Color(0xFF20113D), Color(0xFF35205A),
        Color(0xFFF9F5FF), Color(0xFFCFC1E1), Color(0xFF57318A), Color(0xFFF0DDFF)
    ) else lightScheme(
        Color(0xFF7946B6), Color(0xFF5066C7), Color(0xFFE5D9F5), Color(0xFFF4EEFB), Color(0xFFD8C9EA),
        Color(0xFF23162E), Color(0xFF6D5A79), Color(0xFFE8D7FA), Color(0xFF442464)
    )

    // 10. Golden sand.
    ThemeStyle.CYBER -> if (dark) darkScheme(
        Color(0xFFE9B168), Color(0xFFD18D50), Color(0xFF2E1E12), Color(0xFF4C321D), Color(0xFF654225),
        Color(0xFFFFF8EF), Color(0xFFDEC7AD), Color(0xFF78451E), Color(0xFFFFE3BD)
    ) else lightScheme(
        Color(0xFFAC5F27), Color(0xFF7B6042), Color(0xFFF8DDAF), Color(0xFFFFF2DB), Color(0xFFEBC99D),
        Color(0xFF2C1D11), Color(0xFF765F4A), Color(0xFFFFDDB2), Color(0xFF5B2D10)
    )
}

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun DuoBudgetTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePreferences.currentMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = palette(ThemePreferences.currentStyle, dark),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
