package com.duobudget.app.ui.theme

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.rotate

private data class Backdrop(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accentA: Color,
    val accentB: Color
)

private fun backdrop(style: ThemeStyle, dark: Boolean): Backdrop = when (style) {
    ThemeStyle.BOTANICAL -> if (dark) Backdrop(Color(0xFF4F4438), Color(0xFF3E493D), Color(0xFF202820), Color(0xFF6F8265), Color(0xFF7F9271))
    else Backdrop(Color(0xFFE2CBA9), Color(0xFF9EAA90), Color(0xFF697660), Color(0xFF718168), Color(0xFFAEB991))

    ThemeStyle.MINIMAL -> if (dark) Backdrop(Color(0xFF111412), Color(0xFF151A17), Color(0xFF0E1110), Color(0xFF25342A), Color(0xFF314138))
    else Backdrop(Color(0xFFFCFDFC), Color(0xFFF5F8F5), Color(0xFFEDF2EE), Color(0xFFDCE9DE), Color(0xFFE8EFEA))

    ThemeStyle.NEON -> if (dark) Backdrop(Color(0xFF061014), Color(0xFF0A2022), Color(0xFF07131B), Color(0xFF12D9A7), Color(0xFF2B9EFF))
    else Backdrop(Color(0xFFF1FBF9), Color(0xFFDFF7F2), Color(0xFFD8EFF7), Color(0xFF5BE6C2), Color(0xFF6EB7FF))

    ThemeStyle.FAMILY -> if (dark) Backdrop(Color(0xFF2A1D19), Color(0xFF352A22), Color(0xFF1E1915), Color(0xFFD87958), Color(0xFF7B8D64))
    else Backdrop(Color(0xFFFFE9D5), Color(0xFFF6E6CF), Color(0xFFE7E6CF), Color(0xFFE68C6C), Color(0xFF95A777))

    ThemeStyle.AURORA -> if (dark) Backdrop(Color(0xFF14142B), Color(0xFF202350), Color(0xFF0F1830), Color(0xFF7B63F5), Color(0xFF38C9BA))
    else Backdrop(Color(0xFFDDE5FF), Color(0xFFE5E0FF), Color(0xFFD8F5EF), Color(0xFF7E6CF2), Color(0xFF50C7B8))

    ThemeStyle.SAGE -> if (dark) Backdrop(Color(0xFF20271F), Color(0xFF2A3329), Color(0xFF171D17), Color(0xFF718B72), Color(0xFFA28B70))
    else Backdrop(Color(0xFFEDEDE2), Color(0xFFDDE4D7), Color(0xFFCCD6C9), Color(0xFF819982), Color(0xFFBBA58A))

    ThemeStyle.MATERIAL -> if (dark) Backdrop(Color(0xFF191620), Color(0xFF2A223B), Color(0xFF171A25), Color(0xFF8D78E8), Color(0xFF4DC3AD))
    else Backdrop(Color(0xFFF0E8FF), Color(0xFFE8F4FF), Color(0xFFE9F8EE), Color(0xFF866CE4), Color(0xFF4AB8A2))

    ThemeStyle.LUXURY -> if (dark) Backdrop(Color(0xFF120F0A), Color(0xFF241C10), Color(0xFF0B0907), Color(0xFFC99C45), Color(0xFF7F6A40))
    else Backdrop(Color(0xFFF6E9CE), Color(0xFFEADBC0), Color(0xFFD9C6A4), Color(0xFFC89A43), Color(0xFF8E7548))

    ThemeStyle.CORAL -> if (dark) Backdrop(Color(0xFF261718), Color(0xFF342326), Color(0xFF151D1D), Color(0xFFFF6E5D), Color(0xFF2EC1B5))
    else Backdrop(Color(0xFFFFDDD4), Color(0xFFFFEAD8), Color(0xFFD9F4EB), Color(0xFFFF765F), Color(0xFF2DB8A9))

    ThemeStyle.CYBER -> if (dark) Backdrop(Color(0xFF06101D), Color(0xFF0B2440), Color(0xFF071426), Color(0xFF00C8F4), Color(0xFF00E0B8))
    else Backdrop(Color(0xFFE6F6FF), Color(0xFFDCEEFF), Color(0xFFD9FAF4), Color(0xFF16BFE8), Color(0xFF22CFAF))
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(bg.top, bg.middle, bg.bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                ThemeStyle.MINIMAL -> Unit
                ThemeStyle.BOTANICAL, ThemeStyle.SAGE, ThemeStyle.FAMILY -> {
                    fun leaf(x: Float, y: Float, w: Float, h: Float, angle: Float, color: Color) {
                        val center = Offset(x, y)
                        rotate(angle, center) {
                            drawOval(
                                color.copy(alpha = if (dark) .36f else .30f),
                                topLeft = Offset(x - w / 2, y - h / 2),
                                size = Size(w, h)
                            )
                        }
                    }
                    leaf(size.width * .92f, size.height * .16f, size.width * .42f, size.height * .08f, -24f, bg.accentA)
                    leaf(size.width * .78f, size.height * .27f, size.width * .34f, size.height * .07f, 22f, bg.accentB)
                    leaf(size.width * .08f, size.height * .70f, size.width * .45f, size.height * .08f, 26f, bg.accentA)
                }
                else -> {
                    drawCircle(bg.accentA.copy(alpha = if (dark) .24f else .20f), size.minDimension * .44f, Offset(size.width * .82f, size.height * .18f))
                    drawCircle(bg.accentB.copy(alpha = if (dark) .20f else .17f), size.minDimension * .34f, Offset(size.width * .12f, size.height * .72f))
                    drawCircle(bg.accentA.copy(alpha = if (dark) .14f else .12f), size.minDimension * .25f, Offset(size.width * .58f, size.height * .46f))
                }
            }
        }
        content()
    }
}

private fun Color.luminance(): Float =
    (red * .2126f) + (green * .7152f) + (blue * .0722f)
