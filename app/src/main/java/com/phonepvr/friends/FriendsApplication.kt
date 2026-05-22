package com.phonepvr.friends

import android.app.Application
import com.phonepvr.friends.work.scheduleReminderWork
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FriendsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleReminderWork(this)
    }
}
