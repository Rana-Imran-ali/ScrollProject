package com.example.scrollproject.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scrollproject.MainActivity
import com.example.scrollproject.R
import com.example.scrollproject.core.ForegroundAppDetector
import com.example.scrollproject.core.TimerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

/**
 * CountdownService — foreground service with two jobs:
 *
 * 1. Keeps the app process alive in the background (so the AccessibilityService stays connected).
 * 2. Acts as a fallback enforcer: if the AccessibilityService is OFF, this service polls
 *    every 2 seconds to check which app is open and enforces the block via a Home intent.
 *
 * The ongoing notification shows a live countdown ticker while monitoring is active.
 */
class CountdownService : Service() {

    companion object {
        const val CHANNEL_ID       = "countdown_channel"
        const val NOTIF_ID         = 2001
        const val ACTION_STOP      = "com.example.scrollproject.STOP_COUNTDOWN"
        private const val POLL_MS  = 2_000L   // normal polling interval
        private const val FAST_MS  = 1_000L   // interval in final 10 seconds
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var detector: ForegroundAppDetector

    // Snapshot taken the moment the timer expires — survives TimerManager.stop().
    @Volatile private var expiredPackage: String? = null
    @Volatile private var expiredAppName:  String? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(applicationContext)
        ensureCountdownChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                buildForegroundNotification("Scroll Guard Active · Monitoring ready"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIF_ID,
                buildForegroundNotification("Scroll Guard Active · Monitoring ready")
            )
        }
        observeTimerForNotification()
        startFallbackEnforcement()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TimerManager.stop()
            // Update notification to ready state instead of stopping the service.
            // Stopping the service kills the process, disconnecting the accessibility service.
            updateForegroundNotification("Scroll Guard Active · Monitoring ready")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ─── Ongoing foreground notification (countdown ticker) ───────────────────

    /**
     * Collects the three relevant flows as a single combined emission to ensure
     * the notification is rebuilt exactly once per state change, not three times.
     */
    private fun observeTimerForNotification() {
        serviceScope.launch {
            combine(
                TimerManager.remainingSeconds,
                TimerManager.isRunning,
                TimerManager.monitoredAppName
            ) { remaining, running, appName ->
                Triple(remaining, running, appName)
            }
                .distinctUntilChanged()
                .collectLatest { (remaining, running, appName) ->
                    when {
                        running -> {
                            // Live tick — update the countdown label.
                            updateForegroundNotification(
                                "Monitoring ${appName ?: "app"} · ${formatTime(remaining)} left"
                            )
                        }
                        !running && remaining == 0L && appName != null -> {
                            // Natural expiry — show brief expiry message then revert to ready.
                            updateForegroundNotification(
                                "⏰ ${appName} time finished. App closed."
                            )
                            delay(4_000L)
                            // After expiry message, return to ready state (service stays alive).
                            updateForegroundNotification("Scroll Guard Active · Monitoring ready")
                        }
                        else -> {
                            // Idle or no app selected — show ready state.
                            updateForegroundNotification("Scroll Guard Active · Monitoring ready")
                        }
                    }
                }
        }
    }

    // ─── Fallback enforcement (no accessibility service) ─────────────────────

    /**
     * Polls UsageStats when [ScrollGuardAccessibilityService.isConnected] is false.
     *
     * On each poll:
     *  1. Skip entirely if the Accessibility Service is active (it handles this).
     *  2. Query the current foreground package from UsageStats.
     *  3. Inform TimerManager of the foreground package (feeds the timer engine).
     *  4. If the timer just expired and the monitored app is in the foreground,
     *     perform the redirect + notification.
     */
    private fun startFallbackEnforcement() {
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    pm.isInteractive
                } else {
                    @Suppress("DEPRECATION")
                    pm.isScreenOn
                }

                if (isInteractive) {
                    if (!ScrollGuardAccessibilityService.isConnected) {
                        val pkg = detector.getForegroundPackage()
                        if (!pkg.isNullOrEmpty()) {
                            TimerManager.setActiveForegroundPackage(pkg)

                            // Check if the foreground app is blocked!
                            if (TimerManager.isPackageBlocked(pkg)) {
                                expiredPackage = pkg
                                expiredAppName = TimerManager.getAppName(pkg)
                                enforceExpiryFallback()
                            }
                        }
                    }
                } else {
                    // Screen is off — clear foreground package to pause ticking immediately
                    TimerManager.setActiveForegroundPackage(null)
                }

                // Adaptive interval — tighter in the final 10 s.
                val remaining = TimerManager.getMinRemainingSecondsOfActiveApps()
                delay(if (remaining in 1..10) FAST_MS else POLL_MS)
            }
        }

        // Wire the expiry callback for the fallback path.
        // (The AccessibilityService overwrites this with its own handler when connected.)
        if (!ScrollGuardAccessibilityService.isConnected) {
            TimerManager.onTimerExpired = { pkg, name ->
                expiredPackage = pkg
                expiredAppName  = name
                serviceScope.launch(Dispatchers.IO) {
                    enforceExpiryFallback()
                }
            }
        }
    }

    /**
     * Fallback enforcement executed when the Accessibility Service is unavailable.
     *
     * GLOBAL_ACTION_HOME is not available to a regular Service, so we use the
     * CATEGORY_HOME intent — Android 12–15 exempts home-screen navigation from
     * the background-activity-start restriction.
     */
    private fun enforceExpiryFallback() {
        val target  = expiredPackage ?: return
        val name    = expiredAppName
            ?: target.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        val currentFg = detector.getForegroundPackage()
        if (currentFg == target) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            applicationContext.startActivity(homeIntent)
        }

        // Always notify — even if the user had already switched away.
        ExpiryNotificationManager.notify(applicationContext, name)
    }

    // ─── Countdown foreground notification helpers ────────────────────────────

    private fun ensureCountdownChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Scroll Guard Countdown",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description   = "Live countdown for the active monitoring session"
                setShowBadge(false)
                enableVibration(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildForegroundNotification(text: String): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, CountdownService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Scroll Guard Active")
            .setContentText(text)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_close, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateForegroundNotification(text: String) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildForegroundNotification(text))
    }

    private fun formatTime(s: Long): String {
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, sec)
               else       "%02d:%02d".format(m, sec)
    }
}
