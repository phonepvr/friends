package com.phonepvr.friends.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.notification.BackupNudgeNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friends-backup-nudge"
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupNudgeWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
}

/**
 * Daily check: if the user has not exported a backup within
 * [com.phonepvr.friends.data.settings.AppSettings.backupNudgeIntervalDays],
 * posts a low-priority notification. The in-app banner uses the same
 * threshold computation independently, so this worker exists only to put
 * the nudge in the system shade.
 *
 * First-run reference point is the package's first-install time, so a brand
 * new install never nudges immediately.
 */
class BackupNudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupNudgeWorkerEntryPoint::class.java,
        )
        val settings = deps.settingsRepository().settings.first()
        val installTime = runCatching {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        val anchor = settings.lastSuccessfulBackupAt ?: installTime
        val now = System.currentTimeMillis()
        val threshold = settings.backupNudgeIntervalDays.toLong() * DAY_MILLIS
        val overdue = now - anchor > threshold
        val notDismissedSinceAnchor = settings.backupNudgeDismissedAt?.let { it < anchor } ?: true
        if (overdue && notDismissedSinceAnchor) {
            BackupNudgeNotifier.post(applicationContext)
        }
        return Result.success()
    }
}

/** Schedules the daily backup-nudge job, keeping any existing schedule. */
fun scheduleBackupNudgeWork(context: Context) {
    val request = PeriodicWorkRequestBuilder<BackupNudgeWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
