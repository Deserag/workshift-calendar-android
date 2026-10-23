package com.example.workshift

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private val store by lazy { ShiftStore(this) }
    private lateinit var content: LinearLayout
    private var selectingAnchor = false
    private val navy = Color.rgb(17, 28, 61)
    private val blue = Color.rgb(49, 91, 245)
    private val pale = Color.rgb(236, 241, 255)
    private val colorChoices = intArrayOf(
        0xFFE1F6EB.toInt(), 0xFFB7EDCF.toInt(), 0xFFFFE8AC.toInt(),
        0xFFFFCFD1.toInt(), 0xFFDDEAFF.toInt(), 0xFFD9D0FF.toInt(),
        0xFFEBEFF5.toInt(), 0xFFFFD9B8.toInt(), 0xFFC4F0F4.toInt(),
        0xFFEFDBF4.toInt(), 0xFFBCCACD.toInt(), 0xFFBAE0A1.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(22))
            setBackgroundColor(Color.rgb(247, 249, 253))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.reschedule(this)
        if (::content.isInitialized) render()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()
    private fun bg(color: Int, radius: Int = 12, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }
    private fun label(value: String, size: Float = 14f, color: Int = navy, strong: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
        }
    private fun action(name: String, onClick: () -> Unit) = Button(this).apply {
        text = name; textSize = 12f; isAllCaps = false
        setTextColor(Color.WHITE); background = bg(blue)
        setPadding(dp(9), dp(8), dp(9), dp(8))
        setOnClickListener { onClick() }
    }
    private fun smallAction(name: String, onClick: () -> Unit) = Button(this).apply {
        text = name; textSize = 11f; isAllCaps = false
        setOnClickListener { onClick() }
    }
    private fun panel(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(Color.WHITE)
        setPadding(dp(12), dp(12), dp(12), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        addView(label(title, 17f, navy, true))
    }
    private fun gap(parent: LinearLayout, amount: Int = 8) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(amount)))
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun changed() { store.save(); AlarmScheduler.reschedule(this); render() }

    private fun render() {
        content.removeAllViews()
        content.addView(label("МОЙ ГРАФИК", 25f, navy, true))
        content.addView(label("Любые смены и необязательные будильники", 13f, Color.DKGRAY))
        gap(content, 14)

        val typesPanel = panel("Типы дней")
        typesPanel.addView(label("Создавайте названия и выбирайте цвета — типы используются в любом графике."))
        gap(typesPanel)
        typesPanel.addView(action("Настроить типы и цвета") { showTypes() })
        content.addView(typesPanel)

        val schedulePanel = panel("Мои графики")
        if (store.schedules.isEmpty()) schedulePanel.addView(label("Сначала создайте график, затем укажите дату начала в календаре."))
        store.schedules.forEach { s ->
            gap(schedulePanel, 6)
            val active = s.id == store.activeId
            val status = when {
                !s.enabled -> "выключен"
                s.paused(LocalDate.now()) -> "на паузе"
                s.anchor == null -> "нет даты начала"
                else -> "включен"
            }
            schedulePanel.addView(label("${if (active) "● " else "○ "}${s.name} • $status", 14f, navy, active))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallAction(if (active) "Выбран" else "Выбрать") {
                store.activeId = s.id; selectingAnchor = false; changed()
            }.apply { isEnabled = !active }, LinearLayout.LayoutParams(0, dp(47), 1f))
            row.addView(smallAction("Настройки") { editSchedule(s) }, LinearLayout.LayoutParams(0, dp(47), 1.1f))
            row.addView(smallAction("Цикл") { editCycle(s) }, LinearLayout.LayoutParams(0, dp(47), 0.9f))
            schedulePanel.addView(row)
            schedulePanel.addView(smallAction("Удалить график «${s.name}»") {
                AlertDialog.Builder(this).setTitle("Удалить график?")
                    .setMessage("График «${s.name}», его паузы, цикл и ручные изменения будут удалены без возможности восстановления. Типы дней останутся.")
                    .setPositiveButton("Удалить") { _, _ ->
                        store.schedules.removeAll { it.id == s.id }
                        if (store.activeId == s.id) store.activeId = null
                        selectingAnchor = false
                        changed()
                    }
                    .setNegativeButton("Отмена", null).show()
            })
        }
        gap(schedulePanel)
        schedulePanel.addView(action("+ Создать ещё график") { editSchedule(null) })
        content.addView(schedulePanel)

        val selected = store.active()
        if (selected != null) {
            val control = panel("Текущий: ${selected.name}")
            val enable = CheckBox(this).apply {
                text = "График включён"; isChecked = selected.enabled
                setOnCheckedChangeListener { _, value -> selected.enabled = value; changed() }
            }
            control.addView(enable)
            if (selected.steps.isEmpty()) {
                control.addView(label("Добавьте дни в цикл, затем выберите дату начала."))
                control.addView(action("Заполнить цикл") { editCycle(selected) })
            } else {
                control.addView(label("Цикл: ${selected.steps.size} дн. • Дата начала: ${selected.anchor ?: "не выбрана"}"))
                control.addView(action(if (selected.anchor == null) "Указать начало в календаре" else "Изменить дату начала") {
                    selectingAnchor = true
                    render()
                    toast("Нажмите нужную дату в календаре")
                })
                gap(control)
                if (selected.paused(LocalDate.now())) {
                    control.addView(label("⏸ Сейчас пауза: смены сохраняются, будильники не звонят.", 13f))
                    control.addView(action("Вернуться к работе сегодня") {
                        store.resumeToday(selected); AlarmScheduler.reschedule(this); render()
                    })
                }
                control.addView(action("Паузы / отпуск / больничный") { showPauses(selected) })
            }
            content.addView(control)
        }

        val alarmPanel = panel("Будильники")
        if (selected == null || !selected.enabled ||
            (!selected.defaultAlarmEnabled && selected.steps.none { it.alarmMode == "ON" } &&
                selected.overrides.values.none { it.alarmMode == "ON" })) {
            alarmPanel.addView(label("Будильники не обязательны. Без них календарь работает без разрешений на сигналы."))
        } else if (AlarmScheduler.permitted(this)) {
            alarmPanel.addView(label("✓ Точные будильники разрешены", 13f, Color.rgb(18, 110, 68), true))
        } else {
            alarmPanel.addView(label("⚠ Android пока не разрешил точные будильники.", 13f, Color.rgb(165, 60, 40)))
        }
        if (!AlarmScheduler.permitted(this)) alarmPanel.addView(action("Разрешить точные будильники") { openAlarmPermission() })
        if (Build.VERSION.SDK_INT >= 34 && !(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()) {
            alarmPanel.addView(action("Разрешить экран будильника") { openFullScreenPermission() })
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            alarmPanel.addView(action("Разрешить уведомления") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 201) })
        }
        alarmPanel.addView(action("Тест: будильник через 1 минуту") {
            if (AlarmScheduler.scheduleTest(this)) toast("Тестовый будильник установлен")
            else { toast("Нужно разрешить точные будильники"); openAlarmPermission() }
        })
        content.addView(alarmPanel)

        if (selectingAnchor) {
            content.addView(label("Выберите ячейку даты начала: это будет первый день цикла.", 15f, blue, true))
            gap(content)
        }
        val month = YearMonth.now()
        content.addView(monthView(month))
        content.addView(monthView(month.plusMonths(1)))
        content.addView(label("Нажмите дату, чтобы изменить только этот день (в том числе его будильник).", 13f, Color.DKGRAY))
    }

    private fun slot() = GridLayout.LayoutParams().apply {
        width = 0; height = dp(28)
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
    }
    private fun lighten(color: Int): Int {
        fun blend(part: Int) = ((part * 0.43) + (255 * 0.57)).toInt()
        return Color.rgb(blend(Color.red(color)), blend(Color.green(color)), blend(Color.blue(color)))
    }
    private fun monthView(month: YearMonth): LinearLayout {
        val container = panel(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU"))).replaceFirstChar { it.uppercase() })
        val grid = GridLayout(this).apply { columnCount = 7 }
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { weekday ->
            grid.addView(label(weekday, 12f, Color.GRAY, true).apply { gravity = Gravity.CENTER }, slot())
        }
        repeat(month.atDay(1).dayOfWeek.value - 1) { grid.addView(View(this), slot()) }
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            val entry = store.forDate(date)
            val hasAlarm = store.alarmForDate(date) != null
            val isToday = date == LocalDate.now()
            val paint = when {
                entry == null -> 0xFFF6F8FB.toInt()
                entry.paused -> lighten(entry.type.color)
                else -> entry.type.color
            }
            val details = entry?.let {
                val times = if (it.day.start != null && it.day.end != null) "\n${it.day.start}" else ""
                "${it.type.name.take(9)}$times${if (hasAlarm) " ⏰" else ""}${if (it.paused) "\n⏸" else ""}"
            } ?: ""
            val tile = TextView(this).apply {
                text = "$d\n$details"; textSize = 10.5f
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(6), dp(2), 0)
                setTextColor(if (isToday) blue else navy)
                if (isToday) setTypeface(null, Typeface.BOLD)
                background = bg(paint, 8, if (isToday) blue else null)
                setOnClickListener { onDate(date) }
                contentDescription = "$date ${entry?.type?.name ?: "нет смены"}${if (entry?.paused == true) ", пауза" else ""}"
            }
            val lp = slot().apply {
                height = dp(80); setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            grid.addView(tile, lp)
        }
        container.addView(grid)
        return container
    }

    private fun onDate(date: LocalDate) {
        val schedule = store.active()
        if (schedule == null) { toast("Сначала создайте и выберите график"); return }
        if (schedule.steps.isEmpty()) { toast("Сначала добавьте дни в цикл"); editCycle(schedule); return }
        if (schedule.anchor == null || selectingAnchor) {
            AlertDialog.Builder(this).setTitle("Первый день цикла — $date?")
                .setMessage("Начало изменится только у графика «${schedule.name}». Его ручные изменения отдельных дат будут удалены.")
                .setPositiveButton("Применить") { _, _ ->
                    schedule.anchor = date; schedule.overrides.clear(); selectingAnchor = false; changed()
                }
                .setNegativeButton("Отмена", null).show()
            return
        }
        if (!schedule.enabled) { toast("Сначала включите этот график"); return }
        val entry = store.forDate(date) ?: return
        val shift = entry.day
        val alarm = store.alarmForDate(date)
        val source = if (store.hasOverride(schedule, date)) "ручное изменение" else "по циклу"
        val hours = if (shift.start != null && shift.end != null) "${shift.start}–${shift.end}" else "Время работы не указано"
        val text = "${entry.type.name} • $source\n$hours\n" + when {
            entry.paused -> "Пауза: будильник отключён"
            alarm == null -> "Без будильника"
            else -> "Будильник ${alarm.time}${if (alarm.previousDay) " (накануне)" else ""}"
        }
        AlertDialog.Builder(this).setTitle(date.toString()).setMessage(text)
            .setPositiveButton("Изменить") { _, _ ->
                editDay(shift.copy()) { modified -> schedule.overrides[date.toString()] = modified; changed() }
            }
            .setNeutralButton("По циклу") { _, _ -> schedule.overrides.remove(date.toString()); changed() }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun showTypes() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(label("Тип дня задаёт название, цвет и признак выходного. Он доступен для всех графиков."))
        panel.addView(rows)
        lateinit var redraw: () -> Unit
        redraw = {
            rows.removeAllViews()
            store.types.toList().forEach { t ->
                gap(rows)
                val typeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                typeRow.addView(View(this).apply { background = bg(t.color, 6, navy) },
                    LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(9) })
                typeRow.addView(label("${t.name}${if (t.rest) " • выходной" else ""}", 14f, navy, true))
                rows.addView(typeRow)
                val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                buttons.addView(smallAction("Изменить") { editType(t, redraw) }, LinearLayout.LayoutParams(0, dp(44), 1f))
                buttons.addView(smallAction("Удалить") {
                    if (store.isTypeUsed(t.id)) toast("Тип используется. Сначала замените его во всех графиках.")
                    else if (store.types.size == 1) toast("Нужен хотя бы один тип дня")
                    else { store.types.removeAll { it.id == t.id }; changed(); redraw() }
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                rows.addView(buttons)
            }
        }
        redraw()
        gap(panel)
        panel.addView(action("+ Новый тип дня") { editType(null, redraw) })
        AlertDialog.Builder(this).setTitle("Типы дней")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("Закрыть", null).show()
    }

    private fun editType(existing: DayType?, after: () -> Unit) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(8)) }
        val name = EditText(this).apply { hint = "Например: Ночная смена"; setSingleLine(true); setText(existing?.name ?: "") }
        val rest = CheckBox(this).apply { text = "Выходной (без рабочих будильников)"; isChecked = existing?.rest ?: false }
        body.addView(label("Название")); body.addView(name)
        body.addView(rest)
        body.addView(label("Выберите цвет"))
        var chosen = existing?.color ?: colorChoices[0]
        val palette = GridLayout(this).apply { columnCount = 4 }
        fun showPalette() {
            palette.removeAllViews()
            colorChoices.forEachIndexed { i, color ->
                val swatch = TextView(this).apply {
                    text = if (chosen == color) "✓" else ""
                    textSize = 20f; gravity = Gravity.CENTER
                    setTextColor(navy)
                    background = bg(color, 9, if (chosen == color) blue else Color.TRANSPARENT)
                    contentDescription = "Цвет ${i + 1}${if (chosen == color) ", выбран" else ""}"
                    setOnClickListener { chosen = color; showPalette() }
                }
                palette.addView(swatch, GridLayout.LayoutParams().apply {
                    width = dp(42); height = dp(42); setMargins(dp(3), dp(4), dp(3), dp(4))
                })
            }
        }
        showPalette(); body.addView(palette)
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Новый тип дня" else "Изменить тип")
            .setView(body).setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                if (title.isEmpty()) { toast("Укажите название типа дня"); return@setOnClickListener }
                if (existing == null) store.types.add(DayType(name = title, color = chosen, rest = rest.isChecked))
                else { existing.name = title; existing.color = chosen; existing.rest = rest.isChecked }
                changed(); after(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun timeButton(parent: LinearLayout, title: String, initial: String, onChange: (String) -> Unit): Button {
        parent.addView(label(title))
        val btn = smallAction(initial) { }
        fun updatePicker() {
            val parsed = LocalTime.parse(btn.text.toString())
            TimePickerDialog(this, { _, hour, minute ->
                val value = "%02d:%02d".format(Locale.ROOT, hour, minute)
                btn.text = value; onChange(value)
            }, parsed.hour, parsed.minute, true).show()
        }
        btn.setOnClickListener { updatePicker() }
        parent.addView(btn)
        return btn
    }

    private fun editSchedule(existing: WorkSchedule?) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(10)) }
        val name = EditText(this).apply { hint = "Название графика"; setSingleLine(true); setText(existing?.name ?: "") }
        val enabled = CheckBox(this).apply { text = "График включён"; isChecked = existing?.enabled ?: true }
        val alarm = CheckBox(this).apply {
            text = "Будильник по умолчанию для рабочих дней (необязательно)"
            isChecked = existing?.defaultAlarmEnabled ?: false
        }
        body.addView(label("Название графика"))
        body.addView(name)
        body.addView(enabled)
        body.addView(alarm)
        var clock = existing?.defaultAlarmTime ?: "07:00"
        val time = timeButton(body, "Время будильника по умолчанию", clock) { clock = it }
        val previous = CheckBox(this).apply {
            text = "По умолчанию звонить накануне"
            isChecked = existing?.defaultAlarmPreviousDay ?: false
        }
        body.addView(previous)
        val update = { time.isEnabled = alarm.isChecked; previous.isEnabled = alarm.isChecked }
        alarm.setOnCheckedChangeListener { _, _ -> update() }; update()
        val dlg = AlertDialog.Builder(this).setTitle(if (existing == null) "Новый график" else "Настройки графика")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                if (title.isEmpty()) { toast("Назовите график"); return@setOnClickListener }
                val s = existing ?: WorkSchedule(name = title)
                s.name = title; s.enabled = enabled.isChecked
                s.defaultAlarmEnabled = alarm.isChecked
                s.defaultAlarmTime = clock; s.defaultAlarmPreviousDay = previous.isChecked
                if (existing == null) {
                    store.schedules.add(s)
                    if (store.activeId == null) store.activeId = s.id // Only first profile auto-selected.
                }
                changed(); dlg.dismiss()
                if (existing == null) editCycle(s)
            }
        }
        dlg.show()
    }

    private fun editCycle(schedule: WorkSchedule) {
        val copy = schedule.steps.map { it.copy() }.toMutableList()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        body.addView(label("Каждый день выбирает тип и, при желании, собственные часы и будильник. Последний день сменяется первым."))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows)
        lateinit var repaint: () -> Unit
        repaint = {
            rows.removeAllViews()
            copy.forEachIndexed { i, day ->
                gap(rows)
                val typeName = store.type(day.typeId)?.name ?: "Неизвестный тип"
                val times = if (day.start != null && day.end != null) " ${day.start}–${day.end}" else ""
                rows.addView(label("${i + 1}. $typeName$times${if (day.alarmMode == "ON") " ⏰" else ""}", 13f, navy, true))
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun put(text: String, run: () -> Unit) {
                    line.addView(smallAction(text, run), LinearLayout.LayoutParams(0, dp(43), 1f))
                }
                put("↑") { if (i > 0) { val el = copy.removeAt(i); copy.add(i - 1, el); repaint() } }
                put("↓") { if (i < copy.lastIndex) { val el = copy.removeAt(i); copy.add(i + 1, el); repaint() } }
                put("Правка") { editDay(day.copy()) { copy[i] = it; repaint() } }
                put("×") { copy.removeAt(i); repaint() }
                rows.addView(line)
            }
        }
        repaint()
        gap(body)
        body.addView(action("+ Добавить день в цикл") {
            val initialType = store.types.firstOrNull { !it.rest } ?: store.types.first()
            editDay(ShiftDay(initialType.id)) { copy.add(it); repaint() }
        })
        val dlg = AlertDialog.Builder(this).setTitle("Цикл: ${schedule.name}")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (copy.isEmpty()) { toast("Добавьте хотя бы один день"); return@setOnClickListener }
                if (schedule.anchor != null && copy.size != schedule.steps.size) {
                    AlertDialog.Builder(this).setTitle("Изменить длину цикла?")
                        .setMessage("После изменения длины повторение дат пересчитается от прежней даты начала; ручные правки сохранятся.")
                        .setPositiveButton("Да") { _, _ -> schedule.steps = copy; changed(); dlg.dismiss() }
                        .setNegativeButton("Отмена", null).show()
                } else { schedule.steps = copy; changed(); dlg.dismiss() }
            }
        }
        dlg.show()
    }

    private fun editDay(initial: ShiftDay, onSave: (ShiftDay) -> Unit) {
        if (store.types.isEmpty()) { toast("Сначала создайте тип дня"); return }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        body.addView(label("Тип дня"))
        val types = store.types.toList()
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types.map { it.name })
            setSelection(types.indexOfFirst { it.id == initial.typeId }.coerceAtLeast(0))
        }
        body.addView(typeSpinner)
        val hasHours = CheckBox(this).apply {
            text = "Указать время работы"; isChecked = initial.start != null && initial.end != null
        }
        body.addView(hasHours)
        var workStart = initial.start ?: "09:00"
        var workEnd = initial.end ?: "18:00"
        val startButton = timeButton(body, "Начало смены", workStart) { workStart = it }
        val endButton = timeButton(body, "Конец смены (может быть на следующие сутки)", workEnd) { workEnd = it }
        gap(body)
        body.addView(label("Будильник этого дня"))
        val choices = listOf("По настройке графика", "Отдельный будильник", "Без будильника")
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
            setSelection(when (initial.alarmMode) { "ON" -> 1; "OFF" -> 2; else -> 0 })
        }
        body.addView(modeSpinner)
        var ringTime = initial.alarmTime ?: "07:00"
        val ringButton = timeButton(body, "Время отдельного будильника", ringTime) { ringTime = it }
        val previous = CheckBox(this).apply {
            text = "Звонить за день до смены"; isChecked = initial.alarmPreviousDay
        }
        body.addView(previous)
        body.addView(label("Режим «По настройке графика» берёт общие параметры будильника. Если он выключен — этот день тоже без будильника.", 12f, Color.DKGRAY))
        fun toggle() {
            val rest = types[typeSpinner.selectedItemPosition.coerceAtLeast(0)].rest
            hasHours.isEnabled = !rest
            startButton.isEnabled = !rest && hasHours.isChecked
            endButton.isEnabled = !rest && hasHours.isChecked
            modeSpinner.isEnabled = !rest
            ringButton.isEnabled = !rest && modeSpinner.selectedItemPosition == 1
            previous.isEnabled = !rest && modeSpinner.selectedItemPosition == 1
        }
        hasHours.setOnCheckedChangeListener { _, _ -> toggle() }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = toggle()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = toggle()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        toggle()
        val dlg = AlertDialog.Builder(this).setTitle("День цикла / календаря")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val type = types[typeSpinner.selectedItemPosition]
                val hours = !type.rest && hasHours.isChecked
                val mode = if (type.rest) "OFF" else when (modeSpinner.selectedItemPosition) {
                    1 -> "ON"; 2 -> "OFF"; else -> "INHERIT"
                }
                onSave(ShiftDay(type.id, if (hours) workStart else null, if (hours) workEnd else null,
                    mode, if (mode == "ON") ringTime else null, previous.isChecked))
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun chooseDate(current: LocalDate, callback: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, year, month, day -> callback(LocalDate.of(year, month + 1, day)) },
            current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun showPauses(schedule: WorkSchedule) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(10)) }
        body.addView(label("Пауза глушит будильники, но не удаляет смены и не сдвигает цикл. Прошедшие паузы остаются в истории."))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows)
        lateinit var redraw: () -> Unit
        redraw = {
            rows.removeAllViews()
            schedule.pauses.toList().forEach { period ->
                gap(rows)
                rows.addView(label("⏸ ${period.from} — ${period.until ?: "до возобновления"}"))
                rows.addView(smallAction("Удалить эту паузу") { schedule.pauses.remove(period); changed(); redraw() })
            }
        }
        redraw()
        gap(body)
        var start = LocalDate.now()
        var end = LocalDate.now().plusDays(6)
        val startBtn = smallAction("С даты: $start") { }
        startBtn.setOnClickListener { chooseDate(start) { start = it; startBtn.text = "С даты: $it" } }
        body.addView(startBtn)
        val indefinite = CheckBox(this).apply { text = "Без даты окончания (пока не вернусь)"; isChecked = true }
        body.addView(indefinite)
        val endBtn = smallAction("До даты включительно: $end") { }
        endBtn.setOnClickListener { chooseDate(end) { end = it; endBtn.text = "До даты включительно: $it" } }
        body.addView(endBtn)
        indefinite.setOnCheckedChangeListener { _, checked -> endBtn.isEnabled = !checked }
        endBtn.isEnabled = false
        gap(body)
        body.addView(action("Добавить паузу") {
            if (store.addPause(schedule, start, if (indefinite.isChecked) null else end)) {
                AlarmScheduler.reschedule(this); redraw(); render(); toast("Пауза сохранена")
            } else toast("Неверные даты или пауза пересекается с существующей")
        })
        AlertDialog.Builder(this).setTitle("Паузы: ${schedule.name}")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Закрыть", null).show()
    }

    private fun openAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) return
        try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }
    private fun openFullScreenPermission() {
        if (Build.VERSION.SDK_INT < 34) return
        try { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }
}
