package com.duobudget.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PaymentReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        runCatching{
            if(intent.action==PaymentReminders.ACTION)PaymentReminders.receive(context,intent)
            else if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_USER_UNLOCKED))PaymentReminders.rescheduleAll(context)
        }
    }
}
