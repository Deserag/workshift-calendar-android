package com.example.workshift

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ShiftRulesTest {
    private val day = DayType(id = "work", name = "День", color = 0xFFDDFFEE.toInt(),
        defaultStart = "08:00", defaultEnd = "20:00", alarmMode = "ON", alarmTime = "06:30")
    private val night = DayType(id = "night", name = "Ночь", color = 0xFFAAAFFF.toInt(),
        defaultStart = "20:00", defaultEnd = "08:00", alarmMode = "ON", alarmTime = "18:30")
    private val rest = DayType(id = "rest", name = "Выходной", color = 0xFFFFEEEE.toInt(), rest = true)
    private val types = listOf(day, night, rest)
    private val start = LocalDate.of(2026, 10, 23)
    private fun cycle() = WorkSchedule(name = "2 / 1").apply {
        anchor = start
        steps = mutableListOf(ShiftDay("work"), ShiftDay("night"), ShiftDay("rest"))
    }

    @Test fun cycleDoesNotShiftAfterRestOrPause() {
        val s = cycle()
        assertNull(ShiftRules.forDate(s, types, start.minusDays(1)))
        assertEquals("work", ShiftRules.forDate(s, types, start)?.type?.id)
        assertEquals("night", ShiftRules.forDate(s, types, start.plusDays(1))?.type?.id)
        assertEquals("rest", ShiftRules.forDate(s, types, start.plusDays(2))?.type?.id)
        assertEquals("work", ShiftRules.forDate(s, types, start.plusDays(3))?.type?.id)
        s.pauses.add(PausePeriod(start, start.plusDays(2)))
        assertTrue(ShiftRules.forDate(s, types, start.plusDays(1))!!.paused)
        assertNull(ShiftRules.alarmForDate(s, types, start.plusDays(1)))
        assertEquals("work", ShiftRules.forDate(s, types, start.plusDays(3))?.type?.id)
    }

    @Test fun disabledAndNoAnchorNeverSchedule() {
        val s = cycle()
        s.enabled = false
        assertNull(ShiftRules.alarmForDate(s, types, start))
        s.enabled = true
        s.anchor = null
        assertNull(ShiftRules.forDate(s, types, start))
        s.anchor = start
        s.steps.clear()
        assertNull(ShiftRules.forDate(s, types, start))
    }

    @Test fun dayTypeDefaultsAreInheritedAndReactToChange() {
        val s = cycle()
        assertEquals("08:00", ShiftRules.forDate(s, types, start)?.day?.start)
        assertEquals("06:30", ShiftRules.alarmForDate(s, types, start)?.time)
        day.defaultStart = "09:00"
        day.alarmTime = "07:20"
        assertEquals("09:00", ShiftRules.forDate(s, types, start)?.day?.start)
        assertEquals("07:20", ShiftRules.alarmForDate(s, types, start)?.time)
    }

    @Test fun overrideIsDateSpecificAndDoesNotMutateCycle() {
        val s = cycle()
        val moved = start.plusDays(3) // Normally work
        s.overrides[moved.toString()] = ShiftDay(typeId = "rest")
        assertNull(ShiftRules.alarmForDate(s, types, moved))
        assertEquals("work", ShiftRules.forDate(s, types, start)?.type?.id)
        assertEquals("work", ShiftRules.forDate(s, types, moved.plusDays(3))?.type?.id)
        s.overrides.remove(moved.toString())
        assertEquals("work", ShiftRules.forDate(s, types, moved)?.type?.id)
    }

    @Test fun explicitDayOverrideBeatsTypeAndGraphDefaults() {
        val s = cycle()
        s.defaultAlarmEnabled = true
        s.defaultAlarmTime = "05:00"
        s.overrides[start.toString()] = ShiftDay("work", start = "10:00", end = "16:00",
            alarmMode = "ON", alarmTime = "08:15", workMode = "CUSTOM")
        assertEquals("10:00", ShiftRules.forDate(s, types, start)?.day?.start)
        assertEquals("08:15", ShiftRules.alarmForDate(s, types, start)?.time)
        assertEquals("Отдельный будильник этого дня", ShiftRules.alarmSource(s, types, start))
        s.overrides[start.toString()] = ShiftDay("work", alarmMode = "OFF")
        assertNull(ShiftRules.alarmForDate(s, types, start))
    }

    @Test fun graphFallbackAndOffModes() {
        val s = cycle()
        day.alarmMode = "INHERIT"
        day.alarmTime = null
        assertNull(ShiftRules.alarmForDate(s, types, start))
        assertTrue(ShiftRules.alarmSource(s, types, start).startsWith("Будильник не задан"))
        s.defaultAlarmEnabled = true
        s.defaultAlarmTime = "05:45"
        assertEquals("05:45", ShiftRules.alarmForDate(s, types, start)?.time)
        day.alarmMode = "OFF"
        assertNull(ShiftRules.alarmForDate(s, types, start))
        assertEquals("Выключен в типе дня", ShiftRules.alarmSource(s, types, start))
    }

    @Test fun previousDayCorrectForNightAndMonthBoundary() {
        val s = cycle()
        s.steps = mutableListOf(ShiftDay("night"))
        night.alarmPreviousDay = true
        val target = LocalDate.of(2026, 11, 1)
        val alarm = ShiftRules.alarmForDate(s, types, target)!!
        assertEquals("18:30", alarm.time)
        assertEquals(LocalDate.of(2026, 10, 31).atTime(18, 30), ShiftRules.moment(target, alarm))
    }

    @Test fun restNeverRingsEvenWhenGraphDefaultEnabled() {
        val s = cycle()
        s.defaultAlarmEnabled = true
        assertNull(ShiftRules.alarmForDate(s, types, start.plusDays(2)))
    }

    @Test fun multipleTemporaryDaysRestoreOriginalCycle() {
        val s = cycle()
        for (shift in 3L..5L) {
            s.overrides[start.plusDays(shift).toString()] = ShiftDay("rest", alarmMode = "OFF")
        }
        for (shift in 3L..5L) assertEquals("rest",
            ShiftRules.forDate(s, types, start.plusDays(shift))?.type?.id)
        assertEquals("work", ShiftRules.forDate(s, types, start.plusDays(6))?.type?.id)
        s.overrides.clear()
        assertEquals("work", ShiftRules.forDate(s, types, start.plusDays(3))?.type?.id)
    }
}
