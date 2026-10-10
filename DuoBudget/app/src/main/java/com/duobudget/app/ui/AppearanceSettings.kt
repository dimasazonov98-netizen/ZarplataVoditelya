package com.duobudget.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duobudget.app.ui.theme.ThemeMode
import com.duobudget.app.ui.theme.ThemePreferences

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSettingsContent(context: Context) {
    val mode = ThemePreferences.currentMode
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Оформление", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            "Выбор сохраняется на телефоне и применяется ко всему интерфейсу.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
