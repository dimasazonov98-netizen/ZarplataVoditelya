package com.duobudget.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.model.*
import com.duobudget.app.viewmodel.AppViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class RecurringExpenseUiInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var vm:AppViewModel
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
}
