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
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_usages", primaryKeys = ["packageName", "date"])
data class AppUsageEntity(
    val packageName: String,
    val date: String,           // YYYY-MM-DD
    val timeSpentSeconds: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = "active"   // active | completed | interrupted
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val reason: String = "limit_reached"   // limit_reached | focus_mode
)
