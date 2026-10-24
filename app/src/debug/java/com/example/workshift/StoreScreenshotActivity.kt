package com.example.workshift

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.time.LocalDate

/**
 * Debug-only, isolated demo data for screenshots from a freshly created CI emulator.
 * Never part of a release APK; never run on a user's actual data/device.
 */
class StoreScreenshotActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) { finish(); return }

        val now = LocalDate.now()
        val store = ShiftStore(this)
        store.types.clear()
        store.schedules.clear()

        val day = DayType(id = "demo_day", name = "Дневная смена",
            color = 0xFF146B8B.toInt(), icon = "sun",
            defaultStart = "08:00", defaultEnd = "20:00",
            alarmMode = "ON", alarmTime = "06:30")
        val night = DayType(id = "demo_night", name = "Ночная смена",
            color = 0xFF5D3394.toInt(), icon = "moon",
            defaultStart = "20:00", defaultEnd = "08:00",
            alarmMode = "ON", alarmTime = "18:30")
        val rest = DayType(id = "demo_rest", name = "Выходной",
            color = 0xFF475569.toInt(), rest = true, icon = "rest")
        val study = DayType(id = "demo_study", name = "Подработка",
            color = 0xFFA44426.toInt(), icon = "briefcase",
            defaultStart = "10:00", defaultEnd = "16:00",
            alarmMode = "OFF")
        store.types.addAll(listOf(day, night, rest, study))

        val main = WorkSchedule(name = "Основной график",
            anchor = now.minusDays(22),
            steps = mutableListOf(
                ShiftDay(day.id), ShiftDay(day.id),
                ShiftDay(rest.id), ShiftDay(rest.id),
                ShiftDay(night.id), ShiftDay(night.id),
                ShiftDay(rest.id), ShiftDay(rest.id)
            ))
        main.overrides[now.plusDays(4).toString()] = ShiftDay(study.id)
        main.reminders[now.plusDays(8).toString()] = "19:00"
        val side = WorkSchedule(name = "Подработка по выходным",
            anchor = now.minusDays(5),
            steps = mutableListOf(ShiftDay(study.id), ShiftDay(rest.id), ShiftDay(rest.id)))
        store.schedules.add(main)
        store.schedules.add(side)
        store.activeId = main.id
        store.save()

        // Suppress the initial tour only for the freshly seeded CI test device.
        getSharedPreferences("walkthrough_v1", MODE_PRIVATE).edit()
            .putBoolean("seen", true).commit()
        getSharedPreferences("permissions_intro_v1", MODE_PRIVATE).edit()
            .putBoolean("shown", true).commit()

        val screen = intent.getStringExtra("screen") ?: "home"
        startActivity(Intent(this, ModernHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("storeScreenshotScreen", screen)
            putExtra("focusDate", now.plusDays(1).toString())
        })
        finish()
    }
}
