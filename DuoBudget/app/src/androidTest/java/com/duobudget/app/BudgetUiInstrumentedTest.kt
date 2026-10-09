package com.duobudget.app
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.duobudget.app.ui.DuoBudgetApp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
    private fun idle(expected:()->Boolean={true}){
        rule.waitForIdle();rule.waitUntil(20000){!vm.busy.value&&(expected()||vm.message.value.isNotBlank())};rule.waitForIdle()
    }
    private fun node(text:String):SemanticsNodeInteraction {
        val matcher=hasText(text)
        if(rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag("screen-list").performScrollToNode(matcher)
        val n=rule.onNode(matcher)
        runCatching{n.assertIsDisplayed()}.onFailure{n.performScrollTo()}
        rule.waitForIdle();n.assertIsDisplayed()
        return n
    }
    private fun click(text:String){
        val n=node(text)
        rule.waitUntil(10000){runCatching{n.assertIsEnabled();true}.getOrDefault(false)}
        n.assertIsEnabled().performClick();rule.waitForIdle()
    }
    private fun fill(label:String,value:String){node(label).performTextReplacement(value);rule.waitForIdle();node(label).assertTextContains(value)}
    private fun shot(name:String){
        rule.runOnUiThread{
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(rule.activity.filesDir,name+".png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        // Emit only synthetic test images; the release app contains no image logging.
        val encoded=android.util.Base64.encodeToString(File(rule.activity.filesDir,name+".png").readBytes(),android.util.Base64.NO_WRAP)
        val parts=encoded.chunked(2048)
        parts.forEachIndexed{index,part->android.util.Log.i("DuoBudgetQA","IMAGE|"+name+"|"+index+"|"+parts.size+"|"+part);Thread.sleep(10)}

    }
    @Test fun lockedAppHidesFinancialDialogAndRestoresConfirmation(){
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset()}
        idle();rule.runOnUiThread{vm.clearMessage()};idle()
        rule.runOnUiThread{vm.saveTransaction(null,TransactionType.EXPENSE,12575,"Продукты","Конфиденциальная запись","cash","",Payer.ME,java.time.LocalDateTime.now()) {}}
        idle()
        val gate=mutableStateOf(true)
        rule.runOnUiThread{rule.activity.setContent{DuoBudgetApp(unlocked=gate.value,vm=vm)}}
        rule.waitForIdle();click("Операции");click("Удалить")
        rule.onNodeWithText("Удалить операцию?").assertExists()
        rule.runOnUiThread{gate.value=false};rule.waitForIdle()
        rule.onNodeWithText("Удалить операцию?").assertDoesNotExist()
        assertFalse(vm.data.value.transactions.single().deleted)
        rule.runOnUiThread{gate.value=true};rule.waitForIdle()
        rule.onNodeWithText("Удалить операцию?").assertExists()
        rule.onNodeWithText("Продукты · 125,75 ₽. Её можно восстановить из корзины.").assertExists()
        click("Отмена");assertFalse(vm.data.value.transactions.single().deleted)
    }
    @Test fun householdFlow_decimalEditingTrashGoalsTransferWidget(){
        rule.runOnUiThread{vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset()}
        idle();rule.runOnUiThread{vm.clearMessage()};idle()
        click("+ Доход")
        fill("Сумма, ₽","1000")
        click("Сохранить операцию");idle{vm.data.value.transactions.size==1}
        assertEquals(TransactionType.INCOME,vm.data.value.transactions.single().type)
        click("+ Расход")
        fill("Сумма, ₽","500,50")
        fill("Заметка","Магазин — тест")
        click("Сохранить операцию");idle{vm.data.value.transactions.size==2}
        shot("01-home")
        assertEquals("message=${vm.message.value}; transactions=${vm.data.value.transactions.map{it.type to it.amount}}",49950L,BudgetEngine.available(vm.data.value))
        click("Операции")
        rule.onAllNodesWithText("Изменить")[0].performClick();rule.waitForIdle()
        fill("Сумма, ₽","250,75")
        click("Сохранить операцию");idle{vm.data.value.transactions.any{it.type==TransactionType.EXPENSE&&it.amount==25075L}}
        assertEquals(2,vm.data.value.transactions.size)
        assertEquals(25075L,vm.data.value.transactions.first{it.type==TransactionType.EXPENSE}.amount)
        rule.onAllNodesWithText("Удалить")[0].performClick();rule.waitForIdle()
        rule.onAllNodesWithText("Удалить").onLast().performClick();idle{vm.data.value.transactions.any{it.type==TransactionType.EXPENSE&&it.deleted}}
        assertEquals(100000L,BudgetEngine.available(vm.data.value))
        click("Корзина")
        click("Восстановить");idle{vm.data.value.transactions.none{it.deleted}}
        assertEquals(74925L,BudgetEngine.available(vm.data.value))
        click("Цели")
        fill("Сумма, ₽","500")
        click("Отложить");idle{vm.data.value.savings==50000L}
        assertEquals(50000L,vm.data.value.savings)
        click("+ Цель");rule.waitForIdle()
        fill("Название","Отпуск — тест")
        fill("Сумма цели, ₽","600")
        click("Создать цель");idle{vm.data.value.goals.size==1}
        fill("Из свободных накоплений, ₽","1000")
        click("Внести");idle{vm.data.value.goals.singleOrNull()?.currentAmount==50000L}
        assertEquals(50000L,vm.data.value.goals.single().currentAmount)
        assertEquals(0L,vm.data.value.savings)
        assertEquals(24925L,BudgetEngine.available(vm.data.value))
        shot("02-goal")
        rule.onNodeWithText("Удалить").performScrollTo().performClick();rule.waitForIdle()
        rule.onAllNodesWithText("Удалить").onLast().performClick();idle{vm.data.value.goals.isEmpty()}
        assertTrue(vm.data.value.goals.isEmpty());assertEquals(50000L,vm.data.value.savings)
        click("Главная");click("+ Расход");click("Перевод")
        fill("Сумма, ₽","100")
        click("Сохранить операцию");idle{vm.data.value.transactions.size==3}
        assertEquals(24925L,BudgetEngine.available(vm.data.value))
        assertEquals(10000L,BudgetEngine.balance(vm.data.value,"tbank"))
        val originalActivity=rule.activity
        val originalIntent=Intent(originalActivity.intent)
        try {
            rule.runOnUiThread{originalActivity.startActivity(Intent(originalActivity,MainActivity::class.java).putExtra("openAddExpense",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
            rule.waitForIdle();rule.onNodeWithText("Новая операция").assertExists()
            rule.runOnUiThread{
                val active=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                assertEquals(1,active.size);assertSame(originalActivity,active.single())
            }
            click("Отмена")
            shot("03-final-home")
            assertEquals(24925L,BudgetEngine.available(vm.data.value))
            assertTrue(originalActivity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        } finally {
            // ActivityScenario matches lifecycle events against its original launcher intent.
            // A real onNewIntent replaces it; restore the test fixture before close().
            rule.runOnUiThread{originalActivity.intent=originalIntent}
        }
    }
}

