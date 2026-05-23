package com.phonepvr.friends

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.phonepvr.friends.data.calllog.CallLogChangeObserver
import com.phonepvr.friends.data.repository.CallLogAutoSync
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.widget.WidgetRefreshObserver
import com.phonepvr.friends.work.scheduleBackupNudgeWork
import com.phonepvr.friends.work.scheduleCallLogSyncWork
import com.phonepvr.friends.work.scheduleReminderWork
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FriendsApplication : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetRefreshObserver: WidgetRefreshObserver

    @Inject
    lateinit var callLogAutoSync: CallLogAutoSync

    @Inject
    lateinit var callLogChangeObserver: CallLogChangeObserver

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Schedule the daily reminder at the user's chosen hour, and the daily
        // backup-nudge check, plus the 6-hour call-log backstop. KEEP leaves
        // any existing schedules untouched; the reminder hour matters only on
        // first install.
        appScope.launch {
            val hour = settingsRepository.settings.first().notificationHour
            scheduleReminderWork(this@FriendsApplication, hour)
            scheduleBackupNudgeWork(this@FriendsApplication)
            scheduleCallLogSyncWork(this@FriendsApplication)
        }
        // Live widget refresh: broadcasts an APPWIDGET_UPDATE to the
        // receiver whenever people or the timeline change, debounced.
        // Daily worker still runs for midnight rollovers.
        widgetRefreshObserver.start(appScope)
        // Watch the system call log so calls placed while the process is
        // alive land in the timeline without waiting for a cold launch.
        // No-op without READ_CALL_LOG.
        callLogChangeObserver.start(appScope)
        // Sync once now and every time the app comes back to the foreground.
        // onStart fires on cold launch AND every return-to-foreground, which
        // covers the gap when the process was killed.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appScope.launch(Dispatchers.IO) {
                        runCatching { callLogAutoSync.syncAllPeople() }
                    }
                }
            },
        )
    }
}
