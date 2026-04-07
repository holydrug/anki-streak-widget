package com.ankistreak.widget

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val color: Int,
    val isAllDay: Boolean
)
