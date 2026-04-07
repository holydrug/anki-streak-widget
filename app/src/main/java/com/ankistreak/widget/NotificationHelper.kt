package com.ankistreak.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "anki_streak_reminders"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    enum class Urgency { SOFT, MEDIUM, STRONG, CRITICAL }

    fun sendReminder(streak: Int, cardGoal: Int, urgency: Urgency) {
        val (title, text) = when (urgency) {
            Urgency.SOFT -> Pair(
                context.getString(R.string.notif_soft_title),
                context.getString(R.string.notif_soft_text_format, streak, cardGoal)
            )
            Urgency.MEDIUM -> {
                val hoursLeft = getHoursUntilMidnight()
                Pair(
                    context.getString(R.string.notif_medium_title),
                    context.getString(R.string.notif_medium_text_format, hoursLeft, streak)
                )
            }
            Urgency.STRONG -> {
                val hoursLeft = getHoursUntilMidnight()
                Pair(
                    context.getString(R.string.notif_strong_title),
                    context.getString(R.string.notif_strong_text_format, hoursLeft, streak)
                )
            }
            Urgency.CRITICAL -> {
                val hoursLeft = getHoursUntilMidnight()
                Pair(
                    context.getString(R.string.notif_critical_title),
                    context.getString(R.string.notif_critical_text_format, streak, hoursLeft)
                )
            }
        }

        // Intent to open AnkiDroid
        val ankiIntent = context.packageManager
            .getLaunchIntentForPackage(AnkiTracker.ANKI_PACKAGE)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, ankiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (urgency) {
            Urgency.SOFT -> NotificationCompat.PRIORITY_DEFAULT
            Urgency.MEDIUM -> NotificationCompat.PRIORITY_HIGH
            Urgency.STRONG -> NotificationCompat.PRIORITY_HIGH
            Urgency.CRITICAL -> NotificationCompat.PRIORITY_MAX
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.notif_action_open),
                pendingIntent
            )
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_BASE + urgency.ordinal, notification)
    }

    private fun getHoursUntilMidnight(): Int {
        val now = java.time.LocalTime.now()
        return 24 - now.hour
    }
}
