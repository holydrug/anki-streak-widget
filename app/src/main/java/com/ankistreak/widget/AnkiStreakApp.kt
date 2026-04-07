package com.ankistreak.widget

import android.app.Application

class AnkiStreakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannel()
        WorkScheduler.scheduleAll(this)
    }
}
