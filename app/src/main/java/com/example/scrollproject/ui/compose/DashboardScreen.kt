package com.example.scrollproject.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scrollproject.R
import com.example.scrollproject.ui.viewmodel.DashboardViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddApp: () -> Unit,
    onEditLimit: (com.example.scrollproject.domain.model.MonitoredApp) -> Unit
) {
    val state by viewModel.dashboardState.collectAsState()
    var selectedPackageName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.monitoredApps) {
        if (selectedPackageName == null || state.monitoredApps.none { it.packageName == selectedPackageName }) {
            selectedPackageName = state.monitoredApps.firstOrNull()?.packageName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApp,
                containerColor = Color(0xFF00E5FF),
                contentColor = Color.Black,
                icon = { Icon(Icons.Default.Add, "Add App") },
                text = { Text("Add App", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val selectedApp = state.monitoredApps.find { it.packageName == selectedPackageName }
            val isInfinite = selectedApp?.dailyLimitMinutes == Int.MAX_VALUE
            
            val usedSeconds = selectedApp?.let { state.usageMap[it.packageName] ?: 0L } ?: 0L
            val limitSeconds = selectedApp?.let { 
                if (isInfinite) Long.MAX_VALUE else it.dailyLimitMinutes * 60L 
            } ?: 0L
            
            val remainingSeconds = if (selectedApp == null) {
                0L
            } else if (isInfinite) {
                Long.MAX_VALUE
            } else {
                (limitSeconds - usedSeconds).coerceAtLeast(0L)
            }
            
            val progressRaw = if (selectedApp == null) {
                0f
            } else if (isInfinite) {
                1f
            } else if (limitSeconds > 0) {
                remainingSeconds.toFloat() / limitSeconds.toFloat()
            } else {
                0f
            }
            
            val clampedProgress = progressRaw.coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = clampedProgress,
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                label = "progress"
            )

            // Circular Progress Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner Glow/Shadow effect using Box
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.03f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(180.dp),
                            color = if (clampedProgress < 0.1f && !isInfinite) Color(0xFFFF5252) else Color(0xFF00E5FF),
                            strokeWidth = 14.dp,
                            trackColor = Color.White.copy(alpha = 0.05f),
                            strokeCap = StrokeCap.Round
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val timeText = if (selectedApp == null) {
                                "00:00"
                            } else if (isInfinite) {
                                "Unlimited"
                            } else {
                                val remHours = remainingSeconds / 3600
                                val remMins = (remainingSeconds % 3600) / 60
                                val remSecs = remainingSeconds % 60
                                String.format("%02d:%02d:%02d", remHours, remMins, remSecs)
                            }

                            Text(
                                text = timeText,
                                color = Color.White,
                                fontSize = if (isInfinite) 26.sp else 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (selectedApp == null) "NO APP SELECTED" else selectedApp.appName.uppercase(),
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                // Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(
                        label = "TIME USED", 
                        value = formatDashboardTime(usedSeconds),
                        iconId = R.drawable.ic_shield
                    )
                    
                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )
                    
                    StatItem(
                        label = "DAILY BUDGET", 
                        value = if (selectedApp == null) "0m" else if (isInfinite) "Unlimited" else "${selectedApp.dailyLimitMinutes}m",
                        iconId = R.drawable.ic_shield
                    )
                }
            }

            // Focus Mode Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { viewModel.toggleFocusMode(!state.isFocusModeActive) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB388FF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield),
                            contentDescription = "Focus",
                            tint = Color(0xFFB388FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus Mode",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.isFocusModeActive) "All monitored apps blocked" else "Block all tracked apps",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    Switch(
                        checked = state.isFocusModeActive,
                        onCheckedChange = { viewModel.toggleFocusMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFB388FF),
                            checkedTrackColor = Color(0xFFB388FF).copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Text(
                text = "Monitored Apps",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Compose Monitored Apps
            MonitoredAppsList(
                apps = state.monitoredApps,
                usageMap = state.usageMap,
                selectedPackageName = selectedPackageName,
                onSelectApp = { selectedPackageName = it },
                onToggleBlock = { pkg, enabled -> viewModel.toggleBlocking(pkg, enabled) },
                onEditLimit = onEditLimit,
                onRemove = { pkg -> viewModel.removeApp(pkg) }
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun formatDashboardTime(seconds: Long): String {
    val mins = TimeUnit.SECONDS.toMinutes(seconds)
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun StatItem(label: String, value: String, iconId: Int? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconId != null) {
                Icon(
                    painter = painterResource(id = iconId),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
