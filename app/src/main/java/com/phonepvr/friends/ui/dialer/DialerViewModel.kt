package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.dialer.RecentsRepository
import com.phonepvr.friends.data.dialer.T9
import com.phonepvr.friends.domain.model.CallType
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class DialerSegment { RECENTS, DIALPAD }

data class RecentEntry(
    val number: String,
    val displayName: String?,
    val isTracked: Boolean,
    val photoRelativePath: String?,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
)

data class DialpadMatch(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val matchedNumber: String,
    val isTracked: Boolean,
    val photoRelativePath: String?,
)

data class DialerUiState(
    val segment: DialerSegment = DialerSegment.RECENTS,
    val recents: List<RecentEntry> = emptyList(),
    val recentsLoaded: Boolean = false,
    val dialpadInput: String = "",
    val matches: List<DialpadMatch> = emptyList(),
    val placeError: String? = null,
)

/** Contact + its precomputed T9 forms; cheaper to filter than recomputing. */
private data class IndexedContact(
    val contact: DeviceContact,
    val nameDigits: String,
    val phoneDigits: List<String>,
)

private data class ContactsIndex(
    val entries: List<IndexedContact>,
    val byPhoneSuffix: Map<String, DeviceContact>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    systemContactsRepository: SystemContactsRepository,
    recentsRepository: RecentsRepository,
    personDao: PersonDao,
    private val callPlacer: CallPlacer,
) : ViewModel() {

    // Pre-filled by the DIALER route when the user lands here from a
    // tel: intent. The activity-alias forwards the number through
    // Routes.dialer(number).
    private val prefill: String =
        savedStateHandle.get<String>(Routes.DIALPAD_PREFILL_ARG)?.takeIf { it.isNotBlank() }
            ?: ""

    private val segment = MutableStateFlow(
        if (prefill.isNotEmpty()) DialerSegment.DIALPAD else DialerSegment.RECENTS,
    )
    private val dialpadInput = MutableStateFlow(prefill)
    private val callLogGranted = MutableStateFlow(false)
    private val contactsGranted = MutableStateFlow(false)
    private val placeError = MutableStateFlow<String?>(null)

    private val recentsSince = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

    private val recentsFlow = callLogGranted.flatMapLatest { granted ->
        if (granted) recentsRepository.observe(recentsSince) else flowOf(emptyList())
    }

    private val contactsFlow = contactsGranted.flatMapLatest { granted ->
        if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
    }

    // Heavy work (T9-encoding 1000+ contact names + building the
    // phone-suffix lookup map) runs only when the contacts list itself
    // changes, NOT on every keystroke or recents update.
    private val contactsIndex: Flow<ContactsIndex> = contactsFlow.map { contacts ->
        val suffixMap = HashMap<String, DeviceContact>()
        val entries = ArrayList<IndexedContact>(contacts.size)
        for (c in contacts) {
            val phoneDigits = c.phoneNumbers.map { T9.digitsOnly(it) }
            for (digits in phoneDigits) {
                val key = digits.takeLast(SUFFIX_DIGITS)
                if (key.length >= 4 && key !in suffixMap) suffixMap[key] = c
            }
            entries.add(
                IndexedContact(
                    contact = c,
                    nameDigits = T9.toDigits(c.displayName),
                    phoneDigits = phoneDigits,
                ),
            )
        }
        ContactsIndex(entries, suffixMap)
    }

    private val trackedByKeyFlow: Flow<Map<String, PersonEntity>> =
        personDao.observeActive().map { tracked ->
            tracked.asSequence()
                .mapNotNull { p ->
                    p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p }
                }
                .toMap()
        }

    val state: StateFlow<DialerUiState> = combine(
        segment,
        dialpadInput,
        recentsFlow,
        contactsIndex,
        trackedByKeyFlow,
    ) { seg, input, calls, idx, trackedByKey ->
        val recents = calls.map { call ->
            val contact = lookupCallContact(call.number, idx.byPhoneSuffix)
            val person = contact?.lookupKey?.let { trackedByKey[it] }
            RecentEntry(
                number = call.number,
                displayName = contact?.displayName,
                isTracked = person != null,
                photoRelativePath = person?.photoRelativePath,
                type = call.type,
                timestampMillis = call.timestampMillis,
                durationSeconds = call.durationSeconds,
            )
        }
        val matches = buildDialpadMatches(input, idx.entries, trackedByKey)
        DialerUiState(
            segment = seg,
            recents = recents,
            recentsLoaded = true,
            dialpadInput = input,
            matches = matches,
            placeError = placeError.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DialerUiState(dialpadInput = dialpadInput.value),
    )

    fun onPermissionState(callLog: Boolean, contacts: Boolean) {
        callLogGranted.value = callLog
        contactsGranted.value = contacts
    }

    fun onSegmentChange(value: DialerSegment) {
        segment.value = value
    }

    fun onDigit(c: Char) {
        dialpadInput.update { it + c }
        if (segment.value != DialerSegment.DIALPAD) {
            segment.value = DialerSegment.DIALPAD
        }
    }

    fun onBackspace() {
        dialpadInput.update { it.dropLast(1) }
    }

    fun onClearInput() {
        dialpadInput.value = ""
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

    private fun lookupCallContact(
        number: String,
        byDigits: Map<String, DeviceContact>,
    ): DeviceContact? {
        val digits = T9.digitsOnly(number).takeLast(SUFFIX_DIGITS)
        if (digits.length < 4) return null
        return byDigits[digits]
    }

    private fun buildDialpadMatches(
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
        /** Compare phone numbers on their last N digits so different country
         *  code prefixes still match (e.g. +44 7xxx vs 07xxx in the UK). */
        private const val SUFFIX_DIGITS = 9
        private const val MAX_MATCHES = 30
    }
}
