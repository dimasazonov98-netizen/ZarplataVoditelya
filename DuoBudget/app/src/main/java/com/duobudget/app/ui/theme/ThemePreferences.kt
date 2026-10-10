package com.duobudget.app.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeStyle {
    BOTANICAL,
    MINIMAL,
    NEON,
    FAMILY,
    AURORA,
    SAGE,
    MATERIAL,
    LUXURY,
    CORAL,
    CYBER
}

object ThemePreferences {
    private const val PREFS = "duobudget_ui"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_STYLE = "theme_style"

    var currentMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var currentStyle by mutableStateOf(ThemeStyle.BOTANICAL)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedMode = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        val storedStyle = prefs.getString(KEY_STYLE, ThemeStyle.BOTANICAL.name)

        currentMode = runCatching { ThemeMode.valueOf(storedMode.orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
        currentStyle = runCatching { ThemeStyle.valueOf(storedStyle.orEmpty()) }
            .getOrDefault(ThemeStyle.BOTANICAL)
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

    fun setStyle(context: Context, style: ThemeStyle) {
        if (currentStyle == style) return
        currentStyle = style
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
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
