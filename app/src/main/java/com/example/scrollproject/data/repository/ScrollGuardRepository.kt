package com.example.scrollproject.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.scrollproject.data.local.*
import com.example.scrollproject.domain.model.AppInfo
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.domain.model.UsageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ScrollGuardRepository(private val context: Context) {

    private val db = ScrollGuardDatabase.getInstance(context)
    private val monitoredAppDao = db.monitoredAppDao()
    private val appUsageDao = db.appUsageDao()
    private val focusSessionDao = db.focusSessionDao()
    private val blockEventDao = db.blockEventDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ─── Monitored Apps ───────────────────────────────────────────────────────

    fun getMonitoredApps(): Flow<List<MonitoredApp>> =
        monitoredAppDao.getAllApps().map { list ->
            list.map { it.toDomain(context) }
        }

    suspend fun addMonitoredApp(app: MonitoredApp) = withContext(Dispatchers.IO) {
        monitoredAppDao.insert(app.toEntity())
    }

    suspend fun removeMonitoredApp(packageName: String) = withContext(Dispatchers.IO) {
        monitoredAppDao.deleteByPackage(packageName)
    }

    suspend fun setBlocking(packageName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        monitoredAppDao.setBlocking(packageName, enabled)
    }

    suspend fun updateLimit(packageName: String, minutes: Int) = withContext(Dispatchers.IO) {
        monitoredAppDao.updateLimit(packageName, minutes)
    }


    suspend fun getBlockedApps(): List<MonitoredAppEntity> = withContext(Dispatchers.IO) {
        monitoredAppDao.getBlockedApps()
    }

    suspend fun getMonitoredApp(pkg: String): MonitoredAppEntity? = withContext(Dispatchers.IO) {
        monitoredAppDao.getApp(pkg)
    }

    // ─── Usage Tracking ──────────────────────────────────────────────────────

    fun getTodayUsage(): Flow<List<AppUsageEntity>> =
        appUsageDao.getUsageForDate(today())

    suspend fun getTodayUsageOnce(): List<AppUsageEntity> = withContext(Dispatchers.IO) {
        appUsageDao.getUsageForDateOnce(today())
    }

    fun getTodayTotalSeconds(): Flow<Long?> =
        appUsageDao.getTotalSecondsForDate(today())

    suspend fun addUsageTime(packageName: String, deltaSeconds: Long) = withContext(Dispatchers.IO) {
        val existing = appUsageDao.getUsage(packageName, today())
        if (existing == null) {
            appUsageDao.insert(AppUsageEntity(packageName = packageName, date = today(), timeSpentSeconds = deltaSeconds))
        } else {
            appUsageDao.addTime(packageName, today(), deltaSeconds, System.currentTimeMillis())
        }
    }

    suspend fun getUsedSecondsToday(packageName: String): Long = withContext(Dispatchers.IO) {
        appUsageDao.getUsage(packageName, today())?.timeSpentSeconds ?: 0L
    }

    suspend fun getWeeklyUsage(): List<UsageSummary> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFormat.format(cal.time)
        val usages = appUsageDao.getUsageSince(startDate)
        usages.groupBy { it.date }
            .map { (date, list) -> UsageSummary(date, list.sumOf { it.timeSpentSeconds }) }
            .sortedBy { it.date }
    }

    // ─── Focus Sessions ──────────────────────────────────────────────────────

    fun getActiveSession(): Flow<FocusSessionEntity?> = focusSessionDao.getActiveSession()

    suspend fun startFocusSession(): Long = withContext(Dispatchers.IO) {
        focusSessionDao.insert(FocusSessionEntity())
    }

    suspend fun stopFocusSession(status: String = "completed") = withContext(Dispatchers.IO) {
        val session = focusSessionDao.getActiveSessionOnce()
        session?.let { focusSessionDao.updateStatus(it.id, status, System.currentTimeMillis()) }
    }

    // ─── Block Events ────────────────────────────────────────────────────────

    suspend fun logBlockEvent(packageName: String, reason: String = "limit_reached") = withContext(Dispatchers.IO) {
        blockEventDao.insert(BlockEventEntity(packageName = packageName, reason = reason))
    }

    fun getTodayBlockCount(): Flow<Int> = blockEventDao.getBlockCountForDate(today())

    // ─── Installed Apps ──────────────────────────────────────────────────────

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolvedApps = pm.queryIntentActivities(intent, 0)
        resolvedApps
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map {
                AppInfo(
                    packageName = it.activityInfo.packageName,
                    appName = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.appName }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun today(): String = dateFormat.format(Date())

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private fun MonitoredAppEntity.toDomain(ctx: Context): MonitoredApp {
        val pm = ctx.packageManager
        val icon = try { pm.getApplicationIcon(packageName) } catch (e: Exception) { null }
        return MonitoredApp(
            packageName, appName, dailyLimitMinutes, 
            isBlockingEnabled, icon
        )
    }

    private fun MonitoredApp.toEntity() = MonitoredAppEntity(
        packageName = packageName,
        appName = appName,
        dailyLimitMinutes = dailyLimitMinutes,
        isBlockingEnabled = isBlockingEnabled
    )
}
