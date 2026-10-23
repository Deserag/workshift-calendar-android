package com.example.workshift

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** A dedicated, highly visible stop/snooze UI on unlocked or locked phones. */
class AlarmRingActivity : Activity() {
    private val blue = 0xFF355BEF.toInt()
    private val ink = 0xFF172245.toInt()
    private val white = Color.WHITE
    private val requestId get() = intent.getIntExtra("requestId", 0)
    private val title get() = intent.getStringExtra("title") ?: "Рабочая смена"
    private val scheduleId get() = intent.getStringExtra("scheduleId")
    private val shiftDate get() = intent.getStringExtra("shiftDate")

    private fun d(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()
    private fun background(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(r).toFloat()
    }
    private fun caption(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun button(value: String, filled: Boolean, callback: () -> Unit) = caption(
        value, 18f, if (filled) white else blue, true
    ).apply {
        minHeight = d(62)
        background = background(if (filled) blue else 0xFFEAF0FF.toInt(), 19)
        setOnClickListener { callback() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = 0xFFF4F7FF.toInt()
        window.navigationBarColor = white
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(d(26), d(32), d(26), d(32))
            setBackgroundColor(0xFFF4F7FF.toInt())
        }
        page.addView(caption("БУДИЛЬНИК", 14f, blue, true))
        page.addView(caption("◷", 96f, blue, true))
        page.addView(caption("Пора просыпаться", 30f, ink, true))
        page.addView(caption(title, 19f, 0xFF6C7793.toInt()).apply {
            setPadding(0, d(15), 0, d(48))
        })
        page.addView(button("■  ОТКЛЮЧИТЬ БУДИЛЬНИК", true) {
            act(AlarmActionReceiver.ACTION_DISMISS)
        }, LinearLayout.LayoutParams(-1, d(64)))
        page.addView(button("Отложить на 5 минут", false) {
            act(AlarmActionReceiver.ACTION_SNOOZE)
        }, LinearLayout.LayoutParams(-1, d(64)).apply { topMargin = d(13) })
        page.addView(caption("Если экран закроется, остановить звук можно и в приложении.", 12f,
            0xFF6C7793.toInt()).apply { setPadding(0, d(23), 0, 0) })
        setContentView(page)
    }

    private fun act(action: String) {
        sendBroadcast(Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra("requestId", requestId)
            putExtra("title", title)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        })
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Never leave the user trapped in a ringing alarm without a stop option.
        act(AlarmActionReceiver.ACTION_DISMISS)
    }
}
