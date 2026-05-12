package com.example.scrollproject.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scrollproject.domain.model.MonitoredApp
import com.example.scrollproject.ui.appselection.toBitmap

@Composable
fun MonitoredAppsList(
    apps: List<MonitoredApp>,
    usageMap: Map<String, Long>,
    onToggleBlock: (String, Boolean) -> Unit,
    onEditLimit: (MonitoredApp) -> Unit,
    onRemove: (String) -> Unit
) {
    if (apps.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No monitored apps yet. Add one!", color = Color.Gray)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            apps.forEach { app ->
                val usedSeconds = usageMap[app.packageName] ?: 0L
                val remainingSeconds = (app.dailyLimitMinutes * 60L) - usedSeconds
                
                MonitoredAppItem(
                    app = app,
                    remainingSeconds = remainingSeconds,
                    onToggleBlock = { onToggleBlock(app.packageName, it) },
                    onEditLimit = { onEditLimit(app) },
                    onRemove = { onRemove(app.packageName) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun MonitoredAppItem(
    app: MonitoredApp,
    remainingSeconds: Long,
    onToggleBlock: (Boolean) -> Unit,
    onEditLimit: () -> Unit,
    onRemove: () -> Unit
) {
    val isBlocked = remainingSeconds <= 0 && app.isBlockingEnabled
    val cardColor = if (isBlocked) Color(0xFF3B1E1E) else Color(0xFF1E1E24)
    val timerColor = if (isBlocked) Color(0xFFFF5252) else if (remainingSeconds < 300) Color(0xFFFFAB40) else Color(0xFF00E5FF)
    
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bitmap = remember(app.icon) { app.icon?.toBitmap() }
                Box(contentAlignment = Alignment.Center) {
                    // Small circular progress around the icon
                    val progress = 1f - (remainingSeconds.toFloat() / (app.dailyLimitMinutes * 60f)).coerceIn(0f, 1f)
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(56.dp),
                        color = timerColor,
                        strokeWidth = 3.dp,
                        trackColor = timerColor.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )
                    
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = app.appName,
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                    } else {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.DarkGray))
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName, 
                        color = Color.White, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "DAILY LIMIT: ${app.dailyLimitMinutes}M", 
                        color = Color.Gray, 
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Switch(
                    checked = app.isBlockingEnabled,
                    onCheckedChange = onToggleBlock,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TIME REMAINING", 
                        color = Color.Gray, 
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = formatTime(Math.max(0, remainingSeconds)),
                        color = timerColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Row {
                    IconButton(
                        onClick = onEditLimit,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove App", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
