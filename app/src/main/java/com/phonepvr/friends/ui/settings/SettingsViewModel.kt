package com.phonepvr.friends.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.settings.AppSettings
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.model.ThemeMode
import com.phonepvr.friends.work.rescheduleReminderWork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = runUpdate {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) = runUpdate {
        settingsRepository.setDynamicColorEnabled(enabled)
    }

    fun setNotificationLeadDays(days: Int) = runUpdate {
        settingsRepository.setNotificationLeadDays(days)
    }

    fun setNotificationHour(hour: Int) = runUpdate {
        settingsRepository.setNotificationHour(hour)
        // The hour affects the schedule, so the daily job is re-enqueued.
        rescheduleReminderWork(appContext, hour)
    }

    fun setDefaultCadenceDays(days: Int) = runUpdate {
        settingsRepository.setDefaultCadenceDays(days)
    }

    fun setAppLockEnabled(enabled: Boolean) = runUpdate {
        settingsRepository.setAppLockEnabled(enabled)
    }

    fun setHideFromScreenshots(enabled: Boolean) = runUpdate {
        settingsRepository.setHideFromScreenshots(enabled)
    }

    fun setBackupNudgeIntervalDays(days: Int) = runUpdate {
        settingsRepository.setBackupNudgeIntervalDays(days)
    }

    private fun runUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
