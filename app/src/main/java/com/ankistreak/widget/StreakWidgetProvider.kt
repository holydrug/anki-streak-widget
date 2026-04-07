package com.ankistreak.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class StreakWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TAP = "com.ankistreak.widget.TAP"

        fun updateAllWidgets(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(
                ComponentName(context, StreakWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, awm, id)
            }
        }

        /**
         * Schedule quick refresh bursts: 1, 2, 5, 10 min from now.
         * So when user returns from AnkiDroid, widget updates within ~1 min.
         */
        fun scheduleQuickUpdates(context: Context) {
            val wm = WorkManager.getInstance(context)
            for (delayMin in listOf(1L, 2L, 5L, 10L)) {
                wm.enqueue(
                    OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                        .setInitialDelay(delayMin, TimeUnit.MINUTES)
                        .build()
                )
            }
        }

        private fun updateWidget(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int
        ) {
            val streak = StreakManager(context)
            val tracker = AnkiTracker(context)

            streak.checkStreakContinuity()

            val due = tracker.getTotalDueCards()
            val reviewed = tracker.getReviewedToday()
            streak.reportReviews(reviewed)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val state = resolveState(streak, reviewed)

            // -- Background --
            val bgRes = when (state) {
                WidgetState.DONE -> R.drawable.widget_bg_done
                WidgetState.MILESTONE -> R.drawable.widget_bg_milestone
                WidgetState.PENDING_MORNING -> R.drawable.widget_bg_pending
                WidgetState.PENDING_EVENING -> R.drawable.widget_bg_danger
                WidgetState.PENDING_CRITICAL -> R.drawable.widget_bg_critical
                WidgetState.LOST -> R.drawable.widget_bg_lost
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            // -- Icon --
            val icon = when (state) {
                WidgetState.LOST -> "\uD83D\uDC80"
                WidgetState.MILESTONE -> "\uD83C\uDFC6"
                else -> "\uD83D\uDD25"
            }
            views.setTextViewText(R.id.widget_icon, icon)

            // -- Streak number --
            views.setTextViewText(R.id.widget_streak_number, streak.currentStreak.toString())

            // -- Label --
            val label = when (state) {
                WidgetState.LOST -> context.getString(R.string.streak_lost_label)
                WidgetState.MILESTONE -> context.getString(
                    R.string.milestone_format, streak.currentStreak
                )
                else -> context.getString(R.string.days_label)
            }
            views.setTextViewText(R.id.widget_streak_label, label)

            // -- Badge --
            val goal = streak.cardGoal
            val badge = when (state) {
                WidgetState.DONE -> context.getString(R.string.status_done)
                WidgetState.MILESTONE -> "\u2B50 ${context.getString(R.string.status_done)}"
                WidgetState.LOST -> context.getString(R.string.status_start_over)
                WidgetState.PENDING_CRITICAL -> {
                    val h = getHoursUntilMidnight()
                    context.getString(R.string.status_critical_format, "${h}ч")
                }
                else -> context.getString(R.string.status_today_format, reviewed, goal)
            }
            views.setTextViewText(R.id.widget_badge, badge)

            // -- Progress bar --
            val progress = if (goal > 0) (reviewed * 100 / goal).coerceIn(0, 100) else 0
            views.setProgressBar(R.id.widget_progress, 100, progress, false)

            // -- Progress text --
            views.setTextViewText(
                R.id.widget_progress_text,
                context.getString(R.string.progress_format, reviewed, goal)
            )

            // -- Bottom message --
            val now = java.time.LocalTime.now()
            val message = "d=$due r=$reviewed ${now.hour}:${"%02d".format(now.minute)}"
            views.setTextViewText(R.id.widget_message, message)

            // -- Week dots --
            updateWeekDots(context, views, streak)

            // -- Click: broadcast to us first, then open AnkiDroid --
            val tapIntent = Intent(context, StreakWidgetProvider::class.java).apply {
                action = ACTION_TAP
            }
            val tapPi = PendingIntent.getBroadcast(
                context, widgetId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, tapPi)

            awm.updateAppWidget(widgetId, views)
        }

        private fun resolveState(streak: StreakManager, reviewed: Int): WidgetState {
            if (streak.isTodayDone) {
                return if (streak.isMilestone()) WidgetState.MILESTONE else WidgetState.DONE
            }

            val hasProgress = reviewed > 0
            val hasActiveStreak = streak.currentStreak > 0

            if (!hasActiveStreak && !hasProgress && streak.lastStudyDate.isNotEmpty()) {
                return WidgetState.LOST
            }

            val hour = LocalTime.now().hour
            return when {
                hour >= 22 -> WidgetState.PENDING_CRITICAL
                hour >= 18 -> WidgetState.PENDING_EVENING
                else -> WidgetState.PENDING_MORNING
            }
        }

        private fun getHoursUntilMidnight(): Int {
            return 24 - LocalTime.now().hour
        }

        private fun updateWeekDots(
            context: Context,
            views: RemoteViews,
            streak: StreakManager
        ) {
            val weekStatus = streak.getWeekStatus()
            val dotIds = mapOf(
                DayOfWeek.MONDAY to R.id.dot_mon,
                DayOfWeek.TUESDAY to R.id.dot_tue,
                DayOfWeek.WEDNESDAY to R.id.dot_wed,
                DayOfWeek.THURSDAY to R.id.dot_thu,
                DayOfWeek.FRIDAY to R.id.dot_fri,
                DayOfWeek.SATURDAY to R.id.dot_sat,
                DayOfWeek.SUNDAY to R.id.dot_sun,
            )

            for ((day, viewId) in dotIds) {
                val status = weekStatus[day]
                when (status) {
                    true -> {
                        views.setInt(viewId, "setBackgroundResource", R.drawable.dot_done)
                        views.setTextViewText(viewId, "\u2713")
                        views.setTextColor(viewId, context.getColor(R.color.dot_done_green))
                    }
                    false -> {
                        views.setInt(viewId, "setBackgroundResource", R.drawable.dot_missed)
                        views.setTextViewText(viewId, "\u2717")
                        views.setTextColor(viewId, context.getColor(R.color.dot_missed))
                    }
                    null -> {
                        views.setInt(viewId, "setBackgroundResource", R.drawable.dot_today)
                        views.setTextViewText(viewId, "?")
                        views.setTextColor(viewId, context.getColor(R.color.white))
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onEnabled(context: Context) {
        WorkScheduler.scheduleAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TAP) {
            // 1. Immediate update right now
            updateAllWidgets(context)

            // 2. Schedule quick updates for when user returns from AnkiDroid
            scheduleQuickUpdates(context)

            // 3. Open AnkiDroid
            val ankiIntent = context.packageManager
                .getLaunchIntentForPackage(AnkiTracker.ANKI_PACKAGE)
                ?: Intent(context, SettingsActivity::class.java)
            ankiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(ankiIntent)
        }
    }

    enum class WidgetState {
        DONE, MILESTONE, PENDING_MORNING, PENDING_EVENING, PENDING_CRITICAL, LOST
    }
}
