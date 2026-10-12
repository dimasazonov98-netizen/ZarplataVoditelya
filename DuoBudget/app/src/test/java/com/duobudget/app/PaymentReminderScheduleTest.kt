package com.duobudget.app

import com.duobudget.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PaymentReminderScheduleTest {
    private val zone=ZoneId.of("Europe/Moscow")
    private fun now(s:String)=LocalDateTime.parse(s).atZone(zone)
    private fun rule(day:Int,start:String="2024-01")=PaymentReminderRule("test",day,start)

    @Test fun reminderIsOneCalendarDayBeforeAtNineLocalTime(){
        val n=PaymentReminderSchedule.next(rule(20),now("2024-01-10T12:00:00"))!!
        assertEquals(LocalDate.of(2024,1,20),n.due)
        assertEquals(now("2024-01-19T09:00:00"),n.trigger)
    }
    @Test fun firstDayReminderFallsInPreviousMonthAndYear(){
        val n=PaymentReminderSchedule.next(rule(1,"2025-01"),now("2024-12-15T12:00:00"))!!
        assertEquals(now("2024-12-31T09:00:00"),n.trigger)
        assertEquals(LocalDate.of(2025,1,1),n.due)
    }
    @Test fun thirtyFirstHandlesLeapAndNonLeapFebruary(){
        assertEquals(LocalDate.of(2024,2,29),PaymentReminderSchedule.next(rule(31),now("2024-02-01T12:00:00"))!!.due)
        assertEquals(LocalDate.of(2023,2,28),PaymentReminderSchedule.next(rule(31,"2023-01"),now("2023-02-01T12:00:00"))!!.due)
    }
    @Test fun lateCreationOnReminderDaySchedulesSoonAndNeverDuplicatesDeliveredDate(){
        val time=now("2024-01-19T18:00:00")
        assertEquals(time.plusSeconds(5),PaymentReminderSchedule.next(rule(20),time)!!.trigger)
        assertEquals(LocalDate.of(2024,2,20),PaymentReminderSchedule.next(rule(20),time,"2024-01-20")!!.due)
    }
    @Test fun paymentDayNeverProducesMisleadingTomorrowNotification(){
        assertEquals(LocalDate.of(2024,2,20),PaymentReminderSchedule.next(rule(20),now("2024-01-20T08:00:00"))!!.due)
    }
    @Test fun futureStartAndResumeAreRespected(){
        val r=rule(15,"2024-04").copy(resumeMonth="2024-06")
        assertEquals(LocalDate.of(2024,6,15),PaymentReminderSchedule.next(r,now("2024-01-01T10:00:00"))!!.due)
    }
    @Test fun timezoneChangeKeepsNineInNewLocalTimezone(){
        val r=rule(20)
        val time=LocalDateTime.of(2024,1,10,12,0).atZone(ZoneId.of("Asia/Yekaterinburg"))
        val trigger=PaymentReminderSchedule.next(r,time)!!.trigger
        assertEquals(9,trigger.hour);assertEquals(time.zone,trigger.zone)
    }
}
