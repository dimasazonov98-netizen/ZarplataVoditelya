package com.duobudget.app.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemePreferences {
    private const val PREFS = "duobudget_ui"
    private const val KEY_THEME = "theme_mode"

    var currentMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun init(context: Context) {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.SYSTEM.name)
        currentMode = runCatching { ThemeMode.valueOf(stored.orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
        applyMode(currentMode)
    }

    fun set(context: Context, mode: ThemeMode) {
        if (currentMode == mode) return
        currentMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
        applyMode(mode)
    }

    private fun applyMode(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }
}
