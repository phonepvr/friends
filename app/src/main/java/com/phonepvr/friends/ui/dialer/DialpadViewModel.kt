package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.dialer.T9
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DialpadMatch(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val matchedNumber: String,
    val isTracked: Boolean,
    val photoRelativePath: String?,
)

data class DialpadUiState(
    val input: String = "",
    val matches: List<DialpadMatch> = emptyList(),
    val placeError: String? = null,
)

private data class IndexedContact(
    val contact: DeviceContact,
    val nameDigits: String,
    val phoneDigits: List<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialpadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    systemContactsRepository: SystemContactsRepository,
    personDao: PersonDao,
    private val callPlacer: CallPlacer,
) : ViewModel() {

    private val contactsGranted = MutableStateFlow(false)
    private val placeError = MutableStateFlow<String?>(null)
    private val input = MutableStateFlow(
        savedStateHandle.get<String>(Routes.DIALPAD_PREFILL_ARG)?.takeIf { it.isNotBlank() }
            ?: "",
    )

    private val contactsFlow = contactsGranted.flatMapLatest { granted ->
        if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
    }

    // T9-encode contact names once per contacts emission so each
    // keystroke just does an in-memory String.contains scan.
    private val indexedContacts: Flow<List<IndexedContact>> = contactsFlow.map { contacts ->
        contacts.map { c ->
            IndexedContact(
                contact = c,
                nameDigits = T9.toDigits(c.displayName),
                phoneDigits = c.phoneNumbers.map { T9.digitsOnly(it) },
            )
        }
    }

    private val trackedByKeyFlow: Flow<Map<String, PersonEntity>> =
        personDao.observeActive().map { tracked ->
            tracked.asSequence()
                .mapNotNull { p ->
                    p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p }
                }
                .toMap()
        }

    val state: StateFlow<DialpadUiState> = combine(
        input,
        indexedContacts,
        trackedByKeyFlow,
        placeError,
    ) { value, entries, trackedByKey, err ->
        DialpadUiState(
            input = value,
            matches = buildMatches(value, entries, trackedByKey),
            placeError = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DialpadUiState(input = input.value),
    )

    fun onContactsPermissionState(granted: Boolean) {
        contactsGranted.value = granted
    }

    fun onDigit(c: Char) {
        input.update { it + c }
    }

    fun onBackspace() {
        input.update { it.dropLast(1) }
    }

    fun onClearInput() {
        input.value = ""
    }

    fun place(number: String): CallPlacer.PlaceResult {
        val result = callPlacer.place(number)
        placeError.value = when (result) {
            CallPlacer.PlaceResult.OK,
            CallPlacer.PlaceResult.NO_PERMISSION -> null
            CallPlacer.PlaceResult.INVALID_NUMBER -> "Enter a number to call."
            CallPlacer.PlaceResult.ERROR -> "Couldn't place the call. Try again."
        }
        return result
    }

    fun dismissPlaceError() {
        placeError.value = null
    }

    private fun buildMatches(
        input: String,
        entries: List<IndexedContact>,
        trackedByKey: Map<String, PersonEntity>,
    ): List<DialpadMatch> {
        if (input.length < 2) return emptyList()
        val q = T9.digitsOnly(input)
        if (q.length < 2) return emptyList()
        val results = ArrayList<DialpadMatch>()
        for (e in entries) {
            val nameHit = e.nameDigits.contains(q)
            val phoneIndex = e.phoneDigits.indexOfFirst { it.contains(q) }
            if (!nameHit && phoneIndex < 0) continue
            val person = trackedByKey[e.contact.lookupKey]
            results.add(
                DialpadMatch(
                    contactId = e.contact.contactId,
                    lookupKey = e.contact.lookupKey,
                    displayName = e.contact.displayName,
                    matchedNumber = if (phoneIndex >= 0) {
                        e.contact.phoneNumbers[phoneIndex]
                    } else {
                        e.contact.phoneNumbers.firstOrNull().orEmpty()
                    },
                    isTracked = person != null,
                    photoRelativePath = person?.photoRelativePath,
                ),
            )
            if (results.size >= MAX_MATCHES) break
        }
        return results
    }

    companion object {
        private const val MAX_MATCHES = 30
    }
}
