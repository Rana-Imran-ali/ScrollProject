package com.example.scrollproject

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.scrollproject.ui.appselection.AppSelectionScreen
import com.example.scrollproject.ui.compose.DashboardScreen
import com.example.scrollproject.ui.compose.PermissionSetupScreen
import com.example.scrollproject.ui.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

enum class Screen {
    PermissionSetup,
    Dashboard,
    AppSelection
}

private val BgDark        = Color(0xFF0D0D14)
private val Surface2      = Color(0xFF1E1E2E)
private val Cyan          = Color(0xFF00E5FF)
private val TextPrimary   = Color(0xFFF0F0FF)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface    = Surface2,
                    primary    = Cyan,
                    onPrimary  = Color.Black,
                    onSurface  = TextPrimary
                )
            ) {
                val hasAllPermissions = isAccessibilityServiceEnabled() && isUsageStatsPermissionGranted()
                Log.d("ScrollGuard", "MainActivity content composition: hasAllPermissions=$hasAllPermissions")
                var currentScreen by remember {
                    mutableStateOf(
                        if (hasAllPermissions) Screen.Dashboard else Screen.PermissionSetup
                    )
                }

                // Check again if permissions get enabled outside the app and user returns
                LaunchedEffect(currentScreen) {
                    Log.d("ScrollGuard", "MainActivity LaunchedEffect checking currentScreen=$currentScreen")
                    if (currentScreen == Screen.PermissionSetup &&
                        isAccessibilityServiceEnabled() &&
                        isUsageStatsPermissionGranted()
                    ) {
                        Log.d("ScrollGuard", "Permissions detected as granted. Transitioning to Screen.Dashboard")
                        currentScreen = Screen.Dashboard
                    }
                }

                when (currentScreen) {
                    Screen.PermissionSetup -> {
                        PermissionSetupScreen(
                            onPermissionsGranted = {
                                currentScreen = Screen.Dashboard
                            }
                        )
                    }
                    Screen.Dashboard -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onAddApp  = { currentScreen = Screen.AppSelection }
                        )
                    }
                    Screen.AppSelection -> {
                        AppSelectionScreen(
                            viewModel = viewModel,
                            onBack    = { currentScreen = Screen.Dashboard },
                            onSelect  = { pkg, seconds ->
                                viewModel.selectApp(pkg, seconds)
                                currentScreen = Screen.Dashboard
                            }
                        )
                    }
                }
            }
        }

        observeSnackbar()
        requestPostNotificationsIfNeeded()

        // Always start the foreground service to keep the process alive.
        // This preserves the Accessibility Service connection and granted permissions
        // even when no apps are currently being monitored.
        val serviceIntent = Intent(this, com.example.scrollproject.services.CountdownService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Ignore — service may already be running
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the accessibility status badge every time the user returns
        viewModel.refreshAccessibilityStatus()
    }

    // ─── Snackbar observer ────────────────────────────────────────────────────

    private fun observeSnackbar() {
        lifecycleScope.launch {
            viewModel.snackbar.collect { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Permission helpers ───────────────────────────────────────────────────

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        var enabled = 0
        try {
            enabled = Settings.Secure.getInt(
                contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (_: Settings.SettingNotFoundException) {}
        Log.d("ScrollGuard", "isAccessibilityServiceEnabled: Secure.ACCESSIBILITY_ENABLED=$enabled")

        if (enabled == 1) {
            val services = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d("ScrollGuard", "isAccessibilityServiceEnabled: Secure.ENABLED_ACCESSIBILITY_SERVICES=$services")
            if (services == null) return false
            val component = android.content.ComponentName(
                packageName,
                com.example.scrollproject.services.ScrollGuardAccessibilityService::class.java.name
            ).flattenToString()
            val isEnabled = services.contains(component, ignoreCase = true)
            Log.d("ScrollGuard", "isAccessibilityServiceEnabled: target=$component, isEnabled=$isEnabled")
            return isEnabled
        }
        return false
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        val isGranted = mode == AppOpsManager.MODE_ALLOWED
        Log.d("ScrollGuard", "isUsageStatsPermissionGranted: mode=$mode, isGranted=$isGranted")
        return isGranted
    }
}
