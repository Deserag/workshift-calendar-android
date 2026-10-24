package com.example.workshift

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate

/** A one-off notification, not an alarm ringtone, on the day before a chosen shift. */
class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
        val selectedSchedule = intent.getStringExtra("scheduleId") ?: return
        val store = ShiftStore(context)
        val schedule = store.active() ?: return
        if (schedule.id != selectedSchedule) return
        if (ShiftRules.reminderForDate(schedule, store.types, date) == null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "shift_reminders_v1"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Напоминания перед сменами", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Обычные уведомления за день до выбранных смен" }
        )
        val kind = store.forDate(date)?.type?.name ?: "рабочая смена"
        val open = PendingIntent.getActivity(
            context, date.toEpochDay().toInt(),
            Intent(context, ModernHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focusDate", date.toString())
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Завтра: $kind")
            .setContentText("Напоминание накануне смены • $date")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(date.toEpochDay().toInt(), notification)
    }
}
