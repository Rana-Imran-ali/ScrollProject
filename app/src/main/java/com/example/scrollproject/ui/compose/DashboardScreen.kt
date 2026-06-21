package com.example.scrollproject.ui.compose

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.ui.viewmodel.DashboardViewModel

// ─── Palette ──────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D0D14)
private val Surface1      = Color(0xFF16161F)
private val Surface2      = Color(0xFF1E1E2E)
private val Cyan          = Color(0xFF00E5FF)
private val Red           = Color(0xFFFF5252)
private val Amber         = Color(0xFFFFAB40)
private val GreenAccent   = Color(0xFF00E676)
private val TextPrimary   = Color(0xFFF0F0FF)
private val TextSecondary = Color(0xFF8888AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddApp: () -> Unit
) {
    val context = LocalContext.current
    val state   by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(Unit) { viewModel.loadMonitoredApps() }

    // Auto-navigate to App Selection when no apps are monitored
    LaunchedEffect(state.isMonitoredAppsLoaded, state.monitoredApps) {
        if (state.isMonitoredAppsLoaded && state.monitoredApps.isEmpty() && !viewModel.hasAutoNavigatedToSelection) {
            viewModel.hasAutoNavigatedToSelection = true
            onAddApp()
        }
    }

    // ── Edit-limit dialog ─────────────────────────────────────────────────────
    var editApp by remember { mutableStateOf<MonitoredApp?>(null) }
    editApp?.let { app ->
        var text by remember(app.packageName) { mutableStateOf(app.limitSeconds.toString()) }
        AlertDialog(
            onDismissRequest = { editApp = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIconView(app = app, size = 30)
                    Text(app.appName, fontWeight = FontWeight.Bold,
                        color = TextPrimary, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Update the daily limit for ${app.appName}.",
                        color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label    = { Text("Seconds", color = TextSecondary) },
                        suffix   = { Text("sec",     color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Cyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = Cyan
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30L to "30s", 60L to "1m", 300L to "5m",
                               600L to "10m", 1800L to "30m", 3600L to "1h")
                            .forEach { (v, lbl) ->
                                FilterChip(
                                    selected = text == v.toString(),
                                    onClick  = { text = v.toString() },
                                    label    = { Text(lbl, fontSize = 11.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Cyan.copy(alpha = 0.2f),
                                        selectedLabelColor     = Cyan,
                                        containerColor         = Surface2,
                                        labelColor             = TextSecondary
                                    )
                                )
                            }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val secs = text.toLongOrNull() ?: 0L
                        if (secs > 0L) { viewModel.saveAppLimit(app, secs); editApp = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editApp = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Surface1,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Scroll Guard", fontWeight = FontWeight.Bold,
                    color = TextPrimary, fontSize = 22.sp) },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = BgDark),
                actions = {
                    IconButton(onClick = onAddApp) {
                        Icon(Icons.Default.Add, contentDescription = "Add App", tint = Cyan)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!state.isAccessibilityEnabled) {
                AccessibilityWarningCard {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }

            // Apple-inspired total time ring
            TotalTimeRingCard(apps = state.monitoredApps)

            // Monitored apps list
            MonitoredAppsSection(
                apps     = state.monitoredApps,
                onEdit   = { editApp = it },
                onRemove = { viewModel.removeApp(it) },
                onAddApp = onAddApp
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Accessibility warning ────────────────────────────────────────────────────

@Composable
private fun AccessibilityWarningCard(onClick: () -> Unit) {
    Card(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF2A1A0E)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Warning, null, tint = Amber, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Accessibility Service Disabled",
                    color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to enable — required to close monitored apps automatically.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

// ─── Apple-inspired Total Time Ring ──────────────────────────────────────────

@Composable
private fun TotalTimeRingCard(apps: List<MonitoredApp>) {
    val totalRemaining = apps.sumOf { it.remainingSeconds }
    val totalLimit     = apps.sumOf { it.limitSeconds }.coerceAtLeast(1L)
    val progress       = (totalRemaining.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f)
    val usedToday      = apps.sumOf { it.usedSeconds }

    val animProg by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "total_ring"
    )
    val infinite = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infinite.animateFloat(
        initialValue  = 0.06f,
        targetValue   = 0.20f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label         = "glow_alpha"
    )
    val ringColor = when {
        apps.isEmpty()  -> Cyan
        progress < 0.15f -> Red
        progress < 0.35f -> Amber
        else             -> Cyan
    }

    // Format helpers
    fun fmtTime(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%02d:%02d".format(m, s)
    }
    val timeText   = if (apps.isEmpty()) "--:--" else fmtTime(totalRemaining)
    val usedText   = if (apps.isEmpty()) "" else {
        val uh = usedToday / 3600; val um = (usedToday % 3600) / 60
        if (uh > 0) "${uh}h ${um}m used today" else "${um}m used today"
    }

    Card(
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Daily Screen Time", color = TextSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {

                // Soft radial glow behind ring
                Box(modifier = Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(ringColor.copy(alpha = glowAlpha), Color.Transparent)
                        )
                    )
                )

                // Background track
                CircularProgressIndicator(
                    progress  = { 1f },
                    modifier  = Modifier.size(210.dp),
                    color     = Color.White.copy(alpha = 0.05f),
                    trackColor = Color.Transparent,
                    strokeWidth = 18.dp,
                    strokeCap = StrokeCap.Round
                )

                // Active progress arc
                CircularProgressIndicator(
                    progress  = { animProg },
                    modifier  = Modifier.size(210.dp),
                    color     = ringColor,
                    trackColor = Color.Transparent,
                    strokeWidth = 18.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = timeText, color = TextPrimary, fontSize = 42.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
                        textAlign = TextAlign.Center)
                    Text(text = "remaining", color = ringColor, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    if (usedText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = usedText, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Stats row: apps count + blocked count
            if (apps.isNotEmpty()) {
                val blockedCount = apps.count { it.isBlocked }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip(label = "Tracking", value = "${apps.size}", color = Cyan)
                    StatChip(label = "Blocked",  value = "$blockedCount", color = if (blockedCount > 0) Red else TextSecondary)
                    StatChip(label = "Limit",    value = fmtTime(totalLimit), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 10.sp, letterSpacing = 0.5.sp)
    }
}

// ─── Monitored apps section ───────────────────────────────────────────────────

@Composable
private fun MonitoredAppsSection(
    apps:     List<MonitoredApp>,
    onEdit:   (MonitoredApp) -> Unit,
    onRemove: (String) -> Unit,
    onAddApp: () -> Unit
) {
    val context = LocalContext.current
    fun fmt(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%02d:%02d".format(m, s)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Monitored Apps", color = TextSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text("${apps.size} app${if (apps.size != 1) "s" else ""}",
                color = TextSecondary, fontSize = 12.sp)
        }

        if (apps.isEmpty()) {
            Card(
                onClick  = onAddApp,
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface2),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Add an app to monitor", color = Cyan, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { app ->
                    val prog = (app.remainingSeconds.toFloat() /
                            app.limitSeconds.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    val barColor = when {
                        app.isBlocked  -> Red
                        prog < 0.15f   -> Red
                        prog < 0.35f   -> Amber
                        else           -> GreenAccent
                    }
                    val animProg by animateFloatAsState(
                        targetValue   = prog,
                        animationSpec = tween(600),
                        label         = "bar_${app.packageName}"
                    )

                    Card(
                        onClick = {
                            if (app.isBlocked) {
                                Toast.makeText(context, "${app.appName} is blocked (limit reached)", Toast.LENGTH_SHORT).show()
                            } else {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    Toast.makeText(context, "Cannot open ${app.appName}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape    = RoundedCornerShape(18.dp),
                        colors   = CardDefaults.cardColors(containerColor = Surface2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)) {

                            // ── Top row: icon + name + actions ────────────────
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppIconView(app = app, size = 42)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, color = TextPrimary, fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold)
                                    val status = if (app.isBlocked) "Blocked"
                                    else "${fmt(app.remainingSeconds)} left of ${fmt(app.limitSeconds)}"
                                    Text(status, color = barColor, fontSize = 11.sp)
                                }
                                // Edit + Delete side-by-side
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick  = { onEdit(app) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit limit",
                                            tint = Cyan, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick  = { onRemove(app.packageName) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove",
                                            tint = Red.copy(alpha = 0.75f),
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // ── Progress bar ──────────────────────────────────
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animProg)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(barColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
