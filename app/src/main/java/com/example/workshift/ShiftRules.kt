package com.example.workshift

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Pure calendar/alarm rules: no Android framework dependency.
 * Every alarm and calendar screen must derive its values from these functions.
 */
object ShiftRules {
    fun cycleIndex(anchor: LocalDate, date: LocalDate, length: Int): Int? {
        if (length <= 0 || date.isBefore(anchor)) return null
        return (ChronoUnit.DAYS.between(anchor, date) % length).toInt()
    }

    fun forDate(
        schedule: WorkSchedule?,
        types: List<DayType>,
        date: LocalDate
    ): CalendarEntry? {
        if (schedule == null || !schedule.enabled) return null
        val anchor = schedule.anchor ?: return null
        val index = cycleIndex(anchor, date, schedule.steps.size) ?: return null
        val day = schedule.overrides[date.toString()] ?: schedule.steps[index]
        val kind = types.firstOrNull { it.id == day.typeId } ?: return null
        val resolved = when (day.workMode) {
            "CUSTOM" -> day.copy()
            "OFF" -> day.copy(start = null, end = null)
            else -> day.copy(start = kind.defaultStart, end = kind.defaultEnd)
        }
        return CalendarEntry(resolved, kind, schedule.paused(date))
    }

    fun alarmForDate(
        schedule: WorkSchedule?,
        types: List<DayType>,
        date: LocalDate
    ): AlarmEntry? {
        val entry = forDate(schedule, types, date) ?: return null
        if (entry.paused || entry.type.rest) return null
        val day = entry.day
        return when (day.alarmMode) {
            "OFF" -> null
            "ON" -> day.alarmTime?.let { AlarmEntry(it, day.alarmPreviousDay, entry.type.name) }
            else -> when (entry.type.alarmMode) {
                "OFF" -> null
                "ON" -> entry.type.alarmTime?.let { AlarmEntry(it, entry.type.alarmPreviousDay, entry.type.name) }
                else -> if (schedule?.defaultAlarmEnabled == true)
                    AlarmEntry(schedule.defaultAlarmTime, schedule.defaultAlarmPreviousDay, entry.type.name)
                else null
            }
        }
    }

    fun alarmSource(schedule: WorkSchedule?, types: List<DayType>, date: LocalDate): String {
        val entry = forDate(schedule, types, date) ?: return "График на этот день не назначен"
        if (entry.paused) return "Пауза: сигнал не запланирован"
        if (entry.type.rest) return "Выходной: без будильника"
        return when (entry.day.alarmMode) {
            "OFF" -> "Отключён только для этого дня"
            "ON" -> "Отдельный будильник этого дня"
            else -> when (entry.type.alarmMode) {
                "OFF" -> "Выключен в типе дня"
                "ON" -> "Время из типа «${entry.type.name}»"
                else -> if (schedule?.defaultAlarmEnabled == true) "Время из графика"
                    else "Будильник не задан — настройте его в типе дня или графике"
            }
        }
    }

    fun moment(date: LocalDate, alarm: AlarmEntry): LocalDateTime =
        LocalDateTime.of(if (alarm.previousDay) date.minusDays(1) else date, LocalTime.parse(alarm.time))
}
