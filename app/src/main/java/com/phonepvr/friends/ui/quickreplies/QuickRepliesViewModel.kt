package com.phonepvr.friends.ui.quickreplies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.settings.DEFAULT_QUICK_REPLIES
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor for the quick-reply messages shown on long-press of Reject during
 * an incoming call. Order is preserved (top of the list shows first); the
 * user can add, remove, and reset to defaults.
 */
@HiltViewModel
class QuickRepliesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val messages: StateFlow<List<String>> =
        settingsRepository.settings
            .map { it.quickReplyMessages }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val current = settingsRepository.settings.first().quickReplyMessages
            // Silently no-op on duplicates so the list stays clean.
            if (cleaned !in current) {
                settingsRepository.setQuickReplyMessages(current + cleaned)
            }
        }
    }

    fun remove(index: Int) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().quickReplyMessages
            if (index in current.indices) {
                settingsRepository.setQuickReplyMessages(
                    current.toMutableList().apply { removeAt(index) },
                )
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.setQuickReplyMessages(DEFAULT_QUICK_REPLIES)
        }
    }
}
