package com.phonepvr.friends.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonepvr.friends.data.backup.AutoBackupFrequency
import com.phonepvr.friends.data.backup.AutoBackupRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friends-auto-backup"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupWorkerEntryPoint {
    fun autoBackupRunner(): AutoBackupRunner
}

/**
 * Runs the opt-in scheduled backup. The outcome (success, or why it failed) is
 * recorded in settings by [AutoBackupRunner] for the Backup screen to show, so the
 * worker always reports success to WorkManager: retrying a failing folder on
 * WorkManager's backoff would only hammer it, and the next scheduled run tries again.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, AutoBackupWorkerEntryPoint::class.java)
            .autoBackupRunner()
            .runOnce()
        return Result.success()
    }
}

/**
 * (Re)schedules the periodic backup. Pass [replace] = true when the user changed the
 * frequency so the new period takes effect; false at app start to keep the existing
 * timer running instead of resetting it on every launch.
 */
fun scheduleAutoBackupWork(context: Context, frequency: AutoBackupFrequency, replace: Boolean) {
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days.toLong(), TimeUnit.DAYS)
        // Writing a ZIP is the kind of work that can wait for a good moment.
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}

fun cancelAutoBackupWork(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
}
