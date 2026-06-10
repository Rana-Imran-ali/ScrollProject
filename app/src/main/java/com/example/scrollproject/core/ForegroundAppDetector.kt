package com.example.scrollproject.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * ForegroundAppDetector — UsageStatsManager-based foreground package resolver.
 *
 * Architecture role:
 *  This class is the **fallback** detection path.  The primary path is the
 *  event-driven [ScrollGuardAccessibilityService] which fires with zero latency
 *  and zero polling overhead on every TYPE_WINDOW_STATE_CHANGED event.
 *
 *  This class is used only when the Accessibility Service is not connected
 *  (user hasn't granted it yet, or the service was killed by the system).
 *
 * Battery design:
 *  • [queryLatestForegroundPackage] is the preferred method.  It queries only
 *    [windowMs] milliseconds of UsageEvents history, which is the smallest
 *    possible window that reliably captures a fresh foreground transition.
 *  • Callers (CountdownService) poll at ~2 s intervals — well below any
 *    perceptible enforcement latency while drawing negligible battery.
 *  • Both methods are safe to call on a background thread.
 *
 * Requires: android.permission.PACKAGE_USAGE_STATS (special, granted via Settings).
 */
class ForegroundAppDetector(context: Context) {

    private val usm = context.applicationContext
        .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Walks UsageEvents backwards over the last [windowMs] milliseconds and
     * returns the package name of the **most recent ACTIVITY_RESUMED** event.
     *
     * This is more accurate than [queryByIntervalStats] on Android 10+ because
     * it relies on individual events rather than aggregated interval stats, so
     * it correctly resolves the foreground app even when multiple apps are
     * switched within the same second.
     *
     * @param windowMs  How far back to look (default 3 000 ms).
     * @return          The foreground package name, or null if none is found or
     *                  the permission has not been granted.
     */
    fun queryLatestForegroundPackage(windowMs: Long = 3_000L): String? {
        val end   = System.currentTimeMillis()
        val start = end - windowMs

        return try {
            val events = usm.queryEvents(start, end) ?: return null
            val event  = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isResume && event.timeStamp > latestTime) {
                    latestTime    = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            latestPackage
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fallback for older Android versions (< 10): sorts aggregated interval
     * stats by lastTimeUsed and returns the most recent package name.
     *
     * Less precise than event-based querying — use [queryLatestForegroundPackage]
     * on Android 10+ (API 29+).
     */
    fun queryByIntervalStats(windowMs: Long = 5_000L): String? {
        val end   = System.currentTimeMillis()
        val start = end - windowMs

        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?.filter { it.lastTimeUsed > 0 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unified entry point: uses the event-based query on API 21+ (preferred),
     * falling back to interval stats if no events are found.
     */
    fun getForegroundPackage(): String? =
        queryLatestForegroundPackage().takeIf { !it.isNullOrEmpty() }
            ?: queryByIntervalStats()
}
