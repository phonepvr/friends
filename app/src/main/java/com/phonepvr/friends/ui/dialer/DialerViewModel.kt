package com.phonepvr.friends.ui.dialer

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    systemContactsRepository: SystemContactsRepository,
    recentsRepository: RecentsRepository,
    personDao: PersonDao,
    private val callPlacer: CallPlacer,
) : ViewModel() {

    private val segment = MutableStateFlow(DialerSegment.RECENTS)
    private val dialpadInput = MutableStateFlow("")
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

    val state: StateFlow<DialerUiState> = combine(
        segment,
        dialpadInput,
        recentsFlow,
        contactsFlow,
        personDao.observeActive(),
    ) { seg, input, calls, contacts, tracked ->
        val trackedByKey = tracked
            .mapNotNull { p -> p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p } }
            .toMap()
        val contactByDigits = buildContactByDigits(contacts)
        val recents = calls.map { call ->
            val contact = lookupCallContact(call.number, contactByDigits)
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
        val matches = buildDialpadMatches(input, contacts, trackedByKey)
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
        initialValue = DialerUiState(),
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

    private fun buildContactByDigits(
        contacts: List<DeviceContact>,
    ): Map<String, DeviceContact> {
        val map = HashMap<String, DeviceContact>()
        for (c in contacts) {
            for (number in c.phoneNumbers) {
                val key = T9.digitsOnly(number).takeLast(SUFFIX_DIGITS)
                if (key.length >= 4 && key !in map) map[key] = c
            }
        }
        return map
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
        contacts: List<DeviceContact>,
        trackedByKey: Map<String, PersonEntity>,
    ): List<DialpadMatch> {
        if (input.length < 2) return emptyList()
        val q = T9.digitsOnly(input)
        if (q.length < 2) return emptyList()
        val results = ArrayList<DialpadMatch>()
        for (c in contacts) {
            val nameT9 = T9.toDigits(c.displayName)
            val nameHit = nameT9.contains(q)
            val phoneHit = c.phoneNumbers.firstOrNull { T9.digitsOnly(it).contains(q) }
            if (!nameHit && phoneHit == null) continue
            val person = trackedByKey[c.lookupKey]
            results.add(
                DialpadMatch(
                    contactId = c.contactId,
                    lookupKey = c.lookupKey,
                    displayName = c.displayName,
                    matchedNumber = phoneHit ?: c.phoneNumbers.firstOrNull().orEmpty(),
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
