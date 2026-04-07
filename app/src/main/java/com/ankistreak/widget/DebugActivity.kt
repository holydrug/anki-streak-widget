package com.ankistreak.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.LocalTime

class DebugActivity : AppCompatActivity() {

    private lateinit var debugText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        debugText = findViewById(R.id.debug_text)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh)
        val btnForceUpdate = findViewById<Button>(R.id.btn_force_update)

        val btnCopy = findViewById<Button>(R.id.btn_copy)

        btnRefresh.setOnClickListener { refresh() }
        btnForceUpdate.setOnClickListener {
            StreakWidgetProvider.updateAllWidgets(this)
            WorkScheduler.scheduleAll(this)
            refresh()
        }
        btnCopy.setOnClickListener {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("AnkiStreak Debug", debugText.text))
            Toast.makeText(this, "Скопировано!", Toast.LENGTH_SHORT).show()
        }

        refresh()
    }

    private fun refresh() {
        val sb = StringBuilder()
        val tracker = AnkiTracker(this)
        val streak = StreakManager(this)

        sb.appendLine("=== SYSTEM ===")
        sb.appendLine("Time:     ${LocalTime.now()}")
        sb.appendLine("Date:     ${LocalDate.now()}")
        sb.appendLine("Android:  ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("Device:   ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine()

        sb.appendLine("=== ANKIDROID ===")
        sb.appendLine("Installed:  ${tracker.isAnkiDroidInstalled()}")

        val ankiPerm = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        val permGranted = ContextCompat.checkSelfPermission(this, ankiPerm) ==
                PackageManager.PERMISSION_GRANTED
        sb.appendLine("Permission: $permGranted")

        val diag = tracker.diagnosAccess()
        sb.appendLine("API diag:   $diag")
        sb.appendLine()

        sb.appendLine("=== CONTENT PROVIDER RAW ===")
        try {
            val cursor = contentResolver.query(
                AnkiTracker.DECK_URI, null, null, null, null
            )
            if (cursor != null) {
                sb.appendLine("Columns: ${cursor.columnNames?.joinToString(", ")}")
                sb.appendLine("Rows:    ${cursor.count}")
                var row = 0
                while (cursor.moveToNext() && row < 10) {
                    val cols = mutableListOf<String>()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        val value = try { cursor.getString(i) } catch (_: Exception) { "?" }
                        cols.add("$name=$value")
                    }
                    sb.appendLine("  [$row] ${cols.joinToString(" | ")}")
                    row++
                }
                cursor.close()
            } else {
                sb.appendLine("cursor = null")
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
        sb.appendLine()

        sb.appendLine("=== DUE CARDS ===")
        val due = tracker.getTotalDueCards()
        sb.appendLine("Total due: $due")
        sb.appendLine()

        sb.appendLine("=== TRACKER PREFS ===")
        val tPrefs = getSharedPreferences("anki_tracker", Context.MODE_PRIVATE)
        tPrefs.all.forEach { (k, v) ->
            sb.appendLine("  $k = $v")
        }
        sb.appendLine()

        sb.appendLine("=== REVIEWED TODAY ===")
        val reviewed = tracker.getReviewedToday()
        sb.appendLine("Reviewed: $reviewed")
        sb.appendLine("(after calling getReviewedToday)")
        sb.appendLine()

        sb.appendLine("=== TRACKER PREFS (AFTER) ===")
        tPrefs.all.forEach { (k, v) ->
            sb.appendLine("  $k = $v")
        }
        sb.appendLine()

        sb.appendLine("=== STREAK ===")
        sb.appendLine("Current:    ${streak.currentStreak}")
        sb.appendLine("Best:       ${streak.bestStreak}")
        sb.appendLine("Last study: ${streak.lastStudyDate}")
        sb.appendLine("Today done: ${streak.isTodayDone}")
        sb.appendLine("Card goal:  ${streak.cardGoal}")
        sb.appendLine("Milestone:  ${streak.isMilestone()}")
        sb.appendLine()

        sb.appendLine("=== STREAK PREFS ===")
        val sPrefs = getSharedPreferences("streak_data", Context.MODE_PRIVATE)
        sPrefs.all.forEach { (k, v) ->
            sb.appendLine("  $k = $v")
        }
        sb.appendLine()

        sb.appendLine("=== WEEK STATUS ===")
        streak.getWeekStatus().forEach { (day, status) ->
            sb.appendLine("  $day = $status")
        }
        sb.appendLine()

        sb.appendLine("=== WORKMANAGER ===")
        try {
            val wm = WorkManager.getInstance(this)
            val allWork = wm.getWorkInfosByTag("").get()
            // Try getting by unique work names
            for (name in listOf(
                "widget_update_periodic",
                "anki_content_trigger",
                "daily_reset",
                "reminder_soft",
                "reminder_medium",
                "reminder_strong",
                "reminder_critical"
            )) {
                try {
                    val infos = wm.getWorkInfosForUniqueWork(name).get()
                    for (info in infos) {
                        sb.appendLine("  $name: ${info.state} (id=${info.id})")
                    }
                } catch (_: Exception) {
                    sb.appendLine("  $name: not found")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR: ${e.message}")
        }
        sb.appendLine()

        sb.appendLine("=== NOTIFICATIONS ===")
        sb.appendLine("Enabled:  ${streak.notificationsEnabled}")
        sb.appendLine("Reminder: ${streak.reminderHour}:${"%02d".format(streak.reminderMinute)}")
        sb.appendLine("Critical: ${streak.criticalHour}:${"%02d".format(streak.criticalMinute)}")

        debugText.text = sb.toString()
    }
}
