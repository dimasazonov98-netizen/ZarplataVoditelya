package com.duobudget.app

import com.duobudget.app.data.StateCodec
import com.duobudget.app.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringExpenseEngineTest {
    private val actor="device-A"
    private val rule=RecurringExpense(id="rent",name="Аренда",amount=3500000,category="Жильё",accountId="cash",startMonth="2023-11")
    private val today=LocalDate.of(2024,1,15)
    private fun apply(data:BudgetData,commands:List<BudgetCommand>)=commands.fold(data,BudgetEngine::apply)

    @Test fun catchUpAcrossYearDeductsEveryMonthExactlyOnce(){
        val start=BudgetData(accounts=listOf(MoneyAccount("cash","Наличные",20000000)))
        val commands=RecurringExpenseEngine.commands(start,listOf(rule),actor,today)
        assertEquals(3,commands.size)
        val result=apply(start,commands)
        assertEquals(3500000L,BudgetEngine.netExpenses(result,YearMonth.of(2024,1)))
        assertEquals(9500000L,BudgetEngine.available(result))
        assertTrue(RecurringExpenseEngine.commands(result,listOf(rule),actor,today).isEmpty())
        assertTrue(result.transactions.all{it.createdAt.dayOfMonth==1})
    }
    @Test fun editChangesCurrentMonthAndKeepsPastAmounts(){
        val data=apply(BudgetData(),RecurringExpenseEngine.commands(BudgetData(),listOf(rule),actor,today))
        val edited=rule.copy(amount=4000000)
        val changes=RecurringExpenseEngine.commands(data,listOf(edited),actor,today,rule.id)
        assertEquals(1,changes.size)
        val result=apply(data,changes)
        assertEquals(3,result.transactions.size)
        assertEquals(3500000L,BudgetEngine.netExpenses(result,YearMonth.of(2023,12)))
        assertEquals(4000000L,BudgetEngine.netExpenses(result,YearMonth.of(2024,1)))
    }
    @Test fun manuallyDeletedMonthIsNotCreatedAgain(){
        val data=apply(BudgetData(),RecurringExpenseEngine.commands(BudgetData(),listOf(rule),actor,today))
        val id=RecurringExpenseEngine.transactionId(rule,YearMonth.of(2024,1))
        val deleted=BudgetEngine.apply(data,BudgetCommand(kind="TX_DELETE",actorId=actor,entityId=id))
        assertTrue(RecurringExpenseEngine.commands(deleted,listOf(rule.copy(amount=10000)),actor,today,rule.id).isEmpty())
        assertEquals(0L,BudgetEngine.netExpenses(deleted,YearMonth.of(2024,1)))
    }
    @Test fun futureAndPausedRulesDoNotDeductMoney(){
        assertTrue(RecurringExpenseEngine.commands(BudgetData(),listOf(rule.copy(startMonth="2024-02")),actor,today).isEmpty())
        assertTrue(RecurringExpenseEngine.commands(BudgetData(),listOf(rule.copy(enabled=false)),actor,today).isEmpty())
    }
    @Test fun expenseIsCreatedOnPaymentDateNotBeforeIt(){
        val r=rule.copy(startMonth="2024-01",dayOfMonth=20)
        assertTrue(RecurringExpenseEngine.commands(BudgetData(),listOf(r),actor,today).isEmpty())
        val commands=RecurringExpenseEngine.commands(BudgetData(),listOf(r),actor,LocalDate.of(2024,1,20))
        assertEquals(1,commands.size)
        assertEquals(LocalDate.of(2024,1,20),commands.single().tx!!.createdAt.toLocalDate())
    }
    @Test fun shortMonthsClampDateAndChangingDayKeepsRecordedHistory(){
        val r=rule.copy(startMonth="2023-02",dayOfMonth=31)
        val commands=RecurringExpenseEngine.commands(BudgetData(),listOf(r),actor,LocalDate.of(2023,2,28))
        assertEquals(LocalDate.of(2023,2,28),commands.single().tx!!.createdAt.toLocalDate())
        val old=apply(BudgetData(),RecurringExpenseEngine.commands(BudgetData(),listOf(rule),actor,today))
        val changed=apply(old,RecurringExpenseEngine.commands(old,listOf(rule.copy(dayOfMonth=25,amount=4000000)),actor,today,rule.id))
        assertEquals(LocalDate.of(2024,1,1),changed.transactions.first{YearMonth.from(it.createdAt)==YearMonth.of(2024,1)}.createdAt.toLocalDate())
    }
    @Test fun resumedRuleSkipsPausedMonths(){
        val r=rule.copy(resumeMonth="2024-01")
        val result=apply(BudgetData(),RecurringExpenseEngine.commands(BudgetData(),listOf(r),actor,today))
        assertEquals(1,result.transactions.size)
        assertEquals(0L,BudgetEngine.netExpenses(result,YearMonth.of(2023,12)))
    }
    @Test fun archivedAccountStopsGenerationWithoutCorruptingRule(){
        val data=BudgetData(accounts=listOf(MoneyAccount("cash","Наличные",archived=true),MoneyAccount("sber","Банк")))
        RecurringExpenseEngine.validate(listOf(rule),data)
        assertTrue(RecurringExpenseEngine.commands(data,listOf(rule),actor,today).isEmpty())
    }
    @Test fun rulesAndDecimalAmountsSurviveCodecRoundTrip(){
        val r=rule.copy(amount=700075,payer=Payer.PARTNER,enabled=false,resumeMonth="2024-01",dayOfMonth=31,reminderEnabled=false)
        assertEquals(r,StateCodec.recurring(StateCodec.recurring(r)))
        assertTrue(StateCodec.recurringList(JSONObject()).isEmpty())
    }
    @Test fun malformedBackupRulesAndInvalidAmountsAreRejected(){
        assertThrows(Exception::class.java){StateCodec.recurringList(JSONObject().put("recurringExpenses","bad"))}
        assertThrows(Exception::class.java){RecurringExpenseEngine.validate(listOf(rule.copy(amount=0)),BudgetData())}
        assertThrows(Exception::class.java){RecurringExpenseEngine.validate(listOf(rule,rule),BudgetData())}
    }
}
