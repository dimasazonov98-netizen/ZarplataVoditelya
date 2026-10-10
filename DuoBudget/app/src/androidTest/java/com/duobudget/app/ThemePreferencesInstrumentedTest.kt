package com.duobudget.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duobudget.app.ui.theme.ThemeMode
import com.duobudget.app.ui.theme.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesInstrumentedTest {
    @Test
    fun themeChoicePersistsAndControlsNightMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("duobudget_ui", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("theme_mode").commit()

        ThemePreferences.init(context)
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.currentMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())

        ThemePreferences.set(context, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, ThemePreferences.currentMode)
        assertEquals("DARK", prefs.getString("theme_mode", null))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        ThemePreferences.set(context, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, ThemePreferences.currentMode)
        assertEquals("LIGHT", prefs.getString("theme_mode", null))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

        ThemePreferences.set(context, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.currentMode)
        assertEquals("SYSTEM", prefs.getString("theme_mode", null))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }
}
