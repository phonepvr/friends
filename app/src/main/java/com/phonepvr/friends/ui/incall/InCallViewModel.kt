package com.phonepvr.friends.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.incall.AudioSnapshot
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The matched bonded contact for the active call, if any. */
data class MatchedBondedPerson(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
    val cadenceTargetDays: Int?,
    val daysSinceLastContact: Int?,
    val cadenceStatus: CadenceStatus,
)

data class InCallUiState(
    val snapshot: CallSnapshot? = null,
    val audio: AudioSnapshot = AudioSnapshot(
        isMuted = false,
        route = CallAudioRoute.EARPIECE,
        availableRoutes = setOf(CallAudioRoute.EARPIECE),
    ),
    val bondedPerson: MatchedBondedPerson? = null,
    val callEnded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callSession: CallSession,
    private val phoneNumberDao: PhoneNumberDao,
    private val personDao: PersonDao,
    private val timelineDao: TimelineDao,
) : ViewModel() {

    // Resolve the call's number to a tracked PersonEntity. Suffix-match on
    // the last 9 digits so different country-code prefixes still hit.
    // Re-runs only when the call number changes — not on every audio
    // update or recomposition.
    private val matchedPersonId: Flow<Long?> = callSession.snapshot
        .map { it?.number?.takeIf { n -> n.isNotBlank() } }
        .distinctUntilChanged()
        .map { rawNumber ->
            if (rawNumber.isNullOrBlank()) return@map null
            val digits = rawNumber.filter { it.isDigit() }
            if (digits.length < 4) return@map null
            phoneNumberDao.findOneByNumberSuffix(digits.takeLast(9))?.personId
        }

    private val matchedPerson = matchedPersonId.flatMapLatest { id ->
        if (id == null) flowOf(null) else personDao.observeById(id)
    }

    private val lastContactAtMs: Flow<Long?> = matchedPersonId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            flow {
                emit(null)
                emit(timelineDao.latestContactAt(id))
            }
        }
    }

    val state: StateFlow<InCallUiState> = combine(
        callSession.snapshot,
        callSession.audio,
        matchedPerson,
        lastContactAtMs,
    ) { snapshot, audio, person, lastContact ->
        val bonded = person?.takeIf { !it.isArchived }?.let { p ->
            val lastContactDate = lastContact?.let { ms ->
                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            val cadence = CadenceCalculator.status(
                lastContact = lastContactDate,
                cadenceTargetDays = p.cadenceTargetDays,
                today = LocalDate.now(),
            )
            MatchedBondedPerson(
                personId = p.id,
                displayName = p.displayName,
                photoRelativePath = p.photoRelativePath,
                cadenceTargetDays = p.cadenceTargetDays,
                daysSinceLastContact = lastContact?.let { ms ->
                    TimeUnit.MILLISECONDS
                        .toDays(System.currentTimeMillis() - ms)
                        .toInt()
                        .coerceAtLeast(0)
                },
                cadenceStatus = cadence,
            )
        }
        InCallUiState(
            snapshot = snapshot,
            audio = audio,
            bondedPerson = bonded,
            callEnded = snapshot == null ||
                snapshot.state == CallSimpleState.DISCONNECTED,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InCallUiState(),
    )

    fun accept() = callSession.accept()
    fun reject() = callSession.reject()
    fun end() = callSession.end()

    fun toggleMute() {
        callSession.setMuted(!state.value.audio.isMuted)
    }

    fun toggleSpeaker() {
        val next = if (state.value.audio.route == CallAudioRoute.SPEAKER) {
            CallAudioRoute.EARPIECE
        } else {
            CallAudioRoute.SPEAKER
        }
        callSession.setRoute(next)
    }
}
