package com.duobudget.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duobudget.app.ui.theme.ThemeMode
import com.duobudget.app.ui.theme.ThemePreferences
import com.duobudget.app.ui.theme.ThemeStyle

private fun themeName(style: ThemeStyle) = when (style) {
    ThemeStyle.BOTANICAL -> "Классическая светлая"
    ThemeStyle.MINIMAL -> "Классическая тёмная"
    ThemeStyle.NEON -> "Горные пейзажи"
    ThemeStyle.FAMILY -> "Лесная природа"
    ThemeStyle.AURORA -> "Городской шик"
    ThemeStyle.SAGE -> "Закат"
    ThemeStyle.MATERIAL -> "Океан"
    ThemeStyle.LUXURY -> "Минимализм"
    ThemeStyle.CORAL -> "Фиолетовая ночь"
    ThemeStyle.CYBER -> "Золотой песок"
}

private fun themePreview(style: ThemeStyle) = when (style) {
    ThemeStyle.BOTANICAL -> Color(0xFF5AA8F6)
    ThemeStyle.MINIMAL -> Color(0xFF20242A)
    ThemeStyle.NEON -> Color(0xFF55BCEB)
    ThemeStyle.FAMILY -> Color(0xFF547C52)
    ThemeStyle.AURORA -> Color(0xFF6A64D8)
    ThemeStyle.SAGE -> Color(0xFFF07B55)
    ThemeStyle.MATERIAL -> Color(0xFF13A7D8)
    ThemeStyle.LUXURY -> Color(0xFFD9E1E8)
    ThemeStyle.CORAL -> Color(0xFF7B3FD1)
    ThemeStyle.CYBER -> Color(0xFFD89A47)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSettingsContent(context: Context) {
    val mode = ThemePreferences.currentMode
    val style = ThemePreferences.currentStyle
    val animated = ThemePreferences.animatedBackground

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Оформление", fontWeight = FontWeight.SemiBold)
        Text(
            "Тема приложения",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeStyle.entries.forEach { option ->
                FilterChip(
                    selected = style == option,
                    onClick = { ThemePreferences.setStyle(context, option) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(themePreview(option), CircleShape)
                        )
                    },
                    label = { Text(themeName(option)) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Анимированный фон", fontWeight = FontWeight.SemiBold)
                Text(
                    if (animated) "Живой фон включён" else "Используется статичный фон",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = animated,
                onCheckedChange = { ThemePreferences.setAnimatedBackground(context, it) }
            )
        }

        Text(
            "Яркость интерфейса",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == ThemeMode.SYSTEM,
                onClick = { ThemePreferences.set(context, ThemeMode.SYSTEM) },
                label = { Text("Система") }
            )
            FilterChip(
                selected = mode == ThemeMode.LIGHT,
                onClick = { ThemePreferences.set(context, ThemeMode.LIGHT) },
                label = { Text("Светлая") }
            )
            FilterChip(
                selected = mode == ThemeMode.DARK,
                onClick = { ThemePreferences.set(context, ThemeMode.DARK) },
                label = { Text("Тёмная") }
            )
        }

        Text(
            "Тема, яркость и анимация сохраняются на телефоне и применяются ко всему интерфейсу.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
