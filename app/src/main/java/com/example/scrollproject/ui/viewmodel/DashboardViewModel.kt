package com.example.scrollproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrollproject.data.local.AppUsageEntity
import com.example.scrollproject.data.repository.ScrollGuardRepository
import com.example.scrollproject.domain.model.AppInfo
import com.example.scrollproject.domain.model.DashboardState
import com.example.scrollproject.domain.model.MonitoredApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.scrollproject.core.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val repository: ScrollGuardRepository
) : ViewModel() {

    // ─── Dashboard State ─────────────────────────────────────────────────────

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _isFocusModeActive = MutableStateFlow(false)
    val isFocusModeActive: StateFlow<Boolean> = _isFocusModeActive.asStateFlow()

    // ─── App Selection ───────────────────────────────────────────────────────

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter { it.appName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    // ─── Snackbar ────────────────────────────────────────────────────────────

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        TimerManager.initialize(repository)
        observeDashboard()
        observeFocusSession()
    }

    private fun observeDashboard() {
        viewModelScope.launch {
            combine(
                repository.getMonitoredApps(),
                repository.getTodayTotalSeconds(),
                TimerManager.liveUsages,
                repository.getTodayBlockCount()
            ) { apps, total, liveUsages, blocks ->
                DashboardState(
                    totalSecondsToday = liveUsages.values.sum(),
                    monitoredApps = apps,
                    usageMap = liveUsages,
                    isFocusModeActive = _isFocusModeActive.value,
                    blockCountToday = blocks,
                    isLoading = false
                )
            }.collect { _dashboardState.value = it }
        }
    }

    private fun observeFocusSession() {
        viewModelScope.launch {
            repository.getActiveSession().collect { session ->
                _isFocusModeActive.value = session != null
                _dashboardState.value = _dashboardState.value.copy(isFocusModeActive = session != null)
            }
        }
    }

    // ─── Focus Mode ──────────────────────────────────────────────────────────

    fun toggleFocusMode(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                repository.startFocusSession()
                _snackbarMessage.emit("Focus mode activated — all tracked apps blocked")
            } else {
                repository.stopFocusSession("completed")
                _snackbarMessage.emit("Focus mode ended")
            }
        }
    }

    // ─── Monitored Apps ──────────────────────────────────────────────────────

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            repository.removeMonitoredApp(packageName)
            _snackbarMessage.emit("App removed from monitoring")
        }
    }

    fun toggleBlocking(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlocking(packageName, enabled)
        }
    }

    fun updateLimit(packageName: String, minutes: Int) {
        viewModelScope.launch {
            repository.updateLimit(packageName, minutes)
            _snackbarMessage.emit("Time limit updated")
        }
    }

    // ─── App Selection ───────────────────────────────────────────────────────

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = repository.getInstalledApps()
            _isLoadingApps.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppSelection(packageName: String) {
        val current = _selectedPackages.value.toMutableSet()
        if (current.contains(packageName)) current.remove(packageName)
        else current.add(packageName)
        _selectedPackages.value = current
    }

    fun confirmSelection() {
        viewModelScope.launch {
            val selected = _selectedPackages.value
            val apps = _installedApps.value
            selected.forEach { pkg ->
                val info = apps.find { it.packageName == pkg } ?: return@forEach
                repository.addMonitoredApp(
                    MonitoredApp(
                        packageName = pkg,
                        appName = info.appName,
                        dailyLimitMinutes = 60,
                        isBlockingEnabled = true,
                        icon = null
                    )
                )
            }
            _selectedPackages.value = emptySet()
            _snackbarMessage.emit("${selected.size} app(s) added to monitoring")
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }
}
