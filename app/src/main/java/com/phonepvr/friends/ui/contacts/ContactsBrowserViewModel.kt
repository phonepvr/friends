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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /** System contact photo URI, shown when there's no local bonded copy. */
    val photoUri: String?,
)

data class ContactsBrowserUiState(
    val loading: Boolean = true,
    val contacts: List<BrowseContact> = emptyList(),
    val filtered: List<BrowseContact> = emptyList(),
    val totalCount: Int = 0,
    val bondedCount: Int = 0,
    val query: String = "",
    val filterMode: ContactsFilterMode = ContactsFilterMode.ALL,
    /** Group titles available to filter by; empty hides the group selector. */
    val availableGroups: List<String> = emptyList(),
    /** Selected group title, or null for "all groups". */
    val selectedGroup: String? = null,
)

/** The non-contact filter inputs, pre-merged to keep the main combine ≤5 args. */
private data class BrowseControls(
    val query: String,
    val mode: ContactsFilterMode,
    val selectedGroup: String?,
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
    private val selectedGroup = MutableStateFlow<String?>(null)
    private val permissionGranted = MutableStateFlow(false)

    /** Group titles to offer; loaded once per permission grant. */
    private val availableGroups: kotlinx.coroutines.flow.Flow<List<String>> =
        permissionGranted.flatMapLatest { granted ->
            if (granted) flow { emit(systemContactsRepository.listGroupTitles()) } else flowOf(emptyList())
        }

    /** Contact ids in the selected group, or null when no group is selected. */
    private val groupMemberIds: kotlinx.coroutines.flow.Flow<Set<Long>?> =
        selectedGroup.flatMapLatest { title ->
            if (title == null) flowOf(null) else flow { emit(systemContactsRepository.contactIdsInGroup(title)) }
        }

    private val controls = combine(query, filterMode, selectedGroup) { q, mode, group ->
        BrowseControls(q, mode, group)
    }

    val state: StateFlow<ContactsBrowserUiState> = combine(
        permissionGranted.flatMapLatest { granted ->
            if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
        },
        personDao.observeActive(),
        controls,
        groupMemberIds,
        availableGroups,
    ) { contacts, tracked, ctrl, memberIds, groups ->
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
                photoUri = dc.photoUri,
            )
        }
        // A stale group selection (group deleted, or its membership empty) just
        // shows nothing for that filter rather than erroring.
        ContactsBrowserUiState(
            loading = false,
            contacts = annotated,
            filtered = filter(annotated, ctrl, memberIds),
            totalCount = annotated.size,
            bondedCount = annotated.count { it.isTracked },
            query = ctrl.query,
            filterMode = ctrl.mode,
            availableGroups = groups,
            selectedGroup = ctrl.selectedGroup,
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

    fun onGroupSelected(title: String?) {
        selectedGroup.value = title
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
        controls: BrowseControls,
        groupMemberIds: Set<Long>?,
    ): List<BrowseContact> {
        var scoped = when (controls.mode) {
            ContactsFilterMode.ALL -> contacts
            ContactsFilterMode.TRACKED -> contacts.filter { it.isTracked }
        }
        // Group filter intersects by contact id when a group is selected.
        if (groupMemberIds != null) {
            scoped = scoped.filter { it.contactId in groupMemberIds }
        }
        val trimmed = controls.query.trim()
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
