package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Speed Dial settings screen: view, (re)assign, and clear the 1–9
 * dialpad shortcuts. Assignment itself happens via the system contact picker
 * in the screen; this just persists the chosen number per key.
 */
@HiltViewModel
class SpeedDialViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val assignments: StateFlow<Map<Int, String>> = settingsRepository.settings
        .map { it.speedDial }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun assign(key: Int, number: String) {
        viewModelScope.launch { settingsRepository.setSpeedDial(key, number) }
    }

    fun clear(key: Int) {
        viewModelScope.launch { settingsRepository.setSpeedDial(key, null) }
    }
}
