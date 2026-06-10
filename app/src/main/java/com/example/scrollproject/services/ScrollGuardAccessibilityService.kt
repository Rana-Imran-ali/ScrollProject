package com.example.scrollproject.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.scrollproject.core.TimerManager

/**
 * ScrollGuardAccessibilityService — primary foreground-app detector & enforcer.
 *
 * Enforcement logic on expiry:
 *  ┌─────────────────────────────────────────────────────────────────────────┐
 *  │ 1. Timer reaches 0 → onTimerExpired callback fires (IO thread).         │
 *  │ 2. Capture expiredPackage before TimerManager.stop() clears it.         │
 *  │ 3. On main thread: check activeForegroundPackage == expiredPackage.     │
 *  │    YES → performGlobalAction(GLOBAL_ACTION_HOME) + expiry notification. │
 *  │    NO  → app not in foreground; just post the expiry notification.      │
 *  │ 4. Post-expiry guard: if user reopens the app later, step 3 repeats.   │
 *  └─────────────────────────────────────────────────────────────────────────┘
 *
 * Android 12–15 compatibility:
 *  • GLOBAL_ACTION_HOME is an Accessibility API — bypasses all background
 *    activity-start restrictions introduced in Android 10, 12, 14, and 15.
 *  • isConnected flag lets CountdownService skip UsageStats polling while
 *    this service is running, saving battery.
 */
class ScrollGuardAccessibilityService : AccessibilityService() {

    companion object {
        /** True while this service instance is bound to the system. */
        @Volatile var isConnected: Boolean = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Captured at the moment of expiry (before TimerManager.stop() resets all
     * fields to null). Required for post-expiry re-open detection.
     */
    @Volatile private var expiredPackage: String? = null
    @Volatile private var expiredAppName:  String? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        isConnected = true

        // Belt-and-suspenders config — some OEMs ignore the XML declaration.
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes          = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50L
        serviceInfo = info

        // TimerManager calls this from its IO coroutine the instant remaining == 0.
        TimerManager.onTimerExpired = {
            // Snapshot names NOW — stop() will null them out on the next line.
            val pkg  = TimerManager.monitoredPackage.value
            val name = TimerManager.monitoredAppName.value
            expiredPackage = pkg
            expiredAppName = name
            mainHandler.post { enforceExpiry() }
        }
    }

    // ─── Accessibility events ─────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: return

        // Ignore system chrome and ourselves.
        if (pkg == packageName || pkg == "com.android.systemui") return

        // Always keep TimerManager informed of the current foreground package.
        TimerManager.setActiveForegroundPackage(pkg)

        // Post-expiry guard: block any attempt to reopen the monitored app.
        val expired = expiredPackage
        if (expired != null && pkg == expired && !TimerManager.isRunning.value) {
            mainHandler.post { enforceExpiry() }
        }
    }

    // ─── Enforcement ─────────────────────────────────────────────────────────

    /**
     * Called both at timer expiry and whenever the user tries to reopen the
     * monitored app after the session has ended.
     *
     * Conditional close:
     *  • Only calls GLOBAL_ACTION_HOME when the monitored app is actually in
     *    the foreground. If the user has already switched away, we skip the
     *    home action and only post the notification (avoids disrupting whatever
     *    the user is doing in a different app).
     */
    private fun enforceExpiry() {
        val target   = expiredPackage ?: return
        val appName  = expiredAppName
            ?: target.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        val isForeground = TimerManager.activeForegroundPackage == target

        if (isForeground) {
            // App is currently open → push to home screen immediately.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // Always post the persistent "Time is finished." notification,
        // regardless of whether the app was in the foreground.
        ExpiryNotificationManager.notify(applicationContext, appName)
    }

    // ─── Teardown ─────────────────────────────────────────────────────────────

    override fun onInterrupt() {
        TimerManager.setActiveForegroundPackage(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected    = false
        expiredPackage = null
        expiredAppName = null
        TimerManager.onTimerExpired = null
        TimerManager.setActiveForegroundPackage(null)
    }
}
