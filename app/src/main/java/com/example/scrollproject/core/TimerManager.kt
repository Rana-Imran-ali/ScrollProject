package com.example.scrollproject.core

import com.example.scrollproject.data.repository.ScrollGuardRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * TimerManager — singleton second-tick engine.
 *
 * Responsibilities:
 *  • Maintains live usage map (pkg → seconds today) as StateFlow for UI.
 *  • Increments the active foreground package every second.
 *  • Persists usage to the DB every 30 seconds to survive restarts.
 *  • Exposes [blockingCallbacks] so the AccessibilityService can react
 *    instantly when a limit is hit, without polling.
 */
object TimerManager {

    private var repository: ScrollGuardRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The package currently in the foreground (null = none / non-monitored). */
    var activePackage: String? = null
        private set

    /** Live per-package seconds-used today. Collected by the UI. */
    private val _liveUsages = MutableStateFlow<Map<String, Long>>(emptyMap())
    val liveUsages: StateFlow<Map<String, Long>> = _liveUsages.asStateFlow()

    /**
     * Callback invoked on the IO thread every second for the active package.
     * The AccessibilityService sets this to trigger instant blocking checks
     * without needing a separate observer coroutine that fires on every map update.
     */
    var onLimitCheckNeeded: ((pkg: String, usedSeconds: Long) -> Unit)? = null

    private var timerJob: Job? = null
    private var persistJob: Job? = null
    private var secondsSinceLastPersist = 0

    // ─── Initialisation ───────────────────────────────────────────────────────

    fun initialize(repo: ScrollGuardRepository) {
        if (repository != null) return          // already running
        repository = repo

        scope.launch {
            // Hydrate the live map from DB so countdowns survive restarts.
            val initial = repo.getTodayUsageOnce()
            val map = initial.associate { it.packageName to it.timeSpentSeconds }
            _liveUsages.update { it + map }
        }

        startTicking()
        startPersisting()
    }

    // ─── Active package ───────────────────────────────────────────────────────

    fun setActivePackage(pkg: String?) {
        activePackage = if (pkg.isNullOrEmpty()) null else pkg
    }

    // ─── Live usage (for UI / blocking checks) ────────────────────────────────

    /**
     * Sync a ground-truth value from the Usage Stats API.
     * Only updates if the system reports MORE time than we already have,
     * preventing accidental resets.
     */
    fun updateLiveUsage(pkg: String, totalSeconds: Long) {
        _liveUsages.update { current ->
            val existing = current[pkg] ?: 0L
            if (totalSeconds > existing) current + (pkg to totalSeconds) else current
        }
    }

    // ─── Core tick loop ───────────────────────────────────────────────────────

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val pkg = activePackage ?: continue

                // Increment the in-memory counter.
                var newSeconds = 0L
                _liveUsages.update { current ->
                    val updated = (current[pkg] ?: 0L) + 1L
                    newSeconds = updated
                    current + (pkg to updated)
                }

                // Notify AccessibilityService so it can block without a
                // StateFlow collector re-firing on every map mutation.
                onLimitCheckNeeded?.invoke(pkg, newSeconds)

                secondsSinceLastPersist++
            }
        }
    }

    // ─── Periodic DB persistence (every 30 s) ─────────────────────────────────

    private fun startPersisting() {
        persistJob?.cancel()
        persistJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                flushToDB()
            }
        }
    }

    /** Write the current live-usage deltas to Room. Call on app background too. */
    suspend fun flushToDB() {
        val repo = repository ?: return
        val snapshot = _liveUsages.value
        for ((pkg, liveSeconds) in snapshot) {
            val dbSeconds = repo.getUsedSecondsToday(pkg)
            if (liveSeconds > dbSeconds) {
                repo.addUsageTime(pkg, liveSeconds - dbSeconds)
            }
        }
    }
}
