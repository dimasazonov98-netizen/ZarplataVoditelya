package com.duobudget.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AppearanceSettingsContent(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Оформление", fontWeight = FontWeight.SemiBold)
        Text(
            "Светлая тема",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            "В приложении используется только светлая тема. Переключатель тем и фоновые варианты удалены.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
