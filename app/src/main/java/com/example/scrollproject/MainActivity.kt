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

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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
            android.app.AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage("Scroll Guard needs Usage Access to track your screen time. Please enable it in the next screen.")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setCancelable(false)
                .show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To block apps when your time limit is reached, Scroll Guard needs permission to display over other apps.")
                .setPositiveButton("Enable") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage("To detect when you open restricted apps instantly, please enable the Scroll Guard Accessibility Service.")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setCancelable(false)
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        requestIgnoreBatteryOptimizations()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            // Ignore
        }
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val service = android.content.ComponentName(packageName, com.example.scrollproject.services.ScrollGuardAccessibilityService::class.java.name).flattenToString()
                return settingValue.contains(service)
            }
        }
        return false
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
