
package com.example.workshift

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.provider.AlarmClock
import java.lang.ref.WeakReference
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
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

/**
 * v0.3 — a calendar-first home screen built with native Android views.
 * It shares ShiftStore and AlarmScheduler with the previous release:
 * no migration, reset or changes to alarm semantics are required.
 */
class ModernHomeActivity : Activity() {
    companion object {
        private var visibleActivity: WeakReference<ModernHomeActivity>? = null

        fun openRingingScreenIfVisible(context: Context, source: Intent) {
            val activity = visibleActivity?.get() ?: return
            activity.startActivity(Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtras(source)
            })
        }
    }
    private val store by lazy { ShiftStore(this) }
    private val ink = Color.rgb(20, 30, 57)
    private val muted = Color.rgb(105, 117, 149)
    private val primary = Color.rgb(49, 86, 239)
    private val surface = Color.rgb(246, 248, 253)
    private val border = Color.rgb(231, 235, 246)
    private val good = Color.rgb(14, 140, 92)
    private val colors = intArrayOf(
        0xFFFCE7AE.toInt(), 0xFFE7DBFF.toInt(), 0xFFFADDE2.toInt(),
        0xFFD5EBFF.toInt(), 0xFFD9F7E8.toInt(), 0xFFFFDEC5.toInt(),
        0xFFE1E8F2.toInt(), 0xFFF3DDF6.toInt(), 0xFFC6F2F4.toInt(),
        0xFFEEE6D8.toInt(), 0xFFC9DFB6.toInt(), 0xFFDBE4FF.toInt()
    )
    private var tab = 0
    private var shownMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()
    private var choosingStartFor: String? = null
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout

    private fun d(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 20, line: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(radius).toFloat()
            if (line != null) setStroke(d(1), line)
        }
    private fun gradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF4478FC.toInt(), 0xFF304AE0.toInt())
    ).apply { cornerRadius = d(20).toFloat() }
    private fun tap(view: View, press: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.background = RippleDrawable(ColorStateList.valueOf(0x193C65F4), view.background, null)
        view.setOnClickListener { press() }
    }
    private fun text(value: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = true
        }
    private fun space(parent: LinearLayout, px: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, d(px)))
    }
    private fun line(): View = View(this).apply {
        setBackgroundColor(border)
        layoutParams = LinearLayout.LayoutParams(-1, d(1))
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(d(18), d(18), d(18), d(18))
        background = shape(Color.WHITE, 24, 0xFFF0F2F8.toInt())
        elevation = d(2).toFloat()
    }
    private fun action(value: String, filled: Boolean = true, press: () -> Unit) =
        text(value, 14f, if (filled) Color.WHITE else primary, true).apply {
            gravity = Gravity.CENTER
            setPadding(d(17), d(13), d(17), d(13))
            background = if (filled) gradient() else shape(0xFFE9EEFF.toInt(), 15)
            tap(this, press)
        }
    private fun link(value: String, press: () -> Unit) = text(value, 13f, primary, true).apply {
        setPadding(d(9), d(9), d(9), d(9))
        tap(this, press)
    }
    private fun chip(value: String, foreground: Int, backgroundColor: Int) =
        text(value, 12f, foreground, true).apply {
            setPadding(d(11), d(6), d(11), d(6))
            background = shape(backgroundColor, 30)
        }
    private fun hrow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun stack(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun wide(parent: LinearLayout, view: View, bottom: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(bottom) })
    }
    private fun toast(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
    private fun changed() {
        store.save()
        AlarmScheduler.reschedule(this)
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = surface
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        visibleActivity = WeakReference(this)
        AlarmScheduler.reschedule(this)
        if (::root.isInitialized) render()
    }

    override fun onPause() {
        if (visibleActivity?.get() === this) visibleActivity = null
        super.onPause()
    }

    private fun render() {
        root.removeAllViews()
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        body = stack().apply { setPadding(d(16), d(15), d(16), d(24)) }
        scroller.addView(body)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        header()
        when (tab) {
            0 -> renderHome()
            1 -> renderSchedules()
            2 -> renderAlarms()
            else -> renderSettings()
        }
        bottomBar()
    }

    private fun header() {
        val row = hrow()
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = shape(0xFFE5ECFF.toInt(), 19)
            setPadding(d(4), d(4), d(4), d(4))
        }
        row.addView(icon, LinearLayout.LayoutParams(d(54), d(54)))
        val labels = stack().apply { setPadding(d(12), 0, 0, 0) }
        labels.addView(text("Мой график", 26f, ink, true))
        labels.addView(text("Ваши смены — под контролем", 12f, muted))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = text("⋯", 27f, muted, true).apply { gravity = Gravity.CENTER }
        tap(menu) { tab = 3; render() }
        row.addView(menu, LinearLayout.LayoutParams(d(36), d(46)))
        wide(body, row, 19)
    }

    private fun mainCta() {
        val button = action("＋  Добавить график") { editSchedule(null) }
        button.textSize = 16f
        button.minHeight = d(57)
        wide(body, button, 18)
    }

    private fun renderHome() {
        mainCta()
        if (AlarmSoundService.ringing) {
            wide(body, action("■  Остановить звонящий будильник") { stopActiveAlarm() }, 15)
        }
        val s = store.active()
        if (choosingStartFor != null) {
            val prompt = card()
            prompt.setBackgroundColor(0xFFEDF2FF.toInt())
            prompt.addView(text("Выберите первый день цикла", 16f, primary, true))
            space(prompt, 5)
            prompt.addView(text("Нажмите дату в календаре. С неё начнётся повторение выбранного графика.", 13f, muted))
            prompt.addView(link("Отмена") { choosingStartFor = null; render() })
            wide(body, prompt, 15)
        }
        activeCard(s)
        space(body, 17)
        calendarCard()
        space(body, 14)
        selectedDayCard()
        space(body, 6)
    }

    private fun activeCard(s: WorkSchedule?) {
        val box = card()
        if (s == null) {
            box.addView(text("ПОКА НЕТ АКТИВНОГО ГРАФИКА", 11f, muted, true))
            space(box, 10)
            box.addView(text("Начните со своего первого графика", 19f, ink, true))
            space(box, 6)
            box.addView(text("Создайте цикл смен, настройте типы дней и выберите дату его начала.", 13f, muted))
            space(box, 14)
            box.addView(action("Создать график") { editSchedule(null) })
            wide(body, box)
            return
        }
        val top = hrow()
        val icon = navIcon(R.drawable.ic_nav_calendar, primary).apply {
            setPadding(d(13), d(13), d(13), d(13))
            background = shape(0xFFECF1FF.toInt(), 17)
        }
        top.addView(icon, LinearLayout.LayoutParams(d(54), d(54)))
        val titles = stack().apply { setPadding(d(12), 0, 0, 0) }
        titles.addView(text("ТЕКУЩИЙ ГРАФИК", 10f, muted, true))
        space(titles, 5)
        titles.addView(text(s.name, 18f, ink, true))
        titles.addView(text(if (s.steps.isEmpty()) "Цикл не заполнен" else "Цикл: " + s.steps.size + " дн. · " + (s.anchor?.let { "с " + it } ?: "без даты начала"), 12f, muted))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val status = when {
            !s.enabled -> chip("Выключен", 0xFF6B7280.toInt(), 0xFFF0F2F7.toInt())
            s.paused(LocalDate.now()) -> chip("Пауза", 0xFFA46A13.toInt(), 0xFFFFF0D5.toInt())
            s.anchor == null -> chip("Ожидает", primary, 0xFFEDF1FF.toInt())
            else -> chip("Активен", good, 0xFFDCF8EC.toInt())
        }
        top.addView(status)
        tap(top) { tab = 1; render() }
        box.addView(top)
        space(box, 16)
        if (s.anchor == null && s.steps.isNotEmpty()) {
            wide(box, action("Выбрать дату начала", false) {
                choosingStartFor = s.id; tab = 0; render()
            }, 12)
        }
        if (s.anchor != null && s.steps.isNotEmpty()) {
            val items = countTypes()
            val row = hrow()
            items.forEachIndexed { i, item ->
                if (i > 0) row.addView(View(this), LinearLayout.LayoutParams(d(8), 1))
                val stat = stack().apply {
                    setPadding(d(8), d(13), d(6), d(12))
                    background = shape(item.third, 18)
                    addView(text(item.first, 18f, ink, true))
                    addView(text(item.second, 11f, muted))
                }
                row.addView(stat, LinearLayout.LayoutParams(0, -2, 1f))
            }
            box.addView(row)
        } else {
            box.addView(text("Заполните дни цикла и выберите начало, чтобы увидеть смены в календаре.", 13f, muted))
        }
        wide(body, box)
    }

    private fun countTypes(): List<Triple<String, String, Int>> {
        var day = 0; var night = 0; var off = 0
        val now = YearMonth.now()
        for (d in 1..now.lengthOfMonth()) {
            val entry = store.forDate(now.atDay(d)) ?: continue
            if (entry.type.rest) off++
            else if (entry.type.name.contains("ноч", true)) night++
            else day++
        }
        return listOf(
            Triple(day.toString(), "Рабочих", 0xFFFFF6E2.toInt()),
            Triple(night.toString(), "Ночных", 0xFFF0EAFF.toInt()),
            Triple(off.toString(), "Выходных", 0xFFFFECF0.toInt())
        )
    }

    private fun monthTitle(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU")))
            .replaceFirstChar { it.uppercase() }

    private fun calendarCard() {
        val box = card()
        val header = hrow()
        header.addView(text(monthTitle(shownMonth), 21f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(link("‹") { shownMonth = shownMonth.minusMonths(1); render() }.apply { textSize = 28f })
        header.addView(link("›") { shownMonth = shownMonth.plusMonths(1); render() }.apply { textSize = 28f })
        box.addView(header)
        space(box, 15)
        val grid = GridLayout(this).apply { columnCount = 7 }
        fun params(height: Int) = GridLayout.LayoutParams().apply {
            width = 0; this.height = d(height)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(d(2), d(3), d(2), d(3))
        }
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
            grid.addView(text(it, 12f, muted, true).apply { gravity = Gravity.CENTER }, params(27))
        }
        repeat(shownMonth.atDay(1).dayOfWeek.value - 1) {
            grid.addView(View(this), params(51))
        }
        for (number in 1..shownMonth.lengthOfMonth()) {
            val date = shownMonth.atDay(number)
            val entry = store.forDate(date)
            val selected = selectedDate == date
            val color = if (selected) primary else entry?.type?.color ?: 0xFFF7F8FC.toInt()
            val marker = when {
                entry == null -> ""
                entry.paused -> "Ⅱ"
                else -> entry.type.name.take(3)
            }
            val alarmMark = if (store.alarmForDate(date) != null) " ·" else ""
            val cell = text(number.toString() + "\n" + marker + alarmMark,
                13f, if (selected) Color.WHITE else ink, selected).apply {
                gravity = Gravity.CENTER
                setPadding(0, d(5), 0, d(4))
                background = shape(color, 13, if (date == LocalDate.now() && !selected) primary else null)
                contentDescription = date.toString() + " " + (entry?.type?.name ?: "без смены")
            }
            tap(cell) { selectDate(date) }
            grid.addView(cell, params(51))
        }
        box.addView(grid)
        space(box, 12)
        box.addView(line())
        space(box, 12)
        val hint = hrow()
        hint.addView(text("●  Цвета — типы дней", 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        hint.addView(link("Сегодня") { shownMonth = YearMonth.now(); selectedDate = LocalDate.now(); render() })
        box.addView(hint)
        wide(body, box)
    }

    private fun selectDate(date: LocalDate) {
        selectedDate = date
        val id = choosingStartFor
        if (id != null) {
            val s = store.schedules.firstOrNull { it.id == id } ?: return
            AlertDialog.Builder(this)
                .setTitle("Начать " + date.toString() + "?")
                .setMessage("Этот день станет первым днём цикла «" + s.name + "».")
                .setPositiveButton("Применить") { _, _ ->
                    s.anchor = date
                    s.overrides.clear()
                    choosingStartFor = null
                    changed()
                }.setNegativeButton("Отмена", null).show()
        } else render()
    }

    private fun selectedDayCard() {
        val date = selectedDate
        val entry = store.forDate(date)
        val outer = card()
        val head = hrow()
        head.addView(text(date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru", "RU"))),
            15f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (date == LocalDate.now()) head.addView(chip("Сегодня", primary, 0xFFE8EEFF.toInt()))
        outer.addView(head)
        space(outer, 13)
        if (entry == null) {
            outer.addView(text("На этот день пока нет смены", 14f, muted))
            if (store.active()?.anchor == null && store.active()?.steps?.isNotEmpty() == true) {
                space(outer, 10)
                outer.addView(action("Начать график с этой даты", false) {
                    choosingStartFor = store.active()?.id; selectDate(date)
                })
            }
        } else {
            val row = hrow()
            val emoji = if (entry.type.rest) "☕" else if (entry.type.name.contains("ноч", true)) "☾" else "☀"
            row.addView(text(emoji, 25f, ink).apply {
                gravity = Gravity.CENTER; background = shape(entry.type.color, 19)
            }, LinearLayout.LayoutParams(d(56), d(56)))
            val labels = stack().apply { setPadding(d(12), 0, d(5), 0) }
            labels.addView(text(entry.type.name + if (entry.paused) " · пауза" else "", 16f, ink, true))
            val hours = if (entry.day.start != null && entry.day.end != null)
                entry.day.start + " – " + entry.day.end else "Без указания времени"
            labels.addView(text(hours, 13f, muted))
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            val alarm = store.alarmForDate(date)
            row.addView(text(if (alarm == null) "♢" else "⏰\n" + alarm.time,
                12f, muted, false).apply { gravity = Gravity.CENTER })
            outer.addView(row)
            space(outer, 13)
            outer.addView(action("Изменить этот день", false) { editOverride(date) })
            if (store.active()?.let { store.hasOverride(it, date) } == true) {
                outer.addView(link("Вернуть день из цикла") {
                    store.active()?.overrides?.remove(date.toString()); changed()
                })
            }
        }
        wide(body, outer)
    }

    private fun editOverride(date: LocalDate) {
        val s = store.active() ?: return
        val day = store.forDate(date)?.day ?: return
        editDay(day.copy()) { modified -> s.overrides[date.toString()] = modified; changed() }
    }

    private fun renderSchedules() {
        mainCta()
        wide(body, text("Мои графики", 23f, ink, true), 7)
        wide(body, text("Можно хранить несколько циклов. Переключение происходит только вручную.", 13f, muted), 17)
        if (store.schedules.isEmpty()) {
            val box = card()
            box.addView(text("Пока нет графиков", 18f, ink, true))
            space(box, 6)
            box.addView(text("Нажмите «Добавить график», чтобы настроить первую последовательность смен.", 13f, muted))
            wide(body, box)
        }
        store.schedules.toList().forEach { s ->
            val selected = s.id == store.activeId
            val box = card()
            val row = hrow()
            val labels = stack()
            labels.addView(text(s.name, 19f, ink, true))
            space(labels, 5)
            labels.addView(text("Повторяется каждые " + s.steps.size + " дн.", 13f, muted))
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(chip(if (selected) "Выбран" else "Другой", if (selected) good else muted,
                if (selected) 0xFFE2F9EB.toInt() else 0xFFF2F4F8.toInt()))
            box.addView(row)
            space(box, 12)
            val info = stack()
            val status = when {
                !s.enabled -> "Выключен — сохранён, смены скрыты"
                s.paused(LocalDate.now()) -> "Пауза — смены сохранены, сигналы отключены"
                s.anchor == null -> "Не настроен — выбери дату начала"
                else -> "Включён — календарь заполнен"
            }
            info.addView(text(status, 13f, if (s.enabled) good else muted, true))
            space(info, 5)
            info.addView(text("Первый день: " + (s.anchor?.toString() ?: "не назначен"), 12f, muted))
            space(info, 5)
            info.addView(text("Будильник: " + if (s.defaultAlarmEnabled) s.defaultAlarmTime else "по настройкам дней / отключён", 12f, muted))
            box.addView(info)
            space(box, 15)
            val firstRow = hrow()
            val activate = action(if (selected) "Активный" else "Выбрать", !selected) {
                store.activeId = s.id
                if (s.anchor == null && s.steps.isNotEmpty()) {
                    choosingStartFor = s.id; tab = 0
                }
                changed()
            }
            activate.isEnabled = !selected
            firstRow.addView(activate, LinearLayout.LayoutParams(0, -2, 1f))
            firstRow.addView(View(this), LinearLayout.LayoutParams(d(8), 1))
            firstRow.addView(action("Редактировать", false) { profileMenu(s) },
                LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(firstRow)
            wide(body, box, 12)
        }
        wide(body, action("＋ Настроить типы дней", false) { showTypes() })
    }

    private fun profileMenu(s: WorkSchedule) {
        val outer = stack().apply { setPadding(d(19), d(16), d(19), d(20)) }
        outer.addView(text("НАСТРОЙКИ ГРАФИКА", 11f, primary, true))
        space(outer, 5)
        outer.addView(text(s.name, 22f, ink, true))
        space(outer, 4)
        outer.addView(text("Изменяйте только то, что необходимо", 13f, muted))
        space(outer, 15)
        val options = listOf(
            Triple("✎", "Название и будильник", "Общие параметры графика"),
            Triple("▦", "Изменить цикл", "Порядок смен, время, типы дней"),
            Triple("▣", "Дата начала", "Выберите первый день в календаре"),
            Triple("Ⅱ", "Пауза или отпуск", "Приостановить сигналы без сдвига смен"),
            Triple("●", if (s.enabled) "Выключить график" else "Включить график", "График и настройки сохранятся"),
            Triple("×", "Удалить график", "Без возможности восстановления")
        )
        val dialog = AlertDialog.Builder(this).create()
        options.forEachIndexed { index, option ->
            val row = hrow().apply {
                setPadding(d(9), d(11), d(8), d(11))
                background = shape(if (index == 5) 0xFFFFF3F4.toInt() else 0xFFF7F9FF.toInt(), 15)
            }
            val symbol = when (index) {
                0 -> R.drawable.ic_nav_edit
                1,2 -> R.drawable.ic_nav_calendar
                3 -> R.drawable.ic_nav_pause
                4 -> R.drawable.ic_nav_settings
                else -> R.drawable.ic_nav_edit
            }
            row.addView(navIcon(symbol, if (index == 5) 0xFFD04D52.toInt() else primary).apply {
                setPadding(d(8), d(8), d(8), d(8))
            }, LinearLayout.LayoutParams(d(46), d(46)))
            val words = stack()
            words.addView(text(option.second, 15f, if (index == 5) 0xFFB43B4C.toInt() else ink, true))
            space(words, 3)
            words.addView(text(option.third, 12f, muted))
            row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("›", 25f, muted))
            tap(row) {
                dialog.dismiss()
                when (index) {
                    0 -> editSchedule(s)
                    1 -> editCycle(s, false)
                    2 -> { store.activeId = s.id; choosingStartFor = s.id; tab = 0; changed() }
                    3 -> showPauses(s)
                    4 -> { s.enabled = !s.enabled; changed() }
                    5 -> AlertDialog.Builder(this).setTitle("Удалить график?")
                        .setMessage("График «" + s.name + "», история пауз и ручные изменения будут удалены.")
                        .setPositiveButton("Удалить") { _, _ ->
                            store.schedules.removeAll { it.id == s.id }
                            if (store.activeId == s.id) store.activeId = null
                            if (choosingStartFor == s.id) choosingStartFor = null
                            changed()
                        }.setNegativeButton("Отмена", null).show()
                }
            }
            wide(outer, row, 7)
        }
        space(outer, 3)
        outer.addView(action("Закрыть", false) { dialog.dismiss() })
        dialog.setView(ScrollView(this).apply { addView(outer) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(shape(Color.WHITE, 25))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * .94f).toInt(), -2)
        }
        dialog.show()
    }

    private fun renderAlarms() {
        wide(body, text("Будильники", 24f, ink, true), 7)
        wide(body, text("Будильник необязателен: его можно включить для всего графика или отдельной смены.", 13f, muted), 17)
        val box = card()
        val ok = AlarmScheduler.permitted(this)
        box.addView(text(if (ok) "✓ Точные будильники разрешены" else "⚠ Нужен доступ к точным будильникам",
            16f, if (ok) good else 0xFFB67122.toInt(), true))
        space(box, 9)
        box.addView(text("Для надёжного сигнала приложение использует системный AlarmManager, звук и вибрацию.", 13f, muted))
        if (!ok) {
            space(box, 12)
            box.addView(action("Разрешить будильники") {
                if (Build.VERSION.SDK_INT >= 31) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + packageName))
                    runCatching { startActivity(intent) }.onFailure {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + packageName)))
                    }
                }
            })
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()) {
            space(box, 8)
            box.addView(action("Разрешить экран звонка", false) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + packageName)))
                }
            })
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            space(box, 8)
            box.addView(action("Разрешить уведомления", false) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 47)
            })
        }
        space(box, 12)
        box.addView(action("⏰  Проверить через 1 минуту", false) {
            if (AlarmScheduler.scheduleTest(this)) toast("Тестовый будильник установлен")
            else toast("Сначала разрешите точные будильники")
        })
        wide(body, box, 15)

        val s = store.active()
        val upcoming = card()
        upcoming.addView(text("Ближайшие сигналы", 19f, ink, true))
        space(upcoming, 11)
        if (s == null || !s.enabled) {
            upcoming.addView(text("Включите и выберите график, чтобы увидеть будильники.", 13f, muted))
        } else {
            val items = mutableListOf<Pair<LocalDate, AlarmEntry>>()
            var date = LocalDate.now()
            repeat(62) {
                store.alarmForDate(date)?.let { items.add(date to it) }
                date = date.plusDays(1)
            }
            if (items.isEmpty()) upcoming.addView(text("Сигналов пока нет. Это нормально — будильники можно не использовать.", 13f, muted))
            items.take(10).forEach { (day, alarm) ->
                val row = hrow()
                row.addView(text("◷", 24f, primary), LinearLayout.LayoutParams(d(37), -2))
                val labels = stack()
                labels.addView(text(alarm.label, 14f, ink, true))
                labels.addView(text(day.toString() + if (alarm.previousDay) " · накануне" else "", 11f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(text(alarm.time, 17f, ink, true))
                space(upcoming, 11); upcoming.addView(row); space(upcoming, 11); upcoming.addView(line())
            }
        }
        wide(body, upcoming, 13)
        if (s != null) wide(body, action("Настроить будильник графика", false) { editSchedule(s) })
    }

    private fun renderSettings() {
        wide(body, text("Настройки", 24f, ink, true), 15)
        val box = card()
        box.addView(text("Типы дней", 18f, ink, true))
        space(box, 6)
        box.addView(text("Создавайте свои названия и выбирайте оттенки. Они доступны во всех графиках.", 13f, muted))
        space(box, 14)
        store.types.forEach { type ->
            val row = hrow()
            val color = View(this).apply { background = shape(type.color, 9) }
            row.addView(color, LinearLayout.LayoutParams(d(24), d(24)))
            row.addView(text(type.name + if (type.rest) " · выходной" else "", 14f, ink).apply {
                setPadding(d(11), 0, 0, 0)
            })
            space(box, 7); box.addView(row)
        }
        space(box, 12)
        box.addView(action("Редактировать типы и цвета", false) { showTypes() })
        wide(body, box, 15)
        val second = card()
        second.addView(text("Как работает пауза", 17f, ink, true))
        space(second, 7)
        second.addView(text("Во время паузы даты в календаре сохраняются, а будильники не звонят. По возвращении цикл продолжается без сдвига.", 13f, muted))
        space(second, 16)
        second.addView(text("О приложении", 17f, ink, true))
        space(second, 7)
        second.addView(text("Мой график 0.3 · данные сохраняются на устройстве, без регистрации и интернета.", 13f, muted))
        wide(body, second)
    }

    private fun navIcon(iconId: Int, tint: Int): ImageView = ImageView(this).apply {
        setImageResource(iconId)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun bottomBar() {
        if (AlarmSoundService.ringing) {
            val alarmRow = action("■  ОСТАНОВИТЬ БУДИЛЬНИК") { stopActiveAlarm() }
            alarmRow.setBackgroundColor(0xFFCA3850.toInt())
            wide(root, alarmRow)
        }
        val wrap = hrow().apply {
            setPadding(d(10), d(7), d(10), d(7))
            background = shape(Color.WHITE, 0)
            elevation = d(9).toFloat()
        }
        val items = listOf(
            Triple(R.drawable.ic_nav_home, "Главная", 0),
            Triple(R.drawable.ic_nav_calendar, "Графики", 1),
            Triple(R.drawable.ic_nav_alarm, "Будильники", 2),
            Triple(R.drawable.ic_nav_settings, "Настройки", 3)
        )
        items.forEach { (res, name, index) ->
            val selected = tab == index
            val column = stack().apply {
                gravity = Gravity.CENTER
                setPadding(d(1), d(7), d(1), d(7))
                background = shape(if (selected) 0xFFE7EEFF.toInt() else Color.WHITE, 18)
            }
            column.addView(navIcon(res, if (selected) primary else 0xFF687793.toInt()),
                LinearLayout.LayoutParams(d(26), d(26)))
            space(column, 4)
            column.addView(text(name, 12f, if (selected) primary else muted, selected).apply {
                gravity = Gravity.CENTER
            })
            tap(column) { tab = index; choosingStartFor = null; render() }
            wrap.addView(column, LinearLayout.LayoutParams(0, d(63), 1f).apply {
                marginStart = d(2); marginEnd = d(2)
            })
        }
        root.addView(wrap, LinearLayout.LayoutParams(-1, -2))
    }

    private fun editorPanel(): LinearLayout =
        stack().apply { setPadding(d(21), d(12), d(21), d(18)) }
    private fun dialogScroll(panel: LinearLayout) =
        ScrollView(this).apply { addView(panel) }
    private fun input(initial: String, hint: String = "") =
        EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            this.hint = hint
            textSize = 16f
            setTextColor(ink)
            backgroundTintList = ColorStateList.valueOf(primary)
        }
    private fun selectTime(title: String, initial: String, after: (String) -> Unit): TextView {
        val button = action(title + ": " + initial, false) { }
        button.setOnClickListener {
            val time = LocalTime.parse(initialFromButton(button))
            TimePickerDialog(this, { _, hours, minutes ->
                val result = String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
                button.text = title + ": " + result; after(result)
            }, time.hour, time.minute, true).show()
        }
        return button
    }
    private fun initialFromButton(view: TextView) = view.text.toString().substringAfterLast(": ").trim()
    private fun chooseDate(from: LocalDate, onDate: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, y, m, d -> onDate(LocalDate.of(y, m + 1, d)) },
            from.year, from.monthValue - 1, from.dayOfMonth).show()
    }

    private fun editSchedule(existing: WorkSchedule?) {
        val panel = editorPanel()
        panel.addView(text("Шаг 1 · Основные настройки", 12f, primary, true))
        space(panel, 12)
        panel.addView(text("Название графика", 13f, muted))
        val name = input(existing?.name ?: "", "Например, Основной график")
        panel.addView(name)
        space(panel, 13)
        val enabled = CheckBox(this).apply {
            text = "График включён"; isChecked = existing?.enabled ?: true
            setTextColor(ink)
        }
        panel.addView(enabled)
        val default = CheckBox(this).apply {
            text = "Общий будильник (необязательно)"
            isChecked = existing?.defaultAlarmEnabled ?: false
            setTextColor(ink)
        }
        panel.addView(default)
        var alarmTime = existing?.defaultAlarmTime ?: "07:00"
        val clock = selectTime("Время будильника", alarmTime) { alarmTime = it }
        panel.addView(clock)
        val previous = CheckBox(this).apply {
            text = "Будильник накануне смены"
            isChecked = existing?.defaultAlarmPreviousDay ?: false
        }
        panel.addView(previous)
        val update = { clock.isEnabled = default.isChecked; previous.isEnabled = default.isChecked }
        default.setOnCheckedChangeListener { _, _ -> update() }; update()
        val dlg = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Добавить график" else "Настройки графика")
            .setView(dialogScroll(panel))
            .setPositiveButton(if (existing == null) "Далее →" else "Сохранить", null)
            .setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                if (title.isBlank()) { toast("Введите название графика"); return@setOnClickListener }
                val schedule = existing ?: WorkSchedule(name = title)
                schedule.name = title
                schedule.enabled = enabled.isChecked
                schedule.defaultAlarmEnabled = default.isChecked
                schedule.defaultAlarmTime = alarmTime
                schedule.defaultAlarmPreviousDay = previous.isChecked
                dlg.dismiss()
                if (existing == null) editCycle(schedule, true)
                else changed()
            }
        }
        dlg.show()
    }

    private fun editCycle(s: WorkSchedule, isNew: Boolean) {
        val days = s.steps.map { it.copy() }.toMutableList()
        val panel = editorPanel()
        panel.addView(text("Шаг 2 · Последовательность дней", 12f, primary, true))
        space(panel, 7)
        panel.addView(text("Добавьте типы дней в нужном порядке. После последнего дня цикл повторится с первого.", 13f, muted))
        space(panel, 11)
        val rows = stack()
        panel.addView(rows)
        lateinit var redraw: () -> Unit
        redraw = {
            rows.removeAllViews()
            days.forEachIndexed { i, item ->
                val type = store.type(item.typeId)
                val cell = stack().apply {
                    setPadding(d(10), d(9), d(10), d(8))
                    background = shape(type?.color ?: 0xFFE8EEFF.toInt(), 16)
                }
                cell.addView(text((i + 1).toString() + ".  " + (type?.name ?: "День") +
                    (if (item.start != null) "  " + item.start + "–" + item.end else ""),
                    15f, ink, true))
                val tools = hrow()
                tools.addView(link("↑") { if (i > 0) { val it = days.removeAt(i); days.add(i - 1, it); redraw() } })
                tools.addView(link("↓") { if (i < days.lastIndex) { val it = days.removeAt(i); days.add(i + 1, it); redraw() } })
                tools.addView(link("Изменить") { editDay(item.copy()) { days[i] = it; redraw() } },
                    LinearLayout.LayoutParams(0, -2, 1f))
                tools.addView(link("✕") { days.removeAt(i); redraw() })
                cell.addView(tools)
                wide(rows, cell, 8)
            }
        }
        redraw()
        panel.addView(action("＋ Добавить день", false) {
            val first = store.types.firstOrNull { !it.rest } ?: store.types.firstOrNull()
            if (first == null) { toast("Создайте тип дня"); return@action }
            editDay(ShiftDay(first.id)) { days.add(it); redraw() }
        })
        space(panel, 9)
        panel.addView(link("Настроить типы и цвета") { showTypes() })
        val dlg = AlertDialog.Builder(this).setTitle("Цикл · " + s.name)
            .setView(dialogScroll(panel))
            .setPositiveButton(if (isNew) "Создать график" else "Сохранить", null)
            .setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (days.isEmpty()) { toast("Добавьте хотя бы один день"); return@setOnClickListener }
                val apply = {
                    s.steps = days
                    if (isNew) {
                        store.schedules.add(s)
                        if (store.activeId == null) {
                            store.activeId = s.id
                            choosingStartFor = s.id
                            tab = 0
                        }
                    }
                    changed()
                    dlg.dismiss()
                    if (isNew && store.activeId != s.id) {
                        AlertDialog.Builder(this).setTitle("График создан")
                            .setMessage("Сделать «" + s.name + "» активным? Другой график не переключается автоматически.")
                            .setPositiveButton("Выбрать график") { _, _ ->
                                store.activeId = s.id
                                choosingStartFor = s.id
                                tab = 0
                                changed()
                            }.setNegativeButton("Оставить текущий", null).show()
                    }
                }
                if (!isNew && s.anchor != null && days.size != s.steps.size) {
                    AlertDialog.Builder(this).setTitle("Пересчитать цикл?")
                        .setMessage("Длина цикла изменилась: смены пересчитаются по исходной дате начала. Ручные изменения дней сохраняются.")
                        .setPositiveButton("Да") { _, _ -> apply() }
                        .setNegativeButton("Отмена", null).show()
                } else apply()
            }
        }
        dlg.show()
    }

    private fun editDay(original: ShiftDay, onSave: (ShiftDay) -> Unit) {
        if (store.types.isEmpty()) { toast("Сначала добавьте тип дня"); return }
        val panel = editorPanel()
        val types = store.types.toList()
        panel.addView(text("Тип дня", 13f, muted, true))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item, types.map { it.name })
            setSelection(types.indexOfFirst { it.id == original.typeId }.coerceAtLeast(0))
        }
        panel.addView(spinner)
        val hours = CheckBox(this).apply {
            text = "Указать рабочее время"; isChecked = original.start != null && original.end != null
        }
        panel.addView(hours)
        var from = original.start ?: "09:00"
        var until = original.end ?: "18:00"
        val start = selectTime("Начало смены", from) { from = it }
        val end = selectTime("Конец смены", until) { until = it }
        panel.addView(start); space(panel, 7); panel.addView(end)
        space(panel, 14)
        panel.addView(text("Будильник", 13f, muted, true))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("По настройке графика", "Отдельный будильник", "Без будильника"))
            setSelection(when (original.alarmMode) { "ON" -> 1; "OFF" -> 2; else -> 0 })
        }
        panel.addView(mode)
        var alarmTime = original.alarmTime ?: "07:00"
        val alarm = selectTime("Время будильника", alarmTime) { alarmTime = it }
        panel.addView(alarm)
        val previous = CheckBox(this).apply {
            text = "Звонить за день до смены"; isChecked = original.alarmPreviousDay
        }
        panel.addView(previous)
        fun refresh() {
            val rest = types[spinner.selectedItemPosition.coerceAtLeast(0)].rest
            hours.isEnabled = !rest
            start.isEnabled = !rest && hours.isChecked
            end.isEnabled = !rest && hours.isChecked
            mode.isEnabled = !rest
            alarm.isEnabled = !rest && mode.selectedItemPosition == 1
            previous.isEnabled = !rest && mode.selectedItemPosition == 1
        }
        hours.setOnCheckedChangeListener { _, _ -> refresh() }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refresh()
        val dlg = AlertDialog.Builder(this).setTitle("Настроить день")
            .setView(dialogScroll(panel))
            .setPositiveButton("Готово", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = types[spinner.selectedItemPosition]
                val withHours = hours.isChecked && !chosen.rest
                val alarmMode = if (chosen.rest) "OFF" else when (mode.selectedItemPosition) {
                    1 -> "ON"; 2 -> "OFF"; else -> "INHERIT"
                }
                onSave(ShiftDay(chosen.id,
                    if (withHours) from else null, if (withHours) until else null,
                    alarmMode, if (alarmMode == "ON") alarmTime else null, previous.isChecked))
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun showTypes() {
        val panel = editorPanel()
        panel.addView(text("Названия и цвета используются во всех графиках.", 13f, muted))
        val rows = stack()
        space(panel, 9)
        panel.addView(rows)
        lateinit var redraw: () -> Unit
        redraw = {
            rows.removeAllViews()
            store.types.toList().forEach { type ->
                val r = hrow()
                r.addView(View(this).apply { background = shape(type.color, 10) },
                    LinearLayout.LayoutParams(d(32), d(32)))
                r.addView(text(type.name + if (type.rest) " · выходной" else "", 14f, ink, true).apply {
                    setPadding(d(10), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(link("✎") { editType(type, redraw) })
                r.addView(link("✕") {
                    if (store.isTypeUsed(type.id)) toast("Тип используется в графике — сначала замените его")
                    else if (store.types.size < 2) toast("Должен остаться хотя бы один тип")
                    else { store.types.remove(type); changed(); redraw() }
                })
                wide(rows, r, 7)
            }
        }
        redraw()
        space(panel, 11)
        panel.addView(action("＋ Новый тип дня") { editType(null, redraw) })
        AlertDialog.Builder(this).setTitle("Типы дней и цвета")
            .setView(dialogScroll(panel)).setPositiveButton("Закрыть", null).show()
    }

    private fun editType(existing: DayType?, after: () -> Unit) {
        val panel = editorPanel()
        panel.addView(text("Название", 13f, muted))
        val name = input(existing?.name ?: "", "Например, Ночная смена")
        panel.addView(name)
        val rest = CheckBox(this).apply {
            text = "Выходной (будильники отключены)"
            isChecked = existing?.rest ?: false
        }
        panel.addView(rest)
        space(panel, 8)
        panel.addView(text("Выберите цвет", 13f, muted, true))
        space(panel, 8)
        var chosen = existing?.color ?: colors[0]
        val grid = GridLayout(this).apply { columnCount = 4 }
        fun redrawColors() {
            grid.removeAllViews()
            colors.forEachIndexed { idx, color ->
                val cell = text(if (color == chosen) "✓" else "", 18f, primary, true).apply {
                    gravity = Gravity.CENTER
                    background = shape(color, 12, if (chosen == color) primary else null)
                    contentDescription = "Цвет " + (idx + 1).toString()
                }
                tap(cell) { chosen = color; redrawColors() }
                grid.addView(cell, GridLayout.LayoutParams().apply {
                    width = d(51); height = d(49); setMargins(d(3), d(3), d(3), d(3))
                })
            }
        }
        redrawColors(); panel.addView(grid)
        val dlg = AlertDialog.Builder(this).setTitle(if (existing == null) "Новый тип дня" else "Изменить тип дня")
            .setView(dialogScroll(panel))
            .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = name.text.toString().trim()
                if (value.isBlank()) { toast("Укажите название"); return@setOnClickListener }
                if (existing == null) store.types.add(DayType(name = value, color = chosen, rest = rest.isChecked))
                else { existing.name = value; existing.color = chosen; existing.rest = rest.isChecked }
                changed(); after(); dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun showPauses(s: WorkSchedule) {
        val panel = editorPanel()
        panel.addView(text("Смены продолжают идти по исходному циклу, но будильники на время паузы отключаются.", 13f, muted))
        space(panel, 12)
        val entries = stack()
        panel.addView(entries)
        lateinit var refill: () -> Unit
        refill = {
            entries.removeAllViews()
            s.pauses.toList().forEach { p ->
                val r = hrow()
                r.addView(text("⏸ " + p.from + " — " + (p.until?.toString() ?: "до возвращения"),
                    13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(link("✕") { s.pauses.remove(p); changed(); refill() })
                wide(entries, r, 9)
            }
        }
        refill()
        if (s.paused(LocalDate.now())) {
            panel.addView(action("Вернуться к работе сегодня", false) {
                store.resumeToday(s); AlarmScheduler.reschedule(this); render(); refill()
            })
        }
        var start = LocalDate.now()
        var end = start.plusDays(6)
        val startBtn = action("Начало: " + start, false) { }
        startBtn.setOnClickListener { chooseDate(start) { start = it; startBtn.text = "Начало: " + it } }
        val infinite = CheckBox(this).apply {
            text = "Без даты окончания"; isChecked = true
        }
        val endBtn = action("Окончание: " + end, false) { }
        endBtn.setOnClickListener { chooseDate(end) { end = it; endBtn.text = "Окончание: " + it } }
        infinite.setOnCheckedChangeListener { _, checked -> endBtn.isEnabled = !checked }
        endBtn.isEnabled = false
        space(panel, 15)
        panel.addView(startBtn); space(panel, 7); panel.addView(infinite); panel.addView(endBtn)
        space(panel, 13)
        panel.addView(action("Добавить паузу") {
            if (store.addPause(s, start, if (infinite.isChecked) null else end)) {
                AlarmScheduler.reschedule(this); render(); refill(); toast("Пауза сохранена")
            } else toast("Пауза пересекается с существующей или неверно указаны даты")
        })
        AlertDialog.Builder(this).setTitle("Паузы · " + s.name)
            .setView(dialogScroll(panel)).setPositiveButton("Закрыть", null).show()
    }
}
