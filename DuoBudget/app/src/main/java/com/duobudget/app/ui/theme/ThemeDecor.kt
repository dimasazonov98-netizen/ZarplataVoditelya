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
import kotlin.math.cos
import kotlin.math.sin

private data class Backdrop(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accentA: Color,
    val accentB: Color
)

private fun backdrop(style: ThemeStyle, dark: Boolean): Backdrop = when (style) {
    // 1. Classic light / clean iOS-like surface.
    ThemeStyle.BOTANICAL -> if (dark) Backdrop(
        Color(0xFF20252C), Color(0xFF171B20), Color(0xFF0F1216), Color(0xFF4C8AC7), Color(0xFF8192A5)
    ) else Backdrop(
        Color(0xFFF8FAFD), Color(0xFFF0F4F8), Color(0xFFE7EDF4), Color(0xFF67A9E8), Color(0xFFB7C9DA)
    )

    // 2. Classic dark / graphite.
    ThemeStyle.MINIMAL -> if (dark) Backdrop(
        Color(0xFF080A0D), Color(0xFF101317), Color(0xFF050607), Color(0xFF2D3742), Color(0xFF56626F)
    ) else Backdrop(
        Color(0xFFE8EDF2), Color(0xFFDCE3E9), Color(0xFFCBD4DC), Color(0xFF596A78), Color(0xFF8797A5)
    )

    // 3. Mountain landscape.
    ThemeStyle.NEON -> if (dark) Backdrop(
        Color(0xFF0D3650), Color(0xFF175A76), Color(0xFF0D2A3B), Color(0xFF7FCBE8), Color(0xFFB9D7E4)
    ) else Backdrop(
        Color(0xFF8ED8F3), Color(0xFF5EB7DB), Color(0xFF286E95), Color(0xFFEAF8FF), Color(0xFF3B718B)
    )

    // 4. Forest nature.
    ThemeStyle.FAMILY -> if (dark) Backdrop(
        Color(0xFF172419), Color(0xFF2C4931), Color(0xFF101912), Color(0xFF6D8F68), Color(0xFFD5C98F)
    ) else Backdrop(
        Color(0xFFC6D4AC), Color(0xFF7E9C73), Color(0xFF415E45), Color(0xFFF0D9A4), Color(0xFF31543A)
    )

    // 5. City chic.
    ThemeStyle.AURORA -> if (dark) Backdrop(
        Color(0xFF07111F), Color(0xFF151C3A), Color(0xFF070A14), Color(0xFF496FE7), Color(0xFFCB4EAE)
    ) else Backdrop(
        Color(0xFFCBD6F2), Color(0xFFA9B4DA), Color(0xFF53638C), Color(0xFF6F72E8), Color(0xFFC15B9E)
    )

    // 6. Sunset.
    ThemeStyle.SAGE -> if (dark) Backdrop(
        Color(0xFF3B1E35), Color(0xFF9A433A), Color(0xFF27162C), Color(0xFFF6A45B), Color(0xFF7C4C97)
    ) else Backdrop(
        Color(0xFFFFBA79), Color(0xFFED6C63), Color(0xFF81466E), Color(0xFFFFD28A), Color(0xFF704A87)
    )

    // 7. Ocean.
    ThemeStyle.MATERIAL -> if (dark) Backdrop(
        Color(0xFF023A52), Color(0xFF047B91), Color(0xFF022A3C), Color(0xFF52E2EB), Color(0xFF2E9ED0)
    ) else Backdrop(
        Color(0xFF91E5F2), Color(0xFF3BC5D8), Color(0xFF167BA2), Color(0xFFD5FFFF), Color(0xFF2FA6CF)
    )

    // 8. Minimalism.
    ThemeStyle.LUXURY -> if (dark) Backdrop(
        Color(0xFF252A31), Color(0xFF181C21), Color(0xFF111418), Color(0xFF5F6A76), Color(0xFF37414B)
    ) else Backdrop(
        Color(0xFFF5F7F9), Color(0xFFE5EAF0), Color(0xFFD6DDE5), Color(0xFFFFFFFF), Color(0xFFB7C4D0)
    )

    // 9. Purple night.
    ThemeStyle.CORAL -> if (dark) Backdrop(
        Color(0xFF130A2D), Color(0xFF35135F), Color(0xFF09061C), Color(0xFF8B46E8), Color(0xFF3E56D9)
    ) else Backdrop(
        Color(0xFFD9C8F7), Color(0xFFB69DE7), Color(0xFF7864B9), Color(0xFF8C56D7), Color(0xFF5264CF)
    )

    // 10. Golden sand.
    ThemeStyle.CYBER -> if (dark) Backdrop(
        Color(0xFF4B321D), Color(0xFF7A542C), Color(0xFF2E1E12), Color(0xFFD69A4C), Color(0xFFB56D2E)
    ) else Backdrop(
        Color(0xFFF8D9A2), Color(0xFFDFA45A), Color(0xFFB76F31), Color(0xFFFFE3AA), Color(0xFFC57935)
    )
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
    val slow: Float
    if (animated) {
        val motion = rememberInfiniteTransition(label = "theme-background")
        val p by motion.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
            label = "theme-phase"
        )
        val s by motion.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
            label = "theme-phase-slow"
        )
        phase = p
        slow = s
    } else {
        phase = .22f
        slow = .36f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg.top, bg.middle, bg.bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                ThemeStyle.BOTANICAL -> {
                    // Clean classic light: almost no decoration, only soft ambient blobs.
                    drawCircle(bg.accentA.copy(alpha = if (dark) .10f else .10f), size.minDimension * .34f, Offset(size.width * .88f, size.height * .12f))
                    drawCircle(bg.accentB.copy(alpha = if (dark) .08f else .10f), size.minDimension * .26f, Offset(size.width * .12f, size.height * .78f))
                }

                ThemeStyle.MINIMAL -> {
                    // Graphite dark with restrained soft glow.
                    val dx = sin(phase * 6.283f) * size.width * .025f
                    drawCircle(bg.accentA.copy(alpha = .14f), size.minDimension * .42f, Offset(size.width * .84f + dx, size.height * .22f))
                    drawCircle(bg.accentB.copy(alpha = .10f), size.minDimension * .28f, Offset(size.width * .18f - dx, size.height * .72f))
                }

                ThemeStyle.NEON -> {
                    // Mountains + slowly drifting clouds.
                    val cloudShift = slow * size.width * 1.35f
                    repeat(3) { i ->
                        val x = (cloudShift + i * size.width * .48f) % (size.width * 1.45f) - size.width * .25f
                        val y = size.height * (.12f + i * .055f)
                        drawOval(Color.White.copy(alpha = if (dark) .08f else .20f), Offset(x, y), Size(size.width * .34f, size.height * .055f))
                    }
                    val far = Path().apply {
                        moveTo(0f, size.height * .58f)
                        lineTo(size.width * .18f, size.height * .40f)
                        lineTo(size.width * .31f, size.height * .54f)
                        lineTo(size.width * .50f, size.height * .29f)
                        lineTo(size.width * .69f, size.height * .52f)
                        lineTo(size.width * .84f, size.height * .37f)
                        lineTo(size.width, size.height * .56f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(far, bg.accentB.copy(alpha = if (dark) .48f else .52f))
                    val near = Path().apply {
                        moveTo(0f, size.height * .72f)
                        lineTo(size.width * .24f, size.height * .48f)
                        lineTo(size.width * .43f, size.height * .69f)
                        lineTo(size.width * .66f, size.height * .43f)
                        lineTo(size.width * .83f, size.height * .63f)
                        lineTo(size.width, size.height * .49f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(near, Color(0xFF173E54).copy(alpha = if (dark) .78f else .65f))
                    drawCircle(Color.White.copy(alpha = if (dark) .18f else .34f), size.minDimension * .18f, Offset(size.width * .76f, size.height * .18f))
                }

                ThemeStyle.FAMILY -> {
                    // Forest silhouettes with animated mist.
                    drawCircle(bg.accentB.copy(alpha = if (dark) .18f else .30f), size.minDimension * .38f, Offset(size.width * .78f, size.height * .18f))
                    val mist = (slow * size.width * .55f) - size.width * .25f
                    repeat(3) { i ->
                        drawOval(
                            Color.White.copy(alpha = if (dark) .055f else .11f),
                            Offset(mist + i * size.width * .43f, size.height * (.34f + i * .08f)),
                            Size(size.width * .62f, size.height * .10f)
                        )
                    }
                    repeat(9) { i ->
                        val x = size.width * (i / 8f)
                        val h = size.height * (.20f + (i % 3) * .035f)
                        val base = size.height * (.84f + (i % 2) * .03f)
                        drawRect(Color(0xFF1C3825).copy(alpha = .85f), Offset(x - 2f, base - h * .34f), Size(4f, h * .34f))
                        repeat(3) { layer ->
                            val y = base - h * (.28f + layer * .20f)
                            val half = size.width * (.055f + (2 - layer) * .012f)
                            val tree = Path().apply {
                                moveTo(x, y - h * .20f)
                                lineTo(x - half, y + h * .16f)
                                lineTo(x + half, y + h * .16f)
                                close()
                            }
                            drawPath(tree, Color(0xFF294D31).copy(alpha = .90f))
                        }
                    }
                }

                ThemeStyle.AURORA -> {
                    // Night city skyline, purple/blue light bloom.
                    val pulse = .04f + .03f * ((sin(phase * 6.283f) + 1f) / 2f)
                    drawCircle(bg.accentA.copy(alpha = .22f + pulse), size.minDimension * .50f, Offset(size.width * .25f, size.height * .20f))
                    drawCircle(bg.accentB.copy(alpha = .20f + pulse), size.minDimension * .44f, Offset(size.width * .80f, size.height * .30f))
                    val baseY = size.height * .82f
                    repeat(9) { i ->
                        val w = size.width * (.07f + (i % 3) * .014f)
                        val x = size.width * (.02f + i * .115f)
                        val h = size.height * (.18f + (i % 4) * .065f)
                        drawRect(Color(0xFF0A1022).copy(alpha = .88f), Offset(x, baseY - h), Size(w, h))
                        repeat(4) { row ->
                            repeat(2) { col ->
                                if ((row + col + i) % 3 != 0) {
                                    drawRect(
                                        Color(0xFFF2BC66).copy(alpha = .52f),
                                        Offset(x + w * (.22f + col * .38f), baseY - h + h * (.18f + row * .18f)),
                                        Size(w * .12f, h * .045f)
                                    )
                                }
                            }
                        }
                    }
                }

                ThemeStyle.SAGE -> {
                    // Sunset with sun disk, distant ridge and drifting clouds.
                    drawCircle(Color(0xFFFFD69B).copy(alpha = if (dark) .55f else .78f), size.minDimension * .22f, Offset(size.width * .72f, size.height * .22f))
                    val shift = phase * size.width * .42f
                    repeat(3) { i ->
                        val x = ((i * size.width * .44f + shift) % (size.width * 1.25f)) - size.width * .16f
                        drawOval(Color.White.copy(alpha = if (dark) .07f else .13f), Offset(x, size.height * (.22f + i * .08f)), Size(size.width * .38f, size.height * .055f))
                    }
                    val ridge = Path().apply {
                        moveTo(0f, size.height * .69f)
                        cubicTo(size.width * .20f, size.height * .56f, size.width * .34f, size.height * .76f, size.width * .52f, size.height * .64f)
                        cubicTo(size.width * .72f, size.height * .51f, size.width * .84f, size.height * .69f, size.width, size.height * .58f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(ridge, Color(0xFF3B2544).copy(alpha = if (dark) .72f else .58f))
                }

                ThemeStyle.MATERIAL -> {
                    // Ocean: moving wave lines, caustic highlights and bubbles.
                    repeat(7) { i ->
                        val baseY = size.height * (.12f + i * .12f)
                        val amp = size.height * (.010f + i * .0015f)
                        val path = Path()
                        var x = -size.width * .15f
                        path.moveTo(x, baseY)
                        while (x < size.width * 1.15f) {
                            val nx = x + size.width * .16f
                            val wave = sin(((x / size.width) * 9f) + phase * 6.283f + i) * amp
                            val nextWave = sin(((nx / size.width) * 9f) + phase * 6.283f + i) * amp
                            path.cubicTo(x + size.width * .05f, baseY + wave, x + size.width * .11f, baseY + nextWave, nx, baseY + nextWave)
                            x = nx
                        }
                        drawPath(path, Color.White.copy(alpha = if (dark) .09f else .19f), style = Stroke(width = 1.6f + i * .2f))
                    }
                    val glowX = size.width * (.18f + slow * .64f)
                    drawCircle(bg.accentA.copy(alpha = if (dark) .16f else .25f), size.minDimension * .38f, Offset(glowX, size.height * .22f))
                    repeat(14) { i ->
                        val x = ((i * 73f + slow * 260f) % 120f) / 120f * size.width
                        val y = size.height * (.16f + ((i * 17) % 72) / 100f)
                        drawCircle(Color.White.copy(alpha = if (dark) .18f else .28f), 1.5f + (i % 4) * .8f, Offset(x, y))
                    }
                }

                ThemeStyle.LUXURY -> {
                    // Minimalism: soft monochrome layered shapes only.
                    val dx = sin(slow * 6.283f) * size.width * .02f
                    drawCircle(bg.accentA.copy(alpha = if (dark) .07f else .38f), size.minDimension * .48f, Offset(size.width * .82f + dx, size.height * .20f))
                    drawCircle(bg.accentB.copy(alpha = if (dark) .08f else .20f), size.minDimension * .30f, Offset(size.width * .18f - dx, size.height * .78f))
                }

                ThemeStyle.CORAL -> {
                    // Purple night: stars and a slowly flowing aurora ribbon.
                    repeat(34) { i ->
                        val x = ((i * 37) % 101) / 100f * size.width
                        val y = ((i * 61) % 83) / 100f * size.height * .78f
                        val twinkle = .10f + .12f * ((sin(phase * 6.283f + i) + 1f) / 2f)
                        drawCircle(Color.White.copy(alpha = twinkle), 1f + (i % 3) * .55f, Offset(x, y))
                    }
                    repeat(3) { ribbon ->
                        val path = Path()
                        val base = size.height * (.22f + ribbon * .10f)
                        path.moveTo(-size.width * .10f, base)
                        var x = -size.width * .10f
                        while (x < size.width * 1.10f) {
                            val nx = x + size.width * .20f
                            val y = base + sin((x / size.width) * 5f + slow * 6.283f + ribbon) * size.height * .045f
                            val ny = base + sin((nx / size.width) * 5f + slow * 6.283f + ribbon) * size.height * .045f
                            path.cubicTo(x + size.width * .07f, y, x + size.width * .13f, ny, nx, ny)
                            x = nx
                        }
                        drawPath(path, (if (ribbon % 2 == 0) bg.accentA else bg.accentB).copy(alpha = .22f), style = Stroke(width = size.height * .018f))
                    }
                }

                ThemeStyle.CYBER -> {
                    // Golden sand: layered dunes + drifting grains.
                    val duneShift = sin(slow * 6.283f) * size.width * .025f
                    val back = Path().apply {
                        moveTo(-size.width * .10f, size.height * .62f)
                        cubicTo(size.width * .18f + duneShift, size.height * .50f, size.width * .42f, size.height * .72f, size.width * .68f, size.height * .58f)
                        cubicTo(size.width * .84f, size.height * .50f, size.width * .96f, size.height * .57f, size.width * 1.10f, size.height * .54f)
                        lineTo(size.width * 1.10f, size.height)
                        lineTo(-size.width * .10f, size.height)
                        close()
                    }
                    drawPath(back, bg.accentA.copy(alpha = if (dark) .34f else .48f))
                    val front = Path().apply {
                        moveTo(-size.width * .10f, size.height * .78f)
                        cubicTo(size.width * .14f - duneShift, size.height * .61f, size.width * .38f, size.height * .86f, size.width * .62f, size.height * .70f)
                        cubicTo(size.width * .81f, size.height * .58f, size.width * .95f, size.height * .74f, size.width * 1.10f, size.height * .65f)
                        lineTo(size.width * 1.10f, size.height)
                        lineTo(-size.width * .10f, size.height)
                        close()
                    }
                    drawPath(front, bg.accentB.copy(alpha = if (dark) .44f else .58f))
                    repeat(28) { i ->
                        val x = ((i * 41f + phase * 420f) % 125f) / 125f * size.width
                        val y0 = ((i * 31f) % 100f) / 100f * size.height
                        val y = (y0 + sin(phase * 6.283f + i) * 13f).coerceIn(0f, size.height)
                        drawCircle(Color.White.copy(alpha = if (dark) .12f else .30f), 1.1f + (i % 3) * .7f, Offset(x, y))
                    }
                }
            }
        }
        content()
    }
}

private fun Color.luminance(): Float =
    (red * .2126f) + (green * .7152f) + (blue * .0722f)
