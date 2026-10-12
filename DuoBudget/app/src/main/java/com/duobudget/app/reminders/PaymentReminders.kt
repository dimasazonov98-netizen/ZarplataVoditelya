package com.duobudget.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duobudget.app.MainActivity
import com.duobudget.app.R
import com.duobudget.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime

object PaymentReminders {
    const val ACTION="com.duobudget.family.MONTHLY_PAYMENT_REMINDER"
    const val CHANNEL="monthly_payments"
    private const val PREFS="monthly-reminder-schedule"
    private val lock=Any()
    private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private fun read(c:Context):Map<String,PaymentReminderRule> = runCatching {
        val a=JSONArray(prefs(c).getString("rules","[]"))
        (0 until a.length()).map{a.getJSONObject(it)}.map {
            PaymentReminderRule(it.getString("id"),it.getInt("day"),it.getString("start"),it.optString("resume"))
        }.associateBy{it.id}
    }.getOrDefault(emptyMap())
    private fun json(rules:Collection<PaymentReminderRule>)=JSONArray(rules.sortedBy{it.id}.map{
        JSONObject().put("id",it.id).put("day",it.dayOfMonth).put("start",it.startMonth).put("resume",it.resumeMonth)
    }).toString()
    fun ensureChannel(c:Context){
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Обязательные платежи",NotificationManager.IMPORTANCE_DEFAULT).apply{
                description="Напоминания за день до ежемесячного платежа"
                lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
            })
    }
    fun hasPermission(c:Context)=Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun hasAskedPermission(c:Context)=prefs(c).getBoolean("permissionAsked",false)
    fun markPermissionAsked(c:Context){prefs(c).edit().putBoolean("permissionAsked",true).apply()}
    fun canNotify(c:Context):Boolean {
        val channel=c.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return hasPermission(c)&&NotificationManagerCompat.from(c).areNotificationsEnabled()&&channel?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun alarmIntent(c:Context,id:String,due:String?=null)=Intent(c,PaymentReminderReceiver::class.java)
        .setAction(ACTION).setData(Uri.parse("duobudget://payment-reminder/$id")).putExtra("ruleId",id).apply{if(due!=null)putExtra("due",due)}
    private fun cancel(c:Context,id:String){
        val p=PendingIntent.getBroadcast(c,0,alarmIntent(c,id),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if(p!=null){c.getSystemService(AlarmManager::class.java).cancel(p);p.cancel()}
        c.getSystemService(NotificationManager::class.java).cancel(id,0)
    }
    private fun schedule(c:Context,r:PaymentReminderRule,now:ZonedDateTime=ZonedDateTime.now()){
        if(!canNotify(c))return
        val next=PaymentReminderSchedule.next(r,now,prefs(c).getString("last-${r.id}","").orEmpty())?:return
        val p=PendingIntent.getBroadcast(c,0,alarmIntent(c,r.id,next.due.toString()),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.trigger.toInstant().toEpochMilli(),p)
    }
    fun sync(c:Context,rules:List<RecurringExpense>,data:BudgetData)=synchronized(lock){
        ensureChannel(c)
        val next=rules.filter{it.enabled&&it.reminderEnabled&&data.accounts.any{a->a.id==it.accountId&&!a.archived}}
            .map{PaymentReminderRule(it.id,it.dayOfMonth,it.startMonth,it.resumeMonth)}.associateBy{it.id}
        val old=read(c)
        if(old==next)return@synchronized
        check(prefs(c).edit().putString("rules",json(next.values)).commit()){"Не удалось сохранить напоминания"}
        old.keys.filter{old[it]!=next[it]}.forEach{cancel(c,it)}
        next.values.filter{old[it.id]!=it}.forEach{schedule(c,it)}
        val editor=prefs(c).edit()
        (old.keys-next.keys).forEach{editor.remove("last-$it")};editor.apply()
    }
    fun rescheduleAll(c:Context)=synchronized(lock){ensureChannel(c);read(c).values.forEach{schedule(c,it)}}

    @SuppressLint("MissingPermission") // canNotify checks POST_NOTIFICATIONS immediately before posting.
    fun receive(c:Context,intent:Intent)=synchronized(lock){
        val id=intent.getStringExtra("ruleId")?:return@synchronized
        val rule=read(c)[id]?:return@synchronized
        val due=runCatching{LocalDate.parse(intent.getStringExtra("due"))}.getOrNull()?:return@synchronized
        val expected=PaymentReminderSchedule.dueDate(rule,java.time.YearMonth.from(due))
        val first=maxOf(java.time.YearMonth.parse(rule.startMonth),rule.resumeMonth.takeIf{it.isNotBlank()}?.let(java.time.YearMonth::parse)?:java.time.YearMonth.parse(rule.startMonth))
        val today=LocalDate.now()
        if(expected==due&&java.time.YearMonth.from(due)>=first&&today==due.minusDays(1)&&prefs(c).getString("last-$id","")!=due.toString()){
            ensureChannel(c)
            if(canNotify(c)){
                val open=Intent(c,MainActivity::class.java).setAction("com.duobudget.family.OPEN_PAYMENTS").setData(Uri.parse("duobudget://payments"))
                    .putExtra("openRecurringExpenses",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_payment_reminder)
                    .setContentTitle("Завтра обязательный платёж")
                    .setContentText("Открой DuoBudget, чтобы посмотреть сумму и детали.")
                    .setContentIntent(pending).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).build()
                // Names and amounts remain only in the encrypted vault, including while locked.
                runCatching{c.getSystemService(NotificationManager::class.java).notify(id,0,notification)}.onSuccess{
                    check(prefs(c).edit().putString("last-$id",due.toString()).commit())
                }
            }
        }
        schedule(c,rule)
    }
}
