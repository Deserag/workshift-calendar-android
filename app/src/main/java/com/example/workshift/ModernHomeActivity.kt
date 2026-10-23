
package com.example.workshift

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.provider.AlarmClock
import java.lang.ref.WeakReference
import android.app.DatePickerDialog
import android.app.NotificationManager
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
import android.media.RingtoneManager
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.NumberPicker
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
    private val iconOptions: List<Pair<String, Int>> = listOf(
        "sun" to R.drawable.ic_type_sun,
        "moon" to R.drawable.ic_type_moon,
        "rest" to R.drawable.ic_type_rest,
        "briefcase" to R.drawable.ic_type_briefcase,
        "book" to R.drawable.ic_type_book,
        "plane" to R.drawable.ic_type_plane,
        "star" to R.drawable.ic_type_star,
        "leaf" to R.drawable.ic_type_leaf,
        "home" to R.drawable.ic_type_home
    )
    private fun iconFor(type: DayType?): Int =
        iconOptions.firstOrNull { it.first == type?.icon }?.second
            ?: if (type?.rest == true) R.drawable.ic_type_rest else R.drawable.ic_type_sun

    private var calendarExpanded = false
    private var weeklyView = false
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
        window.decorView.post {
            offerNotificationSetupOnce()
            offerFirstScheduleGuide()
        }
    }

    override fun onResume() {
        super.onResume()
        visibleActivity = WeakReference(this)
        AlarmScheduler.reschedule(this)
        if (::root.isInitialized) render()
    }

    @Deprecated("Deprecated Android activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 82 && resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                AlarmPreferences.setRingtone(this, uri)
                toast("Мелодия сохранена")
                render()
            } else toast("Мелодия не изменена")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
        if (calendarExpanded) {
            renderExpandedCalendar()
            return
        }
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
        if (!notificationsAllowed() || !AlarmScheduler.permitted(this) || !fullScreenAllowed()) {
            val notice = card()
            notice.addView(text("Настройте доступы для будильника", 15f, ink, true))
            space(notice, 5)
            notice.addView(text("Разрешения Android нужны для уведомления с отключением, точного сигнала и экрана звонка.", 12f, muted))
            space(notice, 7)
            notice.addView(action("Открыть настройки будильника", false) { tab = 2; render() })
            wide(body, notice, 12)
        }
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
            val cell = stack().apply {
                gravity = Gravity.CENTER
                setPadding(0, d(4), 0, d(4))
                background = shape(color, 13, if (date == LocalDate.now() && !selected) primary else null)
                contentDescription = date.toString() + " " + (entry?.type?.name ?: "без смены")
            }
            cell.addView(text(number.toString(), 13f, if (selected) Color.WHITE else ink, selected).apply {
                gravity = Gravity.CENTER
            })
            if (entry != null) {
                val icon = navIcon(
                    if (entry.paused) R.drawable.ic_nav_pause else iconFor(entry.type),
                    if (selected) Color.WHITE else primary
                )
                cell.addView(icon, LinearLayout.LayoutParams(d(16), d(16)).apply {
                    topMargin = d(2)
                })
            }
            if (store.alarmForDate(date) != null) {
                cell.addView(text("•", 11f, if (selected) Color.WHITE else primary, true).apply {
                    gravity = Gravity.CENTER
                })
            }
            tap(cell) { selectDate(date) }
            grid.addView(cell, params(55))
        }
        box.addView(grid)
        space(box, 12)
        box.addView(line())
        space(box, 12)
        val hint = hrow()
        hint.addView(text("●  Цвета — типы дней", 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        hint.addView(link("Сегодня") { shownMonth = YearMonth.now(); selectedDate = LocalDate.now(); render() })
        box.addView(hint)
        if (!calendarExpanded) {
            space(box, 8)
            box.addView(action("⛶  Открыть на весь экран", false) {
                calendarExpanded = true
                weeklyView = false
                render()
            })
        }
        wide(body, box)
    }

    private fun renderExpandedCalendar() {
        val top = hrow()
        top.addView(link("← Назад") { calendarExpanded = false; render() })
        top.addView(text("Календарь смен", 22f, ink, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        wide(body, top, 14)
        val tabs = hrow()
        tabs.addView(action(if (!weeklyView) "✓ Месяц" else "Месяц", !weeklyView) {
            weeklyView = false; render()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(View(this), LinearLayout.LayoutParams(d(9), 1))
        tabs.addView(action(if (weeklyView) "✓ Неделя" else "Неделя", weeklyView) {
            weeklyView = true; render()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        wide(body, tabs, 16)
        if (weeklyView) weeklyCalendar() else calendarCard()
        space(body, 11)
        selectedDayCard()
    }

    private fun weeklyCalendar() {
        val first = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
        val box = card()
        val header = hrow()
        header.addView(link("‹") { selectedDate = selectedDate.minusWeeks(1); render() }.apply {
            textSize = 27f
        })
        header.addView(text("С " + first + " по " + first.plusDays(6), 15f, ink, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(link("›") { selectedDate = selectedDate.plusWeeks(1); render() }.apply {
            textSize = 27f
        })
        box.addView(header)
        space(box, 11)
        for (offset in 0L..6L) {
            val date = first.plusDays(offset)
            val entry = store.forDate(date)
            val selected = date == selectedDate
            val tile = hrow().apply {
                setPadding(d(10), d(13), d(10), d(13))
                background = shape(if (selected) 0xFFE8EEFF.toInt() else
                    (entry?.type?.color ?: 0xFFF5F7FB.toInt()), 16)
            }
            val day = stack().apply { gravity = Gravity.CENTER }
            day.addView(text(date.dayOfMonth.toString(), 21f, ink, true).apply {
                gravity = Gravity.CENTER
            })
            day.addView(text(date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT, Locale("ru", "RU")), 12f, muted).apply {
                gravity = Gravity.CENTER
            })
            tile.addView(day, LinearLayout.LayoutParams(d(52), -2))
            if (entry != null) {
                tile.addView(navIcon(iconFor(entry.type), primary),
                    LinearLayout.LayoutParams(d(26), d(26)).apply { marginEnd = d(10) })
            }
            val labels = stack()
            labels.addView(text(entry?.type?.name ?: "Нет смены", 15f, ink, true))
            val hours = entry?.day?.let { dayEntry ->
                if (dayEntry.start != null && dayEntry.end != null) dayEntry.start + " – " + dayEntry.end
                else "Без времени"
            } ?: "Свободный день"
            labels.addView(text(hours, 12f, muted))
            tile.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            val alarm = store.alarmForDate(date)
            if (alarm != null) tile.addView(text("◷ " + alarm.time, 12f, primary, true))
            tap(tile) { selectedDate = date; shownMonth = YearMonth.from(date); render() }
            wide(box, tile, 7)
        }
        wide(body, box)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (calendarExpanded) {
            calendarExpanded = false
            render()
        } else if (tab != 0) {
            tab = 0
            render()
        } else {
            super.onBackPressed()
        }
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
            row.addView(navIcon(iconFor(entry.type), primary).apply {
                background = shape(entry.type.color, 19)
                setPadding(d(13), d(13), d(13), d(13))
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

    private fun stopActiveAlarm() {
        AlarmScheduler.cancelTest(this)
        val stopped = stopService(Intent(this, AlarmSoundService::class.java))
        toast(if (stopped) "Звук остановлен" else "Активного звонка нет; тест отменён")
        render()
    }

    private fun offerFirstScheduleGuide() {
        val prefs = getSharedPreferences("welcome_v1", Context.MODE_PRIVATE)
        if (store.schedules.isNotEmpty() || prefs.getBoolean("seen", false)) return
        prefs.edit().putBoolean("seen", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Настроим ваш первый график")
            .setMessage("Сначала можно создать типы дней с цветом, иконкой, рабочими часами и будильником. Затем составить из них последовательность смен.")
            .setPositiveButton("Настроить типы дней") { _, _ -> showTypes() }
            .setNegativeButton("Сразу добавить график") { _, _ -> editSchedule(null) }
            .show()
    }

    private fun offerNotificationSetupOnce() {
        if (Build.VERSION.SDK_INT < 33 || notificationsAllowed()) return
        val prefs = getSharedPreferences("permissions_intro_v1", Context.MODE_PRIVATE)
        if (prefs.getBoolean("shown", false)) return
        prefs.edit().putBoolean("shown", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Уведомления будильника")
            .setMessage("Чтобы во время звонка появлялась кнопка «Отключить», разрешите уведомления. Точные сигналы и экран блокировки можно настроить в разделе «Будильники».")
            .setPositiveButton("Разрешить") { _, _ -> askNotificationPermission() }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun fullScreenAllowed(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 47)
        }
    }

    private fun testAlarmSafely() {
        if (!notificationsAllowed()) {
            AlertDialog.Builder(this).setTitle("Сначала разрешите уведомления")
                .setMessage("Без уведомления с кнопкой «Отключить» легко оказаться в ситуации, когда сигнал слышен, но остановить его неудобно. Тест пока не запущен.")
                .setPositiveButton("Разрешить") { _, _ -> askNotificationPermission() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        if (!AlarmScheduler.permitted(this)) {
            AlertDialog.Builder(this).setTitle("Точные будильники выключены")
                .setMessage("Разрешите приложению назначать точные сигналы в системных настройках.")
                .setPositiveButton("Настройки") { _, _ -> openExactSettings() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        if (!fullScreenAllowed()) {
            AlertDialog.Builder(this).setTitle("Разрешите экран будильника")
                .setMessage("На Android 14+ полноэкранные оповещения требуют отдельного доступа. После включения вернитесь и повторите тест.")
                .setPositiveButton("Настройки") { _, _ -> openFullScreenSettings() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val ok = AlarmScheduler.scheduleTest(this)
        if (ok) {
            AlertDialog.Builder(this).setTitle("Тестовый сигнал установлен")
                .setMessage("Сигнал зазвонит через одну минуту. В приложении и уведомлении есть кнопка «Остановить». Если заблокировать экран, откроется экран будильника.")
                .setPositiveButton("Хорошо", null)
                .setNeutralButton("Отменить тест") { _, _ -> AlarmScheduler.cancelTest(this); toast("Тест отменён") }
                .show()
        } else toast("Не удалось запланировать тест")
    }

    private fun openExactSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + packageName)))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)))
            }
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + packageName)))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)))
            }
        }
    }

    private fun openSystemClock(alarm: AlarmEntry? = null) {
        val act = if (alarm == null) AlarmClock.ACTION_SHOW_ALARMS else AlarmClock.ACTION_SET_ALARM
        val intent = Intent(act)
        if (alarm != null) {
            val time = LocalTime.parse(alarm.time)
            intent.putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            intent.putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Смена: " + alarm.label)
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        runCatching { startActivity(intent) }.onFailure {
            toast("Системные Часы не поддерживают эту команду")
        }
    }

    private fun chooseAlarmSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодия рабочего будильника")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AlarmPreferences.ringtone(this@ModernHomeActivity))
        }
        runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 82)
        }.onFailure { toast("Выбор мелодии недоступен в этой версии Android") }
    }

    private fun soundCard(): LinearLayout = card().apply {
        addView(text("Звук будильника", 18f, ink, true))
        space(this, 7)
        addView(text("Мелодия: " + AlarmPreferences.ringtoneName(this@ModernHomeActivity), 13f, muted))
        space(this, 10)
        addView(action("Выбрать звук", false) { chooseAlarmSound() })
        val vibrate = CheckBox(this@ModernHomeActivity).apply {
            text = "Вибрация при звонке"
            isChecked = AlarmPreferences.vibrate(this@ModernHomeActivity)
            setTextColor(ink)
            setOnCheckedChangeListener { _, yes ->
                AlarmPreferences.setVibrate(this@ModernHomeActivity, yes)
            }
        }
        addView(vibrate)
        addView(text("Громкость меняется в системной настройке громкости будильников.", 12f, muted))
        space(this, 6)
        addView(action("Громкость в настройках Android", false) {
            runCatching { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                .onFailure { toast("Не удалось открыть настройки звука") }
        })
    }

    private fun renderAlarms() {
        wide(body, text("Будильники", 24f, ink, true), 7)
        wide(body, text("Время сигнала можно задать для графика или любого дня отдельно. Настройка не обязательна.", 13f, muted), 16)

        if (AlarmSoundService.ringing) {
            wide(body, action("■  Остановить звонящий будильник") { stopActiveAlarm() }, 14)
        }
        wide(body, soundCard(), 14)
        val controls = card()
        controls.addView(text("Проверка и управление", 18f, ink, true))
        space(controls, 12)
        controls.addView(action("Остановить звук / отменить тест", false) { stopActiveAlarm() })
        space(controls, 10)
        controls.addView(action("⏰  Проверить будильник через 1 минуту") { testAlarmSafely() })
        space(controls, 8)
        controls.addView(text("Тест запускается только после проверки разрешений. При звонке появятся кнопки «Отключить» и «Отложить».", 12f, muted))
        wide(body, controls, 13)

        val permissions = card()
        permissions.addView(text("Разрешения Android", 18f, ink, true))
        space(permissions, 10)
        val exact = AlarmScheduler.permitted(this)
        permissions.addView(text((if (exact) "✓" else "!") + "  Точное время сигнала",
            14f, if (exact) good else 0xFFBB772C.toInt(), true))
        if (!exact) {
            space(permissions, 6)
            permissions.addView(action("Разрешить точные будильники", false) { openExactSettings() })
        }
        space(permissions, 9)
        permissions.addView(text((if (notificationsAllowed()) "✓" else "!") + "  Уведомления и кнопка отключения",
            14f, if (notificationsAllowed()) good else 0xFFBB772C.toInt(), true))
        if (!notificationsAllowed()) {
            space(permissions, 6)
            permissions.addView(action("Разрешить уведомления", false) { askNotificationPermission() })
        }
        space(permissions, 9)
        permissions.addView(text((if (fullScreenAllowed()) "✓" else "!") + "  Экран звонка поверх блокировки",
            14f, if (fullScreenAllowed()) good else 0xFFBB772C.toInt(), true))
        if (!fullScreenAllowed()) {
            space(permissions, 6)
            permissions.addView(action("Разрешить экран будильника", false) { openFullScreenSettings() })
        }
        wide(body, permissions, 13)

        val system = card()
        system.addView(text("Системные «Часы»", 18f, ink, true))
        space(system, 7)
        system.addView(text("Можно открыть будильник телефона и перенести в него время. Android не предоставляет всем приложениям «Часы» универсальный способ автоматически создавать и удалять будильники на произвольную дату. Перенос вручную может привести к двум звонкам.", 12f, muted))
        space(system, 11)
        system.addView(action("Открыть системные Часы", false) { openSystemClock() })
        wide(body, system, 13)

        val upcoming = card()
        upcoming.addView(text("Ближайшие сигналы графика", 18f, ink, true))
        space(upcoming, 12)
        val s = store.active()
        if (s == null || !s.enabled) {
            upcoming.addView(text("Включите график, чтобы появились его будильники.", 13f, muted))
        } else {
            val items = mutableListOf<Pair<LocalDate, AlarmEntry>>()
            var date = LocalDate.now()
            repeat(62) {
                store.alarmForDate(date)?.let { items.add(date to it) }
                date = date.plusDays(1)
            }
            if (items.isEmpty()) upcoming.addView(text("Нет запланированных сигналов. Будильники необязательны.", 13f, muted))
            items.take(10).forEach { (day, alarm) ->
                val row = hrow()
                row.addView(navIcon(R.drawable.ic_nav_alarm, primary), LinearLayout.LayoutParams(d(25), d(25)))
                val labels = stack().apply { setPadding(d(10), 0, 0, 0) }
                labels.addView(text(alarm.label, 14f, ink, true))
                labels.addView(text(day.toString() + if (alarm.previousDay) " · накануне" else "", 12f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(text(alarm.time, 17f, ink, true))
                space(upcoming, 7); upcoming.addView(row)
                val launch = link("Создать такое время в системных Часах") {
                    AlertDialog.Builder(this).setTitle("Открыть системные Часы?")
                        .setMessage("Будет передано только время " + alarm.time +
                            ". Конкретную дату смены Android передать не умеет. Проверьте день вручную и не оставляйте два будильника на одно время.")
                        .setPositiveButton("Перейти") { _, _ -> openSystemClock(alarm) }
                        .setNegativeButton("Отмена", null).show()
                }
                upcoming.addView(launch)
                space(upcoming, 4)
                upcoming.addView(line())
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
            val color = navIcon(iconFor(type), primary).apply {
                background = shape(type.color, 9)
                setPadding(d(4), d(4), d(4), d(4))
            }
            row.addView(color, LinearLayout.LayoutParams(d(24), d(24)))
            row.addView(text(type.name + if (type.rest) " · выходной" else "", 14f, ink).apply {
                setPadding(d(11), 0, 0, 0)
            })
            space(box, 7); box.addView(row)
        }
        space(box, 12)
        box.addView(action("Редактировать типы и цвета", false) { showTypes() })
        wide(body, box, 15)
        wide(body, soundCard(), 15)
        val second = card()
        second.addView(text("Как работает пауза", 17f, ink, true))
        space(second, 7)
        second.addView(text("Во время паузы даты в календаре сохраняются, а будильники не звонят. По возвращении цикл продолжается без сдвига.", 13f, muted))
        space(second, 16)
        second.addView(text("О приложении", 17f, ink, true))
        space(second, 7)
        second.addView(text("Мой график 0.5 · данные сохраняются на устройстве, без регистрации и интернета.", 13f, muted))
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

    private fun showEditor(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(shape(Color.WHITE, 24))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), -2)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primary)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
    }

    private fun editorPanel(): LinearLayout =
        stack().apply { setPadding(d(21), d(12), d(21), d(18)) }
    private fun dialogScroll(panel: LinearLayout) =
        ScrollView(this).apply { addView(panel) }
    private fun input(initial: String, hint: String = "") =
        EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            setSingleLine(true)
            setText(initial)
            this.hint = hint
            textSize = 16f
            setTextColor(ink)
            backgroundTintList = ColorStateList.valueOf(primary)
        }
    /** Wheel controls replace the hard-to-use analog time dial. */
    private fun selectTime(title: String, initial: String, after: (String) -> Unit): TextView {
        val button = action(title + ": " + initial, false) { }
        button.setOnClickListener {
            val current = LocalTime.parse(button.text.toString().substringAfterLast(": ").trim())
            val content = stack().apply { setPadding(d(16), d(14), d(16), d(10)) }
            content.addView(text("Прокрутите часы и минуты", 14f, muted, true))
            space(content, 10)
            val row = hrow().apply { gravity = Gravity.CENTER }
            fun picker(low: Int, high: Int, value: Int): NumberPicker = NumberPicker(this).apply {
                minValue = low
                maxValue = high
                this.value = value
                wrapSelectorWheel = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setFormatter { i -> String.format(Locale.ROOT, "%02d", i) }
            }
            val hours = picker(0, 23, current.hour)
            val minutes = picker(0, 59, current.minute)
            row.addView(hours, LinearLayout.LayoutParams(d(102), d(195)))
            row.addView(text(":", 27f, ink, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(d(35), d(120)))
            row.addView(minutes, LinearLayout.LayoutParams(d(102), d(195)))
            content.addView(row)
            space(content, 6)
            val presets = hrow()
            listOf(0, 15, 30, 45).forEach { m ->
                val chip = action(String.format(Locale.ROOT, ":%02d", m), false) {
                    minutes.value = m
                }
                presets.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = d(4)
                })
            }
            content.addView(presets)
            val dlg = AlertDialog.Builder(this).setTitle(title)
                .setView(content)
                .setPositiveButton("Выбрать") { _, _ ->
                    val result = String.format(Locale.ROOT, "%02d:%02d", hours.value, minutes.value)
                    button.text = title + ": " + result
                    after(result)
                }
                .setNegativeButton("Отмена", null).create()
            showEditor(dlg)
        }
        return button
    }

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
        val update = {
            clock.visibility = if (default.isChecked) View.VISIBLE else View.GONE
            previous.visibility = if (default.isChecked) View.VISIBLE else View.GONE
        }
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
        showEditor(dlg)
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
                val top = hrow()
                top.addView(navIcon(iconFor(type), primary),
                    LinearLayout.LayoutParams(d(25), d(25)).apply { marginEnd = d(8) })
                top.addView(text((i + 1).toString() + ".  " + (type?.name ?: "День"),
                    15f, ink, true))
                cell.addView(top)
                val effective = if (item.workMode == "INHERIT")
                    listOf(type?.defaultStart, type?.defaultEnd)
                else if (item.workMode == "CUSTOM") listOf(item.start, item.end)
                else listOf(null, null)
                val effectiveTime = if (effective[0] != null && effective[1] != null)
                    effective[0] + "–" + effective[1] else "без времени"
                cell.addView(text(effectiveTime, 12f, muted))
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
        showEditor(dlg)
    }

    private fun editDay(original: ShiftDay, onSave: (ShiftDay) -> Unit) {
        if (store.types.isEmpty()) { toast("Сначала добавьте тип дня"); return }
        val panel = editorPanel()
        val types = store.types.toList()
        panel.addView(text("Тип дня", 14f, ink, true))
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item, types.map { it.name })
            setSelection(types.indexOfFirst { it.id == original.typeId }.coerceAtLeast(0))
        }
        panel.addView(typeSpinner)
        space(panel, 12)

        panel.addView(text("Рабочее время", 15f, ink, true))
        space(panel, 3)
        panel.addView(text("Обычно берётся из типа дня. Для этого дня можно выбрать своё время.", 12f, muted))
        val workOptions = listOf("Из типа дня", "Свои часы", "Без времени")
        val workMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item, workOptions)
            setSelection(when (original.workMode) { "CUSTOM" -> 1; "OFF" -> 2; else -> 0 })
        }
        panel.addView(workMode)
        val workSource = text("", 12f, primary)
        panel.addView(workSource)
        space(panel, 7)
        var from = original.start ?: "08:00"
        var until = original.end ?: "20:00"
        val start = selectTime("Начало смены", from) { from = it }
        val end = selectTime("Конец смены", until) { until = it }
        panel.addView(start); space(panel, 7); panel.addView(end)

        space(panel, 17)
        panel.addView(text("Будильник перед сменой", 15f, ink, true))
        space(panel, 3)
        panel.addView(text("Можно наследовать из типа, поставить свой или отключить.", 12f, muted))
        val alarmOptions = listOf("Из типа дня / графика", "Свой будильник", "Без будильника")
        val alarmMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item, alarmOptions)
            setSelection(when (original.alarmMode) { "ON" -> 1; "OFF" -> 2; else -> 0 })
        }
        panel.addView(alarmMode)
        val alarmSource = text("", 12f, primary)
        panel.addView(alarmSource)
        space(panel, 7)
        var ringTime = original.alarmTime ?: store.type(original.typeId)?.alarmTime
            ?: store.active()?.defaultAlarmTime ?: "07:00"
        val ringButton = selectTime("Время будильника", ringTime) { ringTime = it }
        panel.addView(ringButton)
        val previous = CheckBox(this).apply {
            text = "Звонить за день до смены"
            isChecked = original.alarmPreviousDay
            setTextColor(ink)
        }
        panel.addView(previous)

        fun refresh() {
            val kind = types[typeSpinner.selectedItemPosition.coerceAtLeast(0)]
            val rest = kind.rest
            workMode.isEnabled = !rest
            start.visibility = if (!rest && workMode.selectedItemPosition == 1) View.VISIBLE else View.GONE
            end.visibility = if (!rest && workMode.selectedItemPosition == 1) View.VISIBLE else View.GONE
            workSource.text = when {
                rest -> "Выходной — рабочего времени нет"
                workMode.selectedItemPosition == 0 -> "Из типа «" + kind.name + "»: " +
                    (if (kind.defaultStart != null && kind.defaultEnd != null)
                        kind.defaultStart + " – " + kind.defaultEnd else "время не задано")
                workMode.selectedItemPosition == 2 -> "Рабочее время отключено для этого дня"
                else -> "Уникальное время только для этого дня"
            }
            alarmMode.isEnabled = !rest
            ringButton.visibility = if (!rest && alarmMode.selectedItemPosition == 1) View.VISIBLE else View.GONE
            previous.visibility = if (!rest && alarmMode.selectedItemPosition == 1) View.VISIBLE else View.GONE
            alarmSource.text = when {
                rest -> "Выходной — сигнал не назначается"
                alarmMode.selectedItemPosition == 1 -> "Свое время, независимо от типа дня"
                alarmMode.selectedItemPosition == 2 -> "Будильник отключён для этого дня"
                kind.alarmMode == "ON" && kind.alarmTime != null ->
                    "Из типа «" + kind.name + "»: " + kind.alarmTime
                kind.alarmMode == "OFF" -> "В типе «" + kind.name + "» будильник отключён"
                store.active()?.defaultAlarmEnabled == true ->
                    "Из графика: " + store.active()?.defaultAlarmTime
                else -> "Будильник не задан ни в типе, ни в графике"
            }
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        workMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        alarmMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refresh()
        val dlg = AlertDialog.Builder(this).setTitle("Настроить день")
            .setView(dialogScroll(panel))
            .setPositiveButton("Готово", null)
            .setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kind = types[typeSpinner.selectedItemPosition]
                val modeWork = if (kind.rest) "OFF" else when (workMode.selectedItemPosition) {
                    1 -> "CUSTOM"; 2 -> "OFF"; else -> "INHERIT"
                }
                val modeAlarm = if (kind.rest) "OFF" else when (alarmMode.selectedItemPosition) {
                    1 -> "ON"; 2 -> "OFF"; else -> "INHERIT"
                }
                onSave(ShiftDay(
                    typeId = kind.id,
                    start = if (modeWork == "CUSTOM") from else null,
                    end = if (modeWork == "CUSTOM") until else null,
                    alarmMode = modeAlarm,
                    alarmTime = if (modeAlarm == "ON") ringTime else null,
                    alarmPreviousDay = if (modeAlarm == "ON") previous.isChecked else false,
                    workMode = modeWork
                ))
                dlg.dismiss()
            }
        }
        showEditor(dlg)
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
                r.addView(navIcon(iconFor(type), primary).apply {
                    background = shape(type.color, 10)
                    setPadding(d(5), d(5), d(5), d(5))
                }, LinearLayout.LayoutParams(d(32), d(32)))
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
        panel.addView(text("Название типа дня", 13f, muted, true))
        val name = input(existing?.name ?: "", "Например, Дневная смена")
        panel.addView(name)
        val rest = CheckBox(this).apply {
            text = "Это выходной день"
            isChecked = existing?.rest ?: false
        }
        panel.addView(rest)
        space(panel, 12)
        panel.addView(text("Цвет типа дня", 14f, ink, true))
        space(panel, 8)
        var chosen = existing?.color ?: colors[0]
        val colorGrid = GridLayout(this).apply { columnCount = 4 }
        fun redrawColors() {
            colorGrid.removeAllViews()
            colors.forEachIndexed { idx, color ->
                val tile = text(if (chosen == color) "✓" else "", 18f, primary, true).apply {
                    gravity = Gravity.CENTER
                    background = shape(color, 12, if (chosen == color) primary else null)
                    contentDescription = "Цвет " + (idx + 1)
                }
                tap(tile) { chosen = color; redrawColors() }
                colorGrid.addView(tile, GridLayout.LayoutParams().apply {
                    width = d(52); height = d(46); setMargins(d(3), d(3), d(3), d(3))
                })
            }
        }
        redrawColors()
        panel.addView(colorGrid)
        space(panel, 14)
        panel.addView(text("Иконка типа дня", 14f, ink, true))
        space(panel, 5)
        panel.addView(text("Выберите значок — он будет виден в календаре.", 12f, muted))
        space(panel, 8)
        var icon = existing?.icon ?: if (existing?.rest == true) "rest" else "sun"
        val iconGrid = GridLayout(this).apply { columnCount = 3 }
        fun redrawIcons() {
            iconGrid.removeAllViews()
            iconOptions.forEachIndexed { i, pair ->
                val view = stack().apply {
                    gravity = Gravity.CENTER
                    setPadding(d(4), d(8), d(4), d(6))
                    background = shape(if (pair.first == icon) 0xFFE9EEFF.toInt() else 0xFFF7F9FD.toInt(),
                        15, if (pair.first == icon) primary else null)
                }
                view.addView(navIcon(pair.second, primary),
                    LinearLayout.LayoutParams(d(27), d(27)))
                val names = listOf("Солнце", "Луна", "Отдых", "Работа", "Учёба", "Поездка",
                    "Звезда", "Лист", "Дом")
                space(view, 5)
                view.addView(text(names[i], 11f, muted).apply { gravity = Gravity.CENTER })
                tap(view) { icon = pair.first; redrawIcons() }
                iconGrid.addView(view, GridLayout.LayoutParams().apply {
                    width = d(79); height = d(65); setMargins(d(3), d(3), d(3), d(3))
                })
            }
        }
        redrawIcons()
        panel.addView(iconGrid)
        space(panel, 16)
        panel.addView(text("Рабочие часы по умолчанию", 15f, ink, true))
        panel.addView(text("При добавлении этого типа в цикл значения подставятся автоматически.", 12f, muted))
        val hoursEnabled = CheckBox(this).apply {
            text = "Указать рабочее время"
            isChecked = existing?.defaultStart != null && existing.defaultEnd != null
        }
        panel.addView(hoursEnabled)
        var from = existing?.defaultStart ?: "08:00"
        var end = existing?.defaultEnd ?: "20:00"
        val startBtn = selectTime("Начало работы", from) { from = it }
        val endBtn = selectTime("Конец работы", end) { end = it }
        panel.addView(startBtn); space(panel, 6); panel.addView(endBtn)
        space(panel, 16)
        panel.addView(text("Будильник по умолчанию", 15f, ink, true))
        panel.addView(text("Можно задать здесь один раз, а в конкретном дне изменить или отключить.", 12f, muted))
        val alarmChoice = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModernHomeActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Из настроек графика", "Включён: собственное время", "Отключён"))
            setSelection(when (existing?.alarmMode) { "ON" -> 1; "OFF" -> 2; else -> 0 })
        }
        panel.addView(alarmChoice)
        var alarmTime = existing?.alarmTime ?: "07:00"
        val alarmButton = selectTime("Время будильника", alarmTime) { alarmTime = it }
        panel.addView(alarmButton)
        val previous = CheckBox(this).apply {
            text = "Звонить накануне смены"
            isChecked = existing?.alarmPreviousDay ?: false
        }
        panel.addView(previous)
        fun refresh() {
            hoursEnabled.isEnabled = !rest.isChecked
            startBtn.visibility = if (!rest.isChecked && hoursEnabled.isChecked) View.VISIBLE else View.GONE
            endBtn.visibility = if (!rest.isChecked && hoursEnabled.isChecked) View.VISIBLE else View.GONE
            alarmChoice.isEnabled = !rest.isChecked
            alarmButton.visibility = if (!rest.isChecked && alarmChoice.selectedItemPosition == 1) View.VISIBLE else View.GONE
            previous.visibility = if (!rest.isChecked && alarmChoice.selectedItemPosition == 1) View.VISIBLE else View.GONE
        }
        rest.setOnCheckedChangeListener { _, _ -> refresh() }
        hoursEnabled.setOnCheckedChangeListener { _, _ -> refresh() }
        alarmChoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refresh()
        val dlg = AlertDialog.Builder(this).setTitle(if (existing == null) "Новый тип дня" else "Изменить тип дня")
            .setView(dialogScroll(panel))
            .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                if (title.isBlank()) { toast("Укажите название типа дня"); return@setOnClickListener }
                val item = existing ?: DayType(name = title, color = chosen)
                item.name = title
                item.color = chosen
                item.rest = rest.isChecked
                item.icon = icon
                item.defaultStart = if (!item.rest && hoursEnabled.isChecked) from else null
                item.defaultEnd = if (!item.rest && hoursEnabled.isChecked) end else null
                item.alarmMode = if (item.rest) "OFF" else when (alarmChoice.selectedItemPosition) {
                    1 -> "ON"; 2 -> "OFF"; else -> "INHERIT"
                }
                item.alarmTime = if (item.alarmMode == "ON") alarmTime else null
                item.alarmPreviousDay = item.alarmMode == "ON" && previous.isChecked
                if (existing == null) store.types.add(item)
                changed()
                after()
                dlg.dismiss()
            }
        }
        showEditor(dlg)
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
