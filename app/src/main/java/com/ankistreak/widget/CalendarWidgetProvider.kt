package com.ankistreak.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

class CalendarWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TAP = "com.ankistreak.widget.CALENDAR_TAP"

        private val WEEKDAY_SHORT = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        fun updateAllWidgets(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, awm, id)
            }
        }

        private fun updateWidget(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.calendar_widget_layout)
            val provider = CalendarDataProvider(context)
            val today = LocalDate.now()

            // --- Header ---
            views.setTextViewText(R.id.cal_date_num, today.dayOfMonth.toString())
            views.setTextViewText(R.id.cal_weekday, provider.getWeekdayFull(today))
            views.setTextViewText(R.id.cal_month, provider.getMonthYear(today))

            if (!provider.hasPermission()) {
                // No permission — show message
                views.setViewVisibility(R.id.cal_week_strip, View.GONE)
                views.setViewVisibility(R.id.cal_empty_area, View.VISIBLE)
                views.setViewVisibility(android.R.id.list, View.GONE)
                views.setTextViewText(R.id.cal_empty_msg, context.getString(R.string.cal_no_permission))
                views.setTextViewText(R.id.cal_empty_sub, "Нажмите чтобы настроить")
                views.setViewVisibility(R.id.cal_next_ev_card, View.GONE)
                views.setViewVisibility(R.id.cal_count, View.GONE)

                // Click opens settings
                val settingsIntent = Intent(context, SettingsActivity::class.java)
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    context, widgetId, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.cal_root, pi)
                awm.updateAppWidget(widgetId, views)
                return
            }

            // --- Fetch events ---
            val endDate = today.plusDays(14)
            val events = provider.getEvents(today, endDate)
            val weekEventCount = provider.countWeekEvents(events)
            val totalEvents = events.values.sumOf { it.size }

            // --- Count badge ---
            if (totalEvents > 0) {
                views.setTextViewText(R.id.cal_count, provider.declEvents(totalEvents))
                views.setViewVisibility(R.id.cal_count, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.cal_count, View.GONE)
            }

            // --- Decide layout mode ---
            if (weekEventCount == 0) {
                // Empty week mode
                views.setViewVisibility(android.R.id.list, View.GONE)
                views.setViewVisibility(R.id.cal_week_strip, View.VISIBLE)
                views.setViewVisibility(R.id.cal_empty_area, View.VISIBLE)

                // Populate week strip
                populateWeekStrip(views, today, events)

                // Empty message
                if (totalEvents == 0) {
                    views.setTextViewText(R.id.cal_empty_msg, context.getString(R.string.cal_empty_calendar))
                    views.setTextViewText(R.id.cal_empty_sub, "")
                    views.setViewVisibility(R.id.cal_next_ev_card, View.GONE)
                } else {
                    views.setTextViewText(R.id.cal_empty_msg, context.getString(R.string.cal_free_week))
                    views.setTextViewText(R.id.cal_empty_sub, context.getString(R.string.cal_no_events))

                    // Find and show next event
                    val nextEntry = events.entries.firstOrNull { it.key > today.plusDays((7 - today.dayOfWeek.value).toLong()) }
                    if (nextEntry != null && nextEntry.value.isNotEmpty()) {
                        val ev = nextEntry.value.first()
                        val date = nextEntry.key
                        views.setViewVisibility(R.id.cal_next_ev_card, View.VISIBLE)
                        views.setInt(R.id.cal_next_ev_bar, "setBackgroundColor", ev.color)
                        views.setTextViewText(R.id.cal_next_ev_title, ev.title)
                        val weekday = WEEKDAY_SHORT.getOrElse(date.dayOfWeek.value - 1) { "" }
                        val dateStr = provider.formatDate(date)
                        val time = provider.formatTime(ev.startTime)
                        views.setTextViewText(R.id.cal_next_ev_when, "$weekday, $dateStr · $time")
                    } else {
                        views.setViewVisibility(R.id.cal_next_ev_card, View.GONE)
                    }
                }
            } else {
                // Normal agenda mode
                views.setViewVisibility(R.id.cal_week_strip, View.GONE)
                views.setViewVisibility(R.id.cal_empty_area, View.GONE)
                views.setViewVisibility(android.R.id.list, View.VISIBLE)

                // Connect ListView to RemoteViewsService
                val serviceIntent = Intent(context, CalendarRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(android.R.id.list, serviceIntent)
            }

            // --- Click handler: open Calendar app ---
            val tapIntent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_TAP
            }
            val tapPi = PendingIntent.getBroadcast(
                context, widgetId + 1000, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cal_root, tapPi)

            awm.updateAppWidget(widgetId, views)

            // Force factory refresh
            awm.notifyAppWidgetViewDataChanged(widgetId, android.R.id.list)
        }

        private fun populateWeekStrip(
            views: RemoteViews,
            today: LocalDate,
            events: Map<LocalDate, List<CalendarEvent>>
        ) {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val wsIds = intArrayOf(
                R.id.cal_ws_0, R.id.cal_ws_1, R.id.cal_ws_2,
                R.id.cal_ws_3, R.id.cal_ws_4, R.id.cal_ws_5, R.id.cal_ws_6
            )

            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                val wsId = wsIds[i]
                val hasEvents = events[date]?.isNotEmpty() == true
                val isToday = date == today

                // We can't add children dynamically to RemoteViews LinearLayout,
                // so we set the week strip text via the parent provider
                // Week strip days are styled via CalWeekStripDay style
                // But since we can't dynamically add TextViews to existing LinearLayout children,
                // we'll use a simple approach: make the week strip a flat display
                // handled entirely in the provider with setTextViewText

                // Note: Since RemoteViews can't add children, the week strip
                // needs pre-defined child TextViews. Let's set visibility to handle this.
                views.removeAllViews(wsId)

                val dayView = RemoteViews(context.packageName, R.layout.calendar_ws_day)
                dayView.setTextViewText(R.id.cal_ws_day_name, WEEKDAY_SHORT[i])
                dayView.setTextViewText(R.id.cal_ws_day_num, date.dayOfMonth.toString())

                if (isToday) {
                    dayView.setTextColor(R.id.cal_ws_day_num, 0xFFFF453A.toInt())
                } else if (hasEvents) {
                    dayView.setTextColor(R.id.cal_ws_day_num, 0x80FFFFFF.toInt())
                } else {
                    dayView.setTextColor(R.id.cal_ws_day_num, 0x1AFFFFFF.toInt())
                }

                dayView.setTextColor(R.id.cal_ws_day_name, 0x4DFFFFFF.toInt())

                if (hasEvents) {
                    dayView.setViewVisibility(R.id.cal_ws_dot, View.VISIBLE)
                } else {
                    dayView.setViewVisibility(R.id.cal_ws_dot, View.INVISIBLE)
                }

                views.addView(wsId, dayView)
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TAP) {
            updateAllWidgets(context)

            // Open Google Calendar
            val calIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(calIntent)
            } catch (_: Exception) {
                // Fallback: open settings
                val settingsIntent = Intent(context, SettingsActivity::class.java)
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
            }
        }
    }
}
