package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactTracker
import com.phonepvr.friends.data.contacts.ContactWriter
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.repository.FavouritesRepository
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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
    val isFavourite: Boolean = false,
    /** Epoch ms of the most recent contact (timeline if bonded, else call log). */
    val lastContactedAt: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemContactsRepository: SystemContactsRepository,
    private val contactTracker: ContactTracker,
    private val contactWriter: ContactWriter,
    private val callPlacer: CallPlacer,
    private val favouritesRepository: FavouritesRepository,
    private val timelineDao: TimelineDao,
    private val callLogReader: CallLogReader,
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

    private val isFavouriteFlow: Flow<Boolean> = details.flatMapLatest { d ->
        if (d != null && d.lookupKey.isNotBlank()) {
            favouritesRepository.observeIsFavourite(d.lookupKey)
        } else {
            flowOf(false)
        }
    }

    // Last time we were in touch: the timeline's latest "counts as contact"
    // for bonded people (includes auto-synced calls), otherwise the most
    // recent call-log entry with any of the contact's numbers.
    private val lastContactedFlow: Flow<Long?> =
        combine(details, trackedPerson) { d, person -> d to person }
            .flatMapLatest { (d, person) ->
                flow {
                    emit(null)
                    val ts = when {
                        d == null -> null
                        person != null -> timelineDao.latestContactAt(person.id)
                        else -> latestCallTimestampFor(d.phoneNumbers)
                    }
                    emit(ts)
                }.flowOn(Dispatchers.IO)
            }

    val state: StateFlow<ContactDetailUiState> = combine(
        details,
        loaded,
        mutating,
        deletionState,
        trackedPerson,
        isFavouriteFlow,
        lastContactedFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val d = values[0] as ContactDetails?
        val isLoaded = values[1] as Boolean
        val isMutating = values[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val deletion = values[3] as Pair<Boolean, Boolean>
        val person = values[4] as PersonEntity?
        val isFav = values[5] as Boolean
        val lastContacted = values[6] as Long?
        ContactDetailUiState(
            loading = !isLoaded,
            notFound = isLoaded && d == null,
            details = d,
            isTracked = person != null,
            trackedPersonId = person?.id,
            photoRelativePath = person?.photoRelativePath,
            mutating = isMutating,
            deleting = deletion.first,
            deleted = deletion.second,
            isFavourite = isFav,
            lastContactedAt = lastContacted,
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
    fun callCapableAccounts(): List<CallPlacer.SimAccount> = callPlacer.callCapableAccounts()

    fun needsSimChoice(): Boolean = callPlacer.needsSimChoice()

    fun placeCall(
        number: String,
        account: android.telecom.PhoneAccountHandle? = null,
    ): CallPlacer.PlaceResult = callPlacer.place(number, account)

    /** Sets which of the contact's numbers is the default, then reloads. */
    fun setPrimaryNumber(dataId: Long) {
        viewModelScope.launch {
            contactWriter.setPrimaryNumber(dataId)
            details.value = systemContactsRepository.details(contactId)
        }
    }

    /** Sets the per-contact ringtone (or null to revert to the system default). */
    fun setCustomRingtone(uri: android.net.Uri?) {
        viewModelScope.launch {
            contactWriter.setCustomRingtone(contactId, uri)
            details.value = systemContactsRepository.details(contactId)
        }
    }

    /** Most recent call-log timestamp matching any of [numbers], or null. */
    private fun latestCallTimestampFor(numbers: List<String>): Long? {
        val suffixes = numbers
            .map { it.filter(Char::isDigit).takeLast(9) }
            .filter { it.length >= 7 }
            .toSet()
        if (suffixes.isEmpty()) return null
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)
        return runCatching {
            callLogReader.recentCalls(since)
                .filter { it.number.filter(Char::isDigit).takeLast(9) in suffixes }
                .maxOfOrNull { it.timestampMillis }
        }.getOrNull()
    }

    fun toggleFavourite() {
        val d = details.value ?: return
        if (d.lookupKey.isBlank()) return
        val primaryNumber = d.phoneNumbers.firstOrNull().orEmpty()
        if (primaryNumber.isBlank()) return
        viewModelScope.launch {
            favouritesRepository.toggle(
                lookupKey = d.lookupKey,
                displayName = d.displayName,
                primaryNumber = primaryNumber,
                photoRelativePath = state.value.photoRelativePath,
            )
        }
    }

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
