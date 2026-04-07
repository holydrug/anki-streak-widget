package com.ankistreak.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val RC_ANKI = 101
        private const val RC_NOTIFICATIONS = 100
        private const val RC_CALENDAR = 102
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var ankiStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val streak = StreakManager(this)

        val cardGoalInput = findViewById<EditText>(R.id.card_goal_input)
        val notifSwitch = findViewById<Switch>(R.id.notifications_switch)
        val reminderPicker = findViewById<TimePicker>(R.id.reminder_time_picker)
        val criticalPicker = findViewById<TimePicker>(R.id.critical_time_picker)
        val saveButton = findViewById<Button>(R.id.save_button)
        ankiStatus = findViewById(R.id.anki_status)
        val currentStreakDisplay = findViewById<TextView>(R.id.current_streak_display)
        val bestStreakDisplay = findViewById<TextView>(R.id.best_streak_display)

        cardGoalInput.setText(streak.cardGoal.toString())
        notifSwitch.isChecked = streak.notificationsEnabled

        reminderPicker.setIs24HourView(true)
        reminderPicker.hour = streak.reminderHour
        reminderPicker.minute = streak.reminderMinute

        criticalPicker.setIs24HourView(true)
        criticalPicker.hour = streak.criticalHour
        criticalPicker.minute = streak.criticalMinute

        currentStreakDisplay.text = streak.currentStreak.toString()
        bestStreakDisplay.text = "Лучший: ${streak.bestStreak}"

        // Request AnkiDroid permission first, then check status
        requestAnkiPermission()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    RC_NOTIFICATIONS
                )
            }
        }

        saveButton.setOnClickListener {
            val goal = cardGoalInput.text.toString().toIntOrNull() ?: 10
            streak.cardGoal = goal.coerceIn(1, 200)
            streak.notificationsEnabled = notifSwitch.isChecked
            streak.reminderHour = reminderPicker.hour
            streak.reminderMinute = reminderPicker.minute
            streak.criticalHour = criticalPicker.hour
            streak.criticalMinute = criticalPicker.minute

            WorkScheduler.scheduleAll(this)
            StreakWidgetProvider.updateAllWidgets(this)

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(RESULT_OK, Intent().putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                ))
            }

            Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
            finish()
        }

        val calBtn = findViewById<Button>(R.id.calendar_permission_button)
        updateCalendarButton(calBtn)
        calBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_CALENDAR), RC_CALENDAR
                )
            } else {
                CalendarWidgetProvider.updateAllWidgets(this)
                android.widget.Toast.makeText(this, "Доступ уже есть!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.debug_button).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
    }

    private fun requestAnkiPermission() {
        if (ContextCompat.checkSelfPermission(this, ANKI_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(ANKI_PERMISSION), RC_ANKI)
        } else {
            updateAnkiStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_ANKI) {
            updateAnkiStatus()
        }
        if (requestCode == RC_CALENDAR) {
            CalendarWidgetProvider.updateAllWidgets(this)
            updateCalendarButton(findViewById(R.id.calendar_permission_button))
        }
    }

    override fun onResume() {
        super.onResume()
        updateAnkiStatus()
    }

    private fun updateCalendarButton(btn: Button) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            btn.text = "Доступ к календарю: разрешён"
            btn.isEnabled = false
            btn.alpha = 0.5f
        } else {
            btn.text = "Разрешить доступ к календарю"
            btn.isEnabled = true
            btn.alpha = 1.0f
        }
    }

    private fun updateAnkiStatus() {
        val tracker = AnkiTracker(this)

        if (!tracker.isAnkiDroidInstalled()) {
            ankiStatus.text = getString(R.string.settings_anki_not_installed)
            ankiStatus.setTextColor(getColor(R.color.red_primary))
            return
        }

        val permGranted = ContextCompat.checkSelfPermission(this, ANKI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED

        val diag = tracker.diagnosAccess()

        if (diag.startsWith("ok")) {
            ankiStatus.text = "Доступ к AnkiDroid: $diag"
            ankiStatus.setTextColor(getColor(R.color.green_primary))
        } else {
            ankiStatus.text = "perm=$permGranted | $diag"
            ankiStatus.setTextColor(getColor(R.color.orange_primary))
        }
    }
}
