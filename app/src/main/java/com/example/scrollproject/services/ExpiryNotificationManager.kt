package com.example.scrollproject.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.scrollproject.MainActivity
import com.example.scrollproject.R

/**
 * ExpiryNotificationManager — posts the "Time is finished" alert notification.
 *
 * This is a separate notification from the ongoing countdown foreground notification:
 *  • The foreground notification is IMPORTANCE_LOW (silent, no heads-up).
 *  • The expiry notification is IMPORTANCE_HIGH (heads-up, appears over any app).
 *
 * Compatible with Android 12–15:
 *  • Uses FLAG_IMMUTABLE on PendingIntent (required API 31+).
 *  • Notification channel created before posting (required API 26+).
 *  • POST_NOTIFICATIONS permission declared in manifest (required API 33+).
 *  • No background activity start — notification only, no activity launch on post.
 */
object ExpiryNotificationManager {

    const val CHANNEL_ID = "expiry_channel"
    const val NOTIF_ID   = 3001

    /**
     * Shows a high-priority heads-up notification reading "Time is finished."
     * Safe to call from any thread.
     *
     * @param context     Application context.
     * @param appName     Human-readable name of the monitored app.
     */
    fun notify(context: Context, appName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        nm.notify(NOTIF_ID, buildNotification(context, appName))
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Scroll Guard Alerts",
                NotificationManager.IMPORTANCE_HIGH   // Heads-up, shown over other apps
            ).apply {
                description    = "Time-limit expiry alerts"
                setShowBadge(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(context: Context, appName: String): Notification {
        // Tapping the notification opens the dashboard.
        val tapPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Time is finished.")
            .setContentText("$appName has been closed. Your time limit has been reached.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$appName has been closed. Your time limit has been reached."))
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
