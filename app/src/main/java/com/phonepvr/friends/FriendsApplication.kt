package com.phonepvr.friends

import android.app.Application
import com.phonepvr.friends.data.repository.CallLogAutoSync
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.widget.WidgetRefreshObserver
import com.phonepvr.friends.work.scheduleBackupNudgeWork
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Schedule the daily reminder at the user's chosen hour, and the daily
        // backup-nudge check. KEEP leaves any existing schedules untouched; the
        // reminder hour matters only on first install.
        appScope.launch {
            val hour = settingsRepository.settings.first().notificationHour
            scheduleReminderWork(this@FriendsApplication, hour)
            scheduleBackupNudgeWork(this@FriendsApplication)
        }
        // Live widget refresh: pings UpcomingWidget.updateAll() whenever
        // people or the timeline change, debounced. Daily worker still runs
        // for midnight rollovers.
        widgetRefreshObserver.start(appScope)
        // Sync the device call log into every person's timeline on each
        // launch. No-ops without READ_CALL_LOG; idempotent via callDedupKey.
        appScope.launch(Dispatchers.IO) {
            runCatching { callLogAutoSync.syncAllPeople() }
        }
    }
}
