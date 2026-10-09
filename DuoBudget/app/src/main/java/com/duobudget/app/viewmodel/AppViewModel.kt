package com.duobudget.app.viewmodel
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duobudget.app.cloud.BudgetCloudService
import com.duobudget.app.data.*
import com.duobudget.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

class AppViewModel(application:Application):AndroidViewModel(application) {
    private val app=application
    private val preferences=app.getSharedPreferences("vault-location",0)
    private var store:LocalStore?=null
    private val cloud=BudgetCloudService()
    private val syncMutex=Mutex()
    private var poll:Job?=null
    private val _data=MutableStateFlow(BudgetData());val data=_data.asStateFlow()
    private val _session=MutableStateFlow<CloudSession?>(null);val session=_session.asStateFlow()
    private val _dark=MutableStateFlow(false);val darkTheme=_dark.asStateFlow()
    private val _changes=MutableStateFlow<List<ChangeLogEntry>>(emptyList());val changes=_changes.asStateFlow()
    private val _message=MutableStateFlow("");val message=_message.asStateFlow()
    private val _storageError=MutableStateFlow("");val storageError=_storageError.asStateFlow()
    private val _busy=MutableStateFlow(false);val busy=_busy.asStateFlow()
    private val _syncState=MutableStateFlow(SyncState.DISABLED);val syncState=_syncState.asStateFlow()
    private val _syncMessage=MutableStateFlow("Бюджет хранится на этом телефоне");val syncMessage=_syncMessage.asStateFlow()
    private val _members=MutableStateFlow(1);val members=_members.asStateFlow()
    private val _pending=MutableStateFlow(0);val pending=_pending.asStateFlow()
    private val _month=MutableStateFlow(YearMonth.now());val month=_month.asStateFlow()
    val categories=listOf("Продукты","Жильё","Транспорт","Авто","Кафе","Здоровье","Покупки","Развлечения","Кредит","Другое")
    val myPayer:Payer get()=_session.value?.role?:Payer.ME
    val actorId:String get()=store?.actorId.orEmpty()
    init {
        runCatching { LocalStore(app,preferences.getString("name","duobudget.v1.vault")!!) }.onSuccess {store=it;refresh()}.onFailure {
            _storageError.value="Не удалось открыть защищённое хранилище. Данные не удалены. Для восстановления выберите резервную копию."
        }
    }
    fun setMonth(value:YearMonth){_month.value=value}
    fun payerName(p:Payer):String=if(p==Payer.ME)_data.value.myName else _data.value.partnerName
    fun canEdit(t:MoneyTransaction):Boolean=t.createdByUid==actorId
    fun clearMessage(){_message.value=""}
    private fun refresh() {
        val s=store?:return
        _data.value=s.data();_session.value=s.session();_dark.value=s.dark();_changes.value=s.changes().sortedByDescending{it.id};_members.value=s.members();_pending.value=s.pending().size
    }
    private fun action(block:()->Unit,onSuccess:(()->Unit)?=null) {
        if(_busy.value)return
        _busy.value=true
        viewModelScope.launch {
            runCatching {withContext(Dispatchers.IO){block();refresh()}}.onSuccess {onSuccess?.invoke();retrySync()}.onFailure {_message.value=it.message?:"Не удалось сохранить. Данные не изменены."}
            _busy.value=false
        }
    }
    private fun command(kind:String,id:String="",value:Long=0,text:String="",extra:String="",tx:MoneyTransaction?=null,goal:Goal?=null,account:MoneyAccount?=null,onSuccess:(()->Unit)?=null) {
        action({val s=store?:error("Хранилище недоступно");s.enqueue(BudgetCommand(kind=kind,actorId=s.actorId,entityId=id,value=value,text=text,extra=extra,tx=tx,goal=goal,account=account))},onSuccess)
    }
    fun saveTransaction(old:MoneyTransaction?,type:TransactionType,amount:Long,category:String,note:String,accountId:String,targetAccountId:String,payer:Payer,date:LocalDateTime,onSuccess:()->Unit) {
        val t=MoneyTransaction(id=old?.id?:UUID.randomUUID().toString(),type=type,amount=amount,category=if(type==TransactionType.INCOME)"Доход" else if(type==TransactionType.TRANSFER)"Перевод" else if(type==TransactionType.CASHBACK)"Кэшбэк" else category,
            note=note.trim(),accountId=accountId,targetAccountId=if(type==TransactionType.TRANSFER)targetAccountId else "",payer=payer,payerName=payerName(payer),
            createdByUid=actorId,createdAt=date,updatedAt=LocalDateTime.now(),version=old?.version?:1)
        command("TX_PUT",tx=t,onSuccess=onSuccess)
    }
    fun deleteTransaction(id:String)=command("TX_DELETE",id)
    fun restoreTransaction(id:String)=command("TX_RESTORE",id)
    fun setBudget(n:Long)=command("BUDGET_SET",value=n)
    fun setCategoryLimit(category:String,n:Long)=command("LIMIT_SET",value=n,text=category)
    fun addToSavings(n:Long)=command("SAVINGS_ADD",value=n)
    fun withdrawSavings(n:Long)=command("SAVINGS_WITHDRAW",value=n)
    fun createGoal(name:String,target:Long,onSuccess:()->Unit)=command("GOAL_CREATE",goal=Goal(name=name.trim(),targetAmount=target),onSuccess=onSuccess)
    fun fundGoal(id:String,n:Long)=command("GOAL_FUND",id,n)
    fun withdrawGoal(id:String,n:Long)=command("GOAL_WITHDRAW",id,n)
    fun deleteGoal(id:String)=command("GOAL_DELETE",id)
    fun saveAccount(a:MoneyAccount,onSuccess:()->Unit)=command("ACCOUNT_PUT",account=a,onSuccess=onSuccess)
    fun archiveAccount(id:String,value:Boolean)=command("ACCOUNT_ARCHIVE",id,if(value)1 else 0)
    fun saveNames(myName:String,partnerName:String){
        val first=if(myPayer==Payer.ME)myName else partnerName
        val second=if(myPayer==Payer.ME)partnerName else myName
        command("PROFILE_NAMES",text=first.trim(),extra=second.trim())
    }
    fun setDark(value:Boolean)=action({store!!.setDark(value)})
    fun setForeground(foreground:Boolean) {
        poll?.cancel();poll=null
        if(foreground)poll=viewModelScope.launch {while(isActive){syncNow();delay(15_000)}}
    }
    fun retrySync(){viewModelScope.launch{syncNow()}}
    private suspend fun syncNow()=syncMutex.withLock {
        val s=store?:return@withLock
        val session=s.session()?:return@withLock
        _syncState.value=SyncState.SYNCING
        try {
            repeat(10) {
                val commands=s.pending().take(100)
                val j=withContext(Dispatchers.IO){cloud.sync(session,commands)}
                val ack=j.optJSONArray("ack")
                val ids=if(ack==null)emptySet() else (0 until ack.length()).map{ack.getString(it)}.toSet()
                val rejects=j.optJSONArray("rejected")
                val serverErrors=if(rejects==null)emptyList()else(0 until rejects.length()).map{rejects.getJSONObject(it).getString("error")}
                val localErrors=withContext(Dispatchers.IO){s.acceptRemote(StateCodec.data(j.getJSONObject("state")),j.getLong("revision"),j.getInt("members"),ids)}
                refresh()
                if(serverErrors.isNotEmpty()||localErrors.isNotEmpty())_message.value=(serverErrors+localErrors).distinct().joinToString("\n")
                if(s.pending().isEmpty()){_syncState.value=SyncState.SYNCED;_syncMessage.value="Обновлено "+java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));return@withLock}
            }
            _syncState.value=SyncState.READY;_syncMessage.value="Остальные изменения отправятся автоматически"
        } catch(e:Exception) {
            if(e is CancellationException)throw e
            _syncState.value=SyncState.ERROR
            _syncMessage.value=if(e is BudgetCloudService.CloudException)e.message.orEmpty()else "Нет связи. Изменения сохранены на телефоне и отправятся после подключения."
        }
    }
    fun createFamily()=connect(null)
    fun joinFamily(code:String)=connect(code)
    private fun connect(code:String?) {
        if(_busy.value||store==null||store!!.session()!=null)return
        _busy.value=true;_syncState.value=SyncState.SYNCING
        viewModelScope.launch {
            try {
                syncMutex.withLock {
                    val s=store!!
                    val j=withContext(Dispatchers.IO){if(code==null)cloud.create(s.actorId,s.setupToken(),s.data())else cloud.join(s.actorId,s.setupToken(),code,s.data())}
                    withContext(Dispatchers.IO){s.connect(StateCodec.session(j.getJSONObject("session")),StateCodec.data(j.getJSONObject("state")),j.getLong("revision"),j.getInt("members"))}
                    refresh();_syncState.value=SyncState.SYNCED;_syncMessage.value=if(code==null)"Бюджет создан. Передайте приглашение партнёру."else"Телефоны связаны"
                }
            } catch(e:Exception) {_syncState.value=SyncState.ERROR;_syncMessage.value=e.message?:"Не удалось подключиться. Данные сохранены на телефоне."}
            finally {_busy.value=false}
        }
    }
    fun refreshInvite()=action({
        val s=store!!;val j=cloud.invite(s.session()?:error("Бюджет не подключён"));s.updateInvite(j.getString("inviteCode"),j.getString("expiresAt"))
    })
    fun disconnect() {
        if(_busy.value)return
        _busy.value=true
        viewModelScope.launch {
            try {
                syncMutex.withLock {
                    withContext(Dispatchers.IO){
                        val s=store!!;val session=s.session()?:return@withContext
                        try {cloud.disconnect(session)}catch(e:BudgetCloudService.CloudException){if(e.status!=401)throw e}
                        s.detach();refresh()
                    }
                    _syncState.value=SyncState.DISABLED;_syncMessage.value="Копия бюджета осталась на этом телефоне"
                }
            }catch(e:Exception){_message.value=e.message?:"Не удалось отключить телефон"}
            finally{_busy.value=false}
        }
    }
    private fun readFile(uri:Uri):ByteArray {
        val output=ByteArrayOutputStream()
        app.contentResolver.openInputStream(uri)?.use { input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(output.size()+n<=BackupCipher.MAX_BYTES){"Резервная копия слишком большая"};output.write(b,0,n)} }?:error("Не удалось открыть файл")
        return output.toByteArray()
    }
    fun exportBackup(uri:Uri,password:CharArray)=action({
        try {
            val bytes=store!!.backup(password)
            app.contentResolver.openOutputStream(uri,"wt")?.use{it.write(bytes)}?:error("Не удалось записать файл")
            _message.value="Резервная копия сохранена. Пароль понадобится для восстановления."
        } finally {password.fill('\u0000')}
    })
    fun restoreBackup(uri:Uri,password:CharArray)=action({
        try {
            val bytes=readFile(uri)
            if(store==null) {
                // Verify the password and the complete data BEFORE writing any new vault.
                val plain=BackupCipher.decrypt(bytes,password)
                try {val j=JSONObject(String(plain,Charsets.UTF_8));require(j.getInt("schema")==1);StateCodec.data(j.getJSONObject("data"))}finally{plain.fill(0)}
                val name="duobudget.recovered."+UUID.randomUUID()+".vault"
                val fresh=LocalStore(app,name,false);fresh.restore(bytes,password)
                require(preferences.edit().putString("name",name).commit()){"Не удалось сохранить настройку восстановления"}
                store=fresh;_storageError.value=""
            } else store!!.restore(bytes,password)
            _message.value="Резервная копия восстановлена";_syncState.value=SyncState.DISABLED
        } finally {password.fill('\u0000')}
    })
    fun reset()=action({store!!.reset();_message.value="Локальные данные удалены"})
    fun exportCsv(uri:Uri,month:YearMonth)=action({
        fun cell(s:String):String {val safe=if(s.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r'))"'"+s else s;return "\""+safe.replace("\"","\"\"")+"\""}
        val d=store!!.data()
        val rows=d.transactions.filter{!it.deleted&&YearMonth.from(it.createdAt)==month}.sortedBy{it.createdAt}
        val csv=buildString {
            append("\uFEFFДата;Тип;Сумма ₽;Категория;Счёт;Куда;Участник;Заметка\r\n")
            rows.forEach {t->append(listOf(t.createdAt.toString(),t.type.name,Money.edit(t.amount),t.category,d.accounts.firstOrNull{it.id==t.accountId}?.name.orEmpty(),d.accounts.firstOrNull{it.id==t.targetAccountId}?.name.orEmpty(),payerName(t.payer),t.note).joinToString(";"){cell(it)});append("\r\n")}
        }
        app.contentResolver.openOutputStream(uri,"wt")?.use{it.write(csv.toByteArray(Charsets.UTF_8))}?:error("Не удалось записать файл")
        _message.value="Отчёт за "+month+" сохранён"
    })
    override fun onCleared(){poll?.cancel();super.onCleared()}
}

