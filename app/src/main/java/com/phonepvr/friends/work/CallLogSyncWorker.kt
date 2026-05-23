package com.phonepvr.friends.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonepvr.friends.data.repository.CallLogAutoSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friends-call-log-sync"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CallLogSyncWorkerEntryPoint {
    fun callLogAutoSync(): CallLogAutoSync
}

/**
 * Backstop for [CallLogAutoSync]: even when the process is never
 * resumed (so neither the live ContentObserver nor the
 * process-lifecycle resume hook fire), this periodic worker drags the
 * device call log into the timeline. Safe no-op without
 * READ_CALL_LOG; idempotent via the timeline `callDedupKey` UNIQUE.
 */
class CallLogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            CallLogSyncWorkerEntryPoint::class.java,
        )
        runCatching { deps.callLogAutoSync().syncAllPeople() }
        return Result.success()
    }
}

/** Schedules the periodic call-log sync, keeping any existing schedule. */
fun scheduleCallLogSyncWork(context: Context) {
    val request = PeriodicWorkRequestBuilder<CallLogSyncWorker>(6, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
