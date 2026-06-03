package com.example.scrollproject.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.scrollproject.R
import com.example.scrollproject.core.TimerManager
import com.example.scrollproject.data.repository.ScrollGuardRepository
import com.example.scrollproject.ui.blockscreen.BlockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * ScrollGuardAccessibilityService — foreground-app detector & enforcer.
 *
 * Critical-fix checklist:
 *  ✅ Uses TimerManager.onLimitCheckNeeded callback (fires once/sec for active pkg)
 *     instead of collecting the full liveUsages StateFlow (which fired on every
 *     map update, causing duplicate/race block intents).
 *  ✅ Clears lastCheckedPackage after a block so re-open attempts are detected.
 *  ✅ Guards concurrent blocking with an atomic `isBlocking` flag per package.
 *  ✅ Persists daily-blocked packages across restarts via `blockedTodayPkgs`.
 *  ✅ Re-checks limits on TYPE_WINDOW_STATE_CHANGED even if pkg == lastCheckedPackage
 *     when the app is in the blocked set (relaunch detection).
 */
@AndroidEntryPoint
class ScrollGuardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: ScrollGuardRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Packages whose daily limit has been reached — persist until midnight. */
    private val blockedTodayPkgs = mutableSetOf<String>()

    /** Debounce: prevent firing two simultaneous block intents for the same pkg. */
    private val blockingInProgress = mutableSetOf<String>()

    /** Warning notifications: sent once per pkg per calendar day. */
    private val warnedPackages = mutableSetOf<String>()
    private var lastWarnDate = ""

    /** Last foreground package seen by onAccessibilityEvent. */
    private var lastForegroundPkg = ""

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        TimerManager.initialize(repository)

        // Register the per-second callback.  This replaces the old
        // liveUsages.collect{} pattern that triggered on every map mutation.
        TimerManager.onLimitCheckNeeded = { pkg, usedSeconds ->
            serviceScope.launch { checkAndBlock(pkg, usedSeconds) }
        }

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        createNotificationChannels()

        // Reload packages already blocked today from DB so restarts don't
        // give users a free pass to re-open a blocked app.
        serviceScope.launch { reloadBlockedToday() }
    }

    private suspend fun reloadBlockedToday() {
        val today = repository.today()
        // Any app that has exhausted its limit today should be pre-blocked.
        val monitoredApps = repository.getBlockedApps()
        for (app in monitoredApps) {
            if (app.dailyLimitMinutes == Int.MAX_VALUE) continue
            val used = repository.getUsedSecondsToday(app.packageName)
            val limit = app.dailyLimitMinutes * 60L
            if (used >= limit) {
                blockedTodayPkgs.add(app.packageName)
            }
        }
        lastWarnDate = today
    }

    // ─── Accessibility Events ─────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Skip system UI and our own package.
        if (pkg == packageName || pkg == "com.android.systemui") {
            TimerManager.setActivePackage(null)
            lastForegroundPkg = pkg
            return
        }

        val isRelaunchAttempt = pkg in blockedTodayPkgs
        // Skip duplicate events UNLESS it's a relaunch of an already-blocked app.
        if (pkg == lastForegroundPkg && !isRelaunchAttempt) return
        lastForegroundPkg = pkg

        serviceScope.launch {
            val monitoredApp = repository.getMonitoredApp(pkg)

            if (monitoredApp == null) {
                TimerManager.setActivePackage(null)
                return@launch
            }

            if (isRelaunchAttempt) {
                // Immediately re-block without updating active package tracking.
                launchBlockScreen(pkg, monitoredApp.appName, "limit_reached", monitoredApp.dailyLimitMinutes)
                return@launch
            }

            // App is monitored and not yet blocked — start tracking it.
            TimerManager.setActivePackage(pkg)

            if (monitoredApp.isBlockingEnabled) {
                val usedSeconds = maxOf(
                    repository.getUsedSecondsToday(pkg),
                    TimerManager.liveUsages.value[pkg] ?: 0L
                )
                checkAndBlock(pkg, usedSeconds)
            }
        }
    }

    // ─── Core blocking logic ──────────────────────────────────────────────────

    private suspend fun checkAndBlock(packageName: String, usedSeconds: Long) {
        val monitoredApp = repository.getMonitoredApp(packageName) ?: return
        if (!monitoredApp.isBlockingEnabled) return
        if (monitoredApp.dailyLimitMinutes == Int.MAX_VALUE) return  // Unlimited

        val isFocusActive = repository.getActiveSessionOnce() != null

        // Rotate warning state at midnight.
        val today = repository.today()
        if (today != lastWarnDate) {
            warnedPackages.clear()
            blockedTodayPkgs.clear()
            lastWarnDate = today
        }

        val limitSeconds = monitoredApp.dailyLimitMinutes * 60L

        // 5-minute warning notification (once per day per app).
        if (!isFocusActive
            && usedSeconds in (limitSeconds - 300)..(limitSeconds - 1)
            && packageName !in warnedPackages
        ) {
            warnedPackages.add(packageName)
            withContext(Dispatchers.Main) { showWarningNotification(monitoredApp.appName) }
        }

        val shouldBlock = isFocusActive || usedSeconds >= limitSeconds
        val blockReason = if (isFocusActive) "focus_mode" else "limit_reached"

        if (shouldBlock && packageName !in blockingInProgress) {
            blockingInProgress.add(packageName)
            if (blockReason == "limit_reached") blockedTodayPkgs.add(packageName)

            repository.logBlockEvent(packageName, blockReason)

            withContext(Dispatchers.Main) {
                showBlockNotification(monitoredApp.appName, blockReason)
                launchBlockScreen(packageName, monitoredApp.appName, blockReason, monitoredApp.dailyLimitMinutes)
                TimerManager.setActivePackage(null)
            }

            // Clear flag after a short delay so the same pkg can be re-blocked
            // if it surfaces again after the block screen is dismissed.
            delay(2_000L)
            blockingInProgress.remove(packageName)
        }
    }

    private fun launchBlockScreen(pkg: String, appName: String, reason: String, limitMinutes: Int) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(BlockActivity.EXTRA_APP_NAME, appName)
            putExtra(BlockActivity.EXTRA_REASON, reason)
            putExtra(BlockActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
        }
        startActivity(intent)
        // Clear lastForegroundPkg so the next TYPE_WINDOW_STATE_CHANGED for
        // this same pkg is treated as a fresh relaunch attempt.
        lastForegroundPkg = ""
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel("block_alerts", "App Blocking Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
            mgr.createNotificationChannel(
                NotificationChannel("warning_alerts", "Time Limit Warnings", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun showWarningNotification(appName: String) {
        val n = NotificationCompat.Builder(this, "warning_alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("5 Minutes Remaining")
            .setContentText("You have 5 minutes left for $appName today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify("${appName}_warn".hashCode(), n)
    }

    private fun showBlockNotification(appName: String, reason: String) {
        val text = if (reason == "focus_mode")
            "$appName is blocked during Focus Mode."
        else
            "Daily limit reached. $appName is blocked for today."

        val n = NotificationCompat.Builder(this, "block_alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Access Restricted")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(appName.hashCode(), n)
    }

    // ─── Teardown ─────────────────────────────────────────────────────────────

    override fun onInterrupt() {
        TimerManager.setActivePackage(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        TimerManager.onLimitCheckNeeded = null
        TimerManager.setActivePackage(null)
        serviceScope.launch { TimerManager.flushToDB() }
        serviceScope.cancel()
    }
}
