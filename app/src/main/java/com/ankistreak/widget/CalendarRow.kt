package com.ankistreak.widget

import java.time.LocalDate

sealed class CalendarRow {
    data class TodayHeader(val date: LocalDate) : CalendarRow()
    data class DayHeader(val date: LocalDate) : CalendarRow()
    data class Event(
        val event: CalendarEvent,
        val relativeTag: String?
    ) : CalendarRow()
    data class GhostDate(val date: LocalDate) : CalendarRow()
    data class Range(val from: LocalDate, val to: LocalDate) : CalendarRow()
    object NowIndicator : CalendarRow()
    data class EmptyToday(val nextEventText: String?) : CalendarRow()
}
