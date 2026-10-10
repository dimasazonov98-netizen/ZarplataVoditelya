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
    outlineVariant = onSurfaceVariant.copy(alpha = .28f),
    error = Color(0xFFFFB4AB)
)

private fun palette(style: ThemeStyle, dark: Boolean): ColorScheme = when (style) {
    ThemeStyle.BOTANICAL -> if (dark) darkScheme(
        Color(0xFFDF8969), Color(0xFFA8BA9E), Color(0xFF171311), Color(0xFF2A231F), Color(0xFF453A34),
        Color(0xFFFFF7F1), Color(0xFFD2C2B8), Color(0xFF693522), Color(0xFFFFDBCD)
    ) else lightScheme(
        Color(0xFFA95036), Color(0xFF64745D), Color(0xFFE9E1D5), Color(0xFFF9F2E9), Color(0xFFE8DDD2),
        Color(0xFF211914), Color(0xFF594C43), Color(0xFFF2D6C7), Color(0xFF3B1B11)
    )

    ThemeStyle.MINIMAL -> if (dark) darkScheme(
        Color(0xFF8CCF9A), Color(0xFFA7B8AC), Color(0xFF111412), Color(0xFF1B211D), Color(0xFF28312B),
        Color(0xFFF3F7F4), Color(0xFFBAC5BD), Color(0xFF27472F), Color(0xFFD8F7DF)
    ) else lightScheme(
        Color(0xFF2F8F4E), Color(0xFF637168), Color(0xFFF8FAF8), Color(0xFFFFFFFF), Color(0xFFF0F3F0),
        Color(0xFF121714), Color(0xFF687069), Color(0xFFDDF2E2), Color(0xFF14351F)
    )

    ThemeStyle.NEON -> if (dark) darkScheme(
        Color(0xFF4FF2C2), Color(0xFF55B7FF), Color(0xFF071014), Color(0xFF0D1A20), Color(0xFF122831),
        Color(0xFFF1FCFF), Color(0xFFA8C2CC), Color(0xFF093C32), Color(0xFFB8FFE8)
    ) else lightScheme(
        Color(0xFF007F6B), Color(0xFF176D9A), Color(0xFFF3FBFA), Color(0xFFFFFFFF), Color(0xFFE3F5F2),
        Color(0xFF0B1F1C), Color(0xFF4E6C67), Color(0xFFC8F7EC), Color(0xFF003C32)
    )

    ThemeStyle.FAMILY -> if (dark) darkScheme(
        Color(0xFFFF9A77), Color(0xFFB7C59A), Color(0xFF211815), Color(0xFF30231F), Color(0xFF47352E),
        Color(0xFFFFF3EC), Color(0xFFD8C3B8), Color(0xFF6B392C), Color(0xFFFFD8C7)
    ) else lightScheme(
        Color(0xFFC95D3D), Color(0xFF77855E), Color(0xFFFFF5E8), Color(0xFFFFFBF4), Color(0xFFF4E8D8),
        Color(0xFF2A1C17), Color(0xFF6F5B50), Color(0xFFFBD9CB), Color(0xFF4A2117)
    )

    ThemeStyle.AURORA -> if (dark) darkScheme(
        Color(0xFF8EA8FF), Color(0xFF7DE8D0), Color(0xFF0F1022), Color(0xFF181A31), Color(0xFF262944),
        Color(0xFFF8F8FF), Color(0xFFBEC2DF), Color(0xFF27386E), Color(0xFFDCE3FF)
    ) else lightScheme(
        Color(0xFF5267E9), Color(0xFF278D83), Color(0xFFF3F5FF), Color(0xFFFFFFFF), Color(0xFFE7EAFC),
        Color(0xFF171932), Color(0xFF5C607C), Color(0xFFDDE3FF), Color(0xFF1A286F)
    )

    ThemeStyle.SAGE -> if (dark) darkScheme(
        Color(0xFFA8C7A6), Color(0xFFD0BA9B), Color(0xFF171C17), Color(0xFF222922), Color(0xFF313B32),
        Color(0xFFF2F7F1), Color(0xFFBCC8BA), Color(0xFF38513B), Color(0xFFD9EED8)
    ) else lightScheme(
        Color(0xFF64836B), Color(0xFF9A7655), Color(0xFFF3F3EA), Color(0xFFFCFBF4), Color(0xFFE7E8DC),
        Color(0xFF1E251F), Color(0xFF60695F), Color(0xFFDDE8D8), Color(0xFF2A402E)
    )

    ThemeStyle.MATERIAL -> if (dark) darkScheme(
        Color(0xFFC3B3FF), Color(0xFF80D8C4), Color(0xFF17151F), Color(0xFF221F2C), Color(0xFF342F41),
        Color(0xFFF8F5FF), Color(0xFFC9C2D4), Color(0xFF463A73), Color(0xFFE9E0FF)
    ) else lightScheme(
        Color(0xFF6E56CF), Color(0xFF2E8D7E), Color(0xFFF8F6FF), Color(0xFFFFFFFF), Color(0xFFEDE8F8),
        Color(0xFF201A2B), Color(0xFF685F74), Color(0xFFE8DEFF), Color(0xFF2C1E66)
    )

    ThemeStyle.LUXURY -> if (dark) darkScheme(
        Color(0xFFE5BF6A), Color(0xFFC5A86A), Color(0xFF0E0C09), Color(0xFF1B1711), Color(0xFF30291D),
        Color(0xFFFFF7E9), Color(0xFFD4C6AA), Color(0xFF5B461C), Color(0xFFFFE2A1)
    ) else lightScheme(
        Color(0xFF9A6B18), Color(0xFF5E5135), Color(0xFFF5EFE3), Color(0xFFFFFBF3), Color(0xFFEAE0CD),
        Color(0xFF221B10), Color(0xFF6B604D), Color(0xFFF1D9A5), Color(0xFF3F2B07)
    )

    ThemeStyle.CORAL -> if (dark) darkScheme(
        Color(0xFFFF8B79), Color(0xFF53D3C5), Color(0xFF1B1415), Color(0xFF2A1D1F), Color(0xFF3F2B2D),
        Color(0xFFFFF5F2), Color(0xFFDCC3C0), Color(0xFF6D322A), Color(0xFFFFD8D1)
    ) else lightScheme(
        Color(0xFFFF5F52), Color(0xFF159D91), Color(0xFFFFF6F1), Color(0xFFFFFFFF), Color(0xFFF8E8E1),
        Color(0xFF2B1916), Color(0xFF75615D), Color(0xFFFFDDD7), Color(0xFF5E211B)
    )

    ThemeStyle.CYBER -> if (dark) darkScheme(
        Color(0xFF37D8FF), Color(0xFF28F0D0), Color(0xFF05101F), Color(0xFF0A1A2D), Color(0xFF102B46),
        Color(0xFFF2FAFF), Color(0xFFA7C4D8), Color(0xFF063D55), Color(0xFFB9F1FF)
    ) else lightScheme(
        Color(0xFF0078A8), Color(0xFF008B78), Color(0xFFF0F8FF), Color(0xFFFFFFFF), Color(0xFFDDEEF7),
        Color(0xFF071D2A), Color(0xFF4F6977), Color(0xFFCDEFFF), Color(0xFF00394E)
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
