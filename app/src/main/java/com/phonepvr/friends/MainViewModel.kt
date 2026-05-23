package com.phonepvr.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.settings.AppSettings
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped state for [MainActivity]: the current settings (for theming)
 * and whether the app-lock gate has been passed this foreground session.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val peopleRepository: PeopleRepository,
) : ViewModel() {

    /** Null until the first settings read completes, so theming waits for it. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    init {
        // When app lock is off the gate is always considered passed. This also
        // means turning the lock on mid-session does not lock the user out.
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                if (!current.appLockEnabled) _authenticated.value = true
            }
        }
        // One-time backfill: people created before the default-cadence rule
        // existed have cadenceTargetDays = null. Apply the user's default
        // once so the People list cadence colours light up.
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.cadenceBackfilled) {
                peopleRepository.backfillMissingCadence(settings.defaultCadenceDays)
                settingsRepository.setCadenceBackfilled(true)
            }
        }
    }

    fun onAuthenticated() {
        _authenticated.value = true
    }

    fun onMovedToBackground() {
        _authenticated.value = false
    }

    /** Called once when the user finishes / skips first-run onboarding. */
    fun markOnboardingSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenOnboarding(true)
        }
    }
}
