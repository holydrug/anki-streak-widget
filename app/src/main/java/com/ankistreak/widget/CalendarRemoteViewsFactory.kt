package com.ankistreak.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CalendarRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<CalendarRow> = emptyList()
    private val provider = CalendarDataProvider(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val today = LocalDate.now()
        val endDate = today.plusDays(14)
        val events = provider.getEvents(today, endDate)
        rows = provider.buildRows(events)
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rows.size) {
            return RemoteViews(context.packageName, R.layout.calendar_item_ghost)
        }

        return when (val row = rows[position]) {
            is CalendarRow.TodayHeader -> buildTodayHeader(row)
            is CalendarRow.DayHeader -> buildDayHeader(row)
            is CalendarRow.Event -> buildEvent(row)
            is CalendarRow.GhostDate -> buildGhost(row)
            is CalendarRow.Range -> buildRange(row)
            is CalendarRow.NowIndicator -> buildNow()
            is CalendarRow.EmptyToday -> buildEmptyToday(row)
        }
    }

    private fun buildTodayHeader(row: CalendarRow.TodayHeader): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_today_header)
        views.setTextViewText(R.id.cal_today_date, provider.formatDate(row.date))
        views.setTextViewText(R.id.cal_today_weekday, provider.getWeekdayShort(row.date))
        return views
    }

    private fun buildDayHeader(row: CalendarRow.DayHeader): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_day_header)
        views.setTextViewText(R.id.cal_day_date, provider.formatDate(row.date))
        views.setTextViewText(R.id.cal_day_weekday, provider.getWeekdayShort(row.date))
        return views
    }

    private fun buildEvent(row: CalendarRow.Event): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_event)
        val ev = row.event

        // Color bar
        views.setInt(R.id.cal_ev_bar, "setBackgroundColor", ev.color)

        // Time
        if (ev.isAllDay) {
            views.setTextViewText(R.id.cal_ev_time, "Весь")
            views.setTextViewText(R.id.cal_ev_time_end, "день")
        } else {
            views.setTextViewText(R.id.cal_ev_time, provider.formatTime(ev.startTime))
            views.setTextViewText(R.id.cal_ev_time_end, provider.formatTimeEnd(ev.endTime))
        }

        // Title
        views.setTextViewText(R.id.cal_ev_title, ev.title)

        // Location
        if (!ev.location.isNullOrBlank()) {
            views.setTextViewText(R.id.cal_ev_loc, ev.location)
            views.setViewVisibility(R.id.cal_ev_loc, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.cal_ev_loc, View.GONE)
        }

        // Relative tag
        if (row.relativeTag != null) {
            views.setTextViewText(R.id.cal_ev_tag, row.relativeTag)
            views.setViewVisibility(R.id.cal_ev_tag, View.VISIBLE)
            if (row.relativeTag == "сейчас") {
                views.setInt(R.id.cal_ev_tag, "setBackgroundResource", R.drawable.calendar_tag_now_bg)
                views.setTextColor(R.id.cal_ev_tag, context.getColor(R.color.cal_tag_now_text))
            } else {
                views.setInt(R.id.cal_ev_tag, "setBackgroundResource", R.drawable.calendar_tag_soon_bg)
                views.setTextColor(R.id.cal_ev_tag, context.getColor(R.color.cal_tag_soon_text))
            }
        } else {
            views.setViewVisibility(R.id.cal_ev_tag, View.GONE)
        }

        return views
    }

    private fun buildGhost(row: CalendarRow.GhostDate): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_ghost)
        val text = "${row.date.dayOfMonth} ${provider.getWeekdayShort(row.date)}"
        views.setTextViewText(R.id.cal_ghost_text, text)
        return views
    }

    private fun buildRange(row: CalendarRow.Range): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_range)
        val text = "${row.from.dayOfMonth}–${row.to.dayOfMonth} ${provider.getWeekdayShort(row.from)}–${provider.getWeekdayShort(row.to)}"
        views.setTextViewText(R.id.cal_range_text, text)
        return views
    }

    private fun buildNow(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_now)
        val now = LocalTime.now()
        views.setTextViewText(R.id.cal_now_time, "${now.hour}:${"%02d".format(now.minute)}")
        return views
    }

    private fun buildEmptyToday(row: CalendarRow.EmptyToday): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_item_empty_today)
        if (row.nextEventText != null) {
            views.setTextViewText(R.id.cal_empty_next, "→ ${row.nextEventText}")
            views.setViewVisibility(R.id.cal_empty_next, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.cal_empty_next, View.GONE)
        }
        return views
    }

    override fun getViewTypeCount(): Int = 7

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getLoadingView(): RemoteViews? = null

    override fun onDestroy() {
        rows = emptyList()
    }
}
