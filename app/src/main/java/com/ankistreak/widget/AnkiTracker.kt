package com.ankistreak.widget

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.time.LocalDate

/**
 * Tracks AnkiDroid reviews via Content Provider.
 *
 * AnkiDroid exposes deck info including due card counts through
 * content://com.ichi2.anki.flashcards/decks
 *
 * We capture total due count at start of day and compare with current
 * to calculate how many cards were reviewed today.
 */
class AnkiTracker(private val context: Context) {

    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        val DECK_URI: Uri = Uri.parse("content://$AUTHORITY/decks")
        private const val DECK_COUNTS = "deck_counts" // JSON: [new, learn, review]

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

    fun hasContentProviderAccess(): Boolean {
        return try {
            val cursor = context.contentResolver.query(DECK_URI, null, null, null, null)
            cursor?.close()
            cursor != null
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get total due cards across all decks (new + learn + review).
     */
    fun getTotalDueCards(): Int {
        try {
            val cursor = context.contentResolver.query(
                DECK_URI, arrayOf(DECK_COUNTS), null, null, null
            ) ?: return -1

            var totalDue = 0
            cursor.use {
                val countsIdx = it.getColumnIndex(DECK_COUNTS)
                if (countsIdx < 0) return -1

                while (it.moveToNext()) {
                    val countsJson = it.getString(countsIdx) ?: continue
                    try {
                        val arr = JSONArray(countsJson)
                        // [new, learn, review]
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

    /**
     * Call this at start of day (or first check of the day) to capture baseline.
     */
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

    /**
     * Get estimated number of cards reviewed today.
     * Calculated as: initial_due - current_due (clamped >= 0).
     * We also track a running maximum to handle edge cases where
     * current_due increases (new cards becoming due).
     */
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

        // Keep running max — due count can fluctuate as cards graduate from learning
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
