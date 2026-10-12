package com.duobudget.app.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

// Only these non-financial fields are needed by Android while the vault is locked.
data class PaymentReminderRule(val id:String,val dayOfMonth:Int,val startMonth:String,val resumeMonth:String="")
data class PaymentReminderOccurrence(val due:LocalDate,val trigger:ZonedDateTime)

object PaymentReminderSchedule {
    fun dueDate(rule:PaymentReminderRule,month:YearMonth):LocalDate {
        require(rule.dayOfMonth in 1..31)
        return month.atDay(minOf(rule.dayOfMonth,month.lengthOfMonth()))
    }
    fun next(rule:PaymentReminderRule,now:ZonedDateTime,lastDue:String=""):PaymentReminderOccurrence? {
        var month=maxOf(YearMonth.from(now),YearMonth.parse(rule.startMonth))
        if(rule.resumeMonth.isNotBlank())month=maxOf(month,YearMonth.parse(rule.resumeMonth))
        repeat(3){
            val due=dueDate(rule,month)
            val reminderDate=due.minusDays(1)
            if(due.toString()!=lastDue&&reminderDate>=now.toLocalDate()){
                val morning=reminderDate.atTime(9,0).atZone(now.zone)
                return PaymentReminderOccurrence(due,if(morning>now)morning else now.plusSeconds(5))
            }
            month=month.plusMonths(1)
        }
        return null
    }
    fun nextPayment(rule:RecurringExpense,today:LocalDate=LocalDate.now()):LocalDate {
        var month=maxOf(YearMonth.from(today),YearMonth.parse(rule.startMonth))
        if(rule.resumeMonth.isNotBlank())month=maxOf(month,YearMonth.parse(rule.resumeMonth))
        var due=RecurringExpenseEngine.dueDate(rule,month)
        if(due<today){month=month.plusMonths(1);due=RecurringExpenseEngine.dueDate(rule,month)}
        return due
    }
}
