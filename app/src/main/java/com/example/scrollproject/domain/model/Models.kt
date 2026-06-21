package com.example.scrollproject.domain.model

import android.graphics.drawable.Drawable

data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val limitSeconds: Long = 3600L,
    val remainingSeconds: Long = 3600L,
    val usedSeconds: Long = 0L,
    val isMonitoringActive: Boolean = false,
    val isBlocked: Boolean = false
)

data class AppInfo(
    val packageName: String,
    val appName: String
)

data class DashboardState(
    val monitoredApps: List<MonitoredApp> = emptyList(),
    val selectedApp: MonitoredApp? = null,
    val countdownSeconds: Long = 60L,       // value shown in the input field
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isRunning: Boolean = false,
    val isLoadingApps: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isMonitoredAppsLoaded: Boolean = false
)
