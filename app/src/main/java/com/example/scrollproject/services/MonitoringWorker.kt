package com.example.scrollproject.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * MonitoringWorker — retained as an empty stub for build compatibility.
 *
 * WorkManager periodic jobs are no longer scheduled. The countdown timer
 * is a user-initiated session managed by [CountdownService] and [TimerManager].
 */
class MonitoringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        /** No-op: WorkManager scheduling has been removed. */
        @Suppress("UNUSED_PARAMETER")
        fun schedule(context: Context) = Unit
    }
}
