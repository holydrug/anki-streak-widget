package com.ankistreak.widget

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class StreakManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "streak_data"
        private const val KEY_CURRENT_STREAK = "current_streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_LAST_STUDY_DATE = "last_study_date"
        private const val KEY_TODAY_DONE = "today_done"
        private const val KEY_TODAY_DATE = "today_date"
        // Store week history as comma-separated dates: "2026-04-01,2026-04-02,..."
        private const val KEY_HISTORY = "study_history"
        private const val KEY_CARD_GOAL = "card_goal"
        private const val KEY_NOTIFICATIONS_ON = "notifications_on"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MIN = "reminder_min"
        private const val KEY_CRITICAL_HOUR = "critical_hour"
        private const val KEY_CRITICAL_MIN = "critical_min"

        val MILESTONES = setOf(7, 14, 30, 50, 100, 200, 365)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Streak Data ---

    var currentStreak: Int
        get() = prefs.getInt(KEY_CURRENT_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_CURRENT_STREAK, value).apply()

    var bestStreak: Int
        get() = prefs.getInt(KEY_BEST_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_STREAK, value).apply()

    var lastStudyDate: String
        get() = prefs.getString(KEY_LAST_STUDY_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_STUDY_DATE, value).apply()

    val isTodayDone: Boolean
        get() {
            val today = LocalDate.now().toString()
            return prefs.getString(KEY_TODAY_DATE, "") == today
                    && prefs.getBoolean(KEY_TODAY_DONE, false)
        }

    // --- Settings ---

    var cardGoal: Int
        get() = prefs.getInt(KEY_CARD_GOAL, 10)
        set(value) = prefs.edit().putInt(KEY_CARD_GOAL, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ON, value).apply()

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 12)
        set(value) = prefs.edit().putInt(KEY_REMINDER_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MIN, value).apply()

    var criticalHour: Int
        get() = prefs.getInt(KEY_CRITICAL_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_CRITICAL_HOUR, value).apply()

    var criticalMinute: Int
        get() = prefs.getInt(KEY_CRITICAL_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_CRITICAL_MIN, value).apply()

    // --- Core Logic ---

    /**
     * Called when we detect that the user reviewed [reviewedCount] cards today.
     * Returns true if this marks a NEW day completion (for milestone check).
     */
    fun reportReviews(reviewedCount: Int): Boolean {
        val today = LocalDate.now()
        val todayStr = today.toString()

        // Already marked as done today
        if (isTodayDone) return false

        // Not enough cards
        if (reviewedCount < cardGoal) return false

        // Mark today as done
        prefs.edit()
            .putBoolean(KEY_TODAY_DONE, true)
            .putString(KEY_TODAY_DATE, todayStr)
            .apply()

        // Check streak continuity
        val lastDate = lastStudyDate
        val yesterday = today.minusDays(1).toString()

        currentStreak = if (lastDate == yesterday || lastDate == todayStr) {
            // Continuing streak (yesterday was done, or already counted today)
            currentStreak + 1
        } else if (lastDate.isEmpty()) {
            // First ever study
            1
        } else {
            // Gap — start new streak
            1
        }

        lastStudyDate = todayStr

        if (currentStreak > bestStreak) {
            bestStreak = currentStreak
        }

        addToHistory(todayStr)
        return true
    }

    /**
     * Called at start of each day to check if the streak should be reset.
     */
    fun checkStreakContinuity() {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val todayDate = prefs.getString(KEY_TODAY_DATE, "")

        // Reset today_done flag if it's a new day
        if (todayDate != todayStr) {
            prefs.edit()
                .putBoolean(KEY_TODAY_DONE, false)
                .putString(KEY_TODAY_DATE, todayStr)
                .apply()
        }

        // Check if streak is broken (missed yesterday)
        val lastDate = lastStudyDate
        if (lastDate.isEmpty()) return

        val last = LocalDate.parse(lastDate)
        val daysSince = today.toEpochDay() - last.toEpochDay()

        if (daysSince > 1) {
            // Missed at least one day — reset streak
            currentStreak = 0
        }
    }

    fun isMilestone(): Boolean = currentStreak in MILESTONES

    // --- Week History ---

    private fun addToHistory(dateStr: String) {
        val history = getHistory().toMutableSet()
        history.add(dateStr)
        // Keep only last 30 days
        val cutoff = LocalDate.now().minusDays(30).toString()
        val filtered = history.filter { it >= cutoff }
        prefs.edit().putString(KEY_HISTORY, filtered.joinToString(",")).apply()
    }

    private fun getHistory(): Set<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").toSet()
    }

    /**
     * Returns study status for current week (Mon-Sun).
     * Result: Map of DayOfWeek to Boolean? (true=studied, false=missed, null=future/today-pending)
     */
    fun getWeekStatus(): Map<DayOfWeek, Boolean?> {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val history = getHistory()

        val result = mutableMapOf<DayOfWeek, Boolean?>()
        for (i in 0..6) {
            val day = monday.plusDays(i.toLong())
            val dow = day.dayOfWeek
            result[dow] = when {
                day.isAfter(today) -> null // future
                day.isEqual(today) -> if (isTodayDone) true else null // today
                history.contains(day.toString()) -> true // studied
                else -> false // missed
            }
        }
        return result
    }
}
