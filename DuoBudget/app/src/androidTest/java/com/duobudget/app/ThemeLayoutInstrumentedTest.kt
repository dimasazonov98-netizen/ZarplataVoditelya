package com.duobudget.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeLayoutInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryLayoutFitsSingleLightTheme() {
        rule.waitForIdle()
        listOf("Наш бюджет", "Главная", "Операции", "Цели", "Ещё").forEach { label ->
            rule.onAllNodes(hasText(label))[0].assertIsDisplayed()
        }
    }

    @Test
    fun settingsExposeOnlyLightTheme() {
        rule.waitForIdle()
        rule.onAllNodes(hasText("Ещё"))[0].performClick()
        rule.waitForIdle()

        val light = rule.onAllNodes(hasText("Светлая тема"))[0]
        runCatching { light.performScrollTo() }
        rule.waitForIdle()
        light.assertIsDisplayed()

        val removedLabels = listOf(
            "Классическая тёмная",
            "Горные пейзажи",
            "Лесная природа",
            "Городской шик",
            "Закат",
            "Океан",
            "Минимализм",
            "Фиолетовая ночь",
            "Золотой песок",
            "Система",
            "Тёмная",
            "Анимированный фон"
        )
        removedLabels.forEach { label ->
            assertTrue(
                "Removed theme control is still present: $label",
                rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()
            )
        }
        assertTrue(
            "Legacy theme selector must be removed",
            rule.onAllNodesWithTag("theme-selector").fetchSemanticsNodes().isEmpty()
        )
    }
}
