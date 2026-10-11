package com.duobudget.app.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemePreferences {
    private const val PREFS = "duobudget_ui"

    fun init(context: Context) {
        // Theme selection was removed in 1.1.9. Clear legacy theme/style values
        // so an update from older builds always opens in the single light theme.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
