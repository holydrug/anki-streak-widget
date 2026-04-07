package com.ankistreak.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Triggered by WorkManager when AnkiDroid's content provider data changes.
 * Updates the widget and re-enqueues itself to keep listening.
 */
class ContentTriggerWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // Update widget with fresh data
        StreakWidgetProvider.updateAllWidgets(applicationContext)

        // Re-enqueue to keep the content URI trigger active
        WorkScheduler.scheduleContentTrigger(applicationContext)

        return Result.success()
    }
}
