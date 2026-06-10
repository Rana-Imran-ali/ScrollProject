package com.example.scrollproject.domain.model

import android.graphics.drawable.Drawable

data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
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
    val isAccessibilityEnabled: Boolean = false
)
