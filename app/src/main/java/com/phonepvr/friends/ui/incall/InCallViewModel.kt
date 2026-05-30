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
import com.phonepvr.friends.data.incall.CallerIdentityResolver
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
    /** Address-book name for the caller (any saved contact, not just bonds). */
    val callerName: String? = null,
    /** System contact photo URI for the caller, when available. */
    val callerPhotoUri: String? = null,
    /** The non-primary call, when there's a second call on hold (for swap UI). */
    val heldSnapshot: CallSnapshot? = null,
    /** Best-effort display name for the held call. */
    val heldName: String? = null,
    val callEnded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callSession: CallSession,
    private val phoneNumberDao: PhoneNumberDao,
    private val personDao: PersonDao,
    private val timelineDao: TimelineDao,
    private val callerIdentityResolver: CallerIdentityResolver,
) : ViewModel() {

    // Address-book identity (name + photo) for the caller, resolved off the
    // main thread whenever the number changes. Covers any saved contact, so
    // the name shows even when Telecom's callerDisplayName is null (common
    // for outgoing calls and on many devices once connected).
    private val callerIdentity: Flow<CallerIdentityResolver.Identity> =
        callSession.snapshot
            .map { it?.number.orEmpty() }
            .distinctUntilChanged()
            .map { number ->
                if (number.isBlank()) {
                    CallerIdentityResolver.Identity.EMPTY
                } else {
                    callerIdentityResolver.resolve(number)
                }
            }

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

    // Pre-merge bonded inputs so the main combine stays at ≤5 typed args.
    private data class BondedRaw(
        val person: com.phonepvr.friends.data.db.entity.PersonEntity?,
        val lastContactMs: Long?,
    )

    private val bondedRaw: Flow<BondedRaw> = combine(matchedPerson, lastContactAtMs) { p, lc ->
        BondedRaw(p, lc)
    }

    val state: StateFlow<InCallUiState> = combine(
        callSession.snapshot,
        callSession.audio,
        bondedRaw,
        callerIdentity,
        callSession.heldSnapshot,
    ) { snapshot, audio, bonded, identity, held ->
        val bondedPerson = bonded.person?.takeIf { !it.isArchived }?.let { p ->
            val lastContactDate = bonded.lastContactMs?.let { ms ->
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
                daysSinceLastContact = bonded.lastContactMs?.let { ms ->
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
            bondedPerson = bondedPerson,
            callerName = identity.displayName,
            callerPhotoUri = identity.photoUri,
            heldSnapshot = held,
            heldName = held?.callerDisplayName ?: held?.number?.takeIf { it.isNotBlank() },
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
    fun rejectWith(message: String) = callSession.rejectWithMessage(message)
    fun end() = callSession.end()

    fun toggleMute() {
        callSession.setMuted(!state.value.audio.isMuted)
    }

    fun setAudioRoute(route: CallAudioRoute) {
        callSession.setRoute(route)
    }

    /**
     * Toggle the primary call between active and on-hold. When a second
     * call is parked on hold, the platform handles the cross-fade itself —
     * this just flips the primary's state.
     */
    fun toggleHold() {
        if (state.value.snapshot?.state == CallSimpleState.HOLDING) {
            callSession.unhold()
        } else {
            callSession.hold()
        }
    }

    /** Swap which call is active when two calls are present. */
    fun swap() = callSession.swap()

    /** Tap-and-release on an in-call dialpad key sends one DTMF tone. */
    fun pressDtmf(digit: Char) {
        callSession.playDtmf(digit)
        callSession.stopDtmf()
    }
}

/** Pre-set "can't talk" replies offered on a long-press of Reject. */
val QUICK_DECLINE_MESSAGES: List<String> = listOf(
    "Can't talk right now — call you back.",
    "On my way.",
    "Can't talk. What's up?",
)
