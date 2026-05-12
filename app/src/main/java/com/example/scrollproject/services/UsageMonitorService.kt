package com.example.scrollproject.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scrollproject.MainActivity
import com.example.scrollproject.R
import com.example.scrollproject.data.local.ScrollGuardDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.scrollproject.core.TimerManager

class UsageMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        const val CHANNEL_ID = "scroll_guard_monitoring"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.example.scrollproject.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        startUsagePolling()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startUsagePolling() {
        serviceScope.launch {
            while (isActive) {
                pollUsage()
                delay(10_000L) // poll every 10 seconds
            }
        }
    }

    private suspend fun pollUsage() {
        val db = ScrollGuardDatabase.getInstance(applicationContext)
        val monitoredApps = db.monitoredAppDao().getBlockedApps()
        if (monitoredApps.isEmpty()) return

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfDay, now
        )

        val today = dateFormat.format(Date())

        for (app in monitoredApps) {
            val stat = stats.firstOrNull { it.packageName == app.packageName } ?: continue
            val systemTotalSeconds = stat.totalTimeInForeground / 1000L

            if (systemTotalSeconds > 0) {
                // Update TimerManager first so UI reacts immediately
                TimerManager.updateLiveUsage(app.packageName, systemTotalSeconds)

                val currentDbUsage = db.appUsageDao().getUsage(app.packageName, today)?.timeSpentSeconds ?: 0L
                if (systemTotalSeconds > currentDbUsage) {
                    val delta = systemTotalSeconds - currentDbUsage
                    db.appUsageDao().addTime(app.packageName, today, delta, System.currentTimeMillis())
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scroll Guard Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage in background"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
