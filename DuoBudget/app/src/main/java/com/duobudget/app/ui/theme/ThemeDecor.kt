package com.duobudget.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun DuoBudgetBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF8FAFD),
                        Color(0xFFF0F4F8),
                        Color(0xFFE7EDF4)
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Color(0xFF67A9E8).copy(alpha = .12f),
                size.minDimension * .34f,
                Offset(size.width * .88f, size.height * .12f)
            )
            drawCircle(
                Color(0xFFB7C9DA).copy(alpha = .12f),
                size.minDimension * .27f,
                Offset(size.width * .12f, size.height * .78f)
            )
        }
        content()
    }
}
