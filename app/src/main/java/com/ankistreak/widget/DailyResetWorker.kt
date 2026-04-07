package com.ankistreak.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs shortly after midnight to:
 * 1. Check streak continuity (reset if yesterday was missed)
 * 2. Capture new day's initial due count from AnkiDroid
 * 3. Reschedule reminders for the new day
 * 4. Reschedule self for tomorrow
 * 5. Update widget
 */
class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val streak = StreakManager(ctx)
        val tracker = AnkiTracker(ctx)

        // Check if yesterday was studied, reset streak if not
        streak.checkStreakContinuity()

        // Capture today's initial due count
        tracker.captureStartOfDay()

        // Reschedule reminders for today
        WorkScheduler.scheduleReminders(ctx)

        // Reschedule daily reset for tomorrow
        WorkScheduler.scheduleAll(ctx)

        // Update widget
        StreakWidgetProvider.updateAllWidgets(ctx)

        return Result.success()
    }
}
