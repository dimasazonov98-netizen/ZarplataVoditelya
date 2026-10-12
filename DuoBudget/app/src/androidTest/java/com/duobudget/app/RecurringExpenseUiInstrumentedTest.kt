package com.duobudget.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.model.*
import com.duobudget.app.viewmodel.AppViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class RecurringExpenseUiInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var vm:AppViewModel
    @Before fun permitNotifications(){grantTestNotificationPermission()}
    private fun waitFor(expected:()->Boolean){rule.waitForIdle();rule.waitUntil(20000){!vm.busy.value&&expected()};rule.waitForIdle()}
    private fun tap(tag:String){
        val n=rule.onNodeWithTag(tag)
        runCatching{n.performScrollTo()}
        n.assertIsDisplayed().assertIsEnabled().performTouchInput{click()};rule.waitForIdle()
    }
    @Test fun createEditPauseRestartAndRemoveMonthlyRuleThroughRealTaps(){
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset()}
        waitFor{vm.data.value.transactions.isEmpty()}
        rule.runOnUiThread{vm.clearMessage()};rule.waitForIdle()
        tap("tab-settings");tap("fixed-open");tap("fixed-add")
        rule.onNodeWithTag("fixed-name").performTextReplacement("Кредит за машину")
        rule.onNodeWithTag("fixed-amount").performTextReplacement("7000,50")
        tap("fixed-save")
        waitFor{vm.recurringExpenses.value.size==1&&vm.data.value.transactions.size==1}
        assertEquals(700050L,BudgetEngine.netExpenses(vm.data.value,YearMonth.now()))
        val id=vm.recurringExpenses.value.single().id
        tap("fixed-edit-$id")
        rule.onNodeWithTag("fixed-amount").performTextReplacement("8000")
        tap("fixed-save")
        waitFor{vm.data.value.transactions.single().amount==800000L}
        tap("fixed-toggle-$id")
        waitFor{!vm.recurringExpenses.value.single().enabled}
        assertEquals(1,vm.data.value.transactions.size)
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java]}
        waitFor{vm.recurringExpenses.value.size==1}
        assertFalse(vm.recurringExpenses.value.single().enabled)
        assertEquals(1,vm.data.value.transactions.size)
        tap("fixed-toggle-$id")
        waitFor{vm.recurringExpenses.value.single().enabled}
        assertEquals(1,vm.data.value.transactions.size)
        tap("fixed-delete-$id");tap("fixed-delete-confirm")
        waitFor{vm.recurringExpenses.value.isEmpty()}
        assertEquals(1,vm.data.value.transactions.size)
        tap("nav-home")
        rule.onNodeWithText("Наш бюджет").assertExists()
        assertEquals(-800000L,BudgetEngine.available(vm.data.value))
    }
    @Test fun savePaymentDateAndOpenTheRulesFromNotificationIntent(){
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset()}
        waitFor{vm.recurringExpenses.value.isEmpty()}
        rule.runOnUiThread{vm.clearMessage()};rule.waitForIdle()
        val originalIntent=android.content.Intent(rule.activity.intent)
        try {
            tap("tab-settings");tap("fixed-open");tap("fixed-add")
            rule.onNodeWithTag("fixed-name").performTextReplacement("Аренда")
            rule.onNodeWithTag("fixed-amount").performTextReplacement("35000")
            rule.onNodeWithTag("fixed-month").performScrollTo().performTextReplacement(YearMonth.now().plusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MM.uuuu")))
            rule.onNodeWithTag("fixed-day").performScrollTo().performTextReplacement("31")
            tap("fixed-reminder")
            tap("fixed-save")
            waitFor{vm.recurringExpenses.value.size==1}
            assertEquals(31,vm.recurringExpenses.value.single().dayOfMonth)
            assertFalse(vm.recurringExpenses.value.single().reminderEnabled)
            assertTrue(vm.data.value.transactions.isEmpty())
            tap("nav-home")
            rule.runOnUiThread{rule.activity.startActivity(android.content.Intent(rule.activity,MainActivity::class.java).putExtra("openRecurringExpenses",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))}
            rule.waitForIdle();rule.onNodeWithText("Обязательные расходы").assertExists()
            tap("nav-home");rule.onNodeWithText("Наш бюджет").assertExists()
        }finally{
            rule.runOnUiThread{rule.activity.intent=originalIntent;vm.reset()}
            waitFor{vm.recurringExpenses.value.isEmpty()}
            rule.runOnUiThread{vm.clearMessage()}
        }
    }
}
