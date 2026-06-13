package com.phonepvr.friends.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.dialer.RecentsRepository
import com.phonepvr.friends.domain.review.BondRef
import com.phonepvr.friends.domain.review.CallAnalytics
import com.phonepvr.friends.domain.review.CallAnalyticsResult
import com.phonepvr.friends.domain.review.CallRecord
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Selectable look-back windows for the call analytics. */
enum class AnalyticsWindow(val days: Int, val label: String) {
    MONTH(30, "30 days"),
    TWO_MONTHS(60, "60 days"),
    QUARTER(90, "90 days"),
    YEAR(365, "1 year"),
}

sealed interface CallAnalyticsUiState {
    data object NoPermission : CallAnalyticsUiState
    data object Loading : CallAnalyticsUiState
    data class Ready(val result: CallAnalyticsResult) : CallAnalyticsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CallAnalyticsViewModel @Inject constructor(
    recentsRepository: RecentsRepository,
    systemContactsRepository: SystemContactsRepository,
    phoneNumberDao: PhoneNumberDao,
) : ViewModel() {

    private val window = MutableStateFlow(AnalyticsWindow.MONTH)
    val selectedWindow: StateFlow<AnalyticsWindow> = window

    private val callLogGranted = MutableStateFlow(false)

    private val callsFlow: Flow<List<DeviceCall>> =
        combine(window, callLogGranted) { w, granted -> w to granted }
            .flatMapLatest { (w, granted) ->
                if (!granted) {
                    flowOf(emptyList())
                } else {
                    val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(w.days.toLong())
                    recentsRepository.observe(since)
                }
            }

    private val contactNameBySuffix: Flow<Map<String, String>> =
        systemContactsRepository.observeAll().flatMapLatest { contacts ->
            flowOf(
                buildMap {
                    for (c in contacts) {
                        for (number in c.phoneNumbers) {
                            val key = number.filter(Char::isDigit).takeLast(SUFFIX_LEN)
                            if (key.length >= MIN_MATCH_LEN && key !in this) {
                                put(key, c.displayName)
                            }
                        }
                    }
                },
            )
        }

    private val bondData: Flow<Pair<Map<String, BondRef>, Int>> =
        phoneNumberDao.observeActiveBondedNumbers().flatMapLatest { rows ->
            val map = HashMap<String, BondRef>()
            for (row in rows) {
                val key = row.normalizedNumber.filter(Char::isDigit).takeLast(SUFFIX_LEN)
                if (key.length >= MIN_MATCH_LEN && key !in map) {
                    map[key] = BondRef(
                        personId = row.personId,
                        displayName = row.displayName,
                        photoRelativePath = row.photoRelativePath,
                    )
                }
            }
            // Denominator for "bonded reach": distinct bonds that have at
            // least one number (i.e. are reachable by call).
            val reachable = rows.map { it.personId }.toSet().size
            flowOf(map to reachable)
        }

    val uiState: StateFlow<CallAnalyticsUiState> = combine(
        window,
        callLogGranted,
        callsFlow,
        contactNameBySuffix,
        bondData,
    ) { w, granted, calls, contactSuffix, bonds ->
        if (!granted) {
            CallAnalyticsUiState.NoPermission
        } else {
            val (bondSuffix, bondedTotal) = bonds
            val result = CallAnalytics.compute(
                windowDays = w.days,
                calls = calls.map {
                    CallRecord(it.number, it.type, it.durationSeconds, it.timestampMillis)
                },
                bondBySuffix = bondSuffix,
                contactNameBySuffix = contactSuffix,
                bondedTotalCount = bondedTotal,
            )
            CallAnalyticsUiState.Ready(result)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CallAnalyticsUiState.Loading,
    )

    fun setWindow(w: AnalyticsWindow) {
        window.value = w
    }

    fun onCallLogPermission(granted: Boolean) {
        callLogGranted.value = granted
    }

    companion object {
        private const val SUFFIX_LEN = 9
        private const val MIN_MATCH_LEN = 7
    }
}
