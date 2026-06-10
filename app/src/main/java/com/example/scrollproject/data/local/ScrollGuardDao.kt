package com.example.scrollproject.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY addedAt DESC")
    fun getAllApps(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE isBlockingEnabled = 1")
    fun getBlockedApps(): List<MonitoredAppEntity>

    @Query("SELECT packageName FROM monitored_apps")
    fun getAllPackageNames(): List<String>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :pkg LIMIT 1")
    fun getApp(pkg: String): MonitoredAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(app: MonitoredAppEntity): Long

    @Delete
    fun delete(app: MonitoredAppEntity): Int

    @Query("DELETE FROM monitored_apps WHERE packageName = :pkg")
    fun deleteByPackage(pkg: String): Int

    @Query("UPDATE monitored_apps SET isBlockingEnabled = :enabled WHERE packageName = :pkg")
    fun setBlocking(pkg: String, enabled: Boolean): Int

    @Query("UPDATE monitored_apps SET dailyLimitMinutes = :minutes WHERE packageName = :pkg")
    fun updateLimit(pkg: String, minutes: Int): Int
}
