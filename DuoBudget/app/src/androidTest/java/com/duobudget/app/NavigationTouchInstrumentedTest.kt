package com.duobudget.app

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTouchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun touchTag(tag: String) {
        rule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
    }

    @Test fun tabsAndAccountReturnRemainTouchableAfterRepeatedNavigation() {
        repeat(3) {
            touchTag("tab-settings")
            rule.onNodeWithText("Счета и начальные остатки")
                .performScrollTo().performTouchInput { click() }
            rule.onNodeWithText("Счета").assertExists()
            touchTag("nav-back")
            rule.onNodeWithTag("tab-settings").assertIsDisplayed()
            touchTag("nav-home")
            rule.onNodeWithText("Наш бюджет").assertExists()
            touchTag("tab-history")
            touchTag("nav-home")
            touchTag("tab-savings")
            touchTag("nav-back")
            rule.onNodeWithText("Наш бюджет").assertExists()
        }
    }

    @Test fun addMenuAndCancelledFormsDoNotBlockTheNextTap() {
        listOf("EXPENSE", "INCOME", "TRANSFER", "REFUND", "CASHBACK").forEach { type ->
            rule.onNodeWithContentDescription("Добавить операцию").performTouchInput { click() }
            touchTag("add-$type")
            rule.onNodeWithText("Новая операция").assertExists()
            touchTag("nav-back")
            rule.onNodeWithText("Наш бюджет").assertExists()
        }
        touchTag("tab-settings")
        touchTag("nav-home")
        rule.onNodeWithText("Наш бюджет").assertExists()
    }

    @Test fun systemBackAndKeyboardCannotTrapTheUserInAnOperation() {
        rule.onNodeWithContentDescription("Добавить операцию").performTouchInput { click() }
        touchTag("add-EXPENSE")
        rule.onNodeWithText("Сумма, ₽").performScrollTo().performTouchInput { click() }
        rule.onNodeWithText("Сумма, ₽").performTextInput("123")
        touchTag("nav-home")
        rule.onNodeWithText("Наш бюджет").assertExists()

        touchTag("tab-settings")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        rule.onNodeWithText("Наш бюджет").assertExists()
        touchTag("tab-history")
        touchTag("nav-back")
        rule.onNodeWithText("Наш бюджет").assertExists()
    }
}
