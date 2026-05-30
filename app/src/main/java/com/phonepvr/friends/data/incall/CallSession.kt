package com.phonepvr.friends.data.incall

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class CallSimpleState {
    NEW,
    RINGING,
    DIALING,
    CONNECTING,
    ACTIVE,
    HOLDING,
    DISCONNECTED,
}

enum class CallDirection { INCOMING, OUTGOING }

enum class CallAudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

data class CallSnapshot(
    val callId: String,
    val state: CallSimpleState,
    val direction: CallDirection,
    val number: String,
    val callerDisplayName: String?,
    /** Wall-clock ms when the call first reached ACTIVE — drives the timer. */
    val connectTimeMillis: Long?,
    val isVideo: Boolean,
    /** True iff the platform reports CAPABILITY_HOLD for this Call. */
    val canHold: Boolean = false,
)

data class AudioSnapshot(
    val isMuted: Boolean,
    val route: CallAudioRoute,
    val availableRoutes: Set<CallAudioRoute>,
)

/**
 * Process singleton that bridges the [android.telecom.InCallService] and
 * the Compose in-call UI. The service writes here when calls are added /
 * change state / receive an audio update; the Activity reads via StateFlow
 * and calls back here to drive mute / speaker / accept / reject / end /
 * hold / swap.
 *
 * Multi-call awareness: holds a list of live [android.telecom.Call]s, each
 * paired with its derived [CallSnapshot]. The "primary" call is whichever
 * one the UI should foreground — usually the ACTIVE one, falling back to
 * DIALING / RINGING / HOLDING in priority order. The "held" snapshot is
 * the other call when there are two and one is on hold, so the in-call UI
 * can render the swap surface.
 */
@Singleton
class CallSession @Inject constructor() {

    private data class TrackedCall(
        val call: Call,
        val snapshot: CallSnapshot,
    )

    @Volatile
    private var calls: List<TrackedCall> = emptyList()

    @Volatile
    private var service: InCallService? = null

    private val _snapshot = MutableStateFlow<CallSnapshot?>(null)
    val snapshot: StateFlow<CallSnapshot?> = _snapshot.asStateFlow()

    private val _heldSnapshot = MutableStateFlow<CallSnapshot?>(null)
    /** The non-primary call, when there are two and one is HOLDING. */
    val heldSnapshot: StateFlow<CallSnapshot?> = _heldSnapshot.asStateFlow()

    private val _audio = MutableStateFlow(
        AudioSnapshot(
            isMuted = false,
            route = CallAudioRoute.EARPIECE,
            availableRoutes = setOf(CallAudioRoute.EARPIECE),
        ),
    )
    val audio: StateFlow<AudioSnapshot> = _audio.asStateFlow()

    // --- Wiring from the InCallService ---

    fun attachService(svc: InCallService) {
        service = svc
    }

    fun detachService(svc: InCallService) {
        if (service === svc) service = null
    }

    @Synchronized
    fun attachCall(call: Call) {
        if (calls.none { it.call === call }) {
            calls = calls + TrackedCall(call, call.toSnapshot())
        }
        recomputeSnapshots()
    }

    @Synchronized
    fun detachCall(call: Call) {
        calls = calls.filter { it.call !== call }
        recomputeSnapshots()
    }

    @Synchronized
    fun publishSnapshot() {
        // Re-derive every tracked call's snapshot from its live Call object.
        calls = calls.map { tc -> TrackedCall(tc.call, tc.call.toSnapshot()) }
        recomputeSnapshots()
    }

    fun publishAudio(state: CallAudioState) {
        _audio.value = state.toSnapshot()
    }

    private fun recomputeSnapshots() {
        if (calls.isEmpty()) {
            _snapshot.value = null
            _heldSnapshot.value = null
            return
        }
        val sorted = calls.sortedBy { primaryPriority(it.snapshot.state) }
        _snapshot.value = sorted.first().snapshot
        // The held banner only fires when there's a second call and it's on
        // hold (the typical "added a call while in another call" shape).
        _heldSnapshot.value = sorted.drop(1)
            .firstOrNull { it.snapshot.state == CallSimpleState.HOLDING }
            ?.snapshot
    }

    private fun primaryPriority(state: CallSimpleState): Int = when (state) {
        CallSimpleState.ACTIVE -> 0
        CallSimpleState.DIALING, CallSimpleState.CONNECTING, CallSimpleState.NEW -> 1
        CallSimpleState.RINGING -> 2
        CallSimpleState.HOLDING -> 3
        CallSimpleState.DISCONNECTED -> 4
    }

    // --- UI actions ---

    fun accept() {
        callInState(CallSimpleState.RINGING)
            ?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        callInState(CallSimpleState.RINGING)?.reject(false, null)
    }

    /**
     * Rejects the call and asks Telecom to send [message] back as an SMS
     * (the "can't talk right now" quick replies). The telephony layer
     * delivers the text; Bondwidth itself never touches the network.
     */
    fun rejectWithMessage(message: String) {
        callInState(CallSimpleState.RINGING)?.reject(true, message)
    }

    fun end() {
        primaryCall()?.disconnect()
    }

    /** Puts the primary call on hold. */
    fun hold() {
        callInState(CallSimpleState.ACTIVE)?.hold()
    }

    /** Resumes a held primary call. */
    fun unhold() {
        callInState(CallSimpleState.HOLDING)?.unhold()
    }

    /**
     * Swaps which call is active when two calls are present — the
     * Telecom-standard "switch between calls" gesture. Holds whichever is
     * ACTIVE and resumes whichever is HOLDING; if only one call exists this
     * collapses to plain hold or unhold.
     */
    fun swap() {
        val active = callInState(CallSimpleState.ACTIVE)
        val held = callInState(CallSimpleState.HOLDING)
        active?.hold()
        held?.unhold()
    }

    /** Plays a DTMF tone for the in-call dialpad (IVR menus). */
    fun playDtmf(digit: Char) {
        primaryCall()?.playDtmfTone(digit)
    }

    /** Stops the currently-playing DTMF tone. */
    fun stopDtmf() {
        primaryCall()?.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setRoute(route: CallAudioRoute) {
        service?.setAudioRoute(
            when (route) {
                CallAudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
                CallAudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                CallAudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                CallAudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            },
        )
    }

    /**
     * Advances to the next available audio output, in a stable order. Used by
     * the notification's single audio button (where a full picker doesn't
     * fit): earpiece -> speaker -> bluetooth -> wired -> back to earpiece,
     * skipping whatever isn't connected.
     */
    fun cycleAudioRoute() {
        val current = _audio.value
        val order = listOf(
            CallAudioRoute.EARPIECE,
            CallAudioRoute.SPEAKER,
            CallAudioRoute.BLUETOOTH,
            CallAudioRoute.WIRED_HEADSET,
        ).filter { it in current.availableRoutes }
        if (order.size < 2) return
        val idx = order.indexOf(current.route).coerceAtLeast(0)
        setRoute(order[(idx + 1) % order.size])
    }

    fun hasActiveCall(): Boolean = calls.isNotEmpty()

    /** Snapshot of every tracked call — primary first, held second. */
    private fun primaryCall(): Call? {
        val primaryId = _snapshot.value?.callId ?: return null
        return calls.firstOrNull { it.snapshot.callId == primaryId }?.call
    }

    private fun callInState(state: CallSimpleState): Call? =
        calls.firstOrNull { it.snapshot.state == state }?.call
}

private fun Call.toSnapshot(): CallSnapshot {
    val details = details
    @Suppress("DEPRECATION")
    val rawState = this.state
    val state = rawState.toSimpleState()
    val direction = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> when (details.callDirection) {
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            else -> if (state == CallSimpleState.RINGING) {
                CallDirection.INCOMING
            } else {
                CallDirection.OUTGOING
            }
        }
        // Pre-API-29: callDirection isn't available; ringing = incoming.
        state == CallSimpleState.RINGING -> CallDirection.INCOMING
        else -> CallDirection.OUTGOING
    }
    val number = details.handle?.schemeSpecificPart.orEmpty()
    val callerDisplayName = details.callerDisplayName?.takeIf { it.isNotBlank() }
    val connectTime = details.connectTimeMillis.takeIf { it > 0 }
    val isVideo =
        details.videoState != android.telecom.VideoProfile.STATE_AUDIO_ONLY &&
            details.videoState != 0
    // Held / un-hold uses CAPABILITY_HOLD; CAPABILITY_SUPPORT_HOLD is the
    // "can hold be offered" flag — both wanted before the Hold UI shows.
    val canHold = details.can(Call.Details.CAPABILITY_HOLD) &&
        details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)
    // No public Call ID getter on Call.Details; the number + the Call's
    // identity hash are unique enough for the UI's Compose key.
    val callId = "${number.ifEmpty { "anon" }}-${System.identityHashCode(this)}"
    return CallSnapshot(
        callId = callId,
        state = state,
        direction = direction,
        number = number,
        callerDisplayName = callerDisplayName,
        connectTimeMillis = connectTime,
        isVideo = isVideo,
        canHold = canHold,
    )
}

private fun Int.toSimpleState(): CallSimpleState = when (this) {
    Call.STATE_NEW -> CallSimpleState.NEW
    Call.STATE_RINGING -> CallSimpleState.RINGING
    Call.STATE_DIALING -> CallSimpleState.DIALING
    Call.STATE_CONNECTING -> CallSimpleState.CONNECTING
    Call.STATE_ACTIVE -> CallSimpleState.ACTIVE
    Call.STATE_HOLDING -> CallSimpleState.HOLDING
    Call.STATE_DISCONNECTED -> CallSimpleState.DISCONNECTED
    else -> CallSimpleState.NEW
}

private fun CallAudioState.toSnapshot(): AudioSnapshot {
    val route = when (route) {
        CallAudioState.ROUTE_EARPIECE -> CallAudioRoute.EARPIECE
        CallAudioState.ROUTE_SPEAKER -> CallAudioRoute.SPEAKER
        CallAudioState.ROUTE_BLUETOOTH -> CallAudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> CallAudioRoute.WIRED_HEADSET
        else -> CallAudioRoute.EARPIECE
    }
    val available = mutableSetOf<CallAudioRoute>()
    val mask = supportedRouteMask
    if (mask and CallAudioState.ROUTE_EARPIECE != 0) available += CallAudioRoute.EARPIECE
    if (mask and CallAudioState.ROUTE_SPEAKER != 0) available += CallAudioRoute.SPEAKER
    if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) available += CallAudioRoute.BLUETOOTH
    if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) available += CallAudioRoute.WIRED_HEADSET
    if (available.isEmpty()) available += CallAudioRoute.EARPIECE
    return AudioSnapshot(
        isMuted = isMuted,
        route = route,
        availableRoutes = available,
    )
}
