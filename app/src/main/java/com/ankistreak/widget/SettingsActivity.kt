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

    companion object {
        private const val ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val RC_ANKI_PERMISSION = 101
        private const val RC_NOTIFICATIONS = 100
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var ankiStatus: TextView

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

        // Display streak
        currentStreakDisplay.text = streak.currentStreak.toString()
        bestStreakDisplay.text = "Лучший: ${streak.bestStreak}"

        // Check AnkiDroid status & request permission if needed
        updateAnkiStatus(tracker)
        requestAnkiPermissionIfNeeded()

        // Request notification permission on Android 13+
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

    private fun requestAnkiPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, ANKI_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ANKI_PERMISSION),
                RC_ANKI_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_ANKI_PERMISSION) {
            val tracker = AnkiTracker(this)
            updateAnkiStatus(tracker)
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Доступ к AnkiDroid получен!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check status when returning (user might have changed AnkiDroid settings)
        updateAnkiStatus(AnkiTracker(this))
    }

    private fun updateAnkiStatus(tracker: AnkiTracker) {
        val hasPermission = ContextCompat.checkSelfPermission(this, ANKI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED

        when {
            !tracker.isAnkiDroidInstalled() -> {
                ankiStatus.text = getString(R.string.settings_anki_not_installed)
                ankiStatus.setTextColor(getColor(R.color.red_primary))
            }
            !hasPermission -> {
                ankiStatus.text = getString(R.string.settings_permission_denied)
                ankiStatus.setTextColor(getColor(R.color.orange_primary))
            }
            !tracker.hasContentProviderAccess() -> {
                ankiStatus.text = "Разрешение есть, но API недоступен. Перезапустите AnkiDroid"
                ankiStatus.setTextColor(getColor(R.color.orange_primary))
            }
            else -> {
                ankiStatus.text = getString(R.string.settings_permission_granted)
                ankiStatus.setTextColor(getColor(R.color.green_primary))
            }
        }
    }
}
