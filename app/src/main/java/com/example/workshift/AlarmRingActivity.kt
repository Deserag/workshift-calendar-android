package com.example.workshift

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmRingActivity : Activity() {
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
        val title = intent.getStringExtra("title") ?: "Рабочая смена"
        val id = intent.getIntExtra("requestId", 0)
        val scheduleId = intent.getStringExtra("scheduleId")
        val shiftDate = intent.getStringExtra("shiftDate")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 48, 30, 48)
            setBackgroundColor(Color.rgb(17, 28, 61))
        }
        layout.addView(TextView(this).apply {
            text = "⏰\nПора просыпаться!"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = title
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 40)
        })
        layout.addView(Button(this).apply {
            text = "ОТЛОЖИТЬ НА 5 МИНУТ"
            setOnClickListener { act(AlarmActionReceiver.ACTION_SNOOZE, id, title, scheduleId, shiftDate) }
        })
        layout.addView(Button(this).apply {
            text = "ОТКЛЮЧИТЬ БУДИЛЬНИК"
            setOnClickListener { act(AlarmActionReceiver.ACTION_DISMISS, id, title, scheduleId, shiftDate) }
        })
        setContentView(layout)
    }

    private fun act(actionName: String, id: Int, title: String, scheduleId: String?, shiftDate: String?) {
        sendBroadcast(Intent(this, AlarmActionReceiver::class.java).apply {
            action = actionName
            putExtra("requestId", id)
            putExtra("title", title)
            putExtra("scheduleId", scheduleId)
            putExtra("shiftDate", shiftDate)
        })
        finish()
    }
}
