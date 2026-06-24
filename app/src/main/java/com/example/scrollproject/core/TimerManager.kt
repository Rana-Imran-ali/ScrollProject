package com.example.scrollproject.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.scrollproject.data.local.MonitoredAppEntity
import com.example.scrollproject.data.local.ScrollGuardDatabase
import com.example.scrollproject.services.CountdownService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * TimerManager — the brain of the countdown.
 *
 * - Keeps a map of monitored apps in memory.
 * - Ticks down an app's remaining time only while that app is in the foreground.
 * - Saves progress to the database every 10 seconds (and immediately on expiry).
 * - Resets all daily limits at midnight automatically.
 */
object TimerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickJob: Job? = null
    private val lock = Any()

    private lateinit var db: ScrollGuardDatabase

    /**
     * In-memory mirror of monitored_apps. Updated immediately on start()
     * and lazily synced from the Room flow for everything else.
     */
    private val monitoredAppsMap = java.util.concurrent.ConcurrentHashMap<String, MonitoredAppEntity>()
    private var currentSelectedPackage: String? = null

    // ─── Live state exposed to the UI (flows update whenever TimerManager changes) ─

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _totalSeconds = MutableStateFlow(0L)
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _monitoredPackage = MutableStateFlow<String?>(null)
    val monitoredPackage: StateFlow<String?> = _monitoredPackage.asStateFlow()

    private val _monitoredAppName = MutableStateFlow<String?>(null)
    val monitoredAppName: StateFlow<String?> = _monitoredAppName.asStateFlow()

    // ─── Internal ─────────────────────────────────────────────────────────────

    private lateinit var appPackageName: String

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                setActiveForegroundPackage(null)
            }
        }
    }

    @Volatile var activeForegroundPackage: String? = null
        private set

    @Volatile private var tickingPackage: String? = null

    var onTimerExpired: ((packageName: String, appName: String) -> Unit)? = null

    private var lastActiveTimeMs: Long = 0L

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        appPackageName = context.packageName
        db = ScrollGuardDatabase.getInstance(context)

        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenReceiver, filter)
            }
        } catch (_: Exception) {}

        // Sync map from DB and apply daily resets safely
        scope.launch(Dispatchers.IO) {
            try {
                // Get one-shot initial list of apps to check for daily resets.
                // Using first() avoids re-entrant writes during live collection.
                val entities = db.monitoredAppDao().getAllApps().first()
                val currentDate = getCurrentDateString()
                val toReset = entities.filter { it.lastResetDate != currentDate }

                if (toReset.isNotEmpty()) {
                    toReset.forEach { app ->
                        val reset = app.copy(
                            remainingSeconds  = app.dailyLimitSeconds,
                            usedSeconds       = 0L,
                            isBlocked         = false,
                            isMonitoringActive = true,
                            lastResetDate     = currentDate
                        )
                        db.monitoredAppDao().insert(reset)
                    }
                }

                // Now start the live flow collector to keep monitoredAppsMap synchronized.
                db.monitoredAppDao().getAllApps().collect { liveEntities ->
                    synchronized(lock) {
                        for (app in liveEntities) {
                            val inFlight = monitoredAppsMap[app.packageName]
                            if (inFlight == null) {
                                monitoredAppsMap[app.packageName] = app
                            } else {
                                monitoredAppsMap[app.packageName] = inFlight.copy(
                                    dailyLimitSeconds  = app.dailyLimitSeconds,
                                    isBlocked          = app.isBlocked,
                                    lastResetDate      = app.lastResetDate,
                                    appName            = app.appName
                                )
                            }
                        }
                        val dbKeys = liveEntities.map { it.packageName }.toSet()
                        monitoredAppsMap.keys.retainAll(dbKeys)

                        updatePublicStateFlows()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScrollGuard", "TimerManager database sync failed safely", e)
            }
        }

        // Start CountdownService to keep the process and accessibility service alive
        scope.launch {
            delay(800L)
            startCountdownService(context)
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Register/update a monitored app and immediately start tracking it.
     * The in-memory map is updated synchronously so checkForegroundState()
     * works right away without waiting for the DB flow.
     */
    fun start(packageName: String, appName: String, seconds: Long) {
        require(seconds > 0)
        synchronized(lock) {
            val existing = monitoredAppsMap[packageName]
            val entity = MonitoredAppEntity(
                packageName        = packageName,
                appName            = appName,
                dailyLimitSeconds  = seconds,
                remainingSeconds   = seconds,
                usedSeconds        = existing?.usedSeconds ?: 0L,
                isBlocked          = false,
                isMonitoringActive = true,
                lastResetDate      = getCurrentDateString(),
                addedAt            = existing?.addedAt ?: System.currentTimeMillis()
            )
            // ← Immediate in-memory update (key fix)
            monitoredAppsMap[packageName] = entity
            currentSelectedPackage = packageName
            updatePublicStateFlows()

            // Persist asynchronously
            scope.launch(Dispatchers.IO) { db.monitoredAppDao().insert(entity) }

            // Kick the tick loop now that the map has the app
            checkForegroundState()
        }
    }

    fun stop() {
        synchronized(lock) { currentSelectedPackage?.let { stopAppMonitoring(it) } }
    }

    fun stopAppMonitoring(packageName: String) {
        synchronized(lock) {
            val app = monitoredAppsMap[packageName] ?: return
            val updated = app.copy(isMonitoringActive = false)
            monitoredAppsMap[packageName] = updated
            scope.launch(Dispatchers.IO) { db.monitoredAppDao().insert(updated) }

            if (tickingPackage == packageName) {
                tickJob?.cancel()
                tickJob = null
                tickingPackage = null
            }
            updatePublicStateFlows()
        }
    }

    fun removeApp(packageName: String) {
        synchronized(lock) {
            monitoredAppsMap.remove(packageName)
            if (tickingPackage == packageName) {
                tickJob?.cancel()
                tickJob = null
                tickingPackage = null
            }
            if (currentSelectedPackage == packageName) {
                currentSelectedPackage = monitoredAppsMap.keys.firstOrNull()
            }
            updatePublicStateFlows()
        }
    }

    fun selectApp(packageName: String?) {
        synchronized(lock) {
            currentSelectedPackage = packageName
            updatePublicStateFlows()
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    fun isPackageBlocked(pkg: String) = monitoredAppsMap[pkg]?.isBlocked == true

    fun isPackageMonitored(pkg: String) = monitoredAppsMap.containsKey(pkg)

    fun getAppName(pkg: String) = monitoredAppsMap[pkg]?.appName

    fun getMinRemainingSecondsOfActiveApps(): Long {
        val min = monitoredAppsMap.values
            .filter { !it.isBlocked && it.remainingSeconds > 0 }
            .minOfOrNull { it.remainingSeconds }
        return min ?: 0L
    }

    fun hasAnyActiveMonitoring(): Boolean =
        monitoredAppsMap.values.any { !it.isBlocked && it.remainingSeconds > 0 }

    // ─── Called by AccessibilityService (or fallback polling) on every app switch ─

    fun setActiveForegroundPackage(pkg: String?) {
        synchronized(lock) {
            val isOurs = ::appPackageName.isInitialized && pkg == appPackageName
            val cleanPkg = if (pkg.isNullOrEmpty() || isOurs || pkg == "com.android.systemui") null else pkg
            
            // Sync currentSelectedPackage with the monitored foreground app
            if (cleanPkg != null && monitoredAppsMap.containsKey(cleanPkg)) {
                currentSelectedPackage = cleanPkg
            }
            
            activeForegroundPackage = cleanPkg
            checkForegroundState()
        }
    }

    // ─── Internal tick engine ─────────────────────────────────────────────────

    /**
     * Start ticking if the current foreground app is one we're monitoring (and not blocked).
     * Stop ticking if the user switched to a different app.
     */
    private fun checkForegroundState() {
        val fgPkg = activeForegroundPackage
        val shouldTick = fgPkg != null &&
                monitoredAppsMap.containsKey(fgPkg) &&
                monitoredAppsMap[fgPkg]?.isBlocked == false &&
                (monitoredAppsMap[fgPkg]?.remainingSeconds ?: 0L) > 0L

        if (shouldTick) {
            if (tickingPackage != fgPkg) {
                // Foreground app changed — flush elapsed time for old app, start fresh
                flushElapsedForCurrentTick()
                tickJob?.cancel()
                tickJob = null
                tickingPackage = fgPkg
                lastActiveTimeMs = System.currentTimeMillis()
                startTicking()
            } else if (tickJob == null || !tickJob!!.isActive) {
                // Same app, but tick loop died — restart
                lastActiveTimeMs = System.currentTimeMillis()
                startTicking()
            }
        } else {
            // App left foreground or is blocked — flush and pause
            flushElapsedForCurrentTick()
            tickJob?.cancel()
            tickJob = null
            tickingPackage = null
        }
    }

    private fun flushElapsedForCurrentTick() {
        val pkg = tickingPackage ?: return
        val elapsed = (System.currentTimeMillis() - lastActiveTimeMs) / 1000L
        if (elapsed > 0L) {
            decrementRemainingTime(pkg, elapsed, forceDbWrite = true)
        } else {
            // Write current state to DB anyway to persist latest tick decrements
            monitoredAppsMap[pkg]?.let { app ->
                scope.launch(Dispatchers.IO) { db.monitoredAppDao().insert(app) }
            }
        }
    }

    private fun startTicking() {
        tickJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1000L)
                synchronized(lock) {
                    val fgPkg = activeForegroundPackage
                    if (fgPkg != null && fgPkg == tickingPackage &&
                        monitoredAppsMap[fgPkg]?.isBlocked == false) {
                        val now = System.currentTimeMillis()
                        val elapsed = (now - lastActiveTimeMs) / 1000L
                        if (elapsed > 0L) {
                            tickCount++
                            val forceDbWrite = (tickCount % 10 == 0)
                            decrementRemainingTime(fgPkg, elapsed, forceDbWrite)
                            lastActiveTimeMs += elapsed * 1000L
                        }
                    } else {
                        // Foreground shifted — let checkForegroundState handle it
                        tickJob?.cancel()
                        tickJob = null
                        checkForegroundState()
                    }
                }
            }
        }
    }

    private fun decrementRemainingTime(pkg: String, elapsedSec: Long, forceDbWrite: Boolean = false) {
        val app = monitoredAppsMap[pkg] ?: return
        val nextRemaining = (app.remainingSeconds - elapsedSec).coerceAtLeast(0L)
        val nextUsed      = app.usedSeconds + elapsedSec
        val expired       = nextRemaining <= 0L

        val updated = app.copy(
            remainingSeconds   = nextRemaining,
            usedSeconds        = nextUsed,
            isBlocked          = expired,
            isMonitoringActive = !expired
        )
        monitoredAppsMap[pkg] = updated
        updatePublicStateFlows()

        if (expired || forceDbWrite) {
            scope.launch(Dispatchers.IO) { db.monitoredAppDao().insert(updated) }
        }

        if (expired) {
            tickJob?.cancel(); tickJob = null; tickingPackage = null
            onTimerExpired?.invoke(pkg, app.appName)
        }
    }

    private fun updatePublicStateFlows() {
        val app = monitoredAppsMap[currentSelectedPackage]
        _remainingSeconds.value = app?.remainingSeconds ?: 0L
        _totalSeconds.value     = app?.dailyLimitSeconds ?: 0L
        _isRunning.value        = app != null && !app.isBlocked && app.remainingSeconds > 0L
        _monitoredPackage.value = app?.packageName
        _monitoredAppName.value = app?.appName
    }

    private fun startCountdownService(context: Context) {
        val intent = Intent(context, CountdownService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        } catch (_: Exception) {}
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
