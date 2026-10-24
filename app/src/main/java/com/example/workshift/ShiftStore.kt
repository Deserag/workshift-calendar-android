package com.example.workshift

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A reusable day kind; changes to its name or colour affect every schedule. */
data class DayType(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var color: Int,
    var rest: Boolean = false,
    var icon: String = "sun",
    var defaultStart: String? = null,
    var defaultEnd: String? = null,
    var alarmMode: String = "INHERIT",
    var alarmTime: String? = null,
    var alarmPreviousDay: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("color", color); put("rest", rest)
        put("icon", icon); put("defaultStart", defaultStart); put("defaultEnd", defaultEnd)
        put("alarmMode", alarmMode); put("alarmTime", alarmTime)
        put("alarmPreviousDay", alarmPreviousDay)
    }
    companion object {
        fun fromJson(o: JSONObject) = DayType(
            id = o.getString("id"),
            name = o.optString("name", "День"),
            color = o.optInt("color", 0xFFE1F6EB.toInt()),
            rest = o.optBoolean("rest"),
            icon = o.optString("icon", if (o.optBoolean("rest")) "rest" else "sun"),
            defaultStart = if (o.isNull("defaultStart")) null else o.optString("defaultStart").ifBlank { null },
            defaultEnd = if (o.isNull("defaultEnd")) null else o.optString("defaultEnd").ifBlank { null },
            alarmMode = o.optString("alarmMode", "INHERIT"),
            alarmTime = if (o.isNull("alarmTime")) null else o.optString("alarmTime").ifBlank { null },
            alarmPreviousDay = o.optBoolean("alarmPreviousDay")
        )
    }
}

/** Alarm mode is INHERIT (schedule default), ON (own time), or OFF. */
data class ShiftDay(
    var typeId: String,
    var start: String? = null,
    var end: String? = null,
    var alarmMode: String = "INHERIT",
    var alarmTime: String? = null,
    var alarmPreviousDay: Boolean = false,
    var workMode: String = "INHERIT"
) {
    fun toJson() = JSONObject().apply {
        put("typeId", typeId); put("start", start); put("end", end)
        put("alarmMode", alarmMode); put("alarmTime", alarmTime)
        put("alarmPreviousDay", alarmPreviousDay); put("workMode", workMode)
    }
    companion object {
        fun fromJson(o: JSONObject): ShiftDay {
            val start = if (o.isNull("start")) null else o.optString("start").ifBlank { null }
            val end = if (o.isNull("end")) null else o.optString("end").ifBlank { null }
            return ShiftDay(
                o.getString("typeId"), start, end, o.optString("alarmMode", "INHERIT"),
                if (o.isNull("alarmTime")) null else o.optString("alarmTime").ifBlank { null },
                o.optBoolean("alarmPreviousDay"),
                o.optString("workMode", if (start != null && end != null) "CUSTOM" else "INHERIT")
            )
        }
    }
}

data class PausePeriod(val from: LocalDate, var until: LocalDate? = null) {
    fun contains(date: LocalDate) = !date.isBefore(from) && (until == null || !date.isAfter(until))
    fun toJson() = JSONObject().apply { put("from", from.toString()); put("until", until?.toString()) }
    companion object {
        fun fromJson(o: JSONObject) = PausePeriod(
            LocalDate.parse(o.getString("from")), (if (o.isNull("until")) null else o.optString("until").ifBlank { null })?.let(LocalDate::parse)
        )
    }
}

data class WorkSchedule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var steps: MutableList<ShiftDay> = mutableListOf(),
    var anchor: LocalDate? = null,
    var enabled: Boolean = true,
    var defaultAlarmEnabled: Boolean = false,
    var defaultAlarmTime: String = "07:00",
    var defaultAlarmPreviousDay: Boolean = false,
    val overrides: MutableMap<String, ShiftDay> = mutableMapOf(),
    // One-off, non-ringing notifications only for dates explicitly selected by the user.
    val reminders: MutableMap<String, String> = mutableMapOf(),
    val pauses: MutableList<PausePeriod> = mutableListOf()
) {
    fun paused(date: LocalDate) = pauses.any { it.contains(date) }
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
        put("anchor", anchor?.toString()); put("enabled", enabled)
        put("defaultAlarmEnabled", defaultAlarmEnabled); put("defaultAlarmTime", defaultAlarmTime)
        put("defaultAlarmPreviousDay", defaultAlarmPreviousDay)
        put("overrides", JSONObject().apply { overrides.forEach { (d, v) -> put(d, v.toJson()) } })
        put("reminders", JSONObject().apply { reminders.forEach { (d, v) -> put(d, v) } })
        put("pauses", JSONArray().apply { pauses.forEach { put(it.toJson()) } })
    }
    companion object {
        fun fromJson(o: JSONObject): WorkSchedule {
            val steps = o.optJSONArray("steps") ?: JSONArray()
            val overridesJson = o.optJSONObject("overrides") ?: JSONObject()
            val pausesJson = o.optJSONArray("pauses") ?: JSONArray()
            val remindersJson = o.optJSONObject("reminders") ?: JSONObject()
            val reminders = mutableMapOf<String, String>()
            remindersJson.keys().forEach { key ->
                val valid = runCatching {
                    LocalDate.parse(key)
                    java.time.LocalTime.parse(remindersJson.getString(key))
                }.isSuccess
                if (valid) reminders[key] = remindersJson.getString(key)
            }
            val overrides = mutableMapOf<String, ShiftDay>()
            overridesJson.keys().forEach { key -> overrides[key] = ShiftDay.fromJson(overridesJson.getJSONObject(key)) }
            return WorkSchedule(
                id = o.getString("id"), name = o.optString("name", "График"),
                steps = (0 until steps.length()).map { ShiftDay.fromJson(steps.getJSONObject(it)) }.toMutableList(),
                anchor = (if (o.isNull("anchor")) null else o.optString("anchor").ifBlank { null })?.let(LocalDate::parse),
                enabled = o.optBoolean("enabled", true),
                defaultAlarmEnabled = o.optBoolean("defaultAlarmEnabled"),
                defaultAlarmTime = o.optString("defaultAlarmTime", "07:00"),
                defaultAlarmPreviousDay = o.optBoolean("defaultAlarmPreviousDay"),
                overrides = overrides,
                reminders = reminders,
                pauses = (0 until pausesJson.length()).map { PausePeriod.fromJson(pausesJson.getJSONObject(it)) }.toMutableList()
            )
        }
    }
}

data class CalendarEntry(val day: ShiftDay, val type: DayType, val paused: Boolean)
data class AlarmEntry(val time: String, val previousDay: Boolean, val label: String)

class ShiftStore(context: Context) {
    private val prefs = context.getSharedPreferences("shift_data_v2", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("shift_data_v1", Context.MODE_PRIVATE)
    val types: MutableList<DayType> = mutableListOf()
    val schedules: MutableList<WorkSchedule> = mutableListOf()
    var activeId: String? = null

    init {
        val raw = prefs.getString("state", null)
        if (raw == null) {
            types.add(DayType(id = "work", name = "Рабочий", color = 0xFF167C75.toInt()))
            types.add(DayType(id = "rest", name = "Выходной", color = 0xFF475569.toInt(), rest = true, icon = "rest"))
            if (legacy.contains("cycle")) migrateLegacy()
            save()
        } else {
            val data = JSONObject(raw)
            val a = data.optJSONArray("types") ?: JSONArray()
            val b = data.optJSONArray("schedules") ?: JSONArray()
            (0 until a.length()).forEach { types.add(DayType.fromJson(a.getJSONObject(it))) }
            (0 until b.length()).forEach { schedules.add(WorkSchedule.fromJson(b.getJSONObject(it))) }
            activeId = (if (data.isNull("activeId")) null else data.optString("activeId").ifBlank { null })
        }
    }

    /** One-time migration from the original single-cycle app. */
    private fun migrateLegacy() {
        val oldSteps = JSONArray(legacy.getString("cycle", "[]"))
        val oldOverrides = JSONObject(legacy.getString("replacements", "{}"))
        if (oldSteps.length() == 0 && oldOverrides.length() == 0) return
        val namesToIds = mutableMapOf<String, String>()
        fun convert(o: JSONObject): ShiftDay {
            val rest = o.optBoolean("off")
            val name = o.optString("title", if (rest) "Выходной" else "Смена")
            val key = "$rest::$name"
            val id = namesToIds.getOrPut(key) {
                val existing = types.firstOrNull { it.name == name && it.rest == rest }
                if (existing != null) existing.id else DayType(
                    name = name, color = if (rest) 0xFFEBEFF5.toInt() else 0xFFDDEAFF.toInt(), rest = rest
                ).also(types::add).id
            }
            return ShiftDay(
                id, if (rest) null else o.optString("start", "09:00"),
                if (rest) null else o.optString("end", "18:00"),
                if (o.optBoolean("alarm") && !rest) "ON" else "OFF",
                o.optString("alarmTime", "07:00"), o.optBoolean("alarmPreviousDay"),
                if (rest) "OFF" else "CUSTOM"
            )
        }
        val schedule = WorkSchedule(name = "Мой график")
        (0 until oldSteps.length()).forEach { schedule.steps.add(convert(oldSteps.getJSONObject(it))) }
        legacy.getString("anchor", null)?.let { schedule.anchor = LocalDate.parse(it) }
        oldOverrides.keys().forEach { key -> schedule.overrides[key] = convert(oldOverrides.getJSONObject(key)) }
        schedules.add(schedule)
        activeId = schedule.id
    }

    /** User-controlled backup for cross-signature transitions or device migrations. */
    fun exportJson(): String {
        save()
        return prefs.getString("state", null) ?: error("No schedule data")
    }

    /** Validate the whole import before replacing anything in memory or on disk. */
    fun importJson(raw: String) {
        require(raw.length <= 2_000_000) { "Backup file is too large" }
        val state = JSONObject(raw)
        val t = state.optJSONArray("types") ?: error("Missing types")
        val schedulesArray = state.optJSONArray("schedules") ?: error("Missing schedules")
        val newTypes = (0 until t.length()).map { DayType.fromJson(t.getJSONObject(it)) }
        val newSchedules = (0 until schedulesArray.length())
            .map { WorkSchedule.fromJson(schedulesArray.getJSONObject(it)) }
        require(newTypes.isNotEmpty()) { "At least one type is required" }
        val ids = newTypes.map { it.id }.toSet()
        require(ids.size == newTypes.size && ids.all { it.isNotBlank() }) { "Duplicate type ids" }
        require(newSchedules.map { it.id }.distinct().size == newSchedules.size) { "Duplicate schedules" }
        newSchedules.forEach { schedule ->
            require(schedule.reminders.keys.all { runCatching { LocalDate.parse(it) }.isSuccess }) { "Invalid reminder date" }
            require(schedule.steps.all { it.typeId in ids }) { "Unknown type in schedule" }
            require(schedule.overrides.values.all { it.typeId in ids }) { "Unknown type in calendar" }
        }
        val id = if (state.isNull("activeId")) null else state.optString("activeId").ifBlank { null }
        require(id == null || newSchedules.any { it.id == id }) { "Unknown active schedule" }
        types.clear()
        types.addAll(newTypes)
        schedules.clear()
        schedules.addAll(newSchedules)
        activeId = id
        save()
    }

    fun save() {
        val state = JSONObject().apply {
            put("types", JSONArray().apply { types.forEach { put(it.toJson()) } })
            put("schedules", JSONArray().apply { schedules.forEach { put(it.toJson()) } })
            put("activeId", activeId)
        }
        check(prefs.edit().putString("state", state.toString()).commit()) { "Could not save schedules" }
    }

    fun active(): WorkSchedule? = schedules.firstOrNull { it.id == activeId }
    fun type(id: String): DayType? = types.firstOrNull { it.id == id }
    fun hasOverride(schedule: WorkSchedule, date: LocalDate) = schedule.overrides.containsKey(date.toString())
    fun isTypeUsed(id: String): Boolean = schedules.any { s ->
        s.steps.any { it.typeId == id } || s.overrides.values.any { it.typeId == id }
    }

    fun forDate(date: LocalDate): CalendarEntry? =
        ShiftRules.forDate(active(), types, date)

    fun alarmForDate(date: LocalDate): AlarmEntry? =
        ShiftRules.alarmForDate(active(), types, date)

    fun alarmSource(date: LocalDate): String =
        ShiftRules.alarmSource(active(), types, date)

    fun addPause(schedule: WorkSchedule, from: LocalDate, until: LocalDate?): Boolean {
        if (until != null && until.isBefore(from)) return false
        val newEnd = until ?: LocalDate.MAX
        if (schedule.pauses.any { p -> !newEnd.isBefore(p.from) && !(p.until ?: LocalDate.MAX).isBefore(from) }) return false
        schedule.pauses.add(PausePeriod(from, until))
        schedule.pauses.sortBy { it.from }
        save()
        return true
    }

    /** Keep past pause history, but resume today without shifting the rotation. */
    fun resumeToday(schedule: WorkSchedule) {
        val now = LocalDate.now()
        val endedYesterday = now.minusDays(1)
        schedule.pauses.removeAll { it.from == now && it.contains(now) }
        schedule.pauses.filter { it.contains(now) }.forEach { it.until = endedYesterday }
        save()
    }
}
