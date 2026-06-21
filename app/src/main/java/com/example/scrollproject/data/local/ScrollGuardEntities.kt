package com.example.scrollproject.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 60,
    val isBlockingEnabled: Boolean = true,
    val dailyLimitSeconds: Long = 3600L,
    val remainingSeconds: Long = 3600L,
    val usedSeconds: Long = 0L,
    val isBlocked: Boolean = false,
    val isMonitoringActive: Boolean = false,
    val lastResetDate: String = "",
    val addedAt: Long = System.currentTimeMillis()
)
