package com.duobudget.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.data.*
import com.duobudget.app.model.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class VaultInstrumentedTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun name()="test."+UUID.randomUUID()+".vault"
    @Test fun encryptedDiskRoundTripAndAuthenticatedTamperFailure(){
        val n=name();val s=LocalStore(context,n,false);s.enqueue(BudgetCommand(kind="BUDGET_SET",actorId=s.actorId,value=123456))
        assertEquals(123456L,LocalStore(context,n,false).data().budget)
        val file=File(context.filesDir,n);val original=file.readBytes()
        assertFalse(String(original,Charsets.ISO_8859_1).contains("123456"))
        val tampered=original.copyOf();tampered[tampered.lastIndex]=(tampered.last().toInt() xor 1).toByte();file.writeBytes(tampered)
        try{LocalStore(context,n,false);fail("Corrupt data must not be replaced")}catch(e:Exception){}
        assertArrayEquals(tampered,file.readBytes());file.writeBytes(original)
        assertEquals(123456L,LocalStore(context,n,false).data().budget)
    }
    @Test fun failedWriteDoesNotAdvanceInMemoryState(){
        val n=name();val s=LocalStore(context,n,false);val file=File(context.filesDir,n);file.delete();assertTrue(file.mkdir())
        try{s.enqueue(BudgetCommand(kind="BUDGET_SET",actorId=s.actorId,value=900));fail()}catch(e:Exception){}
        assertEquals(0L,s.data().budget);file.delete()
    }
    @Test fun rejectedRestoreKeepsCurrentBudgetAndCorrectPasswordRecovers(){
        val s=LocalStore(context,name(),false);s.enqueue(BudgetCommand(kind="BUDGET_SET",actorId=s.actorId,value=50000))
        val backup=s.backup("correct-password".toCharArray())
        s.enqueue(BudgetCommand(kind="BUDGET_SET",actorId=s.actorId,value=10000))
        try{s.restore(backup,"wrong-password".toCharArray());fail()}catch(e:Exception){}
        assertEquals(10000L,s.data().budget)
        s.restore(backup,"correct-password".toCharArray());assertEquals(50000L,s.data().budget)
    }
    @Test fun queuedChangesAreRebasedAndAcknowledgedExactlyOnce(){
        val n=name();val s=LocalStore(context,n,false)
        s.connect(CloudSession("family-test","token-test",s.actorId,Payer.ME),BudgetData(),1,2)
        val c=BudgetCommand(kind="SAVINGS_ADD",actorId=s.actorId,value=30000);s.enqueue(c)
        s.acceptRemote(BudgetData(savings=20000),2,2,emptySet());assertEquals(50000L,s.data().savings);assertEquals(1,s.pending().size)
        s.acceptRemote(BudgetData(savings=50000),3,2,setOf(c.id));assertEquals(50000L,s.data().savings);assertTrue(s.pending().isEmpty())
        assertEquals(50000L,LocalStore(context,n,false).data().savings)
    }
}

