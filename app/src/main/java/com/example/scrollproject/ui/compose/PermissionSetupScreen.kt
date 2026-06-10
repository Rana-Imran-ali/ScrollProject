package com.example.scrollproject.ui.compose

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.scrollproject.services.ScrollGuardAccessibilityService

private val BgDark        = Color(0xFF0D0D14)
private val SurfaceCard   = Color(0xFF161622)
private val BorderColor   = Color(0x1AFFFFFF)
private val CyanAccent    = Color(0xFF00E5FF)
private val RedAccent     = Color(0xFFFF5252)
private val GreenAccent   = Color(0xFF00E676)
private val TextPrimary   = Color(0xFFF0F0FF)
private val TextSecondary = Color(0xFF8888AA)

@Composable
fun PermissionSetupScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isUsageStatsGranted by remember { mutableStateOf(false) }

    fun checkAllPermissions() {
        isAccessibilityGranted = isAccessibilityServiceEnabled(context)
        isUsageStatsGranted = isUsageStatsPermissionGranted(context)
        Log.d("ScrollGuard", "PermissionSetupScreen checkAllPermissions: accessibility=$isAccessibilityGranted, usageStats=$isUsageStatsGranted")
    }

    // Monitor lifecycle events to automatically refresh permission status
    // when the user returns from settings screens.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("ScrollGuard", "PermissionSetupScreen ON_RESUME: re-checking permissions")
                checkAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check status immediately on initial composition
    LaunchedEffect(Unit) {
        checkAllPermissions()
    }

    // Auto-advance callback if both permissions are granted
    LaunchedEffect(isAccessibilityGranted, isUsageStatsGranted) {
        Log.d("ScrollGuard", "PermissionSetupScreen LaunchedEffect: accessibility=$isAccessibilityGranted, usageStats=$isUsageStatsGranted")
        if (isAccessibilityGranted && isUsageStatsGranted) {
            Log.d("ScrollGuard", "PermissionSetupScreen: both permissions granted, calling onPermissionsGranted()")
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // ── Logo & Title ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(CyanAccent.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Permission Setup",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scroll Guard requires permissions to track usage and restrict screen time.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Card 1: Usage Stats ──────────────────────────────────────────
            PermissionCard(
                title = "Usage Access Stats",
                description = "Enables detection of foreground apps to see when monitored apps are launched and evaluate active time limits.",
                isGranted = isUsageStatsGranted,
                onRequest = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            )

            // ── Card 2: Accessibility Service ─────────────────────────────────
            PermissionCard(
                title = "Accessibility Service",
                description = "Used to redirect you away from monitored apps immediately once your focus timer expires.",
                isGranted = isAccessibilityGranted,
                onRequest = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        // ── Continue Button ─────────────────────────────────────────────────
        Button(
            onClick = {
                checkAllPermissions()
                if (isAccessibilityGranted && isUsageStatsGranted) {
                    onPermissionsGranted()
                }
            },
            enabled = isAccessibilityGranted && isUsageStatsGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = Color.Black,
                disabledContainerColor = SurfaceCard,
                disabledContentColor = TextSecondary.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "Continue to Dashboard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) GreenAccent else RedAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isGranted) "Granted" else "Missing",
                        color = if (isGranted) GreenAccent else RedAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (!isGranted) {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent.copy(alpha = 0.1f),
                        contentColor = CyanAccent
                    )
                ) {
                    Text(
                        text = "Grant Permission",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    var enabled = 0
    try {
        enabled = Settings.Secure.getInt(
            context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (_: Settings.SettingNotFoundException) {}

    if (enabled == 1) {
        val services = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = android.content.ComponentName(
            context.packageName,
            ScrollGuardAccessibilityService::class.java.name
        ).flattenToString()
        return services.contains(component, ignoreCase = true)
    }
    return false
}

private fun isUsageStatsPermissionGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
