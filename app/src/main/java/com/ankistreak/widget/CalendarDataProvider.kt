package com.ankistreak.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TreeMap

class CalendarDataProvider(private val context: Context) {

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.ALL_DAY
        )

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
        private val WEEKDAY_SHORT = mapOf(
            java.time.DayOfWeek.MONDAY to "Пн",
            java.time.DayOfWeek.TUESDAY to "Вт",
            java.time.DayOfWeek.WEDNESDAY to "Ср",
            java.time.DayOfWeek.THURSDAY to "Чт",
            java.time.DayOfWeek.FRIDAY to "Пт",
            java.time.DayOfWeek.SATURDAY to "Сб",
            java.time.DayOfWeek.SUNDAY to "Вс"
        )
        private val WEEKDAY_FULL = mapOf(
            java.time.DayOfWeek.MONDAY to "Понедельник",
            java.time.DayOfWeek.TUESDAY to "Вторник",
            java.time.DayOfWeek.WEDNESDAY to "Среда",
            java.time.DayOfWeek.THURSDAY to "Четверг",
            java.time.DayOfWeek.FRIDAY to "Пятница",
            java.time.DayOfWeek.SATURDAY to "Суббота",
            java.time.DayOfWeek.SUNDAY to "Воскресенье"
        )
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    fun getEvents(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, List<CalendarEvent>> {
        if (!hasPermission()) return emptyMap()

        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val events = mutableListOf<CalendarEvent>()

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                PROJECTION, selection, selectionArgs, sortOrder
            ) ?: return emptyMap()

            cursor.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val colorIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val allDayIdx = it.getColumnIndex(CalendarContract.Events.ALL_DAY)

                while (it.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = if (idIdx >= 0) it.getLong(idIdx) else 0,
                            title = if (titleIdx >= 0) (it.getString(titleIdx) ?: "") else "",
                            startTime = if (startIdx >= 0) it.getLong(startIdx) else 0,
                            endTime = if (endIdx >= 0) it.getLong(endIdx) else 0,
                            location = if (locIdx >= 0) it.getString(locIdx) else null,
                            color = if (colorIdx >= 0) it.getInt(colorIdx) else 0xFF0A84FF.toInt(),
                            isAllDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }

        val zone2 = ZoneId.systemDefault()
        val grouped = TreeMap<LocalDate, MutableList<CalendarEvent>>()
        for (ev in events) {
            val date = Instant.ofEpochMilli(ev.startTime).atZone(zone2).toLocalDate()
            grouped.getOrPut(date) { mutableListOf() }.add(ev)
        }
        return grouped
    }

    fun buildRows(events: Map<LocalDate, List<CalendarEvent>>, lookAheadDays: Int = 14): List<CalendarRow> {
        val rows = mutableListOf<CalendarRow>()
        val today = LocalDate.now()
        val endDate = today.plusDays(lookAheadDays.toLong())
        val now = System.currentTimeMillis()

        var currentDate = today
        val emptyStreak = mutableListOf<LocalDate>()
        var isFirstDay = true

        while (!currentDate.isAfter(endDate)) {
            val dayEvents = events[currentDate] ?: emptyList()

            if (dayEvents.isEmpty()) {
                if (isFirstDay) {
                    // Today is empty — add special empty-today header
                    rows.add(CalendarRow.TodayHeader(today))
                    rows.add(CalendarRow.NowIndicator)
                    val nextEvent = findNextEvent(events, today)
                    val nextText = if (nextEvent != null) {
                        formatNextEventText(nextEvent.first, nextEvent.second)
                    } else null
                    rows.add(CalendarRow.EmptyToday(nextText))
                    isFirstDay = false
                } else {
                    emptyStreak.add(currentDate)
                }
            } else {
                // Flush empty streak before this day
                flushEmptyDays(emptyStreak, rows)
                emptyStreak.clear()

                // Day header
                if (currentDate == today) {
                    rows.add(CalendarRow.TodayHeader(today))
                    // Insert events: past first, then NOW, then upcoming
                    val (past, upcoming) = dayEvents.partition { it.endTime < now }
                    for (ev in past) {
                        rows.add(CalendarRow.Event(ev, null))
                    }
                    rows.add(CalendarRow.NowIndicator)
                    for (ev in upcoming) {
                        rows.add(CalendarRow.Event(ev, getRelativeTag(ev, now)))
                    }
                } else {
                    rows.add(CalendarRow.DayHeader(currentDate))
                    for (ev in dayEvents) {
                        rows.add(CalendarRow.Event(ev, null))
                    }
                }
                isFirstDay = false
            }

            currentDate = currentDate.plusDays(1)
        }

        flushEmptyDays(emptyStreak, rows)
        return rows
    }

    private fun flushEmptyDays(days: MutableList<LocalDate>, rows: MutableList<CalendarRow>) {
        if (days.isEmpty()) return
        if (days.size == 1) {
            rows.add(CalendarRow.GhostDate(days[0]))
        } else {
            rows.add(CalendarRow.Range(days.first(), days.last()))
        }
        days.clear()
    }

    private fun getRelativeTag(event: CalendarEvent, now: Long): String? {
        val diff = event.startTime - now
        if (diff < 0) return null
        val minutes = diff / 60_000
        return when {
            minutes < 5 -> "сейчас"
            minutes < 60 -> "через ${minutes} мин"
            minutes < 120 -> "через 1 ч"
            minutes < 180 -> "через 2 ч"
            else -> null
        }
    }

    private fun findNextEvent(
        events: Map<LocalDate, List<CalendarEvent>>,
        after: LocalDate
    ): Pair<CalendarEvent, LocalDate>? {
        for (date in events.keys.sorted()) {
            if (date > after) {
                val ev = events[date]?.firstOrNull() ?: continue
                return ev to date
            }
        }
        return null
    }

    private fun formatNextEventText(event: CalendarEvent, date: LocalDate): String {
        val weekday = WEEKDAY_SHORT[date.dayOfWeek] ?: ""
        val dateStr = date.format(DATE_FORMAT)
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(event.startTime).atZone(zone).toLocalTime()
        return "$weekday, $dateStr · ${time.format(TIME_FORMAT)} — ${event.title}"
    }

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

    fun getWeekdayFull(date: LocalDate): String =
        WEEKDAY_FULL[date.dayOfWeek] ?: ""

    fun getWeekdayShort(date: LocalDate): String =
        WEEKDAY_SHORT[date.dayOfWeek] ?: ""

    fun formatTime(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.format(TIME_FORMAT)
    }

    fun formatTimeEnd(millis: Long): String = formatTime(millis)

    fun getMonthYear(date: LocalDate): String {
        val month = date.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            .replaceFirstChar { it.uppercase() }
        return "$month ${date.year}"
    }

    fun countWeekEvents(events: Map<LocalDate, List<CalendarEvent>>): Int {
        val today = LocalDate.now()
        val sunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
        return events.filterKeys { it in today..sunday }.values.sumOf { it.size }
    }

    fun declEvents(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod100 in 11..19 -> "$n событий"
            mod10 == 1 -> "$n событие"
            mod10 in 2..4 -> "$n события"
            else -> "$n событий"
        }
    }
}
