package com.duobudget.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.duobudget.app.R

private data class Backdrop(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accentA: Color,
    val accentB: Color
)

private fun backdrop(style: ThemeStyle, dark: Boolean): Backdrop = when (style) {
    ThemeStyle.BOTANICAL -> if (dark) Backdrop(
        Color(0xFF20252C), Color(0xFF171B20), Color(0xFF0F1216), Color(0xFF4C8AC7), Color(0xFF8192A5)
    ) else Backdrop(
        Color(0xFFF8FAFD), Color(0xFFF0F4F8), Color(0xFFE7EDF4), Color(0xFF67A9E8), Color(0xFFB7C9DA)
    )

    ThemeStyle.MINIMAL -> if (dark) Backdrop(
        Color(0xFF080A0D), Color(0xFF101317), Color(0xFF050607), Color(0xFF2D3742), Color(0xFF56626F)
    ) else Backdrop(
        Color(0xFFE8EDF2), Color(0xFFDCE3E9), Color(0xFFCBD4DC), Color(0xFF596A78), Color(0xFF8797A5)
    )

    ThemeStyle.NEON -> if (dark) Backdrop(
        Color(0xFF0D3650), Color(0xFF175A76), Color(0xFF0D2A3B), Color(0xFF7FCBE8), Color(0xFFB9D7E4)
    ) else Backdrop(
        Color(0xFF8ED8F3), Color(0xFF5EB7DB), Color(0xFF286E95), Color(0xFFEAF8FF), Color(0xFF3B718B)
    )

    ThemeStyle.FAMILY -> if (dark) Backdrop(
        Color(0xFF172419), Color(0xFF2C4931), Color(0xFF101912), Color(0xFF6D8F68), Color(0xFFD5C98F)
    ) else Backdrop(
        Color(0xFFC6D4AC), Color(0xFF7E9C73), Color(0xFF415E45), Color(0xFFF0D9A4), Color(0xFF31543A)
    )

    ThemeStyle.AURORA -> if (dark) Backdrop(
        Color(0xFF07111F), Color(0xFF151C3A), Color(0xFF070A14), Color(0xFF496FE7), Color(0xFFCB4EAE)
    ) else Backdrop(
        Color(0xFFCBD6F2), Color(0xFFA9B4DA), Color(0xFF53638C), Color(0xFF6F72E8), Color(0xFFC15B9E)
    )

    ThemeStyle.SAGE -> if (dark) Backdrop(
        Color(0xFF3B1E35), Color(0xFF9A433A), Color(0xFF27162C), Color(0xFFF6A45B), Color(0xFF7C4C97)
    ) else Backdrop(
        Color(0xFFFFBA79), Color(0xFFED6C63), Color(0xFF81466E), Color(0xFFFFD28A), Color(0xFF704A87)
    )

    ThemeStyle.MATERIAL -> if (dark) Backdrop(
        Color(0xFF023A52), Color(0xFF047B91), Color(0xFF022A3C), Color(0xFF52E2EB), Color(0xFF2E9ED0)
    ) else Backdrop(
        Color(0xFF91E5F2), Color(0xFF3BC5D8), Color(0xFF167BA2), Color(0xFFD5FFFF), Color(0xFF2FA6CF)
    )

    ThemeStyle.LUXURY -> if (dark) Backdrop(
        Color(0xFF252A31), Color(0xFF181C21), Color(0xFF111418), Color(0xFF5F6A76), Color(0xFF37414B)
    ) else Backdrop(
        Color(0xFFF5F7F9), Color(0xFFE5EAF0), Color(0xFFD6DDE5), Color(0xFFFFFFFF), Color(0xFFB7C4D0)
    )

    ThemeStyle.CORAL -> if (dark) Backdrop(
        Color(0xFF130A2D), Color(0xFF35135F), Color(0xFF09061C), Color(0xFF8B46E8), Color(0xFF3E56D9)
    ) else Backdrop(
        Color(0xFFD9C8F7), Color(0xFFB69DE7), Color(0xFF7864B9), Color(0xFF8C56D7), Color(0xFF5264CF)
    )

    ThemeStyle.CYBER -> if (dark) Backdrop(
        Color(0xFF4B321D), Color(0xFF7A542C), Color(0xFF2E1E12), Color(0xFFD69A4C), Color(0xFFB56D2E)
    ) else Backdrop(
        Color(0xFFF8D9A2), Color(0xFFDFA45A), Color(0xFFB76F31), Color(0xFFFFE3AA), Color(0xFFC57935)
    )
}

private fun photoResource(style: ThemeStyle): Int? = when (style) {
    ThemeStyle.NEON -> R.drawable.theme_mountains
    ThemeStyle.AURORA -> R.drawable.theme_city
    ThemeStyle.SAGE -> R.drawable.theme_sunset
    ThemeStyle.MATERIAL -> R.drawable.theme_ocean
    ThemeStyle.CYBER -> R.drawable.theme_sand
    else -> null
}

@Composable
fun DuoBudgetBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = when (ThemePreferences.currentMode) {
        ThemeMode.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < .45f
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = ThemePreferences.currentStyle
    val bg = backdrop(style, dark)
    val photo = photoResource(style)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg.top, bg.middle, bg.bottom)))
    ) {
        if (photo != null) {
            Image(
                painter = painterResource(photo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            val scrim = if (dark) {
                listOf(
                    Color.Black.copy(alpha = .40f),
                    Color.Black.copy(alpha = .25f),
                    Color.Black.copy(alpha = .42f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = .28f),
                    Color.White.copy(alpha = .12f),
                    Color.White.copy(alpha = .18f)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(scrim))
            )
        } else {
            StaticDecor(style = style, bg = bg, dark = dark)
        }
        content()
    }
}

@Composable
private fun BoxScope.StaticDecor(style: ThemeStyle, bg: Backdrop, dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        when (style) {
            ThemeStyle.BOTANICAL -> {
                drawCircle(
                    bg.accentA.copy(alpha = if (dark) .10f else .12f),
                    size.minDimension * .34f,
                    Offset(size.width * .88f, size.height * .12f)
                )
                drawCircle(
                    bg.accentB.copy(alpha = if (dark) .08f else .12f),
                    size.minDimension * .27f,
                    Offset(size.width * .12f, size.height * .78f)
                )
            }

            ThemeStyle.MINIMAL -> {
                drawCircle(
                    bg.accentA.copy(alpha = .14f),
                    size.minDimension * .42f,
                    Offset(size.width * .84f, size.height * .22f)
                )
                drawCircle(
                    bg.accentB.copy(alpha = .10f),
                    size.minDimension * .28f,
                    Offset(size.width * .18f, size.height * .72f)
                )
            }

            ThemeStyle.FAMILY -> {
                drawCircle(
                    bg.accentB.copy(alpha = if (dark) .16f else .26f),
                    size.minDimension * .36f,
                    Offset(size.width * .78f, size.height * .18f)
                )
                repeat(9) { i ->
                    val x = size.width * (i / 8f)
                    val h = size.height * (.20f + (i % 3) * .035f)
                    val base = size.height * (.86f + (i % 2) * .02f)
                    drawRect(
                        Color(0xFF1C3825).copy(alpha = if (dark) .74f else .66f),
                        Offset(x - 2f, base - h * .34f),
                        Size(4f, h * .34f)
                    )
                    repeat(3) { layer ->
                        val y = base - h * (.28f + layer * .20f)
                        val half = size.width * (.055f + (2 - layer) * .012f)
                        val tree = Path().apply {
                            moveTo(x, y - h * .20f)
                            lineTo(x - half, y + h * .16f)
                            lineTo(x + half, y + h * .16f)
                            close()
                        }
                        drawPath(
                            tree,
                            Color(0xFF294D31).copy(alpha = if (dark) .82f else .74f)
                        )
                    }
                }
            }

            ThemeStyle.LUXURY -> {
                drawCircle(
                    Color.White.copy(alpha = if (dark) .035f else .38f),
                    size.minDimension * .30f,
                    Offset(size.width * .88f, size.height * .18f)
                )
                drawCircle(
                    bg.accentB.copy(alpha = if (dark) .08f else .18f),
                    size.minDimension * .24f,
                    Offset(size.width * .12f, size.height * .80f)
                )
            }

            ThemeStyle.CORAL -> {
                drawCircle(
                    bg.accentA.copy(alpha = if (dark) .22f else .24f),
                    size.minDimension * .44f,
                    Offset(size.width * .76f, size.height * .20f)
                )
                drawCircle(
                    bg.accentB.copy(alpha = if (dark) .17f else .20f),
                    size.minDimension * .32f,
                    Offset(size.width * .20f, size.height * .70f)
                )
                repeat(18) { i ->
                    val x = size.width * (((i * 37) % 100) / 100f)
                    val y = size.height * (((i * 61) % 82) / 100f)
                    val radius = if (i % 3 == 0) 2.6f else 1.5f
                    drawCircle(Color.White.copy(alpha = if (dark) .48f else .32f), radius, Offset(x, y))
                }
            }

            else -> Unit
        }
    }
}
