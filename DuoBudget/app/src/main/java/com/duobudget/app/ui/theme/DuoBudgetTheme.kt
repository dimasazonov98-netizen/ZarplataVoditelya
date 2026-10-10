package com.duobudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Light = lightColorScheme(
    primary = Color(0xFFA95036), onPrimary = Color.White,
    primaryContainer = Color(0xFFF2D6C7), onPrimaryContainer = Color(0xFF3B1B11),
    secondary = Color(0xFF64745D), onSecondary = Color.White,
    background = Color(0xFFE9E1D5), onBackground = Color(0xFF211914),
    surface = Color(0xFFF9F2E9), onSurface = Color(0xFF211914),
    surfaceContainer = Color(0xD9FFF9F2), surfaceVariant = Color(0xFFE8DDD2),
    onSurfaceVariant = Color(0xFF594C43), outline = Color(0xFF8E7F75),
    outlineVariant = Color(0x66766860), error = Color(0xFFB3261E)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFDF8969), onPrimary = Color(0xFF2A160F),
    primaryContainer = Color(0xFF693522), onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = Color(0xFFA8BA9E), onSecondary = Color(0xFF182015),
    background = Color(0xFF171311), onBackground = Color(0xFFFFF7F1),
    surface = Color(0xFF2A231F), onSurface = Color(0xFFFFF7F1),
    surfaceContainer = Color(0xD9342B27), surfaceVariant = Color(0xFF453A34),
    onSurfaceVariant = Color(0xFFD2C2B8), outline = Color(0xFFAA9A90),
    outlineVariant = Color(0x66D2C2B8), error = Color(0xFFFFB4AB)
)

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
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun DuoBudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
