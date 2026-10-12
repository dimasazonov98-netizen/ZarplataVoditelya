package com.duobudget.app.data
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.duobudget.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(private val context: Context, vaultName: String="duobudget.v1.vault", migrateLegacy: Boolean=true) {
    private val lock=Any()
    private val vault=AtomicFile(File(context.filesDir,vaultName))
    private val keyAlias="duobudget.final."+vaultName
    private val magic="DUOVLT1".toByteArray(Charsets.US_ASCII)
    private data class Vault(
        val actorId:String=UUID.randomUUID().toString(),val data:BudgetData=BudgetData(),
        val pending:List<BudgetCommand> = emptyList(),val session:CloudSession?=null,
        val dark:Boolean=false,val changes:List<ChangeLogEntry> = emptyList(),
        val setupToken:String="",val revision:Long=0L,val members:Int=1,
        val recurringExpenses:List<RecurringExpense> = emptyList()
    )
    private var state:Vault
    init {
        val exists=vault.baseFile.exists() || File(vault.baseFile.path+".bak").exists()
        ensureKey(exists)
        state=if(exists) read() else {
            var fresh=Vault()
            if(migrateLegacy && hasLegacy()) {
                val old=LegacyLocalStore(context)
                fun cents(n:Long)=Math.multiplyExact(n,100L)
                val profile=old.loadFamilyProfile()
                val converted=BudgetData(
                    transactions=old.loadTransactions(true).map { it.copy(amount=cents(it.amount),createdByUid=fresh.actorId) },
                    budget=cents(old.loadBudget()),savings=cents(old.loadSavings()),
                    categoryLimits=old.loadCategoryLimits().mapValues { cents(it.value) },
                    goals=old.loadGoals().map { it.copy(targetAmount=cents(it.targetAmount),currentAmount=cents(it.currentAmount)) },
                    myName=profile.myName,partnerName=profile.partnerName
                )
                StateCodec.validate(converted);fresh=fresh.copy(data=converted,dark=old.loadDarkTheme(),changes=old.loadChangeLog(500))
            }
            write(fresh);fresh
        }
    }
    private fun hasLegacy():Boolean = context.filesDir.listFiles().orEmpty().any { it.name.contains("vault")&&it!=vault.baseFile } ||
        context.getDatabasePath("duo_budget_v02.db").exists() || File(context.applicationInfo.dataDir,"shared_prefs/duo_budget.xml").exists()
    val actorId:String get()=synchronized(lock){state.actorId}
    fun data():BudgetData=synchronized(lock){state.data}
    fun session():CloudSession?=synchronized(lock){state.session}
    fun pending():List<BudgetCommand> = synchronized(lock){state.pending.toList()}
    fun dark():Boolean=synchronized(lock){state.dark}
    fun changes():List<ChangeLogEntry> = synchronized(lock){state.changes}
    fun revision():Long=synchronized(lock){state.revision}
    fun members():Int=synchronized(lock){state.members}
    fun recurringExpenses():List<RecurringExpense> = synchronized(lock){state.data.recurringExpenses.filterNot { it.deleted }}
    private fun commit(next:Vault){write(next);state=next}
    private fun enqueued(v:Vault,c:BudgetCommand):Vault {
        require(c.actorId==v.actorId)
        require(v.session==null || v.pending.size<1000){"Слишком много изменений без синхронизации. Подключитесь к интернету."}
        if((c.kind=="ACCOUNT_ARCHIVE"&&c.value==1L)||(c.kind=="ACCOUNT_PUT"&&c.account?.archived==true)){
            val id=c.account?.id?:c.entityId
            require(v.data.recurringExpenses.none { !it.deleted&&it.enabled&&it.accountId==id }){"Сначала измените счёт или отключите обязательные расходы для него"}
        }
        val updated=BudgetEngine.apply(v.data,c)
        val entry=ChangeLogEntry((v.changes.maxOfOrNull { it.id }?:0)+1,c.entityId,action(c.kind),c.tx?.let { it.category+": "+Money.edit(it.amount)+" ₽" }?:c.text.ifBlank { if(c.value!=0L)Money.edit(c.value)+" ₽" else "" },LocalDateTime.now())
        return v.copy(data=updated,pending=if(v.session!=null)v.pending+c else emptyList(),changes=(v.changes+entry).takeLast(500))
    }
    fun enqueue(c:BudgetCommand)=synchronized(lock){commit(enqueued(state,c))}
    private fun materialized(v:Vault,today:LocalDate,updateId:String?=null):Vault {
        var next=v
        RecurringExpenseEngine.commands(v.data,v.data.recurringExpenses,v.actorId,today,updateId).forEach { next=enqueued(next,it) }
        return next
    }
    fun generateRecurring(today:LocalDate=LocalDate.now()):Int=synchronized(lock){
        val next=materialized(state,today)
        val count=next.data.transactions.size-state.data.transactions.size
        if(next!=state)commit(next)
        count
    }
    fun saveRecurring(r:RecurringExpense,today:LocalDate=LocalDate.now())=synchronized(lock){
        val caughtUp=materialized(state,today)
        val old=caughtUp.data.recurringExpenses.firstOrNull { it.id==r.id }
        val normalized=if(old?.enabled==false&&r.enabled)r.copy(resumeMonth=java.time.YearMonth.from(today).toString())else r
        val next=enqueued(caughtUp,BudgetCommand(kind="RECURRING_PUT",actorId=state.actorId,recurring=normalized))
        commit(materialized(next,today,r.id))
    }
    fun removeRecurring(id:String,today:LocalDate=LocalDate.now())=synchronized(lock){
        val caughtUp=materialized(state,today)
        commit(enqueued(caughtUp,BudgetCommand(kind="RECURRING_DELETE",actorId=state.actorId,entityId=id)))
    }
    fun setDark(value:Boolean)=synchronized(lock){commit(state.copy(dark=value))}
    fun setupToken():String=synchronized(lock){
        if(state.setupToken.isBlank()){
            val bytes=ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val token=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            commit(state.copy(setupToken=token))
        };state.setupToken
    }
    fun connect(session:CloudSession,data:BudgetData,revision:Long,members:Int)=synchronized(lock){
        StateCodec.validate(data);require(session.actorId==state.actorId)
        commit(state.copy(session=session,data=data,pending=emptyList(),revision=revision,members=members,setupToken="",recurringExpenses=emptyList()))
    }
    fun updateInvite(code:String,expires:String)=synchronized(lock){commit(state.copy(session=state.session?.copy(inviteCode=code,expiresAt=expires)))}
    fun acceptRemote(remote:BudgetData,revision:Long,members:Int,ack:Set<String>):List<String> = synchronized(lock){
        StateCodec.validate(remote)
        var next=remote;val remaining=mutableListOf<BudgetCommand>();val rejected=mutableListOf<String>()
        for(c in state.pending.filterNot { it.id in ack }) {
            runCatching { BudgetEngine.apply(next,c) }.onSuccess { next=it;remaining.add(c) }.onFailure { rejected.add(action(c.kind)+": "+(it.message?:"изменение не применено")) }
        }
        commit(state.copy(data=next,pending=remaining,revision=revision,members=members));rejected
    }
    fun detach()=synchronized(lock){
        var d=state.data
        var rules=state.data.recurringExpenses
        if(state.session?.role==Payer.PARTNER)d=d.copy(myName=d.partnerName,partnerName=d.myName,transactions=d.transactions.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) })
        if(state.session?.role==Payer.PARTNER)rules=rules.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) }
        d=d.copy(transactions=d.transactions.map { it.copy(createdByUid=state.actorId) },recurringExpenses=rules)
        commit(state.copy(data=d,session=null,pending=emptyList(),revision=0,members=1,setupToken="",recurringExpenses=emptyList()))
    }
    fun backup(password:CharArray):ByteArray=synchronized(lock){
        val payload=JSONObject().put("schema",1).put("data",StateCodec.data(state.data)).put("role",state.session?.role?.name?:Payer.ME.name).put("dark",state.dark)
            .put("recurringExpenses",JSONArray(state.data.recurringExpenses.map(StateCodec::recurring)))
        BackupCipher.encrypt(payload.toString().toByteArray(Charsets.UTF_8),password)
    }
    fun restore(bytes:ByteArray,password:CharArray)=synchronized(lock){
        require(state.session==null){"Сначала отключите облако, чтобы не заменить общий бюджет"}
        val plain=BackupCipher.decrypt(bytes,password)
        try {
            val j=JSONObject(String(plain,Charsets.UTF_8));require(j.getInt("schema")==1){"Неизвестная версия резервной копии"}
            var d=StateCodec.data(j.getJSONObject("data"))
            var rules=if(j.getJSONObject("data").has("recurringExpenses"))d.recurringExpenses else StateCodec.recurringList(j)
            if(j.optString("role")=="PARTNER")d=d.copy(myName=d.partnerName,partnerName=d.myName,transactions=d.transactions.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) })
            if(j.optString("role")=="PARTNER")rules=rules.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) }
            d=d.copy(transactions=d.transactions.map { it.copy(createdByUid=state.actorId) },recurringExpenses=rules)
            RecurringExpenseEngine.validate(rules,d)
            commit(state.copy(data=d,pending=emptyList(),session=null,dark=j.optBoolean("dark"),changes=emptyList(),setupToken="",revision=0,members=1,recurringExpenses=emptyList()))
        } finally { plain.fill(0) }
    }
    fun reset()=synchronized(lock){require(state.session==null);commit(Vault(actorId=state.actorId,dark=state.dark))}
    private fun ensureKey(existing:Boolean) {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if(ks.containsAlias(keyAlias))return
        require(!existing){"Ключ хранилища недоступен. Восстановите резервную копию."}
        val builder=KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true)
        // Android <=14 has documented key-loss bugs with unlockedDeviceRequired.
        if(Build.VERSION.SDK_INT>=35)builder.setUnlockedDeviceRequired(true)
        KeyGenerator.getInstance("AES","AndroidKeyStore").apply { init(builder.build()) }.generateKey()
    }
    private fun key():SecretKey=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey(keyAlias,null) as SecretKey
    private fun write(v:Vault) {
        val plain=encode(v).toString().toByteArray(Charsets.UTF_8)
        require(plain.size<BackupCipher.MAX_BYTES){"Хранилище слишком большое"}
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(magic)
            val encrypted=cipher.doFinal(plain);require(!vault.baseFile.isDirectory){"Не удалось записать хранилище"};val expected=magic+cipher.iv+encrypted;val out=vault.startWrite()
            try {out.write(expected);vault.finishWrite(out);require(vault.baseFile.length()==expected.size.toLong() && vault.baseFile.readBytes().contentEquals(expected)){"Не удалось завершить запись хранилища"}}catch(t:Throwable){vault.failWrite(out);throw t}
        } finally {plain.fill(0)}
    }
    private fun read():Vault {
        val bytes=vault.openRead().use { require(it.channel.size()<=BackupCipher.MAX_BYTES){"Хранилище слишком большое"};it.readBytes() };require(bytes.size in (magic.size+12+16)..BackupCipher.MAX_BYTES){"Хранилище повреждено"}
        require(bytes.copyOfRange(0,magic.size).contentEquals(magic)){"Неизвестный формат хранилища"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(magic.size,magic.size+12)));cipher.updateAAD(magic)
        val plain=cipher.doFinal(bytes.copyOfRange(magic.size+12,bytes.size))
        return try {decode(JSONObject(String(plain,Charsets.UTF_8)))}finally{plain.fill(0)}
    }
    private fun encode(v:Vault):JSONObject=JSONObject().put("schema",1).put("actorId",v.actorId).put("data",StateCodec.data(v.data)).put("pending",JSONArray(v.pending.map(StateCodec::command)))
        .put("session",v.session?.let(StateCodec::session)?:JSONObject.NULL).put("dark",v.dark).put("setupToken",v.setupToken).put("revision",v.revision).put("members",v.members)
        .put("changes",JSONArray(v.changes.map { JSONObject().put("id",it.id).put("entityId",it.entityId).put("action",it.action).put("description",it.description).put("changedAt",it.changedAt.toString()) }))
        .put("recurringExpenses",JSONArray(v.recurringExpenses.map(StateCodec::recurring)))
    private fun decode(j:JSONObject):Vault {
        require(j.getInt("schema")==1)
        val changes=j.getJSONArray("changes")
        val actor=j.getString("actorId")
        val data=StateCodec.data(j.getJSONObject("data"))
        val session=j.optJSONObject("session")?.let(StateCodec::session)
        // One-time migration of 1.3/1.4 local rules into the shared command queue.
        val legacy=if(j.getJSONObject("data").has("recurringExpenses"))emptyList() else StateCodec.recurringList(j)
        val migrated=legacy.map { r ->
            val mapped="a_"+java.security.MessageDigest.getInstance("SHA-256").digest((actor+":"+r.accountId).toByteArray()).joinToString("") { "%02x".format(it) }.take(48)
            val previous=data.transactions.firstOrNull { it.id.startsWith("fixed_${r.id}_") }
            if(session?.role==Payer.PARTNER&&previous==null&&data.accounts.any { it.id==mapped })r.copy(accountId=mapped)else r
        }
        val merged=data.copy(recurringExpenses=data.recurringExpenses+migrated)
        RecurringExpenseEngine.validate(merged.recurringExpenses,merged)
        return Vault(actor,merged,StateCodec.commands(j.getJSONArray("pending"))+
            (if(session!=null)migrated.map { BudgetCommand(kind="RECURRING_PUT",actorId=actor,recurring=it) }else emptyList()),session,j.getBoolean("dark"),
            (0 until changes.length()).map { i->val c=changes.getJSONObject(i);ChangeLogEntry(c.getLong("id"),c.getString("entityId"),c.getString("action"),c.getString("description"),LocalDateTime.parse(c.getString("changedAt"))) },
            j.optString("setupToken"),j.optLong("revision"),j.optInt("members",1))
    }
    private fun action(k:String):String=when(k){
        "RECURRING_PUT"->"Обязательный расход сохранён";"RECURRING_DELETE"->"Обязательный расход удалён";"RECURRING_POST"->"Обязательный платёж учтён"
        "TX_PUT"->"Операция сохранена";"TX_DELETE"->"Операция удалена";"TX_RESTORE"->"Операция восстановлена"
        "SAVINGS_ADD"->"Пополнение накоплений";"SAVINGS_WITHDRAW"->"Снятие накоплений";"GOAL_CREATE"->"Цель создана"
        "GOAL_FUND"->"Взнос в цель";"GOAL_WITHDRAW"->"Возврат из цели";"GOAL_DELETE"->"Цель удалена"
        "BUDGET_SET"->"Лимит месяца";"LIMIT_SET"->"Лимит категории";"PROFILE_NAMES"->"Имена обновлены";else->"Счёт обновлён"
    }
}

