package com.example.scrollproject.services

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class AppUsageMonitorService : Service() {

    private var isMonitoring = false
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.d("ScrollGuard", "AppUsageMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            isMonitoring = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            
            while (isActive) {
                // Get the top activity package name
                val time = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 10,
                    time
                )
                
                val currentApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
                if (currentApp != null) {
                    Log.d("ScrollGuard", "Current Foreground App: $currentApp")
                    
                    // TODO: Cross-reference with monitored apps list.
                    // If limit is exceeded, fire a broadcast or trigger the Accessibility Service
                    // to show the blocking screen.
                }

                delay(1000) // Poll every second (or use Accessibility events for better efficiency)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
}
