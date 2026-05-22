package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactDate
import com.phonepvr.friends.data.contacts.ContactsReader
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.photo.PhotoStorage
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.domain.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ImportUiState(
    val loading: Boolean = false,
    val contacts: List<DeviceContact> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val importing: Boolean = false,
)

@HiltViewModel
class ImportContactsViewModel @Inject constructor(
    private val contactsReader: ContactsReader,
    private val repository: PeopleRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun loadContacts() {
        if (_state.value.loading || _state.value.contacts.isNotEmpty()) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) { contactsReader.listContacts() }
            _state.value = _state.value.copy(loading = false, contacts = contacts)
        }
    }

    fun toggleSelection(contactId: Long) {
        val selected = _state.value.selectedIds.toMutableSet()
        if (!selected.add(contactId)) {
            selected.remove(contactId)
        }
        _state.value = _state.value.copy(selectedIds = selected)
    }

    fun importSelected(onDone: () -> Unit) {
        val ids = _state.value.selectedIds
        if (ids.isEmpty() || _state.value.importing) return
        _state.value = _state.value.copy(importing = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                ids.forEach { contactId ->
                    val details = contactsReader.readDetails(contactId) ?: return@forEach
                    val uuid = UUID.randomUUID().toString()
                    val photoRelativePath = runCatching {
                        contactsReader.openContactPhoto(contactId)?.use { stream ->
                            photoStorage.savePhoto(uuid, stream)
                        }
                    }.getOrNull()
                    val person = PersonEntity(
                        uuid = uuid,
                        displayName = details.displayName,
                        contactLookupKey = details.lookupKey.ifBlank { null },
                        photoRelativePath = photoRelativePath,
                        createdAt = now,
                        updatedAt = now,
                    )
                    val phones = details.phoneNumbers.map { raw ->
                        PhoneNumberEntity(
                            personId = 0,
                            rawNumber = raw,
                            normalizedNumber = raw.filter { it.isDigit() },
                        )
                    }
                    val events = buildList {
                        details.birthday?.let { add(it.toEvent(EventType.BIRTHDAY)) }
                        details.anniversary?.let {
                            add(it.toEvent(EventType.WEDDING_ANNIVERSARY))
                        }
                    }
                    repository.createPerson(person, phones, events)
                }
            }
            _state.value = _state.value.copy(importing = false)
            onDone()
        }
    }
}

private fun ContactDate.toEvent(type: EventType): EventEntity = EventEntity(
    personId = 0,
    type = type,
    month = month,
    day = day,
    year = year,
)
