package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactTracker
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ContactsFilterMode { ALL, TRACKED }

data class BrowseContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val primaryNumber: String?,
    val isTracked: Boolean,
    val trackedPersonId: Long?,
    val photoRelativePath: String?,
)

data class ContactsBrowserUiState(
    val loading: Boolean = true,
    val contacts: List<BrowseContact> = emptyList(),
    val filtered: List<BrowseContact> = emptyList(),
    val query: String = "",
    val filterMode: ContactsFilterMode = ContactsFilterMode.ALL,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsBrowserViewModel @Inject constructor(
    private val systemContactsRepository: SystemContactsRepository,
    private val contactTracker: ContactTracker,
    personDao: PersonDao,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filterMode = MutableStateFlow(ContactsFilterMode.ALL)
    private val permissionGranted = MutableStateFlow(false)

    val state: StateFlow<ContactsBrowserUiState> = combine(
        permissionGranted.flatMapLatest { granted ->
            if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
        },
        personDao.observeActive(),
        query,
        filterMode,
    ) { contacts, tracked, q, mode ->
        val trackedByKey = tracked
            .mapNotNull { p -> p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p } }
            .toMap()
        val annotated = contacts.map { dc ->
            val person = trackedByKey[dc.lookupKey]
            BrowseContact(
                contactId = dc.contactId,
                lookupKey = dc.lookupKey,
                displayName = dc.displayName,
                primaryNumber = dc.phoneNumbers.firstOrNull(),
                isTracked = person != null,
                trackedPersonId = person?.id,
                photoRelativePath = person?.photoRelativePath,
            )
        }
        ContactsBrowserUiState(
            loading = false,
            contacts = annotated,
            filtered = filter(annotated, q, mode),
            query = q,
            filterMode = mode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContactsBrowserUiState(loading = true),
    )

    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(mode: ContactsFilterMode) {
        filterMode.value = mode
    }

    fun toggleTracked(contact: BrowseContact) {
        viewModelScope.launch {
            if (contact.isTracked) {
                contactTracker.untrack(contact.lookupKey)
            } else {
                contactTracker.track(contact.contactId, contact.lookupKey)
            }
        }
    }

    private fun filter(
        contacts: List<BrowseContact>,
        query: String,
        mode: ContactsFilterMode,
    ): List<BrowseContact> {
        val scoped = when (mode) {
            ContactsFilterMode.ALL -> contacts
            ContactsFilterMode.TRACKED -> contacts.filter { it.isTracked }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return scoped
        val lowerName = trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        return scoped.filter { c ->
            if (c.displayName.lowercase().contains(lowerName)) return@filter true
            if (digits.isEmpty()) return@filter false
            c.primaryNumber?.filter { it.isDigit() }?.contains(digits) == true
        }
    }
}
