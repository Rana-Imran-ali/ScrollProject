package com.example.scrollproject.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.scrollproject.R
import com.example.scrollproject.core.TimerManager
import com.example.scrollproject.data.local.ScrollGuardDatabase
import com.example.scrollproject.data.repository.ScrollGuardRepository
import com.example.scrollproject.ui.blockscreen.BlockActivity
import kotlinx.coroutines.*

class ScrollGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCheckedPackage = ""
    private val warnedPackages = mutableSetOf<String>()
    private var lastWarnDate = ""

    override fun onServiceConnected() {
        val repo = ScrollGuardRepository(applicationContext)
        TimerManager.initialize(repo)
        
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        
        createNotificationChannel()
        observeLiveTimer()
    }

    private fun observeLiveTimer() {
        serviceScope.launch {
            TimerManager.liveUsages.collect { usages ->
                val activePkg = TimerManager.activePackage
                if (!activePkg.isNullOrEmpty()) {
                    checkAndBlock(activePkg)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == lastCheckedPackage) return
        lastCheckedPackage = pkg

        // Skip our own app and system UI
        if (pkg == packageName || pkg == "com.android.systemui") {
            TimerManager.setActivePackage("")
            return
        }

        TimerManager.setActivePackage(pkg)

        serviceScope.launch {
            checkAndBlock(pkg)
        }
    }

    private suspend fun checkAndBlock(packageName: String) {
        val db = ScrollGuardDatabase.getInstance(applicationContext)
        val appDao = db.monitoredAppDao()
        val usageDao = db.appUsageDao()
        val focusDao = db.focusSessionDao()
        val blockDao = db.blockEventDao()

        val monitoredApp = appDao.getApp(packageName) ?: return
        if (!monitoredApp.isBlockingEnabled) return

        val isFocusActive = focusDao.getActiveSessionOnce() != null
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        
        if (dateStr != lastWarnDate) {
            warnedPackages.clear()
            lastWarnDate = dateStr
        }

        val dbUsedSeconds = usageDao.getUsage(packageName, dateStr)?.timeSpentSeconds ?: 0L
        val liveUsedSeconds = TimerManager.liveUsages.value[packageName] ?: 0L
        val usedSeconds = maxOf(dbUsedSeconds, liveUsedSeconds)
        val limitSeconds = monitoredApp.dailyLimitMinutes * 60L
        val remainingSeconds = limitSeconds - usedSeconds

        if (remainingSeconds in 1..300 && !warnedPackages.contains(packageName) && !isFocusActive) {
            warnedPackages.add(packageName)
            withContext(Dispatchers.Main) {
                showWarningNotification(monitoredApp.appName)
            }
        }

        var blockReason = "limit_reached"
        val shouldBlock = when {
            isFocusActive -> {
                blockReason = "focus_mode"
                true
            }
            usedSeconds >= limitSeconds -> {
                blockReason = "limit_reached"
                true
            }
            else -> false
        }

        if (shouldBlock) {
            withContext(Dispatchers.IO) {
                blockDao.insert(
                    com.example.scrollproject.data.local.BlockEventEntity(
                        packageName = packageName,
                        reason = blockReason
                    )
                )
            }
            withContext(Dispatchers.Main) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockNotification(monitoredApp.appName, blockReason)
                val intent = Intent(this@ScrollGuardAccessibilityService, BlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(BlockActivity.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(BlockActivity.EXTRA_APP_NAME, monitoredApp.appName)
                    putExtra(BlockActivity.EXTRA_REASON, blockReason)
                    putExtra(BlockActivity.EXTRA_LIMIT_MINUTES, monitoredApp.dailyLimitMinutes)
                }
                startActivity(intent)
                TimerManager.setActivePackage("") 
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val blockChannel = NotificationChannel(
                "block_alerts",
                "App Blocking Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val warningChannel = NotificationChannel(
                "warning_alerts",
                "Time Limit Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(blockChannel)
            manager.createNotificationChannel(warningChannel)
        }
    }

    private fun showWarningNotification(appName: String) {
        val notification = NotificationCompat.Builder(this, "warning_alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("5 Minutes Remaining")
            .setContentText("You have 5 minutes left for $appName today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify("${appName}_warning".hashCode(), notification)
    }

    private fun showBlockNotification(appName: String, reason: String) {
        val content = when(reason) {
            "focus_mode" -> "$appName is blocked during Focus Mode."
            else -> "$appName has been blocked for the rest of the day."
        }
        val notification = NotificationCompat.Builder(this, "block_alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Access Restricted")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(appName.hashCode(), notification)
    }

    override fun onInterrupt() {
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
