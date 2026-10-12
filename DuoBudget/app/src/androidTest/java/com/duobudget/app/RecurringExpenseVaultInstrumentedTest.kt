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
        assertEquals(listOf("RECURRING_PUT","RECURRING_POST"),s.pending().map { it.kind })
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
    @Test fun twoLinkedVaultsReceivePartnerRulesEditsAndDeletion(){
        val a=LocalStore(context,name(),false);val b=LocalStore(context,name(),false)
        a.connect(CloudSession("family","a",a.actorId,Payer.ME),BudgetData(),1,2)
        b.connect(CloudSession("family","b",b.actorId,Payer.PARTNER),BudgetData(),1,2)
        b.saveRecurring(r.copy(startMonth="2024-01",payer=Payer.PARTNER),january)
        var remote=b.pending().fold(BudgetData(),BudgetEngine::apply)
        b.acceptRemote(remote,2,2,b.pending().map{it.id}.toSet());a.acceptRemote(remote,2,2,emptySet())
        assertEquals(b.recurringExpenses(),a.recurringExpenses());assertEquals(0,a.generateRecurring(january))
        a.saveRecurring(a.recurringExpenses().single().copy(amount=850000,dayOfMonth=31,reminderEnabled=true),january)
        remote=a.pending().fold(remote,BudgetEngine::apply)
        a.acceptRemote(remote,3,2,a.pending().map{it.id}.toSet());b.acceptRemote(remote,3,2,emptySet())
        assertEquals(850000L,b.recurringExpenses().single().amount)
        assertEquals(31,b.recurringExpenses().single().dayOfMonth)
        assertTrue(b.recurringExpenses().single().reminderEnabled)
        assertEquals(1,b.data().transactions.size)
        b.removeRecurring(r.id,january);remote=b.pending().fold(remote,BudgetEngine::apply)
        a.acceptRemote(remote,4,2,emptySet());b.acceptRemote(remote,4,2,b.pending().map{it.id}.toSet())
        assertTrue(a.recurringExpenses().isEmpty());assertEquals(1,a.data().transactions.size)
    }
    @Test fun oldEncryptedVaultMigratesRulesIntoPendingQueueOnlyOnce(){
        val n=name();val old=LocalStore(context,n,false)
        old.connect(CloudSession("family","test",old.actorId,Payer.ME),BudgetData(),1,2)
        old.saveRecurring(r.copy(startMonth="2024-01"),january)
        old.acceptRemote(old.data(),2,2,old.pending().map{it.id}.toSet())
        val f=File(context.filesDir,n);val bytes=f.readBytes();val magic="DUOVLT1".toByteArray()
        val key=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey("duobudget.final."+n,null) as javax.crypto.SecretKey
        val decrypt=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply{init(javax.crypto.Cipher.DECRYPT_MODE,key,javax.crypto.spec.GCMParameterSpec(128,bytes.copyOfRange(7,19)));updateAAD(magic)}
        val json=org.json.JSONObject(String(decrypt.doFinal(bytes.copyOfRange(19,bytes.size))))
        val data=json.getJSONObject("data");json.put("recurringExpenses",data.getJSONArray("recurringExpenses"));data.remove("recurringExpenses")
        val encrypt=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply{init(javax.crypto.Cipher.ENCRYPT_MODE,key);updateAAD(magic)}
        f.writeBytes(magic+encrypt.iv+encrypt.doFinal(json.toString().toByteArray()))
        val migrated=LocalStore(context,n,false)
        assertEquals(1,migrated.recurringExpenses().size);assertEquals(listOf("RECURRING_PUT"),migrated.pending().map{it.kind})
        assertEquals(0,migrated.generateRecurring(january))
        migrated.acceptRemote(migrated.data(),3,2,migrated.pending().map{it.id}.toSet())
        val reopened=LocalStore(context,n,false);assertTrue(reopened.pending().isEmpty());assertEquals(1,reopened.data().transactions.size)
    }

}
