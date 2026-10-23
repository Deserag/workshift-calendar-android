package com.example.workshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Рабочая смена"
        // A cancelled/paused/replaced schedule must never fire a stale broadcast.
        val scheduleId = intent.getStringExtra("scheduleId")
        val shiftDate = intent.getStringExtra("shiftDate")
        if (scheduleId != null && shiftDate != null) {
            val store = ShiftStore(context)
            if (store.active()?.id != scheduleId ||
                runCatching { store.alarmForDate(java.time.LocalDate.parse(shiftDate)) }.getOrNull() == null) {
                return
            }
        }
        val requestId = intent.getIntExtra("requestId", 0)
        val start = Intent(context, AlarmSoundService::class.java).apply {
            putExtra("title", title)
            putExtra("requestId", requestId)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start)
            else context.startService(start)
        } catch (ex: Exception) {
            Log.e("WorkshiftAlarm", "Unable to ring alarm", ex)
        }
    }
}

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra("requestId", 0)
        val title = intent.getStringExtra("title") ?: "Рабочая смена"
        if (intent.action == ACTION_SNOOZE) AlarmScheduler.snooze(
            context, requestId, title,
            intent.getStringExtra("scheduleId"), intent.getStringExtra("shiftDate")
        )
        context.stopService(Intent(context, AlarmSoundService::class.java))
    }

    companion object {
        const val ACTION_DISMISS = "com.example.workshift.DISMISS"
        const val ACTION_SNOOZE = "com.example.workshift.SNOOZE"
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val commonEvents = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_MONTH_REFRESH
        )
        val alarmPermissionChanged = Build.VERSION.SDK_INT >= 31 &&
            intent.action == android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (intent.action in commonEvents || alarmPermissionChanged) {
            AlarmScheduler.reschedule(context)
        }
    }

    companion object { const val ACTION_MONTH_REFRESH = "com.example.workshift.REFRESH_MONTH" }
}
