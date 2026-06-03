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

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usages WHERE date = :date")
    fun getUsageForDate(date: String): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usages WHERE date = :date")
    fun getUsageForDateOnce(date: String): List<AppUsageEntity>

    @Query("SELECT * FROM app_usages WHERE date = :date AND packageName = :pkg LIMIT 1")
    fun getUsage(pkg: String, date: String): AppUsageEntity?

    @Query("SELECT SUM(timeSpentSeconds) FROM app_usages WHERE date = :date")
    fun getTotalSecondsForDate(date: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(usage: AppUsageEntity): Long

    @Query("""
        UPDATE app_usages SET timeSpentSeconds = timeSpentSeconds + :delta, lastUpdated = :now
        WHERE packageName = :pkg AND date = :date
    """)
    fun addTime(pkg: String, date: String, delta: Long, now: Long): Int

    @Query("SELECT * FROM app_usages WHERE date >= :startDate ORDER BY date DESC")
    fun getUsageSince(startDate: String): List<AppUsageEntity>
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE status = 'active' LIMIT 1")
    fun getActiveSession(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status = 'active' LIMIT 1")
    fun getActiveSessionOnce(): FocusSessionEntity?

    @Insert
    fun insert(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET status = :status, endTime = :endTime WHERE id = :id")
    fun updateStatus(id: Long, status: String, endTime: Long): Int
}

@Dao
interface BlockEventDao {
    @Insert
    fun insert(event: BlockEventEntity): Long

    @Query("SELECT COUNT(*) FROM block_events WHERE date(blockedAt/1000, 'unixepoch') = :date")
    fun getBlockCountForDate(date: String): Flow<Int>
}
