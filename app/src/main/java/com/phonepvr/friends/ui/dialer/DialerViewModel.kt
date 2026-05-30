package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
)

data class DialerUiState(
    val recents: List<RecentEntry> = emptyList(),
    val recentsLoaded: Boolean = false,
    val favourites: List<FavouriteContactEntity> = emptyList(),
    val placeError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    systemContactsRepository: SystemContactsRepository,
    recentsRepository: RecentsRepository,
    personDao: PersonDao,
    favouritesRepository: FavouritesRepository,
    private val callPlacer: CallPlacer,
) : ViewModel() {

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

    private val contactByPhoneSuffix: Flow<Map<String, DeviceContact>> =
        contactsFlow.map { contacts ->
            val map = HashMap<String, DeviceContact>()
            for (c in contacts) {
                for (number in c.phoneNumbers) {
                    val key = T9.digitsOnly(number).takeLast(SUFFIX_DIGITS)
                    if (key.length >= 4 && key !in map) map[key] = c
                }
            }
            map
        }

    private val trackedByKeyFlow = personDao.observeActive().map { tracked ->
        tracked.asSequence()
            .mapNotNull { p -> p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { it to p } }
            .toMap()
    }

    val state: StateFlow<DialerUiState> = combine(
        recentsFlow,
        contactByPhoneSuffix,
        trackedByKeyFlow,
        favouritesRepository.observeAll(),
        placeError,
    ) { calls, byDigits, trackedByKey, favourites, err ->
        val recents = calls.map { call ->
            val contact = lookupCallContact(call.number, byDigits)
            val person = contact?.lookupKey?.let { trackedByKey[it] }
            RecentEntry(
                number = call.number,
                displayName = contact?.displayName,
                contactId = contact?.contactId,
                isTracked = person != null,
                photoRelativePath = person?.photoRelativePath,
                type = call.type,
                timestampMillis = call.timestampMillis,
                durationSeconds = call.durationSeconds,
            )
        }
        DialerUiState(
            recents = recents,
            recentsLoaded = true,
            favourites = favourites,
            placeError = err,
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

    companion object {
        /** Compare phone numbers on their last N digits so different country
         *  code prefixes still match (e.g. +44 7xxx vs 07xxx in the UK). */
        private const val SUFFIX_DIGITS = 9
    }
}
