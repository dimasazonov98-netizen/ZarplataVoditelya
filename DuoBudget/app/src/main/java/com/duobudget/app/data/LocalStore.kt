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
        val setupToken:String="",val revision:Long=0L,val members:Int=1
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
    fun members():Int=synchronized(lock){state.members}
    private fun commit(next:Vault){write(next);state=next}
    fun enqueue(c:BudgetCommand)=synchronized(lock){
        require(c.actorId==state.actorId)
        require(state.pending.size<1000){"Слишком много изменений без синхронизации. Подключитесь к интернету."}
        val updated=BudgetEngine.apply(state.data,c)
        val entry=ChangeLogEntry((state.changes.maxOfOrNull { it.id }?:0)+1,c.entityId,action(c.kind),c.tx?.let { it.category+": "+Money.edit(it.amount)+" ₽" }?:c.text.ifBlank { if(c.value!=0L)Money.edit(c.value)+" ₽" else "" },LocalDateTime.now())
        commit(state.copy(data=updated,pending=if(state.session!=null)state.pending+c else emptyList(),changes=(state.changes+entry).takeLast(500)))
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
        commit(state.copy(session=session,data=data,pending=emptyList(),revision=revision,members=members,setupToken=""))
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
        if(state.session?.role==Payer.PARTNER)d=d.copy(myName=d.partnerName,partnerName=d.myName,transactions=d.transactions.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) })
        d=d.copy(transactions=d.transactions.map { it.copy(createdByUid=state.actorId) })
        commit(state.copy(data=d,session=null,pending=emptyList(),revision=0,members=1,setupToken=""))
    }
    fun backup(password:CharArray):ByteArray=synchronized(lock){
        val payload=JSONObject().put("schema",1).put("data",StateCodec.data(state.data)).put("role",state.session?.role?.name?:Payer.ME.name).put("dark",state.dark)
        BackupCipher.encrypt(payload.toString().toByteArray(Charsets.UTF_8),password)
    }
    fun restore(bytes:ByteArray,password:CharArray)=synchronized(lock){
        require(state.session==null){"Сначала отключите облако, чтобы не заменить общий бюджет"}
        val plain=BackupCipher.decrypt(bytes,password)
        try {
            val j=JSONObject(String(plain,Charsets.UTF_8));require(j.getInt("schema")==1){"Неизвестная версия резервной копии"}
            var d=StateCodec.data(j.getJSONObject("data"))
            if(j.optString("role")=="PARTNER")d=d.copy(myName=d.partnerName,partnerName=d.myName,transactions=d.transactions.map { it.copy(payer=if(it.payer==Payer.ME)Payer.PARTNER else Payer.ME) })
            d=d.copy(transactions=d.transactions.map { it.copy(createdByUid=state.actorId) })
            commit(state.copy(data=d,pending=emptyList(),session=null,dark=j.optBoolean("dark"),changes=emptyList(),setupToken="",revision=0,members=1))
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
    private fun decode(j:JSONObject):Vault {
        require(j.getInt("schema")==1)
        val changes=j.getJSONArray("changes")
        return Vault(j.getString("actorId"),StateCodec.data(j.getJSONObject("data")),StateCodec.commands(j.getJSONArray("pending")),j.optJSONObject("session")?.let(StateCodec::session),j.getBoolean("dark"),
            (0 until changes.length()).map { i->val c=changes.getJSONObject(i);ChangeLogEntry(c.getLong("id"),c.getString("entityId"),c.getString("action"),c.getString("description"),LocalDateTime.parse(c.getString("changedAt"))) },
            j.optString("setupToken"),j.optLong("revision"),j.optInt("members",1))
    }
    private fun action(k:String):String=when(k){
        "TX_PUT"->"Операция сохранена";"TX_DELETE"->"Операция удалена";"TX_RESTORE"->"Операция восстановлена"
        "SAVINGS_ADD"->"Пополнение накоплений";"SAVINGS_WITHDRAW"->"Снятие накоплений";"GOAL_CREATE"->"Цель создана"
        "GOAL_FUND"->"Взнос в цель";"GOAL_WITHDRAW"->"Возврат из цели";"GOAL_DELETE"->"Цель удалена"
        "BUDGET_SET"->"Лимит месяца";"LIMIT_SET"->"Лимит категории";"PROFILE_NAMES"->"Имена обновлены";else->"Счёт обновлён"
    }
}

