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
 * and calls back here to drive mute / speaker / accept / reject / end.
 *
 * Lifecycle: the service holds a single active [android.telecom.Call] at
 * a time in v1 (multi-call is Phase 7). When the call ends the service
 * clears its reference here so the Activity's collector receives a null
 * and pops itself off.
 */
@Singleton
class CallSession @Inject constructor() {

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var service: InCallService? = null

    private val _snapshot = MutableStateFlow<CallSnapshot?>(null)
    val snapshot: StateFlow<CallSnapshot?> = _snapshot.asStateFlow()

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
        // Only clear if the service detaching is the one we hold.
        if (service === svc) service = null
    }

    fun attachCall(call: Call) {
        currentCall = call
        publishSnapshot()
    }

    fun detachCall(call: Call) {
        if (currentCall === call) {
            currentCall = null
            _snapshot.value = null
        }
    }

    fun publishSnapshot() {
        val call = currentCall ?: return
        _snapshot.value = call.toSnapshot()
    }

    fun publishAudio(state: CallAudioState) {
        _audio.value = state.toSnapshot()
    }

    // --- UI actions ---

    fun accept() {
        currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        currentCall?.reject(false, null)
    }

    fun end() {
        currentCall?.disconnect()
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

    fun hasActiveCall(): Boolean = currentCall != null
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
    // No public Call ID getter on Call.Details; the number + the Call's
    // identity hash are unique enough for the UI's Compose key.
    val callId = number.ifEmpty { "anon-${System.identityHashCode(this)}" }
    return CallSnapshot(
        callId = callId,
        state = state,
        direction = direction,
        number = number,
        callerDisplayName = callerDisplayName,
        connectTimeMillis = connectTime,
        isVideo = isVideo,
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
