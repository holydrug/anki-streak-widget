package com.ankistreak.widget

import android.content.Context
import android.os.Build
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WIDGET_UPDATE_WORK = "widget_update_periodic"
    private const val CONTENT_TRIGGER_WORK = "anki_content_trigger"
    private const val DAILY_RESET_WORK = "daily_reset"
    private const val REMINDER_SOFT_WORK = "reminder_soft"
    private const val REMINDER_MEDIUM_WORK = "reminder_medium"
    private const val REMINDER_STRONG_WORK = "reminder_strong"
    private const val REMINDER_CRITICAL_WORK = "reminder_critical"
    private const val CALENDAR_UPDATE_WORK = "calendar_widget_update"

    fun scheduleAll(context: Context) {
        scheduleWidgetUpdates(context)
        scheduleContentTrigger(context)
        scheduleDailyReset(context)
        scheduleReminders(context)
        scheduleCalendarWidgetUpdates(context)
    }

    /**
     * Periodic fallback: update every 15 min (WorkManager minimum).
     * Handles time-of-day color changes and cases where content trigger misses.
     */
    private fun scheduleWidgetUpdates(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Reactive trigger: fires when AnkiDroid's content provider data changes.
     * This gives near-instant widget updates after each card review.
     * Worker re-enqueues itself to keep the trigger active.
     */
    fun scheduleContentTrigger(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(AnkiTracker.DECK_URI, true)
                .setTriggerContentUpdateDelay(3, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(10, TimeUnit.SECONDS)
                .build()

            val request = OneTimeWorkRequestBuilder<ContentTriggerWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                CONTENT_TRIGGER_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun scheduleDailyReset(context: Context) {
        val delay = getDelayUntil(0, 5)
        val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_RESET_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

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
        if (delay <= 0) return

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

    private fun scheduleCalendarWidgetUpdates(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarWidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CALENDAR_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun getDelayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
