package com.duobudget.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

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

    ThemeStyle.LUXURY -> if (dark) Backdrop(Color(0xFF160F08), Color(0xFF3B2811), Color(0xFF1C1208), Color(0xFFD9AA4E), Color(0xFF9C6B2B))
    else Backdrop(Color(0xFFFFE5B5), Color(0xFFE8BC6A), Color(0xFFB87931), Color(0xFFF4D58B), Color(0xFFB2752E))

    ThemeStyle.CORAL -> if (dark) Backdrop(Color(0xFF261718), Color(0xFF342326), Color(0xFF151D1D), Color(0xFFFF6E5D), Color(0xFF2EC1B5))
    else Backdrop(Color(0xFFFFDDD4), Color(0xFFFFEAD8), Color(0xFFD9F4EB), Color(0xFFFF765F), Color(0xFF2DB8A9))

    ThemeStyle.CYBER -> if (dark) Backdrop(Color(0xFF031521), Color(0xFF063754), Color(0xFF052438), Color(0xFF17C6F3), Color(0xFF63E6E2))
    else Backdrop(Color(0xFFBCEFFF), Color(0xFF66D1E9), Color(0xFF2384B3), Color(0xFFC4FAFF), Color(0xFF42E1D2))
}

@Composable
fun DuoBudgetBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = when (ThemePreferences.currentMode) {
        ThemeMode.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < .45f
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = ThemePreferences.currentStyle
    val animated = ThemePreferences.animatedBackground
    val bg = backdrop(style, dark)

    val phase: Float
    val phaseSlow: Float
    if (animated) {
        val motion = rememberInfiniteTransition(label = "theme-background")
        val p by motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "theme-phase"
        )
        val s by motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(19_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "theme-phase-slow"
        )
        phase = p
        phaseSlow = s
    } else {
        phase = 0.18f
        phaseSlow = 0.34f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(bg.top, bg.middle, bg.bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                ThemeStyle.MINIMAL -> {
                    drawCircle(
                        bg.accentA.copy(alpha = if (dark) .08f else .10f),
                        size.minDimension * .26f,
                        Offset(size.width * .82f, size.height * .18f)
                    )
                }

                ThemeStyle.BOTANICAL, ThemeStyle.SAGE, ThemeStyle.FAMILY -> {
                    val sway = sin(phase * Math.PI.toFloat() * 2f) * 10f
                    fun leaf(x: Float, y: Float, w: Float, h: Float, angle: Float, color: Color) {
                        val center = Offset(x, y)
                        rotate(angle + sway, center) {
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

                ThemeStyle.LUXURY -> {
                    val drift = phaseSlow * size.width * .34f
                    drawOval(
                        bg.accentA.copy(alpha = if (dark) .30f else .42f),
                        topLeft = Offset(-size.width * .18f + drift, size.height * .56f),
                        size = Size(size.width * 1.18f, size.height * .42f)
                    )
                    drawOval(
                        bg.accentB.copy(alpha = if (dark) .30f else .38f),
                        topLeft = Offset(-size.width * .48f + drift * .55f, size.height * .68f),
                        size = Size(size.width * 1.36f, size.height * .34f)
                    )
                    repeat(22) { i ->
                        val x = ((i * 47f + phase * 430f) % 120f) / 120f * size.width
                        val yBase = ((i * 29f) % 100f) / 100f * size.height
                        val y = (yBase + sin((phase * 6.283f) + i) * 18f).coerceIn(0f, size.height)
                        val r = 1.2f + (i % 3) * .7f
                        drawCircle(
                            Color.White.copy(alpha = if (dark) .18f else .36f),
                            r,
                            Offset(x, y)
                        )
                    }
                }

                ThemeStyle.CYBER -> {
                    repeat(6) { i ->
                        val baseY = size.height * (.12f + i * .145f)
                        val amp = size.height * (.012f + i * .002f)
                        val p = Path()
                        val shift = phase * size.width
                        p.moveTo(-size.width * .20f, baseY)
                        var x = -size.width * .20f
                        while (x < size.width * 1.2f) {
                            val next = x + size.width * .18f
                            val wave = sin(((x + shift) / size.width) * Math.PI.toFloat() * 4f + i) * amp
                            val waveNext = sin(((next + shift) / size.width) * Math.PI.toFloat() * 4f + i) * amp
                            p.cubicTo(
                                x + size.width * .06f,
                                baseY + wave,
                                x + size.width * .12f,
                                baseY + waveNext,
                                next,
                                baseY + waveNext
                            )
                            x = next
                        }
                        drawPath(
                            p,
                            color = Color.White.copy(alpha = if (dark) .10f else .20f),
                            style = Stroke(width = 2f + i * .25f)
                        )
                    }
                    drawCircle(
                        bg.accentA.copy(alpha = if (dark) .22f else .30f),
                        size.minDimension * .34f,
                        Offset(size.width * (.18f + phaseSlow * .64f), size.height * .24f)
                    )
                    repeat(10) { i ->
                        val x = ((i * 61f + phaseSlow * 300f) % 110f) / 110f * size.width
                        val y = size.height * (.18f + ((i * 13f) % 68f) / 100f)
                        drawCircle(Color.White.copy(alpha = .20f), 2f + (i % 3), Offset(x, y))
                    }
                }

                ThemeStyle.AURORA -> {
                    val xA = size.width * (.15f + phaseSlow * .70f)
                    val xB = size.width * (.85f - phase * .70f)
                    drawCircle(bg.accentA.copy(alpha = if (dark) .28f else .24f), size.minDimension * .55f, Offset(xA, size.height * .20f))
                    drawCircle(bg.accentB.copy(alpha = if (dark) .24f else .20f), size.minDimension * .48f, Offset(xB, size.height * .48f))
                }

                else -> {
                    val dx = sin(phase * Math.PI.toFloat() * 2f) * size.width * .06f
                    val dy = sin(phaseSlow * Math.PI.toFloat() * 2f) * size.height * .035f
                    drawCircle(bg.accentA.copy(alpha = if (dark) .24f else .20f), size.minDimension * .44f, Offset(size.width * .82f + dx, size.height * .18f + dy))
                    drawCircle(bg.accentB.copy(alpha = if (dark) .20f else .17f), size.minDimension * .34f, Offset(size.width * .12f - dx, size.height * .72f - dy))
                    drawCircle(bg.accentA.copy(alpha = if (dark) .14f else .12f), size.minDimension * .25f, Offset(size.width * .58f, size.height * .46f + dy))
                }
            }
        }
        content()
    }
}

private fun Color.luminance(): Float =
    (red * .2126f) + (green * .7152f) + (blue * .0722f)
