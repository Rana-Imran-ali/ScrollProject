package com.example.scrollproject.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TimerManager — global countdown singleton.
 *
 * Design decisions:
 *  • Uses wall-clock anchoring (System.currentTimeMillis) instead of pure delay()
 *    to guarantee accurate timing regardless of coroutine scheduling jitter. Over
 *    a 30-minute session, drift is < 1 second.
 *  • Survives screen changes and app minimisation because it lives in a top-level
 *    object outside any Activity/Fragment lifecycle.
 *  • The foreground CountdownService keeps the process alive so Android does not
 *    kill the coroutine scope while the screen is off.
 *  • onTimerExpired is called on the IO thread; callers must dispatch to Main if
 *    they need to touch Views.
 */
object TimerManager {

    // Dedicated IO scope — outlives every Activity.
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickJob: Job? = null

    // ─── Public state ─────────────────────────────────────────────────────────

    /** Seconds remaining in the active countdown. 0 = expired or not started. */
    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    /** Total seconds the current session was started with (used for ring progress). */
    private val _totalSeconds = MutableStateFlow(0L)
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    /** True while the ticker is running. */
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Package name of the app being monitored. */
    private val _monitoredPackage = MutableStateFlow<String?>(null)
    val monitoredPackage: StateFlow<String?> = _monitoredPackage.asStateFlow()

    /** Display name of the monitored app (shown in notifications and toasts). */
    private val _monitoredAppName = MutableStateFlow<String?>(null)
    val monitoredAppName: StateFlow<String?> = _monitoredAppName.asStateFlow()

    // ─── Internal state ───────────────────────────────────────────────────────

    /**
     * Package currently in the foreground, updated by the Accessibility Service
     * on every TYPE_WINDOW_STATE_CHANGED event.
     */
    @Volatile var activeForegroundPackage: String? = null
        private set

    /**
     * Callback fired the instant the timer reaches zero.
     * Set by ScrollGuardAccessibilityService; cleared on stop/destroy.
     * Always invoked on the IO thread — post to Main before touching Views.
     */
    var onTimerExpired: (() -> Unit)? = null

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Begin a new countdown. Any existing session is cancelled first.
     *
     * @param packageName  Package to monitor (compared against foreground app).
     * @param appName      Human-readable label used in notifications and toasts.
     * @param seconds      Session duration in seconds (must be > 0).
     */
    fun start(packageName: String, appName: String, seconds: Long) {
        require(seconds > 0) { "Countdown duration must be positive." }
        stop()                              // clean up any running session
        _monitoredPackage.value = packageName
        _monitoredAppName.value = appName
        _totalSeconds.value     = seconds
        _remainingSeconds.value = seconds
        _isRunning.value        = true
        startTicking()
    }

    /**
     * Cancel the countdown and reset all state.
     * Safe to call even if no session is running.
     */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _isRunning.value        = false
        _remainingSeconds.value = 0L
        _totalSeconds.value     = 0L
        _monitoredPackage.value = null
        _monitoredAppName.value = null
        activeForegroundPackage = null
    }

    // ─── Accessibility bridge ─────────────────────────────────────────────────

    /** Called by ScrollGuardAccessibilityService on every window-state change. */
    fun setActiveForegroundPackage(pkg: String?) {
        activeForegroundPackage = if (pkg.isNullOrEmpty()) null else pkg
    }

    // ─── Internal tick loop ───────────────────────────────────────────────────

    /**
     * Wall-clock-anchored tick loop.
     *
     * Instead of blindly delaying 1000 ms and subtracting 1, we record the
     * session's end time at launch and compute remaining seconds from the
     * real clock each tick. This eliminates cumulative drift from coroutine
     * scheduling overhead, GC pauses, and Doze-mode wake-up latency.
     */
    private fun startTicking() {
        val endAt = System.currentTimeMillis() + (_totalSeconds.value * 1_000L)

        tickJob = scope.launch {
            while (isActive && _isRunning.value) {
                val nowMs    = System.currentTimeMillis()
                val leftMs   = endAt - nowMs
                val leftSecs = (leftMs / 1_000L).coerceAtLeast(0L)

                _remainingSeconds.value = leftSecs

                if (leftSecs <= 0L) {
                    _isRunning.value = false
                    onTimerExpired?.invoke()
                    break
                }

                // Sleep until the next whole second boundary (not a fixed 1000 ms).
                // This keeps the UI clock perfectly synchronised with wall time.
                val msUntilNextTick = leftMs % 1_000L
                delay(if (msUntilNextTick > 0L) msUntilNextTick else 1_000L)
            }
        }
    }
}
