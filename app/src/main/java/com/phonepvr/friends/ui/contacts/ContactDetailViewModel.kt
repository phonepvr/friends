package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactTracker
import com.phonepvr.friends.data.contacts.ContactWriter
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val details: ContactDetails? = null,
    val isTracked: Boolean = false,
    val trackedPersonId: Long? = null,
    val photoRelativePath: String? = null,
    /** True while a track/untrack write is in flight, so the toggle disables. */
    val mutating: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemContactsRepository: SystemContactsRepository,
    private val contactTracker: ContactTracker,
    private val contactWriter: ContactWriter,
    private val callPlacer: CallPlacer,
    personDao: PersonDao,
) : ViewModel() {

    val contactId: Long = savedStateHandle.get<Long>(Routes.CONTACT_ID_ARG) ?: 0L
    private val details = MutableStateFlow<ContactDetails?>(null)
    private val loaded = MutableStateFlow(false)
    private val mutating = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            details.value = systemContactsRepository.details(contactId)
            loaded.value = true
        }
    }

    private val deletionState = combine(deleting, deleted) { d, done -> d to done }

    private val trackedPerson: Flow<PersonEntity?> =
        details.flatMapLatest { d ->
            if (d != null && d.lookupKey.isNotBlank()) {
                personDao.observeActiveByContactLookupKey(d.lookupKey)
            } else {
                flowOf(null)
            }
        }

    val state: StateFlow<ContactDetailUiState> = combine(
        details,
        loaded,
        mutating,
        deletionState,
        trackedPerson,
    ) { d, isLoaded, isMutating, deletion, person ->
        val (isDeleting, isDeleted) = deletion
        ContactDetailUiState(
            loading = !isLoaded,
            notFound = isLoaded && d == null,
            details = d,
            isTracked = person != null,
            trackedPersonId = person?.id,
            photoRelativePath = person?.photoRelativePath,
            mutating = isMutating,
            deleting = isDeleting,
            deleted = isDeleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContactDetailUiState(loading = true),
    )

    fun toggleTracked() {
        val d = details.value ?: return
        val isTracked = state.value.isTracked
        viewModelScope.launch {
            mutating.value = true
            try {
                if (isTracked) {
                    contactTracker.untrack(d.lookupKey)
                } else {
                    contactTracker.track(contactId, d.lookupKey)
                }
            } finally {
                mutating.value = false
            }
        }
    }

    /**
     * Place a call directly through Bondwidth's CallPlacer. Returns the
     * Telecom result so the screen can prompt for CALL_PHONE or snackbar
     * an error. Avoids the ACTION_DIAL chooser detour the screen used to
     * launch — tapping a number on a contact you're looking at is intent
     * enough to dial.
     */
    fun placeCall(number: String): CallPlacer.PlaceResult = callPlacer.place(number)

    fun deleteContact() {
        val d = details.value ?: return
        if (deleting.value || deleted.value) return
        viewModelScope.launch {
            deleting.value = true
            try {
                // Untrack first so the linked Bondwidth person is archived
                // (preserves any timeline history accumulated before the
                // contact gets erased from the system provider).
                contactTracker.untrack(d.lookupKey)
                val ok = contactWriter.delete(contactId, d.lookupKey)
                if (ok) deleted.value = true
            } finally {
                deleting.value = false
            }
        }
    }
}
