package com.example.scrollproject.domain.model

import android.graphics.drawable.Drawable

data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 60,
    val isBlockingEnabled: Boolean = true,
    val icon: Drawable? = null
)

data class AppInfo(
    val packageName: String,
    val appName: String
)

data class UsageSummary(
    val date: String,
    val totalSeconds: Long
)

data class DashboardState(
    val totalSecondsToday: Long = 0L,
    val monitoredApps: List<MonitoredApp> = emptyList(),
    val usageMap: Map<String, Long> = emptyMap(),
    val isFocusModeActive: Boolean = false,
    val blockCountToday: Int = 0,
    val isLoading: Boolean = true
)
