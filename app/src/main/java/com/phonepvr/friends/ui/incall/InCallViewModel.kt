package com.phonepvr.friends.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.incall.AudioSnapshot
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InCallUiState(
    val snapshot: CallSnapshot? = null,
    val audio: AudioSnapshot = AudioSnapshot(
        isMuted = false,
        route = CallAudioRoute.EARPIECE,
        availableRoutes = setOf(CallAudioRoute.EARPIECE),
    ),
    val callEnded: Boolean = false,
)

@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callSession: CallSession,
) : ViewModel() {

    val state: StateFlow<InCallUiState> = combine(
        callSession.snapshot,
        callSession.audio,
    ) { snapshot, audio ->
        InCallUiState(
            snapshot = snapshot,
            audio = audio,
            // Either the service has cleared the call (snapshot null) or
            // the call self-reported DISCONNECTED. Activity reacts by
            // finishing itself after a short grace period.
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
