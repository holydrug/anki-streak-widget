package com.ankistreak.widget

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.time.LocalDate

class AnkiTracker(private val context: Context) {

    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        val DECK_URI: Uri = Uri.parse("content://$AUTHORITY/decks")
        private const val DECK_COUNTS = "deck_counts"

        private const val PREFS_NAME = "anki_tracker"
        private const val KEY_LAST_DUE = "last_due_check"
        private const val KEY_LAST_CHECK_DATE = "last_check_date"
        private const val KEY_REVIEWED_TODAY = "reviewed_today"
        private const val KEY_REVIEW_DATE = "review_date"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAnkiDroidInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(ANKI_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun diagnosAccess(): String {
        return try {
            val cursor = context.contentResolver.query(DECK_URI, null, null, null, null)
            if (cursor != null) {
                val count = cursor.count
                val cols = cursor.columnNames?.joinToString(", ") ?: "none"
                cursor.close()
                "ok (decks: $count, columns: $cols)"
            } else {
                "cursor=null (API выключен в AnkiDroid)"
            }
        } catch (e: SecurityException) {
            "SecurityException: ${e.message}"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun hasContentProviderAccess(): Boolean = diagnosAccess().startsWith("ok")

    /**
     * Get total due cards across all decks (new + learn + review).
     */
    fun getTotalDueCards(): Int {
        try {
            val cursor = context.contentResolver.query(
                DECK_URI, null, null, null, null
            ) ?: return -1

            var totalDue = 0
            cursor.use {
                val countsIdx = it.getColumnIndex(DECK_COUNTS)
                if (countsIdx < 0) return -1

                while (it.moveToNext()) {
                    val countsJson = it.getString(countsIdx) ?: continue
                    try {
                        val arr = JSONArray(countsJson)
                        totalDue += arr.optInt(0, 0) + arr.optInt(1, 0) + arr.optInt(2, 0)
                    } catch (_: Exception) { }
                }
            }
            return totalDue
        } catch (e: Exception) {
            return -1
        }
    }

    /**
     * Incremental tracking: compare current due count with last check.
     * If due went DOWN since last check → user studied those cards.
     * If due went UP → new day / new cards added, don't subtract.
     *
     * This works regardless of when the app was installed.
     */
    fun getReviewedToday(): Int {
        val today = LocalDate.now().toString()
        val currentDue = getTotalDueCards()
        if (currentDue < 0) return getTodayCount()

        val lastCheckDate = prefs.getString(KEY_LAST_CHECK_DATE, "") ?: ""
        val lastDue = prefs.getInt(KEY_LAST_DUE, -1)

        // Reset counter if new day
        if (prefs.getString(KEY_REVIEW_DATE, "") != today) {
            prefs.edit()
                .putInt(KEY_REVIEWED_TODAY, 0)
                .putString(KEY_REVIEW_DATE, today)
                .apply()
        }

        // If we have a previous check from today, calculate delta
        if (lastDue >= 0 && lastCheckDate == today) {
            val delta = lastDue - currentDue
            if (delta > 0) {
                // Due went down → user reviewed cards
                val newTotal = getTodayCount() + delta
                prefs.edit().putInt(KEY_REVIEWED_TODAY, newTotal).apply()
            }
        }
        // If last check was yesterday/before → this is first check of the day.
        // Just save current due as baseline, don't count anything yet.

        // Save current state for next check
        prefs.edit()
            .putInt(KEY_LAST_DUE, currentDue)
            .putString(KEY_LAST_CHECK_DATE, today)
            .apply()

        return getTodayCount()
    }

    private fun getTodayCount(): Int {
        val today = LocalDate.now().toString()
        return if (prefs.getString(KEY_REVIEW_DATE, "") == today) {
            prefs.getInt(KEY_REVIEWED_TODAY, 0)
        } else 0
    }
}
