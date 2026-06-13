package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SaveNumberUiState(
    val number: String = "",
    val query: String = "",
    val contacts: List<DeviceContact> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SaveNumberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    systemContactsRepository: SystemContactsRepository,
) : ViewModel() {

    val number: String =
        savedStateHandle.get<String>(Routes.DIALPAD_PREFILL_ARG).orEmpty()

    private val query = MutableStateFlow("")
    private val permissionGranted = MutableStateFlow(false)

    val state: StateFlow<SaveNumberUiState> = combine(
        permissionGranted.flatMapLatest { granted ->
            if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
        },
        query,
    ) { contacts, q ->
        SaveNumberUiState(
            number = number,
            query = q,
            contacts = filter(contacts, q),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SaveNumberUiState(number = number),
    )

    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun filter(contacts: List<DeviceContact>, query: String): List<DeviceContact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts
        val lower = trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        return contacts.filter { c ->
            if (c.displayName.lowercase().contains(lower)) return@filter true
            if (digits.isEmpty()) return@filter false
            c.phoneNumbers.any { it.filter(Char::isDigit).contains(digits) }
        }
    }
}
