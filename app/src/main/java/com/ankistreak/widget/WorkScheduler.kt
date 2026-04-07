package com.ankistreak.widget

import android.content.Context
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Central place for scheduling all periodic and one-time workers.
 */
object WorkScheduler {

    private const val WIDGET_UPDATE_WORK = "widget_update_periodic"
    private const val DAILY_RESET_WORK = "daily_reset"
    private const val REMINDER_SOFT_WORK = "reminder_soft"
    private const val REMINDER_MEDIUM_WORK = "reminder_medium"
    private const val REMINDER_STRONG_WORK = "reminder_strong"
    private const val REMINDER_CRITICAL_WORK = "reminder_critical"

    fun scheduleAll(context: Context) {
        scheduleWidgetUpdates(context)
        scheduleDailyReset(context)
        scheduleReminders(context)
    }

    /**
     * Update widget every 30 minutes to reflect AnkiDroid changes and time-of-day color.
     */
    private fun scheduleWidgetUpdates(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Reset streak state at midnight.
     */
    private fun scheduleDailyReset(context: Context) {
        val delay = getDelayUntil(0, 5) // 00:05 — slight offset to ensure date rolled over
        val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_RESET_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Schedule reminder notifications at configured times.
     */
    fun scheduleReminders(context: Context) {
        val streak = StreakManager(context)
        if (!streak.notificationsEnabled) {
            cancelReminders(context)
            return
        }

        val reminderH = streak.reminderHour
        val reminderM = streak.reminderMinute
        val criticalH = streak.criticalHour
        val criticalM = streak.criticalMinute

        // Calculate intermediate times
        val midH = (reminderH + criticalH) / 2
        val strongH = (midH + criticalH) / 2

        scheduleOneReminder(context, REMINDER_SOFT_WORK, reminderH, reminderM, "SOFT")
        scheduleOneReminder(context, REMINDER_MEDIUM_WORK, midH, 0, "MEDIUM")
        scheduleOneReminder(context, REMINDER_STRONG_WORK, strongH, 0, "STRONG")
        scheduleOneReminder(context, REMINDER_CRITICAL_WORK, criticalH, criticalM, "CRITICAL")
    }

    private fun scheduleOneReminder(
        context: Context, tag: String, hour: Int, minute: Int, urgency: String
    ) {
        val delay = getDelayUntil(hour, minute)
        if (delay <= 0) return // Time already passed today, skip

        val data = Data.Builder().putString("urgency", urgency).build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelReminders(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(REMINDER_SOFT_WORK)
        wm.cancelUniqueWork(REMINDER_MEDIUM_WORK)
        wm.cancelUniqueWork(REMINDER_STRONG_WORK)
        wm.cancelUniqueWork(REMINDER_CRITICAL_WORK)
    }

    /**
     * Returns milliseconds from now until the next occurrence of [hour]:[minute].
     */
    private fun getDelayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
