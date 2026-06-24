package com.example.scrollproject.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.scrollproject.core.TimerManager

/**
 * ScrollGuardAccessibilityService — the primary enforcer.
 *
 * How it works:
 * 1. Android calls onAccessibilityEvent() every time any app comes to the foreground.
 * 2. We tell TimerManager which package is now in front.
 * 3. If the timer reaches 0, TimerManager calls onTimerExpired → we press the Home button.
 * 4. If the user reopens a blocked app later, we press Home again.
 *
 * When this service is connected, CountdownService skips its polling loop (saves battery).
 */
class ScrollGuardAccessibilityService : AccessibilityService() {

    companion object {
        /** True while this service instance is bound to the system. */
        @Volatile var isConnected: Boolean = false
            private set

        /**
         * When invoked, clears the post-expiry block held by the live service
         * instance (nulls expiredPackage / expiredAppName) so the service stops
         * intercepting the app that was just removed from the monitored list.
         *
         * Set by the live instance in [onServiceConnected]; cleared in [onDestroy].
         * Safe to call as a no-op when the service is not connected (nullable invoke).
         */
        @Volatile var clearBlock: (() -> Unit)? = null
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
        TimerManager.onTimerExpired = { pkg, name ->
            expiredPackage = pkg
            expiredAppName = name
            mainHandler.post { enforceExpiry() }
        }

        // Expose a hook so DashboardViewModel.removeApp() can instantly lift the
        // post-expiry block without restarting the phone or the service.
        clearBlock = {
            expiredPackage = null
            expiredAppName = null
        }

        // Restore/Ensure CountdownService is running if there is an active session
        if (TimerManager.isRunning.value) {
            val serviceIntent = Intent(applicationContext, CountdownService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Log or ignore
            }
        }
    }

    // ─── Accessibility events ─────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: return

        // Always keep TimerManager informed of the current foreground package.
        TimerManager.setActiveForegroundPackage(pkg)

        // Ignore system chrome and ourselves.
        if (pkg == packageName || pkg == "com.android.systemui") return

        // Post-expiry guard: block any attempt to reopen a blocked monitored app.
        if (TimerManager.isPackageBlocked(pkg)) {
            expiredPackage = pkg
            expiredAppName = TimerManager.getAppName(pkg)
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
        clearBlock             = null
        TimerManager.onTimerExpired = null
        TimerManager.setActiveForegroundPackage(null)
    }
}
