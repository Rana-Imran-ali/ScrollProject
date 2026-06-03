package com.example.scrollproject.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scrollproject.MainActivity
import com.example.scrollproject.R
import com.example.scrollproject.core.TimerManager
import com.example.scrollproject.data.repository.ScrollGuardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject

/**
 * UsageMonitorService — foreground service that reconciles live usage with
 * the Android Usage Stats API every 10 seconds.
 *
 * Critical fixes:
 *  ✅ Polls ALL monitored apps (not just isBlockingEnabled=1), so usage is
 *     always tracked even when the user temporarily disables blocking.
 *  ✅ Uses TimerManager.updateLiveUsage() as the single source of truth,
 *     then persists any delta to Room every 30 s (handled by TimerManager).
 *  ✅ START_STICKY ensures the OS restarts the service if it is killed.
 */
@AndroidEntryPoint
class UsageMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var repository: ScrollGuardRepository

    companion object {
        const val CHANNEL_ID  = "scroll_guard_monitoring"
        const val NOTIF_ID    = 1001
        const val ACTION_STOP = "com.example.scrollproject.STOP_SERVICE"
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        // Make sure TimerManager is initialised (may already be by the
        // AccessibilityService, but idempotent).
        TimerManager.initialize(repository)
        startUsagePolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { TimerManager.flushToDB() }
        serviceScope.cancel()
    }

    // ─── Foreground notification ───────────────────────────────────────────────

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Scroll Guard Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage in background"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    // ─── Usage polling ────────────────────────────────────────────────────────

    private fun startUsagePolling() {
        serviceScope.launch {
            while (isActive) {
                reconcileUsageStats()
                delay(10_000L)   // every 10 seconds
            }
        }
    }

    /**
     * Pull ground-truth usage from UsageStatsManager and feed it into
     * TimerManager. We track ALL monitored apps regardless of blocking state.
     */
    private suspend fun reconcileUsageStats() {
        // getAllMonitoredAppPackages returns every row in monitored_apps
        val monitoredPackages = repository.getAllMonitoredPackages()
        if (monitoredPackages.isEmpty()) return

        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfDay, now
        ) ?: return

        val statsMap = stats.associateBy { it.packageName }

        for (pkg in monitoredPackages) {
            val systemSeconds = statsMap[pkg]?.totalTimeInForeground?.div(1000L) ?: 0L
            // updateLiveUsage only applies if systemSeconds > current live value,
            // so it won't accidentally reduce counts mid-session.
            TimerManager.updateLiveUsage(pkg, systemSeconds)
        }
    }
}
