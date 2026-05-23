package com.phonepvr.friends

import android.app.Application
import com.phonepvr.friends.data.settings.SettingsRepository
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

    override fun onCreate() {
        super.onCreate()
        // Schedule the daily reminder at the user's chosen hour, and the daily
        // backup-nudge check. KEEP leaves any existing schedules untouched; the
        // reminder hour matters only on first install.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val hour = settingsRepository.settings.first().notificationHour
            scheduleReminderWork(this@FriendsApplication, hour)
            scheduleBackupNudgeWork(this@FriendsApplication)
        }
    }
}
