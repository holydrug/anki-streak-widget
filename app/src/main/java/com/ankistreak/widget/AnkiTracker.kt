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
        private const val KEY_INITIAL_DUE = "initial_due"
        private const val KEY_INITIAL_DATE = "initial_date"
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

    /**
     * Try to access AnkiDroid Content Provider and return diagnostic info.
     * Returns: "ok" on success, or error description on failure.
     */
    fun diagnosAccess(): String {
        return try {
            val cursor = context.contentResolver.query(DECK_URI, null, null, null, null)
            if (cursor != null) {
                val count = cursor.count
                val cols = cursor.columnNames?.joinToString(", ") ?: "none"
                cursor.close()
                "ok (decks: $count, columns: $cols)"
            } else {
                "cursor=null (API выключен в AnkiDroid или нет колод)"
            }
        } catch (e: SecurityException) {
            "SecurityException: ${e.message}"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun hasContentProviderAccess(): Boolean {
        return diagnosAccess().startsWith("ok")
    }

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
                        val newCount = arr.optInt(0, 0)
                        val learnCount = arr.optInt(1, 0)
                        val reviewCount = arr.optInt(2, 0)
                        totalDue += newCount + learnCount + reviewCount
                    } catch (_: Exception) { }
                }
            }
            return totalDue
        } catch (e: SecurityException) {
            return -1
        } catch (e: Exception) {
            return -1
        }
    }

    fun captureStartOfDay() {
        val today = LocalDate.now().toString()
        val storedDate = prefs.getString(KEY_INITIAL_DATE, "")

        if (storedDate != today) {
            val due = getTotalDueCards()
            if (due >= 0) {
                prefs.edit()
                    .putInt(KEY_INITIAL_DUE, due)
                    .putString(KEY_INITIAL_DATE, today)
                    .putInt(KEY_REVIEWED_TODAY, 0)
                    .putString(KEY_REVIEW_DATE, today)
                    .apply()
            }
        }
    }

    fun getReviewedToday(): Int {
        val today = LocalDate.now().toString()
        val storedDate = prefs.getString(KEY_INITIAL_DATE, "")

        if (storedDate != today) {
            captureStartOfDay()
        }

        val initialDue = prefs.getInt(KEY_INITIAL_DUE, -1)
        if (initialDue < 0) return 0

        val currentDue = getTotalDueCards()
        if (currentDue < 0) return prefs.getInt(KEY_REVIEWED_TODAY, 0)

        val calculated = maxOf(0, initialDue - currentDue)

        val previousMax = if (prefs.getString(KEY_REVIEW_DATE, "") == today) {
            prefs.getInt(KEY_REVIEWED_TODAY, 0)
        } else 0

        val reviewed = maxOf(calculated, previousMax)

        prefs.edit()
            .putInt(KEY_REVIEWED_TODAY, reviewed)
            .putString(KEY_REVIEW_DATE, today)
            .apply()

        return reviewed
    }
}
