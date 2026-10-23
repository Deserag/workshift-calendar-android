package com.example.workshift

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

object AlarmScheduler {
    const val TEST_ID = 90000001
    private const val REFRESH_ID = 90000002
    private const val SNOOZE_OFFSET = 200_000_000
    private const val TRACKING = "scheduled_request_codes"

    private fun manager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || manager(context).canScheduleExactAlarms()

    private fun pendingAlarm(context: Context, requestId: Int, title: String, scheduleId: String? = null, shiftDate: String? = null): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.workshift.FIRE"
            data = Uri.parse("workshift://alarm/$requestId")
            putExtra("requestId", requestId)
            putExtra("title", title)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        }
        return PendingIntent.getBroadcast(
            context, requestId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showAlarm(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 17, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Android's alarm clock API is intended for wake-up alarms, even during Doze. */
    fun at(context: Context, requestId: Int, title: String, millis: Long, scheduleId: String? = null, shiftDate: String? = null): Boolean {
        if (!permitted(context) || millis <= System.currentTimeMillis()) return false
        return try {
            manager(context).setAlarmClock(
                AlarmManager.AlarmClockInfo(millis, showAlarm(context)),
                pendingAlarm(context, requestId, title, scheduleId, shiftDate)
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun scheduleTest(context: Context): Boolean =
        at(context, TEST_ID, "Проверка будильника", System.currentTimeMillis() + 60_000)

    fun snooze(context: Context, requestId: Int, title: String, scheduleId: String?, shiftDate: String?): Boolean =
        at(context, if (requestId >= SNOOZE_OFFSET) requestId else requestId + SNOOZE_OFFSET,
            title, System.currentTimeMillis() + 5 * 60_000, scheduleId, shiftDate)

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshReceiver::class.java).apply {
            action = RefreshReceiver.ACTION_MONTH_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, REFRESH_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Clear previously scheduled dates, then rebuild current + next month. */
    @Synchronized
    fun reschedule(context: Context): Int {
        val app = context.applicationContext
        val tracking = app.getSharedPreferences("alarm_tracking_v1", Context.MODE_PRIVATE)
        val old = tracking.getString(TRACKING, "").orEmpty()
        old.split(',').filter { it.isNotBlank() }.forEach { saved ->
            val requestId = saved.toIntOrNull() ?: return@forEach
            val intent = pendingAlarm(app, requestId, "")
            manager(app).cancel(intent)
            intent.cancel()
        }
        manager(app).cancel(refreshIntent(app))
        tracking.edit().remove(TRACKING).commit()

        if (!permitted(app)) return 0
        val store = ShiftStore(app)
        val active = store.active() ?: return 0
        if (!active.enabled || active.anchor == null || active.steps.isEmpty()) return 0
        val first = YearMonth.now().atDay(1)
        val last = YearMonth.now().plusMonths(1).atEndOfMonth()
        val ids = mutableListOf<Int>()
        var date = first
        while (!date.isAfter(last)) {
            val alarm = store.alarmForDate(date)
            if (alarm != null) {
                val atDate = if (alarm.previousDay) date.minusDays(1) else date
                val whenLocal = LocalDateTime.of(atDate, LocalTime.parse(alarm.time))
                val millis = whenLocal.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val requestId = date.toEpochDay().toInt() + 1_000_000
                if (at(app, requestId, "${alarm.label} • $date", millis, active.id, date.toString())) {
                    ids.add(requestId)
                }
            }
            date = date.plusDays(1)
        }
        tracking.edit().putString(TRACKING, ids.joinToString(",")).commit()
        // The two-month sliding window must continue advancing without opening the app.
        val nextMonth = YearMonth.now().plusMonths(1).atDay(1).atTime(0, 2)
        val trigger = nextMonth.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            manager(app).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, trigger, refreshIntent(app)
            )
        } catch (_: SecurityException) { /* UI shows permission is required */ }
        return ids.size
    }
}
