package com.ankistreak.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Get widget ID if launched from widget config
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val streak = StreakManager(this)
        val tracker = AnkiTracker(this)

        // Populate current values
        val cardGoalInput = findViewById<EditText>(R.id.card_goal_input)
        val notifSwitch = findViewById<Switch>(R.id.notifications_switch)
        val reminderPicker = findViewById<TimePicker>(R.id.reminder_time_picker)
        val criticalPicker = findViewById<TimePicker>(R.id.critical_time_picker)
        val saveButton = findViewById<Button>(R.id.save_button)
        val ankiStatus = findViewById<TextView>(R.id.anki_status)
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

        // Display streak
        currentStreakDisplay.text = streak.currentStreak.toString()
        bestStreakDisplay.text = "Лучший: ${streak.bestStreak}"

        // Check AnkiDroid status
        updateAnkiStatus(ankiStatus, tracker)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        saveButton.setOnClickListener {
            // Save settings
            val goal = cardGoalInput.text.toString().toIntOrNull() ?: 10
            streak.cardGoal = goal.coerceIn(1, 200)
            streak.notificationsEnabled = notifSwitch.isChecked
            streak.reminderHour = reminderPicker.hour
            streak.reminderMinute = reminderPicker.minute
            streak.criticalHour = criticalPicker.hour
            streak.criticalMinute = criticalPicker.minute

            // Reschedule workers with new settings
            WorkScheduler.scheduleAll(this)

            // Update widget
            StreakWidgetProvider.updateAllWidgets(this)

            // If launched as widget config, confirm
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val result = Intent().putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                )
                setResult(RESULT_OK, result)
            }

            Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateAnkiStatus(statusView: TextView, tracker: AnkiTracker) {
        when {
            !tracker.isAnkiDroidInstalled() -> {
                statusView.text = getString(R.string.settings_anki_not_installed)
                statusView.setTextColor(getColor(R.color.red_primary))
            }
            !tracker.hasContentProviderAccess() -> {
                statusView.text = getString(R.string.settings_permission_denied)
                statusView.setTextColor(getColor(R.color.orange_primary))
            }
            else -> {
                statusView.text = getString(R.string.settings_permission_granted)
                statusView.setTextColor(getColor(R.color.green_primary))
            }
        }
    }
}
