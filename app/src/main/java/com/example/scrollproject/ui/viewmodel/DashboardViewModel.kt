package com.example.scrollproject.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrollproject.core.TimerManager
import com.example.scrollproject.data.repository.ScrollGuardRepository
import com.example.scrollproject.domain.model.AppInfo
import com.example.scrollproject.domain.model.DashboardState
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.services.CountdownService
import com.example.scrollproject.services.ScrollGuardAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ScrollGuardRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ─── UI State ─────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _openTimeInputDialog = MutableSharedFlow<MonitoredApp>(extraBufferCapacity = 1)
    val openTimeInputDialog: SharedFlow<MonitoredApp> = _openTimeInputDialog.asSharedFlow()

    var hasAutoNavigatedToSelection = false

    // ─── App picker state ─────────────────────────────────────────────────────

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Derived: installed apps filtered by the live search query. */
    val filteredApps: StateFlow<List<AppInfo>> =
        combine(_installedApps, _searchQuery) { apps, query ->
            if (query.isBlank()) apps
            else apps.filter { it.appName.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ─── Snackbar ─────────────────────────────────────────────────────────────

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        observeTimerState()
    }

    // ─── Timer state observation ──────────────────────────────────────────────

    /**
     * Mirrors the four TimerManager flows into [_state] so the Compose UI reacts
     * automatically to every countdown tick.
     *
     * Uses collectLatest so that if TimerManager emits faster than the lambda
     * processes (e.g. during rapid start-stop cycles), stale emissions are dropped
     * rather than queued.
     *
     * Also handles post-expiry state reset: when the timer reaches 0 naturally
     * (isRunning = false, remaining = 0, but total > 0 meaning a session ran),
     * we reset countdownSeconds so the input field is ready for a fresh session.
     */
    private fun observeTimerState() {
        viewModelScope.launch {
            combine(
                TimerManager.remainingSeconds,
                TimerManager.totalSeconds,
                TimerManager.isRunning,
                TimerManager.monitoredPackage
            ) { remaining, total, running, pkg ->
                // Keep selectedApp in sync with what TimerManager is tracking.
                val currentSelected = _state.value.selectedApp
                val syncedApp = if (pkg != null && currentSelected?.packageName != pkg) {
                    _state.value.monitoredApps.find { it.packageName == pkg } ?: currentSelected
                } else {
                    currentSelected
                }
                _state.value.copy(
                    remainingSeconds = remaining,
                    totalSeconds     = total,
                    isRunning        = running,
                    selectedApp      = syncedApp
                )
            }
            .collectLatest { newState ->
                val prev = _state.value
                _state.value = newState

                // Post-expiry reset: session finished naturally.
                // Restore countdownSeconds to the value that just ran so the user
                // can restart the same duration with one tap.
                if (prev.isRunning && !newState.isRunning && newState.remainingSeconds == 0L) {
                    _state.update { it.copy(countdownSeconds = prev.totalSeconds.coerceAtLeast(60L)) }
                }
            }
        }
    }

    // ─── Countdown controls ───────────────────────────────────────────────────

    /**
     * Validates inputs, starts the TimerManager countdown, and launches the
     * foreground CountdownService to keep the process alive.
     *
     * Android 12+ note: [startForegroundService] throws
     * [android.app.ForegroundServiceStartNotAllowedException] if called from a
     * truly backgrounded process. This is safe here because the user taps a
     * button in a visible Activity — the app is always in the foreground at the
     * moment of the call. The try-catch guards against edge cases (e.g. rapid
     * double-tap after the activity pauses).
     */
    fun startCountdown(context: Context) {
        val app = _state.value.selectedApp
        if (app == null) {
            emit("Please select an app first.")
            return
        }
        val seconds = _state.value.countdownSeconds
        if (seconds <= 0L) {
            emit("Enter a duration greater than 0 seconds.")
            return
        }

        TimerManager.start(app.packageName, app.appName, seconds)

        val serviceIntent = Intent(context, CountdownService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or similar.
            // Timer is already running in TimerManager; service start is best-effort.
            emit("Could not start background service: ${e.message}")
        }
    }

    /**
     * Stops the countdown. Sends the STOP action to the service (so it calls
     * stopSelf and removes the notification), then stops TimerManager directly
     * as a belt-and-suspenders guard if the service is already dead.
     */
    fun stopCountdown(context: Context) {
        val selected = _state.value.selectedApp
        if (selected != null) {
            TimerManager.stopAppMonitoring(selected.packageName)
            if (!TimerManager.hasAnyActiveMonitoring()) {
                try {
                    val stopIntent = Intent(context, CountdownService::class.java)
                        .apply { action = CountdownService.ACTION_STOP }
                    context.startService(stopIntent)
                } catch (_: Exception) { /* service may already be stopped */ }
            }
        }
    }

    fun saveAppLimit(app: MonitoredApp, seconds: Long) {
        TimerManager.start(app.packageName, app.appName, seconds)
        val serviceIntent = Intent(context, CountdownService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {}
    }

    fun setCountdownSeconds(seconds: Long) {
        _state.update { it.copy(countdownSeconds = seconds.coerceAtLeast(1L)) }
    }

    // ─── Accessibility status ─────────────────────────────────────────────────

    /** Call from Activity.onResume() to refresh the accessibility warning card. */
    fun refreshAccessibilityStatus() {
        _state.update { it.copy(isAccessibilityEnabled = isAccessibilityEnabled()) }
    }

    // ─── App picker ───────────────────────────────────────────────────────────

    fun loadInstalledApps() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingApps = true) }
            _installedApps.value = repository.getInstalledApps()
            _state.update { it.copy(isLoadingApps = false) }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /**
     * Called when the user selects an app from the picker.
     * Persists to Room (so the selection survives restarts) and updates UI state.
     */
    fun selectApp(pkg: String, seconds: Long = 3600L) {
        val app  = _installedApps.value.find { it.packageName == pkg } ?: return
        val icon = try { context.packageManager.getApplicationIcon(pkg) }
                   catch (_: Exception) { null }

        val monitored = MonitoredApp(
            packageName = app.packageName,
            appName = app.appName,
            icon = icon,
            limitSeconds = seconds,
            remainingSeconds = seconds
        )
        viewModelScope.launch {
            repository.addMonitoredApp(monitored)
        }

        _state.update { s ->
            s.copy(
                selectedApp   = monitored,
                monitoredApps = s.monitoredApps.filter { it.packageName != pkg } + monitored
            )
        }
        TimerManager.selectApp(pkg)
        saveAppLimit(monitored, seconds)
        _searchQuery.value = ""
    }

    fun loadMonitoredApps() {
        viewModelScope.launch {
            repository.getMonitoredApps().collect { apps ->
                _state.update { it.copy(monitoredApps = apps, isMonitoredAppsLoaded = true) }
            }
        }
    }

    fun selectFromMonitored(app: MonitoredApp) {
        _state.update { it.copy(selectedApp = app) }
        TimerManager.selectApp(app.packageName)
        viewModelScope.launch {
            _openTimeInputDialog.emit(app)
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            // Remove from TimerManager memory (stops ticking, cleans map)
            TimerManager.removeApp(packageName)
            // Lift any post-expiry block held by the Accessibility Service
            ScrollGuardAccessibilityService.clearBlock?.invoke()
            // Remove from DB
            repository.removeMonitoredApp(packageName)
            // Update UI state
            _state.update { s ->
                val remaining = s.monitoredApps.filter { it.packageName != packageName }
                val newSelected = if (s.selectedApp?.packageName == packageName)
                    remaining.firstOrNull() else s.selectedApp
                s.copy(monitoredApps = remaining, selectedApp = newSelected)
            }
            TimerManager.selectApp(_state.value.selectedApp?.packageName)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun emit(msg: String) { viewModelScope.launch { _snackbar.emit(msg) } }

    private fun isAccessibilityEnabled(): Boolean {
        // Fast path: if the service object is connected, skip Settings.Secure query.
        if (ScrollGuardAccessibilityService.isConnected) return true

        var enabled = 0
        try {
            enabled = Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (_: Settings.SettingNotFoundException) {}

        if (enabled != 1) return false

        val services = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = android.content.ComponentName(
            context.packageName,
            ScrollGuardAccessibilityService::class.java.name
        ).flattenToString()
        return services.contains(component, ignoreCase = true)
    }
}
