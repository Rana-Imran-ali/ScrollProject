package com.example.scrollproject.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.scrollproject.data.local.*
import com.example.scrollproject.domain.model.AppInfo
import com.example.scrollproject.domain.model.MonitoredApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScrollGuardRepository — simplified data layer.
 *
 * Only surfaces what the countdown-timer feature needs:
 *  • List of monitored apps (persisted in Room so the selection survives restarts).
 *  • Installed-apps query for the app-picker screen.
 *
 * Legacy tables (app_usages, focus_sessions, block_events) have been fully
 * removed from the schema in Phase 4. Only MonitoredAppEntity remains.
 */
@Singleton
class ScrollGuardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoredDao: MonitoredAppDao
) {

    // ─── Monitored Apps ───────────────────────────────────────────────────────

    /** Live list for the dashboard. Emits whenever the table changes. */
    fun getMonitoredApps(): Flow<List<MonitoredApp>> =
        monitoredDao.getAllApps().map { list -> list.map { it.toDomain(context) } }

    /** Upsert a monitored app (called when user selects from picker). */
    suspend fun addMonitoredApp(app: MonitoredApp) = withContext(Dispatchers.IO) {
        monitoredDao.insert(app.toEntity())
    }

    /** Remove a monitored app by package name. */
    suspend fun removeMonitoredApp(packageName: String) = withContext(Dispatchers.IO) {
        monitoredDao.deleteByPackage(packageName)
    }

    // ─── Installed Apps ───────────────────────────────────────────────────────

    /** Returns all user-installed, launchable apps sorted by name. */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm     = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.appName }
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private fun MonitoredAppEntity.toDomain(ctx: Context): MonitoredApp {
        val icon = try { ctx.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
        return MonitoredApp(
            packageName = packageName,
            appName = appName,
            icon = icon,
            limitSeconds = dailyLimitSeconds,
            remainingSeconds = remainingSeconds,
            usedSeconds = usedSeconds,
            isMonitoringActive = isMonitoringActive,
            isBlocked = isBlocked
        )
    }

    /** Maps the simplified domain model back to the full entity. */
    private fun MonitoredApp.toEntity() = MonitoredAppEntity(
        packageName       = packageName,
        appName           = appName,
        dailyLimitMinutes = (limitSeconds / 60L).toInt(),
        isBlockingEnabled = isBlocked,
        dailyLimitSeconds = limitSeconds,
        remainingSeconds  = remainingSeconds,
        usedSeconds       = usedSeconds,
        isBlocked         = isBlocked,
        isMonitoringActive = isMonitoringActive
    )
}
