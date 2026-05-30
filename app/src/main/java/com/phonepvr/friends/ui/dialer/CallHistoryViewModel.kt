package com.phonepvr.friends.ui.dialer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.dialer.RecentsRepository
import com.phonepvr.friends.data.dialer.T9
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CallHistoryUiState(
    val loading: Boolean = true,
    val number: String = "",
    val contactId: Long? = null,
    val displayName: String? = null,
    val photoRelativePath: String? = null,
    val isBonded: Boolean = false,
    val bondedPersonId: Long? = null,
    val calls: List<DeviceCall> = emptyList(),
    val placeError: String? = null,
)

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recentsRepository: RecentsRepository,
    systemContactsRepository: SystemContactsRepository,
    personDao: PersonDao,
    private val callPlacer: CallPlacer,
) : ViewModel() {

    val number: String =
        savedStateHandle.get<String>(Routes.DIALPAD_PREFILL_ARG).orEmpty()

    private val numberSuffix: String = T9.digitsOnly(number).takeLast(SUFFIX_DIGITS)
    private val placeError = MutableStateFlow<String?>(null)
    private val yearAgoMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)

    val state: StateFlow<CallHistoryUiState> = combine(
        recentsRepository.observe(yearAgoMs),
        systemContactsRepository.observeAll(),
        personDao.observeActive(),
        placeError,
    ) { allCalls, contacts, tracked, err ->
        val callsForNumber = if (numberSuffix.length < 4) {
            emptyList()
        } else {
            allCalls.filter {
                T9.digitsOnly(it.number).takeLast(SUFFIX_DIGITS) == numberSuffix
            }
        }
        val contact = if (numberSuffix.length < 4) {
            null
        } else {
            contacts.firstOrNull { c ->
                c.phoneNumbers.any {
                    T9.digitsOnly(it).takeLast(SUFFIX_DIGITS) == numberSuffix
                }
            }
        }
        val person = contact?.lookupKey
            ?.takeIf { it.isNotBlank() }
            ?.let { key -> tracked.firstOrNull { it.contactLookupKey == key } }
        CallHistoryUiState(
            loading = false,
            number = number,
            contactId = contact?.contactId,
            displayName = contact?.displayName,
            photoRelativePath = person?.photoRelativePath,
            isBonded = person != null,
            bondedPersonId = person?.id,
            calls = callsForNumber,
            placeError = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CallHistoryUiState(number = number),
    )

    fun placeCall(): CallPlacer.PlaceResult {
        val result = callPlacer.place(number)
        placeError.value = when (result) {
            CallPlacer.PlaceResult.OK,
            CallPlacer.PlaceResult.NO_PERMISSION -> null
            CallPlacer.PlaceResult.INVALID_NUMBER -> "Invalid number."
            CallPlacer.PlaceResult.ERROR -> "Couldn't place the call. Try again."
        }
        return result
    }

    fun dismissPlaceError() {
        placeError.value = null
    }

    companion object {
        private const val SUFFIX_DIGITS = 9
    }
}
