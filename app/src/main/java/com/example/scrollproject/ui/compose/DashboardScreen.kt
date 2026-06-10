package com.example.scrollproject.ui.compose

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay

// ─── Color palette ────────────────────────────────────────────────────────────
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

    LaunchedEffect(Unit) {
        viewModel.loadMonitoredApps()
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Scroll Guard", fontWeight = FontWeight.Bold,
                        color = TextPrimary, fontSize = 22.sp)
                },
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
            // Accessibility warning (only when needed)
            if (!state.isAccessibilityEnabled) {
                AccessibilityWarningCard(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }

            // Main countdown ring — carries its own "finished" linger state
            CountdownRingCard(
                remainingSeconds = state.remainingSeconds,
                totalSeconds     = state.totalSeconds,
                isRunning        = state.isRunning,
                appName          = state.selectedApp?.appName
            )

            // App selector
            AppSelectorRow(
                apps        = state.monitoredApps,
                selectedApp = state.selectedApp,
                isRunning   = state.isRunning,
                onSelect    = { viewModel.selectFromMonitored(it) },
                onAddApp    = onAddApp,
                onRemove    = { viewModel.removeApp(it) }
            )

            // Duration input + presets
            SecondsInputCard(
                seconds         = state.countdownSeconds,
                isRunning       = state.isRunning,
                onSecondsChange = { viewModel.setCountdownSeconds(it) }
            )

            // Primary action button
            StartStopButton(
                isRunning   = state.isRunning,
                selectedApp = state.selectedApp,
                onStart     = { viewModel.startCountdown(context) },
                onStop      = { viewModel.stopCountdown(context) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Accessibility warning card ───────────────────────────────────────────────

@Composable
private fun AccessibilityWarningCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        colors  = CardDefaults.cardColors(containerColor = Color(0xFF2A1A0E)),
        border  = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = Amber, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Accessibility Service Disabled",
                    color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to enable — required to close the monitored app automatically.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

// ─── Countdown ring ───────────────────────────────────────────────────────────

/**
 * The ring card manages its own "TIME FINISHED" linger state so the label
 * stays visible for [FINISH_LINGER_MS] after the timer expires, regardless of
 * how quickly TimerManager resets its own fields.
 */
@Composable
private fun CountdownRingCard(
    remainingSeconds: Long,
    totalSeconds:     Long,
    isRunning:        Boolean,
    appName:          String?
) {
    // ── Linger logic ─────────────────────────────────────────────────────────
    // "Finished" = timer ran (totalSeconds > 0) and just hit 0.
    // We keep this true for FINISH_LINGER_MS even after TimerManager resets.
    val FINISH_LINGER_MS = 3_000L
    var showFinished by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingSeconds, totalSeconds) {
        if (!isRunning && remainingSeconds == 0L && totalSeconds > 0L) {
            showFinished = true
            delay(FINISH_LINGER_MS)
            showFinished = false
        } else if (isRunning) {
            showFinished = false
        }
    }

    // ── Progress & colour ─────────────────────────────────────────────────────
    val progressRaw = when {
        showFinished           -> 0f
        totalSeconds > 0L      -> (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        else                   -> 1f          // idle — full ring
    }
    val progress by animateFloatAsState(
        targetValue  = progressRaw,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label        = "ring_progress"
    )

    val ringColor = when {
        showFinished           -> Red
        !isRunning             -> Cyan
        progressRaw < 0.15f    -> Red
        progressRaw < 0.35f    -> Amber
        else                   -> Cyan
    }

    // ── Pulse glow (active only while running) ────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.50f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glow"
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    Card(
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning || showFinished)
                            ringColor.copy(alpha = glowAlpha * 0.08f)
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress  = progress,
                    modifier  = Modifier.size(200.dp),
                    color     = ringColor,
                    trackColor = Color.White.copy(alpha = 0.06f),
                    strokeWidth = 14.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Time label
                    val displaySeconds = if (showFinished) 0L else remainingSeconds
                    val h = displaySeconds / 3600
                    val m = (displaySeconds % 3600) / 60
                    val s = displaySeconds % 60
                    val timeText = when {
                        showFinished                    -> "00:00"
                        !isRunning && totalSeconds == 0L -> "--:--"
                        h > 0                           -> "%02d:%02d:%02d".format(h, m, s)
                        else                            -> "%02d:%02d".format(m, s)
                    }
                    Text(
                        text     = timeText,
                        color    = if (showFinished) Red else TextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    // Status label
                    val statusText = when {
                        showFinished -> "TIME FINISHED"
                        isRunning    -> (appName ?: "MONITORING").uppercase()
                        else         -> "READY"
                    }
                    Text(
                        text     = statusText,
                        color    = ringColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// ─── App selector row ─────────────────────────────────────────────────────────

@Composable
private fun AppSelectorRow(
    apps:        List<MonitoredApp>,
    selectedApp: MonitoredApp?,
    isRunning:   Boolean,
    onSelect:    (MonitoredApp) -> Unit,
    onAddApp:    () -> Unit,
    onRemove:    (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Select App to Monitor",
            color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

        if (apps.isEmpty()) {
            Card(
                onClick  = onAddApp,
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface2),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Add an app to monitor", color = Cyan, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { app ->
                    val isSelected = app.packageName == selectedApp?.packageName
                    Card(
                        onClick  = { if (!isRunning) onSelect(app) },
                        shape    = RoundedCornerShape(14.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = if (isSelected) Cyan.copy(alpha = 0.12f) else Surface2
                        ),
                        border   = if (isSelected)
                            androidx.compose.foundation.BorderStroke(1.5.dp, Cyan) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier          = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconView(app = app, size = 40)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text       = app.appName,
                                color      = if (isSelected) Cyan else TextPrimary,
                                fontSize   = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier   = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isSelected) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Cyan))
                                }
                                IconButton(
                                    onClick = { onRemove(app.packageName) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove monitored app",
                                        tint = Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Seconds input card ───────────────────────────────────────────────────────

@Composable
private fun SecondsInputCard(
    seconds:         Long,
    isRunning:       Boolean,
    onSecondsChange: (Long) -> Unit
) {
    // Use a separate mutable state for the raw text so the field stays
    // responsive while typing. Sync from the outside (e.g. post-expiry ViewModel
    // reset) via LaunchedEffect rather than remember(seconds) to avoid
    // recomposing the entire card on every timer tick.
    var textValue by remember { mutableStateOf(seconds.toString()) }
    LaunchedEffect(seconds) {
        // Only overwrite if the external value genuinely changed (e.g. expiry reset),
        // not if the user is mid-type with the same numeric value.
        val external = seconds.toString()
        if (external != textValue && !isRunning) {
            textValue = external
        }
    }

    val presets = listOf(30L to "30s", 60L to "1m", 300L to "5m",
                         600L to "10m", 1800L to "30m", 3600L to "1h")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Countdown Duration",
            color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

        Card(
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface1),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier              = Modifier.padding(16.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value     = textValue,
                    onValueChange = { v ->
                        textValue = v.filter { it.isDigit() }.take(6)
                        val parsed = textValue.toLongOrNull() ?: 0L
                        if (parsed > 0L) onSecondsChange(parsed)
                    },
                    enabled   = !isRunning,
                    modifier  = Modifier.fillMaxWidth(),
                    label     = { Text("Seconds", color = TextSecondary) },
                    suffix    = { Text("sec", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape     = RoundedCornerShape(12.dp),
                    colors    = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Cyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        disabledTextColor    = TextSecondary,
                        disabledBorderColor  = Color.White.copy(alpha = 0.08f),
                        cursorColor          = Cyan
                    )
                )

                // Horizontally scrollable preset chips — prevents overflow on small screens
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (value, label) ->
                        val isActive = seconds == value
                        FilterChip(
                            selected = isActive,
                            onClick  = {
                                if (!isRunning) {
                                    textValue = value.toString()
                                    onSecondsChange(value)
                                }
                            },
                            label    = {
                                Text(label, fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan.copy(alpha = 0.2f),
                                selectedLabelColor     = Cyan,
                                containerColor         = Surface2,
                                labelColor             = TextSecondary
                            ),
                            border   = FilterChipDefaults.filterChipBorder(
                                enabled             = true,
                                selected            = isActive,
                                selectedBorderColor = Cyan.copy(alpha = 0.5f),
                                borderColor         = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── Start / Stop button ──────────────────────────────────────────────────────

@Composable
private fun StartStopButton(
    isRunning:   Boolean,
    selectedApp: MonitoredApp?,
    onStart:     () -> Unit,
    onStop:      () -> Unit
) {
    val enabled = selectedApp != null || isRunning

    Button(
        onClick  = { if (isRunning) onStop() else onStart() },
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape    = RoundedCornerShape(18.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = if (isRunning) Red else Cyan,
            contentColor           = if (isRunning) Color.White else Color(0xFF0D0D14),
            disabledContainerColor = Surface2,
            disabledContentColor   = TextSecondary
        )
    ) {
        Icon(
            imageVector    = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier       = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text       = if (isRunning) "Stop Monitoring" else "Start Monitoring",
            fontSize   = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
