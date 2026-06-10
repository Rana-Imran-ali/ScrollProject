package com.example.scrollproject.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * UsageMonitorService — retained as an empty stub for build compatibility.
 *
 * This service is no longer registered in AndroidManifest.xml and is never
 * started. The countdown engine is now fully handled by [CountdownService]
 * and [TimerManager]. This file exists solely to prevent compile errors from
 * any lingering import references during the refactor transition.
 */
class UsageMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
