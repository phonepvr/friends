package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactTracker
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemContactsRepository: SystemContactsRepository,
    private val contactTracker: ContactTracker,
    personDao: PersonDao,
) : ViewModel() {

    private val contactId: Long = savedStateHandle.get<Long>(Routes.CONTACT_ID_ARG) ?: 0L
    private val details = MutableStateFlow<ContactDetails?>(null)
    private val loaded = MutableStateFlow(false)
    private val mutating = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            details.value = systemContactsRepository.details(contactId)
            loaded.value = true
        }
    }

    val state: StateFlow<ContactDetailUiState> = combine(
        details,
        loaded,
        mutating,
        details.flatMapLatest { d ->
            if (d != null && d.lookupKey.isNotBlank()) {
                personDao.observeActiveByContactLookupKey(d.lookupKey)
            } else {
                flowOf(null)
            }
        },
    ) { d, isLoaded, isMutating, person ->
        ContactDetailUiState(
            loading = !isLoaded,
            notFound = isLoaded && d == null,
            details = d,
            isTracked = person != null,
            trackedPersonId = person?.id,
            photoRelativePath = person?.photoRelativePath,
            mutating = isMutating,
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
}
