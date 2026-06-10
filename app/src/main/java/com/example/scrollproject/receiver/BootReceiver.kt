package com.example.scrollproject.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver — intentionally a no-op.
 *
 * The countdown timer is a user-initiated session that starts from zero on
 * each use. There is nothing to restore on device boot, so this receiver
 * is kept only to satisfy the manifest entry and avoid class-not-found errors.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op: countdown sessions do not survive reboots by design.
    }
}
