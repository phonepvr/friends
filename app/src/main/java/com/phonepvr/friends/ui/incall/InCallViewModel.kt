package com.phonepvr.friends.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.blocking.BlockedNumberManager
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.incall.AudioSnapshot
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.data.incall.CallerIdentityResolver
import com.phonepvr.friends.data.incall.Ringer
import com.phonepvr.friends.data.settings.DEFAULT_QUICK_REPLIES
import com.phonepvr.friends.data.settings.SettingsRepository
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
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /**
     * Whether to offer "Block & reject" on the incoming screen: we can block
     * numbers (default dialer, phone form factor) AND the caller's number
     * isn't withheld. False hides the action rather than letting it fail.
     */
    val canBlockCaller: Boolean = false,
    /** User-editable replies offered on long-press of Reject. */
    val quickReplyMessages: List<String> = DEFAULT_QUICK_REPLIES,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callSession: CallSession,
    private val phoneNumberDao: PhoneNumberDao,
    private val personDao: PersonDao,
    private val timelineDao: TimelineDao,
    private val callerIdentityResolver: CallerIdentityResolver,
    private val blockedNumberManager: BlockedNumberManager,
    private val settingsRepository: SettingsRepository,
    private val ringer: Ringer,
) : ViewModel() {

    // Default-dialer role + device capability can't change mid-call, so
    // resolve the "can we block at all?" question once instead of on every
    // snapshot/audio emission through the combine below.
    private val canBlockNumbers: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        blockedNumberManager.canBlock()
    }

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

    // Pre-merge bonded inputs + persisted UI prefs so the main combine
    // stays at ≤5 typed args.
    private data class Aux(
        val person: com.phonepvr.friends.data.db.entity.PersonEntity?,
        val lastContactMs: Long?,
        val quickReplies: List<String>,
    )

    private val aux: Flow<Aux> = combine(
        matchedPerson,
        lastContactAtMs,
        settingsRepository.settings.map { it.quickReplyMessages },
    ) { p, lc, q -> Aux(p, lc, q) }

    val state: StateFlow<InCallUiState> = combine(
        callSession.snapshot,
        callSession.audio,
        aux,
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
            canBlockCaller = canBlockNumbers && snapshot?.number?.isNotBlank() == true,
            quickReplyMessages = bonded.quickReplies,
        )
    }.runningReduce { previous, current ->
        // Once the call ends, the call.snapshot goes null → both the
        // identity flow and the matched-person lookup emit nulls →
        // callerName / bondedPerson become null and the Header flashes
        // "Unknown" for the 800 ms before the activity finishes. Hold
        // the last known caller info through the ended state so that
        // transition stays cosmetic.
        if (current.callEnded) {
            current.copy(
                callerName = current.callerName ?: previous.callerName,
                callerPhotoUri = current.callerPhotoUri ?: previous.callerPhotoUri,
                bondedPerson = current.bondedPerson ?: previous.bondedPerson,
            )
        } else {
            current
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InCallUiState(),
    )

    fun accept() = callSession.accept()
    fun reject() = callSession.reject()
    fun rejectWith(message: String) = callSession.rejectWithMessage(message)
    fun end() = callSession.end()

    /**
     * Silences the ringtone for the current incoming call, without rejecting
     * it. No-op when there isn't a ringing call. Wired to volume keys and
     * the screen turning off (power button) by [InCallActivity].
     *
     * Note: TelecomManager.silenceRinger() is intentionally NOT used here —
     * it only silences the system-generated ringtone, and as the default
     * dialer Bondwidth plays its own via [Ringer]. We have to stop our
     * own MediaPlayer + vibration source.
     */
    fun silenceRinger() {
        val snap = state.value.snapshot ?: return
        if (snap.state != CallSimpleState.RINGING) return
        ringer.silence()
    }

    /**
     * Block the caller's number, then reject the call. Block first so the
     * insert is guaranteed to land before the call disconnects and tears the
     * activity (and this scope) down; the extra few ms of ringing is
     * imperceptible. Falls back to a plain reject when the number is withheld.
     */
    fun blockAndReject() {
        val number = state.value.snapshot?.number?.takeIf { it.isNotBlank() }
        if (number == null) {
            callSession.reject()
            return
        }
        viewModelScope.launch {
            blockedNumberManager.block(number)
            callSession.reject()
        }
    }

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

    /** Merge the active and held calls into a conference. */
    fun merge() = callSession.merge()

    /** Tap-and-release on an in-call dialpad key sends one DTMF tone. */
    fun pressDtmf(digit: Char) {
        callSession.playDtmf(digit)
        callSession.stopDtmf()
    }
}
