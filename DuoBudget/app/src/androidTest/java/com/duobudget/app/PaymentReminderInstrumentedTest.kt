package com.duobudget.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.model.*
import com.duobudget.app.reminders.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PaymentReminderInstrumentedTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun permitNotifications(){grantTestNotificationPermission()}
    private fun rule(day:Int,start:String)=RecurringExpense(id="test_${UUID.randomUUID()}",name="Секретный кредит",amount=7654321,category="Кредит",accountId="sber",startMonth=start,dayOfMonth=day)
    private fun exists(id:String)=PendingIntent.getBroadcast(context,0,Intent(context,PaymentReminderReceiver::class.java).setAction(PaymentReminders.ACTION).setData(Uri.parse("duobudget://payment-reminder/$id")),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)!=null
    @Test fun scheduleSurvivesBootAndTimezoneBroadcastAndPauseCancelsIt(){
        val r=rule(31,YearMonth.now().plusMonths(1).toString())
        try {
            PaymentReminders.sync(context,listOf(r),BudgetData())
            assertTrue(exists(r.id))
            val registry=context.getSharedPreferences("monthly-reminder-schedule",0).getString("rules","").orEmpty()
            assertFalse(registry.contains(r.name));assertFalse(registry.contains(r.amount.toString()))
            PaymentReminderReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
            PaymentReminderReceiver().onReceive(context,Intent(Intent.ACTION_TIMEZONE_CHANGED))
            assertTrue(exists(r.id))
            PaymentReminders.sync(context,listOf(r.copy(enabled=false)),BudgetData())
            assertFalse(exists(r.id))
        }finally{PaymentReminders.sync(context,emptyList(),BudgetData())}
    }
    @Test fun receiverPostsPrivateReminderOnlyOnceAndRemovalCancelsNotification(){
        val due=LocalDate.now().plusDays(1)
        val r=rule(due.dayOfMonth,YearMonth.from(due).toString())
        val manager=context.getSystemService(NotificationManager::class.java)
        try {
            PaymentReminders.sync(context,listOf(r),BudgetData())
            val intent=Intent(context,PaymentReminderReceiver::class.java).setAction(PaymentReminders.ACTION).putExtra("ruleId",r.id).putExtra("due",due.toString())
            PaymentReminderReceiver().onReceive(context,intent)
            val deadline=System.currentTimeMillis()+5000
            while(manager.activeNotifications.none{it.tag==r.id}&&System.currentTimeMillis()<deadline)Thread.sleep(20)
            val posted=manager.activeNotifications.single{it.tag==r.id}
            assertEquals("Завтра обязательный платёж",posted.notification.extras.getString("android.title"))
            assertFalse(posted.notification.extras.toString().contains(r.name))
            assertNotNull(posted.notification.contentIntent)
            Thread.sleep(50)
            PaymentReminderReceiver().onReceive(context,intent)
            assertEquals(posted.postTime,manager.activeNotifications.single{it.tag==r.id}.postTime)
            PaymentReminders.sync(context,emptyList(),BudgetData())
            val removedDeadline=System.currentTimeMillis()+5000
            while(manager.activeNotifications.any{it.tag==r.id}&&System.currentTimeMillis()<removedDeadline)Thread.sleep(20)
            assertTrue(manager.activeNotifications.none{it.tag==r.id})
        }finally{PaymentReminders.sync(context,emptyList(),BudgetData())}
    }
}
