package com.example.scrollproject

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.scrollproject.services.MonitoringWorker
import com.example.scrollproject.services.UsageMonitorService
import com.example.scrollproject.ui.appselection.AppSelectionActivity
import com.example.scrollproject.ui.compose.DashboardScreen
import com.example.scrollproject.ui.dialog.TimeLimitDialog
import com.example.scrollproject.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DashboardScreen(
                viewModel = viewModel,
                onAddApp = {
                    if (!hasUsageStatsPermission()) {
                        requestUsageStatsPermission()
                    } else {
                        startActivity(Intent(this, AppSelectionActivity::class.java))
                    }
                },
                onEditLimit = { app ->
                    TimeLimitDialog(this, app) { minutes ->
                        viewModel.updateLimit(app.packageName, minutes)
                    }.show()
                }
            )
        }

        observeViewModel()
        requestPermissionsIfNeeded()
        startMonitoringService()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.snackbarMessage.collect { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startMonitoringService() {
        if (!hasUsageStatsPermission()) return
        val intent = Intent(this, UsageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        MonitoringWorker.schedule(this)
    }

    private fun requestPermissionsIfNeeded() {
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access required for screen time tracking", Toast.LENGTH_LONG).show()
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required for blocking screen", Toast.LENGTH_LONG).show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        requestIgnoreBatteryOptimizations()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        // Re-attempt start if permission was just granted
        startMonitoringService()
    }
}
