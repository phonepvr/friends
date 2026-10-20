package com.phonepvr.friends.data.incall

import com.phonepvr.friends.data.incall.ProximityPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityPolicyTest {

    private fun call(
        state: CallSimpleState,
        direction: CallDirection = CallDirection.OUTGOING,
        isVideo: Boolean = false,
    ) = CallSnapshot(
        callId = "1",
        state = state,
        direction = direction,
        number = "5551234567",
        callerDisplayName = null,
        connectTimeMillis = null,
        isVideo = isVideo,
    )

    private fun decide(
        call: CallSnapshot?,
        route: CallAudioRoute = CallAudioRoute.EARPIECE,
        enabled: Boolean = true,
    ) = ProximityPolicy.decide(call, route, enabled)

    @Test
    fun offHookOnEarpiece_holdsTheSensor() {
        assertEquals(Action.HOLD, decide(call(CallSimpleState.DIALING)))
        assertEquals(Action.HOLD, decide(call(CallSimpleState.CONNECTING)))
        assertEquals(Action.HOLD, decide(call(CallSimpleState.ACTIVE, CallDirection.INCOMING)))
        assertEquals(Action.HOLD, decide(call(CallSimpleState.ACTIVE, CallDirection.OUTGOING)))
        assertEquals(Action.HOLD, decide(call(CallSimpleState.HOLDING)))
    }

    @Test
    fun outgoingCallBeforeDialling_alreadyHolds() {
        assertEquals(Action.HOLD, decide(call(CallSimpleState.NEW, CallDirection.OUTGOING)))
        assertEquals(
            Action.RELEASE_WHEN_CLEAR,
            decide(call(CallSimpleState.NEW, CallDirection.INCOMING)),
        )
    }

    @Test
    fun ringingEndedOrNoCall_releaseOnlyOnceThePhoneLeavesTheFace() {
        assertEquals(
            Action.RELEASE_WHEN_CLEAR,
            decide(call(CallSimpleState.RINGING, CallDirection.INCOMING)),
        )
        assertEquals(Action.RELEASE_WHEN_CLEAR, decide(call(CallSimpleState.DISCONNECTED)))
        assertEquals(Action.RELEASE_WHEN_CLEAR, decide(null))
    }

    @Test
    fun audioAwayFromTheEarpiece_lightsTheScreenAtOnce() {
        val active = call(CallSimpleState.ACTIVE)
        assertEquals(Action.RELEASE_NOW, decide(active, route = CallAudioRoute.SPEAKER))
        assertEquals(Action.RELEASE_NOW, decide(active, route = CallAudioRoute.BLUETOOTH))
        assertEquals(Action.RELEASE_NOW, decide(active, route = CallAudioRoute.WIRED_HEADSET))
    }

    @Test
    fun videoCall_lightsTheScreenAtOnce() {
        assertEquals(Action.RELEASE_NOW, decide(call(CallSimpleState.ACTIVE, isVideo = true)))
    }

    @Test
    fun switchedOffInSettings_neverHolds() {
        assertEquals(Action.RELEASE_NOW, decide(call(CallSimpleState.ACTIVE), enabled = false))
        assertEquals(Action.RELEASE_NOW, decide(null, enabled = false))
    }
}
