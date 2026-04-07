package com.ankistreak.widget

import android.content.Intent
import android.widget.RemoteViewsService

class CalendarRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarRemoteViewsFactory(applicationContext, intent)
    }
}
