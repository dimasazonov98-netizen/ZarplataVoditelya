package com.duobudget.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.data.LocalStore
import com.duobudget.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurringExpenseVaultInstrumentedTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun name()="fixed-test.${UUID.randomUUID()}.vault"
    private val r=RecurringExpense(id="credit",name="Кредит",amount=700000,category="Кредит",accountId="sber",startMonth="2023-11",dayOfMonth=15,reminderEnabled=false)
    private val november=LocalDate.of(2023,11,15)
    private val january=LocalDate.of(2024,1,15)

    @Test fun restartBackupAndRestoreKeepRulesAndNeverDuplicateExpenses(){
        val n=name();val s=LocalStore(context,n,false)
        s.saveRecurring(r,january)
        assertEquals(3,s.data().transactions.size)
        val reopened=LocalStore(context,n,false)
        assertEquals(listOf(r),reopened.recurringExpenses())
        assertEquals(0,reopened.generateRecurring(january))
        val backup=reopened.backup("fixed-expense-password".toCharArray())
        val target=LocalStore(context,name(),false)
        target.restore(backup,"fixed-expense-password".toCharArray())
        assertEquals(listOf(r),target.recurringExpenses())
        assertEquals(0,target.generateRecurring(january))
        assertTrue(target.data().transactions.all{it.createdByUid==target.actorId})
    }
    @Test fun editingPausingAndResumingPreserveHistory(){
        val s=LocalStore(context,name(),false)
        s.saveRecurring(r,november)
        s.saveRecurring(r.copy(enabled=false),november)
        s.saveRecurring(r,january)
        assertEquals(2,s.data().transactions.size)
        assertEquals(0L,BudgetEngine.netExpenses(s.data(),YearMonth.of(2023,12)))
        val resumed=s.recurringExpenses().single()
        s.saveRecurring(resumed.copy(amount=800000),january)
        assertEquals(700000L,BudgetEngine.netExpenses(s.data(),YearMonth.of(2023,11)))
        assertEquals(800000L,BudgetEngine.netExpenses(s.data(),YearMonth.of(2024,1)))
        s.removeRecurring(r.id,january)
        assertTrue(s.recurringExpenses().isEmpty())
        assertEquals(2,s.data().transactions.size)
    }
    @Test fun generatedTransactionsUseNormalCloudQueueAndAcknowledgement(){
        val s=LocalStore(context,name(),false)
        s.connect(CloudSession("family","token",s.actorId,Payer.ME),BudgetData(),1,2)
        s.saveRecurring(r.copy(startMonth="2024-01"),january)
        assertEquals(1,s.pending().size)
        assertEquals("TX_PUT",s.pending().single().kind)
        val ack=s.pending().map{it.id}.toSet()
        s.acceptRemote(s.data(),2,2,ack)
        assertTrue(s.pending().isEmpty())
        assertEquals(0,s.generateRecurring(january))
        assertEquals(1,s.recurringExpenses().size)
    }
    @Test fun failedDiskWriteDoesNotCreateRuleOrChargeAndAccountArchiveIsGuarded(){
        val n=name();val s=LocalStore(context,n,false);val f=File(context.filesDir,n)
        f.delete();assertTrue(f.mkdir())
        assertThrows(Exception::class.java){s.saveRecurring(r,november)}
        assertTrue(s.recurringExpenses().isEmpty());assertTrue(s.data().transactions.isEmpty())
        f.delete()
        val good=LocalStore(context,name(),false);good.saveRecurring(r,november)
        assertThrows(Exception::class.java){good.enqueue(BudgetCommand(kind="ACCOUNT_ARCHIVE",actorId=good.actorId,entityId="sber",value=1))}
        assertFalse(good.data().accounts.first{it.id=="sber"}.archived)
    }
}
