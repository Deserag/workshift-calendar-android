package com.example.workshift

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmSoundService : Service() {
    companion object {
        @Volatile var ringing: Boolean = false
            private set
        const val ACTION_STOP = "com.example.workshift.STOP_RINGING"
        private const val ALARM_CHANNEL = "rings_v2"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var lock: PowerManager.WakeLock? = null
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra("title") ?: "Рабочая смена"
        val requestId = intent?.getIntExtra("requestId", 0) ?: 0
        val scheduleId = intent?.getStringExtra("scheduleId")
        val shiftDate = intent?.getStringExtra("shiftDate")
        createChannel()
        val notification = notification(title, requestId, scheduleId, shiftDate)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(1001, notification)
        ringing = true
        play()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            ALARM_CHANNEL, "Звонящие будильники", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Полноэкранное оповещение о рабочей смене"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Audio is played by this foreground service instead.
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun actionIntent(action: String, id: Int, title: String, scheduleId: String?, shiftDate: String?): PendingIntent {
        val intent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra("requestId", id)
            putExtra("title", title)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        }
        return PendingIntent.getBroadcast(
            this, id * 2 + if (action == AlarmActionReceiver.ACTION_SNOOZE) 1 else 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notification(title: String, id: Int, scheduleId: String?, shiftDate: String?): Notification {
        val full = Intent(this, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("title", title)
            putExtra("requestId", id)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        }
        val fullPending = PendingIntent.getActivity(
            this, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, ALARM_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Будильник: $title")
            .setContentText("Пора готовиться к смене")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setContentIntent(fullPending)
            .setFullScreenIntent(fullPending, true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                null, "Через 5 минут",
                actionIntent(AlarmActionReceiver.ACTION_SNOOZE, id, title, scheduleId, shiftDate)
            ).build())
            .addAction(Notification.Action.Builder(
                null, "Отключить",
                actionIntent(AlarmActionReceiver.ACTION_DISMISS, id, title, scheduleId, shiftDate)
            ).build())
            .build()
    }

    private fun play() {
        if (player != null) return
        try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Workshift:AlarmSound")
            lock?.acquire(10 * 60_000L)
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(this@AlarmSoundService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (ex: Exception) {
            Log.e("WorkshiftAlarm", "Cannot play alarm tone", ex)
        }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0))
        } catch (ex: Exception) {
            Log.w("WorkshiftAlarm", "Vibration unavailable", ex)
        }
    }

    override fun onDestroy() {
        ringing = false
        handler.removeCallbacks(timeout)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        if (lock?.isHeld == true) lock?.release()
        lock = null
        super.onDestroy()
    }
}
