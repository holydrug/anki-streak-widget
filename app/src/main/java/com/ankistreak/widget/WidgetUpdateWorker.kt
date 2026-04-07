package com.ankistreak.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        StreakWidgetProvider.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
