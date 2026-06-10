package com.example.scrollproject.ui.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.scrollproject.domain.model.MonitoredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared composable: renders an app icon from a [MonitoredApp.icon] Drawable,
 * or falls back to a coloured placeholder box.
 *
 * Bitmap conversion happens off the main thread to avoid jank on first render.
 */
@Composable
fun AppIconView(app: MonitoredApp, size: Int = 48) {
    val sizeDp: Dp = size.dp
    var bitmap by remember(app.packageName) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    // Load from the pre-fetched Drawable, or fall back to PackageManager.
    LaunchedEffect(app.packageName) {
        bitmap = withContext(Dispatchers.IO) {
            val drawable: Drawable? = app.icon ?: try {
                context.packageManager.getApplicationIcon(app.packageName)
            } catch (_: Exception) { null }
            drawable?.safeToBitmap()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = app.appName,
            modifier = Modifier
                .size(sizeDp)
                .clip(RoundedCornerShape((size * 0.22f).dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(RoundedCornerShape((size * 0.22f).dp))
                .background(Color(0xFF2A2A3E))
        )
    }
}

// ─── Drawable → Bitmap helper ─────────────────────────────────────────────────

fun Drawable.safeToBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = if (intrinsicWidth > 0) intrinsicWidth else 64
    val h = if (intrinsicHeight > 0) intrinsicHeight else 64
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
