package com.duobudget.app

import com.duobudget.app.data.StateCodec
import com.duobudget.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SharedRecurringExpenseTest {
    private val r=RecurringExpense(id="rent",name="Аренда",amount=3000000,category="Жильё",accountId="cash",payer=Payer.PARTNER,startMonth="2024-01",dayOfMonth=15)
    private val day=LocalDate.of(2024,1,15)
    private fun put(data:BudgetData,r:RecurringExpense=this.r,actor:String="A")=BudgetEngine.apply(data,BudgetCommand(kind="RECURRING_PUT",actorId=actor,recurring=r))
    @Test fun twoOfflinePhonesProduceOnePaymentWhenCommandsMerge(){
        val shared=put(BudgetData())
        val a=RecurringExpenseEngine.commands(shared,shared.recurringExpenses,"A",day)
        val b=RecurringExpenseEngine.commands(shared,shared.recurringExpenses,"B",day)
        val merged=(a+b).fold(shared,BudgetEngine::apply)
        assertEquals(1,merged.transactions.size)
        assertEquals(3000000L,merged.transactions.single().amount)
        assertEquals(Payer.PARTNER,merged.transactions.single().payer)
        assertEquals("A",merged.transactions.single().createdByUid)
        assertEquals(merged, (b+a).fold(merged,BudgetEngine::apply))
    }
    @Test fun eitherPartnerCanEditSharedRuleAndCurrentPayment(){
        val shared=put(BudgetData())
        val paid=RecurringExpenseEngine.commands(shared,shared.recurringExpenses,"A",day).fold(shared,BudgetEngine::apply)
        val edited=put(paid,r.copy(amount=4000000,dayOfMonth=31,reminderEnabled=false),"B")
        val updated=RecurringExpenseEngine.commands(edited,edited.recurringExpenses,"B",day,r.id).fold(edited,BudgetEngine::apply)
        assertEquals(4000000L,updated.transactions.single().amount)
        assertEquals("A",updated.transactions.single().createdByUid)
        assertEquals(15,updated.transactions.single().createdAt.dayOfMonth)
        assertEquals(updated,StateCodec.data(StateCodec.data(updated)))
    }
    @Test fun tombstoneRejectsStaleEditsAndDoesNotErasePaidHistory(){
        val shared=put(BudgetData())
        val paid=RecurringExpenseEngine.commands(shared,shared.recurringExpenses,"B",day).fold(shared,BudgetEngine::apply)
        val removed=BudgetEngine.apply(paid,BudgetCommand(kind="RECURRING_DELETE",actorId="A",entityId=r.id))
        assertThrows(Exception::class.java){put(removed)}
        assertTrue(RecurringExpenseEngine.commands(removed,removed.recurringExpenses,"A",day).isEmpty())
        assertEquals(paid.transactions,removed.transactions)
        assertEquals(removed,StateCodec.data(StateCodec.data(removed)))
    }
    @Test fun sharedCommandRoundTripAndOldStateCompatibility(){
        val command=BudgetCommand(kind="RECURRING_PUT",actorId="B",recurring=r)
        assertEquals(command,StateCodec.command(StateCodec.command(command)))
        val old=StateCodec.data(BudgetData());old.remove("recurringExpenses")
        assertTrue(StateCodec.data(old).recurringExpenses.isEmpty())
    }
}
