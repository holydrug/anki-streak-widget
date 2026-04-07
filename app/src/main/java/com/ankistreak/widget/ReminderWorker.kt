package com.ankistreak.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val streak = StreakManager(ctx)

        // Don't notify if already studied today
        if (streak.isTodayDone) return Result.success()

        // Don't notify if notifications are disabled
        if (!streak.notificationsEnabled) return Result.success()

        val urgencyStr = inputData.getString("urgency") ?: "SOFT"
        val urgency = try {
            NotificationHelper.Urgency.valueOf(urgencyStr)
        } catch (_: Exception) {
            NotificationHelper.Urgency.SOFT
        }

        // If streak is 0, only send soft reminders (less pressure when nothing to lose)
        if (streak.currentStreak == 0 && urgency != NotificationHelper.Urgency.SOFT) {
            return Result.success()
        }

        val helper = NotificationHelper(ctx)
        helper.createChannel()
        helper.sendReminder(streak.currentStreak, streak.cardGoal, urgency)

        // Also update widget (time-of-day color may have changed)
        StreakWidgetProvider.updateAllWidgets(ctx)

        return Result.success()
    }
}
