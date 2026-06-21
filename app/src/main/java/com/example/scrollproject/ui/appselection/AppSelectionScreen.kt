package com.example.scrollproject.ui.appselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.scrollproject.domain.model.AppInfo
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.ui.compose.AppIconView
import com.example.scrollproject.ui.viewmodel.DashboardViewModel

private val BgDark        = Color(0xFF0D0D14)
private val Surface2      = Color(0xFF1E1E2E)
private val Cyan          = Color(0xFF00E5FF)
private val TextPrimary   = Color(0xFFF0F0FF)
private val TextSecondary = Color(0xFF8888AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onSelect: (String, Long) -> Unit
) {
    val apps by viewModel.filteredApps.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val state by viewModel.state.collectAsState()
    val isLoading = state.isLoadingApps

    var showTimeInputDialog by remember { mutableStateOf(false) }
    var selectedAppForLimit by remember { mutableStateOf<AppInfo?>(null) }

    if (showTimeInputDialog && selectedAppForLimit != null) {
        val app = selectedAppForLimit!!
        var textValue by remember { mutableStateOf("60") }
        AlertDialog(
            onDismissRequest = { showTimeInputDialog = false },
            title = {
                Text(
                    text = "Set Daily Limit",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Enter the daily limit for ${app.appName} in seconds.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { v ->
                            textValue = v.filter { it.isDigit() }.take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seconds", color = TextSecondary) },
                        suffix = { Text("sec", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Cyan
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30L to "30s", 60L to "1m", 300L to "5m", 600L to "10m").forEach { (value, label) ->
                            FilterChip(
                                selected = textValue == value.toString(),
                                onClick = { textValue = value.toString() },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Cyan.copy(alpha = 0.2f),
                                    selectedLabelColor = Cyan,
                                    containerColor = Surface2,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val seconds = textValue.toLongOrNull() ?: 0L
                        if (seconds > 0L) {
                            onSelect(app.packageName, seconds)
                            showTimeInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeInputDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Surface2,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Fetch the list of apps when entering the screen
    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Select App", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps…", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Cyan
                )
            )

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan)
                }
                apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps found", color = TextSecondary, fontSize = 16.sp)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppPickerItem(
                            app = app,
                            onClick = {
                                selectedAppForLimit = app
                                showTimeInputDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerItem(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconView(
            app = MonitoredApp(packageName = app.packageName, appName = app.appName),
            size = 48
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = app.packageName,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.05f)
    )
}
