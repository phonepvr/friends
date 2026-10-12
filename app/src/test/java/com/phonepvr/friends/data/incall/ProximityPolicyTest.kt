package com.phonepvr.friends.data.incall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityPolicyTest {

    private fun call(
        state: CallSimpleState,
        direction: CallDirection = CallDirection.OUTGOING,
    ) = CallSnapshot(
        callId = "1",
        state = state,
        direction = direction,
        number = "5551234567",
        callerDisplayName = null,
        connectTimeMillis = null,
        isVideo = false,
    )

    private fun use(
        call: CallSnapshot?,
        route: CallAudioRoute = CallAudioRoute.EARPIECE,
        keypadOpen: Boolean = false,
    ) = ProximityPolicy.shouldUseProximity(call, route, keypadOpen)

    @Test
    fun offHookOnEarpiece_turnsScreenOffNearTheFace() {
        assertTrue(use(call(CallSimpleState.DIALING)))
        assertTrue(use(call(CallSimpleState.CONNECTING)))
        assertTrue(use(call(CallSimpleState.ACTIVE, CallDirection.INCOMING)))
        assertTrue(use(call(CallSimpleState.ACTIVE, CallDirection.OUTGOING)))
        assertTrue(use(call(CallSimpleState.HOLDING)))
    }

    @Test
    fun outgoingCallBeforeDialling_alreadyUsesProximity() {
        assertTrue(use(call(CallSimpleState.NEW, CallDirection.OUTGOING)))
        assertFalse(use(call(CallSimpleState.NEW, CallDirection.INCOMING)))
    }

    @Test
    fun ringing_keepsScreenOnSoTheUserCanAnswer() {
        assertFalse(use(call(CallSimpleState.RINGING, CallDirection.INCOMING)))
    }

    @Test
    fun noCallOrEndedCall_releasesProximity() {
        assertFalse(use(null))
        assertFalse(use(call(CallSimpleState.DISCONNECTED)))
    }

    @Test
    fun audioAwayFromTheEarpiece_keepsScreenOn() {
        val active = call(CallSimpleState.ACTIVE)
        assertFalse(use(active, route = CallAudioRoute.SPEAKER))
        assertFalse(use(active, route = CallAudioRoute.BLUETOOTH))
        assertFalse(use(active, route = CallAudioRoute.WIRED_HEADSET))
    }

    @Test
    fun openKeypad_keepsScreenOnForTyping() {
        assertFalse(use(call(CallSimpleState.ACTIVE), keypadOpen = true))
    }
}
