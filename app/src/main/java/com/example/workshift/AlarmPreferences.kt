package com.example.workshift

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object AlarmPreferences {
    private const val PREFS = "alarm_sound_prefs_v1"
    private const val RINGTONE = "ringtone_uri"
    private const val VIBRATE = "vibrate"

    fun ringtone(context: Context): Uri {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RINGTONE, null)
        return if (text.isNullOrEmpty()) {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else Uri.parse(text)
    }

    fun ringtoneName(context: Context): String {
        val uri = ringtone(context)
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Системный звук"
        }.getOrDefault("Системный звук")
    }

    fun setRingtone(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(RINGTONE, uri?.toString()).apply()
    }

    fun vibrate(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VIBRATE, true)

    fun setVibrate(context: Context, yes: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(VIBRATE, yes).apply()
    }
}
