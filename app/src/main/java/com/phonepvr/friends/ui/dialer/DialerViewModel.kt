package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.blocking.BlockedNumberManager
import com.phonepvr.friends.data.contacts.ContactPhone
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.dialer.RecentsRepository
import com.phonepvr.friends.data.dialer.T9
import com.phonepvr.friends.data.repository.FavouritesRepository
import com.phonepvr.friends.domain.model.CallType
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RecentEntry(
    val number: String,
    val displayName: String?,
    val contactId: Long?,
    val isTracked: Boolean,
    val photoRelativePath: String?,
    /** System contact photo URI, shown when there's no local bonded copy. */
    val photoUri: String?,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
)

/**
 * A contact surfaced by the in-tab search. Carries enough to call directly
 * (primary or all numbers) and to reuse the existing long-press sheet via a
 * synthetic RecentEntry.
 */
data class ContactSearchResult(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val primaryNumber: String?,
    val allNumbers: List<String>,
    val isTracked: Boolean,
    val photoRelativePath: String?,
    val photoUri: String?,
)

data class DialerUiState(
    val recents: List<RecentEntry> = emptyList(),
    val recentsLoaded: Boolean = false,
    val favourites: List<FavouriteContactEntity> = emptyList(),
    /** Live system photo URI per favourite lookupKey, for the strip avatars. */
    val favouritePhotoByLookupKey: Map<String, String> = emptyMap(),
    val placeError: String? = null,
    /** Current contact-search query (empty = no search). */
    val query: String = "",
    /** Contact-search results, populated whenever [query] is non-empty. */
    val contactResults: List<ContactSearchResult> = emptyList(),
)

/**
 * Decides which number a "call back" tap should dial when the row maps
 * to a saved contact. Direct = call this number now; Pick = the user
 * has more than one number with no default tagged, so show a picker.
 */
sealed interface CallTarget {
    data class Direct(val number: String) : CallTarget
    data class Pick(val displayName: String, val phones: List<ContactPhone>) : CallTarget
}

/** Two address-book indexes built together in one pass over the contacts list. */
private data class ContactsIndex(
    val byPhoneSuffix: Map<String, DeviceContact>,
    val photoByLookupKey: Map<String, String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val systemContactsRepository: SystemContactsRepository,
    recentsRepository: RecentsRepository,
    personDao: PersonDao,
    favouritesRepository: FavouritesRepository,
    private val callPlacer: CallPlacer,
    private val blockedNumberManager: BlockedNumberManager,
) : ViewModel() {

    /** True iff blocking is supported on this device + we're the default dialer. */
    fun canBlock(): Boolean = blockedNumberManager.canBlock()

    suspend fun isBlocked(number: String): Boolean = blockedNumberManager.isBlocked(number)

    suspend fun setBlocked(number: String, blocked: Boolean): Boolean = if (blocked) {
        blockedNumberManager.block(number)
    } else {
        blockedNumberManager.unblock(number)
    }

    private val callLogGranted = MutableStateFlow(false)
    private val contactsGranted = MutableStateFlow(false)
    private val placeError = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    private val recentsSince = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

    private val recentsFlow = callLogGranted.flatMapLatest { granted ->
        if (granted) recentsRepository.observe(recentsSince) else flowOf(emptyList())
    }

    private val contactsFlow = contactsGranted.flatMapLatest { granted ->
        if (granted) systemContactsRepository.observeAll() else flowOf(emptyList())
    }

    // One pass over the address book yields both indexes the dialer needs:
    // a phone-suffix → contact map (for matching a recents number to its
    // saved identity) and a lookupKey → photo URI map (for the favourites
    // strip, which is keyed by lookupKey).
    private val contactsIndex: Flow<ContactsIndex> = contactsFlow.map { contacts ->
        val byPhoneSuffix = HashMap<String, DeviceContact>()
        val photoByLookupKey = HashMap<String, String>()
        for (c in contacts) {
            for (number in c.phoneNumbers) {
                val key = T9.digitsOnly(number).takeLast(SUFFIX_DIGITS)
                if (key.length >= 4 && key !in byPhoneSuffix) byPhoneSuffix[key] = c
            }
            val photo = c.photoUri
            if (c.lookupKey.isNotBlank() && photo != null) photoByLookupKey[c.lookupKey] = photo
        }
        ContactsIndex(byPhoneSuffix, photoByLookupKey)
    }

    private val trackedByKeyFlow = personDao.observeActive().map { tracked ->
        tracked.asSequence()
            .mapNotNull { p -> p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p } }
            .toMap()
    }

    private val baseState: Flow<DialerUiState> = combine(
        recentsFlow,
        contactsIndex,
        trackedByKeyFlow,
        favouritesRepository.observeAll(),
        placeError,
    ) { calls, index, trackedByKey, favourites, err ->
        val recents = calls.map { call ->
            val contact = lookupCallContact(call.number, index.byPhoneSuffix)
            val person = contact?.lookupKey?.let { trackedByKey[it] }
            RecentEntry(
                number = call.number,
                displayName = contact?.displayName,
                contactId = contact?.contactId,
                isTracked = person != null,
                photoRelativePath = person?.photoRelativePath,
                photoUri = contact?.photoUri,
                type = call.type,
                timestampMillis = call.timestampMillis,
                durationSeconds = call.durationSeconds,
            )
        }
        DialerUiState(
            recents = recents,
            recentsLoaded = true,
            favourites = favourites,
            favouritePhotoByLookupKey = index.photoByLookupKey,
            placeError = err,
        )
    }

    /**
     * Substring contact search driven by [query]. Hits when the trimmed query
     * is at least 2 chars; matches against name (case-insensitive) and the
     * digits of any phone number. Capped at 50 results so a typo doesn't list
     * the whole address book.
     */
    private val searchResults: Flow<List<ContactSearchResult>> =
        combine(query, contactsFlow, trackedByKeyFlow) { q, contacts, trackedByKey ->
            val trimmed = q.trim()
            if (trimmed.length < 2) return@combine emptyList()
            val lowerName = trimmed.lowercase()
            val digits = trimmed.filter(Char::isDigit)
            contacts.asSequence()
                .filter { c ->
                    c.displayName.lowercase().contains(lowerName) ||
                        (
                            digits.isNotEmpty() &&
                                c.phoneNumbers.any { it.filter(Char::isDigit).contains(digits) }
                            )
                }
                .take(SEARCH_RESULT_LIMIT)
                .map { c ->
                    val person = trackedByKey[c.lookupKey]
                    ContactSearchResult(
                        contactId = c.contactId,
                        lookupKey = c.lookupKey,
                        displayName = c.displayName,
                        primaryNumber = c.phoneNumbers.firstOrNull(),
                        allNumbers = c.phoneNumbers,
                        isTracked = person != null,
                        photoRelativePath = person?.photoRelativePath,
                        photoUri = c.photoUri,
                    )
                }
                .toList()
        }

    val state: StateFlow<DialerUiState> = combine(
        baseState,
        query,
        searchResults,
    ) { base, q, results ->
        base.copy(query = q, contactResults = results)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DialerUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onPermissionState(callLog: Boolean, contacts: Boolean) {
        callLogGranted.value = callLog
        contactsGranted.value = contacts
    }

    fun callCapableAccounts(): List<CallPlacer.SimAccount> = callPlacer.callCapableAccounts()

    fun needsSimChoice(): Boolean = callPlacer.needsSimChoice()

    fun place(
        number: String,
        account: android.telecom.PhoneAccountHandle? = null,
    ): CallPlacer.PlaceResult {
        val result = callPlacer.place(number, account)
        placeError.value = when (result) {
            CallPlacer.PlaceResult.OK,
            CallPlacer.PlaceResult.NO_PERMISSION -> null
            CallPlacer.PlaceResult.INVALID_NUMBER -> "Enter a number to call."
            CallPlacer.PlaceResult.ERROR -> "Couldn't place the call. Try again."
        }
        return result
    }

    /**
     * Decide which number to call back when the user taps the call icon on
     * a recents row. Honours the user's primary-number choice; surfaces a
     * picker only when there's a real ambiguity (multiple numbers, none
     * tagged default). Falls back to the row's own number when the contact
     * can't be read.
     */
    suspend fun resolveCallTarget(entry: RecentEntry): CallTarget {
        val contactId = entry.contactId
            ?: return CallTarget.Direct(entry.number)
        val details = systemContactsRepository.details(contactId)
            ?: return CallTarget.Direct(entry.number)
        val phones = details.phoneEntries
        return when {
            phones.isEmpty() -> CallTarget.Direct(entry.number)
            phones.size == 1 -> CallTarget.Direct(phones.first().number)
            else -> {
                val primary = phones.firstOrNull { it.isPrimary }
                if (primary != null) {
                    CallTarget.Direct(primary.number)
                } else {
                    CallTarget.Pick(details.displayName, phones)
                }
            }
        }
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

    companion object {
        /** Compare phone numbers on their last N digits so different country
         *  code prefixes still match (e.g. +44 7xxx vs 07xxx in the UK). */
        private const val SUFFIX_DIGITS = 9
        private const val SEARCH_RESULT_LIMIT = 50
    }
}
