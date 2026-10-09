package com.duobudget.app.data
import com.duobudget.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime

object StateCodec {
    private fun JSONObject.whole(key:String,default:Long?=null):Long {
        if(!has(key)&&default!=null)return default
        val n=get(key)
        require(n is Int || n is Long){"Поле $key должно быть целым числом"}
        return (n as Number).toLong()
    }
    private fun date(s: String): LocalDateTime = runCatching { LocalDateTime.parse(s) }.getOrElse { OffsetDateTime.parse(s).toLocalDateTime() }
    private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }
    fun account(a: MoneyAccount) = JSONObject().put("id",a.id).put("name",a.name).put("openingBalance",a.openingBalance).put("archived",a.archived)
    fun account(j: JSONObject) = MoneyAccount(j.getString("id"),j.getString("name"),j.whole("openingBalance",0L),j.optBoolean("archived"))
    fun tx(t: MoneyTransaction) = JSONObject().put("id",t.id).put("type",t.type.name).put("amount",t.amount).put("category",t.category).put("note",t.note)
        .put("accountId",t.accountId).put("targetAccountId",t.targetAccountId).put("payer",t.payer.name).put("payerName",t.payerName).put("createdByUid",t.createdByUid)
        .put("createdAt",t.createdAt.toString()).put("updatedAt",t.updatedAt.toString()).put("version",t.version).put("deleted",t.deleted)
    fun tx(j: JSONObject) = MoneyTransaction(id=j.getString("id"),type=TransactionType.valueOf(j.getString("type")),amount=j.whole("amount"),category=j.getString("category"),note=j.optString("note"),
        accountId=j.getString("accountId"),payer=Payer.valueOf(j.getString("payer")),payerName=j.optString("payerName"),createdByUid=j.optString("createdByUid"),
        createdAt=date(j.getString("createdAt")),updatedAt=date(j.getString("updatedAt")),version=j.whole("version",1L),deleted=j.optBoolean("deleted"),targetAccountId=j.optString("targetAccountId"))
    fun goal(g: Goal) = JSONObject().put("id",g.id).put("name",g.name).put("targetAmount",g.targetAmount).put("currentAmount",g.currentAmount).put("joint",g.joint).put("createdAt",g.createdAt.toString())
    fun goal(j: JSONObject) = Goal(j.getString("id"),j.getString("name"),j.whole("targetAmount"),j.whole("currentAmount"),j.optBoolean("joint",true),date(j.getString("createdAt")))
    fun data(s: BudgetData) = JSONObject().put("transactions",JSONArray(s.transactions.map(::tx))).put("accounts",JSONArray(s.accounts.map(::account)))
        .put("budget",s.budget).put("savings",s.savings).put("categoryLimits",JSONObject(s.categoryLimits)).put("goals",JSONArray(s.goals.map(::goal))).put("myName",s.myName).put("partnerName",s.partnerName)
    fun data(j: JSONObject): BudgetData {
        val limits=j.getJSONObject("categoryLimits")
        return BudgetData(objects(j.getJSONArray("transactions")).map(::tx),objects(j.getJSONArray("accounts")).map(::account),
            j.whole("budget"),j.whole("savings"),limits.keys().asSequence().associateWith { limits.whole(it) },
            objects(j.getJSONArray("goals")).map(::goal),j.getString("myName"),j.getString("partnerName")).also(::validate)
    }
    fun validate(s: BudgetData) {
        require(s.transactions.size<=20000 && s.accounts.size in 1..100 && s.goals.size<=100 && s.categoryLimits.size<=100)
        require(s.myName.isNotBlank() && s.myName.length<=60 && s.partnerName.isNotBlank() && s.partnerName.length<=60)
        require(s.budget in 0..Money.MAX && s.savings in 0..Money.MAX)
        require(s.accounts.distinctBy { it.id }.size==s.accounts.size && s.accounts.any { !it.archived })
        s.accounts.forEach { require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.name.isNotBlank() && it.name.length<=60 && it.openingBalance in -Money.MAX..Money.MAX) }
        require(s.transactions.distinctBy { it.id }.size==s.transactions.size)
        s.transactions.forEach { t ->
            require(t.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && t.amount in 1..Money.MAX && t.category.isNotBlank() && t.category.length<=60 && t.note.length<=500 && t.payerName.length<=60 && t.version>0)
            require(s.accounts.any { it.id==t.accountId })
            if(t.type==TransactionType.TRANSFER)require(t.targetAccountId!=t.accountId && s.accounts.any { it.id==t.targetAccountId })
        }
        require(s.goals.distinctBy { it.id }.size==s.goals.size)
        s.goals.forEach { require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.name.isNotBlank() && it.name.length<=60 && it.targetAmount in 1..Money.MAX && it.currentAmount in 0..it.targetAmount) }
        s.categoryLimits.forEach { (k,v)-> require(k.isNotBlank() && k.length<=60 && k !in listOf("__proto__","prototype","constructor") && v in 1..Money.MAX) }
    }
    fun command(c: BudgetCommand) = JSONObject().put("id",c.id).put("kind",c.kind).put("actorId",c.actorId).put("entityId",c.entityId).put("value",c.value).put("text",c.text).put("extra",c.extra)
        .also { j-> c.tx?.let { j.put("tx",tx(it)) };c.goal?.let { j.put("goal",goal(it)) };c.account?.let { j.put("account",account(it)) } }
    fun command(j: JSONObject) = BudgetCommand(j.getString("id"),j.getString("kind"),j.getString("actorId"),j.optString("entityId"),j.whole("value",0L),j.optString("text"),j.optString("extra"),
        j.optJSONObject("tx")?.let(::tx),j.optJSONObject("goal")?.let(::goal),j.optJSONObject("account")?.let(::account))
    fun session(s: CloudSession) = JSONObject().put("familyId",s.familyId).put("token",s.token).put("actorId",s.actorId).put("role",s.role.name).put("inviteCode",s.inviteCode).put("expiresAt",s.expiresAt)
    fun session(j: JSONObject) = CloudSession(j.getString("familyId"),j.getString("token"),j.getString("actorId"),Payer.valueOf(j.getString("role")),j.optString("inviteCode"),j.optString("expiresAt"))
    fun commands(a: JSONArray) = objects(a).map(::command)
}

