package com.duobudget.app
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.model.*
import com.duobudget.app.viewmodel.AppViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BudgetUiInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var vm:AppViewModel
    private fun idle(){rule.waitForIdle();rule.waitUntil(20000){!vm.busy.value};rule.waitForIdle()}
    private fun click(text:String){val n=rule.onNodeWithText(text);runCatching{n.assertIsDisplayed()}.onFailure{n.performScrollTo()};n.performClick();rule.waitForIdle()}
    private fun fill(label:String,value:String){val n=rule.onNodeWithText(label);runCatching{n.assertIsDisplayed()}.onFailure{n.performScrollTo()};n.performTextReplacement(value)}
    private fun shot(name:String){
        rule.runOnUiThread{
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(rule.activity.filesDir,name+".png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun householdFlow_decimalEditingTrashGoalsTransferWidget(){
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset()}
        idle()
        click("+ Доход")
        fill("Сумма, ₽","1000")
        rule.onNodeWithText("Сохранить операцию").performScrollTo().performClick();idle()
        assertEquals(TransactionType.INCOME,vm.data.value.transactions.single().type)
        click("+ Расход")
        fill("Сумма, ₽","500,50")
        fill("Заметка","Магазин — тест")
        rule.onNodeWithText("Сохранить операцию").performScrollTo().performClick();idle()
        assertEquals(49950L,BudgetEngine.available(vm.data.value))
        shot("01-home")
        click("Операции")
        rule.onAllNodesWithText("Изменить")[0].performClick();rule.waitForIdle()
        fill("Сумма, ₽","250,75")
        rule.onNodeWithText("Сохранить операцию").performScrollTo().performClick();idle()
        assertEquals(2,vm.data.value.transactions.size)
        assertEquals(25075L,vm.data.value.transactions.first{it.type==TransactionType.EXPENSE}.amount)
        rule.onAllNodesWithText("Удалить")[0].performClick();rule.waitForIdle()
        rule.onAllNodesWithText("Удалить").onLast().performClick();idle()
        assertEquals(100000L,BudgetEngine.available(vm.data.value))
        click("Корзина")
        rule.onNodeWithText("Восстановить").performScrollTo().performClick();idle()
        assertEquals(74925L,BudgetEngine.available(vm.data.value))
        click("Цели")
        fill("Сумма, ₽","500")
        rule.onNodeWithText("Отложить").performScrollTo().performClick();idle()
        assertEquals(50000L,vm.data.value.savings)
        rule.onNodeWithText("+ Цель").performScrollTo().performClick();rule.waitForIdle()
        fill("Название","Отпуск — тест")
        fill("Сумма цели, ₽","600")
        click("Создать цель");idle()
        fill("Из свободных накоплений, ₽","1000")
        rule.onNodeWithText("Внести").performScrollTo().performClick();idle()
        assertEquals(50000L,vm.data.value.goals.single().currentAmount)
        assertEquals(0L,vm.data.value.savings)
        assertEquals(24925L,BudgetEngine.available(vm.data.value))
        shot("02-goal")
        rule.onNodeWithText("Удалить").performScrollTo().performClick();rule.waitForIdle()
        rule.onAllNodesWithText("Удалить").onLast().performClick();idle()
        assertTrue(vm.data.value.goals.isEmpty());assertEquals(50000L,vm.data.value.savings)
        click("Главная");click("+ Расход");click("Перевод")
        fill("Сумма, ₽","100")
        rule.onNodeWithText("Сохранить операцию").performScrollTo().performClick();idle()
        assertEquals(24925L,BudgetEngine.available(vm.data.value))
        assertEquals(10000L,BudgetEngine.balance(vm.data.value,"tbank"))
        rule.runOnUiThread{rule.activity.onNewIntent(Intent(rule.activity,MainActivity::class.java).putExtra("openAddExpense",true))}
        rule.waitForIdle();rule.onNodeWithText("Новая операция").assertExists()
        click("Отмена")
        shot("03-final-home")
        assertTrue(rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}

