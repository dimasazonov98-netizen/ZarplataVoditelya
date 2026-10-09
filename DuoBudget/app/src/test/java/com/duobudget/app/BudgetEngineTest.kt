package com.duobudget.app
import com.duobudget.app.model.*
import com.duobudget.app.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime
import java.time.YearMonth

class BudgetEngineTest {
    private val actor="device-A"
    private fun c(kind:String,value:Long=0,id:String="",tx:MoneyTransaction?=null,goal:Goal?=null,account:MoneyAccount?=null)=BudgetCommand(kind=kind,actorId=actor,value=value,entityId=id,tx=tx,goal=goal,account=account)
    private fun tx(type:TransactionType=TransactionType.EXPENSE,amount:Long=15050,payer:Payer=Payer.ME)=MoneyTransaction(type=type,amount=amount,category="Продукты",note="",accountId="sber",payer=payer,createdByUid=actor,createdAt=LocalDateTime.of(2026,1,2,12,0))
    private fun fails(block:()->Unit){try{block();fail("Expected rejection")}catch(e:Exception){}}
    @Test fun decimalInputNeverDropsSeparators(){assertEquals(50050L,Money.parse("500,50"));assertEquals(50050L,Money.parse("500.50"));assertEquals(123456L,Money.parse("1 234,56"));assertNull(Money.parse("1,234"));assertNull(Money.parse("1e3"));assertNull(Money.parse("999999999999999999999"));assertNull(Money.parse("500,50,1"))}
    @Test fun amountsRoundTrip(){for(n in listOf(0L,1L,15050L,-9900L,Money.MAX))assertEquals(n,Money.parse(Money.edit(n)))}
    @Test fun goalTopUpConservesMoney(){val g=Goal(name="Отпуск",targetAmount=10000,currentAmount=9000);val s=BudgetData(savings=50000,goals=listOf(g));val updated=BudgetEngine.apply(s,c("GOAL_FUND",40000,g.id));assertEquals(49000L,updated.savings);assertEquals(10000L,updated.goals[0].currentAmount);assertEquals(BudgetEngine.savingsTotal(s),BudgetEngine.savingsTotal(updated))}
    @Test fun deletingGoalReturnsItsMoney(){val g=Goal(name="Цель",targetAmount=10000,currentAmount=5000);val updated=BudgetEngine.apply(BudgetData(savings=3000,goals=listOf(g)),c("GOAL_DELETE",id=g.id));assertEquals(8000L,updated.savings);assertTrue(updated.goals.isEmpty())}
    @Test fun withdrawalsCannotOverdraw(){fails{BudgetEngine.apply(BudgetData(savings=5000),c("SAVINGS_WITHDRAW",5001))};assertEquals(0L,BudgetEngine.apply(BudgetData(savings=5000),c("SAVINGS_WITHDRAW",5000)).savings)}
    @Test fun aggregateAmountsCannotOverflow(){fails{BudgetEngine.apply(BudgetData(savings=Money.MAX),c("SAVINGS_ADD",1))}}
    @Test fun transfersMoveBalancesWithoutIncomeOrExpenses(){val t=tx(TransactionType.TRANSFER,10000).copy(targetAccountId="cash");val s=BudgetEngine.apply(BudgetData(accounts=listOf(MoneyAccount("sber","Банк",20000),MoneyAccount("cash","Наличные"))),c("TX_PUT",tx=t));assertEquals(10000L,BudgetEngine.balance(s,"sber"));assertEquals(10000L,BudgetEngine.balance(s,"cash"));assertEquals(0L,BudgetEngine.netExpenses(s,YearMonth.of(2026,1)));assertEquals(0L,BudgetEngine.incomes(s,YearMonth.of(2026,1)));assertEquals(20000L,BudgetEngine.available(s))}
    @Test fun transfersRequireTwoDifferentExistingAccounts(){fails{BudgetEngine.apply(BudgetData(),c("TX_PUT",tx=tx(TransactionType.TRANSFER).copy(targetAccountId="sber")))};fails{BudgetEngine.apply(BudgetData(),c("TX_PUT",tx=tx(TransactionType.TRANSFER).copy(targetAccountId="none")))}}
    @Test fun refundsReduceBudgetAndCashbackCountsAsIncome(){val e=tx();val s=BudgetData(transactions=listOf(e,tx(TransactionType.REFUND,5050),tx(TransactionType.CASHBACK,300)));assertEquals(10000L,BudgetEngine.netExpenses(s,YearMonth.of(2026,1)));assertEquals(300L,BudgetEngine.incomes(s,YearMonth.of(2026,1)))}
    @Test fun negativeAvailableIsVisibleAndSavingsAreExcluded(){val s=BudgetData(transactions=listOf(tx()),savings=1000);assertEquals(-16050L,BudgetEngine.available(s))}
    @Test fun partnerCannotEditDeleteOrRestoreSomeoneElsesRecord(){val t=tx().copy(createdByUid="device-B");val s=BudgetData(transactions=listOf(t));fails{BudgetEngine.apply(s,c("TX_DELETE",id=t.id))};fails{BudgetEngine.apply(s,c("TX_RESTORE",id=t.id))};fails{BudgetEngine.apply(s,c("TX_PUT",tx=t.copy(createdByUid=actor)))}}
    @Test fun trashIsExcludedFromBalancesAndCanBeRestored(){val t=tx();val s=BudgetData(transactions=listOf(t));val deleted=BudgetEngine.apply(s,c("TX_DELETE",id=t.id));assertEquals(0L,BudgetEngine.netExpenses(deleted,YearMonth.of(2026,1)));val restored=BudgetEngine.apply(deleted,c("TX_RESTORE",id=t.id));assertEquals(t.amount,BudgetEngine.netExpenses(restored,YearMonth.of(2026,1)));assertEquals(3L,restored.transactions[0].version)}
    @Test fun editsReplaceInsteadOfDuplicating(){val t=tx();val s=BudgetEngine.apply(BudgetData(),c("TX_PUT",tx=t));val updated=BudgetEngine.apply(s,c("TX_PUT",tx=t.copy(amount=700)));assertEquals(1,updated.transactions.size);assertEquals(700L,updated.transactions[0].amount);assertEquals(2L,updated.transactions[0].version)}
    @Test fun archivePreservesHistoryAndPreventsNewSpending(){val s=BudgetEngine.apply(BudgetData(),c("ACCOUNT_ARCHIVE",1,"sber"));fails{BudgetEngine.apply(s,c("TX_PUT",tx=tx()))};val only=BudgetData(accounts=listOf(MoneyAccount("sber","Банк")));fails{BudgetEngine.apply(only,c("ACCOUNT_ARCHIVE",1,"sber"))};fails{BudgetEngine.apply(only,c("ACCOUNT_PUT",account=MoneyAccount("sber","Банк",archived=true)))}}
    @Test fun codecRoundTripPreservesMoneyAndOwnership(){val t=tx(TransactionType.TRANSFER).copy(targetAccountId="cash");val d=BudgetData(transactions=listOf(t),savings=4400,goals=listOf(Goal(name="Цель",targetAmount=10000,currentAmount=55)));assertEquals(d,StateCodec.data(StateCodec.data(d)))}
    @Test fun incompleteOrPoisonedBackupIsRejected(){fails{StateCodec.data(org.json.JSONObject("{}"))};fails{StateCodec.validate(BudgetData(accounts=listOf(MoneyAccount("x","",0))))}}
}
class BackupCipherTest {
    private val password="my-strong-passphrase".toCharArray()
    @Test fun backupRoundTripIsPortable(){val data="секрет 500,50 ₽".toByteArray();assertArrayEquals(data,BackupCipher.decrypt(BackupCipher.encrypt(data,password),password))}
    @Test fun eachExportHasFreshSaltAndNonce(){val data="private".toByteArray();assertFalse(BackupCipher.encrypt(data,password).contentEquals(BackupCipher.encrypt(data,password)))}
    @Test fun wrongPasswordAndTamperingAreRejected(){val bytes=BackupCipher.encrypt("private".toByteArray(),password);try{BackupCipher.decrypt(bytes,"wrong-password".toCharArray());fail()}catch(e:IllegalArgumentException){};bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();try{BackupCipher.decrypt(bytes,password);fail()}catch(e:IllegalArgumentException){}}
    @Test fun weakPasswordAndTruncatedFilesAreRejected(){try{BackupCipher.encrypt(byteArrayOf(1),"short".toCharArray());fail()}catch(e:IllegalArgumentException){};try{BackupCipher.decrypt(byteArrayOf(1),password);fail()}catch(e:IllegalArgumentException){}}
}

